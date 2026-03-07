package dev.trooped.tvquickbars.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.services.HAConnectionService
import dev.trooped.tvquickbars.persistence.SecurePrefsManager

/**
 * A [BroadcastReceiver] that listens for boot completed events.
 *
 * When the device boots up, this receiver checks if persistent connection is enabled in app preferences.
 * If it is, it starts the [HAConnectionService] to establish a connection with Home Assistant.
 *
 * This receiver is registered in the AndroidManifest.xml file to receive the following actions:
 * - [Intent.ACTION_BOOT_COMPLETED]
 * - "android.intent.action.QUICKBOOT_POWERON" (for some devices that use a different boot completed action)
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        if (AppPrefs.isPersistentConnectionEnabled(context)) {
            val svc = Intent(context, dev.trooped.tvquickbars.services.HAConnectionService::class.java)

            ContextCompat.startForegroundService(context, svc)
        }
    }
}