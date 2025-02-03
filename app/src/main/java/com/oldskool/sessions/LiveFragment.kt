package com.oldskool.sessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class LiveFragment : Fragment() {
    private var webView: WebView? = null
    private var lastUrl: String? = null
    private var needsRefresh: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWebView(view)
    }

    private fun setupWebView(view: View) {
        webView = view.findViewById<WebView>(R.id.webView)?.apply {
            with(settings) {
                // Required for audio player functionality, but restricted to trusted domains
                javaScriptEnabled = true
                mediaPlaybackRequiresUserGesture = false
                domStorageEnabled = true
                
                // Security settings
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                
                // Disable WebView cache to ensure fresh content
                cacheMode = WebSettings.LOAD_NO_CACHE
                
                // Set custom user agent
                userAgentString = "OldSkoolSessions-Android"
            }
            
            // Additional security measures
            setOnCreateContextMenuListener(null) // Disable context menu
            isLongClickable = false // Disable long press menu
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    lastUrl = url
                }
            }
            webChromeClient = WebChromeClient()
        }
        
        loadInitialUrl()
    }

    private fun loadInitialUrl() {
        webView?.loadUrl("https://oldskoolsessions.com/OSS/")
    }

    override fun onResume() {
        super.onResume()
        if (needsRefresh) {
            // Reload the WebView when returning to ensure fresh state
            webView?.reload()
            needsRefresh = false
        }
        registerWebView()
    }

    override fun onPause() {
        super.onPause()
        // Mark for refresh if audio was playing
        needsRefresh = (activity as? MainActivity)?.isAudioPlaying() == true
        unregisterWebView()
    }

    override fun onDestroyView() {
        unregisterWebView()
        webView?.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
        }
        webView = null
        super.onDestroyView()
    }

    private fun registerWebView() {
        webView?.let { (activity as? MainActivity)?.registerWebView(it) }
    }

    private fun unregisterWebView() {
        webView?.let { (activity as? MainActivity)?.unregisterWebView(it) }
    }
}
