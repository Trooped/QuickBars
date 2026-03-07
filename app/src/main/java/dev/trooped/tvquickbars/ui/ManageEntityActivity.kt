
package dev.trooped.tvquickbars.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityAction
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.PressType
import dev.trooped.tvquickbars.data.computeLightCaps
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.utils.EntityUsageUtils
import org.json.JSONObject

class ManageEntityActivity : BaseActivity() {
    private lateinit var savedEntitiesManager: SavedEntitiesManager
    private var entityId: String? = null
    private var entity: EntityItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // Get entity ID from intent
        entityId = intent.getStringExtra("entityId")
        if (entityId == null) {
            Toast.makeText(this, "Error: No entity ID provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load entity from storage
        savedEntitiesManager = SavedEntitiesManager(this)
        val entities = savedEntitiesManager.loadEntities()
        entity = entities.find { it.id == entityId }

        if (entity == null) {
            Toast.makeText(this, "Error: Entity not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Ensure we have default actions applied
        if (!entity!!.defaultPressActionsApplied) {
            savedEntitiesManager.applyDefaultActions(entity!!)
        }

        setContent {
            CompositionLocalProvider(LocalActivity provides this) {

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
                    ManageEntityScreen(
                        entity = entity!!,
                        onSave = { updatedEntity -> saveEntity(updatedEntity) },
                        onDelete = { showDeleteConfirmation() },
                        onCancel = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }

    private fun saveEntity(updatedEntity: EntityItem) {
        try {
            // Create a safe copy
            val safeCopy = updatedEntity.copy(
                pressActions   = updatedEntity.pressActions.toMutableMap(), // ensure actions are persisted
                pressTargets   = updatedEntity.pressTargets.toMutableMap(), // kept for backward-compat (ignored at runtime)
                lastKnownState = updatedEntity.lastKnownState.toMutableMap()
            )

            // Load all entities
            val allEntities = savedEntitiesManager.loadEntities()

            // Find and replace the entity in the list
            val index = allEntities.indexOfFirst { it.id == safeCopy.id }
            if (index != -1) {
                allEntities[index] = safeCopy
            } else {
                Log.w("ManageEntityActivity", "Entity not found in loaded entities list!")
            }

            // Save all entities
            savedEntitiesManager.saveEntities(allEntities)

            // Complete the activity
            val resultIntent = Intent().apply {
                putExtra("updated", true)
                putExtra("updatedEntityId", safeCopy.id)
                putExtra("position", intent.getIntExtra("position", -1))
                // Bundle the actual updated entity
                putExtra("updatedEntity", savedEntitiesManager.gson.toJson(safeCopy))
            }

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving changes. Please try again.", Toast.LENGTH_LONG).show()
        }
    }

    // Update the deleteEntity method
    private fun deleteEntity() {
        entityId?.let {
            savedEntitiesManager.removeEntity(it)

            val resultIntent = Intent()
            resultIntent.putExtra("deleted", true)
            resultIntent.putExtra("deletedEntityId", it)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun showDeleteConfirmation() {
        val entityId = this.entityId ?: return

        // Get entity usage information
        val (quickBarNames, triggerKeyNames) = EntityUsageUtils.getEntityUsageDetails(this, entityId)

        // Build the confirmation message with usage details
        val message = EntityUsageUtils.buildRemovalConfirmationMessage(
            entity?.customName ?: "",
            quickBarNames,
            triggerKeyNames
        )

        // Show confirmation dialog
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Entity")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ -> deleteEntity() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageEntityScreen(
    entity: EntityItem,
    onSave: (EntityItem) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    var entityState by remember { mutableStateOf(entity) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val saveButtonFocusRequester = remember { FocusRequester() }
    var saveButtonFocused by remember { mutableStateOf(false) }
    var deleteButtonFocused by remember { mutableStateOf(false) }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes") },
            text = { Text("Are you sure you want to leave? Changes will not be saved.") },
            confirmButton = {
                TextButton(onClick = onCancel) {
                    Text("LEAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("STAY")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Edit Entity")
                        if (hasUnsavedChanges) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onCancel()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Delete button
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .onFocusChanged { deleteButtonFocused = it.isFocused },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = if (deleteButtonFocused)
                        BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                    else null
                ) {
                    Text("DELETE", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Cancel button
                var cancelFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onCancel()
                        }
                    },
                    modifier = Modifier.onFocusChanged { cancelFocused = it.isFocused },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (cancelFocused)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    ),
                    border = if (cancelFocused)
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else null
                ) {
                    Text("CANCEL", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Save button
                Button(
                    onClick = { onSave(entityState) },
                    enabled = hasUnsavedChanges,
                    modifier = Modifier
                        .focusRequester(saveButtonFocusRequester)
                        .onFocusChanged { saveButtonFocused = it.isFocused },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasUnsavedChanges)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (saveButtonFocused && hasUnsavedChanges)
                        BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary)
                    else null
                ) {
                    Text("SAVE", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 70.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Entity Details
            item {
                EntityDetailsCard(
                    entity = entityState,
                    onEntityChanged = {
                        entityState = it
                        hasUnsavedChanges = true
                    }
                )
            }

            // Card 2: Click Actions
            item {
                ClickActionsCard(
                    entity = entityState,
                    onEntityChanged = {
                        entityState = it
                        hasUnsavedChanges = true
                    }
                )
            }

            // Card 3: Entity-Specific Settings
            item {
                EntitySpecificSettingsCard(
                    entity = entityState,
                    onEntityChanged = {
                        entityState = it
                        hasUnsavedChanges = true
                    }
                )
            }

            // Add extra space at bottom for better scrolling
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(bottom = 16.dp)) {
                    title()
                }

                Box(modifier = Modifier.padding(bottom = 24.dp)) {
                    text()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        dismissButton()
                    }

                    Box {
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
fun EntityDetailsCard(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    val context = LocalContext.current
    val iconSelectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val iconOnName = data?.getStringExtra(IconSelectionActivity.RESULT_ICON_ON_NAME)
            val iconOffName = data?.getStringExtra(IconSelectionActivity.RESULT_ICON_OFF_NAME)

            if (iconOnName != null) {
                val updatedEntity = entity.copy(
                    customIconOnName = iconOnName,
                    customIconOffName = iconOffName
                )
                onEntityChanged(updatedEntity)
            }
        }
    }

    val iconButtonFocusRequester = remember { FocusRequester() }
    var iconButtonFocused by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf(entity.customName.takeIf { it.isNotBlank() } ?: entity.friendlyName) }
    var nameError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entity.customName, entity.friendlyName) {
        name = entity.customName.takeIf { it.isNotBlank() } ?: entity.friendlyName
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant // Match app style
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                // Title
                Text(
                    text = "Entity Details",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Icon and button to change it
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val iconRes =
                        EntityIconMapper.getFinalIconForEntity(entity) ?: R.drawable.ic_default
                    // Icon preview
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = "Entity Icon",
                            modifier = Modifier.size(70.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Button to change icon
                    Button(
                        onClick = {
                            val intent = Intent(context, IconSelectionActivity::class.java).apply {
                                putExtra("ENTITY_ID", entity.id)
                                putExtra("IS_TOGGLEABLE", entity.isActionable)

                                // Get resource IDs from resource names
                                val iconOnRes = try {
                                    if (entity.customIconOnName != null)
                                        context.resources.getIdentifier(
                                            entity.customIconOnName,
                                            "drawable",
                                            context.packageName
                                        ) else 0
                                } catch (e: Exception) {
                                    0
                                }

                                val iconOffRes = try {
                                    if (entity.customIconOffName != null)
                                        context.resources.getIdentifier(
                                            entity.customIconOffName,
                                            "drawable",
                                            context.packageName
                                        ) else 0
                                } catch (e: Exception) {
                                    0
                                }

                                putExtra("CURRENT_ICON_ON", iconOnRes)
                                putExtra("CURRENT_ICON_OFF", iconOffRes)
                            }
                            iconSelectionLauncher.launch(intent)
                        },
                        modifier = Modifier
                            .focusRequester(iconButtonFocusRequester)
                            .onFocusChanged { iconButtonFocused = it.isFocused },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (iconButtonFocused)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            else
                                MaterialTheme.colorScheme.primary
                        ),
                        border = if (iconButtonFocused)
                            BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                        else null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Icon")
                    }
                }

                // Keep syncing local `name` with entity values
                LaunchedEffect(entity.customName, entity.friendlyName) {
                    name = entity.customName.takeIf { it.isNotBlank() } ?: entity.friendlyName
                }

                // Replace the old OutlinedTextField + dialog with this:
                EntityNameEditorRow(
                    value = name,
                    enabled = true,
                    title = "Custom Entity Name:",
                    subtitle = name,
                    dialogTitle = "Edit Entity Name",
                    fieldLabel = "Entity Name",
                    maxLength = 20,
                    validate = { newName ->
                        when {
                            newName.trim().isEmpty() -> "Name cannot be empty"
                            newName.length > 20      -> "Name must be ≤ 20 characters"
                            else -> null
                        }
                    },
                    onCommit = { newName ->
                        name = newName
                        onEntityChanged(entity.copy(customName = newName))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                /*
                // Entity type info
                val category = entity.category.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault())
                    else it.toString()
                }

                Text(
                    text = "Type: $category",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                 */

                // Entity ID
                Text(
                    text = "Entity ID: ${entity.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Warning for missing entities
                if (!entity.isAvailable) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "This entity is no longer provided by Home Assistant. It may have been deleted or its ID has changed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun EntityNameEditorRow(
    value: String,
    enabled: Boolean,
    onCommit: (String) -> Unit,
    // New, generic params (with camera-alias defaults to avoid breaking callers)
    title: String = "Entity Name",
    subtitle: String? = "Set a friendly name for the entity",
    dialogTitle: String = "Edit",
    fieldLabel: String = title,
    maxLength: Int? = null,
    validate: (String) -> String? = { null }
) {
    val activatorFocus = remember { FocusRequester() }
    var showDialog by remember { mutableStateOf(false) }
    var buttonFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(activatorFocus),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
            }
        }
        OutlinedButton(
            enabled = enabled,
            onClick = { showDialog = true },
            modifier = Modifier.onFocusChanged { buttonFocused = it.isFocused },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(
                width = if (buttonFocused) 2.dp else 1.dp,
                color = if (buttonFocused)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            )
        ) { Text("Edit") }
    }

    if (showDialog) {
        val keyboard = LocalSoftwareKeyboardController.current
        val tfFocus = remember { FocusRequester() }
        val okFocus = remember { FocusRequester() }
        val cancelFocus = remember { FocusRequester() }

        var text by remember { mutableStateOf(value) }
        var error by remember { mutableStateOf<String?>(validate(value)) }

        // Detect IME visibility; when it hides, push focus to OK
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0

        LaunchedEffect(Unit) {
            tfFocus.requestFocus()
            keyboard?.show()
        }
        LaunchedEffect(imeVisible) {
            if (!imeVisible) okFocus.requestFocus()
        }

        fun confirmAndClose() {
            if (error == null) {
                keyboard?.hide()
                onCommit(text.trim())
                showDialog = false
                activatorFocus.requestFocus()
            }
        }
        fun cancelAndClose() {
            keyboard?.hide()
            showDialog = false
            activatorFocus.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { cancelAndClose() },
            title = { Text(dialogTitle) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        val v = if (maxLength != null && it.length > maxLength) it.take(maxLength) else it
                        text = v
                        error = validate(v)
                    },
                    label = { Text(fieldLabel) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(tfFocus)
                        .onPreviewKeyEvent { ke ->
                            if (ke.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                            when (ke.key) {
                                Key.DirectionDown, Key.Tab -> {
                                    okFocus.requestFocus(); true
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    confirmAndClose(); true
                                }
                                Key.Back -> {
                                    if (imeVisible) {
                                        keyboard?.hide()
                                        okFocus.requestFocus()
                                        true
                                    } else {
                                        cancelAndClose()
                                        true
                                    }
                                }
                                else -> false
                            }
                        },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { confirmAndClose() }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.focusRequester(okFocus),
                    onClick = { confirmAndClose() },
                    enabled = error == null
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.focusRequester(cancelFocus),
                    onClick = { cancelAndClose() }
                ) { Text("Cancel") }
            }
        )
    }
}



@Composable
fun ClickActionsCard(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    val context = LocalContext.current
    val savedEntitiesManager = remember { SavedEntitiesManager(context) }

    // Current actions (may be null/Default -> show default text)
    val singleAction = entity.pressActions[PressType.SINGLE]
    val longAction   = entity.pressActions[PressType.LONG]

    // Dialog state
    var showActionDialog by remember { mutableStateOf(false) }
    var currentEditPressType by remember { mutableStateOf<PressType?>(null) }

    if (showActionDialog && currentEditPressType != null) {
        ActionConfigDialog(
            entity = entity,
            pressType = currentEditPressType!!,
            currentAction = entity.pressActions[currentEditPressType!!],
            savedEntitiesManager = savedEntitiesManager,
            onDismiss = { showActionDialog = false },
            onActionSelected = { pressType, newAction ->
                val updated = entity.copy(
                    pressActions = entity.pressActions.toMutableMap().apply {
                        this[pressType] = newAction
                    }
                )
                onEntityChanged(updated)
                showActionDialog = false
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Click Actions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Single Press
            ClickActionRow(
                title = "Single Press",
                description = getActionDescription(singleAction, entity, PressType.SINGLE),
                actionText = "Configure",
                onClick = {
                    currentEditPressType = PressType.SINGLE
                    showActionDialog = true
                }
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Long Press
            ClickActionRow(
                title = "Long Press",
                description = getActionDescription(longAction, entity, PressType.LONG),
                actionText = "Configure",
                onClick = {
                    currentEditPressType = PressType.LONG
                    showActionDialog = true
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Configure what happens when you interact with this entity in the QuickBar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun isSimpleLight(entity: EntityItem): Boolean {
    if (!entity.id.startsWith("light.")) return false
    // Prefer what we persisted during applyDefaultLightOptions(...)
    val persisted = entity.lastKnownState["is_simple_light"] as? Boolean
    return persisted ?: computeLightCaps(entity.attributes, entity.lastKnownState).isSimple
}

@Composable
private fun getActionDescription(action: EntityAction?, entity: EntityItem, pressType: PressType): String {
    // First check if the action matches what would be the default
    if (isDefaultActionForEntity(action, entity, pressType)) {
        // Show the default description with "Default:" prefix
        return getDefaultActionDescription(entity, pressType)
    }

    val context = LocalContext.current
    val savedEntitiesManager = remember { SavedEntitiesManager(context) }

    return when (action) {
        null, is EntityAction.Default -> {
            // Show what the app will do if no explicit action is set
            getDefaultActionDescription(entity, pressType)
        }

        is EntityAction.BuiltIn -> {
            when (action.type) {
                EntityAction.Special.EXPAND -> "Expand Card"
                EntityAction.Special.CAMERA_PIP -> "Camera PIP"
                EntityAction.Special.LIGHT_TOGGLE -> "Toggle Light"
                EntityAction.Special.CLIMATE_TOGGLE_WITH_MEMORY -> "Toggle Climate"
                EntityAction.Special.FAN_TOGGLE_WITH_MEMORY -> "Toggle Fan"
                EntityAction.Special.COVER_TOGGLE -> "Toggle Cover"
                EntityAction.Special.LOCK_TOGGLE -> "Lock/Unlock"
                EntityAction.Special.TRIGGER -> "Trigger Automation"
                EntityAction.Special.MEDIA_PLAYER_TOGGLE -> "Toggle Media Player"
            }
        }

        is EntityAction.ControlEntity -> {
            val targetId = action.targetId
            val target = remember(targetId) { savedEntitiesManager.loadEntities().find { it.id == targetId } }
            val targetName = target?.customName?.takeIf { it.isNotBlank() }
                ?: target?.friendlyName
                ?: targetId.substringAfterLast('.')
            val domain = targetId.substringBefore('.')
            val verb = when (domain) {
                "climate" -> "Toggle Climate"
                "media_player" -> "Toggle Media Player"
                "fan" -> "Toggle Fan"
                "cover" -> "Toggle Cover"
                "lock" -> "Lock/Unlock"
                "light" -> "Toggle Light"
                "switch", "input_boolean" -> "Toggle"
                "button", "input_button" -> "Press"
                "script", "scene" -> "Turn On"
                "camera" -> "Camera PIP"
                "automation" -> {
                    val pref = target?.lastKnownState?.get("automation_action") as? String ?: "trigger"
                    if (pref == "trigger") "Trigger" else "Toggle"
                }
                else -> "Control"
            }
            "$verb → $targetName"
        }

        is EntityAction.ServiceCall -> {
            // Helpful labels for the common cases; otherwise show the raw call
            val label = when (Pair(action.domain, action.service)) {
                "switch" to "toggle", "input_boolean" to "toggle" -> "Toggle"
                "button" to "press", "input_button" to "press"   -> "Press"
                "script" to "turn_on" -> "Turn On Script"
                "scene"  to "turn_on" -> "Turn On Scene"
                "automation" to "trigger" -> "Trigger Automation"
                "automation" to "toggle"  -> "Toggle Automation"
                else -> "${action.domain}.${action.service}"
            }
            label
        }
    }
}

private fun getDefaultActionDescription(entity: EntityItem, pressType: PressType? = null): String {
    val domain = entity.id.substringBefore('.')

    val isSimpleLight = if (domain == "light") isSimpleLight(entity) else false

    if (domain == "automation") {
        // For long press, always show "No Action"
        if (pressType == PressType.LONG) {
            return "Default: No Action"
        }

        // For single press or summary, use the user's preference
        val action = entity.lastKnownState["automation_action"] as? String ?: "trigger"
        val actionText = if (action == "trigger") "Trigger Automation" else "Toggle Automation"

        // For summary view (when pressType is null), add explanation
        return if (pressType == null) {
            if (action == "trigger") "Trigger Automation (Single Press)" else "Toggle Automation (Single Press)"
        } else {
            "Default: $actionText"
        }
    }

    // Different defaults based on press type
    return when (pressType) {
        PressType.SINGLE -> {
            when {
                domain == "light" && isSimpleLight -> "Default: Toggle"
                domain == "camera" -> "Default: Toggle Camera PIP"
                domain in listOf("climate", "fan", "cover", "lock", "alarm_control_panel", "light", "media_player") ->
                    "Default: Expand Card"
                domain in listOf("switch", "input_boolean") -> "Default: Toggle"
                domain in listOf("button", "input_button") -> "Default: Press"
                domain in listOf("script", "scene") -> "Default: Turn On"
                else -> "Default Action"
            }
        }

        PressType.LONG -> {
            when {
                // For simple lights, no action on long press
                domain == "light" && isSimpleLight -> "Default: No Action"
                domain == "camera" -> "Default: No Action"
                domain == "climate" -> "Default: Toggle Climate"
                domain == "media_player" -> "Default: Toggle Media Player"
                domain == "fan" -> "Default: Toggle Fan"
                domain == "cover" -> "Default: Toggle Cover"
                domain == "lock" -> "Default: Lock/Unlock"
                domain == "light" -> "Default: Toggle Light"
                domain == "alarm_control_panel" -> "Default: No Action"
                domain in listOf("switch", "input_boolean") -> "Default: No Action"
                domain in listOf("button", "input_button") -> "Default: No Action"
                domain in listOf("script", "scene") -> "Default: No Action"
                else -> "Default: No Action"
            }
        }


        else -> {
            when {
                domain == "camera" -> "Camera PIP/No Action"
                domain == "automation" -> "Trigger/Toggle"
                // Summary for simple lights
                domain == "light" && isSimpleLight -> "Toggle/No Action"

                // For other entities
                domain in listOf("climate", "fan", "cover", "lock", "alarm_control_panel", "light", "media_player") ->
                    "Expand Card/Toggle"
                domain in listOf("switch", "input_boolean") -> "Toggle"
                domain in listOf("button", "input_button") -> "Press"
                domain in listOf("script", "scene") -> "Turn On"
                else -> "Default Action"
            }
        }
    }
}

/**
 * Checks if a specific action matches what would be the default action
 * for this entity type and press type
 */
private fun isDefaultActionForEntity(
    action: EntityAction?,
    entity: EntityItem,
    pressType: PressType
): Boolean {
    if (action == null || action is EntityAction.Default) return true

    val domain = entity.id.substringBefore('.')
    val isSimpleLight = if (domain == "light") isSimpleLight(entity) else false

    // Check if this specific action matches what would be the default
    return when (pressType) {
        PressType.SINGLE -> when {
            // For single press on fan, default is EXPAND
            domain == "fan" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.EXPAND -> true

            // For single press on climate, default is EXPAND
            domain == "climate" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.EXPAND -> true

            // For single press on media_player, default is EXPAND
            domain == "media_player" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.EXPAND -> true

            // For single press on simple lights, default is toggle
            domain == "light" && isSimpleLight &&
                    action is EntityAction.ServiceCall &&
                    action.domain == "light" &&
                    action.service == "toggle" -> true

            // Add other entity types as needed
            domain == "cover" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.EXPAND -> true

            domain == "lock" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.EXPAND -> true

            domain == "light" && !isSimpleLight && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.EXPAND -> true

            // For camera, default is PIP
            domain == "camera" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.CAMERA_PIP -> true

            else -> false
        }

        PressType.LONG -> when {
            // For long press on fan, default is toggle
            domain == "fan" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.FAN_TOGGLE_WITH_MEMORY -> true

            // For long press on climate, default is toggle
            domain == "climate" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.CLIMATE_TOGGLE_WITH_MEMORY -> true

            // For long press on media_player, default is toggle
            domain == "media_player" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.MEDIA_PLAYER_TOGGLE -> true

            // For long press on cover, default is toggle
            domain == "cover" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.COVER_TOGGLE -> true

            // For long press on lock, default is toggle
            domain == "lock" && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.LOCK_TOGGLE -> true

            // For long press on lights (non-simple), default is toggle
            domain == "light" && !isSimpleLight && action is EntityAction.BuiltIn &&
                    action.type == EntityAction.Special.LIGHT_TOGGLE -> true

            else -> false
        }

        else -> false
    }
}


private enum class Choice { DEFAULT, EXPAND, CONTROL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionConfigDialog(
    entity: EntityItem,
    pressType: PressType,
    currentAction: EntityAction?,
    savedEntitiesManager: SavedEntitiesManager,
    onDismiss: () -> Unit,
    onActionSelected: (PressType, EntityAction) -> Unit   // <-- returns an action
) {
    // Initial choice from currentAction
    val initialChoice = when {
        // If action is null or explicitly Default, show DEFAULT
        currentAction == null || currentAction is EntityAction.Default -> Choice.DEFAULT

        // If the action matches what would be the default for this entity type,
        // also show DEFAULT option as selected
        isDefaultActionForEntity(currentAction, entity, pressType) -> Choice.DEFAULT

        // Otherwise, use standard logic
        currentAction is EntityAction.BuiltIn &&
                currentAction.type == EntityAction.Special.EXPAND -> Choice.EXPAND

        currentAction is EntityAction.ControlEntity ||
                currentAction is EntityAction.ServiceCall -> Choice.CONTROL

        else -> Choice.DEFAULT
    }

    var choice by remember { mutableStateOf(initialChoice) }
    var selectedEntityId by remember {
        mutableStateOf((currentAction as? EntityAction.ControlEntity)?.targetId ?: "")
    }

    // Model for picker
    data class EntityWithIcon(
        val id: String,
        val displayName: String,
        val iconResId: Int
    )

    val entitiesWithIcons = remember {
        savedEntitiesManager.loadEntities()
            .filter { e ->
                val d = e.id.substringBefore('.')
                d in listOf(
                    "light","switch","input_boolean","button","input_button",
                    "script","scene","climate","fan","cover","lock",
                    "alarm_control_panel","media_player","automation","camera"
                )
            }
            .map { e ->
                val iconRes = when {
                    !e.customIconOnName.isNullOrEmpty() -> {
                        val resId = EntityIconMapper.getResourceIdByName(e.customIconOnName)
                        resId ?: EntityIconMapper.getDefaultOnIconForEntity(e.id)
                    }
                    else -> EntityIconMapper.getDefaultOnIconForEntity(e.id)
                }
                val displayName = e.customName.ifBlank { e.friendlyName }
                EntityWithIcon(e.id, displayName, iconRes)
            }
    }

    val canBeExpanded = remember {
        val d = entity.id.substringBefore('.')
        if (d == "light" && isSimpleLight(entity)) false
        else {
            d in listOf("climate", "fan", "cover", "lock", "alarm_control_panel", "light", "media_player")
        }
    }

    val pressTypeText = when (pressType) {
        PressType.SINGLE -> "Single Press"
        PressType.DOUBLE -> "Double Press"
        PressType.LONG   -> "Long Press"
    }

    val selectedEntityInfo = remember(selectedEntityId) {
        entitiesWithIcons.find { it.id == selectedEntityId }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Configure $pressTypeText", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                // DEFAULT
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = choice == Choice.DEFAULT,
                        onClick = { choice = Choice.DEFAULT }
                    )
                    Column {
                        Text("Use Default Action", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            getDefaultActionDescription(entity, pressType),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // EXPAND (if applicable)
                if (canBeExpanded) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = choice == Choice.EXPAND,
                            onClick = { choice = Choice.EXPAND }
                        )
                        Text("Expand Card", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                Spacer(Modifier.height(16.dp))

                // CONTROL ENTITY
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = choice == Choice.CONTROL,
                        onClick = { choice = Choice.CONTROL }
                    )
                    Column {
                        Text("Control Entity", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Choose an entity to toggle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                AnimatedVisibility(visible = choice == Choice.CONTROL) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text("Select Entity", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))

                        var showEntityPicker by remember { mutableStateOf(false) }

                        OutlinedTextField(
                            value = if (selectedEntityId.isNotBlank()) {
                                "${selectedEntityInfo?.displayName} ($selectedEntityId)"
                            } else {
                                "Select an entity"
                            },
                            onValueChange = {},
                            readOnly = true,
                            enabled = choice == Choice.CONTROL,
                            leadingIcon = {
                                selectedEntityInfo?.let { info ->
                                    Image(
                                        painter = painterResource(id = info.iconResId),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        colorFilter = ColorFilter.tint(
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showEntityPicker) Icons.Default.ArrowDropUp
                                    else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = choice == Choice.CONTROL) {
                                    showEntityPicker = true
                                }
                        )

                        if (showEntityPicker) {
                            Dialog(onDismissRequest = { showEntityPicker = false }) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    tonalElevation = 6.dp,
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .fillMaxHeight(0.6f)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(
                                            "Select Entity",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        LazyColumn {
                                            items(entitiesWithIcons) { info ->
                                                val interaction = remember { MutableInteractionSource() }
                                                val focused by interaction.collectIsFocusedAsState()

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .border(
                                                            width = if (focused) 3.dp else 1.dp,
                                                            color = if (focused) Color.White else MaterialTheme.colorScheme.outline,
                                                            shape = RoundedCornerShape(14.dp)
                                                        )
                                                        .background(
                                                            if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                                            else Color.Transparent
                                                        )
                                                        .focusable(interactionSource = interaction)
                                                        .clickable(
                                                            interactionSource = interaction,
                                                            indication = null
                                                        ) {
                                                            selectedEntityId = info.id
                                                            showEntityPicker = false
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Image(
                                                            painter = painterResource(id = info.iconResId),
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .padding(end = 12.dp),
                                                            colorFilter = ColorFilter.tint(
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        )
                                                        Text(
                                                            "${info.displayName} (${info.id})",
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("CANCEL", style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val chosenAction: EntityAction = when (choice) {
                                Choice.DEFAULT -> EntityAction.Default
                                Choice.EXPAND  -> EntityAction.BuiltIn(EntityAction.Special.EXPAND)
                                Choice.CONTROL -> {
                                    if (selectedEntityId.isNotBlank()) {
                                        EntityAction.ControlEntity(selectedEntityId)
                                    } else {
                                        EntityAction.Default
                                    }
                                }
                            }
                            onActionSelected(pressType, chosenAction)
                        },
                        enabled = when (choice) {
                            Choice.DEFAULT, Choice.EXPAND -> true
                            Choice.CONTROL -> selectedEntityId.isNotBlank()
                        }
                    ) {
                        Text("SAVE", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun ClickActionRow(
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    var buttonFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.onFocusChanged { buttonFocused = it.isFocused },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(
                width = if (buttonFocused) 2.dp else 1.dp,
                color = if (buttonFocused)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            )
        ) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium // Smaller text
            )
        }
    }
}

private enum class DefaultAction { EXPAND, TOGGLE, PRESS, TURN_ON, NONE, CAMERA_PIP, TRIGGER }

private fun defaultActionFor(entity: EntityItem, pressType: PressType): DefaultAction {
    val domain = entity.id.substringBefore('.')
    val isSimpleLight = entity.lastKnownState["is_simple_light"] as? Boolean ?: false

    if (domain == "automation") {
        // For long press, always return NONE
        if (pressType == PressType.LONG) {
            return DefaultAction.NONE
        }

        // For single press, use the user's preference
        val action = entity.lastKnownState["automation_action"] as? String ?: "trigger"
        return if (action == "trigger") DefaultAction.TRIGGER else DefaultAction.TOGGLE
    }

    return when (pressType) {
        PressType.SINGLE -> when {
            domain == "camera" -> DefaultAction.CAMERA_PIP
            domain == "automation" -> DefaultAction.TRIGGER
            domain == "light" && isSimpleLight -> DefaultAction.TOGGLE
            domain in listOf("climate", "fan", "cover", "lock", "alarm_control_panel", "light", "media_player") -> DefaultAction.EXPAND
            domain in listOf("switch", "input_boolean") -> DefaultAction.TOGGLE
            domain in listOf("button", "input_button") -> DefaultAction.PRESS
            domain in listOf("script", "scene") -> DefaultAction.TURN_ON
            else -> DefaultAction.NONE
        }
        PressType.LONG -> when {
            domain == "camera" -> DefaultAction.NONE
            domain == "light" && isSimpleLight -> DefaultAction.NONE
            domain == "climate" -> DefaultAction.TOGGLE
            domain == "fan"     -> DefaultAction.TOGGLE
            domain == "cover"   -> DefaultAction.TOGGLE
            domain == "lock"    -> DefaultAction.TOGGLE
            domain == "light"   -> DefaultAction.TOGGLE
            domain == "media_player"   -> DefaultAction.TOGGLE
            domain == "alarm_control_panel" -> DefaultAction.NONE
            domain in listOf("switch","input_boolean","button","input_button","script","scene") -> DefaultAction.NONE
            else -> DefaultAction.NONE
        }
        PressType.DOUBLE -> DefaultAction.NONE
    }
}

@Composable
fun EntitySpecificSettingsCard(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // Match app style
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
                .animateContentSize()
        ) {
            // Title
            Text(
                text = "Entity Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dynamically generate entity-specific settings
            EntitySpecificOptions(
                entity = entity,
                onEntityChanged = onEntityChanged
            )
        }
    }
}

@Composable
fun EntitySpecificOptions(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    val entityType = entity.id.split(".").firstOrNull() ?: ""

    when (entityType) {
        "climate" -> ClimateEntityOptions(
            entity = entity,
            onEntityChanged = onEntityChanged
        )

        "light" -> LightEntityOptions(
            entity = entity,
            onEntityChanged = onEntityChanged
        )

        /*
        "cover" -> CoverEntityOptions(
            entity = entity,
            onEntityChanged = onEntityChanged
        )
         */

        "fan" -> FanEntityOptions(
            entity = entity,
            onEntityChanged = onEntityChanged
        )

        "camera" -> CameraEntityOptions(
            entity = entity,
            onEntityChanged = onEntityChanged
        )

        "automation" -> AutomationEntityOptions(entity = entity, onEntityChanged = onEntityChanged) // Add this

        else -> {
            Text(
                "No special settings available for this entity type.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ClimateEntityOptions(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    val attributes = entity.attributes ?: JSONObject()
    // Check if entity supports these features
    val hasTemperatureSensor = attributes.has("current_temperature")

    val hasModes = try {
        val modesJson = attributes.optJSONArray("hvac_modes")
        val modesString = attributes.optString("hvac_modes", "")

        if (modesJson != null && modesJson.length() > 1) {
            true
        } else if (modesString.contains("[") && modesString.contains("]")) {
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }

    val hasFanModes = try {
        val fanModesJson = attributes.optJSONArray("fan_modes")
        val fanModesString = attributes.optString("fan_modes", "")

        if (fanModesJson != null && fanModesJson.length() > 0) {
            true
        } else if (fanModesString.contains("[") && fanModesString.contains("]")) {
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }

    // Get settings from lastKnownState or use defaults
    val options = entity.lastKnownState.toMutableMap()

    var showRoomTemp by remember {
        mutableStateOf(options["show_room_temp"] as? Boolean ?: hasTemperatureSensor)
    }

    var showModeControls by remember {
        mutableStateOf(options["show_mode_controls"] as? Boolean ?: hasModes)
    }

    var showFanControls by remember {
        mutableStateOf(options["show_fan_controls"] as? Boolean ?: hasFanModes)
    }

    // Always remember temperature setting - hard-coded to true as requested
    val rememberTemp = true

    Column {
        // Only show temperature sensor option if supported
        if (hasTemperatureSensor) {
            SwitchRow(
                title = "Show Room Temperature",
                checked = showRoomTemp,
                onCheckedChange = {
                    showRoomTemp = it
                    options["show_room_temp"] = it
                    onEntityChanged(entity.copy(lastKnownState = options))
                }
            )
        }

        // Only show mode controls option if supported
        if (hasModes) {
            SwitchRow(
                title = "Show Mode Controls",
                checked = showModeControls,
                onCheckedChange = {
                    showModeControls = it
                    options["show_mode_controls"] = it
                    onEntityChanged(entity.copy(lastKnownState = options))
                }
            )
        }

        // Only show fan controls option if supported
        if (hasFanModes) {
            SwitchRow(
                title = "Show Fan Controls",
                checked = showFanControls,
                onCheckedChange = {
                    showFanControls = it
                    options["show_fan_controls"] = it
                    onEntityChanged(entity.copy(lastKnownState = options))
                }
            )
        }

        // Information text about temperature settings
        Text(
            text = "Temperature settings are always remembered.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
    }
}


@Composable
fun CameraEntityOptions(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    val options = entity.lastKnownState.toMutableMap()

    // PIP Corner options
    val cornerOptions = listOf("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT")
    val cornerLabels = listOf("Top Left", "Top Right", "Bottom Left", "Bottom Right")
    var selectedCorner by remember {
        mutableStateOf(options["pip_corner"] as? String ?: "TOP_LEFT")
    }

    // PIP Size options
    val sizeOptions = listOf("SMALL", "MEDIUM", "LARGE")
    val sizeLabels = listOf("Small", "Medium", "Large")
    var selectedSize by remember {
        mutableStateOf(options["pip_size"] as? String ?: "MEDIUM")
    }

    // Show title option
    var showTitle by remember {
        mutableStateOf(options["show_title"] as? Boolean ?: true)
    }

    // Auto-hide timeout options
    val timeoutOptions = listOf(15, 30, 60, 300, 0) // 0 means never
    val timeoutLabels = listOf("15 seconds", "30 seconds", "1 minute", "5 minutes", "Never")
    var selectedTimeout by remember {
        mutableIntStateOf((options["auto_hide_timeout"] as? Number)?.toInt() ?: 30)
    }

    // Track focus state for each button group
    var focusedCornerIndex by remember { mutableStateOf(-1) }
    var focusedSizeIndex by remember { mutableStateOf(-1) }
    var focusedTimeoutIndex by remember { mutableStateOf(-1) }

    var cameraAliasText by remember(entity.id, entity.cameraAlias) {
        mutableStateOf(entity.cameraAlias ?: "")
    }

    val context = LocalContext.current
    val isPersistentEnabled = remember {
        AppPrefs.isPersistentConnectionEnabled(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Position
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Position",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                cornerOptions.forEachIndexed { index, corner ->
                    val isSelected = selectedCorner == corner
                    val isFocused = focusedCornerIndex == index

                    OutlinedButton(
                        onClick = {
                            selectedCorner = corner
                            options["pip_corner"] = corner
                            onEntityChanged(entity.copy(lastKnownState = options))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged {
                                focusedCornerIndex = if (it.isFocused) index else -1
                            },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surface,
                            contentColor = if (isSelected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            width = if (isFocused) 3.dp else 1.dp,
                            color = when {
                                isFocused -> Color.White
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                    ) {
                        Text(
                            text = cornerLabels[index],
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Size
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Size",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sizeOptions.forEachIndexed { index, size ->
                    val isSelected = selectedSize == size
                    val isFocused = focusedSizeIndex == index

                    OutlinedButton(
                        onClick = {
                            selectedSize = size
                            options["pip_size"] = size
                            onEntityChanged(entity.copy(lastKnownState = options))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged {
                                focusedSizeIndex = if (it.isFocused) index else -1
                            },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surface,
                            contentColor = if (isSelected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            width = if (isFocused) 3.dp else 1.dp,
                            color = when {
                                isFocused -> Color.White
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                    ) {
                        Text(
                            text = sizeLabels[index],
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Auto-hide Timeout
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Auto-hide Timeout",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                timeoutOptions.forEachIndexed { index, timeout ->
                    val isSelected = selectedTimeout == timeout
                    val isFocused = focusedTimeoutIndex == index

                    OutlinedButton(
                        onClick = {
                            selectedTimeout = timeout
                            options["auto_hide_timeout"] = timeout
                            onEntityChanged(entity.copy(lastKnownState = options))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged {
                                focusedTimeoutIndex = if (it.isFocused) index else -1
                            },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surface,
                            contentColor = if (isSelected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            width = if (isFocused) 3.dp else 1.dp,
                            color = when {
                                isFocused -> Color.White
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                    ) {
                        Text(
                            text = timeoutLabels[index],
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Show Title toggle - Keep existing code
        SwitchRow(
            title = "Show Camera Title",
            checked = showTitle,
            onCheckedChange = {
                showTitle = it
                options["show_title"] = it
                onEntityChanged(entity.copy(lastKnownState = options))
            },
            compact = true
        )

        CameraAliasEditorRow(
            value = cameraAliasText,
            enabled = isPersistentEnabled,
            onCommit = { alias ->
                cameraAliasText = alias
                onEntityChanged(
                    entity.copy(
                        cameraAlias = alias,
                        lastKnownState = entity.lastKnownState.toMutableMap(),
                        pressTargets = entity.pressTargets.toMutableMap(),
                        pressActions = entity.pressActions.toMutableMap()
                    )
                )
            }
        )

        // Explanation text - Keep existing code
        Text(
            text = "These settings control how the camera appears in picture-in-picture mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}


@Composable
private fun CameraAliasEditorRow(
    value: String,
    enabled: Boolean,
    onCommit: (String) -> Unit
) {
    val activatorFocus = remember { FocusRequester() }
    var showDialog by remember { mutableStateOf(false) }

    var buttonFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(activatorFocus),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Camera Alias", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(
                if (enabled) (value.ifBlank { "None" }) else "Enable persistent connection to use camera alias",
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
        }
        OutlinedButton(
            enabled = enabled,
            onClick = { showDialog = true },
            modifier = Modifier.onFocusChanged { buttonFocused = it.isFocused },
            border = BorderStroke(
                width = if (buttonFocused) 2.dp else 1.dp,
                color = if (buttonFocused)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            )
        ) { Text("Edit") }
    }

    if (showDialog) {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val tfFocus = remember { FocusRequester() }

        val okFocus = remember { FocusRequester() }
        val cancelFocus = remember { FocusRequester() }

        var text by remember { mutableStateOf(value) }

        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0

        LaunchedEffect(Unit) {
            // small delay helps some OEM IMEs attach; then focus + show keyboard
            tfFocus.requestFocus()
            keyboard?.show()
        }

        LaunchedEffect(imeVisible) {
            if (!imeVisible) {
                okFocus.requestFocus()
            }
        }

        fun confirmAndClose() {
            keyboard?.hide()
            onCommit(text.trim())
            showDialog = false
            activatorFocus.requestFocus()
        }

        fun cancelAndClose() {
            keyboard?.hide()
            showDialog = false
            activatorFocus.requestFocus()
        }

        AlertDialog(
            onDismissRequest = {
                // Treat outside/back dismiss the same as Cancel
                cancelAndClose()
            },
            title = { Text("Edit Camera Alias") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Camera Alias") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { confirmAndClose() } // OK on IME Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(tfFocus)
                        .onPreviewKeyEvent { ke ->
                            if (ke.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                            when (ke.key) {
                                Key.DirectionDown, Key.Tab -> {
                                    okFocus.requestFocus()
                                    true
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    confirmAndClose()
                                    true
                                }
                                Key.Back -> {
                                    // First BACK hides IME if visible; keep dialog open
                                    if (imeVisible) {
                                        keyboard?.hide()
                                        okFocus.requestFocus()
                                        true
                                    } else {
                                        // No IME: behave like Cancel
                                        cancelAndClose()
                                        true
                                    }
                                }
                                else -> false
                            }
                        }
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.focusRequester(okFocus),
                    onClick = { confirmAndClose() }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.focusRequester(cancelFocus),
                    onClick = { cancelAndClose() }
                ) { Text("Cancel") }
            }
        )
    }
}


@Composable
fun AutomationEntityOptions(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    val options = entity.lastKnownState.toMutableMap()

    // Default action is "trigger"
    var actionType by remember {
        mutableStateOf(options["automation_action"] as? String ?: "trigger")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Action Type",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )

        // Option 1: Trigger Action (default)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = actionType == "trigger",
                onClick = {
                    if (actionType != "trigger") {
                        actionType = "trigger"
                        options["automation_action"] = "trigger"

                        // ONLY update the preference, not any actions
                        onEntityChanged(entity.copy(lastKnownState = options))
                    }
                }
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = "Trigger Automation Actions",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Run the automation's actions regardless of whether the automation is enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Option 2: Toggle Automation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = actionType == "toggle",
                onClick = {
                    if (actionType != "toggle") {
                        actionType = "toggle"
                        options["automation_action"] = "toggle"

                        // ONLY update the preference, not any actions
                        onEntityChanged(entity.copy(lastKnownState = options))
                    }
                }
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = "Toggle Automation Triggers",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Enable or disable the automation's trigger system",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This setting determines how this automation behaves whenever it's used - in QuickBars, trigger keys, or as actions for other entities.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun LightEntityOptions(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    val attributes = entity.attributes ?: JSONObject()
    val options = entity.lastKnownState.toMutableMap()

    // Use saved values or get defaults from capabilities
    val caps = remember(entity.id, entity.attributes, entity.lastKnownState) {
        computeLightCaps(entity.attributes, entity.lastKnownState)
    }
    LaunchedEffect(caps) {
        Log.d("LightCaps", "${entity.id} -> simple=${caps.isSimple}, " +
                "brightness=${caps.brightness}, color=${caps.color}, temp=${caps.colorTemp}")
    }

    // Only show options for non-simple lights
    if (caps.isSimple) {
        Text(
            "This is a simple on/off light with no brightness or color capabilities.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    var showBrightnessControls by remember {
        mutableStateOf(options["show_brightness_controls"] as? Boolean ?: caps.brightness)
    }
    var showWarmthControls by remember {
        mutableStateOf(options["show_warmth_controls"] as? Boolean ?: caps.colorTemp)
    }
    var showColorControls by remember {
        mutableStateOf(options["show_color_controls"] as? Boolean ?: caps.color)
    }

    Column {
        if (caps.brightness) {
            SwitchRow(
                title = "Show Brightness Controls",
                checked = showBrightnessControls,
                onCheckedChange = {
                    showBrightnessControls = it
                    options["show_brightness_controls"] = it
                    options.remove("show_brightness_slider") // legacy key cleanup
                    onEntityChanged(entity.copy(lastKnownState = options))
                }
            )
        }
        if (caps.colorTemp) {
            SwitchRow(
                title = "Show Warmth Controls",
                checked = showWarmthControls,
                onCheckedChange = {
                    showWarmthControls = it
                    options["show_warmth_controls"] = it
                    onEntityChanged(entity.copy(lastKnownState = options))
                }
            )
        }
        if (caps.color) {
            SwitchRow(
                title = "Show Color Controls",
                checked = showColorControls,
                onCheckedChange = {
                    showColorControls = it
                    options["show_color_controls"] = it
                    onEntityChanged(entity.copy(lastKnownState = options))
                }
            )
        }
    }
}

@Composable
fun FanEntityOptions(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    val options = entity.lastKnownState.toMutableMap()

    var customStepEnabled by remember {
        mutableStateOf(options["custom_step_enabled"] as? Boolean ?: false)
    }

    var customStepPercentage by remember {
        mutableStateOf((options["custom_step_percentage"] as? Number)?.toInt() ?: 0)
    }

    var sliderIsFocused by remember { mutableStateOf(false) }
    var sliderIsPressed by remember { mutableStateOf(false) }

    Column {
        SwitchRow(
            title = "Use Custom Step Percentage",
            checked = customStepEnabled,
            onCheckedChange = {
                customStepEnabled = it
                options["custom_step_enabled"] = it
                onEntityChanged(entity.copy(lastKnownState = options))
            }
        )

        // Only show slider if custom step is enabled
        AnimatedVisibility(visible = customStepEnabled) {
            Column {
                // Show Custom Step Percentage option
                Text(
                    text = "Custom Step Percentage: ${if(customStepPercentage > 0) "$customStepPercentage%" else "Default"}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Improved slider with better focus handling
                Slider(
                    value = customStepPercentage.toFloat(),
                    onValueChange = { newValue: Float ->
                        customStepPercentage = newValue.toInt()
                        options["custom_step_percentage"] = customStepPercentage
                        onEntityChanged(entity.copy(lastKnownState = options))
                        sliderIsPressed = true
                    },
                    onValueChangeFinished = {
                        sliderIsPressed = false
                    },
                    valueRange = 0f..50f,
                    steps = 50,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .onFocusChanged {
                            sliderIsFocused = it.isFocused
                        }
                        .onKeyEvent { keyEvent ->
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    if (customStepPercentage > 0) {
                                        customStepPercentage = (customStepPercentage - 1).coerceAtLeast(0)
                                        options["custom_step_percentage"] = customStepPercentage
                                        onEntityChanged(entity.copy(lastKnownState = options))
                                        true
                                    } else false
                                }
                                Key.DirectionRight -> {
                                    if (customStepPercentage < 50) {
                                        customStepPercentage = (customStepPercentage + 1).coerceAtMost(50)
                                        options["custom_step_percentage"] = customStepPercentage
                                        onEntityChanged(entity.copy(lastKnownState = options))
                                        true
                                    } else false
                                }
                                else -> false
                            }
                        }
                        .focusable(),
                    colors = SliderDefaults.colors(
                        thumbColor = when {
                            sliderIsPressed -> colorResource(id=R.color.md_theme_onPrimary)
                            sliderIsFocused -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        },
                        activeTrackColor = when {
                            sliderIsPressed -> MaterialTheme.colorScheme.primary
                            sliderIsFocused -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        },
                        inactiveTrackColor = when {
                            sliderIsPressed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            sliderIsFocused -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                )

                Text(
                    text = "Set a custom step percentage for your fan's '+' and '-' button press. Default is 25%.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // Fan settings are always remembered
        Text(
            text = "Fan speed settings are always remembered.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
    }
}

/*
@Composable
fun CoverEntityOptions(
    entity: EntityItem,
    onEntityChanged: (EntityItem) -> Unit
) {
    val options = entity.lastKnownState.toMutableMap()
    var showPositionSlider by remember {
        mutableStateOf(options["show_position_slider"] as? Boolean ?: true)
    }

    Column {
        SwitchRow(
            title = "Show Position Slider",
            checked = showPositionSlider,
            onCheckedChange = {
                showPositionSlider = it
                options["show_position_slider"] = it
                onEntityChanged(entity.copy(lastKnownState = options))
            }
        )

        // Remember Position
        val rememberPosition by remember {
            mutableStateOf(options["remember_position"] as? Boolean ?: true)
        }

        SwitchRow(
            title = "Remember Position Setting",
            checked = rememberPosition,
            onCheckedChange = {
                options["remember_position"] = it
                onEntityChanged(entity.copy(lastKnownState = options))
            }
        )
    }
}

 */

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    compact: Boolean = false
) {
    var switchFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 8.dp), // Smaller padding in compact mode
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.onFocusChanged { switchFocused = it.isFocused },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                checkedBorderColor = if (switchFocused)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = if (switchFocused)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            )
        )
    }
}
