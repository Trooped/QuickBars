package dev.trooped.tvquickbars.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import dev.trooped.tvquickbars.background.HAStateStore
import dev.trooped.tvquickbars.camera.CameraPipSpec
import dev.trooped.tvquickbars.camera.CameraRequest
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.services.QuickBarService
import dev.trooped.tvquickbars.ui.EntityIconMapper
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.text.equals


/**
 * Keys used for persisting and retrieving entity-specific state data within [dev.trooped.tvquickbars.data.EntityItem.lastKnownState].
 * These keys facilitate "memory" functionality for devices like fans and climate controllers,
 * allowing the application to restore previous settings (speed, temperature, mode) when toggling devices back on.
 */
object EntityStateKeys {
    const val LAST_FAN_SPEED = "last_fan_speed"
    //const val FAN_STEP_PERCENTAGE = "fan_step_percentage"
    const val LAST_AC_TEMP = "last_ac_temp"
    const val LAST_AC_MODE = "last_ac_mode"
    const val LAST_AC_FAN = "last_ac_fan"
}

/**
 * Utility object providing state-aware operations and persistence for Home Assistant entities.
 *
 * This utility handles complex entity interactions such as "toggling with memory" for climate
 * and fan devices—ensuring that when a device is turned back on, it restores its previous
 * settings (temperature, mode, speed) rather than defaulting to a basic "on" state.
 *
 * It also facilitates periodic state capturing to keep the local [SavedEntitiesManager]
 * synchronized with real-time attributes from the Home Assistant instance.
 */
object EntityStateUtils {

    /**
     * Toggles a climate with memory of the last state (temp and mode).
     */
    fun toggleClimateWithMemory(
        entity: EntityItem,
        haClient: HomeAssistantClient?,
        savedEntitiesManager: SavedEntitiesManager
    ) {
        if (haClient == null) {
            Log.e("EntityStateUtils", "Cannot toggle climate: HA client null")
            return
        }

        val attributes = entity.attributes ?: JSONObject()

        // Load last known state from saved entities
        val savedEntities = savedEntitiesManager.loadEntities()
        val savedEntity = savedEntities.find { it.id == entity.id }
        if (savedEntity != null && savedEntity.lastKnownState != null) {
            for ((key, value) in savedEntity.lastKnownState) {
                entity.lastKnownState[key] = value
            }
        }

        try {
            // IMPROVED OFF DETECTION: check both hvac_mode attribute and entity state
            val currentMode = attributes.optString("hvac_mode", "").lowercase().ifEmpty { entity.state.lowercase() }
            val isOff = currentMode == "off" || entity.state.lowercase() == "off"

            if (!isOff) {
                // Device is ON - save state and turn off

                // Save current temperature
                val currentTemp = attributes.optDouble("temperature", Double.NaN)
                if (!currentTemp.isNaN()) {
                    entity.lastKnownState[EntityStateKeys.LAST_AC_TEMP] = currentTemp
                } else {
                    // Try to get target temperature if regular temperature isn't available
                    val targetTemp = attributes.optDouble("target_temp", Double.NaN)
                    if (!targetTemp.isNaN()) {
                        entity.lastKnownState[EntityStateKeys.LAST_AC_TEMP] = targetTemp
                    }
                }

                // Save current mode (IMPORTANT - save actual mode, not just "on")
                val currentModeActual = attributes.optString("hvac_mode", "")
                if (currentModeActual.isNotBlank() && currentModeActual.lowercase() != "off") {
                    entity.lastKnownState[EntityStateKeys.LAST_AC_MODE] = currentModeActual
                }

                // Save fan mode
                val currentFanMode = attributes.optString("fan_mode", "")
                if (currentFanMode.isNotBlank()) {
                    entity.lastKnownState[EntityStateKeys.LAST_AC_FAN] = currentFanMode
                }

                // ALWAYS save entity after updating state
                savedEntitiesManager.saveEntity(entity)

                // Turn off
                val turnOffData = JSONObject().apply {
                    put("hvac_mode", "off")
                }
                haClient.callService("climate", "set_hvac_mode", entity.id, turnOffData)

            } else {   // entity is OFF ────────────────────────────────────────

                // ── 1. decide which mode / temp / fan to use ────────────────
                // Check multiple sources for mode information
                val attributeMode = attributes.optString("hvac_mode", "").lowercase()
                val lastOnOperation = attributes.optString("last_on_operation", "").lowercase()
                val lastUsedOperation = attributes.optString("last_used_operation", "").lowercase()

                // Try to find a non-off mode from attributes
                val modeFromAttrs = when {
                    attributeMode.isNotBlank() && attributeMode != "off" -> attributeMode
                    lastOnOperation.isNotBlank() && lastOnOperation != "off" -> lastOnOperation
                    lastUsedOperation.isNotBlank() && lastUsedOperation != "off" -> lastUsedOperation
                    else -> null
                }

                // Get saved mode from our storage
                val savedMode = entity.lastKnownState[EntityStateKeys.LAST_AC_MODE] as? String

                // Use the first available mode, with "cool" as final fallback
                val modeToUse = when {
                    modeFromAttrs != null && modeFromAttrs != "off" -> modeFromAttrs
                    savedMode != null && savedMode != "off" -> savedMode
                    else -> "cool"  // Always fall back to cool if nothing else available
                }

                // Check multiple sources for temperature
                val attributeTemp = attributes.optDouble("temperature", Double.NaN)
                val targetTemp = attributes.optDouble("target_temp", Double.NaN)
                val tempFromAttrs = if (!attributeTemp.isNaN()) attributeTemp
                else if (!targetTemp.isNaN()) targetTemp
                else null

                // Get saved temp from our storage
                val savedTemp = when (val t = entity.lastKnownState[EntityStateKeys.LAST_AC_TEMP]) {
                    is Double -> t
                    is Int    -> t.toDouble()
                    is Float  -> t.toDouble()
                    is String -> t.toDoubleOrNull()
                    else      -> null
                }

                // Use first available temp with 25.0 as fallback
                val tempToUse = tempFromAttrs ?: savedTemp ?: 25.0

                // Get fan mode
                val attributeFanMode = attributes.optString("fan_mode", "")
                val savedFanMode = entity.lastKnownState[EntityStateKeys.LAST_AC_FAN] as? String
                val fanToUse = attributeFanMode.ifBlank { savedFanMode }
                if (!fanToUse.isNullOrBlank()) {
                    Log.d("EntityStateUtils", "Using fan mode: $fanToUse")
                }

                // ── 2. legacy integrations may need an explicit turn_on ────
                haClient.callService("climate", "turn_on", entity.id)

                // ── 3. single combined payload works for every platform ────
                val payload = JSONObject().apply {
                    put("temperature", tempToUse)
                    put("hvac_mode", modeToUse)
                    if (!fanToUse.isNullOrBlank()) put("fan_mode", fanToUse)
                }
                haClient.callService("climate", "set_temperature", entity.id, payload)

                // ── 4. remember what we just used ──────────────────────────
                entity.lastKnownState[EntityStateKeys.LAST_AC_TEMP] = tempToUse
                entity.lastKnownState[EntityStateKeys.LAST_AC_MODE] = modeToUse
                if (!fanToUse.isNullOrBlank()) {
                    entity.lastKnownState[EntityStateKeys.LAST_AC_FAN] = fanToUse
                }
                savedEntitiesManager.saveEntity(entity)
            }
        } catch (e: Exception) {
            Log.e("EntityStateUtils", "Error toggling climate with memory: ${e.message}", e)
            // Try to save whatever state we have, even if an error occurred
            try {
                savedEntitiesManager.saveEntity(entity)
            } catch (saveEx: Exception) {
                Log.e("EntityStateUtils", "Additionally failed to save entity state: ${saveEx.message}")
            }
        }
    }

    /**
     * Captures the current climate state if it's on.
     * Call this whenever climate entities are displayed
     */
    fun captureClimateState(entity: EntityItem, savedEntitiesManager: SavedEntitiesManager) {
        if (!entity.id.startsWith("climate.")) return
        if (entity.state == "off" || entity.state == "unavailable" || entity.state == "unknown") return

        val wasSaved = entity.isSaved
        val attributes = entity.attributes ?: return
        val currentTemp = attributes.optDouble("temperature", Double.NaN)
        val currentMode = attributes.optString("hvac_mode", "")

        if (!currentTemp.isNaN()) {
            val existingTemp = entity.lastKnownState[EntityStateKeys.LAST_AC_TEMP] as? Double
            if (existingTemp == null || existingTemp != currentTemp) {
                entity.lastKnownState[EntityStateKeys.LAST_AC_TEMP] = currentTemp
            }
        }

        if (currentMode.isNotBlank()) {
            val existingMode = entity.lastKnownState[EntityStateKeys.LAST_AC_MODE] as? String
            if (existingMode == null || existingMode != currentMode) {
                entity.lastKnownState[EntityStateKeys.LAST_AC_MODE] = currentMode
            }
        }

        if (entity.customIconOnName.isNullOrEmpty()) {
            entity.customIconOnName = EntityIconMapper.getDefaultOnIconForEntityName(entity.id)
        }

        // Preserve isSaved flag
        if (wasSaved != entity.isSaved) {
            entity.isSaved = true
        }

        savedEntitiesManager.saveEntity(entity)
    }

    /**
     * Toggle a fan with memory (saves speed when turning off, restores when turning on)
     */
    fun toggleFanWithMemory(
        entity: EntityItem,
        haClient: HomeAssistantClient?,
        savedEntitiesManager: SavedEntitiesManager
    ) {
        // ---------- bring lastKnownState in ---------------------
        savedEntitiesManager.loadEntities()
            .find { it.id == entity.id }
            ?.lastKnownState
            ?.forEach { (k, v) -> entity.lastKnownState[k] = v }

        if (haClient == null) {
            Log.e("EntityStateUtils", "Cannot toggle fan: HA client null")
            return
        }

        try {
            val isOn = entity.state == "on"
            val attributes = entity.attributes ?: JSONObject()

            if (isOn) {
                /* SAVE the current speed then turn OFF ------------------- */
                val currentSpeed = attributes.optInt("percentage", 0)
                if (currentSpeed > 0) {
                    entity.lastKnownState[EntityStateKeys.LAST_FAN_SPEED] = currentSpeed
                    savedEntitiesManager.saveEntity(entity)
                }
                haClient.callService("fan", "turn_off", entity.id)
            } else {
                // Get last saved speed or use default
                val lastSpeed = when (val speed = entity.lastKnownState[EntityStateKeys.LAST_FAN_SPEED]) {
                    is Int -> speed
                    is Double -> speed.toInt()
                    is Float -> speed.toInt()
                    is String -> speed.toIntOrNull()
                    else -> null
                }
                val speedToUse = (lastSpeed ?: 33).coerceIn(1, 100)

                // Set flag to handle intermediate updates
                entity.lastKnownState["fan_turning_on_to"] = speedToUse

                // Turn on fan first (required by some integrations)
                haClient.callService("fan", "turn_on", entity.id)

                // Set the saved percentage
                val data = JSONObject().apply { put("percentage", speedToUse) }
                haClient.callService("fan", "set_percentage", entity.id, data)

                // Update last known state
                entity.lastKnownState[EntityStateKeys.LAST_FAN_SPEED] = speedToUse
                savedEntitiesManager.saveEntity(entity)
            }
        } catch (e: Exception) {
            Log.e("EntityStateUtils",
                "Error toggling fan with memory: ${e.message}", e)
            Log.e("EntityStateUtils",
                "Entity data: id=${entity.id}, state=${entity.state}")
            Log.e("EntityStateUtils",
                "lastKnownState: ${entity.lastKnownState}")
        }
    }

    /**
     * Captures the current fan speed if it's on
     * Call this whenever fan entities are displayed
     */
    fun captureFanSpeed(entity: EntityItem, savedEntitiesManager: SavedEntitiesManager) {
        // Only process fan entities that are on
        if (entity.id.startsWith("fan.") && entity.state == "on") {
            val wasSaved = entity.isSaved
            try {
                val attributes = entity.attributes ?: return
                val currentSpeed = attributes.optInt("percentage", 0)

                // Only save if speed is > 0
                if (currentSpeed > 0) {
                    // Check if we need to update
                    val existingSpeed = entity.lastKnownState[EntityStateKeys.LAST_FAN_SPEED] as? Int

                    if (existingSpeed == null || existingSpeed != currentSpeed) {
                        entity.lastKnownState[EntityStateKeys.LAST_FAN_SPEED] = currentSpeed

                        // Preserve isSaved flag
                        if (wasSaved != entity.isSaved) {
                            entity.isSaved = true
                        }

                        savedEntitiesManager.saveEntity(entity)
                    }
                }
            } catch (e: Exception) {
                Log.e("EntityStateUtils", "Error in captureFanSpeed: ${e.message}", e)
            }
        }
    }

    fun runCoverToggle(entity: EntityItem, ha: HomeAssistantClient?) {
        ha?.callService("cover", "toggle", entity.id)
    }

    fun runLockToggle(entity: EntityItem, ha: HomeAssistantClient?) {
        val service = if (entity.state == "locked") "unlock" else "lock"
        ha?.callService("lock", service, entity.id)
    }

    /**
     * Determines the appropriate service ("lock" or "unlock") for a lock entity
     * by querying its current state.
     * Returns null if the state cannot be determined or connection fails.
     */
    suspend fun determineLockService(entityId: String, isClientConnected : Boolean): String? {
        // 1) If we have a very fresh state from event stream, use it immediately
        EntityActionExecutor.QuickBarDataCache.getLatestEntityState(entityId)?.let { s ->
            return if (s.equals("locked", ignoreCase = true)) "unlock" else "lock"
        }

        // 2) Ensure we are connected. If the background manager is running, we'll reuse it.
        if (!isClientConnected) return null

        // At this point, HomeAssistantClient will perform the initial get_states
        // and push entities into HAStateStore.setCategories(...).
        // Wait briefly for HAStateStore.entitiesById to contain our entity.
        val maxWaitMs = 1500
        val stepMs = 100L
        var waited = 0

        while (waited < maxWaitMs) {
            val state = HAStateStore.entitiesById.value[entityId]?.state
            if (state != null) {
                return if (state.equals("locked", ignoreCase = true)) "unlock" else "lock"
            }
            delay(stepMs)
            waited += stepMs.toInt()
        }

        // 3) Still unknown – prefer a deterministic, safe fallback and inform the user.
        //showToast("Couldn’t read state for $entityId; defaulting to unlock")
        return "unlock"
    }

    fun runLightToggle(entity: EntityItem, ha: HomeAssistantClient?) {
        ha?.callService("light", "toggle", entity.id)
    }

    /**
     * Shows a camera entity in picture-in-picture mode
     */
    fun showCameraPip(
        entity: EntityItem,
        haClient: HomeAssistantClient?,
        savedEntitiesManager: SavedEntitiesManager,
        explicitContext: Context? = null
    ): Boolean {
        val svc = QuickBarService.serviceInstance ?: return false
        svc.handleCameraRequest(CameraRequest(cameraEntity = entity.id))
        return true
    }



}