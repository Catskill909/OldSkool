package com.oldskool.sessions.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.oldskool.sessions.databinding.FragmentWebviewBinding
import com.oldskool.sessions.ui.LoadingUtils.showLoading
import com.oldskool.sessions.ui.LoadingUtils.hideLoading

class WebViewFragment : Fragment() {
    private var _binding: FragmentWebviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.webView.apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.root.showLoading()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.root.hideLoading()
                }
            }
        }

        arguments?.getString("url")?.let { url ->
            binding.webView.loadUrl(url)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.root.hideLoading() // Clean up any loading state
        _binding = null
    }
}
