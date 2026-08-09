package com.darkaxt.dualdex.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.View

class PokeBallBubbleView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixelPaint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private var sprite: Bitmap? = null

    init {
        contentDescription = "Toggle DualDex companion"
        elevation = 12f * resources.displayMetrics.density
        isClickable = true
        isFocusable = false
    }

    fun setRomSprite(png: ByteArray?) {
        sprite = png?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = minOf(width, height) * 0.47f
        val centerX = width / 2f
        val centerY = height / 2f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(7, 30, 24)
        canvas.drawCircle(centerX, centerY, radius, paint)

        val romSprite = sprite
        if (romSprite != null) {
            val inset = (radius * 0.32f).toInt()
            val destination = Rect(
                (centerX - radius + inset).toInt(),
                (centerY - radius + inset).toInt(),
                (centerX + radius - inset).toInt(),
                (centerY + radius - inset).toInt(),
            )
            canvas.drawBitmap(romSprite, null, destination, pixelPaint)
            return
        }

        val ballRadius = radius * 0.72f
        val clip = Path().apply { addCircle(centerX, centerY, ballRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        paint.color = Color.rgb(210, 63, 55)
        canvas.drawRect(centerX - ballRadius, centerY - ballRadius, centerX + ballRadius, centerY, paint)
        paint.color = Color.rgb(244, 242, 216)
        canvas.drawRect(centerX - ballRadius, centerY, centerX + ballRadius, centerY + ballRadius, paint)
        canvas.restore()
        paint.color = Color.rgb(38, 48, 43)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ballRadius * 0.15f
        canvas.drawCircle(centerX, centerY, ballRadius, paint)
        canvas.drawLine(centerX - ballRadius, centerY, centerX + ballRadius, centerY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, ballRadius * 0.29f, paint)
        paint.color = Color.rgb(217, 244, 122)
        canvas.drawCircle(centerX, centerY, ballRadius * 0.16f, paint)
    }
}
