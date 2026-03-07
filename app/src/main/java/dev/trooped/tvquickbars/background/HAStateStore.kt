package dev.trooped.tvquickbars.background

import android.util.Log
import dev.trooped.tvquickbars.data.CategoryItem
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ha.ConnectionState
import dev.trooped.tvquickbars.ui.EntityIconMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


/**
 * Manages the state of the Home Assistant connection and entities.
 *
 * This object provides a centralized store for:
 * - The current connection state to the Home Assistant server.
 * - A list of categories, each containing a list of entities.
 * - A map of entities by their ID for quick lookups.
 *
 * It uses StateFlow to allow observers to react to changes in the state.
 */
object HAStateStore {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection

    private val _categories = MutableStateFlow<List<CategoryItem>>(emptyList())

    // Convenience: current entities as a map (by id) for fast lookups in overlay building
    private val _entitiesById = MutableStateFlow<Map<String, EntityItem>>(emptyMap())
    val entitiesById: StateFlow<Map<String, EntityItem>> = _entitiesById

    fun setConnection(state: ConnectionState) { _connection.value = state }

    fun setCategories(list: List<CategoryItem>) {
        _categories.value = list
        _entitiesById.value = list.flatMap { it.entities }.associateBy { it.id }
    }

    fun updateEntity(entityId: String, newItem: EntityItem) {
        // Get previous entity for comparison
        val oldItem = _entitiesById.value[entityId]

        // Ensure we don't lose entity state during update
        val mergedItem = if (oldItem != null && oldItem.isSaved) {
            // Make sure we preserve isSaved flag during validation
            val validatedItem = ensureValidEntity(newItem, entityId)
            validatedItem.copy(isSaved = true)
        } else {
            ensureValidEntity(newItem, entityId)
        }

        // Update the store with proper validated entity
        val cur = _entitiesById.value.toMutableMap()
        cur[entityId] = mergedItem
        _entitiesById.value = cur
    }

    // Enhance ensureValidEntity method
    private fun ensureValidEntity(entity: EntityItem, entityId: String): EntityItem {
        // 1. Ensure non-null lastKnownState
        val safeLastKnownState = entity.lastKnownState ?: mutableMapOf()

        // 2. Always validate and fix icon names
        val safeOnIcon = if (entity.customIconOnName.isNullOrEmpty()) {
            EntityIconMapper.getDefaultOnIconForEntityName(entityId)
        } else {
            entity.customIconOnName
        }

        // 3. Create a safe entity with valid values (preserving isSaved)
        return entity.copy(
            customIconOnName = safeOnIcon,
            lastKnownState = safeLastKnownState,
            isSaved = entity.isSaved  // EXPLICIT preservation of isSaved flag
        )
    }
}