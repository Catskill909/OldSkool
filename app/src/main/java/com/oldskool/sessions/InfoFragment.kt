package com.oldskool.sessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment

class InfoFragment : Fragment() {
    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        progressBar = view.findViewById(R.id.loadingProgressBar)
        webView = view.findViewById<WebView>(R.id.webView).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar?.isVisible = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar?.isVisible = false
                }
            }
            loadUrl("https://oldskoolsessions.com/soundboard/")
        }
    }

    override fun onDestroyView() {
        webView?.apply {
            stopLoading()
            clearCache(true)
            loadUrl("about:blank")
        }
        webView = null
        progressBar = null
        super.onDestroyView()
    }
}
