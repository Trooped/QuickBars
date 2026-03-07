package dev.trooped.tvquickbars.data

import com.google.gson.annotations.SerializedName

/**
 * TriggerKey Data Class
 * Represents a physical remote key that can trigger an action.
 * @property keyCode The Android key code associated with this key.
 * @property keyName The name of the key (according to Android).
 * @property friendlyName A human-readable, user-editable name for the key.
 * @property singlePressAction The action to perform when this key is pressed once.
 * @property doublePressAction The action to perform when this key is pressed twice.
 * @property longPressAction The action to perform when this key is held for a long time.
 * @property singlePressActionType The type of action for single press (e.g., entity, quickbar).
 * @property doublePressActionType The type of action for double press (e.g., entity, quickbar).
 * @property longPressActionType The type of action for long press (e.g., entity, quickbar).
 * @property originalAction The original action string, if this key was created from an existing action.
 * @property appLabel The label of the app that this key is associated with, if applicable.
 * @property enabled Whether this key is enabled or not.
 */
data class TriggerKey(
    @SerializedName("keyCode") val keyCode: Int,
    @SerializedName("keyName") val keyName: String,
    @SerializedName("friendlyName") val friendlyName: String? = null,

    @SerializedName("singlePressAction") val singlePressAction: String? = null,
    @SerializedName("doublePressAction") val doublePressAction: String? = null,
    @SerializedName("longPressAction")   val longPressAction:   String? = null,

    @SerializedName("singlePressActionType") val singlePressActionType: String? = null,
    @SerializedName("doublePressActionType") val doublePressActionType: String? = null,
    @SerializedName("longPressActionType")   val longPressActionType:   String? = null,

    @SerializedName("originalAction") val originalAction: String? = null,
    @SerializedName("appLabel")       val appLabel: String? = null,

    @SerializedName("enabled") val enabled: Boolean = true,

    //FUTURE ATTRIBUTES---------------------------------------------
    @SerializedName("showConfirmationToast") var showConfirmationToast: Boolean = true

    ){
    // Helper function to check if this key has any assignments
    fun hasAnyAssignments(): Boolean {
        return singlePressAction != null || doublePressAction != null || longPressAction != null
    }

    // Helper to get a formatted display of what this key does
    fun getAssignmentSummary(): String {
        val assignments = mutableListOf<String>()

        if (singlePressAction != null) {
            val actionType = singlePressActionType
            assignments.add("Single press: $actionType - $singlePressAction")
        } else if (singlePressAction?.startsWith("launch_app:") == true) {
            val packageName = singlePressAction.substringAfter("launch_app:")
            assignments.add("Single press: Launch app - $packageName")
        }

        if (doublePressAction != null) {
            val actionType = doublePressActionType
            assignments.add("Double press: $actionType - $doublePressAction")
        }

        if (longPressAction != null) {
            val actionType = longPressActionType
            assignments.add("Long press: $actionType - $longPressAction")
        }

        return assignments.joinToString("\n")
    }
}
