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

class OverlayService : Service() {
    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
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
        startForeground(17, notification("Timer ready"))
        if (overlay == null) {
            wave = 0
            waveStartedAt = SystemClock.elapsedRealtime()
            remainingWhenPaused = 0L
            paused = false
            lastAlert = -1
            createOverlay()
            handler.post(tick)
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
            setPadding(12, 9, 12, 9)
            setBackgroundColor(Color.rgb(25, 29, 42))
            alpha = opacity
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "COOPTD WALL ASSIST"
            textSize = 12f
            setTextColor(Color.WHITE)
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
            textSize = 26f
            setTextColor(Color.WHITE)
            setPadding(2, 2, 2, 0)
        }
        countdownText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(115, 220, 170))
            setPadding(2, 0, 2, 4)
        }
        nextText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(130, 210, 255))
            setPadding(2, 3, 2, 6)
        }

        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            textSize = 11f
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
        nextText?.text = upcoming?.let { "NEXT: ${it.second} · WAVE ${it.first}" } ?: "No upcoming reminders"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).notify(17, notification("Wave $wave · ${formatTime(remaining)}"))
        }
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
        overlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
