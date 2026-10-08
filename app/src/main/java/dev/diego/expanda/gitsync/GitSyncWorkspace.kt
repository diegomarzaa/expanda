package dev.diego.expanda.gitsync

import dev.diego.expanda.ExpandaApplication
import dev.diego.expanda.data.BackupCodec
import dev.diego.expanda.data.EspansoSourceFile
import dev.diego.expanda.data.EspansoSourceImportResult
import dev.diego.expanda.data.EspansoSourceRepository
import dev.diego.expanda.data.MatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Production [SyncWorkspace]: the app's source repository, with backups in app-private storage. */
class RepositorySyncWorkspace(
    private val repository: EspansoSourceRepository,
    private val matches: MatchRepository,
    private val backupJson: () -> String,
    private val backups: SyncBackups,
) : SyncWorkspace {
    override fun isLinked() = repository.workspace.value.linked
    override suspend fun awaitReady() {
        // initialize() always creates base.yml, so an empty file list means loading has not finished.
        try {
            withTimeout(READY_TIMEOUT_MS) { repository.files.first { it.isNotEmpty() } }
        } catch (e: TimeoutCancellationException) {
            throw GitSyncException("Expanda is still loading your snippets. Try again in a moment.", cause = e)
        }
    }

    override fun localFileCount() = repository.files.value.size
    override fun localMatchCount() = matches.matches.value.size
    override fun decodableMatchCount(files: List<EspansoSourceFile>) = repository.inspectFiles(files).matches.size

    override suspend fun backup() {
        withContext(Dispatchers.IO) { backups.save(backupJson()) }
    }

    override suspend fun replaceAll(files: List<EspansoSourceFile>): EspansoSourceImportResult =
        repository.importFiles(files, replaceAll = true, requireUnlinked = true)

    private companion object {
        const val READY_TIMEOUT_MS = 10_000L
    }
}

fun createGitSyncer(app: ExpandaApplication, store: GitSyncStore = GitSyncPrefs(app)): GitSyncer =
    GitSyncer(
        RepositorySyncWorkspace(
            app.espansoSourceRepository,
            app.matchRepository,
            backupJson = {
                // Same content as Settings > Expanda backup > Export.
                BackupCodec.encode(
                    app.matchRepository.matches.value,
                    app.settingsRepository.settings.value,
                    BackupCodec.ActionSnapshot(
                        enabledIds = app.actionSettingsStore.enabledIds.value,
                        shortcutOverrides = app.actionSettingsStore.shortcutOverrides.value,
                    ),
                    app.espansoSourceRepository.files.value,
                )
            },
            SyncBackups(SyncBackups.directory(app)),
        ),
        store,
    )
