package dev.trooped.tvquickbars.ha.ws

import org.json.JSONObject

interface WsHandler {
    /** Receives the inner "event" object (with "event_type", "data", etc.) */
    fun canHandle(event: JSONObject): Boolean
    fun handle(event: JSONObject, ctx: HaClientBridge)
}

class MessageRouter(private val handlers: List<WsHandler>) {
    fun handleIncoming(message: JSONObject, ctx: HaClientBridge) {
        val type = message.optString("type")
        if (type != "event") return

        val event = message.optJSONObject("event") ?: return
        for (h in handlers) {
            if (h.canHandle(event)) {
                h.handle(event, ctx)
                return
            }
        }
    }
}
