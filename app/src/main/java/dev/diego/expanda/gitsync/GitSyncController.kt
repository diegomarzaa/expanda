package dev.diego.expanda.gitsync

import dev.diego.expanda.ExpandaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitSyncUiState(
    val syncing: Boolean = false,
    /** A first apply waiting for the user to confirm (survives leaving and re-entering the screen). */
    val pending: PendingReplace? = null,
)

/** The UI state machine behind [GitSyncController], free of Android types so it can be unit-tested. */
internal class SyncUiModel {
    private val mutableState = MutableStateFlow(GitSyncUiState())
    val state: StateFlow<GitSyncUiState> = mutableState.asStateFlow()

    /** Marks a sync as running; false when one is running or a confirmation is waiting. */
    fun claim(): Boolean {
        while (true) {
            val current = mutableState.value
            if (current.syncing || current.pending != null) return false
            if (mutableState.compareAndSet(current, GitSyncUiState(syncing = true))) return true
        }
    }

    /**
     * Runs [block] for a claimed sync. Whatever happens (including a throw or cancellation) `syncing`
     * is reset; [block]'s result becomes the pending confirmation, if any.
     */
    suspend fun runClaimed(block: suspend () -> PendingReplace?) {
        var pending: PendingReplace? = null
        try {
            pending = block()
        } finally {
            mutableState.update { GitSyncUiState(pending = pending) }
        }
    }

    /** Switches a waiting confirmation to "applying"; null when there is nothing to confirm. */
    fun beginConfirm(): PendingReplace? {
        while (true) {
            val current = mutableState.value
            val pending = current.pending ?: return null
            if (current.syncing) return null
            if (mutableState.compareAndSet(current, GitSyncUiState(syncing = true))) return pending
        }
    }

    fun finishConfirm() {
        mutableState.update { GitSyncUiState() }
    }

    /** Drops a waiting confirmation (and the downloaded files it holds). */
    fun dismiss() {
        mutableState.update { if (it.syncing) it else GitSyncUiState() }
    }

    /** Drops [pending] if it is still the one waiting (used by the timeout). */
    fun expire(pending: PendingReplace) {
        mutableState.update { if (it.pending === pending && !it.syncing) GitSyncUiState() else it }
    }
}

/**
 * Runs "Sync now" in an application-level scope so leaving the settings screen does not cancel it.
 * The card observes [state] and the status in [GitSyncPrefs].
 */
object GitSyncController {
    /** A waiting confirmation is dropped after this long so downloaded files are not held indefinitely. */
    internal const val PENDING_TTL_MS = 10 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val model = SyncUiModel()
    val state: StateFlow<GitSyncUiState> = model.state

    fun syncNow(app: ExpandaApplication) {
        if (!model.claim()) return
        scope.launch {
            model.runClaimed {
                val prefs = GitSyncPrefs(app)
                if (prefs.loadConfig().configured) GitSyncScheduler.schedule(app)
                app.settingsRepository.awaitLoaded()
                val outcome = createGitSyncer(app, prefs).sync(force = true, manual = true).getOrNull()
                (outcome as? SyncOutcome.NeedsConfirmation)?.pending?.also(::expireLater)
            }
        }
    }

    private fun expireLater(pending: PendingReplace) {
        scope.launch {
            delay(PENDING_TTL_MS)
            model.expire(pending)
        }
    }

    fun confirmReplace(app: ExpandaApplication) {
        val pending = model.beginConfirm() ?: return
        scope.launch {
            try {
                createGitSyncer(app).applyConfirmed(pending)
            } finally {
                model.finishConfirm()
            }
        }
    }

    fun dismissReplace() = model.dismiss()
}
