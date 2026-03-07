package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.climate

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.PressType
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.ui.AnimatedIconButton
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.ui.QuickBar.controls.PowerButton
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaHigh
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaLow
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaMedium
import dev.trooped.tvquickbars.ui.QuickBar.foundation.SafePainterResource
import dev.trooped.tvquickbars.ui.QuickBar.foundation.getTypeSafe
import dev.trooped.tvquickbars.ui.ValuePill
import dev.trooped.tvquickbars.utils.EntityActionExecutor
import dev.trooped.tvquickbars.utils.EntityStateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.text.ifEmpty

/**
 * Represents the UI state for a climate (HVAC) entity, encapsulating all necessary
 * data for rendering the climate control card and its expanded controls.
 *
 * @property name The display name of the climate entity, favoring custom names over friendly names.
 * @property hvacMode The current operating mode (e.g., "heat", "cool", "off").
 * @property targetTemp The desired temperature setting for the device.
 * @property currentTemp The actual ambient temperature reported by the device, if available.
 * @property hvacModes A list of all supported HVAC operating modes.
 * @property fanMode The current fan speed or mode setting.
 * @property fanModes A list of all supported fan speed settings.
 * @property swingMode The current oscillation or swing mode setting.
 * @property swingModes A list of all supported swing mode settings.
 * @property supportsFanMode Boolean indicating if the entity supports fan speed adjustments.
 * @property showRoomTemp Boolean indicating if the current ambient temperature should be displayed.
 * @property tabLabels A list of strings representing the available control categories (e.g., "Temp", "Mode", "Fan") based on entity capabilities.
 */
data class ClimateUiState(
    val name: String,
    val hvacMode: String,
    val targetTemp: Double,
    val currentTemp: Double?,
    val hvacModes: List<String>,
    val fanMode: String,
    val fanModes: List<String>,
    val swingMode: String,
    val swingModes: List<String>,
    val supportsFanMode: Boolean,
    val showRoomTemp: Boolean,
    val tabLabels: List<String>
)

/**
 * Remembers and calculates the UI state for a climate entity, providing a reactive [ClimateUiState]
 * object that encapsulates attributes, modes, and display preferences.
 *
 * This function extracts relevant data from the [EntityItem]'s attributes, such as current and
 * target temperatures, available HVAC/fan/swing modes, and determines which control tabs
 * (Temperature, Mode, Fan) should be visible based on the entity's capabilities and user preferences.
 */
@Composable
fun rememberClimateUiState(entity: EntityItem): ClimateUiState {
    val attributes = entity.attributes ?: JSONObject()
    
    val name = remember(entity.customName, entity.friendlyName) {
        entity.customName.ifEmpty { entity.friendlyName }
    }

    val hvacMode = attributes.optString("hvac_mode", entity.state).ifEmpty { entity.state }
    val targetTemp = attributes.optDouble("temperature", 0.0)
    val currentTemp = if (attributes.has("current_temperature")) attributes.optDouble("current_temperature", 0.0) else null

    val supportedFeatures = attributes.optInt("supported_features", 0)
    val supportsFanMode = (supportedFeatures and 8) != 0 && attributes.has("fan_modes")

    val hvacModes = remember(attributes) { getClimateHvacModes(attributes) }
    val fanModes = remember(attributes) { getClimateFanModes(attributes) }
    val fanMode = attributes.optString("fan_mode", "")
    
    val swingModes = remember(attributes) { getClimateSwingModes(attributes) }
    val swingMode = attributes.optString("swing_mode", "")

    val showRoomTempPref = entity.lastKnownState.getTypeSafe("show_room_temp", true)
    val showModeControls = entity.lastKnownState.getTypeSafe("show_mode_controls", true)
    val showFanControls = entity.lastKnownState.getTypeSafe("show_fan_controls", true)

    val tabLabels = remember(showModeControls, showFanControls, hvacModes, supportsFanMode, fanModes) {
        val labels = mutableListOf<String>()
        labels.add("Temp")
        if (showModeControls && hvacModes.size > 1) labels.add("Mode")
        if (showFanControls && supportsFanMode && fanModes.isNotEmpty()) labels.add("Fan")
        labels
    }

    return ClimateUiState(
        name = name,
        hvacMode = hvacMode,
        targetTemp = targetTemp,
        currentTemp = currentTemp,
        hvacModes = hvacModes,
        fanMode = fanMode,
        fanModes = fanModes,
        swingMode = swingMode,
        swingModes = swingModes,
        supportsFanMode = supportsFanMode,
        showRoomTemp = showRoomTempPref && currentTemp != null,
        tabLabels = tabLabels
    )
}

/**
 * A specialized card component for controlling Climate (HVAC) entities.
 *
 * This card displays the current state, temperature, and mode of a climate device.
 * When expanded, it provides an interactive interface for:
 * - Adjusting target temperature with a debounced service call.
 * - Switching HVAC modes (Heat, Cool, Auto, etc.) via a tabbed interface.
 * - Controlling fan speeds and swing modes if supported by the entity.
 * - Toggling power state with memory of the last active settings.
 *
 * @param entity The climate entity data from Home Assistant.
 * @param haClient The client used to dispatch service calls for temperature and mode changes.
 * @param onStateColor The theme-defined color key to use when the device is active.
 * @param customOnStateColor Optional RGB values for a user-defined active color.
 * @param modifier Modifier to be applied to the card's layout.
 * @param isHorizontal Whether to display the card in a wide, horizontal layout instead of vertical.
 * @param isEntityInitialized Flag to prevent accidental service calls during initial composition.
 */
@Composable
fun ClimateEntityCard(
    entity: EntityItem,
    haClient: HomeAssistantClient?,
    onStateColor: String,
    customOnStateColor: List<Int>? = null,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = false,
    isEntityInitialized: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val cardFocusRequester = remember { FocusRequester() }
    val closeBtnFocusRequester = remember { FocusRequester() }
    val wasCardFocused = remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) wasCardFocused.value = true
    }

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

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(expanded) {
        if (expanded) {
            wasCardFocused.value = isFocused
            delay(50)
            bringIntoViewRequester.bringIntoView()
            delay(100)
            withContext(Dispatchers.Main.immediate) {
                try { closeBtnFocusRequester.requestFocus() } catch (e: Exception) { Log.e("EntityCard", "Focus request failed", e) }
            }
        } else if (wasCardFocused.value) {
            delay(100)
            withContext(Dispatchers.Main.immediate) {
                try {
                    cardFocusRequester.requestFocus()
                    wasCardFocused.value = false
                } catch (e: Exception) { Log.e("EntityCard", "Focus request failed", e) }
            }
        }
    }

    val context = LocalContext.current
    val savedEntitiesManager = SavedEntitiesManager(context)

    LaunchedEffect(entity.id, entity.state, entity.attributes) {
        if (entity.state != "off") {
            EntityStateUtils.captureClimateState(entity, savedEntitiesManager)
        }
    }

    val uiState = rememberClimateUiState(entity)
    val isEnabled = uiState.hvacMode !in listOf("unavailable", "unknown")
    val isOn = isEnabled && uiState.hvacMode != "off"

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
        label = "climateBackgroundColor"
    )

    val animatedContentColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> disabledContent
            isOn       -> onContentColor
            else       -> offContentColor
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "climateContentColor"
    )

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
        colors = CardDefaults.cardColors(containerColor = animatedBackgroundColor, contentColor = animatedContentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .animateContentSize(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                .fillMaxSize()
        ) {
            key(expanded) {
                if (!isHorizontal) {
                    VerticalClimateContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        savedEntitiesManager = savedEntitiesManager,
                        expanded = expanded,
                        onClose = { expanded = false },
                        iconRes = iconRes,
                        contentColor = animatedContentColor,
                        backgroundColor = animatedBackgroundColor,
                        closeBtnFocusRequester = closeBtnFocusRequester,
                        iconModifier = Modifier.scale(iconScale)
                    )
                } else {
                    HorizontalClimateContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        savedEntitiesManager = savedEntitiesManager,
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

/**
 * A composable function that renders the control interface for a climate entity, providing
 * specialized UI for temperature adjustment, HVAC mode selection, and fan/swing settings.
 *
 * This section supports a tabbed navigation system to switch between different control types
 * and adapts its layout based on whether it is displayed in a compact or standard view.
 *
 * @param entity The [EntityItem] representing the climate device.
 * @param uiState The current [ClimateUiState] containing processed attributes and state information.
 * @param haClient The [HomeAssistantClient] used to dispatch service calls (e.g., set_temperature).
 * @param selectedTab The index of the currently active control tab (Temp, Mode, or Fan).
 * @param onTabSelected Callback invoked when the user selects a different control tab.
 * @param contentColor The primary color used for text, icons, and borders.
 * @param backgroundColor The background color of the card, used for contrast when an item is focused.
 * @param modifier The [Modifier] to be applied to the outer layout of this section.
 * @param isCompact Boolean flag to shrink font sizes and padding for smaller display areas.
 */
@Composable
private fun ClimateControlsSection(
    entity: EntityItem,
    uiState: ClimateUiState,
    haClient: HomeAssistantClient?,
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
                    .fillMaxWidth()
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
            uiState.tabLabels.isNotEmpty() && selectedTab < uiState.tabLabels.size && uiState.tabLabels[selectedTab] == "Temp" -> {
                TemperatureControl(entity, uiState.targetTemp, haClient, contentColor, backgroundColor, isCompact)
            }
            uiState.tabLabels.isNotEmpty() && selectedTab < uiState.tabLabels.size && uiState.tabLabels[selectedTab] == "Mode" -> {
                ModeControl(entity, uiState.hvacMode, uiState.hvacModes, haClient, contentColor, backgroundColor, isCompact)
            }
            uiState.tabLabels.isNotEmpty() && selectedTab < uiState.tabLabels.size && uiState.tabLabels[selectedTab] == "Fan" -> {
                ClimateFanModeControl(entity, uiState.fanMode, uiState.fanModes, uiState.swingMode, uiState.swingModes, haClient, contentColor, backgroundColor, isCompact)
            }
        }
    }
}

/**
 * Renders the vertical layout for the climate entity card, displaying the entity's icon,
 * current status, and target temperature. When expanded, it reveals detailed controls
 * for temperature adjustments, HVAC modes, and fan settings.
 *
 */
@Composable
private fun VerticalClimateContent(
    entity: EntityItem,
    uiState: ClimateUiState,
    haClient: HomeAssistantClient?,
    savedEntitiesManager: SavedEntitiesManager,
    expanded: Boolean,
    onClose: () -> Unit,
    iconRes: Int,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    iconModifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uiState.showRoomTemp) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .background(color = contentColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Filled.DeviceThermostat, contentDescription = "Current temperature", modifier = Modifier.size(10.dp), tint = contentColor.copy(alpha = 0.7f))
                    Text(text = String.format("%.1f°", uiState.currentTemp), fontSize = 10.sp, color = contentColor.copy(alpha = 0.8f))
                }
            }
        }

        Image(
            painter = SafePainterResource(id = iconRes),
            contentDescription = uiState.name,
            modifier = Modifier.size(28.dp).then(iconModifier),
            colorFilter = ColorFilter.tint(contentColor)
        )

        Text(text = uiState.name, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modeIcon = remember(uiState.hvacMode) { derivedStateOf {
                getClimateModeIconFallback(
                    uiState.hvacMode
                )
            } }.value

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(imageVector = modeIcon, contentDescription = uiState.hvacMode, modifier = Modifier.size(14.dp), tint = contentColor.copy(alpha = AlphaHigh))
            }

            if (uiState.hvacMode != "off" && uiState.hvacMode != "fan_only" && uiState.hvacMode != "dry") {
                Text(text = String.format("%.1f°", uiState.targetTemp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (expanded) {
            var selectedTab by remember { mutableStateOf(0) }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(Modifier, DividerDefaults.Thickness, color = contentColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            ClimateControlsSection(
                entity = entity,
                uiState = uiState,
                haClient = haClient,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                contentColor = contentColor,
                backgroundColor = backgroundColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            PowerButton(
                isOn = uiState.hvacMode != "off",
                onClick = {
                    EntityStateUtils.toggleClimateWithMemory(
                        entity = entity,
                        haClient = haClient,
                        savedEntitiesManager = savedEntitiesManager
                    )
                },
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

/**
 * Renders the horizontal layout for the climate entity card.
 *
 * This layout is optimized for a wide aspect ratio, placing the entity's primary status
 * (icon, name, and current temperature) on the left side. When [expanded], it extends
 * to the right to reveal a control section for temperature, HVAC modes, and fan settings
 * alongside quick-action buttons for power and closing the expanded view.
 *
 */
@Composable
private fun HorizontalClimateContent(
    entity: EntityItem,
    uiState: ClimateUiState,
    haClient: HomeAssistantClient?,
    savedEntitiesManager: SavedEntitiesManager,
    expanded: Boolean,
    onClose: () -> Unit,
    iconRes: Int,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    iconModifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .height(160.dp)
            .then(if (expanded) Modifier.width(360.dp) else Modifier.width(120.dp))
            .clip(RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.width(104.dp).fillMaxHeight()) {
            if (uiState.showRoomTemp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(color = contentColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Filled.DeviceThermostat, contentDescription = "Current temperature", modifier = Modifier.size(10.dp), tint = contentColor.copy(alpha = 0.7f))
                    Text(text = String.format("%.1f°", uiState.currentTemp), fontSize = 10.sp, color = contentColor.copy(alpha = 0.8f))
                }
            }

            val mainContentYOffset = if (uiState.showRoomTemp) 8.dp else 0.dp

            Column(
                modifier = Modifier.align(Alignment.Center).offset(y = mainContentYOffset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = SafePainterResource(id = iconRes),
                    contentDescription = uiState.name,
                    modifier = Modifier.size(28.dp).then(iconModifier),
                    colorFilter = ColorFilter.tint(contentColor)
                )

                Text(text = uiState.name, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modeIcon = remember(uiState.hvacMode) { derivedStateOf {
                        getClimateModeIconFallback(
                            uiState.hvacMode
                        )
                    } }.value

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = modeIcon, contentDescription = uiState.hvacMode, modifier = Modifier.size(14.dp), tint = contentColor.copy(alpha = AlphaHigh))
                    }

                    if (uiState.hvacMode != "off" && uiState.hvacMode != "fan_only" && uiState.hvacMode != "dry") {
                        Text(text = String.format("%.1f°", uiState.targetTemp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (expanded) {
            var selectedTab by remember { mutableStateOf(0) }

            Box(modifier = Modifier.width(1.dp).height(100.dp).background(contentColor.copy(alpha = 0.2f)))

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ClimateControlsSection(
                    entity = entity,
                    uiState = uiState,
                    haClient = haClient,
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
                        isOn = uiState.hvacMode != "off",
                        onClick = {
                            EntityStateUtils.toggleClimateWithMemory(
                                entity = entity,
                                haClient = haClient,
                                savedEntitiesManager = savedEntitiesManager
                            )
                        },
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
 * A composable function that provides an interface for adjusting the target temperature
 * of a climate entity.
 *
 * This control features increment and decrement buttons that modify the temperature based
 * on the entity's supported step size. It includes a debouncing mechanism (500ms) to
 * prevent excessive service calls to Home Assistant while the user is actively making
 * adjustments.
 */
@Composable
private fun TemperatureControl(
    entity: EntityItem,
    targetTemp: Double,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    isCompact: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val attributes = entity.attributes ?: JSONObject()

    val minTemp = attributes.optDouble("min_temp", 16.0)
    val maxTemp = attributes.optDouble("max_temp", 28.0)
    val rawStep = attributes.optDouble("target_temp_step", 1.0)
    val step = if (rawStep > 0) rawStep else 1.0

    val COMMIT_DELAY_MS = 500L

    var isChanging by remember { mutableStateOf(false) }
    var pendingTarget by remember { mutableStateOf<Double?>(null) }
    var commitJob by remember { mutableStateOf<Job?>(null) }

    fun snap(value: Double): Double {
        val steps = ((value - minTemp) / step).roundToInt()
        val snapped = minTemp + steps * step
        return snapped.coerceIn(minTemp, maxTemp)
    }

    var localTemp by remember(entity.id) { mutableStateOf(snap(targetTemp)) }

    LaunchedEffect(targetTemp) {
        if (!isChanging) localTemp = snap(targetTemp)
    }

    fun commitNow(value: Double) {
        pendingTarget = null
        isChanging = false
        haClient?.callService("climate", "set_temperature", entity.id, JSONObject().put("temperature", value))
    }

    fun scheduleCommit() {
        val target = pendingTarget ?: return
        commitJob?.cancel()
        commitJob = scope.launch {
            delay(COMMIT_DELAY_MS)
            commitNow(target)
        }
    }

    fun applyDelta(delta: Double) {
        val next = snap(localTemp + delta)
        if (next == localTemp) return
        localTemp = next
        isChanging = true
        pendingTarget = next
        scheduleCommit()
    }

    DisposableEffect(entity.id) {
        onDispose {
            if (isChanging && pendingTarget != null) {
                commitJob?.cancel()
                commitNow(pendingTarget!!)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedIconButton(
                icon = Icons.Filled.Remove, contentDescription = "Decrease temperature", onClick = { applyDelta(-step) },
                contentColor = contentColor, backgroundColor = backgroundColor, size = if (isCompact) 36.dp else 40.dp
            )

            val tempText = if (step < 1.0) String.format("%.1f°", localTemp) else "${localTemp.toInt()}°"
            ValuePill(
                text = tempText, contentColor = contentColor, backgroundColor = backgroundColor,
                minWidth = if (isCompact) 50.dp else 60.dp, height = if (isCompact) 36.dp else 40.dp
            )

            AnimatedIconButton(
                icon = Icons.Filled.Add, contentDescription = "Increase temperature", onClick = { applyDelta(+step) },
                contentColor = contentColor, backgroundColor = backgroundColor, size = if (isCompact) 36.dp else 40.dp
            )
        }
    }
}

/**
 * A composable function that provides an interface for selecting the HVAC operating mode
 * (e.g., Heat, Cool, Auto, Off) for a climate entity.
 *
 * This control displays a grid of available modes supported by the device. Each mode is
 * represented by an icon and a label. When a mode is selected, a service call is dispatched
 * to Home Assistant to update the device's state.
 */
@Composable
private fun ModeControl(
    entity: EntityItem,
    currentMode: String,
    availableModes: List<String>,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    isCompact: Boolean = false
) {
    val attributes = entity.attributes ?: JSONObject()
    val modeIcons = remember(entity.id, attributes) { getClimateModeIcons(attributes) }

    val orderedModes = listOf("off", "heat", "cool", "auto", "heat_cool", "fan_only", "dry")
    val sortedModes = availableModes.sortedBy { mode -> orderedModes.indexOf(mode).takeIf { it >= 0 } ?: orderedModes.size }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().height(if (isCompact) 120.dp else 140.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (isCompact) 2.dp else 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
            ) {
                items(sortedModes) { mode ->
                    val isSelected = mode == currentMode
                    val buttonInteraction = remember { MutableInteractionSource() }
                    val buttonFocused by buttonInteraction.collectIsFocusedAsState()

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(
                            modifier = Modifier
                                .size(if (isCompact) 36.dp else 40.dp)
                                .clip(CircleShape)
                                .background(when { buttonFocused -> contentColor; isSelected -> contentColor.copy(alpha = 0.4f); else -> contentColor.copy(alpha = AlphaLow) })
                                .border(width = if (isSelected) 2.dp else 1.dp, color = if (buttonFocused) contentColor else contentColor.copy(alpha = AlphaMedium), shape = CircleShape)
                                .clickable(
                                    interactionSource = buttonInteraction, indication = ripple(),
                                    onClick = { haClient?.callService("climate", "set_hvac_mode", entity.id, JSONObject().apply { put("hvac_mode", mode) }) }
                                )
                                .focusable(interactionSource = buttonInteraction),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = modeIcons[mode] ?: Icons.Filled.Thermostat,
                                contentDescription = mode.replaceFirstChar { it.uppercase() },
                                tint = if (buttonFocused) backgroundColor else contentColor,
                                modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                            )
                        }

                        Text(
                            text = mode.replaceFirstChar { it.uppercase() }.replace("_", " "),
                            fontSize = if (isCompact) 10.sp else 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A composable function that provides an interface for controlling the fan and swing modes
 * of a climate entity.
 *
 * This control displays a scrollable grid of available fan speeds (e.g., Low, Medium, High, Auto).
 * If the entity supports swing modes, it also displays a specialized toggle button to
 * enable or disable oscillation. Changes are dispatched as service calls to Home Assistant
 * (set_fan_mode or set_swing_mode).
 *
 */
@Composable
private fun ClimateFanModeControl(
    entity: EntityItem,
    currentFanMode: String,
    availableFanModes: List<String>,
    currentSwingMode: String,
    availableSwingModes: List<String>,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    isCompact: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().height(if (isCompact) 120.dp else 140.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isCompact) 1 else 2),
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (isCompact) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)
            ) {
                items(availableFanModes) { mode ->
                    val isSelected = mode == currentFanMode
                    val buttonInteraction = remember { MutableInteractionSource() }
                    val buttonFocused by buttonInteraction.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isCompact) 32.dp else 36.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(when { buttonFocused -> contentColor; isSelected -> contentColor.copy(alpha = 0.5f); else -> contentColor.copy(alpha = AlphaLow) })
                            .border(width = if (isSelected) 2.dp else 1.dp, color = if (buttonFocused) contentColor else contentColor.copy(alpha = if (isSelected) AlphaHigh else AlphaMedium), shape = RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = buttonInteraction, indication = ripple(),
                                onClick = { haClient?.callService("climate", "set_fan_mode", entity.id, JSONObject().apply { put("fan_mode", mode) }) }
                            )
                            .focusable(interactionSource = buttonInteraction),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.replaceFirstChar { it.uppercase() },
                            fontSize = if (isCompact) 12.sp else 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (buttonFocused) backgroundColor else contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (availableSwingModes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            val offMode = availableSwingModes.find { it.contains("off") || it.contains("stop") } ?: availableSwingModes.last()
            val onMode = availableSwingModes.find { it != offMode } ?: availableSwingModes.first()

            val isSwingOn = currentSwingMode != offMode
            val toggleInteraction = remember { MutableInteractionSource() }
            val toggleFocused by toggleInteraction.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(if (isCompact) 60.dp else 70.dp)
                    .clip(CircleShape)
                    .background(when { toggleFocused -> contentColor; isSwingOn -> contentColor.copy(alpha = 0.6f); else -> contentColor.copy(alpha = 0.1f) })
                    .border(width = if (isSwingOn) 2.dp else 1.dp, color = if (toggleFocused) contentColor else contentColor.copy(alpha = if (isSwingOn) 0.8f else 0.3f), shape = CircleShape)
                    .clickable(
                        interactionSource = toggleInteraction, indication = ripple(),
                        onClick = { haClient?.callService("climate", "set_swing_mode", entity.id, JSONObject().apply { put("swing_mode", if (isSwingOn) offMode else onMode) }) }
                    )
                    .focusable(interactionSource = toggleInteraction),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = if (isSwingOn) Icons.Default.Autorenew else Icons.Default.Stop, contentDescription = if (isSwingOn) "Turn Off Swing" else "Turn On Swing", tint = if (toggleFocused) backgroundColor else contentColor, modifier = Modifier.size(if (isCompact) 24.dp else 28.dp))
                    Text(text = "Swing", fontSize = if (isCompact) 10.sp else 12.sp, fontWeight = if (isSwingOn) FontWeight.Bold else FontWeight.Normal, color = if (toggleFocused) backgroundColor else contentColor)
                }
            }
        }
    }
}