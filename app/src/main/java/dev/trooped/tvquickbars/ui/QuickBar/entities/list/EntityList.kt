package dev.trooped.tvquickbars.ui.QuickBar.entities.list

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.ui.QuickBar.overlay.LocalListState
import dev.trooped.tvquickbars.ui.QuickBar.foundation.getEntityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * EntityList
 * List of entities to display.
 * @param entities The entities to display.
 * @param isHorizontal Whether the list should be displayed horizontally.
 * @param haClient The HomeAssistantClient to use.
 * @param onStateColor The color to use for the state of the entities.
 * @param useGridLayout Whether to use a grid layout.
 * @param modifier The modifier to apply to the list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntityList(
    entities: List<EntityItem>,
    isHorizontal: Boolean,
    haClient: HomeAssistantClient?,
    onStateColor: String,
    customOnStateColor: List<Int>? = null,
    useGridLayout: Boolean = false,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    // Create view spec for bringing items into view
    val bringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            suspend fun bringChildIntoView(
                parentBounds: Rect,
                childBounds: Rect,
                layoutDirection: LayoutDirection
            ): Offset {
                val parentCenter = parentBounds.top + parentBounds.height / 2
                val childCenter = childBounds.top + childBounds.height / 2
                return Offset(0f, childCenter - parentCenter)
            }
        }
    }

    // Make this run only AFTER first composition is complete
    LaunchedEffect(Unit) {
        delay(100)
        withContext(Dispatchers.Main.immediate) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("EntityList", "Failed to request focus", e)
            }
        }
    }

    // HORIZONTAL LAYOUT
    if (isHorizontal) {
        val listState = rememberLazyListState()

        CompositionLocalProvider(
            LocalBringIntoViewSpec provides bringIntoViewSpec,
            LocalListState provides listState
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp)
            ) {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = entities,
                        key = { it.id },
                        contentType = { entity -> getEntityType(entity) }
                    ) { entity ->
                        val itemModifier = Modifier
                            .padding(4.dp)
                            .animateItem()
                            .background(Color.Transparent)
                            .then(
                                if (entities.indexOf(entity) == 0)
                                    Modifier.focusRequester(focusRequester)
                                else
                                    Modifier
                            )

                        RenderEntityItem(entity, haClient, onStateColor, customOnStateColor, itemModifier, true)
                    }
                }
            }
        }
    }
    // VERTICAL GRID LAYOUT
    else if (useGridLayout) {
        val gridState = rememberLazyStaggeredGridState()

        CompositionLocalProvider(
            LocalBringIntoViewSpec provides bringIntoViewSpec,
            LocalListState provides gridState
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                state = gridState,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 800.dp),
                verticalItemSpacing = 4.dp,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = entities,
                    key = { it.id },
                    contentType = { entity -> getEntityType(entity) }
                ) { entity ->
                    val itemModifier = Modifier
                        .padding(4.dp)
                        .animateItem()
                        .background(Color.Transparent)
                        .fillMaxWidth()
                        .then(
                            if (entities.indexOf(entity) == 0)
                                Modifier.focusRequester(focusRequester)
                            else
                                Modifier
                        )

                    RenderEntityItem(entity, haClient, onStateColor, customOnStateColor, itemModifier, false)
                }
            }
        }
    }
    // VERTICAL LIST LAYOUT
    else {
        val listState = rememberLazyListState()

        CompositionLocalProvider(
            LocalBringIntoViewSpec provides bringIntoViewSpec,
            LocalListState provides listState
        ) {
            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 800.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(
                    items = entities,
                    key = { it.id },
                    contentType = { entity -> getEntityType(entity) }
                ) { entity ->
                    val itemModifier = Modifier
                        .padding(4.dp)
                        .animateItem()
                        .background(Color.Transparent)
                        .widthIn(max = 300.dp)
                        .then(
                            if (entities.indexOf(entity) == 0)
                                Modifier.focusRequester(focusRequester)
                            else
                                Modifier
                        )

                    RenderEntityItem(entity, haClient, onStateColor, customOnStateColor, itemModifier, false)
                }
            }
        }
    }
}
