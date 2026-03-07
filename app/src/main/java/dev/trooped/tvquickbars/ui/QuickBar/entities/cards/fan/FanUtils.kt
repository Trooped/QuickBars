package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.fan

import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ui.QuickBar.foundation.getTypeSafe

/**
 * Calculates the percentage increment/decrement step for a fan entity.
 *
 * This function determines the step size based on several priorities:
 * 1. Returns a user-defined custom step if `custom_step_enabled` is true and a valid
 *    `custom_step_percentage` exists in the entity's state.
 * 2. Falls back to a calculated default based on the entity's `percentage_step` attribute:
 *    - If no step is specified (<= 0), defaults to 33.0% (standard for 3-speed fans).
 *    - If the step is very fine-grained ( < 5.0), defaults to 10.0% for better UI usability.
 *    - Otherwise, returns the raw `percentage_step` defined by the entity.
 *
 */
fun getFanPercentageStep(entity: EntityItem): Double {
    val customStepEnabled = entity.lastKnownState.getTypeSafe("custom_step_enabled", true)
    val customStepPercentage = (entity.lastKnownState["custom_step_percentage"] as? Number)?.toInt() ?: 0

    val rawStep = entity.attributes?.optDouble("percentage_step", 0.0) ?: 0.0
    val defaultPercentageStep = when {
        // No step specified - use 33% (typical for 3-speed fans)
        rawStep <= 0.0 -> 33.0
        // Very fine-grained control (1% steps) - use 10% for UI buttons
        rawStep == 1.0 -> 10.0
        rawStep < 5.0 -> 10.0
        // Use the specified step if it's reasonable
        else -> rawStep
    }

    return if (customStepEnabled && customStepPercentage > 0) {
        customStepPercentage.toDouble()
    } else {
        defaultPercentageStep
    }
}

fun getFanPercentage(entity: EntityItem, isOn: Boolean): Int {
    if (!isOn) return 0
    
    val attributes = entity.attributes
    if (attributes?.has("percentage") == true) {
        // First priority: Use actual attribute from server
        return attributes.optInt("percentage", 0)
    } 
    
    if (entity.lastKnownState.containsKey("last_fan_speed")) {
        // Second priority: Use saved speed from lastKnownState
        return when (val speed = entity.lastKnownState["last_fan_speed"]) {
            is Int -> speed
            is Double -> speed.toInt()
            is Float -> speed.toInt()
            is String -> speed.toIntOrNull() ?: 50
            else -> 50
        }
    }
    
    // Default value
    return 50
}