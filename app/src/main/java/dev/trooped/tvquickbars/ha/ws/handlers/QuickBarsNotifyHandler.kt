package dev.trooped.tvquickbars.ha.ws.handlers

import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import dev.trooped.tvquickbars.data.AppIdProvider
import dev.trooped.tvquickbars.ha.ws.HaClientBridge
import dev.trooped.tvquickbars.notification.NotificationSpec
import dev.trooped.tvquickbars.notification.toNotificationSpec
import org.json.JSONObject

class QuickBarsNotifyHandler : dev.trooped.tvquickbars.ha.ws.WsHandler {
    private val TAG = "QuickBarsNotifyHandler" // Optional: for logging

    override fun canHandle(event: JSONObject): Boolean =
        event.optString("event_type") == "quickbars.notify"

    @OptIn(UnstableApi::class)
    override fun handle(event: JSONObject, ctx: HaClientBridge) {
        val context = ctx.getContext()
        if (context == null) {
            Log.w(TAG, "Cannot handle notification event: Context is not available.")
            return
        }

        val data = event.optJSONObject("data") ?: return

        val targetId = data.optString("id", "")         // set by HA service
        val myId = AppIdProvider.get(context) ?: ""
        if (targetId.isNotEmpty() && !targetId.equals(myId, ignoreCase = true)) {
            return // not for this TV → ignore
        }
        // If targetId is missing, we accept it, per the notifications design.

        val cid = data.optString("cid", null)
        val spec: NotificationSpec = data.toNotificationSpec(cid)
        ctx.listener.onNotifyReceived(spec)
    }
}
