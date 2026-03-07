package dev.trooped.tvquickbars.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dev.trooped.tvquickbars.data.EntityAction
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.PressType
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import org.json.JSONObject


/**
 * Singleton object responsible for executing actions triggered by user interactions with Home Assistant entities.
 *
 * This executor handles the logic for determining which action to take (e.g., toggling a switch,
 * expanding a control panel, or calling a specific service) based on the [PressType] (Single, Long, Double)
 * and the entity's domain.
 *
 * Key responsibilities:
 * - Maintains a [QuickBarDataCache] for real-time state and attribute tracking.
 * - Resolves conflicts between user-defined explicit actions and system-calculated defaults.
 * - Executes built-in special logic (e.g., Climate/Fan memory, Camera Picture-in-Picture).
 * - Dispatches service calls to the [HomeAssistantClient].
 * - Broadcasts execution events locally via [LocalBroadcastManager].
 */
object EntityActionExecutor {

    /**
     * An internal cache that maintains the real-time state and metadata of entities currently
     * managed by the QuickBar.
     *
     * This cache synchronizes incoming Home Assistant events with the displayed UI components
     * to ensure that interaction logic (such as memory-based toggles for fans or climate
     * controls) uses the most up-to-date attributes even if the [EntityItem] has not yet
     * been fully refreshed from the JSON file.
     */
    object QuickBarDataCache {
        // Keep track of entities being displayed in the QuickBar
        var cachedEntities = listOf<EntityItem>()

        // Map to store the latest state of each entity from events
        private val latestEntityStates = mutableMapOf<String, String>()

        // Map to store the latest attributes of each entity from events
        private val latestEntityAttributes = mutableMapOf<String, JSONObject>()


        // Update the latest state when we receive state change events
        fun updateEntityState(entityId: String, state: String) {
            latestEntityStates[entityId] = state
        }

        fun updateEntityAttributes(entityId: String, attributes: JSONObject) {
            latestEntityAttributes[entityId] = attributes
        }

        // Get the latest known state
        fun getLatestEntityState(entityId: String): String? {
            return latestEntityStates[entityId]
        }

        fun getLatestEntityAttributes(entityId: String): JSONObject? {
            return latestEntityAttributes[entityId]
        }
    }

    private enum class DefaultAct { EXPAND, TOGGLE, PRESS, TURN_ON, NONE, CAMERA_PIP, TRIGGER}

    private fun defaultActionFor(entity: EntityItem, press: PressType): DefaultAct {
        val domain = entity.id.substringBefore('.')
        val isSimpleLight = (entity.lastKnownState["is_simple_light"] as? Boolean) ?: false

        return when (press) {
            PressType.SINGLE -> when {
                domain == "camera" -> DefaultAct.CAMERA_PIP
                domain == "light" && isSimpleLight -> DefaultAct.TOGGLE
                domain == "automation" -> {
                    val action = entity.lastKnownState["automation_action"] as? String ?: "trigger"
                    if (action == "trigger") DefaultAct.TRIGGER else DefaultAct.TOGGLE
                }
                domain in listOf("climate","fan","cover","lock","alarm_control_panel","light") -> DefaultAct.EXPAND
                domain in listOf("switch","input_boolean", "media_player") -> DefaultAct.TOGGLE
                domain in listOf("button","input_button") -> DefaultAct.PRESS
                domain in listOf("script","scene") -> DefaultAct.TURN_ON
                else -> DefaultAct.NONE
            }
            PressType.LONG -> when {
                domain == "camera" || domain == "automation" -> DefaultAct.NONE
                domain == "light" && isSimpleLight -> DefaultAct.NONE
                domain in listOf("climate","fan","cover","lock","light") -> DefaultAct.TOGGLE
                else -> DefaultAct.NONE
            }
            PressType.DOUBLE -> DefaultAct.NONE
        }
    }

    /** Resolve what to actually do: prefer explicit pressActions, then computed default. */
    private fun resolveAction(entity: EntityItem, press: PressType): EntityAction {
        // 1) Explicit action wins if not Default
        entity.pressActions[press]?.let { explicit ->
            if (explicit !is EntityAction.Default) {
                return explicit
            }
        }

        // 2) Fallback to computed default
        val defaultAct = defaultActionFor(entity, press)

        return when (defaultAct) {
            DefaultAct.EXPAND      -> EntityAction.BuiltIn(EntityAction.Special.EXPAND)
            DefaultAct.TOGGLE      -> {
                val entityDomain = entity.id.substringBefore('.')
                EntityAction.ServiceCall(entityDomain, "toggle")
            }
            DefaultAct.PRESS       -> EntityAction.ServiceCall("button", "press")
            DefaultAct.TURN_ON     -> EntityAction.ServiceCall("script", "turn_on")
            DefaultAct.NONE        -> EntityAction.Default
            DefaultAct.CAMERA_PIP  -> EntityAction.BuiltIn(EntityAction.Special.CAMERA_PIP)
            DefaultAct.TRIGGER     -> EntityAction.ServiceCall("automation", "trigger")
        }
    }

    /**
     * Executes the appropriate action for a given entity based on the type of interaction (press type).
     *
     * This method resolves the action to be performed by checking explicit user-defined actions
     * or falling back to domain-specific defaults. It handles built-in special behaviors (like
     * climate memory or camera PIP), service calls to Home Assistant, and broadcasting
     * the action events locally.
     */
    fun perform(
        entity: EntityItem,
        press: PressType,
        haClient: HomeAssistantClient?,
        savedEntitiesManager: SavedEntitiesManager,
        onExpand: () -> Unit = {}
    ): Boolean {
        val action = resolveAction(entity, press)
        val context = savedEntitiesManager.context // Get context from savedEntitiesManager

        var result = false

        when (action) {
            is EntityAction.BuiltIn -> {
                when (action.type) {
                    EntityAction.Special.EXPAND -> {
                        try {
                            // keep your "simple light" safety before expanding
                            if (entity.id.startsWith("light.")) {
                                if (!entity.lastKnownState.containsKey("is_simple_light")) {
                                    entity.lastKnownState["is_simple_light"] = false
                                    savedEntitiesManager.applyDefaultLightOptions(entity)
                                }
                            }
                            onExpand()
                            result = true
                        } catch (e: Exception) {
                            Log.e("EntityActionExecutor", "Error expanding entity: ${e.message}", e)
                            result = false
                        }
                    }

                    EntityAction.Special.CLIMATE_TOGGLE_WITH_MEMORY -> {
                        EntityStateUtils.toggleClimateWithMemory(
                            entity,
                            haClient,
                            savedEntitiesManager
                        ); result = true
                    }

                    EntityAction.Special.FAN_TOGGLE_WITH_MEMORY -> {
                        EntityStateUtils.toggleFanWithMemory(
                            entity,
                            haClient,
                            savedEntitiesManager
                        ); result = true
                    }

                    EntityAction.Special.COVER_TOGGLE -> {
                        EntityStateUtils.runCoverToggle(entity, haClient); result = true
                    }

                    EntityAction.Special.LOCK_TOGGLE -> {
                        EntityStateUtils.runLockToggle(entity, haClient); result = true
                    }

                    EntityAction.Special.LIGHT_TOGGLE -> {
                        EntityStateUtils.runLightToggle(entity, haClient); result = true
                    }

                    EntityAction.Special.CAMERA_PIP -> {
                        result = EntityStateUtils.showCameraPip(
                            entity,
                            haClient,
                            savedEntitiesManager
                        )
                    }
                    EntityAction.Special.TRIGGER -> {
                        haClient?.callService("automation", "trigger", entity.id)
                        result = true
                    }

                    EntityAction.Special.MEDIA_PLAYER_TOGGLE -> {
                        haClient?.callService("media_player", "toggle", entity.id)
                        result = true
                    }
                }
            }

            is EntityAction.ControlEntity -> {
                // keep your "control other entity" path
                val targetId = action.targetId
                val entitiesBeingDisplayed = QuickBarDataCache.cachedEntities

                val targetItem = when {
                    targetId == entity.id -> entity
                    entitiesBeingDisplayed.isNotEmpty() -> {
                        entitiesBeingDisplayed.find { it.id == targetId }
                            ?: (savedEntitiesManager.loadEntities().find { it.id == targetId } ?: EntityItem(id = targetId))
                    }
                    else -> savedEntitiesManager.loadEntities().find { it.id == targetId } ?: EntityItem(id = targetId)
                }

                val domain = targetId.substringBefore('.')
                when (domain) {
                    "climate" -> {
                        if (entity.id != targetId && targetItem.state.lowercase() != "off") {
                            QuickBarDataCache.getLatestEntityState(targetId)?.let { latest ->
                                if (latest != targetItem.state) targetItem.state = latest
                            }
                        }
                        EntityStateUtils.toggleClimateWithMemory(targetItem, haClient, savedEntitiesManager)
                    }
                    "fan" -> {
                        // Add this block similar to what we do for climate
                        if (entity.id != targetId) {
                            // Update state if available
                            QuickBarDataCache.getLatestEntityState(targetId)?.let { latest ->
                                if (latest != targetItem.state) targetItem.state = latest
                            }

                            // Update attributes if available
                            QuickBarDataCache.getLatestEntityAttributes(targetId)?.let { latestAttrs ->
                                targetItem.attributes = latestAttrs
                            }
                        }

                        // Now toggle the fan with memory
                        EntityStateUtils.toggleFanWithMemory(targetItem, haClient, savedEntitiesManager)
                    }
                    "cover" -> EntityStateUtils.runCoverToggle(targetItem, haClient)
                    "lock" -> EntityStateUtils.runLockToggle(targetItem, haClient)
                    "light" -> EntityStateUtils.runLightToggle(targetItem, haClient)
                    "switch", "input_boolean", "media_player" -> haClient?.callService(domain, "toggle", targetId)
                    "button", "input_button" -> haClient?.callService(domain, "press", targetId)
                    "script" -> {
                        val service = if (targetItem.state == "on") "turn_off" else "turn_on"
                        haClient?.callService(domain, service, targetId)
                    }
                    "camera" -> {
                        EntityStateUtils.showCameraPip(targetItem, haClient, savedEntitiesManager)
                    }
                    "scene" -> haClient?.callService(domain, "turn_on", targetId)
                    "automation" -> {
                        // Get the automation's preference from the target entity
                        val targetAction = targetItem.lastKnownState["automation_action"] as? String ?: "trigger"
                        haClient?.callService(domain, targetAction, targetId)
                    }
                    else -> runDefault(targetItem, haClient, savedEntitiesManager)
                }
                result = true
            }

            is EntityAction.ServiceCall -> {
                val entityId = entity.id.trim()
                if (!entityId.contains('.')) return false
                val domainFromId = entityId.substringBefore('.').lowercase()

                // Always call using the domain that matches the entity you’re acting on.
                haClient?.callService(domainFromId, action.service, entityId)
                result = true
            }

            EntityAction.Default -> {
                if (press == PressType.SINGLE) {
                    runDefault(entity, haClient, savedEntitiesManager)
                    result = true
                } else {
                    result = false
                }
            }
        }

        // Broadcast the action AFTER it's been executed
        broadcastEntityAction(context, entity.id, press, action)

        return result
    }

    /**
     * Broadcasts a local intent indicating that an entity action has been executed.
     *
     * This allows other components of the application to react to user interactions.
     * Special logic is included for the "light" domain to avoid broadcasting events
     * when the action is merely expanding the control panel rather than toggling state.
     *
     */
    fun broadcastEntityAction(context: Context, entityId: String, pressType: PressType, action: EntityAction) {
        val intent = Intent("dev.trooped.tvquickbars.ENTITY_ACTION")
        intent.putExtra("ENTITY_ID", entityId)
        intent.putExtra("PRESS_TYPE", pressType.name)

        // For light domain, add special handling
        if (entityId.startsWith("light.")) {
            // Only broadcast for toggle actions, not expand
            if (action is EntityAction.BuiltIn && action.type == EntityAction.Special.EXPAND) {
                return
            }
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    /**
     * Executes the fallback logic for entities that do not have a specialized or built-in action.
     *
     * This method implements the legacy toggle/trigger logic, determining the appropriate service
     * (e.g., toggle, press, turn_on) based strictly on the entity's domain. It is typically
     * invoked when an [EntityAction.Default] is resolved or as a safety fallback for
     * [EntityAction.ControlEntity] targets.
     */
    private fun runDefault(
        entity: EntityItem,
        haClient: HomeAssistantClient?,
        savedEntitiesManager: SavedEntitiesManager
    ) {
        val domain = entity.id.substringBefore('.')
        val service = when (domain) {
            "switch", "input_boolean", "light", "media_player" -> "toggle"
            "button", "input_button"           -> "press"
            "script"                           -> if (entity.state == "on") "turn_off" else "turn_on"
            "scene"                            -> "turn_on"
            "automation" -> {
                // Always use the entity's preference
                entity.lastKnownState["automation_action"] as? String ?: "trigger"
            }
            else                               -> return
        }
        Log.d("EntityActionExecutor", "Running default action: $domain.$service on ${entity.id}")
        haClient?.callService(domain, service, entity.id)
    }
}