/**
 * QuickBarOverlay File
 * Contains all of the Compose UI logic for the QuickBar overlay service.
 */

package dev.trooped.tvquickbars.ui.QuickBar.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.QuickBar
import dev.trooped.tvquickbars.data.QuickBarPosition
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.ui.QuickBar.entities.list.EntityList
import dev.trooped.tvquickbars.ui.QuickBar.foundation.AlphaHigh
import dev.trooped.tvquickbars.ui.QuickBar.foundation.rememberClockText
import dev.trooped.tvquickbars.utils.EntityActionExecutor


/**
 * A [CompositionLocal] used to provide and access the scroll or layout state
 * of the entity list within the QuickBar overlay.
 */
val LocalListState = compositionLocalOf<Any> { error("No list state provided") }

/**
 * AnimatedQuickBar
 * Animated overlay for a QuickBar.
 * @param isVisible Whether the overlay is supposed to be visible.
 * @param bar The QuickBar to display.
 * @param entities The entities to display.
 * @param haClient The HomeAssistantClient to use.
 */
@Composable
fun AnimatedQuickBar(
    isVisible: Boolean,
    bar: QuickBar,
    entities: List<EntityItem>,
    haClient: HomeAssistantClient?,
    connectionErrorMessage: String? = null
) {
    // The animation definitions you provided are perfect.
    val enterTransition = remember(bar.position) {
        when (bar.position) {
            QuickBarPosition.LEFT -> slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 300))

            QuickBarPosition.RIGHT -> slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 300))

            QuickBarPosition.TOP -> slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 300))

            QuickBarPosition.BOTTOM -> slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 300))
        }
    }

    val exitTransition = remember(bar.position) {
        when (bar.position) {
            QuickBarPosition.LEFT -> slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))

            QuickBarPosition.RIGHT -> slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))

            QuickBarPosition.TOP -> slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))

            QuickBarPosition.BOTTOM -> slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = enterTransition,
        exit = exitTransition
    ) {
        // The AnimatedVisibility composable now manages the entire show/hide animation.
        QuickBarOverlay(
            bar = bar,
            entities = entities,
            haClient = haClient,
            connectionErrorMessage = connectionErrorMessage
        )
    }
}

/**
 * QuickBarOverlay
 * Overlay for a QuickBar.
 * @param bar The QuickBar to display.
 * @param entities The entities to display.
 * @param haClient The HomeAssistantClient to use.
 */
@Composable
fun QuickBarOverlay(
    bar: QuickBar,
    entities: List<EntityItem>,
    haClient: HomeAssistantClient?,
    connectionErrorMessage: String? = null
) {
    val context = LocalContext.current

    // Get the fallback color resource outside any remember blocks
    val fallbackColorResource = colorResource(id = R.color.md_theme_surfaceVariant)
    val fallbackColor = fallbackColorResource.copy(alpha = AlphaHigh)

    // Calculate the background color with remember
    val backgroundColor = remember(bar.backgroundColor, bar.backgroundOpacity) {
        try {
            Color(bar.getBackgroundColorWithOpacity(context))
        } catch (e: Exception) {
            fallbackColor
        }
    }

    val cardPadding = when {
        bar.useGridLayout && bar.position == QuickBarPosition.LEFT ->
            PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)

        bar.useGridLayout && bar.position == QuickBarPosition.RIGHT ->
            PaddingValues(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)

        bar.useGridLayout ->
            PaddingValues(8.dp) // Default grid padding for top/bottom
        else ->
            PaddingValues(16.dp) // Standard padding
    }

    // Inner content padding
    val contentPadding = when {
        bar.useGridLayout && bar.position == QuickBarPosition.LEFT ->
            PaddingValues(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)

        bar.useGridLayout && bar.position == QuickBarPosition.RIGHT ->
            PaddingValues(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)

        bar.useGridLayout ->
            PaddingValues(8.dp) // Default grid inner padding
        else ->
            PaddingValues(16.dp) // Standard inner padding
    }

    MaterialTheme {
        Card(
            modifier = Modifier
                .padding(cardPadding)
                // Add a size constraint based on position
                .then(
                    if (bar.position == QuickBarPosition.LEFT || bar.position == QuickBarPosition.RIGHT) {
                        Modifier.heightIn(max = 600.dp)
                    } else {
                        Modifier
                            .widthIn(max = 800.dp)
                            .heightIn(max = 300.dp)
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(0.dp, Color.Transparent)
        ) {
            Column(
                modifier = Modifier.padding(contentPadding)
            ) {
                if (bar.showNameInOverlay || bar.showTimeOnQuickBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Show name on the left if enabled
                        if (bar.showNameInOverlay) {
                            Text(
                                text = bar.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorResource(id = R.color.md_theme_onSurface),
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        if (bar.showTimeOnQuickBar) {
                            val timeText by rememberClockText()
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorResource(id = R.color.md_theme_onSurface),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                // Show connection error if any
                if (connectionErrorMessage != null) {
                    ConnectionErrorBanner(message = connectionErrorMessage)
                }

                EntityList(
                    entities = entities,
                    isHorizontal = bar.position == QuickBarPosition.TOP || bar.position == QuickBarPosition.BOTTOM,
                    haClient = haClient,
                    onStateColor = bar.onStateColor,
                    customOnStateColor = bar.customOnStateColor,
                    useGridLayout = bar.useGridLayout
                )
            }
        }
    }

    EntityActionExecutor.QuickBarDataCache.cachedEntities = entities
}

/**
 * Displays a banner indicating a connection error to Home Assistant.
 *
 * @param message The error message to be displayed within the banner.
 */
@Composable
fun ConnectionErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                colorResource(id = R.color.md_theme_errorContainer),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Error",
            tint = colorResource(id = R.color.md_theme_onErrorContainer),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colorResource(id = R.color.md_theme_onErrorContainer),
            fontSize = 12.sp
        )
    }
}

