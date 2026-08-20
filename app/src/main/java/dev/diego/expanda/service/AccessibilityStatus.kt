package dev.diego.expanda.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityStatus {
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ExpansionAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
