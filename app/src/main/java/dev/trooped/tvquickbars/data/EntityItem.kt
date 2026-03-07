package dev.trooped.tvquickbars.data

import androidx.annotation.Keep
import org.json.JSONObject

/**
 * EntityItem Data Class
 * Represents an entity (under a CategoryItem) in the EntityImporterActivity.
 * @property id The unique identifier of the entity (or just entity name in HA).
 * @property friendlyName The friendly name of the entity from HA.
 * @property state The current state of the entity (on/off/unknown etc.)
 * @property category The category of the entity.
 * @property attributes Additional attributes of the entity, from HA.
 * @property isSelected A boolean indicating whether the entity is selected (in the EntityImporterActivity) or not.
 * @property isSaved A boolean indicating whether the entity is a saved entity (selected permanently) or not.
 * @property customName A custom name for the entity.
 * @property customIconOnRes The resource ID of the custom icon for the entity when it is in the "on" state.
 * @property customIconOffRes The resource ID of the custom icon for the entity when it is in the "off" state.
 * @property isActionable A boolean indicating whether the entity can be toggled (on/off).
 * @property isAvailable A boolean indicating whether the entity is still available in Home Assistant (not deleted/changed its ID).
 * @property requireConfirmation A boolean indicating whether confirmation is required to perform actions on this entity.
 * @property overrideService An optional service to override the default action for this entity.
 * @property overrideServiceData Optional data for the override service, stored as a JSON string.
 * @property customIcon A custom icon for the entity.
 */
@Keep
data class EntityItem(
    val id: String,
    val friendlyName: String = "", // The friendly name that is given by Home Assistant
    var state: String = "unknown",
    var category: String = "unknown",
    var attributes: JSONObject? = null,
    var isSelected: Boolean = false, // Is it selected in the EntityImporterActivity?
    var isSaved: Boolean = false, // Is it a saved entity we can use? (selected permanently)
    var customName: String = "", // The custom name the user can give the entity

    var customIconOnName: String? = null,
    var customIconOffName: String? = null,

    var isActionable: Boolean = false, // Can be called directly via a trigger key.
    var isAvailable: Boolean = true, // Determines if the entity is still available in HA (not deleted/changed it's ID)

    // Holds specific state details like fan speed, AC temp, etc.
    var lastKnownState: MutableMap<String, Any> = mutableMapOf(),

    /** key = SINGLE | DOUBLE | LONG  + value = EntityAction | expand */
    var pressActions: MutableMap<PressType, EntityAction> = mutableMapOf(),

    @Deprecated ("use pressActions instead")
    /** key = SINGLE | DOUBLE | LONG  + value = EntityId  TODO deprecate this sometime in the future and mmigrate everything to pressActions */
    var pressTargets: MutableMap<PressType, String?> = mutableMapOf(
        PressType.SINGLE to null,
        PressType.DOUBLE to null,
        PressType.LONG   to null
    ),

    /** Used to persist defaults for new entities */
    var defaultPressActionsApplied: Boolean = false,

    /** An alias to trigger a camera from Home Assistant */
    var cameraAlias: String? = null,

    // FUTURE ATTRIBUTES ------------------------------------------------------------------------------------
    var requireConfirmation: Boolean = false, // For specific entities, require confirmation INSIDE the QuickBar.
    /*
    This would let a user change a button's action from a simple "toggle" to something like
    light.turn_on and provide specific data, like {"brightness": 255, "color_name": "red"}.
    A single button could execute a complex command.
     */
    var overrideService: String? = null,
    var overrideServiceData: String? = null, // Stored as a simple JSON string


    /** signifies if we migrated all actions to pressActions */
    var actionsVersion: Int = 1
)


@Keep
/** Three physical gestures you support on a card */
enum class PressType { SINGLE, DOUBLE, LONG }

@Keep
/** What to do when a gesture fires */
sealed class EntityAction {

    data class ControlEntity(val targetId: String) : EntityAction()

    /** Standard HA service call */
    data class ServiceCall(
        val domain: String,
        val service: String
    ) : EntityAction()

    /** A built-in special that needs code, not just a service */
    enum class Special {
        CLIMATE_TOGGLE_WITH_MEMORY,
        FAN_TOGGLE_WITH_MEMORY,
        COVER_TOGGLE,
        LOCK_TOGGLE,
        LIGHT_TOGGLE,
        EXPAND,
        CAMERA_PIP,
        TRIGGER,
        MEDIA_PLAYER_TOGGLE
    }
    data class BuiltIn(val type: Special) : EntityAction()

    /** If user left this gesture unassigned -> fall back logic */
    object Default : EntityAction()
}

