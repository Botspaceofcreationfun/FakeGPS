package com.gpsfakepro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gpsfakepro.engine.SimStatus
import com.gpsfakepro.service.SimulationStateHolder
import com.gpsfakepro.ui.theme.GpsFakeProTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result is reflected in state; the app works without notifications */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GpsFakeProTheme {
                com.gpsfakepro.ui.AppRoot()
            }
        }

        // Android 13+ requires a runtime permission for the persistent
        // simulation notification. Ask once on first launch.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Keep the screen awake while a simulation is running.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SimulationStateHolder.state.collect { state ->
                    val keepAwake = state.status == SimStatus.RUNNING ||
                        state.status == SimStatus.PAUSED ||
                        state.status == SimStatus.COMPLETED
                    if (keepAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }
}
