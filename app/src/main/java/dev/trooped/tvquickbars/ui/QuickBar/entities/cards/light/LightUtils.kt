package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.light

import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/**
 * Light Utilities
 */

fun colorFromKelvin(kelvin: Int, min: Int, max: Int): Color {
    val t = ((kelvin - min).toFloat() / (max - min).coerceAtLeast(1)).coerceIn(0f, 1f)
    return Color(
        red = (255 * (1.0f - 0.5f * t)).toInt(),
        green = (255 * (1.0f - 0.3f * t)).toInt(),
        blue = (255 * (1.0f + 0.8f * t)).toInt()
    )
}

/**
 * Calculates the effective minimum and maximum Kelvin color temperature range from a light's attributes.
 *
 * This function prioritizes explicit Kelvin values if available. If they are missing (old HA version), it attempts
 * to derive the range from mired values (1,000,000 / mireds). If no range information is provided
 * in the [attributes], it defaults to a standard range of 2000K to 6500K.
 *
 * @param attributes A [JSONObject] containing the light's state attributes.
 * @return A [Pair] where the first value is the effective minimum Kelvin and the second is the effective maximum Kelvin.
 */
fun getEffectiveKelvinRange(attributes: JSONObject): Pair<Int, Int> {
    val minKelvin = attributes.optInt("min_color_temp_kelvin", 2000)
    val maxKelvin = attributes.optInt("max_color_temp_kelvin", 6500)
    val minMireds = if (attributes.has("min_mireds")) attributes.optInt("min_mireds", 153) else 153
    val maxMireds = if (attributes.has("max_mireds")) attributes.optInt("max_mireds", 500) else 500
    val effectiveMin = if (attributes.has("min_color_temp_kelvin")) minKelvin else (if (attributes.has("max_mireds") && maxMireds > 0) 1_000_000 / maxMireds else 2000).toInt()
    val effectiveMax = if (attributes.has("max_color_temp_kelvin")) maxKelvin else (if (attributes.has("min_mireds") && minMireds > 0) 1_000_000 / minMireds else 6500).toInt()
    return Pair(effectiveMin, effectiveMax)
}

/**
 * Extracts and converts the current color temperature from mireds to Kelvin.
 *
 * @param attributes A [JSONObject] containing the light's state attributes.
 * @return The current temperature in Kelvin if the "color_temp" (mireds) attribute is present and valid,
 *         otherwise returns 0.
 */
fun getCurrentKelvin(attributes: JSONObject): Int {
    val colorTemp = if (attributes.has("color_temp")) attributes.optInt("color_temp", 0) else 0
    return if (attributes.has("color_temp") && colorTemp > 0) (1_000_000 / colorTemp).toInt() else 0
}