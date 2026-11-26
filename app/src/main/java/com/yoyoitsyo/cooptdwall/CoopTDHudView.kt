package com.yoyoitsyo.cooptdwall

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Compact run HUD designed to stay clear of the game's top-center wave readout. */
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
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var moved = false

    private fun p(color:Int, style:Paint.Style=Paint.Style.FILL, stroke:Float=1f)=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color=color;this.style=style;strokeWidth=stroke;strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND
    }
    private fun txt(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,center:Boolean=false,bold:Boolean=true) {
        val q=p(color).apply { textSize=size;textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
            typeface=Typeface.create("sans-serif-condensed",if(bold)Typeface.BOLD else Typeface.NORMAL)
            setShadowLayer(1.8f,1f,1.3f,Color.BLACK) }
        c.drawText(s,x,y,q)
    }
    private fun rr(c:Canvas,l:Float,t:Float,r:Float,b:Float,rad:Float,color:Int,edge:Int?=null,sw:Float=1.4f) {
        c.drawRoundRect(l,t,r,b,rad,rad,p(color))
        if(edge!=null)c.drawRoundRect(l,t,r,b,rad,rad,p(edge,Paint.Style.STROKE,sw))
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val save=canvas.save();canvas.scale(width/360f,height/112f)
        rr(canvas,1f,1f,359f,111f,13f,Color.argb(226,9,15,29),Color.rgb(63,76,105),1.5f)
        rr(canvas,4f,4f,356f,108f,10f,Color.argb(90,25,36,59),Color.argb(130,132,147,174),1f)
        canvas.drawLine(15f,7f,345f,7f,p(Color.argb(180,230,190,93),Paint.Style.STROKE,1f))
        val wave=waveLabel.uppercase().replace("WAVE","W")
        txt(canvas,wave,16f,36f,25f,Color.WHITE)
        txt(canvas,"GAME",144f,20f,8.5f,Color.rgb(177,192,220))
        val time=timerLabel.substringAfter("GAME TIMER").trim().ifBlank{"--:--"}
        txt(canvas,time,144f,43f,25f,Color.rgb(255,205,52))
        val locked=statusLabel.contains("OCR",true)||statusLabel.contains("SYNC",true)
        canvas.drawCircle(18f,57f,4f,p(if(locked)Color.rgb(70,235,97) else Color.rgb(255,194,61)))
        val status=statusLabel.replace("OCR SYNC  ·  LOCKED ON WAVE","OCR LOCKED").replace("OCR SYNC · LOCKED ON WAVE","OCR LOCKED")
        txt(canvas,status.take(49),28f,61f,10.2f,Color.rgb(235,241,255),false,false)
        val labels=listOf("WAVE −",if(paused)"RESUME" else "PAUSE","WAVE +","SIZE")
        val xs=listOf(9f,96f,183f,270f)
        labels.forEachIndexed { i,label ->
            rr(canvas,xs[i],78f,xs[i]+81f,104f,6f,if(i==3)Color.rgb(34,39,57) else Color.rgb(23,52,101),
                if(i==3)Color.rgb(128,144,171) else Color.rgb(92,132,205),1.2f)
            txt(canvas,label,xs[i]+40.5f,95.5f,10f,Color.WHITE,true)
        }
        rr(canvas,329f,10f,350f,31f,5f,Color.rgb(112,24,33),Color.rgb(255,125,92),1f)
        txt(canvas,"×",339.5f,27f,18f,Color.rgb(255,126,103),true)
        canvas.restoreToCount(save)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        val x=e.x/(width/360f);val y=e.y/(height/112f)
        when(e.action) {
            MotionEvent.ACTION_DOWN->{downX=e.rawX;downY=e.rawY;dragging=y<72f&&!(x>325f&&y<35f);moved=false;return true}
            MotionEvent.ACTION_MOVE->if(dragging){
                val dx=e.rawX-downX;val dy=e.rawY-downY
                if(abs(dx)>2||abs(dy)>2)moved=true
                drag(dx,dy);downX=e.rawX;downY=e.rawY;return true
            }
            MotionEvent.ACTION_UP->{
                if(dragging){dragEnd();dragging=false}
                if(!moved)when{
                    x>325f&&y<35f->action("close")
                    y>=75f&&x<91f->action("wave-minus")
                    y>=75f&&x<178f->action("pause")
                    y>=75f&&x<265f->action("wave-plus")
                    y>=75f->action("size")
                    else->action("pause")
                }
                performClick();return true
            }
            MotionEvent.ACTION_CANCEL->{dragEnd();dragging=false;return true}
        }
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
