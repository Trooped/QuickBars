package dev.trooped.tvquickbars.ha

import dev.trooped.tvquickbars.camera.CameraRequest
import dev.trooped.tvquickbars.data.CategoryItem
import dev.trooped.tvquickbars.notification.NotificationSpec
import org.json.JSONObject

/**
 * HomeAssistantListener Interface
 * Interface for listening to Home Assistant events.
 * Defines callbacks for when entities are fetched and when an entity's state changes.
 * @property onEntitiesFetched Called when all entities have been fetched.
 * @property onEntityStateChanged Called when an entity's state changes.
 * @property onEntityStateUpdated Called when an entity's state changes.
 */
interface HomeAssistantListener {
    fun onEntitiesFetched(categories: List<CategoryItem>)

    fun onEntityStateChanged(entityId: String, newState: String)

    fun onEntityStateUpdated(entityId: String, newState: String, attributes: JSONObject) {
        // Default implementation just calls the simple version for backward compatibility
        onEntityStateChanged(entityId, newState)
    }

    fun onQuickBarAliasTriggered(alias: String) {}

    @Deprecated("Use onCameraRequest(CameraRequest) instead.")
    fun onCameraAliasTrigger(cameraAlias: String) {
        onCameraRequest(CameraRequest(cameraAlias = cameraAlias))
    }

    fun onNotifyReceived(spec: NotificationSpec) {}

    fun onCameraRequest(req: CameraRequest) {}
}