package dev.trooped.tvquickbars.background

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import dev.trooped.tvquickbars.camera.CameraRequest
import dev.trooped.tvquickbars.ha.*
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.data.CategoryItem
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.notification.NotificationSpec
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.services.QuickBarService
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.utils.EntityStateKeys
import dev.trooped.tvquickbars.utils.EntityStateUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random


/**
 * Manages the background WebSocket connection to Home Assistant.
 *
 * This object is responsible for:
 * - Establishing and maintaining a persistent WebSocket connection.
 * - Handling automatic reconnections with backoff strategies.
 * - Listening for network changes to trigger reconnections.
 * - Receiving and processing entity state updates from Home Assistant.
 * - Throttling rapid entity updates to avoid overwhelming the system.
 * - Subscribing to specific Home Assistant events (e.g., `quickbars.open`).
 * - Providing a client instance for sending commands to Home Assistant.
 * - Managing memory by periodically cleaning up stale data (e.g., throttle map entries).
 * - Exposing connection status and last successful connection time.
 * - Reloading QuickBar configurations in the [QuickBarService] upon starting.
 * - Processing special entity attributes (e.g., for climate, fan, light domains) to ensure
 *   consistent state representation and persistence.
 *
 * It implements [HomeAssistantListener] to react to events from the [HomeAssistantClient].
 * The connection lifecycle is managed within a [CoroutineScope] using [Dispatchers.IO].
 */
object BackgroundHaConnectionManager : HomeAssistantListener {
    private const val TAG = "BgHaConn"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var client: HomeAssistantClient? = null
    @Volatile private var running = false

    // Backoff (seconds): 1,2,4,8,16 (cap 30s) + jitter
    private var backoffSec = 1

    private val updateThrottleMap = ConcurrentHashMap<String, Long>()
    private val UPDATE_THROTTLE_MS = 250L // Min time between updates for same entity

    private var lastSuccessfulConnectionTime = 0L

    private var cleanupJob: Job? = null

    /**
     * Returns the timestamp of the last successful connection in milliseconds since epoch.
     * Returns 0 if no successful connection has been established yet.
     */
    fun getLastSuccessfulConnectionTime(): Long {
        return lastSuccessfulConnectionTime
    }


    private fun performMemoryCleanup() {
        // Clear throttle map entries older than 5 minutes
        val now = System.currentTimeMillis()
        val initialSize = updateThrottleMap.size
        updateThrottleMap.entries.removeIf { (_, timestamp) -> now - timestamp > 5 * 60 * 1000 }

        if (initialSize != updateThrottleMap.size) {
            Log.d(TAG, "Memory cleanup: removed ${initialSize - updateThrottleMap.size} throttle entries")
        }
    }

    fun isRunning() = running

    fun getClient(): HomeAssistantClient? = client

    fun start(context: Context) {
        Log.d(TAG, "Starting background connection manager. Already running: $running")
        if (running) return
        running = true
        registerNetworkCallback(context)

        // Force reload QuickBars in the service
        val reloadIntent = Intent("ACTION_RELOAD_TRIGGER_KEYS")
        reloadIntent.putExtra("FORCE_FULL_RELOAD", true)
        reloadIntent.setPackage(context.packageName)
        context.sendBroadcast(reloadIntent)

        // Start the cleanup job when the manager starts
        cleanupJob = scope.launch {
            while (isActive) {
                delay(30 * 60 * 1000) // Every 30 minutes
                performMemoryCleanup()
            }
        }

        scope.launch {
            connectLoop(context.applicationContext)
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping background connection manager")
        running = false
        try { unregisterNetworkCallback() } catch (_: Throwable) {}

        // Cancel the cleanup job explicitly
        cleanupJob?.cancel()
        cleanupJob = null

        scope.coroutineContext.cancelChildren()
        client?.disconnect()
        client = null
        HAStateStore.setConnection(ConnectionState.Disconnected)
    }

    private suspend fun connectLoop(appCtx: Context) {
        var reconnectCount = 0
        while (running) {
            reconnectCount++
            Log.d(TAG, "Connection attempt #$reconnectCount")

            // Skip connecting if client is already connected
            if (client?.isConnected() == true) {
                Log.d(TAG, "Client already connected, skipping connection attempt")
                delay(5000) // Check again in 5 seconds
                continue
            }

            val url = SecurePrefsManager.getHAUrl(appCtx)
            val token = SecurePrefsManager.getHAToken(appCtx)
            if (url.isNullOrBlank() || token.isNullOrBlank()) {
                Log.w(TAG, "Missing HA url/token; sleeping.")
                HAStateStore.setConnection(ConnectionState.Error(ConnectionState.Reason.AUTH_FAILED))
                delay(10_000)
                continue
            }

            try {
                val c = HomeAssistantClient(url, token, this, appCtx)
                client = c
                c.connect()

                // Wait until connected or failed/time out
                val state = withTimeoutOrNull(10_000) {
                    c.connectionState
                        .filterNot { it is ConnectionState.Connecting }
                        .first()
                }

                when (state) {
                    is ConnectionState.Connected -> {
                        lastSuccessfulConnectionTime = System.currentTimeMillis()
                        Log.d(TAG, "Successfully connected to HA!")
                        HAStateStore.setConnection(state)
                        backoffSec = 1

                        // Keep the loop parked while connected; when disconnects happen
                        // HomeAssistantClient will invoke onConnectionClosed() and we continue.
                        c.connectionState.onEach { s -> HAStateStore.setConnection(s) }
                            .launchIn(scope)

                        // Suspend until disconnected
                        waitUntilDisconnected(c)
                    }
                    is ConnectionState.Error -> {
                        Log.d(TAG, "Connection failed: ${state.reason}. Will retry with backoff.")
                        HAStateStore.setConnection(state)
                        delayWithBackoff()
                    }
                    else -> { // timeout or null
                        Log.d(TAG, "Connection timed out. Will retry with backoff.")
                        HAStateStore.setConnection(ConnectionState.Error(ConnectionState.Reason.TIMEOUT))
                        delayWithBackoff()
                    }
                }
                Log.d(TAG, "Connect loop exited, running=$running")
            } catch (t: Throwable) {
                Log.e(TAG, "connectLoop error", t)
                HAStateStore.setConnection(ConnectionState.Error(ConnectionState.Reason.UNKNOWN))
                delayWithBackoff()
            } finally {
                client?.disconnect()
                client = null
            }
        }
    }

    private suspend fun waitUntilDisconnected(c: HomeAssistantClient) {
        withTimeoutOrNull(30 * 60 * 1000L) { // 30-minute maximum session
            while (running && c.isConnected()) {
                try {
                    // Send ping/heartbeat every 5 minutes to detect zombie connections
                    if (System.currentTimeMillis() % (5 * 60 * 1000) < 1000) {
                        c.ping()
                    }
                    delay(1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in connection monitoring", e)
                    break
                }
            }
        }
    }

    private suspend fun delayWithBackoff() {
        val jitter = Random.nextLong(0, 300)
        delay(backoffSec * 1000L + jitter)
        backoffSec = min(backoffSec * 2, 30)
    }

    // ——— HomeAssistantListener ———
    override fun onEntitiesFetched(categories: List<CategoryItem>) {
        HAStateStore.setCategories(categories)
    }
    override fun onEntityStateUpdated(entityId: String, newState: String, attributes: JSONObject) {
        // Get current entity from the store
        val cur = HAStateStore.entitiesById.value[entityId]
        if (cur != null) {
            // Only apply throttling to non-saved entities
            if (!cur.isSaved) {
                // Skip rapid updates for non-saved entities
                val now = System.currentTimeMillis()
                val lastUpdate = updateThrottleMap[entityId] ?: 0L

                if (now - lastUpdate < UPDATE_THROTTLE_MS) {
                    return
                }

                updateThrottleMap[entityId] = now
            }

            // Create updated entity with new state and attributes
            val updatedEntity = cur.copy(
                state = newState,
                attributes = attributes,
                // IMPORTANT: Preserve these values from the original entity
                customName = cur.customName,
                customIconOnName = cur.customIconOnName,
                customIconOffName = cur.customIconOffName,
                isSelected = cur.isSelected,
                isSaved = cur.isSaved,
                isActionable = cur.isActionable,
                isAvailable = cur.isAvailable,
                lastKnownState = cur.lastKnownState,
                pressActions = cur.pressActions,
                defaultPressActionsApplied = cur.defaultPressActionsApplied,
                pressTargets = cur.pressTargets,
                requireConfirmation = cur.requireConfirmation,
                overrideService = cur.overrideService,
                overrideServiceData = cur.overrideServiceData
            )

            // Process special entity attributes based on domain
            processSpecialEntityAttributes(updatedEntity)

            // Update the store with processed entity
            HAStateStore.updateEntity(entityId, updatedEntity)
        }
    }

    override fun onEntityStateChanged(entityId: String, newState: String) {
        val cur = HAStateStore.entitiesById.value[entityId]
        if (cur != null) {
            HAStateStore.updateEntity(entityId, cur.copy(state = newState))
        }
    }

    override fun onQuickBarAliasTriggered(alias: String) {
        // Bounce to the running service (main thread), same pattern as camera
        QuickBarService.serviceInstance?.runOnMain {
            QuickBarService.serviceInstance?.onQuickBarAliasTriggered(alias)
        } ?: Log.w("BgHaConn", "Service not running; quickbar alias ignored: $alias")
    }

    override fun onCameraAliasTrigger(cameraAlias: String) {
        if (cameraAlias.isBlank()) return

        // Legacy alias → wrap to CameraRequest and route via the new handler
        val req = CameraRequest(cameraAlias = cameraAlias)
        QuickBarService.serviceInstance?.runOnMain {
            QuickBarService.serviceInstance?.handleCameraRequest(req)
        } ?: Log.w("BgHaConn", "Service not running; camera alias ignored: $cameraAlias")
    }

    override fun onNotifyReceived(spec: NotificationSpec) {
        Log.i(TAG, "onPromptNotifyReceived: $spec")
        // Single hand-off point to the UI/service layer
        QuickBarService.handleNotificationFromHa(spec)
    }

    override fun onCameraRequest(req: CameraRequest) {
        // Bounce to the running service (main thread) like notify does
        QuickBarService.serviceInstance?.runOnMain {
            QuickBarService.serviceInstance?.handleCameraRequest(req)
        } ?: Log.w("BgHaConn", "Service not running; camera request ignored: $req")
    }

    /**
     * Process special entity attributes just like QuickBarOverlay does
     */
    private fun processSpecialEntityAttributes(entity: EntityItem) {
        val context = client?.getContext() ?: return
        val savedEntitiesManager = SavedEntitiesManager(context)

        // First, ensure entity has valid icons before any other processing
        if (entity.customIconOnName.isNullOrEmpty()) {
            entity.customIconOnName = EntityIconMapper.getDefaultOnIconForEntityName(entity.id)
        }

        // Track original saved state
        val wasSaved = entity.isSaved

        // Process different entity types
        when {
            entity.id.startsWith("climate.") -> {
                if (entity.state != "off" && entity.state != "unavailable" && entity.state != "unknown") {
                    // Save current values
                    val tempBefore = entity.lastKnownState[EntityStateKeys.LAST_AC_TEMP]
                    val modeBefore = entity.lastKnownState[EntityStateKeys.LAST_AC_MODE]

                    try {
                        EntityStateUtils.captureClimateState(entity, savedEntitiesManager)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error capturing climate state: ${e.message}", e)
                    }

                    // Restore values if they were lost
                    if (entity.lastKnownState[EntityStateKeys.LAST_AC_TEMP] == null && tempBefore != null) {
                        entity.lastKnownState[EntityStateKeys.LAST_AC_TEMP] = tempBefore
                    }

                    if (entity.lastKnownState[EntityStateKeys.LAST_AC_MODE] == null && modeBefore != null) {
                        entity.lastKnownState[EntityStateKeys.LAST_AC_MODE] = modeBefore
                    }

                    // Ensure isSaved flag wasn't lost
                    if (wasSaved && !entity.isSaved) {
                        entity.isSaved = true
                    }

                    // Re-save if entity was saved
                    if (entity.isSaved) {
                        savedEntitiesManager.saveEntity(entity)
                    }
                }
            }
            entity.id.startsWith("fan.") -> {
                if (entity.state == "on") {
                    // Save current speed
                    val speedBefore = entity.lastKnownState[EntityStateKeys.LAST_FAN_SPEED]

                    try {
                        EntityStateUtils.captureFanSpeed(entity, savedEntitiesManager)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error capturing fan speed: ${e.message}", e)
                    }

                    // Restore speed if lost
                    if (entity.lastKnownState[EntityStateKeys.LAST_FAN_SPEED] == null && speedBefore != null) {
                        entity.lastKnownState[EntityStateKeys.LAST_FAN_SPEED] = speedBefore
                    }

                    // Ensure isSaved flag wasn't lost
                    if (wasSaved && !entity.isSaved) {
                        entity.isSaved = true
                    }

                    // Re-save if entity was saved
                    if (entity.isSaved) {
                        savedEntitiesManager.saveEntity(entity)
                    }
                }
            }
            entity.id.startsWith("light.") -> {
                // Track light attribute changes - this already works correctly
                val attrs = entity.attributes
                if (attrs != null && entity.state == "on") {
                    val brightness = attrs.optInt("brightness", -1)
                    val colorTemp = attrs.optInt("color_temp", -1)

                    if (brightness > 0 || colorTemp > 0) {
                        Log.d(TAG, "💡 Light update: ${entity.id} → " +
                                (if (brightness > 0) "brightness: ${(brightness * 100 / 255)}%, " else "") +
                                (if (colorTemp > 0) "temp: $colorTemp" else ""))
                    }
                }
            }
        }
        // Final check to ensure isSaved wasn't lost
        if (wasSaved && !entity.isSaved) {
            entity.isSaved = true
        }
    }


    // ——— Network awareness: reconnect as soon as the network is back ———
    private var cm: ConnectivityManager? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null

    private fun registerNetworkCallback(ctx: Context) {
        if (netCb != null) return
        cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        netCb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Nudge the loop to reconnect quickly
                scope.launch {
                    client?.disconnect()
                }
            }
        }
        cm?.registerDefaultNetworkCallback(netCb!!)
    }

    private fun unregisterNetworkCallback() {
        netCb?.let { cb ->
            cm?.unregisterNetworkCallback(cb)
        }
        netCb = null
        cm = null
    }

    // Public API for commands from UI:
    fun callService(domain: String, service: String, entityId: String, data: JSONObject? = null) {
        client?.callService(domain, service, entityId, data)
    }
}