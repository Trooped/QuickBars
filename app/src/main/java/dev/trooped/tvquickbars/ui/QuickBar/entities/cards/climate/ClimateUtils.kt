package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.climate

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject

/**
 * Climate Utilities
 */

fun parseStringArray(jsonArray: JSONArray?): List<String> {
    if (jsonArray == null) return emptyList()
    val list = mutableListOf<String>()
    for (i in 0 until jsonArray.length()) {
        list.add(jsonArray.optString(i))
    }
    return list
}

fun getClimateFanModes(attributes: JSONObject): List<String> {
    return try {
        parseStringArray(attributes.optJSONArray("fan_modes"))
    } catch (e: Exception) {
        Log.e("ClimateUtils", "Error parsing fan modes", e)
        emptyList()
    }
}

fun getClimateSwingModes(attributes: JSONObject): List<String> {
    return try {
        parseStringArray(attributes.optJSONArray("swing_modes"))
    } catch (e: Exception) {
        Log.e("ClimateUtils", "Error parsing swing modes", e)
        emptyList()
    }
}

fun getClimateHvacModes(attributes: JSONObject): List<String> {
    return try {
        val modes = parseStringArray(attributes.optJSONArray("hvac_modes"))
        modes.ifEmpty { listOf("off", "heat", "cool") }
    } catch (e: Exception) {
        Log.e("ClimateUtils", "Error parsing hvac modes", e)
        listOf("off", "heat", "cool")
    }
}

val defaultModeIcons = mapOf(
    "off" to Icons.Filled.PowerSettingsNew,
    "heat" to Icons.Filled.WbSunny,
    "cool" to Icons.Filled.AcUnit,
    "auto" to Icons.Filled.Autorenew,
    "fan_only" to Icons.Filled.Air,
    "dry" to Icons.Filled.WaterDrop,
)

fun getClimateModeIcons(attributes: JSONObject): Map<String, ImageVector> {
    return try {
        val modeIconsObj = attributes.optJSONObject("mode_icons") ?: return defaultModeIcons
        val customIcons = mutableMapOf<String, ImageVector>()
        modeIconsObj.keys().forEach { mode ->
            val iconName = modeIconsObj.optString(mode, "")
            val icon = when (iconName) {
                "mdi:power" -> Icons.Filled.PowerSettingsNew
                "mdi:weather-sunny" -> Icons.Filled.WbSunny
                "mdi:snowflake" -> Icons.Filled.AcUnit
                "mdi:autorenew" -> Icons.Filled.Autorenew
                "mdi:fan" -> Icons.Filled.Air
                "mdi:water" -> Icons.Filled.WaterDrop
                "mdi:thermostat" -> Icons.Filled.Thermostat
                else -> null
            }
            if (icon != null) {
                customIcons[mode] = icon
            }
        }
        customIcons.ifEmpty { defaultModeIcons }
    } catch (e: Exception) {
        Log.e("ClimateUtils", "Error parsing mode icons", e)
        defaultModeIcons
    }
}

fun getClimateModeIconFallback(mode: String): ImageVector {
    return when (mode) {
        "off" -> Icons.Filled.PowerSettingsNew
        "heat" -> Icons.Filled.WbSunny
        "cool" -> Icons.Filled.AcUnit
        "auto" -> Icons.Filled.Autorenew
        "fan_only" -> Icons.Filled.Air
        "dry" -> Icons.Filled.WaterDrop
        else -> Icons.Filled.Thermostat
    }
}