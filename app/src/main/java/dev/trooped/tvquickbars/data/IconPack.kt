package dev.trooped.tvquickbars.data

import androidx.annotation.DrawableRes

/**
 * IconPack Data Class
 * Represents an icon/ dual on/off icon pack for entities in the app.
 * @property name A user-friendly name for the icon pack, e.g., "Smart Plug".
 * @property iconOnRes The resource ID of the icon when the entity is in the "on" state.
 * @property iconOffRes The resource ID of the icon when the entity is in the "off" state (nullable for solo icons).
 * @property tags A list of tags for making icons searchable.
 */
data class IconPack(

    val name: String, // A user-friendly name, e.g., "Smart Plug"
    @DrawableRes val iconOnRes: Int,
    @DrawableRes val iconOffRes: Int? = null, // Nullable for solo icons (icons with no 'off' version)
    val tags: List<String> = emptyList() // For making icons searchable
){
    /**
     * Get the resource name for the ON icon
     */
    fun IconPack.getOnIconName(context: android.content.Context): String {
        return context.resources.getResourceEntryName(iconOnRes)
    }

    /**
     * Get the resource name for the OFF icon (if available)
     */
    fun IconPack.getOffIconName(context: android.content.Context): String? {
        return iconOffRes?.let { context.resources.getResourceEntryName(it) }
    }

}