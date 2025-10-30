package org.dydlakcloud.resticopia.ui.common

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.dydlakcloud.resticopia.R

/**
 * Generic skeleton loader view with customizable item count and layout
 * 
 * Usage:
 * ```xml
 * <org.dydlakcloud.resticopia.ui.common.SkeletonLoaderView
 *     android:id="@+id/skeleton_loader"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:itemLayout="@layout/skeleton_item_snapshot"
 *     app:itemCount="3" />
 * ```
 */
class SkeletonLoaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var itemLayoutRes: Int = R.layout.skeleton_item_snapshot
    private var itemCount: Int = 3
    private val skeletonItems = mutableListOf<View>()

    init {
        orientation = VERTICAL

        // Read custom attributes
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SkeletonLoaderView,
            0, 0
        ).apply {
            try {
                itemLayoutRes = getResourceId(
                    R.styleable.SkeletonLoaderView_itemLayout,
                    R.layout.skeleton_item_snapshot
                )
                itemCount = getInt(R.styleable.SkeletonLoaderView_itemCount, 3)
            } finally {
                recycle()
            }
        }

        createSkeletonItems()
    }

    private fun createSkeletonItems() {
        val inflater = LayoutInflater.from(context)
        
        for (i in 0 until itemCount) {
            val itemView = inflater.inflate(itemLayoutRes, this, false)
            skeletonItems.add(itemView)
            addView(itemView)
            
            // Add divider between items (except after last item)
            if (i < itemCount - 1) {
                val divider = View(context).apply {
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        1
                    )
                    setBackgroundResource(R.drawable.list_divider)
                }
                addView(divider)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startShimmerAnimation()
    }

    override fun onDetachedFromWindow() {
        stopShimmerAnimation()
        super.onDetachedFromWindow()
    }

    private fun startShimmerAnimation() {
        skeletonItems.forEach { itemView ->
            startAnimationForView(itemView)
        }
    }

    private fun startAnimationForView(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                startAnimationForView(view.getChildAt(i))
            }
        } else {
            val background = view.background
            if (background is AnimationDrawable) {
                background.start()
            }
        }
    }

    private fun stopShimmerAnimation() {
        skeletonItems.forEach { itemView ->
            stopAnimationForView(itemView)
        }
    }

    private fun stopAnimationForView(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                stopAnimationForView(view.getChildAt(i))
            }
        } else {
            val background = view.background
            if (background is AnimationDrawable) {
                background.stop()
            }
        }
    }

    /**
     * Update the number of skeleton items displayed
     */
    fun setItemCount(count: Int) {
        if (count != itemCount) {
            itemCount = count
            removeAllViews()
            skeletonItems.clear()
            createSkeletonItems()
            if (isAttachedToWindow) {
                startShimmerAnimation()
            }
        }
    }

    /**
     * Change the skeleton item layout
     */
    fun setItemLayout(layoutRes: Int) {
        if (layoutRes != itemLayoutRes) {
            itemLayoutRes = layoutRes
            removeAllViews()
            skeletonItems.clear()
            createSkeletonItems()
            if (isAttachedToWindow) {
                startShimmerAnimation()
            }
        }
    }
}

