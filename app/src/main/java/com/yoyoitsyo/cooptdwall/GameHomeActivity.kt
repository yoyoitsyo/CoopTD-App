package com.yoyoitsyo.cooptdwall

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.min

/**
 * New reference-driven home screen. The legacy settings screen remains available
 * as the settings editor while the visual home and floating HUD are rebuilt.
 */
class GameHomeActivity : Activity() {
    companion object { private const val REQUEST_CAPTURE = 73; private const val REQUEST_ARTWORK = 74 }
    private lateinit var screen: CoopTDHomeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(17, 19, 49)
        window.navigationBarColor = Color.rgb(9, 12, 28)
        window.decorView.systemUiVisibility = 0
        screen = CoopTDHomeView(this) { action ->
            when (action) {
                "settings", "add", "edit", "timer-settings" -> openLegacySettings()
                "artwork" -> requestReferenceArtwork()
                "start-timer" -> startFloatingTimer()
                "auto-sync" -> requestAutoSync()
                "remove" -> openLegacySettings()
            }
        }
        ReferenceArt.load(this)?.let { screen.setReferenceArt(it) }
        setContentView(screen)
    }

    override fun onResume() {
        super.onResume()
        if (::screen.isInitialized) screen.invalidate()
    }

    private fun openLegacySettings() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun requestReferenceArtwork() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(pick, REQUEST_ARTWORK)
    }

    private fun requestAutoSync() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow floating window permission, then tap Auto Sync again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Handled for the Android screen-capture permission flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ARTWORK) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                getSharedPreferences("wall", MODE_PRIVATE).edit()
                    .putString(ReferenceArt.PREF_URI, uri.toString()).apply()
                val bitmap = ReferenceArt.decodeUri(this, uri)
                if (bitmap != null) {
                    screen.setReferenceArt(bitmap)
                    Toast.makeText(this, "Reference artwork loaded. Your live timer stays active.", Toast.LENGTH_LONG).show()
                } else Toast.makeText(this, "Could not read that image.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Screen capture was cancelled.", Toast.LENGTH_SHORT).show()
            return
        }
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START_SCAN
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, data)
            putExtra(OverlayService.EXTRA_CALIBRATE, false)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
    }

    private fun startFloatingTimer() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow floating window permission, then tap timer again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        try {
            val serviceIntent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            Toast.makeText(this, "Floating timer started", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't start floating timer. Open settings and try again.", Toast.LENGTH_LONG).show()
        }
    }
}

private class CoopTDHomeView(
    context: android.content.Context,
    private val onAction: (String) -> Unit
) : View(context) {
    private val prefs = context.getSharedPreferences("wall", Activity.MODE_PRIVATE)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val gold = Color.rgb(255, 198, 58)
    private val brightGold = Color.rgb(255, 224, 112)
    private val navy = Color.rgb(13, 19, 43)
    private val ink = Color.rgb(8, 10, 22)
    private val white = Color.rgb(248, 248, 255)
    private var sx = 1f
    private var sy = 1f
    private var referenceArt: Bitmap? = null
    fun setReferenceArt(bitmap: Bitmap) { referenceArt = bitmap; invalidate() }
    private var rows = mutableListOf<Reminder>()
    private data class Reminder(val wave: Int, val unit: String, val action: String, val lead: Int)

    private fun readRows() {
        rows = prefs.getString("reminders", "").orEmpty().lines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val modern = line.split("|", limit = 4)
            if (modern.size >= 4) Reminder(
                modern[0].toIntOrNull() ?: return@mapNotNull null,
                modern[1], modern[2], modern[3].toIntOrNull() ?: 0
            ) else {
                val old = line.split(",", limit = 3)
                if (old.size >= 3) Reminder(old[0].trim().toIntOrNull() ?: return@mapNotNull null,
                    old[1].trim(), old[2].trim(), 0) else null
            }
        }.sortedBy { it.wave }.toMutableList()
    }

    private fun saveRows() {
        prefs.edit().putString("reminders", rows.sortedBy { it.wave }.joinToString("\n") {
            "${it.wave}|${it.unit}|${it.action}|${it.lead}"
        }).apply()
        invalidate()
    }
    private fun addAlert() {
        val nextWave = ((rows.maxOfOrNull { it.wave } ?: 67) + 5).coerceAtMost(999)
        rows.add(Reminder(nextWave, "Blue Dragon", "PLACE", 0))
        saveRows()
    }
    private fun removeAlert(index: Int? = null) {
        if (rows.isEmpty()) {
            Toast.makeText(context, "No reminders to remove.", Toast.LENGTH_SHORT).show()
            return
        }
        if (index != null && index in rows.indices) rows.removeAt(index) else rows.removeAt(rows.lastIndex)
        saveRows()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        readRows()
        sx = width / 420f
        sy = height / 900f
        val save = canvas.save()
        canvas.scale(sx, sy)
        drawBackdrop(canvas)
        drawHeader(canvas)
        drawHud(canvas)
        drawReminders(canvas)
        drawBottomDock(canvas)
        canvas.restoreToCount(save)
    }

    private fun fill(color: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 1f): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = style
            this.strokeWidth = stroke
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

    private fun round(c: Canvas, rect: RectF, radius: Float, color: Int, edge: Int? = null, edgeWidth: Float = 2f) {
        c.drawRoundRect(rect, radius, radius, fill(color))
        if (edge != null) c.drawRoundRect(rect, radius, radius, fill(edge, Paint.Style.STROKE, edgeWidth))
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int,
                     bold: Boolean = true, align: Paint.Align = Paint.Align.LEFT, shadow: Boolean = true) {
        val paint = fill(color).apply {
            textSize = size
            textAlign = align
            typeface = Typeface.create(if (bold) "sans-serif-black" else "sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
            if (shadow) setShadowLayer(2.5f, 1.5f, 2f, Color.BLACK)
        }
        c.drawText(value, x, y, paint)
    }

    private fun drawBackdrop(c: Canvas) {
        referenceArt?.let { art ->
            ReferenceArt.drawCrop(c, art, RectF(710f, 465f, 1213f, 1296f), RectF(0f, 0f, 420f, 900f),
                fill(Color.WHITE).apply { isFilterBitmap = true })
            c.drawRect(0f, 0f, 420f, 900f, fill(Color.argb(112, 4, 7, 22)))
            c.drawRect(0f, 0f, 235f, 900f, fill(Color.argb(152, 5, 9, 25)))
            return
        }
        p.shader = LinearGradient(0f, 0f, 420f, 900f,
            intArrayOf(Color.rgb(42, 35, 112), Color.rgb(25, 31, 83), Color.rgb(9, 13, 31)),
            null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 420f, 900f, p)
        p.shader = null
        c.drawCircle(330f, 145f, 105f, fill(Color.argb(45, 184, 155, 255)))
        c.drawCircle(330f, 145f, 73f, fill(Color.argb(60, 213, 205, 255)))
        for (i in 0..45) {
            val x = ((i * 83 + 19) % 417).toFloat()
            val y = ((i * 47 + 27) % 610).toFloat()
            c.drawCircle(x, y, if (i % 4 == 0) 1.8f else 1f, fill(Color.argb(180, 218, 223, 255)))
        }
        // Layered castle silhouettes, warm windows and distant rooftops.
        val towers = listOf(
            floatArrayOf(-5f, 195f, 54f), floatArrayOf(52f, 260f, 43f),
            floatArrayOf(96f, 185f, 55f), floatArrayOf(160f, 230f, 48f),
            floatArrayOf(217f, 160f, 59f), floatArrayOf(283f, 222f, 44f),
            floatArrayOf(334f, 178f, 54f), floatArrayOf(383f, 260f, 50f)
        )
        towers.forEachIndexed { i, t ->
            val x=t[0]; val top=t[1]; val w=t[2]
            val base=if(i%2==0) Color.rgb(27,25,72) else Color.rgb(35,31,91)
            c.drawRect(x, top, x+w, 720f, fill(base))
            path.reset(); path.moveTo(x-10,top+4); path.lineTo(x+w/2,top-43); path.lineTo(x+w+10,top+4); path.close()
            c.drawPath(path,fill(Color.rgb(18,17,56)))
            c.drawPath(path,fill(Color.rgb(60,48,124),Paint.Style.STROKE,3f))
            for (j in 0..2) c.drawRoundRect(x+9+j*13,top+28,x+15+j*13,top+52,5f,5f,fill(Color.rgb(255,164,87)))
        }
        path.reset(); path.moveTo(0f,570f); path.lineTo(72f,500f); path.lineTo(142f,552f)
        path.lineTo(222f,470f); path.lineTo(301f,552f); path.lineTo(369f,490f); path.lineTo(420f,530f)
        path.lineTo(420f,900f); path.lineTo(0f,900f); path.close()
        c.drawPath(path,fill(Color.rgb(15,24,54)))
        drawHero(c, 318f, 710f, 1.05f)
        c.drawRect(0f, 0f, 420f, 900f, fill(Color.argb(28, 3, 5, 18)))
    }

    private fun drawHeader(c: Canvas) {
        // Gear/settings button
        round(c, RectF(14f,14f,61f,61f),10f,Color.rgb(20,32,68),Color.rgb(147,190,255),3f)
        text(c,"⚙",37.5f,47f,31f,white,true,Paint.Align.CENTER)
        // Use the exact logo artwork from the supplied reference when it has been imported.
        if (referenceArt != null) {
            ReferenceArt.drawCrop(c, referenceArt, RectF(62f, 8f, 480f, 260f), RectF(68f, 8f, 300f, 126f),
                fill(Color.WHITE).apply { isFilterBitmap = true })
        } else {
            text(c,"COOP",76f,66f,43f,Color.rgb(255,196,38))
            text(c,"TD",190f,66f,43f,Color.rgb(242,53,45))
            text(c,"WALL ASSISTANT",78f,98f,23f,white)
            text(c,"WAVE TIMER  •  REMINDER CONTROL",79f,119f,9.5f,Color.rgb(195,209,255),true)
        }
        // tiny status icons
        round(c, RectF(324f,24f,404f,51f),13f,Color.rgb(8,24,40),Color.rgb(81,206,118),1.5f)
        c.drawCircle(339f,37f,6f,fill(Color.rgb(65,235,99)))
        text(c,"ART",350f,41f,10f,white)
    }

    private fun drawHud(c: Canvas) {
        val r=RectF(14f,132f,406f,286f)
        round(c,r,18f,Color.rgb(11,17,35),Color.rgb(255,197,72),4f)
        // inner bevel and top highlight
        round(c,RectF(20f,138f,400f,280f),14f,Color.rgb(19,31,63),Color.rgb(106,134,205),1.5f)
        if (referenceArt != null) {
            ReferenceArt.drawCrop(c, referenceArt, RectF(625f, 82f, 815f, 450f), RectF(22f, 140f, 110f, 274f),
                fill(Color.WHITE).apply { isFilterBitmap = true })
        } else drawKnight(c,62f,208f,.78f)
        text(c,"WAVE",142f,177f,15f,Color.rgb(186,210,255))
        text(c,"12",210f,179f,42f,white)
        text(c,"GAME TIMER",143f,207f,13f,white)
        text(c,"00:37",226f,246f,36f,gold)
        // Green OCR lock strip
        round(c,RectF(132f,254f,387f,274f),10f,Color.rgb(8,92,31),Color.rgb(101,255,105),2f)
        c.drawCircle(148f,264f,5f,fill(Color.rgb(47,244,77)))
        text(c,"OCR SYNC  •  LOCKED ON WAVE",159f,269f,10f,white)
    }

    private fun drawReminders(c: Canvas) {
        // red, beveled header
        round(c,RectF(14f,300f,406f,345f),10f,Color.rgb(126,22,35),Color.rgb(255,191,67),3f)
        if (referenceArt != null) {
            ReferenceArt.drawCrop(c, referenceArt, RectF(18f, 260f, 570f, 355f), RectF(14f, 292f, 406f, 350f),
                fill(Color.WHITE).apply { isFilterBitmap = true })
        } else text(c,"WAVE REMINDERS",28f,329f,20f,Color.rgb(255,216,71))
        val shown=rows.take(5)
        val start=354f
        val rowH=83f
        if (shown.isEmpty()) {
            round(c,RectF(14f,start,406f,start+rowH),12f,Color.rgb(16,23,43),Color.rgb(174,142,75),2f)
            text(c,"NO ALERTS YET",32f,start+34f,17f,white)
            text(c,"Tap + ADD ALERT to create one",32f,start+57f,12f,Color.rgb(185,198,229),false)
        }
        shown.forEachIndexed { index, row ->
            val top=start+index*rowH
            if (referenceArt != null) {
                // Reuse the actual ornate card edge from the supplied design, then mask its sample text.
                ReferenceArt.drawCrop(c,referenceArt,RectF(25f,350f,645f,490f),RectF(14f,top,406f,top+77f),
                    fill(Color.WHITE).apply { isFilterBitmap=true })
                round(c,RectF(94f,top+7f,306f,top+70f),7f,Color.rgb(12,19,38))
                round(c,RectF(278f,top+7f,401f,top+70f),7f,Color.rgb(12,19,38))
            } else {
                round(c,RectF(14f,top,406f,top+77f),12f,Color.rgb(12,19,38),Color.rgb(191,151,73),2f)
                round(c,RectF(19f,top+5f,88f,top+72f),10f,Color.rgb(25,38,78),Color.rgb(72,113,206),2f)
            }
            if (referenceArt != null) {
                ReferenceArt.drawCrop(c, referenceArt, portraitSource(row.unit), RectF(19f,top+5f,88f,top+72f),
                    fill(Color.WHITE).apply { isFilterBitmap = true })
            } else drawUnit(c,row.unit,53f,top+39f,.50f)
            text(c,"WAVE ${row.wave}",99f,top+29f,20f,white)
            val unitColor=when(row.unit.lowercase()) {
                "blue dragon" -> Color.rgb(80,185,255)
                "ivy whisper" -> Color.rgb(117,246,113)
                "witch doctor" -> Color.rgb(205,111,255)
                else -> Color.rgb(255,167,65)
            }
            text(c,row.unit.uppercase().take(17),99f,top+52f,14f,unitColor)
            text(c,if(row.lead>0) "◷ ${row.lead} WAVES EARLY" else "◷ ON TARGET WAVE",100f,top+69f,10f,white,false)
            // speaker and remove controls
            if (referenceArt != null) {
                ReferenceArt.drawCrop(c, referenceArt, RectF(450f, 375f, 530f, 445f), RectF(310f,top+13f,356f,top+61f),
                    fill(Color.WHITE).apply { isFilterBitmap = true })
                ReferenceArt.drawCrop(c, referenceArt, RectF(540f, 375f, 620f, 445f), RectF(360f,top+13f,401f,top+61f),
                    fill(Color.WHITE).apply { isFilterBitmap = true })
            } else {
                round(c,RectF(330f,top+13f,365f,top+61f),8f,Color.rgb(22,48,101),Color.rgb(150,190,255),2f)
                text(c,"◖))",347f,top+43f,17f,gold,true,Paint.Align.CENTER)
                round(c,RectF(370f,top+13f,401f,top+61f),8f,Color.rgb(91,18,31),Color.rgb(255,130,112),2f)
                text(c,"×",385.5f,top+46f,31f,Color.rgb(255,76,66),true,Paint.Align.CENTER)
            }
        }
        if (rows.size>5) text(c,"+${rows.size-5} more alerts in settings",24f,775f,10f,white,false)
    }

    private fun portraitSource(unit: String): RectF {
        val name=unit.lowercase()
        return when {
            "reaper" in name -> RectF(38f,355f,218f,480f)
            "orc" in name -> RectF(38f,505f,218f,630f)
            "stone" in name || "giant" in name -> RectF(38f,675f,218f,775f)
            "red dragon" in name || "dragon" in name -> RectF(38f,800f,218f,915f)
            "void" in name || "knight" in name -> RectF(38f,935f,218f,1050f)
            else -> RectF(28f,495f,232f,640f)
        }
    }

    private fun drawBottomDock(c: Canvas) {
        referenceArt?.let { art ->
            val source = listOf(
                RectF(30f,1070f,192f,1205f), RectF(193f,1070f,348f,1205f),
                RectF(348f,1068f,550f,1207f), RectF(550f,1070f,720f,1205f)
            )
            val gap=6f
            val w=(392f-gap*3)/4f
            for(i in 0..3) {
                val x=14f+i*(w+gap)
                ReferenceArt.drawCrop(c,art,source[i],RectF(x,796f,x+w,883f),
                    fill(Color.WHITE).apply { isFilterBitmap=true })
            }
            text(c,"COOP TOGETHER  •  DEFEND FOREVER",210f,897f,10f,Color.rgb(255,190,82),true,Paint.Align.CENTER)
            return
        }
        // Four large game-like deck buttons anchored at bottom.
        val y=796f
        val gap=6f
        val w=(392f-gap*3)/4f
        val labels=listOf("＋\nADD\nALERT","－\nREMOVE\nALERT","✓\nAUTO SYNC","◷\nTIMER\nSETTINGS")
        val colors=listOf(Color.rgb(27,81,170),Color.rgb(27,81,170),Color.rgb(95,28,147),Color.rgb(27,81,170))
        for(i in 0..3) {
            val x=14f+i*(w+gap)
            round(c,RectF(x,y,x+w,883f),11f,colors[i],if(i==2) Color.rgb(214,105,255) else gold,3f)
            round(c,RectF(x+4,y+4,x+w-4,879f),8f,Color.argb(55,8,10,28),Color.argb(110,160,191,255),1f)
            val parts=labels[i].split("\n")
            parts.forEachIndexed { line, value ->
                text(c,value,x+w/2,y+28f+line*18f,if(line==0) 24f else 12f,
                    if(line==0) brightGold else white,true,Paint.Align.CENTER)
            }
            if(i==2) text(c,"OCR ACTIVE",x+w/2,867f,9f,Color.rgb(109,255,83),true,Paint.Align.CENTER)
        }
        text(c,"COOP TOGETHER  •  DEFEND FOREVER",210f,897f,10f,Color.rgb(255,190,82),true,Paint.Align.CENTER)
    }

    private fun drawHero(c: Canvas,x:Float,y:Float,scale:Float) {
        val save=c.save(); c.translate(x,y); c.scale(scale,scale)
        // red hood mage in rear
        path.reset(); path.moveTo(-55f,-70f); path.quadTo(-10f,-135f,45f,-78f); path.lineTo(74f,10f)
        path.quadTo(0f,60f,-65f,18f); path.close()
        c.drawPath(path,fill(ink,Paint.Style.STROKE,7f)); c.drawPath(path,fill(Color.rgb(156,29,50)))
        path.reset(); path.moveTo(-58f,-65f); path.lineTo(0f,-125f); path.lineTo(55f,-65f); path.lineTo(25f,-42f); path.lineTo(-30f,-42f); path.close()
        c.drawPath(path,fill(Color.rgb(215,42,58),Paint.Style.STROKE,5f)); c.drawPath(path,fill(Color.rgb(205,40,54)))
        c.drawOval(RectF(-25f,-57f,27f,-9f),fill(Color.rgb(238,181,126)))
        c.drawOval(RectF(-17f,-39f,-6f,-27f),fill(Color.rgb(255,218,62)))
        c.drawOval(RectF(7f,-39f,18f,-27f),fill(Color.rgb(255,218,62)))
        // blue armored knight
        c.drawRoundRect(RectF(-70f,-10f,18f,90f),25f,25f,fill(ink,Paint.Style.STROKE,9f))
        c.drawRoundRect(RectF(-70f,-10f,18f,90f),25f,25f,fill(Color.rgb(36,84,165)))
        c.drawRoundRect(RectF(-57f,5f,8f,71f),14f,14f,fill(Color.rgb(58,124,225),Paint.Style.STROKE,4f))
        c.drawRoundRect(RectF(-53f,10f,4f,69f),12f,12f,fill(Color.rgb(43,98,194)))
        // helmet
        c.drawOval(RectF(-66f,-69f,19f,5f),fill(ink,Paint.Style.STROKE,8f))
        c.drawOval(RectF(-64f,-71f,17f,3f),fill(Color.rgb(147,173,222)))
        path.reset(); path.moveTo(-51f,-55f); path.lineTo(-22f,-92f); path.lineTo(10f,-53f); path.lineTo(0f,-18f); path.lineTo(-45f,-18f); path.close()
        c.drawPath(path,fill(ink,Paint.Style.STROKE,7f)); c.drawPath(path,fill(Color.rgb(67,103,175)))
        c.drawRoundRect(RectF(-43f,-44f,3f,-30f),5f,5f,fill(Color.rgb(13,25,55)))
        c.drawCircle(-28f,-37f,4f,fill(Color.rgb(70,235,255))); c.drawCircle(-10f,-37f,4f,fill(Color.rgb(70,235,255)))
        c.restoreToCount(save)
    }

    private fun drawKnight(c:Canvas,x:Float,y:Float,scale:Float) {
        val save=c.save(); c.translate(x,y); c.scale(scale,scale)
        c.drawOval(RectF(-43f,-48f,44f,57f),fill(ink,Paint.Style.STROKE,7f))
        c.drawOval(RectF(-41f,-46f,42f,55f),fill(Color.rgb(48,90,166)))
        c.drawOval(RectF(-31f,-36f,32f,30f),fill(Color.rgb(179,194,223),Paint.Style.STROKE,5f))
        c.drawOval(RectF(-29f,-35f,30f,28f),fill(Color.rgb(102,133,192)))
        path.reset(); path.moveTo(-38f,-20f); path.lineTo(-26f,-62f); path.lineTo(7f,-66f); path.lineTo(38f,-19f); path.lineTo(13f,-3f); path.lineTo(-27f,-3f); path.close()
        c.drawPath(path,fill(ink,Paint.Style.STROKE,7f)); c.drawPath(path,fill(Color.rgb(170,190,229)))
        c.drawRoundRect(RectF(-23f,-24f,23f,-11f),5f,5f,fill(Color.rgb(10,20,48)))
        c.drawCircle(-12f,-18f,4f,fill(Color.rgb(255,208,65))); c.drawCircle(12f,-18f,4f,fill(Color.rgb(255,208,65)))
        c.restoreToCount(save)
    }

    private fun drawUnit(c:Canvas,unit:String,x:Float,y:Float,scale:Float) {
        val save=c.save(); c.translate(x,y); c.scale(scale,scale)
        when(unit.lowercase()) {
            "blue dragon" -> {
                path.reset(); path.moveTo(-42f,24f); path.lineTo(-62f,-22f); path.lineTo(-24f,-11f)
                path.lineTo(-6f,-48f); path.lineTo(17f,-12f); path.lineTo(49f,-26f); path.lineTo(37f,27f); path.close()
                c.drawPath(path,fill(ink,Paint.Style.STROKE,6f)); c.drawPath(path,fill(Color.rgb(57,153,235)))
                c.drawCircle(-13f,5f,6f,fill(Color.WHITE)); c.drawCircle(14f,5f,6f,fill(Color.WHITE))
                c.drawCircle(-13f,5f,3f,fill(ink)); c.drawCircle(14f,5f,3f,fill(ink))
            }
            "ivy whisper" -> {
                c.drawOval(RectF(-43f,-38f,43f,40f),fill(ink,Paint.Style.STROKE,6f)); c.drawOval(RectF(-40f,-35f,40f,37f),fill(Color.rgb(81,195,88)))
                c.drawOval(RectF(-23f,-8f,-5f,12f),fill(Color.WHITE)); c.drawOval(RectF(5f,-8f,23f,12f),fill(Color.WHITE))
                c.drawCircle(-14f,3f,5f,fill(ink)); c.drawCircle(14f,3f,5f,fill(ink))
            }
            "witch doctor" -> {
                path.reset(); path.moveTo(-42f,-20f); path.lineTo(-56f,-52f); path.lineTo(-12f,-36f); path.lineTo(8f,-54f)
                path.lineTo(52f,-50f); path.lineTo(35f,-15f); path.lineTo(39f,28f); path.lineTo(-38f,28f); path.close()
                c.drawPath(path,fill(ink,Paint.Style.STROKE,6f)); c.drawPath(path,fill(Color.rgb(126,57,172)))
                c.drawCircle(-14f,3f,6f,fill(Color.rgb(75,236,255))); c.drawCircle(14f,3f,6f,fill(Color.rgb(75,236,255)))
            }
            else -> {
                c.drawRoundRect(RectF(-42f,-38f,42f,39f),14f,14f,fill(ink,Paint.Style.STROKE,6f))
                c.drawRoundRect(RectF(-39f,-35f,39f,36f),13f,13f,fill(Color.rgb(125,147,180)))
                c.drawCircle(-14f,0f,6f,fill(Color.rgb(255,222,81))); c.drawCircle(14f,0f,6f,fill(Color.rgb(255,222,81)))
            }
        }
        c.restoreToCount(save)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if(event.action != android.view.MotionEvent.ACTION_UP) return true
        val x=event.x/sx; val y=event.y/sy
        when {
            x<68f && y<70f -> onAction("settings")
            x>=320f && y<70f -> onAction("artwork")
            y in 796f..890f -> {
                val index=((x-14f)/99f).toInt().coerceIn(0,3)
                when(index) {
                    0 -> addAlert()
                    1 -> removeAlert()
                    2 -> onAction("auto-sync")
                    3 -> onAction("timer-settings")
                }
            }
            y in 350f..780f -> {
                val index=((y-354f)/83f).toInt()
                when {
                    x>=370f -> removeAlert(index)
                    x>=330f -> {
                        val enabled=!prefs.getBoolean("sound",true)
                        prefs.edit().putBoolean("sound",enabled).apply()
                        Toast.makeText(context,if(enabled) "Reminder sounds enabled" else "Reminder sounds muted",Toast.LENGTH_SHORT).show()
                        invalidate()
                    }
                    else -> onAction("edit")
                }
            }
            y in 130f..290f -> onAction("start-timer")
            else -> onAction("settings")
        }
        return true
    }
}
