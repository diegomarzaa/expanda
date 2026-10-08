package dev.diego.expanda.gitsync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.diego.expanda.ExpandaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs a non-forced sync when the app (process) comes to the foreground and the last check is stale. */
object GitSyncForeground {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register(app: ExpandaApplication) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch {
                    val prefs = GitSyncPrefs(app)
                    val configured = prefs.loadConfig().configured
                    if (!configured) return@launch
                    app.settingsRepository.awaitLoaded()
                    val linked = app.settingsRepository.settings.value.espansoFolderUri != null
                    if (SyncOnOpenPolicy.shouldSync(configured, linked, prefs.loadStatus().lastCheckedAt, System.currentTimeMillis())) {
                        createGitSyncer(app, prefs).sync(force = false)
                    }
                }
            }
        })
    }
}
