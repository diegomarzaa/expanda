package dev.diego.expanda

import android.app.Application
import dev.diego.expanda.data.ClipboardMonitor
import dev.diego.expanda.data.ExpandaDatabase
import dev.diego.expanda.data.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExpandaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database by lazy { ExpandaDatabase(this) }
    val matchRepository by lazy { MatchRepository(database) }
    val clipboardRepository by lazy { dev.diego.expanda.data.ClipboardRepository(database) }
    val clipboardMonitor by lazy {
        ClipboardMonitor(
            context = this,
            historyReader = { database.latestClipboardText() },
            historyWriter = { text ->
                applicationScope.launch {
                    if (settingsRepository.settings.value.clipboardHistoryEnabled) {
                        clipboardRepository.add(text)
                    }
                }
            },
        )
    }
    val settingsRepository by lazy { dev.diego.expanda.data.SettingsRepository(this, applicationScope) }
    val espansoSourceRepository by lazy {
        dev.diego.expanda.data.EspansoSourceRepository(this, matchRepository, settingsRepository)
    }
    val onboardingStore by lazy { dev.diego.expanda.data.OnboardingStore(this, applicationScope) }
    val actionSettingsStore by lazy { dev.diego.expanda.data.ActionSettingsStore(this) }

    fun consumePendingTutorialAfterUpgrade(): Boolean = database.consumePendingTutorialAfterUpgrade()

    override fun onCreate() {
        super.onCreate()
        settingsRepository
        espansoSourceRepository
        onboardingStore
        actionSettingsStore
        clipboardMonitor.start()
        applicationScope.launch {
            matchRepository.refresh()
            espansoSourceRepository.initialize()
            clipboardRepository.refresh()
            clipboardMonitor.capture()
        }
    }
}
