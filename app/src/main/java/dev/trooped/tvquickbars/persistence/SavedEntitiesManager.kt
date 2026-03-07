package dev.trooped.tvquickbars.persistence

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.Keep
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import dev.trooped.tvquickbars.data.EntityAction
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.PressType
import dev.trooped.tvquickbars.data.capsFromModes
import dev.trooped.tvquickbars.data.deduceColorMode
import dev.trooped.tvquickbars.data.deriveSupportedColorModes
import dev.trooped.tvquickbars.data.normalizeSupportedColorModes
import dev.trooped.tvquickbars.services.QuickBarService
import dev.trooped.tvquickbars.ui.EntityIconMapper
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Type

@Keep
/**
 * SavedEntitiesManager Class
 * This class handles saving and loading of saved entities (the entities that are imported from HA).
 * It uses Gson for serialization and deserialization.
 * @property prefs The SharedPreferences instance for saving data.
 * @property entitiesKey The key used to store entities in SharedPreferences.
 * @property gson The Gson instance for serialization and deserialization.
 */
class SavedEntitiesManager(val context: Context) {
    private val prefs = context.getSharedPreferences("HA_Saved_Entities", Context.MODE_PRIVATE)
    private val entitiesKey = "saved_entity_list"
    val gson = GsonBuilder()
        .registerTypeAdapter(EntityAction::class.java, EntityActionTypeAdapter())
        .registerTypeAdapter(
            object : TypeToken<Map<PressType, String?>>() {}.type,
            PressTargetsTypeAdapter()
        )
        .registerTypeAdapter(
            object : TypeToken<Map<String, Any?>>() {}.type,
            LastKnownStateAdapter()
        )
        .create()

    // Add this inner class for EntityAction deserialization
    private class EntityActionTypeAdapter : JsonDeserializer<EntityAction>,
        JsonSerializer<EntityAction> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): EntityAction {
            val jsonObject = json.asJsonObject

            return when {
                jsonObject.has("targetId") -> {
                    // It's a ControlEntity action
                    val targetId = jsonObject.get("targetId").asString
                    EntityAction.ControlEntity(targetId)
                }

                jsonObject.has("type") -> {
                    // It's a BuiltIn action
                    val typeObj = jsonObject.getAsJsonObject("type")
                    val name = typeObj.get("name").asString
                    val specialType = EntityAction.Special.valueOf(name)
                    EntityAction.BuiltIn(specialType)
                }

                jsonObject.has("domain") -> {
                    // It's a ServiceCall action (which only has domain and service)
                    val domain = jsonObject.get("domain").asString
                    val service = jsonObject.get("service").asString
                    EntityAction.ServiceCall(domain, service)
                }

                else -> {
                    // Default action
                    EntityAction.Default
                }
            }
        }

        override fun serialize(
            src: EntityAction,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val jsonObject = JsonObject()

            when (src) {
                is EntityAction.ControlEntity -> {
                    jsonObject.addProperty("targetId", src.targetId)
                }

                is EntityAction.BuiltIn -> {
                    val typeObj = JsonObject()
                    typeObj.addProperty("name", src.type.name)
                    jsonObject.add("type", typeObj)
                }

                is EntityAction.ServiceCall -> {
                    jsonObject.addProperty("domain", src.domain)
                    jsonObject.addProperty("service", src.service)
                }

                is EntityAction.Default -> {
                    // No additional properties needed
                }
            }

            return jsonObject
        }
    }

    private class PressTargetsTypeAdapter : JsonSerializer<Map<PressType, String?>>,
        JsonDeserializer<Map<PressType, String?>> {

        override fun serialize(
            src: Map<PressType, String?>,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val jsonObject = JsonObject()

            // Only serialize non-null values
            src.forEach { (pressType, targetId) ->
                if (targetId != null) {
                    jsonObject.addProperty(pressType.name, targetId)
                }
            }

            return jsonObject
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): Map<PressType, String?> {
            // Explicitly define the type for the result map
            val result: MutableMap<PressType, String?> = mutableMapOf(
                PressType.SINGLE to null,
                PressType.DOUBLE to null,
                PressType.LONG to null
            )

            if (json.isJsonObject) {
                val jsonObject = json.asJsonObject

                // Process each press type
                PressType.values().forEach { pressType ->
                    if (jsonObject.has(pressType.name)) {
                        val element = jsonObject.get(pressType.name)
                        if (!element.isJsonNull) {
                            // Create an intermediate variable with explicit type
                            val value: String = element.asString
                            result[pressType] = value
                        }
                    }
                }
            }

            return result
        }
    }

    private class LastKnownStateAdapter : JsonSerializer<Map<String, Any?>>,
        JsonDeserializer<Map<String, Any?>> {

        override fun serialize(src: Map<String, Any?>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val jsonObject = JsonObject()

            for ((key, value) in src) {
                when (value) {
                    is Boolean -> jsonObject.addProperty(key, value)
                    is Number -> jsonObject.addProperty(key, value)
                    is String -> jsonObject.addProperty(key, value)
                    null -> jsonObject.add(key, null)
                    else -> jsonObject.addProperty(key, value.toString())
                }
            }

            return jsonObject
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()

            if (json.isJsonObject) {
                val jsonObject = json.asJsonObject

                for ((key, element) in jsonObject.entrySet()) {
                    if (element.isJsonNull) {
                        result[key] = null
                    } else if (element.isJsonPrimitive) {
                        val primitive = element.asJsonPrimitive
                        when {
                            primitive.isBoolean -> result[key] = primitive.asBoolean
                            primitive.isNumber -> result[key] = primitive.asNumber
                            else -> {
                                // Check if string is "true" or "false" and convert accordingly
                                val asString = primitive.asString
                                when (asString.lowercase()) {
                                    "true" -> result[key] = true
                                    "false" -> result[key] = false
                                    else -> result[key] = asString
                                }
                            }
                        }
                    }
                }
            }

            return result
        }
    }

    /**
     * Save a list of entities to SharedPreferences.
     */
    fun saveEntities(entities: List<EntityItem>) {
        val entitiesToSave = entities.filter { it.isSaved }

        val jsonString = gson.toJson(entitiesToSave)
        prefs.edit() { putString(entitiesKey, jsonString) }
    }

    fun saveEntity(entity: EntityItem) {
        // Get current entities
        val entities = loadEntities()

        // Find index
        val index = entities.indexOfFirst { it.id == entity.id }
        val alreadySaved = index >= 0

        // If the entity already exists in saved list, preserve isSaved=true status, even though it didn't preserve it
        if (alreadySaved && !entity.isSaved) {
            entity.isSaved = true
        }

        // Validate icons before saving
        val validatedEntity = validateEntityIcons(entity)

        if (!alreadySaved) {
            applyDefaultActions(validatedEntity)
        }

        // Ensure isSaved flag was preserved by validation
        if (entity.isSaved && !validatedEntity.isSaved) {
            validatedEntity.isSaved = true
        }

        if (index >= 0) {
            // Entity exists, just replace it in the list with the updated version
            entities[index] = validatedEntity
        } else {
            // Entity is new, add it to the list
            entities.add(validatedEntity)
        }

        // Save the updated list
        saveEntities(entities)
    }

    // Validate entity icons and fix if needed
    private fun validateEntityIcons(entity: EntityItem): EntityItem {
        // Check if ON icon is valid
        val validOnIcon = !entity.customIconOnName.isNullOrEmpty() &&
                EntityIconMapper.isValidDrawableResourceName(entity.customIconOnName)

        // Check if OFF icon is valid
        val validOffIcon = entity.customIconOffName.isNullOrEmpty() ||
                EntityIconMapper.isValidDrawableResourceName(entity.customIconOffName)

        // If either icon is invalid, create and return a new entity with default icons
        if (!validOnIcon || !validOffIcon) {
            // Log the issue
            Log.w(
                "EntityIcons", "Invalid icons detected for ${entity.id} - " +
                        "ON: ${entity.customIconOnName} (valid: $validOnIcon), " +
                        "OFF: ${entity.customIconOffName} (valid: $validOffIcon)"
            )

            // Get the default icons
            val defaultOnIcon = EntityIconMapper.getDefaultOnIconForEntityName(entity.id)
            val defaultOffIcon = if (entity.isActionable) {
                EntityIconMapper.getDefaultOffIconForEntityName(entity.id)
            } else null

            Log.d(
                "EntityIcons", "Fixed icons for ${entity.id} - " +
                        "ON: $defaultOnIcon, OFF: $defaultOffIcon"
            )

            // Return a new copy with the corrected icons BUT PRESERVE ALL OTHER PROPERTIES
            return entity.copy(
                customName = entity.customName,
                friendlyName = entity.friendlyName,
                state = entity.state,
                category = entity.category,
                attributes = entity.attributes,
                isSelected = entity.isSelected,
                isSaved = entity.isSaved,
                isActionable = entity.isActionable,
                isAvailable = entity.isAvailable,
                lastKnownState = entity.lastKnownState,
                customIconOnName = defaultOnIcon,
                customIconOffName = defaultOffIcon,
                pressActions = entity.pressActions,
                defaultPressActionsApplied = entity.defaultPressActionsApplied,
                pressTargets = entity.pressTargets,
                cameraAlias = entity.cameraAlias,

                //future data?
                requireConfirmation = entity.requireConfirmation,
                overrideService = entity.overrideService,
                overrideServiceData = entity.overrideServiceData,
            )
        }

        // If all icons are valid, return the original entity
        return entity
    }

    /**
     * Load a list of entities from SharedPreferences.
     */
    fun loadEntities(): MutableList<EntityItem> {
        val jsonString = prefs.getString(entitiesKey, null)
        val entities = if (jsonString != null) {
            try {
                val type = object : TypeToken<MutableList<EntityItem>>() {}.type
                val loadedEntities: MutableList<EntityItem> = gson.fromJson(jsonString, type)

                // Ensure all loaded entities have basic properties
                loadedEntities.forEach {
                    it.isSaved = true

                    // Initialize lastKnownState if it's null
                    if (it.lastKnownState == null) {
                        it.lastKnownState = mutableMapOf()

                        if (it.id.startsWith("light.")) {
                            applyDefaultLightOptions(it)  // Ensure light options are applied (and also migration from old method)
                        }
                    }

                    it.isAvailable = true

                    // Initialize lastKnownState if it's null
                    if (it.lastKnownState == null) {
                        it.lastKnownState = mutableMapOf()
                    }

                    // Initialize pressActions if it's null
                    if (it.pressActions == null) {
                        it.pressActions = mutableMapOf()
                    }

                    if (it.pressTargets == null) {
                        it.pressTargets = mutableMapOf()
                    }

                    migrateActionsToV2(it)

                    // Apply default actions to entities
                    if (!it.defaultPressActionsApplied) {
                        applyDefaultActions(it)
                    }

                    // Set default icon names if missing
                    if (it.customIconOnName.isNullOrEmpty()) {
                        it.customIconOnName = getDefaultOnIconForEntityName(it.id)

                        if (EntityIconMapper.isEntityToggleable(it.id)) {
                            it.customIconOffName = getDefaultOffIconForEntityName(it.id)
                        }
                    }
                }
                loadedEntities
            } catch (e: Exception) {
                Log.e("SavedEntitiesManager", "Error parsing saved entities: ${e.message}")
                // Clear corrupt data and return empty list
                handleParsingError()
                mutableListOf<EntityItem>()
            }
        } else {
            mutableListOf()
        }

        // Validate all entity icons after loading
        val validatedEntities = validateAllEntityIcons(entities)

        // If any entities were fixed, save them back
        if (validatedEntities.second) {
            saveEntities(validatedEntities.first)
        }

        return validatedEntities.first
    }


    private fun migrateActionsToV2(entity: EntityItem): Boolean {
        if (entity.actionsVersion >= 2) return false
        var changed = false
        val newActions = entity.pressActions.toMutableMap()

        fun mapTargetToAction(target: String?, press: PressType): EntityAction? {
            return when {
                target == null || target.isBlank() -> null
                target == "expand" -> EntityAction.BuiltIn(EntityAction.Special.EXPAND)
                else -> EntityAction.ControlEntity(target) // includes “self” or “other”
            }
        }

        // 1) Prefer existing explicit pressActions if not Default; else map pressTargets
        PressType.values().forEach { p ->
            val cur = newActions[p]
            if (cur == null || cur is EntityAction.Default) {
                mapTargetToAction(entity.pressTargets[p], p)?.let {
                    newActions[p] = it; changed = true
                }
            }
        }

        // 2) Camera: make intent explicit
        if (entity.id.startsWith("camera.")) {
            if (newActions[PressType.SINGLE] !is EntityAction.BuiltIn ||
                (newActions[PressType.SINGLE] as? EntityAction.BuiltIn)?.type != EntityAction.Special.CAMERA_PIP) {
                newActions[PressType.SINGLE] = EntityAction.BuiltIn(EntityAction.Special.CAMERA_PIP)
                changed = true
            }
            if (newActions[PressType.LONG] == null) {
                newActions[PressType.LONG] = EntityAction.Default
                changed = true
            }
        }

        // 3) Automation: encode the preference as an action
        if (entity.id.startsWith("automation.")) {
            val pref = (entity.lastKnownState["automation_action"] as? String) ?: "trigger"
            val action =
                if (pref == "trigger") EntityAction.BuiltIn(EntityAction.Special.TRIGGER)
                else EntityAction.ServiceCall("automation","toggle")
            if (newActions[PressType.SINGLE] != action) {
                newActions[PressType.SINGLE] = action
                changed = true
            }
            if (newActions[PressType.LONG] == null) {
                newActions[PressType.LONG] = EntityAction.Default
                changed = true
            }
        }

        // 4) Finalize
        if (changed) {
            entity.pressActions = newActions
        }
        entity.actionsVersion = 2
        return changed
    }

    private fun handleParsingError() {
        Log.w("SavedEntitiesManager", "Clearing saved entities to fix data format issues")
        prefs.edit { remove(entitiesKey) }
    }


    /**
     * Apply default settings for climate entities based on their capabilities
     */
    fun applyDefaultClimateOptions(entity: EntityItem) {
        if (entity.id.substringBefore('.') != "climate") return

        val attributes = entity.attributes ?: JSONObject()

        // Check if entity supports these features
        val hasTemperatureSensor = attributes.has("current_temperature")

        val hasModes = try {
            // Try multiple formats for hvac_modes
            val modesJson = attributes.optJSONArray("hvac_modes")
            val modesString = attributes.optString("hvac_modes", "")

            if (modesJson != null && modesJson.length() > 1) {
                true
            } else if (modesString.contains("[") && modesString.contains("]")) {
                // Handle string format like "{values=[heat, cool, off]}"
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("ClimateSettings", "Error checking hvac_modes: ${e.message}")
            false
        }

        val hasFanModes = try {
            // Try multiple formats for fan_modes
            val fanModesJson = attributes.optJSONArray("fan_modes")
            val fanModesString = attributes.optString("fan_modes", "")

            if (fanModesJson != null && fanModesJson.length() > 0) {
                true
            } else if (fanModesString.contains("[") && fanModesString.contains("]")) {
                // Handle string format like "{values=[auto, low, high]}"
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("ClimateSettings", "Error checking fan_modes: ${e.message}")
            false
        }

        if (!entity.lastKnownState.containsKey("show_room_temp")) {
            entity.lastKnownState["show_room_temp"] = hasTemperatureSensor
        }

        if (!entity.lastKnownState.containsKey("show_mode_controls")) {
            entity.lastKnownState["show_mode_controls"] = hasModes
        }

        if (!entity.lastKnownState.containsKey("show_fan_controls")) {
            entity.lastKnownState["show_fan_controls"] = hasFanModes
        }

    }

    /**
     * Apply default settings for light entities based on their capabilities
     */
    fun applyDefaultLightOptions(entity: EntityItem) {
        if (!entity.id.startsWith("light.")) return

        val attrs = (entity.attributes ?: JSONObject())
        val scm   = deriveSupportedColorModes(attrs)          // Step 1
        val _cm   = deduceColorMode(attrs, scm)               // Step 2 (optional for logging/UI)
        val caps  = capsFromModes(scm)                        // Step 3

        // Persist-only-if-missing
        val lks = entity.lastKnownState
        lks.putIfAbsent("is_simple_light", caps.isSimple)
        lks.putIfAbsent("show_brightness_controls", caps.brightness)
        lks.putIfAbsent("show_color_controls", caps.color)
        lks.putIfAbsent("show_warmth_controls", caps.colorTemp)

        // (Optional) if you want to expose what we deduced for debugging/UI:
        lks.putIfAbsent("deduced_color_mode", _cm)
        lks["supported_color_modes_set"] = scm.joinToString(",") // helpful for diag screens

        // Logging (optional)
        //android.util.Log.d("LightCaps", "id=${entity.id} scm=$scm cm=$_cm caps=$caps lks=$lks")
    }


    /**
     * Apply default settings for fan entities based on their capabilities
     */
    fun applyDefaultFanOptions(entity: EntityItem) {
        if (entity.id.substringBefore('.') != "fan") return

        val attributes = entity.attributes ?: JSONObject()

        if (!entity.lastKnownState.containsKey("custom_step_enabled")) {
            entity.lastKnownState["custom_step_enabled"] = false
        }

        if (!entity.lastKnownState.containsKey("custom_step_percentage")) {
            entity.lastKnownState["custom_step_percentage"] = 0
        }

    }

    /**
     * Apply default settings for camera entities
     */
    fun applyDefaultCameraOptions(entity: EntityItem) {
        if (entity.id.substringBefore('.') != "camera") return

        // Ensure lastKnownState is initialized
        if (entity.lastKnownState == null) {
            entity.lastKnownState = mutableMapOf()
        }

        // Apply default PIP corner position (top-left default)
        if (!entity.lastKnownState.containsKey("pip_corner")) {
            entity.lastKnownState["pip_corner"] = "TOP_LEFT"
        }

        // Apply default PIP size (medium default)
        if (!entity.lastKnownState.containsKey("pip_size")) {
            entity.lastKnownState["pip_size"] = "MEDIUM"
        }

        // Show camera title by default
        if (!entity.lastKnownState.containsKey("show_title")) {
            entity.lastKnownState["show_title"] = true
        }

        if (!entity.lastKnownState.containsKey("auto_hide_timeout")) {
            entity.lastKnownState["auto_hide_timeout"] = 30
        }
    }

    fun applyDefaultActions(entity: EntityItem) {
        if (entity.defaultPressActionsApplied) {
            Log.d("EntitySettings", "Default actions already applied for ${entity.id}, skipping")
            return
        }

        val domain = entity.id.substringBefore('.')

        // Save existing targets
        val existingTargets = entity.pressTargets.toMutableMap()

        // Apply domain-specific settings first
        when (domain) {
            "climate" -> {
                applyDefaultClimateOptions(entity)
            }
            "light" -> applyDefaultLightOptions(entity)
            "fan" -> applyDefaultFanOptions(entity)
            "camera" -> {
                applyDefaultCameraOptions(entity)
            }
        }

        val isSimpleLight: Boolean = when (domain) {
            "light" -> {
                val v = entity.lastKnownState["is_simple_light"] as? Boolean
                if (v != null) v else {
                    // belt & suspenders in case a legacy entity slipped through
                    applyDefaultLightOptions(entity)
                    (entity.lastKnownState["is_simple_light"] as? Boolean) ?: false
                }
            }
            else -> false
        }

        if (domain == "camera") {
            // Single press should invoke our built-in CAMERA_PIP action.
            entity.pressActions[PressType.SINGLE] = EntityAction.BuiltIn(EntityAction.Special.CAMERA_PIP)

            // No default long-press for camera
            entity.pressActions[PressType.LONG] = EntityAction.Default

            Log.d("EntityActions", "📋 Set camera ${entity.id} actions: SINGLE=CAMERA_PIP, LONG=Default")
        }

        if (entity.id.substringBefore('.') == "automation" && !entity.lastKnownState.containsKey("automation_action")) {
            entity.lastKnownState["automation_action"] = "trigger" // Default to "trigger"
        }

        // Set press targets for simple lights
        if (domain == "light" && isSimpleLight) {
            // update the actions
            entity.pressActions[PressType.SINGLE] = EntityAction.ServiceCall(domain, "toggle")
            entity.pressActions[PressType.LONG] = EntityAction.Default
        }
        else {
            // ---------- SINGLE click ----------
            entity.pressActions[PressType.SINGLE] = when {
                // Special case for simple lights
                domain == "light" && isSimpleLight ->
                    EntityAction.ServiceCall(domain, "toggle")

                domain == "automation" -> EntityAction.Default

                // Normal behavior for other entities
                domain in listOf("climate", "fan", "cover", "lock", "alarm_control_panel", "light", "media_player") ->
                    EntityAction.BuiltIn(EntityAction.Special.EXPAND)

                domain in listOf("switch", "input_boolean") ->
                    EntityAction.ServiceCall(domain, "toggle")

                domain in listOf("button", "input_button") ->
                    EntityAction.ServiceCall(domain, "press")

                domain in listOf("script", "scene") ->
                    EntityAction.ServiceCall(domain, "turn_on")

                else -> EntityAction.Default
            }

            // ---------- LONG click ----------
            when {
                // No long-press action for simple lights
                domain == "light" && isSimpleLight ->
                    entity.pressActions[PressType.LONG] = EntityAction.Default

                // Normal behavior for other entities
                domain == "climate" -> entity.pressActions[PressType.LONG] =
                    EntityAction.BuiltIn(EntityAction.Special.CLIMATE_TOGGLE_WITH_MEMORY)

                domain == "media_player" -> entity.pressActions[PressType.LONG] =
                    EntityAction.BuiltIn(EntityAction.Special.MEDIA_PLAYER_TOGGLE)

                domain == "fan" -> entity.pressActions[PressType.LONG] =
                    EntityAction.BuiltIn(EntityAction.Special.FAN_TOGGLE_WITH_MEMORY)

                domain == "cover" -> entity.pressActions[PressType.LONG] =
                    EntityAction.BuiltIn(EntityAction.Special.COVER_TOGGLE)

                domain == "lock" -> entity.pressActions[PressType.LONG] =
                    EntityAction.BuiltIn(EntityAction.Special.LOCK_TOGGLE)

                domain == "light" -> entity.pressActions[PressType.LONG] =
                    EntityAction.BuiltIn(EntityAction.Special.LIGHT_TOGGLE)

                else -> entity.pressActions[PressType.LONG] = EntityAction.Default
            }
        }

        // RESTORE EXISTING TARGETS
        existingTargets.forEach { (pressType, targetId) ->
            if (targetId != null) {
                entity.pressTargets[pressType] = targetId
            }
        }

        entity.defaultPressActionsApplied = true
    }

    // Helper method to get default icon names
    private fun getDefaultOnIconForEntityName(entityId: String): String {
        return EntityIconMapper.getDefaultOnIconForEntityName(entityId)
    }

    private fun getDefaultOffIconForEntityName(entityId: String): String {
        return EntityIconMapper.getDefaultOffIconForEntityName(entityId)
    }

    private fun validateAllEntityIcons(entities: MutableList<EntityItem>): Pair<MutableList<EntityItem>, Boolean> {
        var anyFixed = false
        val validatedEntities = entities.map { entity ->
            val validatedEntity = validateEntityIcons(entity)
            if (validatedEntity != entity) {
                anyFixed = true
            }
            validatedEntity
        }.toMutableList()

        return Pair(validatedEntities, anyFixed)
    }

    /**
     * Remove an entity and update all QuickBars and TriggerKeys that reference it
     * @param entityId The entity ID to remove
     * @return true if the entity was removed successfully
     */
    fun removeEntity(entityId: String): Boolean {
        val entities = loadEntities().toMutableList()
        val wasRemoved = entities.removeAll { it.id == entityId }

        if (wasRemoved) {
            // Save the updated entities
            saveEntities(entities)

            // Also remove from all QuickBars
            val quickBarManager = QuickBarManager(context)
            val updatedQuickBars = quickBarManager.removeEntityFromAllQuickBars(entityId)

            val triggerKeyManager = TriggerKeyManager(context)
            val updatedTriggerKeys = triggerKeyManager.removeEntityReference(entityId)

            if (updatedQuickBars > 0 || updatedTriggerKeys > 0) {
                Log.d("SavedEntitiesManager",
                    "Removed entity $entityId from $updatedQuickBars QuickBars and $updatedTriggerKeys TriggerKeys")

                // Notify the QuickBarService to reload trigger keys!
                if (updatedTriggerKeys > 0) {
                    val intent = Intent(context, QuickBarService::class.java).apply {
                        action = "ACTION_RELOAD_TRIGGER_KEYS"
                        putExtra("FORCE_FULL_RELOAD", true)
                    }
                    context.startService(intent)
                }
            }
        }

        return wasRemoved
    }

    /**
     * Get a list of actionable entities (switches, lights, input_booleans, and scripts etc..) - entities that can be called to turn on/off or activate directly
     */
    fun getActionableEntities(): List<EntityItem> {
        return loadEntities().filter { entity ->
            val domain = entity.id.split(".").firstOrNull() ?: ""
            domain == "switch" || domain == "light" || domain == "input_boolean" || domain == "script"  || domain == "button" || domain == "input_button"
                    || domain == "scene" || domain == "automation" || domain == "lock" || domain == "cover" || domain == "climate" || domain == "fan" || domain == "media_player"
        }
    }

    /**
     * Get a list of entities that are missing from Home Assistant (deleted / changed their entity name)
     */
    fun getMissingEntities(): List<EntityItem> =
        loadEntities().filter { !it.isAvailable }
}