package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.lock

import dev.trooped.tvquickbars.data.EntityItem

/**
 * Lock Utilities
 */

fun isLockLocked(state: String): Boolean = state == "locked"
fun isLockJammed(state: String): Boolean = state == "jammed"

fun formatLockState(state: String): String {
    return when (state) {
        "locked" -> "Locked"
        "unlocked" -> "Unlocked"
        "locking" -> "Locking..."
        "unlocking" -> "Unlocking..."
        "jammed" -> "Jammed!"
        else -> state.replaceFirstChar { it.uppercase() }
    }
}

fun supportsLockOpen(entity: EntityItem): Boolean {
    return (entity.attributes?.optInt("supported_features", 0) ?: 0 and 1) != 0
}

fun getLockBatteryLevel(entity: EntityItem): Int {
    return entity.attributes?.optInt("battery_level", -1) ?: -1
}