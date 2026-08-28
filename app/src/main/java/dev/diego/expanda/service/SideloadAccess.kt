package dev.diego.expanda.service

import android.content.Context
import android.os.Build

enum class InstallSource {
    PLAY_STORE,
    OTHER_STORE,
    SIDELOAD,
}

/**
 * Android 13+ blocks sideloaded apps from enabling Accessibility until the user
 * explicitly allows restricted settings in App info.
 *
 * Normal apps cannot query the restricted-settings AppOp on current Android
 * versions (the call throws [SecurityException]), so guidance is inferred from
 * install source and whether Accessibility is already enabled.
 */
object SideloadAccess {
    fun installSource(context: Context): InstallSource {
        val packageName = context.packageName
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = manager.getInstallSourceInfo(packageName)
            when (info.installingPackageName) {
                "com.android.vending" -> InstallSource.PLAY_STORE
                null -> InstallSource.SIDELOAD
                else -> InstallSource.OTHER_STORE
            }
        } else {
            @Suppress("DEPRECATION")
            when (manager.getInstallerPackageName(packageName)) {
                "com.android.vending" -> InstallSource.PLAY_STORE
                null -> InstallSource.SIDELOAD
                else -> InstallSource.OTHER_STORE
            }
        }
    }

    fun needsRestrictedSettingsGuidance(
        context: Context,
        accessibilityEnabled: Boolean,
        hintDismissed: Boolean = false,
    ): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            installSource(context) != InstallSource.PLAY_STORE &&
            !accessibilityEnabled &&
            !hintDismissed
}
