package dev.diego.expanda.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class EspansoSourceFile(
    val relativePath: String,
    /** Exact source text, including comments, scalar style and line endings. */
    val content: String,
)

data class EspansoSourceImportResult(
    val matches: Int,
    val issues: List<CompatibilityIssue>,
)

data class SourceWorkspaceState(
    val linked: Boolean = false,
    val issue: String? = null,
    val lastSyncAt: Long? = null,
)

/**
 * Source-first Espanso workspace.
 *
 * A linked SAF tree is authoritative. The private directory is a last-known-good
 * snapshot, and SQLite is only a runtime projection plus Android metadata.
 */
class EspansoSourceRepository(
    private val context: Context,
    private val matches: MatchRepository,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val root = File(context.filesDir, "espanso-match")
    private val mutation = Mutex()
    private val mutableFiles = MutableStateFlow<List<EspansoSourceFile>>(emptyList())
    private val mutableWorkspace = MutableStateFlow(SourceWorkspaceState())
    val files: StateFlow<List<EspansoSourceFile>> = mutableFiles.asStateFlow()
    val workspace: StateFlow<SourceWorkspaceState> = mutableWorkspace.asStateFlow()

    private data class DecodedSourceSet(
        val byPath: Map<String, EspansoImportResult>,
        val activePaths: Set<String>,
        val issues: List<CompatibilityIssue>,
    )

    suspend fun initialize() = withContext(io) {
        settings.awaitLoaded()
        mutation.withLock {
            root.mkdirs()
            val linked = linkedUri()
            var sourceFiles = if (linked == null) {
                readInternalFiles()
            } else {
                runCatching { EspansoFolderAccess.read(context, linked) }
                    .onSuccess {
                        replaceInternalDirectory(it)
                        mutableWorkspace.value = SourceWorkspaceState(
                            linked = true,
                            lastSyncAt = System.currentTimeMillis(),
                        )
                    }
                    .onFailure { error ->
                        mutableWorkspace.value = SourceWorkspaceState(
                            linked = true,
                            issue = error.message ?: "The match folder is unavailable",
                        )
                    }
                    .getOrElse { readInternalFiles() }
            }
            sourceFiles = migrateLegacySyntaxInFiles(sourceFiles, linked)
            val legacyMatches = matches.matches.value.filter { it.sourceFile == null }
            if (sourceFiles.isEmpty() && legacyMatches.isNotEmpty()) {
                val migration = EspansoSourceFile(
                    MIGRATION_FILE,
                    EspansoYamlCodec.encode(
                        migrateLegacyMatches(legacyMatches),
                        settings.settings.value.globalVariables,
                    ).yaml,
                )
                if (linked == null) {
                    sourceFiles = listOf(migration)
                    replaceInternalDirectory(sourceFiles)
                } else {
                    EspansoFolderAccess.writeSafely(context, linked, migration, expectMissing = true)
                    sourceFiles = normalize(EspansoFolderAccess.read(context, linked))
                    replaceInternalDirectory(sourceFiles)
                }
                mutableWorkspace.value = SourceWorkspaceState(
                    linked = linked != null,
                    issue = "Existing snippets were migrated once to '$MIGRATION_FILE'. Their text and triggers are now stored as Espanso YAML.",
                    lastSyncAt = if (linked != null) System.currentTimeMillis() else null,
                )
            }
            sourceFiles = ensureBaseFileInWorkspace(sourceFiles, linked)
            rebuildProjection(sourceFiles)
            publish(sourceFiles)
        }
    }

    suspend fun importFiles(
        incoming: List<EspansoSourceFile>,
        replaceAll: Boolean,
    ): EspansoSourceImportResult = withContext(io) {
        mutation.withLock {
            val normalized = normalize(incoming)
            require(normalized.isNotEmpty()) { "No Espanso YAML files found" }
            val current = readAuthoritative()
            val candidate = if (replaceAll) normalized else merge(current, normalized)
            commit(
                current = current,
                candidate = candidate,
                changed = normalized,
                deleted = if (replaceAll) current.filter { old ->
                    normalized.none { it.relativePath == old.relativePath }
                } else emptyList(),
            )
        }
    }

    suspend fun installExampleSnippets(): EspansoSourceImportResult = withContext(io) {
        mutation.withLock {
            val linked = linkedUri()
            var current = readAuthoritative()
            current = ensureBaseFileInWorkspace(current, linked)
            val examples = ExampleSnippets.file
            val base = current.first { it.relativePath == BASE_FILE }
            val updatedBase = base.copy(content = ExampleSnippets.baseWithImport(base.content))
            val changed = buildList {
                add(examples)
                if (updatedBase.content != base.content) add(updatedBase)
            }
            val candidate = current
                .filterNot { it.relativePath == BASE_FILE || it.relativePath == ExampleSnippets.EXAMPLES_FILE }
                .plus(changed)
            commit(current, candidate, changed, emptyList())
        }
    }

    fun inspectFiles(incoming: List<EspansoSourceFile>): EspansoImportResult {
        val normalized = normalize(incoming)
        val decoded = decodeSourceSet(normalized)
        val importedMatches = normalized.flatMap { file -> decoded.byPath.getValue(file.relativePath).matches }
        val globals = normalized.asSequence()
            .filter { it.relativePath in decoded.activePaths }
            .flatMap { decoded.byPath.getValue(it.relativePath).globalVariables.asSequence() }
            .distinctBy(TemplateVariable::name)
            .toList()
        return EspansoImportResult(importedMatches, decoded.issues, globals)
    }

    suspend fun replaceSource(file: EspansoSourceFile): EspansoSourceImportResult = withContext(io) {
        mutation.withLock {
            val normalized = normalize(listOf(file)).single()
            EspansoYamlCodec.decode(normalized.content.removePrefix("\uFEFF"), normalized.relativePath, true)
            val current = readAuthoritativeForEdit(normalized.relativePath)
            commit(current, merge(current, listOf(normalized)), listOf(normalized), emptyList())
        }
    }

    suspend fun saveMatch(match: TextMatch): Long = withContext(io) {
        mutation.withLock {
            val sourcePath = match.sourceFile?.let(EspansoFolderAccess::safeRelativePath) ?: BASE_FILE
            val current = readAuthoritativeForEdit(sourcePath, allowMissing = match.sourceMatchIndex == null)
            val original = current.firstOrNull { it.relativePath == sourcePath }
                ?: EspansoSourceFile(sourcePath, "matches: []\n")
            val rawIndex = match.sourceMatchIndex
            if (rawIndex != null) {
                require(match.sourceEditMode == SourceEditMode.VISUAL) {
                    "This match uses YAML formatting or fields that the visual editor cannot preserve. Open Source to edit it."
                }
                require(EspansoSourceText.visualEditMode(original.content, rawIndex) == SourceEditMode.VISUAL) {
                    "This match contains comments or YAML syntax that must be edited from Source."
                }
            }
            require(match.runtimeCompatibility == RuntimeCompatibility.PORTABLE) {
                "Desktop-only matches must be edited from Source."
            }
            require(match.options.activation == TriggerActivation.IMMEDIATE) {
                "Delimiter activation is Android-only. Use immediate activation for an Espanso-compatible snippet."
            }
            val normalizedOptions = match.options.normalizedCase()
            require(match.selectionMode != TemplateSelectionMode.SEQUENTIAL) {
                "Sequential replacement selection is Android-only. Choose first, random or manual selection."
            }
            val encoded = EspansoYamlCodec.encodeMatchItems(match.copy(options = normalizedOptions))
            require(encoded.yaml.isNotBlank()) { "Could not encode this match as Espanso YAML" }
            require(encoded.issues.none { it.severity == CompatibilitySeverity.ERROR }) {
                encoded.issues.first { it.severity == CompatibilitySeverity.ERROR }.message
            }
            val updatedText = if (rawIndex != null) {
                EspansoSourceText.replaceMatch(original.content, rawIndex, encoded.yaml)
            } else {
                EspansoSourceText.appendMatches(original.content, encoded.yaml)
            }
            val updated = original.copy(content = updatedText)
            EspansoYamlCodec.decode(updated.content, sourcePath, importsResolved = true)
            val preferredIndex = rawIndex ?: EspansoSourceText.matchSpans(updatedText).lastIndex
            val preferred = match.copy(
                sourceFile = sourcePath,
                sourceMatchIndex = preferredIndex,
                options = normalizedOptions,
            )
            val result = commit(current, merge(current, listOf(updated)), listOf(updated), emptyList(), preferred)
            matches.matches.value.firstOrNull {
                it.sourceFile == sourcePath && it.sourceMatchIndex == preferredIndex
            }?.id ?: result.matches.toLong()
        }
    }

    suspend fun deleteMatch(match: TextMatch) = withContext(io) {
        mutation.withLock {
            val path = match.sourceFile ?: return@withLock matches.delete(match.id)
            val index = match.sourceMatchIndex ?: return@withLock matches.delete(match.id)
            val current = readAuthoritativeForEdit(path)
            val original = current.firstOrNull { it.relativePath == path }
                ?: throw EspansoFolderAccess.SourceConflictException("'$path' was removed. Sync and try again.")
            val updated = original.copy(content = EspansoSourceText.deleteMatch(original.content, index))
            EspansoYamlCodec.decode(updated.content, path, importsResolved = true)
            commit(current, merge(current, listOf(updated)), listOf(updated), emptyList())
        }
    }

    suspend fun saveGlobalVariables(updated: List<TemplateVariable>) = withContext(io) {
        mutation.withLock {
            val current = readAuthoritativeForEdit(BASE_FILE, allowMissing = true)
            val base = current.firstOrNull { it.relativePath == BASE_FILE }
                ?: EspansoSourceFile(BASE_FILE, "matches: []\n")
            require(EspansoSourceText.rootSectionVisualEditMode(base.content, "global_vars") == SourceEditMode.VISUAL) {
                "Global variables contain comments or advanced YAML. Edit them from Source."
            }
            require(updated.all { it.type.lowercase() in PORTABLE_VARIABLE_TYPES }) {
                "Desktop-only global variables must be edited from Source."
            }
            val revised = base.copy(
                content = EspansoSourceText.replaceRootSection(
                    base.content,
                    "global_vars",
                    EspansoYamlCodec.encodeGlobalVariablesSection(updated),
                ),
            )
            EspansoYamlCodec.decode(revised.content, BASE_FILE, importsResolved = true)
            commit(current, merge(current, listOf(revised)), listOf(revised), emptyList())
        }
    }

    suspend fun linkFolder(treeUri: Uri, incoming: List<EspansoSourceFile>) = withContext(io) {
        mutation.withLock {
            val scanned = normalize(incoming)
            val currentScan = normalize(EspansoFolderAccess.read(context, treeUri))
            require(currentScan == scanned) { "The folder changed while it was being linked. Scan it again." }
            val privateFiles = readInternalFiles()
            if (scanned.isEmpty() && privateFiles.isNotEmpty()) {
                privateFiles.forEach { file ->
                    EspansoFolderAccess.writeSafely(context, treeUri, file, expectMissing = true)
                }
            }
            val authoritative = normalize(EspansoFolderAccess.read(context, treeUri))
            val decoded = decodeSourceSet(authoritative)
            settings.setEspansoFolderUri(treeUri.toString())
            replaceInternalDirectory(authoritative)
            rebuildProjection(authoritative, decoded)
            publish(authoritative)
            mutableWorkspace.value = SourceWorkspaceState(true, lastSyncAt = System.currentTimeMillis())
        }
    }

    suspend fun syncLinkedFolder(): EspansoSourceImportResult = withContext(io) {
        mutation.withLock {
            val uri = linkedUri() ?: throw IllegalStateException("No Espanso folder is linked")
            val authoritative = normalize(EspansoFolderAccess.read(context, uri))
            val decoded = decodeSourceSet(authoritative)
            replaceInternalDirectory(authoritative)
            rebuildProjection(authoritative, decoded)
            publish(authoritative)
            mutableWorkspace.value = SourceWorkspaceState(true, lastSyncAt = System.currentTimeMillis())
            result(decoded)
        }
    }

    suspend fun unlinkFolder() = withContext(io) {
        mutation.withLock {
            linkedUri()?.let { uri ->
                val authoritative = normalize(EspansoFolderAccess.read(context, uri))
                replaceInternalDirectory(authoritative)
                rebuildProjection(authoritative)
                publish(authoritative)
            }
            settings.setEspansoFolderUri(null)
            mutableWorkspace.value = SourceWorkspaceState(false)
        }
    }

    suspend fun restoreFiles(restored: List<EspansoSourceFile>) = withContext(io) {
        mutation.withLock {
            if (restored.isEmpty()) return@withLock
            val normalized = normalize(restored)
            val current = readAuthoritative()
            commit(
                current,
                normalized,
                normalized,
                current.filter { old -> normalized.none { it.relativePath == old.relativePath } },
            )
        }
    }

    suspend fun reconcileSources() = withContext(io) {
        mutation.withLock {
            val current = readAuthoritative()
            replaceInternalDirectory(current)
            rebuildProjection(current)
            publish(current)
        }
    }

    suspend fun reset() = withContext(io) {
        mutation.withLock {
            root.deleteRecursively()
            root.mkdirs()
            mutableFiles.value = emptyList()
            mutableWorkspace.value = SourceWorkspaceState(linked = linkedUri() != null)
        }
    }

    fun exportAll(): EspansoExportResult {
        val current = files.value
        if (current.size == 1) return EspansoExportResult(current.single().content, emptyList())
        val portable = matches.matches.value.filter(TextMatch::runsOnAndroid)
        val result = EspansoYamlCodec.encode(portable, settings.settings.value.globalVariables)
        return if (current.size <= 1) result else result.copy(
            issues = result.issues + CompatibilityIssue(
                CompatibilitySeverity.INFO,
                "Espanso export",
                "Several source files were combined. Export an exact file or use the linked folder to preserve layout.",
            ),
        )
    }

    private fun readAuthoritative(): List<EspansoSourceFile> = linkedUri()?.let { uri ->
        normalize(EspansoFolderAccess.read(context, uri))
    } ?: readInternalFiles()

    private suspend fun readAuthoritativeForEdit(path: String, allowMissing: Boolean = false): List<EspansoSourceFile> {
        val current = readAuthoritative()
        val baseline = mutableFiles.value.firstOrNull { it.relativePath == path }
        val actual = current.firstOrNull { it.relativePath == path }
        if (baseline == null && actual != null && allowMissing) {
            publishConflict(current)
            throw EspansoFolderAccess.SourceConflictException("'$path' was created outside Expanda. Sync and try again.")
        }
        if (baseline != null && actual?.content != baseline.content) {
            publishConflict(current)
            throw EspansoFolderAccess.SourceConflictException("'$path' changed outside Expanda. Review the new source before saving.")
        }
        if (!allowMissing && actual == null) {
            publishConflict(current)
            throw EspansoFolderAccess.SourceConflictException("'$path' was removed. Sync and try again.")
        }
        return current
    }

    private suspend fun publishConflict(current: List<EspansoSourceFile>) {
        replaceInternalDirectory(current)
        rebuildProjection(current)
        publish(current)
        mutableWorkspace.value = SourceWorkspaceState(
            linked = linkedUri() != null,
            issue = "A source file changed outside Expanda. The latest version was loaded; your edit was not written.",
        )
    }

    private suspend fun commit(
        current: List<EspansoSourceFile>,
        candidate: List<EspansoSourceFile>,
        changed: List<EspansoSourceFile>,
        deleted: List<EspansoSourceFile>,
        preferred: TextMatch? = null,
    ): EspansoSourceImportResult {
        val normalizedCandidate = normalize(candidate)
        val decodedCandidate = decodeSourceSet(normalizedCandidate)
        val uri = linkedUri()
        val authoritative = if (uri == null) {
            replaceInternalDirectory(normalizedCandidate)
            normalizedCandidate
        } else {
            val currentByPath = current.associateBy(EspansoSourceFile::relativePath)
            changed.forEach { file ->
                val old = currentByPath[file.relativePath]
                EspansoFolderAccess.writeSafely(
                    context,
                    uri,
                    file,
                    expectedContent = old?.content,
                    expectMissing = old == null,
                )
            }
            deleted.forEach { EspansoFolderAccess.deleteSafely(context, uri, it) }
            normalize(EspansoFolderAccess.read(context, uri)).also { actual ->
                changed.forEach { expected ->
                    check(actual.firstOrNull { it.relativePath == expected.relativePath }?.content == expected.content) {
                        "The storage provider did not commit '${expected.relativePath}'"
                    }
                }
                replaceInternalDirectory(actual)
            }
        }
        val decoded = if (authoritative == normalizedCandidate) decodedCandidate else decodeSourceSet(authoritative)
        rebuildProjection(authoritative, decoded, preferred)
        publish(authoritative)
        mutableWorkspace.value = SourceWorkspaceState(
            linked = uri != null,
            lastSyncAt = if (uri != null) System.currentTimeMillis() else null,
        )
        return result(decoded)
    }

    private fun result(decoded: DecodedSourceSet) = EspansoSourceImportResult(
        matches = decoded.byPath.values.sumOf { it.matches.size },
        issues = decoded.issues,
    )

    private fun annotate(file: EspansoSourceFile, decoded: List<TextMatch>): List<TextMatch> = decoded.map { match ->
        val rawIndex = requireNotNull(match.sourceMatchIndex)
        val textMode = EspansoSourceText.visualEditMode(file.content, rawIndex)
        match.copy(
            sourceFile = file.relativePath,
            sourceEditMode = if (
                match.sourceEditMode == SourceEditMode.SOURCE_ONLY || textMode == SourceEditMode.SOURCE_ONLY
            ) SourceEditMode.SOURCE_ONLY else SourceEditMode.VISUAL,
        )
    }

    private fun decodeSourceSet(sourceFiles: List<EspansoSourceFile>): DecodedSourceSet {
        val byFile = sourceFiles.associateBy(EspansoSourceFile::relativePath)
        val active = sourceFiles.asSequence()
            .filterNot { File(it.relativePath).name.startsWith('_') }
            .mapTo(linkedSetOf(), EspansoSourceFile::relativePath)
        val pending = ArrayDeque(active)
        val issues = mutableListOf<CompatibilityIssue>()
        val missingImportOwners = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val ownerPath = pending.removeFirst()
            val owner = byFile[ownerPath] ?: continue
            val imports = runCatching { EspansoYamlCodec.importPaths(owner.content) }.getOrElse { error ->
                issues += CompatibilityIssue(
                    CompatibilitySeverity.ERROR,
                    ownerPath,
                    error.message ?: "Invalid Espanso YAML",
                )
                emptyList()
            }
            imports.forEach { importedPath ->
                val resolved = resolveRelativeImport(ownerPath, importedPath, byFile.keys)
                if (resolved == null) {
                    missingImportOwners += ownerPath
                    issues += CompatibilityIssue(
                        CompatibilitySeverity.WARNING,
                        ownerPath,
                        "Import '$importedPath' is outside the linked folder or was not found on Android.",
                    )
                } else if (active.add(resolved)) {
                    pending.addLast(resolved)
                }
            }
        }
        val decoded = sourceFiles.associate { file ->
            file.relativePath to if (file.relativePath !in active) {
                EspansoImportResult(
                    matches = emptyList(),
                    issues = listOf(
                        CompatibilityIssue(
                            CompatibilitySeverity.INFO,
                            file.relativePath,
                            "Kept inactive because its file name begins with _ and no active file imports it.",
                        ),
                    ),
                )
            } else {
                runCatching {
                    EspansoYamlCodec.decode(
                        file.content.removePrefix("\uFEFF"),
                        file.relativePath,
                        importsResolved = true,
                    )
                }.getOrElse { error ->
                    EspansoImportResult(
                        matches = emptyList(),
                        issues = listOf(
                            CompatibilityIssue(
                                CompatibilitySeverity.ERROR,
                                file.relativePath,
                                error.message ?: "Invalid Espanso YAML",
                            ),
                        ),
                    )
                }.let { result ->
                    if (file.relativePath !in missingImportOwners) result else result.copy(
                        matches = result.matches.map { match ->
                            match.copy(
                                runtimeCompatibility = RuntimeCompatibility.DESKTOP_ONLY,
                                sourceEditMode = SourceEditMode.SOURCE_ONLY,
                                compatibilityWarnings = (
                                    match.compatibilityWarnings +
                                        "One or more imported files are missing, so this match is inactive on Android."
                                    ).distinct(),
                            )
                        },
                    )
                }
            }
        }
        return DecodedSourceSet(
            byPath = decoded,
            activePaths = active,
            issues = issues + decoded.values.flatMap(EspansoImportResult::issues),
        )
    }

    private suspend fun rebuildProjection(
        sourceFiles: List<EspansoSourceFile>,
        decoded: DecodedSourceSet = decodeSourceSet(sourceFiles),
        preferred: TextMatch? = null,
    ) {
        val previous = matches.matches.value
        val usedMetadataIds = mutableSetOf<Long>()
        val annotated = sourceFiles.associate { file ->
            file.relativePath to annotate(file, decoded.byPath.getValue(file.relativePath).matches)
        }
        val globals = sourceFiles.asSequence()
            .filter { it.relativePath in decoded.activePaths }
            .flatMap { decoded.byPath.getValue(it.relativePath).globalVariables.asSequence() }
            .distinctBy(TemplateVariable::name)
            .toList()
        val unsupportedGlobals = globals.filterNot { it.type.lowercase() in PORTABLE_VARIABLE_TYPES }.map { it.name }.toSet()

        sourceFiles.forEach { file ->
            val safe = annotated.getValue(file.relativePath).map { parsed ->
                val referencedNames = parsed.replacements
                    .flatMap { replacement -> referencedVariableNames(replacement) }
                    .toSet()
                val unsupportedGlobal = referencedNames.any { it in unsupportedGlobals }
                if (!unsupportedGlobal) parsed else parsed.copy(
                    runtimeCompatibility = RuntimeCompatibility.DESKTOP_ONLY,
                    sourceEditMode = SourceEditMode.SOURCE_ONLY,
                    compatibilityWarnings = (
                        parsed.compatibilityWarnings +
                            "This match depends on a desktop-only global variable."
                    ).distinct(),
                )
            }
            val projected = reconcileMetadata(
                parsed = safe,
                previous = previous,
                preferred = preferred?.takeIf { it.sourceFile == file.relativePath },
                used = usedMetadataIds,
            )
            matches.replaceSource(file.relativePath, projected)
        }
        val sourcePaths = sourceFiles.mapTo(mutableSetOf(), EspansoSourceFile::relativePath)
        previous.filter { it.sourceFile == null || it.sourceFile !in sourcePaths }
            .forEach { matches.delete(it.id) }
        settings.setGlobalVariables(globals)
    }

    private fun reconcileMetadata(
        parsed: List<TextMatch>,
        previous: List<TextMatch>,
        preferred: TextMatch? = null,
        used: MutableSet<Long> = mutableSetOf(),
    ): List<TextMatch> = parsed.map { sourceMatch ->
        val exact = previous.firstOrNull {
            it.id !in used && it.sourceFile == sourceMatch.sourceFile &&
                it.sourceMatchIndex == sourceMatch.sourceMatchIndex && it.trigger == sourceMatch.trigger
        }
        val uniqueTrigger = previous.filter {
            it.id !in used && it.sourceFile == sourceMatch.sourceFile && it.trigger == sourceMatch.trigger
        }.singleOrNull()
        val legacyTrigger = previous.filter {
            it.id !in used && it.sourceFile == null && it.trigger == sourceMatch.trigger
        }.singleOrNull()
        val metadata = preferred?.takeIf {
            it.id !in used && it.sourceMatchIndex == sourceMatch.sourceMatchIndex
        } ?: exact ?: uniqueTrigger ?: legacyTrigger
        metadata?.id?.takeIf { it != 0L }?.let(used::add)
        if (metadata == null) {
            val tagged = if (sourceMatch.sourceFile == ExampleSnippets.EXAMPLES_FILE) {
                sourceMatch.copy(tags = setOf(ExampleSnippets.EXAMPLES_TAG))
            } else {
                sourceMatch
            }
            tagged
        } else sourceMatch.copy(
            id = metadata.id,
            tags = metadata.tags,
            enabled = metadata.enabled,
            excludedPackages = metadata.excludedPackages,
            templateIndex = metadata.templateIndex,
            usageCount = metadata.usageCount,
            createdAt = metadata.createdAt,
            updatedAt = metadata.updatedAt,
        )
    }

    private fun resolveRelativeImport(ownerPath: String, rawImport: String, available: Set<String>): String? {
        val normalizedImport = rawImport.replace('\\', '/')
        if (normalizedImport.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(normalizedImport)) return null
        val segments = ownerPath.substringBeforeLast('/', "")
            .split('/').filter(String::isNotBlank).toMutableList()
        normalizedImport.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return segments.joinToString("/").takeIf { it in available }
    }

    private fun normalize(sourceFiles: List<EspansoSourceFile>): List<EspansoSourceFile> {
        val normalized = sourceFiles.map { file ->
            file.copy(relativePath = EspansoFolderAccess.safeRelativePath(file.relativePath))
        }
        val duplicate = normalized.groupBy(EspansoSourceFile::relativePath).entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) { "Duplicate source path '${duplicate?.key}'" }
        return normalized.sortedBy(EspansoSourceFile::relativePath)
    }

    private fun migrateLegacySyntaxInFiles(
        files: List<EspansoSourceFile>,
        linked: Uri?,
    ): List<EspansoSourceFile> {
        var changed = false
        val migrated = files.map { file ->
            val decoded = runCatching {
                EspansoYamlCodec.decode(
                    file.content.removePrefix("\uFEFF"),
                    file.relativePath,
                    importsResolved = true,
                )
            }.getOrNull() ?: return@map file
            val updatedMatches = decoded.matches.map(LegacyTemplateMigrator::migrateMatch)
            if (updatedMatches == decoded.matches) return@map file
            changed = true
            file.copy(
                content = EspansoYamlCodec.encode(updatedMatches, decoded.globalVariables).yaml,
            )
        }
        if (!changed) return files
        migrated.forEach { file ->
            if (linked != null) {
                EspansoFolderAccess.writeSafely(context, linked, file)
            }
        }
        if (linked == null) replaceInternalDirectory(migrated)
        val notice = "Converted legacy 0.2 template tokens to Espanso syntax."
        mutableWorkspace.value = mutableWorkspace.value.copy(
            issue = mutableWorkspace.value.issue?.let { "$it $notice" } ?: notice,
        )
        return migrated
    }

    private fun migrateLegacyMatches(legacy: List<TextMatch>): List<TextMatch> = legacy
        .map(LegacyTemplateMigrator::migrateMatch)
        .flatMap { match ->
            val portable = match.copy(
            options = match.options.copy(
                caseSensitive = !match.options.propagateCase,
                activation = TriggerActivation.IMMEDIATE,
            ),
            selectionMode = when {
                match.replacements.size > 1 && match.selectionMode in setOf(
                    TemplateSelectionMode.FIRST,
                    TemplateSelectionMode.SEQUENTIAL,
                ) -> TemplateSelectionMode.MANUAL
                else -> match.selectionMode
            },
            runtimeCompatibility = RuntimeCompatibility.PORTABLE,
            sourceEditMode = SourceEditMode.VISUAL,
            sourceFile = null,
            sourceMatchIndex = null,
        )
        val literals = portable.triggers.filter { it.kind == TriggerKind.TEXT }
        val regex = portable.triggers.filter { it.kind == TriggerKind.REGEX }
        buildList {
            if (literals.isNotEmpty()) add(portable.copy(triggers = literals))
            regex.forEach { trigger -> add(portable.copy(triggers = listOf(trigger))) }
        }
        }

    private fun merge(
        current: List<EspansoSourceFile>,
        incoming: List<EspansoSourceFile>,
    ): List<EspansoSourceFile> = (
        current.filterNot { old -> incoming.any { it.relativePath == old.relativePath } } + incoming
        ).sortedBy(EspansoSourceFile::relativePath)

    private fun linkedUri(): Uri? = settings.settings.value.espansoFolderUri?.let(Uri::parse)

    private fun readInternalFiles(): List<EspansoSourceFile> {
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension.equals("yml", true) || it.extension.equals("yaml", true) }
            .map { file ->
                EspansoSourceFile(
                    file.relativeTo(root).invariantSeparatorsPath,
                    file.readText(Charsets.UTF_8),
                )
            }
            .sortedBy(EspansoSourceFile::relativePath)
            .toList()
    }

    /** Ensures [BASE_FILE] exists in the active workspace. */
    suspend fun ensureBaseFile() = withContext(io) {
        mutation.withLock {
            val linked = linkedUri()
            val current = readAuthoritative()
            val updated = ensureBaseFileInWorkspace(current, linked)
            if (updated !== current) {
                rebuildProjection(updated)
                publish(updated)
            }
        }
    }

    private suspend fun ensureBaseFileInWorkspace(
        current: List<EspansoSourceFile>,
        linked: Uri?,
    ): List<EspansoSourceFile> {
        if (current.any { it.relativePath == BASE_FILE }) return current
        val starter = EspansoSourceFile(BASE_FILE, ExampleSnippets.emptyBaseContent())
        if (linked != null) {
            EspansoFolderAccess.writeSafely(context, linked, starter, expectMissing = true)
            val authoritative = normalize(EspansoFolderAccess.read(context, linked))
            replaceInternalDirectory(authoritative)
            return authoritative
        }
        val candidate = merge(current, listOf(starter))
        replaceInternalDirectory(candidate)
        return candidate
    }

    private fun publish(sourceFiles: List<EspansoSourceFile>) {
        mutableFiles.value = sourceFiles.sortedBy(EspansoSourceFile::relativePath)
    }

    private fun replaceInternalDirectory(sourceFiles: List<EspansoSourceFile>) {
        val staging = File(root.parentFile, "${root.name}-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        sourceFiles.forEach { file ->
            val relative = EspansoFolderAccess.safeRelativePath(file.relativePath)
            File(staging, relative).apply {
                parentFile?.mkdirs()
                writeText(file.content, Charsets.UTF_8)
            }
        }
        val old = File(root.parentFile, "${root.name}-old")
        old.deleteRecursively()
        if (root.exists() && !root.renameTo(old)) throw IllegalStateException("Could not replace source snapshot")
        if (!staging.renameTo(root)) {
            old.renameTo(root)
            throw IllegalStateException("Could not install source snapshot")
        }
        old.deleteRecursively()
    }

    companion object {
        const val BASE_FILE = "base.yml"
        const val MIGRATION_FILE = "expanda-migrated.yml"
        private val PORTABLE_VARIABLE_TYPES = setOf("echo", "date", "choice", "random", "clipboard", "form", "match")
    }
}
