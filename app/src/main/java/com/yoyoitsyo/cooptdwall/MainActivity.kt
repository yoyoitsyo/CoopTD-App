package com.yoyoitsyo.cooptdwall

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("wall", MODE_PRIVATE) }
    private lateinit var normalLength: EditText
    private lateinit var wave40Length: EditText
    private lateinit var wave60Length: EditText
    private lateinit var reminders: EditText
    private lateinit var sound: CheckBox
    private lateinit var vibration: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(18, 23, 34))
        }
        fun label(value: String) = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(Color.rgb(205, 215, 230))
            setPadding(0, 12, 0, 5)
        }
        fun numberField(key: String, fallback: Int): EditText = EditText(this).apply {
            setText(prefs.getInt(key, fallback).toString())
            inputType = 2
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(37, 45, 61))
        }

        root.addView(TextView(this).apply {
            text = "COOPTD WALL ASSISTANT"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        })
        root.addView(label("NORMAL WAVE LENGTH (SECONDS)"))
        normalLength = numberField("interval", 60)
        root.addView(normalLength, ViewGroup.LayoutParams(-1, 52))
        root.addView(label("WAVE 40 LENGTH (SECONDS)"))
        wave40Length = numberField("wave40", 90)
        root.addView(wave40Length, ViewGroup.LayoutParams(-1, 52))
        root.addView(label("WAVE 60 LENGTH (SECONDS)"))
        wave60Length = numberField("wave60", 90)
        root.addView(wave60Length, ViewGroup.LayoutParams(-1, 52))
        root.addView(TextView(this).apply {
            text = "Set Wave 40 and Wave 60 to 0 to use the normal duration."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 4, 0, 4)
        })

        root.addView(label("REMINDERS — one per line: Wave, Unit, Action"))
        reminders = EditText(this).apply {
            setText(prefs.getString("reminders", "72, Blue Dragon, LIB\n76, Ivy Whisper, PLACE\n85, Witch Doctor, PLACE + SSS cooldown gem"))
            minLines = 5
            gravity = Gravity.TOP
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(37, 45, 61))
        }
        root.addView(reminders, ViewGroup.LayoutParams(-1, 210))
        sound = CheckBox(this).apply {
            text = "Sound alerts"; setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("sound", true)
        }
        vibration = CheckBox(this).apply {
            text = "Vibration alerts"; setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("vibration", true)
        }
        root.addView(sound)
        root.addView(vibration)
        root.addView(Button(this).apply {
            text = "SAVE SETTINGS"
            setOnClickListener {
                saveSettings()
                Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "START FLOATING TIMER"
            setOnClickListener { saveSettings(); startOverlay() }
        })
        root.addView(TextView(this).apply {
            text = "Start at Wave 0. The timer counts each wave separately, including your custom Wave 40 and Wave 60 lengths. Use +/- in the floating panel to correct the game wave; the corrected wave becomes the new timing reference."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 14, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
        }
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
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 9
            )
            Toast.makeText(this, "Allow display over other apps, then tap Start again", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Floating timer started", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}
