package dev.diego.expanda

import android.app.Application
import dev.diego.expanda.data.ExpandaDatabase
import dev.diego.expanda.data.SnippetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExpandaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database by lazy { ExpandaDatabase(this) }
    val repository by lazy { SnippetRepository(database) }
    val clipboardRepository by lazy { dev.diego.expanda.data.ClipboardRepository(database) }
    val settingsRepository by lazy { dev.diego.expanda.data.SettingsRepository(this, applicationScope) }
    val actionSettingsStore by lazy { dev.diego.expanda.data.ActionSettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        settingsRepository
        actionSettingsStore
        applicationScope.launch {
            repository.refresh()
            clipboardRepository.refresh()
        }
    }
}
