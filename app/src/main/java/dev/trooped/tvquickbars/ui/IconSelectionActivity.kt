package dev.trooped.tvquickbars.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.IconPack
import dev.trooped.tvquickbars.utils.IconLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * IconSelectionActivity
 *
 * Activity for selecting icons for entities in the QuickBar.
 * Allows users to search and select icons, with support for on/off states for toggleable entities.
 */
class IconSelectionActivity : BaseActivity() {

    companion object {
        const val RESULT_ICON_ON_NAME = "ICON_ON_NAME"
        const val RESULT_ICON_OFF_NAME = "ICON_OFF_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get data from intent
        val entityId = intent.getStringExtra("ENTITY_ID") ?: ""
        val isToggleable = intent.getBooleanExtra("IS_TOGGLEABLE", false)
        val currentIconOnRes = intent.getIntExtra("CURRENT_ICON_ON", 0)
        val currentIconOffRes = intent.getIntExtra("CURRENT_ICON_OFF", 0)

        setContent {
            // Use Material3 theme with your app's color scheme
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IconSelectionScreen(
                        isToggleable = isToggleable,
                        initialIconOnRes = if (currentIconOnRes != 0) currentIconOnRes else null,
                        initialIconOffRes = if (currentIconOffRes != 0) currentIconOffRes else null,
                        onIconSelected = { onRes, offRes ->
                            val resultIntent = Intent()

                            // Add resource names (for stable storage)
                            try {
                                val iconOnName = resources.getResourceEntryName(onRes)
                                resultIntent.putExtra(RESULT_ICON_ON_NAME, iconOnName)

                                if (offRes != null) {
                                    val iconOffName = resources.getResourceEntryName(offRes)
                                    resultIntent.putExtra(RESULT_ICON_OFF_NAME, iconOffName)
                                }
                            } catch (e: Exception) {
                                // Fallback if resource name can't be retrieved
                                Log.e("IconSelection", "Failed to get resource name", e)
                            }

                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                        },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Composable function for the Icon Selection screen.
 * Displays a grid of icons, allows searching, and provides options to select icons.
 *
 * @param isToggleable Whether the selected icon can have an on/off state.
 * @param initialIconOnRes The resource ID of the initially selected "on" icon.
 * @param initialIconOffRes The resource ID of the initially selected "off" icon (if applicable).
 * @param onIconSelected Callback when an icon is selected.
 * @param onCancel Callback when the user cancels the selection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun IconSelectionScreen(
    isToggleable: Boolean,
    initialIconOnRes: Int?,
    initialIconOffRes: Int?,
    onIconSelected: (iconOnRes: Int, iconOffRes: Int?) -> Unit,
    onCancel: () -> Unit
) {
    /* ───────────────── 1. Stable / derived state  ───────────────── */

    // Static list – never changes at runtime
    val allIcons: List<IconPack> = remember { IconLibrary.getAvailableIcons() }

    // User input survives config‑changes
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Selected icon (survive config change as well)
    var selectedIconPack by rememberSaveable(
        stateSaver = Saver(
            save = { it?.name },                     // save by unique name
            restore = { name -> allIcons.find { it.name == name } }
        )
    ) {
        mutableStateOf(
            initialIconOnRes?.takeIf { it != 0 }?.let { onRes ->
                allIcons.find { it.iconOnRes == onRes && it.iconOffRes == initialIconOffRes }
                    ?: allIcons.find { it.iconOnRes == onRes }
                    // If still no match (unlikely), find anything similar
                    ?: allIcons.firstOrNull()
            }
        )
    }

    /* ‑‑ Off‑load the potentially heavy filtering ‑‑ */
    val filteredIcons by produceState(initialValue = allIcons, searchQuery) {
        // simple debounce – remove if you want *instant* filtering
        delay(150)
        val q = searchQuery.trim().lowercase()
        value = if (q.isEmpty()) {
            allIcons
        } else {
            // run on background; then post result
            withContext(Dispatchers.Default) {
                allIcons.filter { icon ->
                    icon.name.contains(q, true) ||
                            icon.tags.any { it.contains(q, true) }
                }
            }
        }
    }

    val saveButtonFocusRequester = remember { FocusRequester() }

    /* ───────────────── 2. Scaffold  ───────────────── */

    Scaffold(
        topBar = {
            IconSelectionTopBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onCancel = onCancel
            )
        },
        bottomBar = {
            IconSelectionBottomBar(
                isToggleable = isToggleable,
                selectedIconPack = selectedIconPack,
                onSave = { pack ->
                    if (isToggleable) {
                        onIconSelected(pack.iconOnRes, pack.iconOffRes)
                    } else {
                        onIconSelected(pack.iconOnRes, null)
                    }
                },
                onCancel = onCancel,
                saveButtonFocusRequester = saveButtonFocusRequester
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp)
        ) {
            /* ─ Preview (unchanged) ─ */
            AnimatedContent(
                targetState = selectedIconPack,
                transitionSpec = {
                    fadeIn(tween(300)) with fadeOut(tween(300))
                },
                label = "PreviewAnim"
            ) { pack ->
                if (pack != null) {
                    CompactIconPreviewSection(pack, isToggleable)
                } else {
                    Spacer(Modifier.height(8.dp))
                }
            }

            /* ─ Icon grid  ─ */
            IconGrid(
                icons = filteredIcons,
                selectedIconPack = selectedIconPack,
                onIconClick = { selectedIconPack = it },
                saveButtonFocusRequester = saveButtonFocusRequester,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* ─────────────────────────  TOP BAR  ───────────────────────── */

/**
 * Composable function for the top bar of the Icon Selection screen.
 * Displays a search field and a back button.
 *
 * @param query The current search query.
 * @param onQueryChange Callback to update the search query.
 * @param onCancel Callback when the user cancels the selection.
 */
@Composable
private fun IconSelectionTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit
) {
    // Collapsed / expanded animation state
    var isSearchFocused by remember { mutableStateOf(false) }

    val collapsedW = 56.dp
    val expandedW  = 280.dp
    val barHeight  = 56.dp

    val isExpanded = isSearchFocused || query.isNotBlank()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Row(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = Modifier.padding(start = 16.dp),
            onClick = onCancel
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Text(
            "Select Icon",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f)
        )

        Box(
            modifier = Modifier
                .padding(end = 24.dp)
                .height(barHeight)
                .width(
                    animateDpAsState(
                        if (isExpanded) expandedW else collapsedW,   // ← use isExpanded
                        label = "sbWidth"
                    ).value
                ),
            contentAlignment = Alignment.Center
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { isSearchFocused = it.isFocused },
                placeholder = {
                    if (isExpanded && query.isBlank()) Text("Search icons")
                },
                leadingIcon = {
                    if (isExpanded) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    } else {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            Icon(Icons.Default.Search, contentDescription = null)
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
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus() // Optional: Clears focus from the text field
                    }
                )
            )
        }
    }
}

/* ─────────────────────────  BOTTOM BAR  ───────────────────────── */

/**
 * Composable function for the bottom bar of the Icon Selection screen.
 * Displays Save and Cancel buttons.
 *
 * @param isToggleable Whether the selected icon can have an on/off state.
 * @param selectedIconPack The currently selected icon pack.
 * @param onSave Callback when the user saves the selection.
 * @param onCancel Callback when the user cancels the selection.
 * @param saveButtonFocusRequester FocusRequester for the Save button.
 */
@Composable
private fun IconSelectionBottomBar(
    isToggleable: Boolean,
    selectedIconPack: IconPack?,
    onSave: (IconPack) -> Unit,
    onCancel: () -> Unit,
    saveButtonFocusRequester: FocusRequester
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Spacer(Modifier.weight(1f))

        var cancelFocused by remember { mutableStateOf(false) }
        var saveFocused by remember { mutableStateOf(false) }

        TextButton(
            onClick = onCancel,
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
        ) { Text("CANCEL", style = MaterialTheme.typography.labelLarge) }

        Spacer(Modifier.width(8.dp))

        Button(
            modifier = Modifier
                .focusRequester(saveButtonFocusRequester)
                .onFocusChanged { saveFocused = it.isFocused },
            onClick = { selectedIconPack?.let(onSave) },
            enabled = selectedIconPack != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (saveFocused)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.primary
            ),
            border = if (saveFocused)
                BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary)
            else null
        ) { Text("SAVE", style = MaterialTheme.typography.labelLarge) }
    }
}

/* ─────────────────────────  ICON GRID  ───────────────────────── */

/**
 * Composable function to display a grid of icons.
 * Allows users to select an icon, with support for toggleable entities.
 *
 * @param icons The list of available icons.
 * @param selectedIconPack The currently selected icon pack.
 * @param onIconClick Callback when an icon is clicked.
 * @param saveButtonFocusRequester FocusRequester for the Save button in the bottom bar.
 * @param modifier Modifier for styling and layout.
 */
@Composable
private fun IconGrid(
    icons: List<IconPack>,
    selectedIconPack: IconPack?,
    onIconClick: (IconPack) -> Unit,
    saveButtonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 80.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier
    ) {
        items(
            items = icons,
            key = { it.name }  // stable key
        ) { iconPack ->
            CompactIconItem(
                iconPack = iconPack,
                isSelected = iconPack == selectedIconPack,
                onClick = { onIconClick(iconPack) },
                saveButtonFocusRequester = saveButtonFocusRequester
            )
        }
    }
}

/**
 * Composable function for the compact icon preview section.
 * Displays the selected icon with an optional toggleable state.
 *
 * @param iconPack The currently selected icon pack.
 * @param isToggleable Whether the selected icon can have an on/off state.
 */
@Composable
fun CompactIconPreviewSection(
    iconPack: IconPack,
    isToggleable: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp), // Reduced padding
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // Smaller elevation
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title row with name
            Text(
                text = "Selected icon",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Icon preview - centered
            val isNonToggleableIconWithToggleableEntity = isToggleable && iconPack.iconOffRes == null

            if (isNonToggleableIconWithToggleableEntity) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ON & OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = iconPack.iconOnRes),
                            contentDescription = "Preview icon",
                            modifier = Modifier.size(32.dp),
                            colorFilter = ThemedIconColorFilter()
                        )
                    }
                }
            } else if (isToggleable && iconPack.iconOffRes != null) {
                // This Row will correctly center its children because its parent Column
                // and its own modifiers are configured for centering.
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactPreviewBox(label = "ON", iconRes = iconPack.iconOnRes)
                    Spacer(modifier = Modifier.width(24.dp))
                    CompactPreviewBox(label = "OFF", iconRes = iconPack.iconOffRes)
                }
            } else {
                // Single icon (non-toggleable entity)
                // Passing an empty label ensures no text is shown above the icon.
                CompactPreviewBox(label = "", iconRes = iconPack.iconOnRes)
            }
        }
    }
}

/**
 * Composable function for a compact preview box with an icon and optional label.
 * Displays the icon with a fixed size and background.
 *
 * @param label The label to display above the icon (optional).
 * @param iconRes The resource ID of the icon to display.
 */
@Composable
private fun CompactPreviewBox(label: String, iconRes: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp) // Fixed width for consistency
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = "Preview of $label icon",
                modifier = Modifier.size(28.dp),
                colorFilter = ThemedIconColorFilter()
            )
        }
    }
}

/**
 * Composable function for a compact icon item in the grid.
 * Displays the icon with a smaller size and reduced padding.
 *
 * @param iconPack The icon pack to display.
 * @param isSelected Whether this icon is currently selected.
 * @param onClick Callback when the icon is clicked.
 * @param saveButtonFocusRequester FocusRequester for the Save button in the bottom bar.
 */
@Composable
fun CompactIconItem(
    iconPack: IconPack,
    isSelected: Boolean,
    onClick: () -> Unit,
    saveButtonFocusRequester: FocusRequester
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .padding(3.dp) // Reduced padding
            .aspectRatio(1f)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
                    val moved = focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
                    if (!moved) {
                        saveButtonFocusRequester.requestFocus()
                    }
                    return@onPreviewKeyEvent true
                }
                false
            },
        onClick = onClick,
        elevation = CardDefaults.cardElevation(
            defaultElevation = when {
                isSelected && isFocused -> 6.dp
                isSelected -> 3.dp
                isFocused -> 4.dp
                else -> 1.dp
            }
        ),
        shape = MaterialTheme.shapes.small, // Smaller corner radius
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected && isFocused -> MaterialTheme.colorScheme.primary
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isFocused -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(8.dp), // Less padding
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconPack.iconOnRes),
                contentDescription = iconPack.name,
                modifier = Modifier.fillMaxSize(0.7f), // Make icon fill 70% of space
                colorFilter = ThemedIconColorFilter()
            )
        }
    }
}

/**
 * Composable function for a preview box with an icon and optional label.
 * Displays the icon with a fixed size and background.
 *
 * @param label The label to display above the icon (optional).
 * @param iconRes The resource ID of the icon to display.
 */
@Composable
private fun PreviewBox(label: String, iconRes: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Set a minimum height so single icons don't cause the box to resize.
        modifier = Modifier.defaultMinSize(minHeight = 76.dp)
    ) {
        // ✅ The label and spacer will only appear if a label is provided.
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = "Preview of $label icon",
                modifier = Modifier.size(32.dp),
                colorFilter = ThemedIconColorFilter()
            )
        }
    }
}

/**
 * Composable function to apply a themed color filter to icons.
 * Uses the onSurface color from the MaterialTheme color scheme.
 *
 * @return A ColorFilter that tints icons with the onSurface color.
 */
@Composable
fun ThemedIconColorFilter(): ColorFilter? {
    return ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
}