package dev.trooped.tvquickbars.camera

import android.os.Parcel
import android.os.Parcelable

/**
 * Data class representing the specifications for a Camera Picture-in-Picture (PIP) window.
 * This class is Parcelable, allowing it to be passed between components, such as Activities or Services.
 *
 * @property url The URL of the camera stream.
 * @property authToken Optional authentication token for accessing the camera stream.
 * @property entityId The unique identifier for the camera entity.
 * @property title The title to be displayed for the PIP window.
 * @property widthDp The width of the PIP window in density-independent pixels (dp).
 * @property heightDp The height of the PIP window in density-independent pixels (dp).
 * @property marginDp The margin around the PIP window in density-independent pixels (dp).
 * @property showTitle A boolean indicating whether to display the title in the PIP window.
 * @property cornerPosition The initial corner position of the PIP window on the screen.
 *                          Defaults to "TOP_LEFT". Other possible values could be "TOP_RIGHT",
 *                          "BOTTOM_LEFT", "BOTTOM_RIGHT".
 * @property autoHideTimeout The duration in seconds after which the PIP window should automatically
 *                           hide if there's no interaction. Defaults to 30 seconds.
 */
data class CameraPipSpec(
    val url: String,
    val authToken: String?,
    val entityId: String,
    val title: String,
    val widthDp: Int,
    val heightDp: Int,
    val marginDp: Int,
    val showTitle: Boolean,
    val cornerPosition: String = "TOP_LEFT",
    val autoHideTimeout: Int = 30,
    val rtspProfile: RtspProfile = RtspProfile()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt() == 1,
        parcel.readString() ?: "TOP_LEFT",
        parcel.readInt(),
        parcel.readParcelable(RtspProfile::class.java.classLoader) ?: RtspProfile()    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(authToken)
        parcel.writeString(entityId)
        parcel.writeString(title)
        parcel.writeInt(widthDp)
        parcel.writeInt(heightDp)
        parcel.writeInt(marginDp)
        parcel.writeInt(if (showTitle) 1 else 0)
        parcel.writeString(cornerPosition)
        parcel.writeInt(autoHideTimeout)
        parcel.readParcelable(RtspProfile::class.java.classLoader) ?: RtspProfile()    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<CameraPipSpec> {
        override fun createFromParcel(parcel: Parcel): CameraPipSpec {
            return CameraPipSpec(parcel)
        }

        override fun newArray(size: Int): Array<CameraPipSpec?> {
            return arrayOfNulls(size)
        }
    }
}