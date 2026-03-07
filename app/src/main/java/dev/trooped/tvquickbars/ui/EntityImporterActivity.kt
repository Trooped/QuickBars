package dev.trooped.tvquickbars.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.CategoryItem
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ha.ConnectionState
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.ha.HomeAssistantListener
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.utils.EntityUsageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject


val LocalActivity = compositionLocalOf<ComponentActivity> {
    error("No Activity provided")
}

//region ==================== The New Activity (Very Simple) ====================
class EntityImporterActivity : BaseActivity(), HomeAssistantListener {

    private val viewModel by viewModels<EntityImporterViewModel> {
        EntityImporterViewModelFactory(SavedEntitiesManager(application), application)
    }

    private var haClient: HomeAssistantClient? = null     // <-- move here

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isFirstTimeSetup = intent.getBooleanExtra("FIRST_TIME_SETUP", false) || AppPrefs.isFirstTimeSetupInProgress(this)
        viewModel.setFirstTimeSetup(isFirstTimeSetup)

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
                ){
                    EntityImporterScreen(
                        viewModel          = viewModel,
                        onNavigateToMain   = {
                            AppPrefs.setFirstTimeSetupInProgress(this, false)
                            startActivity(Intent(this, ComposeMainActivity::class.java))
                            finish()
                        },
                        onNavigateBack     = {
                            AppPrefs.setFirstTimeSetupInProgress(this, false)
                            finish()
                        },
                        onRetryConnection  = { connectToHomeAssistant() }
                    )
                }
            }
        }
        // first connection is done right away
        connectToHomeAssistant()
    }

    override fun onEntitiesFetched(list: List<CategoryItem>) =
        viewModel.onEntitiesFetched(list)

    override fun onEntityStateChanged(id: String, state: String) = Unit
    override fun onEntityStateUpdated(id: String, state: String, attr: JSONObject) = Unit

    /* 2️⃣ identical connect code but listener = this (the Activity) */
    fun connectToHomeAssistant() {
        val url = SecurePrefsManager.getHAUrl(this) ?: return
        val token = SecurePrefsManager.getHAToken(this) ?: return
        haClient?.disconnect()
        haClient = HomeAssistantClient(url, token, this).also { it.connect() }
    }

    override fun onDestroy() {
        haClient?.disconnect()
        super.onDestroy()
    }
}
//endregion

//region ==================== UI State & ViewModel ====================
sealed interface ImporterUiState {
    object Loading : ImporterUiState
    data class Error(val title: String, val message: String) : ImporterUiState
    data class Success(val items: List<Any>) : ImporterUiState
}

class EntityImporterViewModel(
    application: Application,
    private val savedEntitiesManager: SavedEntitiesManager
) : AndroidViewModel(application), HomeAssistantListener {

    private var haClient: HomeAssistantClient? = null
    private val _isFirstTimeSetup = MutableStateFlow(false)
    val isFirstTimeSetup: StateFlow<Boolean> = _isFirstTimeSetup.asStateFlow()
    private var isBackConfirmationNeeded = true

    private val _uiState = MutableStateFlow<ImporterUiState>(ImporterUiState.Loading)
    val uiState: StateFlow<ImporterUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var allCategories = listOf<CategoryItem>()
    private var savedEntities = mutableListOf<EntityItem>()

    private var originalSortedCategories = listOf<CategoryItem>()


    val _showDialog = MutableStateFlow<DialogState?>(null)
    val showDialog: StateFlow<DialogState?> = _showDialog.asStateFlow()

    sealed interface DialogState {
        data class RemovalConfirmation(val entityId: String, val friendlyName: String, val message: String) : DialogState
        object FirstTimeProceed : DialogState
        object FirstTimeBack : DialogState
        object FirstTimeSetupChoice : DialogState
    }


    override fun onCleared() {
        super.onCleared()
        haClient?.disconnect()
    }

    fun setFirstTimeSetup(isFirstTime: Boolean) {
        _isFirstTimeSetup.value = isFirstTime

        // Show initial setup dialog automatically if this is first time setup
        if (isFirstTime) {
            _showDialog.value = DialogState.FirstTimeSetupChoice
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterAndBuildList()
    }

    override fun onEntityStateUpdated(entityId: String, newState: String, attributes: JSONObject) {
        // This screen doesn't need to handle live state updates,
        // but we must implement all interface methods
    }

    override fun onEntitiesFetched(categories: List<CategoryItem>) {
        try {
            originalSortedCategories = emptyList()

            savedEntities = savedEntitiesManager.loadEntities()
            val savedEntityIds = savedEntities.map { it.id }.toSet()
            val allowedCategoryNames =
                setOf("light", "switch", "button", "fan", "input_boolean","input_button", "script", "scene", "climate",
                    "cover", "sensor", "binary_sensor", "lock", "alarm_control_panel", "camera", "automation", "media_player")

            allCategories = categories
                .filter { allowedCategoryNames.contains(it.name.lowercase()) }
                .onEach { category ->
                    category.entities.forEach { entity ->
                        entity.category = category.name.lowercase()
                        if (savedEntityIds.contains(entity.id)) {
                            entity.isSelected = true
                            entity.isAvailable = true
                        }
                    }
                    category.entities = category.entities.sortedByDescending { it.isSelected }
                }
                .sortedWith(
                    compareByDescending<CategoryItem> { it.entities.any { e -> e.isSelected } }
                        .thenBy { it.name }
                )

            // Get a map of all received entity IDs
            val receivedEntityIds = allCategories.flatMap { it.entities }.map { it.id }.toSet()

            // Find missing entities (saved but not received from HA)
            val missingEntities = savedEntities.filter { saved ->
                saved.isSelected && saved.id !in receivedEntityIds
            }

            // Add missing entities to their respective categories
            if (missingEntities.isNotEmpty()) {
                // Group missing entities by domain
                val missingByDomain = missingEntities.groupBy { it.id.split('.').first() }

                // Add missing entities to existing categories or create new ones
                missingByDomain.forEach { (domain, entities) ->
                    // Find or create category
                    val category = allCategories.find { it.name.equals(domain, ignoreCase = true) }
                        ?: CategoryItem(domain.uppercase(), mutableListOf()).also {
                            if (allowedCategoryNames.contains(domain.lowercase())) {
                                allCategories = allCategories + it
                            }
                        }

                    // Add missing entities to the category
                    if (allowedCategoryNames.contains(domain.lowercase())) {
                        entities.forEach { entity ->
                            entity.isAvailable = false // Mark as unavailable
                            entity.isSelected = true // Keep it selected

                            // Only add if not already in the category
                            if (category.entities.none { it.id == entity.id }) {
                                val mutableEntities = category.entities.toMutableList()
                                mutableEntities.add(entity)
                                category.entities = mutableEntities
                            }
                        }
                    }
                }
            }

            // Update the UI state
            filterAndBuildList()

            // Log the result for debugging
            Log.d("EntityImporter", "Processed ${allCategories.size} categories with ${allCategories.sumOf { it.entities.size }} entities")
        } catch (e: Exception) {
            // Catch and log any exceptions to prevent crashes
            Log.e("EntityImporter", "Error processing entities", e)
            _uiState.value = ImporterUiState.Error(
                "Processing Error",
                "An error occurred while processing entities: ${e.message}"
            )
        }
    }

    fun onEntityChecked(entity: EntityItem, isSelected: Boolean, updateUi: Boolean = true) {
        /*──────────────────────── 1.   Build a fresh instance  ────────────────────────*/
        val updated = entity.copy(isSelected = isSelected)

        /*──────────────────────── 2.   Replace it inside our category map ─────────────*/
        allCategories.forEach { cat ->
            val idx = cat.entities.indexOfFirst { it.id == updated.id }
            if (idx != -1) {
                val mutableEntities = cat.entities.toMutableList()
                mutableEntities[idx] = updated
                cat.entities = mutableEntities
            }
        }

        if (isSelected) {
            /*──────── 2-a  add to the saved-entities library if it isn’t there ───────*/
            val isActionable = updated.id.startsWith("light.") ||
                    updated.id.startsWith("switch.") ||
                    updated.id.startsWith("input_boolean.") ||
                    updated.id.startsWith("fan.") ||
                    updated.id.startsWith("climate.") ||
                    updated.id.startsWith("script.") ||
                    updated.id.startsWith("button.") ||
                    updated.id.startsWith("input_button.") ||
                    updated.id.startsWith("media_player")

            if (savedEntities.none { it.id == updated.id }) {
                val newEntity = updated.copy(
                    isSaved         = true,
                    isActionable    = isActionable,
                    customName = if (updated.friendlyName.length > 20) updated.friendlyName.substring(0, 20) else updated.friendlyName,
                    customIconOnName = EntityIconMapper.getDefaultOnIconForEntityName(updated.id),
                    customIconOffName = if (isActionable)
                        EntityIconMapper.getDefaultOffIconForEntityName(updated.id)
                    else null
                )
                savedEntities.add(newEntity)
                savedEntitiesManager.saveEntity(newEntity)
            }
        } else {
            /*──────── 2-b  removal path (with QuickBar / Trigger-key safety) ─────────*/
            val (quickBars, triggerKeys) =
                EntityUsageUtils.getEntityUsageDetails(getApplication(), updated.id)

            if (quickBars.isNotEmpty() || triggerKeys.isNotEmpty()) {
                val msg = EntityUsageUtils.buildRemovalConfirmationMessage(
                    updated.friendlyName, quickBars, triggerKeys
                )
                _showDialog.value =
                    DialogState.RemovalConfirmation(updated.id, updated.friendlyName, msg)
            } else {
                removeEntity(updated.id)
            }
        }

        /*──────────────────────── 3.   Re-emit the list so Compose recomposes ─────────*/
        if (updateUi) {
            filterAndBuildList(preserveOrder = true)
        }
    }

    private fun updateCurrentListWithoutResorting() {
        // Get the current display list from the UI state
        val currentDisplayList = when (val state = _uiState.value) {
            is ImporterUiState.Success -> state.items
            else -> emptyList()
        }

        // Simply update the UI with the current list
        _uiState.value = ImporterUiState.Success(currentDisplayList)
    }

    fun onCategoryLongClicked(category: CategoryItem) {
        // If any entity is not selected, the action is to select all.
        // Otherwise, the action is to deselect all.
        val shouldSelectAll = category.entities.any { !it.isSelected }

        // Go through each entity and update its state, but don't refresh the UI yet
        category.entities.forEach { entity ->

            if (entity.isSelected != shouldSelectAll) {

                onEntityChecked(entity, shouldSelectAll, updateUi = false)

            }

        }

        // After processing all entities, refresh the UI once.
        filterAndBuildList(preserveOrder = true)
    }

    // CHANGE: Add the missing function from the HomeAssistantListener interface
    override fun onEntityStateChanged(entityId: String, newState: String) {
        // This screen does not need to handle live state updates.
    }

    fun resetBackConfirmation() {
        isBackConfirmationNeeded = false
    }

    fun isBackConfirmationNeeded(): Boolean {
        return isFirstTimeSetup.value && savedEntities.isEmpty() && isBackConfirmationNeeded
    }

    fun getErrorInfo(reason: ConnectionState.Reason): Pair<String, String> {
        return when (reason) {
            ConnectionState.Reason.CANNOT_RESOLVE_HOST -> Pair(
                "Host Not Found",
                "Could not find Home Assistant at the specified address. Please check:\n\n" +
                        "• Is the IP address correct?\n" +
                        "• Is Home Assistant running?\n" +
                        "• Are you connected to the same network?"
            )
            ConnectionState.Reason.AUTH_FAILED -> Pair(
                "Authentication Failed",
                "The token was rejected by Home Assistant. Please check your Long-Lived Access Token."
            )
            ConnectionState.Reason.BAD_TOKEN -> Pair(
                "Invalid Token Format",
                "The provided token appears to be invalid. Please make sure you're using a Long-Lived Access Token."
            )
            ConnectionState.Reason.SSL_HANDSHAKE -> Pair(
                "SSL Certificate Error",
                "There was a problem with the SSL certificate. Try using HTTP instead of HTTPS for local connections."
            )
            ConnectionState.Reason.TIMEOUT -> Pair(
                "Connection Timeout",
                "The connection attempt timed out. Please check if Home Assistant is running and accessible."
            )
            ConnectionState.Reason.NETWORK_IO -> Pair(
                "Network Error",
                "A network error occurred while connecting to Home Assistant. Please check your network connection."
            )
            ConnectionState.Reason.UNKNOWN -> Pair(
                "Unknown Error",
                "An unknown error occurred while connecting to Home Assistant. Please try again later."
            )

            ConnectionState.Reason.BAD_URL -> Pair(
                "Invalid URL",
                "The provided URL is invalid. Please check the format and try again."
            )
        }
    }

    // Update onConnectionError
    fun onConnectionError(reason: ConnectionState.Reason) {
        val (title, message) = getErrorInfo(reason)
        _uiState.value = ImporterUiState.Error(
            title,
            message + "\n\nTo update your settings, close this screen and select Settings from the main menu."
        )
    }

    private fun filterAndBuildList(preserveOrder: Boolean = false) {
        val query = _searchQuery.value

        val categoriesToDisplay = if (query.isBlank()) {
            allCategories
        } else {
            allCategories.mapNotNull { category ->
                val matchingEntities = category.entities.filter {
                    it.friendlyName.contains(query, ignoreCase = true) ||
                            it.id.contains(query, ignoreCase = true)
                }
                if (matchingEntities.isNotEmpty() || category.name.contains(query, ignoreCase = true)) {
                    val updatedCategory = category.copy(entities = matchingEntities)
                    updatedCategory.matchCount = matchingEntities.size
                    updatedCategory
                } else {
                    null
                }
            }
        }

        if (categoriesToDisplay.isEmpty() && allCategories.isNotEmpty()) {
            _uiState.value = ImporterUiState.Success(listOf("No results"))
        } else {
            val displayList = mutableListOf<Any>()
            categoriesToDisplay.forEach { category ->
                displayList.add(category)
                if (category.isExpanded) {
                    // This now simply adds the entities without re-sorting them
                    displayList.addAll(category.entities)
                }
            }
            _uiState.value = ImporterUiState.Success(displayList)
        }
    }

    fun onCategoryClicked(categoryId: String) {
        allCategories.find { it.name == categoryId }?.let {
            it.isExpanded = !it.isExpanded
        }
        filterAndBuildList(preserveOrder = true)
    }

    fun confirmEntityRemoval(entityId: String) {
        removeEntity(entityId)
        _showDialog.value = null
        filterAndBuildList()
    }

    fun cancelEntityRemoval(entityId: String) {
        allCategories.flatMap { it.entities }.find { it.id == entityId }?.isSelected = true
        _showDialog.value = null
        filterAndBuildList()
    }

    private fun removeEntity(entityId: String) {
        savedEntitiesManager.removeEntity(entityId)
        savedEntities.removeAll { it.id == entityId }
        allCategories.flatMap { it.entities }.find { it.id == entityId }?.isSelected = false
    }

    fun onContinueClicked() {
        if (_isFirstTimeSetup.value && savedEntities.isEmpty()) {
            _showDialog.value = DialogState.FirstTimeProceed
        } else {
            // Signal navigation
        }
    }

    fun dismissDialog() {
        _showDialog.value = null
    }
}

// CHANGE: Update the factory to pass the application context
class EntityImporterViewModelFactory(
    private val savedEntitiesManager: SavedEntitiesManager,
    private val application: Application
    // CHANGE: Removed the EntityUsageUtils parameter
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EntityImporterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EntityImporterViewModel(application, savedEntitiesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
//endregion

//region ==================== Composable UI ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityImporterScreen(
    viewModel: EntityImporterViewModel,
    onNavigateToMain: () -> Unit,
    onNavigateBack: () -> Unit,
    onRetryConnection: () -> Unit
) {
    LaunchedEffect(Unit) { onRetryConnection() }

    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isFirstTimeSetup by viewModel.isFirstTimeSetup.collectAsState()
    val dialogState by viewModel.showDialog.collectAsState()

    var continueButtonFocused by remember { mutableStateOf(false) }

    BackHandler(
        enabled = true // Keep this enabled
    ) {
        if (viewModel.isBackConfirmationNeeded()) {
            // Just call viewModel method, don't trigger another back press
            viewModel._showDialog.value = EntityImporterViewModel.DialogState.FirstTimeBack
        } else {
            onNavigateBack()
        }
    }

    val context = LocalContext.current
    // Dialog states
    when (val dialog = dialogState) {
        is EntityImporterViewModel.DialogState.RemovalConfirmation -> {
            AlertDialog(
                onDismissRequest = { viewModel.cancelEntityRemoval(dialog.entityId) },
                title = { Text("Remove Entity") },
                text = { Text(dialog.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmEntityRemoval(dialog.entityId) }) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelEntityRemoval(dialog.entityId) }) {
                        Text("Cancel")
                    }
                }
            )
        }

        is EntityImporterViewModel.DialogState.FirstTimeProceed -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text("No Entities Selected") },
                text = {
                    Text("You haven't selected any entities yet. Would you like to continue without selecting entities? You can always add them later from the Entities screen.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dismissDialog()
                        viewModel.resetBackConfirmation()
                        AppPrefs.setFirstTimeSetupInProgress(context, false)
                        onNavigateToMain()
                    }) {
                        Text("Continue to App")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text("Select Entities")
                    }
                }
            )
        }

        is EntityImporterViewModel.DialogState.FirstTimeBack -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text("Skip Entity Selection?") },
                text = {
                    Text("You haven't selected any entities yet. Would you like to continue without selecting entities? You can always add them later from the Entities menu.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dismissDialog()
                        viewModel.resetBackConfirmation()
                        AppPrefs.setFirstTimeSetupInProgress(context, false)
                        onNavigateToMain()
                    }) {
                        Text("Continue to App")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text("Stay Here")
                    }
                }
            )
        }

        is EntityImporterViewModel.DialogState.FirstTimeSetupChoice -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text("Welcome to QuickBars") },
                text = {
                    Column {
                        Text("Would you like to restore your settings from a backup or start fresh?")

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "If you've used QuickBars before and have a recent backup, you can restore your Entities, QuickBars, and Trigger Keys.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    val ctx = LocalContext.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left: Restore (no icon)
                        FocusableButton(
                            text = "Restore from Backup",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.dismissDialog()
                                val intent = Intent(ctx, BackupActivity::class.java)
                                intent.putExtra("restore_mode", true)
                                ctx.startActivity(intent)
                            }
                        )

                        // Right: Start Fresh (no icon)
                        FocusableButton(
                            text = "Start Fresh",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.dismissDialog() }
                        )
                    }
                },
                // Don't use dismissButton; we already rendered both actions above with equal sizing.
                dismissButton = {}
            )
        }

        null -> { /* No dialog to show */
        }
    }


    Surface(
        color = MaterialTheme.colorScheme.surface,  // Use the proper surface color
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(80.dp),
                    windowInsets = WindowInsets.statusBars,
                    title = {
                        Text(
                            text = if (isFirstTimeSetup) "Welcome to QuickBars for HA" else "Import Entities",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 26.dp)
                        )
                    },
                    navigationIcon = {
                        if (!isFirstTimeSetup) {
                            IconButton(
                                modifier = Modifier
                                    .padding(start = 24.dp, top = 20.dp)
                                    .onFocusChanged { isFocused ->
                                        // We'll use this in the border and elevation
                                        continueButtonFocused = isFocused.hasFocus
                                    },
                                onClick = onNavigateBack
                            )
                            {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        } else {
                            // Add continue button on the left for first-time setup
                            Button(
                                onClick = {
                                    if (viewModel.isBackConfirmationNeeded()) {
                                        viewModel.onContinueClicked()
                                    } else {
                                        onNavigateToMain()
                                    }
                                },
                                modifier = Modifier
                                    .padding(start = 24.dp, top = 26.dp)
                                    .onFocusChanged { isFocused ->
                                        // We'll use this in the border and elevation
                                        continueButtonFocused = isFocused.hasFocus
                                    },
                                // The Button's own shape, which the border will follow
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = if (continueButtonFocused) 8.dp else 2.dp
                                ),
                                // Use the Button's dedicated border parameter
                                border = if (continueButtonFocused) {
                                    BorderStroke(
                                        2.dp,
                                        colorResource(id = R.color.md_theme_onSurface)
                                    )
                                } else {
                                    null // Completely removes the border when not focused
                                }
                            ) {
                                Text("Continue")
                            }
                        }
                    },
                    actions = {
                        // Add search bar in actions
                        CollapsibleSearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier.padding(end = 24.dp, top = 20.dp)
                        )
                    }
                )
            }
        ) { paddingValues ->
            val context = LocalContext.current
            Column(Modifier.padding(paddingValues)) {
                // First-time setup instruction
                if (isFirstTimeSetup) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Select the entities you want to use in the app. You can always add more later from the Entities screen.\n" +
                                    "Long Click on any category to select/deselect all entities in it.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // --- Subtle Tip for Users ---
                    Text(
                        text = "Tip: Long-press a category to select/deselect all of it's entities.",
                        style = MaterialTheme.typography.bodySmall, // Smaller text
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp), // A little space
                        color = MaterialTheme.colorScheme.onSurfaceVariant // Less prominent color
                    )
                }

                // Compute when to block focus:
                val blockFocus = when (val s = uiState) {
                    is ImporterUiState.Loading -> true
                    is ImporterUiState.Error -> false // allow focusing the Retry button
                    is ImporterUiState.Success -> {
                        val hasRealList = s.items.isNotEmpty() && s.items.firstOrNull() !is String;
                        !hasRealList // block until the first real list is ready
                    }
                }

                // Wrap the whole content area so DPAD is soaked while loading
                FocusBlocker(enabled = blockFocus) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (val state = uiState) {
                            is ImporterUiState.Loading -> {
                                CircularProgressIndicator()
                            }

                            is ImporterUiState.Error -> {
                                EnhancedErrorView(state.title, state.message) {
                                    onRetryConnection()
                                }
                            }

                            is ImporterUiState.Success -> {
                                if (state.items.isEmpty()) {
                                    Text("No entities found.")
                                } else if (state.items.firstOrNull() is String) {
                                    Text(state.items.first() as String)
                                } else {
                                    EnhancedEntityList(
                                        items = state.items,
                                        onCategoryClick = { viewModel.onCategoryClicked(it.name) },
                                        onCategoryLongClick = { viewModel.onCategoryLongClicked(it) },
                                        onEntityCheckedChange = { entity, isChecked ->
                                            viewModel.onEntityChecked(entity, isChecked)
                                        },
                                        searchQuery = searchQuery
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

/**
 * A composable function that, when enabled, blocks focus and consumes DPAD/Enter key events
 * for its content. This is useful for preventing user interaction with underlying elements
 * while a loading state or modal is active.
 *
 * It works by placing a focusable `Box` on top of the `content`. When `enabled` is true,
 * this Box requests focus as soon as it's laid out and intercepts key events.
 * The `BACK` key is still allowed to pass through.
 *
 * @param enabled A boolean indicating whether the focus blocking mechanism should be active.
 *                When `true`, focus is blocked, and key events (except BACK) are consumed.
 *                When `false`, the `content` behaves normally.
 * @param content The composable content that should be conditionally blocked from focus.
 */
@Composable
fun FocusBlocker(enabled: Boolean, content: @Composable () -> Unit) {
    val fr = remember { FocusRequester() }
    var laidOut by remember { mutableStateOf(false) }

    // When enabled, claim focus once the node exists
    LaunchedEffect(enabled, laidOut) {
        if (enabled && laidOut) {
            // one frame to ensure FocusTarget is attached
            withFrameNanos { }
            fr.requestFocus()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { laidOut = true }
            .then(
                if (enabled) {
                    Modifier
                        .focusRequester(fr)
                        .focusable() // creates a FocusTarget
                        .onPreviewKeyEvent { e ->
                            // Consume DPAD + Enter while we're loading, but allow BACK
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER -> true
                                KeyEvent.KEYCODE_BACK -> false
                                else -> false
                            }
                        }
                } else Modifier
            )
    ) {
        content()
    }
}

@Composable
fun EnhancedErrorView(title: String, message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(32.dp)
            .fillMaxWidth()
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Retry")
        }
    }
}



/**
 * A private composable function that displays a button with focus-aware styling.
 * RIGHT NOW IT' ONLY USED INSIDE THE FIRSTTIMESETUP INITIAL DIALOG!!
 *
 * This button changes its border and elevation when focused, providing visual feedback
 * for keyboard navigation, particularly useful in TV UIs or when accessibility is a concern.
 *
 * @param text The text to display on the button.
 * @param modifier A [Modifier] to be applied to this button. Defaults to [Modifier].
 * @param onClick A lambda function to be executed when the button is clicked.
 */
@Composable
private fun FocusableButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        interactionSource = interaction,
        shape = MaterialTheme.shapes.small,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (focused) 8.dp else 2.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text)
    }
}

@Composable
fun EnhancedEntityList(
    items: List<Any>,
    onCategoryClick: (CategoryItem) -> Unit,
    onCategoryLongClick: (CategoryItem) -> Unit,
    onEntityCheckedChange: (EntityItem, Boolean) -> Unit,
    searchQuery: String
) {
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        delay(300) // Short delay to ensure layout is ready
        focusRequester.requestFocus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { _, item ->
                when(item) {
                    is CategoryItem -> "category_${item.name}"
                    is EntityItem -> "entity_${item.id}"
                    else -> "unknown_${item.hashCode()}"
                }
            }
        ) { index, item ->
            when (item) {
                is CategoryItem -> EnhancedCategoryRow(
                    category = item,
                    expanded     = item.isExpanded,
                    onClick = { onCategoryClick(item) },
                    onLongClick = { onCategoryLongClick(item) },
                    isFocused = index == focusedIndex,
                    searchQuery = searchQuery,
                    modifier = Modifier
                        .focusRequester(if (index == 0) focusRequester else remember { FocusRequester() })
                        .onFocusChanged {
                            if (it.hasFocus) {
                                focusedIndex = index
                            } else if (focusedIndex == index) {
                                // If I am the item that was focused and I'm losing focus,
                                // reset the index.
                                focusedIndex = -1
                            }
                        }
                )
                is EntityItem -> EnhancedEntityRow(
                    entity = item,
                    onCheckedChange = { isChecked -> onEntityCheckedChange(item, isChecked) },
                    isFocused = index == focusedIndex,
                    modifier = Modifier.onFocusChanged {
                        if (it.hasFocus) {
                            focusedIndex = index
                        } else if (focusedIndex == index) {
                            // If I am the item that was focused and I'm losing focus,
                            // reset the index.
                            focusedIndex = -1
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EnhancedCategoryRow(
    category: CategoryItem,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isFocused: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f
    )
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f)  // Reduced from 1.05

    var isFlashing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Count missing entities in this category
    val missingCount = category.entities.count { !it.isAvailable }

    // Determine if we should show a warning indicator on the category
    val hasMissingEntities = missingCount > 0

    val rowBackgroundColor = if (isFlashing) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else if (hasMissingEntities) {
        // Subtle error background for categories with missing entities
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
    } else {
        colorResource(id = R.color.md_theme_surface)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .scale(scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    scope.launch {
                        isFlashing = true
                        delay(300) // How long the flash lasts
                        isFlashing = false
                    }
                    onLongClick()
                },
                indication = null, // This disables the default square overlay
                interactionSource = remember { MutableInteractionSource() }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 1.2f.dp else 1.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = rowBackgroundColor 
        ),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused)
                MaterialTheme.colorScheme.primary
            else if (hasMissingEntities)
                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = rowBackgroundColor,
                    shape = MaterialTheme.shapes.medium
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon based on type
            val iconRes = when (category.name.lowercase()) {
                "light" -> R.drawable.lightbulb_on
                "switch" -> R.drawable.toggle_switch_variant
                "button" -> R.drawable.gesture_tap_button
                "input_button" -> R.drawable.gesture_tap_button
                "script" -> R.drawable.script_text_play
                "scene" -> R.drawable.palette
                "climate" -> R.drawable.ic_ac_unit
                "fan" -> R.drawable.fan
                "cover" -> R.drawable.window_shutter_open
                "input_boolean" -> R.drawable.toggle_switch
                "sensor" -> R.drawable.chart_line
                "binary_sensor" -> R.drawable.dip_switch
                "lock" -> R.drawable.lock_outline
                "alarm_control_panel" -> R.drawable.shield_home
                "camera" -> R.drawable.cctv
                "automation" -> R.drawable.robot_happy
                "media_player" -> R.drawable.play_circle
                else -> R.drawable.ic_default
            }

            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(16.dp))

            // Selected count in category name
            val selectedCount = category.entities.count { it.isSelected }
            val categoryText = buildAnnotatedString {
                append(category.name.replaceFirstChar { it.uppercase() })

                // Show selected count
                if (selectedCount > 0) {
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("($selectedCount selected")

                        // Add missing count if needed
                        if (hasMissingEntities) {
                            append(", ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                append("$missingCount missing")
                            }
                        }

                        append(")")
                    }
                } else if (hasMissingEntities) {
                    // If there are no selected items but there are missing ones
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("(")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                            append("$missingCount missing")
                        }
                        append(")")
                    }
                }

                // Show match count after searching
                if (searchQuery.isNotBlank() && category.matchCount > 0) {
                    append(" ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("(${category.matchCount} matches)")
                    }
                }
            }

            Text(
                text = categoryText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            if (hasMissingEntities) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Missing entities",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
            }

            Icon(
                painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = "Expand",
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun EnhancedEntityRow(
    entity: EntityItem,
    onCheckedChange: (Boolean) -> Unit,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.01f else 1.0f)
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (isFocused) 2.dp else 1.dp

    val isMissing = !entity.isAvailable

    val containerColor = when {
        isMissing -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        entity.isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> colorResource(id = R.color.md_theme_surfaceContainerLow)
    }

    val textColor = when {
        isMissing -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    val borderColor2 = when {
        isMissing -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 60.dp, end = 60.dp, top = 2.dp, bottom = 2.dp)
            .scale(scale)
            .clickable(
                onClick = { onCheckedChange(!entity.isSelected) },
                indication = null, // This disables the default square overlay
                interactionSource = remember { MutableInteractionSource() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = borderWidth,
            color = if (isFocused)
                colorResource(id = R.color.md_theme_primary)
            else
                colorResource(id = R.color.md_theme_outline)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colorResource(id = R.color.md_theme_surfaceContainerLow),
                    shape = MaterialTheme.shapes.small
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isMissing) "${entity.friendlyName} (MISSING ENTITY)" else entity.friendlyName,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                    color = textColor,
                    textDecoration = if (isMissing) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    entity.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMissing) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )

                // Add warning message for missing entities
                if (isMissing) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This entity is no longer provided by Home Assistant, you should remove it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Start
                    )
                }
            }

            // Show checkmark icon if checked, otherwise show checkbox
            IconToggleButton(
                checked = entity.isSelected,
                onCheckedChange = { onCheckedChange(it) } // It handles the toggle
            ) {
                if (entity.isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Circle,
                        contentDescription = "Not Selected",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchFocused by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isSearchFocused, query) {
        if (!isSearchFocused && query.isBlank()) {
            // Delay collapsing to make it feel smoother
            delay(200)
            isExpanded = false
        }
    }

    val collapsedWidth = 48.dp
    val expandedWidth = 280.dp

    // Expand when focused or when there's text
    val isSearchActive = isSearchFocused || query.isNotBlank()
    if (isSearchActive) isExpanded = true

    val width by animateDpAsState(
        targetValue = if (isExpanded) expandedWidth else collapsedWidth,
        label = "searchBarWidth"
    )

    Box(
        modifier = modifier
            .width(width),
        contentAlignment = Alignment.Center
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isSearchFocused = it.isFocused },
            placeholder = {
                if (isExpanded && query.isBlank()) Text("Search entities")
            },
            leadingIcon = {
                if (isExpanded) {
                    Icon(Icons.Default.Search, contentDescription = null)
                } else {
                    Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.clickable {
                                isExpanded = true
                            }
                        )
                    }
                }
            },
            trailingIcon = {
                if (isExpanded && query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                // Fix text color explicitly
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                // Also improve placeholder contrast
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        )
    }
}
//endregion