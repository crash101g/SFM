package app.sofabrowser.multiview

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Lays out 1–4 tiles: one fills the screen, two sit side by side at 16:9, three or four make a 2×2 grid. With
 * [solo] set, that tile fills the screen and the others are hidden (they keep playing, muted).
 */
class TileGrid(context: Context) : ViewGroup(context) {

    var solo: Int = -1
        set(v) {
            field = v
            for (i in 0 until childCount) getChildAt(i).visibility = if (v < 0 || v == i) View.VISIBLE else View.INVISIBLE
            requestLayout()
        }

    init {
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        for (i in 0 until childCount) {
            val r = cell(i, w, h)
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(r[2] - r[0], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(r[3] - r[1], MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = cell(i, r - l, b - t)
            getChildAt(i).layout(c[0], c[1], c[2], c[3])
        }
    }

    /** left, top, right, bottom of tile [i]. */
    private fun cell(i: Int, w: Int, h: Int): IntArray {
        val n = childCount
        if (solo >= 0 && i == solo || n == 1) return intArrayOf(0, 0, w, h)
        if (solo >= 0) return intArrayOf(0, 0, 1, 1)
        val half = w / 2
        if (n == 2) {
            val th = minOf(h, half * 9 / 16)
            val top = (h - th) / 2
            return intArrayOf(i * half, top, i * half + half, top + th)
        }
        val hh = h / 2
        val col = i % 2
        val row = i / 2
        return intArrayOf(col * half, row * hh, col * half + half, row * hh + hh)
    }
}
