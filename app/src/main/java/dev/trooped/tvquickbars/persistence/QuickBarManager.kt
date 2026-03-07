package dev.trooped.tvquickbars.persistence

import android.content.Context
import androidx.core.content.edit
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import dev.trooped.tvquickbars.data.QuickBar

/**
 * QuickBarManager Class
 * This class handles saving and loading of QuickBars.
 * It uses Gson for serialization and deserialization.
 * @property prefs The SharedPreferences instance for saving data.
 * @property quickBarsKey The key used to store QuickBars in SharedPreferences.
 * @property gson The Gson instance for serialization and deserialization.
 */
class QuickBarManager(context: Context) {

    private val prefs = context.getSharedPreferences("HA_QuickBars", Context.MODE_PRIVATE)
    private val quickBarsKey = "quickbar_list"
    private val gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)  // This helps with schema changes
        .create()

    /**
     * Save a list of QuickBars to SharedPreferences.
     */
    fun saveQuickBars(bars: List<QuickBar>) {
        val jsonString = gson.toJson(bars)
        prefs.edit(commit = true) { putString(quickBarsKey, jsonString) }
    }

    /**
     * Load a list of QuickBars from SharedPreferences.
     * @return A mutable list of QuickBars. if there's an exception it returns an empty list.
     */
    fun loadQuickBars(): MutableList<QuickBar> {
        val json = prefs.getString(quickBarsKey, null) ?: return mutableListOf()

        // TODO delete this after a few versions, when migration has finished.
        migrateLegacyLetterKeys(json)?.let { migrated ->
            prefs.edit(commit = true) { putString(quickBarsKey, migrated) }
            val type = object : com.google.gson.reflect.TypeToken<MutableList<QuickBar>>() {}.type
            return gson.fromJson(migrated, type) ?: mutableListOf()
        }
        
        // 1) Straight parse
        return try {
            val type = object : com.google.gson.reflect.TypeToken<MutableList<QuickBar>>() {}.type
            gson.fromJson<MutableList<QuickBar>>(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }


    /**
     * Migrates legacy QuickBar data from a format using single-letter keys to the current format.
     *
     * This function is designed to handle very old versions of the app's data where QuickBar
     * properties were stored with short, single-letter keys (e.g., "a" for "id", "b" for "name").
     * It parses the input JSON string, detects if it's in the legacy format, and if so,
     * transforms it into a new JSON string with the modern, more descriptive keys.
     *
     * The detection of legacy format is based on the presence of an "a" key and the absence
     * of an "id" key in the first object of the JSON array.
     *
     * If the input JSON is not a valid JSON array, is empty, or is not in the recognized
     * legacy format, the function returns `null`. Otherwise, it returns the migrated JSON string.
     *
     * @param json The JSON string potentially containing legacy QuickBar data.
     * @return A new JSON string with migrated data if the input was legacy, otherwise `null`.
     */
    private fun migrateLegacyLetterKeys(json: String): String? {
        // Parse and detect legacy payload: first element has "a" but no "id"
        val root = try { JsonParser.parseString(json) } catch (_: Exception) { return null }
        if (!root.isJsonArray) return null
        val arr = root.asJsonArray
        if (arr.size() == 0) return null
        val firstObj = arr[0].asJsonObject
        val isLegacy = firstObj.has("a") && !firstObj.has("id")
        if (!isLegacy) return null

        fun mapOne(src: JsonObject): JsonObject {
            val dst = JsonObject()
            // Map observed legacy letters -> canonical names (based on your model order)
            // If a key isn't present, we simply leave the field out (your model has defaults).
            if (src.has("a")) dst.add("id", src.get("a"))
            if (src.has("b")) dst.add("name", src.get("b"))
            if (src.has("c")) dst.add("backgroundColor", src.get("c"))
            if (src.has("d")) dst.add("backgroundOpacity", src.get("d"))
            if (src.has("e")) dst.add("onStateColor", src.get("e"))
            if (src.has("f")) dst.add("isEnabled", src.get("f"))
            if (src.has("g")) dst.add("showNameInOverlay", src.get("g"))
            // Old builds didn’t have showTimeOnQuickBar; let default stand.
            if (src.has("h")) dst.add("position", src.get("h"))                   // "RIGHT", etc.
            if (src.has("i")) dst.add("useGridLayout", src.get("i"))
            if (src.has("j")) dst.add("savedEntityIds", src.get("j"))
            if (src.has("k")) dst.add("animationStyle", src.get("k"))
            if (src.has("l")) dst.add("animationDuration", src.get("l"))
            if (src.has("m")) dst.add("displayMode", src.get("m"))
            if (src.has("n")) dst.add("closeAfterOneAction", src.get("n"))        // legacy boolean
            if (src.has("o")) dst.add("autoCloseDelay", src.get("o"))
            if (src.has("p")) dst.add("entityTextSize", src.get("p"))
            if (src.has("q")) dst.add("entityIconSize", src.get("q"))             // may not exist in old data
            // showEntityNames not present in your legacy JSON; default (true) will apply.
            return dst
        }

        val migratedArr = JsonArray()
        for (el in arr) {
            val obj = el.asJsonObject
            migratedArr.add(mapOne(obj))
        }
        return migratedArr.toString()
    }

    /**
     * Remove an entity from all QuickBars that reference it
     * @param entityId The entity ID to remove
     * @return The number of QuickBars that were updated
     */
    fun removeEntityFromAllQuickBars(entityId: String): Int {
        val allQuickBars = loadQuickBars().toMutableList()
        var updatedCount = 0

        // For each QuickBar, remove the entity from its savedEntityIds list
        for (quickBar in allQuickBars) {
            if (quickBar.savedEntityIds.contains(entityId)) {
                quickBar.savedEntityIds.remove(entityId)
                updatedCount++
            }
        }

        // Save the updated QuickBars if any were changed
        if (updatedCount > 0) {
            saveQuickBars(allQuickBars)
        }

        return updatedCount
    }

    /**
     * Get the valid entity count for a QuickBar (only counting entities that still exist)
     * @param quickBar The QuickBar to check
     * @param context Context needed to access SavedEntitiesManager
     * @return The count of valid entities in the QuickBar
     */
    fun getValidEntityCount(quickBar: QuickBar, context: Context): Int {
        val savedEntitiesManager = SavedEntitiesManager(context)
        val savedEntityIds = savedEntitiesManager.loadEntities().map { it.id }.toSet()

        // Count only entities that are still in the saved entities list
        return quickBar.savedEntityIds.count { savedEntityIds.contains(it) }
    }

    /**
     * Validate all QuickBars by removing references to entities that no longer exist
     * This function is no longer used at the moment, need to figure out why I used it in the first place.
     * @param context Context needed to access SavedEntitiesManager
     * @return The number of QuickBars that were updated
     */
    fun validateAllQuickBars(context: Context): Int {
        val allQuickBars = loadQuickBars().toMutableList()
        val savedEntitiesManager = SavedEntitiesManager(context)
        val savedEntityIds = savedEntitiesManager.loadEntities().map { it.id }.toSet()
        var updatedCount = 0

        // For each QuickBar, filter out entities that no longer exist
        for (quickBar in allQuickBars) {
            val originalSize = quickBar.savedEntityIds.size
            val validEntities = quickBar.savedEntityIds.filter { savedEntityIds.contains(it) }

            if (validEntities.size < originalSize) {
                quickBar.savedEntityIds.clear()
                quickBar.savedEntityIds.addAll(validEntities)
                updatedCount++
            }
        }

        // Save the updated QuickBars if any were changed
        if (updatedCount > 0) {
            saveQuickBars(allQuickBars)
        }

        return updatedCount
    }

    // TODO is this function still necessary? used in onKeyEvent in the quickbarmanager.
    fun saveCapturedKey(keyCode: Int, keyName: String) {
        prefs.edit(commit = true) {
            putInt("captured_keycode", keyCode)
                .putString("captured_keyname", keyName)
        }
    }
}