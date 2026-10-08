package dev.diego.expanda.gitsync

import dev.diego.expanda.data.EspansoSourceFile
import dev.diego.expanda.data.EspansoSourceImportResult
import dev.diego.expanda.data.SourceFolderLinkedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitSyncerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val config = GitSyncConfig("o", "r", "", VALID_TOKEN)
    private val key = config.repoKey

    private class FakeStore(var config: GitSyncConfig, var applied: AppliedState, val events: MutableList<String>) : GitSyncStore {
        var failure: String? = null
        var unchanged = false
        var savedEtag: String? = null
        var waiting = false
        override fun loadConfig() = config
        override fun loadApplied() = applied
        override fun saveApplied(matches: Int, issues: List<StoredIssue>, applied: AppliedState, at: Long) {
            events += "saveApplied"
            this.applied = applied
        }
        override fun saveFailure(message: String, at: Long) { failure = message }
        override fun saveUnchanged(etag: String?, at: Long) { unchanged = true; savedEtag = etag }
        override fun saveWaitingForFirstSync(at: Long) { waiting = true }
    }

    private class FakeWorkspace(val events: MutableList<String>) : SyncWorkspace {
        var linked = false
        var files = 4
        var matches = 10
        var decodable = 5
        var replaceError: Exception? = null
        var backupError: Exception? = null
        var replaced: List<EspansoSourceFile>? = null
        var readyError: Exception? = null
        override fun isLinked() = linked
        override suspend fun awaitReady() { readyError?.let { throw it } }
        override fun localFileCount() = files
        override fun localMatchCount() = matches
        override fun decodableMatchCount(files: List<EspansoSourceFile>) = decodable
        override suspend fun backup() {
            backupError?.let { throw it }
            events += "backup"
        }
        override suspend fun replaceAll(files: List<EspansoSourceFile>): EspansoSourceImportResult {
            events += "replace"
            replaceError?.let { throw it }
            replaced = files
            return EspansoSourceImportResult(decodable, emptyList())
        }
    }

    /** Serves commit, tree and contents; [commit] decides the commit response. */
    private class FakeHttp(var commit: (Map<String, String>) -> HttpResult, var tree: HttpResult? = null) : HttpGet {
        val urls = mutableListOf<String>()
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResult {
            urls += url
            return when {
                "/commits/" in url -> commit(headers)
                "git/trees" in url -> tree ?: HttpResult(200, """{"tree":[{"path":"match/a.yml","type":"blob"},{"path":"match/b.yml","type":"blob"}]}""")
                else -> HttpResult(200, "matches: []\n")
            }
        }
    }

    private val events = mutableListOf<String>()
    private val workspace = FakeWorkspace(events)
    private fun store(applied: AppliedState) = FakeStore(config, applied, events)
    private fun http(sha: String = SHA_B) = FakeHttp({ HttpResult(200, sha, etag = "\"e\"") })
    private fun syncer(http: FakeHttp, store: FakeStore) = GitSyncer(workspace, store, GitSyncFetcher(http))
    private val applied = AppliedState(SHA_A, "\"old\"", key)

    private fun run(s: GitSyncer, force: Boolean = false, manual: Boolean = false) =
        runBlocking { s.sync(force, manual) }

    @Test fun failureLeavesWorkspaceUntouchedAndShaNotSaved() {
        val store = store(applied)
        val http = FakeHttp({ HttpResult(200, SHA_B, etag = "\"e\"") }, tree = HttpResult(500, ""))
        val r = run(syncer(http, store))
        assertTrue(r.isFailure)
        assertNull(workspace.replaced)
        assertFalse("replace" in events)
        assertEquals(applied, store.applied)
        assertNotNull(store.failure)
        assertFalse(store.failure!!.contains(VALID_TOKEN))
    }

    @Test fun successSavesShaAfterApplying() {
        val store = store(applied)
        val r = run(syncer(http(), store))
        assertTrue(r.getOrThrow() is SyncOutcome.Applied)
        assertEquals(listOf("replace", "saveApplied"), events)
        assertEquals(AppliedState(SHA_B, "\"e\"", key), store.applied)
        assertEquals(2, workspace.replaced!!.size)
    }

    @Test fun downloadIsPinnedToTheCheckedCommit() {
        val http = http()
        run(syncer(http, store(applied)))
        assertTrue(http.urls[0], http.urls[0].endsWith("/commits/HEAD"))
        http.urls.drop(1).forEach { assertTrue(it, SHA_B in it) }
    }

    @Test fun unchangedDoesNothing() {
        val store = store(applied)
        val http = FakeHttp({ HttpResult(304, "") })
        val r = run(syncer(http, store))
        assertTrue(r.getOrThrow() is SyncOutcome.Unchanged)
        assertTrue(store.unchanged)
        assertTrue(events.isEmpty())
        assertEquals(1, http.urls.size)
    }

    @Test fun linkedFolderIsRefusedWithoutNetwork() {
        workspace.linked = true
        val http = http()
        val store = store(applied)
        val r = run(syncer(http, store), manual = true)
        assertTrue(r.isFailure)
        assertTrue(http.urls.isEmpty())
        assertEquals(GitSyncer.LINKED_MESSAGE, store.failure)
    }

    @Test fun linkRaceDetectedUnderTheRepositoryLockIsRefused() {
        workspace.replaceError = SourceFolderLinkedException()
        val store = store(applied)
        val r = run(syncer(http(), store))
        assertTrue(r.isFailure)
        assertEquals(GitSyncer.LINKED_MESSAGE, store.failure)
        assertEquals(applied, store.applied)
    }

    @Test fun backgroundNeverDoesTheFirstApply() {
        val store = store(AppliedState())
        val http = http()
        val r = run(syncer(http, store), manual = false)
        assertTrue(r.getOrThrow() is SyncOutcome.WaitingForFirstSync)
        assertTrue(store.waiting)
        assertTrue(http.urls.isEmpty())
        assertTrue(events.isEmpty())
        assertNull(store.failure)
    }

    @Test fun changedRepositoryCountsAsFirstApplyAgain() {
        val store = store(AppliedState(SHA_A, "\"old\"", "o/other@"))
        val r = run(syncer(http(), store), manual = false)
        assertTrue(r.getOrThrow() is SyncOutcome.WaitingForFirstSync)
        assertTrue(events.isEmpty())
    }

    @Test fun manualFirstApplyNeedsConfirmationThenBacksUpBeforeReplacing() {
        val store = store(AppliedState())
        val s = syncer(http(), store)
        val outcome = run(s, force = true, manual = true).getOrThrow() as SyncOutcome.NeedsConfirmation
        assertEquals(4, outcome.pending.localFiles)
        assertEquals(2, outcome.pending.remoteFiles)
        assertTrue("nothing may change before confirmation", events.isEmpty())
        assertNull(store.applied.sha)

        assertTrue(runBlocking { s.applyConfirmed(outcome.pending) }.getOrThrow() is SyncOutcome.Applied)
        assertEquals(listOf("backup", "replace", "saveApplied"), events)
        assertEquals(AppliedState(SHA_B, "\"e\"", key), store.applied)
    }

    @Test fun failedBackupMeansNothingIsReplaced() {
        val store = store(AppliedState())
        val s = syncer(http(), store)
        val pending = (run(s, force = true, manual = true).getOrThrow() as SyncOutcome.NeedsConfirmation).pending
        workspace.backupError = java.io.IOException("disk secret details")
        val r = runBlocking { s.applyConfirmed(pending) }
        assertTrue(r.isFailure)
        assertFalse("replace" in events)
        assertNull(store.applied.sha)
        assertFalse(store.failure!!.contains("secret"))
    }

    @Test fun cancellationIsRethrownAndNotRecorded() {
        workspace.replaceError = CancellationException("stop")
        val store = store(applied)
        try {
            run(syncer(http(), store))
            fail("expected cancellation")
        } catch (_: CancellationException) {
        }
        assertNull(store.failure)
        assertEquals(applied, store.applied)
    }

    @Test fun refusesWhenNothingDecodesButSnippetsExist() {
        workspace.decodable = 0
        val store = store(applied)
        val r = run(syncer(http(), store))
        assertTrue(r.isFailure)
        assertFalse("replace" in events)
        assertEquals(applied, store.applied)
        assertTrue(store.failure!!, store.failure!!.contains("not replaced"))
    }

    @Test fun emptyDecodeIsFineWhenWorkspaceIsEmpty() {
        workspace.decodable = 0
        workspace.matches = 0
        assertTrue(run(syncer(http(), store(applied))).isSuccess)
    }

    @Test fun refusalAlsoGuardsTheConfirmedFirstApply() {
        workspace.decodable = 0
        val store = store(AppliedState())
        assertTrue(run(syncer(http(), store), force = true, manual = true).isFailure)
        assertTrue(events.isEmpty())
    }

    @Test fun unexpectedErrorsRecordOnlyTheClassName() {
        workspace.replaceError = IllegalStateException("token=$VALID_TOKEN path=/data/x")
        val store = store(applied)
        val r = run(syncer(http(), store))
        assertTrue(r.isFailure)
        assertTrue(store.failure!!, store.failure!!.contains("IllegalStateException"))
        assertFalse(store.failure!!.contains(VALID_TOKEN))
        assertFalse(r.exceptionOrNull()!!.message!!.contains(VALID_TOKEN))
    }

    @Test fun backupsKeepOnlyTheNewestThree() {
        var now = 1_700_000_000_000L
        val backups = SyncBackups(tmp.newFolder("b"), keep = 3, clock = { now++ * 1000 })
        repeat(5) { backups.save("{\"n\":$it}") }
        val files = backups.files()
        assertEquals(3, files.size)
        assertEquals("{\"n\":4}", files.first().readText())
        assertEquals("{\"n\":2}", files.last().readText())
        assertTrue(files.all { it.name.endsWith(".json") })
    }

    @Test fun unchangedWithNewEtagSavesTheEtag() {
        val store = store(applied)
        val http = FakeHttp({ HttpResult(200, SHA_A, etag = "\"new\"") })
        assertTrue(run(syncer(http, store)).getOrThrow() is SyncOutcome.Unchanged)
        assertEquals("\"new\"", store.savedEtag)
    }

    @Test fun notReadyWorkspaceFailsClearlyBeforeAnyWork() {
        workspace.readyError = GitSyncException("Expanda is still loading your snippets. Try again in a moment.")
        val store = store(AppliedState())
        val http = http()
        val r = run(syncer(http, store), manual = true)
        assertTrue(r.isFailure)
        assertTrue(store.failure!!.contains("still loading"))
        assertTrue(http.urls.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test fun exclusiveWaitsForARunningSync() {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        val http = FakeHttp({
            started.countDown()
            release.await()
            order += "sync-end"
            HttpResult(200, SHA_B, etag = "\"e\"")
        })
        runBlocking {
            val syncJob = launch(Dispatchers.Default) {
                syncer(http, store(applied)).sync()
            }
            started.await()
            val clearJob = launch(Dispatchers.Default) {
                GitSyncer.exclusive { order += "exclusive" }
            }
            Thread.sleep(150)
            assertEquals("exclusive must wait while a sync runs", emptyList<String>(), order.toList())
            release.countDown()
            syncJob.join()
            clearJob.join()
        }
        assertEquals(listOf("sync-end", "exclusive"), order.toList())
    }

    @Test fun syncingFlagIsResetWhenTheSyncThrows() {
        val model = SyncUiModel()
        assertTrue(model.claim())
        assertTrue(model.state.value.syncing)
        try {
            runBlocking { model.runClaimed { throw IllegalStateException("boom") } }
            fail("expected throw")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals(GitSyncUiState(), model.state.value)
        assertTrue("a new sync can start", model.claim())
    }

    @Test fun pendingConfirmationIsKeptThenDroppedOnDismissAndExpiry() {
        val model = SyncUiModel()
        val pending = PendingReplace(1, 2, emptyList(), BranchCheck.Changed(SHA_B, null), key)
        assertTrue(model.claim())
        runBlocking { model.runClaimed { pending } }
        assertTrue(model.state.value.pending === pending)
        assertFalse("no second sync while confirming", model.claim())
        model.dismiss()
        assertNull(model.state.value.pending)

        assertTrue(model.claim())
        runBlocking { model.runClaimed { pending } }
        model.expire(pending)
        assertNull(model.state.value.pending)
    }

    @Test fun tokenFileRoundTripsAndDeletes() {
        val file = TokenFile(java.io.File(tmp.newFolder("nb"), "tok"))
        assertEquals("", file.read())
        file.write(VALID_TOKEN)
        assertEquals(VALID_TOKEN, file.read())
        file.write(VALID_TOKEN + "x")
        assertEquals(VALID_TOKEN + "x", file.read())
        file.delete()
        assertEquals("", file.read())
    }

    @Test fun legacyTokenMigratesOnceAndDoesNotOverwrite() {
        val file = TokenFile(java.io.File(tmp.newFolder("nb2"), "tok"))
        TokenFile.migrate("  $VALID_TOKEN \n", file)
        assertEquals(VALID_TOKEN, file.read())
        TokenFile.migrate("other_legacy_token_value_1234", file)
        assertEquals(VALID_TOKEN, file.read())
        val empty = TokenFile(java.io.File(tmp.newFolder("nb3"), "tok"))
        TokenFile.migrate(null, empty)
        TokenFile.migrate("  ", empty)
        assertEquals("", empty.read())
    }

    @Test fun newestBackupIsTheLatestSave() {
        var now = 1_700_000_000_000L
        val backups = SyncBackups(tmp.newFolder("b2"), clock = { now++ * 1000 })
        assertNull(backups.newest())
        backups.save("{\"n\":1}")
        backups.save("{\"n\":2}")
        assertEquals("{\"n\":2}", backups.newest()!!.readText())
    }
}
