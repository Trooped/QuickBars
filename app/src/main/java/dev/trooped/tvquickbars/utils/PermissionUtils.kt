package dev.trooped.tvquickbars.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import dev.trooped.tvquickbars.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dev.trooped.tvquickbars.services.QuickBarService

/**
 * PermissionUtils Class
 * This class provides utility functions for managing permissions related to the app.
 * It includes methods for checking and requesting overlay and accessibility permissions,
 * as well as showing explanations and banners for these permissions.
 */
object PermissionUtils {

    private const val PREFS_NAME = "permission_prefs"
    private const val KEY_OVERLAY_EXPLAINED = "overlay_explained"
    private const val KEY_ACCESSIBILITY_EXPLAINED = "accessibility_explained"

    /**
     * Checks if the "Draw over other apps" permission is granted.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Checks if your specific Accessibility Service is enabled.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceId = "${context.packageName}/${QuickBarService::class.java.canonicalName}"
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices ?: "")
            while (splitter.hasNext()) {
                if (splitter.next().equals(serviceId, ignoreCase = true)) {
                    // Found in system settings... but make sure it's actually bound
                    val isActuallyBound = QuickBarService.isRunning
                    if (isActuallyBound){
                        return true
                    }
                    else{
                        Toast.makeText(context, "Please go to system settings again, disable and re-enable the accessibility setting for QuickBars", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        } catch (e: Exception) {
            // Handle exceptions, e.g., if the setting is not found
        }
        return false
    }

    /**
     * Checks if we've already shown the explanation for overlay permission
     */
    fun hasExplainedOverlayPermission(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERLAY_EXPLAINED, false)
    }

    /**
     * Checks if we've already shown the explanation for accessibility permission
     */
    fun hasExplainedAccessibilityPermission(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCESSIBILITY_EXPLAINED, false)
    }

    /**
     * Mark that we've explained the overlay permission
     */
    fun markOverlayPermissionExplained(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OVERLAY_EXPLAINED, true)
            .apply()
    }

    /**
     * Mark that we've explained the accessibility permission
     */
    fun markAccessibilityPermissionExplained(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCESSIBILITY_EXPLAINED, true)
            .apply()
    }

    /**
     * Show overlay permission explanation and direct to settings if user agrees
     * Follows Android special permissions guidelines
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun showOverlayPermissionExplanation(activity: Activity, onCancel: () -> Unit = {}) {
        // Only show explanation dialog the first time
        if (!hasExplainedOverlayPermission(activity)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.overlay_permission_title)
                .setMessage(R.string.overlay_permission_explanation)
                .setPositiveButton(R.string.go_to_settings) { _, _ ->
                    markOverlayPermissionExplained(activity)
                    navigateToOverlaySettings(activity)
                }
                .setNegativeButton(R.string.not_now) { _, _ ->
                    markOverlayPermissionExplained(activity)
                    Toast.makeText(
                        activity,
                        R.string.overlay_permission_denied_message,
                        Toast.LENGTH_SHORT
                    ).show()
                    onCancel()
                }
                .setCancelable(true)
                .setOnCancelListener {
                    onCancel()
                }
                .show()
        } else {
            // If already explained, just show a snackbar reminder
            Snackbar.make(
                activity.findViewById(android.R.id.content),
                R.string.overlay_permission_reminder,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.more_info_button) {
                    // Show the detailed dialog again instead of direct navigation
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.overlay_permission_title)
                        .setMessage(R.string.overlay_permission_explanation)
                        .setPositiveButton(R.string.go_to_settings) { _, _ ->
                            navigateToOverlaySettings(activity)
                        }
                        .setNegativeButton(R.string.not_now, null)
                        .show()
                }
                .show()
            onCancel()
        }
    }

    /**
     * Show accessibility permission explanation and direct to settings if user agrees
     * Follows Android special permissions guidelines
     */
    fun showAccessibilityPermissionExplanation(activity: Activity, onCancel: () -> Unit = {}) {
        // Only show explanation dialog the first time
        if (!hasExplainedAccessibilityPermission(activity)) {
            showAccessibilityPermissionDialog(activity, onCancel)
        } else {
            // If already explained, just show a snackbar reminder with more helpful text
            Snackbar.make(
                activity.findViewById(android.R.id.content),
                R.string.accessibility_permission_reminder,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.more_info_button) {
                    showAccessibilityPermissionDialog(activity, onCancel)
                }
                .show()
            onCancel()
        }
    }

    fun showAccessibilityPermissionDialog(activity: Activity, onCancel: () -> Unit) {
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.accessibility_permission_title)
            .setMessage(R.string.accessibility_permission_explanation)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                markAccessibilityPermissionExplained(activity)
                navigateToAccessibilitySettings(activity)
            }
            .setNegativeButton(R.string.not_now) { _, _ ->
                markAccessibilityPermissionExplained(activity)
                onCancel()
            }
            .setNeutralButton(R.string.more_info) { _, _ ->
                // Store the dialog and show it again after more info
                // Important: Don't dismiss the dialog here, just temporarily hide it

                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.accessibility_more_info_title)
                    .setMessage(R.string.accessibility_more_info_message)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        // This is key: show the original dialog again when More Info closes
                        showAccessibilityPermissionDialog(activity, onCancel)
                    }
                    .setCancelable(false) // Force them to click OK to return
                    .show()
            }
            .setCancelable(true)
            .setOnCancelListener {
                onCancel()
            }
            .create()

        dialog.show()
    }

    /**
     * Shows additional info about accessibility service
     */
    fun showAccessibilityMoreInfo(activity: Activity, onDismiss: () -> Unit = {}) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.accessibility_more_info_title)
            .setMessage(R.string.accessibility_more_info_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                onDismiss()
            }
            .setCancelable(true)
            .setOnCancelListener {
                onDismiss()
            }
            .show()
    }

    /**
     * Shows the overlay permission dialog for activities
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun showOverlayPermissionDialog(activity: Activity, onCancel: () -> Unit = {}) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.overlay_permission_title)
            .setMessage(R.string.overlay_permission_explanation)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                markOverlayPermissionExplained(activity)
                navigateToOverlaySettings(activity)
            }
            .setNegativeButton(R.string.not_now) { _, _ ->
                markOverlayPermissionExplained(activity)
                onCancel()
            }
            .setCancelable(true)
            .setOnCancelListener {
                onCancel()
            }
            .show()
    }

    // The Fragment version can then call the Activity version
    @RequiresApi(Build.VERSION_CODES.M)
    fun showOverlayPermissionDialog(fragment: Fragment, onCancel: () -> Unit = {}) {
        fragment.activity?.let { activity ->
            showOverlayPermissionDialog(activity, onCancel)
        } ?: onCancel()
    }

    /**
     * Navigate to overlay settings
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun navigateToOverlaySettings(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )

        try { // Navigate to overlay settings
            val resolved = intent.resolveActivity(activity.packageManager)
            if (resolved != null && !resolved.className.contains("Stub", ignoreCase = true)) {
                activity.startActivity(intent)
                Toast.makeText(
                    activity,
                    R.string.overlay_settings_toast,
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Fallback to general settings if stub or not found
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                if (fallbackIntent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(fallbackIntent)
                    Toast.makeText(
                        activity,
                        "Please navigate to: Settings → Apps → Special app access → Draw over other apps → enable QuickBars",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        activity,
                        "Unable to open settings. Please navigate manually.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e("OverlayPermission", "Error launching overlay settings: ${e.message}")
            Toast.makeText(
                activity,
                "Unable to open settings. Please navigate manually.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Navigate to accessibility settings
     */
    private fun navigateToAccessibilitySettings(activity: Activity) {
        // First dismiss any existing dialogs to prevent state restoration issues
        if (activity is FragmentActivity) {
            // Dismiss any active dialogs
            activity.supportFragmentManager.fragments.forEach { fragment ->
                fragment.childFragmentManager.fragments.forEach { childFragment ->
                    if (childFragment is DialogFragment) {
                        childFragment.dismissAllowingStateLoss()
                    }
                }
                if (fragment is DialogFragment) {
                    fragment.dismissAllowingStateLoss()
                }
            }
        }

        // Now navigate to accessibility settings
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        try {
            val resolved = intent.resolveActivity(activity.packageManager)
            if (resolved != null && !resolved.className.contains("Stub", ignoreCase = true)) {
                activity.startActivity(intent)
                Toast.makeText(
                    activity,
                    R.string.accessibility_settings_toast,
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Fallback to general settings if stub or not found
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                if (fallbackIntent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(fallbackIntent)
                    Toast.makeText(
                        activity,
                        "Please navigate to: Settings → Device Preferences → Accessibility and enable QuickBars",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        activity,
                        "Unable to open settings. Please navigate manually.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e("AccessibilityIntent", "Error launching settings: ${e.message}")
            Toast.makeText(
                activity,
                "Unable to open settings. Please navigate manually.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * For Fragment usage
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun showOverlayPermissionExplanation(fragment: Fragment, onCancel: () -> Unit = {}) {
        fragment.activity?.let { activity ->
            showOverlayPermissionExplanation(activity, onCancel)
        } ?: onCancel()
    }

    /**
     * For Fragment usage
     */
    fun showAccessibilityPermissionExplanation(fragment: Fragment, onCancel: () -> Unit = {}) {
        fragment.activity?.let { activity ->
            showAccessibilityPermissionExplanation(activity, onCancel)
        } ?: onCancel()
    }

    /**
     * Create a permission banner for ongoing visibility of permission requirements
     */
    fun createPermissionBanner(
        context: Context,
        parent: View,
        isOverlay: Boolean,
        onButtonClick: () -> Unit
    ): View {
        val bannerView = View.inflate(context, R.layout.view_permission_banner, null)

        val titleView = bannerView.findViewById<TextView>(R.id.banner_title)
        val messageView = bannerView.findViewById<TextView>(R.id.banner_message)
        val actionButton = bannerView.findViewById<Button>(R.id.banner_settings_button)

        // Set text based on permission type
        if (isOverlay) {
            titleView.setText(R.string.overlay_banner_title)
            messageView.setText(R.string.overlay_banner_message)
        } else {
            titleView.setText(R.string.accessibility_banner_title)
            messageView.setText(R.string.accessibility_banner_message)
        }

        // Change button text to "More Info" instead of "Settings"
        actionButton.setText(R.string.more_info_button)

        // Set click listener
        actionButton.setOnClickListener {
            onButtonClick()
        }

        return bannerView
    }
}