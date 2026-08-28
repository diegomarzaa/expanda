package dev.diego.expanda.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.diego.expanda.data.AppSettings
import dev.diego.expanda.data.ColorSchemeMode
import dev.diego.expanda.data.ThemeMode

/** Semantic colors and sizing shared by Compose and WindowManager overlays. */
data class NativeThemeTokens(
    val dark: Boolean,
    val background: Int,
    val surface: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val outline: Int,
    val warningContainer: Int,
    val onWarningContainer: Int,
    val scrim: Int,
    val textScale: Float,
)

private val fallbackTokens = lightColorScheme().toNativeTokens(false, 1f)
val LocalNativeThemeTokens = staticCompositionLocalOf { fallbackTokens }

@Composable
fun ExpandaTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val colors = resolveColorScheme(context, settings, dark)
    val textScale = settings.textScale
    val nativeTokens = colors.toNativeTokens(dark, textScale)
    val baseDensity = LocalDensity.current

    SideEffect {
        (context as? Activity)?.let { activity ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val lightBars = if (dark) 0 else {
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                }
                activity.window.insetsController?.setSystemBarsAppearance(
                    lightBars,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                )
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = if (dark) 0 else {
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * textScale),
        LocalNativeThemeTokens provides nativeTokens,
    ) {
        MaterialTheme(
            colorScheme = colors,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colors.background,
                contentColor = colors.onBackground,
                content = content,
            )
        }
    }
}

fun resolveNativeTheme(context: Context, settings: AppSettings): NativeThemeTokens {
    val dark = isDarkTheme(context, settings.themeMode)
    return resolveColorScheme(context, settings, dark)
        .toNativeTokens(dark, settings.textScale)
}

fun isDarkTheme(context: Context, mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
    ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
}

fun resolveColorScheme(context: Context, settings: AppSettings, dark: Boolean): ColorScheme {
    val base = when (settings.colorSchemeMode) {
        ColorSchemeMode.WALLPAPER -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme() else lightColorScheme()
        ColorSchemeMode.DEFAULT -> if (dark) darkColorScheme() else lightColorScheme()
        ColorSchemeMode.CUSTOM -> customColorScheme(Color(settings.customColor), dark)
    }
    return if (settings.themeMode == ThemeMode.AMOLED) base.asAmoled() else base
}

private fun customColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val primary = if (dark) lerp(seed, Color.White, 0.28f) else lerp(seed, Color.Black, 0.12f)
    val primaryContainer = if (dark) lerp(seed, Color.Black, 0.52f) else lerp(seed, Color.White, 0.72f)
    val secondary = lerp(primary, base.secondary, 0.38f)
    val secondaryContainer = if (dark) lerp(secondary, Color.Black, 0.54f)
    else lerp(secondary, Color.White, 0.74f)
    val tertiary = lerp(primary, base.tertiary, 0.52f)
    val tertiaryContainer = if (dark) lerp(tertiary, Color.Black, 0.56f)
    else lerp(tertiary, Color.White, 0.76f)
    return base.copy(
        primary = primary,
        onPrimary = contentColor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = contentColor(primaryContainer),
        secondary = secondary,
        onSecondary = contentColor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = contentColor(secondaryContainer),
        tertiary = tertiary,
        onTertiary = contentColor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = contentColor(tertiaryContainer),
    )
}

private fun ColorScheme.asAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF181818),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF040404),
    surfaceContainer = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF101010),
    surfaceContainerHighest = Color(0xFF181818),
    surfaceVariant = Color(0xFF1B1B1B),
)

private fun contentColor(background: Color): Color =
    if (background.luminance() > 0.43f) Color.Black else Color.White

private fun ColorScheme.toNativeTokens(dark: Boolean, textScale: Float): NativeThemeTokens {
    val warningContainer = if (dark) Color(0xFF4A2C00) else Color(0xFFFFDDB3)
    val onWarningContainer = if (dark) Color(0xFFFFDDB3) else Color(0xFF2A1700)
    return NativeThemeTokens(
        dark = dark,
        background = background.toArgb(),
        surface = surface.toArgb(),
        surfaceContainer = surfaceContainer.toArgb(),
        surfaceContainerHigh = surfaceContainerHigh.toArgb(),
        onSurface = onSurface.toArgb(),
        onSurfaceVariant = onSurfaceVariant.toArgb(),
        primary = primary.toArgb(),
        onPrimary = onPrimary.toArgb(),
        primaryContainer = primaryContainer.toArgb(),
        onPrimaryContainer = onPrimaryContainer.toArgb(),
        secondaryContainer = secondaryContainer.toArgb(),
        onSecondaryContainer = onSecondaryContainer.toArgb(),
        outline = outline.toArgb(),
        warningContainer = warningContainer.toArgb(),
        onWarningContainer = onWarningContainer.toArgb(),
        scrim = scrim.toArgb(),
        textScale = textScale,
    )
}
