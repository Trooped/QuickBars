package dev.trooped.tvquickbars.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dev.trooped.tvquickbars.R

/**
 * Composable function to display a camera Picture-in-Picture (PiP) overlay.
 *
 * This function creates a rounded box that displays a camera stream (MJPEG)
 * and optionally an overlay title at one of the corners.
 *
 * @param spec The [CameraPipSpec] defining the appearance and behavior of the PiP overlay.
 *             This includes the camera URL, authentication token, dimensions, title,
 *             and corner position for the title.
 * @param onClose A lambda function that will be invoked when the PiP overlay is closed.
 *                (Currently, there is no explicit close button in this implementation,
 *                but this parameter is provided for future enhancements or external close triggers).
 */
@Composable
fun CameraPipOverlay(
    spec: CameraPipSpec,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .requiredSize(spec.widthDp.dp, spec.heightDp.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val isRtsp = remember(spec.url) { spec.url.startsWith("rtsp://", ignoreCase = true) }

            if (isRtsp) {
                CameraRtspView(
                    url = spec.url,
                    modifier = Modifier.fillMaxSize(),
                    config = spec.rtspProfile
                )
            } else {
                CameraMjpegView(
                    url = spec.url,
                    authToken = spec.authToken,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (spec.showTitle) {
                val alignment = when (spec.cornerPosition) {
                    "TOP_RIGHT" -> Alignment.BottomStart
                    "BOTTOM_LEFT" -> Alignment.TopEnd
                    "BOTTOM_RIGHT" -> Alignment.TopStart
                    else -> Alignment.BottomEnd
                }
                Box(
                    modifier = Modifier
                        .align(alignment)
                        .padding(8.dp)
                ) {
                    Text(
                        text = spec.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

enum class StreamLatency(val minBufferMs: Int, val maxBufferMs: Int, val startBufferMs: Int, val rebufferMs: Int) {
    // ─────────────────────────────────────────────────────────────
    // LOW LATENCY (Target: ~1 sec delay)
    // ─────────────────────────────────────────────────────────────
    // Start: 500ms (Minimum safe value for 15-30fps cameras)
    // Min: 500ms (Keep it close to the edge)
    // Rebuffer: 500ms (If it freezes, recover fast)
    LOW_LATENCY(
        minBufferMs = 500,
        maxBufferMs = 2000,
        startBufferMs = 500,
        rebufferMs = 500
    ),

    // ─────────────────────────────────────────────────────────────
    // BALANCED (Target: ~1-3 sec delay)
    // ─────────────────────────────────────────────────────────────
    // Start: 1000ms (Safe for almost any camera)
    // Min: 1000ms (Standard buffer)
    BALANCED(
        minBufferMs = 1000,
        maxBufferMs = 4000,
        startBufferMs = 1000,
        rebufferMs = 1000
    ),

    // ─────────────────────────────────────────────────────────────
    // HIGH STABILITY (Target: Unbreakable stream, latency irrelevant)
    // ─────────────────────────────────────────────────────────────
    // Start: 2000ms (Ensures we have a massive safety net before playing)
    // Min: 2000ms (Absorbs huge WiFi spikes)
    HIGH_STABILITY(
        minBufferMs = 2000,
        maxBufferMs = 8000,
        startBufferMs = 2000,
        rebufferMs = 2000
    )
}

enum class TransportProtocol {
    AUTO, // Let ExoPlayer decide (usually UDP)
    TCP,  // Force TCP (Fixes packet loss/green artifacts)
    UDP   // Force UDP (Faster, but glitchy on bad wifi)
}

data class RtspProfile(
    val transport: TransportProtocol = TransportProtocol.TCP, // Default to TCP for HA users
    val latency: StreamLatency = StreamLatency.BALANCED,
    val useSoftwareDecoder: Boolean = false,
    val muteAudio: Boolean = false
)

// ───────────────────────── RTSP player (Media3) ─────────────────────────
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun CameraRtspView(
    url: String,
    config: RtspProfile,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // STATE: Should we show the debug screen?
    var showDebug by remember { mutableStateOf(false) }
    // STATE: Specific error text to display
    var lastError by remember { mutableStateOf<String?>(null) }

    val player = remember(url, config) {
        try {
            // 1. Buffer Settings (Fixed for 10FPS streams)
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    config.latency.minBufferMs,
                    config.latency.maxBufferMs,
                    config.latency.startBufferMs,
                    config.latency.rebufferMs // Rebuffer same as start
                )
                .setBackBuffer(0, false)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            // 2. Decoder Fallback (Important for generic Android TV boxes)
            val renderersFactory = if (config.useSoftwareDecoder) {
                // Use the custom selector we wrote earlier to force Software
                androidx.media3.exoplayer.DefaultRenderersFactory(context)
                    .setMediaCodecSelector(SoftwarePreferredCodecSelector())
                    .setEnableDecoderFallback(true)
            } else {
                // Standard Hardware (with auto-fallback allowed)
                androidx.media3.exoplayer.DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
            }

            // 2. CREATE THE TCP MEDIA SOURCE HERE (Not in LaunchedEffect)
            val mediaItem = androidx.media3.common.MediaItem.fromUri(url)
            val rtspFactory = androidx.media3.exoplayer.rtsp.RtspMediaSource.Factory()
                .setTimeoutMs(4000) // Always good to have a timeout

            // Apply the Transport Protocol logic
            when (config.transport) {
                TransportProtocol.TCP -> rtspFactory.setForceUseRtpTcp(true)
                TransportProtocol.UDP -> rtspFactory.setForceUseRtpTcp(false)
                TransportProtocol.AUTO -> { /* Do nothing, let Exo decide */ }
            }

            val mediaSource = rtspFactory.createMediaSource(mediaItem)

            val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
            if (config.muteAudio) {
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                )
            }

            // 3. Build Player with the source pre-loaded
            androidx.media3.exoplayer.ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
                .setTrackSelector(trackSelector)
                .build().apply {
                    val attrs = androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build()

                    setAudioAttributes(attrs, /* handleAudioFocus = */ !config.muteAudio)
                    volume = if (config.muteAudio) 0f else 1f

                    videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT

                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            val cause = error.cause?.message ?: "Unknown"
                            lastError = "Code: ${error.errorCodeName}\n$cause"
                            showDebug = true
                        }
                    })

                    // Set source and prepare IMMEDIATELY
                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true
                }
        } catch (e: Throwable) {
            lastError = "Init Fail: ${e.message}"
            showDebug = true
            null
        }
    }

    Box(modifier = modifier) {
        if (player != null) {
            // 1. The Video Player (Bottom Layer)
            PlayerSurface(
                player = player,
                // TEXTURE_VIEW is required for rounded corners to work!
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.fillMaxSize() // Fills the parent Box
            )

            // 2. The Debug Overlay (Top Layer) - Only visible on error
            if (showDebug) {
                DebugInfoOverlay(player = player, errorMessage = lastError)
            }
        } else {
            // Fallback if player failed to create entirely
            androidx.compose.material3.Text(
                "RTSP playback unavailable",
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LIFECYCLE: Cleanup
    // ─────────────────────────────────────────────────────────────
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
}


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun DebugInfoOverlay(
    player: androidx.media3.exoplayer.ExoPlayer,
    errorMessage: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
    ) {
        // 1. The Green Matrix Stats (No 'key' wrapper needed)
        AndroidView(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            factory = { context ->
                // FACTORY BLOCK: This runs when the view is first created.
                android.widget.TextView(context).apply {
                    setTextColor(android.graphics.Color.GREEN)
                    textSize = 10f
                    typeface = android.graphics.Typeface.MONOSPACE
                }
            },
            update = { view ->
                // UPDATE BLOCK: This runs on recomposition when 'player' changes.
                // Stop any old helper
                (view.tag as? androidx.media3.exoplayer.util.DebugTextViewHelper)?.stop()

                // Create and attach the new helper
                val helper = androidx.media3.exoplayer.util.DebugTextViewHelper(player, view)
                helper.start()

                // Save reference to stop it later
                view.tag = helper
            },
            onRelease = { view ->
                // Clean up the listener when the composable leaves the composition
                (view.tag as? androidx.media3.exoplayer.util.DebugTextViewHelper)?.stop()
            }
        )

        // 2. The Error Message (Standard Compose Text)
        if (errorMessage != null) {
            Text(
                text = "PLAYBACK FAILED\n\n$errorMessage",
                color = androidx.compose.ui.graphics.Color.Red,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}


// A selector that hunts for "Google" or "Android" software decoders
@UnstableApi
class SoftwarePreferredCodecSelector : MediaCodecSelector {
    @OptIn(UnstableApi::class)
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): MutableList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
        // 1. Get ALL available decoders
        val decoders = MediaCodecUtil.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        ).toMutableList()

        // 2. Sort them so Software decoders come FIRST
        decoders.sortBy { decoder ->
            // If it's hardware, put it at the end (return 1)
            // If it's software (google/android), put it at the start (return -1)
            if (decoder.hardwareAccelerated) 1 else -1
        }

        return decoders
    }
}

/**
 * A Composable function that displays an MJPEG stream from a given URL.
 *
 * This function handles the network request, decodes the MJPEG stream,
 * and displays the frames as images. It also shows loading and error states.
 *
 * @param url The URL of the MJPEG stream.
 * @param authToken An optional authorization token to be included in the request header.
 * @param modifier A [Modifier] to be applied to the layout of the Composable.
 *                 Defaults to [Modifier.fillMaxSize].
 */
@Composable
fun CameraMjpegView(
    url: String,
    authToken: String?,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val tag = "CameraMjpegView"
    val okHttp = remember {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(url, authToken) {
        error = null
        isLoading = true

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $authToken")
            .build()

        try {
            withContext(Dispatchers.IO) {
                try {
                    okHttp.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            val errorMsg = "HTTP ${resp.code}: ${resp.message}"
                            Log.e(tag, errorMsg)
                            error = errorMsg
                            isLoading = false
                            return@withContext
                        }

                        val stream = resp.body?.byteStream()
                        if (stream == null) {
                            error = "Empty body"
                            isLoading = false
                            return@withContext
                        }

                        BufferedInputStream(stream, 64 * 1024).use { bis ->
                            readMjpegLoop(bis) { frameBytes ->
                                val bmp = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                                if (bmp != null) {
                                    bitmap = bmp
                                    if (isLoading) isLoading = false
                                } else {
                                    Log.e(tag, "Failed to decode bitmap from frame")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error in MJPEG request", e)
                    throw e  // rethrow to be caught by outer try-catch
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "MJPEG failed", t)
            error = t.message ?: "Unknown error"
            isLoading = false
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // Scale to fit the container while maintaining aspect ratio
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            error != null -> Text(
                "Camera error: $error",
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
                color = colorResource(id = R.color.md_theme_error) // Use app's theme error color
            )
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = colorResource(id = R.color.md_theme_primary) // Use app's theme primary color
            )
        }
    }
}


/**
 * Reads an MJPEG stream from the given [input] and calls [onFrame] for each complete JPEG frame.
 *
 * This function is suspendable and should be called from a coroutine. It will continue to read
 * frames until the coroutine is cancelled or the input stream is closed.
 *
 * The MJPEG stream is expected to be a sequence of JPEG images, each starting with the SOI
 * marker (0xFF, 0xD8) and ending with the EOI marker (0xFF, 0xD9).
 *
 * @param input The input stream to read the MJPEG data from.
 * @param onFrame A callback function that will be invoked with the byte array of each complete
 * JPEG frame.
 */
private suspend fun readMjpegLoop(
    input: InputStream,
    onFrame: (ByteArray) -> Unit
) {
    val buf = ByteArray(8 * 1024)
    val frame = ByteArrayOutputStream(256 * 1024)

    fun findMarker(bytes: ByteArray, len: Int, hi: Int, lo: Int): Int {
        val hiB = hi and 0xFF
        val loB = lo and 0xFF
        for (i in 0 until (len - 1)) {
            if ((bytes[i].toInt() and 0xFF) == hiB && (bytes[i + 1].toInt() and 0xFF) == loB) return i
        }
        return -1
    }

    while (coroutineContext.isActive) {
        val read = withContext(Dispatchers.IO) { input.read(buf) }
        if (read <= 0) break

        var offset = 0
        while (offset < read) {
            // Look for SOI 0xFF 0xD8
            val window = buf.copyOfRange(offset, read)
            val soi = findMarker(window, window.size, 0xFF, 0xD8)
            if (soi == -1) {
                offset = read
                break
            }

            val start = offset + soi
            frame.reset()
            frame.write(buf, start, read - start)

            // Search for EOI in the current bytes; if not found, keep reading
            var jpegBytes = frame.toByteArray()
            var eoiIndex = findMarker(jpegBytes, jpegBytes.size, 0xFF, 0xD9)

            while (eoiIndex == -1) {
                val more = withContext(Dispatchers.IO) { input.read(buf) }
                if (more <= 0) return
                frame.write(buf, 0, more)

                jpegBytes = frame.toByteArray()
                eoiIndex = findMarker(jpegBytes, jpegBytes.size, 0xFF, 0xD9)
            }

            // We have a full JPEG (SOI..EOI). Emit it.
            onFrame(jpegBytes)

            // Consume up to the end of the current read buffer; next outer read continues.
            offset = read
        }
    }
}
