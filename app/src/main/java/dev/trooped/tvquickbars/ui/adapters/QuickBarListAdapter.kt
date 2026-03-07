package dev.trooped.tvquickbars.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.QuickBar
import dev.trooped.tvquickbars.persistence.QuickBarManager

/**
 * QuickBarListAdapter
 *
 * Adapter for displaying a list of QuickBar objects in a RecyclerView.
 * This adapter binds QuickBar data to the views in each item of the list.
 *
 * @property quickBars The list of QuickBar objects to display.
 * @property onItemClicked Callback function to handle item clicks.
 */
class QuickBarListAdapter(
    private val quickBars: List<QuickBar>,
    // This is a lambda function that will be called when a row is clicked
    private val onItemClicked: (QuickBar) -> Unit
) : RecyclerView.Adapter<QuickBarListAdapter.QuickBarViewHolder>() {

    /**
     * A ViewHolder describes an item view and metadata about its place within the RecyclerView.
     */
    class QuickBarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Holds the TextView that will display the name of the QuickBar.
        val nameTextView: TextView = view.findViewById(R.id.quickbar_name)
        val entitiesCountTextView: TextView = view.findViewById(R.id.quickbar_entities_count)
    }

    /**
     * Called when RecyclerView needs a new ViewHolder of the given type to represent an item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickBarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fragment_quick_bars_list, parent, false)
        return QuickBarViewHolder(view)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method updates the contents of the ViewHolder's views to reflect the item at the given position.
     */
    override fun onBindViewHolder(holder: QuickBarViewHolder, position: Int) {
        val quickBar = quickBars[position]

        val quickBarManager = QuickBarManager(holder.itemView.context)
        val validEntityCount = quickBarManager.getValidEntityCount(quickBar, holder.itemView.context)

        holder.nameTextView.text = quickBar.name

        holder.nameTextView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        holder.nameTextView.textDirection = View.TEXT_DIRECTION_LTR

        val entityText = if (validEntityCount == 1) "1 entity" else "$validEntityCount entities"
        holder.entitiesCountTextView.text = entityText


        holder.entitiesCountTextView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        holder.entitiesCountTextView.textDirection = View.TEXT_DIRECTION_LTR

        if (!quickBar.isEnabled) {
            // Add "DISABLED" indicator to the friendly name
            holder.nameTextView.text = "${quickBar.name} (DISABLED)"

            // Reduce opacity for the entire item to indicate it's disabled
            holder.itemView.alpha = 0.5f
        } else {
            // Reset styling for enabled items
            holder.itemView.alpha = 1.0f
            holder.itemView.background = null
        }

        holder.itemView.setOnClickListener {
            onItemClicked(quickBar)
        }
    }

    override fun getItemCount(): Int = quickBars.size
}
