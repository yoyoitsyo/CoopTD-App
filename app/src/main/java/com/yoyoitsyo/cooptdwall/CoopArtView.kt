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
 * Original CoopTD-inspired monster illustrations.
 * Bright, playful-creepy tower-defense art: chunky ink outlines, expressive faces,
 * saturated colors and magical glow. All artwork is drawn locally.
 */
class CoopArtView @JvmOverloads constructor(
    context: Context,
    var artType: String = "banner",
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val ink = Color.rgb(27, 22, 43)
    private val path = Path()

    private fun brush(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 3f): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = style
            strokeWidth = width
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val save = canvas.save()
        canvas.scale(width / 300f, height / 150f)
        backdrop(canvas)
        when {
            artType.equals("banner", true) -> {
                monster(canvas, "flame", 52f, 85f, .66f)
                monster(canvas, "slime", 119f, 91f, .72f)
                monster(canvas, "golem", 190f, 88f, .72f)
                monster(canvas, "spirit", 258f, 89f, .63f)
                label(canvas, "WAVE CREW")
            }
            artType.equals("badge", true) -> monster(canvas, "flame", 150f, 82f, .82f)
            artType.contains("dragon", true) -> monster(canvas, "flame", 150f, 82f, 1.12f)
            artType.contains("ivy", true) -> monster(canvas, "slime", 150f, 82f, 1.12f)
            artType.contains("witch", true) -> monster(canvas, "spirit", 150f, 82f, 1.12f)
            else -> monster(canvas, "golem", 150f, 82f, 1.12f)
        }
        canvas.restoreToCount(save)
    }

    private fun backdrop(c: Canvas) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 300f, 150f,
                intArrayOf(Color.rgb(42, 24, 75), Color.rgb(24, 55, 93), Color.rgb(15, 31, 58)),
                null, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RectF(0f, 0f, 300f, 150f), 17f, 17f, bg)
        c.drawCircle(252f, 27f, 25f, brush(Color.argb(45, 120, 225, 255)))
        c.drawCircle(252f, 27f, 13f, brush(Color.rgb(255, 209, 113)))
        for (i in 0..11) {
            val x = 12f + i * 25f
            c.drawCircle(x, 18f + (i % 3) * 13f, 1.8f, brush(Color.rgb(255, 226, 145)))
        }
        // Layered gothic skyline like the night-castle backdrop in the user's reference.
        val castleInk = Color.rgb(31, 29, 73)
        val castleEdge = Color.rgb(53, 47, 105)
        val towers = listOf(
            Triple(12f, 78f, 24f), Triple(45f, 58f, 28f),
            Triple(91f, 83f, 22f), Triple(132f, 47f, 34f),
            Triple(181f, 73f, 25f), Triple(218f, 55f, 28f),
            Triple(263f, 82f, 22f)
        )
        for ((x, top, w) in towers) {
            c.drawRect(x, top, x + w, 140f, brush(castleInk))
            c.drawRect(x + 3f, top + 5f, x + w - 3f, 140f, brush(castleEdge))
            path.reset()
            path.moveTo(x - 5f, top + 2f)
            path.lineTo(x + w / 2f, top - 20f)
            path.lineTo(x + w + 5f, top + 2f)
            path.close()
            c.drawPath(path, brush(castleInk))
            c.drawRect(x + w * .30f, top + 14f, x + w * .48f, top + 23f, brush(Color.rgb(255, 183, 91)))
            c.drawRect(x + w * .60f, top + 29f, x + w * .78f, top + 38f, brush(Color.rgb(255, 183, 91)))
        }
        path.reset()
        path.moveTo(0f, 122f); path.lineTo(48f, 96f); path.lineTo(91f, 119f)
        path.lineTo(148f, 91f); path.lineTo(208f, 119f); path.lineTo(263f, 96f)
        path.lineTo(300f, 116f); path.lineTo(300f, 150f); path.lineTo(0f, 150f); path.close()
        c.drawPath(path, brush(Color.rgb(20, 44, 66)))
        c.drawRect(0f, 137f, 300f, 150f, brush(Color.rgb(13, 28, 49)))
        c.drawRoundRect(RectF(2f, 2f, 298f, 148f), 16f, 16f, brush(Color.rgb(128, 91, 206), Paint.Style.STROKE, 3f))
    }

    private fun monster(c: Canvas, kind: String, x: Float, y: Float, scale: Float) {
        val save = c.save()
        c.translate(x, y)
        c.scale(scale, scale)
        val glow = when (kind) {
            "flame" -> Color.rgb(255, 111, 65)
            "slime" -> Color.rgb(102, 232, 132)
            "golem" -> Color.rgb(100, 185, 255)
            else -> Color.rgb(196, 117, 255)
        }
        c.drawCircle(0f, 0f, 48f, brush(Color.argb(65, Color.red(glow), Color.green(glow), Color.blue(glow))))
        when (kind) {
            "flame" -> {
                path.reset(); path.moveTo(-37f, 8f); path.quadTo(-58f, -25f, -28f, -49f)
                path.quadTo(-27f, -22f, -7f, -43f); path.quadTo(0f, -65f, 13f, -43f)
                path.quadTo(47f, -22f, 37f, 13f); path.quadTo(28f, 46f, 0f, 46f)
                path.quadTo(-35f, 43f, -37f, 8f); path.close()
                c.drawPath(path, brush(ink)); c.drawPath(path, brush(Color.rgb(255, 111, 57), Paint.Style.FILL))
                c.drawCircle(-12f, 5f, 6f, brush(Color.rgb(255, 238, 153)))
                c.drawCircle(13f, 5f, 6f, brush(Color.rgb(255, 238, 153)))
                c.drawCircle(-12f, 5f, 2.7f, brush(ink)); c.drawCircle(13f, 5f, 2.7f, brush(ink))
                c.drawArc(RectF(-12f, 9f, 13f, 28f), 15f, 150f, false, brush(ink, Paint.Style.STROKE, 3f))
                c.drawCircle(0f, -20f, 5f, brush(Color.rgb(255, 215, 91)))
            }
            "slime" -> {
                path.reset(); path.moveTo(-42f, 28f); path.quadTo(-48f, -5f, -25f, -31f)
                path.quadTo(-8f, -50f, 8f, -35f); path.quadTo(39f, -43f, 43f, -5f)
                path.quadTo(48f, 21f, 34f, 34f); path.quadTo(0f, 49f, -35f, 36f); path.close()
                c.drawPath(path, brush(ink)); c.drawPath(path, brush(Color.rgb(93, 220, 121)))
                c.drawOval(RectF(-24f, -5f, -7f, 15f), brush(Color.WHITE))
                c.drawOval(RectF(8f, -5f, 25f, 15f), brush(Color.WHITE))
                c.drawCircle(-15f, 5f, 5f, brush(ink)); c.drawCircle(17f, 5f, 5f, brush(ink))
                c.drawArc(RectF(-9f, 10f, 12f, 27f), 5f, 165f, false, brush(ink, Paint.Style.STROKE, 3f))
                c.drawCircle(-27f, 23f, 5f, brush(Color.rgb(255, 145, 155)))
                c.drawCircle(28f, 23f, 5f, brush(Color.rgb(255, 145, 155)))
            }
            "golem" -> {
                c.drawRoundRect(RectF(-39f, -35f, 39f, 39f), 15f, 15f, brush(ink))
                c.drawRoundRect(RectF(-35f, -39f, 35f, 35f), 14f, 14f, brush(Color.rgb(105, 145, 170)))
                path.reset(); path.moveTo(-30f, -31f); path.lineTo(-19f, -51f); path.lineTo(-7f, -34f)
                path.lineTo(12f, -49f); path.lineTo(30f, -28f); path.close()
                c.drawPath(path, brush(ink)); c.drawPath(path, brush(Color.rgb(117, 178, 198)))
                c.drawRoundRect(RectF(-23f, -8f, -5f, 10f), 5f, 5f, brush(Color.rgb(255, 224, 111)))
                c.drawRoundRect(RectF(5f, -8f, 23f, 10f), 5f, 5f, brush(Color.rgb(255, 224, 111)))
                c.drawCircle(-14f, 1f, 4f, brush(ink)); c.drawCircle(14f, 1f, 4f, brush(ink))
                c.drawRoundRect(RectF(-13f, 18f, 13f, 27f), 4f, 4f, brush(ink))
                c.drawLine(-29f, 17f, -37f, 29f, brush(Color.rgb(62, 100, 130), Paint.Style.STROKE, 7f))
                c.drawLine(29f, 17f, 37f, 29f, brush(Color.rgb(62, 100, 130), Paint.Style.STROKE, 7f))
            }
            else -> {
                path.reset(); path.moveTo(-35f, -28f); path.lineTo(-48f, -53f); path.lineTo(-14f, -39f)
                path.lineTo(0f, -51f); path.lineTo(16f, -38f); path.lineTo(47f, -52f)
                path.lineTo(34f, -20f); path.lineTo(40f, 21f); path.quadTo(0f, 51f, -40f, 21f); path.close()
                c.drawPath(path, brush(ink)); c.drawPath(path, brush(Color.rgb(160, 103, 215)))
                c.drawOval(RectF(-29f, -25f, 29f, 31f), brush(Color.rgb(230, 201, 151)))
                c.drawRoundRect(RectF(-23f, -5f, 23f, 13f), 7f, 7f, brush(Color.rgb(48, 31, 67)))
                c.drawCircle(-12f, 4f, 4f, brush(Color.rgb(112, 239, 255)))
                c.drawCircle(12f, 4f, 4f, brush(Color.rgb(112, 239, 255)))
                c.drawArc(RectF(-10f, 12f, 10f, 24f), 10f, 160f, false, brush(ink, Paint.Style.STROKE, 3f))
                c.drawCircle(0f, -33f, 6f, brush(Color.rgb(255, 211, 105)))
            }
        }
        // Thick ink edge around the portrait gives it the hand-drawn game-icon feel.
        c.drawCircle(0f, 0f, 49f, brush(Color.argb(130, 20, 12, 35), Paint.Style.STROKE, 2.5f))
        c.restoreToCount(save)
    }

    private fun label(c: Canvas, text: String) {
        c.drawRoundRect(RectF(9f, 8f, 96f, 29f), 8f, 8f, brush(Color.argb(225, 23, 17, 43)))
        val p = brush(Color.rgb(255, 224, 135))
        p.textSize = 10f
        p.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        c.drawText(text, 19f, 22f, p)
    }
}