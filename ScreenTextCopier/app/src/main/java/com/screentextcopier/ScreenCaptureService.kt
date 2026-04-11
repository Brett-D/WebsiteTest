package com.screentextcopier

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.screentextcopier.model.ClipboardItem
import java.io.File
import java.io.FileOutputStream

/**
 * Service that handles MediaProjection-based screen capture and OCR processing.
 *
 * The capture flow:
 *   1. MainActivity obtains a MediaProjection result intent and stores it.
 *   2. FloatingButtonService sends ACTION_CAPTURE to trigger a capture.
 *   3. This service creates a VirtualDisplay, captures one frame, runs OCR,
 *      and saves the result to ClipboardHistoryManager.
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val ocrProcessor = OcrProcessor()
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == Activity.RESULT_OK && data != null) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    val projectionManager =
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                    projectionResultCode = resultCode
                    projectionData = data
                }
            }
            ACTION_CAPTURE -> {
                performCapture()
            }
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        ocrProcessor.close()
    }

    private fun performCapture() {
        if (mediaProjection == null) {
            // Try to re-create from stored data
            val code = projectionResultCode
            val data = projectionData
            if (code != null && data != null) {
                val projectionManager =
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(code, data)
            }
        }

        val projection = mediaProjection
        if (projection == null) {
            Toast.makeText(this, "Screen capture not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = projection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, handler
        )

        // Capture a single frame after a short delay
        handler.postDelayed({
            captureFrame(width, height)
        }, 300)
    }

    private fun captureFrame(width: Int, height: Int) {
        val image = imageReader?.acquireLatestImage()
        if (image == null) {
            Toast.makeText(this, "Failed to capture screen", Toast.LENGTH_SHORT).show()
            cleanupVirtualDisplay()
            return
        }

        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        // Crop to actual screen width (remove row padding)
        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        if (croppedBitmap != bitmap) bitmap.recycle()

        cleanupVirtualDisplay()

        // Save thumbnail
        val thumbnailFile = File(filesDir, "capture_${System.currentTimeMillis()}.jpg")
        FileOutputStream(thumbnailFile).use { out ->
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        // Run OCR
        ocrProcessor.processImage(
            croppedBitmap,
            onResult = { text ->
                val item = ClipboardItem(
                    text = text.ifBlank { null },
                    imagePath = thumbnailFile.absolutePath
                )
                ScreenTextCopierApp.instance.clipboardHistoryManager.addItem(item)

                if (text.isNotBlank()) {
                    // Also copy to system clipboard immediately
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("OCR Text", text)
                    )
                    handler.post {
                        Toast.makeText(this, "Text captured and copied!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    handler.post {
                        Toast.makeText(this, "No text found in capture", Toast.LENGTH_SHORT).show()
                    }
                }
                croppedBitmap.recycle()
            },
            onError = { error ->
                handler.post {
                    Toast.makeText(this, "OCR error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
                croppedBitmap.recycle()
            }
        )
    }

    private fun cleanupVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    private fun stopCapture() {
        cleanupVirtualDisplay()
        mediaProjection?.stop()
        mediaProjection = null
    }

    // ── Notification ─────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active during screen capture"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture Active")
            .setContentText("Ready to capture text from screen")
            .setSmallIcon(R.drawable.ic_capture)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.screentextcopier.START_CAPTURE"
        const val ACTION_CAPTURE = "com.screentextcopier.DO_CAPTURE"
        const val ACTION_STOP = "com.screentextcopier.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "screen_capture_channel"

        // Stored projection data for re-use
        var projectionResultCode: Int? = null
        var projectionData: Intent? = null
    }
}
