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
    private var objectAnimator: ObjectAnimator? = null
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
        objectAnimator?.end()
        (parent as? ViewGroup)?.removeView(this)
    }
    //endregion

    //region Public Methods
    fun update(model: NotificationSnackbarModel) {
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

    @JvmOverloads
    fun show(container: ViewGroup, shouldAnimate: Boolean = true) {
        if (parent == null) {
            visibility = View.INVISIBLE
            container.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        if (windowInsetsCompat != null) {
            displaySnackbar(windowInsetsCompat!!, shouldAnimate)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                requestApplyInsets()
            }

            ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsetsCompat ->
                this.windowInsetsCompat = windowInsetsCompat
                ViewCompat.setOnApplyWindowInsetsListener(v, null)
                displaySnackbar(windowInsetsCompat, shouldAnimate)
                windowInsetsCompat
            }
        }
    }

    @JvmOverloads
    fun hide(shouldAnimate: Boolean = true, shouldClearNotification: Boolean = false) {
        val layoutParams = layoutParams
        val marginTop = if (layoutParams is MarginLayoutParams) layoutParams.topMargin else 0
        val translationEnd = (marginTop + measuredHeight) * -1f

        if (shouldAnimate) {
            objectAnimator?.end()
            objectAnimator = ObjectAnimator.ofFloat(this, "translationY", translationY, translationEnd).apply {
                duration = ANIMATION_DURATION
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : Animator.AnimatorListener {
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
                start()
            }
        } else {
            translationY = translationEnd
            cleanup(shouldClearNotification)
        }
    }
    //endregion

    //region Helpers
    private fun displaySnackbar(windowInsetsCompat: WindowInsetsCompat, shouldAnimate: Boolean = true) {
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics).toInt()
        val topInsets = windowInsetsCompat.stableInsetTop + windowInsetsCompat.systemWindowInsetTop

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
            objectAnimator?.end()
            objectAnimator = ObjectAnimator.ofFloat(this, "translationY", translationY, 0f).apply {
                duration = ANIMATION_DURATION
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
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