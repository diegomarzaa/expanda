package dev.diego.expanda.gitsync

import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

/**
 * What the syncer needs from a git host, and nothing more. Every implementation must keep these rules:
 * HTTPS only, requests go only to the configured host, the token is sent only to that host (and only when
 * set), redirects are never followed (see [HttpGet]), responses are size capped, and no text that reaches the
 * user or the logs contains the token.
 */
interface GitHost {
    /** Host name as stored in the config, e.g. `github.com` or `codeberg.org`. */
    val host: String

    /** Short name for messages, e.g. "GitHub" or "codeberg.org". */
    val label: String

    /** Message for a bad non-empty [token], or null. */
    fun validateToken(token: String): String?

    /**
     * Cheap "has the branch moved?" probe. With [force] the stored ETag/SHA are ignored (always
     * [BranchCheck.Changed]). Either way the probe must succeed, because its SHA pins the download.
     */
    fun checkBranch(config: GitSyncConfig, applied: AppliedState, force: Boolean): BranchCheck

    /** Paths of the regular files (blobs, no symlinks) in the tree of commit [sha]; fails when the listing is incomplete. */
    fun listBlobPaths(config: GitSyncConfig, sha: String): List<String>

    /** Raw content of [path] at commit [sha]. */
    fun fetchFile(config: GitSyncConfig, sha: String, path: String): String

    /** Maps a failed HTTP status to a message safe to show. [authenticated] is whether a token was sent. */
    fun errorFor(status: Int, rateLimitRemaining: String? = null, authenticated: Boolean = true): GitSyncException
}

object GitHosts {
    fun forHost(host: String, http: HttpGet): GitHost =
        if (GitSyncInput.isGitHub(host)) GitHubHost(http) else ForgejoHost(host.lowercase(), http)
}

internal const val MAX_SHA_BYTES = 4096
private val SHA = Regex("^[0-9a-f]{40}(?:[0-9a-f]{24})?$")

internal fun isSha(text: String) = SHA.matches(text)

internal fun encodeSegment(segment: String): String = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
internal fun encodePath(path: String): String = path.split('/').joinToString("/", transform = ::encodeSegment)

/** GET [url], failing with the host's own error mapping unless the status is 2xx. */
private fun GitHost.getOk(http: HttpGet, url: String, headers: Map<String, String>, maxBytes: Int, authenticated: Boolean): String {
    val result = http.get(url, headers, maxBytes)
    if (result.status !in 200..299) throw errorFor(result.status, result.rateLimitRemaining, authenticated)
    return result.body
}

/** api.github.com. Anonymous reads of public repositories work without a token (60 requests/hour per IP). */
class GitHubHost(private val http: HttpGet = HttpUrlConnectionGet()) : GitHost {
    override val host = GitSyncInput.GITHUB_HOST
    override val label = "GitHub"

    override fun validateToken(token: String) = GitSyncInput.validateToken(token, host)

    override fun checkBranch(config: GitSyncConfig, applied: AppliedState, force: Boolean): BranchCheck {
        val url = GitHubApi.commitUrl(config.owner, config.repo, config.branch)
        val extra = if (!force && applied.etag != null && applied.sha != null) mapOf("If-None-Match" to applied.etag) else emptyMap()
        val result = http.get(url, headers(config, "application/vnd.github.sha") + extra, MAX_SHA_BYTES)
        if (result.status == 304 && !force) return BranchCheck.Unchanged()
        if (result.status !in 200..299) throw errorFor(result.status, result.rateLimitRemaining, config.hasToken)
        val sha = result.body.trim()
        if (!isSha(sha)) throw GitSyncException("GitHub returned an unexpected commit response.")
        if (!force && sha == applied.sha) return BranchCheck.Unchanged(result.etag)
        return BranchCheck.Changed(sha, result.etag)
    }

    override fun listBlobPaths(config: GitSyncConfig, sha: String): List<String> {
        val tree = GitHubApi.parseTree(
            getOk(
                http, GitHubApi.treeUrl(config.owner, config.repo, sha),
                headers(config, "application/vnd.github+json"), GitHubApi.MAX_TREE_BYTES, config.hasToken,
            ),
        )
        if (tree.truncated) {
            throw GitSyncException("The repository tree is too large for GitHub to list in one response (truncated).")
        }
        return GitHubApi.blobPaths(tree.entries)
    }

    override fun fetchFile(config: GitSyncConfig, sha: String, path: String): String =
        getOk(
            http, GitHubApi.contentsUrl(config.owner, config.repo, sha, path),
            headers(config, "application/vnd.github.raw"), GitHubApi.MAX_FILE_BYTES, config.hasToken,
        )

    override fun errorFor(status: Int, rateLimitRemaining: String?, authenticated: Boolean) =
        GitHubApi.errorFor(status, rateLimitRemaining, authenticated)

    private fun headers(config: GitSyncConfig, accept: String): Map<String, String> {
        val map = linkedMapOf("Accept" to accept, "X-GitHub-Api-Version" to "2022-11-28", "User-Agent" to "Expanda-GitSync")
        if (config.hasToken) map["Authorization"] = "Bearer ${config.token}"
        return map
    }
}

/**
 * Forgejo / Gitea (e.g. Codeberg) API v1 at `https://<host>/api/v1`. Open source and self-hostable.
 * Public repositories can be read without a token.
 */
class ForgejoHost(override val host: String, private val http: HttpGet = HttpUrlConnectionGet()) : GitHost {
    override val label = host

    private val apiRoot = "https://$host/api/v1"

    override fun validateToken(token: String) = GitSyncInput.validateToken(token, host)

    private fun repoUrl(config: GitSyncConfig) = "$apiRoot/repos/${encodeSegment(config.owner)}/${encodeSegment(config.repo)}"

    fun branchUrl(config: GitSyncConfig, branch: String) = "${repoUrl(config)}/branches/${encodePath(branch)}"

    fun treeUrl(config: GitSyncConfig, sha: String, page: Int) =
        "${repoUrl(config)}/git/trees/${encodeSegment(sha)}?recursive=true&per_page=$TREE_PER_PAGE&page=$page"

    /** `media` serves the real bytes for LFS files and the plain content otherwise. */
    fun fileUrl(config: GitSyncConfig, sha: String, path: String) =
        "${repoUrl(config)}/media/${encodePath(path)}?ref=${URLEncoder.encode(sha, "UTF-8")}"

    override fun checkBranch(config: GitSyncConfig, applied: AppliedState, force: Boolean): BranchCheck {
        val branch = config.branch.ifEmpty { defaultBranch(config) }
        val extra = if (!force && applied.etag != null && applied.sha != null) mapOf("If-None-Match" to applied.etag) else emptyMap()
        val result = http.get(branchUrl(config, branch), headers(config) + extra, MAX_JSON_BYTES)
        if (result.status == 304 && !force) return BranchCheck.Unchanged()
        if (result.status !in 200..299) throw errorFor(result.status, result.rateLimitRemaining, config.hasToken)
        val sha = try {
            JSONObject(result.body).getJSONObject("commit").getString("id")
        } catch (e: JSONException) {
            throw GitSyncException("$label returned an unexpected branch response.", cause = e)
        }
        if (!isSha(sha)) throw GitSyncException("$label returned an unexpected commit response.")
        if (!force && sha == applied.sha) return BranchCheck.Unchanged(result.etag)
        return BranchCheck.Changed(sha, result.etag)
    }

    /** The repository's default branch name (one extra request, only when no branch is configured). */
    private fun defaultBranch(config: GitSyncConfig): String {
        val body = getOk(http, repoUrl(config), headers(config), MAX_JSON_BYTES, config.hasToken)
        val name = try {
            JSONObject(body).getString("default_branch")
        } catch (e: JSONException) {
            throw GitSyncException("$label returned an unexpected repository response.", cause = e)
        }
        if (GitSyncInput.validateBranch(name) != null) throw GitSyncException("$label returned an unexpected default branch.")
        return name
    }

    override fun listBlobPaths(config: GitSyncConfig, sha: String): List<String> {
        val entries = ArrayList<GitTreeEntry>()
        var page = 1
        while (true) {
            val body = getOk(http, treeUrl(config, sha, page), headers(config), GitHubApi.MAX_TREE_BYTES, config.hasToken)
            val (batch, more) = parseTreePage(body)
            entries += batch
            if (entries.size > MAX_TREE_ENTRIES) throw tooLarge()
            if (!more) return GitHubApi.blobPaths(entries)
            // A page that claims "more" but is empty would loop forever; treat it as an incomplete listing.
            if (batch.isEmpty() || page >= MAX_TREE_PAGES) throw tooLarge()
            page++
        }
    }

    private fun tooLarge() = GitSyncException("The repository is too large to list (more than $MAX_TREE_ENTRIES entries).")

    /** Entries of one page, and whether another page follows (`truncated`). */
    internal fun parseTreePage(json: String): Pair<List<GitTreeEntry>, Boolean> {
        try {
            val root = JSONObject(json)
            val array = root.optJSONArray("tree") ?: throw GitSyncException("$label returned an unexpected tree response.")
            val entries = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                GitTreeEntry(item.optString("path"), item.optString("type"), item.optString("mode"))
            }
            return entries to root.optBoolean("truncated", false)
        } catch (e: JSONException) {
            throw GitSyncException("$label returned an unreadable tree response.", cause = e)
        }
    }

    override fun fetchFile(config: GitSyncConfig, sha: String, path: String): String =
        getOk(http, fileUrl(config, sha, path), headers(config), GitHubApi.MAX_FILE_BYTES, config.hasToken)

    override fun errorFor(status: Int, rateLimitRemaining: String?, authenticated: Boolean) =
        httpError(
            label, status, rateLimitRemaining, authenticated,
            forbiddenHint = "token lacks permission (needs read access to the repository) or access is forbidden",
        )

    private fun headers(config: GitSyncConfig): Map<String, String> {
        val map = linkedMapOf("Accept" to "application/json", "User-Agent" to "Expanda-GitSync")
        if (config.hasToken) map["Authorization"] = "token ${config.token}"
        return map
    }

    companion object {
        const val MAX_JSON_BYTES = 256 * 1024
        const val TREE_PER_PAGE = 1000
        const val MAX_TREE_PAGES = 20
        const val MAX_TREE_ENTRIES = 20_000
    }
}
