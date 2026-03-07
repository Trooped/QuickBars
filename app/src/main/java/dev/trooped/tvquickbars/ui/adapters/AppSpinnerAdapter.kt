package dev.trooped.tvquickbars.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.AppInfo

/**
 * AppSpinnerAdapter
 *
 * Custom ArrayAdapter for displaying a list of AppInfo objects in a Spinner.
 * This adapter inflates custom views for both the main view and the dropdown view.
 *
 * @property context The context in which the adapter is created.
 * @property apps The list of AppInfo objects to display.
 */
class AppSpinnerAdapter(
    context: Context,
    private val apps: List<AppInfo>
) : ArrayAdapter<AppInfo>(context, R.layout.item_app_spinner, apps) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_app_spinner, parent, false)
        bindView(position, view)
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_app_spinner_dropdown, parent, false)
        bindView(position, view)
        return view
    }

    private fun bindView(position: Int, view: View) {
        val app = apps[position]
        view.findViewById<TextView>(R.id.app_name).text = app.label
        view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(app.icon)
    }
}