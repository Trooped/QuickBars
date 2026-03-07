package dev.trooped.tvquickbars.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.utils.ISpinnerItem

class GenericSpinnerAdapter(
    context: Context,
    private val items: List<ISpinnerItem>
) : ArrayAdapter<ISpinnerItem>(context, R.layout.item_generic_spinner, items) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // Use the layout WITHOUT the selector background for the closed view
        val view = convertView ?: inflater.inflate(R.layout.item_generic_spinner, parent, false)
        bindView(position, view)
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        // Use the layout WITH the selector background for the dropdown items
        val view = convertView ?: inflater.inflate(R.layout.item_generic_spinner_dropdown, parent, false)
        bindView(position, view)
        return view
    }

    private fun bindView(position: Int, view: View) {
        val item = items[position]
        val textView = view.findViewById<TextView>(R.id.item_text)
        val imageView = view.findViewById<ImageView>(R.id.item_icon)

        textView.text = item.displayText

        // Handle the icon
        if (item.displayIcon != null) {
            imageView.setImageDrawable(item.displayIcon)
            imageView.visibility = View.VISIBLE
        } else {
            // Hide the icon if there isn't one
            imageView.visibility = View.GONE
        }
    }
}