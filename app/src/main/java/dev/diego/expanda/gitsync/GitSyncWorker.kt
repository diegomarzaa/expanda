package dev.diego.expanda.gitsync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.diego.expanda.ExpandaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class GitSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ExpandaApplication
        val prefs = GitSyncPrefs(app)
        if (!prefs.loadConfig().configured) return Result.success()
        app.settingsRepository.awaitLoaded()
        if (app.settingsRepository.settings.value.espansoFolderUri != null) return Result.success()
        // Failures are recorded in prefs; the next periodic run retries, so no WorkManager backoff.
        createGitSyncer(app, prefs).sync()
        return Result.success()
    }
}

object GitSyncScheduler {
    /**
     * Persisted name of the enqueued work; do not change it. Schedule uses UPDATE so a renamed worker class
     * replaces the one already queued.
     */
    private const val UNIQUE_NAME = "github-sync-periodic"

    /** Battery over freshness: periodic checks are cheap, but every wake-up costs. */
    const val SYNC_INTERVAL_HOURS = 12L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<GitSyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        GitSyncPrefs(context).setScheduled(true)
    }

    /** Touches WorkManager only if this feature scheduled something before. */
    fun cancel(context: Context) {
        val prefs = GitSyncPrefs(context)
        if (!prefs.isScheduled()) return
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        prefs.setScheduled(false)
    }

    /** Call on app start: schedule if configured, otherwise make sure nothing is queued. Runs off the main thread. */
    fun reconcile(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            if (GitSyncPrefs(appContext).loadConfig().configured) schedule(appContext) else cancel(appContext)
        }
    }
}
