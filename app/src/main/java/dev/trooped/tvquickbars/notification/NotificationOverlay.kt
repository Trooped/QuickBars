package dev.trooped.tvquickbars.notification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import androidx.core.net.toUri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * Simple image overlay:
 * - If imageUrl is provided → fetch once and show.
 * - If iconSvgDataUri is provided but no image → (ignore for now; can add SVG renderer later).
 * - Background color uses "#RRGGBB" + transparency (0..1) if provided.
 */
private object IconHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // small, sensible timeouts to avoid stalls
            .connectTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            // reuse sockets across icons (HTTP/2 pooling)
            .connectionPool(okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
            .build()
    }
}

private const val EXPECTED_ASPECT_GUESS = 16f / 9f

@Composable
fun NotificationOverlay(
    title: String?,
    message: String,
    imageUrl: String?,
    iconSvgDataUri: String?,
    actions: List<NotificationAction>,
    onActionClick: (NotificationAction) -> Unit,
    bgColorHex: String?,
    transparency: Double?,
    onDismissRequest: () -> Unit,
    maxWidthDp: Int = 600
) {
    val bgColor = remember(bgColorHex, transparency) {
        val opaque = parseRgbHex(bgColorHex) ?: Color(0xFF222222)
        val alpha = (1.0 - (transparency ?: 0.0)).coerceIn(0.0, 1.0)
        opaque.copy(alpha = alpha.toFloat())
    }
    val contentColor = remember(bgColor) {
        if (bgColor.luminance() > 0.5f) Color(0xFF101010) else Color.White
    }

    val minCardWidth = 200.dp
    val maxCardWidth = maxWidthDp.dp
    val imageMaxHeight = 200.dp

    var imgAspect by remember { mutableStateOf<Float?>(null) }

    val hasImage = !imageUrl.isNullOrBlank()

    // Start with a stable “good guess” width so the card doesn’t pop later.
    // Final width = min(maxWidth, imageMaxHeight * actualAspect).
    var actualAspect by remember(imageUrl) { mutableStateOf<Float?>(null) }
    val initialWidthGuess = (imageMaxHeight * EXPECTED_ASPECT_GUESS).coerceIn(minCardWidth, maxCardWidth)
    val targetWidth = remember(actualAspect, maxCardWidth, imageMaxHeight) {
        val a = (actualAspect ?: EXPECTED_ASPECT_GUESS).coerceAtLeast(0.25f) // guard against 0 or nonsense
        (imageMaxHeight * a).coerceIn(minCardWidth, maxCardWidth)
    }
    val animatedCardWidth by animateDpAsState(
        targetValue = if (hasImage) targetWidth else minCardWidth,
        animationSpec = tween(220),
        label = "cardWidthAnimV2"
    )

    Column(
        modifier = Modifier
            .widthIn(min = minCardWidth, max = if (hasImage) animatedCardWidth else minCardWidth)
            .wrapContentHeight()
            .onPreviewKeyEvent {
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    onDismissRequest()
                    true // Consume the event
                } else {
                    false // Don't consume other keys
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Header row: icon (optional) + title (optional)
        if (!iconSvgDataUri.isNullOrBlank() || !title.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!iconSvgDataUri.isNullOrBlank()) {
                    HeaderIcon(
                        data = iconSvgDataUri,
                        size = 24.dp,
                        currentColorHex = contentColor.toHex()
                    )
                    if (!title.isNullOrBlank()) Spacer(Modifier.width(8.dp))
                }
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title!!,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Text(
            text = message,
            //textDirection = TextDirection.ContentOrLtr,
            //textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            color = contentColor,
        )

        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ActionRow(
                actions = actions,
                bgColor = bgColor,           // the color you computed for the card
                onActionClick = onActionClick       // your existing callback
            )
        }

        if (!imageUrl.isNullOrBlank()) {
            if (!iconSvgDataUri.isNullOrBlank() || !title.isNullOrBlank() || message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
            }

            val ctx = LocalContext.current

            fun rewriteStreamToStill(u: String?): String? {
                if (u.isNullOrBlank()) return u
                return u
                    .replace("/api/camera_proxy_stream/", "/api/camera_proxy/")
                    .replace("/api/image_proxy_stream/", "/api/image_proxy/")
            }

            val (abs0, isHaImg) = remember(imageUrl) { resolveAgainstHaBase(ctx, imageUrl) }
            val abs = remember(abs0) { rewriteStreamToStill(abs0) }
            val token = remember(isHaImg) { if (isHaImg) SecurePrefsManager.getHAToken(ctx) else null }

            // Try stream-first for a fresh frame; only show fallback still AFTER we tried the stream.
            val haBase = remember { normalizedHaBase(ctx)?.toString()?.trimEnd('/') }
            val entityId = remember(imageUrl) {
                imageUrl.substringAfter("/api/camera_proxy/").substringAfter("/api/camera_proxy_stream/")
            }.takeIf { it.isNotBlank() }

            var freshFrame by remember(entityId) { mutableStateOf<ByteArray?>(null) }
            var loadingStream by remember(entityId) { mutableStateOf(false) }
            var streamAttempted by remember(entityId) { mutableStateOf(false) }

            LaunchedEffect(entityId, isHaImg, token, haBase) {
                if (isHaImg && entityId != null && token != null && haBase != null) {
                    loadingStream = true
                    try {
                        // keep this short so fallback feels snappy
                        freshFrame = fetchFirstMjpegFrameBytes(haBase, token, entityId, timeoutMs = 1200)
                    } catch (_: Throwable) {
                        freshFrame = null
                    } finally {
                        streamAttempted = true
                        loadingStream = false
                    }
                } else {
                    // not an HA image → consider stream attempt done, use abs directly
                    streamAttempted = true
                }
            }

            // Decide what to render:
            // - freshFrame available → use it.
            // - stream tried and failed/timed out → fallback to abs (may be stale).
            // - otherwise (stream in flight) → show placeholder (nothing stale).
            val model: Any? = when {
                freshFrame != null -> freshFrame!!
                streamAttempted     -> abs
                else                -> null
            }

            // Smooth fade-in for the bitmap (no layout jump)
            var visible by remember(model) { mutableStateOf(false) }
            val alpha by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(180),
                label = "imgAlphaV2"
            )

            if (model != null) {
                val req = remember(model, token) {
                    ImageRequest.Builder(ctx)
                        .data(model)
                        .crossfade(false)
                        .apply {
                            if (model is String && !token.isNullOrBlank() && isHaImg) {
                                addHeader("Authorization", "Bearer $token")
                                addHeader("Accept", "image/*")
                                addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                                addHeader("Pragma", "no-cache")
                                addHeader("Expires", "0")
                            }
                        }
                        .build()
                }

                AsyncImage(
                    model = req,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = animatedCardWidth, height = imageMaxHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .alpha(alpha),
                    contentScale = ContentScale.Fit,
                    onSuccess = { success ->
                        val d = success.result.drawable
                        val w = d.intrinsicWidth
                        val h = d.intrinsicHeight
                        if (w > 0 && h > 0) actualAspect = w.toFloat() / h.toFloat()
                        visible = true
                    },
                    onError = { visible = true }
                )
            } else {
                // Placeholder while we wait for the fresh stream frame (keeps layout stable, shows no stale image)
                Spacer(
                    Modifier
                        .size(width = animatedCardWidth, height = imageMaxHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}


private object HaHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Fetch the first JPEG part from HA's MJPEG stream and return its bytes.
 * Throws on protocol/timeouts. Keep timeouts small so UI stays snappy.
 */
@Throws(Exception::class)
private suspend fun fetchFirstMjpegFrameBytes(
    haBase: String,
    token: String,
    entityId: String,
    timeoutMs: Long = 2500
): ByteArray = withContext(Dispatchers.IO) {
    val url = "$haBase/api/camera_proxy_stream/$entityId"
    val req = okhttp3.Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "multipart/x-mixed-replace")
        .build()

    HaHttp.client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) error("Stream HTTP ${resp.code}")
        val ct = resp.header("Content-Type") ?: error("No Content-Type")
        val boundaryToken = ct.substringAfter("boundary=", "").trim().trim('"')
        val boundary = if (boundaryToken.startsWith("--")) boundaryToken else "--$boundaryToken"
        val src = resp.body?.source() ?: error("No body")

        // Read until boundary line
        fun readLine(): String = src.readUtf8LineStrict(timeoutMs)
        var line: String
        do {
            line = readLine()
        } while (!line.contains(boundary))

        // Read headers of first part
        val headers = mutableMapOf<String, String>()
        while (true) {
            line = readLine()
            if (line.isEmpty()) break // end of headers
            val i = line.indexOf(':')
            if (i > 0) {
                headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            }
        }

        val lenHdr = headers["content-length"]
        if (lenHdr != null) {
            val len = lenHdr.toInt()
            return@use src.readByteArray(len.toLong())
        } else {
            // Fallback: read until JPEG EOI (FFD9). We assume SOI (FFD8) at start.
            val buf = okio.Buffer()
            // Read an initial chunk
            src.read(buf, 8 * 1024)
            // Ensure SOI
            val firstTwo = buf.readByteArray(2)
            if (!(firstTwo[0] == 0xFF.toByte() && firstTwo[1] == 0xD8.toByte())) {
                error("Not a JPEG SOI")
            }
            val out = okio.Buffer().write(firstTwo)
            // Stream until we see ... FF D9
            var prev = 0.toByte()
            while (true) {
                if (buf.size < 1) {
                    if (src.read(buf, 8 * 1024) <= 0) error("EOI not found")
                }
                val b = buf.readByte()
                out.writeByte(b.toInt())
                if (prev == 0xFF.toByte() && b == 0xD9.toByte()) break
                prev = b
            }
            return@use out.readByteArray()
        }
    }
}

private fun defaultPortForScheme(scheme: String) =
    if (scheme.equals("https", true)) 443 else 80

private fun normalizedHaBase(ctx: android.content.Context): HttpUrl? {
    var raw = dev.trooped.tvquickbars.persistence.SecurePrefsManager.getHAUrl(ctx)?.trim().orEmpty()
    if (raw.isEmpty()) return null

    // Add scheme if missing
    if (!raw.startsWith("http://", true) && !raw.startsWith("https://", true)) {
        raw = "http://$raw"
    }

    val parsed = raw.toHttpUrlOrNull() ?: return null

    // Detect if user explicitly provided a port in the host part (after scheme)
    // Note: tolerate IPv6 by looking after '://'
    val afterScheme = raw.substringAfter("://")
    val hasExplicitPort = afterScheme.contains(':') && !afterScheme.startsWith("[")

    // If no explicit port and scheme is http, default HA's common port 8123
    return if (!hasExplicitPort && parsed.scheme.equals("http", true)) {
        parsed.newBuilder().port(8123).build()
    } else {
        parsed
    }
}

@Composable
private fun ActionRow(
    actions: List<NotificationAction>,
    onActionClick: (NotificationAction) -> Unit,
    bgColor: Color,
) {
    if (actions.isEmpty()) return

    val firstRequester = remember { FocusRequester() }
    val onColor = if (bgColor.luminance() < 0.5f) Color.White else Color.Black
    val baseContainer = bgColor.copy(alpha = 0.18f)

    // Request initial focus once the composable is ready
    LaunchedEffect(Unit) {
        firstRequester.requestFocus()
    }

    // Use FlowRow to allow buttons to wrap to multiple lines if needed
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(), // Keep focusGroup for proper TV navigation
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp) // Add vertical spacing for wrapped rows
    ) {
        actions.forEachIndexed { index, action ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            val borderW by animateDpAsState(if (isFocused) 3.dp else 1.dp, label = "border")
            val tint by animateColorAsState(
                if (isFocused) baseContainer.copy(alpha = 0.85f) else baseContainer.copy(alpha = 0.95f),
                label = "tint"
            )

            Button(
                onClick = { onActionClick(action) },
                modifier = Modifier
                    // Apply the focus requester only to the first button
                    .then(if (index == 0) Modifier.focusRequester(firstRequester) else Modifier)
                    .border(borderW, onColor, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tint,
                    contentColor   = onColor
                ),
                // Pass the interaction source to the button to observe its focus state
                interactionSource = interactionSource,
            ) {
                Text(
                    text = action.label ?: action.id,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HeaderIcon(
    data: String,
    size: Dp,
    currentColorHex: String // "#RRGGBB"
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx().coerceAtLeast(1) }

    val looksLikeUrlOrPath = remember(data) {
        val s = data.trim()
        s.startsWith("http", ignoreCase = true) || s.startsWith("/")
    }

    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(data, sizePx, currentColorHex) {
        bmp = try {
            val s = data.trim()

            val svgText: String? = when {
                looksLikeUrlOrPath -> {
                    val (abs0, isHa) = resolveAgainstHaBase(ctx, s)
                    val abs = if (abs0?.startsWith("https://api.iconify.design/") == true) {
                        appendIconifyParams(abs0, sizePx, currentColorHex.removePrefix("#"))
                    } else abs0
                    // fetch on IO thread
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        fetchSvgText(ctx, abs, isHa)
                    }
                }

                s.startsWith("data:image/svg+xml;base64,", true) ->
                    String(android.util.Base64.decode(s.substringAfter("base64,"), android.util.Base64.DEFAULT), Charsets.UTF_8)

                s.startsWith("<svg", true) -> s

                s.matches(Regex("^[A-Za-z0-9+/=\\s]+\$")) ->
                    String(Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8)

                else -> null
            }

            if (svgText == null) {
                null
            } else {
                // render off main
                withContext(Dispatchers.Default) {
                    renderSvgToBitmap(svgText, sizePx, currentColorHex)
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    if (bmp != null) {
        Image(bitmap = bmp!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}

// Resolve /local, /api, etc. against HA base & tell if it’s HA-origin
private fun resolveAgainstHaBase(ctx: android.content.Context, raw: String?): Pair<String?, Boolean> {
    if (raw.isNullOrBlank()) return null to false
    val s = raw.trim()

    val ha = normalizedHaBase(ctx)

    // Case 1: already absolute
    if (s.startsWith("http://", true) || s.startsWith("https://", true)) {
        val u = s.toHttpUrlOrNull()
        val isHa = ha != null && u != null &&
                u.scheme.equals(ha.scheme, true) &&
                u.host.equals(ha.host, true) &&
                ( (if (u.port != -1) u.port else defaultPortForScheme(u.scheme)) ==
                        (if (ha.port != -1) ha.port else defaultPortForScheme(ha.scheme)) )
        return s to isHa
    }

    // Case 2: relative -> require HA base
    if (ha == null) return s to false

    val relPath = if (s.startsWith("/")) s else "/$s"

    // Build canonical absolute URL (also percent-encodes spaces/Unicode)
    val abs = ha.newBuilder()
        .encodedPath(relPath)
        .build()
        .toString()

    return abs to true
}

// Add size + color params to Iconify so we get the correct dimensions/tint
private fun appendIconifyParams(url: String, sizePx: Int, colorNoHash: String): String {
    val uri = url.toUri()
    val b = uri.buildUpon()
    if (uri.getQueryParameter("height") == null) b.appendQueryParameter("height", sizePx.toString())
    // pass "#RRGGBB" and let Uri builder encode to %23RRGGBB (avoid %2523)
    if (uri.getQueryParameter("color") == null)  b.appendQueryParameter("color", "#$colorNoHash")
    return b.build().toString()
}

// Fetch SVG text with optional HA Bearer
private fun fetchSvgText(ctx: android.content.Context, url: String?, isHa: Boolean): String? {
    if (url.isNullOrBlank()) return null
    val token = if (isHa) SecurePrefsManager.getHAToken(ctx) else null

    val reqBuilder = okhttp3.Request.Builder().url(url)
    if (!token.isNullOrBlank() && isHa) reqBuilder.header("Authorization", "Bearer $token")

    IconHttp.client.newCall(reqBuilder.build()).execute().use { resp ->
        val ctype = resp.header("Content-Type") ?: ""
        val bodyStr = resp.body?.string()
        if (!resp.isSuccessful) return null

        // be tolerant: some servers return text/plain or octet-stream for SVG
        val looksSvg = bodyStr?.trim()?.startsWith("<svg", ignoreCase = true) == true
        return if (ctype.contains("svg", ignoreCase = true) || looksSvg) bodyStr else null
    }
}

// Sanitize & render via AndroidSVG
private fun renderSvgToBitmap(svgTextIn: String, sizePx: Int, currentColorHex: String?): Bitmap? {
    return try {
        var s = svgTextIn.replace(Regex("""\swidth="[^"]*""""), "")
            .replace(Regex("""\sheight="[^"]*""""), "")
        s = s.replaceFirst(
            Regex("""<svg(\s|>)"""),
            buildString {
                append("<svg width=\"").append(sizePx).append("px\" height=\"").append(sizePx).append("px\"")
                if (!currentColorHex.isNullOrBlank()) append(" style=\"color: ").append(currentColorHex).append('"')
                append(' ')
            }
        )

        val svg = com.caverock.androidsvg.SVG.getFromString(s)
        if (svg.documentViewBox == null) svg.setDocumentViewBox(0f, 0f, 24f, 24f)
        svg.setDocumentWidth(sizePx.toFloat())
        svg.setDocumentHeight(sizePx.toFloat())

        val picture = svg.renderToPicture()
        val bmp = androidx.core.graphics.createBitmap(sizePx, sizePx)
        val canvas = Canvas(bmp)
        canvas.drawPicture(picture)
        bmp
    } catch (t: Throwable) {
        null
    }
}

private fun parseRgbHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val s = hex.trim().removePrefix("#")
    if (s.length != 6) return null
    val r = s.substring(0, 2).toInt(16)
    val g = s.substring(2, 4).toInt(16)
    val b = s.substring(4, 6).toInt(16)
    return Color(r, g, b)
}

private fun Color.toHex(): String {
    val argb = this.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = (argb) and 0xFF
    return String.format("#%02X%02X%02X", r, g, b)
}
