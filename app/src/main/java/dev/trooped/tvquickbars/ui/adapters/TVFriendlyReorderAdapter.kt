package dev.trooped.tvquickbars.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.trooped.tvquickbars.R
import com.google.android.material.card.MaterialCardView
import dev.trooped.tvquickbars.ui.extensions.setThemedResource
import java.util.Locale

/**
 * TVFriendlyReorderAdapter
 *
 * Adapter for displaying a list of entities in a TV-friendly reorderable format.
 * This adapter allows users to reorder entities using a TV remote.
 *
 * @property entityIds The list of entity IDs to display.
 * @property displayNames A map of entity IDs to their display names.
 * @property entityIcons A map of entity IDs to their icon resources.
 * @property moveMode Whether the adapter is in move mode for reordering.
 * @property onItemSelected Callback function to handle item selection changes.
 */
class TVFriendlyReorderAdapter(
    private val entityIds: MutableList<String>,
    private val displayNames: Map<String, String>,
    private val entityIcons: Map<String, Int>, // Add this parameter
    private var moveMode: Boolean = false,
    private val onItemSelected: (Int) -> Unit
) : RecyclerView.Adapter<TVFriendlyReorderAdapter.ViewHolder>() {

    private var selectedPosition = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.entity_name)
        val idTextView: TextView = view.findViewById(R.id.entity_id)
        val iconView: ImageView = view.findViewById(R.id.entity_icon)
        val container: ConstraintLayout = view.findViewById(R.id.item_container)
        val cardView: MaterialCardView = view as MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tv_reorderable_entity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entityId = entityIds[position]

        val displayName = displayNames[entityId]?.takeIf { it.isNotEmpty() }
            ?: run {
                entityId.split(".").last().replace("_", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }

        holder.iconView.setThemedResource(entityIcons[entityId] ?: R.drawable.ic_default)

        holder.nameTextView.text = displayName
        // Force left alignment for name
        holder.nameTextView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        holder.nameTextView.textDirection = View.TEXT_DIRECTION_LTR

        holder.idTextView.text = entityId
        // Force left alignment for ID
        holder.idTextView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        holder.idTextView.textDirection = View.TEXT_DIRECTION_LTR


        // Make the whole item focusable for TV remote
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true

        // Handle selection - store the position and call the callback
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                selectedPosition = holder.adapterPosition
                onItemSelected(selectedPosition)
            }
        }

        // Apply appropriate styling based on selection and move mode
        updateItemAppearance(holder, position)
    }

    private fun updateItemAppearance(holder: ViewHolder, position: Int) {
        val isSelected = position == selectedPosition
        val isMovable = moveMode && isSelected
        val context = holder.itemView.context

        // Set the activated state for visual feedback
        holder.container.isActivated = isMovable

        when {
            isMovable -> {
                // Item is selected and in move mode
                holder.cardView.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_width_selected)
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_primaryContainer))
                holder.nameTextView.setTextColor(ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer))
                holder.idTextView.setTextColor(ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer))

                // Add move indicators to text
                val entityId = entityIds[position]
                val name = displayNames[entityId]?.takeIf { it.isNotEmpty() }
                    ?: run {
                        entityId.split(".").last().replace("_", " ")
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    }
                holder.nameTextView.text = "⇕ $name"
            }
            isSelected -> {
                // Item is selected but not in move mode
                holder.cardView.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_width_selected)
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surfaceContainerHigh))
                holder.nameTextView.setTextColor(ContextCompat.getColor(context, R.color.md_theme_onSurface))
                holder.idTextView.setTextColor(ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant))
            }
            else -> {
                // Item is not selected
                holder.cardView.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_width_normal)
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surfaceContainerHigh))
                holder.nameTextView.setTextColor(ContextCompat.getColor(context, R.color.md_theme_onSurface))
                holder.idTextView.setTextColor(ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant))
            }
        }

        // Ensure focus is maintained correctly
        if (isSelected && !holder.itemView.hasFocus()) {
            holder.itemView.requestFocus()
        }
    }

    override fun getItemCount() = entityIds.size

    fun setMoveMode(position: Int, enabled: Boolean) {
        if (position < 0 || position >= entityIds.size) return

        moveMode = enabled
        selectedPosition = position
        notifyDataSetChanged()
    }
}