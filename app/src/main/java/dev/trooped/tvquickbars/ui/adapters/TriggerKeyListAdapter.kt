package dev.trooped.tvquickbars.ui.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.TriggerKey
import dev.trooped.tvquickbars.persistence.QuickBarManager
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import java.util.Locale

/**
 * TriggerKeyListAdapter
 *
 * Adapter for displaying a list of TriggerKey objects in a RecyclerView.
 * This adapter binds TriggerKey data to the views in each item of the list.
 *
 * @property keys The list of TriggerKey objects to display.
 * @property onItemClick Callback function to handle item clicks.
 */
class TriggerKeyListAdapter(
    private val keys: List<TriggerKey>,
    private val onItemClick: (TriggerKey) -> Unit
) : RecyclerView.Adapter<TriggerKeyListAdapter.ViewHolder>() {

    private lateinit var context: Context
    private lateinit var quickBarManager: QuickBarManager
    private lateinit var savedEntitiesManager: SavedEntitiesManager

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val friendlyNameTextView: TextView = view.findViewById(R.id.trigger_key_friendly_name)
        val technicalNameTextView: TextView = view.findViewById(R.id.trigger_key_technical_name)
        val assignmentsTextView: TextView = view.findViewById(R.id.trigger_key_assignments)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context

        // Then initialize the managers that need context
        quickBarManager = QuickBarManager(context)
        savedEntitiesManager = SavedEntitiesManager(context)

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trigger_key, parent, false)
        return ViewHolder(view)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val key = keys[position]

        // Format friendly name, defaulting to formatted technical name if not set
        val friendlyName = key.friendlyName ?: formatKeyName(key.keyName)
        holder.friendlyNameTextView.text = friendlyName

        // Show technical name
        holder.technicalNameTextView.text = key.keyName

        // Show assignments summary
        holder.assignmentsTextView.text = getFormattedAssignments(key)

        // Apply disabled state styling
        if (!key.enabled) {
            // Add "DISABLED" indicator to the friendly name
            holder.friendlyNameTextView.text = "$friendlyName (DISABLED)"

            // Reduce opacity for the entire item to indicate it's disabled
            holder.itemView.alpha = 0.5f
        } else {
            // Reset styling for enabled items
            holder.itemView.alpha = 1.0f
            holder.itemView.background = null
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick(key)
        }
    }

    override fun getItemCount() = keys.size

    private fun formatKeyName(keyName: String): String {
        return keyName.replace("KEYCODE_", "")
            .split("_")
            .joinToString(" ") { it ->
                it.lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }

    private fun getFormattedAssignments(key: TriggerKey): String {
        val assignments = StringBuilder()

        // Get all QuickBars and entities for lookup
        val quickBars = quickBarManager.loadQuickBars()
        val savedEntities = savedEntitiesManager.loadEntities()

        // Map of IDs to names
        val quickBarMap = quickBars.associateBy { it.id }
        val entityMap = savedEntities.associateBy { it.id }

        // Single press
        if (key.singlePressAction != null) {
            assignments.append("Single press: ")
            when (key.singlePressActionType) {
                "app_launch" -> {
                    val appName = key.appLabel
                    assignments.append("$appName (App)")
                }
                "entity" -> {
                    val entityId = key.singlePressAction
                    val entity = entityMap[entityId]

                    // Improved name resolution with fallbacks
                    val entityName = when {
                        // Use custom name if it exists and isn't blank
                        entity?.customName?.isNotBlank() == true -> entity.customName

                        // Use friendly name if it exists and isn't blank
                        entity?.friendlyName?.isNotBlank() == true -> entity.friendlyName

                        // Fall back to entity ID, or "Unknown" if even that is null
                        else -> entityId ?: "Unknown"
                    }

                    assignments.append("$entityName (Entity)")
                }
                "camera_pip" -> {
                    val entityId = key.singlePressAction
                    val entity = entityMap[entityId]

                    // Use same improved name resolution
                    val cameraName = when {
                        entity?.customName?.isNotBlank() == true -> entity.customName
                        entity?.friendlyName?.isNotBlank() == true -> entity.friendlyName
                        else -> entityId ?: "Unknown"
                    }

                    assignments.append("$cameraName (Camera)")
                }
                else -> {
                    // Default case - assume it's a QuickBar
                    val barName = quickBarMap[key.singlePressAction]?.name ?: key.singlePressAction
                    assignments.append("$barName (QuickBar)")
                }
            }
            assignments.append("\n")
        }

        // Double press
        if (key.doublePressAction != null) {
            assignments.append("Double press: ")
            when (key.doublePressActionType) {
                "entity" -> {
                    val entityId = key.doublePressAction
                    val entity = entityMap[entityId]

                    // Improved name resolution with fallbacks
                    val entityName = when {
                        // Use custom name if it exists and isn't blank
                        entity?.customName?.isNotBlank() == true -> entity.customName

                        // Use friendly name if it exists and isn't blank
                        entity?.friendlyName?.isNotBlank() == true -> entity.friendlyName

                        // Fall back to entity ID, or "Unknown" if even that is null
                        else -> entityId ?: "Unknown"
                    }

                    assignments.append("$entityName (Entity)")
                }
                "camera_pip" -> {
                    val cameraName = entityMap[key.doublePressAction]?.customName
                        ?: entityMap[key.doublePressAction]?.friendlyName
                        ?: key.doublePressAction
                    assignments.append("$cameraName (Camera)")
                }
                else -> {
                    // Default case - assume it's a QuickBar
                    val barName = quickBarMap[key.doublePressAction]?.name ?: key.doublePressAction
                    assignments.append("$barName (QuickBar)")
                }
            }
            assignments.append("\n")
        }

        // Long press
        if (key.longPressAction != null) {
            assignments.append("Long press: ")
            when (key.longPressActionType) {
                "entity" -> {
                    val entityId = key.longPressAction
                    val entity = entityMap[entityId]

                    // Improved name resolution with fallbacks
                    val entityName = when {
                        // Use custom name if it exists and isn't blank
                        entity?.customName?.isNotBlank() == true -> entity.customName

                        // Use friendly name if it exists and isn't blank
                        entity?.friendlyName?.isNotBlank() == true -> entity.friendlyName

                        // Fall back to entity ID, or "Unknown" if even that is null
                        else -> entityId ?: "Unknown"
                    }

                    assignments.append("$entityName (Entity)")
                }
                "camera_pip" -> {
                    val cameraName = entityMap[key.longPressAction]?.customName
                        ?: entityMap[key.longPressAction]?.friendlyName
                        ?: key.longPressAction
                    assignments.append("$cameraName (Camera)")
                }
                else -> {
                    // Default case - assume it's a QuickBar
                    val barName = quickBarMap[key.longPressAction]?.name ?: key.longPressAction
                    assignments.append("$barName (QuickBar)")
                }
            }
        }

        return assignments.toString().trim()
    }
}