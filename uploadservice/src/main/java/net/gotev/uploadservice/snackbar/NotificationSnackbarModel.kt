package net.gotev.uploadservice.snackbar

import android.app.PendingIntent
import android.graphics.Bitmap
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes

data class NotificationSnackbarModel(
        val title: String?,
        val message: String?,
        val uploadedBytes: Long = 0,
        val totalBytes: Long = 0,
        @DrawableRes val iconResourceID: Int,
        @ColorInt val iconColorInt: Int,
        val iconBitmap: Bitmap?,
        val pendingIntent: PendingIntent?
)