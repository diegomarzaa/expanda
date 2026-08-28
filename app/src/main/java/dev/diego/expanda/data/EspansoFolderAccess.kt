package dev.diego.expanda.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/** Storage Access Framework bridge for an Espanso-compatible match folder. */
object EspansoFolderAccess {
    class SourceConflictException(message: String) : IllegalStateException(message)

    fun read(context: Context, treeUri: Uri): List<EspansoSourceFile> {
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return buildList { readDirectory(resolver, treeUri, root, "", this) }
            .sortedBy(EspansoSourceFile::relativePath)
    }

    fun write(context: Context, treeUri: Uri, file: EspansoSourceFile) {
        writeSafely(context, treeUri, file)
    }

    /**
     * Writes through a verified sibling document. Providers with rename support get
     * a recoverable swap; other providers get a verified write with rollback.
     */
    fun writeSafely(
        context: Context,
        treeUri: Uri,
        file: EspansoSourceFile,
        expectedContent: String? = null,
        expectMissing: Boolean = false,
    ) {
        val resolver = context.contentResolver
        val parts = safeRelativePath(file.relativePath).split('/')
        var directory = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        parts.dropLast(1).forEach { name ->
            directory = findChild(resolver, treeUri, directory, name, directoryOnly = true)
                ?: DocumentsContract.createDocument(
                    resolver,
                    directory,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name,
                )
                ?: throw IllegalStateException("Could not create Espanso folder '$name'")
        }
        val displayName = parts.last()
        val existing = findChild(resolver, treeUri, directory, displayName, directoryOnly = false)
        if (expectMissing && existing != null) {
            throw SourceConflictException("'$displayName' was created by another app. Sync and try again.")
        }
        if (expectedContent != null) {
            val current = existing ?: throw SourceConflictException("'$displayName' was removed. Sync and try again.")
            if (readText(resolver, current, displayName) != expectedContent) {
                throw SourceConflictException("'$displayName' changed outside Expanda. Sync before saving.")
            }
        }

        val nonce = System.nanoTime().toString(36)
        val temporary = DocumentsContract.createDocument(
            resolver,
            directory,
            "text/plain",
            ".$displayName.expanda-$nonce",
        ) ?: throw IllegalStateException("Could not prepare '$displayName'")
        var temporaryInstalled = false
        try {
            writeText(resolver, temporary, file.content, displayName)
            check(readText(resolver, temporary, displayName) == file.content) {
                "Storage provider did not preserve '$displayName'"
            }

            if (existing == null) {
                val installed = renameDocument(resolver, temporary, displayName)
                if (installed != null) {
                    temporaryInstalled = true
                    check(readText(resolver, installed, displayName) == file.content)
                    return
                }
                val target = DocumentsContract.createDocument(resolver, directory, YAML_MIME, displayName)
                    ?: throw IllegalStateException("Could not create '$displayName'")
                writeText(resolver, target, file.content, displayName)
                check(readText(resolver, target, displayName) == file.content)
                return
            }

            val backupName = ".$displayName.expanda-backup-$nonce"
            val backup = renameDocument(resolver, existing, backupName)
            if (backup != null) {
                val installed = renameDocument(resolver, temporary, displayName)
                if (installed == null) {
                    renameDocument(resolver, backup, displayName)
                    throw IllegalStateException("Could not replace '$displayName'")
                }
                temporaryInstalled = true
                check(readText(resolver, installed, displayName) == file.content)
                runCatching { DocumentsContract.deleteDocument(resolver, backup) }
                return
            }

            val original = readText(resolver, existing, displayName)
            try {
                writeText(resolver, existing, file.content, displayName)
                check(readText(resolver, existing, displayName) == file.content)
            } catch (error: Throwable) {
                runCatching { writeText(resolver, existing, original, displayName) }
                throw error
            }
        } finally {
            if (!temporaryInstalled) runCatching { DocumentsContract.deleteDocument(resolver, temporary) }
        }
    }

    fun findFileUri(context: Context, treeUri: Uri, relativePath: String): Uri? {
        val resolver = context.contentResolver
        val parts = safeRelativePath(relativePath).split('/')
        var current = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        parts.forEachIndexed { index, name ->
            current = findChild(
                resolver,
                treeUri,
                current,
                name,
                directoryOnly = index < parts.lastIndex,
            ) ?: return null
        }
        return current
    }

    fun deleteSafely(context: Context, treeUri: Uri, file: EspansoSourceFile) {
        val resolver = context.contentResolver
        val uri = findFileUri(context, treeUri, file.relativePath)
            ?: throw SourceConflictException("'${file.relativePath}' was already removed. Sync and try again.")
        if (readText(resolver, uri, file.relativePath) != file.content) {
            throw SourceConflictException("'${file.relativePath}' changed outside Expanda. Sync before deleting.")
        }
        val hiddenName = ".${file.relativePath.substringAfterLast('/')}.expanda-deleted-${System.nanoTime().toString(36)}"
        val hidden = renameDocument(resolver, uri, hiddenName)
            ?: throw IllegalStateException("The storage provider cannot safely delete '${file.relativePath}'")
        if (!DocumentsContract.deleteDocument(resolver, hidden)) {
            throw IllegalStateException("Could not remove '${file.relativePath}'")
        }
    }

    private fun readDirectory(
        resolver: ContentResolver,
        treeUri: Uri,
        directory: Uri,
        prefix: String,
        destination: MutableList<EspansoSourceFile>,
    ) {
        children(resolver, treeUri, directory).forEach { child ->
            val relative = if (prefix.isBlank()) child.name else "$prefix/${child.name}"
            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                readDirectory(resolver, treeUri, child.uri, relative, destination)
            } else if (child.name.endsWith(".yml", true) || child.name.endsWith(".yaml", true)) {
                val content = resolver.openInputStream(child.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("Could not read '$relative'")
                destination += EspansoSourceFile(relative, content)
            }
        }
    }

    private data class Child(val name: String, val mimeType: String, val uri: Uri)

    private fun children(resolver: ContentResolver, treeUri: Uri, directory: Uri): List<Child> {
        val documentId = DocumentsContract.getDocumentId(directory)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            OpenableColumns.DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)
                    val name = cursor.getString(nameColumn).orEmpty()
                    val mime = cursor.getString(mimeColumn).orEmpty()
                    add(Child(name, mime, DocumentsContract.buildDocumentUriUsingTree(treeUri, id)))
                }
            }
        } ?: throw IllegalStateException("The selected folder is unavailable")
    }

    private fun readText(resolver: ContentResolver, uri: Uri, name: String): String =
        resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("Could not read '$name'")

    private fun writeText(resolver: ContentResolver, uri: Uri, text: String, name: String) {
        val stream = resolver.openOutputStream(uri, "wt") ?: resolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("Could not write '$name'")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    private fun renameDocument(resolver: ContentResolver, uri: Uri, name: String): Uri? =
        runCatching { DocumentsContract.renameDocument(resolver, uri, name) }.getOrNull()

    private fun findChild(
        resolver: ContentResolver,
        treeUri: Uri,
        directory: Uri,
        name: String,
        directoryOnly: Boolean,
    ): Uri? = children(resolver, treeUri, directory).firstOrNull { child ->
        child.name == name && (!directoryOnly || child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR)
    }?.uri

    internal fun safeRelativePath(value: String): String {
        val normalized = value.replace('\\', '/').trim('/')
        require(normalized.isNotBlank()) { "Source file needs a name" }
        require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Invalid source path"
        }
        require(normalized.endsWith(".yml", true) || normalized.endsWith(".yaml", true)) {
            "Espanso source files must end in .yml or .yaml"
        }
        return normalized
    }

    private const val YAML_MIME = "application/x-yaml"
}
