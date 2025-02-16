package com.oldskool.sessions.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.oldskool.sessions.R

/**
 * Utility functions for managing loading state in views
 */
object LoadingUtils {
    /**
     * Shows a loading spinner overlay on top of the specified view
     */
    fun View.showLoading() {
        val loadingView = this.findViewWithTag<View>("loading_overlay")
            ?: LayoutInflater.from(context)
                .inflate(R.layout.layout_loading, this as ViewGroup, false)
                .apply { tag = "loading_overlay" }
        
        if (loadingView.parent == null && this is ViewGroup) {
            addView(loadingView)
        }
        loadingView.visibility = View.VISIBLE
    }

    /**
     * Hides the loading spinner overlay
     */
    fun View.hideLoading() {
        this.findViewWithTag<View>("loading_overlay")?.visibility = View.GONE
    }
}