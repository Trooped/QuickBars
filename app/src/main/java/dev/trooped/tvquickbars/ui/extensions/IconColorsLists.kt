package dev.trooped.tvquickbars.ui.extensions

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.Log
import android.util.TypedValue
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import dev.trooped.tvquickbars.R


/**
 * Apply theme-based color to an ImageView
 *
 * This function applies a color filter to the ImageView based on the current theme.
 * It first tries to use the Material theme's on-surface color, then falls back to the primary text color,
 * and finally uses a hardcoded color resource if necessary.
 */
fun ImageView.applyThemeColor() {
    try {
        // Method 1: Try using Material attribute for on-surface color (better contrast)
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
            val color = typedValue.data
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            return
        }

        // Method 2: Fallback to primary text color
        if (context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
            val color = typedValue.data
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            return
        }

        // Method 3: Last resort - use a hardcoded color resource
        val fallbackColor = ContextCompat.getColor(context, R.color.md_theme_onSurface)
        colorFilter = PorterDuffColorFilter(fallbackColor, PorterDuff.Mode.SRC_IN)
    } catch (e: Exception) {
        Log.e("IconDebug", "Error applying theme color", e)
        // Don't apply any filter if we encounter an error
    }
}

/**
 * Set an image resource with theme-based coloring
 */
fun ImageView.setThemedResource(@DrawableRes resId: Int) {
    setImageResource(resId)
    applyThemeColor()
}