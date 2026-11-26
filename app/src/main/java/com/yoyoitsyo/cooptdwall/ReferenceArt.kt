package com.yoyoitsyo.cooptdwall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri

/**
 * Artwork adapter for the user's 1213x1296 CoopTD Wall Assistant reference.
 * The screenshot is selected once from the gallery and its URI is persisted.
 * We crop the original artwork at draw-time so live wave/timer text stays dynamic.
 */
object ReferenceArt {
    const val PREF_URI = "reference_art_uri"
    private const val DESIGN_WIDTH = 1213f
    private const val DESIGN_HEIGHT = 1296f

    fun decodeUri(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (_: Exception) { null }

    fun load(context: Context): Bitmap? {
        val value = context.getSharedPreferences("wall", Context.MODE_PRIVATE)
            .getString(PREF_URI, null) ?: return null
        return try { decodeUri(context, Uri.parse(value)) } catch (_: Exception) { null }
    }

    fun source(bitmap: Bitmap, original: RectF): Rect {
        val sx = bitmap.width / DESIGN_WIDTH
        val sy = bitmap.height / DESIGN_HEIGHT
        return Rect(
            (original.left * sx).toInt().coerceIn(0, bitmap.width - 1),
            (original.top * sy).toInt().coerceIn(0, bitmap.height - 1),
            (original.right * sx).toInt().coerceIn(1, bitmap.width),
            (original.bottom * sy).toInt().coerceIn(1, bitmap.height)
        )
    }

    fun drawCrop(canvas: Canvas, bitmap: Bitmap?, original: RectF, destination: RectF, paint: Paint) {
        if (bitmap == null || bitmap.isRecycled) return
        val src = source(bitmap, original)
        canvas.drawBitmap(bitmap, src, destination, paint)
    }
}
