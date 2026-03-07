package dev.trooped.tvquickbars.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dev.trooped.tvquickbars.persistence.QuickBarManager
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.persistence.TriggerKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * BackupManager - pure SAF IO
 */
class BackupManager(private val context: Context) {

    data class BackupData(
        @SerializedName("backup_version")
        val backupVersion: Int = 1,
        @SerializedName("backup_date")
        val backupDate: String = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
        @SerializedName("app_version")
        val appVersion: String?,
        @SerializedName("entities")
        val entities: List<EntityItem>? = null,
        @SerializedName("quick_bars")
        val quickBars: List<QuickBar>? = null,
        @SerializedName("trigger_keys")
        val triggerKeys: List<TriggerKey>? = null
    )

    private val savedEntitiesManager = SavedEntitiesManager(context)
    private val gson = savedEntitiesManager.gson

    suspend fun createBackup(
        includeEntities: Boolean,
        includeQuickBars: Boolean,
        includeTriggerKeys: Boolean
    ): BackupData = withContext(Dispatchers.IO) {
        val savedEntitiesManager = SavedEntitiesManager(context)
        val quickBarManager = QuickBarManager(context)
        val triggerKeyManager = TriggerKeyManager(context)

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersion = packageInfo.versionName

        BackupData(
            appVersion = appVersion,
            entities = if (includeEntities) savedEntitiesManager.loadEntities() else null,
            quickBars = if (includeQuickBars) quickBarManager.loadQuickBars() else null,
            triggerKeys = if (includeTriggerKeys) triggerKeyManager.loadTriggerKeys() else null
        )
    }

    suspend fun saveBackupToFile(backupData: BackupData, outputUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val jsonData = gson.toJson(backupData)
                context.contentResolver.openOutputStream(outputUri)?.use { os ->
                    OutputStreamWriter(os, StandardCharsets.UTF_8).use { it.write(jsonData) }
                } ?: return@withContext false
                Log.d("BackupManager", "Backup saved to $outputUri")
                true
            } catch (e: Exception) {
                Log.e("BackupManager", "Error saving backup: ${e.message}", e)
                false
            }
        }

    fun getDefaultBackupFilename(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "haquickbars_backup_$timestamp.json"
    }

    suspend fun readBackupFromFile(inputUri: Uri): BackupData? = withContext(Dispatchers.IO) {
        try {
            val jsonData = context.contentResolver.openInputStream(inputUri)?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            } ?: return@withContext null

            // Normalize legacy shapes in the JSON so current adapters can parse safely
            val normalizedJson = normalizeBackupJson(jsonData)

            gson.fromJson(normalizedJson, BackupData::class.java)
        } catch (e: Exception) {
            Log.e("BackupManager", "Error reading backup: ${e.message}", e)
            null
        }
    }


    private fun normalizeBackupJson(raw: String): String {
        return try {
            val root = com.google.gson.JsonParser.parseString(raw).asJsonObject

            // Only 'entities' contain EntityAction maps (pressActions). QuickBars/TriggerKeys use IDs/strings.
            if (root.has("entities") && root.get("entities").isJsonArray) {
                val entities = root.getAsJsonArray("entities")
                for (entityEl in entities) {
                    if (!entityEl.isJsonObject) continue
                    val entityObj = entityEl.asJsonObject

                    // Fix pressActions: map<PressType, EntityAction>
                    if (entityObj.has("pressActions") && entityObj.get("pressActions").isJsonObject) {
                        val actionsObj = entityObj.getAsJsonObject("pressActions")
                        val keysToFix = mutableListOf<String>()

                        // First pass: find keys whose value needs normalization
                        for ((k, v) in actionsObj.entrySet()) {
                            if (needsEntityActionNormalization(v)) {
                                keysToFix += k
                            } else if (v.isJsonObject) {
                                // If "type" exists but is a string, normalize that field too
                                val obj = v.asJsonObject
                                if (obj.has("type") && obj.get("type").isJsonPrimitive) {
                                    keysToFix += k
                                }
                            }
                        }

                        // Second pass: rewrite values
                        for (k in keysToFix) {
                            val current = actionsObj.get(k)
                            val normalized = normalizeEntityAction(current)
                            if (normalized != null) actionsObj.add(k, normalized)
                        }
                    }
                }
            }

            root.toString()
        } catch (t: Throwable) {
            // If anything goes wrong, return the raw JSON to avoid hiding real errors
            raw
        }
    }

    private fun needsEntityActionNormalization(el: com.google.gson.JsonElement): Boolean {
        // Legacy: entire action is a primitive string, e.g., "DEFAULT" or "CAMERA_PIP"
        if (el.isJsonPrimitive) return true

        // If it's an object and has "type" but that type is a primitive string, we should wrap it
        if (el.isJsonObject) {
            val obj = el.asJsonObject
            if (obj.has("type") && obj.get("type").isJsonPrimitive) return true
        }
        return false
    }

    /**
     * Normalize various legacy forms of EntityAction into the current expected shapes:
     * - "DEFAULT"                      -> { "type": { "name": "DEFAULT" } }
     * - "CAMERA_PIP" (or other enum)   -> { "type": { "name": "CAMERA_PIP" } }
     * - { "type": "CAMERA_PIP" }       -> { "type": { "name": "CAMERA_PIP" } }
     * - Control/Service shapes are returned as-is:
     *      { "targetId": "light.k" }   -> unchanged
     *      { "domain":"light","service":"toggle" } -> unchanged
     */
    private fun normalizeEntityAction(el: com.google.gson.JsonElement): com.google.gson.JsonElement? {
        return try {
            // Whole action is a primitive string
            if (el.isJsonPrimitive) {
                val name = el.asString.orEmpty().trim().uppercase()
                // Map DEFAULT-ish tokens to empty object => adapter -> EntityAction.Default
                if (name.isEmpty() || name == "DEFAULT" || name == "NONE" || name == "NO_ACTION") {
                    return com.google.gson.JsonObject()
                }
                // Try to treat as BuiltIn if it matches our Special enum; otherwise fall back to Default
                return try {
                    EntityAction.Special.valueOf(name)
                    com.google.gson.JsonObject().apply {
                        add("type", com.google.gson.JsonObject().apply { addProperty("name", name) })
                    }
                } catch (_: IllegalArgumentException) {
                    com.google.gson.JsonObject() // unknown token -> Default
                }
            }

            // Object cases
            if (el.isJsonObject) {
                val obj = el.asJsonObject

                // If it already looks like ControlEntity or ServiceCall, leave it
                val isControl = obj.has("targetId")
                val isService = obj.has("domain") && obj.has("service")
                if (isControl || isService) return obj

                // If it has a primitive "type", wrap it into { "name": ... }
                if (obj.has("type") && obj.get("type").isJsonPrimitive) {
                    val typeName = obj.getAsJsonPrimitive("type").asString.orEmpty().trim().uppercase()
                    // If invalid BuiltIn name, convert to Default by clearing the "type"
                    val valid = try {
                        EntityAction.Special.valueOf(typeName); true
                    } catch (_: IllegalArgumentException) { false }
                    return if (valid) {
                        obj.apply {
                            add("type", com.google.gson.JsonObject().apply { addProperty("name", typeName) })
                        }
                    } else {
                        com.google.gson.JsonObject() // -> Default
                    }
                }
                return obj
            }

            // Unexpected shape – return null so caller can skip replacing
            null
        } catch (_: Throwable) {
            null
        }
    }

    fun validateBackup(backupData: BackupData): Boolean {
        Log.d("BackupManager", "Validating backup: version=${backupData.backupVersion}")

        if (backupData.backupVersion <= 0) {
            Log.e("BackupManager", "Validation failed: Invalid version ${backupData.backupVersion}")
            return false
        }

        if (backupData.entities == null && backupData.quickBars == null && backupData.triggerKeys == null) {
            Log.e("BackupManager", "Validation failed: No content found")
            return false
        }

        Log.d("BackupManager", "Backup validation successful")
        return true
    }

    suspend fun restoreFromBackup(
        backupData: BackupData,
        restoreEntities: Boolean,
        restoreQuickBars: Boolean,
        restoreTriggerKeys: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val savedEntitiesManager = SavedEntitiesManager(context)
            val quickBarManager = QuickBarManager(context)
            val triggerKeyManager = TriggerKeyManager(context)

            if (restoreEntities && backupData.entities != null) {
                val normalized = backupData.entities.map { e ->
                    e.apply {
                        isSaved = true
                        if (lastKnownState == null) lastKnownState = mutableMapOf()
                        if (pressActions == null)   pressActions   = mutableMapOf()
                        if (pressTargets == null)   pressTargets   = mutableMapOf(
                            PressType.SINGLE to null,
                            PressType.DOUBLE to null,
                            PressType.LONG   to null
                        )
                    }
                }
                savedEntitiesManager.saveEntities(normalized)
            }
            if (restoreQuickBars && backupData.quickBars != null) {
                quickBarManager.saveQuickBars(backupData.quickBars)
            }
            if (restoreTriggerKeys && backupData.triggerKeys != null) {
                triggerKeyManager.saveTriggerKeys(backupData.triggerKeys)
            }
            Log.d("BackupManager", "Restore OK")
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Error restoring backup: ${e.message}", e)
            false
        }
    }

    suspend fun validateReferences(
        restoreEntities: Boolean,
        restoreQuickBars: Boolean,
        restoreTriggerKeys: Boolean
    ): Int = withContext(Dispatchers.IO) {
        var fixed = 0

        val savedEntitiesManager = SavedEntitiesManager(context)
        val quickBarManager = QuickBarManager(context)
        val triggerKeyManager = TriggerKeyManager(context)

        val validEntityIds = savedEntitiesManager.loadEntities().map { it.id }.toSet()

        if (restoreQuickBars && !restoreEntities) {
            val qbs = quickBarManager.loadQuickBars()
            var changed = false
            for (q in qbs) {
                val before = q.savedEntityIds.size
                q.savedEntityIds.retainAll { it in validEntityIds }
                if (q.savedEntityIds.size != before) {
                    changed = true
                    fixed += (before - q.savedEntityIds.size)
                }
            }
            if (changed) quickBarManager.saveQuickBars(qbs)
        }

        if (restoreTriggerKeys) {
            val keys = triggerKeyManager.loadTriggerKeys().toMutableList()
            var changed = false
            val validQuickBarIds = quickBarManager.loadQuickBars().map { it.id }.toSet()

            for (i in keys.indices) {
                var cur = keys[i]
                var upd = cur
                var updated = false

                if (!restoreEntities) {
                    if (cur.singlePressActionType == "entity" && cur.singlePressAction?.let { it !in validEntityIds } == true) {
                        upd = upd.copy(singlePressAction = null, singlePressActionType = null); updated = true; fixed++
                    }
                    if (cur.doublePressActionType == "entity" && cur.doublePressAction?.let { it !in validEntityIds } == true) {
                        upd = upd.copy(doublePressAction = null, doublePressActionType = null); updated = true; fixed++
                    }
                    if (cur.longPressActionType == "entity" && cur.longPressAction?.let { it !in validEntityIds } == true) {
                        upd = upd.copy(longPressAction = null, longPressActionType = null); updated = true; fixed++
                    }
                }

                if (!restoreQuickBars) {
                    if (cur.singlePressActionType == "quickbar" && cur.singlePressAction?.let { it !in validQuickBarIds } == true) {
                        upd = upd.copy(singlePressAction = null, singlePressActionType = null); updated = true; fixed++
                    }
                    if (cur.doublePressActionType == "quickbar" && cur.doublePressAction?.let { it !in validQuickBarIds } == true) {
                        upd = upd.copy(doublePressAction = null, doublePressActionType = null); updated = true; fixed++
                    }
                    if (cur.longPressActionType == "quickbar" && cur.longPressAction?.let { it !in validQuickBarIds } == true) {
                        upd = upd.copy(longPressAction = null, longPressActionType = null); updated = true; fixed++
                    }
                }

                if (updated) {
                    keys[i] = upd
                    changed = true
                }
            }
            if (changed) triggerKeyManager.saveTriggerKeys(keys)
        }

        return@withContext fixed
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveBackupToDownloads(backupData: BackupData, filename: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                }
                val uri = resolver.insert(collection, values) ?: return@withContext false
                val json = gson.toJson(backupData)
                resolver.openOutputStream(uri)?.use { os ->
                    OutputStreamWriter(os, StandardCharsets.UTF_8).use { it.write(json) }
                } ?: return@withContext false
                true
            } catch (e: Exception) {
                Log.e("BackupManager", "saveBackupToDownloads failed: ${e.message}", e)
                false
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun listBackupsInDownloads(): List<Pair<String, Uri>> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val out = LinkedHashMap<String, Uri>()

        // (A) Robust MediaStore.Files query scoped to "Download" or "Downloads" folders.
        runCatching {
            val collection = MediaStore.Files.getContentUri("external")

            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED
            )

            // Accept any .json in Downloads or Download (different vendors use different labels)
            val selection = """
            (${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?) AND
            (
              ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR
              ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR
              ${MediaStore.MediaColumns.RELATIVE_PATH} IS NULL
            )
        """.trimIndent()

            val args = arrayOf("%.json", "%Download/%", "%Downloads/%")

            resolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: continue
                    val uri = ContentUris.withAppendedId(collection, id)
                    out.putIfAbsent(name, uri)
                }
            }
        }.onFailure { Log.w("BackupManager", "MediaStore Files query failed: ${it.message}") }

        // (B) If still empty, try the older Downloads collections on various volumes.
        if (out.isEmpty()) {
            val volumes = listOf(
                MediaStore.VOLUME_EXTERNAL_PRIMARY,
                MediaStore.VOLUME_EXTERNAL,
                "external"
            )
            volumes.distinct().forEach { vol ->
                runCatching {
                    val collection = if (vol == "external") {
                        MediaStore.Files.getContentUri("external")
                    } else {
                        MediaStore.Downloads.getContentUri(vol)
                    }
                    val projection = arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME
                    )
                    val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                    val args = arrayOf("%.json")

                    resolver.query(collection, projection, sel, args, null)?.use { c ->
                        val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        while (c.moveToNext()) {
                            val id = c.getLong(idCol)
                            val name = c.getString(nameCol) ?: continue
                            val uri = ContentUris.withAppendedId(collection, id)
                            out.putIfAbsent(name, uri)
                        }
                    }
                }.onFailure { Log.w("BackupManager", "Downloads($vol) query failed: ${it.message}") }
            }
        }

        // (C) Very last resort: permissive “any .json anywhere” (some TVs are weird)
        if (out.isEmpty()) {
            runCatching {
                val collection = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME
                )
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("%.json"),
                    null
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: continue
                        val uri = ContentUris.withAppendedId(collection, id)
                        out.putIfAbsent(name, uri)
                    }
                }
            }
        }

        // Prefer our backups first, then alpha
        return@withContext out.entries
            .map { it.key to it.value }
            .sortedWith(compareBy({ !it.first.contains("backup", ignoreCase = true) }, { it.first }))
    }


}
