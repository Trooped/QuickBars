package dev.trooped.tvquickbars.ui.adapters

import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.ui.extensions.setThemedResource

/**
 * ManageSavedEntitiesAdapter
 *
 * Adapter for displaying a list of saved entities in a RecyclerView.
 * This adapter handles the display of entity names, IDs, and icons,
 * and allows for editing entities through a button click.
 *
 * @property entities The list of EntityItem objects to display.
 * @property onItemClick Callback function to handle item clicks.
 */
class ManageSavedEntitiesAdapter(
    private val entities: MutableList<EntityItem>,
    private val onItemClick: (EntityItem, Int) -> Unit
) : RecyclerView.Adapter<ManageSavedEntitiesAdapter.EntityViewHolder>() {

    class EntityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val entityName: TextView = view.findViewById(R.id.entity_name)
        val entityId: TextView = view.findViewById(R.id.entity_id)
        val editButton: View = view.findViewById(R.id.edit_button)
        val entityIcon: ImageView = view.findViewById(R.id.entity_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manage_entity, parent, false)
        return EntityViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBindViewHolder(holder: EntityViewHolder, position: Int) {
        val entity = entities[position]

        val displayName = if (entity.customName.isNotEmpty()) entity.customName else entity.friendlyName
        holder.entityName.text = displayName
        holder.entityId.text = entity.id

        holder.entityName.textAlignment= View.TEXT_ALIGNMENT_VIEW_START
        holder.entityName.textDirection = View.TEXT_DIRECTION_LTR

        val cardView = holder.itemView as com.google.android.material.card.MaterialCardView

        // Make the card focusable for TV navigation
        cardView.isFocusable = true
        cardView.isClickable = true
        cardView.isFocusableInTouchMode = true

        cardView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // When focused - add a 3dp primary color border
                cardView.strokeColor = holder.itemView.context.getColor(R.color.md_theme_onBackground)
                cardView.strokeWidth = 3
                // Optional: increase elevation when focused
                cardView.cardElevation = 8f
            } else {
                // When not focused - revert to default state
                if (!entity.isAvailable) {
                    // Keep error styling for missing entities
                    cardView.strokeColor = holder.itemView.context.getColor(R.color.md_theme_error)
                    cardView.strokeWidth = 1
                } else {
                    // Normal entities have no border when unfocused
                    cardView.strokeWidth = 0
                }
                // Reset elevation when unfocused
                cardView.cardElevation = 2f
            }
        }

        // Apply visual styling for missing entities
        if (!entity.isAvailable) {
            // Add strikethrough to entity name
            holder.entityName.paintFlags = holder.entityName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

            // Add "(MISSING ENTITY)" to the display name
            holder.entityName.text = "${entity.customName} (MISSING ENTITY)"

            // Set text color to error color
            holder.entityName.setTextColor(holder.itemView.context.getColor(R.color.md_theme_error))

            // Reduce alpha for the entire item
            holder.itemView.alpha = 0.7f

            // Apply error color to the card background (maintains rounded corners)
            cardView.setCardBackgroundColor(holder.itemView.context.getColor(R.color.md_theme_errorContainer))

            // Add a stroke around the card
            cardView.strokeColor = holder.itemView.context.getColor(R.color.md_theme_error)
            cardView.strokeWidth = 1 // 1dp stroke
        } else {
            // Normal styling for available entities
            holder.entityName.paintFlags = holder.entityName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.entityName.setTextColor(holder.itemView.context.getColor(R.color.md_theme_onSurface))
            holder.itemView.alpha = 1.0f

            // Reset card background to default
            cardView.setCardBackgroundColor(holder.itemView.context.getColor(R.color.md_theme_surfaceContainerHigh))
            cardView.strokeWidth = 0 // No stroke
        }

        // Explicitly clear any previous icon
        holder.entityIcon.setImageDrawable(null)

        // Get icon using string name with a unique tag to prevent caching issues
        val iconRes = EntityIconMapper.getFinalIconForEntity(entity)

        // Load with a unique tag based on entity ID and icon name to prevent caching
        val iconTag = "${entity.id}_${entity.customIconOnName}_${System.currentTimeMillis()}"
        holder.entityIcon.tag = iconTag

        // Set the icon with themed resource
        holder.entityIcon.setThemedResource(iconRes)

        holder.editButton.setOnClickListener {
            onItemClick(entity, position)
        }

        // Make the whole item clickable too
        holder.itemView.setOnClickListener {
            onItemClick(entity, position)
        }
    }

    fun updateEntities(newEntities: List<EntityItem>) {
        val changedPositions = mutableListOf<Int>()

        // Check for changes by comparing with current entities
        newEntities.forEachIndexed { index, newEntity ->
            if (index < entities.size) {
                val oldEntity = entities[index]
                if (oldEntity.id == newEntity.id &&
                    (oldEntity.customIconOnName != newEntity.customIconOnName ||
                            oldEntity.customIconOffName != newEntity.customIconOffName)) {
                    changedPositions.add(index)
                }
            }
        }

        // Update data
        entities.clear()
        entities.addAll(newEntities)

        // Force redraw of specific items that changed
        if (changedPositions.isNotEmpty()) {
            changedPositions.forEach { position ->
                notifyItemChanged(position)
            }
        } else {
            // Fall back to notifyDataSetChanged if no specific positions found
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = entities.size
}