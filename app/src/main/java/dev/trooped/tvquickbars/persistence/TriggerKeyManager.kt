package dev.trooped.tvquickbars.persistence

import android.content.Context
import android.util.Log
import android.util.SparseArray
import dev.trooped.tvquickbars.data.TriggerKey
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

/**
 * TriggerKeyManager Class
 * This class handles saving and loading of trigger keys.
 * It uses Gson for serialization and deserialization.
 * @property prefs The SharedPreferences instance for saving data.
 * @property keyCache A cache to store loaded keys for quick access.
 */
class TriggerKeyManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("HA_TriggerKeys", Context.MODE_PRIVATE)
    private val keyCache = SparseArray<TriggerKey>()

    init {
        // Load all keys into cache on initialization
        loadTriggerKeys().forEach { key ->
            keyCache[key.keyCode] = key
        }
    }

    /**
     * Save a list of trigger keys to SharedPreferences.
     * @param keys The list of trigger keys to save.
     */
    fun saveTriggerKeys(keys: List<TriggerKey>): Boolean {
        val jsonArray = JSONArray()
        for (key in keys) {
            val keyJson = JSONObject().apply {
                put("keyCode", key.keyCode)
                put("keyName", key.keyName)
                put("friendlyName", key.friendlyName ?: JSONObject.NULL)

                // Store action references
                put("singlePressAction", key.singlePressAction ?: JSONObject.NULL)
                put("doublePressAction", key.doublePressAction ?: JSONObject.NULL)
                put("longPressAction", key.longPressAction ?: JSONObject.NULL)

                put("singlePressActionType", key.singlePressActionType)
                put("doublePressActionType", key.doublePressActionType)
                put("longPressActionType", key.longPressActionType)

                put("originalAction", key.originalAction ?: JSONObject.NULL)
                put("appLabel", key.appLabel ?: JSONObject.NULL)

                put("enabled", key.enabled)

            }
            jsonArray.put(keyJson)
        }

        prefs.edit { putString("trigger_keys", jsonArray.toString()) }

        // Clear and rebuild the cache
        keyCache.clear()
        keys.forEach { key ->
            keyCache[key.keyCode] = key
        }
        return true
    }

    /**
     * Clear the cache of trigger keys.
     */
    fun clearCache() {
        keyCache.clear()
        loadTriggerKeys().forEach { key ->
            keyCache[key.keyCode] = key
        }
    }

    /**
     * Load a list of trigger keys from SharedPreferences.
     * @return A list of trigger keys.
     */
    fun loadTriggerKeys(): List<TriggerKey> {
        val keysJson = prefs.getString("trigger_keys", "[]")

        val keys = mutableListOf<TriggerKey>()

        try {
            val jsonArray = JSONArray(keysJson)
            for (i in 0 until jsonArray.length()) {
                val keyJson = jsonArray.getJSONObject(i)
                val key = TriggerKey(
                    keyCode = keyJson.getInt("keyCode"),
                    keyName = keyJson.getString("keyName"),
                    friendlyName = if (keyJson.has("friendlyName") && !keyJson.isNull("friendlyName"))
                        keyJson.getString("friendlyName") else null,

                    singlePressAction = if (keyJson.has("singlePressAction") && !keyJson.isNull("singlePressAction"))
                        keyJson.getString("singlePressAction") else null,

                    doublePressAction = if (keyJson.has("doublePressAction") && !keyJson.isNull("doublePressAction"))
                        keyJson.getString("doublePressAction") else null,

                    longPressAction = if (keyJson.has("longPressAction") && !keyJson.isNull("longPressAction"))
                        keyJson.getString("longPressAction") else null,

                    // Read the entity flags
                    singlePressActionType = if (keyJson.has("singlePressActionType") && !keyJson.isNull("singlePressActionType"))
                        keyJson.getString("singlePressActionType") else null,
                    doublePressActionType = if (keyJson.has("doublePressActionType") && !keyJson.isNull("doublePressActionType"))
                        keyJson.getString("doublePressActionType") else null,
                    longPressActionType = if (keyJson.has("longPressActionType") && !keyJson.isNull("longPressActionType"))
                        keyJson.getString("longPressActionType") else null,

                    originalAction = if (keyJson.has("originalAction") && !keyJson.isNull("originalAction"))
                        keyJson.getString("originalAction") else null,

                    appLabel = if (keyJson.has("appLabel") && !keyJson.isNull("appLabel"))
                        keyJson.getString("appLabel") else null,

                    enabled = keyJson.optBoolean("enabled", true)

                )
                keys.add(key)

            }
        } catch (e: Exception) {
            Log.e("TriggerKeyManager", "Error loading trigger keys", e)
        }

        return keys
    }

    fun setOriginalAction(keyCode: Int, originalAction: String) {
        val tk = getTriggerKey(keyCode) ?: return
        val updated = tk.copy(originalAction = originalAction)
        saveTriggerKey(updated)                // whatever you already use
    }

    fun saveTriggerKey(key: TriggerKey) {
        val all = loadTriggerKeys().toMutableList()
        val idx = all.indexOfFirst { it.keyCode == key.keyCode }
        if (idx >= 0) all[idx] = key else all += key
        saveTriggerKeys(all)          // you already have this
    }

    /**
     * Delete a trigger key by its key code
     * @param keyCode The key code to delete
     * @return true if deletion was successful
     */
    fun deleteTriggerKey(keyCode: Int): Boolean {
        // Load all keys
        val keys = loadTriggerKeys().toMutableList()

        // Check if the key exists
        val keyExists = keys.any { it.keyCode == keyCode }
        if (!keyExists) {
            Log.w("TriggerKeyManager", "Attempted to delete non-existent key: $keyCode")
            return false
        }

        // Remove the key
        keys.removeAll { it.keyCode == keyCode }

        // Save the updated list
        val success = saveTriggerKeys(keys)

        if (success) {
            // Explicitly remove from cache if save succeeded
            keyCache.remove(keyCode)

            // Double-check to ensure it's gone
            if (keyCache[keyCode] != null) {
                Log.e("TriggerKeyManager", "Failed to remove key $keyCode from cache!")
                keyCache.remove(keyCode)
            }

            // Verify key is truly gone from storage
            val reloadedKeys = loadTriggerKeys()
            val stillExists = reloadedKeys.any { it.keyCode == keyCode }
            if (stillExists) {
                Log.e("TriggerKeyManager", "Key $keyCode still exists after deletion!")
                return false
            }
        }

        return success
    }

    /**
     * Remove a reference to an entity from trigger keys.
     * @param entityId The ID of the entity to remove references to.
     * @return The number of trigger keys that were updated.
     */
    fun removeEntityReference(entityId: String): Int {
        val keys = loadTriggerKeys().toMutableList()
        val keysToUpdate = mutableListOf<TriggerKey>()

        for (key in keys) {
            var needsUpdate = false
            var updatedKey = key

            // Check and update single press action
            if (key.singlePressActionType == "entity" || key.singlePressActionType == "camera_pip" && key.singlePressAction == entityId) {
                updatedKey = updatedKey.copy(singlePressAction = null, singlePressActionType = null)
                needsUpdate = true
            }

            // Check and update double press action
            if (key.doublePressActionType == "entity" || key.doublePressActionType == "camera_pip" && key.doublePressAction == entityId) {
                updatedKey = updatedKey.copy(doublePressAction = null, doublePressActionType = null)
                needsUpdate = true
            }

            // Check and update long press action
            if (key.longPressActionType == "entity" || key.longPressActionType == "camera_pip" && key.longPressAction == entityId) {
                updatedKey = updatedKey.copy(longPressAction = null, longPressActionType = null)
                needsUpdate = true
            }

            if (needsUpdate) {
                keysToUpdate.add(updatedKey)
            }
        }

        // Update changed keys
        if (keysToUpdate.isNotEmpty()) {
            // Replace old keys with updated ones
            for (updatedKey in keysToUpdate) {
                val index = keys.indexOfFirst { it.keyCode == updatedKey.keyCode }
                if (index >= 0) {
                    keys[index] = updatedKey
                }
            }

            saveTriggerKeys(keys)

            // Update cache for updated keys
            for (updatedKey in keysToUpdate) {
                keyCache[updatedKey.keyCode] = updatedKey
            }
        }

        return keysToUpdate.size
    }

    /**
     * Debug function to print all loaded trigger keys.
     */
    fun debugPrintAllKeys() {
        val keys = loadTriggerKeys()
        Log.d("TriggerKeyManager", "=== ALL TRIGGER KEYS ===")
        for (key in keys) {
            Log.d("TriggerKeyManager", "Key ${key.keyCode} (${key.keyName}): " +
                    "Single=${key.singlePressAction}, " +
                    "Double=${key.doublePressAction}, " +
                    "Long=${key.longPressAction}")
        }
        Log.d("TriggerKeyManager", "=== END TRIGGER KEYS ===")
    }

    /**
     * Get a trigger key by its key code.
     * If the key is not in the cache, it is loaded from disk.
     * @param code The key code to search for.
     */
    fun getTriggerKey(code: Int): TriggerKey? {
        keyCache[code]?.let { return it }
        loadTriggerKeys().find { it.keyCode == code }?.also { keyCache.put(code, it) }
        return keyCache[code]
    }

    /**
     * Update a trigger key.
     * Loads the keys, updates the relevant key, and saves the updated keys.
     */
    fun updateTriggerKey(key: TriggerKey) {
        // Load existing keys
        val keys = loadTriggerKeys().toMutableList()

        // Find and replace or add new
        val existingIndex = keys.indexOfFirst { it.keyCode == key.keyCode }
        if (existingIndex >= 0) {
            keys[existingIndex] = key
        } else {
            keys.add(key)
        }

        val success = saveTriggerKeys(keys)

        // Only update cache if disk save was successful
        if (success) {
            keyCache[key.keyCode] = key
        }
    }

    /**
     * Remove a reference to a QuickBar from trigger keys.
     * @param quickBarId The ID of the QuickBar to remove references from.
     */
    fun removeQuickBarReference(quickBarId: String) {
        val keys = loadTriggerKeys().toMutableList()
        val keysToUpdate = mutableListOf<TriggerKey>()

        for (key in keys) {
            var needsUpdate = false
            var updatedKey = key

            if (key.singlePressAction == quickBarId) {
                updatedKey = updatedKey.copy(singlePressAction = null)
                needsUpdate = true
            }

            if (key.doublePressAction == quickBarId) {
                updatedKey = updatedKey.copy(doublePressAction = null)
                needsUpdate = true
            }

            if (key.longPressAction == quickBarId) {
                updatedKey = updatedKey.copy(longPressAction = null)
                needsUpdate = true
            }

            if (needsUpdate) {
                keysToUpdate.add(updatedKey)
            }
        }

        // Update changed keys
        if (keysToUpdate.isNotEmpty()) {
            // Replace old keys with updated ones
            keys.removeAll { key -> keysToUpdate.any { it.keyCode == key.keyCode } }

            // Only keep keys that still have assignments
            keys.addAll(keysToUpdate.filter { it.hasAnyAssignments() })

            saveTriggerKeys(keys)
        }
    }
}