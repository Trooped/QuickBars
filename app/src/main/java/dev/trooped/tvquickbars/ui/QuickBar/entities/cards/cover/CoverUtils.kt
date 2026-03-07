package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.cover

import org.json.JSONObject

/**
 * Cover Utilities
 */

fun isCoverOpen(state: String): Boolean {
    return state == "open"
}

fun getCoverPosition(attributes: JSONObject?): Int {
    return attributes?.optInt("current_position", 0) ?: 0
}

/**
 * Checks if the cover entity supports tilt functionality.
 */
fun supportsCoverTilt(attributes: JSONObject?): Boolean {
    return attributes?.optInt("supported_features", 0)?.let {
        (it and 4) > 0 || (it and 8) > 0
    } ?: false
}

fun hasCoverTiltState(attributes: JSONObject?): Boolean {
    return attributes?.has("current_tilt_position") ?: false
}