package dev.trooped.tvquickbars.ui

import android.util.Log
import androidx.annotation.DrawableRes
import dev.trooped.tvquickbars.QuickBarsApp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityItem

/**
 * EntityIconMapper
 *
 * Utility object for mapping entity states to icons.
 * Provides methods to get icons based on entity state, domain, and custom user choices.
 */
object EntityIconMapper {
    // Cache for faster lookups
    private val iconCache = mutableMapOf<String, Int>()
    private val nameToIdCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * Validates if a resource ID refers to a valid drawable resource
     */
    fun isValidDrawableResource(@DrawableRes resId: Int?): Boolean {
        if (resId == null || resId == 0) return false
        return try {
            val name = QuickBarsApp.getAppContext().resources.getResourceEntryName(resId)
            name != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the final icon resource ID for an entity based on its string name
     */
    @DrawableRes
    fun getFinalIconForEntity(entity: EntityItem): Int {
        val isOn = entity.state == "on"

        // Check if custom icons are valid
        val customOnIconValid = !entity.customIconOnName.isNullOrEmpty() &&
                isValidDrawableResourceName(entity.customIconOnName)

        val customOffIconValid = !entity.customIconOffName.isNullOrEmpty() &&
                isValidDrawableResourceName(entity.customIconOffName)

        if (customOnIconValid) {
            if (isOn || !customOffIconValid) {
                // Use ON icon
                return getResourceIdByName(entity.customIconOnName) ?: R.drawable.ic_default
            } else {
                // Use OFF icon
                return getResourceIdByName(entity.customIconOffName) ?: R.drawable.ic_default
            }
        }

        // No valid custom icons, fall back to defaults
        val domain = entity.id.split('.').firstOrNull() ?: return R.drawable.ic_default
        return if (isOn) {
            getDefaultOnIconForEntity(entity.id)
        } else {
            getDefaultOffIconForEntity(entity.id)
        }
    }

    /**
     * Get the display icon for an entity (for lists, etc.)
     */
    @DrawableRes
    fun getDisplayIconForEntity(entity: EntityItem): Int {
        // For display purposes, prefer ON icons
        if (!entity.customIconOnName.isNullOrEmpty()) {
            val iconId = getResourceIdByName(entity.customIconOnName)
            if (iconId != null) return iconId
        }

        // Fall back to default domain icon
        return getDefaultOnIconForEntity(entity.id)
    }

    /**
     * Get icon for entity based on domain and state
     */
    @DrawableRes
    fun getIconForEntityState(entityId: String, state: String): Int {
        // Create cache key
        val cacheKey = "$entityId:$state"

        // Return cached icon if available
        iconCache[cacheKey]?.let {
            if (isValidDrawableResource(it)) return it
            // If invalid, remove from cache
            iconCache.remove(cacheKey)
        }

        // Calculate icon based on domain and state
        val domain = entityId.split('.').firstOrNull() ?: return R.drawable.ic_default
        val isOn = state == "on"

        val iconRes = when (domain) {
            "light" -> if (isOn) R.drawable.lightbulb_on else R.drawable.lightbulb_off
            "switch" -> if (isOn) R.drawable.toggle_switch_variant else R.drawable.toggle_switch_variant_off
            "input_boolean" -> if (isOn) R.drawable.toggle_switch else R.drawable.toggle_switch_off
            "button" -> R.drawable.gesture_tap_button
            "input_button" -> R.drawable.gesture_tap_button
            "script" -> R.drawable.script_text_play
            "scene" -> R.drawable.palette
            "climate" -> R.drawable.ic_ac_unit
            "cover" -> R.drawable.window_shutter_open
            "fan" -> R.drawable.fan
            "sensor" -> R.drawable.chart_line
            "binary_sensor" -> R.drawable.dip_switch
            "lock" -> R.drawable.lock_outline
            "alarm_control_panel" -> R.drawable.shield_home
            "camera" -> R.drawable.cctv
            "automation" -> R.drawable.robot_happy
            "media_player" -> R.drawable.play_circle
            else -> R.drawable.ic_default
        }

        // Cache the result
        iconCache[cacheKey] = iconRes
        return iconRes
    }

    /**
     * Get default icon for an entity domain (for initial setup)
     */
    @DrawableRes
    fun getDefaultIconForEntity(entityId: String): Int {
        val domain = entityId.split('.').firstOrNull() ?: return R.drawable.ic_default

        return when (domain) {
            "light" -> R.drawable.lightbulb
            "switch" -> R.drawable.toggle_switch_variant
            "input_boolean" -> R.drawable.toggle_switch
            "button" -> R.drawable.gesture_tap_button
            "input_button" -> R.drawable.gesture_tap_button
            "script" -> R.drawable.script_text_play
            "scene" -> R.drawable.palette
            "climate" -> R.drawable.ic_ac_unit
            "cover" -> R.drawable.window_shutter_open
            "fan" -> R.drawable.fan
            "sensor" -> R.drawable.chart_line
            "binary_sensor" -> R.drawable.dip_switch
            "lock" -> R.drawable.lock_outline
            "alarm_control_panel" -> R.drawable.shield_home
            "camera" -> R.drawable.cctv
            "automation" -> R.drawable.robot_happy
            "media_player" -> R.drawable.play_circle
            else -> R.drawable.ic_default
        }
    }

    /**
     * Check if an entity is toggleable based on its domain
     */
    fun isEntityToggleable(entityId: String): Boolean {
        val domain = entityId.split('.').firstOrNull() ?: return false
        return domain in listOf("light", "switch", "input_boolean", "climate", "fan")
    }

    /**
     * Get default ON icon for an entity domain
     */
    @DrawableRes
    fun getDefaultOnIconForEntity(entityId: String): Int {
        val domain = entityId.split('.').firstOrNull() ?: return R.drawable.ic_default

        return when (domain) {
            "light" -> R.drawable.lightbulb_on
            "switch" -> R.drawable.toggle_switch_variant
            "input_boolean" -> R.drawable.toggle_switch
            "button" -> R.drawable.gesture_tap_button
            "input_button" -> R.drawable.gesture_tap_button
            "script" -> R.drawable.script_text_play
            "scene" -> R.drawable.palette
            "climate" -> R.drawable.ic_ac_unit
            "cover" -> R.drawable.window_shutter_open
            "fan" -> R.drawable.fan
            "sensor" -> R.drawable.chart_line
            "binary_sensor" -> R.drawable.dip_switch
            "lock" -> R.drawable.lock_outline
            "alarm_control_panel" -> R.drawable.shield_home
            "camera" -> R.drawable.cctv
            "automation" -> R.drawable.robot_happy
            "media_player" -> R.drawable.play_circle
            else -> R.drawable.ic_default
        }
    }

    /**
     * Get default OFF icon for an entity domain
     */
    @DrawableRes
    fun getDefaultOffIconForEntity(entityId: String): Int {
        val domain = entityId.split('.').firstOrNull() ?: return R.drawable.ic_default

        return when (domain) {
            "light" -> R.drawable.lightbulb_off
            "switch" -> R.drawable.toggle_switch_variant_off
            "input_boolean" -> R.drawable.toggle_switch_off
            "button" -> R.drawable.gesture_tap_button
            "input_button" -> R.drawable.gesture_tap_button
            "script" -> R.drawable.script_text_play
            "scene" -> R.drawable.palette
            "climate" -> R.drawable.ic_ac_unit
            "cover" -> R.drawable.window_shutter
            "fan" -> R.drawable.fan_off
            "sensor" -> R.drawable.chart_line
            "binary_sensor" -> R.drawable.dip_switch
            "lock" -> R.drawable.lock_open_outline
            "alarm_control_panel" -> R.drawable.shield_home
            "camera" -> R.drawable.cctv_off
            "automation" -> R.drawable.robot_happy
            "media_player" -> R.drawable.play_circle
            else -> R.drawable.ic_default
        }
    }

    /**
     * Clear the icon cache
     * Useful for testing or when icons change dynamically
     */
    fun clearCache() {
        iconCache.clear()
        nameToIdCache.clear()
    }


    /**
     * Get resource ID from a resource name
     */
    fun getResourceIdByName(resourceName: String?): Int? {
        if (resourceName.isNullOrEmpty()) return null

        nameToIdCache[resourceName]?.let { return it }

        return try {
            val context = QuickBarsApp.getAppContext()
            val id = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
            if (id != 0) {
                nameToIdCache[resourceName] = id // cache positive hits only
                id
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("EntityIconMapper", "Failed to get resource ID for: $resourceName", e)
            null
        }
    }

    /**
     * Validates if a resource name refers to a valid drawable resource
     */
    fun isValidDrawableResourceName(resourceName: String?): Boolean {
        if (resourceName.isNullOrEmpty()) return false

        return getResourceIdByName(resourceName) != null
    }

    /**
     * Get default ON icon name for an entity domain
     */
    fun getDefaultOnIconForEntityName(entityId: String): String {
        val domain = entityId.split('.').firstOrNull() ?: return "ic_default"

        return when (domain) {
            "light" -> "lightbulb_on"
            "switch" -> "toggle_switch_variant"
            "input_boolean" -> "toggle_switch"
            "button" -> "gesture_tap_button"
            "input_button" -> "gesture_tap_button"
            "script" -> "script_text_play"
            "scene" -> "palette"
            "climate" -> "ic_ac_unit"
            "cover" -> "window_shutter_open"
            "fan" -> "fan"
            "sensor" -> "chart_line"
            "binary_sensor" -> "dip_switch"
            "lock" -> "lock_outline"
            "alarm_control_panel" -> "shield_home"
            "camera" -> "cctv"
            "automation" -> "robot_happy"
            "media_player" -> "play_circle"
            else -> "ic_default"
        }
    }

    /**
     * Get default OFF icon name for an entity domain
     */
    fun getDefaultOffIconForEntityName(entityId: String): String {
        val domain = entityId.split('.').firstOrNull() ?: return "ic_default"

        return when (domain) {
            "light" -> "lightbulb_off"
            "switch" -> "toggle_switch_variant_off"
            "input_boolean" -> "toggle_switch_off"
            "button" -> "gesture_tap_button"
            "input_button" -> "gesture_tap_button"
            "script" -> "script_text_play"
            "scene" -> "palette"
            "climate" -> "ic_ac_unit"
            "cover" -> "window_shutter"
            "fan" -> "fan_off"
            "sensor" -> "chart_line"
            "binary_sensor" -> "dip_switch"
            "lock" -> "lock_outline"
            "alarm_control_panel" -> "shield_home"
            "camera" -> "cctv_off"
            "automation" -> "robot_happy"
            "media_player" -> "play_circle"
            else -> "ic_default"
        }
    }

    /**
     * Get the final icon resource ID for an entity based on its string name
     */
    @DrawableRes
    fun getFinalIconFromString(entity: EntityItem): Int {
        val isOn = entity.state == "on"

        // Check if custom icons are valid
        val customOnIconValid = !entity.customIconOnName.isNullOrEmpty() &&
                isValidDrawableResourceName(entity.customIconOnName)

        val customOffIconValid = !entity.customIconOffName.isNullOrEmpty() &&
                isValidDrawableResourceName(entity.customIconOffName)

        if (customOnIconValid) {
            if (isOn || !customOffIconValid) {
                // Use ON icon
                return getResourceIdByName(entity.customIconOnName) ?: R.drawable.ic_default
            } else {
                // Use OFF icon
                return getResourceIdByName(entity.customIconOffName) ?: R.drawable.ic_default
            }
        }

        // No valid custom icons, fall back to defaults
        val domain = entity.id.split('.').firstOrNull() ?: return R.drawable.ic_default
        return if (isOn) {
            getDefaultOnIconForEntity(entity.id)
        } else {
            getDefaultOffIconForEntity(entity.id)
        }
    }
}