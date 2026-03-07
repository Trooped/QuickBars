package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.light

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.PressType
import dev.trooped.tvquickbars.data.computeLightCaps
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.ui.AnimatedIconButton
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.ui.QuickBar.controls.PowerButton
import dev.trooped.tvquickbars.ui.QuickBar.entities.cards.normal.EntityCard
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaLow
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaMedium
import dev.trooped.tvquickbars.ui.QuickBar.foundation.SafePainterResource
import dev.trooped.tvquickbars.ui.QuickBar.foundation.getTypeSafe
import dev.trooped.tvquickbars.ui.ValuePill
import dev.trooped.tvquickbars.utils.EntityActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.text.ifEmpty

/**
 * UI state representation for a light entity, containing all the necessary information
 * to render brightness, color temperature, and RGB controls.
 *
 * @property name The display name of the light (custom name or friendly name).
 * @property isOn Whether the light is currently turned on.
 * @property brightnessPercent The current brightness level scaled from 0 to 100.
 * @property currentKelvin The current color temperature in Kelvin.
 * @property minKelvin The minimum supported color temperature in Kelvin.
 * @property maxKelvin The maximum supported color temperature in Kelvin.
 * @property rgbColor The current RGB color of the light.
 * @property indicatorColor A derived color used for UI indicators, representing either the RGB color or color temperature.
 * @property tabLabels A list of available control categories (e.g., "Brightness", "Temperature", "Color") based on entity capabilities.
 * @property supportsColorTemp Indicates if the light entity supports color temperature adjustments.
 * @property supportsRgbColor Indicates if the light entity supports RGB color adjustments.
 */
data class LightUiState(
    val name: String,
    val isOn: Boolean,
    val brightnessPercent: Int,
    val currentKelvin: Int,
    val minKelvin: Int,
    val maxKelvin: Int,
    val rgbColor: Color,
    val indicatorColor: Color?,
    val tabLabels: List<String>,
    val supportsColorTemp: Boolean,
    val supportsRgbColor: Boolean
)

/**
 * A helper function that debounces updates to a state value, primarily used for UI controls
 * (like sliders or color pickers) that trigger frequent network calls.
 *
 * It maintains a local state that updates immediately for a responsive UI, while delaying
 * the execution of the [onSend] callback until the user has stopped interacting for [delayMs].
 *
 * @param T The type of value being managed.
 * @param initialValue The starting value, typically synchronized with the external entity state.
 * @param delayMs The debounce timeout in milliseconds. Defaults to 180ms.
 * @param onSend The callback to execute after the debounce delay (e.g., calling a Home Assistant service).
 * @return A [Pair] containing the current local value and a function to update that value.
 */
@Composable
fun <T> rememberDebouncedAction(
    initialValue: T,
    delayMs: Long = 180L,
    onSend: (T) -> Unit
): Pair<T, (T) -> Unit> {
    val scope = rememberCoroutineScope()
    var isChanging by remember { mutableStateOf(false) }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    var localValue by remember(initialValue) { mutableStateOf(initialValue) }

    LaunchedEffect(initialValue) {
        if (!isChanging) {
            localValue = initialValue
        }
    }

    val updateValue: (T) -> Unit = { newValue ->
        isChanging = true
        localValue = newValue
        sendJob?.cancel()
        sendJob = scope.launch {
            delay(delayMs)
            isChanging = false
            onSend(newValue)
        }
    }

    return localValue to updateValue
}

/**
 * Remembers and computes the [LightUiState] for a given light entity.
 *
 * This function handles the logic for extracting and calculating light-specific properties from
 * the entity's attributes, such as brightness percentage, color temperature (Kelvin),
 * and RGB color values. It also determines which control tabs (Brightness, Temperature, Color)
 * should be visible based on the entity's capabilities and user preferences.
 *
 * @param entity The [EntityItem] representing the light.
 * @param isOn Boolean indicating if the light is currently powered on.
 * @param supportsColorTemp Boolean indicating if the light hardware supports color temperature adjustments.
 * @param supportsRgbColor Boolean indicating if the light hardware supports RGB color selection.
 * @return A [LightUiState] containing the processed UI properties for the light card.
 */
@Composable
fun rememberLightUiState(entity: EntityItem, isOn: Boolean, supportsColorTemp: Boolean, supportsRgbColor: Boolean): LightUiState {
    val attributes = entity.attributes ?: JSONObject()
    val name = remember(entity.customName, entity.friendlyName) {
        entity.customName.ifEmpty { entity.friendlyName }
    }

    val brightnessPercent = remember(entity.attributes) {
        fun readNumberLike(key: String): Double? {
            val v = attributes.opt(key)
            return when (v) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull()
                else -> null
            }
        }
        val raw = readNumberLike("brightness")
            ?: readNumberLike("brightness_pct")?.let { it.coerceIn(0.0, 100.0) * 2.55 }
            ?: readNumberLike("level")
            ?: 0.0
        val clamped = raw.coerceIn(0.0, 255.0)
        ((clamped * 100.0 + 127.0) / 255.0).toInt().coerceIn(0, 100)
    }

    val showBrightnessControls = entity.lastKnownState.getTypeSafe("show_brightness_controls", true)
    val showWarmthControls = entity.lastKnownState.getTypeSafe("show_warmth_controls", true)
    val showColorControls = entity.lastKnownState.getTypeSafe("show_color_controls", true)

    val (minKelvin, maxKelvin) = remember(attributes) { getEffectiveKelvinRange(attributes) }
    val currentKelvin = remember(attributes) { getCurrentKelvin(attributes) }

    val rgbColor = remember(attributes) {
        try {
            val rgb = attributes.optJSONArray("rgb_color")
            if (rgb != null && rgb.length() == 3) {
                Color(rgb.getInt(0), rgb.getInt(1), rgb.getInt(2))
            } else Color.White
        } catch (e: Exception) {
            Color.White
        }
    }

    val indicatorColor: Color? = remember(isOn, supportsRgbColor, supportsColorTemp, rgbColor, currentKelvin, minKelvin, maxKelvin) {
        if (!isOn) null
        else if (supportsRgbColor) rgbColor
        else if (supportsColorTemp && currentKelvin in minKelvin..maxKelvin) colorFromKelvin(
            currentKelvin,
            minKelvin,
            maxKelvin
        )
        else null
    }

    val tabLabels = remember(showBrightnessControls, showWarmthControls, showColorControls, supportsColorTemp, supportsRgbColor) {
        val labels = mutableListOf<String>()
        if (showBrightnessControls) labels.add("Brightness")
        if (showWarmthControls && supportsColorTemp) labels.add("Temperature")
        if (showColorControls && supportsRgbColor) labels.add("Color")
        labels
    }

    return LightUiState(
        name = name,
        isOn = isOn,
        brightnessPercent = brightnessPercent,
        currentKelvin = currentKelvin,
        minKelvin = minKelvin,
        maxKelvin = maxKelvin,
        rgbColor = rgbColor,
        indicatorColor = indicatorColor,
        tabLabels = tabLabels,
        supportsColorTemp = supportsColorTemp,
        supportsRgbColor = supportsRgbColor
    )
}

/**
 * A specialized card component for Home Assistant light entities.
 *
 * This card provides a rich interface for controlling lights, supporting:
 * - Simple toggle actions for basic lights.
 * - Expanded controls for brightness, color temperature (Kelvin), and RGB color selection.
 * - Adaptive UI that scales based on the entity's capabilities (capabilities are computed via [computeLightCaps]).
 * - Visual indicators for the current light color and brightness percentage.
 * - Dynamic theming based on the light's state and user-defined color preferences.
 * - Focus management and accessibility support for Android TV navigation.
 *
 * If the light is "simple" (no extra features enabled or supported), it delegates rendering
 * to a standard [EntityCard]. Otherwise, it provides an expandable interface with tabbed
 * controls for advanced adjustments.
 *
 * @param entity The [EntityItem] representing the light and its current state/attributes.
 * @param haClient The [HomeAssistantClient] used to dispatch service calls (turn_on, turn_off, etc.).
 * @param onStateColor The theme color key to use when the light is on.
 * @param customOnStateColor An optional list of RGB values if [onStateColor] is set to "custom".
 * @param modifier The [Modifier] to be applied to the card's layout.
 * @param isHorizontal Determines if the card should use a wide, horizontal layout or a standard vertical one.
 * @param isEntityInitialized Whether the entity data has been fully loaded and is ready for interaction.
 */
@Composable
fun LightEntityCard(
    entity: EntityItem,
    haClient: HomeAssistantClient?,
    onStateColor: String,
    customOnStateColor: List<Int>? = null,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = false,
    isEntityInitialized: Boolean
) {
    val context = LocalContext.current

    LaunchedEffect(entity.id) {
        if (entity.lastKnownState == null) entity.lastKnownState = mutableMapOf()
        val savedEntitiesManager = SavedEntitiesManager(context.applicationContext)
        savedEntitiesManager.applyDefaultLightOptions(entity)
    }

    val caps = remember(entity.id, entity.attributes, entity.lastKnownState) {
        computeLightCaps(entity.attributes, entity.lastKnownState)
    }

    if (caps.isSimple) {
        EntityCard(
            entity = entity,
            haClient = haClient,
            onStateColor = onStateColor,
            customOnStateColor = customOnStateColor,
            modifier = modifier,
            isHorizontal = isHorizontal,
            isEntityInitialized = isEntityInitialized
        )
    } else {
        var expanded by remember { mutableStateOf(false) }
        val cardFocusRequester = remember { FocusRequester() }
        val closeBtnFocusRequester = remember { FocusRequester() }
        val wasCardFocused = remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        val bringIntoViewRequester = remember { BringIntoViewRequester() }

        LaunchedEffect(isFocused) {
            if (isFocused) wasCardFocused.value = true
        }

        val isOn = entity.state == "on"
        val isEnabled = entity.state !in listOf("unavailable", "unknown")
        
        val uiState = rememberLightUiState(
            entity = entity, 
            isOn = isOn, 
            supportsColorTemp = caps.colorTemp, 
            supportsRgbColor = caps.color
        )

        var isPressed by remember { mutableStateOf(false) }
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> isPressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> isPressed = false
                }
            }
        }

        val iconScale by animateFloatAsState(
            targetValue = if (isPressed) 1.2f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
            label = "iconPressAnim"
        )

        LaunchedEffect(expanded) {
            if (expanded) {
                wasCardFocused.value = isFocused
                delay(50)
                bringIntoViewRequester.bringIntoView()
                delay(100)
                withContext(Dispatchers.Main.immediate) {
                    try { closeBtnFocusRequester.requestFocus() } catch (e: Exception) { Log.e("LightEntityCard", "Focus request failed", e) }
                }
            } else if (wasCardFocused.value) {
                delay(100)
                withContext(Dispatchers.Main.immediate) {
                    try {
                        cardFocusRequester.requestFocus()
                        wasCardFocused.value = false
                    } catch (e: Exception) { Log.e("LightEntityCard", "Focus request failed", e) }
                }
            }
        }

        val iconRes = remember(entity.id, entity.state, entity.customIconOnName, entity.customIconOffName) {
            EntityIconMapper.getFinalIconForEntity(entity) ?: R.drawable.ic_default
        }

        val onBackgroundColor = when {
            onStateColor.equals("custom", ignoreCase = true) && customOnStateColor != null && customOnStateColor.size >= 3 -> {
                Color(android.graphics.Color.rgb(customOnStateColor[0].coerceIn(0, 255), customOnStateColor[1].coerceIn(0, 255), customOnStateColor[2].coerceIn(0, 255)))
            }
            onStateColor == "colorAmber500" -> colorResource(id = R.color.md_theme_Amber500)
            onStateColor == "colorTertiary" -> colorResource(id = R.color.md_theme_tertiary)
            onStateColor == "colorError"    -> colorResource(id = R.color.md_theme_error)
            else                            -> colorResource(id = R.color.md_theme_primary)
        }

        fun contentFor(bg: Color): Color {
            val luma = 0.299f*bg.red + 0.587f*bg.green + 0.114f*bg.blue
            return if (luma > 0.6f) Color.Black else Color.White
        }

        val onContentColor = when {
            onStateColor.equals("custom", true) && customOnStateColor != null && customOnStateColor.size >= 3 -> contentFor(onBackgroundColor)
            onStateColor == "colorAmber500" -> Color.Black
            onStateColor == "colorTertiary" -> colorResource(id = R.color.md_theme_onTertiary)
            onStateColor == "colorError"    -> colorResource(id = R.color.md_theme_onError)
            else                            -> colorResource(id = R.color.md_theme_onPrimary)
        }

        val offBackgroundColor = colorResource(id = R.color.md_theme_surfaceVariant)
        val offContentColor    = colorResource(id = R.color.md_theme_onSurface)
        val disabledBackground = offBackgroundColor
        val disabledContent    = offContentColor.copy(alpha = 0.2f)

        val animatedBackgroundColor by animateColorAsState(
            targetValue = when {
                !isEnabled -> disabledBackground
                isOn       -> onBackgroundColor
                else       -> offBackgroundColor
            },
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "lightBackgroundColor"
        )

        val animatedContentColor by animateColorAsState(
            targetValue = when {
                !isEnabled -> disabledContent
                isOn       -> onContentColor
                else       -> offContentColor
            },
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "lightContentColor"
        )

        val savedEntitiesManager = remember { SavedEntitiesManager(context) }
        val handlePress: (PressType) -> Unit = remember(entity.id, haClient, savedEntitiesManager, isEntityInitialized) {
            { press ->
                if (isEntityInitialized) {
                    EntityActionExecutor.perform(
                        entity = entity,
                        press = press,
                        haClient = haClient,
                        savedEntitiesManager = savedEntitiesManager,
                        onExpand = { expanded = true }
                    )
                }
            }
        }

        Card(
            modifier = modifier
                .focusRequester(cardFocusRequester)
                .combinedClickable(
                    enabled = !expanded && isEnabled,
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = animatedContentColor),
                    onClick = { handlePress(PressType.SINGLE) },
                    onLongClick = { handlePress(PressType.LONG) }
                )
                .focusable(enabled = !expanded, interactionSource = interactionSource)
                .bringIntoViewRequester(bringIntoViewRequester),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                width = if (isFocused && !expanded) 3.dp else 0.dp,
                color = if (isFocused && !expanded) Color.White else Color.Transparent
            ),
            colors = CardDefaults.cardColors(
                containerColor = animatedBackgroundColor,
                contentColor = animatedContentColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Box(
                modifier = Modifier
                    .animateContentSize(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                    .fillMaxSize()
            ) {
                key(expanded) {
                    if (!isHorizontal) {
                        VerticalLightContent(
                            entity = entity,
                            uiState = uiState,
                            haClient = haClient,
                            expanded = expanded,
                            onClose = { expanded = false },
                            iconRes = iconRes,
                            contentColor = animatedContentColor,
                            backgroundColor = animatedBackgroundColor,
                            closeBtnFocusRequester = closeBtnFocusRequester,
                            iconModifier = Modifier.scale(iconScale)
                        )
                    } else {
                        HorizontalLightContent(
                            entity = entity,
                            uiState = uiState,
                            haClient = haClient,
                            expanded = expanded,
                            onClose = { expanded = false },
                            iconRes = iconRes,
                            contentColor = animatedContentColor,
                            backgroundColor = animatedBackgroundColor,
                            closeBtnFocusRequester = closeBtnFocusRequester,
                            iconModifier = Modifier.scale(iconScale)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A composable that provides a tabbed interface for controlling light attributes.
 *
 * Depending on the [uiState] and [selectedTab], this section displays controls for:
 * - Brightness (Percentage adjustment)
 * - Color Temperature (Kelvin adjustment)
 * - RGB Color (Color selection)
 */
@Composable
private fun LightControlsSection(
    entity: EntityItem,
    haClient: HomeAssistantClient?,
    uiState: LightUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.tabLabels.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(if (isCompact) 0.9f else 1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.tabLabels.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    val tabInteraction = remember { MutableInteractionSource() }
                    val tabFocused by tabInteraction.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    tabFocused -> contentColor
                                    isSelected -> contentColor.copy(alpha = 0.2f)
                                    else -> contentColor.copy(alpha = 0.05f)
                                }
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (tabFocused) contentColor else contentColor.copy(alpha = if (isSelected) 0.8f else 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(
                                interactionSource = tabInteraction,
                                indication = ripple(),
                                onClick = { onTabSelected(index) }
                            )
                            .focusable(interactionSource = tabInteraction)
                            .padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = if (isCompact) 4.dp else 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = if (isCompact) 11.sp else 12.sp,
                            color = if (tabFocused) backgroundColor else contentColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        when {
            uiState.tabLabels.isNotEmpty() && selectedTab < uiState.tabLabels.size && uiState.tabLabels[selectedTab] == "Brightness" -> {
                BrightnessControl(entity, uiState.brightnessPercent, haClient, contentColor, backgroundColor, isCompact)
            }
            uiState.tabLabels.isNotEmpty() && selectedTab < uiState.tabLabels.size && uiState.tabLabels[selectedTab] == "Temperature" -> {
                ColorTempControl(entity, uiState.currentKelvin, uiState.minKelvin, uiState.maxKelvin, haClient, contentColor, backgroundColor, isCompact)
            }
            uiState.tabLabels.isNotEmpty() && selectedTab < uiState.tabLabels.size && uiState.tabLabels[selectedTab] == "Color" -> {
                RgbColorControl(entity, uiState.rgbColor, haClient, contentColor, backgroundColor, isCompact)
            }
        }
    }
}

@Composable
private fun VerticalLightContent(
    entity: EntityItem,
    uiState: LightUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    iconRes: Int,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    iconModifier: Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = SafePainterResource(id = iconRes),
            contentDescription = uiState.name,
            modifier = Modifier.size(28.dp).then(iconModifier),
            colorFilter = ColorFilter.tint(contentColor)
        )

        Text(
            text = uiState.name,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 2.dp)
        )

        if (uiState.isOn) {
            if (uiState.indicatorColor != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${uiState.brightnessPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    ColorDot(color = uiState.indicatorColor, outline = contentColor, size = 16.dp)
                }
            } else {
                Text(text = "${uiState.brightnessPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(Modifier, DividerDefaults.Thickness, color = contentColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            LightControlsSection(
                entity = entity,
                haClient = haClient,
                uiState = uiState,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                contentColor = contentColor,
                backgroundColor = backgroundColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            PowerButton(
                isOn = uiState.isOn,
                onClick = { haClient?.callService("light", if (uiState.isOn) "turn_off" else "turn_on", entity.id) },
                contentColor = contentColor,
                backgroundColor = backgroundColor
            )

            Spacer(modifier = Modifier.height(8.dp))
            val closeBtnInteraction = remember { MutableInteractionSource() }
            val closeIsFocused by closeBtnInteraction.collectIsFocusedAsState()
            
            Box(
                modifier = Modifier
                    .focusRequester(closeBtnFocusRequester)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (closeIsFocused) contentColor else contentColor.copy(alpha = AlphaLow))
                    .border(width = if (closeIsFocused) 2.dp else 1.dp, color = if (closeIsFocused) contentColor else contentColor.copy(alpha = AlphaMedium), shape = CircleShape)
                    .clickable(interactionSource = closeBtnInteraction, indication = ripple(), onClick = onClose)
                    .focusable(interactionSource = closeBtnInteraction)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = if (closeIsFocused) backgroundColor else contentColor, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun HorizontalLightContent(
    entity: EntityItem,
    uiState: LightUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    iconRes: Int,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    iconModifier: Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }

    Row(
        modifier = Modifier
            .height(160.dp)
            .then(if (expanded) Modifier.width(360.dp) else Modifier.width(120.dp))
            .clip(RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.width(104.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = SafePainterResource(id = iconRes),
                contentDescription = uiState.name,
                modifier = Modifier.size(28.dp).then(iconModifier),
                colorFilter = ColorFilter.tint(contentColor)
            )
            Text(
                text = uiState.name,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            if (uiState.isOn) {
                if (uiState.indicatorColor != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "${uiState.brightnessPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        ColorDot(color = uiState.indicatorColor, outline = contentColor, size = 16.dp)
                    }
                } else {
                    Text(text = "${uiState.brightnessPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (expanded) {
            Box(modifier = Modifier.width(1.dp).height(100.dp).background(contentColor.copy(alpha = 0.2f)))

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LightControlsSection(
                    entity = entity,
                    haClient = haClient,
                    uiState = uiState,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    contentColor = contentColor,
                    backgroundColor = backgroundColor,
                    modifier = Modifier.fillMaxSize().padding(end = 32.dp),
                    isCompact = true
                )

                Column(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val closeBtnInteraction = remember { MutableInteractionSource() }
                    val closeIsFocused by closeBtnInteraction.collectIsFocusedAsState()
                    Box(
                        modifier = Modifier
                            .focusRequester(closeBtnFocusRequester)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (closeIsFocused) contentColor else contentColor.copy(alpha = AlphaLow))
                            .border(width = if (closeIsFocused) 2.dp else 1.dp, color = if (closeIsFocused) contentColor else contentColor.copy(alpha = AlphaMedium), shape = CircleShape)
                            .clickable(interactionSource = closeBtnInteraction, indication = ripple(), onClick = onClose)
                            .focusable(interactionSource = closeBtnInteraction),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = if (closeIsFocused) backgroundColor else contentColor, modifier = Modifier.size(20.dp))
                    }

                    PowerButton(
                        isOn = uiState.isOn,
                        onClick = { haClient?.callService("light", if (uiState.isOn) "turn_off" else "turn_on", entity.id) },
                        contentColor = contentColor,
                        backgroundColor = backgroundColor,
                        size = 36.dp
                    )
                }
            }
        }
    }
}

/**
 * A small circular visual indicator used to display the current color or temperature of a light.
 */
@Composable
private fun ColorDot(color: Color?, outline: Color, size: Dp = 16.dp) {
    val dot = color ?: Color.Transparent
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(dot)
            .border(1.dp, outline.copy(alpha = 0.6f), CircleShape)
    )
}

/**
 * A UI component for controlling the brightness level of a light entity.
 *
 * This control provides a debounced interface for adjusting brightness using increment (plus)
 * and decrement (minus) buttons. It synchronizes the local state with Home Assistant by
 * calling the `light.turn_on` service with the `brightness_pct` attribute. If the brightness
 * is set to 0%, it automatically triggers the `light.turn_off` service.
 */
@Composable
private fun BrightnessControl(
    entity: EntityItem,
    brightnessPercent: Int,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    isCompact: Boolean = false
) {
    val (localBrightness, setLocalBrightness) = rememberDebouncedAction(
        initialValue = brightnessPercent,
        onSend = { v ->
            if (haClient == null) return@rememberDebouncedAction
            if (v <= 0) {
                haClient.callService("light", "turn_off", entity.id)
            } else {
                entity.lastKnownState?.set("last_brightness_pct", v)
                haClient.callService("light", "turn_on", entity.id, JSONObject().put("brightness_pct", v))
            }
        }
    )

    val step = 10
    fun applyDelta(delta: Int) {
        val next = (localBrightness + delta).coerceIn(0, 100)
        if (next != localBrightness) setLocalBrightness(next)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedIconButton(
                icon = Icons.Filled.Remove, contentDescription = "Dim", onClick = { applyDelta(-step) },
                contentColor = contentColor, backgroundColor = backgroundColor, size = if (isCompact) 36.dp else 40.dp
            )
            ValuePill(
                text = "$localBrightness%", contentColor = contentColor, backgroundColor = backgroundColor,
                minWidth = if (isCompact) 50.dp else 60.dp, height = if (isCompact) 36.dp else 40.dp
            )
            AnimatedIconButton(
                icon = Icons.Filled.Add, contentDescription = "Brighten", onClick = { applyDelta(+step) },
                contentColor = contentColor, backgroundColor = backgroundColor, size = if (isCompact) 36.dp else 40.dp
            )
        }
    }
}

/**
 * A UI component for controlling the color temperature (Kelvin) of a light entity.
 *
 * This control allows users to adjust the warmth or coolness of a light using increment and
 * decrement buttons. It utilizes [rememberDebouncedAction] to ensure that Home Assistant
 * service calls are only dispatched after the user has finished their selection, preventing
 * network congestion.
 *
 * The component calculates a percentage representation of the current Kelvin value relative
 * to the entity's supported [minKelvin] and [maxKelvin] range for display in a central pill.
 *
 */
@Composable
private fun ColorTempControl(
    entity: EntityItem,
    currentKelvin: Int,
    minKelvin: Int,
    maxKelvin: Int,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    isCompact: Boolean = false
) {
    val (localKelvin, setLocalKelvin) = rememberDebouncedAction(
        initialValue = currentKelvin.coerceIn(minKelvin, maxKelvin),
        onSend = { v -> haClient?.callService("light", "turn_on", entity.id, JSONObject().put("color_temp_kelvin", v)) }
    )

    val step = ((maxKelvin - minKelvin) / 10).coerceAtLeast(100)
    fun applyDelta(delta: Int) {
        val next = (localKelvin + delta).coerceIn(minKelvin, maxKelvin)
        if (next != localKelvin) setLocalKelvin(next)
    }

    val tempPercent = ((localKelvin - minKelvin).toFloat() / (maxKelvin - minKelvin).toFloat() * 100f).toInt().coerceIn(0, 100)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Warmer", onClick = { applyDelta(-step) },
                contentColor = contentColor, backgroundColor = backgroundColor, size = if (isCompact) 36.dp else 40.dp
            )
            Box(
                modifier = Modifier
                    .widthIn(min = if (isCompact) 50.dp else 60.dp)
                    .height(if (isCompact) 36.dp else 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(contentColor.copy(alpha = AlphaLow))
                    .border(1.dp, contentColor.copy(alpha = AlphaMedium), RoundedCornerShape(8.dp))
                    .semantics { disabled() }
                    .focusable(false),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "$tempPercent%", fontSize = if (isCompact) 12.sp else 14.sp, fontWeight = FontWeight.Medium, color = contentColor)
            }
            AnimatedIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Cooler", onClick = { applyDelta(step) },
                contentColor = contentColor, backgroundColor = backgroundColor, size = if (isCompact) 36.dp else 40.dp
            )
        }
    }
}

/**
 * A UI component for controlling the RGB color of a light entity.
 *
 * This control provides a selection interface to cycle through a predefined list of
 * standard colors (e.g., Red, Green, Blue, White, etc.). It uses [rememberDebouncedAction]
 * to handle color updates, sending the selected RGB values to Home Assistant via
 * the `light.turn_on` service.
 *
 * The component matches the current entity color to the closest available preset index
 * for its initial state and displays a [ColorDot] preview of the currently selected color.
 */
@Composable
private fun RgbColorControl(
    entity: EntityItem,
    currentColor: Color,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    isCompact: Boolean = false
) {
    val colorOptions = remember {
        listOf(
            Color.White to "White", Color.Red to "Red", Color.Green to "Green", Color.Blue to "Blue",
            Color.Yellow to "Yellow", Color.Cyan to "Cyan", Color.Magenta to "Magenta", Color(0xFFFF7F00) to "Orange",
            Color(0xFF800080) to "Purple", Color(0xFF00FF7F) to "Spring Green"
        )
    }

    fun closestIndex(c: Color): Int {
        var best = 0; var bestDist = Float.MAX_VALUE
        for (i in colorOptions.indices) {
            val o = colorOptions[i].first
            val d = (o.red - c.red)*(o.red - c.red) + (o.green - c.green)*(o.green - c.green) + (o.blue - c.blue)*(o.blue - c.blue)
            if (d < bestDist) { bestDist = d; best = i }
        }
        return best
    }

    val (localColorIndex, setLocalColorIndex) = rememberDebouncedAction(
        initialValue = closestIndex(currentColor),
        onSend = { i ->
            val col = colorOptions[i].first
            haClient?.callService(
                "light", "turn_on", entity.id,
                JSONObject().put("rgb_color", JSONArray().put((col.red * 255).toInt()).put((col.green * 255).toInt()).put((col.blue * 255).toInt()))
            )
        }
    )

    fun applyStep(delta: Int) {
        val next = (localColorIndex + delta + colorOptions.size) % colorOptions.size
        if (next != localColorIndex) setLocalColorIndex(next)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Color", onClick = { applyStep(-1) },
                contentColor = contentColor, backgroundColor = backgroundColor, size = if (isCompact) 36.dp else 40.dp
            )
            Box(
                modifier = Modifier
                    .size(if (isCompact) 50.dp else 60.dp, if (isCompact) 36.dp else 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(contentColor.copy(alpha = AlphaLow))
                    .border(1.dp, contentColor.copy(alpha = AlphaMedium), RoundedCornerShape(8.dp))
                    .semantics { disabled() }
                    .focusable(false),
                contentAlignment = Alignment.Center
            ) {
                ColorDot(color = colorOptions[localColorIndex].first, outline = contentColor, size = if (isCompact) 18.dp else 22.dp)
            }
            AnimatedIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Color", onClick = { applyStep(1) },
                contentColor = contentColor, backgroundColor = backgroundColor, size = if (isCompact) 36.dp else 40.dp
            )
        }
    }
}