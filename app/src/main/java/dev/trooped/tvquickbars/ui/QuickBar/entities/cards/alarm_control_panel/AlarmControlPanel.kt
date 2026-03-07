package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.alarm_control_panel

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.PressType
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.ui.AnimatedActionButton
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaLow
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaMedium
import dev.trooped.tvquickbars.ui.QuickBar.foundation.SafePainterResource
import dev.trooped.tvquickbars.utils.EntityActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.text.ifEmpty

data class AlarmUiState(
    val name: String,
    val state: String,
    val formattedState: String,
    val isArmed: Boolean,
    val isEnabled: Boolean,
    val requiresCode: Boolean,
    val codeFormat: String?,
    val availableTabs: List<String>,
    val iconRes: Int
)

/**
 * Remembers and computes the [AlarmUiState] for a given [EntityItem], providing a reactive
 * state object that simplifies the UI logic for the Alarm Control Panel.
 *
 * This function handles:
 * - Determining the display name (custom vs. friendly).
 * - Evaluating the current armed/enabled status.
 * - Checking for security requirements like PIN codes and code formats.
 * - Calculating available control tabs based on the current entity state.
 * - Resolving the appropriate icon resource using the [EntityIconMapper].
 *
 * @param entity The [EntityItem] representing the alarm control panel from Home Assistant.
 * @return An [AlarmUiState] containing the processed properties needed to render the card.
 */
@Composable
fun rememberAlarmUiState(entity: EntityItem): AlarmUiState {
    val name = remember(entity.customName, entity.friendlyName) {
        entity.customName.ifEmpty { entity.friendlyName }
    }
    val state = entity.state
    val isArmed = remember(state) { isAlarmArmed(state) }
    val isEnabled = state !in listOf("unavailable", "unknown")
    val formattedState = remember(state) { formatAlarmState(state) }
    val availableTabs = remember(state) { getAlarmAvailableTabs(state) }

    val attributes = entity.attributes ?: JSONObject()
    val codeFormat = attributes.optString("code_format", null)
    val requiresCode = codeFormat != null

    val iconRes = remember(entity.id, entity.state, entity.customIconOnName, entity.customIconOffName) {
        EntityIconMapper.getFinalIconForEntity(entity) ?: R.drawable.ic_default
    }

    return AlarmUiState(
        name = name,
        state = state,
        formattedState = formattedState,
        isArmed = isArmed,
        isEnabled = isEnabled,
        requiresCode = requiresCode,
        codeFormat = codeFormat,
        availableTabs = availableTabs,
        iconRes = iconRes
    )
}

/**
 * A Composable card component that provides a user interface for viewing and controlling
 * Home Assistant Alarm Control Panel entities.
 *
 * This card supports multiple alarm states (disarmed, armed_home, armed_away, triggered, arming)
 * with dynamic color feedback and animations. It handles PIN/code entry requirements,
 * service calls for arming/disarming, and provides both vertical and horizontal layouts
 * optimized for TV interfaces.
 *
 * @param entity The [EntityItem] representing the alarm control panel state and attributes.
 * @param haClient The [HomeAssistantClient] used to dispatch arm/disarm service calls.
 * @param modifier The [Modifier] to be applied to the card's outer container.
 * @param isHorizontal Determines if the card should render in a wide horizontal format or a standard vertical one.
 * @param onStateColor The theme color key to use when the alarm is in an "active" (armed) state.
 * @param customOnStateColor An optional list of RGB values to use for the armed state if [onStateColor] is set to "custom".
 * @param isEntityInitialized Boolean flag indicating if the entity data is ready for user interaction.
 */
@Composable
fun AlarmControlPanelEntityCard(
    entity: EntityItem,
    haClient: HomeAssistantClient?,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = false,
    onStateColor: String = "colorPrimary",
    customOnStateColor: List<Int>? = null,
    isEntityInitialized: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    val cardFocusRequester = remember { FocusRequester() }
    val closeBtnFocusRequester = remember { FocusRequester() }
    val wasCardFocused = remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    
    var isPinDismissed by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) wasCardFocused.value = true
    }

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
                try { closeBtnFocusRequester.requestFocus() } catch (e: Exception) { Log.e("AlarmEntityCard", "Focus request failed", e) }
            }
        } else if (wasCardFocused.value) {
            delay(100)
            withContext(Dispatchers.Main.immediate) {
                try {
                    cardFocusRequester.requestFocus()
                    wasCardFocused.value = false
                } catch (e: Exception) { Log.e("AlarmEntityCard", "Focus request failed", e) }
            }
        }
    }

    LaunchedEffect(expanded, isPinDismissed) {
        if (!expanded && isPinDismissed) {
            isPinDismissed = false
            delay(300)
            try { cardFocusRequester.requestFocus() } catch (e: Exception) { Log.e("AlarmCard", "Focus request failed: ${e.message}") }
        }
    }

    val uiState = rememberAlarmUiState(entity)

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
    val offContentColor = colorResource(id = R.color.md_theme_onSurfaceVariant)
    val errorColor = colorResource(id = R.color.md_theme_error)
    val errorContentColor = colorResource(id = R.color.md_theme_onError)

    val animatedBackgroundColor by animateColorAsState(
        targetValue = when {
            !uiState.isEnabled -> offBackgroundColor
            uiState.state == "triggered" -> errorColor
            uiState.isArmed -> onBackgroundColor
            else -> offBackgroundColor
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "alarmBackgroundColor"
    )

    val animatedContentColor by animateColorAsState(
        targetValue = when {
            !uiState.isEnabled -> offContentColor.copy(alpha = 0.2f)
            uiState.state == "triggered" -> errorContentColor
            uiState.isArmed -> onContentColor
            else -> offContentColor
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "alarmContentColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "alarmPulse")
    val animatedAlpha = if (uiState.state == "triggered") {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(animation = tween(500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
            label = "alarmPulseAnimation"
        ).value
    } else 1f

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
            .bringIntoViewRequester(bringIntoViewRequester)
            .alpha(animatedAlpha),
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
                    VerticalAlarmContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false; isPinDismissed = true },
                        contentColor = animatedContentColor,
                        backgroundColor = animatedBackgroundColor,
                        closeBtnFocusRequester = closeBtnFocusRequester,
                        iconModifier = Modifier.scale(iconScale)
                    )
                } else {
                    HorizontalAlarmContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false; isPinDismissed = true },
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
 * A composable function that renders the interactive control section for an alarm entity.
 *
 * This section manages the user interface for arming and disarming the alarm, including:
 * - Tab selection for different alarm modes (e.g., Arm Home, Arm Away).
 * - PIN/Code entry fields with validation feedback and shake animations.
 * - Integration with [HomeAssistantClient] to execute alarm service calls.
 * - Dynamic layout adjustment for compact or expanded views.
 *
 * @param entity The [EntityItem] representing the alarm control panel.
 * @param uiState The current processed UI state of the alarm.
 * @param haClient The client used to send service commands to Home Assistant.
 * @param contentColor The color used for text, icons, and borders.
 * @param backgroundColor The primary background color of the container.
 * @param closeBtnFocusRequester A [FocusRequester] used to manage focus transitions back to the close button.
 * @param isCompact Whether to render a smaller, more condensed version of the controls (used in horizontal layouts).
 * @param modifier The modifier to be applied to the layout.
 */
@Composable
private fun AlarmControlsSection(
    entity: EntityItem,
    uiState: AlarmUiState,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var pinCode by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var isPinIncorrect by remember { mutableStateOf(false) }
    var lastSubmittedPin by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    fun submitPinCode() {
        if (pinCode.isEmpty()) {
            isPinIncorrect = true
            return
        }

        val actionType = if (uiState.availableTabs.size <= 1)
            uiState.availableTabs.firstOrNull() ?: "Disarm"
        else
            uiState.availableTabs[selectedTab]

        val service = when (actionType) {
            "Arm Home" -> "alarm_arm_home"
            "Arm Away" -> "alarm_arm_away"
            else -> "alarm_disarm"
        }

        haClient?.callService("alarm_control_panel", service, entity.id, JSONObject().apply { put("code", pinCode) })
        lastSubmittedPin = pinCode
        pinCode = ""
    }

    LaunchedEffect(showPin) {
        if (!showPin) {
            try {
                delay(100)
                closeBtnFocusRequester.requestFocus()
            } catch (e: Exception) { Log.e("AlarmPanel", "Focus request failed: ${e.message}") }
        }
    }

    LaunchedEffect(uiState.state) {
        if (lastSubmittedPin != null) {
            when {
                uiState.state == "triggered" || uiState.state == "disarmed" -> {
                    isPinIncorrect = true
                }
                uiState.state.startsWith("armed_") -> {
                    showPin = false
                }
            }
            lastSubmittedPin = null
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.availableTabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.availableTabs.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    val tabInteraction = remember { MutableInteractionSource() }
                    val tabFocused by tabInteraction.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(when { tabFocused -> contentColor; isSelected -> contentColor.copy(alpha = 0.2f); else -> contentColor.copy(alpha = 0.05f) })
                            .border(width = if (isSelected) 2.dp else 1.dp, color = if (tabFocused) contentColor else contentColor.copy(alpha = if (isSelected) 0.8f else 0.3f), shape = RoundedCornerShape(16.dp))
                            .clickable(interactionSource = tabInteraction, indication = ripple(), onClick = { selectedTab = index })
                            .focusable(interactionSource = tabInteraction)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = label, fontSize = 12.sp, color = if (tabFocused) backgroundColor else contentColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        val actionType = if (uiState.availableTabs.size <= 1) uiState.availableTabs.firstOrNull() ?: "Disarm" else uiState.availableTabs[selectedTab]

        if (uiState.requiresCode && showPin) {
            PinEntryField(
                value = pinCode,
                onValueChange = { pinCode = it },
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                isNumeric = uiState.codeFormat == "number",
                placeholder = if (isCompact) "PIN" else (if (uiState.codeFormat == "number") "Enter PIN" else "Enter Code"),
                isCompact = isCompact,
                isIncorrect = isPinIncorrect,
                onIncorrectAnimationFinished = { isPinIncorrect = false },
                onSubmit = ::submitPinCode
            )
            
            if (!isCompact) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    AlarmActionButton(
                        text = actionType,
                        icon = null,
                        contentColor = contentColor,
                        backgroundColor = backgroundColor,
                        onClick = ::submitPinCode,
                        enabled = pinCode.isNotEmpty()
                    )
                }
            }
        } else if (uiState.requiresCode && !showPin) {
            val buttonText = if (actionType == "Disarm") "Disarm" else "Enter PIN"
            AlarmActionButton(
                text = buttonText,
                icon = if (!isCompact) Icons.Default.OpenInBrowser else null,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                onClick = { showPin = true },
                isCompact = isCompact
            )
        } else {
            AlarmActionButton(
                text = actionType,
                icon = null,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                onClick = {
                    val service = when (actionType) {
                        "Arm Home" -> "alarm_arm_home"
                        "Arm Away" -> "alarm_arm_away"
                        else -> "alarm_disarm"
                    }
                    haClient?.callService("alarm_control_panel", service, entity.id)
                },
                isCompact = isCompact
            )
        }
    }
}

/**
 * Renders the vertical layout for the Alarm Control Panel card.
 *
 * Displays the entity icon, name, and state. When [expanded], shows
 * [AlarmControlsSection] and a close button.
 */
@Composable
private fun VerticalAlarmContent(
    entity: EntityItem,
    uiState: AlarmUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    iconModifier: Modifier
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

        Text(text = uiState.formattedState, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

        if (uiState.state == "arming") {
            Spacer(modifier = Modifier.height(4.dp))
            DotsLoader(color = contentColor)
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(Modifier, DividerDefaults.Thickness, color = contentColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            AlarmControlsSection(
                entity = entity,
                uiState = uiState,
                haClient = haClient,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                closeBtnFocusRequester = closeBtnFocusRequester,
                isCompact = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

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
 * Renders the horizontal layout for the [AlarmControlPanelEntityCard].
 *
 * This view displays the alarm entity's information in a row-based format, which is particularly
 * useful for wide screens or side-by-side card layouts. When [expanded] is true, it provides
 * a side-by-side view of the entity status and the interactive [AlarmControlsSection].
 */
@Composable
private fun HorizontalAlarmContent(
    entity: EntityItem,
    uiState: AlarmUiState,
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

            Text(text = uiState.formattedState, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            if (uiState.state == "arming") {
                Spacer(modifier = Modifier.height(4.dp))
                DotsLoader(color = contentColor)
            }
        }

        if (expanded) {
            Box(modifier = Modifier.width(1.dp).height(100.dp).background(contentColor.copy(alpha = 0.2f)))

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val scrollState = rememberScrollState()
                
                AlarmControlsSection(
                    entity = entity,
                    uiState = uiState,
                    haClient = haClient,
                    contentColor = contentColor,
                    backgroundColor = backgroundColor,
                    closeBtnFocusRequester = closeBtnFocusRequester,
                    isCompact = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 32.dp)
                        .verticalScroll(scrollState)
                )

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
 * A composable function that provides a secure input field for entering an alarm PIN or code.
 *
 * This component handles numeric or alphanumeric input, provides a toggle for visibility (in non-compact mode),
 * and includes a shake animation to indicate incorrect entries.
 *
 */
@Composable
private fun PinEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    isNumeric: Boolean,
    placeholder: String = "Enter PIN",
    isCompact: Boolean = false,
    isIncorrect: Boolean = false,
    onIncorrectAnimationFinished: () -> Unit = {},
    onSubmit: () -> Unit = {}
) {
    var hidePIN by remember { mutableStateOf(true) }
    val pinFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            delay(150)
            pinFieldFocusRequester.requestFocus()
        } catch (e: Exception) { Log.e("PinEntryField", "Failed to request focus: ${e.message}") }
    }

    val offset = remember { Animatable(0f) }
    LaunchedEffect(isIncorrect) {
        if (isIncorrect) {
            val shakePattern = listOf(0f, -15f, 15f, -10f, 10f, -5f, 5f, 0f)
            shakePattern.forEach { position -> offset.animateTo(targetValue = position, animationSpec = tween(100, easing = LinearEasing)) }
            onIncorrectAnimationFinished()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().offset(x = offset.value.dp)
    ) {
        Box(
            modifier = Modifier
                .height(if (isCompact) 40.dp else 48.dp)
                .width(if (isCompact) 120.dp else 160.dp)
                .border(width = 1.dp, color = if (isIncorrect) colorResource(id = R.color.md_theme_error) else contentColor.copy(alpha = AlphaMedium), shape = RoundedCornerShape(8.dp))
                .background(contentColor.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box {
                if (value.isEmpty()) {
                    Text(text = placeholder, color = contentColor.copy(alpha = 0.5f), fontSize = if (isCompact) 13.sp else 14.sp)
                }

                val keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Password)

                BasicTextField(
                    value = value,
                    onValueChange = { newValue -> if (isNumeric && newValue.all { it.isDigit() } || !isNumeric) onValueChange(newValue) },
                    textStyle = TextStyle(color = contentColor, fontSize = if (isCompact) 13.sp else 14.sp),
                    keyboardOptions = keyboardOptions,
                    visualTransformation = if (hidePIN) PasswordVisualTransformation() else VisualTransformation.None,
                    singleLine = true,
                    modifier = Modifier.focusRequester(pinFieldFocusRequester)
                )
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(if (isCompact) 40.dp else 48.dp)
                .clip(CircleShape)
                .background(if (isFocused) contentColor else contentColor.copy(alpha = 0.1f))
                .border(width = if (isFocused) 2.dp else 1.dp, color = if (isFocused) contentColor else contentColor.copy(alpha = 0.3f), shape = CircleShape)
                .clickable(interactionSource = interactionSource, indication = ripple(bounded = true), onClick = { if (isCompact) onSubmit() else hidePIN = !hidePIN })
                .focusable(interactionSource = interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCompact) Icons.Default.CheckCircle else if (hidePIN) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (isCompact) "Submit PIN" else if (hidePIN) "Show PIN" else "Hide PIN",
                tint = if (isFocused) backgroundColor else contentColor,
                modifier = Modifier.size(if (isCompact) 18.dp else 24.dp)
            )
        }
    }
}

@Composable
private fun AlarmActionButton(
    text: String,
    icon: ImageVector?,
    contentColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isCompact: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    AnimatedActionButton(
        text = text,
        icon = icon,
        onClick = onClick,
        contentColor = contentColor,
        backgroundColor = backgroundColor,
        enabled = enabled,
        isCompact = isCompact,
        focusRequester = focusRequester
    )
}

@Composable
fun DotsLoader(color: Color, modifier: Modifier = Modifier, dotSize: Dp = 6.dp, space: Dp = 4.dp) {
    val infiniteTransition = rememberInfiniteTransition()
    val delays = listOf(0, 100, 200)

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(space)) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes { durationMillis = 800; 1.2f at delays[index] + 200; 0.5f at delays[index] + 600 },
                    repeatMode = RepeatMode.Restart
                ),
                label = "dotScale$index"
            )

            Box(modifier = Modifier.size(dotSize).scale(scale).background(color = color, shape = CircleShape))
        }
    }
}