package dev.diego.expanda.gitsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitSyncSafetyTest {
    @get:Rule val tmp = TemporaryFolder()

    private val tokenFile get() = TokenFile(File(tmp.root, "token"))

    /** In-memory stand-in for the `token_host` preference. */
    private class HostPref(var value: String? = null)

    private fun store(pref: HostPref) = HostBoundToken(tokenFile, { pref.value }, { pref.value = it })

    @Test fun tokenIsReturnedOnlyForTheHostItWasEnteredFor() {
        val pref = HostPref()
        val tokens = store(pref)
        tokens.save(VALID_TOKEN, "codeberg.org")
        assertEquals(VALID_TOKEN, tokens.read("codeberg.org"))
        assertEquals("", tokens.read("github.com"))
        assertEquals("", tokens.read("other.example"))
    }

    @Test fun savingTheSameTokenForAnotherHostDropsIt() {
        val pref = HostPref()
        val tokens = store(pref)
        tokens.save(VALID_TOKEN, "codeberg.org")
        tokens.save(VALID_TOKEN, "git.example.com")
        assertEquals("", tokens.read("git.example.com"))
        assertEquals("", tokens.read("codeberg.org"))
        assertEquals("", tokenFile.read())
        assertNull(pref.value)
    }

    @Test fun aNewTokenForAnotherHostIsKeptAndBound() {
        val pref = HostPref()
        val tokens = store(pref)
        tokens.save(VALID_TOKEN, "codeberg.org")
        tokens.save(VALID_TOKEN + "x", "git.example.com")
        assertEquals(VALID_TOKEN + "x", tokens.read("git.example.com"))
        assertEquals("git.example.com", pref.value)
    }

    @Test fun tokenWithoutBindingBelongsToGitHub() {
        val pref = HostPref()
        tokenFile.write(VALID_TOKEN) // an install from before the binding existed
        val tokens = store(pref)
        assertEquals(VALID_TOKEN, tokens.read("github.com"))
        assertEquals("", tokens.read("codeberg.org"))
        // Saving it again for the same host keeps it; for another host drops it.
        tokens.save(VALID_TOKEN, "github.com")
        assertEquals(VALID_TOKEN, tokens.read("github.com"))
        tokens.save(VALID_TOKEN, "codeberg.org")
        assertEquals("", tokenFile.read())
    }

    @Test fun emptyTokenClearsFileAndBinding() {
        val pref = HostPref()
        val tokens = store(pref)
        tokens.save(VALID_TOKEN, "codeberg.org")
        tokens.save("", "codeberg.org")
        assertEquals("", tokenFile.read())
        assertNull(pref.value)
    }

    @Test fun noAuthorizationHeaderIsSentToTheNewHostAfterAHostChange() {
        val pref = HostPref()
        val tokens = store(pref)
        tokens.save(VALID_TOKEN, "github.com")
        tokens.save(VALID_TOKEN, "codeberg.org") // the host changed, the token was not re-entered
        val config = GitSyncConfig("o", "r", "main", tokens.read("codeberg.org"), "codeberg.org")
        val seen = mutableListOf<Map<String, String>>()
        val http = HttpGet { _, headers, _ ->
            seen += headers
            HttpResult(200, """{"commit":{"id":"$SHA_A"}}""")
        }
        GitHosts.forHost(config.host, http).checkBranch(config, AppliedState(), force = true)
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.none { it.containsKey("Authorization") })
    }

    @Test fun mapToRelativeRejectsEmptyAndDotSegments() {
        listOf(
            "match//a.yml", "match/./a.yml", "match/../a.yml", "match/a/../b.yml", "match/a//b.yml",
            "match/a/./b.yml", "match//", "match/a/.yml/..",
        ).forEach { assertNull(it, GitSyncPaths.mapToRelative(it)) }
        assertEquals("a/b.yml", GitSyncPaths.mapToRelative("match/a/b.yml"))
        assertEquals(".hidden.yml", GitSyncPaths.mapToRelative("match/.hidden.yml"))
    }

    @Test fun totalDownloadIsCapped() {
        val chunk = "x".repeat(GitHubApi.MAX_FILE_BYTES - 1)
        val count = GitHubApi.MAX_TOTAL_BYTES / chunk.length + 1
        val paths = (0 until count).map { "match/f$it.yml" }
        val fetched = mutableListOf<String>()
        val host = object : GitHost {
            override val host = "codeberg.org"
            override val label = "codeberg.org"
            override fun validateToken(token: String) = null
            override fun checkBranch(config: GitSyncConfig, applied: AppliedState, force: Boolean) = BranchCheck.Changed(SHA_A, null)
            override fun listBlobPaths(config: GitSyncConfig, sha: String) = paths
            override fun fetchFile(config: GitSyncConfig, sha: String, path: String): String {
                fetched += path
                return chunk
            }
            override fun errorFor(status: Int, rateLimitRemaining: String?, authenticated: Boolean) = GitSyncException("x")
        }
        val fetcher = GitSyncFetcher(HttpGet { _, _, _ -> HttpResult(200, "") }, { _, _ -> host })
        try {
            fetcher.fetch(GitSyncConfig("o", "r", "main", "", "codeberg.org"), SHA_A)
            fail("expected the total cap to apply")
        } catch (e: GitSyncException) {
            assertTrue(e.message!!, e.message!!.contains("16 MiB"))
        }
        assertTrue(fetched.size <= count)
    }

    @Test fun gitHubDefaultPortIsGitHubAndOtherPortsAreRejected() {
        val ok = GitSyncInput.parseRepositoryUrl("https://github.com:443/o/r")
        assertTrue(ok.error, ok.valid)
        assertEquals("github.com", ok.host)
        assertFalse(GitSyncInput.parseRepositoryUrl("https://github.com:8443/o/r").valid)
        assertFalse(GitSyncInput.parseRepositoryUrl("github.com:80/o/r").valid)
        assertTrue(GitSyncInput.parseRepositoryUrl("https://git.example.com:8443/o/r").valid)
    }
}
