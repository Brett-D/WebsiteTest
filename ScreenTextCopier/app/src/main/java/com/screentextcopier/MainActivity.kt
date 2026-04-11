package com.screentextcopier

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Entry-point activity that:
 *   1. Requests overlay permission (SYSTEM_ALERT_WINDOW).
 *   2. Requests notification permission (Android 13+).
 *   3. Requests screen capture (MediaProjection) permission.
 *   4. Starts FloatingButtonService and ScreenCaptureService.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    // Permission launchers
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkPermissionsAndProceed()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            checkPermissionsAndProceed()
        }

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startServices(result.resultCode, result.data!!)
            } else {
                statusText.text = getString(R.string.status_permission_denied)
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.tv_status)
        startButton = findViewById(R.id.btn_start)
        stopButton = findViewById(R.id.btn_stop)

        startButton.setOnClickListener { checkPermissionsAndProceed() }
        stopButton.setOnClickListener { stopServices() }
    }

    private fun checkPermissionsAndProceed() {
        // Step 1: Overlay permission
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = getString(R.string.status_requesting_overlay)
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        // Step 2: Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                statusText.text = getString(R.string.status_requesting_notifications)
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // Step 3: Screen capture permission
        statusText.text = getString(R.string.status_requesting_capture)
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startServices(resultCode: Int, data: Intent) {
        // Start the screen capture service first (needs foreground for MediaProjection)
        val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        startForegroundService(captureIntent)

        // Then start the floating button overlay service
        val floatingIntent = Intent(this, FloatingButtonService::class.java)
        startForegroundService(floatingIntent)

        statusText.text = getString(R.string.status_running)
        startButton.isEnabled = false
        stopButton.isEnabled = true

        Toast.makeText(this, "Floating button is now active!", Toast.LENGTH_SHORT).show()

        // Minimize the app so the user sees the floating button
        moveTaskToBack(true)
    }

    private fun stopServices() {
        stopService(Intent(this, FloatingButtonService::class.java))

        val stopCaptureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(stopCaptureIntent)

        statusText.text = getString(R.string.status_stopped)
        startButton.isEnabled = true
        stopButton.isEnabled = false
    }
}
