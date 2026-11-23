package com.yoyoitsyo.cooptdwall

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.content.pm.ServiceInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OverlayService : Service() {
    private data class ReminderEntry(val wave: Int, val unit: String, val action: String, val lead: Int)
    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_START_SCAN = "START_SCAN"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
        const val EXTRA_CALIBRATE = "capture_calibrate"
        private const val CHANNEL = "cooptd_timer"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var wave = 0
    private var waveStartedAt = 0L
    private var remainingWhenPaused = 0L
    private var paused = false
    private var lastAlert = -1
    private var opacity = 0.92f
    private var scale = 1.0f
    private var waveText: TextView? = null
    private var countdownText: TextView? = null
    private var nextText: TextView? = null
    private var pauseButton: Button? = null
    private val prefs by lazy { getSharedPreferences("wall", MODE_PRIVATE) }
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var scanInFlight = false
    private var lastScanAt = 0L
    private var pendingWave = -1
    private var pendingWaveCount = 0
    private var autoSync = false
    private var calibrationView: View? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private fun durationForWave(number: Int): Long {
        val normal = prefs.getInt("interval", 60).coerceIn(5, 3600)
        val seconds = when (number) {
            40 -> prefs.getInt("wave40", 0).takeIf { it > 0 } ?: normal
            60 -> prefs.getInt("wave60", 0).takeIf { it > 0 } ?: normal
            else -> normal
        }
        return seconds * 1000L
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!paused) {
                var elapsed = (SystemClock.elapsedRealtime() - waveStartedAt).coerceAtLeast(0L)
                var safety = 0
                while (elapsed >= durationForWave(wave) && safety < 100) {
                    elapsed -= durationForWave(wave)
                    wave++
                    waveStartedAt = SystemClock.elapsedRealtime() - elapsed
                    checkReminder()
                    safety++
                }
            }
            refresh()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        @Suppress("DEPRECATION")
        val captureData: Intent? = if (intent?.action == ACTION_START_SCAN) {
            if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else intent?.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        } else null
        val hasCapture = intent?.action == ACTION_START_SCAN && captureData != null
        val foregroundNote = notification(if (hasCapture) "Starting automatic wave sync…" else "Timer ready")
        when {
            Build.VERSION.SDK_INT >= 34 -> {
                val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    (if (hasCapture) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
                startForeground(17, foregroundNote, types)
            }
            Build.VERSION.SDK_INT >= 29 && hasCapture ->
                startForeground(17, foregroundNote, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else -> startForeground(17, foregroundNote)
        }
        if (overlay == null) {
            wave = 0
            waveStartedAt = SystemClock.elapsedRealtime()
            remainingWhenPaused = 0L
            paused = false
            lastAlert = -1
            createOverlay()
            handler.post(tick)
        }
        if (hasCapture && captureData != null) {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
            val calibrate = intent?.getBooleanExtra(EXTRA_CALIBRATE, false) ?: false
            startScreenCapture(resultCode, captureData, calibrate)
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "CoopTD timer", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(msg: String): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder.setContentTitle("CoopTD Wall Assistant")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()
    }

    private fun createOverlay() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(13, 10, 13, 10)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(14, 29, 48), Color.rgb(8, 16, 29))
            ).apply {
                cornerRadius = 18f * resources.displayMetrics.density
                setStroke((1.2f * resources.displayMetrics.density).toInt(), Color.rgb(45, 190, 210))
            }
            alpha = opacity
            elevation = 12f * resources.displayMetrics.density
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "COOPTD  /  WALL ASSIST"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(48, 220, 231))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 5, 8, 5)
        }
        val close = Button(this).apply {
            text = "×"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(4, 0, 4, 0)
            minWidth = 42
            setOnClickListener { stopSelf() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, 40, 1f))
        header.addView(close, LinearLayout.LayoutParams(42, 40))
        waveText = TextView(this).apply {
            textSize = 29f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 2, 2, 0)
        }
        countdownText = TextView(this).apply {
            textSize = 19f
            setTextColor(Color.rgb(75, 235, 185))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 0, 2, 4)
        }
        nextText = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(90, 195, 230))
            setPadding(2, 3, 2, 6)
        }

        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(31, 65, 88), Color.rgb(21, 45, 67))
            ).apply {
                cornerRadius = 9f * resources.displayMetrics.density
                setStroke((1f * resources.displayMetrics.density).toInt(), Color.rgb(48, 112, 139))
            }
            setPadding(2, 0, 2, 0)
            minWidth = 38
            setOnClickListener { action() }
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(button("−") { correctWave(-1) }, LinearLayout.LayoutParams(0, 42, 1f))
        pauseButton = button("PAUSE") { togglePause() }
        controls.addView(pauseButton, LinearLayout.LayoutParams(0, 42, 2f))
        controls.addView(button("+") { correctWave(1) }, LinearLayout.LayoutParams(0, 42, 1f))
        val appearance = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        appearance.addView(button("A−") { changeScale(-0.1f) }, LinearLayout.LayoutParams(0, 38, 1f))
        appearance.addView(button("A+") { changeScale(0.1f) }, LinearLayout.LayoutParams(0, 38, 1f))
        appearance.addView(button("◐") { changeOpacity() }, LinearLayout.LayoutParams(0, 38, 1f))

        box.addView(header)
        box.addView(waveText)
        box.addView(countdownText)
        box.addView(nextText)
        box.addView(controls)
        box.addView(appearance)

        title.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val lp = windowParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        startX = lp.x; startY = lp.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (event.rawX - downX).toInt()
                        lp.y = startY + (event.rawY - downY).toInt()
                        try { windowManager.updateViewLayout(box, lp) } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 12; y = 130 }
        windowParams = params
        windowManager.addView(box, params)
        overlay = box
        applyScale()
        refresh()
    }

    private fun refresh() {
        val elapsed = if (paused) durationForWave(wave) - remainingWhenPaused
            else SystemClock.elapsedRealtime() - waveStartedAt
        val remaining = (durationForWave(wave) - elapsed).coerceAtLeast(0L)
        waveText?.text = "WAVE $wave"
        countdownText?.text = if (paused) "PAUSED · " + formatTime(remainingWhenPaused) else formatTime(remaining)
        val upcoming = reminders().filter { it.wave - it.lead > wave }.minByOrNull { it.wave - it.lead }
        val reminderLine = upcoming?.let {
            val triggerWave = it.wave - it.lead
            val prefix = if (it.lead > 0) "HEADS-UP" else "NEXT"
            "$prefix: ${it.action} ${it.unit} · WAVE ${it.wave} (alert at $triggerWave)"
        } ?: "No upcoming reminders"
        nextText?.text = if (autoSync) "● AUTO SYNC ON  ·  $reminderLine" else reminderLine
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).notify(17, notification("Wave $wave · ${formatTime(remaining)}"))
        }
    }

    private fun startScreenCapture(resultCode: Int, data: Intent, calibrate: Boolean) {
        stopScreenCapture()
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data)
            mediaProjection = projection
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            captureWidth = width
            captureHeight = height
            imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
                val now = SystemClock.elapsedRealtime()
                if (!autoSync || scanInFlight || now - lastScanAt < 850L) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                lastScanAt = now
                scanInFlight = true
                var bitmap: Bitmap? = null
                try {
                    val plane = image.planes[0]
                    val pixelStride = plane.pixelStride
                    val rowPadding = plane.rowStride - pixelStride * width
                    val paddedWidth = width + rowPadding / pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    bitmap = Bitmap.createBitmap(padded, 0, 0, width, height)
                    padded.recycle()
                } catch (_: Exception) {
                    scanInFlight = false
                } finally {
                    image.close()
                }
                val frame = bitmap
                if (frame == null) return@setOnImageAvailableListener
                val crop = cropToSavedArea(frame)
                if (crop == null) {
                    frame.recycle()
                    scanInFlight = false
                    return@setOnImageAvailableListener
                }
                textRecognizer.process(InputImage.fromBitmap(crop, 0))
                    .addOnSuccessListener { result ->
                        val detected = parseWaveNumber(result.text)
                        if (detected != null && detected in 0..200) {
                            if (detected == pendingWave) pendingWaveCount++ else {
                                pendingWave = detected
                                pendingWaveCount = 1
                            }
                            if (pendingWaveCount >= 2 && detected != wave) {
                                wave = detected
                                waveStartedAt = SystemClock.elapsedRealtime()
                                remainingWhenPaused = durationForWave(wave)
                                paused = false
                                lastAlert = -1
                                pauseButton?.text = "PAUSE"
                                nextText?.text = "● SYNCED TO GAME  ·  WAVE $wave"
                                refresh()
                            }
                        }
                    }
                    .addOnFailureListener { }
                    .addOnCompleteListener {
                        if (crop !== frame) crop.recycle()
                        frame.recycle()
                        scanInFlight = false
                    }
            }, handler)
            virtualDisplay = projection.createVirtualDisplay(
                "CoopTDWaveScan", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    autoSync = false
                    stopScreenCapture()
                    refresh()
                }
            }, handler)
            autoSync = false
            if (calibrate || !hasSavedCaptureArea()) {
                showCaptureAreaSelector()
            } else {
                autoSync = true
                nextText?.text = "● AUTO SYNC ON  ·  Scanning saved area"
                refresh()
            }
        } catch (e: Exception) {
            autoSync = false
            nextText?.text = "SCREEN SYNC ERROR  ·  Check permission"
            refresh()
        }
    }


    private fun hasSavedCaptureArea(): Boolean =
        prefs.contains("scan_left") && prefs.contains("scan_top") &&
            prefs.contains("scan_right") && prefs.contains("scan_bottom")

    private fun cropToSavedArea(frame: Bitmap): Bitmap? {
        if (!hasSavedCaptureArea()) return null
        val left = (prefs.getFloat("scan_left", 0f) * frame.width).toInt().coerceIn(0, frame.width - 1)
        val top = (prefs.getFloat("scan_top", 0f) * frame.height).toInt().coerceIn(0, frame.height - 1)
        val right = (prefs.getFloat("scan_right", 1f) * frame.width).toInt().coerceIn(left + 1, frame.width)
        val bottom = (prefs.getFloat("scan_bottom", 1f) * frame.height).toInt().coerceIn(top + 1, frame.height)
        return try { Bitmap.createBitmap(frame, left, top, right - left, bottom - top) } catch (_: Exception) { null }
    }

    private fun showCaptureAreaSelector() {
        if (calibrationView != null) return
        val density = resources.displayMetrics.density
        val selector = object : View(this) {
            private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(205, 3, 10, 20) }
            private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(48, 220, 231); style = Paint.Style.STROKE; strokeWidth = 3f * density
            }
            private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(45, 48, 220, 231); style = Paint.Style.FILL
            }
            private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 15f * density; typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            private val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(185, 207, 224); textSize = 12f * density
            }
            private val savePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(27, 180, 198) }
            private val rect = RectF()
            private var startX = 0f
            private var startY = 0f
            private var drawing = false
            private var hasSelection = false
            private val buttonRect = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
                canvas.drawText("SET WAVE CAPTURE AREA", 22f * density, 42f * density, white)
                canvas.drawText("Drag a box around the wave number.", 22f * density, 68f * density, muted)
                canvas.drawText("Make it tight enough to exclude nearby text.", 22f * density, 88f * density, muted)
                if (hasSelection || drawing) {
                    canvas.drawRoundRect(rect, 8f * density, 8f * density, fill)
                    canvas.drawRoundRect(rect, 8f * density, 8f * density, border)
                    canvas.drawCircle(rect.left, rect.top, 7f * density, savePaint)
                    canvas.drawCircle(rect.right, rect.bottom, 7f * density, savePaint)
                }
                val bw = 190f * density
                val bh = 48f * density
                buttonRect.set((width - bw) / 2f, height - 90f * density, (width + bw) / 2f, height - 90f * density + bh)
                savePaint.alpha = if (hasSelection) 255 else 90
                canvas.drawRoundRect(buttonRect, 12f * density, 12f * density, savePaint)
                white.textAlign = Paint.Align.CENTER
                canvas.drawText("SAVE CAPTURE AREA", buttonRect.centerX(), buttonRect.centerY() + 5f * density, white)
                white.textAlign = Paint.Align.LEFT
                canvas.drawText("Draw again to change the selection.", 22f * density, height - 28f * density, muted)
            }

            private var dragMode = 0
            private var activeCorner = 0
            private var downX = 0f
            private var downY = 0f
            private var startRect = RectF()
            private val handleRadius = 30f * density

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (buttonRect.contains(event.x, event.y) && hasSelection) {
                            saveCaptureArea(rect)
                            return true
                        }
                        if (event.y < 105f * density || event.y > height - 105f * density) return true
                        downX = event.x
                        downY = event.y
                        startRect.set(rect)
                        val corners = listOf(
                            rect.left to rect.top, rect.right to rect.top,
                            rect.left to rect.bottom, rect.right to rect.bottom
                        )
                        activeCorner = corners.indexOfFirst { (x, y) ->
                            hasSelection && kotlin.math.hypot((event.x - x).toDouble(), (event.y - y).toDouble()) <= handleRadius
                        }
                        dragMode = when {
                            activeCorner >= 0 -> 2
                            hasSelection && rect.contains(event.x, event.y) -> 1
                            else -> 0
                        }
                        if (dragMode == 0) {
                            rect.set(event.x, event.y, event.x, event.y)
                            hasSelection = false
                        }
                        drawing = true
                        postInvalidateOnAnimation()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> if (drawing) {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        when (dragMode) {
                            0 -> rect.set(minOf(downX, event.x), minOf(downY, event.y), maxOf(downX, event.x), maxOf(downY, event.y))
                            1 -> {
                                val left = (startRect.left + dx).coerceIn(0f, width - startRect.width())
                                val top = (startRect.top + dy).coerceIn(105f * density, height - 105f * density - startRect.height())
                                rect.set(left, top, left + startRect.width(), top + startRect.height())
                            }
                            2 -> {
                                val leftSide = activeCorner == 0 || activeCorner == 2
                                val topSide = activeCorner == 0 || activeCorner == 1
                                val minSize = 24f * density
                                val l = if (leftSide) (startRect.left + dx).coerceIn(0f, startRect.right - minSize) else startRect.left
                                val r = if (!leftSide) (startRect.right + dx).coerceIn(startRect.left + minSize, width.toFloat()) else startRect.right
                                val t = if (topSide) (startRect.top + dy).coerceIn(105f * density, startRect.bottom - minSize) else startRect.top
                                val bt = if (!topSide) (startRect.bottom + dy).coerceIn(startRect.top + minSize, height - 105f * density) else startRect.bottom
                                rect.set(l, t, r, bt)
                            }
                        }
                        postInvalidateOnAnimation()
                        return true
                    }
                    MotionEvent.ACTION_UP -> if (drawing) {
                        drawing = false
                        hasSelection = rect.width() >= 24f * density && rect.height() >= 18f * density
                        postInvalidateOnAnimation()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        drawing = false
                        rect.set(startRect)
                        hasSelection = rect.width() >= 24f * density && rect.height() >= 18f * density
                        invalidate()
                        return true
                    }
                }
                return true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            windowManager.addView(selector, params)
            calibrationView = selector
            nextText?.text = "● CALIBRATION  ·  Draw around wave number"
            refresh()
        } catch (_: Exception) {
            nextText?.text = "CAPTURE AREA ERROR  ·  Check overlay permission"
        }
    }

    private fun saveCaptureArea(rect: RectF) {
        val view = calibrationView ?: return
        val viewWidth = view.width.coerceAtLeast(1).toFloat()
        val viewHeight = view.height.coerceAtLeast(1).toFloat()
        prefs.edit()
            .putFloat("scan_left", (rect.left / viewWidth).coerceIn(0f, 1f))
            .putFloat("scan_top", (rect.top / viewHeight).coerceIn(0f, 1f))
            .putFloat("scan_right", (rect.right / viewWidth).coerceIn(0f, 1f))
            .putFloat("scan_bottom", (rect.bottom / viewHeight).coerceIn(0f, 1f))
            .apply()
        try { windowManager.removeView(view) } catch (_: Exception) {}
        calibrationView = null
        autoSync = true
        nextText?.text = "● AREA SAVED  ·  Auto sync running"
        refresh()
    }

    private fun parseWaveNumber(text: String): Int? {
        val normalized = text.replace("\\n", " ").replace(Regex("\\s+"), " ")
        val match = Regex("(?i)\\bwave\\s*[:#-]?\\s*(\\d{1,3})\\b").find(normalized)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun stopScreenCapture() {
        calibrationView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        calibrationView = null
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.setOnImageAvailableListener(null, null); imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        autoSync = false
        scanInFlight = false
        pendingWave = -1
        pendingWaveCount = 0
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms + 999L) / 1000L
        return String.format("%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun correctWave(delta: Int) {
        wave = (wave + delta).coerceAtLeast(0)
        waveStartedAt = SystemClock.elapsedRealtime()
        remainingWhenPaused = durationForWave(wave)
        lastAlert = -1
        if (paused) paused = false
        pauseButton?.text = "PAUSE"
        refresh()
    }

    private fun togglePause() {
        if (!paused) {
            val elapsed = (SystemClock.elapsedRealtime() - waveStartedAt).coerceAtLeast(0L)
            remainingWhenPaused = (durationForWave(wave) - elapsed).coerceAtLeast(0L)
            paused = true
            pauseButton?.text = "RESUME"
        } else {
            waveStartedAt = SystemClock.elapsedRealtime() - (durationForWave(wave) - remainingWhenPaused)
            paused = false
            pauseButton?.text = "PAUSE"
        }
        refresh()
    }

    private fun changeScale(delta: Float) {
        scale = (scale + delta).coerceIn(0.7f, 1.6f)
        applyScale()
    }

    private fun applyScale() {
        overlay?.let { view ->
            view.scaleX = scale
            view.scaleY = scale
        }
    }

    private fun changeOpacity() {
        opacity = when {
            opacity > 0.85f -> 0.65f
            opacity > 0.55f -> 0.4f
            opacity > 0.3f -> 1.0f
            else -> 0.92f
        }
        overlay?.alpha = opacity
    }

    private fun reminders(): List<ReminderEntry> =
        prefs.getString("reminders", "").orEmpty().lines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val modern = line.split("|", limit = 4)
            if (modern.size >= 4) {
                val number = modern[0].toIntOrNull() ?: return@mapNotNull null
                ReminderEntry(number, modern[1], modern[2], modern[3].toIntOrNull()?.coerceIn(0,10) ?: 0)
            } else {
                val old = line.split(",", limit = 3)
                val number = old.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
                ReminderEntry(number, old.getOrNull(1)?.trim().orEmpty(), old.getOrNull(2)?.trim().orEmpty(), 0)
            }
        }.sortedBy { it.wave }

    private fun checkReminder() {
        val matches = reminders().filter { it.wave - it.lead == wave }
        if (matches.isEmpty() || wave == lastAlert) return
        lastAlert = wave
        matches.forEachIndexed { index, match ->
            if (prefs.getBoolean("sound", true)) {
                try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 250) } catch (_: Exception) {}
            }
            if (prefs.getBoolean("vibration", true)) {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vibrator.vibrate(350)
            }
            val whenText = if (match.lead > 0) "Heads-up for Wave ${match.wave}" else "Wave ${match.wave}"
            getSystemService(NotificationManager::class.java).notify(180 + index, notification("$whenText: ${match.action} ${match.unit}"))
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        stopScreenCapture()
        try { textRecognizer.close() } catch (_: Exception) {}
        overlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
