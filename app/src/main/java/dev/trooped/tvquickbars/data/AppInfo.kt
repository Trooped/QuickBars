package dev.trooped.tvquickbars.data

import android.graphics.drawable.Drawable

/**
 * Simple value-object for display of an application in the UI.
 * Contains the label, package name, and icon of the application.
 * Overrides equals and hashCode to ensure uniqueness based on package name.
 * This is used for displaying apps in a list or grid format.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
) {
    // Override equals to only check package name
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppInfo
        return packageName == other.packageName
    }

    // Override hashCode to match equals
    override fun hashCode(): Int {
        return packageName.hashCode()
    }
}