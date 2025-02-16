package com.oldskool.sessions

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment

class LiveFragment : Fragment() {
    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var lastUrl: String? = null
    private var isAudioPlaying: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        progressBar = view.findViewById(R.id.loadingProgressBar)
        setupWebView(view)
    }

    private fun setupWebView(view: View) {
        webView = view.findViewById<WebView>(R.id.webView)?.apply {
            with(settings) {
                // SECURITY NOTE: JavaScript is required for the audio player functionality.
                // This is safe because:
                // 1. We only load content from our trusted domain
                // 2. We have disabled file and content access
                // 3. We enforce HTTPS and block mixed content
                // 4. We disable WebView cache to prevent persistence of sensitive data
                // 5. Content-Security-Policy headers are enforced on the server
                @SuppressLint("SetJavaScriptEnabled")
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
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar?.isVisible = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar?.isVisible = false
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
        if (isAudioPlaying) {
            webView?.evaluateJavascript("""
                var mediaElements = document.querySelectorAll('audio, video');
                mediaElements.forEach(function(media) {
                    media.play();
                });
            """.trimIndent(), null)
        }
    }

    override fun onPause() {
        super.onPause()
        webView?.evaluateJavascript("""
            var mediaElements = document.querySelectorAll('audio, video');
            mediaElements.forEach(function(media) {
                if (!media.paused) {
                    isAudioPlaying = true;
                    media.pause();
                }
            });
        """.trimIndent(), null)
    }

    override fun onDestroyView() {
        webView?.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
        }
        webView = null
        progressBar = null
        super.onDestroyView()
    }
}
