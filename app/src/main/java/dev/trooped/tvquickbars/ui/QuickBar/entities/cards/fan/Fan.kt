package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.fan

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.trooped.tvquickbars.ui.AnimatedControlButton
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.ui.QuickBar.controls.PowerButton
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaLow
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaMedium
import dev.trooped.tvquickbars.ui.QuickBar.foundation.SafePainterResource
import dev.trooped.tvquickbars.ui.ValuePill
import dev.trooped.tvquickbars.utils.EntityActionExecutor
import dev.trooped.tvquickbars.utils.EntityStateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.text.ifEmpty

/**
 * UI state representation for a Fan entity.
 *
 * @property name The display name of the fan, favoring a custom name over the friendly name.
 * @property isOn Whether the fan is currently in the "on" state.
 * @property isEnabled Whether the fan is available and in a known state.
 * @property percentage The current speed of the fan expressed as a percentage (0-100).
 * @property percentageStep The amount by which the percentage changes per step, determined by the entity's attributes.
 * @property iconRes The resource ID of the icon to be displayed, based on state and custom icon settings.
 */
data class FanUiState(
    val name: String,
    val isOn: Boolean,
    val isEnabled: Boolean,
    val percentage: Int,
    val percentageStep: Double,
    val iconRes: Int
)

/**
 * Remembers and updates the UI state for a fan entity.
 *
 * This composable maps the raw [EntityItem] data into a [FanUiState] object, handling
 * the logic for the display name, power status, availability, speed percentage,
 * percentage step increments, and the appropriate icon resource.
 *
 * @param entity The fan entity from which to derive the state.
 * @return A [FanUiState] containing the processed properties for the UI.
 */
@Composable
fun rememberFanUiState(entity: EntityItem): FanUiState {
    val name = remember(entity.customName, entity.friendlyName) {
        entity.customName.ifEmpty { entity.friendlyName }
    }
    val isOn = entity.state == "on"
    val isEnabled = entity.state !in listOf("unavailable", "unknown")

    var percentage by remember { mutableIntStateOf(0) }
    LaunchedEffect(entity.id, entity.state, entity.attributes, entity.lastKnownState) {
        percentage = getFanPercentage(entity, isOn)
    }

    val percentageStep = remember(entity.id, entity.attributes, entity.lastKnownState) {
        getFanPercentageStep(entity)
    }

    val iconRes = remember(entity.id, entity.state, entity.customIconOnName, entity.customIconOffName) {
        EntityIconMapper.getFinalIconForEntity(entity) ?: R.drawable.ic_default
    }

    return FanUiState(name, isOn, isEnabled, percentage, percentageStep, iconRes)
}

/**
 * A highly interactive card component representing a Fan entity from Home Assistant.
 *
 * This card features:
 * - Real-time state updates (On/Off, Speed percentage).
 * - Animated fan icon that spins based on the current speed/percentage.
 * - Dynamic theming based on the entity state and custom color configurations.
 * - Expandable interface (Vertical or Horizontal) providing granular speed controls and power toggles.
 * - TV-optimized focus management and D-pad navigation support.
 *
 * @param entity The [EntityItem] data containing the fan's current state and attributes.
 * @param haClient The [HomeAssistantClient] used to dispatch service calls (e.g., set_percentage, toggle).
 * @param onStateColor A string identifier for the color to display when the fan is active.
 * @param customOnStateColor An optional list of RGB values used if [onStateColor] is set to "custom".
 * @param modifier The [Modifier] to be applied to the card's outer layout.
 * @param isHorizontal Determines if the card should use a wide horizontal layout or a standard vertical one.
 * @param isEntityInitialized A flag to prevent interactions before the entity data is fully loaded.
 */
@Composable
fun FanEntityCard(
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
        if (isFocused) {
            wasCardFocused.value = true
        }
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconPressAnim"
    )

    val context = LocalContext.current
    val savedEntitiesManager = SavedEntitiesManager(context)

    LaunchedEffect(entity.id, entity.state, entity.attributes) {
        if (entity.state == "on") {
            EntityStateUtils.captureFanSpeed(entity, savedEntitiesManager)
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            wasCardFocused.value = isFocused
            delay(50)
            bringIntoViewRequester.bringIntoView()
            delay(100)
            withContext(Dispatchers.Main.immediate) {
                try { closeBtnFocusRequester.requestFocus() } catch (e: Exception) { Log.e("FanEntityCard", "Focus request failed", e) }
            }
        } else if (wasCardFocused.value) {
            delay(100)
            withContext(Dispatchers.Main.immediate) {
                try {
                    cardFocusRequester.requestFocus()
                    wasCardFocused.value = false
                } catch (e: Exception) { Log.e("FanEntityCard", "Focus request failed", e) }
            }
        }
    }

    val uiState = rememberFanUiState(entity)

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
            uiState.isOn -> onBackgroundColor
            uiState.isEnabled -> offBackgroundColor
            else -> disabledBackground
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "fanBackgroundColor"
    )

    val animatedContentColor by animateColorAsState(
        targetValue = when {
            uiState.isOn -> onContentColor
            uiState.isEnabled -> offContentColor
            else -> disabledContent
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "fanContentColor"
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
                    VerticalFanContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false },
                        contentColor = animatedContentColor,
                        savedEntitiesManager = savedEntitiesManager,
                        closeBtnFocusRequester = closeBtnFocusRequester,
                        backgroundColor = animatedBackgroundColor,
                        iconModifier = iconModifier
                    )
                } else {
                    HorizontalFanContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false },
                        contentColor = animatedContentColor,
                        savedEntitiesManager = savedEntitiesManager,
                        closeBtnFocusRequester = closeBtnFocusRequester,
                        backgroundColor = animatedBackgroundColor,
                        iconModifier = iconModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalFanContent(
    entity: EntityItem,
    uiState: FanUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    contentColor: Color,
    closeBtnFocusRequester: FocusRequester,
    savedEntitiesManager: SavedEntitiesManager,
    backgroundColor: Color,
    iconModifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FanIcon(
            isOn = uiState.isOn,
            percentage = uiState.percentage,
            iconRes = uiState.iconRes,
            contentColor = contentColor,
            iconModifier = iconModifier
        )

        Text(
            text = uiState.name,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 2.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.isOn) {
                Text(text = "${uiState.percentage}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(Modifier, DividerDefaults.Thickness, color = contentColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            FanSpeedControl(
                entity = entity,
                percentage = uiState.percentage,
                stepPercent = uiState.percentageStep,
                haClient = haClient,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                isCompact = false
            )

            Spacer(modifier = Modifier.height(8.dp))
            PowerButton(
                isOn = uiState.isOn,
                onClick = { EntityStateUtils.toggleFanWithMemory(entity = entity, haClient = haClient, savedEntitiesManager = savedEntitiesManager) },
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
private fun HorizontalFanContent(
    entity: EntityItem,
    uiState: FanUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    contentColor: Color,
    closeBtnFocusRequester: FocusRequester,
    savedEntitiesManager: SavedEntitiesManager,
    backgroundColor: Color,
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
        Column(
            modifier = Modifier.width(104.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FanIcon(
                isOn = uiState.isOn,
                percentage = uiState.percentage,
                iconRes = uiState.iconRes,
                contentColor = contentColor,
                iconModifier = iconModifier
            )

            Text(
                text = uiState.name,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isOn) {
                    Text(text = "${uiState.percentage}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (expanded) {
            Box(modifier = Modifier.width(1.dp).height(100.dp).background(contentColor.copy(alpha = 0.2f)))

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(end = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FanSpeedControl(
                        entity = entity,
                        percentage = uiState.percentage,
                        stepPercent = uiState.percentageStep,
                        haClient = haClient,
                        contentColor = contentColor,
                        backgroundColor = backgroundColor,
                        isCompact = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    PowerButton(
                        isOn = uiState.isOn,
                        onClick = { EntityStateUtils.toggleFanWithMemory(entity = entity, haClient = haClient, savedEntitiesManager = savedEntitiesManager) },
                        contentColor = contentColor,
                        backgroundColor = backgroundColor,
                        size = 36.dp
                    )
                }

                val closeBtnInteraction = remember { MutableInteractionSource() }
                val closeIsFocused by closeBtnInteraction.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
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
            }
        }
    }
}

/**
 * A composable component that provides UI controls for adjusting the fan speed.
 *
 * It features decrement and increment buttons that calculate the new percentage
 * based on the provided step value and communicates the change to Home Assistant.
 */
@Composable
private fun FanSpeedControl(
    entity: EntityItem,
    percentage: Int,
    stepPercent: Double,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    isCompact: Boolean = false
) {
    val step = stepPercent.coerceAtLeast(1.0)
    val displayW = if (isCompact) 50.dp else 60.dp
    val displayH = if (isCompact) 36.dp else 40.dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedControlButton(
            onClick = {
                val newPercentage = (percentage.toDouble() - step).coerceIn(0.0, 100.0).toInt()
                haClient?.callService("fan", "set_percentage", entity.id, JSONObject().apply { put("percentage", newPercentage) })
            },
            contentColor = contentColor,
            backgroundColor = backgroundColor,
            size = if (isCompact) 36.dp else 40.dp
        ) { isFocused, _, animationScale, contentColor, backgroundColor ->
            Box(modifier = Modifier.scale(animationScale), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(if (isCompact) 14.dp else 16.dp).height(2.dp).background(if (isFocused) backgroundColor else contentColor, RoundedCornerShape(1.dp)))
            }
        }

        ValuePill(
            text = "$percentage%",
            contentColor = contentColor,
            backgroundColor = backgroundColor,
            minWidth = displayW,
            height = displayH
        )

        AnimatedControlButton(
            onClick = {
                val newPercentage = (percentage.toDouble() + step).coerceIn(0.0, 100.0).toInt()
                haClient?.callService("fan", "set_percentage", entity.id, JSONObject().apply { put("percentage", newPercentage) })
            },
            contentColor = contentColor,
            backgroundColor = backgroundColor,
            size = if (isCompact) 36.dp else 40.dp
        ) { isFocused, _, animationScale, contentColor, backgroundColor ->
            Box(modifier = Modifier.scale(animationScale), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(if (isCompact) 14.dp else 16.dp).height(2.dp).background(if (isFocused) backgroundColor else contentColor, RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.width(2.dp).height(if (isCompact) 14.dp else 16.dp).background(if (isFocused) backgroundColor else contentColor, RoundedCornerShape(1.dp)))
            }
        }
    }
}

/**
 * Renders the fan icon with an optional spinning animation based on the fan's state.
 *
 * If the fan is currently [isOn] and uses a supported fan icon resource, it will
 * rotate continuously. The speed of the rotation is dynamically calculated based
 * on the [percentage] value, where a higher percentage results in a faster spin.
 */
@Composable
private fun FanIcon(
    isOn: Boolean,
    percentage: Int,
    @DrawableRes iconRes: Int,
    contentColor: Color,
    iconModifier: Modifier
) {
    val shouldSpin = isOn && (iconRes == R.drawable.fan || iconRes == R.drawable.ic_fan)

    val rotation by if (shouldSpin) {
        val infinite = rememberInfiniteTransition(label = "fanSpin")
        val duration = (2000 - (percentage * 15)).coerceIn(500, 2000)
        infinite.animateFloat(
            0f, 360f,
            animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart),
            label = "fanDegree"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Image(
        painter = SafePainterResource(id = iconRes),
        contentDescription = "Fan Icon",
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer { rotationZ = rotation }
            .then(iconModifier),
        colorFilter = ColorFilter.tint(contentColor)
    )
}