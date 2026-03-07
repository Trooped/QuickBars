package dev.trooped.tvquickbars.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.trooped.tvquickbars.R
import com.google.android.material.checkbox.MaterialCheckBox
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.ui.extensions.setThemedResource

/**
 * QuickBarSavedEntitiesSelectorAdapter
 *
 * Adapter for displaying a list of selectable entities in a RecyclerView.
 * This adapter allows users to select entities to add to a QuickBar.
 *
 * @property entities The list of EntityItem objects to display.
 * @property selectedEntityIds A mutable set of selected entity IDs.
 * @property onEntitySelected Callback function to handle entity selection changes.
 */
class QuickBarSavedEntitiesSelectorAdapter(
    private val entities: List<EntityItem>,
    private val selectedEntityIds: MutableSet<String>,
    private val onEntitySelected: (entityId: String, isSelected: Boolean) -> Unit
) : RecyclerView.Adapter<QuickBarSavedEntitiesSelectorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val entityName: TextView = view.findViewById(R.id.entity_name)
        val checkbox: MaterialCheckBox = view.findViewById(R.id.entity_checkbox)
        val iconView: ImageView = view.findViewById(R.id.entity_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_entity_selectable, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entity = entities[position]

        // Set entity name
        holder.entityName.text = entity.customName.ifEmpty { entity.friendlyName }

        val iconRes = if (entity.isActionable) {
            EntityIconMapper.getDisplayIconForEntity(entity)
        } else {
            EntityIconMapper.getFinalIconForEntity(entity)
        } ?: R.drawable.ic_default // consistent fallback
        holder.iconView.setThemedResource(iconRes)

        // Force left alignment for text
        holder.entityName.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        holder.entityName.textDirection = View.TEXT_DIRECTION_LTR

        // Set checkbox state based on selection
        holder.checkbox.isChecked = selectedEntityIds.contains(entity.id)

        holder.itemView.setOnClickListener {
            val isCurrentlyChecked = holder.checkbox.isChecked
            val newCheckedState = !isCurrentlyChecked

            // Update visual checkbox state
            holder.checkbox.isChecked = newCheckedState

            // Call callback to update selected IDs
            onEntitySelected(entity.id, newCheckedState)
        }
    }

    override fun getItemCount() = entities.size
}
