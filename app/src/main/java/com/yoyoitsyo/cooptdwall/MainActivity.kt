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
    private var pendingCalibration = false
    private lateinit var normalLength: EditText
    private lateinit var wave40Length: EditText
    private lateinit var wave60Length: EditText
    private val reminderRows = mutableListOf<ReminderRow>()
    private lateinit var reminderList: LinearLayout
    private lateinit var sound: CheckBox
    private lateinit var vibration: CheckBox
    private lateinit var soundStyle: Spinner

    private val bg = Color.rgb(9, 14, 25)
    private val panel = Color.rgb(19, 29, 47)
    private val cyan = Color.rgb(48, 220, 231)
    private val muted = Color.rgb(154, 174, 197)
    private data class ReminderRow(val wave: Spinner, val unit: Spinner, val action: Spinner, val lead: Spinner, val custom: EditText)

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
            text = "Choose the target wave, unit, action, and how early to warn you."
            textSize = 12f
            setTextColor(muted)
            setPadding(2, 0, 2, 10)
        })
        reminderList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        reminderCard.addView(reminderList)
        val saved = prefs.getString("reminders", "").orEmpty()
        if (saved.isBlank()) {
            addReminderRow(72, "Blue Dragon", "LIB", 0)
            addReminderRow(76, "Ivy Whisper", "PLACE", 0)
            addReminderRow(85, "Witch Doctor", "Activate", 0)
        } else {
            saved.lines().forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 4) {
                    addReminderRow(parts[0].toIntOrNull() ?: 72, parts[1], parts[2], parts[3].toIntOrNull() ?: 0)
                } else {
                    val old = line.split(",", limit = 3)
                    if (old.size >= 3) addReminderRow(old[0].trim().toIntOrNull() ?: 72, old[1].trim(), old[2].trim(), 0)
                }
            }
        }
        reminderCard.addView(actionButton("+ ADD WAVE ALERT", false) {
            addReminderRow(72, "Blue Dragon", "LIB", 0)
        }, LinearLayout.LayoutParams(-1, 44).apply { topMargin = 8 })
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
        reminderCard.addView(TextView(this).apply { text = "ALERT SOUND"; textSize = 10f; setTextColor(muted); setPadding(2, 8, 2, 3) })
        soundStyle = soundSpinner()
        reminderCard.addView(soundStyle, LinearLayout.LayoutParams(-1, 44))
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
        textSize = 13f
        setTextColor(Color.WHITE)
        setPadding(12, 5, 12, 5)
        background = gradient(intArrayOf(Color.rgb(10, 19, 32), Color.rgb(10, 19, 32)), 10, Color.rgb(43, 67, 88))
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 12f
        letterSpacing = 0.015f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        includeFontPadding = true
        minHeight = 48
        setPadding(12, 10, 12, 10)
        setTextColor(if (primary) Color.rgb(3, 18, 28) else Color.rgb(255, 255, 255))
        background = if (primary) gradient(intArrayOf(cyan, Color.rgb(41, 168, 206)), 14)
            else gradient(intArrayOf(Color.rgb(29, 48, 70), Color.rgb(22, 36, 56)), 14, Color.rgb(49, 83, 109))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun soundSpinner(): Spinner {
        val options = listOf("Beep", "Alarm", "Urgent")
        return Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, options) {
                private fun styled(position: Int, convertView: View?, dropdown: Boolean): View {
                    val label = (convertView as? TextView) ?: TextView(this@MainActivity)
                    label.text = options[position]
                    label.textSize = 12f
                    label.setTextColor(Color.WHITE)
                    label.gravity = Gravity.CENTER
                    label.textAlignment = View.TEXT_ALIGNMENT_CENTER
                    label.includeFontPadding = true
                    label.minHeight = 54
                    label.setSingleLine(true)
                    label.ellipsize = android.text.TextUtils.TruncateAt.END
                    label.setPadding(10, 12, 10, 12)
                    label.layoutParams = ViewGroup.LayoutParams(-1, -1)
                    label.background = gradient(intArrayOf(Color.rgb(20, 39, 59), Color.rgb(12, 24, 40)), 6, Color.rgb(48, 92, 120))
                    return label
                }
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = styled(position, convertView, false)
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = styled(position, convertView, true)
            }
            setPopupBackgroundDrawable(gradient(intArrayOf(Color.rgb(17, 32, 50), Color.rgb(10, 20, 34)), 10, Color.rgb(48, 92, 120)))
            setSelection(options.indexOf(prefs.getString("sound_style", "Alarm")).takeIf { it >= 0 } ?: 1)
        }
    }

    private fun saveSettings() {
        fun read(field: EditText, fallback: Int): Int =
            field.text.toString().trim().toIntOrNull()?.coerceIn(0, 3600) ?: fallback
        prefs.edit()
            .putInt("interval", read(normalLength, 60).coerceAtLeast(5))
            .putInt("wave40", read(wave40Length, 90))
            .putInt("wave60", read(wave60Length, 90))
            .putString("reminders", reminderRows.map { row ->
                "${row.wave.selectedItemPosition + 1}|${if (row.unit.selectedItem == "Custom unit") row.custom.text.toString().trim().ifBlank { "Custom unit" } else row.unit.selectedItem}|${row.action.selectedItem}|${row.lead.selectedItemPosition}"
            }.joinToString("\n"))
            .putBoolean("sound", sound.isChecked)
            .putString("sound_style", soundStyle.selectedItem?.toString() ?: "Alarm")
            .putBoolean("vibration", vibration.isChecked)
            .apply()
    }

    private fun addReminderRow(waveValue: Int, unitValue: String, actionValue: String, leadValue: Int) {
        val rowCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 9, 10, 10)
            background = gradient(intArrayOf(Color.rgb(12, 22, 37), Color.rgb(12, 22, 37)), 12, Color.rgb(42, 66, 87))
        }
        fun spinner(items: List<String>, selected: String): Spinner {
            val view = Spinner(this)
            view.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
                private fun styled(position: Int, convertView: View?, dropdown: Boolean): View {
                    val label = (convertView as? TextView) ?: TextView(this@MainActivity)
                    label.text = items[position]
                    label.textSize = 12f
                    label.setTextColor(Color.WHITE)
                    label.gravity = Gravity.CENTER_VERTICAL
                    label.setPadding(12, 8, 12, 8)
                    label.background = gradient(intArrayOf(
                        if (dropdown) Color.rgb(24, 43, 64) else Color.rgb(12, 24, 40),
                        if (dropdown) Color.rgb(17, 32, 50) else Color.rgb(12, 24, 40)
                    ), 6, Color.rgb(48, 92, 120))
                    return label
                }
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = styled(position, convertView, false)
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = styled(position, convertView, true)
            }
            view.setPopupBackgroundDrawable(gradient(intArrayOf(Color.rgb(17, 32, 50), Color.rgb(10, 20, 34)), 10, Color.rgb(48, 92, 120)))
            view.minimumHeight = (54 * resources.displayMetrics.density).toInt()
            view.setPadding(0, 0, 0, 0)
            view.setSelection(items.indexOf(selected).takeIf { it >= 0 } ?: 0)
            return view
        }
        val wave = spinner((1..100).map { "Wave $it" }, "Wave ${waveValue.coerceIn(1,100)}")
        val units = listOf("Blue Dragon", "Ivy Whisper", "Witch Doctor", "Custom unit")
        val unit = spinner(units, if (unitValue in units) unitValue else "Custom unit")
        val actions = listOf("Liberate", "Place", "Activate")
        val normalizedAction = when (actionValue.uppercase()) {
            "LIB", "LIBERATE" -> "Liberate"
            "PLACE" -> "Place"
            else -> "Activate"
        }
        val action = spinner(actions, normalizedAction)
        val leads = (0..10).map { if (it == 0) "At target wave" else "$it wave${if (it == 1) "" else "s"} early" }
        val lead = spinner(leads, leads[leadValue.coerceIn(0,10)])
        val custom = EditText(this).apply {
            hint = "Custom unit name (optional)"
            setText(if (unitValue !in units) unitValue else "")
            setTextColor(Color.WHITE); setHintTextColor(muted); textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
            setSingleLine(true)
            setPadding(12, 10, 12, 10)
            background = gradient(intArrayOf(Color.rgb(19, 31, 48), Color.rgb(19, 31, 48)), 8, Color.rgb(42, 66, 87))
            visibility = if (unitValue == "Custom unit" || unitValue !in units) View.VISIBLE else View.GONE
        }
        unit.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                custom.visibility = if (units[position] == "Custom unit") View.VISIBLE else View.GONE
            }
        }
        rowCard.addView(TextView(this).apply {
            text = "TARGET WAVE"; setTextColor(cyan); textSize = 10f; typeface = Typeface.DEFAULT_BOLD
        })
        rowCard.addView(wave, LinearLayout.LayoutParams(-1, 56))
        rowCard.addView(TextView(this).apply {
            text = "UNIT"; setTextColor(muted); textSize = 10f
        })
        rowCard.addView(unit, LinearLayout.LayoutParams(-1, 56))
        rowCard.addView(custom, LinearLayout.LayoutParams(-1, 50))
        rowCard.addView(TextView(this).apply {
            text = "ACTION"; setTextColor(muted); textSize = 10f
        })
        rowCard.addView(action, LinearLayout.LayoutParams(-1, 56))
        rowCard.addView(TextView(this).apply {
            text = "EARLY WARNING"; setTextColor(muted); textSize = 10f
        })
        rowCard.addView(lead, LinearLayout.LayoutParams(-1, 56))
        rowCard.addView(actionButton("REMOVE ALERT", false) {
            reminderRows.removeAll { it.wave === wave }
            reminderList.removeView(rowCard)
        }, LinearLayout.LayoutParams(-1, 38).apply { topMargin = 5 })
        reminderRows.add(ReminderRow(wave, unit, action, lead, custom))
        reminderList.addView(rowCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 })
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
        pendingCalibration = calibrate
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
                putExtra(OverlayService.EXTRA_CALIBRATE, pendingCalibration)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            Toast.makeText(this, "Screen sync starting…", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
        } else if (requestCode == REQUEST_CAPTURE) {
            Toast.makeText(this, "Screen sync permission was not granted", Toast.LENGTH_SHORT).show()
        }
    }
}
