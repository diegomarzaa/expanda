package dev.diego.expanda.gitsync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class GitSyncConfig(
    val owner: String = "",
    val repo: String = "",
    /** Blank means the repository's default branch. */
    val branch: String = "",
    /** Empty means anonymous (public repositories only). */
    val token: String = "",
    /** The git host: `github.com`, or a Forgejo/Gitea server such as `codeberg.org`. */
    val host: String = GitSyncInput.GITHUB_HOST,
) {
    val hasToken: Boolean get() = token.isNotEmpty()

    val configured: Boolean
        get() = owner.isNotBlank() && repo.isNotBlank() && GitSyncInput.validateHost(host) == null &&
            (token.isEmpty() || GitSyncInput.validateToken(token, host) == null)

    /**
     * Identifies what the stored applied SHA belongs to. github.com keys keep their pre-multi-host form, so
     * existing setups stay applied; any other host is part of the key, so changing host counts as a repo change.
     */
    val repoKey: String
        get() = if (GitSyncInput.isGitHub(host)) "$owner/$repo@$branch" else "${host.lowercase()}:$owner/$repo@$branch"

    // Never include the token in toString(): configs can end up in logs by accident.
    override fun toString(): String =
        "GitSyncConfig(host=$host, owner=$owner, repo=$repo, branch=$branch, token=<redacted>)"
}

data class GitSyncStatus(
    val lastSyncAt: Long? = null,
    val lastOk: Boolean = true,
    val lastMessage: String? = null,
    /** Last time we contacted the git host for any reason (synced, unchanged or failed). */
    val lastCheckedAt: Long? = null,
    /** True when the most recent check found nothing new (lastSyncAt/lastMessage describe the older sync). */
    val unchanged: Boolean = false,
    /** True when a background sync stopped because nothing was ever applied from this repository. */
    val waitingForFirstSync: Boolean = false,
    val issues: List<StoredIssue> = emptyList(),
    val issueCount: Int = 0,
)

/** What [GitSyncer] needs from persistent state; [GitSyncPrefs] is the production implementation. */
interface GitSyncStore {
    fun loadConfig(): GitSyncConfig
    fun loadApplied(): AppliedState
    fun saveApplied(matches: Int, issues: List<StoredIssue>, applied: AppliedState, at: Long = System.currentTimeMillis())
    fun saveFailure(message: String, at: Long = System.currentTimeMillis())
    fun saveUnchanged(etag: String?, at: Long = System.currentTimeMillis())
    fun saveWaitingForFirstSync(at: Long = System.currentTimeMillis())
}

/** Own SharedPreferences file so nothing here touches (or gets exported with) upstream's settings. */
class GitSyncPrefs(context: Context) : GitSyncStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The token lives in no-backup app-private storage, never in shared prefs. */
    private val tokenFile = TokenFile(File(context.applicationContext.noBackupFilesDir, TOKEN_FILE))

    private val tokens = HostBoundToken(
        tokenFile,
        readHost = { prefs.getString(KEY_TOKEN_HOST, null) },
        writeHost = { host -> prefs.edit { if (host == null) remove(KEY_TOKEN_HOST) else putString(KEY_TOKEN_HOST, host) } },
    )

    override fun loadConfig(): GitSyncConfig {
        migrateLegacyToken()
        val host = storedHost()
        return GitSyncConfig(
            owner = prefs.getString(KEY_OWNER, null).orEmpty(),
            repo = prefs.getString(KEY_REPO, null).orEmpty(),
            branch = prefs.getString(KEY_BRANCH, null).orEmpty(),
            token = tokens.read(host),
            host = host,
        )
    }

    private fun storedHost(): String = prefs.getString(KEY_HOST, null)?.takeIf { it.isNotBlank() } ?: GitSyncInput.GITHUB_HOST

    /** Older versions stored the token in preferences: move it to the file once, then delete it from prefs. */
    private fun migrateLegacyToken() {
        if (!prefs.contains(KEY_TOKEN)) return
        try {
            TokenFile.migrate(prefs.getString(KEY_TOKEN, null), tokenFile)
            prefs.edit { remove(KEY_TOKEN) }
        } catch (e: Exception) {
            // Keep the legacy value and retry on the next load rather than losing the token.
        }
    }

    /** Changing repository or branch forgets what was applied, so the next apply is a "first" apply again. */
    fun saveConfig(config: GitSyncConfig) {
        val previous = loadConfig()
        val trimmed = config.copy(
            owner = config.owner.trim(), repo = config.repo.trim(),
            branch = config.branch.trim(), token = config.token.trim(),
            host = config.host.trim().lowercase().ifEmpty { GitSyncInput.GITHUB_HOST },
        )
        tokens.save(trimmed.token, trimmed.host)
        prefs.edit {
            putString(KEY_OWNER, trimmed.owner)
            putString(KEY_REPO, trimmed.repo)
            putString(KEY_BRANCH, trimmed.branch)
            putString(KEY_HOST, trimmed.host)
            if (previous.repoKey != trimmed.repoKey) {
                remove(KEY_SHA)
                remove(KEY_ETAG)
                remove(KEY_APPLIED_FOR)
                remove(KEY_WAITING)
            }
            if (previous.repoKey != trimmed.repoKey || !trimmed.configured) {
                // The old result belongs to a repository that is no longer configured: don't leave it showing.
                remove(KEY_LAST_AT)
                remove(KEY_CHECKED_AT)
                remove(KEY_LAST_OK)
                remove(KEY_LAST_MESSAGE)
                remove(KEY_UNCHANGED)
                remove(KEY_ISSUES)
                remove(KEY_ISSUE_COUNT)
            }
        }
    }

    fun clearToken() {
        tokens.clear()
        prefs.edit { remove(KEY_TOKEN) }
    }

    /** Removes everything this feature stored (used by "Reset Expanda"). */
    fun clearAll() {
        tokens.clear()
        prefs.edit { clear() }
    }

    fun loadStatus(): GitSyncStatus = GitSyncStatus(
        lastSyncAt = if (prefs.contains(KEY_LAST_AT)) prefs.getLong(KEY_LAST_AT, 0L) else null,
        lastOk = prefs.getBoolean(KEY_LAST_OK, true),
        lastMessage = prefs.getString(KEY_LAST_MESSAGE, null),
        lastCheckedAt = if (prefs.contains(KEY_CHECKED_AT)) prefs.getLong(KEY_CHECKED_AT, 0L)
        else if (prefs.contains(KEY_LAST_AT)) prefs.getLong(KEY_LAST_AT, 0L) else null,
        unchanged = prefs.getBoolean(KEY_UNCHANGED, false),
        waitingForFirstSync = prefs.getBoolean(KEY_WAITING, false),
        issues = IssueCodec.decode(prefs.getString(KEY_ISSUES, null)),
        issueCount = prefs.getInt(KEY_ISSUE_COUNT, 0),
    )

    /** Marker for the commit check: SHA (and its ETag) of the last successfully applied sync. */
    override fun loadApplied(): AppliedState =
        AppliedState(prefs.getString(KEY_SHA, null), prefs.getString(KEY_ETAG, null), prefs.getString(KEY_APPLIED_FOR, null))

    /** Failure: previous applied SHA/ETag and issues are kept so a later check can still short-circuit. */
    override fun saveFailure(message: String, at: Long) {
        prefs.edit {
            putLong(KEY_LAST_AT, at)
            putLong(KEY_CHECKED_AT, at)
            putBoolean(KEY_LAST_OK, false)
            putBoolean(KEY_UNCHANGED, false)
            putBoolean(KEY_WAITING, false)
            putString(KEY_LAST_MESSAGE, message)
        }
    }

    /** Success: SHA/ETag are stored only here, i.e. only after the files were applied. */
    override fun saveApplied(matches: Int, issues: List<StoredIssue>, applied: AppliedState, at: Long) {
        prefs.edit {
            putLong(KEY_LAST_AT, at)
            putLong(KEY_CHECKED_AT, at)
            putBoolean(KEY_LAST_OK, true)
            putBoolean(KEY_UNCHANGED, false)
            putBoolean(KEY_WAITING, false)
            putString(KEY_LAST_MESSAGE, "$matches matches, ${issues.size} issues")
            putString(KEY_ISSUES, IssueCodec.encode(issues))
            putInt(KEY_ISSUE_COUNT, issues.size)
            if (applied.sha != null) putString(KEY_SHA, applied.sha) else remove(KEY_SHA)
            if (applied.sha != null && applied.etag != null) putString(KEY_ETAG, applied.etag) else remove(KEY_ETAG)
            if (applied.sha != null && applied.repoKey != null) putString(KEY_APPLIED_FOR, applied.repoKey) else remove(KEY_APPLIED_FOR)
        }
    }

    /** Nothing new: keep previous match/issue info, only refresh the last-checked time. */
    override fun saveUnchanged(etag: String?, at: Long) {
        prefs.edit {
            if (etag != null && prefs.getString(KEY_SHA, null) != null) putString(KEY_ETAG, etag)
            putLong(KEY_CHECKED_AT, at)
            putBoolean(KEY_UNCHANGED, true)
            putBoolean(KEY_LAST_OK, true)
            putBoolean(KEY_WAITING, false)
        }
    }

    override fun saveWaitingForFirstSync(at: Long) {
        prefs.edit {
            putLong(KEY_CHECKED_AT, at)
            putBoolean(KEY_WAITING, true)
            putBoolean(KEY_UNCHANGED, false)
            putBoolean(KEY_LAST_OK, true)
        }
    }

    /** Whether a periodic job was enqueued by this feature; lets "off" skip touching WorkManager at all. */
    fun isScheduled(): Boolean = prefs.getBoolean(KEY_SCHEDULED, false)

    fun setScheduled(scheduled: Boolean) {
        prefs.edit { putBoolean(KEY_SCHEDULED, scheduled) }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val FILE = "github_sync"
        const val KEY_OWNER = "owner"
        const val KEY_REPO = "repo"
        const val KEY_BRANCH = "branch"
        const val KEY_HOST = "host" // absent in older builds: github.com
        const val KEY_TOKEN_HOST = "token_host" // the host the stored token was entered for; absent: github.com
        const val KEY_TOKEN = "token" // legacy: only read to migrate and delete
        const val TOKEN_FILE = "github-sync-token"
        const val KEY_LAST_AT = "last_sync_at"
        const val KEY_LAST_OK = "last_sync_ok"
        const val KEY_LAST_MESSAGE = "last_sync_message"
        const val KEY_CHECKED_AT = "last_checked_at"
        const val KEY_UNCHANGED = "last_unchanged"
        const val KEY_WAITING = "waiting_first_sync"
        const val KEY_SHA = "applied_sha"
        const val KEY_ETAG = "applied_etag"
        const val KEY_APPLIED_FOR = "applied_for"
        const val KEY_ISSUES = "issues_json"
        const val KEY_ISSUE_COUNT = "issue_count"
        const val KEY_SCHEDULED = "scheduled"
    }
}

/** Everything the feature keeps on the device, for "Reset Expanda". */
object GitSyncData {
    /** Waits for a running sync to finish (so it cannot write state afterwards), then wipes everything. */
    suspend fun clearAll(context: Context) {
        GitSyncController.dismissReplace()
        withContext(Dispatchers.IO) {
            GitSyncer.exclusive {
                GitSyncScheduler.cancel(context)
                GitSyncPrefs(context).clearAll()
                SyncBackups.directory(context).deleteRecursively()
            }
        }
        GitSyncController.dismissReplace()
    }
}

/**
 * The token file plus the host it was entered for, so a token can never be sent to a different host.
 * [readHost]/[writeHost] persist the binding; a missing binding means github.com (tokens stored before it existed).
 */
class HostBoundToken(
    private val file: TokenFile,
    private val readHost: () -> String?,
    private val writeHost: (String?) -> Unit,
) {
    private fun boundHost(): String = readHost()?.takeIf { it.isNotBlank() } ?: GitSyncInput.GITHUB_HOST

    /** The stored token, or empty when it was entered for another host than [host]. */
    fun read(host: String): String {
        val token = file.read()
        return if (token.isNotEmpty() && boundHost().equals(host, ignoreCase = true)) token else ""
    }

    /**
     * Stores [token] for [host]. The same token arriving for a different host than it was entered for is
     * dropped, not carried over.
     */
    fun save(token: String, host: String) {
        val stored = file.read()
        if (token.isEmpty() || (token == stored && !boundHost().equals(host, ignoreCase = true))) {
            clear()
        } else {
            file.write(token)
            writeHost(host)
        }
    }

    fun clear() {
        file.delete()
        writeHost(null)
    }
}

/** The access token as a single app-private file (pass the no-backup directory); written atomically. */
class TokenFile(private val file: File) {
    fun read(): String = try {
        if (file.isFile) file.readText().trim() else ""
    } catch (e: java.io.IOException) {
        ""
    }

    fun write(token: String) {
        val dir = file.parentFile
        check(dir == null || dir.isDirectory || dir.mkdirs()) { "Could not create the token folder" }
        val temp = File(file.path + ".tmp")
        temp.writeText(token)
        temp.setReadable(false, false)
        temp.setReadable(true, true)
        check(temp.renameTo(file)) { "Could not store the token" }
    }

    fun delete() {
        file.delete()
        File(file.path + ".tmp").delete()
    }

    companion object {
        /** Moves a token found in the old prefs location into [target] unless it already has one. */
        fun migrate(legacy: String?, target: TokenFile) {
            val value = legacy?.trim().orEmpty()
            if (value.isNotEmpty() && target.read().isEmpty()) target.write(value)
        }
    }
}

/** Pre-replace backups, kept in app-private storage; only the newest [keep] are retained. */
class SyncBackups(private val dir: File, private val keep: Int = KEEP, private val clock: () -> Long = System::currentTimeMillis) {
    fun save(json: String): File {
        check(dir.isDirectory || dir.mkdirs()) { "Could not create the backup folder" }
        val name = java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(clock()))
        val target = File(dir, "$name.json")
        val temp = File(dir, "$name.json.tmp")
        temp.writeText(json)
        check(temp.renameTo(target)) { "Could not write the backup" }
        files().drop(keep).forEach { it.delete() }
        return target
    }

    /** The most recent backup, or null. */
    fun newest(): File? = files().firstOrNull()

    /** Newest first. */
    fun files(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedByDescending { it.name }

    companion object {
        const val KEEP = 3
        fun directory(context: Context) = File(context.filesDir, "github-sync-backups")
    }
}
