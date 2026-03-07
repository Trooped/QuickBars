/**
 * BaseActivity class
 * Allows us to force LTR on all main activities.
 */
package dev.trooped.tvquickbars.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import dev.trooped.tvquickbars.R

/**
 * A base activity class that provides common functionality and consistent styling for all
 * activities in the application.
 *
 * This class serves two primary purposes:
 * 1. It forces a Left-to-Right (LTR) layout direction across all activities to ensure
 *    consistent UI behavior regardless of system locale.
 * 2. It provides utility methods for managing [Toolbar] navigation icons, specifically
 *    enhancing focusability for TV D-pad navigation and consistent color tinting.
 */
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Force LTR for all activities
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR

        super.onCreate(savedInstanceState)
    }

    /**
     * Finds the Toolbar's navigation icon (back button) and applies a focusable
     * background selector to it, making it compatible with D-pad navigation on TVs.
     */
    fun makeToolbarNavIconFocusable(toolbar: Toolbar) {
        // We need to wait for the toolbar to lay out its children
        toolbar.post {
            for (i in 0 until toolbar.childCount) {
                val view: View = toolbar.getChildAt(i)
                if (view is ImageButton) {
                    // The navigation button is usually the only ImageButton with this content description
                    val description = view.contentDescription?.toString()
                    if (description == toolbar.navigationContentDescription?.toString()) {
                        view.isFocusable = true
                        view.isFocusableInTouchMode = true // Important for some TV devices
                        view.setBackgroundResource(R.drawable.toolbar_icon_background_selector)
                        return@post
                    }
                }
            }
        }
    }

    /**
     * Sets the navigation (back) icon in all toolbars to the same color.
     */
    fun setToolbarNavigationIconColor(toolbar: Toolbar, @ColorRes colorRes: Int) {
        toolbar.navigationIcon?.let {
            val color = ContextCompat.getColor(toolbar.context, colorRes)
            val wrappedDrawable = DrawableCompat.wrap(it).mutate()
            DrawableCompat.setTint(wrappedDrawable, color)
            toolbar.navigationIcon = wrappedDrawable
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure it stays LTR even after configuration changes
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
    }
}