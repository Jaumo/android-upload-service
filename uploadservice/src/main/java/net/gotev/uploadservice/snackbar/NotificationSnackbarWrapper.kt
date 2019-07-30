package net.gotev.uploadservice.snackbar

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.OnLifecycleEvent
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
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
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        shouldShowInstantly = NotificationSnackbarRepository.model.value != null
        NotificationSnackbarRepository.model.observe(fragmentActivity, notificationSnackbarModelObserver)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
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
                show(container = fragmentActivity.window.decorView as ViewGroup, shouldAnimate = !shouldShowInstantly)
            }

        }
        notificationSnackbar?.apply {
            update(notificationSnackbarModel!!)
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