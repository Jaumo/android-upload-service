package net.gotev.uploadservice

import android.app.Activity

data class SnackbarHolder(
        var notificationSnackbar: NotificationSnackbar? = null,
        var snackbarActivity: Activity? = null
) {
    fun shouldBeRecreated(activity: Activity): Boolean {
        return notificationSnackbar == null || snackbarActivity !== activity
    }

    fun clear() {
        notificationSnackbar = null
        snackbarActivity = null
    }
}