package dev.trooped.tvquickbars.ui.QuickBar.foundation

import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ui.EntityIconMapper
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Helper function to make binary sensor states user-friendly.
 */
fun formatBinarySensorState(entity: EntityItem): String {
    val deviceClass = entity.attributes?.optString("device_class", "") ?: ""
    val isOn = entity.state == "on"

    return when (deviceClass) {
        "door", "garage_door", "opening", "window" -> if (isOn) "Open" else "Closed"
        "motion", "occupancy", "sound", "vibration" -> if (isOn) "Detected" else "Clear"
        "gas", "smoke", "carbon_monoxide", "tamper" -> if (isOn) "Detected" else "Clear"
        "battery" -> if (isOn) "Low" else "Normal"
        "battery_charging" -> if (isOn) "Charging" else "Not Charging"
        "cold" -> if (isOn) "Cold" else "Normal"
        "connectivity" -> if (isOn) "Connected" else "Disconnected"
        "heat" -> if (isOn) "Hot" else "Normal"
        "light" -> if (isOn) "Light Detected" else "No Light"
        "lock" -> if (isOn) "Unlocked" else "Locked"
        "moisture" -> if (isOn) "Wet" else "Dry"
        "moving" -> if (isOn) "Moving" else "Stopped"
        "plug" -> if (isOn) "Plugged In" else "Unplugged"
        "power" -> if (isOn) "Power Detected" else "No Power"
        "presence" -> if (isOn) "Home" else "Away"
        "problem" -> if (isOn) "Problem Detected" else "OK"
        "running" -> if (isOn) "Running" else "Not Running"
        "safety" -> if (isOn) "Unsafe" else "Safe"
        "external_power" -> if (isOn) "Connected to External Power" else "Connected to Battery"
        "update" -> if (isOn) "Update Available" else "Up-to-date"
        // Default fallback for any other device class or "None"
        else -> entity.state.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Formats a timestamp to a human readable form.
 */
fun formatTimestamp(isoString: String): String {
    return try {
        val zonedDateTime = ZonedDateTime.parse(isoString, DateTimeFormatter.ISO_DATE_TIME)
        val millis = zonedDateTime.toInstant().toEpochMilli()
        // Format to a relative time string like "5 minutes ago"
        DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } catch (e: Exception) {
        isoString // If something goes wrong, just show the original text
    }
}


/**
 * Remembers and provides the current time as a formatted string.
 *
 * This Composable function observes the system's time and locale settings.
 * It formats the current time according to whether the 24-hour format is enabled
 * and the current locale. The displayed time updates every minute.
 *
 * The time format will be "HH:mm" if 24-hour format is enabled, otherwise it will be "h:mm a".
 *
 * @return A [State] holding the formatted time string. Changes to this state will
 *         trigger recomposition in Composables that read it.
 */
@Composable
fun rememberClockText(): State<String> {
    val context = LocalContext.current
    val cfg = LocalConfiguration.current
    val is24h = remember(cfg) { DateFormat.is24HourFormat(context) }
    val locale = remember(cfg) { if (Build.VERSION.SDK_INT >= 24) cfg.locales[0] else @Suppress("DEPRECATION") cfg.locale }

    val formatter = remember(is24h, locale) {
        DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale)
    }

    return produceState(
        initialValue = ZonedDateTime.now().format(formatter),
        key1 = formatter
    ) {
        while (true) {
            val nowMs = System.currentTimeMillis()
            value = Instant.ofEpochMilli(nowMs)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
            val delayMs = 60_000 - (nowMs % 60_000)
            delay(delayMs)
        }
    }
}


/**
 * A Composable function that safely loads a painter resource.
 *
 * This function attempts to load a painter resource using the provided `id`.
 * If the `id` is not a valid drawable resource, it will fall back to using the
 * `fallbackId`. This helps prevent crashes due to invalid resource IDs.
 *
 * The validity check is memoized using `remember` to avoid redundant checks
 * on recomposition if the `id` hasn't changed.
 *
 * @param id The primary drawable resource ID to load.
 * @param fallbackId The drawable resource ID to use as a fallback if the primary `id` is invalid.
 *                   Defaults to `R.drawable.ic_default`.
 * @return A [Painter] object for the resolved drawable resource.
 */
@Composable
fun SafePainterResource(
    @DrawableRes id: Int,
    @DrawableRes fallbackId: Int = R.drawable.ic_default
): Painter {
    LocalContext.current
    val safeResId = remember(id) {
        if (EntityIconMapper.isValidDrawableResource(id)) id else fallbackId
    }

    return painterResource(id = safeResId)
}


/**
 * Safely get a typed value from lastKnownState with fallback string conversion
 */
fun <T> Map<String, Any?>.getTypeSafe(key: String, defaultValue: T): T {
    val value = this[key]
    return try {
        @Suppress("UNCHECKED_CAST")
        (value as? T) ?: defaultValue
    } catch (e: ClassCastException) {
        // Handle string conversions for common types
        when (defaultValue) {
            is Boolean -> {
                if (value is String) {
                    (value.lowercase() == "true") as T
                } else defaultValue
            }

            is Int -> {
                if (value is String) {
                    (value.toIntOrNull() ?: defaultValue) as T
                } else defaultValue
            }

            else -> defaultValue
        }
    }
}


/**
 * Returns the entity type from the id
 */
fun getEntityType(entity: EntityItem): String {
    return entity.id.split('.').first()
}