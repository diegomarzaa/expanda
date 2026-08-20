package dev.diego.expanda

import android.content.Intent
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.diego.expanda.data.ThemeMode
import dev.diego.expanda.data.ColorSchemeMode
import dev.diego.expanda.data.TextSizeMode
import dev.diego.expanda.service.AccessibilityStatus
import dev.diego.expanda.ui.ExpandaApp
import dev.diego.expanda.ui.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val openNewSnippetRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openNewSnippetRequest.value = intent.getBooleanExtra(dev.diego.expanda.service.ExpansionAccessibilityService.EXTRA_OPEN_NEW_SNIPPET, false)
        setContent { ExpandaRoot(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(dev.diego.expanda.service.ExpansionAccessibilityService.EXTRA_OPEN_NEW_SNIPPET, false)) {
            openNewSnippetRequest.value = true
        }
    }

    @Composable
    private fun ExpandaRoot(viewModel: MainViewModel) {
        val state by viewModel.uiState.collectAsState()
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        var serviceEnabled = remember { androidx.compose.runtime.mutableStateOf(AccessibilityStatus.isEnabled(this)) }
        var backgroundAllowed by remember { mutableStateOf(isBackgroundOperationAllowed()) }
        var exportCsv by remember { mutableStateOf(false) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            serviceEnabled.value = AccessibilityStatus.isEnabled(this)
            backgroundAllowed = isBackgroundOperationAllowed()
            if (state.settings.clipboardHistoryEnabled) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
                    ?.toString()?.takeIf(String::isNotBlank)?.let(viewModel::captureClipboard)
            }
        }

        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            if (uri != null) scope.launch {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                        it.write(if (exportCsv) viewModel.exportCsv() else viewModel.exportBackup())
                    }
                }.onSuccess { snackbar.showSnackbar("Backup exported") }
                    .onFailure { snackbar.showSnackbar("Export failed: ${it.message}") }
            }
        }
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) scope.launch {
                runCatching { contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
                    .onSuccess { json ->
                        viewModel.importData(json) { result ->
                            scope.launch { snackbar.showSnackbar(result.fold(
                                onSuccess = { "Imported $it snippets" },
                                onFailure = { "Import failed: ${it.message}" },
                            )) }
                        }
                    }.onFailure { snackbar.showSnackbar("Import failed: ${it.message}") }
            }
        }

        val dark = when (state.settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AMOLED -> true
        }
        val baseColors = when (state.settings.colorSchemeMode) {
            ColorSchemeMode.WALLPAPER -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else if (dark) darkColorScheme() else lightColorScheme()
            ColorSchemeMode.CUSTOM -> {
                val seed = Color(state.settings.customColor)
                if (dark) darkColorScheme(primary = seed, secondary = seed)
                else lightColorScheme(primary = seed, secondary = seed)
            }
            ColorSchemeMode.DEFAULT -> if (dark) darkColorScheme() else lightColorScheme()
        }
        val colors = if (state.settings.themeMode == ThemeMode.AMOLED) {
            baseColors.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color(0xFF080808),
            )
        } else baseColors
        val textScale = when (state.settings.textSizeMode) {
            TextSizeMode.SMALL -> 0.90f
            TextSizeMode.DEFAULT -> 1f
            TextSizeMode.LARGE -> 1.12f
        }
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * textScale),
        ) {
            MaterialTheme(colorScheme = colors) {
                ExpandaApp(
                state = state,
                serviceEnabled = serviceEnabled.value,
                backgroundAllowed = backgroundAllowed,
                viewModel = viewModel,
                snackbarHostState = snackbar,
                openNewSnippetRequest = openNewSnippetRequest.value,
                onNewSnippetRequestConsumed = { openNewSnippetRequest.value = false },
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onOpenBackgroundSettings = ::openBackgroundSettings,
                onExportJson = { exportCsv = false; exportLauncher.launch("expanda-backup.json") },
                onExportCsv = { exportCsv = true; exportLauncher.launch("expanda-snippets.csv") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/csv", "text/plain")) },
                )
            }
        }
    }

    private fun isBackgroundOperationAllowed(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBackgroundSettings() {
        val packageUri = Uri.parse("package:$packageName")
        val directRequest = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(directRequest) }
            .recoverCatching { startActivity(fallback) }
            .onFailure { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)) }
    }
}
