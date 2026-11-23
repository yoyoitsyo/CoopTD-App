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
    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_START_SCAN = "START_SCAN"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
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
        val captureData = if (intent?.action == ACTION_START_SCAN) {
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent)
        } else null
        val hasCapture = intent?.action == ACTION_START_SCAN && captureData != null
        val foregroundTypes = if (hasCapture) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        if (Build.VERSION.SDK_INT >= 29) startForeground(17, notification(if (hasCapture) "Starting automatic wave sync…" else "Timer ready"), foregroundTypes)
        else startForeground(17, notification("Timer ready"))
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
            startScreenCapture(resultCode, captureData)
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
        val upcoming = reminders().firstOrNull { it.first > wave }
        val reminderLine = upcoming?.let { "NEXT: ${it.second} · WAVE ${it.first}" } ?: "No upcoming reminders"
        nextText?.text = if (autoSync) "● AUTO SYNC ON  ·  $reminderLine" else reminderLine
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).notify(17, notification("Wave $wave · ${formatTime(remaining)}"))
        }
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        stopScreenCapture()
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data)
            mediaProjection = projection
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
                val now = SystemClock.elapsedRealtime()
                if (scanInFlight || now - lastScanAt < 850L) {
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
                textRecognizer.process(InputImage.fromBitmap(frame, 0))
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
            autoSync = true
            nextText?.text = "● AUTO SYNC ON  ·  Looking for WAVE + number"
            refresh()
        } catch (e: Exception) {
            autoSync = false
            nextText?.text = "SCREEN SYNC ERROR  ·  Check permission"
            refresh()
        }
    }

    private fun parseWaveNumber(text: String): Int? {
        val normalized = text.replace("\\n", " ").replace(Regex("\\s+"), " ")
        val match = Regex("(?i)\\bwave\\s*[:#-]?\\s*(\\d{1,3})\\b").find(normalized)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun stopScreenCapture() {
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

    private fun reminders(): List<Pair<Int, String>> =
        prefs.getString("reminders", "").orEmpty().lines().mapNotNull { line ->
            val parts = line.split(",", limit = 3)
            val number = parts.getOrNull(0)?.trim()?.toIntOrNull()
            if (number != null && parts.size >= 2) number to parts.drop(1).joinToString(",").trim() else null
        }.sortedBy { it.first }

    private fun checkReminder() {
        if (wave == lastAlert) return
        val match = reminders().firstOrNull { it.first == wave } ?: return
        lastAlert = wave
        if (prefs.getBoolean("sound", true)) {
            try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 250) } catch (_: Exception) {}
        }
        if (prefs.getBoolean("vibration", true)) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(350)
        }
        getSystemService(NotificationManager::class.java).notify(18, notification("REMINDER: ${match.second} — Wave $wave"))
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
