package com.example.floatclip

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private var overlayManager: OverlayManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivitiesIfAvailable(application)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        checkOverlayPermissionAndStart()
    }

    private fun checkOverlayPermissionAndStart() {
        if (PermissionUtils.hasOverlayPermission(this)) {
            startClipboardServiceAndOverlay()
        } else {
            PermissionUtils.requestOverlayPermission(this) {
                // Permission denied callback
            }
        }
    }

    private fun startClipboardServiceAndOverlay() {
        // Start clipboard monitoring service
        val intent = android.content.Intent(this, ClipboardService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Show overlay widget
        if (overlayManager == null) {
            overlayManager = OverlayManager(applicationContext)
        }
        overlayManager?.showCollapsedWidget()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1234) {
            // Overlay permission result
            if (PermissionUtils.hasOverlayPermission(this)) {
                startClipboardServiceAndOverlay()
            }
        }
    }

    override fun onDestroy() {
        overlayManager?.removeWidget()
        super.onDestroy()
    }
}