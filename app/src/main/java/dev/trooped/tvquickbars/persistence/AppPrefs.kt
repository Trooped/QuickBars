package dev.trooped.tvquickbars.persistence

import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import androidx.core.content.edit
import dev.trooped.tvquickbars.receivers.BootCompletedReceiver

/**
 * Utility object for managing application preferences.
 *
 * This object provides methods for accessing and modifying shared preferences
 * related to the application's behavior.
 */
object AppPrefs {
    private const val FILE = "app_prefs"
    private const val KEY_PERSISTENT_CONN = "pref_persistent_connection_enabled"
    private const val KEY_FIRST_TIME_SETUP = "pref_first_time_setup_in_progress"
    private const val KEY_LAST_SEEN_VERSION = "pref_last_seen_version"
    private const val KEY_SHOW_TOAST_ON_ENTITY_TRIGGER = "pref_show_toast_on_entity_trigger"

    fun isPersistentConnectionEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, MODE_PRIVATE)
            .getBoolean(KEY_PERSISTENT_CONN, false)

    fun setPersistentConnectionEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, MODE_PRIVATE).edit {
            putBoolean(KEY_PERSISTENT_CONN, enabled)
        }
        setBootReceiverEnabled(ctx, enabled)
    }

    fun hasPersistentConnectionFlag(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, MODE_PRIVATE).contains(KEY_PERSISTENT_CONN)


    fun isFirstTimeSetupInProgress(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, MODE_PRIVATE)
            .getBoolean(KEY_FIRST_TIME_SETUP, false)

    fun setFirstTimeSetupInProgress(ctx: Context, inProgress: Boolean) {
        ctx.getSharedPreferences(FILE, MODE_PRIVATE)
            .edit {
                putBoolean(KEY_FIRST_TIME_SETUP, inProgress)
            }
    }

    /**
     * Enables or disables the BOOT_COMPLETED receiver component.
     * Call this ONLY from setPersistentConnectionEnabled or one-time at app start to resync.
     */
    fun setBootReceiverEnabled(ctx: Context, enabled: Boolean) {
        val pm = ctx.packageManager
        val cn = ComponentName(ctx, BootCompletedReceiver::class.java)
        pm.setComponentEnabledSetting(
            cn,
            if (enabled)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }


    /**
     * Gets the last app version code that showed the "What's New" dialog
     */
    fun getLastSeenVersion(ctx: Context): Int {
        return ctx.getSharedPreferences(FILE, MODE_PRIVATE).getInt(KEY_LAST_SEEN_VERSION, 0)
    }

    /**
     * Sets the last seen version code after showing the "What's New" dialog
     */
    fun setLastSeenVersion(ctx: Context, versionCode: Int) {
        ctx.getSharedPreferences(FILE, MODE_PRIVATE).edit {
            putInt(KEY_LAST_SEEN_VERSION, versionCode)
        }
    }

    /**
     * Checks if the current app version is newer than the last version the user saw
     * Returns true if the app has been updated since last launch
     */
    fun isAppUpdated(ctx: Context): Boolean {
        val currentVersion = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
        val lastSeenVersion = getLastSeenVersion(ctx)
        return currentVersion > lastSeenVersion
    }

    /**
     * Gets the current app version code
     */
    fun getCurrentAppVersion(ctx: Context): Int {
        return ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
    }

    fun isShowToastOnEntityTriggerEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, MODE_PRIVATE)
            // default = true if not yet written
            .getBoolean(KEY_SHOW_TOAST_ON_ENTITY_TRIGGER, true)

    fun setShowToastOnEntityTriggerEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, MODE_PRIVATE).edit {
            putBoolean(KEY_SHOW_TOAST_ON_ENTITY_TRIGGER, enabled)
        }
    }

    /**
     * Ensure the show-toast flag exists and defaults to true.
     * Called once at app startup so both new and existing users start with it enabled.
     */
    fun ensureShowToastOnEntityTriggerDefault(ctx: Context) {
        val prefs = ctx.getSharedPreferences(FILE, MODE_PRIVATE)
        if (!prefs.contains(KEY_SHOW_TOAST_ON_ENTITY_TRIGGER)) {
            prefs.edit {
                putBoolean(KEY_SHOW_TOAST_ON_ENTITY_TRIGGER, true)
            }
        }
    }
}