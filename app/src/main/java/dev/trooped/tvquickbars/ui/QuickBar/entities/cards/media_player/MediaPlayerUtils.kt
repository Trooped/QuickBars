package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.media_player

import dev.trooped.tvquickbars.data.EntityItem

/**
 * Media Player Utilities
 *
 */
fun isMediaPlaying(state: String): Boolean = state.equals("playing", ignoreCase = true)
fun isMediaOn(state: String): Boolean = state.lowercase() != "off"

fun isMediaMuted(entity: EntityItem): Boolean {
    return entity.attributes?.optBoolean("is_volume_muted", false) ?: false
}