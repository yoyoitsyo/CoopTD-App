package com.yoyoitsyo.cooptdwall

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Original, lightweight CoopTD-inspired fantasy artwork drawn locally.
 * It keeps the UI visually rich without relying on remote images or copyrighted game files.
 */
class CoopArtView @JvmOverloads constructor(
    context: Context,
    var artType: String = "banner",
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val density = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val save = canvas.save()
        canvas.scale(w / 300f, h / 150f)
        drawBackdrop(canvas)
        if (artType.equals("banner", true) || artType.equals("badge", true)) {
            drawCastle(canvas)
            drawDragon(canvas, if (artType.equals("badge", true)) 1.05f else 1f)
            if (artType.equals("banner", true)) drawTitleEmblem(canvas)
        } else {
            when {
                artType.contains("dragon", true) -> drawDragon(canvas, 1.08f)
                artType.contains("ivy", true) -> drawIvy(canvas)
                artType.contains("witch", true) -> drawWitch(canvas)
                else -> drawKnight(canvas)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 1f): Paint {
        p.color = color
        p.style = style
        p.strokeWidth = stroke
        p.shader = null
        return p
    }

    private fun drawBackdrop(c: Canvas) {
        p.shader = LinearGradient(0f, 0f, 300f, 150f,
            intArrayOf(Color.rgb(10, 22, 43), Color.rgb(21, 52, 73), Color.rgb(31, 38, 61)),
            null, Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        c.drawRoundRect(RectF(0f, 0f, 300f, 150f), 16f, 16f, p)
        p.shader = null
        c.drawCircle(246f, 30f, 21f, paint(Color.rgb(255, 210, 120)))
        c.drawCircle(252f, 26f, 18f, paint(Color.rgb(255, 236, 185)))
        path.reset()
        path.moveTo(0f, 112f); path.lineTo(48f, 50f); path.lineTo(91f, 112f)
        path.lineTo(143f, 42f); path.lineTo(205f, 115f); path.lineTo(260f, 62f)
        path.lineTo(300f, 110f); path.lineTo(300f, 150f); path.lineTo(0f, 150f); path.close()
        c.drawPath(path, paint(Color.rgb(28, 55, 72)))
        path.reset()
        path.moveTo(0f, 130f); path.lineTo(72f, 87f); path.lineTo(125f, 134f)
        path.lineTo(205f, 83f); path.lineTo(300f, 128f); path.lineTo(300f, 150f)
        path.lineTo(0f, 150f); path.close()
        c.drawPath(path, paint(Color.rgb(18, 40, 54)))
        c.drawRect(0f, 137f, 300f, 150f, paint(Color.rgb(11, 27, 39)))
        for (i in 0..7) {
            val x = 14f + i * 38f
            c.drawCircle(x, 128f + (i % 3) * 4f, 1.4f, paint(Color.rgb(255, 190, 85)))
        }
    }

    private fun drawCastle(c: Canvas) {
        val stone = paint(Color.rgb(45, 63, 76))
        c.drawRect(22f, 84f, 72f, 137f, stone)
        c.drawRect(30f, 70f, 47f, 137f, paint(Color.rgb(58, 76, 87)))
        c.drawRect(55f, 76f, 68f, 137f, paint(Color.rgb(58, 76, 87)))
        c.drawRect(26f, 77f, 35f, 88f, paint(Color.rgb(12, 27, 40)))
        c.drawRect(44f, 91f, 52f, 101f, paint(Color.rgb(255, 188, 75)))
        c.drawRect(59f, 85f, 65f, 96f, paint(Color.rgb(255, 188, 75)))
        c.drawRect(20f, 82f, 74f, 87f, paint(Color.rgb(82, 99, 108)))
        for (x in 24..68 step 11) c.drawRect(x.toFloat(), 76f, x + 6f, 82f, paint(Color.rgb(82, 99, 108)))
    }

    private fun drawDragon(c: Canvas, scale: Float) {
        val save = c.save()
        c.translate(174f, 76f)
        c.scale(scale, scale)
        // Wing silhouette
        path.reset()
        path.moveTo(-25f, 5f); path.lineTo(-65f, -39f); path.lineTo(-55f, 8f)
        path.lineTo(-87f, -8f); path.lineTo(-57f, 35f); path.lineTo(-20f, 40f); path.close()
        c.drawPath(path, paint(Color.rgb(17, 90, 102)))
        path.reset()
        path.moveTo(10f, 10f); path.lineTo(47f, -34f); path.lineTo(42f, 11f)
        path.lineTo(70f, -4f); path.lineTo(48f, 35f); path.lineTo(10f, 42f); path.close()
        c.drawPath(path, paint(Color.rgb(23, 111, 119)))
        // Neck and head
        path.reset()
        path.moveTo(-27f, 41f); path.lineTo(-18f, 9f); path.lineTo(5f, -5f)
        path.lineTo(42f, -2f); path.lineTo(55f, 15f); path.lineTo(39f, 31f)
        path.lineTo(19f, 35f); path.lineTo(8f, 56f); path.lineTo(-10f, 52f); path.close()
        c.drawPath(path, paint(Color.rgb(35, 157, 151)))
        path.reset()
        path.moveTo(-9f, 2f); path.lineTo(-24f, -31f); path.lineTo(2f, -16f)
        path.lineTo(14f, -38f); path.lineTo(23f, -11f); path.close()
        c.drawPath(path, paint(Color.rgb(40, 187, 169)))
        c.drawOval(RectF(4f, -4f, 53f, 28f), paint(Color.rgb(45, 173, 162)))
        path.reset()
        path.moveTo(19f, -3f); path.lineTo(33f, -30f); path.lineTo(35f, -2f)
        path.close()
        c.drawPath(path, paint(Color.rgb(16, 94, 102)))
        c.drawOval(RectF(29f, 8f, 38f, 17f), paint(Color.rgb(255, 218, 98)))
        c.drawCircle(34f, 12f, 2.8f, paint(Color.rgb(24, 34, 39)))
        path.reset()
        path.moveTo(47f, 19f); path.lineTo(69f, 27f); path.lineTo(45f, 34f); path.close()
        c.drawPath(path, paint(Color.rgb(12, 68, 79)))
        c.drawLine(-5f, 43f, 14f, 47f, paint(Color.rgb(12, 89, 95), Paint.Style.STROKE, 3f))
        c.restoreToCount(save)
    }

    private fun drawIvy(c: Canvas) {
        c.drawCircle(150f, 77f, 46f, paint(Color.rgb(20, 68, 58)))
        c.drawRoundRect(RectF(117f, 46f, 183f, 128f), 22f, 22f, paint(Color.rgb(48, 142, 88)))
        for (i in 0..4) {
            val x = 120f + i * 17f
            path.reset(); path.moveTo(x, 84f); path.quadTo(x - 16f, 53f, x + 2f, 42f)
            path.quadTo(x + 23f, 65f, x, 84f); path.close()
            c.drawPath(path, paint(if (i % 2 == 0) Color.rgb(112, 202, 94) else Color.rgb(48, 169, 103)))
        }
        c.drawOval(RectF(130f, 66f, 170f, 101f), paint(Color.rgb(227, 196, 127)))
        c.drawCircle(141f, 81f, 4f, paint(Color.rgb(36, 50, 40)))
        c.drawCircle(159f, 81f, 4f, paint(Color.rgb(36, 50, 40)))
        c.drawArc(RectF(140f, 82f, 160f, 96f), 10f, 160f, false, paint(Color.rgb(70, 54, 40), Paint.Style.STROKE, 2f))
        c.drawLine(150f, 65f, 150f, 42f, paint(Color.rgb(255, 210, 104), Paint.Style.STROKE, 3f))
    }

    private fun drawWitch(c: Canvas) {
        c.drawCircle(150f, 79f, 48f, paint(Color.rgb(51, 37, 86)))
        path.reset(); path.moveTo(104f, 70f); path.lineTo(150f, 17f); path.lineTo(197f, 70f)
        path.lineTo(177f, 63f); path.lineTo(124f, 63f); path.close()
        c.drawPath(path, paint(Color.rgb(90, 60, 139)))
        c.drawOval(RectF(124f, 58f, 176f, 111f), paint(Color.rgb(224, 190, 133)))
        c.drawRoundRect(RectF(120f, 76f, 180f, 93f), 7f, 7f, paint(Color.rgb(35, 28, 48)))
        c.drawCircle(138f, 84f, 4f, paint(Color.rgb(117, 235, 231)))
        c.drawCircle(162f, 84f, 4f, paint(Color.rgb(117, 235, 231)))
        c.drawRect(132f, 97f, 168f, 109f, paint(Color.rgb(48, 35, 70)))
        c.drawCircle(150f, 121f, 7f, paint(Color.rgb(255, 198, 88)))
        c.drawLine(150f, 121f, 150f, 140f, paint(Color.rgb(255, 198, 88), Paint.Style.STROKE, 3f))
    }

    private fun drawKnight(c: Canvas) {
        c.drawCircle(150f, 78f, 47f, paint(Color.rgb(42, 58, 76)))
        c.drawRoundRect(RectF(119f, 48f, 181f, 119f), 18f, 18f, paint(Color.rgb(115, 130, 143)))
        c.drawRoundRect(RectF(127f, 62f, 173f, 95f), 12f, 12f, paint(Color.rgb(29, 45, 60)))
        c.drawRect(132f, 76f, 168f, 85f, paint(Color.rgb(255, 196, 85)))
        c.drawRect(146f, 48f, 154f, 119f, paint(Color.rgb(67, 82, 95)))
        c.drawCircle(150f, 122f, 10f, paint(Color.rgb(204, 149, 66)))
    }

    private fun drawTitleEmblem(c: Canvas) {
        c.drawRoundRect(RectF(14f, 14f, 92f, 35f), 8f, 8f, paint(Color.argb(210, 9, 20, 34)))
        p.color = Color.rgb(255, 210, 125)
        p.textSize = 10f
        p.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        c.drawText("BATTLE READY", 23f, 28f, p)
    }
}
