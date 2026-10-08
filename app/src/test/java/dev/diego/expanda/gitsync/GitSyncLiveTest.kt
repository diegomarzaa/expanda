package dev.diego.expanda.gitsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Smoke tests against real hosts, anonymous and read-only. Each test is skipped unless its target is set:
 *
 *  - `GIT_SYNC_LIVE_GITHUB=owner/repo` (a public GitHub repository with a `match/` folder)
 *  - `GIT_SYNC_LIVE_FORGEJO=host/owner/repo` (a public Forgejo/Gitea repository, e.g. on codeberg.org)
 *
 * Run: GIT_SYNC_LIVE_GITHUB=owner/repo ./gradlew testDebugUnitTest --tests '*GitSyncLiveTest*' -i
 */
class GitSyncLiveTest {
    private val http = HttpUrlConnectionGet()

    private fun target(variable: String, parts: Int): List<String> {
        val value = System.getenv(variable)
        assumeTrue("set $variable to run", !value.isNullOrBlank())
        val split = value!!.trim().split('/')
        assumeTrue("$variable must have $parts '/'-separated parts", split.size == parts && split.none { it.isEmpty() })
        return split
    }

    private fun forgejoConfig(): GitSyncConfig {
        val (host, owner, repo) = target("GIT_SYNC_LIVE_FORGEJO", 3)
        return GitSyncConfig(owner, repo, "", "", host)
    }

    @Test
    fun forgejoAnonymousListAndRawFetch() {
        val config = forgejoConfig()
        val host = GitHosts.forHost(config.host, http)
        assertTrue(host is ForgejoHost)
        val head = host.checkBranch(config, AppliedState(), force = true) as BranchCheck.Changed
        assertTrue(head.sha, head.sha.matches(Regex("[0-9a-f]{40}")))
        val paths = host.listBlobPaths(config, head.sha)
        println("LIVE forgejo head=${head.sha} paths=$paths")
        assertTrue(paths.isNotEmpty())
        val text = host.fetchFile(config, head.sha, paths.first())
        println("LIVE forgejo first file bytes=${text.length}")
        // Second probe with the same SHA and no stored ETag is Unchanged.
        assertTrue(host.checkBranch(config, AppliedState(head.sha, head.etag, config.repoKey), false) is BranchCheck.Unchanged)
    }

    @Test
    fun forgejoMissingRepoIsFriendly404() {
        val config = forgejoConfig().copy(repo = "this-repo-does-not-exist-expanda", branch = "main")
        try {
            GitSyncFetcher(http).checkBranch(config, AppliedState(), true)
            fail("expected 404")
        } catch (e: GitSyncException) {
            assertEquals(404, e.status)
            println("LIVE forgejo 404 message=${e.message}")
        }
    }

    @Test
    fun gitHubAnonymousFetchesMatchFiles() {
        val (owner, repo) = target("GIT_SYNC_LIVE_GITHUB", 2)
        val config = GitSyncConfig(owner, repo, "", "")
        val head = GitSyncFetcher(http).checkBranch(config, AppliedState(), true) as BranchCheck.Changed
        val files = GitSyncFetcher(http).fetch(config, head.sha)
        println("LIVE github head=${head.sha} files=${files.map { it.relativePath }}")
        assertTrue(files.isNotEmpty() && files.all { it.content.isNotBlank() })
    }
}
