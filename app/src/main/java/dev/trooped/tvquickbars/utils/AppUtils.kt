package dev.trooped.tvquickbars.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import dev.trooped.tvquickbars.data.AppInfo

/**
 * Utility functions for managing applications and launching intents.
 * Provides methods to retrieve launchable apps and launch them by package name.
 */
@RequiresApi(Build.VERSION_CODES.M)
fun Context.getLeanbackLaunchables(): List<AppInfo> {
    val pm = packageManager
    val seen = mutableSetOf<String>()
    val out  = mutableListOf<AppInfo>()

    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    )

    intents.forEach { intent ->
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (seen.add(pkg)) {                      // skip duplicates
                out += AppInfo(
                    label       = ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon        = ri.loadIcon(pm)
                )
            }
        }
    }

    return out.sortedBy { it.label.lowercase() }
}

fun Context.launchPackage(pkg: String) {
    val intent = getBestLaunchIntent(pkg)
    if (intent == null) {
        Toast.makeText(this, "Cannot launch $pkg", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        // No exported activity matched (or got disabled between resolve and start)
        android.util.Log.w("launchPackage", "ActivityNotFound for $pkg with $intent", e)
        Toast.makeText(this, "Can’t open $pkg (no launchable activity).", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        // Activity is not exported / permission denied
        android.util.Log.w("launchPackage", "SecurityException for $pkg with $intent", e)
        Toast.makeText(this, "Can’t open $pkg (blocked by app).", Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        // Malformed component / bad intent extras, etc.
        android.util.Log.w("launchPackage", "IllegalArgument launching $pkg with $intent", e)
        Toast.makeText(this, "Failed to launch $pkg (bad intent).", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // Catch-all so we never crash here
        android.util.Log.w("launchPackage", "Unexpected error launching $pkg with $intent", e)
        Toast.makeText(this, "Failed to launch $pkg.", Toast.LENGTH_SHORT).show()
    }
}


/**
 * Launches an app by package name
 */
fun Context.getBestLaunchIntent(packageName: String): Intent? {
    val pm = packageManager

    // 1. Try standard launch intent
    pm.getLaunchIntentForPackage(packageName)?.let { return it }

    // 2. Try leanback (TV-specific) intent
    Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        `package` = packageName
    }.also { leanbackIntent ->
        if (pm.resolveActivity(leanbackIntent, 0) != null) return leanbackIntent
    }

    // 3. Try to find *any* launchable activity
    Intent(Intent.ACTION_MAIN).apply {
        `package` = packageName
    }.also { genericIntent ->
        val activities = pm.queryIntentActivities(genericIntent, 0)
        if (activities.isNotEmpty()) {
            val activity = activities[0].activityInfo
            return Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(activity.packageName, activity.name)
            }
        }
    }

    // 4. Special case: Amazon Prime Video
    if (packageName == "com.amazon.amazonvideo.livingroom") {
        val knownActivities = listOf(
            "com.amazon.ignition.IgnitionActivity",
            "com.amazon.amazonvideo.livingroom.MainActivity"
        )

        for (activityName in knownActivities) {
            var component = ComponentName(packageName, activityName)
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = component
            }
            if (pm.resolveActivity(intent, 0) != null) return intent
        }
    }

    return null
}