package net.gotev.uploadservice.snackbar

import android.arch.lifecycle.MutableLiveData

object NotificationSnackbarRepository {
    val model = MutableLiveData<NotificationSnackbarModel?>()
}