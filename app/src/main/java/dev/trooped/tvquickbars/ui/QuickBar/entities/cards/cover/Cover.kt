package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.cover

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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaLow
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaMedium
import dev.trooped.tvquickbars.ui.QuickBar.foundation.SafePainterResource
import dev.trooped.tvquickbars.utils.EntityActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.text.ifEmpty

/**
 * Represents the processed UI state for a Cover entity, containing all the necessary
 * information to render the cover card and its controls.
 *
 * @property name The display name of the cover, prioritizing custom names over friendly names.
 * @property state The raw state string from Home Assistant (e.g., "open", "closed", "opening").
 * @property isOpen Boolean indicating if the cover is currently in an open or partially open state.
 * @property isEnabled Boolean indicating if the entity is available and reachable.
 * @property position The current position percentage of the cover (0-100).
 * @property supportsTilt Boolean indicating if the hardware supports tilt functionality.
 * @property hasTiltState Boolean indicating if the current entity provides state information for tilt.
 * @property shouldShowTiltControls Boolean determining if tilt control buttons should be rendered.
 * @property iconRes The resource ID for the icon to be displayed, based on the entity's state and configuration.
 */
data class CoverUiState(
    val name: String,
    val state: String,
    val isOpen: Boolean,
    val isEnabled: Boolean,
    val position: Int,
    val supportsTilt: Boolean,
    val hasTiltState: Boolean,
    val shouldShowTiltControls: Boolean,
    val iconRes: Int
)

/**
 * Remembers and computes the [CoverUiState] for a given [EntityItem].
 *
 * This composable processes the raw entity data to determine various UI-related properties
 * such as the display name, current state, whether the cover is open, and if it
 * supports specific features like tilt controls.
 *
 * @param entity The [EntityItem] representing the cover entity from Home Assistant.
 * @return A [CoverUiState] object containing the processed information for UI rendering.
 */
@Composable
fun rememberCoverUiState(entity: EntityItem): CoverUiState {
    val name = remember(entity.customName, entity.friendlyName) {
        entity.customName.ifEmpty { entity.friendlyName }
    }
    val state = entity.state
    val isOpen = isCoverOpen(state)
    val isEnabled = state !in listOf("unavailable", "unknown")
    val position = remember(entity.attributes) { getCoverPosition(entity.attributes) }
    
    val supportsTilt = remember(entity.attributes) { supportsCoverTilt(entity.attributes) }
    val hasTiltState = remember(entity.attributes) { hasCoverTiltState(entity.attributes) }
    val shouldShowTiltControls = supportsTilt && hasTiltState
    
    val iconRes = remember(entity.id, entity.state, entity.customIconOnName, entity.customIconOffName) {
        EntityIconMapper.getFinalIconForEntity(entity) ?: R.drawable.ic_default
    }

    return CoverUiState(
        name = name, state = state, isOpen = isOpen, isEnabled = isEnabled,
        position = position, supportsTilt = supportsTilt, hasTiltState = hasTiltState,
        shouldShowTiltControls = shouldShowTiltControls, iconRes = iconRes
    )
}

/**
 * A composable card component that represents and controls a "Cover" entity (e.g., blinds, shutters, garage doors)
 * from Home Assistant.
 *
 * This card displays the current state (open/closed), position percentage, and entity icon.
 * It supports interactive states including:
 * - Single press: Executes the default action (toggle/open/close).
 * - Long press: Expands the card to reveal a control panel with specific actions (Open, Stop, Close).
 * - Tilt controls: Automatically displays tilt adjustment buttons if the entity supports them.
 *
 * @param entity The [EntityItem] data containing the state and attributes of the cover.
 * @param haClient The [HomeAssistantClient] used to send service calls (open, close, stop, tilt).
 * @param onStateColor A string identifier for the color to be used when the cover is in an "on" (open) state.
 * @param customOnStateColor An optional list of RGB integers for a custom "on" state color.
 * @param modifier The [Modifier] to be applied to the card layout.
 * @param isHorizontal Determines if the card should be rendered in a horizontal wide format or a standard vertical format.
 * @param isEntityInitialized A flag indicating if the entity data is fully loaded and ready for interaction.
 */
@Composable
fun CoverEntityCard(
    entity: EntityItem,
    haClient: HomeAssistantClient?,
    onStateColor: String,
    customOnStateColor: List<Int>? = null,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = false,
    isEntityInitialized: Boolean
) {
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
                try { closeBtnFocusRequester.requestFocus() } catch (e: Exception) { Log.e("CoverEntityCard", "Focus request failed", e) }
            }
        } else if (wasCardFocused.value) {
            delay(100)
            withContext(Dispatchers.Main.immediate) {
                try {
                    cardFocusRequester.requestFocus()
                    wasCardFocused.value = false
                } catch (e: Exception) { Log.e("CoverEntityCard", "Focus request failed", e) }
            }
        }
    }

    val uiState = rememberCoverUiState(entity)

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
    val offContentColor = colorResource(id = R.color.md_theme_onSurface)
    val disabledBackground = offBackgroundColor
    val disabledContent = offContentColor.copy(alpha = 0.2f)

    val animatedBackgroundColor by animateColorAsState(
        targetValue = when {
            !uiState.isEnabled -> disabledBackground
            uiState.isOpen -> onBackgroundColor
            else -> offBackgroundColor
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "coverBackgroundColor"
    )

    val animatedContentColor by animateColorAsState(
        targetValue = when {
            !uiState.isEnabled -> disabledContent
            uiState.isOpen -> onContentColor
            else -> offContentColor
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "coverContentColor"
    )

    val context = LocalContext.current
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
                enabled = !expanded && uiState.isEnabled,
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
        val iconModifier = Modifier.scale(iconScale)
        Box(
            modifier = Modifier
                .animateContentSize(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                .fillMaxSize()
        ) {
            key(expanded) {
                if (!isHorizontal) {
                    VerticalCoverContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false },
                        contentColor = animatedContentColor,
                        backgroundColor = animatedBackgroundColor,
                        closeBtnFocusRequester = closeBtnFocusRequester,
                        iconModifier = iconModifier
                    )
                } else {
                    HorizontalCoverContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false },
                        contentColor = animatedContentColor,
                        backgroundColor = animatedBackgroundColor,
                        closeBtnFocusRequester = closeBtnFocusRequester,
                        iconModifier = iconModifier
                    )
                }
            }
        }
    }
}

/**
 * A UI component that provides control buttons for a cover entity (e.g., blinds, curtains, or garage doors).
 *
 * This panel includes primary controls for opening, closing, and stopping the cover.
 * If the entity supports tilt functionality, it additionally displays controls for
 * opening, closing, and stopping the tilt. It also includes a close button to
 * collapse the expanded view.
 */
@Composable
private fun CoverControlPanel(
    entity: EntityItem,
    uiState: CoverUiState,
    haClient: HomeAssistantClient?,
    onClose: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester
) {
    var lastCommand by remember { mutableStateOf("") }
    var lastTiltCommand by remember { mutableStateOf("") }

    fun sendCommand(service: String) {
        haClient?.callService("cover", service, entity.id)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            fun toggleCover(command: String) {
                if (lastCommand == command) {
                    sendCommand("stop_cover"); lastCommand = ""
                } else {
                    sendCommand(command); lastCommand = command
                }
            }
            CoverControlButton(icon = Icons.Default.KeyboardArrowUp, contentDescription = "Open", isSelected = lastCommand == "open_cover", contentColor = contentColor, backgroundColor = backgroundColor, onClick = { toggleCover("open_cover") })
            CoverControlButton(icon = Icons.Default.Pause, contentDescription = "Stop", isSelected = false, contentColor = contentColor, backgroundColor = backgroundColor, onClick = { sendCommand("stop_cover"); lastCommand = "" })
            CoverControlButton(icon = Icons.Default.KeyboardArrowDown, contentDescription = "Close", isSelected = lastCommand == "close_cover", contentColor = contentColor, backgroundColor = backgroundColor, onClick = { toggleCover("close_cover") })
        }

        if (uiState.shouldShowTiltControls) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                fun toggleTiltCover(command: String) {
                    if (lastTiltCommand == command) {
                        sendCommand("stop_cover_tilt"); lastTiltCommand = ""
                    } else {
                        sendCommand(command); lastTiltCommand = command
                    }
                }
                CoverControlButton(icon = Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Open Tilt", isSelected = lastTiltCommand == "open_cover_tilt", contentColor = contentColor, backgroundColor = backgroundColor, onClick = { toggleTiltCover("open_cover_tilt") })
                CoverControlButton(icon = Icons.Default.Stop, contentDescription = "Stop Tilt", isSelected = false, contentColor = contentColor, backgroundColor = backgroundColor, onClick = { sendCommand("stop_cover_tilt"); lastTiltCommand = "" })
                CoverControlButton(icon = Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Close Tilt", isSelected = lastTiltCommand == "close_cover_tilt", contentColor = contentColor, backgroundColor = backgroundColor, onClick = { toggleTiltCover("close_cover_tilt") })
            }
        }
        
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


/**
 * Renders the internal content of the cover card in a vertical layout.
 *
 * This component displays the entity's icon, name, current state, and position percentage.
 * When [expanded] is true, it reveals a [CoverControlPanel] below the main information
 * to allow for manual adjustments of the cover and its tilt.
 */
@Composable
private fun VerticalCoverContent(
    entity: EntityItem,
    uiState: CoverUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    iconModifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = SafePainterResource(id = uiState.iconRes),
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
            Text(text = uiState.state.replaceFirstChar { it.uppercaseChar() }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (!uiState.state.equals("closed", ignoreCase = true)) {
                Text(text = "${uiState.position}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = contentColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            
            CoverControlPanel(
                entity = entity,
                uiState = uiState,
                haClient = haClient,
                onClose = onClose,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                closeBtnFocusRequester = closeBtnFocusRequester
            )
        }
    }
}

/**
 * Renders the internal content of the cover card in a horizontal layout.
 *
 * This component displays the entity's information (icon, name, state) in a sidebar-style
 * column. When [expanded] is true, it extends the width of the card and reveals the
 * [CoverControlPanel] to the right of the main information, separated by a vertical divider.
 */
@Composable
fun HorizontalCoverContent(
    entity: EntityItem,
    uiState: CoverUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    iconModifier: Modifier
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
        Column(
            modifier = Modifier.width(104.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = SafePainterResource(id = uiState.iconRes),
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
                Text(text = uiState.state.replaceFirstChar { it.uppercaseChar() }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (!uiState.state.equals("closed", ignoreCase = true)) {
                    Text(text = "${uiState.position}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (expanded) {
            VerticalDivider(modifier = Modifier.height(100.dp), color = contentColor.copy(alpha = 0.2f))
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                CoverControlPanel(
                    entity = entity,
                    uiState = uiState,
                    haClient = haClient,
                    onClose = onClose,
                    contentColor = contentColor,
                    backgroundColor = backgroundColor,
                    closeBtnFocusRequester = closeBtnFocusRequester
                )
            }
        }
    }
}

@Composable
fun CoverControlButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    contentColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        contentColor = contentColor,
        backgroundColor = backgroundColor,
        modifier = modifier,
        isSelected = isSelected
    )
}