package dev.diego.expanda.gitsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubApiTest {
    @Test
    fun pathMapping() {
        assertEquals("base.yml", GitSyncPaths.mapToRelative("match/base.yml"))
        assertEquals("foo/bar.yaml", GitSyncPaths.mapToRelative("match/foo/bar.yaml"))
        assertNull(GitSyncPaths.mapToRelative("match/packages/x/package.yml"))
        assertNull(GitSyncPaths.mapToRelative("match/readme.md"))
        assertNull(GitSyncPaths.mapToRelative("config/default.yml"))
        assertNull(GitSyncPaths.mapToRelative("match/"))
        assertNull(GitSyncPaths.mapToRelative("matchx/base.yml"))
    }

    private val treeJson = """
        {"sha":"abc","truncated":false,"tree":[
          {"path":"match","type":"tree"},
          {"path":"match/base.yml","type":"blob"},
          {"path":"match/sub","type":"tree"},
          {"path":"match/sub/a.yml","type":"blob"},
          {"path":"match/packages/p/package.yml","type":"blob"},
          {"path":"match/notes.txt","type":"blob"},
          {"path":"config/default.yml","type":"blob"},
          {"path":"match/dir.yml","type":"tree"}
        ]}
    """.trimIndent()

    @Test
    fun treeParsingAndSelection() {
        val tree = GitHubApi.parseTree(treeJson)
        assertFalse(tree.truncated)
        assertEquals(
            listOf("base.yml" to "match/base.yml", "sub/a.yml" to "match/sub/a.yml"),
            GitHubApi.selectFiles(tree),
        )
    }

    @Test
    fun truncatedFlagParsed() {
        assertTrue(GitHubApi.parseTree("""{"truncated":true,"tree":[]}""").truncated)
    }

    @Test
    fun malformedTreeThrowsFriendlyError() {
        try {
            GitHubApi.parseTree("not json")
            fail()
        } catch (e: GitSyncException) {
            assertTrue(e.message!!.contains("unreadable"))
        }
    }

    @Test
    fun errorMapping() {
        assertTrue(GitHubApi.errorFor(401).message!!.contains("token invalid or expired"))
        assertTrue(GitHubApi.errorFor(403).message!!.contains("permission"))
        assertTrue(GitHubApi.errorFor(403, "0").message!!.contains("rate limit"))
        assertTrue(GitHubApi.errorFor(404).message!!.contains("repo/branch not found"))
        assertEquals(404, GitHubApi.errorFor(404).status)
        assertTrue(GitHubApi.errorFor(409).message!!.contains("repository is empty"))
        assertTrue(GitHubApi.errorFor(418).message!!.contains("HTTP 418"))
        assertTrue(GitHubApi.errorFor(503).message!!.contains("server error"))
    }

    @Test
    fun urls() {
        assertEquals(
            "https://api.github.com/repos/o/r/git/trees/$SHA_A?recursive=1",
            GitHubApi.treeUrl("o", "r", SHA_A),
        )
        assertEquals(
            "https://api.github.com/repos/o/r/contents/match/my%20file.yml?ref=feat%2Fx",
            GitHubApi.contentsUrl("o", "r", "feat/x", "match/my file.yml"),
        )
    }

    @Test
    fun fetcherSendsHeadersAndMapsFiles() {
        val seen = mutableListOf<Pair<String, Map<String, String>>>()
        val http = HttpGet { url, headers, _ ->
            seen += url to headers
            if ("git/trees" in url) HttpResult(200, treeJson) else HttpResult(200, "matches: []\n")
        }
        val files = GitSyncFetcher(http).fetch(GitSyncConfig("o", "r", "main", "secret-token"), SHA_A)
        // Tree and contents are pinned to the commit SHA, not the branch name.
        assertTrue(seen[0].first, seen[0].first.contains("/git/trees/$SHA_A?"))
        assertTrue(seen[1].first, seen[1].first.endsWith("?ref=$SHA_A"))
        assertEquals(listOf("base.yml", "sub/a.yml"), files.map { it.relativePath })
        assertEquals(3, seen.size)
        assertEquals("Bearer secret-token", seen[0].second["Authorization"])
        assertEquals("2022-11-28", seen[0].second["X-GitHub-Api-Version"])
        assertEquals("application/vnd.github.raw", seen[1].second["Accept"])
    }

    @Test
    fun fetcherFailsOnTruncatedAndNeverLeaksToken() {
        val truncated = HttpGet { _, _, _ -> HttpResult(200, """{"truncated":true,"tree":[]}""") }
        try {
            GitSyncFetcher(truncated).fetch(GitSyncConfig("o", "r", "main", "tok"), SHA_A)
            fail()
        } catch (e: GitSyncException) {
            assertTrue(e.message!!.contains("truncated"))
        }
        val unauthorized = HttpGet { _, _, _ -> HttpResult(401, "{}") }
        try {
            GitSyncFetcher(unauthorized).fetch(GitSyncConfig("o", "r", "main", "super-secret"), SHA_A)
            fail()
        } catch (e: GitSyncException) {
            assertEquals(401, e.status)
            assertFalse(e.message!!.contains("super-secret"))
        }
    }

    @Test
    fun configToStringRedactsToken() {
        assertFalse(GitSyncConfig(token = "abc123").toString().contains("abc123"))
    }
}
