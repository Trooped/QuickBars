package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.alarm_control_panel

/**
 * Alarm Control Panel Utilities
 */


fun isAlarmArmed(alarmState: String): Boolean {
    return alarmState.startsWith("armed_") || alarmState == "arming" ||
            alarmState == "pending" || alarmState == "triggered"
}

fun formatAlarmState(alarmState: String): String {
    return when (alarmState) {
        "disarmed" -> "Disarmed"
        "armed_home" -> "Armed (Home)"
        "armed_away" -> "Armed (Away)"
        "armed_night" -> "Armed (Night)"
        "arming" -> "Arming..."
        "pending" -> "Entry Pending!"
        "triggered" -> "ALARM TRIGGERED!"
        else -> alarmState.replaceFirstChar { it.uppercase() }
    }
}

fun getAlarmAvailableTabs(alarmState: String): List<String> {
    return if (isAlarmArmed(alarmState)) {
        listOf("Disarm")
    } else {
        listOf("Arm Home", "Arm Away")
    }
}