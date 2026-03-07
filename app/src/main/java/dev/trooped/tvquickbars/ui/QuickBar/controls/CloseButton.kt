package dev.trooped.tvquickbars.ui.QuickBar.controls

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.trooped.tvquickbars.ui.AnimatedIconButton

@Composable
private fun CloseButton(
    onClose: () -> Unit,
    contentColor: Color,
    backgroundColor: Color,
    focusRequester: FocusRequester,
    size: Dp = 40.dp
) {
    AnimatedIconButton(
        icon = Icons.Default.Close,
        contentDescription = "Close",
        onClick = onClose,
        contentColor = contentColor,
        backgroundColor = backgroundColor,
        size = size,
        focusRequester = focusRequester
    )
}