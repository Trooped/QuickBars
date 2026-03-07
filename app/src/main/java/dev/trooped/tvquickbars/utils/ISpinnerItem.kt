package dev.trooped.tvquickbars.utils

import android.graphics.drawable.Drawable

/**
 * An interface for items that can be displayed in a generic spinner.
 */
interface ISpinnerItem {
    val id: String // A unique ID for the item
    val displayText: String
    val displayIcon: Drawable? // Nullable for items that don't have an icon
}