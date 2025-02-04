package com.oldskool.sessions

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.oldskool.sessions.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    companion object {
        const val ACTION_OPEN_PLAYER = "com.oldskool.sessions.action.OPEN_PLAYER"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_PLAYER) return
        showExistingPlayer()
    }

    private fun showExistingPlayer() {
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                ?: return

            val navController = navHostFragment.navController
            
            // Check if we're already on the player fragment
            if (navController.currentDestination?.id == R.id.navigation_player_detail) {
                // Already showing player, just bring activity to front
                moveTaskToFront()
                return
            }

            // Navigate to player without recreating if possible
            val navOptions = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .build()
            navController.navigate(R.id.navigation_player_detail, null, navOptions)
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Navigation failed: ${e.message}")
        }
    }

    private fun moveTaskToFront() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
    }
}
