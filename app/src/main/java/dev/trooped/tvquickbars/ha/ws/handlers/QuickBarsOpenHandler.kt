package dev.trooped.tvquickbars.ha.ws.handlers

import dev.trooped.tvquickbars.camera.CameraRequest
import dev.trooped.tvquickbars.ha.ws.HaClientBridge
import org.json.JSONObject
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dev.trooped.tvquickbars.data.AppIdProvider

class QuickBarsOpenHandler : dev.trooped.tvquickbars.ha.ws.WsHandler {
    val TAG = "QuickBarsOpenHandler"

    override fun canHandle(event: JSONObject): Boolean =
        event.optString("event_type") == "quickbars.open"

    @OptIn(UnstableApi::class)
    override fun handle(event: JSONObject, ctx: HaClientBridge) {
        val context = ctx.getContext()
        if (context == null) {
            androidx.media3.common.util.Log.w(TAG, "Cannot handle notification event: Context is not available.")
            return
        }

        val data = event.optJSONObject("data") ?: run {
            Log.w("HAClient", "QuickBar event had no data payload")
            return
        }

        val targetId = data.optString("id", "")
        if (targetId.isNotEmpty()) {
            val myId = AppIdProvider.get(context) ?: ""
            if (!targetId.equals(myId, ignoreCase = true)) {
                Log.d("HAClient", "quickbars.open ignored (targetId=$targetId, myId=$myId)")
                return
            }
        }

        val rtspFromEvent = if (data.has("rtsp_url") && !data.isNull("rtsp_url")) {
            data.optString("rtsp_url", "").trim()
                .takeIf { it.startsWith("rtsp://", ignoreCase = true) }
        } else null


        val hasCameraPayload = data.has("camera_alias") || data.has("camera_entity") || (rtspFromEvent != null)
        if (hasCameraPayload) {
            val sizePx = data.optJSONObject("size_px")
            val req = CameraRequest(
                cameraAlias = data.optString("camera_alias", null)?.takeIf { it.isNotBlank() },
                cameraEntity = data.optString("camera_entity", null)?.takeIf { it.isNotBlank() },
                position = data.optString("position", null)?.takeIf { it.isNotBlank() },
                size = data.optString("size", null)?.takeIf { it.isNotBlank() },
                sizePxW = sizePx?.optInt("w"),
                sizePxH = sizePx?.optInt("h"),
                autoHideSec = if (data.has("auto_hide")) data.optInt("auto_hide") else null,
                showTitle = if (data.has("show_title")) data.optBoolean("show_title") else null,
                rtspUrl = rtspFromEvent,
                customTitle   = data.optString("camera_title", null)
                    ?.takeIf { it.isNotBlank() && !it.equals("null", true) },
                muteAudio     = if (data.has("mute_audio")) data.optBoolean("mute_audio") else null,
                showToggleToast = if (data.has("show_toast")) data.optBoolean("show_toast") else null,
                rtspTransport = data.optString("rtsp_transport", null), // "tcp", "udp"
                rtspLatency = data.optString("rtsp_latency", null),     // "low", "high"
                useSoftwareDecoder = if (data.has("software_decoder")) data.optBoolean("software_decoder") else null
            )
            ctx.listener.onCameraRequest(req)
            return
        }

        // Legacy quickbar alias or legacy camera alias
        val alias = data.optString("alias", "")
        val cameraAliasLegacy = data.optString("camera_alias", "")

        when {
            alias.isNotBlank() -> ctx.listener.onQuickBarAliasTriggered(alias)
            cameraAliasLegacy.isNotBlank() -> ctx.listener.onCameraRequest(CameraRequest(cameraAlias = cameraAliasLegacy))
            else -> Log.w("HAClient", "QuickBar trigger without camera/alias: $data")
        }
    }
}
