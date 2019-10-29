package net.gotev.uploadservice.inapp

import androidx.lifecycle.MutableLiveData

object InAppNotificationRepository {
    const val HIDE_DURATION_MS = 3500
    val model = MutableLiveData<InAppNotificationModel?>()
}