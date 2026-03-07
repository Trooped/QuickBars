package dev.trooped.tvquickbars.utils

import android.content.Context
import dev.trooped.tvquickbars.persistence.QuickBarManager
import dev.trooped.tvquickbars.persistence.TriggerKeyManager

/**
 * EntityUsageUtils Class
 * This class provides utility functions to check where an entity is used in QuickBars and TriggerKeys.
 */
object EntityUsageUtils {

    /**
     * Get information about where an entity is used
     * @return Pair of (QuickBar names, TriggerKey names) using this entity
     */
    fun getEntityUsageDetails(context: Context, entityId: String): Pair<List<String>, List<String>> {
        // Find QuickBars using this entity
        val quickBarManager = QuickBarManager(context)
        val allQuickBars = quickBarManager.loadQuickBars()
        val quickBarNames = allQuickBars
            .filter { it.savedEntityIds.contains(entityId) }
            .map { it.name }

        // Find TriggerKeys using this entity
        val triggerKeyManager = TriggerKeyManager(context)
        val allTriggerKeys = triggerKeyManager.loadTriggerKeys()
        val triggerKeyNames = allTriggerKeys
            .filter {
                (it.singlePressActionType == "entity" && it.singlePressAction == entityId) ||
                        (it.doublePressActionType == "entity" && it.doublePressAction == entityId) ||
                        (it.longPressActionType == "entity" && it.longPressAction == entityId)
            }
            .map { it.friendlyName }

        return Pair(quickBarNames, triggerKeyNames) as Pair<List<String>, List<String>>
    }

    /**
     * Build dialog message showing where an entity is used
     */
    fun buildRemovalConfirmationMessage(
        entityName: String,
        quickBarNames: List<String>,
        triggerKeyNames: List<String>
    ): String {
        val messageBuilder = StringBuilder("Are you sure you want to remove \"$entityName\" from your saved entities?")

        // Add QuickBars information if any
        if (quickBarNames.isNotEmpty()) {
            messageBuilder.append("\n\nThis entity is used in the following QuickBars:")
            quickBarNames.forEachIndexed { index, name ->
                if (index < 5) {
                    messageBuilder.append("\n• $name")
                } else if (index == 5) {
                    messageBuilder.append("\n• ...and ${quickBarNames.size - 5} more")
                    return@forEachIndexed
                }
            }
        }

        // Add TriggerKeys information if any
        if (triggerKeyNames.isNotEmpty()) {
            messageBuilder.append("\n\nThis entity is used in the following Trigger Keys:")
            triggerKeyNames.forEachIndexed { index, name ->
                if (index < 5) {
                    messageBuilder.append("\n• $name")
                } else if (index == 5) {
                    messageBuilder.append("\n• ...and ${triggerKeyNames.size - 5} more")
                    return@forEachIndexed
                }
            }
        }

        // Add the consequences
        messageBuilder.append("\n\nThis entity will be removed from all QuickBars and Trigger Keys that use it.")

        return messageBuilder.toString()
    }
}