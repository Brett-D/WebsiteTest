package com.screentextcopier

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.screentextcopier.model.ClipboardItem

/**
 * Foreground service that shows a draggable floating button overlay.
 *
 * - Single tap: triggers a screen capture + OCR flow.
 * - Long press: expands/collapses a clipboard history panel.
 */
class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButtonView: View
    private lateinit var clipboardOverlayView: View
    private lateinit var clipboardAdapter: ClipboardAdapter
    private lateinit var historyManager: ClipboardHistoryManager

    private val handler = Handler(Looper.getMainLooper())
    private var isClipboardVisible = false

    // Dragging state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private lateinit var floatingButtonParams: WindowManager.LayoutParams
    private lateinit var clipboardOverlayParams: WindowManager.LayoutParams

    private val historyChangeListener: () -> Unit = {
        handler.post { refreshClipboardList() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        historyManager = ScreenTextCopierApp.instance.clipboardHistoryManager
        historyManager.addChangeListener(historyChangeListener)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupFloatingButton()
        setupClipboardOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        historyManager.removeChangeListener(historyChangeListener)
        try { windowManager.removeView(floatingButtonView) } catch (_: Exception) {}
        try { windowManager.removeView(clipboardOverlayView) } catch (_: Exception) {}
    }

    // ── Floating Button Setup ────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingButton() {
        floatingButtonView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        floatingButtonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onFloatingButtonTap()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                toggleClipboardOverlay()
            }
        })

        val button = floatingButtonView.findViewById<ImageButton>(R.id.fab_capture)
        button.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingButtonParams.x
                    initialY = floatingButtonParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingButtonParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    floatingButtonParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingButtonView, floatingButtonParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingButtonView, floatingButtonParams)
    }

    // ── Clipboard Overlay Setup ──────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClipboardOverlay() {
        clipboardOverlayView = LayoutInflater.from(this).inflate(R.layout.clipboard_overlay, null)

        val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        clipboardOverlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.55).toInt(),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        // Set up RecyclerView
        val recyclerView = clipboardOverlayView.findViewById<RecyclerView>(R.id.rv_clipboard_items)
        clipboardAdapter = ClipboardAdapter(
            onItemClick = { item -> copyToSystemClipboard(item) },
            onItemLongClick = { item -> showTextSelectionDialog(item) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = clipboardAdapter

        // Clear all button
        clipboardOverlayView.findViewById<View>(R.id.btn_clear_all).setOnClickListener {
            historyManager.clearAll()
        }

        // Close button
        clipboardOverlayView.findViewById<View>(R.id.btn_close_clipboard).setOnClickListener {
            toggleClipboardOverlay()
        }

        // Start hidden
        clipboardOverlayView.visibility = View.GONE
        windowManager.addView(clipboardOverlayView, clipboardOverlayParams)
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private fun onFloatingButtonTap() {
        // Hide floating button momentarily, request screen capture via ScreenCaptureService
        floatingButtonView.visibility = View.GONE
        if (isClipboardVisible) {
            clipboardOverlayView.visibility = View.GONE
        }

        // Small delay to ensure overlay is hidden before capture
        handler.postDelayed({
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CAPTURE
            }
            startService(intent)

            // Restore floating button visibility after capture delay
            handler.postDelayed({
                floatingButtonView.visibility = View.VISIBLE
                if (isClipboardVisible) {
                    clipboardOverlayView.visibility = View.VISIBLE
                }
            }, 500)
        }, 200)
    }

    private fun toggleClipboardOverlay() {
        isClipboardVisible = !isClipboardVisible
        if (isClipboardVisible) {
            refreshClipboardList()
            clipboardOverlayView.visibility = View.VISIBLE
        } else {
            clipboardOverlayView.visibility = View.GONE
        }
    }

    private fun refreshClipboardList() {
        val items = historyManager.getItems()
        clipboardAdapter.submitList(items)

        val emptyView = clipboardOverlayView.findViewById<TextView>(R.id.tv_empty_clipboard)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun copyToSystemClipboard(item: ClipboardItem) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = item.text ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    /**
     * Long-pressing a clipboard item opens a dialog / selectable text view
     * so the user can highlight and copy a portion of the text.
     */
    private fun showTextSelectionDialog(item: ClipboardItem) {
        if (item.text.isNullOrBlank()) {
            Toast.makeText(this, "No text to select", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a selectable-text overlay
        val textOverlay = LayoutInflater.from(this).inflate(R.layout.text_selection_overlay, null)
        val selectableText = textOverlay.findViewById<TextView>(R.id.tv_selectable_text)
        selectableText.text = item.text
        selectableText.setTextIsSelectable(true)

        val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        val closeBtn = textOverlay.findViewById<View>(R.id.btn_close_selection)
        closeBtn.setOnClickListener {
            try { windowManager.removeView(textOverlay) } catch (_: Exception) {}
        }

        val copyBtn = textOverlay.findViewById<View>(R.id.btn_copy_selection)
        copyBtn.setOnClickListener {
            // Copy whatever is currently selected (or the full text)
            val fullText = item.text ?: ""
            val selectedStart = selectableText.selectionStart
            val selectedEnd = selectableText.selectionEnd
            val selectedText = if (selectedStart >= 0 && selectedEnd > selectedStart
                && selectedStart <= fullText.length && selectedEnd <= fullText.length
            ) {
                fullText.substring(selectedStart, selectedEnd)
            } else {
                fullText
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Selected Text", selectedText))
            Toast.makeText(this, "Copied selected text", Toast.LENGTH_SHORT).show()
            try { windowManager.removeView(textOverlay) } catch (_: Exception) {}
        }

        windowManager.addView(textOverlay, params)
    }

    // ── Notification ─────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Text Copier",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating button active"
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
            .setContentTitle("Screen Text Copier")
            .setContentText("Tap the floating button to capture text")
            .setSmallIcon(R.drawable.ic_capture)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_button_channel"
    }
}
