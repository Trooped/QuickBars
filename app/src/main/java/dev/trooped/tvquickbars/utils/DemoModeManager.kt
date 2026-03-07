package dev.trooped.tvquickbars.utils

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import dev.trooped.tvquickbars.QuickBarsApp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.CategoryItem
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import org.json.JSONObject

/**
 * Manager class for Demo Mode functionality.
 * This provides fake entities and handles demo-specific logic.
 * This is used for Play Store approval process.
 */
object DemoModeManager {
    // Demo mode trigger credentials
    const val DEMO_URL = "demo.homeassistant.local"
    const val DEMO_TOKEN = "demo_token"
    const val DEMO_WEBSOCKET_URL = "ws://demo.homeassistant.local:8123/api/websocket"

    // Track whether we're in demo mode
    private var _isInDemoMode = false
    val isInDemoMode: Boolean get() = _isInDemoMode

    // Store demo entities with their current states
    private val demoEntities = mutableStateListOf<EntityItem>()

    // Check if the credentials are the demo credentials
    fun isDemoCredentials(url: String, token: String): Boolean {
        return (url.contains(DEMO_URL) || url == DEMO_WEBSOCKET_URL) && token == DEMO_TOKEN
    }

    // Enable demo mode
    fun enableDemoMode() {
        _isInDemoMode = true
        Log.i("DemoMode", "Demo mode enabled")
    }

    // Disable demo mode
    fun disableDemoMode() {
        _isInDemoMode = false
        demoEntities.clear()
        Log.i("DemoMode", "Demo mode disabled")
    }

    // Get the demo entities, categorized appropriately
    fun getDemoCategories(context: Context): List<CategoryItem> {
        if (demoEntities.isEmpty()) {
            createDemoEntities(context)
        }

        // Group entities by domain
        val entityItemsByDomain = demoEntities.groupBy { it.id.split('.').first() }

        // Convert the grouped data into the final List<CategoryItem>
        return entityItemsByDomain.map { (domain, entities) ->
            CategoryItem(
                name = domain.uppercase(),
                entities = entities.sortedBy { it.friendlyName },
            )
        }.sortedBy { it.name }
    }

    // Toggle an entity's state
    fun toggleEntityState(entityId: String): String {
        val entity = demoEntities.find { it.id == entityId } ?: return "unknown"

        // Toggle state based on entity type
        val newState = when {
            entity.id.startsWith("light.") ||
                    entity.id.startsWith("switch.") ||
                    entity.id.startsWith("input_boolean.") -> {
                if (entity.state == "on") "off" else "on"
            }
            entity.id.startsWith("script.") -> {
                // Scripts don't have persistent states, they're just triggered
                "unknown"
            }
            entity.id.startsWith("button.") ||
                    entity.id.startsWith("input_button.") -> {
                // Buttons are just pressed, no persistent state
                "unknown"
            }
            entity.id.startsWith("climate.") -> {
                // For simplicity, just toggle between heat and off
                if (entity.state == "heat") "off" else "heat"
            }
            entity.id.startsWith("fan.") -> {
                if (entity.state == "on") "off" else "on"
            }
            else -> "unknown"
        }

        // Update the entity's state
        val index = demoEntities.indexOfFirst { it.id == entityId }
        if (index != -1) {
            demoEntities[index] = demoEntities[index].copy(state = newState)
        }

        return newState
    }

    // Create a set of demo entities covering all supported types
    private fun createDemoEntities(context: Context) {
        demoEntities.addAll(listOf(
            // Lights
            createEntityItem(
                id = "light.living_room",
                friendlyName = "Living Room Lights",
                state = "on",
                iconOnRes = R.drawable.lightbulb_on,
                iconOffRes = R.drawable.lightbulb_off
            ),
            createEntityItem(
                id = "light.kitchen",
                friendlyName = "Kitchen Lights",
                state = "off",
                iconOnRes = R.drawable.lightbulb_on,
                iconOffRes = R.drawable.lightbulb_off
            ),
            createEntityItem(
                id = "light.bedroom",
                friendlyName = "Bedroom Lights",
                state = "on",
                iconOnRes = R.drawable.lightbulb_on,
                iconOffRes = R.drawable.lightbulb_off
            ),

            // Switches
            createEntityItem(
                id = "switch.coffee_machine",
                friendlyName = "Coffee Machine",
                state = "off",
                iconOnRes = R.drawable.coffee_outline,
                iconOffRes = R.drawable.coffee_off_outline
            ),
            createEntityItem(
                id = "switch.tv_power",
                friendlyName = "TV Power",
                state = "on",
                iconOnRes = R.drawable.television,
                iconOffRes = R.drawable.television_off
            ),

            // Buttons
            createEntityItem(
                id = "button.doorbell",
                friendlyName = "Doorbell",
                state = "unknown",
                iconOnRes = R.drawable.bell,
                iconOffRes = null
            ),

            // Input Buttons
            createEntityItem(
                id = "input_button.garage_door",
                friendlyName = "Garage Door",
                state = "unknown",
                iconOnRes = R.drawable.garage,
                iconOffRes = null
            ),

            // Scripts
            createEntityItem(
                id = "script.movie_mode",
                friendlyName = "Movie Mode",
                state = "unknown",
                iconOnRes = R.drawable.movie,
                iconOffRes = null
            ),
            createEntityItem(
                id = "script.good_morning",
                friendlyName = "Good Morning",
                state = "unknown",
                iconOnRes = R.drawable.weather_sunny,
                iconOffRes = null
            ),

            // Climate
            createEntityItem(
                id = "climate.living_room",
                friendlyName = "Living Room Thermostat",
                state = "heat",
                iconOnRes = R.drawable.ic_ac_unit,
                iconOffRes = R.drawable.ic_ac_unit
            ),

            // Fan
            createEntityItem(
                id = "fan.living_room",
                friendlyName = "Living Room Fan",
                state = "off",
                iconOnRes = R.drawable.fan,
                iconOffRes = R.drawable.fan_off,
                attributes = JSONObject().apply {
                    put("friendly_name", "Living Room Fan")
                    put("percentage", 50)  // Add default percentage
                    put("percentage_step", 25.0)  // Add step size
                }
            ),

            createEntityItem(
                id = "input_boolean.night_mode",
                friendlyName = "Night Mode",
                state = "on",
                iconOnRes = R.drawable.weather_night,
                iconOffRes = R.drawable.weather_sunny
            )
        ))
    }

    // Helper to create EntityItem objects
    private fun createEntityItem(
        id: String,
        friendlyName: String,
        state: String,
        iconOnRes: Int,  // Still accept resource ID for backward compatibility
        iconOffRes: Int?,
        attributes: JSONObject? = null
    ): EntityItem {
        val domain = id.split('.').first()
        val isToggleable = domain in listOf("light", "switch", "input_boolean", "fan", "climate")

        // Convert resource IDs to resource names
        val iconOnName = try {
            QuickBarsApp.getAppContext().resources.getResourceEntryName(iconOnRes)
        } catch (e: Exception) {
            null // If we can't get the name, leave it null
        }

        val iconOffName = try {
            iconOffRes?.let {
                QuickBarsApp.getAppContext().resources.getResourceEntryName(it)
            }
        } catch (e: Exception) {
            null
        }

        // Create default attributes if none provided
        val entityAttributes = attributes ?: JSONObject().apply {
            put("friendly_name", friendlyName)
            if (domain == "light") {
                put("brightness", 255)
                put("color_temp", 300)
            }
            if (domain == "climate") {
                put("temperature", 21.5)
                put("hvac_modes", listOf("heat", "cool", "off"))
                put("current_temperature", 20.0)
            }
        }

        return EntityItem(
            id = id,
            friendlyName = friendlyName,
            customName = friendlyName,
            state = state,
            category = domain,
            attributes = entityAttributes,
            isActionable = isToggleable,
            // Use string resource names instead of resource IDs
            customIconOnName = iconOnName,
            customIconOffName = iconOffName,
            isAvailable = true
        )
    }

    /**
     * Check if the stored credentials are demo credentials and enable demo mode if they are
     * Call this early in app lifecycle to ensure demo mode is properly detected
     */
    fun checkAndEnableDemoMode(context: Context) {
        val url = SecurePrefsManager.getHAUrl(context)
        val token = SecurePrefsManager.getHAToken(context)

        if (url != null && token != null && isDemoCredentials(url, token)) {
            enableDemoMode()
        }
    }

    // Get a specific entity by ID
    fun getEntityById(entityId: String): EntityItem? {
        return demoEntities.find { it.id == entityId }
    }

    /**
     * Handle service calls for demo entities
     */
    fun handleServiceCall(domain: String, service: String, entityId: String, data: JSONObject?): String {
        // Find the entity
        val entityIndex = demoEntities.indexOfFirst { it.id == entityId }
        if (entityIndex == -1) return "unknown"

        val entity = demoEntities[entityIndex]
        var newState = entity.state

        when (domain) {
            "climate" -> {
                newState = handleClimateService(entity, service, data, entityIndex)
            }
            "fan" -> {
                newState = handleFanService(entity, service, data, entityIndex)
            }
            else -> {
                // Default toggle behavior for other entities
                newState = toggleEntityState(entityId)
            }
        }

        return newState
    }

    /**
     * Handle climate service calls with realistic behavior
     */
    private fun handleClimateService(entity: EntityItem, service: String, data: JSONObject?, entityIndex: Int): String {
        val attributes = JSONObject(entity.attributes.toString()) // Create a copy of the attributes
        var newState = entity.state

        when (service) {
            "set_temperature" -> {
                // Update the temperature attribute
                val newTemp = data?.optDouble("temperature", 21.0) ?: 21.0
                attributes.put("temperature", newTemp)

                // Keep the current state, just update the temperature
                demoEntities[entityIndex] = entity.copy(attributes = attributes)
                return entity.state
            }
            "set_hvac_mode" -> {
                // Update the mode
                newState = data?.optString("hvac_mode", "heat") ?: "heat"
                demoEntities[entityIndex] = entity.copy(state = newState, attributes = attributes)
                return newState
            }
            "toggle" -> {
                // Toggle between off and heat
                newState = if (entity.state == "off") "heat" else "off"
                demoEntities[entityIndex] = entity.copy(state = newState, attributes = attributes)
                return newState
            }
        }

        return newState
    }

    /**
     * Handle fan service calls with realistic behavior
     */
    private fun handleFanService(entity: EntityItem, service: String, data: JSONObject?, entityIndex: Int): String {
        val attributes = JSONObject(entity.attributes.toString()) // Create a copy of the attributes
        var newState = entity.state

        when (service) {
            "set_percentage" -> {
                // Update the percentage attribute
                val newPercentage = data?.optInt("percentage", 50) ?: 50
                attributes.put("percentage", newPercentage)

                // If percentage is 0, turn off the fan
                newState = if (newPercentage == 0) "off" else "on"
                demoEntities[entityIndex] = entity.copy(state = newState, attributes = attributes)
                return newState
            }
            "turn_on" -> {
                // Turn on with default percentage if not already set
                if (!attributes.has("percentage")) {
                    attributes.put("percentage", 50)
                }
                newState = "on"
                demoEntities[entityIndex] = entity.copy(state = newState, attributes = attributes)
                return newState
            }
            "turn_off" -> {
                // Turn off but keep the percentage attribute
                newState = "off"
                demoEntities[entityIndex] = entity.copy(state = newState, attributes = attributes)
                return newState
            }
            "toggle" -> {
                // Toggle between on and off
                if (entity.state == "off") {
                    // When turning on, make sure percentage is set
                    if (!attributes.has("percentage")) {
                        attributes.put("percentage", 50)
                    }
                    newState = "on"
                } else {
                    newState = "off"
                }
                demoEntities[entityIndex] = entity.copy(state = newState, attributes = attributes)
                return newState
            }
        }

        return newState
    }
}