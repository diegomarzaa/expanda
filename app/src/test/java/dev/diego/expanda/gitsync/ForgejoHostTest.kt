package dev.diego.expanda.gitsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ForgejoHostTest {
    private val token = "0123456789abcdef0123456789abcdef01234567"
    private val config = GitSyncConfig("o", "r", "main", token, "codeberg.org")
    private val anon = config.copy(token = "")
    private val api = "https://codeberg.org/api/v1/repos/o/r"

    private class Recorder(val respond: (String) -> HttpResult) : HttpGet {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResult {
            calls += url to headers
            return respond(url)
        }
    }

    private fun branchJson(sha: String) = """{"name":"main","commit":{"id":"$sha","message":"m"}}"""

    private fun treePage(vararg paths: String, truncated: Boolean = false, page: Int = 1, total: Int = paths.size) =
        """{"sha":"x","page":$page,"total_count":$total,"truncated":$truncated,"tree":[""" +
            paths.joinToString(",") { """{"path":"$it","type":"blob","mode":"100644"}""" } + "]}"

    private fun fail(e: () -> Unit): GitSyncException {
        try {
            e()
        } catch (ex: GitSyncException) {
            return ex
        }
        fail("expected GitSyncException")
        throw IllegalStateException()
    }

    @Test
    fun branchHeadUsesBranchEndpointAndTokenHeader() {
        val http = Recorder { HttpResult(200, branchJson(SHA_A)) }
        val r = GitSyncFetcher(http).checkBranch(config, AppliedState(), false) as BranchCheck.Changed
        assertEquals(SHA_A, r.sha)
        assertEquals(listOf("$api/branches/main"), http.calls.map { it.first })
        assertEquals("token $token", http.calls[0].second["Authorization"])
    }

    @Test
    fun branchWithSlashesKeepsSlashesAndEncodesSegments() {
        val http = Recorder { HttpResult(200, branchJson(SHA_A)) }
        GitSyncFetcher(http).checkBranch(config.copy(branch = "feat/é"), AppliedState(), false)
        assertEquals("$api/branches/feat/%C3%A9", http.calls[0].first)
    }

    @Test
    fun defaultBranchIsResolvedFirstWhenBlank() {
        val http = Recorder { url ->
            if (url.endsWith("/branches/trunk")) HttpResult(200, branchJson(SHA_B))
            else HttpResult(200, """{"name":"r","default_branch":"trunk"}""")
        }
        val r = GitSyncFetcher(http).checkBranch(config.copy(branch = ""), AppliedState(), false) as BranchCheck.Changed
        assertEquals(SHA_B, r.sha)
        assertEquals(listOf(api, "$api/branches/trunk"), http.calls.map { it.first })
    }

    @Test
    fun badDefaultBranchFromServerIsRejected() {
        val http = Recorder { HttpResult(200, """{"default_branch":"../../x"}""") }
        fail { GitSyncFetcher(http).checkBranch(config.copy(branch = ""), AppliedState(), false) }
        assertEquals(1, http.calls.size)
    }

    @Test
    fun sameShaUnchangedAndEtagUsedWhenPresent() {
        val http = Recorder { HttpResult(200, branchJson(SHA_A), etag = "\"e2\"") }
        assertTrue(GitSyncFetcher(http).checkBranch(config, AppliedState(SHA_A, "\"e1\""), false) is BranchCheck.Unchanged)
        assertEquals("\"e1\"", http.calls[0].second["If-None-Match"])
        val forced = GitSyncFetcher(http).checkBranch(config, AppliedState(SHA_A, "\"e1\""), true)
        assertTrue(forced is BranchCheck.Changed)
        assertFalse(http.calls[1].second.containsKey("If-None-Match"))
        // No ETag stored (Forgejo does not send one): plain request, compare by SHA.
        GitSyncFetcher(http).checkBranch(config, AppliedState(SHA_A, null), false)
        assertFalse(http.calls[2].second.containsKey("If-None-Match"))
        assertTrue(GitSyncFetcher(Recorder { HttpResult(304, "") }).checkBranch(config, AppliedState(SHA_A, "\"e\""), false) is BranchCheck.Unchanged)
    }

    @Test
    fun malformedBranchResponsesAreFriendlyErrors() {
        listOf("not json", """{"commit":{}}""", """{"commit":{"id":"zzz"}}""").forEach { body ->
            fail { GitSyncFetcher(Recorder { HttpResult(200, body) }).checkBranch(config, AppliedState(), false) }
        }
    }

    @Test
    fun treeIsPagedAndPinnedToShaAndSkipsSymlinks() {
        val http = Recorder { url ->
            when {
                "&page=1" in url -> HttpResult(200, treePage("match/a.yml", "README.md", truncated = true, page = 1, total = 4))
                else -> HttpResult(
                    200,
                    """{"page":2,"total_count":4,"truncated":false,"tree":[
                        {"path":"match/b.yml","type":"blob","mode":"100644"},
                        {"path":"match/link.yml","type":"blob","mode":"120000"},
                        {"path":"match","type":"tree","mode":"040000"}]}""",
                )
            }
        }
        val paths = ForgejoHost("codeberg.org", http).listBlobPaths(config, SHA_A)
        assertEquals(listOf("match/a.yml", "README.md", "match/b.yml"), paths)
        assertEquals(
            listOf(
                "$api/git/trees/$SHA_A?recursive=true&per_page=1000&page=1",
                "$api/git/trees/$SHA_A?recursive=true&per_page=1000&page=2",
            ),
            http.calls.map { it.first },
        )
    }

    @Test
    fun treeStopsAtPageAndEntryCaps() {
        // Server claims "more" forever with non-empty pages: bounded by the page cap.
        val endless = Recorder { HttpResult(200, treePage("a.txt", truncated = true)) }
        val e = fail { ForgejoHost("codeberg.org", endless).listBlobPaths(config, SHA_A) }
        assertTrue(e.message!!, e.message!!.contains("too large to list"))
        assertEquals(ForgejoHost.MAX_TREE_PAGES, endless.calls.size)
        // An empty page that still claims "more" is an incomplete listing, not an endless loop.
        val empty = Recorder { HttpResult(200, treePage(truncated = true)) }
        fail { ForgejoHost("codeberg.org", empty).listBlobPaths(config, SHA_A) }
        assertEquals(1, empty.calls.size)
        // More entries than the cap in one response.
        val big = Recorder { HttpResult(200, treePage(*Array(ForgejoHost.MAX_TREE_ENTRIES + 1) { "f$it.txt" })) }
        fail { ForgejoHost("codeberg.org", big).listBlobPaths(config, SHA_A) }
    }

    @Test
    fun malformedTreeIsFriendlyError() {
        val e = fail { ForgejoHost("codeberg.org", Recorder { HttpResult(200, "oops") }).listBlobPaths(config, SHA_A) }
        assertTrue(e.message!!.contains("unreadable"))
        fail { ForgejoHost("codeberg.org", Recorder { HttpResult(200, "{}") }).listBlobPaths(config, SHA_A) }
    }

    @Test
    fun rawFileFetchedAtShaViaMediaWithEncodedPath() {
        val http = Recorder { HttpResult(200, "matches: []\n") }
        val body = ForgejoHost("codeberg.org", http).fetchFile(config, SHA_A, "match/my file.yml")
        assertEquals("matches: []\n", body)
        assertEquals("$api/media/match/my%20file.yml?ref=$SHA_A", http.calls[0].first)
    }

    @Test
    fun fullFetchMapsMatchFilesAndPassesCaps() {
        val caps = mutableListOf<Int>()
        val http = object : HttpGet {
            override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResult {
                caps += maxBytes
                return if ("git/trees" in url) HttpResult(200, treePage("match/base.yml", "match/packages/p.yml", "x.txt"))
                else HttpResult(200, "matches: []\n")
            }
        }
        val files = GitSyncFetcher(http).fetch(config, SHA_A)
        assertEquals(listOf("base.yml"), files.map { it.relativePath })
        assertEquals(listOf(GitHubApi.MAX_TREE_BYTES, GitHubApi.MAX_FILE_BYTES), caps)
    }

    @Test
    fun tooManyMatchFilesAcrossPagesStillCapped() {
        val http = Recorder { HttpResult(200, treePage(*Array(GitHubApi.MAX_FILES + 1) { "match/f$it.yml" })) }
        val e = fail { GitSyncFetcher(http).fetch(config, SHA_A) }
        assertTrue(e.message!!, e.message!!.contains("Too many files"))
        assertEquals(1, http.calls.size)
    }

    @Test
    fun authHeaderOnlyWhenTokenSet() {
        val http = Recorder { HttpResult(200, branchJson(SHA_A)) }
        GitSyncFetcher(http).checkBranch(anon, AppliedState(), false)
        assertFalse(http.calls[0].second.containsKey("Authorization"))
        val tree = Recorder { HttpResult(200, treePage("match/a.yml")) }
        ForgejoHost("codeberg.org", tree).listBlobPaths(anon, SHA_A)
        ForgejoHost("codeberg.org", tree).fetchFile(anon, SHA_A, "match/a.yml")
        assertTrue(tree.calls.none { it.second.containsKey("Authorization") })
        // And with a token it is on every request, never in the URL.
        val authed = Recorder { HttpResult(200, treePage("match/a.yml")) }
        GitSyncFetcher(authed).fetch(config, SHA_A)
        assertTrue(authed.calls.all { it.second["Authorization"] == "token $token" && token !in it.first })
    }

    @Test
    fun requestsOnlyGoToTheConfiguredHostOverHttps() {
        val urls = mutableListOf<String>()
        val http = Recorder { url ->
            urls += url
            if ("branches" in url) HttpResult(200, branchJson(SHA_A)) else HttpResult(200, treePage("match/a.yml"))
        }
        val self = config.copy(host = "git.example.com:8443")
        GitSyncFetcher(http).checkBranch(self, AppliedState(), false)
        GitSyncFetcher(http).fetch(self, SHA_A)
        assertTrue(urls.toString(), urls.all { it.startsWith("https://git.example.com:8443/api/v1/") })
    }

    @Test
    fun invalidHostOrCoordinatesNeverReachTheNetwork() {
        val http = Recorder { HttpResult(200, "{}") }
        listOf(
            config.copy(host = "evil.example/x"), config.copy(host = "user@evil.example"), config.copy(host = ""),
            config.copy(owner = ".."), config.copy(repo = "a/b"), config.copy(branch = "../x"),
        ).forEach { c ->
            fail { GitSyncFetcher(http).checkBranch(c, AppliedState(), false) }
            fail { GitSyncFetcher(http).fetch(c, SHA_A) }
        }
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun errorMappingWithAndWithoutToken() {
        val host = ForgejoHost("codeberg.org", Recorder { HttpResult(200, "") })
        assertTrue(host.errorFor(401).message!!.contains("token invalid"))
        assertTrue(host.errorFor(401, authenticated = false).message!!.contains("add a token"))
        assertTrue(host.errorFor(404).message!!.contains("repo/branch not found"))
        assertTrue(host.errorFor(404, authenticated = false).message!!.contains("private repository needs a token"))
        assertTrue(host.errorFor(429).message!!.contains("rate limit"))
        assertTrue(host.errorFor(429, authenticated = false).message!!.contains("anonymous"))
        assertTrue(host.errorFor(403, "0").message!!.contains("rate limit"))
        assertTrue(host.errorFor(403, authenticated = false).message!!.contains("rate limiting"))
        assertTrue(host.errorFor(403).message!!.contains("forbidden"))
        assertTrue(host.errorFor(409).message!!.contains("empty"))
        assertTrue(host.errorFor(502).message!!.contains("server error"))
        assertTrue(host.errorFor(302).message!!.contains("redirect"))
        assertEquals(429, host.errorFor(429).status)
        assertTrue(host.errorFor(404).message!!.startsWith("codeberg.org HTTP 404"))
    }

    @Test
    fun errorsDuringFetchNeverEchoTokenOrBody() {
        val http = Recorder { HttpResult(500, "secret-body $token") }
        val e = fail { GitSyncFetcher(http).fetch(config, SHA_A) }
        assertFalse(e.message!!.contains(token))
        assertFalse(e.message!!.contains("secret-body"))
        assertNull(e.cause)
    }

    @Test
    fun gitHubHostAnonymousSendsNoAuthorization() {
        val http = Recorder { HttpResult(200, SHA_A) }
        GitSyncFetcher(http).checkBranch(GitSyncConfig("o", "r", "main"), AppliedState(), false)
        assertFalse(http.calls[0].second.containsKey("Authorization"))
        assertTrue(GitHubHost(http).errorFor(403, "0", authenticated = false).message!!.contains("anonymous"))
    }

    @Test
    fun gitHubSymlinkBlobsAreSkipped() {
        val tree = """{"truncated":false,"tree":[
            {"path":"match/a.yml","type":"blob","mode":"100644"},
            {"path":"match/l.yml","type":"blob","mode":"120000"}]}"""
        val host = GitHubHost(Recorder { HttpResult(200, tree) })
        assertEquals(listOf("match/a.yml"), host.listBlobPaths(GitSyncConfig("o", "r", "main"), SHA_A))
    }
}
