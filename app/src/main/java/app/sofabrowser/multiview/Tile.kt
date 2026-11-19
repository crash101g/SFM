package app.sofabrowser.multiview

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.sofabrowser.ui.px

/** One cell of the Multiview grid: the feed's view, its title bar and a status line. */
abstract class Tile(context: Context, val index: Int, val feed: Feed) : FrameLayout(context) {

    protected val main = Handler(Looper.getMainLooper())

    private val title = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 22.px.toFloat())
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
        setBackgroundColor(0x99000000.toInt())
        setPadding(14.px, 6.px, 14.px, 6.px)
        maxLines = 1
    }

    private val status = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 26.px.toFloat())
        gravity = Gravity.CENTER
        setBackgroundColor(0xB3000000.toInt())
        setPadding(24.px, 16.px, 24.px, 16.px)
        visibility = GONE
    }

    private val frame = View(context).apply { isClickable = false; isFocusable = false }

    /** Clean view's sign that this tile just got the sound: a speaker for a moment, nothing else. */
    private val badge = TextView(context).apply {
        text = "🔊"
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 40.px.toFloat())
        setBackgroundColor(0x99000000.toInt())
        setPadding(16.px, 8.px, 16.px, 8.px)
        visibility = GONE
    }
    private val hideBadge = Runnable { badge.visibility = GONE }

    /** Shows the speaker badge for a moment. */
    fun flashSound() {
        main.removeCallbacks(hideBadge)
        badge.visibility = VISIBLE
        main.postDelayed(hideBadge, BADGE_MS)
    }

    var audible = false
        set(v) {
            field = v
            updateTitle()
            applyAudio(v)
        }

    var highlighted = false
        set(v) {
            field = v
            frame.background = if (v) GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(4.px, 0xFFFF8A3D.toInt())
            } else null
        }

    var titlesShown = true
        set(v) {
            field = v
            title.visibility = if (v) VISIBLE else GONE
        }

    init {
        setBackgroundColor(Color.BLACK)
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        isFocusable = false
    }

    /** Adds the content view under the overlays; subclasses call it once. */
    protected fun setContent(view: View) {
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (title.parent == null) {
            addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
            addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            addView(frame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                setMargins(16.px, 16.px, 0, 0)
            })
        }
        updateTitle()
    }

    protected fun replaceContent(view: View) {
        removeViewAt(0)
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun updateTitle() {
        title.text = "${index + 1}  ${if (audible) "🔊" else "🔇"}  ${feed.label}"
    }

    /** Shows [text] over the tile, or hides the status line for null. */
    protected fun showStatus(text: CharSequence?) {
        status.text = text
        status.visibility = if (text == null) GONE else VISIBLE
    }

    abstract fun start()
    abstract fun reload()
    abstract fun release()
    protected abstract fun applyAudio(on: Boolean)

    /** A press in the middle of the feed, for players that wait for a click. Only web tiles have one. */
    open fun tap() {}

    private companion object {
        const val BADGE_MS = 1_500L
    }
}
