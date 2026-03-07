package dev.trooped.tvquickbars.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

// ---- Legacy flags (only used if supported_color_modes is missing) ----
private const val SUPPORT_BRIGHTNESS  = 1
private const val SUPPORT_COLOR_TEMP  = 2
private const val SUPPORT_COLOR       = 16
private const val SUPPORT_WHITE_VALUE = 128

/**
 * This class is used to determine the capabilities of light entities.
 * This was the approach I chose when implementing it, due to light entities being more complex.
 * This will probably get refactored in the future.
 */
data class LightCaps(
    val isSimple: Boolean,
    val brightness: Boolean,
    val color: Boolean,
    val colorTemp: Boolean
)

fun JSONObject.hasNonNull(key: String) = has(key) && !isNull(key)

/** Normalize supported_color_modes into a Set<String> (handles JSONArray / Map / String). */
fun normalizeSupportedColorModes(any: Any?): Set<String> = when (any) {
    is JSONArray -> (0 until any.length()).mapNotNull { any.optString(it, null) }.toSet()
    is JSONObject  -> normalizeSupportedColorModes(any.opt("values"))
    is Collection<*> -> any.mapNotNull { it?.toString() }.toSet()
    is String      -> Regex("[A-Za-z_]+").findAll(any).map { it.value }.toSet()
    else           -> emptySet()
}.map { it.lowercase() }.toSet()

/** HA docs: prefer supported_color_modes; if missing, derive from supported_features. */
fun deriveSupportedColorModes(attrs: JSONObject): Set<String> {
    val direct = normalizeSupportedColorModes(attrs.opt("supported_color_modes"))
    if (direct.isNotEmpty()) return direct

    val features = (attrs.opt("supported_features") as? Number)?.toInt() ?: 0
    val modes = mutableSetOf<String>()

    if ((features and SUPPORT_COLOR_TEMP)   != 0) modes += "color_temp"
    if ((features and SUPPORT_COLOR)        != 0) modes += "hs"       // HS per docs
    if ((features and SUPPORT_WHITE_VALUE)  != 0) modes += "rgbw"

    if (modes.isEmpty() && (features and SUPPORT_BRIGHTNESS) != 0) modes += "brightness"
    if (modes.isEmpty()) modes += "onoff"
    return modes
}

/** Capabilities from the modes set (no reliance on current values). */
fun capsFromModes(scm: Set<String>): LightCaps {
    val isSimple = (scm.size == 1 && "onoff" in scm)

    val brightness = scm.intersect(setOf(
        "brightness","color_temp","hs","rgb","xy","rgbw","rgbww","white"
    )).isNotEmpty()

    val colorTemp  = "color_temp" in scm
    val color      = scm.intersect(setOf("hs","rgb","xy","rgbw","rgbww")).isNotEmpty()

    return LightCaps(isSimple, brightness, color, colorTemp)
}

/** HA docs: deduce current color_mode from present (non-null) values if missing. */
fun deduceColorMode(attrs: JSONObject, scm: Set<String>): String {
    val cm = attrs.optString("color_mode", "").lowercase()
    if (cm.isNotBlank()) return cm

    fun present(k: String) = attrs.hasNonNull(k)

    if ("rgbw"       in scm && present("white_value") && present("hs_color")) return "rgbw"
    if ("rgb"        in scm && present("rgb_color"))  return "rgb"
    if ("xy"         in scm && present("xy_color"))   return "xy"
    if ("hs"         in scm && present("hs_color"))   return "hs"
    if ("color_temp" in scm && present("color_temp")) return "color_temp"
    if ("brightness" in scm && present("brightness")) return "brightness"
    if ("white"      in scm && present("brightness")) return "white"
    if ("onoff"      in scm)                          return "onoff"
    return "unknown"
}

/**
 * Compute light capabilities.
 * - Prefer attributes.supported_color_modes / supported_features
 * - Fallback to persisted lastKnownState["supported_color_modes_set"] or ["deduced_color_mode"]
 */
fun computeLightCaps(
    attrs: JSONObject?,
    lastKnownState: Map<String, Any?>? = null
): LightCaps {
    val a = attrs ?: JSONObject()

    // 1) Try attributes
    var scm = deriveSupportedColorModes(a)

    // 2) Fallback to persisted snapshot if attributes are empty or gave only "onoff"
    val snapshot = lastKnownState?.get("supported_color_modes_set")?.toString()
        ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }?.toSet().orEmpty()
    if ((scm.isEmpty() || scm == setOf("onoff")) && snapshot.isNotEmpty()) {
        scm = snapshot
    }

    // 3) Last-ditch: a single deduced mode (e.g., "rgb")
    if ((scm.isEmpty() || scm == setOf("onoff"))) {
        val deduced = lastKnownState?.get("deduced_color_mode")?.toString()?.lowercase().orEmpty()
        if (deduced.isNotBlank() && deduced != "unknown") {
            scm = setOf(deduced)
        }
    }

    val caps = capsFromModes(scm)
    //Log.d("LightCaps", "computeLightCaps: scm=$scm -> $caps")
    return caps
}