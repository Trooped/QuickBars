package dev.trooped.tvquickbars.camera

data class CameraRequest(
    val cameraAlias: String? = null,
    val cameraEntity: String? = null,
    val position: String? = null,         // "top_left" | "top_right" | "bottom_left" | "bottom_right"
    val size: String? = null,             // "small" | "medium" | "large"
    val sizePxW: Int? = null,             // custom width in px (if provided)
    val sizePxH: Int? = null,             // custom height in px
    val autoHideSec: Int? = null,         // 0 = never, else seconds
    val showTitle: Boolean? = null,        // overlay title
    val rtspUrl:  String? = null,          // for RTSP stream
    val customTitle: String? = null,       // overrides title if provided/non-blank
    val muteAudio: Boolean? = null,        // RTSP only; true = mute player
    val showToggleToast: Boolean? = null,  // default true when not provided
    val rtspTransport: String? = null, // "tcp", "udp", "auto"
    val rtspLatency: String? = null,   // "low", "balanced", "high"
    val useSoftwareDecoder: Boolean? = null
)
