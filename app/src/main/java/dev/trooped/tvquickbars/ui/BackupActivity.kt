package dev.trooped.tvquickbars.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.BackupManager
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.persistence.QuickBarManager
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.persistence.TriggerKeyManager
import kotlinx.coroutines.launch

/**
 * BackupActivity - SAF-based backup/restore with user-selected location/file
 */
class BackupActivity : BaseActivity() {

    enum class Mode { BACKUP, RESTORE }

    private lateinit var backupManager: BackupManager
    private var isRestoreMode = false

    // simple isProcessing bridge to Compose
    private var isProcessing = false
    private var processingStateUpdated: ((Boolean) -> Unit)? = null
    private fun setProcessing(processing: Boolean) {
        isProcessing = processing
        runOnUiThread { processingStateUpdated?.invoke(processing) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backupManager = BackupManager(this)
        isRestoreMode = intent.getBooleanExtra("restore_mode", false)

        val entityCount = SavedEntitiesManager(this).loadEntities().size
        val quickBarCount = QuickBarManager(this).loadQuickBars().size
        val triggerKeyCount = TriggerKeyManager(this).loadTriggerKeys().size

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = colorResource(id = R.color.md_theme_primary),
                    onPrimary = colorResource(id = R.color.md_theme_onPrimary),
                    primaryContainer = colorResource(id = R.color.md_theme_primaryContainer),
                    onPrimaryContainer = colorResource(id = R.color.md_theme_onPrimaryContainer),
                    secondary = colorResource(id = R.color.md_theme_secondary),
                    onSecondary = colorResource(id = R.color.md_theme_onSecondary),
                    secondaryContainer = colorResource(id = R.color.md_theme_secondaryContainer),
                    onSecondaryContainer = colorResource(id = R.color.md_theme_onSecondaryContainer),
                    tertiary = colorResource(id = R.color.md_theme_tertiary),
                    onTertiary = colorResource(id = R.color.md_theme_onTertiary),
                    tertiaryContainer = colorResource(id = R.color.md_theme_tertiaryContainer),
                    onTertiaryContainer = colorResource(id = R.color.md_theme_onTertiaryContainer),
                    error = colorResource(id = R.color.md_theme_error),
                    onError = colorResource(id = R.color.md_theme_onError),
                    errorContainer = colorResource(id = R.color.md_theme_errorContainer),
                    onErrorContainer = colorResource(id = R.color.md_theme_onErrorContainer),
                    background = colorResource(id = R.color.md_theme_background),
                    onBackground = colorResource(id = R.color.md_theme_onBackground),
                    surface = colorResource(id = R.color.md_theme_surface),
                    onSurface = colorResource(id = R.color.md_theme_onSurface),
                    surfaceVariant = colorResource(id = R.color.md_theme_surfaceVariant),
                    onSurfaceVariant = colorResource(id = R.color.md_theme_onSurfaceVariant),
                    outline = colorResource(id = R.color.md_theme_outline),
                    inverseSurface = colorResource(id = R.color.md_theme_inverseSurface),
                    inverseOnSurface = colorResource(id = R.color.md_theme_inverseOnSurface),
                    inversePrimary = colorResource(id = R.color.md_theme_inversePrimary),
                    surfaceTint = colorResource(id = R.color.md_theme_primary)
                )
            ) {
                BackupScreen(
                    mode = if (isRestoreMode) Mode.RESTORE else Mode.BACKUP,
                    entityCount = entityCount,
                    quickBarCount = quickBarCount,
                    triggerKeyCount = triggerKeyCount,
                    onBackPressed = { finish() }
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BackupScreen(
        mode: Mode,
        entityCount: Int,
        quickBarCount: Int,
        triggerKeyCount: Int,
        onBackPressed: () -> Unit
    ) {
        val ctx = LocalContext.current

        var alertState by remember { mutableStateOf<AlertState?>(null) }
        var confirmState by remember { mutableStateOf<ConfirmState?>(null) }
        var pickerItems by remember { mutableStateOf<List<PickerItemUi>?>(null) }

        var includeEntities by remember { mutableStateOf(true) }
        var includeQuickBars by remember { mutableStateOf(true) }
        var includeTriggerKeys by remember { mutableStateOf(true) }
        var uiProcessing by remember { mutableStateOf(isProcessing) }

        val browseDownloadsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val pickedUri = result.data?.data ?: return@rememberLauncherForActivityResult
                // Proceed: read + validate + confirm + restore (same flow you already use)
                setProcessing(true)
                lifecycleScope.launch {
                    val data = backupManager.readBackupFromFile(pickedUri)
                    setProcessing(false)
                    if (data == null || !backupManager.validateBackup(data)) {
                        alertState = AlertState("Error", "Invalid backup file.")
                    } else {
                        confirmState = ConfirmState(
                            title = "Confirm Restore",
                            message = "Replace current data with this backup?",
                            onConfirm = {
                                setProcessing(true)
                                lifecycleScope.launch {
                                    val ok = backupManager.restoreFromBackup(
                                        data,
                                        includeEntities,
                                        includeQuickBars,
                                        includeTriggerKeys
                                    )

                                    // Check if this is first-time setup
                                    val isFirstTimeSetup = AppPrefs.isFirstTimeSetupInProgress(this@BackupActivity)

                                    val msg = if (ok) {
                                        val fixed = backupManager.validateReferences(
                                            includeEntities,
                                            includeQuickBars,
                                            includeTriggerKeys
                                        )
                                        if (fixed > 0) "Restore complete. $fixed references were fixed."
                                        else "Restore complete."
                                    } else "Restore failed."

                                    setProcessing(false)
                                    alertState = AlertState(
                                        title = if (ok) "Restore Successful" else "Error",
                                        message = msg,
                                        onConfirm = {
                                            if (ok) {
                                                if (isFirstTimeSetup) {
                                                    // If this is first-time setup, clear the flag and go directly to main activity
                                                    AppPrefs.setFirstTimeSetupInProgress(this@BackupActivity, false)
                                                    val intent = Intent(this@BackupActivity, ComposeMainActivity::class.java)
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    startActivity(intent)
                                                } else {
                                                    // Normal flow - just return to previous screen
                                                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                                                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    startActivity(intent)
                                                }
                                                finish()
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        fun launchDownloadsPicker() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json" // we only want .json backups

                // Try to start at the Downloads root. Some TVs may ignore this (that’s fine).
                val downloadsRoot = DocumentsContract.buildRootUri(
                    "com.android.providers.downloads.documents",
                    "downloads"
                )
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsRoot)
            }
            browseDownloadsLauncher.launch(intent)
        }

        val saveFileLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@rememberLauncherForActivityResult

                setProcessing(true)
                lifecycleScope.launch {
                    val data = backupManager.createBackup(includeEntities, includeQuickBars, includeTriggerKeys)
                    val saved = backupManager.saveBackupToFile(data, uri)
                    setProcessing(false)
                    if (saved) {
                        alertState = AlertState(
                            title = "Backup Saved",
                            message = "Backup saved successfully to selected location.",
                            confirmText = "OK",
                            onConfirm = { finish() }
                        )
                    } else {
                        alertState = AlertState(
                            title = "Backup Failed",
                            message = "Could not save backup to the selected location.",
                            confirmText = "OK"
                        )
                    }
                }
            }
        }


        val folderPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val treeUri = result.data?.data ?: return@rememberLauncherForActivityResult

                // Persist permission so future saves can reuse this folder if you want
                val flags = result.data?.flags ?: 0
                try {
                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    )
                } catch (_: SecurityException) { /* best effort */ }

                // Create the file in the chosen folder and write the backup
                setProcessing(true)
                lifecycleScope.launch {
                    val data = backupManager.createBackup(includeEntities, includeQuickBars, includeTriggerKeys)
                    val dir = DocumentFile.fromTreeUri(this@BackupActivity, treeUri)
                    val name = backupManager.getDefaultBackupFilename()
                    val doc = dir?.createFile("application/json", name)

                    val ok = if (doc != null) backupManager.saveBackupToFile(data, doc.uri) else false
                    setProcessing(false)
                    alertState = if (ok) {
                        AlertState(
                            title = "Backup Saved",
                            message = "Backup saved to the selected folder as $name.",
                            onConfirm = { finish() }
                        )
                    } else {
                        AlertState(
                            title = "Backup Failed",
                            message = "Could not save backup to the selected folder."
                        )
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            processingStateUpdated = { uiProcessing = it }
            onDispose { processingStateUpdated = null }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (mode == Mode.BACKUP) "Create Backup" else "Restore From Backup") },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 40.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = colorResource(id = R.color.md_theme_surfaceContainerLow)
                    ),
                    border = BorderStroke(1.dp, colorResource(id = R.color.md_theme_outline))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (mode == Mode.BACKUP)
                                "Select what to include in your backup (it's recommended to select all):"
                            else
                                "Select what to restore from backup (it's recommended to select all):",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = includeEntities,
                                onCheckedChange = { includeEntities = it })
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Entities", style = MaterialTheme.typography.bodyLarge)
                                if (mode == Mode.BACKUP) {
                                    Text(
                                        "$entityCount ${if (entityCount == 1) "entity" else "entities"} available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = includeQuickBars,
                                onCheckedChange = { includeQuickBars = it })
                            Column(modifier = Modifier.weight(1f)) {
                                Text("QuickBars", style = MaterialTheme.typography.bodyLarge)
                                if (mode == Mode.BACKUP) {
                                    Text(
                                        "$quickBarCount ${if (quickBarCount == 1) "QuickBar" else "QuickBars"} available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = includeTriggerKeys,
                                onCheckedChange = { includeTriggerKeys = it })
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Trigger Keys", style = MaterialTheme.typography.bodyLarge)
                                if (mode == Mode.BACKUP) {
                                    Text(
                                        "$triggerKeyCount ${if (triggerKeyCount == 1) "key" else "keys"} available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if ((includeQuickBars && !includeEntities) || (includeTriggerKeys && (!includeQuickBars || !includeEntities))) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Dependency Warning",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (includeQuickBars && !includeEntities) {
                                Text(
                                    "• QuickBars reference entities. Without entities, restored QuickBars may be empty.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (includeTriggerKeys && !includeEntities) {
                                Text(
                                    "• Trigger keys may reference entities. Without entities, some key functions may not work.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (includeTriggerKeys && !includeQuickBars) {
                                Text(
                                    "• Trigger keys may open QuickBars. Without QuickBars, these keys won't function properly.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mode == Mode.BACKUP)
                            colorResource(id = R.color.md_theme_primaryContainer).copy(alpha = 0.5f)
                        else
                            colorResource(id = R.color.md_theme_tertiaryContainer).copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Security Note", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (mode == Mode.BACKUP)
                                "This backup does NOT include your Home Assistant URL or access token."
                            else
                                "Restore won’t change your Home Assistant URL or access token.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Instructions card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = colorResource(id = R.color.md_theme_surfaceContainerLow)
                    ),
                    border = BorderStroke(1.dp, colorResource(id = R.color.md_theme_outline))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (mode == Mode.BACKUP) "Where backups are saved:" else "Finding backup files:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // SAF capability detection (once per composition)
                        val ctx = LocalContext.current
                        val pm = ctx.packageManager

                        val hasCreatePicker = remember {
                            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("application/json")
                            pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
                                .isNotEmpty()
                        }
                        val hasTreePicker = remember {
                            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
                                .isNotEmpty()
                        }
                        val hasOpenPicker = remember {
                            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("application/json")
                            pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
                                .isNotEmpty()
                        }

                        if (mode == Mode.BACKUP) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                Text(
                                    text = "• Backups will be saved to your Downloads folder.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                if (hasCreatePicker || hasTreePicker) {
                                    Text(
                                        text = "• You can also choose a custom folder using your device’s file picker.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    Text(
                                        text = "• This device doesn’t have a file picker app that supports the Android file picker (SAF). To choose a folder, install a compatible file manager. Until then, backups will be saved to Downloads.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Not all devices support saving to a custom location, and not all file picker apps support this feature.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = "• Backup to Downloads folder is only supported on Android 10+",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            // RESTORE mode
                            Text(
                                text = "• The app will search for backup files in your Downloads folder.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            if (hasOpenPicker || hasTreePicker) {
                                Text(
                                    text = "• You can also choose a backup file using your device’s file picker.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = "• This device doesn’t have a file picker app that supports the Android file picker (SAF). If you can’t see your file, install a compatible file manager. You can still restore from files in Downloads.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Make sure the file name contains “backup” and ends with “.json”.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                var context = LocalContext.current
                // Action Button
                var continueButtonFocused by remember { mutableStateOf(false) }
                // Then, modify the Backup action button to include the custom save option
                if (mode == Mode.BACKUP) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        var customButtonFocused by remember { mutableStateOf(false) }
                        // Custom location button
                        Button(
                            onClick = {
                                if (!includeEntities && !includeQuickBars && !includeTriggerKeys) {
                                    Toast.makeText(context, "Please select at least one item.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // 1) Prefer CREATE_DOCUMENT (save-as dialog). Some TVs don’t support it.
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TITLE, backupManager.getDefaultBackupFilename())

                                    // Optional: try to start in Downloads (some pickers ignore this)
                                    val downloadsRoot = DocumentsContract.buildRootUri(
                                        "com.android.providers.downloads.documents",
                                        "downloads"
                                    )
                                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsRoot)
                                }

                                try {
                                    saveFileLauncher.launch(intent)
                                } catch (_: ActivityNotFoundException) {
                                    // 2) Fallback: folder picker + DocumentFile.createFile(...)
                                    val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                        // Optional: try to start at Downloads
                                        val downloadsRoot = DocumentsContract.buildRootUri(
                                            "com.android.providers.downloads.documents",
                                            "downloads"
                                        )
                                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsRoot)
                                    }
                                    folderPickerLauncher.launch(treeIntent)
                                } catch (e: Exception) {
                                    Log.e("BackupActivity", "Failed to launch save flow: ${e.message}", e)
                                    alertState = AlertState(
                                        title = "File Picker Error",
                                        message = "No compatible picker found to save the file."
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .onFocusChanged { isFocused ->
                                    customButtonFocused = isFocused.hasFocus
                                },
                            enabled = !uiProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = if (customButtonFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = if (customButtonFocused) 8.dp else 2.dp
                            )
                        ) {
                            if (uiProcessing) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save to Custom Location")
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        var downloadsButtonFocused by remember { mutableStateOf(false) }
                        // Save to Downloads button
                        Button(
                            onClick = {
                                // Original action to save to Downloads
                                if (!includeEntities && !includeQuickBars && !includeTriggerKeys) {
                                    Toast.makeText(context, "Please select at least one item.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Check Android version for backup
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    alertState = AlertState(
                                        title = "Not Supported",
                                        message = "Backup to Downloads folder is only supported on Android 10 and newer.",
                                        confirmText = "OK"
                                    )
                                    return@Button
                                }

                                setProcessing(true)
                                val filename = backupManager.getDefaultBackupFilename()

                                lifecycleScope.launch {
                                    val data = backupManager.createBackup(includeEntities, includeQuickBars, includeTriggerKeys)

                                    // Try MediaStore Downloads on API 29+
                                    val savedToDownloads = backupManager.saveBackupToDownloads(data, filename)

                                    setProcessing(false)
                                    if (savedToDownloads) {
                                        alertState = AlertState(
                                            title = "Backup Saved",
                                            message = "Saved to Downloads folder.\nFile name: $filename",
                                            confirmText = "OK",
                                            onConfirm = { finish() }
                                        )
                                    } else {
                                        // Failed to save
                                        alertState = AlertState(
                                            title = "Backup Failed",
                                            message = "Could not save backup to Downloads folder.",
                                            confirmText = "OK"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .onFocusChanged { isFocused ->
                                    downloadsButtonFocused = isFocused.hasFocus
                                },
                            enabled = !uiProcessing,
                            border = if (downloadsButtonFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant) else null,
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = if (downloadsButtonFocused) 8.dp else 2.dp
                            )
                        ) {
                            if (uiProcessing) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save to Downloads")
                            }
                        }
                    }
                } else {
                    // RESTORE mode - two buttons like backup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        var customButtonFocused by remember { mutableStateOf(false) }

                        // Browse custom location button
                        Button(
                            onClick = {
                                if (!includeEntities && !includeQuickBars && !includeTriggerKeys) {
                                    Toast.makeText(context, "Please select at least one item.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Launch SAF directly to pick a file
                                launchDownloadsPicker()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .onFocusChanged { isFocused ->
                                    customButtonFocused = isFocused.hasFocus
                                },
                            enabled = !uiProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = if (customButtonFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = if (customButtonFocused) 8.dp else 2.dp
                            )
                        ) {
                            if (uiProcessing) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Select Custom File")
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        var downloadsButtonFocused by remember { mutableStateOf(false) }
                        // Find in Downloads button
                        Button(
                            onClick = {
                                if (!includeEntities && !includeQuickBars && !includeTriggerKeys) {
                                    Toast.makeText(context, "Please select at least one item.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Search Downloads folder automatically
                                setProcessing(true)
                                lifecycleScope.launch {
                                    val downloads = if (android.os.Build.VERSION.SDK_INT >= 29)
                                        backupManager.listBackupsInDownloads() else emptyList()

                                    setProcessing(false)
                                    if (downloads.isEmpty()) {
                                        alertState = AlertState(
                                            title = "No Backups Found",
                                            message = "We didn't find any backup files automatically in your Downloads folder. Please try selecting a file manually with a file picker app.",
                                            confirmText = "OK"
                                        )
                                        return@launch
                                    }

                                    val list = mutableListOf<PickerItemUi>()

                                    downloads.forEach { (name, uri) ->
                                        list += PickerItemUi(
                                            headline = name,
                                            supporting = "From Downloads",
                                            badge = "Downloads",
                                            onClick = {
                                                pickerItems = null    // close dialog
                                                setProcessing(true)
                                                lifecycleScope.launch {
                                                    val data = backupManager.readBackupFromFile(uri)
                                                    setProcessing(false)
                                                    if (data == null || !backupManager.validateBackup(data)) {
                                                        alertState = AlertState("Error", "Invalid backup file.")
                                                    } else {
                                                        confirmState = ConfirmState(
                                                            title = "Confirm Restore",
                                                            message = "Replace current data with this backup?",
                                                            onConfirm = {
                                                                setProcessing(true)
                                                                lifecycleScope.launch {
                                                                    val ok = backupManager.restoreFromBackup(
                                                                        data,
                                                                        includeEntities,
                                                                        includeQuickBars,
                                                                        includeTriggerKeys
                                                                    )

                                                                    // Check if this is first-time setup
                                                                    val isFirstTimeSetup = AppPrefs.isFirstTimeSetupInProgress(this@BackupActivity)

                                                                    val msg = if (ok) {
                                                                        val fixed = backupManager.validateReferences(
                                                                            includeEntities,
                                                                            includeQuickBars,
                                                                            includeTriggerKeys
                                                                        )
                                                                        if (fixed > 0) "Restore complete. $fixed references were fixed."
                                                                        else "Restore complete."
                                                                    } else "Restore failed."

                                                                    setProcessing(false)
                                                                    alertState = AlertState(
                                                                        title = if (ok) "Restore Successful" else "Error",
                                                                        message = msg,
                                                                        onConfirm = {
                                                                            if (ok) {
                                                                                if (isFirstTimeSetup) {
                                                                                    // If this is first-time setup, clear the flag and go directly to main activity
                                                                                    AppPrefs.setFirstTimeSetupInProgress(this@BackupActivity, false)
                                                                                    val intent = Intent(this@BackupActivity, ComposeMainActivity::class.java)
                                                                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                                    startActivity(intent)
                                                                                } else {
                                                                                    // Normal flow - just return to previous screen
                                                                                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                                                                                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                                    startActivity(intent)
                                                                                }
                                                                                finish()
                                                                            }
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    // Open the modern picker dialog
                                    pickerItems = list
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .onFocusChanged { isFocused ->
                                    downloadsButtonFocused = isFocused.hasFocus
                                },
                            enabled = !uiProcessing,
                            border = if (downloadsButtonFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant) else null,
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = if (downloadsButtonFocused) 8.dp else 2.dp
                            )
                        ) {
                            if (uiProcessing) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Find in Downloads")
                            }
                        }
                    }
                }
            }
        }

        pickerItems?.let { items ->
            TvListDialog(
                title = "Select Backup",
                items = items,
                onDismiss = { pickerItems = null }
            )
        }

        TvAlertDialog(
            state = alertState,
            onDismiss = { alertState = null }
        )

        TvConfirmDialog(
            state = confirmState,
            onDismiss = { confirmState = null }
        )
    }
}

data class AlertState(
    val title: String,
    val message: String,
    val confirmText: String = "OK",
    val onConfirm: (() -> Unit)? = null
)

data class ConfirmState(
    val title: String,
    val message: String,
    val confirmText: String = "Restore",
    val cancelText: String = "Cancel",
    val onConfirm: () -> Unit,
    val onCancel: (() -> Unit)? = null
)

data class PickerItemUi(
    val headline: String,
    val supporting: String? = null,
    val badge: String? = null,            // e.g. "Downloads" / "App"
    val onClick: () -> Unit
)

// ---------- Building blocks ----------
@Composable
private fun FocusCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "focus-scale")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .focusable(interactionSource = interaction)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (focused) 8.dp else 2.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(contentPadding)
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

// ---------- Pretty alert ----------
@Composable
fun TvAlertDialog(
    state: AlertState?,
    onDismiss: () -> Unit
) {
    if (state == null) return
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(state.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = {
                        onDismiss()
                        state.onConfirm?.invoke()
                    }) {
                        Text(state.confirmText)
                    }
                }
            }
        }
    }
}

// ---------- Pretty confirm ----------
@Composable
fun TvConfirmDialog(
    state: ConfirmState?,
    onDismiss: () -> Unit
) {
    if (state == null) return
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(state.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = {
                        onDismiss(); state.onCancel?.invoke()
                    }) { Text(state.cancelText) }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = {
                        onDismiss(); state.onConfirm()
                    }) { Text(state.confirmText) }
                }
            }
        }
    }
}

@Composable
fun ManualFilenameDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!visible) return
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(24.dp).widthIn(min = 640.dp)) {
                Text("Enter Backup Filename", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("haquickbars_backup_YYYYMMDD_HHMMSS.json") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { onConfirm(text.trim()) }) { Text("Use File") }
                }
            }
        }
    }
}

// ---------- Pretty picker (restore selection) ----------
@Composable
fun TvListDialog(
    title: String,
    items: List<PickerItemUi>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(24.dp).widthIn(min = 640.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(min = 120.dp, max = 480.dp)
                ) {
                    items(items.size) { idx ->
                        val item = items[idx]
                        FocusCard(onClick = item.onClick) {
                            Column(Modifier.weight(1f)) {
                                Text(item.headline, style = MaterialTheme.typography.titleMedium)
                                if (!item.supporting.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        item.supporting,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                            if (!item.badge.isNullOrBlank()) {
                                Spacer(Modifier.width(12.dp))
                                AssistChip(
                                    onClick = item.onClick,
                                    label = { Text(item.badge) }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}
