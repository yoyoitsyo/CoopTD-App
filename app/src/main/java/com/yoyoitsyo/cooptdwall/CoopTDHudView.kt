package com.yoyoitsyo.cooptdwall

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Reference-inspired floating game HUD, drawn as one compact overlay surface. */
class CoopTDHudView(
    context: Context,
    private val action: (String) -> Unit,
    private val drag: (Float, Float) -> Unit,
    private val dragEnd: () -> Unit
) : View(context) {
    var waveLabel = "WAVE 1"
    var timerLabel = "GAME TIMER  01:00"
    var statusLabel = "MANUAL TIMER"
    var paused = false
    private val density = resources.displayMetrics.density
    private var referenceArt: Bitmap? = ReferenceArt.load(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var moved = false

    private fun p(color: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 1f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; this.style = style; strokeWidth = stroke
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, edge: Int? = null, sw: Float = 2f) {
        c.drawRoundRect(l,t,r,b,radius,radius,p(color))
        if(edge!=null)c.drawRoundRect(l,t,r,b,radius,radius,p(edge,Paint.Style.STROKE,sw))
    }
    private fun txt(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,center:Boolean=false) {
        val q=p(color).apply {
            textSize=size; textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
            typeface=Typeface.create("sans-serif-black",Typeface.BOLD)
            setShadowLayer(2f,1f,1.5f,Color.BLACK)
        }
        c.drawText(s,x,y,q)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save=canvas.save()
        canvas.scale(width/360f,height/250f)
        referenceArt?.let { art ->
            drawReferenceHud(canvas, art)
            canvas.restoreToCount(save)
            return
        }
        // Outer black/gold game frame with layered beveled edge.
        rr(canvas,1f,1f,359f,249f,18f,Color.rgb(7,10,22),Color.rgb(255,194,58),4f)
        rr(canvas,7f,7f,353f,243f,14f,Color.rgb(16,25,54),Color.rgb(91,112,181),2f)
        canvas.drawLine(22f,12f,300f,12f,p(Color.rgb(255,221,121),Paint.Style.STROKE,2f))
        // Character illustration panel.
        rr(canvas,13f,17f,130f,214f,13f,Color.rgb(21,34,71),Color.rgb(195,151,67),2f)
        drawKnight(canvas,72f,116f)
        // Header / wave counter.
        txt(canvas,"COOP TD",145f,35f,15f,Color.rgb(255,199,52))
        txt(canvas,"WALL ASSISTANT",145f,49f,8f,Color.rgb(221,231,255))
        txt(canvas,waveLabel,146f,89f,28f,Color.WHITE)
        canvas.drawLine(146f,96f,333f,96f,p(Color.rgb(255,198,65),Paint.Style.STROKE,2f))
        txt(canvas,"GAME TIMER",148f,119f,13f,Color.WHITE)
        txt(canvas,timerLabel.substringAfter("GAME TIMER").trim(),328f,151f,31f,Color.rgb(255,211,36),true)
        // OCR state: saturated green when locked, deep blue otherwise.
        val locked=statusLabel.contains("OCR",true)||statusLabel.contains("SYNC",true)
        rr(canvas,143f,164f,340f,194f,9f,
            if(locked)Color.rgb(7,98,31) else Color.rgb(18,37,79),
            if(locked)Color.rgb(103,255,103) else Color.rgb(255,197,67),2f)
        canvas.drawCircle(157f,179f,5f,p(if(locked)Color.rgb(52,255,77) else Color.rgb(255,198,58)))
        txt(canvas,statusLabel.take(29),168f,183f,9.2f,Color.WHITE)
        txt(canvas,"COOP TD",240f,213f,23f,Color.rgb(255,199,47),true)
        // Close button like the red corner tile in the reference.
        rr(canvas,315f,12f,347f,45f,7f,Color.rgb(160,25,38),Color.rgb(255,198,68),2f)
        txt(canvas,"×",331f,39f,29f,Color.rgb(255,89,72),true)
        // Functional small control rail, tucked into the bottom bevel.
        val labels=listOf("WAVE −","PAUSE","WAVE +","SIZE")
        val xs=listOf(18f,101f,184f,267f)
        labels.forEachIndexed { i,label ->
            rr(canvas,xs[i],218f,xs[i]+75f,239f,6f,Color.rgb(24,55,115),Color.rgb(213,170,76),1.2f)
            txt(canvas,if(i==1&&paused)"RESUME" else label,xs[i]+37.5f,232f,9f,Color.WHITE,true)
        }
        canvas.restoreToCount(save)
    }
    private fun drawReferenceHud(c: Canvas, art: Bitmap) {
        val imagePaint=p(Color.WHITE).apply { isFilterBitmap=true }
        ReferenceArt.drawCrop(c,art,RectF(610f,78f,1183f,460f),RectF(0f,0f,360f,250f),imagePaint)
        // Hide the static sample wave/time/status printed in the design and redraw live values.
        rr(c,126f,37f,306f,94f,9f,Color.rgb(9,15,34),Color.rgb(255,198,65),2f)
        txt(c,waveLabel,216f,82f,27f,Color.WHITE,true)
        rr(c,163f,96f,306f,159f,8f,Color.rgb(9,15,34),Color.rgb(255,198,65),1.5f)
        txt(c,"GAME TIMER",234f,116f,13f,Color.WHITE,true)
        val liveTime=timerLabel.substringAfter("GAME TIMER").trim()
        txt(c,liveTime,234f,149f,28f,Color.rgb(255,211,36),true)
        val locked=statusLabel.contains("OCR",true)||statusLabel.contains("SYNC",true)
        rr(c,38f,161f,325f,198f,9f,
            if(locked)Color.rgb(7,98,31) else Color.rgb(18,37,79),
            if(locked)Color.rgb(103,255,103) else Color.rgb(255,197,67),2f)
        c.drawCircle(53f,179f,5f,p(if(locked)Color.rgb(52,255,77) else Color.rgb(255,198,58)))
        txt(c,statusLabel.take(27),64f,184f,9.5f,Color.WHITE,true)
        // Compact controls replace the static logo strip while preserving wave corrections.
        val labels=listOf("−","PAUSE","+","SIZE")
        val xs=listOf(118f,166f,214f,262f)
        labels.forEachIndexed { i,label ->
            rr(c,xs[i],207f,xs[i]+43f,239f,6f,Color.rgb(20,52,112),Color.rgb(255,196,65),1.4f)
            txt(c,if(i==1&&paused)"GO" else label,xs[i]+21.5f,228f,if(i==1)8.5f else 17f,Color.WHITE,true)
        }
    }

    private fun drawKnight(c:Canvas,x:Float,y:Float) {
        val save=c.save();c.translate(x,y)
        // Blue plume and steel helmet silhouette.
        path.reset();path.moveTo(-35f,-42f);path.quadTo(-53f,-69f,-19f,-76f);path.quadTo(9f,-77f,26f,-50f);path.close()
        c.drawPath(path,p(Color.rgb(26,17,45),Paint.Style.STROKE,6f));c.drawPath(path,p(Color.rgb(44,117,245)))
        c.drawOval(-42f,-50f,42f,50f,p(Color.rgb(8,11,25),Paint.Style.STROKE,8f))
        c.drawOval(-39f,-47f,39f,47f,p(Color.rgb(82,113,178)))
        path.reset();path.moveTo(-39f,-22f);path.lineTo(-27f,-61f);path.lineTo(13f,-65f);path.lineTo(42f,-19f);path.lineTo(18f,1f);path.lineTo(-28f,1f);path.close()
        c.drawPath(path,p(Color.rgb(8,10,21),Paint.Style.STROKE,8f));c.drawPath(path,p(Color.rgb(172,192,228)))
        c.drawRoundRect(-28f,-24f,29f,-10f,5f,5f,p(Color.rgb(11,20,47)))
        c.drawCircle(-13f,-17f,5f,p(Color.rgb(255,207,68)));c.drawCircle(13f,-17f,5f,p(Color.rgb(255,207,68)))
        c.drawOval(-18f,3f,18f,37f,p(Color.rgb(236,178,125)))
        c.drawRoundRect(-28f,34f,28f,77f,12f,12f,p(Color.rgb(8,10,23),Paint.Style.STROKE,7f))
        c.drawRoundRect(-25f,36f,25f,74f,11f,11f,p(Color.rgb(39,75,155)))
        c.restoreToCount(save)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x=e.x/(width/360f); val y=e.y/(height/250f)
        when(e.action) {
            MotionEvent.ACTION_DOWN -> { downX=e.rawX;downY=e.rawY;dragging=y<60f||(x>305f&&y>200f);moved=false;return true }
            MotionEvent.ACTION_MOVE -> if(dragging) {
                val dx=e.rawX-downX;val dy=e.rawY-downY
                if(kotlin.math.abs(dx)>2||kotlin.math.abs(dy)>2)moved=true
                drag(dx,dy);downX=e.rawX;downY=e.rawY;return true
            }
            MotionEvent.ACTION_UP -> {
                if(dragging){dragEnd();dragging=false}
                if(!moved) when {
                    x>310f&&y<55f -> action("close")
                    y>=200f&&x>305f -> action("size")
                    y>=200f&&x<162f -> action("wave-minus")
                    y>=200f&&x<210f -> action("pause")
                    y>=200f&&x<258f -> action("wave-plus")
                    y>=200f -> action("size")
                    else -> action("pause")
                }
                performClick();return true
            }
            MotionEvent.ACTION_CANCEL -> {dragEnd();dragging=false;return true}
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
