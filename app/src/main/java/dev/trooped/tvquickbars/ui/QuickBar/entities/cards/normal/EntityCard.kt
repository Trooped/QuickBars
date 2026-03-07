package dev.trooped.tvquickbars.ui.QuickBar.entities.cards.normal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityAction
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.PressType
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.ui.QuickBar.foundation.SafePainterResource
import dev.trooped.tvquickbars.ui.QuickBar.foundation.formatBinarySensorState
import dev.trooped.tvquickbars.ui.QuickBar.foundation.formatTimestamp
import dev.trooped.tvquickbars.utils.EntityActionExecutor
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.text.ifEmpty

/**
 * EntityCard
 * Card for the normal entities (e.g not climate/fan/cover)
 * @param entity The entity to display.
 * @param haClient The HomeAssistantClient to use.
 * @param onStateColor The color to use for the state of the entity.
 * @param modifier The modifier to apply to the card.
 * @param isHorizontal Whether the card should be displayed horizontally.
 */
@Composable
fun EntityCard(
    entity: EntityItem,
    haClient: HomeAssistantClient?,
    onStateColor: String,
    customOnStateColor: List<Int>?,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = false,  // Add this parameter back
    isEntityInitialized: Boolean = false
) {
    val name = remember(entity.customName, entity.friendlyName) {
        entity.customName.ifEmpty { entity.friendlyName }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()


    val context = LocalContext.current
    val savedEntitiesManager = remember {
        SavedEntitiesManager(context)
    }

    var buttonPressed by remember { mutableStateOf(false) }

    val primaryBgColor = colorResource(id = R.color.md_theme_primary)
    val primaryTextColor = colorResource(id = R.color.md_theme_onPrimary)
    val amberBgColor = colorResource(id = R.color.md_theme_Amber500)
    val tertiaryBgColor = colorResource(id = R.color.md_theme_tertiary)
    val tertiaryTextColor = colorResource(id = R.color.md_theme_onTertiary)
    val errorBgColor = colorResource(id = R.color.md_theme_error)
    val errorTextColor = colorResource(id = R.color.md_theme_onError)
    val surfaceVariantBgColor = colorResource(id = R.color.md_theme_surfaceVariant)
    val surfaceVariantTextColor = colorResource(id = R.color.md_theme_onSurfaceVariant)
    val disabledContentColor = surfaceVariantTextColor.copy(alpha = 0.4f)

    // Identify if these are special/different entities
    val isSensor = entity.id.startsWith("sensor.")
    val isBinarySensor = entity.id.startsWith("binary_sensor.")
    val isScene = entity.id.startsWith("scene.")
    val isButton = entity.id.startsWith("button.") || entity.id.startsWith("input_button.")
    val isScript = entity.id.startsWith("script.")
    val isCamera = entity.id.startsWith("camera.") || entity.id.startsWith("custom_camera.")
    val isAutomation = entity.id.startsWith("automation.")

    val isDisabledState = entity.state in listOf("unavailable", "unknown")

    val isClickable = when {
        isSensor || isBinarySensor -> false // Rule 1: Sensors are never clickable.
        isDisabledState -> isButton || isScript || isScene // Rule 2: If disabled, only buttons/scripts are clickable.
        else -> true // Rule 3: All other enabled entities are clickable.
    }

    // Calculate background color
    val (backgroundColor, contentColor) =
        remember(entity.state, isDisabledState, onStateColor, customOnStateColor) {

            // camera-specific “is on” rule
            val cameraIsOn = isCamera &&
                    !isDisabledState &&
                    !entity.state.equals("off", ignoreCase = true)

            // generic entities: ON only when state == "on"
            val genericEntityIsOn = !isCamera && entity.state == "on"

            val isOnVisual = cameraIsOn || genericEntityIsOn

            // If using custom, compute it once
            val customOnColor: Color? =
                if (onStateColor.equals("custom", ignoreCase = true))
                    rgbListToColor(customOnStateColor)
                else null

            when {
                // Disabled
                isDisabledState -> surfaceVariantBgColor to disabledContentColor

                // Scene (special styling)
                isScene -> surfaceVariantBgColor.copy(alpha = 0.7f) to surfaceVariantTextColor

                // Sensors never “ON highlight”
                isSensor || isBinarySensor -> surfaceVariantBgColor to surfaceVariantTextColor

                // ON visual state
                isOnVisual -> {
                    if (customOnColor != null) {
                        customOnColor to contentFor(customOnColor)
                    } else {
                        when (onStateColor) {
                            "colorAmber500" -> amberBgColor to Color.Black
                            "colorTertiary" -> tertiaryBgColor to tertiaryTextColor
                            "colorError"    -> errorBgColor to errorTextColor
                            else            -> primaryBgColor to primaryTextColor
                        }
                    }
                }

                // OFF/default
                else -> surfaceVariantBgColor to surfaceVariantTextColor
            }
        }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(200),
        label = "bgColorAnim"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(200),
        label = "contentColorAnim"
    )

    val iconRes =
        remember(entity.id, entity.state, entity.customIconOnName, entity.customIconOffName) {
            EntityIconMapper.getFinalIconForEntity(entity) ?: R.drawable.ic_default
        }

    var isPressed by remember { mutableStateOf(false) }

    // This effect listens for physical press and release events
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed = true
                is PressInteraction.Release -> isPressed = false
                is PressInteraction.Cancel -> isPressed = false
            }
        }
    }

    // This animation smoothly scales the icon based on the isPressed state
    val iconScaleLongPress by animateFloatAsState(
        targetValue = if (isPressed) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconPressAnim"
    )

    val iconScaleButtonPress by animateFloatAsState(
        targetValue = if (buttonPressed) 1.3f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        // When the "pop" animation finishes, this listener resets the state,
        // which causes the icon to animate back to its normal size.
        finishedListener = { buttonPressed = false },
        label = "iconScaleAnim"
    )

    val combinedIconScale = maxOf(iconScaleLongPress, iconScaleButtonPress)


    rememberCoroutineScope()

    val handlePress: (PressType) -> Unit =
        remember(entity.id, haClient, savedEntitiesManager, isEntityInitialized) {
            { press ->
                // First, determine if the action for this specific press should have the pop animation.
                val isButtonEntity =
                    entity.id.startsWith("button.") || entity.id.startsWith("input_button.")
                val action = entity.pressActions[press] ?: EntityAction.Default

                val shouldAnimate = isButtonEntity && (
                        // It's the default single press on a button
                        (press == PressType.SINGLE && action is EntityAction.Default) ||
                                // Or it's a specific service call to "press"
                                (action is EntityAction.ServiceCall && action.service == "press")
                        )

                if (shouldAnimate) {
                    buttonPressed = true
                }

                if (isEntityInitialized) {
                    EntityActionExecutor.perform(
                        entity = entity,
                        press = press,
                        haClient = haClient,
                        savedEntitiesManager = savedEntitiesManager,
                        onExpand = {}
                    )
                }
            }
        }


    Card(
        modifier = modifier
            .combinedClickable(
                enabled = isClickable,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = contentColor),

                onClick = { handlePress(PressType.SINGLE) },
                onLongClick = {
                    /* keep long-press ripple / haptic if you wish */
                    handlePress(PressType.LONG)
                }
            )
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            2.dp,
            if (isFocused) Color.White else Color.Transparent
        ),
        colors = CardDefaults.cardColors(
            containerColor = animatedBackgroundColor,
            contentColor = animatedContentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {

        val iconModifier = Modifier
            .size(24.dp)
            .scale(combinedIconScale) // Apply the animated scale
            .alpha(if (isDisabledState) 0.4f else 1f)

        val iconColorFilter = ColorFilter.tint(animatedContentColor)

        val stateText = when {
            isSensor -> {
                val attributes = entity.attributes
                val deviceClass = attributes?.optString("device_class", "") ?: ""

                if (deviceClass == "timestamp") {
                    formatTimestamp(entity.state)
                } else {
                    val unit = attributes?.optString("unit_of_measurement", "") ?: ""
                    val formattedState = formatNumericSmart(entity.state, unit, attributes)
                    if (unit.isBlank()) formattedState else "$formattedState $unit"
                }
            }

            isBinarySensor -> formatBinarySensorState(entity)
            else -> ""
        }


        if (isHorizontal) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = SafePainterResource(id = iconRes),
                    contentDescription = name,
                    modifier = iconModifier,
                    colorFilter = iconColorFilter
                )

                Text(
                    text = name,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (stateText.isNotEmpty()) {
                    // Force LTR layout direction for state text
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = stateText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = SafePainterResource(iconRes),
                    contentDescription = name,
                    modifier = iconModifier,
                    colorFilter = iconColorFilter
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

                if (stateText.isNotEmpty()) {
                    // Force LTR layout direction for state text
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = stateText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a numeric state string into a human-readable format based on precision rules.
 *
 * The formatting logic follows this priority:
 * 1. Returns "unknown" or "unavailable" as-is.
 * 2. If the value is a whole number, returns it without decimals.
 * 3. Uses `display_precision` from [attrs] if available (0-6).
 * 4. Otherwise, matches the source precision (number of digits after the decimal point) up to 3 places.
 * 5. As a fallback, determines decimal count based on the magnitude of the value.
 *
 */
private fun formatNumericSmart(rawState: String, unit: String, attrs: JSONObject?): String {
    val raw = rawState.trim()

    // pass through non-numeric states
    if (raw.equals("unknown", true) || raw.equals("unavailable", true)) return raw

    val bd = raw.toBigDecimalOrNull() ?: return raw

    // Whole numbers -> no decimals
    if (bd.stripTrailingZeros().scale() <= 0) {
        return bd.toPlainString()
    }

    // 1) allow HA to hint precision
    val haPrec = attrs?.opt("display_precision")?.toString()?.toIntOrNull()
    val decimals = when {
        haPrec != null && haPrec in 0..6 -> haPrec

        // 2) respect source precision up to 3 decimals
        raw.contains('.') -> minOf(raw.substringAfter('.', "").length, 3)

        // 3) fallback by magnitude when string has no '.'
        else -> {
            val abs = bd.abs()
            when {
                abs >= BigDecimal("100") -> 0
                abs >= BigDecimal("10")  -> 1
                abs >= BigDecimal("1")   -> 2
                else                     -> 3
            }
        }
    }

    val scaled = bd.setScale(decimals, RoundingMode.HALF_UP)
    return scaled.stripTrailingZeros().toPlainString()
}


private fun rgbListToColor(rgb: List<Int>?): Color? {
    if (rgb == null || rgb.size < 3) return null
    val r = rgb[0].coerceIn(0, 255)
    val g = rgb[1].coerceIn(0, 255)
    val b = rgb[2].coerceIn(0, 255)
    return Color(android.graphics.Color.rgb(r, g, b))
}

/** Simple luma heuristic (sRGB) to select black/white for readability */
private fun contentFor(bg: Color): Color {
    val luma = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (luma > 0.6f) Color.Black else Color.White
}