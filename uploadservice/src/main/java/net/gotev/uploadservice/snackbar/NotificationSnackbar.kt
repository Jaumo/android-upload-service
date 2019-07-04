package net.gotev.uploadservice.snackbar

import android.animation.Animator
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Context
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.v4.view.ViewCompat
import android.support.v4.view.WindowInsetsCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_notification_snackbar.view.*
import net.gotev.uploadservice.R

class NotificationSnackbar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : LinearLayout(context, attrs), LifecycleObserver {
    //region Statics
    companion object {
        private const val ANIMATION_DURATION = 500L
        const val HIDE_DURATION_MS = 3500
    }
    //endregion

    //region Variables
    private val notificationHandler = Handler(Looper.getMainLooper())
    private var windowInsetsCompat: WindowInsetsCompat? = null
    //endregion

    //region Constructor
    init {
        LayoutInflater.from(context).inflate(R.layout.view_notification_snackbar, this, true)

        val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics).toInt()
        setPadding(padding, padding, padding, padding)

        setBackgroundResource(R.drawable.notification_background)
        layoutTransition = LayoutTransition()
        ViewCompat.setElevation(this, 8f)

        if (context is LifecycleOwner) {
            context.lifecycle.addObserver(this)
        }
    }
    //endregion

    //region Lifecycle
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        imageView.setImageDrawable(null)
        notificationHandler.removeCallbacksAndMessages(null)

        if (parent != null) {
            (parent as ViewGroup).removeView(this)
        }
    }
    //endregion

    //region Public Methods
    fun update(model: NotificationSnackbarModel) {
        notificationHandler.post {
            model.run {
                titleText.text = title
                messageText.text = message
                progressBar.progress = uploadedBytes.toInt()
                progressBar.max = totalBytes.toInt()
                progressBar.indeterminateDrawable.setColorFilter(iconColorInt, PorterDuff.Mode.SRC_ATOP)

                if (totalBytes > 0) {
                    messageText.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                    messageText.visibility = View.VISIBLE
                }

                if (iconBitmap != null && !iconBitmap.isRecycled) {
                    imageView.setImageBitmap(iconBitmap)
                    imageView.clearColorFilter()
                } else {
                    imageView.setImageResource(iconResourceID)
                    imageView.setColorFilter(iconColorInt)
                }
            }
        }
    }

    @JvmOverloads
    fun show(container: ViewGroup, shouldAnimate: Boolean = true) {
        notificationHandler.post {
            if (parent == null) {
                visibility = View.INVISIBLE
                container.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            if (windowInsetsCompat == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    requestApplyInsets()
                }

                ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsetsCompat ->
                    this.windowInsetsCompat = windowInsetsCompat
                    displaySnackbar(shouldAnimate)
                    windowInsetsCompat
                }
            } else {
                displaySnackbar(shouldAnimate)
            }
        }
    }

    @JvmOverloads
    fun hide(shouldAnimate: Boolean = true, shouldClearNotification: Boolean = false) {
        notificationHandler.post {
            val layoutParams = layoutParams
            val marginTop = if (layoutParams is MarginLayoutParams) layoutParams.topMargin else 0
            val translationEnd = (marginTop + measuredHeight) * -1f

            if (shouldAnimate) {
                val objectAnimator = ObjectAnimator.ofFloat(this, "translationY", translationY, translationEnd)
                objectAnimator.duration = ANIMATION_DURATION
                objectAnimator.interpolator = AccelerateDecelerateInterpolator()
                objectAnimator.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationEnd(animation: Animator?) {
                        cleanup(shouldClearNotification)
                    }

                    override fun onAnimationCancel(animation: Animator?) {
                        cleanup(shouldClearNotification)
                    }

                    override fun onAnimationRepeat(animation: Animator?) {
                        // Ignored
                    }

                    override fun onAnimationStart(animation: Animator?) {
                        // Ignored
                    }
                })
                objectAnimator.start()
            } else {
                translationY = translationEnd
                cleanup(shouldClearNotification)
            }
        }
    }
    //endregion

    //region Helpers
    private fun displaySnackbar(shouldAnimate: Boolean = true) {
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics).toInt()
        val topInsets = ((windowInsetsCompat?.stableInsetTop ?: 0) + (windowInsetsCompat?.systemWindowInsetTop ?: 0))

        if (layoutParams is MarginLayoutParams) {
            val marginLayoutParams = layoutParams as MarginLayoutParams
            marginLayoutParams.topMargin = topInsets + margin
            marginLayoutParams.leftMargin = margin
            marginLayoutParams.rightMargin = margin
            layoutParams = marginLayoutParams
            translationY = (marginLayoutParams.topMargin + measuredHeight) * -1f
        }

        visibility = View.VISIBLE

        if (shouldAnimate) {
            val objectAnimator = ObjectAnimator.ofFloat(this, "translationY", translationY, 0f)
            objectAnimator.duration = ANIMATION_DURATION
            objectAnimator.interpolator = AccelerateDecelerateInterpolator()
            objectAnimator.start()
        } else {
            translationY = 0f
        }
    }

    private fun cleanup(shouldClearNotification: Boolean) {
        imageView.setImageDrawable(null)

        if (shouldClearNotification) {
            NotificationSnackbarRepository.model.postValue(null)
        }
    }
    //endregion
}