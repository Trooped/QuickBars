package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.media_player

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
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
import dev.trooped.tvquickbars.ui.AnimatedControlButton
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.ui.QuickBar.controls.PowerButton
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaLow
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaMedium
import dev.trooped.tvquickbars.utils.EntityActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.text.ifEmpty

/**
 * UI state representation for a Media Player entity.
 *
 * This data class encapsulates all the necessary properties required to render a media player card,
 * including its current playback status, connectivity state, and visual metadata.
 *
 * @property name The display name of the media player (either a custom alias or the friendly name).
 * @property state The raw state string received from the Home Assistant entity (e.g., "playing", "paused", "off").
 * @property isOn Indicates whether the media player is currently in an active power state.
 * @property isEnabled Indicates if the entity is available and not in an "unknown" or "unavailable" state.
 * @property isPlaying Specifically indicates if the current state is "playing".
 * @property isMuted Indicates if the volume is currently muted based on the entity attributes.
 * @property iconRes The resource ID of the icon to be displayed, determined by the entity's type and state.
 */
data class MediaPlayerUiState(
    val name: String,
    val state: String,
    val isOn: Boolean,
    val isEnabled: Boolean,
    val isPlaying: Boolean,
    val isMuted: Boolean,
    val iconRes: Int
)

/**
 * Remembers and maps the current state of a media player entity into a [MediaPlayerUiState].
 *
 * This composable handles the logic for determining the display name, operational state (on/off),
 * playback status, mute status, and the appropriate icon based on the [EntityItem] attributes.
 *
 * @param entity The media player entity data from Home Assistant.
 * @return A [MediaPlayerUiState] containing the processed UI properties.
 */
@Composable
fun rememberMediaPlayerUiState(entity: EntityItem): MediaPlayerUiState {
    val name = remember(entity.customName, entity.friendlyName) {
        entity.customName.ifEmpty { entity.friendlyName }
    }
    val state = entity.state
    val isEnabled = state !in listOf("unavailable", "unknown")
    val isOn = remember(state) { isMediaOn(state) }
    val isPlaying = remember(state) { isMediaPlaying(state) }
    val isMuted = remember(entity.attributes) { isMediaMuted(entity) }
    
    val iconRes = remember(entity.id, entity.state, entity.customIconOnName, entity.customIconOffName) {
        EntityIconMapper.getFinalIconForEntity(entity) ?: R.drawable.ic_default
    }

    return MediaPlayerUiState(
        name = name, state = state, isOn = isOn, isEnabled = isEnabled,
        isPlaying = isPlaying, isMuted = isMuted, iconRes = iconRes
    )
}

/**
 * A composable function that represents a media player entity as a card in the QuickBar UI.
 *
 * This card displays the current state of a Home Assistant media player entity (e.g., Playing, Paused, Off)
 * and provides interactive controls. It supports both a standard compact view and an expanded view
 * containing playback controls (play/pause, skip, volume, and power).
 *
 * @param entity The [EntityItem] data object containing the media player's state and attributes.
 * @param haClient The [HomeAssistantClient] used to dispatch service calls for media control.
 * @param onStateColor A string identifier representing the color to be used when the entity is "on".
 * @param customOnStateColor An optional list of RGB integers used if [onStateColor] is set to "custom".
 * @param modifier The [Modifier] to be applied to the card's layout.
 * @param isHorizontal A boolean flag determining if the card should be rendered in a horizontal aspect ratio.
 * @param isEntityInitialized A boolean flag indicating if the entity data is fully loaded and ready for interaction.
 */
@Composable
fun MediaPlayerEntityCard(
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

    LaunchedEffect(expanded) {
        if (expanded) {
            wasCardFocused.value = isFocused
            delay(50)
            bringIntoViewRequester.bringIntoView()
            delay(100)
            withContext(Dispatchers.Main.immediate) {
                try { closeBtnFocusRequester.requestFocus() } catch (e: Exception) { Log.e("MediaPlayerEntityCard", "Focus request failed", e) }
            }
        } else if (wasCardFocused.value) {
            delay(100)
            withContext(Dispatchers.Main.immediate) {
                try {
                    cardFocusRequester.requestFocus()
                    wasCardFocused.value = false
                } catch (e: Exception) { Log.e("MediaPlayerEntityCard", "Focus request failed", e) }
            }
        }
    }

    val uiState = rememberMediaPlayerUiState(entity)

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
    val disabledContentColor = offContentColor.copy(alpha = 0.35f)

    val animatedBackgroundColor by animateColorAsState(
        targetValue = when {
            !uiState.isEnabled -> offBackgroundColor
            uiState.isOn -> onBackgroundColor
            else -> offBackgroundColor
        },
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "mp_bg"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = when {
            !uiState.isEnabled -> disabledContentColor
            uiState.isOn -> onContentColor
            else -> offContentColor
        },
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "mp_fg"
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
        Box(
            modifier = Modifier
                .animateContentSize(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                .fillMaxSize()
        ) {
            key(expanded) {
                if (!isHorizontal) {
                    VerticalMediaContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false },
                        contentColor = animatedContentColor,
                        backgroundColor = animatedBackgroundColor,
                        closeBtnFocusRequester = closeBtnFocusRequester
                    )
                } else {
                    HorizontalMediaContent(
                        entity = entity,
                        uiState = uiState,
                        haClient = haClient,
                        expanded = expanded,
                        onClose = { expanded = false },
                        contentColor = animatedContentColor,
                        backgroundColor = animatedBackgroundColor,
                        closeBtnFocusRequester = closeBtnFocusRequester
                    )
                }
            }
        }
    }
}

/**
 * Renders the interactive control buttons for the media player, including volume
 * adjustments (up, down, mute) and playback controls (previous, play/pause, next).
 *
 */
@Composable
private fun MediaControlsSection(
    entity: EntityItem,
    uiState: MediaPlayerUiState,
    haClient: HomeAssistantClient?,
    contentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaCtlButton(
                R.drawable.volume_down_24px, "Volume down",
                { haClient?.callService("media_player", "volume_down", entity.id, null) },
                contentColor, backgroundColor
            )

            MediaCtlButton(
                if (uiState.isMuted) R.drawable.volume_off_24px else R.drawable.volume_mute_24px, "Mute",
                {
                    val data = JSONObject().apply { put("is_volume_muted", !uiState.isMuted) }
                    haClient?.callService("media_player", "volume_mute", entity.id, data)
                },
                contentColor, backgroundColor, isSelected = uiState.isMuted
            )

            MediaCtlButton(
                R.drawable.volume_up_24px, "Volume up",
                { haClient?.callService("media_player", "volume_up", entity.id, null) },
                contentColor, backgroundColor
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaCtlButton(
                R.drawable.skip_previous_24px, "Previous",
                { haClient?.callService("media_player", "media_previous_track", entity.id, null) },
                contentColor, backgroundColor
            )

            MediaCtlButton(
                if (uiState.isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px, "Play/Pause",
                { haClient?.callService("media_player", "media_play_pause", entity.id, null) },
                contentColor, backgroundColor, isSelected = uiState.isPlaying, size = 44.dp
            )

            MediaCtlButton(
                R.drawable.skip_next_24px, "Next",
                { haClient?.callService("media_player", "media_next_track", entity.id, null) },
                contentColor, backgroundColor
            )
        }
    }
}

@Composable
private fun VerticalMediaContent(
    entity: EntityItem,
    uiState: MediaPlayerUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painter = painterResource(uiState.iconRes), contentDescription = null, tint = contentColor, modifier = Modifier.size(32.dp))
        Text(text = uiState.name, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = contentColor.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))

            MediaControlsSection(entity = entity, uiState = uiState, haClient = haClient, contentColor = contentColor, backgroundColor = backgroundColor)

            Spacer(Modifier.height(10.dp))
            PowerButton(contentColor = contentColor, backgroundColor = backgroundColor, onClick = { haClient?.callService("media_player", "toggle", entity.id) }, isOn = uiState.isOn)

            Spacer(Modifier.height(8.dp))
            val closeIsrc = remember { MutableInteractionSource() }
            val closeFocused by closeIsrc.collectIsFocusedAsState()
            Box(
                modifier = Modifier
                    .focusRequester(closeBtnFocusRequester)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (closeFocused) contentColor else contentColor.copy(alpha = AlphaLow))
                    .border(width = if (closeFocused) 2.dp else 1.dp, color = if (closeFocused) contentColor else contentColor.copy(alpha = AlphaMedium), shape = CircleShape)
                    .clickable(interactionSource = closeIsrc, indication = ripple(), onClick = onClose)
                    .focusable(interactionSource = closeIsrc),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = if (closeFocused) backgroundColor else contentColor, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun HorizontalMediaContent(
    entity: EntityItem,
    uiState: MediaPlayerUiState,
    haClient: HomeAssistantClient?,
    expanded: Boolean,
    onClose: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    closeBtnFocusRequester: FocusRequester
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
            Icon(painter = painterResource(uiState.iconRes), contentDescription = null, tint = contentColor, modifier = Modifier.size(32.dp))
            Text(text = uiState.name, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
            if (uiState.state.isNotBlank()) {
                Text(uiState.state.replaceFirstChar { it.uppercase() }, fontSize = 11.sp)
            }
        }

        if (expanded) {
            Box(Modifier.width(1.dp).height(100.dp).background(contentColor.copy(alpha = 0.2f)))

            Box(Modifier.weight(1f).fillMaxHeight()) {
                MediaControlsSection(
                    entity = entity, uiState = uiState, haClient = haClient, contentColor = contentColor, backgroundColor = backgroundColor,
                    modifier = Modifier.fillMaxSize().padding(end = 48.dp)
                )

                Column(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PowerButton(contentColor = contentColor, backgroundColor = backgroundColor, onClick = { haClient?.callService("media_player", "toggle", entity.id) }, isOn = uiState.isOn, size = 36.dp)

                    val closeIsrc = remember { MutableInteractionSource() }
                    val closeFocused by closeIsrc.collectIsFocusedAsState()
                    Box(
                        modifier = Modifier
                            .focusRequester(closeBtnFocusRequester)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (closeFocused) contentColor else contentColor.copy(alpha = AlphaLow))
                            .border(width = if (closeFocused) 2.dp else 1.dp, color = if (closeFocused) contentColor else contentColor.copy(alpha = AlphaMedium), shape = CircleShape)
                            .clickable(interactionSource = closeIsrc, indication = ripple(), onClick = onClose)
                            .focusable(interactionSource = closeIsrc),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = if (closeFocused) backgroundColor else contentColor, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCtlButton(
    @DrawableRes icon: Int,
    contentDesc: String,
    onClick: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    isSelected: Boolean = false,
    size: Dp = 40.dp
) {
    val interactionSource = remember { MutableInteractionSource() }

    AnimatedControlButton(
        onClick = onClick,
        contentColor = contentColor,
        backgroundColor = backgroundColor,
        modifier = Modifier,
        size = size,
        isSelected = isSelected,
        interactionSource = interactionSource
    ) { isFocused, _, animationScale, contentColor, backgroundColor ->
        Icon(
            painter = painterResource(id = icon),
            contentDescription = contentDesc,
            tint = if (isFocused) backgroundColor else contentColor,
            modifier = Modifier.size(size * 0.55f).scale(animationScale)
        )
    }
}