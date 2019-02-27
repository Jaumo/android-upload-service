package net.gotev.uploadservice

import android.os.Parcel
import android.os.Parcelable

data class LargeIcon(
        var path: String?,
        var width: Float,
        var height: Float
) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readFloat(),
            parcel.readFloat()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path)
        parcel.writeFloat(width)
        parcel.writeFloat(height)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<LargeIcon> {
        override fun createFromParcel(parcel: Parcel): LargeIcon {
            return LargeIcon(parcel)
        }

        override fun newArray(size: Int): Array<LargeIcon?> {
            return arrayOfNulls(size)
        }
    }
}