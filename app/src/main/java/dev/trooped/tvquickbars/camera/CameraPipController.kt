package dev.trooped.tvquickbars.camera

import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import dev.trooped.tvquickbars.QuickBarsApp
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.services.ComposeViewLifecycleOwner
import kotlin.math.roundToInt

/**
 * Encapsulates all Camera PiP responsibilities:
 *  - Resolve camera target (alias/entity)
 *  - Build CameraPipSpec (URL, size, corner, title, auto-hide)
 *  - Show/Hide the overlay (ComposeView + WindowManager)
 *  - Auto-hide timer + dedicated Compose lifecycle
 *
 * Host requirements:
 *  - Supplies Context + WindowManager
 *  - Provides a runOnMain { } helper and showToast(msg) function
 */
class CameraPipController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val runOnMain: (block: () -> Unit) -> Unit,
    private val showToast: (String) -> Unit
) {

    // Compose lifecycle isolated to PiP surface
    private val pipLifecycleOwner = ComposeViewLifecycleOwner()
    private var pipLifecycleCreated = false

    // Overlay surface + auto-hide
    private var cameraPipOverlay: ComposeView? = null
    private var cameraHideHandler: Handler? = null
    private val cameraHideRunnable: Runnable = Runnable { hide() }

    private var showToastOnToggle: Boolean = true

    /** Main entry point used by the service. */
    fun handleRequest(req: CameraRequest): Unit = runOnMain {
        try {
            val TriggerKeyToggle: Boolean = try {
                QuickBarsApp.showToastOnEntityTrigger
            } catch (e: Exception) {
                true
            }

            showToastOnToggle = when (req.showToggleToast) {
                true -> true                     // explicit request from HA: show
                false -> TriggerKeyToggle        // explicit request: defer to global
                null -> TriggerKeyToggle         // no preference: defer to global
            }

            // ── ad-hoc RTSP path (no entity required) ───────────────────────────
            val safeRtsp = asValidRtspOrNull(req.rtspUrl)
            if (safeRtsp != null) {
                try {
                    val spec = buildAdhocRtspSpec(req.copy(rtspUrl = safeRtsp))
                    if (cameraPipOverlay == null && showToastOnToggle) showToast("${spec.title ?: "Camera"} → PIP")
                    show(spec)
                } catch (_: Throwable) {
                    showToast("Failed to open RTSP stream")
                }
                return@runOnMain
            }

            // ── Resolve a saved camera entity (an MJPEG stream) ─────────────────────────
            val entity = resolveCameraEntity(
                cameraEntityId = req.cameraEntity?.takeIf { it.isNotBlank() },
                aliasOrName    = req.cameraAlias?.takeIf { it.isNotBlank() }
            ) ?: return@runOnMain

            val spec = buildCameraSpecWithOverrides(entity, req)

            val playable = spec.url.startsWith("http://", ignoreCase = true)
                    || spec.url.startsWith("https://", ignoreCase = true)
                    || spec.url.startsWith("rtsp://", ignoreCase = true)

            if (!playable) {
                showToast("Stream not found")
                return@runOnMain
            }

            if (cameraPipOverlay == null && showToastOnToggle) {
                showToast("${spec.title} → PIP")
            }
            show(spec)
        } catch (t: Throwable) {
            Log.e("CameraPipController", "handleRequest failed: ${t.message}", t)
            showToast("Failed to open camera")
        }
    }

    private fun String?.isNullish(): Boolean =
        this == null || this.isBlank() || this.trim().equals("null", ignoreCase = true)

    private fun asValidRtspOrNull(raw: String?): String? {
        val url = raw?.trim() ?: return null
        if (!url.startsWith("rtsp://", ignoreCase = true)) return null
        val u = Uri.parse(url)
        return if (!u.host.isNullOrBlank()) url else null
    }

    private fun buildAdhocRtspSpec(req: CameraRequest): CameraPipSpec {
        val raw = requireNotNull(req.rtspUrl) { "rtspUrl is required" }.trim()
        val sanitized = sanitizeRtspUserInfo(raw) // encodes '@', ':', '/', '?', '#', spaces in userinfo

        val entityId = buildRtspEntityId(req.copy(rtspUrl = sanitized))

        val defaultCorner = "TOP_LEFT"
        val defaultSize   = "MEDIUM"
        val corner        = mapCornerOverride(req.position) ?: defaultCorner
        val (wDp, hDp)    = chooseSize(req, defaultSize)
        val autoHide      = req.autoHideSec ?: 30
        val showTitle     = req.showTitle ?: true
        val title = req.customTitle?.takeIf { !it.isNullish() }?.trim() ?: "Camera"

        val transport = when (req.rtspTransport?.lowercase()) {
            "udp" -> TransportProtocol.UDP
            "tcp" -> TransportProtocol.TCP
            "auto" -> TransportProtocol.AUTO
            else -> TransportProtocol.TCP // TCP is king for HA
        }

        val latency = when (req.rtspLatency?.lowercase()) {
            "low" -> StreamLatency.LOW_LATENCY
            "high" -> StreamLatency.HIGH_STABILITY
            "balanced" -> StreamLatency.BALANCED
            else -> StreamLatency.BALANCED
        }

        val softwareDecode = req.useSoftwareDecoder ?: false

        val profile = RtspProfile(
            transport = transport,
            latency = latency,
            useSoftwareDecoder = softwareDecode,
            muteAudio = req.muteAudio ?: false
        )

        return CameraPipSpec(
            url             = sanitized,
            authToken       = null,     // RTSP does not use HTTP Authorization
            entityId        = entityId, // stable, credentials-free
            title           = title,
            widthDp         = wDp,
            heightDp        = hDp,
            marginDp        = 20,
            showTitle       = showTitle,
            cornerPosition  = corner,
            autoHideTimeout = autoHide,
            rtspProfile = profile
        )
    }

    /** Percent-encode '@', ':', '/', '?', '#', and spaces inside username/password. Safe no-op if not needed. */
    private fun sanitizeRtspUserInfo(url: String): String {
        if (!url.startsWith("rtsp://", ignoreCase = true)) return url

        val withoutScheme = url.substring(7) // "rtsp://"
        val at = withoutScheme.lastIndexOf('@')
        if (at <= 0) return url // no userinfo present

        val userInfo = withoutScheme.substring(0, at)
        val rest     = withoutScheme.substring(at + 1)

        // Split user:pass (only on the first ':', password may contain ':')
        val colonIdx = userInfo.indexOf(':')
        val userRaw  = if (colonIdx >= 0) userInfo.substring(0, colonIdx) else userInfo
        val passRaw  = if (colonIdx >= 0) userInfo.substring(colonIdx + 1) else null

        val userEnc = encodeUserInfoComponent(userRaw)
        val passEnc = passRaw?.let { encodeUserInfoComponent(it) }

        val rebuiltUserInfo = if (passEnc != null) "$userEnc:$passEnc" else userEnc
        return "rtsp://$rebuiltUserInfo@$rest"
    }

    /** RFC 3986 userinfo-safe percent-encoding. Keep unreserved [-._~A-Za-z0-9]; encode others. */
    private fun encodeUserInfoComponent(s: String): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val sb = StringBuilder(s.length * 3)
        for (ch in s) {
            if (unreserved.indexOf(ch) >= 0) {
                sb.append(ch)
            } else {
                // encode '@', ':', '/', '?', '#', ' ' and any other non-unreserved
                val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                for (b in bytes) {
                    sb.append('%')
                    val v = b.toInt() and 0xFF
                    sb.append("0123456789ABCDEF"[v ushr 4])
                    sb.append("0123456789ABCDEF"[v and 0x0F])
                }
            }
        }
        return sb.toString()
    }

    /** Stable ID without leaking creds: alias when present, else host:port/path. */
    private fun buildRtspEntityId(req: CameraRequest): String {
        req.cameraAlias?.takeIf { it.isNotBlank() }?.let { return "rtsp:alias:$it" }
        val u = Uri.parse(requireNotNull(req.rtspUrl))
        val host = u.host ?: "unknown"
        val port = if (u.port != -1) u.port else 554
        val path = (u.encodedPath ?: "").ifEmpty { "/" }
        return "rtsp:url:$host:$port$path"
    }

    @MainThread
    fun show(spec: CameraPipSpec): Unit = runOnMain {
        if (!Settings.canDrawOverlays(context)) return@runOnMain

        if (!pipLifecycleCreated) {
            pipLifecycleOwner.create()
            pipLifecycleCreated = true
        }

        // Cancel pending auto-hide
        cameraHideHandler?.removeCallbacks(cameraHideRunnable)

        val existing: View? = cameraPipOverlay
        if (existing != null) {
            val currentSpec = existing.tag as? CameraPipSpec
            if (currentSpec?.entityId == spec.entityId) {
                hide() // toggle off
            } else {
                hide()
                existing.post { show(spec) }
            }
            return@runOnMain
        }

        // Fresh view
        val themed = ContextThemeWrapper(context, R.style.Theme_HAQuickBars)
        val view = ComposeView(themed).apply {
            tag = spec
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    pipLifecycleOwner.attachToView(this@apply)
                    // Set content while paused → avoid first-frame detach/draw race
                    setContent {
                        MaterialTheme {
                            val align = when (spec.cornerPosition) {
                                "TOP_RIGHT" -> Alignment.TopEnd
                                "BOTTOM_LEFT" -> Alignment.BottomStart
                                "BOTTOM_RIGHT" -> Alignment.BottomEnd
                                else -> Alignment.TopStart
                            }
                            Box(Modifier.wrapContentSize(), contentAlignment = align) {
                                Box(Modifier.padding(12.dp)) {
                                    CameraPipOverlay(spec = spec, onClose = { hide() })
                                }
                            }
                        }
                    }
                    Choreographer.getInstance().postFrameCallback { pipLifecycleOwner.resume() }
                    v.removeOnAttachStateChangeListener(this)
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
        cameraPipOverlay = view

        // Compute layout params
        val density = context.resources.displayMetrics.density
        val padPx   = (12f * 2f * density).roundToInt()
        val wPx     = (spec.widthDp  * density).roundToInt() + padPx
        val hPx     = (spec.heightDp * density).roundToInt() + padPx

        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val finalW  = wPx.coerceAtMost((screenW * 0.95f).toInt())
        val finalH  = hPx.coerceAtMost((screenH * 0.95f).toInt())

        val params = WindowManager.LayoutParams(
            finalW,
            finalH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            windowAnimations = 0
            gravity = when (spec.cornerPosition) {
                "TOP_RIGHT"    -> Gravity.TOP or Gravity.END
                "BOTTOM_LEFT"  -> Gravity.BOTTOM or Gravity.START
                "BOTTOM_RIGHT" -> Gravity.BOTTOM or Gravity.END
                else           -> Gravity.TOP or Gravity.START
            }
            x = 24; y = 24; alpha = 1f
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e("CameraPIP", "Error adding camera overlay", e)
            cameraPipOverlay = null
            return@runOnMain
        }

        // Optional auto-hide
        val timeout = spec.autoHideTimeout
        if (timeout > 0) {
            if (cameraHideHandler == null) cameraHideHandler = Handler(Looper.getMainLooper())
            cameraHideHandler?.postDelayed(cameraHideRunnable, timeout * 1000L)
        }
    }

    @MainThread
    fun hide(): Unit = runOnMain {
        cameraHideHandler?.removeCallbacks(cameraHideRunnable)
        val view = cameraPipOverlay ?: return@runOnMain
        cameraPipOverlay = null

        try { pipLifecycleOwner.pause() } catch (_: Throwable) {}
        try { view.setContent { } } catch (_: Throwable) {}

        view.post {
            try {
                if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
            } catch (t: Throwable) {
                Log.w("CameraPIP", "removeViewImmediate failed, fallback", t)
                try { windowManager.removeView(view) } catch (_: Throwable) {}
            }
        }
    }


    @MainThread
    fun destroy() = runOnMain {
        // Stop any pending auto-hide
        cameraHideHandler?.removeCallbacks(cameraHideRunnable)
        cameraHideHandler = null

        // Hide the overlay if it exists (matches previous behavior)
        try { hide() } catch (_: Throwable) {}

        // Tear down the dedicated Compose lifecycle
        if (pipLifecycleCreated) {
            try { pipLifecycleOwner.destroy() } catch (_: Throwable) {}
            pipLifecycleCreated = false
        }
    }

    // ---------- internals ----------

    private fun resolveCameraEntity(
        cameraEntityId: String?,
        aliasOrName: String?
    ): EntityItem? {
        val sem = SavedEntitiesManager(context)
        val cams = sem.loadEntities().filter { it.id.startsWith("camera.") }

        val e = when {
            !cameraEntityId.isNullOrBlank() ->
                cams.firstOrNull { it.id.equals(cameraEntityId, ignoreCase = true) }

            !aliasOrName.isNullOrBlank() -> {
                val q = aliasOrName.trim().lowercase()
                cams.firstOrNull {
                    it.cameraAlias?.lowercase() == q ||
                            it.customName?.lowercase()  == q ||
                            it.friendlyName?.lowercase()== q
                }
            }
            else -> null
        }

        if (e == null) {
            Toast.makeText(context, "Camera not found: ${cameraEntityId ?: aliasOrName ?: "(none)"}", Toast.LENGTH_SHORT).show()
            return null
        }
        return e
    }

    private fun buildCameraSpecWithOverrides(
        entity: EntityItem,
        req: CameraRequest
    ): CameraPipSpec {
        // 1) Resolve final URL + token (RTSP wins; no HTTP auth for RTSP)
        val rtsp = req.rtspUrl?.takeIf { it.isNotBlank() }
        val (finalUrl, finalToken) = if (rtsp != null) {
            rtsp to null
        } else {
            var baseUrl = SecurePrefsManager.getHAUrl(context)
            val authToken = SecurePrefsManager.getHAToken(context)
            if (baseUrl != null && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                baseUrl = if (baseUrl.contains(":")) "http://$baseUrl" else "http://$baseUrl:8123"
            }
            "${baseUrl}/api/camera_proxy_stream/${entity.id}" to authToken
        }


        // 2) Defaults from per-camera saved state
        val defaultCorner = (entity.lastKnownState["pip_corner"] as? String) ?: "TOP_LEFT"
        val defaultSize   = (entity.lastKnownState["pip_size"]   as? String) ?: "MEDIUM"
        val defaultTitle  = (entity.lastKnownState["show_title"] as? Boolean) ?: true
        val defaultAuto   = ((entity.lastKnownState["auto_hide_timeout"] as? Number)?.toInt())
            ?: ((entity.lastKnownState["auto_hide_timeout"] as? String)?.toIntOrNull())
            ?: 30

        // 3) Apply overrides
        val corner       = mapCornerOverride(req.position) ?: defaultCorner
        val (wDp, hDp)   = chooseSize(req, defaultSize)
        val autoHide     = req.autoHideSec ?: defaultAuto
        val showTitle    = req.showTitle ?: defaultTitle

        val cn = entity.customName
        val fn = entity.friendlyName
        val safeTitle = when {
            !req.customTitle.isNullish() -> req.customTitle!!.trim()
            !cn.isNullOrBlank() && !cn.equals("null", true) -> cn
            !fn.isNullOrBlank() && !fn.equals("null", true) -> fn
            else -> "Camera"
        }                               // final fallback

        return CameraPipSpec(
            url             = finalUrl,
            authToken       = finalToken,  // null for RTSP
            entityId        = entity.id,
            title           = safeTitle,
            widthDp         = wDp,
            heightDp        = hDp,
            marginDp        = 20,
            showTitle       = showTitle,
            cornerPosition  = corner,
            autoHideTimeout = autoHide
        )
    }

    private fun mapCornerOverride(pos: String?): String? = when (pos?.lowercase()) {
        null, ""       -> null
        "top_left"     -> "TOP_LEFT"
        "top_right"    -> "TOP_RIGHT"
        "bottom_left"  -> "BOTTOM_LEFT"
        "bottom_right" -> "BOTTOM_RIGHT"
        else           -> null
    }

    private fun chooseSize(req: CameraRequest, defaultSize: String): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density

        // Custom px → convert to dp
        if (req.sizePxW != null && req.sizePxH != null) {
            val wDp = (req.sizePxW / density).roundToInt().coerceIn(120, 2000)
            val hDp = (req.sizePxH / density).roundToInt().coerceIn(90, 2000)
            return wDp to hDp
        }

        // Named size mapping (keeps old UX)
        return when (req.size?.lowercase()) {
            "small"  -> 200 to 112
            "medium" -> 280 to 158
            "large"  -> 360 to 202
            else -> when (defaultSize.uppercase()) {
                "SMALL"  -> 200 to 112
                "MEDIUM" -> 280 to 158
                "LARGE"  -> 360 to 202
                else     -> 280 to 158   // MEDIUM
            }
        }
    }
}
