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
        // Remove any existing loading view first
        hideLoading()
        
        val loadingView = LayoutInflater.from(context).inflate(R.layout.layout_loading, null)
        loadingView.tag = "loading_overlay"
        
        // Add loading view on top of the content
        val parent = parent as? ViewGroup ?: return
        if (parent is FrameLayout) {
            parent.addView(loadingView)
        } else {
            // Wrap target view in FrameLayout if needed
            val index = parent.indexOfChild(this)
            parent.removeView(this)
            
            val frameLayout = FrameLayout(context)
            frameLayout.layoutParams = layoutParams
            
            // Add original view and loading overlay
            frameLayout.addView(this)
            frameLayout.addView(loadingView)
            
            parent.addView(frameLayout, index)
        }
    }

    /**
     * Hides the loading spinner overlay
     */
    fun View.hideLoading() {
        val parent = parent as? ViewGroup ?: return
        val loadingView = parent.findViewWithTag<View>("loading_overlay")
        
        if (loadingView != null) {
            if (parent is FrameLayout && parent.childCount == 2) {
                // If we created a wrapper FrameLayout, remove it and restore original hierarchy
                val grandParent = parent.parent as ViewGroup
                val index = grandParent.indexOfChild(parent)
                
                parent.removeView(this)
                parent.removeView(loadingView)
                grandParent.removeView(parent)
                grandParent.addView(this, index)
            } else {
                // Just remove the loading overlay
                parent.removeView(loadingView)
            }
        }
    }
}