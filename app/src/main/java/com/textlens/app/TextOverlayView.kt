package com.textlens.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.text.Text

/**
 * Draws tappable word/line bounding boxes over the image.
 * Tap = select word. Drag = select range. Long press = select line.
 */
class TextOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class WordBox(
        val text: String,
        val rect: RectF,
        var selected: Boolean = false
    )

    private val wordBoxes = mutableListOf<WordBox>()
    private var imageRect = RectF()
    private var isDragging = false
    private var dragStart: PointF? = null

    var onSelectionChanged: ((String) -> Unit)? = null

    // ─── Paints ───────────────────────────────────────────────────────────────

    private val normalPaint = Paint().apply {
        color = Color.argb(40, 66, 133, 244)
        style = Paint.Style.FILL
    }
    private val normalStroke = Paint().apply {
        color = Color.argb(120, 66, 133, 244)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val selectedPaint = Paint().apply {
        color = Color.argb(120, 66, 133, 244)
        style = Paint.Style.FILL
    }
    private val selectedStroke = Paint().apply {
        color = Color.argb(255, 25, 90, 200)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    fun setOcrResult(visionText: Text, imageBitmap: Bitmap, viewWidth: Int, viewHeight: Int) {
        wordBoxes.clear()

        val imgW = imageBitmap.width.toFloat()
        val imgH = imageBitmap.height.toFloat()

        // Calculate how image fits in view (centerCrop / fitCenter)
        val scaleX = viewWidth / imgW
        val scaleY = viewHeight / imgH
        val scale = minOf(scaleX, scaleY)

        val scaledW = imgW * scale
        val scaledH = imgH * scale
        val offsetX = (viewWidth - scaledW) / 2f
        val offsetY = (viewHeight - scaledH) / 2f

        imageRect = RectF(offsetX, offsetY, offsetX + scaledW, offsetY + scaledH)

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (word in line.elements) {
                    val bb = word.boundingBox ?: continue
                    val rect = RectF(
                        offsetX + bb.left * scale,
                        offsetY + bb.top * scale,
                        offsetX + bb.right * scale,
                        offsetY + bb.bottom * scale
                    )
                    wordBoxes.add(WordBox(word.text, rect))
                }
            }
        }
        invalidate()
    }

    fun selectAll() {
        wordBoxes.forEach { it.selected = true }
        invalidate()
        notifySelection()
    }

    fun clearSelection() {
        wordBoxes.forEach { it.selected = false }
        invalidate()
        notifySelection()
    }

    fun getSelectedText(): String =
        wordBoxes.filter { it.selected }.joinToString(" ") { it.text }

    fun getAllText(): String =
        wordBoxes.joinToString(" ") { it.text }

    // ─── Draw ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in wordBoxes) {
            val fill = if (box.selected) selectedPaint else normalPaint
            val stroke = if (box.selected) selectedStroke else normalStroke
            canvas.drawRoundRect(box.rect, 4f, 4f, fill)
            canvas.drawRoundRect(box.rect, 4f, 4f, stroke)
        }
    }

    // ─── Touch ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStart = PointF(event.x, event.y)
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val start = dragStart ?: return true
                if (!isDragging) {
                    val dx = Math.abs(event.x - start.x)
                    val dy = Math.abs(event.y - start.y)
                    if (dx > 10 || dy > 10) isDragging = true
                }
                if (isDragging) {
                    selectInRect(RectF(
                        minOf(start.x, event.x), minOf(start.y, event.y),
                        maxOf(start.x, event.x), maxOf(start.y, event.y)
                    ))
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    toggleWordAt(event.x, event.y)
                }
                notifySelection()
                isDragging = false
                dragStart = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun toggleWordAt(x: Float, y: Float) {
        val hit = wordBoxes.firstOrNull { it.rect.contains(x, y) }
        if (hit != null) {
            hit.selected = !hit.selected
        } else {
            // Tap on empty = deselect all
            wordBoxes.forEach { it.selected = false }
        }
        invalidate()
    }

    private fun selectInRect(dragRect: RectF) {
        for (box in wordBoxes) {
            if (RectF.intersects(dragRect, box.rect)) {
                box.selected = true
            }
        }
        invalidate()
    }

    private fun notifySelection() {
        onSelectionChanged?.invoke(getSelectedText())
    }
}
