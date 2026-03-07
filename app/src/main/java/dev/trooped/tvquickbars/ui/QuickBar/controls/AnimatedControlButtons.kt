package dev.trooped.tvquickbars.ui


import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val AlphaLow = 0.1f
private const val AlphaMedium = 0.3f

/**
 * A reusable animated control button that provides consistent press animation
 * across all entity controls in the app.
 */
@Composable
fun AnimatedControlButton(
    onClick: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester? = null,
    content: @Composable (
        isFocused: Boolean,
        isPressed: Boolean,
        animationScale: Float,
        contentColor: Color,
        backgroundColor: Color
    ) -> Unit
) {
    // Track focus state
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Track press state for animation
    var isPressed by remember { mutableStateOf(false) }

    // Listen for press interactions
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed = true
                is PressInteraction.Release -> isPressed = false
                is PressInteraction.Cancel -> isPressed = false
            }
        }
    }

    // Calculate animation scale
    val animationScale by animateFloatAsState(
        targetValue = if (isPressed) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonPressAnim"
    )

    // Build the button modifier
    var buttonModifier = modifier
        .size(size)
        .clip(shape)
        .background(
            when {
                !enabled -> contentColor.copy(alpha = 0.1f)
                isFocused -> contentColor
                isSelected -> contentColor.copy(alpha = 0.4f)
                else -> contentColor.copy(alpha = AlphaLow)
            }
        )
        .border(
            width = if (isSelected || isFocused) 2.dp else 1.dp,
            color = if (isFocused) contentColor else if (isSelected) contentColor.copy(alpha = 0.8f) else contentColor.copy(alpha = AlphaMedium),
            shape = shape
        )
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = onClick
        )
        .focusable(interactionSource = interactionSource)

    // Apply focus requester if provided
    if (focusRequester != null) {
        buttonModifier = buttonModifier.focusRequester(focusRequester)
    }

    // Button container
    Box(
        modifier = buttonModifier,
        contentAlignment = Alignment.Center
    ) {
        // Call the content with all necessary parameters
        content(
            isFocused,
            isPressed,
            animationScale,
            contentColor,
            backgroundColor
        )
    }
}

/**
 * Convenient version for icon-only buttons
 */
@Composable
fun AnimatedIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.6f,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester? = null
) {
    AnimatedControlButton(
        onClick = onClick,
        contentColor = contentColor,
        backgroundColor = backgroundColor,
        modifier = modifier,
        size = size,
        enabled = enabled,
        isSelected = isSelected,
        interactionSource = interactionSource,
        focusRequester = focusRequester
    ) { isFocused, _, animationScale, contentColor, backgroundColor ->
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) backgroundColor else contentColor,
            modifier = Modifier
                .size(iconSize)
                .scale(animationScale)
        )
    }
}

/**
 * Rounded button with text for actions
 */
@Composable
fun AnimatedActionButton(
    text: String,
    onClick: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isCompact: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester? = null
) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    var buttonModifier = modifier
        .height(if (isCompact) 36.dp else 44.dp)
        .widthIn(min = if (isCompact) 80.dp else 120.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(
            when {
                !enabled -> contentColor.copy(alpha = 0.1f)
                isFocused -> contentColor
                else -> contentColor.copy(alpha = 0.15f)
            }
        )
        .border(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) contentColor else contentColor.copy(alpha = 0.3f),
            shape = RoundedCornerShape(8.dp)
        )
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = onClick
        )
        .focusable(interactionSource = interactionSource)

    // Apply focus requester if provided
    if (focusRequester != null) {
        buttonModifier = buttonModifier.focusRequester(focusRequester)
    }

    // Track press state for animation
    var isPressed by remember { mutableStateOf(false) }

    // Listen for press interactions
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed = true
                is PressInteraction.Release -> isPressed = false
                is PressInteraction.Cancel -> isPressed = false
            }
        }
    }

    // Calculate animation scale
    val animationScale by animateFloatAsState(
        targetValue = if (isPressed) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonPressAnim"
    )

    Box(
        modifier = buttonModifier.scale(animationScale),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) backgroundColor else if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(if (isCompact) 16.dp else 20.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = text,
                fontSize = (if (isCompact) 12.sp else 14.sp),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = if (isFocused) backgroundColor else if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ValuePill(
    text: String,
    contentColor: Color,
    backgroundColor: Color,
    minWidth: Dp = 60.dp,
    height: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(contentColor.copy(alpha = AlphaLow))
            .border(
                width = 1.dp,
                color = contentColor.copy(alpha = AlphaMedium),
                shape = RoundedCornerShape(8.dp)
            )
            .semantics { disabled() }   // explicitly not actionable
            .focusable(false),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}