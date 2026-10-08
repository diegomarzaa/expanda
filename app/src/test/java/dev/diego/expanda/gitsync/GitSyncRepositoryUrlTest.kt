package dev.diego.expanda.gitsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitSyncRepositoryUrlTest {
    private fun ok(input: String, owner: String, repo: String, branch: String = "") {
        val p = GitSyncInput.parseRepositoryUrl(input)
        assertNull(input, p.error)
        assertEquals(input, Triple(owner, repo, branch), Triple(p.owner, p.repo, p.branch))
    }

    private fun bad(input: String) {
        val p = GitSyncInput.parseRepositoryUrl(input)
        assertNotNull(input, p.error)
        assertFalse(input, p.valid)
    }

    @Test fun acceptedForms() {
        ok("https://github.com/o/r", "o", "r")
        ok("https://github.com/o/r.git", "o", "r")
        ok("https://github.com/o/r/", "o", "r")
        ok("https://github.com/o/r.git/", "o", "r")
        ok("github.com/o/r", "o", "r")
        ok("www.github.com/o/r", "o", "r")
        ok("HTTPS://GitHub.com/o/r", "o", "r")
        ok("o/r", "o", "r")
        ok("  o/r  ", "o", "r")
        ok("my-org/my.repo_1", "my-org", "my.repo_1")
    }

    @Test fun branches() {
        ok("https://github.com/o/r/tree/dev", "o", "r", "dev")
        ok("https://github.com/o/r/tree/feature/x/y", "o", "r", "feature/x/y")
        ok("github.com/o/r/tree/release-1.0/", "o", "r", "release-1.0")
    }

    @Test fun rejectsLookalikeAndInsecureUrls() {
        bad("https://evil.example/github.com/o/r")
        bad("http://github.com/o/r")
        bad("ssh://git@github.com/o/r")
        bad("git@github.com:o/r.git")
        bad("https://user:pw@host.example/o/r")
    }

    @Test fun rejectsExtraPathSegments() {
        bad("https://github.com/o/r/blob/main/x.yml")
        bad("https://github.com/o/r/issues")
        bad("https://github.com/o/r/tree")
        bad("o/r/extra")
    }

    @Test fun rejectsEmptyOrMalformed() {
        bad("")
        bad("   ")
        bad("o")
        bad("o/")
        bad("/r")
        bad("https://github.com/")
        bad("https://github.com/o")
        bad("https://github.com//r")
        bad("https://github.com/o/r/tree/../x")
        bad("https://github.com/o/ r")
    }

    @Test fun roundTrip() {
        listOf(
            Triple("o", "r", ""),
            Triple("o", "r", "main"),
            Triple("my-org", "my.repo", "dev"),
            Triple("o", "r", "feature/a/b"),
        ).forEach { (o, r, b) ->
            val url = GitSyncInput.displayUrl(o, r, b)
            val p = GitSyncInput.parseRepositoryUrl(url)
            assertTrue(url, p.valid)
            assertEquals(Triple(o, r, b), Triple(p.owner, p.repo, p.branch))
        }
        assertEquals("https://github.com/o/r", GitSyncInput.displayUrl("o", "r", ""))
        assertEquals("https://github.com/o/r/tree/main", GitSyncInput.displayUrl("o", "r", "main"))
        assertEquals("https://github.com/o/r/tree/dev", GitSyncInput.displayUrl("o", "r", "dev"))
        assertEquals("", GitSyncInput.displayUrl("", "", ""))
    }

    @Test fun rejectsPercentEncodedAndQueryBranches() {
        val pct = GitSyncInput.parseRepositoryUrl("https://github.com/o/r/tree/feat%2Fx")
        assertFalse(pct.valid)
        assertTrue(pct.error!!, pct.error!!.contains("Percent-encoded"))
        assertFalse(GitSyncInput.parseRepositoryUrl("o/r/tree/feat#1").valid)
        assertFalse(GitSyncInput.parseRepositoryUrl("o/r/tree/feat?x=1").valid)
        assertTrue(GitSyncInput.parseRepositoryUrl("https://github.com/o/r?tab=readme").valid)
    }
}

class ForgejoRepositoryUrlTest {
    private fun ok(input: String, host: String, owner: String, repo: String, branch: String = "") {
        val p = GitSyncInput.parseRepositoryUrl(input)
        assertNull(input + " -> " + p.error, p.error)
        assertEquals(input, listOf(host, owner, repo, branch), listOf(p.host, p.owner, p.repo, p.branch))
    }

    private fun bad(input: String) = assertFalse(input, GitSyncInput.parseRepositoryUrl(input).valid)

    @Test fun githubKeepsGitHubHost() {
        ok("https://github.com/o/r", "github.com", "o", "r")
        ok("o/r", "github.com", "o", "r")
        ok("www.github.com/o/r", "github.com", "o", "r")
        ok("HTTPS://GitHub.com/o/r/tree/dev", "github.com", "o", "r", "dev")
    }

    @Test fun codebergAndSelfHosted() {
        ok("https://codeberg.org/o/r", "codeberg.org", "o", "r")
        ok("https://Codeberg.org/o/r.git/", "codeberg.org", "o", "r")
        ok("codeberg.org/o/r", "codeberg.org", "o", "r")
        ok("https://git.example.com:3000/my_org/my.repo", "git.example.com:3000", "my_org", "my.repo")
        ok("git.example.com:3000/o/r", "git.example.com:3000", "o", "r")
        ok("https://forgejo.lan/o.x/r", "forgejo.lan", "o.x", "r")
    }

    @Test fun forgejoBranchUrls() {
        ok("https://codeberg.org/o/r/src/branch/dev", "codeberg.org", "o", "r", "dev")
        ok("https://codeberg.org/o/r/src/branch/feature/x/y", "codeberg.org", "o", "r", "feature/x/y")
        ok("https://git.example.com:8443/o/r/src/branch/main/", "git.example.com:8443", "o", "r", "main")
    }

    @Test fun forgejoRejectsGitHubStyleAndOtherPaths() {
        bad("https://codeberg.org/o/r/tree/dev")
        bad("https://codeberg.org/o/r/src/branch")
        bad("https://codeberg.org/o/r/src/tag/v1")
        bad("https://codeberg.org/o/r/issues")
        bad("https://github.com/o/r/src/branch/dev")
        bad("https://codeberg.org/o")
        bad("https://codeberg.org/o/r/src/branch/a%2Fb")
    }

    @Test fun httpAndBadHostsRejected() {
        val p = GitSyncInput.parseRepositoryUrl("http://codeberg.org/o/r")
        assertTrue(p.error!!, p.error!!.contains("https"))
        bad("https://-bad.example/o/r")
        bad("https://host..example/o/r")
        bad("https://host.example:99999/o/r")
        bad("https://host.example:0/o/r")
        bad("https://[::1]/o/r")
        bad("https://host.example:abc/o/r")
    }

    @Test fun displayUrlRoundTripsHostAndBranch() {
        listOf(
            listOf("github.com", "o", "r", ""), listOf("github.com", "o", "r", "a/b"),
            listOf("codeberg.org", "o", "r", ""), listOf("codeberg.org", "o", "r", "feature/x"),
            listOf("git.example.com:3000", "my_org", "r.x", "main"),
        ).forEach { (h, o, r, b) ->
            val url = GitSyncInput.displayUrl(o, r, b, h)
            val p = GitSyncInput.parseRepositoryUrl(url)
            assertEquals(url, listOf(h, o, r, b), listOf(p.host, p.owner, p.repo, p.branch))
        }
        assertEquals("https://codeberg.org/o/r/src/branch/x", GitSyncInput.displayUrl("o", "r", "x", "codeberg.org"))
        assertEquals("https://github.com/o/r/tree/x", GitSyncInput.displayUrl("o", "r", "x"))
    }

    @Test fun repoKeyKeepsLegacyFormatForGitHubAndSeparatesHosts() {
        val gh = GitSyncConfig("o", "r", "main", "", "github.com")
        assertEquals("o/r@main", gh.repoKey)
        assertEquals("o/r@main", GitSyncConfig("o", "r", "main").repoKey)
        val cb = GitSyncConfig("o", "r", "main", "", "codeberg.org")
        val other = GitSyncConfig("o", "r", "main", "", "git.example.com")
        assertTrue(gh.repoKey != cb.repoKey && cb.repoKey != other.repoKey)
    }

    @Test fun tokenRulesPerHost() {
        val hex40 = "0123456789abcdef".repeat(3).take(40)
        assertNull(GitSyncInput.validateToken(hex40, "codeberg.org"))
        assertNull(GitSyncInput.validateToken(VALID_TOKEN, "github.com"))
        assertNotNull(GitSyncInput.validateToken("bad token with spaces 123456", "codeberg.org"))
        assertNotNull(GitSyncInput.validateToken("short", "github.com"))
        assertTrue(GitSyncConfig("o", "r", "", "", "codeberg.org").configured)
        assertTrue(GitSyncConfig("o", "r", "", hex40, "codeberg.org").configured)
        assertFalse(GitSyncConfig("o", "r", "", "short", "codeberg.org").configured)
        assertFalse(GitSyncConfig("o", "r", "", "", "bad host").configured)
    }
}
