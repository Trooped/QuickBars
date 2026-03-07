package dev.trooped.tvquickbars.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.background.BackgroundHaConnectionManager
import dev.trooped.tvquickbars.background.HAStateStore
import dev.trooped.tvquickbars.camera.CameraPipOverlay
import dev.trooped.tvquickbars.camera.CameraPipSpec
import dev.trooped.tvquickbars.data.CategoryItem
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.data.QuickBar
import dev.trooped.tvquickbars.data.QuickBarPosition
import dev.trooped.tvquickbars.ha.ConnectionState
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.ha.HomeAssistantListener
import dev.trooped.tvquickbars.persistence.QuickBarManager
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.persistence.TriggerKeyManager
import dev.trooped.tvquickbars.ui.QuickBar.overlay.AnimatedQuickBar
import dev.trooped.tvquickbars.ui.EntityIconMapper
import dev.trooped.tvquickbars.utils.DemoModeManager
import dev.trooped.tvquickbars.utils.EntityActionExecutor
import dev.trooped.tvquickbars.utils.EntityStateKeys
import dev.trooped.tvquickbars.utils.EntityStateUtils
import dev.trooped.tvquickbars.utils.launchPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import androidx.core.content.edit
import dev.trooped.tvquickbars.QuickBarsApp
import dev.trooped.tvquickbars.camera.CameraPipController
import dev.trooped.tvquickbars.camera.CameraRequest
import dev.trooped.tvquickbars.data.TriggerKey
import dev.trooped.tvquickbars.notification.NotificationController
import dev.trooped.tvquickbars.notification.NotificationSpec

/**
 * QuickBarService Class
 * An AccessibilityService responsible for intercepting hardware key events to trigger Home Assistant
 * actions, display interactive overlays, or manage secondary UI elements like Camera PiP.
 *
 * This is the most important module in the project.
 *
 * This service operates in the following modes:
 *
 * 1. **Normal Operation (Event Remapping):** Listens for configured trigger keys.
 *    Supports Single, Double, and Long press gestures to either:
 *    - Show a QuickBar (Jetpack Compose overlay) for manual entity control.
 *    - Directly execute a Home Assistant service call (e.g., toggle a light).
 *
 * 2. **Key Capture Mode:** Used during the setup process to bind a physical remote button
 *    to a specific action by capturing the next valid key code.
 *
 * 3. **Overlay Interaction:** When a QuickBar is visible, this service manages focus,
 *    consumes navigation keys (D-pad), and implements an auto-hide timer that resets
 *    on user activity.
 *
 * 4. **Camera PiP (Picture-in-Picture):** Manages floating camera streams using
 *    the [CameraPipController], allowing users to monitor streams while using other apps.
 *
 * 5. **Notifications:** Handles incoming alerts via [NotificationController],
 *    displaying visual overlays with images and playing notification sounds.
 *
 * 6. **Failsafe Mechanism:** Provides a critical safety feature where holding a trigger
 *    key for 5 seconds toggles all remapping on/off. This prevents users from being
 *    permanently "locked out" of their system buttons.
 *
 * 7. **Resource Management:** Automatically manages the lifecycle of the Home Assistant
 *    connection, disconnecting after a period of inactivity ([disconnectDelayMs]) to
 *    save system resources.
 *
 * @property serviceScope Main-thread scope for UI-related coroutine work.
 * @property quickBarManager Handles persistence and loading of QuickBar configurations.
 * @property windowManager System service used to attach/detach overlay windows.
 * @property isOverlayVisible Reactive state tracking if a QuickBar is currently on screen.
 * @property camera Controller for managing Camera Picture-in-Picture overlays.
 * @property notifications Controller for showing visual/audio alerts.
 * @property failsafeKeyHoldEnabled Global toggle for button remapping, managed by the 5s hold gesture.
 * @property autoHideRunnable Pending task to hide the UI after the configured timeout.
 * @property triggerKeyManager Manages the mapping of hardware key codes to specific actions.
 * @property haClient The active connection to Home Assistant.
 */
class QuickBarService : AccessibilityService(), HomeAssistantListener {

    // ───── Coroutines / lifetime ───────────────────────────────────────────────────

    /**
     * Main-thread scope for service work that must interact with the UI/WindowManager
     * (QuickBar overlay, Compose, etc.). Cancel this in onDestroy().
     */
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    /** Collector job for connection/HA state; cancel/recreate as overlays come and go. */
    private var connectionStateJob: Job? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }
    private fun postNextFrame(block: () -> Unit) {
        Choreographer.getInstance().postFrameCallback { block() }
    }


// ───── Managers, data, and OS hooks ────────────────────────────────────────────

    /** Persisted trigger-key and QuickBar configuration. */
    private lateinit var quickBarManager: QuickBarManager

    /** Cached list/map of configured QuickBars to avoid recomputing lookups on every press. */
    private var configuredBars: List<QuickBar> = emptyList()
    private var quickBarMap: Map<String, QuickBar> = emptyMap()

    /** System window manager used to add/remove our overlay surfaces. */
    private lateinit var windowManager: WindowManager

    /** The current overlay root view (ComposeView) if visible, otherwise null. */
    private var overlayView: View? = null

    /** The active HA client (background or on-demand) used by the QuickBar. */
    private var haClient: HomeAssistantClient? = null

    /** Which QuickBar is currently shown (for restoring/closing logic), if any. */
    private var currentBarId: String? = null


// ───── Key capture mode (for assigning trigger keys) ───────────────────────────

    /** When true, we intercept the next key to bind it in settings instead of running actions. */
    private var isInCaptureMode = false

    /** Tracks the key code pressed during capture so we complete on the matching ACTION_UP. */
    private var capturedKeyDownCode = -1


// ───── Gesture timing / UI thread handler ──────────────────────────────────────

    /** Main looper handler used for long-press/double-press timers and UI-safe Runnables. */
    private val handler = Handler(Looper.getMainLooper())

    /** Max window to wait for a second tap before treating the first as SINGLE. */
    private val doublePressTimeout: Long = 400

    /** -1 when no tap is pending; otherwise the key awaiting a potential second tap. */
    private var doublePressPendingKeyCode: Int = -1

    /** Threshold (ms) to consider a press a long-press. */
    private val longPressTimeout: Long = 750

    /**
     * Records ACTION_DOWN timestamps for keys so we can compute hold duration on ACTION_UP,
     * and decide between SINGLE/LONG.
     */
    private val keyDownStartTimes = mutableMapOf<Int, Long>()

    /** Cached orientation flag for sizing/positioning the overlay window. */
    private var isHorizontal: Boolean = false

    /** Direct reference to the ComposeView hosting the QuickBar (if present). */
    private var composeView: ComposeView? = null


// ───── Compose lifecycle proxies (to bind ComposeView to Service lifecycle) ────

    /** Lifecycle owner used by the QuickBar ComposeView. */
    private val quickBarLifecycleOwner = ComposeViewLifecycleOwner()
    /** True after we created the QuickBar lifecycle once this process lifetime. */
    private var quickBarLifecycleCreated = false


// ───── Reactive UI state for Compose ───────────────────────────────────────────

    /** Whether the QuickBar overlay is currently visible (drives AnimatedVisibility etc). */
    private val isOverlayVisible = mutableStateOf(false)

    /** Provides per-key mapping and enabled/disabled state. */
    private lateinit var triggerKeyManager: TriggerKeyManager

    /** The live list backing the QuickBar UI grid/list (Compose observes this). */
    private val entitiesState = mutableStateListOf<EntityItem>()

    /** Last time (ms) we updated each entity; used for throttling/coalescing updates. */
    private val lastEntityUpdateTime = mutableMapOf<String, Long>()


    /** Delay before disconnecting an on-demand HA client after UI inactivity. */
    private val disconnectDelayMs = 30_000L

    /** Job scheduled to disconnect the client after `disconnectDelayMs`, if any. */
    private var disconnectJob: Job? = null

    /** Latest connection error (shown inline in the QuickBar header). Null when OK. */
    private var connectionErrorMessage = mutableStateOf<String?>(null)

    // ───── QuickBar auto-hide timer ─────────────────────────────────────────────

    /** Pending runnable for the current QuickBar auto-hide timeout, if any. */
    private var autoHideRunnable: Runnable? = null

    /** Schedule auto-hide for the currently visible QuickBar. 0/negative = disabled. */
    private fun scheduleAutoHideTimeout(delaySeconds: Int) {
        // Always clear any previous timer, in case we’re switching bars
        cancelAutoHideTimeout()
        if (delaySeconds <= 0) return

        val r = Runnable {
            // Safe: hideOverlay() no-ops if the view is already gone
            hideOverlay()
        }
        autoHideRunnable = r
        handler.postDelayed(r, delaySeconds * 1000L)
        //Log.d("QuickBarService", "Scheduled auto-hide for $delaySeconds seconds")
    }

    /** Cancel any pending QuickBar auto-hide timeout. */
    private fun cancelAutoHideTimeout() {
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = null
    }


// ───── Camera PiP overlay (separate Compose surface) ───────────────────────────

    private lateinit var camera: CameraPipController


// ───── Per-key timers/state for gestures (long/double) and ownership ──────────

    /** Pending Runnables keyed by keyCode for double-press timeouts. */
    private val doublePressRunnables = mutableMapOf<Int, Runnable>()

    /** Pending Runnables keyed by keyCode for long-press detection. */
    private val longPressRunnables  = mutableMapOf<Int, Runnable>()

    /** Set of keys whose long-press already fired (prevents duplicate long logic on UP). */
    private val longPressFired      = mutableSetOf<Int>()

    /**
     * When a double-press fires, swallow the second tap so the system/foreground app
     * doesn’t also treat it as a separate click (safer UX on TVs).
     */
    private val consumeSecondTapOnDouble = true

    /** Which key opened the current QuickBar; used to allow “press same key to close”. */
    private var overlayOwnerKeyCode: Int = -1

    /** True after DOWN of the owner key while overlay is visible, allowing close on UP. */
    private var ownerKeyArmedToClose: Boolean = false

    /** Flow collector for entity updates while overlay is mounted. */
    private var entitiesJob: Job? = null


// ───── Failsafe (hold a trigger key to toggle remaps) ─────────────────────────

    /** Duration (ms) the user must hold a trigger key to toggle the failsafe on/off. */
    private val FAILSAFE_HOLD_MS = 5000L

    /**
     * After re-enabling remaps via failsafe, we swallow further events for that key
     * until ACTION_UP arrives (prevents an accidental SINGLE firing).
     */
    private var swallowUntilKeyUp: Int = -1

    /**
     * After disabling remaps via failsafe, we swallow further events for that key
     * until ACTION_UP arrives so the system doesn’t perform its native action.
     */
    private var swallowSystemUntilKeyUp: Int = -1

    /**
     * Whether trigger-key remaps are currently enabled. Marked volatile because it’s read
     * from handlers/runnables posted to the main Looper.
     */
    @Volatile private var failsafeKeyHoldEnabled: Boolean = true


    // ───── Notification/notification overlay (image + sound) ───────────────────────────────────────────
    private lateinit var notifications: NotificationController

    /** App-scoped preferences for persisting the failsafe state across restarts. */
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("quickbar_prefs", MODE_PRIVATE)
    }

    /** Reads persisted “trigger keys enabled” state. */
    private fun isTriggerKeysEnabled(): Boolean =
        prefs.getBoolean("trigger_keys_enabled", true)

    /** Persists “trigger keys enabled” state. */
    private fun saveTriggerKeysEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("trigger_keys_enabled", enabled) }
    }

    /** One pending Runnable per keyCode for the 5s failsafe hold. */
    private val failsafeHoldRunnables = mutableMapOf<Int, Runnable>()

    private val entityActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "dev.trooped.tvquickbars.ENTITY_ACTION") {
                val entityId = intent.getStringExtra("ENTITY_ID") ?: return
                val pressType = intent.getStringExtra("PRESS_TYPE")

                // Get the entity domain
                val domain = entityId.substringBefore('.')

                // Check if we should auto-close
                if (currentBarId != null) {
                    val currentBar = quickBarManager.loadQuickBars().find { it.id == currentBarId }

                    if (currentBar != null) {
                        if (currentBar.autoCloseQuickBarDomains.contains(domain)) {
                            hideOverlay()
                        }
                    }
                    /*
                    else {
                        Log.e("AutoClose", "Current bar ID ($currentBarId) doesn't match any loaded QuickBar")
                    }
                     */
                } else {
                    Log.d("AutoClose", "No QuickBar is currently open - nothing to close")
                }
            }
        }
    }

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_RELOAD_TRIGGER_KEYS") {
                val force = intent.getBooleanExtra("FORCE_FULL_RELOAD", false)
                if (force) {
                    triggerKeyManager = TriggerKeyManager(this@QuickBarService)
                } else {
                    triggerKeyManager.clearCache()
                }
                quickBarMap = quickBarManager.loadQuickBars().associateBy { it.id }
                Log.d("QuickBarService", "Reloaded trigger keys & QuickBars (force=$force)")
            }
        }
    }


    companion object {
        @Volatile
        var isRunning = false

        const val ACTION_SHOW_CAMERA_PIP = "dev.trooped.tvquickbars.SHOW_CAMERA_PIP"
        const val EXTRA_CAMERA_SPEC = "CAMERA_SPEC"

        fun handleNotificationFromHa(spec: NotificationSpec) {
            serviceInstance?.runOnMain {
                serviceInstance?.notifications?.enqueue(spec)
            } ?: Log.w("QuickBarService", "Service not running; notification ignored")
        }

        // reference to running service (set in onServiceConnected / cleared onDestroy)
        @Volatile internal var serviceInstance: QuickBarService? = null
    }

    /**
     * Called when the service is started.
     * @param intent The intent that started the service.
     * @param flags Additional data about the start request.
     * @param startId A unique identifier for the start request.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::quickBarManager.isInitialized) {
            quickBarManager = QuickBarManager(this)
        }

        if (!::triggerKeyManager.isInitialized) {
            triggerKeyManager = TriggerKeyManager(this)
        }

        // This is how the editor communicates with us.
        when (intent?.action) {
            "ACTION_CAPTURE_KEY" -> {
                isInCaptureMode = true
                capturedKeyDownCode = -1
                Log.i("QuickBarService", "Entering key capture mode")
            }
            "ACTION_CANCEL_CAPTURE" -> {
                isInCaptureMode = false
                capturedKeyDownCode = -1
                Log.i("QuickBarService", "Exiting key capture mode")
            }
            "ACTION_RELOAD_TRIGGER_KEYS" -> {
                // Check if we need a full reload
                val forceFullReload = intent.getBooleanExtra("FORCE_FULL_RELOAD", false)

                if (forceFullReload) {
                    // Re-initialize the TriggerKeyManager completely
                    triggerKeyManager = TriggerKeyManager(this)
                } else {
                    // Just clear the cache and reload
                    triggerKeyManager.clearCache()
                }

                // Also reload QuickBars map
                quickBarMap = quickBarManager
                    .loadQuickBars()
                    .associateBy { it.id }
            }
        }
        return START_STICKY
    }

    /**
     * Called when the service is connected to the system.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        serviceInstance = this
        if (!quickBarLifecycleCreated) {
            quickBarLifecycleOwner.create()
            quickBarLifecycleCreated = true
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        notifications = NotificationController(
            context = this,
            windowManager = windowManager,
            runOnMain = ::runOnMain,
            serviceScope = serviceScope
        )
        notifications.onServiceConnected()

        camera = CameraPipController(
            context = this,
            windowManager = windowManager,
            runOnMain = ::runOnMain,
            showToast = ::showToast
        )

        quickBarManager = QuickBarManager(this)
        triggerKeyManager = TriggerKeyManager(this)

        // Load data asynchronously to avoid blocking the main thread
        serviceScope.launch(Dispatchers.IO) {
            try {
                val loadedBars = quickBarManager.loadQuickBars()
                // Switch back to the Main thread to update UI-related properties
                withContext(Dispatchers.Main) {
                    configuredBars = loadedBars
                    quickBarMap = loadedBars.associateBy { it.id }
                    Log.d("QuickBarService", "Successfully loaded bars and triggers.")
                }
            } catch (e: Exception) {
                Log.e("QuickBarService", "Failed to load initial data.", e)
                // Handle the error, maybe show a toast on the main thread
            }
        }

        // Register the new reload trigger keys receiver (app-local, not exported)
        ContextCompat.registerReceiver(
            this,
            reloadReceiver,
            IntentFilter("ACTION_RELOAD_TRIGGER_KEYS"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )


        val entityActionFilter = IntentFilter("dev.trooped.tvquickbars.ENTITY_ACTION")
        LocalBroadcastManager.getInstance(this).registerReceiver(
            entityActionReceiver,
            entityActionFilter
        )

        failsafeKeyHoldEnabled = isTriggerKeysEnabled()
    }


    /**
     * Intercepts every remote‑key event while the service is running.
     *
     *  • If the key is **not** one of ours → immediately return super.onKeyEvent().
     *  • Otherwise decide (SINGLE / DOUBLE / LONG) and either
     *      – open a QuickBar overlay, or
     *      – call a Home‑Assistant service for an entity.
     *
     * Timing notes
     * ────────────
     * keyDownStartTimes ........ measures hold‑duration for long‑press.
     * doublePressPendingKeyCode . remembers “first tap” while we wait to see if a second arrives.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // 0) Sanity
        val keyCodeRaw = event?.keyCode ?: return super.onKeyEvent(event)
        val keyCode = normalizeConfirm(keyCodeRaw)

        // 1) Overlay visible?
        if (overlayView != null && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == overlayOwnerKeyCode)) {
            return handleOverlayWhileVisible(event, keyCode)
        }
        // Restart the auto hide countdown whenever the QuickBar is visible AND we click on dpad keys.
        else if (overlayView != null){
            restartAutoHide(event)
        }

        // Never intercept confirm key pair
        if (isConfirmKey(keyCodeRaw)) {
            return false
        }

        // Remaps disabled → only allow the failsafe hold on trigger keys; otherwise pass through
        if (!failsafeKeyHoldEnabled) {
            val tk = triggerKeyManager.getTriggerKey(keyCode)
            if (tk != null && tk.enabled && tk.hasAnyAssignments()) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> scheduleFailsafeHold(keyCodeRaw)
                    KeyEvent.ACTION_UP   -> cancelFailsafeHold(keyCodeRaw)
                }
            }
            return false
        }

        // Guards: swallow gestures that toggled failsafe
        handleSwallowSystemGuard(keyCodeRaw, event)?.let { return it }
        handleSwallowEnableGuard(keyCodeRaw, event)?.let { return it }


        // 2) Capture mode
        if (isInCaptureMode) {
            return handleCaptureMode(event, keyCode)
        }

        // 3) Trigger lookup
        val triggerKey = triggerKeyManager.getTriggerKey(keyCode)
        if (triggerKey == null || !triggerKey.enabled || !triggerKey.hasAnyAssignments()) {
            return false
        }

        // Failsafe arming while remaps enabled
        when (event.action) {
            KeyEvent.ACTION_DOWN -> scheduleFailsafeHold(keyCodeRaw)
            KeyEvent.ACTION_UP   -> cancelFailsafeHold(keyCodeRaw)
        }

        // 4/5) Gesture handling for this trigger
        return handleTriggerGestures(event, keyCode, triggerKey)
    }

    /* ───────────────────────── helpers ───────────────────────── */

    private fun restartAutoHide(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    // Get the current bar's configured timeout and restart the timer.
                    currentBarId?.let { barId ->
                        quickBarMap[barId]?.autoCloseDelay?.let { timeout ->
                            scheduleAutoHideTimeout(timeout)
                        }
                    }
                }
            }
        }
    }

    private fun KeyEvent.isCanceledCompat(): Boolean =
        (flags and (KeyEvent.FLAG_CANCELED or KeyEvent.FLAG_CANCELED_LONG_PRESS)) != 0

    private fun handleSwallowSystemGuard(keyCodeRaw: Int, event: KeyEvent): Boolean? {
        if (swallowSystemUntilKeyUp == keyCodeRaw) {
            if (event.action == KeyEvent.ACTION_UP || event.isCanceledCompat()) {
                swallowSystemUntilKeyUp = -1
                cancelFailsafeHold(keyCodeRaw)
            }
            return true
        }
        return null
    }

    private fun handleSwallowEnableGuard(keyCodeRaw: Int, event: KeyEvent): Boolean? {
        if (swallowUntilKeyUp == keyCodeRaw) {
            if (event.action == KeyEvent.ACTION_UP || event.isCanceledCompat()) {
                swallowUntilKeyUp = -1
                cancelFailsafeHold(keyCodeRaw)
            }
            return true
        }
        return null
    }

    private fun handleOverlayWhileVisible(event: KeyEvent, keyCode: Int): Boolean {
        // BACK closes as before
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // eat BACK immediately so the app behind never sees it
                    swallowSystemUntilKeyUp = keyCode
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    hideOverlay()
                    resetKeyGestureState(overlayOwnerKeyCode)
                    overlayOwnerKeyCode = -1
                    ownerKeyArmedToClose = false
                    // clear the guard now that UP arrived
                    swallowSystemUntilKeyUp = -1
                    return true
                }
                else -> return true
            }
        }

        // Same key that opened the QuickBar → allow close or failsafe
        if (keyCode == overlayOwnerKeyCode) {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    ownerKeyArmedToClose = true
                    scheduleFailsafeHold(event.keyCode)   // re-arm failsafe while overlay is open
                    true
                }
                KeyEvent.ACTION_UP -> {
                    cancelFailsafeHold(event.keyCode)
                    if (ownerKeyArmedToClose) {
                        hideOverlay()
                        resetKeyGestureState(keyCode)
                        overlayOwnerKeyCode = -1
                        ownerKeyArmedToClose = false
                    }
                    true // also swallow the UP from the original long-press
                }
                else -> true
            }
        }

        // Any other key: pass through
        return false
    }

    private fun handleCaptureMode(event: KeyEvent, keyCode: Int): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                isInCaptureMode = false
                capturedKeyDownCode = -1
                return false // let system handle BACK
            }
            if (isConfirmKey(keyCode)) {
                showToast("The OK/Enter button cannot be remapped.")
                isInCaptureMode = false
                capturedKeyDownCode = -1
                return true
            }
            capturedKeyDownCode = keyCode
        } else if (event.action == KeyEvent.ACTION_UP && keyCode == capturedKeyDownCode) {
            val keyName = KeyEvent.keyCodeToString(keyCode)
            quickBarManager.saveCapturedKey(keyCode, keyName)
            Intent("ACTION_KEY_CAPTURED").also { intent ->
                intent.putExtra("keyCode", keyCode)
                intent.putExtra("keyName", keyName)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
            isInCaptureMode = false
            capturedKeyDownCode = -1
        }
        return true
    }

    private fun handleTriggerGestures(
        event: KeyEvent,
        keyCode: Int,
        triggerKey: TriggerKey // whatever your type is
    ): Boolean {
        val longActionId     = triggerKey.longPressAction
        val longActionType   = triggerKey.longPressActionType
        val singleActionId   = triggerKey.singlePressAction
        val singleActionType = triggerKey.singlePressActionType
        val doubleActionId   = triggerKey.doublePressAction
        val doubleActionType = triggerKey.doublePressActionType

        val hasLong   = longActionId != null
        val hasSingle = singleActionId != null
        val hasDouble = doubleActionId != null

        // ACTION_DOWN: start timing, maybe arm long
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (!keyDownStartTimes.containsKey(keyCode)) {
                longPressFired.remove(keyCode) // clear stale "long already fired" from a previous gesture

                keyDownStartTimes[keyCode] = System.currentTimeMillis()
                if (hasLong) {
                    scheduleLongPress(keyCode, event.keyCode) {
                        if (longActionType == "quickbar") markQuickBarOwner(keyCode)
                        runAction(longActionId!!, longActionType)
                    }
                }
            }
            return true
        }

        // ACTION_UP: finalize
        val startTime   = keyDownStartTimes.remove(keyCode)
        val pressedMs   = if (startTime != null) System.currentTimeMillis() - startTime else 0L
        val didLongFire = longPressFired.contains(keyCode)
        cancelLongPress(keyCode)

        // Long attempted with no long action → toast + swallow
        if (!hasLong && pressedMs >= longPressTimeout) {
            showToast("No long-press action assigned")
            return true
        }

        // Long path: already fired or threshold on release
        if (hasLong && (didLongFire || pressedMs >= longPressTimeout)) {
            if (!didLongFire) {
                if (longActionType == "quickbar") markQuickBarOwner(keyCode)
                swallowSystemUntilKeyUp = event.keyCode
                runAction(longActionId!!, longActionType)
            }
            return true
        }

        // Double path
        if (hasDouble) {
            if (doublePressPendingKeyCode == keyCode) {
                doublePressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
                doublePressPendingKeyCode = -1
                if (doubleActionType == "quickbar") markQuickBarOwner(keyCode)
                runAction(doubleActionId!!, doubleActionType)
                return consumeSecondTapOnDouble
            } else {
                doublePressPendingKeyCode = keyCode
                doublePressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
                val r = Runnable {
                    if (doublePressPendingKeyCode == keyCode) {
                        doublePressPendingKeyCode = -1
                        if (hasSingle) {
                            if (singleActionType == "quickbar") markQuickBarOwner(keyCode)
                            runAction(singleActionId!!, singleActionType)
                        } else {
                            showToast("No single-press action assigned")
                        }
                    }
                }
                doublePressRunnables[keyCode] = r
                handler.postDelayed(r, doublePressTimeout)
                return true
            }
        }

        // Single (no double configured) — debounce against double
        if (hasSingle && !hasDouble) {
            if (doublePressPendingKeyCode == keyCode) {
                doublePressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
                doublePressPendingKeyCode = -1
                showToast("No double-press action assigned")
                return true
            }
            doublePressPendingKeyCode = keyCode
            doublePressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
            val r = Runnable {
                if (doublePressPendingKeyCode == keyCode) {
                    doublePressPendingKeyCode = -1
                    if (singleActionType == "quickbar") markQuickBarOwner(keyCode)
                    runAction(singleActionId!!, singleActionType)
                }
            }
            doublePressRunnables[keyCode] = r
            handler.postDelayed(r, doublePressTimeout)
            return true
        }

        // No assignments left (shouldn't happen)
        return false
    }

    /**
     * Schedules a long-press action for a given key code.
     * If a long-press is already scheduled for this key, it's cancelled first.
     * The provided [run] lambda will be executed after [longPressTimeout] milliseconds,
     * and the [keyCode] will be added to the [longPressFired] set.
     *
     * @param keyCodeNorm The normalized key code for which to schedule the long-press.
     * @param keyCode The raw key code for which to schedule the long-press.
     * @param run The lambda function to execute when the long-press fires.
     */
    private fun scheduleLongPress(keyCodeNorm: Int, keyCodeRaw: Int, run: () -> Unit) {
        cancelLongPress(keyCodeNorm)
        val r = Runnable {
            // gesture state → normalized
            longPressFired.add(keyCodeNorm)
            keyDownStartTimes.remove(keyCodeNorm)
            longPressRunnables.remove(keyCodeNorm) // hygiene
            swallowSystemUntilKeyUp = keyCodeRaw
            run()
        }
        longPressRunnables[keyCodeNorm] = r
        handler.postDelayed(r, longPressTimeout)
    }

    /**
     * Cancels any pending long-press action for the given key code.
     * This involves removing the scheduled runnable from the handler
     * and clearing the long-press fired flag for that key.
     *
     * @param keyCode The key code for which to cancel the long-press.
     */
    private fun cancelLongPress(keyCode: Int) {
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
        longPressFired.remove(keyCode)
    }

    private fun isConfirmKey(code: Int): Boolean =
        code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER

    private fun normalizeConfirm(code: Int): Int =
        if (isConfirmKey(code)) KeyEvent.KEYCODE_DPAD_CENTER else code

    /**
     * Marks the key code that "owns" the currently displayed QuickBar.
     * This is used to allow the same key that opened the QuickBar to also close it
     * when pressed again while the overlay is visible.
     *
     * @param keyCode The [KeyEvent] code of the key that triggered the QuickBar.
     */
    private fun markQuickBarOwner(keyCode: Int) {
        overlayOwnerKeyCode = keyCode
        ownerKeyArmedToClose = false
        resetKeyGestureState(keyCode)
    }

    /**
     * Toggles the failsafe mechanism for trigger key actions.
     *
     * This function inverts the `keyCaptureEnabled` state, effectively enabling or disabling
     * all trigger key actions. It also performs several cleanup actions:
     * - Saves the new state of `keyCaptureEnabled` to persistent storage.
     * - Cancels any pending single, double, or long press timers to prevent actions from
     *   firing after the toggle.
     * - Clears all internal state related to key press timing (e.g., `doublePressPendingKeyCode`,
     *   `keyDownStartTimes`).
     * - Clears any pending failsafe hold timers.
     * - Displays a toast message indicating the new state of trigger key actions and the reason
     *   for the toggle.
     *
     * @param reason A string explaining why the failsafe was toggled (e.g., "held KEY_X for 5s").
     *               This reason is included in the toast message.
     */
    private fun toggleFailsafe(reason: String) {
        failsafeKeyHoldEnabled = !failsafeKeyHoldEnabled
        saveTriggerKeysEnabled(failsafeKeyHoldEnabled)

        // Cancel any pending action timers so nothing fires post-toggle
        doublePressPendingKeyCode = -1
        doublePressRunnables.values.forEach { handler.removeCallbacks(it) }
        doublePressRunnables.clear()
        longPressRunnables.values.forEach { handler.removeCallbacks(it) }
        longPressRunnables.clear()
        longPressFired.clear()
        keyDownStartTimes.clear()

        // Clear any pending failsafe holds
        failsafeHoldRunnables.values.forEach { handler.removeCallbacks(it) }
        failsafeHoldRunnables.clear()

        if (overlayView != null){
            hideOverlay()
        }

        showToast(
            if (failsafeKeyHoldEnabled)
                "Trigger Key actions enabled ($reason)"
            else
                "Trigger Key actions disabled: ($reason). Hold 5 seconds to re-enable."
        )

        // reset any stale swallow guards, either for enabling the remaps (first one) or disabling (second one)
        swallowUntilKeyUp = -1
        swallowSystemUntilKeyUp = -1
    }

    /**
     * Schedules a delayed runnable to toggle the failsafe state if a trigger key
     * is held for `FAILSAFE_HOLD_MS`.
     *
     * This is used to allow users to disable/re-enable trigger key actions
     * by long-pressing a configured trigger key.
     *
     * If a failsafe hold is already scheduled for this `keyCode`, it is
     * cancelled before scheduling the new one.
     *
     * @param keyCode The key code of the key being held.
     */
    private fun scheduleFailsafeHold(keyCode: Int) {
        cancelFailsafeHold(keyCode)
        val r = Runnable {
            val wasEnabled = failsafeKeyHoldEnabled
            toggleFailsafe("held ${KeyEvent.keyCodeToString(keyCode)} for ${FAILSAFE_HOLD_MS/1000}s")

            if (!wasEnabled && failsafeKeyHoldEnabled) {
                // We just ENABLED remaps → don’t fire our single action on release
                swallowUntilKeyUp = keyCode
            } else if (wasEnabled && !failsafeKeyHoldEnabled) {
                // We just DISABLED remaps → don’t let the system act on this release
                swallowSystemUntilKeyUp = keyCode
            }
        }
        failsafeHoldRunnables[keyCode] = r
        handler.postDelayed(r, FAILSAFE_HOLD_MS)
    }

    /**
     * Cancels any pending failsafe hold runnable for the given [keyCode].
     *
     * This is called when a key is released or when the failsafe is triggered,
     * to ensure the toggle logic doesn't run unintentionally later.
     *
     * @param keyCode The key code for which to cancel the failsafe hold.
     */
    private fun cancelFailsafeHold(keyCode: Int) {
        failsafeHoldRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
    }

    private fun resetKeyGestureState(keyCode: Int) {
        // long
        cancelLongPress(keyCode)           // also clears longPressFired
        keyDownStartTimes.remove(keyCode)

        // double
        if (doublePressPendingKeyCode == keyCode) {
            doublePressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
            doublePressPendingKeyCode = -1
        }

        // failsafe hold for this key
        cancelFailsafeHold(keyCode)
    }

    /**
     * Run an action by ID, optionally targeting an entity, QuickBar, app launch, or camera PIP.
     *
     * @param actionId The ID of the action to run. This could be an entity ID, QuickBar ID,
     *                 package name for app launch (optionally prefixed with "launch_app:"),
     *                 or camera entity ID for PIP.
     * @param actionType An optional string specifying the type of action.
     *                   Possible values: "entity", "app_launch", "camera_pip", or null/other for QuickBar.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun runAction(actionId: String, actionType: String?) {
        if (actionId.startsWith("launch_app:") || actionType == "app_launch") {
            val targetPackage = if (actionId.startsWith("launch_app:")) {
                actionId.substringAfter("launch_app:")
            } else {
                actionId
            }
            launchPackage(targetPackage)
        } else if (actionType == "entity") {
            triggerEntity(actionId)
        }
        else if (actionType == "camera_pip") {
            handleCameraPip(actionId)
        } else {
            // QuickBar triggering logic remains unchanged...
            quickBarMap[actionId]?.let { bar ->
                if (bar.isEnabled) {
                    triggerQuickBar(bar)
                } else {
                    showToast("QuickBar '${bar.name}' is disabled")
                }
            }
        }
    }

    /**
     * Handles the display of a camera's Picture-in-Picture (PiP) overlay.
     *
     * This function attempts to find the specified camera entity by its ID.
     * If found, it will try to show the camera feed in a PiP window using [EntityStateUtils.showCameraPip].
     * - If no PiP stream is currently active, a toast message "CameraName -> PIP" is displayed.
     * - Errors during the process (e.g., entity not found, issues showing PiP) are logged.
     *
     * @param cameraEntityId The ID of the camera entity to display in PiP.
     * @return `true` if the PiP was successfully initiated or was already showing, `false` otherwise (e.g., entity not found, error).
     */
    private fun handleCameraPip(cameraEntityId: String): Boolean {
        return try {
            handleCameraRequest(CameraRequest(cameraEntity = cameraEntityId))
            true
        } catch (t: Throwable) {
            Log.e("camera_pip_service", "Error showing camera PIP: ${t.message}", t)
            false
        }
    }

    /**
     * Show a toast message on the main thread.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun showToast(msg: String) {
        mainExecutor.execute {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Trigger a Home Assistant service for an entity.
     * This function handles the logic for various entity domains like lights, switches,
     * locks, climate, and fans. It ensures the Home Assistant client is connected
     * before attempting to call a service and provides appropriate user feedback via toasts.
     * For climate and fan entities, it uses helper functions to toggle their state
     * while preserving their last known settings (e.g., temperature for climate, speed for fan).
     *
     * @param entityid The ID of the entity to trigger (e.g., "light.living_room_light").
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun triggerEntity(entityid: String) {
        // Launch a coroutine on an IO-optimized thread pool.
        serviceScope.launch(Dispatchers.IO) {
            val domain = entityid.substringBefore('.')

            val service = when (domain) {
                "light", "switch", "input_boolean", "script", "cover", "media_player" -> "toggle"
                "button", "input_button" -> "press"
                //"lock" -> determineLockService(entityid) ?: return@launch // Use the new function
                "lock" -> EntityStateUtils.determineLockService(entityid, ensureClientIsConnected()) ?: return@launch
                "climate", "fan" -> null
                else -> { return@launch }
            }

            if (DemoModeManager.isInDemoMode) {
                val newState = DemoModeManager.toggleEntityState(entityid)
                if (QuickBarsApp.showToastOnEntityTrigger) {
                    val displayName = buildEntityDisplayName(entityid)
                    showToast("$displayName → ${service ?: "toggle"} (Demo Mode)")
                }
                return@launch
            }

            val isConnected = ensureClientIsConnected()
            if (!isConnected && (domain == "climate" || domain == "fan")) {
                // Without a connection we at least try to use latest event cache for state,
                // but memory helpers need connection to apply changes, so bail if no socket.
                showToast("Not connected to Home Assistant")
                return@launch
            }

            // climate / fan path that mirrors the lock logic
            if (domain == "climate" || domain == "fan") {
                // For climate/fan we still NEED the entity for the memory behavior,
                // even if toasts are disabled.
                val savedEntitiesManager = SavedEntitiesManager(applicationContext)
                val entity = savedEntitiesManager.loadEntities().find { it.id == entityid }

                if (entity != null) {
                    // Hydrate state (and attrs, if store is ready) from cache/store
                    hydrateEntityFromCacheOrStore(entity, isConnected)

                    if (domain == "climate") {
                        EntityStateUtils.toggleClimateWithMemory(
                            entity,
                            haClient,
                            savedEntitiesManager
                        )
                    } else {
                        EntityStateUtils.toggleFanWithMemory(
                            entity,
                            haClient,
                            savedEntitiesManager
                        )
                    }

                    if (QuickBarsApp.showToastOnEntityTrigger) {
                        val displayName =
                            entity.customName.takeIf { it.isNotBlank() } ?: entityid
                        showToast("$displayName → toggle")
                    }

                    scheduleDisconnect()
                    return@launch
                }
            }

            // Generic entity call (light/switch/etc)
            haClient?.callService(domain, service!!, entityid)

            if (QuickBarsApp.showToastOnEntityTrigger) {
                val displayName = buildEntityDisplayName(entityid)
                showToast("$displayName → $service")
            }
            scheduleDisconnect() // 30-second disconnect logic is preserved.

            // If not connected, ensureClientIsConnected already shows an error toast.
        }
    }


    /**
     * Builds the display name for an entity, prioritizing the custom name.
     *
     * This function determines the most appropriate name to show in the UI for a given entity.
     * It follows a specific order of preference:
     * 1.  If the entity has a `customName` that is not blank, that name is used.
     * 2.  Otherwise, it falls back to the entity's `friendlyName`.
     * 3.  If both are unavailable (which should be rare), it defaults to the entity's `id`.
     *
     * @param entity The [EntityItem] for which to build the display name.
     * @return The calculated display name as a [String].
     */
    private fun buildEntityDisplayName(entityId: String): String {
        val savedEntitiesManager = SavedEntitiesManager(applicationContext)
        val entity = savedEntitiesManager.loadEntities().find { it.id == entityId }
        return entity?.customName?.takeIf { it.isNotBlank() } ?: entityId
    }

    /**
     * Hydrates an [EntityItem] with the most up-to-date state and attributes available.
     * It prioritizes sources of truth in the following order:
     * 1. **[EntityActionExecutor.QuickBarDataCache]**: This cache is event-driven and reflects the
     *    latest state received from Home Assistant events, even if the main WebSocket
     *    connection is not active. It only provides the state.
     * 2. **[HAStateStore]**: If `isClientConnected` is true, this function will attempt to
     *    fetch the entity's state and attributes from the `HAStateStore`. This store is
     *    populated after a successful connection and initial `get_states` call.
     *    It will wait for a short period (up to `maxWaitMs`) for the store to be populated
     *    if the entity is not immediately found.
     *
     * This function is crucial for ensuring that actions performed on entities (like toggling
     * a climate or fan with memory) use the freshest possible data, especially when the
     * main HA connection might be intermittent or delayed.
     *
     * @param entity The [EntityItem] to hydrate. Its `state` and `attributes` will be updated in place.
     * @param isClientConnected A boolean indicating whether the Home Assistant client is currently connected.
     *                          This affects whether the function will attempt to use the [HAStateStore].
     * @return `true` if the entity's `state` was successfully updated from either the cache or the store.
     *         `false` if no updated state could be found (e.g., entity not in cache, client not
     *         connected, or entity not yet in store within the timeout). Attributes are updated on a
     *         best-effort basis if fetched from the store.
     */
    private suspend fun hydrateEntityFromCacheOrStore(
        entity: EntityItem,
        isClientConnected: Boolean
    ): Boolean {
        // 1) Freshest: event-driven cache (no connection needed)
        EntityActionExecutor.QuickBarDataCache.getLatestEntityState(entity.id)?.let { s ->
            entity.state = s
            return true
        }

        // 2) If connected, wait briefly for initial get_states → HAStateStore
        if (!isClientConnected) return false

        val maxWaitMs = 1500
        val stepMs = 100L
        var waited = 0

        while (waited < maxWaitMs) {
            HAStateStore.entitiesById.value[entity.id]?.let { live ->
                entity.state = live.state
                entity.attributes = live.attributes
                return true
            }
            kotlinx.coroutines.delay(stepMs)
            waited += stepMs.toInt()
        }
        return false
    }

    /**
     * helper suspend function to connect the client if it's not already.
     * This encapsulates the connection logic in one place.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun ensureClientIsConnected(): Boolean {

        // If the background manager is running, reuse its client.
        if (BackgroundHaConnectionManager.isRunning()) {
            Log.d("QuickBarService", "Using background connection manager client")
            haClient = BackgroundHaConnectionManager.getClient()

            if (haClient != null) {
                // Check if the client is already connected - no need to reconnect
                if (haClient!!.isConnected()) {
                    Log.d("QuickBarService", "Client from background manager already connected")
                    connectionErrorMessage.value = null
                    return true
                } else {
                    Log.d("QuickBarService", "Client from background manager exists but not connected yet")
                    // Wait briefly for background client to connect if it's in the process
                    val connectedState = withTimeoutOrNull(2000) {
                        haClient!!.connectionState
                            .filterNot { it is ConnectionState.Connecting }
                            .first()
                    }

                    if (connectedState is ConnectionState.Connected) {
                        Log.d("QuickBarService", "Background client connected successfully")
                        connectionErrorMessage.value = null
                        return true
                    }
                }
            } else {
                Log.d("QuickBarService", "Background manager running but returned null client")
            }
        } else {
            Log.d("QuickBarService", "Background manager not running, will create on-demand connection")
        }

        if (haClient?.isConnected() == true) {
            return true // Already connected, nothing to do.
        }

        val url = SecurePrefsManager.getHAUrl(this)
        val token = SecurePrefsManager.getHAToken(this)

        if (url == null || token == null) {
            showToast("Home Assistant credentials missing")
            return false
        }

        haClient = HomeAssistantClient(url, token, this)
        haClient?.connect()

        val resultState = withTimeoutOrNull(5_000) {
            haClient?.connectionState
                ?.filterNot { it is ConnectionState.Connecting || it is ConnectionState.Disconnected }
                ?.first()
        }

        return when (resultState) {
            is ConnectionState.Connected -> {
                connectionErrorMessage.value = null
                true
            }
            is ConnectionState.Error -> {
                connectionErrorMessage.value = resultState.reason.name
                showToast("Connection error: ${resultState.reason}")
                false
            }
            else -> {
                connectionErrorMessage.value = "Timed out connecting to HA"
                showToast("Timed out connecting to HA")
                false
            }
        }
    }

    /**
     * Schedule a disconnect after a delay.
     * This background connection is triggered by the triggerEntity action, and it's disconnected
     * after a specific time to prevent excessive network usage.
     */
    private fun scheduleDisconnect() {
        // If we’re using the background socket, never tear it down from the overlay
        if (BackgroundHaConnectionManager.isRunning()) return

        // Cancel any previously scheduled disconnect
        disconnectJob?.cancel()

        // Schedule a new disconnect using a coroutine
        disconnectJob = serviceScope.launch {
            delay(disconnectDelayMs)

            // Disconnect and clear the client only if the overlay is not visible
            if (overlayView == null) {
                if (!BackgroundHaConnectionManager.isRunning()) {
                    haClient?.disconnect()
                    haClient = null
                }
            }
        }
    }


    /**
     * Trigger a QuickBar by its ID.
     * @param bar The QuickBar to trigger.
     */
    @MainThread
    private fun triggerQuickBar(bar: QuickBar) = runOnMain{
        if (!Settings.canDrawOverlays(this)) {
            // This permission check part is correct and unchanged.
            val intent = Intent("ACTION_OVERLAY_PERMISSION_REQUIRED")
            intent.putExtra("QUICKBAR_ID", bar.id)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            return@runOnMain
        }
        connectionErrorMessage.value = null

        val openView = overlayView
        if (openView != null) {
            // Toggle if same alias is open; otherwise switch (close then open)
            if (currentBarId == bar.id) {
                hideOverlay(immediate = true)
                return@runOnMain
            } else {
                hideOverlay(immediate = true) {
                    showOverlay(bar) { afterOverlayVisibleSetup(bar) }
                }
                return@runOnMain
            }
        }

        // Normal open
        showOverlay(bar) { afterOverlayVisibleSetup(bar) }
    }

    private fun afterOverlayVisibleSetup(bar: QuickBar) {
        Log.d("QuickBarService", "QuickBar overlay attached, setting up connection")

        // Cancel any previous connection state job
        connectionStateJob?.cancel()

        if (DemoModeManager.isInDemoMode) {
            connectionErrorMessage.value = null
            haClient = HomeAssistantClient("demo_url", "demo_token", this)
            haClient?.connect()
            return
        }

        if (BackgroundHaConnectionManager.isRunning()) {
            Log.d("QuickBarService", "Using existing background connection for QuickBar")
            haClient = BackgroundHaConnectionManager.getClient()

            connectionStateJob = serviceScope.launch {
                HAStateStore.connection.collect { state ->
                    connectionErrorMessage.value = when (state) {
                        is ConnectionState.Connected -> null
                        is ConnectionState.Error     -> getErrorMessage(state.reason)
                        else                         -> connectionErrorMessage.value
                    }
                }
            }
        } else {
            Log.d("QuickBarService", "Creating new on-demand connection for QuickBar")

            val url = SecurePrefsManager.getHAUrl(this)
            val token = SecurePrefsManager.getHAToken(this)

            if (url != null && token != null) {
                haClient?.disconnect()
                haClient = HomeAssistantClient(url, token, this)

                connectionStateJob = serviceScope.launch(Dispatchers.Main.immediate) {
                    haClient?.connectionState?.collect { state ->
                        connectionErrorMessage.value = when (state) {
                            is ConnectionState.Connected -> null
                            is ConnectionState.Error     -> getErrorMessage(state.reason)
                            else                         -> connectionErrorMessage.value
                        }
                    }
                }

                haClient?.connect()
            } else {
                connectionErrorMessage.value = "Home Assistant credentials missing"
                showToast("Home Assistant credentials missing")
            }
        }
    }

    /**
     * Get a user-friendly error message based on the connection error reason
     */
    private fun getErrorMessage(reason: ConnectionState.Reason): String {
        return when (reason) {
            ConnectionState.Reason.CANNOT_RESOLVE_HOST ->
                "Could not connect to Home Assistant. Check your network or server address."
            ConnectionState.Reason.AUTH_FAILED ->
                "Authentication failed. Please check your access token."
            ConnectionState.Reason.BAD_TOKEN ->
                "Invalid access token format. Generate a new Long-Lived Access Token."
            ConnectionState.Reason.SSL_HANDSHAKE ->
                "SSL certificate error. Try using HTTP instead of HTTPS."
            ConnectionState.Reason.TIMEOUT ->
                "Connection timed out. Check if Home Assistant is running."
            ConnectionState.Reason.NETWORK_IO ->
                "Network error. Check your internet connection."
            ConnectionState.Reason.UNKNOWN ->
                "Unknown connection error."
            ConnectionState.Reason.BAD_URL ->
                "Invalid URL format. Check your server address."
        }
    }

    /**
     * Shows a QuickBar overlay.
     * Uses window manager for the overlay service, and Jetpack Compose for the UI itself.
     * @param bar The QuickBar to show.
     */
    @MainThread
    private fun showOverlay(bar: QuickBar, onVisible: () -> Unit) {
        if (overlayView != null) hideOverlay(immediate = true)

        currentBarId = bar.id
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HAQuickBars)

        val composeView = ComposeView(themedContext).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
        }

        // Keep refs
        overlayView = composeView
        this.composeView = composeView

        // Prepare initial entities BEFORE content
        val savedEntitiesManager = SavedEntitiesManager(this)
        val entityMap = savedEntitiesManager.loadEntities().associateBy { it.id }
        entitiesState.apply {
            clear()
            addAll(
                bar.savedEntityIds.mapNotNull { id -> entityMap[id] }.map { s ->
                    EntityItem(
                        id = s.id,
                        friendlyName = s.friendlyName,
                        customName = s.customName,
                        state = "unknown",
                        isSaved = true,
                        customIconOnName = s.customIconOnName,
                        customIconOffName = s.customIconOffName,
                        pressActions = s.pressActions,
                        isActionable = EntityIconMapper.isEntityToggleable(s.id)
                    )
                }
            )
        }

        isHorizontal = bar.position == QuickBarPosition.TOP || bar.position == QuickBarPosition.BOTTOM
        val params = createWindowParams(bar)

        // Add the view first so a surface exists
        try {
            windowManager.addView(composeView, params)
        } catch (t: Throwable) {
            Log.e("QuickBarService", "addView failed", t)
            overlayView = null
            return
        }

        // Single listener that does all the "after-attach" work
        composeView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (v !== overlayView) { // stale attach from a view we're already discarding
                    v.removeOnAttachStateChangeListener(this)
                    return
                }

                // Lifecycle is safe to attach/resume now
                quickBarLifecycleOwner.attachToView(composeView)
                quickBarLifecycleOwner.resume()

                // Ensure initial state is false for enter animation
                isOverlayVisible.value = false

                // Now set content (surface + lifecycle are ready)
                composeView.setContent {
                    AnimatedQuickBar(
                        isVisible = isOverlayVisible.value,
                        bar = bar,
                        entities = entitiesState,
                        haClient = haClient,
                        connectionErrorMessage = connectionErrorMessage.value
                    )
                }

                // Start overlay-scoped collector AFTER attach
                entitiesJob?.cancel()
                entitiesJob = serviceScope.launch(Dispatchers.Main.immediate) {
                    HAStateStore.entitiesById.collect { map ->
                        for (i in 0 until entitiesState.size) {
                            val old = entitiesState[i]
                            val fresh = map[old.id] ?: continue
                            val mergedLastKnownState = when {
                                old.id.startsWith("climate.") -> {
                                    val m = old.lastKnownState.toMutableMap()
                                    fresh.lastKnownState[EntityStateKeys.LAST_AC_TEMP]?.let { m[EntityStateKeys.LAST_AC_TEMP] = it }
                                    fresh.lastKnownState[EntityStateKeys.LAST_AC_MODE]?.let { m[EntityStateKeys.LAST_AC_MODE] = it }
                                    m
                                }
                                old.id.startsWith("fan.") -> {
                                    val m = old.lastKnownState.toMutableMap()
                                    fresh.lastKnownState[EntityStateKeys.LAST_FAN_SPEED]?.let { m[EntityStateKeys.LAST_FAN_SPEED] = it }
                                    m
                                }
                                old.id.startsWith("light.") -> {
                                    val m = old.lastKnownState.toMutableMap()
                                    fresh.lastKnownState["is_simple_light"]?.let { m["is_simple_light"] = it }
                                    m
                                }
                                else -> old.lastKnownState
                            }
                            entitiesState[i] = fresh.copy(
                                customName        = old.customName,
                                customIconOnName  = old.customIconOnName,
                                customIconOffName = old.customIconOffName,
                                isSaved           = old.isSaved,
                                pressActions      = old.pressActions,
                                lastKnownState    = mergedLastKnownState
                            )
                        }
                    }
                }

                // Only now flip visible + run your callback
                Choreographer.getInstance().postFrameCallback {
                    isOverlayVisible.value = true
                    scheduleAutoHideTimeout(bar.autoCloseDelay)
                    onVisible()
                }

                v.removeOnAttachStateChangeListener(this)
            }

            override fun onViewDetachedFromWindow(v: View) { /* no-op */ }
        })
    }

    /**
     * Creates and configures the WindowManager.LayoutParams for the QuickBar overlay.
     *
     * This function determines the size, position (gravity), type, and flags
     * for the window that will host the QuickBar. The dimensions and gravity
     * are dynamically set based on the `QuickBar`'s configuration (e.g.,
     * horizontal/vertical orientation, position on screen, and layout type).
     *
     * @param bar The [QuickBar] object containing the configuration for the overlay.
     * @return A [WindowManager.LayoutParams] object ready to be used for adding
     *         the QuickBar overlay view to the WindowManager.
     */
    private fun createWindowParams(bar: QuickBar): WindowManager.LayoutParams {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val overlayWidth = when {
            isHorizontal -> WindowManager.LayoutParams.MATCH_PARENT
            bar.useGridLayout -> (screenWidth * 0.40f).toInt()
            else -> (screenWidth * 0.30f).toInt()
        }
        val overlayHeight = if (isHorizontal) (screenHeight * 0.40f).toInt() else WindowManager.LayoutParams.MATCH_PARENT

        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val gravity = when (bar.position) {
            QuickBarPosition.RIGHT -> Gravity.END or Gravity.TOP
            QuickBarPosition.LEFT -> Gravity.START or Gravity.TOP
            QuickBarPosition.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            QuickBarPosition.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        return WindowManager.LayoutParams(
            overlayWidth,
            overlayHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            windowAnimations = 0
        }
    }

    /**
     * Hides the QuickBar overlay.
     * Also disconnects and turns of all relevant services that were initialized for the service.
     * If the overlay is already hidden, this method does nothing.
     */
    @MainThread
    private fun hideOverlay(
        immediate: Boolean = false,
        onHidden: (() -> Unit)? = null
    ) {
        // Always clear any pending auto-hide when we’re closing the QuickBar
        cancelAutoHideTimeout()

        runOnMain {
            val v = overlayView ?: run { onHidden?.invoke(); return@runOnMain }

            // Drive exit animation
            isOverlayVisible.value = false

            val wasUsingBg = BackgroundHaConnectionManager.isRunning()

            // Stop work & blank compose before removing the surface
            try {
                entitiesJob?.cancel(); entitiesJob = null
                connectionStateJob?.cancel(); connectionStateJob = null
                quickBarLifecycleOwner.pause()
                if (immediate) {
                    (v as? ComposeView)?.setContent { } // only in immediate path
                }
            } catch (_: Throwable) {
            }

            val removeBlock = {
                try {
                    if (v.isAttachedToWindow) windowManager.removeViewImmediate(v)
                } catch (t: Throwable) {
                    try {
                        windowManager.removeView(v)
                    } catch (_: Throwable) {
                    }
                } finally {
                    overlayView = null
                    composeView = null
                    currentBarId = null

                    if (!wasUsingBg) {
                        try {
                            haClient?.disconnect()
                        } catch (_: Throwable) {
                        }
                        haClient = null
                    }
                    onHidden?.invoke()
                }
            }

            if (immediate) {
                // Remove on the next frame to avoid mid-traversal races
                v.post { removeBlock() }
            } else {
                // Match AnimatedVisibility exit duration (350ms) + a tiny cushion
                v.postDelayed({ removeBlock() }, 370L)
            }
        }
    }

    /**
     * Handles the triggering of a QuickBar based on its configured alias, from an Home Assistant custom event (quickbars.open)
     *
     * This method is called when an event (e.g., from a broadcast receiver or a Flow)
     * indicates that a QuickBar should be triggered using its alias.
     *
     * It performs the following steps:
     * 1. Checks if the provided alias is blank. If so, logs an error and returns.
     * 2. Searches for a QuickBar in the `quickBarMap` that has a `haTriggerAlias` matching the provided alias.
     * 3. If a matching QuickBar is found:
     *    a. Checks if the QuickBar is enabled.
     *    b. If enabled, checks if the Android version is sufficient (Android M or higher) to trigger the QuickBar.
     *       - If the version is sufficient, calls `triggerQuickBar()` to display the QuickBar.
     *       - If the version is too old, logs an error.
     *    c. If the QuickBar is disabled, logs this information.
     * 4. If no matching QuickBar is found, logs an error.
     * 5. Catches any exceptions that occur during the process and logs an error.
     *
     * @param alias The alias string used to identify the QuickBar to be triggered.
     */
    override fun onQuickBarAliasTriggered(alias: String) {
        try {
            if (alias.isBlank()) {
                Log.e("QuickBarService", "Alias is blank, cannot trigger QuickBar")
                return
            }

            val matchingBar = quickBarMap.values.firstOrNull { it.haTriggerAlias == alias }

            if (matchingBar != null) {
                if (matchingBar.isEnabled) {
                    triggerQuickBar(matchingBar)
                } else {
                    Log.d("QuickBarService", "QuickBar is disabled: ${matchingBar.name}")
                }
            } else {
                Log.e("QuickBarService", "No QuickBar found with alias: '$alias'")
            }
        } catch (e: Exception) {
            Log.e("QuickBarService", "Error triggering QuickBar with alias: $alias", e)
        }
    }

    @Deprecated("Use onCameraRequest(CameraRequest) instead.")
    override fun onCameraAliasTrigger(cameraAlias: String) {
        try {
            if (cameraAlias.isBlank()) {
                Log.e("QuickBarService", "Camera alias is blank, cannot trigger Camera PIP")
                return
            }
            handleCameraRequest(CameraRequest(cameraAlias = cameraAlias))
        } catch (e: Exception) {
            Log.e("QuickBarService", "Error triggering Camera with alias: $cameraAlias", e)
        }
    }


    /**
     * Shows a camera Picture-in-Picture (PiP) overlay on the screen.
     *
     * This function manages the display of a camera feed in a small overlay window.
     * Key behaviors:
     * - **Toggle:** If the specified camera is already showing, calling this function will hide it.
     * - **Replace:** If a different camera is currently showing, it will be replaced by the new one.
     * - **New Overlay:** If no camera PiP is active, a new overlay is created and displayed.
     * - **Auto-hide:** The PiP can be configured to automatically hide after a specified timeout.
     * - **Positioning:** The PiP can be positioned in one of the four corners of the screen with padding.
     * - **Non-Focusable:** The overlay is designed to be non-focusable and non-touch-modal,
     *   allowing users to continue interacting with the underlying application.
     *
     * The camera feed is rendered using the [CameraPipOverlay] composable.
     *
     * @param spec The [CameraPipSpec] containing the configuration for the camera PiP,
     *             including the entity ID, stream URL, corner position, and auto-hide timeout.
     */
    @MainThread
    fun showCameraPip(spec: CameraPipSpec) {
        camera.show(spec)
    }

    @MainThread
    private fun hideCameraPip() {
        camera.hide()
    }


    fun handleCameraRequest(req: CameraRequest) {
        camera.handleRequest(req)
    }

    /**
     * Called when Home Assistant fetches categories and entities.
     * Assists the compose overlay update the entities' states.
     * @param categories The list of categories.
     */
    override fun onEntitiesFetched(categories: List<CategoryItem>) {
        val entityMap = categories.flatMap { it.entities }.associateBy { it.id }

        val updatedEntities = entitiesState.map { existingEntity ->
            // If a new version exists from HA, merge its properties
            entityMap[existingEntity.id]?.let { newEntity ->
                existingEntity.copy(
                    friendlyName = newEntity.friendlyName,
                    state = newEntity.state,
                    attributes = newEntity.attributes,
                    isActionable = newEntity.isActionable,
                    // PRESERVE THESE CUSTOM PROPERTIES:
                    customName = existingEntity.customName,
                    pressActions      = existingEntity.pressActions,
                    customIconOnName = existingEntity.customIconOnName,
                    customIconOffName = existingEntity.customIconOffName,
                    lastKnownState = existingEntity.lastKnownState
                )
            } ?: existingEntity
        }
        entitiesState.clear()
        entitiesState.addAll(updatedEntities)
    }

    /**
     * Called when Home Assistant updates a specific entity's state.
     * @param entityId The ID of the entity.
     * @param newState The new state of the entity.
     * @param attributes Additional attributes of the entity.
     */
    override fun onEntityStateUpdated(entityId: String, newState: String, attributes: JSONObject) {
        val currentTime = System.currentTimeMillis()
        val lastUpdate = lastEntityUpdateTime[entityId] ?: 0L

        // Only update every 250ms for the same entity
        if (currentTime - lastUpdate < 250) {
            return
        }
        lastEntityUpdateTime[entityId] = currentTime

        val index = entitiesState.indexOfFirst { it.id == entityId }
        if (index != -1 && (entitiesState[index].state != newState ||
                    entitiesState[index].attributes != attributes)) {
            // Only update if state actually changed
            entitiesState[index] = entitiesState[index].copy(state = newState, attributes = attributes)
        }
    }

    /**
     * Called when Home Assistant updates a specific entity's state.
     * @param entityId The ID of the entity.
     * @param newState The new state of the entity.
     */
    override fun onEntityStateChanged(entityId: String, newState: String) {
        // Call the newer method with empty attributes
        onEntityStateUpdated(entityId, newState, JSONObject())
    }

    /**
     * Called when the service is being destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        serviceScope.cancel()
        if (!BackgroundHaConnectionManager.isRunning()) {
            haClient?.disconnect()
        }

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(entityActionReceiver)
        } catch (e: Exception) {
            Log.e("QuickBarService", "Error unregistering entity action receiver", e)
        }

        if (this::camera.isInitialized) {
            try { camera.destroy() } catch (_: Throwable) {}
        }

        if (::notifications.isInitialized)
            try { notifications.onDestroy() } catch (_: Throwable) {}

        if (quickBarLifecycleCreated) {
            quickBarLifecycleOwner.destroy()
            quickBarLifecycleCreated = false
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        Log.w("QuickBarService", "Service unbound (onUnbind)")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
