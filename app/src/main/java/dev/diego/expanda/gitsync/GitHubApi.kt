package dev.diego.expanda.gitsync

import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

/** A sync failure with a message safe to show to the user. Never contains the token. */
class GitSyncException(message: String, val status: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

class GitTreeEntry(val path: String, val type: String, val mode: String = "")

class GitTree(val entries: List<GitTreeEntry>, val truncated: Boolean)

class HttpResult(
    val status: Int,
    val body: String,
    val rateLimitRemaining: String? = null,
    val etag: String? = null,
)

/** Seam for tests; the production implementation is [HttpUrlConnectionGet]. */
fun interface HttpGet {
    fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResult
}

object GitSyncPaths {
    private const val PREFIX = "match/"
    private const val PACKAGES = "match/packages/"

    /** `match/foo/bar.yml` -> `foo/bar.yml`, or null when the path should not be synced. */
    fun mapToRelative(path: String): String? {
        if (!path.startsWith(PREFIX)) return null
        if (path.startsWith(PACKAGES)) return null
        val lower = path.lowercase()
        if (!lower.endsWith(".yml") && !lower.endsWith(".yaml")) return null
        val relative = path.removePrefix(PREFIX)
        if (relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        return relative
    }
}

/** Validation of user-entered coordinates. Each function returns an error message, or null when valid. */
object GitSyncInput {
    const val GITHUB_HOST = "github.com"

    private val OWNER = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
    private val REPO = Regex("^[A-Za-z0-9._-]{1,100}$")

    /** Forgejo/Gitea user and organization names may also contain '_' and '.'. */
    private val FORGEJO_OWNER = Regex("^[A-Za-z0-9_.-]{1,100}$")
    private val HOSTNAME = Regex("^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?(?::[0-9]{1,5})?$")
    private val BRANCH_FORBIDDEN = " ~^:?*[\\#%"
    private const val MAX_BRANCH = 255

    /** GitHub tokens (ghp_..., github_pat_...) and Forgejo tokens (40 hex) are letters, digits and underscores only. */
    private val TOKEN = Regex("^[A-Za-z0-9_]{20,255}$")
    private const val TOKEN_HELP =
        "Token must be 20-255 letters, digits or underscores (GitHub tokens start with github_pat_ or ghp_)."
    private const val FORGEJO_TOKEN_HELP = "Token must be 20-255 letters, digits or underscores."

    fun isGitHub(host: String) = host.equals(GITHUB_HOST, ignoreCase = true)

    /** Validates an already trimmed, non-empty token for [host]. An empty token (anonymous access) is not checked here. */
    fun validateToken(token: String, host: String = GITHUB_HOST): String? =
        if (TOKEN.matches(token)) null else if (isGitHub(host)) TOKEN_HELP else FORGEJO_TOKEN_HELP

    /** Null when [host] is a plain lowercase host name with an optional port (no user info, path or IP brackets). */
    fun validateHost(host: String): String? {
        if (!HOSTNAME.matches(host) || ".." in host) return "Host name is not valid."
        if (host.startsWith("$GITHUB_HOST:")) return "github.com is only available on its default port."
        val port = host.substringAfter(':', "")
        if (port.isNotEmpty() && port.toInt() !in 1..65535) return "Host port is not valid."
        return null
    }

    fun validateOwner(owner: String, host: String = GITHUB_HOST): String? = when {
        isGitHub(host) ->
            if (OWNER.matches(owner)) null else "Owner may only contain letters, digits and hyphens (max 39 characters)."
        owner != "." && owner != ".." && FORGEJO_OWNER.matches(owner) -> null
        else -> "Owner may only contain letters, digits, '.', '_' and '-'."
    }

    fun validateRepo(repo: String): String? =
        if (repo != "." && repo != ".." && REPO.matches(repo)) null
        else "Repository may only contain letters, digits, '.', '_' and '-'."

    fun validateBranch(branch: String): String? {
        val bad = branch.isEmpty() || branch.length > MAX_BRANCH || branch.startsWith("/") || branch.endsWith("/") ||
            branch.contains("..") || branch.contains("//") || branch.contains("@{") ||
            branch.any { it.code < 0x20 || it.code == 0x7f || it in BRANCH_FORBIDDEN } ||
            branch.split('/').any { it == "." || it.isEmpty() }
        return if (bad) "Branch name is not valid." else null
    }

    /** Validates everything in [config] that ends up in a request URL; the first problem, or null. */
    fun validateCoordinates(host: String, owner: String, repo: String, branch: String): String? =
        validateHost(host) ?: validateOwner(owner, host) ?: validateRepo(repo)
            ?: if (branch.isEmpty()) null else validateBranch(branch)

    /** Result of [parseRepositoryUrl]: host/owner/repo/branch, or an [error] message. */
    data class ParsedRepository(
        val owner: String,
        val repo: String,
        val branch: String,
        val error: String? = null,
        val host: String = GITHUB_HOST,
    ) {
        val valid: Boolean get() = error == null
    }

    const val URL_HELP =
        "Use https://github.com/owner/repo, owner/repo (GitHub) or https://host/owner/repo for Codeberg, " +
            "Forgejo or Gitea. Add /tree/<branch> (GitHub) or /src/branch/<branch> (Forgejo) for a branch."

    private fun invalid(message: String = URL_HELP) = ParsedRepository("", "", "", message)

    /**
     * Parses the single "Repository URL" field. Accepts `https://github.com/owner/repo`, optionally with
     * `.git`, a trailing slash or `/tree/<branch>` (the branch may contain slashes); the same without the
     * scheme; and plain `owner/repo` (GitHub). Any other host is a Forgejo/Gitea server:
     * `https://host[:port]/owner/repo[/src/branch/<branch>]`, or `host/owner/repo` without the scheme.
     * The branch is empty (the repository's default branch) unless given. `http://` is rejected.
     */
    fun parseRepositoryUrl(input: String): ParsedRepository {
        var text = input.trim()
        if (text.isEmpty()) return invalid("Enter the repository URL.")
        if (text.startsWith("http://", ignoreCase = true)) return invalid("Only https:// repository URLs are supported.")
        if (text.contains("://") && !text.startsWith("https://", ignoreCase = true)) return invalid()
        val hadScheme = text.startsWith("https://", ignoreCase = true)
        if (hadScheme) text = text.substring("https://".length)
        val hadQueryOrFragment = text.contains('#') || text.contains('?')
        text = text.substringBefore('#').substringBefore('?').trimEnd('/')
        val segments = text.split('/')
        if (segments.any { it.isEmpty() || it.any(Char::isWhitespace) }) return invalid()

        // Scheme-less input is "owner/repo[/tree/x]" (GitHub) unless it starts with something host-like and has a path.
        val first = segments[0].lowercase()
        val hostFirst = hadScheme || (segments.size >= 3 && ('.' in first || ':' in first))
        var host = if (!hostFirst) GITHUB_HOST else if (first == "www.github.com") GITHUB_HOST else first
        val rest = if (hostFirst) segments.drop(1) else segments
        if (host == "$GITHUB_HOST:443") host = GITHUB_HOST // the default HTTPS port is the same host
        if (hostFirst) validateHost(host)?.let { return invalid(it) }
        val github = isGitHub(host)
        if (rest.size < 2) return invalid()

        val owner = rest[0]
        val repo = rest[1].removeSuffix(".git")
        var branch = ""
        if (rest.size > 2) {
            val keyword = if (github) 1 else 2
            val marker = if (github) rest[2] == "tree" else rest[2] == "src" && rest.getOrNull(3) == "branch"
            if (!marker || rest.size < 3 + keyword) return invalid()
            branch = rest.drop(2 + keyword).joinToString("/")
        }
        if (branch.isNotEmpty() && hadQueryOrFragment) {
            return invalid("Branch names containing '#' or '?' are not supported.")
        }
        if ('%' in branch) {
            return invalid("Percent-encoded branch names (%20, %2F, ...) are not supported. Type the branch name without % escapes.")
        }
        val error = validateOwner(owner, host) ?: validateRepo(repo) ?: if (branch.isEmpty()) null else validateBranch(branch)
        return if (error != null) invalid(error) else ParsedRepository(owner, repo, branch, host = host)
    }

    /** Inverse of [parseRepositoryUrl] for display; the branch part only when a branch is set. */
    fun displayUrl(owner: String, repo: String, branch: String, host: String = GITHUB_HOST): String {
        if (owner.isBlank() && repo.isBlank()) return ""
        val base = "https://$host/$owner/$repo"
        return when {
            branch.isBlank() -> base
            isGitHub(host) -> "$base/tree/$branch"
            else -> "$base/src/branch/$branch"
        }
    }
}

object GitHubApi {
    const val MAX_FILE_BYTES = 1 shl 20
    const val MAX_TREE_BYTES = 5 shl 20
    const val MAX_FILES = 500

    /** Cap on the combined size of all downloaded files. */
    const val MAX_TOTAL_BYTES = 16 shl 20

    const val API_ROOT = "https://api.github.com"
    const val SYMLINK_MODE = "120000"

    /** [ref] is the commit SHA resolved by the branch check, so tree and contents describe one commit. */
    fun treeUrl(owner: String, repo: String, ref: String) =
        "$API_ROOT/repos/${encodeSegment(owner)}/${encodeSegment(repo)}/git/trees/${encodePath(ref)}?recursive=1"

    /** [branch] blank means the repository's default branch (GitHub resolves `HEAD`). */
    fun commitUrl(owner: String, repo: String, branch: String) =
        "$API_ROOT/repos/${encodeSegment(owner)}/${encodeSegment(repo)}/commits/${encodePath(branch.ifBlank { "HEAD" })}"

    fun contentsUrl(owner: String, repo: String, ref: String, path: String) =
        "$API_ROOT/repos/${encodeSegment(owner)}/${encodeSegment(repo)}/contents/${encodePath(path)}" +
            "?ref=${URLEncoder.encode(ref, "UTF-8")}"

    fun parseTree(json: String): GitTree {
        try {
            val root = JSONObject(json)
            val array = root.optJSONArray("tree") ?: throw GitSyncException("GitHub returned an unexpected tree response.")
            val entries = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                GitTreeEntry(item.optString("path"), item.optString("type"), item.optString("mode"))
            }
            return GitTree(entries, root.optBoolean("truncated", false))
        } catch (e: JSONException) {
            throw GitSyncException("GitHub returned an unreadable tree response.", cause = e)
        }
    }

    /** Relative (match/-stripped) paired with the original repo path, blobs only. */
    fun selectFiles(tree: GitTree): List<Pair<String, String>> = selectFiles(blobPaths(tree.entries))

    /** Regular files only: symlinks (mode 120000) are listed as blobs too, but their "content" is a path. */
    fun blobPaths(entries: List<GitTreeEntry>): List<String> =
        entries.filter { it.type == "blob" && it.mode != SYMLINK_MODE }.map { it.path }

    fun selectFiles(paths: List<String>): List<Pair<String, String>> =
        paths
            .mapNotNull { path -> GitSyncPaths.mapToRelative(path)?.let { it to path } }
            .sortedBy { it.first }

    fun errorFor(status: Int, rateLimitRemaining: String? = null, authenticated: Boolean = true): GitSyncException =
        httpError("GitHub", status, rateLimitRemaining, authenticated, forbiddenHint = "token lacks permission (needs Contents: read) or access is forbidden")
}

/** Shared HTTP status -> user-facing error mapping. Never includes request details or the token. */
internal fun httpError(
    label: String,
    status: Int,
    rateLimitRemaining: String?,
    authenticated: Boolean,
    forbiddenHint: String,
): GitSyncException {
    val rateLimited = "$label rate limit reached, try again later" +
        if (authenticated) "" else " (anonymous requests are limited; adding a token raises the limit)"
    val hint = when {
        status == 401 -> if (authenticated) "token invalid or expired" else "authentication required (private repository? add a token)"
        status == 403 && rateLimitRemaining == "0" -> rateLimited
        status == 403 -> if (authenticated) forbiddenHint else "access forbidden, or $label is rate limiting anonymous requests; try again later or add a token"
        status == 409 -> "repository is empty"
        status == 404 -> if (authenticated) "repo/branch not found or token lacks access" else "repo/branch not found (a private repository needs a token)"
        status in 300..399 -> "unexpected redirect (not followed, to protect the token)"
        status == 429 -> rateLimited
        status >= 500 -> "$label server error, try again later"
        else -> "unexpected response"
    }
    return GitSyncException("$label HTTP $status: $hint", status)
}
