package dev.trooped.tvquickbars.ui.QuickBar.entities.list

import android.util.Log
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.ui.QuickBar.entities.cards.alarm_control_panel.AlarmControlPanelEntityCard
import dev.trooped.tvquickbars.ui.QuickBar.entities.cards.climate.ClimateEntityCard
import dev.trooped.tvquickbars.ui.QuickBar.entities.cards.cover.CoverEntityCard
import dev.trooped.tvquickbars.ui.QuickBar.entities.cards.fan.FanEntityCard
import dev.trooped.tvquickbars.ui.QuickBar.entities.cards.light.LightEntityCard
import dev.trooped.tvquickbars.ui.QuickBar.entities.cards.lock.LockEntityCard
import dev.trooped.tvquickbars.ui.QuickBar.entities.cards.media_player.MediaPlayerEntityCard
import dev.trooped.tvquickbars.ui.QuickBar.entities.cards.normal.EntityCard
import kotlinx.coroutines.delay

/**
 * Renders the appropriate UI card for a specific Home Assistant entity based on its domain.
 *
 * This function handles the initialization of entity state, applies saved user customizations
 * (such as custom names, icons, and press actions) from [SavedEntitiesManager], and
 * dispatches the rendering to specialized components (e.g., [ClimateEntityCard], [LightEntityCard])
 * based on the entity's ID prefix.
 *
 * @param entity The [EntityItem] data object containing state and configuration.
 * @param haClient The [HomeAssistantClient] used for communication with the Home Assistant instance.
 * @param onStateColor The hex string color to apply when the entity is in an "on" or active state.
 * @param customOnStateColor An optional list of integers representing a custom color palette for active states.
 * @param modifier The [Modifier] to be applied to the layout of the rendered card.
 * @param isHorizontal Boolean flag indicating if the card should be rendered in a horizontal orientation.
 */
@Composable
fun RenderEntityItem(
    entity: EntityItem,
    haClient: HomeAssistantClient?,
    onStateColor: String,
    customOnStateColor: List<Int>?,
    modifier: Modifier,
    isHorizontal: Boolean
) {
    val context = LocalContext.current
    val savedEntitiesManager = remember { SavedEntitiesManager(context) }
    val savedEntity = remember(entity.id) {
        savedEntitiesManager.loadEntities().find { it.id == entity.id }
    }

    var isEntityInitialized by remember { mutableStateOf(false) }

    // Apply saved customizations to the current entity
    LaunchedEffect(entity.id, savedEntity) {
        if (savedEntity != null) {
            // Apply all customizations from saved entity
            //Log.d("QuickBarOverlay", "Applying saved customizations for ${entity.id}")
            //Log.d("QuickBarOverlay", "Saved targets: ${savedEntity.pressTargets}")

            // Copy pressTargets from saved entity to current entity
            entity.pressTargets.clear()
            entity.pressTargets.putAll(savedEntity.pressTargets)


            entity.pressActions.clear()
            entity.pressActions.putAll(savedEntity.pressActions)


            // Copy other customizations that might be relevant
            entity.customIconOnName = savedEntity.customIconOnName
            entity.customIconOffName = savedEntity.customIconOffName
            entity.customName = savedEntity.customName

            if (entity.lastKnownState == null) {
                entity.lastKnownState = mutableMapOf()
            }

            savedEntity.lastKnownState?.let { savedState ->
                entity.lastKnownState.clear()
                entity.lastKnownState.putAll(savedState)
            }

            // Mark as having default actions applied to prevent overwrites
            entity.defaultPressActionsApplied = true

            // Give a small delay to ensure everything is properly initialized
            delay(100)

            // Mark entity as initialized and ready for interaction
            isEntityInitialized = true
        } else {
            // Only apply defaults if no saved entity exists
            Log.d("QuickBarOverlay", "No saved entity found, applying defaults for ${entity.id}")
            savedEntitiesManager.applyDefaultActions(entity)

            // Still mark as initialized after defaults are applied
            isEntityInitialized = true
        }
    }


    when {
        entity.id.startsWith("climate.") -> ClimateEntityCard(
            entity,
            haClient,
            onStateColor = onStateColor,
            customOnStateColor,
            modifier = modifier,
            isHorizontal = isHorizontal,
            isEntityInitialized = isEntityInitialized
        )

        entity.id.startsWith("fan.") -> FanEntityCard(
            entity,
            haClient,
            onStateColor,
            customOnStateColor,
            modifier,
            isHorizontal = isHorizontal,
            isEntityInitialized = isEntityInitialized
        )

        entity.id.startsWith("cover.") -> CoverEntityCard(
            entity,
            haClient,
            onStateColor,
            customOnStateColor,
            modifier,
            isHorizontal = isHorizontal,
            isEntityInitialized = isEntityInitialized
        )

        entity.id.startsWith("light.") -> LightEntityCard(
            entity,
            haClient,
            onStateColor,
            customOnStateColor,
            modifier,
            isHorizontal = isHorizontal,
            isEntityInitialized = isEntityInitialized
        )

        entity.id.startsWith("lock.") -> LockEntityCard(  // Add this case for lock entities
            entity,
            haClient,
            onStateColor,
            customOnStateColor,
            modifier,
            isHorizontal = isHorizontal,
            isEntityInitialized = isEntityInitialized
        )

        entity.id.startsWith("alarm_control_panel.") -> AlarmControlPanelEntityCard(
            entity,
            haClient,
            modifier,
            isHorizontal = isHorizontal,
            onStateColor,
            customOnStateColor,
            isEntityInitialized = isEntityInitialized
        )

        entity.id.startsWith("media_player.") -> MediaPlayerEntityCard(
            entity,
            haClient,
            onStateColor,
            customOnStateColor,
            modifier,
            isHorizontal,
            isEntityInitialized
        )

        else -> EntityCard(
            entity,
            haClient,
            onStateColor,
            customOnStateColor,
            if (isHorizontal) modifier.width(120.dp) else modifier,
            isHorizontal = isHorizontal,
            isEntityInitialized = isEntityInitialized
        )
    }
}