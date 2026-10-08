package dev.diego.expanda.gitsync

import dev.diego.expanda.data.EspansoSourceFile
import dev.diego.expanda.data.EspansoSourceImportResult
import dev.diego.expanda.data.SourceFolderLinkedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Downloads match files from the configured git host. Pure network + mapping; does not touch the workspace. */
class GitSyncFetcher(private val http: HttpGet = HttpUrlConnectionGet(), private val hosts: (String, HttpGet) -> GitHost = GitHosts::forHost) {
    private fun requireValid(config: GitSyncConfig) {
        GitSyncInput.validateCoordinates(config.host, config.owner, config.repo, config.branch)
            ?.let { throw GitSyncException(it) }
    }

    /** Downloads the match files of commit [sha] (from [checkBranch]); tree and contents are pinned to it. */
    fun fetch(config: GitSyncConfig, sha: String): List<EspansoSourceFile> {
        requireValid(config)
        val host = hosts(config.host, http)
        val selected = GitHubApi.selectFiles(host.listBlobPaths(config, sha))
        if (selected.isEmpty()) {
            throw GitSyncException("No .yml files found under match/ in the repository. Nothing was changed.")
        }
        if (selected.size > GitHubApi.MAX_FILES) {
            throw GitSyncException(
                "Too many files under match/ (${selected.size}); at most ${GitHubApi.MAX_FILES} can be synced. Nothing was changed.",
            )
        }
        var total = 0L
        return selected.map { (relative, repoPath) ->
            val content = host.fetchFile(config, sha, repoPath)
            total += content.toByteArray(Charsets.UTF_8).size
            if (total > GitHubApi.MAX_TOTAL_BYTES) {
                throw GitSyncException(
                    "The files under match/ are larger than ${GitHubApi.MAX_TOTAL_BYTES shr 20} MiB in total. Nothing was changed.",
                )
            }
            EspansoSourceFile(relative, content)
        }
    }

    /**
     * Cheap "has the branch moved?" probe: one tiny request, 304 when the ETag still matches (where the host
     * supports it). With [force] the stored ETag/SHA are ignored (always [BranchCheck.Changed]). Either way the
     * probe must succeed, because its SHA pins the download.
     */
    fun checkBranch(config: GitSyncConfig, applied: AppliedState, force: Boolean): BranchCheck {
        requireValid(config)
        return hosts(config.host, http).checkBranch(config, applied, force)
    }
}

/** The two repository calls (plus counts and backup) the syncer needs; seam for tests. */
interface SyncWorkspace {
    fun isLinked(): Boolean

    /** Suspends until the workspace has finished loading; throws [GitSyncException] if it never does. */
    suspend fun awaitReady()

    fun localFileCount(): Int
    fun localMatchCount(): Int

    /** How many snippets [files] would yield once decoded. */
    fun decodableMatchCount(files: List<EspansoSourceFile>): Int

    /** Writes a restorable backup of the current workspace to app-private storage. */
    suspend fun backup()

    /**
     * Replaces the whole workspace with [files]. Must throw [SourceFolderLinkedException] when a local
     * folder is linked, checked atomically with the write.
     */
    suspend fun replaceAll(files: List<EspansoSourceFile>): EspansoSourceImportResult
}

/** A first apply that is waiting for the user's confirmation (the card shows the counts). */
class PendingReplace internal constructor(
    val localFiles: Int,
    val remoteFiles: Int,
    internal val files: List<EspansoSourceFile>,
    internal val check: BranchCheck.Changed,
    internal val repoKey: String,
)

sealed interface SyncOutcome {
    data object Unchanged : SyncOutcome
    class Applied(val result: EspansoSourceImportResult) : SyncOutcome

    /** Background sync stopped because nothing was ever applied from this repository. */
    data object WaitingForFirstSync : SyncOutcome

    /** Manual first apply: call [GitSyncer.applyConfirmed] once the user agrees. */
    class NeedsConfirmation(val pending: PendingReplace) : SyncOutcome
}

/** Fetches then applies with replaceAll; any failure before the apply leaves the workspace untouched. */
class GitSyncer(
    private val workspace: SyncWorkspace,
    private val store: GitSyncStore,
    private val fetcher: GitSyncFetcher = GitSyncFetcher(),
) {
    /**
     * [force] = full download regardless of the commit check ("Sync now"). [manual] = started by the user;
     * only manual syncs may do the first apply, and that one needs [applyConfirmed].
     * Cancellation is rethrown, never recorded as a failure.
     */
    suspend fun sync(force: Boolean = false, manual: Boolean = false): Result<SyncOutcome> = withContext(Dispatchers.IO) {
        gate.withLock {
            record {
                val config = requireUsable(store.loadConfig())
                workspace.awaitReady()
                val applied = store.loadApplied()
                val firstApply = applied.sha == null || applied.repoKey != config.repoKey
                if (firstApply && !manual) {
                    store.saveWaitingForFirstSync()
                    return@record SyncOutcome.WaitingForFirstSync
                }
                val check = fetcher.checkBranch(config, applied, force || firstApply)
                if (check is BranchCheck.Unchanged) {
                    store.saveUnchanged(check.etag)
                    return@record SyncOutcome.Unchanged
                }
                check as BranchCheck.Changed
                val files = fetcher.fetch(config, check.sha)
                requireDecodable(files)
                if (firstApply) {
                    SyncOutcome.NeedsConfirmation(
                        PendingReplace(workspace.localFileCount(), files.size, files, check, config.repoKey),
                    )
                } else {
                    apply(files, check, config.repoKey, backup = false)
                }
            }
        }
    }

    /** Backs up, then applies a first sync the user confirmed. */
    suspend fun applyConfirmed(pending: PendingReplace): Result<SyncOutcome> = withContext(Dispatchers.IO) {
        gate.withLock {
            record {
                val config = requireUsable(store.loadConfig())
                workspace.awaitReady()
                if (config.repoKey != pending.repoKey) {
                    throw GitSyncException("The repository settings changed while waiting. Run Sync now again.")
                }
                requireDecodable(pending.files)
                apply(pending.files, pending.check, pending.repoKey, backup = true)
            }
        }
    }

    private fun requireUsable(config: GitSyncConfig): GitSyncConfig {
        if (!config.configured) throw GitSyncException("Git sync is not configured (a repository is required, and the token must be valid if one is set).")
        if (workspace.isLinked()) throw GitSyncException(LINKED_MESSAGE)
        return config
    }

    /** Never replace a populated workspace with files that yielded no snippets at all. */
    private fun requireDecodable(files: List<EspansoSourceFile>) {
        val local = workspace.localMatchCount()
        if (local > 0 && workspace.decodableMatchCount(files) == 0) {
            throw GitSyncException(
                "None of the ${files.size} files from the repository contained snippets Expanda could read, " +
                    "so your $local local snippets were not replaced.",
            )
        }
    }

    private suspend fun apply(
        files: List<EspansoSourceFile>,
        check: BranchCheck.Changed,
        repoKey: String,
        backup: Boolean,
    ): SyncOutcome {
        if (backup) {
            try {
                workspace.backup()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw GitSyncException("Could not export a backup first (${e.javaClass.simpleName}). Nothing was changed.", cause = e)
            }
        }
        val result = try {
            workspace.replaceAll(files)
        } catch (e: SourceFolderLinkedException) {
            throw GitSyncException(LINKED_MESSAGE, cause = e)
        }
        store.saveApplied(
            result.matches,
            IssueCodec.fromCompat(result.issues).take(IssueCodec.MAX_ISSUES),
            AppliedState(check.sha, check.etag, repoKey),
        )
        return SyncOutcome.Applied(result)
    }

    /** Runs [block], recording failures; cancellation passes through untouched. */
    private inline fun record(block: () -> SyncOutcome): Result<SyncOutcome> {
        val outcome = try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only our own exceptions carry text that is safe to persist; others contribute their class name.
            Result.failure(
                if (e is GitSyncException) e
                else GitSyncException("Could not apply files (${e.javaClass.simpleName}).", cause = e),
            )
        }
        outcome.onFailure { store.saveFailure(it.message ?: "Sync failed") }
        return outcome
    }

    companion object {
        const val LINKED_MESSAGE = "A local folder is linked. Unlink it to use Git sync."

        /** Worker, app-open and "Sync now" must not run concurrently. */
        private val gate = Mutex()

        /** Runs [block] while no sync can run (e.g. reset, restore); waits for a running sync to finish first. */
        suspend fun <T> exclusive(block: suspend () -> T): T = gate.withLock { block() }
    }
}
