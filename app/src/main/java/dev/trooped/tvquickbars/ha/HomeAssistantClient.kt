package dev.trooped.tvquickbars.ha

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.core.util.PatternsCompat
import dev.trooped.tvquickbars.background.HAStateStore
import dev.trooped.tvquickbars.data.CategoryItem
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.ha.ws.HaClientBridge
import dev.trooped.tvquickbars.ha.ws.MessageRouter
import dev.trooped.tvquickbars.ha.ws.handlers.QuickBarsNotifyHandler
import dev.trooped.tvquickbars.ha.ws.handlers.QuickBarsOpenHandler
import dev.trooped.tvquickbars.ha.ws.handlers.StateChangedHandler
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.utils.DemoModeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.json.JSONArray

/**
 * Represents the result of validating Home Assistant credentials.
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val reason: ConnectionState.Reason) : ValidationResult()
}

/**
 * Represents the result of fetching entities from Home Assistant.
 * This is used to encapsulate both successful and error states when fetching entities.
 */
sealed class FetchResult {
    data class Success(val categories: List<CategoryItem>) : FetchResult()
    data class Error(val reason: ConnectionState.Reason) : FetchResult()
}


/**
 * HomeAssistantClient Class
 * This class handles communication with the Home Assistant server.
 * It's responsible for establishing a WebSocket connection, sending authentication
 * messages, sending service calls, and receiving state updates.
 * @param url The URL of the Home Assistant server.
 * @param token The authentication token for Home Assistant.
 * @param listener The listener to receive state updates and other events.
 * @property client The OkHttp client for making HTTP requests.
 * @property webSocket The WebSocket connection to Home Assistant.
 * @property nextCommandId The ID for the next command to be sent to Home Assistant.
 * @property _connectionState The current connection state of the client.
 * @property connectionState A StateFlow representing the current connection state.
 * @property knownIds A set of known entity IDs after the last full get_states call.
 * @property isInDemoMode Whether the client is in demo mode.
 */
class HomeAssistantClient(
    private val url: String,
    private val token: String,
    private val listener: HomeAssistantListener,
    private val appContext: Context? = null
)  {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var nextCommandId = 2
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    val  connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile private var latestStatesStr: String? = null
    fun getLatestStatesJson(): org.json.JSONArray? =
        latestStatesStr?.let { org.json.JSONArray(it) }

    private val knownIds = mutableSetOf<String>() // All known entity IDs after the last full get_states

    private val isInDemoMode: Boolean
        get() = DemoModeManager.isInDemoMode

    private val messageRouter by lazy {
        MessageRouter(
            listOf(
                StateChangedHandler(),
                QuickBarsOpenHandler(),
                QuickBarsNotifyHandler(),
            )
        )
    }

    private val bridge = object : HaClientBridge {
        override val listener = this@HomeAssistantClient.listener
        override val knownIds = this@HomeAssistantClient.knownIds

        override fun getContext() = this@HomeAssistantClient.getContext()
        override fun fireEvent(type: String, data: JSONObject) =
            this@HomeAssistantClient.fireEvent(type, data)

        override fun send(obj: JSONObject): Boolean =
            webSocket?.send(obj.toString()) ?: false

        override fun nextId(): Int = nextCommandId++

        override fun updateConnectionState(state: ConnectionState) { _connectionState.value = state }

        override fun getLatestStatesJson(): org.json.JSONArray? =
            this@HomeAssistantClient.getLatestStatesJson()
    }

    private val TAG = "HAClient"

    private val EVENT_REQ = "quickbars_config_request"
    private val EVENT_RES = "quickbars_config_response"

    /**
     * Check if the client is currently connected to Home Assistant.
     * @return True if connected, false otherwise.
     */
    fun isConnected() = connectionState.value is ConnectionState.Connected

    /**
     * Get the last error that occurred during the connection.
     * @return The last error as a ConnectionState.Error, or null if no error occurred.
     */
    val lastError: ConnectionState.Error? get() =
        connectionState.value as? ConnectionState.Error

    /**
     * Connect to the Home Assistant server.
     */
    fun connect() {
        if (_connectionState.value is ConnectionState.Connected ||
            _connectionState.value is ConnectionState.Connecting) {
            Log.i(TAG, "connect() called but already connected/connecting, skipping")
            return
        }

        Log.i(TAG, "connect() called")
        if (isInDemoMode) {
            _connectionState.value = ConnectionState.Connecting
            Handler(Looper.getMainLooper()).postDelayed({
                _connectionState.value = ConnectionState.Connected
                val demo = DemoModeManager.getDemoCategories(getContext() ?: return@postDelayed)
                listener.onEntitiesFetched(demo)
            }, 800)
            return
        }

        _connectionState.value = ConnectionState.Connecting

        val wsUrl =
            if (url.startsWith("ws://") || url.startsWith("wss://")) url.trim()
            else formatUrlToWebsocketUrl(url) // keep your existing logic

        Log.d("HomeAssistantClient", "Connecting with WebSocket URL: $wsUrl")

        val request = try {
            Request.Builder().url(wsUrl).build()
        } catch (iae: IllegalArgumentException) {
            // Don’t crash — treat as bad input and exit gracefully
            _connectionState.value = ConnectionState.Error(ConnectionState.Reason.BAD_URL)
            Log.w(TAG, "Invalid WebSocket URL (parse failed): '$wsUrl' from input '$url'", iae)
            return
        }

        val listener = HomeAssistantWebSocketListener()
        webSocket = client.newWebSocket(request, listener)
    }

    /**
     * Disconnect from the Home Assistant server.
     */
    fun disconnect() {
        webSocket?.close(1000, "Client Disconnecting")
        client.dispatcher.executorService.shutdown()
    }

    /**
     * Reconcile saved entities with the known IDs.
     * This updates the availability of saved entities based on the current known IDs.
     * @param manager The SavedEntitiesManager to manage saved entities.
     */
    fun reconcileSavedEntities(manager: SavedEntitiesManager) {
        // Guard: never flip everything to unavailable on an empty snapshot
        if (knownIds.isEmpty()) {
            Log.w(TAG, "reconcileSavedEntities skipped: knownIds is empty")
            return
        }

        val saved = manager.loadEntities()
        var changed = false

        for (e in saved) {
            val was = e.isAvailable
            e.isAvailable = e.id in knownIds
            if (e.isAvailable != was) changed = true
        }

        if (changed) manager.saveEntities(saved)       // overwrite in prefs
    }

    fun fireEvent(eventType: String, data: JSONObject): Boolean {
        if (_connectionState.value !is ConnectionState.Connected) return false
        val msg = JSONObject().apply {
            put("id", nextCommandId++)
            put("type", "fire_event")
            put("event_type", eventType)
            put("event_data", data)
        }
        return webSocket?.send(msg.toString()) ?: false
    }

    /**
     * A companion object containing utility functions related to Home Assistant.
     * These functions can be used without needing an instance of HomeAssistantClient.
     */
    companion object {
        /**
         * A reusable, self-contained function to test HA credentials.
         * It creates a temporary client, checks the connection, and returns a result.
         * @return ValidationResult.Success or ValidationResult.Error with a specific reason.
         */
        suspend fun validateCredentials(url: String, token: String): ValidationResult {
            if (DemoModeManager.isDemoCredentials(url, token)) {
                return ValidationResult.Success
            }

            // A dummy listener, since we only care about the connection state for validation.
            val listener = object : HomeAssistantListener { /* No-op */
                override fun onEntitiesFetched(categories: List<CategoryItem>) {
                }
                override fun onEntityStateChanged(
                    entityId: String,
                    newState: String
                ) {
                }
            }
            val tempClient = HomeAssistantClient(url, token, listener)
            tempClient.connect()

            // Wait up to 10 seconds for the first significant connection event.
            val resultState = withTimeoutOrNull(10_000) {
                tempClient.connectionState
                    .filterNot { it is ConnectionState.Connecting || it is ConnectionState.Disconnected }
                    .first() // Wait for the first Connected or Error state
            }

            tempClient.disconnect()

            // Convert the final state into our simple ValidationResult
            return when (resultState) {
                is ConnectionState.Connected -> ValidationResult.Success
                is ConnectionState.Error -> ValidationResult.Error(resultState.reason)
                else -> ValidationResult.Error(ConnectionState.Reason.TIMEOUT)
            }
        }

        /**
         * Formats a URL to a WebSocket URL.
         * @param input The input URL string.
         * @return A formatted WebSocket URL.
         */
        fun formatUrlToWebsocketUrl(input: String): String {
            val cleanInput = input.trim().trimEnd('/')

            // Check for cloud URLs or likely reverse proxies (no port needed)
            val isCloudOrReverseProxy = cleanInput.contains("remote.homeassistant.io") ||
                    cleanInput.contains("nabucasa.com") ||
                    cleanInput.contains("ui.nabu.casa") ||
                    cleanInput.contains(".ui.nabu.casa")

            try {
                // If protocol is missing, add https:// for cloud/proxy URLs, http:// for others
                val uriString = if (!cleanInput.contains("://")) {
                    if (isCloudOrReverseProxy) "https://$cleanInput" else "http://$cleanInput"
                } else {
                    cleanInput
                }

                val uri = uriString.toUri()
                val host = uri.host ?: return "ws://invalid:8123/api/websocket"

                // For cloud URLs or reverse proxies, don't add port
                if (isCloudOrReverseProxy) {
                    val protocol = if (uriString.startsWith("https://")) "wss" else "ws"
                    return "$protocol://$host/api/websocket"
                }

                // For standard URLs, use specified port or default 8123
                val port = if (uri.port != -1) uri.port else 8123
                val protocol = if (uriString.startsWith("https://")) "wss" else "ws"

                return "$protocol://$host:$port/api/websocket"
            } catch (e: Exception) {
                Log.e("HomeAssistantClient", "Error formatting URL: $cleanInput", e)

                // Last resort fallback - try to handle various formats
                val hostPart = cleanInput.replace("http://", "").replace("https://", "").split("/")[0]

                // If it seems to be a cloud URL or reverse proxy
                if (isCloudOrReverseProxy) {
                    return "wss://$hostPart/api/websocket"  // Assume HTTPS for reverse proxies
                }

                // Otherwise assume local URL with standard port
                return "ws://$hostPart:8123/api/websocket"
            }
        }

        /**
         * Validates credentials with automatic protocol fallback (HTTPS → HTTP)
         * @return ValidationResult with the working URL on success
         */
        suspend fun validateWithFallback(userInputUrl: String, token: String): ValidationResult {
            if (DemoModeManager.isDemoCredentials(userInputUrl, token)) {
                return ValidationResult.Success
            }

            // Clean the input URL
            val cleanInput = userInputUrl.trim().trimEnd('/')

            // Build the base list EXACTLY as you do now
            val baseUrlsToTry = mutableListOf<String>()
            if (cleanInput.startsWith("http://") || cleanInput.startsWith("https://")) {
                baseUrlsToTry.add(cleanInput)
                if (cleanInput.startsWith("https://")) {
                    baseUrlsToTry.add("http://" + cleanInput.substring(8))
                } else {
                    baseUrlsToTry.add("https://" + cleanInput.substring(7))
                }
            } else {
                baseUrlsToTry.add("https://$cleanInput")
                baseUrlsToTry.add("http://$cleanInput")
            }

            // expand with sensible port fallbacks
            val urlsToTry = expandWithPortFallbacks(baseUrlsToTry)

            Log.d("HomeAssistantClient", "Trying URLs in order: $urlsToTry")

            // Try each URL in sequence
            for (urlToTry in urlsToTry) {
                Log.d("HomeAssistantClient", "Validating with: $urlToTry")

                // Create a temporary listener for validation
                val listener = object : HomeAssistantListener {
                    override fun onEntitiesFetched(categories: List<CategoryItem>) {}
                    override fun onEntityStateChanged(entityId: String, newState: String) {}
                    override fun onEntityStateUpdated(entityId: String, newState: String, attributes: JSONObject) {}
                }

                // Create a client with the WebSocket URL
                val tempClient = HomeAssistantClient(urlToTry, token, listener)
                tempClient.connect()

                // Wait for connection result
                val resultState = withTimeoutOrNull(10_000) {
                    tempClient.connectionState
                        .filterNot { it is ConnectionState.Connecting || it is ConnectionState.Disconnected }
                        .first()
                }

                tempClient.disconnect()

                // If this URL works, return success
                if (resultState is ConnectionState.Connected) {
                    Log.d("HomeAssistantClient", "Connection successful with URL: $urlToTry")
                    return ValidationResult.Success
                }

                // If auth failed, no need to try other URLs
                if (resultState is ConnectionState.Error &&
                    (resultState.reason == ConnectionState.Reason.AUTH_FAILED ||
                            resultState.reason == ConnectionState.Reason.BAD_TOKEN)) {
                    return ValidationResult.Error(resultState.reason)
                }
            }

            // If all URLs failed, return error
            return ValidationResult.Error(ConnectionState.Reason.CANNOT_RESOLVE_HOST)
        }

        private fun expandWithPortFallbacks(baseCandidates: List<String>): List<String> {
            val out = LinkedHashSet<String>() // preserves insertion order, dedupes
            for (c in baseCandidates) {
                out += c
                val httpUrl = c.toHttpUrlOrNull() ?: continue
                when (httpUrl.scheme) {
                    "https" -> {
                        // Try explicit :443 as well (covers reverse proxies)
                        out += httpUrl.newBuilder().port(443).build().toString()
                    }
                    "http" -> {
                        // Typical HA port + plain HTTP default
                        out += httpUrl.newBuilder().port(8123).build().toString()
                        out += httpUrl.newBuilder().port(80).build().toString()
                    }
                }
            }
            return out.toList()
        }
    }

    /**
     * Send a service call to Home Assistant.
     * @param domain The domain of the service (e.g., "light").
     * @param service The name of the service (e.g., "toggle").
     * @param entityId The entity ID to perform the service on.
     * @param data Additional data for the service (optional).
     */
    fun callService(domain: String, service: String, entityId: String, data: JSONObject? = null) {
        if (isInDemoMode) {
            val newState = DemoModeManager.handleServiceCall(domain, service, entityId, data)
            Handler(Looper.getMainLooper()).postDelayed({
                listener.onEntityStateChanged(entityId, newState)
                val entity = DemoModeManager.getEntityById(entityId)
                if (entity != null) {
                    listener.onEntityStateUpdated(entityId, newState, entity.attributes ?: JSONObject())
                }
            }, 300)
            return
        }

        // Do not send if not Connected yet — this was causing success=false when BG WS was still authing
        if (_connectionState.value !is ConnectionState.Connected) {
            Log.w(TAG, "callService($domain.$service $entityId) ignored: not Connected")
            return
        }

        val commandId = nextCommandId++

        val serviceData = JSONObject().apply {
            // Always include entity_id in service_data for maximum compatibility
            put("entity_id", entityId)
            // Merge any extra data
            if (data != null) {
                for (key in data.keys()) {
                    put(key, data.get(key))
                }
            }
        }

        val serviceCallMessage = JSONObject().apply {
            put("id", commandId)
            put("type", "call_service")
            put("domain", domain)
            put("service", service)
            // Keep target as well (HA accepts both; safe, and future-proof for area/device targets)
            put("target", JSONObject().put("entity_id", entityId))
            put("service_data", serviceData)
        }

        val sent = webSocket?.send(serviceCallMessage.toString()) ?: false
        if (!sent) {
            Log.w(TAG, "callService send returned false for id=$commandId")
        }
    }

    /**
     * Sends a ping message to the Home Assistant server to keep the connection alive
     * or to check if the connection is still active.
     *
     * This function constructs a JSON message with type "ping" and a unique ID,
     * then sends it over the WebSocket connection.
     *
     * @return `true` if the ping message was successfully sent (or queued to be sent),
     *         `false` if the WebSocket is not connected, if an error occurs during JSON
     *         creation, or if the send operation fails.
     */
    fun ping(): Boolean {
        if (webSocket == null || _connectionState.value !is ConnectionState.Connected) {
            return false
        }

        try {
            val pingId = nextCommandId++
            val pingMessage = JSONObject().apply {
                put("id", pingId)
                put("type", "ping")
            }
            return webSocket?.send(pingMessage.toString()) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending ping", e)
            return false
        }
    }

    /**
     * Get the context from the listener if it is a Context.
     * This is useful for accessing resources or services in the Android framework.
     * @return The context if available, null otherwise.
     */
    fun getContext(): Context? {
        if (appContext != null) return appContext

        // If listener is a Context (like your Service), return it
        return if (listener is Context) {
            listener as Context
        } else {
            null
        }
    }

    /**
     * WebSocket listener for Home Assistant.
     * This is where we receive state updates from Home Assistant.
     * @property onOpen Called when the WebSocket connection is opened.
     * @property onMessage Called when a message is received from the server.
     * @property onClosing Called when the WebSocket connection is closing.
     * @property onFailure Called when the WebSocket connection fails.
     */
    inner class HomeAssistantWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket Connection Opened!")
            val authMessage = JSONObject().apply {
                put("type", "auth")
                put("access_token", token)
            }
            webSocket.send(authMessage.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = JSONObject(text)
            when (message.getString("type")) {
                "auth_ok" -> {
                    _connectionState.value = ConnectionState.Connected
                    val getStatesMessage = JSONObject().apply { put("id", 1); put("type", "get_states") }
                    webSocket.send(getStatesMessage.toString())
                    subscribeToEvents(webSocket)
                }
                "auth_invalid" -> _connectionState.value = ConnectionState.Error(ConnectionState.Reason.AUTH_FAILED)

                "result" -> {
                    val id = message.getInt("id")
                    val success = message.getBoolean("success")
                    if (!success) {
                        val err = message.optJSONObject("error")
                        Log.w(TAG, "msg result id=$id success=false error=${err?.toString()}")
                    } else {
                        Log.v(TAG, "msg result id=$id success=true")
                    }
                    val res = message.opt("result")
                    if (success && res is JSONArray) {
                        val looksLikeStates = (res.length() == 0) ||
                                (res.optJSONObject(0)?.has("entity_id") == true)
                        if (looksLikeStates) {
                            Log.i(TAG, "Processing get_states result id=$id size=${res.length()}")
                            processEntityData(res)
                            latestStatesStr = res.toString()
                        }
                    }
                }

                // ↓↓↓ NEW: fully routed event handling ↓↓↓
                "event" -> messageRouter.handleIncoming(message, bridge)
            }
        }

        /**
         * Called when the WebSocket connection is closing.
         * @param webSocket The WebSocket connection.
         * @param code The close code.
         * @param reason The reason for closing.
         */
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "websocket onClosed code=$code reason=$reason")
            if (_connectionState.value !is ConnectionState.Error) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        /**
         * Called when the WebSocket connection fails.
         * @param webSocket The WebSocket connection.
         * @param t The Throwable that caused the failure.
         * @param response The Response object, if available.
         */
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "websocket onFailure", t)
            if (response?.code == 401) {
                _connectionState.value = ConnectionState.Error(ConnectionState.Reason.AUTH_FAILED)
                return
            }

            val reason = when (t) {
                is java.net.UnknownHostException -> ConnectionState.Reason.CANNOT_RESOLVE_HOST
                is javax.net.ssl.SSLHandshakeException -> ConnectionState.Reason.SSL_HANDSHAKE
                is java.net.SocketTimeoutException -> ConnectionState.Reason.TIMEOUT
                else -> ConnectionState.Reason.NETWORK_IO
            }
            _connectionState.value = ConnectionState.Error(reason)
        }
    }

    /**
     * Sends a raw WebSocket message to Home Assistant.
     * This can be used to send custom subscription requests or other messages.
     * This function is currently not used - but will probably be useful in the future.
     * @param message The JSON message string to send
     * @return True if the message was sent successfully
     */
    fun sendRawMessage(message: String): Boolean {
        if (webSocket == null || _connectionState.value !is ConnectionState.Connected) {
            Log.e(TAG, "Cannot send message - WebSocket not connected")
            return false
        }

        return try {
            webSocket?.send(message) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WebSocket message", e)
            false
        }
    }

    /**
     * Subscribe to state change events from Home Assistant.
     * @param webSocket The WebSocket connection to Home Assistant.
     */
    private fun subscribeToEvents(webSocket: WebSocket) {
        val eventSubscriptionMessage = JSONObject().apply {
            put("id", nextCommandId++)
            put("type", "subscribe_events")
            put("event_type", "state_changed")
        }
        webSocket.send(eventSubscriptionMessage.toString())

        // Subscribe to both naming conventions to be safe
        val quickbarEventSubscriptionMessage = JSONObject().apply {
            put("id", nextCommandId++)
            put("type", "subscribe_events")
            put("event_type", "quickbars.open") // Original format
        }
        webSocket.send(quickbarEventSubscriptionMessage.toString())

        val cfgReq = JSONObject().apply {
            put("id", nextCommandId++)
            put("type", "subscribe_events")
            put("event_type", EVENT_REQ)
        }
        webSocket.send(cfgReq.toString())

        val quickbarsNotifySub = JSONObject().apply {
            put("id", nextCommandId++)
            put("type", "subscribe_events")
            put("event_type", "quickbars.notify")
        }
        webSocket.send(quickbarsNotifySub.toString())
    }

    /**
     * Process the raw entity data from Home Assistant.
     * @param allEntities The raw entity data from Home Assistant.
     */
    private fun processEntityData(allEntities: org.json.JSONArray) {
        val entityItemsByDomain = mutableMapOf<String, MutableList<EntityItem>>()

        knownIds.clear()

        // Loop through raw data and create EntityItem objects
        for (i in 0 until allEntities.length()) {
            val entity = allEntities.getJSONObject(i)
            val entityId = entity.getString("entity_id")
            val domain = entityId.split('.').first()
            val attributes = entity.getJSONObject("attributes")
            val friendlyName = attributes.optString("friendly_name", entityId)
            val state = entity.getString("state")

            knownIds += entityId

            val entityItem = EntityItem(
                id = entityId,
                friendlyName = friendlyName,
                state = state,
                attributes = attributes, // Store the full attributes object
                isSelected = false
            )
            entityItemsByDomain.getOrPut(domain) { mutableListOf() }.add(entityItem)
        }

        // Convert the grouped data into the final List<CategoryItem>
        val categoryList = entityItemsByDomain.map { (domain, entities) ->
            CategoryItem(
                name = domain.uppercase(),
                entities = entities.sortedBy { it.friendlyName },
            )
        }.sortedBy { it.name }

        val context = getContext()
        if (context != null) {
            reconcileSavedEntities(SavedEntitiesManager(context))
        } else {
            Log.w(TAG, "Context is null, skipping reconcileSavedEntities")
            // We still want to update the global state store even if we can't reconcile
        }

        // Use the listener to send the final, structured list back to the MainActivity
        listener.onEntitiesFetched(categoryList)
        HAStateStore.setCategories(categoryList) // Ensure state store is updated
        Log.d(TAG, "entitiesFetched categories=${categoryList.size} totalEntities=${categoryList.sumOf { it.entities.size }}")
    }
}

/**
 * Represents the connection state of the Home Assistant client.
 * This is used to track the current connection status and any errors that may occur.
 */
sealed interface ConnectionState {
    object Disconnected : ConnectionState           // never connected / closed
    object Connecting : ConnectionState             // attempting to connect
    object Connected    : ConnectionState           // fully authed + ready
    data class Error(val reason: Reason) : ConnectionState

    enum class Reason {
        AUTH_FAILED,
        BAD_TOKEN,
        CANNOT_RESOLVE_HOST,
        SSL_HANDSHAKE,
        TIMEOUT,
        NETWORK_IO,
        BAD_URL,
        UNKNOWN
    }
}