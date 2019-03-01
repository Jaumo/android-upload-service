package net.gotev.uploadservice

import android.os.Parcel
import android.os.Parcelable

data class Dimensions(
        var width: Float,
        var height: Float
) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readFloat(),
            parcel.readFloat()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(width)
        parcel.writeFloat(height)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Dimensions> {
        override fun createFromParcel(parcel: Parcel): Dimensions {
            return Dimensions(parcel)
        }

        override fun newArray(size: Int): Array<Dimensions?> {
            return arrayOfNulls(size)
        }
    }
}