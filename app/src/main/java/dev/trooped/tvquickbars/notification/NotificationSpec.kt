package dev.trooped.tvquickbars.notification

import org.json.JSONArray
import org.json.JSONObject

data class NotificationAction(val id: String, val label: String)

data class NotificationSpec(
    val title: String?,
    val message: String,
    val actions: List<NotificationAction>,
    val durationSec: Int?,
    val position: String?,          // "top_left" | "top_right" | "bottom_left" | "bottom_right"
    val colorHex: String?,          // normalized "#RRGGBB" if present
    val transparency: Double?,      // 0.0..1.0
    val interrupt: Boolean,
    val imageUrl: String?,          // absolute URL (already resolved in HA)
    val soundUrl: String?,          // absolute/signed URL (already resolved in HA)
    val soundVolumePercent: Int?,   // 0..200 (0=mute, 100=normal, >100 boosted)
    val iconSvgDataUri: String?,    // inline SVG data URI if provided
    val cid: String?,                // correlation id echoed from HA fire
)

private fun JSONObject.stringOrNull(key: String): String? =
    optString(key, "").takeIf { it.isNotBlank() }

private fun parseActions(arr: JSONArray?): List<NotificationAction> {
    if (arr == null) return emptyList()
    val out = ArrayList<NotificationAction>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val id = obj.optString("id").ifBlank { continue }
        val label = obj.optString("label").ifBlank { id }
        out += NotificationAction(id, label)
    }
    return out
}

// Accept color as "#hex" OR [r,g,b] OR {"r":..,"g":..,"b":..}; normalize to "#RRGGBB"
private fun normalizeColorToHex(any: Any?): String? = when (any) {
    is String -> any.trim().takeIf { it.isNotEmpty() }
    is JSONArray -> if (any.length() == 3) {
        val r = any.optInt(0, -1); val g = any.optInt(1, -1); val b = any.optInt(2, -1)
        if (r in 0..255 && g in 0..255 && b in 0..255) "#%02x%02x%02x".format(r, g, b) else null
    } else null
    is JSONObject -> {
        val r = any.optInt("r", -1); val g = any.optInt("g", -1); val b = any.optInt("b", -1)
        if (r in 0..255 && g in 0..255 && b in 0..255) "#%02x%02x%02x".format(r, g, b) else null
    }
    else -> null
}

private fun iconifyUrlFor(mdi: String?): String? =
    mdi?.trim()?.takeIf { it.isNotEmpty() }?.let { "https://api.iconify.design/${it.replace(":", "%3A")}.svg" }

fun JSONObject.toNotificationSpec(cid: String?): NotificationSpec {
    val title        = stringOrNull("title")
    val message      = optString("message", "")
    val actions      = parseActions(optJSONArray("actions"))
    val duration     = optInt("duration", -1).let { if (it > 0) it else null }
    val position     = stringOrNull("position")
    val colorHex     = normalizeColorToHex(opt("color"))
    val transparency = optDouble("transparency", Double.NaN).let { if (it.isNaN()) null else it }
    val interrupt    = optBoolean("interrupt", false)
    val imageUrl     = stringOrNull("image_url") ?: stringOrNull("image") // tolerate older key
    val soundUrl     = stringOrNull("sound_url")
    val soundObj     = optJSONObject("sound")
    val svgData      = stringOrNull("icon_svg_data_uri")
    val iconUrl      = stringOrNull("icon_url")
    val mdiName      = stringOrNull("mdi_icon")
    val iconModel    = svgData ?: iconUrl ?: iconifyUrlFor(mdiName)

    val sndPctRaw = when {
        soundObj?.has("volume_percent") == true -> soundObj.optInt("volume_percent", 100)
        has("sound_volume_percent")            -> optInt("sound_volume_percent", 100)
        else                                   -> -1
    }
    val soundPct  = sndPctRaw.takeIf { it in 0..200 }   // clamp in caller if you want different bounds

    return NotificationSpec(
        title = title,
        message = message,
        actions = actions,
        durationSec = duration,
        position = position,
        colorHex = colorHex,
        transparency = transparency,
        interrupt = interrupt,
        imageUrl = imageUrl,
        soundUrl = soundUrl,
        iconSvgDataUri = iconModel,
        cid = cid,
        soundVolumePercent = soundPct
    )
}