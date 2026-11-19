package app.sofabrowser.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.sofabrowser.R
import app.sofabrowser.browser.UrlInput
import app.sofabrowser.data.Db
import app.sofabrowser.data.Favicons
import app.sofabrowser.multiview.MultiviewBookmark
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** History or bookmarks as one focusable list. Returns the chosen URL in [SearchActivity.EXTRA_URL]. */
class ListActivity : Activity() {

    private val db by lazy { Db.get(this) }
    private val history get() = intent.getStringExtra(EXTRA_MODE) == MODE_HISTORY
    private lateinit var list: LinearLayout
    private lateinit var empty: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.background(false)
            setPadding(116.px, 72.px, 116.px, 0)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(Ui.text(this, 56, Ui.p.text, bold = true).apply {
            text = getString(if (history) R.string.history else R.string.bookmarks)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Ui.text(this, 28, Ui.p.textMid).apply {
            text = getString(R.string.delete_all)
            isFocusable = true
            isClickable = true
            gravity = Gravity.CENTER
            background = Ui.fillSelector(Ui.p.fillFocused, Ui.p.focus, 12)
            setPadding(32.px, 14.px, 32.px, 14.px)
            setOnFocusChangeListener { v, has -> (v as android.widget.TextView).setTextColor(if (has) Ui.p.onFocus else Ui.p.textMid) }
            setOnClickListener { confirmDeleteAll() }
        })
        root.addView(header)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 24.px, 0, 72.px); clipChildren = false }
        val scroll = FocusScrollView(this).apply { isVerticalScrollBarEnabled = false; clipChildren = false; addView(list) }
        val frame = FrameLayout(this)
        frame.addView(scroll)
        empty = Ui.text(this, 32, Ui.p.textWeak).apply {
            text = getString(if (history) R.string.history_empty else R.string.bookmarks_empty)
            gravity = Gravity.CENTER
        }
        frame.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        load()
    }

    private fun load() {
        list.removeAllViews()
        if (history) {
            var lastHeader: String? = null
            db.history().forEach { h ->
                val header = dayLabel(h.visitedAt)
                if (header != lastHeader) {
                    list.addView(Ui.text(this, 30, Ui.p.textWeak).apply { text = header; setPadding(0, 36.px, 0, 20.px) })
                    lastHeader = header
                }
                addRow(h.title, h.url, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(h.visitedAt))) {
                    db.removeHistory(h.id)
                }
            }
        } else {
            db.bookmarks().forEach { b ->
                // Any bookmark can be renamed; its address (or a Multiview bookmark's feeds) stays shown under the name.
                addRow(b.title, b.url, null, { shown: TextView -> renameBookmark(b.url, shown) }) { db.removeBookmark(b.url) }
            }
        }
        empty.visibility = if (list.childCount == 0) View.VISIBLE else View.GONE
        list.getChildAt((0 until list.childCount).firstOrNull { list.getChildAt(it) is LinearLayout } ?: 0)
            ?.let { (it as? LinearLayout)?.getChildAt(0)?.requestFocus() }
    }

    private fun dayLabel(t: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = t }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val days = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        return when {
            sameYear && days == 0 -> getString(R.string.today)
            sameYear && days == 1 -> getString(R.string.yesterday)
            else -> DateUtils.formatDateTime(this, t, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY)
        }
    }

    private fun renameBookmark(url: String, shown: TextView) {
        val default = if (MultiviewBookmark.isOne(url)) MultiviewBookmark.title(MultiviewBookmark.feeds(url)) else UrlInput.display(url)
        val input = EditText(this).apply {
            setText(shown.text)
            setHint(R.string.multiview_bookmark_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(MultiviewBookmark.MAX_NAME))
            selectAll()
        }
        Ui.dialog(this)
            .setTitle(R.string.multiview_bookmark_rename)
            .setView(FrameLayout(this).apply { setPadding(48.px, 16.px, 48.px, 0); addView(input) })
            .setPositiveButton(R.string.multiview_bookmark_save) { _, _ ->
                val title = MultiviewBookmark.name(input.text.toString(), default)
                db.renameBookmark(url, title)
                shown.text = title
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun addRow(title: String, url: String, meta: String?, rename: ((TextView) -> Unit)? = null, delete: () -> Unit) {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = Ui.fillSelector(Ui.p.fill, Ui.p.fillFocused, 16)
            foreground = Ui.focusBorder(16)
            setPadding(40.px, 0, 40.px, 0)
            setOnClickListener {
                setResult(RESULT_OK, Intent().putExtra(SearchActivity.EXTRA_URL, url))
                finish()
            }
        }
        body.addView(ImageView(this).apply { setImageDrawable(Favicons.get(this@ListActivity).drawable(url, title, 128)) },
            LinearLayout.LayoutParams(64.px, 64.px).apply { marginEnd = 32.px })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titleView = Ui.text(this, 34, Ui.p.text).apply { text = title.ifBlank { url }; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        texts.addView(titleView)
        // A Multiview bookmark lists its feeds, one per line; any other shows its address.
        val feeds = if (MultiviewBookmark.isOne(url)) MultiviewBookmark.feeds(url) else null
        val where = feeds?.let { f ->
            resources.getQuantityString(R.plurals.multiview_bookmark_feeds, f.size, f.size) + "\n" +
                f.withIndex().joinToString("\n") { (i, feed) -> "${i + 1}  " + UrlInput.display(feed.url) }
        } ?: UrlInput.display(url)
        texts.addView(Ui.text(this, 24, Ui.p.textWeak).apply {
            text = listOfNotNull(meta, where).joinToString("  ·  ")
            maxLines = if (feeds != null) feeds.size + 1 else 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(0, 10.px, 0, 0)
        })
        body.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (feeds != null) { body.minimumHeight = 136.px; body.setPadding(40.px, 20.px, 40.px, 20.px) }
        line.addView(body, LinearLayout.LayoutParams(0, if (feeds != null) LinearLayout.LayoutParams.WRAP_CONTENT else 136.px, 1f))

        val remove = FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
            contentDescription = getString(R.string.delete)
            background = Ui.fillSelector(Ui.p.fillFocused, Ui.p.focus, 40)
            val img = ImageView(context).apply { setImageResource(R.drawable.ic_delete); setColorFilter(Ui.p.textMid, PorterDuff.Mode.SRC_IN) }
            addView(img, FrameLayout.LayoutParams(40.px, 40.px, Gravity.CENTER))
            visibility = View.INVISIBLE
            setOnFocusChangeListener { _, has ->
                img.setColorFilter(if (has) Ui.p.onFocus else Ui.p.textMid, PorterDuff.Mode.SRC_IN)
                if (!has && !body.hasFocus() && line.findFocus() == null) {
                    for (i in 1 until line.childCount) line.getChildAt(i).visibility = View.INVISIBLE
                }
            }
            setOnClickListener {
                delete()
                val i = list.indexOfChild(line)
                list.removeView(line)
                (list.getChildAt(i) as? LinearLayout ?: list.getChildAt(i - 1) as? LinearLayout)?.getChildAt(0)?.requestFocus()
                empty.visibility = if (list.childCount == 0) View.VISIBLE else View.GONE
            }
        }
        val edit = rename?.let { act ->
            FrameLayout(this).apply {
                isFocusable = true
                isClickable = true
                contentDescription = getString(R.string.multiview_bookmark_rename)
                background = Ui.fillSelector(Ui.p.fillFocused, Ui.p.focus, 40)
                val img = ImageView(context).apply { setImageResource(R.drawable.ic_edit); setColorFilter(Ui.p.textMid, PorterDuff.Mode.SRC_IN) }
                addView(img, FrameLayout.LayoutParams(40.px, 40.px, Gravity.CENTER))
                visibility = View.INVISIBLE
                setOnFocusChangeListener { _, has ->
                    img.setColorFilter(if (has) Ui.p.onFocus else Ui.p.textMid, PorterDuff.Mode.SRC_IN)
                    if (!has && !body.hasFocus() && !remove.hasFocus()) { visibility = View.INVISIBLE; remove.visibility = View.INVISIBLE }
                }
                setOnClickListener { act(titleView) }
            }
        }
        edit?.let { line.addView(it, LinearLayout.LayoutParams(80.px, 80.px).apply { marginStart = 24.px }) }
        line.addView(remove, LinearLayout.LayoutParams(80.px, 80.px).apply { marginStart = 24.px })
        val buttons = listOfNotNull(edit, remove)
        fun showButtons(on: Boolean) = buttons.forEach { it.visibility = if (on) View.VISIBLE else View.INVISIBLE }
        body.setOnFocusChangeListener { v, has ->
            Ui.animateScale(v, if (has) 1.02f else 1f, has)
            if (has) showButtons(true) else if (buttons.none { it.hasFocus() }) showButtons(false)
        }
        list.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 16.px
        })
    }

    private fun confirmDeleteAll() {
        Ui.dialog(this)
            .setMessage(if (history) R.string.confirm_clear_history else R.string.confirm_clear_bookmarks)
            .setPositiveButton(R.string.delete_all) { _, _ ->
                if (history) db.clearHistory() else db.bookmarks().forEach { db.removeBookmark(it.url) }
                load()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_HISTORY = "history"
        const val MODE_BOOKMARKS = "bookmarks"
    }
}
