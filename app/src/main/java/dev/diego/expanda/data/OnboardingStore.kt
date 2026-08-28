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

fun initialOnboardingStatus(hasMatches: Boolean): OnboardingStatus =
    if (hasMatches) OnboardingStatus.SKIPPED else OnboardingStatus.ACTIVE

data class OnboardingState(
    val status: OnboardingStatus = OnboardingStatus.UNINITIALIZED,
    val step: Int = 0,
    val workspaceReady: Boolean = false,
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
        )
    }.stateIn(scope, SharingStarted.Eagerly, OnboardingState())

    suspend fun activate(step: Int = 0) = update(OnboardingStatus.ACTIVE, step)
    suspend fun setStep(step: Int) = update(OnboardingStatus.ACTIVE, step)
    suspend fun complete() = update(OnboardingStatus.COMPLETED, 0)
    suspend fun skip() = update(OnboardingStatus.SKIPPED, 0)
    suspend fun finishWorkspaceSetup() = store.edit { it[Keys.WORKSPACE_READY] = true }
    suspend fun restart() = store.edit {
        it[Keys.STATUS] = OnboardingStatus.ACTIVE.name
        it[Keys.STEP] = 0
        it[Keys.WORKSPACE_READY] = false
    }

    private suspend fun update(status: OnboardingStatus, step: Int) = store.edit {
        it[Keys.STATUS] = status.name
        it[Keys.STEP] = step.coerceAtLeast(0)
    }

    private object Keys {
        val STATUS = stringPreferencesKey("status_v1")
        val STEP = intPreferencesKey("step_v1")
        val WORKSPACE_READY = booleanPreferencesKey("workspace_ready_v2")
    }
}
