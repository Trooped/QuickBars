package dev.trooped.tvquickbars.ui.dialog

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.trooped.tvquickbars.R

/**
 * Represents a single change/feature in a version update
 */
data class ChangeItem(
    val title: String,
    val description: String,
    val isHighlighted: Boolean = false
)

/**
 * TV-friendly dialog that shows what's new in the current app version
 */
@Composable
fun WhatsNewDialog(
    versionName: String,
    changes: List<ChangeItem>,
    onDismiss: () -> Unit
) {
    val listFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var buttonFocused by remember { mutableStateOf(false) }

    // Focus list first (after composition so node is attached)
    LaunchedEffect(Unit) {
        delay(100)
        listFocusRequester.requestFocus()
    }

    // Shift focus to the button once we hit the bottom
    val atBottom by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 8 }
    }
    LaunchedEffect(atBottom) {
        if (atBottom) {
            delay(50)
            buttonFocusRequester.requestFocus()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .heightIn(max = 600.dp) // cap height so content scrolls
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            color = colorResource(id = R.color.md_theme_surface) // ← same as your original
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header — keep your colors
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = colorResource(id = R.color.md_theme_primary), // ← keep
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "What's New",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colorResource(id = R.color.md_theme_onSurface) // ← keep
                        )
                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorResource(id = R.color.md_theme_onSurfaceVariant) // ← keep
                        )
                    }
                }

                HorizontalDivider(
                    Modifier,
                    DividerDefaults.Thickness,
                    color = colorResource(id = R.color.md_theme_outline)
                )

                // Scrollable list (DPAD-friendly)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(listFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown) {
                                when (ev.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        coroutineScope.launch { scrollState.animateScrollBy(160f) }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        coroutineScope.launch { scrollState.animateScrollBy(-160f) }
                                        true
                                    }
                                    KeyEvent.KEYCODE_PAGE_DOWN,
                                    KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                        coroutineScope.launch { scrollState.animateScrollBy(600f) }
                                        true
                                    }
                                    KeyEvent.KEYCODE_PAGE_UP,
                                    KeyEvent.KEYCODE_CHANNEL_UP -> {
                                        coroutineScope.launch { scrollState.animateScrollBy(-600f) }
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .verticalScroll(scrollState)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        changes.forEach { change ->
                            ChangeListItemWithBullet(change)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "For full release notes, go to quickbars.app/release-notes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorResource(id = R.color.md_theme_onSurfaceVariant).copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Button — keep your theme colors & focus border
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .focusRequester(buttonFocusRequester)
                        .onFocusChanged { buttonFocused = it.hasFocus },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(id = R.color.md_theme_primary),      // ← keep
                        contentColor = colorResource(id = R.color.md_theme_onPrimary)       // ← keep
                    ),
                    border = if (buttonFocused)
                        BorderStroke(2.dp, colorResource(id = R.color.md_theme_onSurface))
                    else null,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (buttonFocused) 8.dp else 4.dp
                    )
                ) {
                    Text("Got it", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ChangeListItemWithBullet(change: ChangeItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Bullet point
        Text(
            text = "•",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colorResource(id = R.color.md_theme_primary),
            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
        )

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = change.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (change.isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                color = if (change.isHighlighted)
                    colorResource(id = R.color.md_theme_primary)
                else
                    colorResource(id = R.color.md_theme_onSurface)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = change.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(id = R.color.md_theme_onSurfaceVariant)
            )
        }
    }
}