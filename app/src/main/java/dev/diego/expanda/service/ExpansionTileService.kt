package dev.diego.expanda.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.diego.expanda.ExpandaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ExpansionTileService : TileService() {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings by lazy { (application as ExpandaApplication).settingsRepository }
    private var observation: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observation?.cancel()
        observation = scope.launch {
            settings.settings.collectLatest { updateTile(it.expansionEnabled && !it.isPaused) }
        }
    }

    override fun onStopListening() {
        observation?.cancel()
        observation = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val enable = !settings.settings.value.expansionEnabled || settings.settings.value.isPaused
        scope.launch {
            if (enable) {
                settings.resume()
                settings.setEnabled(true)
            } else settings.setEnabled(false)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTile(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (active) "On" else "Off"
            }
            updateTile()
        }
    }
}
