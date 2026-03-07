package dev.trooped.tvquickbars.notification

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.compose.ui.platform.ComposeView
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.background.BackgroundHaConnectionManager
import dev.trooped.tvquickbars.data.AppIdProvider
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.services.ComposeViewLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import kotlin.math.ln
import kotlin.math.min
import androidx.core.net.toUri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class NotificationController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val runOnMain: (block: () -> Unit) -> Unit,
    private val serviceScope: CoroutineScope,
) {

    // ====== state moved from QuickBarService ======
    private var notificationOverlay: ComposeView? = null
    private var notificationHideHandler: Handler? = null

    private var notificationPlayer: MediaPlayer? = null
    private var notificationLoudness: android.media.audiofx.LoudnessEnhancer? = null
    @Volatile private var notificationSoundToken: Int = 0

    private val lifecycleOwner = ComposeViewLifecycleOwner()
    private var lifecycleCreated = false

    private val notificationQueue: ArrayDeque<NotificationSpec> = ArrayDeque()
    private var isNotificationShowing: Boolean = false

    // ====== lifecycle hooks ======
    fun onServiceConnected() {
        if (!lifecycleCreated) {
            lifecycleOwner.create()
            lifecycleCreated = true
        }
    }

    fun onDestroy() {
        // stop timers
        notificationHideHandler?.removeCallbacksAndMessages(null)
        notificationHideHandler = null

        // stop audio
        notificationPlayer?.let { try { it.stop() } catch (_: Throwable) {}; try { it.release() } catch (_: Throwable) {} }
        notificationPlayer = null
        notificationLoudness?.let { try { it.release() } catch (_: Throwable) {} }
        notificationLoudness = null

        // remove overlay
        val v = notificationOverlay
        notificationOverlay = null
        if (v != null) {
            try { lifecycleOwner.pause() } catch (_: Throwable) {}
            try { v.setContent { } } catch (_: Throwable) {}
            try { windowManager.removeViewImmediate(v) } catch (_: Throwable) {}
        }

        try { lifecycleOwner.destroy() } catch (_: Throwable) {}
    }

    /** Percent-encode and canonicalize an absolute URL for MediaPlayer/HTTP stack. */
    private fun canonicalizeUrl(abs: String): String {
        return abs.toHttpUrlOrNull()?.toString() ?: abs.replace(" ", "%20")
    }

    // ====== public entry point (exact queue/interrupt semantics preserved) ======
    @MainThread
    fun enqueue(spec: NotificationSpec) {
        runOnMain {
            if (spec.interrupt == true) {
                // Interrupt: clear queue, show this one next, preempt current if showing
                notificationQueue.clear()
                notificationQueue.addFirst(spec)
                if (isNotificationShowing) {
                    hideNotification(immediate = true)
                } else {
                    showNextNotification()
                }
                return@runOnMain
            }

            // Normal queue behavior
            notificationQueue.addLast(spec)
            if (!isNotificationShowing) showNextNotification()
        }
    }

    // ====== identical rendering / behavior ======
    @MainThread
    private fun showNextNotification() {
        val next = notificationQueue.removeFirstOrNull()
        if (next == null) {
            isNotificationShowing = false
            return
        }
        isNotificationShowing = true
        show(next) // same renderer as before
    }

    @MainThread
    private fun onNotificationHidden() {
        isNotificationShowing = false
        if (notificationQueue.isNotEmpty()) showNextNotification()
    }

    @MainThread
    fun hide(immediate: Boolean = false) {
        hideNotification(immediate)
    }

    @MainThread
    private fun show(spec: NotificationSpec) {
        if (!android.provider.Settings.canDrawOverlays(context)) return

        val themed = ContextThemeWrapper(context, R.style.Theme_HAQuickBars)
        val view = ComposeView(themed).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
        }
        notificationOverlay = view

        val lp = createWindowParams(spec)

        try {
            windowManager.addView(view, lp)
        } catch (t: Throwable) {
            Log.e("NotificationController", "addView(notification) failed", t)
            notificationOverlay = null
            return
        }

        // bind lifecycle after attach, then setContent (exact sequence)
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (v !== notificationOverlay) { v.removeOnAttachStateChangeListener(this); return }

                lifecycleOwner.attachToView(view)
                lifecycleOwner.resume()

                view.setContent {
                    NotificationOverlay(
                        title            = spec.title ?: "",
                        message          = spec.message,
                        imageUrl         = spec.imageUrl,
                        iconSvgDataUri   = spec.iconSvgDataUri,
                        actions          = spec.actions,
                        onActionClick    = { action ->
                            sendQuickbarsActionToHA(spec.cid, action.id, action.label)
                            hideNotification(immediate = true)
                        },
                        bgColorHex       = spec.colorHex,
                        transparency     = spec.transparency,
                        onDismissRequest = { hideNotification() },
                        maxWidthDp       = maxOverlayWidthDp()
                    )
                }

                v.removeOnAttachStateChangeListener(this)
            }
            override fun onViewDetachedFromWindow(v: View) { /* no-op */ }
        })

        // auto-hide (same default/range)
        notificationHideHandler?.removeCallbacksAndMessages(null)
        val timeoutSec = (spec.durationSec ?: 6).coerceIn(1, 120)
        val timeoutMs = timeoutSec * 1000L
        notificationHideHandler = Handler(Looper.getMainLooper()).also { h ->
            h.postDelayed({ hideNotification() }, timeoutMs)
        }

        // optional sound (fire-and-forget)
        playNotificationSound(spec.soundUrl, spec.soundVolumePercent)
    }

    @MainThread
    private fun hideNotification(immediate: Boolean = false) {
        notificationHideHandler?.removeCallbacksAndMessages(null)
        notificationHideHandler = null

        // stop audio
        notificationPlayer?.let { try { it.stop() } catch (_: Throwable) {}; try { it.release() } catch (_: Throwable) {} }
        notificationPlayer = null

        val v = notificationOverlay ?: run {
            onNotificationHidden()
            return
        }
        notificationOverlay = null

        try { lifecycleOwner.pause() } catch (_: Throwable) {}
        try { v.setContent { } } catch (_: Throwable) {}

        try {
            if (immediate) windowManager.removeViewImmediate(v) else windowManager.removeView(v)
        } catch (_: Throwable) {
            try { windowManager.removeViewImmediate(v) } catch (_: Throwable) {}
        } finally {
            try { v.setContent { } } catch (_: Throwable) {}
            onNotificationHidden()
        }
    }

    private fun maxOverlayWidthDp(): Int {
        val dm = context.resources.displayMetrics
        val screenDp = dm.widthPixels / dm.density
        return min((screenDp * 0.60f).toInt(), 640)  // cap at 640dp or 60% of screen
    }


    private fun createWindowParams(spec: NotificationSpec): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val hasActions = spec.actions.isNotEmpty()

        val baseFlags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        val finalFlags = if (hasActions) baseFlags
        else baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        val gravity = when (spec.position ?: "top_right") {
            "top_left"     -> Gravity.TOP or Gravity.START
            "top_right"    -> Gravity.TOP or Gravity.END
            "bottom_left"  -> Gravity.BOTTOM or Gravity.START
            "bottom_right" -> Gravity.BOTTOM or Gravity.END
            else           -> Gravity.TOP or Gravity.END
        }

        return WindowManager.LayoutParams(
            /* w = */ WindowManager.LayoutParams.WRAP_CONTENT,
            /* h = */ WindowManager.LayoutParams.WRAP_CONTENT,
            /* _type = */ type,
            /* _flags = */ finalFlags,
            /* _format = */ PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            val m = (12 * context.resources.displayMetrics.density).toInt()
            x = m; y = m
            windowAnimations = 0
        }
    }

    private fun dbFromLinearGain(gain: Float): Float {
        // 20 * log10(g)  ==  8.6858896 * ln(g)
        return 8.685889f * ln(gain.coerceAtLeast(1e-4f))
    }

    private fun defaultPortForScheme(scheme: String) =
        if (scheme.equals("https", true)) 443 else 80

    private fun normalizedHaBase(ctx: android.content.Context): HttpUrl? {
        var raw = SecurePrefsManager.getHAUrl(ctx)?.trim().orEmpty()
        if (raw.isEmpty()) return null

        // Add scheme if user saved bare host (e.g., "192.168.68.121" or "192.168.68.121:8123")
        if (!raw.startsWith("http://", true) && !raw.startsWith("https://", true)) {
            raw = "http://$raw"
        }

        val parsed = raw.toHttpUrlOrNull() ?: return null

        // Detect an explicit port after the scheme (IPv6-safe check)
        val afterScheme = raw.substringAfter("://")
        val hasExplicitPort = afterScheme.contains(':') && !afterScheme.startsWith("[")

        // If no port and scheme is http, default to HA's common 8123
        return if (!hasExplicitPort && parsed.scheme.equals("http", true)) {
            parsed.newBuilder().port(8123).build()
        } else {
            parsed
        }
    }

    /** Convert possibly-relative HA paths to absolute URLs using saved HA base. */
    /** Convert possibly-relative HA paths to absolute URLs using saved HA base (scheme + port guaranteed). */
    private fun resolveAgainstHaBase(raw: String?): Pair<String, Boolean> {
        if (raw.isNullOrBlank()) return "" to false
        val s = raw.trim()

        val ha = normalizedHaBase(context)

        // Already absolute?
        if (s.startsWith("http://", true) || s.startsWith("https://", true)) {
            val u = s.toHttpUrlOrNull()
            val isHa = ha != null && u != null &&
                    u.scheme.equals(ha.scheme, true) &&
                    u.host.equals(ha.host, true) &&
                    ((if (u.port != -1) u.port else defaultPortForScheme(u.scheme)) ==
                            (if (ha.port != -1) ha.port else defaultPortForScheme(ha.scheme)))
            return s to isHa
        }

        // Relative -> require HA base
        if (ha == null) return s to false

        val relPath = if (s.startsWith("/")) s else "/$s"
        // Build canonical absolute URL (properly encodes spaces/unicode)
        val abs = ha.newBuilder()
            .encodedPath(relPath)
            .build()
            .toString()

        return abs to true
    }

    /** Rewrite stream → still for camera/image proxies (to mirror old Python behavior). */
    private fun rewriteStreamToStill(u: String): String {
        return u
            .replace("/api/camera_proxy_stream/", "/api/camera_proxy/")
            .replace("/api/image_proxy_stream/", "/api/image_proxy/")
    }


    /** 0..300%, >100 uses LoudnessEnhancer when available. */
    private fun playNotificationSound(url: String?, volumePercent: Int?) {
        if (url.isNullOrBlank()) return
        val myToken = (++notificationSoundToken)

        // tear down any previous
        notificationPlayer?.let { try { it.stop() } catch (_: Throwable) {}; try { it.release() } catch (_: Throwable) {} }
        notificationPlayer = null
        notificationLoudness?.let { try { it.release() } catch (_: Throwable) {} }
        notificationLoudness = null

        val pct = (volumePercent ?: 100).coerceIn(0, 300)
        val gain = pct / 100f

        try {
            // Resolve URL + auth
            val (abs0, isHaUrl0) = resolveAgainstHaBase(url)
            val abs1 = rewriteStreamToStill(abs0)
            val abs  = canonicalizeUrl(abs1)

            val token = if (isHaUrl0) SecurePrefsManager.getHAToken(context) else null
            val uri = abs.toUri()

            val headers: MutableMap<String, String>? = if (!token.isNullOrBlank() && isHaUrl0) {
                mutableMapOf("Authorization" to "Bearer $token")
            } else null

            val player = MediaPlayer().apply {
                val usage = if (gain > 1f) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri, headers ?: emptyMap())

                // 0..100% via setVolume; >100% via LoudnessEnhancer
                val vol = gain.coerceAtMost(1f)
                setVolume(vol, vol)

                setOnPreparedListener { mp ->
                    if (myToken != notificationSoundToken) { try { mp.release() } catch (_: Throwable) {}; return@setOnPreparedListener }

                    if (gain > 1f) {
                        try {
                            val dB   = dbFromLinearGain(gain)
                            val mB   = (dB * 100f).toInt()
                            val safe = mB.coerceIn(0, 1200) // ≈ +12 dB cap
                            val sessionId = mp.audioSessionId

                            notificationLoudness?.let { try { it.release() } catch (_: Throwable) {} }
                            notificationLoudness = android.media.audiofx.LoudnessEnhancer(sessionId).apply {
                                setTargetGain(safe)
                                enabled = true
                            }
                        } catch (e: Throwable) {
                            Log.w("NotificationController", "LoudnessEnhancer unavailable: ${e.message}")
                        }
                    }

                    try { mp.start() } catch (t: Throwable) {
                        try { mp.release() } catch (_: Throwable) {}
                        if (notificationPlayer === mp) notificationPlayer = null
                        notificationLoudness?.let { try { it.release() } catch (_: Throwable) {} }
                        notificationLoudness = null
                    }
                }

                setOnCompletionListener { mp ->
                    if (myToken != notificationSoundToken) { try { mp.release() } catch (_: Throwable) {}; return@setOnCompletionListener }
                    try { mp.release() } catch (_: Throwable) {}
                    if (notificationPlayer === mp) notificationPlayer = null
                    notificationLoudness?.let { try { it.release() } catch (_: Throwable) {} }
                    notificationLoudness = null
                }

                setOnErrorListener { mp, what, extra ->
                    if (myToken == notificationSoundToken) {
                        Log.e("NotificationController", "Media error what=$what extra=$extra url=$url")
                        try { mp.release() } catch (_: Throwable) {}
                        if (notificationPlayer === mp) notificationPlayer = null
                        notificationLoudness?.let { try { it.release() } catch (_: Throwable) {} }
                        notificationLoudness = null
                    } else {
                        try { mp.release() } catch (_: Throwable) {}
                    }
                    true
                }

                prepareAsync()
            }

            notificationPlayer = player
        } catch (t: Throwable) {
            Log.e("NotificationController", "playNotificationSound failed: ${t.message}", t)
            notificationLoudness?.let { try { it.release() } catch (_: Throwable) {} }
            notificationLoudness = null
        }
    }

    // ====== HA action event (unchanged semantics) ======
    private fun sendQuickbarsActionToHA(cid: String?, actionId: String, label: String?) {
        val ha = BackgroundHaConnectionManager.getClient() ?: return

        val data = JSONObject().apply {
            put("cid", cid)
            put("action_id", actionId)
            if (!label.isNullOrEmpty()) put("label", label)

            // Optional scoping: include integration/device id if you have it
            val myId = AppIdProvider.get(context)
            if (myId.isNotBlank()) put("id", myId)
        }

        serviceScope.launch(Dispatchers.IO) {
            ha.fireEvent("quickbars.action", data)
        }
    }
}
