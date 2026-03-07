package dev.trooped.tvquickbars.data

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.annotations.SerializedName
import dev.trooped.tvquickbars.R
import java.util.UUID

// An enum to define the types of presses we can detect
enum class TriggerType {
    SINGLE_PRESS,
    DOUBLE_PRESS,
    LONG_PRESS
}

// Position enum
enum class QuickBarPosition {
    RIGHT,
    LEFT,
    BOTTOM,
    TOP
}


/**
 * QuickBar Class
 * Represents a single QuickBar object.
 * @property id A unique identifier for the QuickBar.
 * @property name The name of the QuickBar.
 * @property backgroundColor The background color of the QuickBar.
 * @property backgroundOpacity The opacity of the background color.
 * @property onStateColor The color of the on state for all entities but climate.
 * @property isEnabled Whether the QuickBar is enabled or not.
 * @property showNameInOverlay Whether the QuickBar should show its name in the overlay.
 * @property position The position of the QuickBar overlay in the screen.
 * @property useGridLayout Whether the QuickBar should use a grid layout when in vertical (left/right) position.
 * @property savedEntityIds A list of entity IDs that are saved for this QuickBar.
 */
data class QuickBar(
    // A unique ID so we can identify and edit each bar.
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") var name: String = "New QuickBar",

    @SerializedName("backgroundColor") var backgroundColor: String = "colorSurface",
    @SerializedName("backgroundOpacity") var backgroundOpacity: Int = 90,


    @SerializedName("onStateColor") var onStateColor: String = "colorPrimary",

    @SerializedName("isEnabled") var isEnabled: Boolean = true,
    @SerializedName("showNameInOverlay") var showNameInOverlay: Boolean = true,

    @SerializedName("position") var position: QuickBarPosition = QuickBarPosition.RIGHT,
    @SerializedName("useGridLayout") var useGridLayout: Boolean = false,
    @SerializedName("savedEntityIds") var savedEntityIds: MutableList<String> = mutableListOf(),

    @SerializedName("showTimeOnQuickBar") var showTimeOnQuickBar: Boolean = true,

    @SerializedName("haTriggerAlias") var haTriggerAlias: String = "",

    @SerializedName("autoCloseQuickBarDomains") var autoCloseQuickBarDomains: MutableList<String> = mutableListOf(),

    @SerializedName("customBackgroundColor") var customBackgroundColor: List<Int>? = null, // [R,G,B]
    @SerializedName("customOnStateColor") var customOnStateColor: List<Int>? = null,       // [R,G,B]

    @SerializedName("autoCloseDelay") var autoCloseDelay: Int = 0,

    // FUTURE ATTRIBUTES - TO BE IMPLEMENTED--------------------------------------------------
    @SerializedName("animationStyle") var animationStyle: String = "slide",
    @SerializedName("animationDuration") var animationDuration: Int = 500,
    @SerializedName("displayMode") var displayMode: String = "rectangle",
    @SerializedName("closeAfterOneAction") var closeAfterOneAction: Boolean = true,
    @SerializedName("entityTextSize") var entityTextSize: Int = 14,
    @SerializedName("entityIconSize") var entityIconSize: Int = 14,

){
    fun getBackgroundColorWithOpacity(context: Context): Int {
        // Base color: either theme attr or explicit custom RGB
        val baseColor: Int = if (
            backgroundColor.equals("custom", ignoreCase = true) &&
            customBackgroundColor != null && customBackgroundColor!!.size >= 3
        ) {
            val r = customBackgroundColor!![0].coerceIn(0, 255)
            val g = customBackgroundColor!![1].coerceIn(0, 255)
            val b = customBackgroundColor!![2].coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        } else {
            getThemeColor(context, backgroundColor)
        }

        // Apply opacity
        val alpha = (255 * (backgroundOpacity / 100f)).toInt().coerceIn(0, 255)
        return (baseColor and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun getThemeColor(context: Context, attrName: String): Int {
        // Get the attribute ID from resources
        val attrId = context.resources.getIdentifier(attrName, "attr", context.packageName)

        if (attrId == 0) {
            // Fallback to our default color if attribute not found
            return ContextCompat.getColor(context, R.color.md_theme_surface)
        }

        // Resolve the attribute to a color
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }
}
