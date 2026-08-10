package com.darkaxt.dualdex.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class OverlayResizeHandle(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(210, 235, 101)
        strokeWidth = resources.displayMetrics.density * 2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        contentDescription = "Resize companion"
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = resources.displayMetrics.density * 7f
        val step = resources.displayMetrics.density * 6f
        repeat(3) { index ->
            val offset = inset + index * step
            canvas.drawLine(width - offset, height - inset, width - inset, height - offset, paint)
        }
    }
}
