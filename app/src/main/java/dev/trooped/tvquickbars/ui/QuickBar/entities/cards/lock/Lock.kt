package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.lock

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
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
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MeetingRoom
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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlin.text.ifEmpty

/**
 * Represents the UI state for a lock entity, encapsulating all necessary information
 * to render the lock card and its controls.
 *
 * @property name The display name of the lock, prioritizing custom names over friendly names.
 * @property state The raw state string received from Home Assistant (e.g., "locked", "unlocked", "jammed").
 * @property formattedState A user-friendly, localized string representing the current state.
 * @property isLocked True if the entity is currently in a "locked" state.
 * @property isJammed True if the lock reports a "jammed" condition, requiring user attention.
 * @property isEnabled True if the entity is reachable and not in an "unavailable" or "unknown" state.
 * @property batteryLevel The battery percentage of the device, or -1 if not available.
 * @property supportsOpen True if the lock entity supports the "open" (unlatch) service.
 * @property iconRes The resource ID of the icon to be displayed, based on the entity type and state.
 */
data class LockUiState(
    val name: String,
    val state: String,
    val formattedState: String,
    val isLocked: Boolean,
    val isJammed: Boolean,
    val isEnabled: Boolean,
    val batteryLevel: Int,
    val supportsOpen: Boolean,
    val iconRes: Int
)

/**
 * Remembers and computes the UI state for a lock entity, transforming the raw [EntityItem]
 * data into a structured [LockUiState].
 *
 * This function handles the logic for:
 * - Determining the display name (preferring custom names over friendly names).
 * - Calculating the availability and lock status (locked, jammed, etc.).
 * - Formatting the state string for display.
 * - Extracting battery levels and supported features (like the 'open' command) from attributes.
 * - Resolving the appropriate icon resource based on the current state.
 *
 * @param entity The [EntityItem] representing the lock from Home Assistant.
 * @return A [LockUiState] containing the processed information required to render the lock card.
 */
@Composable
fun rememberLockUiState(entity: EntityItem): LockUiState {
    val name = remember(entity.customName, entity.friendlyName) {
        entity.customName.ifEmpty { entity.friendlyName }
    }
    val state = entity.state
    val isEnabled = state !in listOf("unavailable", "unknown")
    
    val formattedState = remember(state) { formatLockState(state) }
    val isLocked = remember(state) { isLockLocked(state) }
    val isJammed = remember(state) { isLockJammed(state) }
    
    val batteryLevel = remember(entity.attributes) { getLockBatteryLevel(entity) }
    val supportsOpen = remember(entity.attributes) { supportsLockOpen(entity) }
    
    val iconRes = remember(entity.id, entity.state, entity.customIconOnName, entity.customIconOffName) {
        EntityIconMapper.getFinalIconForEntity(entity) ?: R.drawable.ic_default
    }

    return LockUiState(
        name = name, state = state, formattedState = formattedState,
        isLocked = isLocked, isJammed = isJammed, isEnabled = isEnabled,
        batteryLevel = batteryLevel, supportsOpen = supportsOpen, iconRes = iconRes
    )
}

/**
 * A composable function that renders a UI card for a lock entity, supporting both vertical and horizontal layouts.
 *
 * This card displays the current state of the lock (locked, unlocked, jammed, etc.), battery levels,
 * and provides an expanded view for control actions such as locking, unlocking, or opening.
 *
 * @param entity The [EntityItem] representing the lock device from Home Assistant.
 * @param haClient The [HomeAssistantClient] used to dispatch service calls (lock/unlock).
 * @param onStateColor A string identifier for the primary color to use when the entity is in an "active" state.
 * @param customOnStateColor An optional list of RGB integers for a custom active state color.
 * @param modifier The [Modifier] to be applied to the card.
 * @param isHorizontal Boolean flag to determine if the card should be rendered in a horizontal orientation.
 * @param isEntityInitialized Boolean flag indicating if the entity data is fully loaded and ready for interaction.
 */
@Composable
fun LockEntityCard(
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
    val lockBtnFocusRequester = remember { FocusRequester() }
    val unlockBtnFocusRequester = remember { FocusRequester() }
    var lastFocusedButton by remember { mutableStateOf<FocusRequester?>(null) }

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
                try { closeBtnFocusRequester.requestFocus() } catch (e: Exception) { Log.e("LockEntityCard", "Focus request failed", e) }
            }
        } else if (wasCardFocused.value) {
            delay(100)
            withContext(Dispatchers.Main.immediate) {
                try {
                    cardFocusRequester.requestFocus()
                    wasCardFocused.value = false
                } catch (e: Exception) { Log.e("LockEntityCard", "Focus request failed", e) }
            }
        }
    }

    val uiState = rememberLockUiState(entity)

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
            uiState.isJammed -> errorColor
            uiState.isLocked -> onBackgroundColor
            else -> offBackgroundColor
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "lockBackgroundColor"
    )

    val animatedContentColor by animateColorAsState(
        targetValue = when {
            !uiState.isEnabled -> offContentColor.copy(alpha = 0.2f)
            uiState.isJammed -> errorContentColor
            uiState.isLocked -> onContentColor
            else -> offContentColor
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "lockContentColor"
    )

    val context = LocalContext.current
    val savedEntitiesManager = remember { SavedEntitiesManager(context) }

    val handlePress: (PressType) -> Unit = remember(entity, haClient, savedEntitiesManager, isEntityInitialized) {
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
                    VerticalLockContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false },
                        contentColor = animatedContentColor,
                        backgroundColor = animatedBackgroundColor,
                        closeBtnFocusRequester = closeBtnFocusRequester,
                        lockBtnFocusRequester = lockBtnFocusRequester,
                        unlockBtnFocusRequester = unlockBtnFocusRequester,
                        lastFocusedButton = { requester -> lastFocusedButton = requester },
                        iconModifier = iconModifier
                    )
                } else {
                    HorizontalLockContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false },
                        contentColor = animatedContentColor,
                        backgroundColor = animatedBackgroundColor,
                        closeBtnFocusRequester = closeBtnFocusRequester,
                        lockBtnFocusRequester = lockBtnFocusRequester,
                        unlockBtnFocusRequester = unlockBtnFocusRequester,
                        lastFocusedButton = { requester -> lastFocusedButton = requester },
                        iconModifier = iconModifier
                    )
                }
            }
        }
    }
}

/**
 * Renders the control buttons for the Lock entity, such as Lock, Unlock, and Open.
 *
 * This section dynamically determines which buttons to display based on the current
 * [uiState] of the lock (e.g., hiding the "Lock" button if the entity is already locked).
 * It manages focus transitions between action buttons and handles service calls to Home Assistant.
 *
 */
@Composable
private fun LockControlsSection(
    entity: EntityItem,
    uiState: LockUiState,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    lockBtnFocusRequester: FocusRequester,
    unlockBtnFocusRequester: FocusRequester,
    closeBtnFocusRequester: FocusRequester,
    lastFocusedButton: (FocusRequester) -> Unit,
    isCompact: Boolean = false
) {
    val buttonsToShow = mutableListOf<@Composable () -> Unit>()

    if (uiState.state != "locked" && uiState.state != "locking") {
        buttonsToShow.add {
            LockActionButton(
                text = "Lock",
                icon = Icons.Filled.Lock,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                onClick = {
                    lastFocusedButton(lockBtnFocusRequester)
                    haClient?.callService("lock", "lock", entity.id)
                    if (uiState.state == "unlocked") {
                        try { unlockBtnFocusRequester.requestFocus() } catch (e: Exception) { closeBtnFocusRequester.requestFocus() }
                    }
                },
                isCompact = isCompact,
                focusRequester = lockBtnFocusRequester
            )
        }
    }

    if (uiState.state != "unlocked" && uiState.state != "unlocking") {
        buttonsToShow.add {
            LockActionButton(
                text = "Unlock",
                icon = Icons.Filled.LockOpen,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                onClick = {
                    lastFocusedButton(unlockBtnFocusRequester)
                    haClient?.callService("lock", "unlock", entity.id)
                    if (uiState.state == "locked") {
                        try { lockBtnFocusRequester.requestFocus() } catch (e: Exception) { closeBtnFocusRequester.requestFocus() }
                    }
                },
                isCompact = isCompact,
                focusRequester = unlockBtnFocusRequester
            )
        }
    }

    if (uiState.supportsOpen && uiState.state == "unlocked") {
        buttonsToShow.add {
            LockActionButton(
                text = "Open",
                icon = Icons.Default.MeetingRoom,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                onClick = { haClient?.callService("lock", "open", entity.id) },
                isCompact = isCompact
            )
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        buttonsToShow.forEach { it() }
    }
}

@Composable
private fun VerticalLockContent(
    entity: EntityItem,
    uiState: LockUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    lockBtnFocusRequester: FocusRequester,
    unlockBtnFocusRequester: FocusRequester,
    lastFocusedButton: (FocusRequester) -> Unit,
    iconModifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedLockIcon(lockState = uiState.state, iconRes = uiState.iconRes, contentColor = contentColor, modifier = iconModifier)

        Text(text = uiState.name, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))

        Text(text = uiState.formattedState, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

        if (uiState.batteryLevel >= 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                val batteryColor = when {
                    uiState.batteryLevel <= 20 -> colorResource(id = R.color.md_theme_error)
                    uiState.batteryLevel <= 40 -> colorResource(id = R.color.md_theme_Amber500)
                    else -> contentColor
                }
                Icon(imageVector = if (uiState.batteryLevel <= 20) Icons.Filled.BatteryAlert else Icons.Filled.Battery6Bar, contentDescription = "Battery Level", tint = batteryColor, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "${uiState.batteryLevel}%", fontSize = 10.sp, color = batteryColor)
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(Modifier, DividerDefaults.Thickness, color = contentColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            LockControlsSection(
                entity = entity, uiState = uiState, haClient = haClient, contentColor = contentColor, backgroundColor = backgroundColor,
                lockBtnFocusRequester = lockBtnFocusRequester, unlockBtnFocusRequester = unlockBtnFocusRequester, closeBtnFocusRequester = closeBtnFocusRequester,
                lastFocusedButton = lastFocusedButton, isCompact = false
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

@Composable
private fun HorizontalLockContent(
    entity: EntityItem,
    uiState: LockUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester,
    lockBtnFocusRequester: FocusRequester,
    unlockBtnFocusRequester: FocusRequester,
    lastFocusedButton: (FocusRequester) -> Unit,
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
            AnimatedLockIcon(lockState = uiState.state, iconRes = uiState.iconRes, contentColor = contentColor, modifier = iconModifier)
            Text(text = uiState.name, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
            Text(text = uiState.formattedState, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            if (uiState.batteryLevel >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(top = 2.dp)) {
                    val batteryColor = when {
                        uiState.batteryLevel <= 20 -> colorResource(id = R.color.md_theme_error)
                        uiState.batteryLevel <= 40 -> colorResource(id = R.color.md_theme_Amber500)
                        else -> contentColor
                    }
                    Icon(imageVector = if (uiState.batteryLevel <= 20) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull, contentDescription = "Battery Level", tint = batteryColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "${uiState.batteryLevel}%", fontSize = 10.sp, color = batteryColor)
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
                    LockControlsSection(
                        entity = entity, uiState = uiState, haClient = haClient, contentColor = contentColor, backgroundColor = backgroundColor,
                        lockBtnFocusRequester = lockBtnFocusRequester, unlockBtnFocusRequester = unlockBtnFocusRequester, closeBtnFocusRequester = closeBtnFocusRequester,
                        lastFocusedButton = lastFocusedButton, isCompact = true
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
 * Displays an icon that animates its transition based on the lock's state changes.
 *
 * It uses [AnimatedContent] to provide visual feedback (sliding and fading) when the
 * state transitions between locked and unlocked, or during the locking/unlocking process.
 *
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedLockIcon(lockState: String, iconRes: Int, contentColor: Color, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val currentState = rememberUpdatedState(lockState)

    AnimatedContent(
        targetState = currentState.value,
        label = "lockStateAnimation",
        transitionSpec = {
            if (targetState == "locked" && initialState == "unlocked") {
                slideInVertically { height -> -height } + fadeIn() with slideOutVertically { height -> height } + fadeOut()
            } else if (targetState == "unlocked" && initialState == "locked") {
                slideInVertically { height -> height } + fadeIn() with slideOutVertically { height -> -height } + fadeOut()
            } else {
                fadeIn() with fadeOut()
            }
        }
    ) { state ->
        val currentIcon = when (state) {
            "locked" -> R.drawable.lock_outline
            "unlocked" -> R.drawable.lock_open_outline
            "locking" -> R.drawable.lock_outline
            "unlocking" -> R.drawable.lock_open_outline
            else -> iconRes
        }

        Image(
            painter = SafePainterResource(id = currentIcon),
            contentDescription = state.replaceFirstChar { it.uppercase() },
            modifier = modifier.size(size),
            colorFilter = ColorFilter.tint(contentColor)
        )
    }
}

@Composable
private fun LockActionButton(
    text: String, icon: ImageVector, contentColor: Color, backgroundColor: Color, onClick: () -> Unit,
    enabled: Boolean = true, isCompact: Boolean = false, focusRequester: FocusRequester? = null
) {
    AnimatedActionButton(
        text = text, icon = icon, onClick = onClick, contentColor = contentColor, backgroundColor = backgroundColor,
        enabled = enabled, isCompact = isCompact, focusRequester = focusRequester
    )
}