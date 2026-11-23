package com.yoyoitsyo.cooptdwall

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    companion object { private const val REQUEST_CAPTURE = 41 }
    private val prefs by lazy { getSharedPreferences("wall", MODE_PRIVATE) }
    private lateinit var normalLength: EditText
    private lateinit var wave40Length: EditText
    private lateinit var wave60Length: EditText
    private lateinit var reminders: EditText
    private lateinit var sound: CheckBox
    private lateinit var vibration: CheckBox

    private val bg = Color.rgb(9, 14, 25)
    private val panel = Color.rgb(19, 29, 47)
    private val cyan = Color.rgb(48, 220, 231)
    private val muted = Color.rgb(154, 174, 197)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 28)
            background = gradient(intArrayOf(bg, Color.rgb(14, 27, 43)), 0)
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 22, 20, 20)
            background = gradient(intArrayOf(Color.rgb(17, 49, 69), Color.rgb(21, 31, 53)), 22)
        }
        hero.addView(TextView(this).apply {
            text = "COOPTD  /  WALL ASSISTANT"
            textSize = 12f
            letterSpacing = 0.16f
            setTextColor(cyan)
            typeface = Typeface.DEFAULT_BOLD
        })
        hero.addView(TextView(this).apply {
            text = "HOLD THE WALL."
            textSize = 29f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setPadding(0, 9, 0, 2)
        })
        hero.addView(TextView(this).apply {
            text = "Wave timing • reminders • live screen sync"
            textSize = 13f
            setTextColor(muted)
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        root.addView(sectionTitle("WAVE TIMING"))
        val timingCard = card()
        normalLength = numberField("interval", 60)
        wave40Length = numberField("wave40", 90)
        wave60Length = numberField("wave60", 90)
        addField(timingCard, "NORMAL WAVE  ·  SECONDS", normalLength)
        addField(timingCard, "WAVE 40  ·  SECONDS", wave40Length)
        addField(timingCard, "WAVE 60  ·  SECONDS", wave60Length)
        timingCard.addView(TextView(this).apply {
            text = "Set Wave 40 or 60 to 0 to use the normal duration."
            textSize = 12f
            setTextColor(muted)
            setPadding(2, 9, 2, 2)
        })
        root.addView(timingCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 })

        root.addView(sectionTitle("WAVE REMINDERS"))
        val reminderCard = card()
        reminderCard.addView(TextView(this).apply {
            text = "One reminder per line:  WAVE, UNIT, ACTION"
            textSize = 12f
            setTextColor(muted)
            setPadding(2, 0, 2, 8)
        })
        reminders = EditText(this).apply {
            setText(prefs.getString("reminders", "72, Blue Dragon, LIB\n76, Ivy Whisper, PLACE\n85, Witch Doctor, PLACE + SSS cooldown gem"))
            minLines = 4
            gravity = Gravity.TOP
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setPadding(12, 10, 12, 10)
            background = gradient(intArrayOf(Color.rgb(11, 19, 33), Color.rgb(11, 19, 33)), 12, Color.rgb(44, 66, 88))
        }
        reminderCard.addView(reminders, ViewGroup.LayoutParams(-1, 150))
        sound = CheckBox(this).apply {
            text = "Sound alerts"; setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(cyan)
            isChecked = prefs.getBoolean("sound", true)
        }
        vibration = CheckBox(this).apply {
            text = "Vibration alerts"; setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(cyan)
            isChecked = prefs.getBoolean("vibration", true)
        }
        reminderCard.addView(sound)
        reminderCard.addView(vibration)
        root.addView(reminderCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        root.addView(sectionTitle("CONTROL DECK"))
        root.addView(actionButton("START FLOATING TIMER", true) {
            saveSettings()
            startOverlay()
        }, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 10 })
        root.addView(actionButton("SET / ADJUST CAPTURE AREA", false) {
            saveSettings()
            requestScreenSync(true)
        }, LinearLayout.LayoutParams(-1, 52).apply { bottomMargin = 10 })
        root.addView(actionButton("START AUTO WAVE SYNC", false) {
            saveSettings()
            requestScreenSync(false)
        }, LinearLayout.LayoutParams(-1, 52).apply { bottomMargin = 10 })
        root.addView(actionButton("SAVE SETTINGS", false) {
            saveSettings()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, 48).apply { bottomMargin = 16 })

        root.addView(TextView(this).apply {
            text = "AUTO SYNC NOTE\nScreen sync asks Android for permission to read the game display. This first pass looks for a clear “Wave 12” style label; we’ll tune the scan area against your actual game screen."
            textSize = 12f
            setTextColor(muted)
            setPadding(4, 4, 4, 8)
        })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        })
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
        }
    }

    private fun gradient(colors: IntArray, radius: Int, stroke: Int? = null) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, colors
    ).apply {
        cornerRadius = radius * resources.displayMetrics.density
        if (stroke != null) setStroke((1 * resources.displayMetrics.density).toInt(), stroke)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(15, 14, 15, 14)
        background = gradient(intArrayOf(panel, Color.rgb(15, 24, 40)), 18, Color.rgb(36, 59, 81))
    }

    private fun sectionTitle(textValue: String) = TextView(this).apply {
        text = textValue
        textSize = 12f
        letterSpacing = 0.14f
        setTextColor(cyan)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(3, 3, 3, 9)
    }

    private fun addField(parent: LinearLayout, title: String, field: EditText) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(muted)
            setPadding(2, 8, 2, 4)
        })
        parent.addView(field, ViewGroup.LayoutParams(-1, 48))
    }

    private fun numberField(key: String, fallback: Int): EditText = EditText(this).apply {
        setText(prefs.getInt(key, fallback).toString())
        inputType = 2
        textSize = 16f
        setTextColor(Color.WHITE)
        setPadding(12, 5, 12, 5)
        background = gradient(intArrayOf(Color.rgb(10, 19, 32), Color.rgb(10, 19, 32)), 10, Color.rgb(43, 67, 88))
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        letterSpacing = 0.06f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) Color.rgb(5, 20, 30) else Color.WHITE)
        background = if (primary) gradient(intArrayOf(cyan, Color.rgb(41, 168, 206)), 14)
            else gradient(intArrayOf(Color.rgb(29, 48, 70), Color.rgb(22, 36, 56)), 14, Color.rgb(49, 83, 109))
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun saveSettings() {
        fun read(field: EditText, fallback: Int): Int =
            field.text.toString().trim().toIntOrNull()?.coerceIn(0, 3600) ?: fallback
        prefs.edit()
            .putInt("interval", read(normalLength, 60).coerceAtLeast(5))
            .putInt("wave40", read(wave40Length, 90))
            .putInt("wave60", read(wave60Length, 90))
            .putString("reminders", reminders.text.toString())
            .putBoolean("sound", sound.isChecked)
            .putBoolean("vibration", vibration.isChecked)
            .apply()
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 9)
            Toast.makeText(this, "Allow display over other apps, then tap Start again", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Floating timer started", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    private fun requestScreenSync(calibrate: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow display over other apps first, then tap screen sync again", Toast.LENGTH_LONG).show()
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 9)
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START_SCAN
                putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
                putExtra(OverlayService.EXTRA_RESULT_DATA, data)
                putExtra(OverlayService.EXTRA_CALIBRATE, calibrate)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            Toast.makeText(this, "Screen sync starting…", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
        } else if (requestCode == REQUEST_CAPTURE) {
            Toast.makeText(this, "Screen sync permission was not granted", Toast.LENGTH_SHORT).show()
        }
    }
}
