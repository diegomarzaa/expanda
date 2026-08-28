package dev.diego.expanda.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class OnboardingStatus { UNINITIALIZED, ACTIVE, COMPLETED, SKIPPED }

/** Tutorial content shipped with 0.3.0; bump when a new walkthrough should replay. */
const val CURRENT_TUTORIAL_GENERATION = 1

fun initialOnboardingStatus(
    hasMatches: Boolean,
    upgradedFromPre03: Boolean = false,
): OnboardingStatus = when {
    !hasMatches || upgradedFromPre03 -> OnboardingStatus.ACTIVE
    else -> OnboardingStatus.SKIPPED
}

/**
 * Decides whether the 0.3 tutorial should open on launch.
 *
 * Existing libraries normally skip the walkthrough, except when upgrading from
 * pre-0.3 storage or when an earlier 0.3 build auto-skipped before generation
 * tracking existed.
 */
fun shouldShowTutorial(
    state: OnboardingState,
    hasMatches: Boolean,
    upgradedFromPre03: Boolean,
): Boolean {
    if (state.tutorialGeneration >= CURRENT_TUTORIAL_GENERATION) return false
    return when (state.status) {
        OnboardingStatus.ACTIVE -> true
        OnboardingStatus.UNINITIALIZED -> initialOnboardingStatus(hasMatches, upgradedFromPre03) == OnboardingStatus.ACTIVE
        OnboardingStatus.SKIPPED -> upgradedFromPre03 || hasMatches
        OnboardingStatus.COMPLETED -> false
    }
}

data class OnboardingState(
    val status: OnboardingStatus = OnboardingStatus.UNINITIALIZED,
    val step: Int = 0,
    val workspaceReady: Boolean = false,
    val tutorialGeneration: Int = 0,
)

private val Context.onboardingDataStore by preferencesDataStore("onboarding")

/** Tutorial state is deliberately separate from app settings and snippet storage. */
class OnboardingStore(context: Context, scope: CoroutineScope) {
    private val store = context.onboardingDataStore

    val state: StateFlow<OnboardingState> = store.data.map { values ->
        OnboardingState(
            status = values[Keys.STATUS]?.let { runCatching { OnboardingStatus.valueOf(it) }.getOrNull() }
                ?: OnboardingStatus.UNINITIALIZED,
            step = (values[Keys.STEP] ?: 0).coerceAtLeast(0),
            workspaceReady = values[Keys.WORKSPACE_READY] ?: false,
            tutorialGeneration = values[Keys.TUTORIAL_GENERATION] ?: 0,
        )
    }.stateIn(scope, SharingStarted.Eagerly, OnboardingState())

    suspend fun activate(step: Int = 0) = update(OnboardingStatus.ACTIVE, step)
    suspend fun setStep(step: Int) = update(OnboardingStatus.ACTIVE, step)
    suspend fun complete() = markTutorialGenerationSeen(OnboardingStatus.COMPLETED)
    suspend fun skip() = markTutorialGenerationSeen(OnboardingStatus.SKIPPED)
    suspend fun finishWorkspaceSetup() = store.edit { it[Keys.WORKSPACE_READY] = true }
    suspend fun restart() = store.edit {
        it[Keys.STATUS] = OnboardingStatus.ACTIVE.name
        it[Keys.STEP] = 0
        it[Keys.WORKSPACE_READY] = false
        it[Keys.TUTORIAL_GENERATION] = 0
    }

    private suspend fun markTutorialGenerationSeen(status: OnboardingStatus) = store.edit {
        it[Keys.STATUS] = status.name
        it[Keys.STEP] = 0
        it[Keys.TUTORIAL_GENERATION] = CURRENT_TUTORIAL_GENERATION
    }

    private suspend fun update(status: OnboardingStatus, step: Int) = store.edit {
        it[Keys.STATUS] = status.name
        it[Keys.STEP] = step.coerceAtLeast(0)
    }

    private object Keys {
        val STATUS = stringPreferencesKey("status_v1")
        val STEP = intPreferencesKey("step_v1")
        val WORKSPACE_READY = booleanPreferencesKey("workspace_ready_v2")
        val TUTORIAL_GENERATION = intPreferencesKey("tutorial_generation_v1")
    }
}
