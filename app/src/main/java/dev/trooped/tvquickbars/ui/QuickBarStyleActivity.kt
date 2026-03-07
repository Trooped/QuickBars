package dev.trooped.tvquickbars.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.QuickBar
import dev.trooped.tvquickbars.data.QuickBarPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * QuickBarStyleActivity
 *
 * Activity for customizing the QuickBar style.
 * Allows users to change background color, opacity, and ON state color.
 */
class QuickBarStyleActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get individual properties from intent
        val backgroundColor = intent.getStringExtra("BACKGROUND_COLOR") ?: "colorSurface"
        val backgroundOpacity = intent.getIntExtra("BACKGROUND_OPACITY", 90)
        val onStateColor = intent.getStringExtra("ON_STATE_COLOR") ?: "colorPrimary"

        val customBgRgb = intent.getIntArrayExtra("CUSTOM_BG_RGB")?.toList()
        val customOnRgb = intent.getIntArrayExtra("CUSTOM_ON_RGB")?.toList()

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
                    QuickBarStyleScreen(
                        initialBackgroundColor = backgroundColor,
                        initialBackgroundOpacity = backgroundOpacity,
                        initialOnStateColor = onStateColor,
                        initialCustomBgRgb = customBgRgb,
                        initialCustomOnRgb = customOnRgb,
                        onSave = { newBgColor, newBgOpacity, newOnColor, bgRgb, onRgb ->   // UPDATED
                            val resultIntent = Intent().apply {
                                putExtra("BACKGROUND_COLOR", newBgColor)
                                putExtra("BACKGROUND_OPACITY", newBgOpacity)
                                putExtra("ON_STATE_COLOR", newOnColor)
                                if (newBgColor.equals("custom", true) && bgRgb != null) {
                                    putExtra("CUSTOM_BG_RGB", bgRgb.toIntArray())
                                }
                                if (newOnColor.equals("custom", true) && onRgb != null) {
                                    putExtra("CUSTOM_ON_RGB", onRgb.toIntArray())
                                }
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        },
                        onCancel = {
                            showDiscardChangesDialog()
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showDiscardChangesDialog()
    }

    private fun showDiscardChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard Changes")
            .setMessage("Are you sure you want to leave this screen? Changes will not be saved.")
            .setPositiveButton("Leave") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("Stay") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}

/**
 * Composable function for the QuickBar style screen.
 * Displays options to customize background color, opacity, and ON state color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBarStyleScreen(
    initialBackgroundColor: String,
    initialBackgroundOpacity: Int,
    initialOnStateColor: String,
    initialCustomBgRgb: List<Int>?,
    initialCustomOnRgb: List<Int>?,
    onSave: (String, Int, String, List<Int>?, List<Int>?) -> Unit,
    onCancel: () -> Unit
) {
    // Create mutable state to track changes
    var backgroundColor by remember { mutableStateOf(initialBackgroundColor) }
    var backgroundOpacity by remember { mutableIntStateOf(initialBackgroundOpacity) }
    var onStateColor by remember { mutableStateOf(initialOnStateColor) }

    var customBgRgb by remember { mutableStateOf(initialCustomBgRgb ?: listOf(24, 24, 24)) }
    var customOnRgb by remember { mutableStateOf(initialCustomOnRgb ?: listOf(255, 204, 0)) }

    val firstColorFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }

    fun rgbToColor(rgb: List<Int>) =
        Color(android.graphics.Color.rgb(rgb.getOrNull(0) ?: 0, rgb.getOrNull(1) ?: 0, rgb.getOrNull(2) ?: 0))

    LaunchedEffect(Unit) {
        try {
            // Use withContext to ensure we're on the main dispatcher
            withContext(Dispatchers.Main) {
                // Small delay to ensure this happens after the preview focus request
                delay(100)
                firstColorFocusRequester.requestFocus()
            }
        } catch (e: Exception) {
            try {
                withContext(Dispatchers.Main) {
                    backButtonFocusRequester.requestFocus()
                }
            } catch (e: Exception) {
                // Silent fallback
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QuickBar Style") },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp) // Match XML padding
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Match XML spacing
        ) {
            // Row with 2 columns - background color/opacity and ON state color
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp) // Match XML spacing
            ) {
                // LEFT COLUMN - BACKGROUND COLOR + OPACITY
                Card(
                    modifier = Modifier
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp), // Match XML
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // Match XML
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline) // Match XML
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp) // Match XML padding
                    ) {
                        Text(
                            text = "Background",
                            style = MaterialTheme.typography.titleLarge, // Match XML
                            modifier = Modifier.padding(bottom = 8.dp) // Match XML
                        )

                        // Background color selection row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Keep the existing color buttons with improved focus properties
                            SimpleColorButton(
                                color = colorResource(id = R.color.md_theme_surface),
                                isSelected = backgroundColor == "colorSurface",
                                onClick = { backgroundColor = "colorSurface" },
                                modifier = Modifier
                                    .size(36.dp)
                                    .focusRequester(firstColorFocusRequester),
                            )
                            SimpleColorButton(
                                color = colorResource(id = R.color.md_theme_primaryContainer),
                                isSelected = backgroundColor == "colorPrimaryContainer",
                                onClick = { backgroundColor = "colorPrimaryContainer" },
                                modifier = Modifier.size(36.dp),
                            )
                            SimpleColorButton(
                                color = colorResource(id = R.color.md_theme_secondaryContainer),
                                isSelected = backgroundColor == "colorSecondaryContainer",
                                onClick = { backgroundColor = "colorSecondaryContainer" },
                                modifier = Modifier.size(36.dp),
                            )
                            SimpleColorButton(
                                color = colorResource(id = R.color.md_theme_tertiaryContainer),
                                isSelected = backgroundColor == "colorTertiaryContainer",
                                onClick = { backgroundColor = "colorTertiaryContainer" },
                                modifier = Modifier.size(36.dp),
                            )
                            SimpleColorButton(
                                color = rgbToColor(customBgRgb),
                                isSelected = backgroundColor.equals("custom", true),
                                onClick = { backgroundColor = "custom" },
                                modifier = Modifier.size(36.dp),
                            )
                            if (backgroundColor.equals("custom", true)) {
                                Text("Custom BG: ${customBgRgb.joinToString(", ")}", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Opacity slider
                        Text(
                            text = "Opacity: $backgroundOpacity%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )

                        var sliderIsFocused by remember { mutableStateOf(false) }
                        var sliderIsPressed by remember { mutableStateOf(false) }

                        // Slider with improved focus properties
                        Slider(
                            value = backgroundOpacity.toFloat(),
                            onValueChange = { newValue: Float ->
                                backgroundOpacity = newValue.toInt()
                                sliderIsPressed = true
                            },
                            onValueChangeFinished = {
                                sliderIsPressed = false
                            },
                            valueRange = 0f..100f,
                            steps = 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .onFocusChanged {
                                    sliderIsFocused = it.isFocused
                                }
                                .onKeyEvent { keyEvent ->
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            if (backgroundOpacity > 0) {
                                                backgroundOpacity = (backgroundOpacity - 2).coerceAtLeast(0)
                                                true
                                            } else false
                                        }
                                        Key.DirectionRight -> {
                                            if (backgroundOpacity < 100) {
                                                backgroundOpacity = (backgroundOpacity + 2).coerceAtMost(100)
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
                    }
                }

                // RIGHT COLUMN - ON STATE COLOR
                Card(
                    modifier = Modifier
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp), // Match XML
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // Match XML
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline) // Match XML
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp) // Match XML padding
                    ) {
                        Text(
                            text = "ON State Color",
                            style = MaterialTheme.typography.titleLarge, // Match XML
                            modifier = Modifier.padding(bottom = 8.dp) // Match XML
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SimpleColorButton(
                                color = colorResource(id = R.color.md_theme_primary),
                                isSelected = onStateColor == "colorPrimary",
                                onClick = { onStateColor = "colorPrimary" },
                                modifier = Modifier.size(36.dp)
                            )
                            SimpleColorButton(
                                color = colorResource(id = R.color.md_theme_Amber500),
                                isSelected = onStateColor == "colorAmber500",
                                onClick = { onStateColor = "colorAmber500" },
                                modifier = Modifier.size(36.dp)
                            )
                            SimpleColorButton(
                                color = colorResource(id = R.color.md_theme_tertiary),
                                isSelected = onStateColor == "colorTertiary",
                                onClick = { onStateColor = "colorTertiary" },
                                modifier = Modifier.size(36.dp)
                            )
                            SimpleColorButton(
                                color = colorResource(id = R.color.md_theme_error),
                                isSelected = onStateColor == "colorError",
                                onClick = { onStateColor = "colorError" },
                                modifier = Modifier.size(36.dp)
                            )
                            SimpleColorButton(
                                color = rgbToColor(customOnRgb),
                                isSelected = onStateColor.equals("custom", true),
                                onClick = { onStateColor = "custom" },
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        if (onStateColor.equals("custom", true)) {
                            Text("Custom ON: ${customOnRgb.joinToString(", ")}", style = MaterialTheme.typography.labelMedium)
                        }

                        // Empty space to balance the slider height
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }

            val previewBrush = Brush.linearGradient(
                colors = listOf(
                    // darkest corner (top‑left)
                    colorResource(id = R.color.md_theme_surfaceContainerLow),      // #171C1F

                    // mid ramp
                    colorResource(id = R.color.md_theme_surfaceContainerHigh),     // #2E3336

                    // lightest corner (bottom‑right)
                    colorResource(id = R.color.md_theme_surfaceBright)             // #404548
                ),
                start = Offset.Zero,
                end   = Offset.Infinite          // TL → BR sweep
            )

            // PREVIEW SECTION - styled like other cards
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                elevation= CardDefaults.cardElevation(2.dp),
                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors   = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                /* 3 – Background box with the gradient  */
                Box(
                    modifier = Modifier
                        .background(previewBrush)              // ← gradient here
                        .padding(16.dp)                        // inner padding
                ) {
                    Column {
                        // ---------- your LiveQuickBarPreview ----------
                        LiveQuickBarPreview(
                            backgroundColor   = backgroundColor,
                            backgroundOpacity = backgroundOpacity,
                            onStateColor      = onStateColor,
                            customBgRgb       = customBgRgb,
                            customOnRgb       = customOnRgb,
                            onDismiss         = {}
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Tip: click the entities in the preview to toggle them",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // SAVE BUTTON
            Button(
                onClick = {
                    onSave(backgroundColor, backgroundOpacity, onStateColor, customBgRgb, customOnRgb)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Save This Style",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Space at the bottom for better scroll experience
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Simple color button that displays a colored circle.
 * @param color The color to display.
 * @param isSelected Whether this color is currently selected.
 * @param onClick Callback when the button is clicked.
 * @param modifier Modifier for styling.
 */
private fun createSampleEntity(
    id: String,
    name: String,
    state: String,
    @DrawableRes iconResource: Int = 0,
    attributes: JSONObject = JSONObject()
): EntityItem {
    // Add standard attributes if not present
    if (!attributes.has("friendly_name")) {
        attributes.put("friendly_name", name)
    }

    val isToggleable = EntityIconMapper.isEntityToggleable(id)

    return EntityItem(
        id = id,
        state = state,
        friendlyName = name,
        customName = name,
        attributes = attributes,
        customIconOnName = EntityIconMapper.getDefaultOnIconForEntityName(id),
        customIconOffName = if (isToggleable) EntityIconMapper.getDefaultOffIconForEntityName(id) else null
    )
}

/**
 * Composable function for the Live QuickBar preview.
 * Displays the QuickBar with the current styling properties and entity states.
 * @param backgroundColor The background color of the QuickBar.
 * @param backgroundOpacity The background opacity of the QuickBar.
 * @param onStateColor The color for the ON state of the QuickBar.
 */
@Composable
fun LiveQuickBarPreview(
    backgroundColor: String,
    backgroundOpacity: Int,
    customBgRgb: List<Int>?,
    customOnRgb: List<Int>?,
    onStateColor: String,
    onDismiss: () -> Unit
) {

    fun rgbToColor(rgb: List<Int>?) =
        if (rgb == null || rgb.size < 3) null
        else Color(android.graphics.Color.rgb(rgb[0], rgb[1], rgb[2]))

    // Create preview state
    var entityStates by remember {
        mutableStateOf(
            mapOf(
                "light.living_room" to "on",
                "switch.kitchen" to "off",
                "input_boolean.office" to "off",
                "button.doorbell" to "unavailable"
            )
        )
    }

    // Create the QuickBar with current styling properties
    // Key change: Add the style parameters as keys to remember() so it updates when they change
    val previewQuickBar = remember(backgroundColor, backgroundOpacity, onStateColor, customBgRgb, customOnRgb) {
        QuickBar().apply {
            this.name = "Preview QuickBar"
            this.backgroundColor = backgroundColor
            this.backgroundOpacity = backgroundOpacity
            this.onStateColor = onStateColor
            if (backgroundColor.equals("custom", true)) {
                this.customBackgroundColor = customBgRgb
            }
            if (onStateColor.equals("custom", true)) {
                this.customOnStateColor = customOnRgb
            }
            this.position = QuickBarPosition.TOP
            this.showNameInOverlay = true
        }
    }

    // Create sample entities with current states
    val sampleEntities = remember(entityStates) {
        listOf(
            createSampleEntity(
                "light.living_room",
                "Kitchen Light",
                entityStates["light.living_room"] ?: "off",
            ),
            createSampleEntity(
                "switch.kitchen",
                "Bedroom Switch",
                entityStates["switch.kitchen"] ?: "off",
            ),
            createSampleEntity(
                "input_boolean.office",
                "Garden Light",
                entityStates["input_boolean.office"] ?: "off",
            )
        )
    }

    // Position the Box to the top instead of center
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The QuickBar content
        PreviewQuickBarOverlay(
            bar = previewQuickBar,
            entities = sampleEntities,
            onEntityToggle = { entityId ->
                // Update entity state when toggled
                entityStates = entityStates.toMutableMap().apply {
                    val currentState = entityStates[entityId] ?: "off"
                    put(entityId, if (currentState == "on") "off" else "on")
                }
            }
        )

    }
}

/**
 * Composable function to preview the QuickBar overlay.
 * Displays the QuickBar with the given properties and entities.
 * @param bar The QuickBar to preview.
 * @param entities The list of entities to display in the QuickBar.
 * @param onEntityToggle Callback when an entity is toggled.
 */
@Composable
fun PreviewQuickBarOverlay(
    bar: QuickBar,
    entities: List<EntityItem>,
    onEntityToggle: (String) -> Unit
) {
    // Simple implementation that mimics the real QuickBarOverlay
    val position = bar.position

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = when (position) {
            QuickBarPosition.TOP -> Alignment.TopCenter
            QuickBarPosition.BOTTOM -> Alignment.BottomCenter
            QuickBarPosition.LEFT -> Alignment.CenterStart
           QuickBarPosition.RIGHT -> Alignment.CenterEnd
            else -> Alignment.BottomCenter // Default to bottom
        }
    ) {
        // Get bar background color
        val bgBase = when (bar.backgroundColor) {
            "custom" -> bar.customBackgroundColor?.let {
                Color(android.graphics.Color.rgb(it.getOrNull(0) ?: 0, it.getOrNull(1) ?: 0, it.getOrNull(2) ?: 0))
            } ?: colorResource(id = R.color.md_theme_surface)
            "colorPrimaryContainer" -> colorResource(id = R.color.md_theme_primaryContainer)
            "colorSecondaryContainer" -> colorResource(id = R.color.md_theme_secondaryContainer)
            "colorTertiaryContainer" -> colorResource(id = R.color.md_theme_tertiaryContainer)
            else -> colorResource(id = R.color.md_theme_surface)
        }
        val bgColor = bgBase.copy(alpha = bar.backgroundOpacity / 100f)

        // Create appropriate layout based on position
        val isHorizontal = position == QuickBarPosition.TOP ||
                position == QuickBarPosition.BOTTOM

        Card(
            modifier = Modifier
                .padding(8.dp)
                .wrapContentHeight()
                .fillMaxWidth(if (isHorizontal) 0.9f else 0.5f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor)
        ) {
            Column(
                modifier = Modifier.padding(12.dp) // Reduced padding from 16dp
            ) {
                // Bar title
                if (bar.showNameInOverlay) {
                    Text(
                        text = bar.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColorFor(bgColor),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Horizontal layout for TOP/BOTTOM positions
                if (isHorizontal) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Create focus requesters for entity cards
                        val focusRequesters = remember { List(entities.size) { FocusRequester() } }

                        entities.forEachIndexed { index, entity ->
                            val nextIndex = if (index < entities.size - 1) index + 1 else index
                            val prevIndex = if (index > 0) index - 1 else 0

                            RealEntityCard(
                                entity = entity,
                                onStateColor = bar.onStateColor,
                                customOnStateColor = bar.customOnStateColor,
                                onToggle = { onEntityToggle(entity.id) },
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(80.dp)
                                    .focusRequester(focusRequesters[index])
                                    .focusProperties {
                                        next = focusRequesters[nextIndex]
                                        previous = focusRequesters[prevIndex]
                                    },
                                isHorizontal = true
                            )
                        }
                    }
                } else {
                    // Vertical layout for LEFT/RIGHT positions
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Create focus requesters for entity cards
                        val focusRequesters = remember { List(entities.size) { FocusRequester() } }

                        entities.forEachIndexed { index, entity ->
                            val nextIndex = if (index < entities.size - 1) index + 1 else index
                            val prevIndex = if (index > 0) index - 1 else 0

                            RealEntityCard(
                                entity = entity,
                                onStateColor = bar.onStateColor,
                                customOnStateColor = bar.customOnStateColor,
                                onToggle = { onEntityToggle(entity.id) },
                                modifier = Modifier
                                    .focusRequester(focusRequesters[index])
                                    .focusProperties {
                                        next = focusRequesters[nextIndex]
                                        previous = focusRequesters[prevIndex]
                                    },
                                isHorizontal = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Composable function to display a real entity card.
 * Mimics the EntityCard from QuickBarOverlay.kt with the same styling.
 * @param entity The entity to display.
 * @param onStateColor The color for the ON state of the entity.
 * @param onToggle Callback when the entity is toggled.
 * @param modifier Modifier for styling.
 * @param isHorizontal Whether to layout the card horizontally or vertically.
 */
@Composable
fun RealEntityCard(
    entity: EntityItem,
    onStateColor: String,
    customOnStateColor: List<Int>?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = false
) {
    fun rgbToColor(rgb: List<Int>?) =
        if (rgb == null || rgb.size < 3) null
        else Color(android.graphics.Color.rgb(rgb[0], rgb[1], rgb[2]))
    fun contentFor(bg: Color): Color {
        val l = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
        return if (l > 0.6f) Color.Black else Color.White
    }

    val name = remember(entity.customName, entity.friendlyName) {
        entity.customName.ifEmpty { entity.friendlyName }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // ON state colors - exact same logic as in QuickBarOverlay.kt EntityCard
    val onBackgroundColor = when {
        onStateColor.equals("custom", true) -> rgbToColor(customOnStateColor)
            ?: colorResource(id = R.color.md_theme_primary)
        onStateColor == "colorAmber500"     -> colorResource(id = R.color.md_theme_Amber500)
        onStateColor == "colorTertiary"     -> colorResource(id = R.color.md_theme_tertiary)
        onStateColor == "colorError"        -> colorResource(id = R.color.md_theme_error)
        else                                -> colorResource(id = R.color.md_theme_primary)
    }
    val onContentColor = when {
        onStateColor.equals("custom", true) -> contentFor(onBackgroundColor)
        onStateColor == "colorAmber500"     -> Color.Black
        onStateColor == "colorTertiary"     -> colorResource(id = R.color.md_theme_onTertiary)
        onStateColor == "colorError"        -> colorResource(id = R.color.md_theme_onError)
        else                                -> colorResource(id = R.color.md_theme_onPrimary)
    }

    // Color variables matching QuickBarOverlay.kt
    val unavailableBackgroundColor = colorResource(id = R.color.md_theme_surfaceVariant).copy(alpha = 0.6f)
    val elseBackgroundColor = colorResource(id = R.color.md_theme_surfaceVariant) // off

    val unavailableContentColor = colorResource(id = R.color.md_theme_onSurfaceVariant).copy(alpha = 0.6f)
    val elseContentColor = colorResource(id = R.color.md_theme_onSurfaceVariant) // off

    // Determine color based on state - EXACT same logic as QuickBarOverlay.kt
    val backgroundColor = remember(entity.state, onStateColor) { // Add onStateColor as dependency
        when (entity.state) {
            "on" -> onBackgroundColor
            "unavailable" -> unavailableBackgroundColor
            else -> elseBackgroundColor
        }
    }

    val contentColor = remember(entity.state, onStateColor) { // Add onStateColor as dependency
        when (entity.state) {
            "on" -> onContentColor
            "unavailable" -> unavailableContentColor
            else -> elseContentColor
        }
    }

    // Get icon
    val iconRes = remember(entity.id, entity.state, entity.customIconOnName, entity.customIconOffName) {
        EntityIconMapper.getFinalIconForEntity(entity) ?: R.drawable.ic_default
    }

    // Determine if entity can be clicked
    val canToggle = !entity.state.equals("unavailable", ignoreCase = true) && (
            entity.id.startsWith("light.") ||
                    entity.id.startsWith("switch.") ||
                    entity.id.startsWith("input_boolean.")
            )

    // Card with identical styling to QuickBarOverlay.kt EntityCard
    Card(
        modifier = modifier
            .padding(4.dp)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.outline else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                enabled = canToggle,
                onClick = onToggle,
                interactionSource = interactionSource,
                indication = ripple()
            )
            .focusable(interactionSource = interactionSource),
        colors = CardDefaults.cardColors(containerColor = backgroundColor, contentColor = contentColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (isHorizontal) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = name,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(contentColor)
                )

                Text(
                    text = name,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    modifier = Modifier.size(24.dp),
                    contentDescription = name,
                    colorFilter = ColorFilter.tint(contentColor)
                )

                Text(
                    text = name,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * Composable function for a simple color button.
 * Displays a circular button with a colored background.
 * @param color The color of the button.
 * @param isSelected Whether the button is currently selected.
 * @param onClick Callback when the button is clicked.
 */
@Composable
fun SimpleColorButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Existing implementation
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .padding(2.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(CircleShape)
            .background(color)
            .border(
                width = when {
                    isFocused -> 3.dp
                    isSelected -> 2.dp
                    else -> 1.dp
                },
                color = when {
                    isFocused -> Color.White
                    isSelected -> Color.White
                    else -> Color.Gray
                },
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
