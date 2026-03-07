package dev.trooped.tvquickbars.ui.QuickBar.controls

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.trooped.tvquickbars.ui.AnimatedIconButton

/**
 * PowerButton
 * On/Off button for the light.
 */
@Composable
fun PowerButton(
    modifier: Modifier = Modifier,
    isOn: Boolean,
    onClick: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    size: Dp = 44.dp
) {
    AnimatedIconButton(
        icon = Icons.Default.PowerSettingsNew,
        contentDescription = if (isOn) "Turn Off" else "Turn On",
        onClick = onClick,
        contentColor = contentColor,
        backgroundColor = backgroundColor,
        modifier = modifier,
        size = size,
        isSelected = isOn
    )
}

