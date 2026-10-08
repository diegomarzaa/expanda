package dev.diego.expanda.gitsync

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubHardeningTest {
    @Test
    fun ownerValidation() {
        listOf("octocat", "a", "my-org", "A1-b2").forEach { assertNull(it, GitSyncInput.validateOwner(it)) }
        listOf("", "-", "a_b", "a.b", "a/b", "a b", "../x", "x".repeat(40), "é").forEach {
            assertNotNull(it, GitSyncInput.validateOwner(it))
        }
    }

    @Test
    fun repoValidation() {
        listOf("espanso", "my.repo", "my_repo-1", ".github", "a".repeat(100)).forEach { assertNull(it, GitSyncInput.validateRepo(it)) }
        listOf("", ".", "..", "a/b", "a b", "a?b", "a".repeat(101)).forEach { assertNotNull(it, GitSyncInput.validateRepo(it)) }
    }

    @Test
    fun branchValidation() {
        listOf("main", "feature/x", "release-1.0", "a.b/c_d").forEach { assertNull(it, GitSyncInput.validateBranch(it)) }
        listOf("", "..", "a/../b", "../../x", "/main", "main/", "a//b", "a/./b", "a\u0000b", "a\nb", "a b", "a~b", "a:b", "a\\b", "a@{b", "a#b", "a?b", "a%2Fb", "a%b").forEach {
            assertNotNull(it, GitSyncInput.validateBranch(it))
        }
    }

    @Test
    fun defaultConfigIsNotConfigured() {
        val c = GitSyncConfig()
        assertEquals("", c.owner)
        assertEquals("", c.repo)
        assertEquals("", c.branch)
        assertTrue(!c.configured)
        // The token is optional: empty means anonymous access to a public repository.
        assertTrue(GitSyncConfig("o", "r", "", "").configured)
        assertTrue(!GitSyncConfig("o", "r", "", "short").configured)
        // A blank branch means the repository's default branch.
        assertTrue(GitSyncConfig("o", "r", "", VALID_TOKEN).configured)
        assertTrue(GitSyncConfig("o", "r", "main", VALID_TOKEN).configured)
    }

    @Test
    fun tokenValidation() {
        listOf(VALID_TOKEN, "ghp_" + "A1".repeat(18), "a".repeat(20), "a".repeat(255)).forEach {
            assertNull(it, GitSyncInput.validateToken(it))
        }
        listOf("", "short", "a".repeat(19), "a".repeat(256), "github_pat_0123456789 ABCDEF", "github-pat-0123456789ABCDEF",
            "github_pat_0123456789ABCDEF\n", "github_pat_0123456789ABCDEé").forEach {
            assertNotNull(it, GitSyncInput.validateToken(it))
        }
    }

    @Test
    fun fetcherRejectsInvalidCoordinatesWithoutNetwork() {
        val http = HttpGet { _, _, _ -> fail("no request expected"); HttpResult(200, "") }
        for (c in listOf(
            GitSyncConfig("o", "..", "main", "t"),
            GitSyncConfig("o/x", "r", "main", "t"),
            GitSyncConfig("o", "r", "../x", "t"),
        )) {
            try {
                GitSyncFetcher(http).fetch(c, SHA_A)
                fail()
            } catch (e: GitSyncException) {
                assertTrue(!e.message!!.contains("t\n"))
            }
            try {
                GitSyncFetcher(http).checkBranch(c, AppliedState(), false)
                fail()
            } catch (_: GitSyncException) {
            }
        }
    }

    @Test
    fun fileCountCap() {
        val entries = (0..GitHubApi.MAX_FILES).joinToString(",") { """{"path":"match/f$it.yml","type":"blob"}""" }
        val http = HttpGet { _, _, _ -> HttpResult(200, """{"truncated":false,"tree":[$entries]}""") }
        try {
            GitSyncFetcher(http).fetch(GitSyncConfig("o", "r", "main", "t"), SHA_A)
            fail()
        } catch (e: GitSyncException) {
            assertTrue(e.message!!, e.message!!.contains("Too many files"))
        }
    }

    @Test
    fun fetcherPassesSizeCaps() {
        val caps = mutableListOf<Int>()
        val http = HttpGet { url, _, max ->
            caps += max
            if ("git/trees" in url) HttpResult(200, """{"tree":[{"path":"match/a.yml","type":"blob"}]}""") else HttpResult(200, "x")
        }
        GitSyncFetcher(http).fetch(GitSyncConfig("o", "r", "main", "t"), SHA_A)
        assertEquals(listOf(GitHubApi.MAX_TREE_BYTES, GitHubApi.MAX_FILE_BYTES), caps)
    }

    @Test
    fun readCappedEnforcesLimit() {
        assertEquals("abc", HttpUrlConnectionGet.readCapped("abc".byteInputStream(), 3))
        try {
            HttpUrlConnectionGet.readCapped("abcd".byteInputStream(), 3)
            fail()
        } catch (e: GitSyncException) {
            assertTrue(e.message!!.contains("limit"))
        }
    }

    @Test
    fun redirectsAreNotFollowedAndOversizeIsRejected() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var targetHit = false
        server.createContext("/target") { ex -> targetHit = true; ex.sendResponseHeaders(200, -1); ex.close() }
        server.createContext("/redirect") { ex ->
            ex.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/target")
            ex.sendResponseHeaders(302, -1); ex.close()
        }
        server.createContext("/notmodified") { ex -> ex.sendResponseHeaders(304, -1); ex.close() }
        server.createContext("/big") { ex ->
            val body = ByteArray(5000) { 'a'.code.toByte() }
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val client = HttpUrlConnectionGet(2000)
            try {
                client.get("$base/redirect", mapOf("Authorization" to "Bearer t"), 1000)
                fail()
            } catch (e: GitSyncException) {
                assertTrue(e.message!!, e.message!!.contains("redirect"))
                assertEquals(302, e.status)
            }
            assertTrue("redirect target must not be requested", !targetHit)
            assertEquals(304, client.get("$base/notmodified", emptyMap(), 1000).status)
            try {
                client.get("$base/big", emptyMap(), 1000)
                fail()
            } catch (e: GitSyncException) {
                assertTrue(e.message!!.contains("limit"))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun redirectStatusesMapToClearError() {
        listOf(301, 302, 307, 308).forEach {
            assertTrue(GitHubApi.errorFor(it).message!!.contains("redirect"))
        }
    }
}
