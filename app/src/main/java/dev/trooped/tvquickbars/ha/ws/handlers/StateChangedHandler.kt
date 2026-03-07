package dev.trooped.tvquickbars.ha.ws.handlers

import dev.trooped.tvquickbars.ha.ws.HaClientBridge
import dev.trooped.tvquickbars.utils.EntityActionExecutor
import org.json.JSONObject
import android.util.Log

class StateChangedHandler : dev.trooped.tvquickbars.ha.ws.WsHandler {
    override fun canHandle(event: JSONObject): Boolean =
        event.optString("event_type") == "state_changed"

    override fun handle(event: JSONObject, ctx: HaClientBridge) {
        val data = event.optJSONObject("data") ?: return
        val entityId = data.optString("entity_id", null) ?: return

        ctx.knownIds += entityId

        val newStateObj = data.optJSONObject("new_state")
        val newState = newStateObj?.optString("state") ?: return
        val attributes = newStateObj.optJSONObject("attributes") ?: JSONObject()

        // Always update base state in cache
        EntityActionExecutor.QuickBarDataCache.updateEntityState(entityId, newState)

        // Preserve your fan 'turning-on-to-target-speed' interception
        if (entityId.startsWith("fan.") && newState == "on") {
            val targetEntity = EntityActionExecutor.QuickBarDataCache.cachedEntities
                .find { it.id == entityId }

            val targetSpeed = targetEntity?.lastKnownState?.get("fan_turning_on_to") as? Number
            val reportedSpeed = attributes.optInt("percentage", -1)

            if (targetSpeed != null && reportedSpeed > 0) {
                if (reportedSpeed == targetSpeed.toInt()) {
                    EntityActionExecutor.QuickBarDataCache.updateEntityAttributes(entityId, attributes)
                    targetEntity.lastKnownState.remove("fan_turning_on_to")
                } else {
                    val modified = JSONObject(attributes.toString())
                    modified.put("percentage", targetSpeed.toInt())
                    EntityActionExecutor.QuickBarDataCache.updateEntityAttributes(entityId, modified)
                    ctx.listener.onEntityStateUpdated(entityId, newState, modified)
                    ctx.listener.onEntityStateChanged(entityId, newState)
                    return
                }
            } else {
                EntityActionExecutor.QuickBarDataCache.updateEntityAttributes(entityId, attributes)
            }
        } else {
            EntityActionExecutor.QuickBarDataCache.updateEntityAttributes(entityId, attributes)
        }

        ctx.listener.onEntityStateUpdated(entityId, newState, attributes)
        ctx.listener.onEntityStateChanged(entityId, newState)
    }
}
