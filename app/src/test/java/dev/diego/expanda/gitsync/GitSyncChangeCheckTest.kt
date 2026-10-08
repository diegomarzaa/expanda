package dev.diego.expanda.gitsync

import dev.diego.expanda.data.CompatibilityIssue
import dev.diego.expanda.data.CompatibilitySeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitSyncChangeCheckTest {
    private val config = GitSyncConfig("o", "r", "main", VALID_TOKEN)
    private val commitUrl = "https://api.github.com/repos/o/r/commits/main"
    private val aaa = SHA_A
    private val bbb = SHA_B

    private class Fake(val response: HttpResult) : HttpGet {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResult {
            calls += url to headers
            return response
        }
    }

    @Test
    fun commitUrlShape() {
        assertEquals(commitUrl, GitHubApi.commitUrl("o", "r", "main"))
        assertEquals("https://api.github.com/repos/o/r/commits/feat/x", GitHubApi.commitUrl("o", "r", "feat/x"))
        // No branch configured: GitHub resolves HEAD to the repository's default branch.
        assertEquals("https://api.github.com/repos/o/r/commits/HEAD", GitHubApi.commitUrl("o", "r", ""))
    }

    @Test
    fun status304IsUnchangedAndSendsEtagAndShaAccept() {
        val http = Fake(HttpResult(304, ""))
        val r = GitSyncFetcher(http).checkBranch(config, AppliedState(aaa, "\"etag1\""), force = false)
        assertTrue(r is BranchCheck.Unchanged)
        assertEquals(commitUrl, http.calls[0].first)
        assertEquals("\"etag1\"", http.calls[0].second["If-None-Match"])
        assertEquals("application/vnd.github.sha", http.calls[0].second["Accept"])
        assertEquals("Bearer $VALID_TOKEN", http.calls[0].second["Authorization"])
    }

    @Test
    fun noEtagStoredMeansNoIfNoneMatch() {
        val http = Fake(HttpResult(200, "$bbb\n", etag = "\"e2\""))
        GitSyncFetcher(http).checkBranch(config, AppliedState(), force = false)
        assertFalse(http.calls[0].second.containsKey("If-None-Match"))
    }

    @Test
    fun sameShaIsUnchanged() {
        val http = Fake(HttpResult(200, aaa, etag = "\"e2\""))
        assertTrue(GitSyncFetcher(http).checkBranch(config, AppliedState(aaa, "\"e1\""), false) is BranchCheck.Unchanged)
    }

    @Test
    fun newShaIsChangedWithShaAndEtag() {
        val http = Fake(HttpResult(200, "$bbb\n", etag = "\"e2\""))
        val r = GitSyncFetcher(http).checkBranch(config, AppliedState(aaa, "\"e1\""), false) as BranchCheck.Changed
        assertEquals(bbb, r.sha)
        assertEquals("\"e2\"", r.etag)
    }

    @Test
    fun forceIgnoresEtagAnd304AndSameSha() {
        val fresh = Fake(HttpResult(200, bbb, etag = "\"e2\""))
        assertTrue(GitSyncFetcher(fresh).checkBranch(config, AppliedState(aaa, "\"e1\""), true) is BranchCheck.Changed)
        assertFalse(fresh.calls[0].second.containsKey("If-None-Match"))
        val same = Fake(HttpResult(200, aaa, etag = "\"e1\""))
        val r = GitSyncFetcher(same).checkBranch(config, AppliedState(aaa, "\"e1\""), true) as BranchCheck.Changed
        assertEquals(aaa, r.sha)
    }

    @Test
    fun forcedCheckStillMustSucceedBecauseTheShaPinsTheDownload() {
        val failing = HttpGet { _, _, _ -> HttpResult(500, "") }
        try {
            GitSyncFetcher(failing).checkBranch(config, AppliedState(aaa, "\"e1\""), true)
            fail()
        } catch (e: GitSyncException) {
            assertEquals(500, e.status)
        }
        try {
            GitSyncFetcher(Fake(HttpResult(200, "not-a-sha"))).checkBranch(config, AppliedState(), true)
            fail()
        } catch (_: GitSyncException) {
        }
    }

    @Test
    fun nonForcedErrorsPropagate() {
        try {
            GitSyncFetcher(Fake(HttpResult(401, "{}"))).checkBranch(config, AppliedState(), false)
            fail()
        } catch (e: GitSyncException) {
            assertEquals(401, e.status)
        }
    }

    @Test
    fun issueJsonRoundTripAndCap() {
        val issues = listOf(
            CompatibilityIssue(CompatibilitySeverity.WARNING, "base.yml", "unsupported \"var\"\nline"),
            CompatibilityIssue(CompatibilitySeverity.ERROR, "x.yml", "bad"),
        )
        val stored = IssueCodec.fromCompat(issues)
        assertEquals(listOf("WARNING", "ERROR"), stored.map { it.severity })
        assertEquals(stored, IssueCodec.decode(IssueCodec.encode(stored)))
        val many = (1..150).map { StoredIssue("INFO", "m$it") }
        val decoded = IssueCodec.decode(IssueCodec.encode(many))
        assertEquals(100, decoded.size)
        assertEquals("m100", decoded.last().message)
        assertTrue(IssueCodec.decode(null).isEmpty())
        assertTrue(IssueCodec.decode("garbage").isEmpty())
    }

    @Test
    fun syncOnOpenPolicy() {
        val now = 10_000_000_000L
        val hour = SyncOnOpenPolicy.MIN_INTERVAL_MS
        assertTrue(SyncOnOpenPolicy.shouldSync(true, false, null, now))
        assertTrue(SyncOnOpenPolicy.shouldSync(true, false, now - hour - 1, now))
        assertFalse(SyncOnOpenPolicy.shouldSync(true, false, now - hour + 1000, now))
        assertFalse(SyncOnOpenPolicy.shouldSync(false, false, null, now))
        assertFalse(SyncOnOpenPolicy.shouldSync(true, true, null, now))
    }
}
