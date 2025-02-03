package com.oldskool.sessions

import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.oldskool.sessions.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var currentWebView: WebView? = null
    private var currentFragmentId: Int = 0
    private var isWebViewAudioPlaying: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Setup navigation change listener
        navController.addOnDestinationChangedListener { _, destination, _ ->
            handleDestinationChange(destination)
        }
        
        // Setup bottom navigation
        binding.bottomNavigation.apply {
            setupWithNavController(navController)
            // Disable item clicks on the currently selected item
            setOnItemSelectedListener { menuItem ->
                if (menuItem.itemId == currentFragmentId) {
                    // Prevent reselection of current item
                    false
                } else {
                    if (currentFragmentId == R.id.navigation_live) {
                        cleanupWebViewAudio()
                    }
                    navController.navigate(menuItem.itemId)
                    true
                }
            }
        }
        
        // Store initial fragment id
        currentFragmentId = navController.currentDestination?.id ?: 0
    }

    private fun handleDestinationChange(destination: NavDestination) {
        if (currentFragmentId == R.id.navigation_live && destination.id != R.id.navigation_live) {
            cleanupWebViewAudio()
        }
        currentFragmentId = destination.id
    }

    fun registerWebView(webView: WebView) {
        currentWebView = webView
        // Add JavaScript interface to monitor audio state
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onAudioStateChange(isPlaying: Boolean) {
                isWebViewAudioPlaying = isPlaying
            }
        }, "AudioInterface")
    }

    fun unregisterWebView(webView: WebView) {
        if (currentWebView == webView) {
            currentWebView = null
            isWebViewAudioPlaying = false
        }
    }

    private fun cleanupWebViewAudio() {
        currentWebView?.evaluateJavascript("""
            // Stop all media elements
            var mediaElements = document.querySelectorAll('audio, video');
            mediaElements.forEach(function(media) {
                media.pause();
                media.currentTime = 0;
                // Reset any custom player UI if present
                if (media.parentElement && media.parentElement.classList.contains('audio-player')) {
                    var playButton = media.parentElement.querySelector('.play-button');
                    if (playButton) {
                        playButton.classList.remove('playing');
                    }
                }
            });
            
            // Notify Java about audio state
            AudioInterface.onAudioStateChange(false);
            
            // Reset any custom player states
            var players = document.querySelectorAll('.audio-player');
            players.forEach(function(player) {
                player.classList.remove('playing');
                var progress = player.querySelector('.progress');
                if (progress) {
                    progress.style.width = '0%';
                }
            });
        """.trimIndent(), null)
    }

    fun isAudioPlaying(): Boolean {
        return isWebViewAudioPlaying
    }

    fun loadWebView(url: String) {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val currentDestination = navController.currentDestination?.id
        if (currentDestination != null) {
            cleanupWebViewAudio()
            val bundle = Bundle().apply {
                putString("url", url)
            }
            navController.navigate(currentDestination, bundle)
        }
    }
}
