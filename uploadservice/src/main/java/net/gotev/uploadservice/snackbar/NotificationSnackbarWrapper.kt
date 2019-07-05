package net.gotev.uploadservice.snackbar

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.Observer
import android.arch.lifecycle.OnLifecycleEvent
import android.os.Handler
import android.os.Looper
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.ViewGroup

/**
 * A wrapper for Fragment Activities to allow them to display NotificationSnackbars without
 * needing to directly implement the display logic.
 */
class NotificationSnackbarWrapper(private val fragmentActivity: FragmentActivity) : LifecycleObserver {
    //region Variables
    private var notificationSnackbarModelObserver = Observer<NotificationSnackbarModel?> { model ->
        if (model != null) {
            showNotificationSnackbar(model)
        } else {
            hideNotificationSnackbar()
        }
    }
    private var notificationSnackbar: NotificationSnackbar? = null
    private val hideHandler by lazy { Handler(Looper.getMainLooper()) }
    private var shouldShowInstantly = false
    //endregion

    //region Constructor
    init {
        fragmentActivity.lifecycle.addObserver(this)
    }
    //endregion

    //region Lifecycle
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        shouldShowInstantly = NotificationSnackbarRepository.model.value != null
        NotificationSnackbarRepository.model.observe(fragmentActivity, notificationSnackbarModelObserver)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        NotificationSnackbarRepository.model.removeObserver(notificationSnackbarModelObserver)
        notificationSnackbar?.hide(shouldAnimate = false, shouldClearNotification = false)
        notificationSnackbar = null
        hideHandler.removeCallbacksAndMessages(null)
    }
    //endregion

    //region Helpers
    private fun showNotificationSnackbar(notificationSnackbarModel: NotificationSnackbarModel?) {
        // Prevent any pending hide actions from executing
        hideHandler.removeCallbacksAndMessages(null)

        if (notificationSnackbar == null) {
            notificationSnackbar = NotificationSnackbar(fragmentActivity).apply {
                update(notificationSnackbarModel!!)
                show(container = fragmentActivity.window.decorView as ViewGroup, shouldAnimate = !shouldShowInstantly)
                setOnClickListener {
                    try {
                        if (notificationSnackbarModel.pendingIntent != null) {
                            notificationSnackbarModel.pendingIntent.send()
                            NotificationSnackbarRepository.model.postValue(null)
                        }
                    } catch (e: Exception) {
                        Log.e("Pending Intent", "Could not consume click event", e)
                    }
                }
            }

        } else {
            notificationSnackbar?.update(notificationSnackbarModel!!)
        }
    }

    private fun hideNotificationSnackbar() {
        if (fragmentActivity.lifecycle.currentState == Lifecycle.State.RESUMED) {
            // Delay hiding briefly to allow user to read notification
            hideHandler.postDelayed({
                notificationSnackbar?.hide()
                notificationSnackbar = null
            }, NotificationSnackbar.HIDE_DURATION_MS.toLong())
        } else {
            notificationSnackbar?.hide(shouldAnimate = true, shouldClearNotification = false)
            notificationSnackbar = null
        }
    }
    //endregion
}