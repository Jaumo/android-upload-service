package net.gotev.uploadservice

import android.os.Parcel
import android.os.Parcelable

data class ServiceParameters(
        var shouldShareNotificationId: Boolean = false,
        var maxConcurrentUploads: Int = Runtime.getRuntime().availableProcessors()
): Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readByte() != 0.toByte(),
            parcel.readInt()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (shouldShareNotificationId) 1 else 0)
        parcel.writeInt(maxConcurrentUploads)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ServiceParameters> {
        override fun createFromParcel(parcel: Parcel): ServiceParameters {
            return ServiceParameters(parcel)
        }

        override fun newArray(size: Int): Array<ServiceParameters?> {
            return arrayOfNulls(size)
        }
    }
}