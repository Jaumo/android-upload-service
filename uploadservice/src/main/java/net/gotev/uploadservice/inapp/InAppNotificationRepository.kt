package net.gotev.uploadservice.inapp

import io.reactivex.subjects.PublishSubject

object InAppNotificationRepository {
    const val HIDE_DURATION_MS = 3500
    val model = PublishSubject.create<InAppNotificationModel>()
}