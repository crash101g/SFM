package app.sofabrowser.multiview

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import app.sofabrowser.R
import app.sofabrowser.ui.Ui
import app.sofabrowser.ui.px
import java.util.concurrent.Executors

/**
 * SFM's Multiview: up to four feeds on one screen, one of them with the sound.
 *
 * Remote: the arrows pick a tile; OK gives it the sound; holding OK, or Menu, opens the options; 1–4 give that feed the
 * sound and 0 mutes all; Back leaves full screen, then Multiview.
 *
 * The feeds come only from what the user saved here ([FeedStore]). The activity is exported so the TV's launcher (and
 * adb) can start it, so it ignores everything in the intent that starts it: another app can't hand it a feed.
 */
class MultiviewActivity : Activity() {

    private lateinit var store: FeedStore
    private lateinit var root: FrameLayout
    private lateinit var grid: TileGrid
    private lateinit var empty: TextView
    private val main = Handler(Looper.getMainLooper())
    private val probes = Executors.newSingleThreadExecutor()

    private var feeds: MutableList<Feed?> = MutableList(Feed.MAX) { null }
    private val tiles = mutableListOf<Tile>()
    private var selected = 0
    private var audio = 0
    private var titles = true
    private var okDownAt = -1L
    private var menuFromHold = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.init(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = FeedStore(this)
        root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        grid = TileGrid(this)
        empty = Ui.text(this, 34, Ui.p.text).apply {
            setText(R.string.multiview_empty)
            gravity = Gravity.CENTER
            setPadding(160.px, 0, 160.px, 0)
        }
        root.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        feeds = store.load().toMutableList()
        audio = store.audio
        buildTiles()
    }

    override fun onStop() {
        // Nothing plays in the background: four decoders and pages are let go as soon as Multiview is left.
        releaseTiles()
        super.onStop()
    }

    override fun onDestroy() {
        probes.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildTiles() {
        releaseTiles()
        val present = feeds.withIndex().filter { it.value != null }
        empty.visibility = if (present.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        present.forEachIndexed { pos, (i, feed) ->
            val tile = if (feed!!.kind == Feed.Kind.WEB) WebTile(this, i, feed) else StreamTile(this, i, feed)
            tile.titlesShown = titles
            tiles += tile
            grid.addView(tile)
            // Many stream sites fail when several players start at once; web feeds start a few seconds apart.
            main.postDelayed({ if (tile.parent != null) tile.start() }, if (feed.kind == Feed.Kind.WEB) pos * STAGGER_MS else 0L)
        }
        grid.solo = -1
        selected = selected.coerceIn(0, maxOf(0, tiles.size - 1))
        refreshMarks()
    }

    private fun releaseTiles() {
        main.removeCallbacksAndMessages(null)
        tiles.forEach { it.release() }
        tiles.clear()
        grid.removeAllViews()
    }

    private fun refreshMarks() {
        tiles.forEachIndexed { pos, t ->
            t.highlighted = pos == selected && tiles.size > 1
            t.audible = t.index == audio
        }
    }

    private fun setAudio(feedIndex: Int) {
        audio = if (audio == feedIndex) -1 else feedIndex
        store.audio = audio
        refreshMarks()
    }

    // Remote control. Keys never reach the pages: the tiles take no focus.

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE)
            return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) onKeyPressed(code, event) else if (event.action == KeyEvent.ACTION_UP) onKeyReleased(code)
        return true
    }

    private fun onKeyPressed(code: Int, event: KeyEvent) {
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.repeatCount == 0) { okDownAt = event.downTime; menuFromHold = false }
                else if (!menuFromHold && event.eventTime - okDownAt >= HOLD_MS) { menuFromHold = true; showMenu() }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(1, 0)
            KeyEvent.KEYCODE_DPAD_UP -> move(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0, 1)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_SETTINGS -> if (event.repeatCount == 0) showMenu()
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4 -> {
                val i = code - KeyEvent.KEYCODE_1
                if (feeds[i] != null) setAudio(i)
            }
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_MUTE -> { audio = -1; store.audio = -1; refreshMarks() }
            KeyEvent.KEYCODE_BACK -> {}
        }
    }

    private fun onKeyReleased(code: Int) {
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (menuFromHold) { menuFromHold = false; return }
                if (tiles.isEmpty()) editFeeds() else setAudio(tiles[selected].index)
            }
            KeyEvent.KEYCODE_BACK -> if (grid.solo >= 0) { grid.solo = -1; refreshMarks() } else finish()
        }
    }

    private fun move(dx: Int, dy: Int) {
        if (tiles.size < 2 || grid.solo >= 0) return
        val cols = 2
        val col = selected % cols + dx
        val row = selected / cols + dy
        if (col !in 0 until cols || row < 0) return
        val next = row * cols + col
        if (next !in tiles.indices || (tiles.size == 2 && dy != 0)) return
        selected = next
        refreshMarks()
    }

    private fun showMenu() {
        if (tiles.isEmpty()) return editFeeds()
        val tile = tiles[selected]
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items += getString(R.string.multiview_menu_audio) to { if (audio != tile.index) setAudio(tile.index) }
        items += if (grid.solo >= 0) getString(R.string.multiview_menu_grid) to { grid.solo = -1 }
                 else getString(R.string.multiview_menu_solo) to { grid.solo = selected; if (audio != tile.index) setAudio(tile.index) }
        items += getString(R.string.multiview_menu_reload) to { tile.reload() }
        items += getString(R.string.multiview_menu_reload_all) to { reloadAll() }
        if (tile is WebTile) items += getString(R.string.multiview_menu_tap) to { tile.tap() }
        items += getString(R.string.multiview_menu_mute) to { audio = -1; store.audio = -1; refreshMarks() }
        items += getString(R.string.multiview_menu_titles) to { titles = !titles; tiles.forEach { it.titlesShown = titles } }
        items += getString(R.string.multiview_menu_edit) to { editFeeds() }
        Ui.dialog(this)
            .setTitle(tile.feed.label)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun reloadAll() {
        tiles.forEachIndexed { pos, t -> main.postDelayed({ if (t.parent != null) t.reload() }, pos * STAGGER_MS) }
    }

    // Editing feeds.

    private fun editFeeds() {
        val labels = (0 until Feed.MAX).map { i ->
            feeds[i]?.let { getString(R.string.multiview_slot, i + 1, it.label) } ?: getString(R.string.multiview_slot_empty, i + 1)
        }
        Ui.dialog(this)
            .setTitle(R.string.multiview_edit_title)
            .setItems(labels.toTypedArray()) { _, which -> editFeed(which) }
            .setPositiveButton(R.string.multiview_done, null)
            .show()
    }

    private fun editFeed(i: Int) {
        val old = feeds[i]
        val input = EditText(this).apply {
            setText(old?.url.orEmpty())
            setHint(R.string.multiview_feed_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSingleLine = true
        }
        val box = FrameLayout(this).apply { setPadding(48.px, 16.px, 48.px, 0); addView(input) }
        val b = Ui.dialog(this)
            .setTitle(getString(R.string.multiview_feed_title, i + 1))
            .setView(box)
            .setPositiveButton(R.string.multiview_save) { _, _ -> save(i, input.text.toString(), old) }
            .setNegativeButton(R.string.multiview_cancel, null)
        if (old != null) b.setNeutralButton(R.string.multiview_remove) { _, _ -> commit(i, null) }
        b.show()
    }

    private fun save(i: Int, text: String, old: Feed?) {
        var feed = when (val r = Feed.parse(text)) {
            is Feed.Parsed.Error -> { toast(errorText(r.reason)); return }
            is Feed.Parsed.Ok -> r.feed
        }
        // Editing a camera without retyping its login keeps the login it had (it is never shown in the box).
        if (feed.user == null && old?.kind == Feed.Kind.CAMERA && old.user != null &&
            old.host.equals(feed.host, true) && old.port == feed.port && feed.url.startsWith("https://")) {
            feed = feed.copy(kind = Feed.Kind.CAMERA, user = old.user, password = old.password)
        }
        if (feed.kind != Feed.Kind.CAMERA) return commit(i, feed)
        val oldPin = old?.pin?.takeIf { old.host.equals(feed.host, true) && old.port == feed.port }
        val wait = Ui.dialog(this).setMessage(R.string.multiview_checking_camera).setCancelable(false).show()
        val host = feed.host
        val port = feed.port
        probes.execute {
            val result = runCatching { PinnedTls.probe(host, port) }
            main.post {
                wait.dismiss()
                if (isFinishing || isDestroyed) return@post
                result.onFailure { toast(getString(R.string.multiview_probe_failed, it.message ?: it.javaClass.simpleName)) }
                result.onSuccess { pin ->
                    if (pin == oldPin) commit(i, feed.copy(pin = pin))
                    else confirmPin(feed, pin) { commit(i, feed.copy(pin = pin)) }
                }
            }
        }
    }

    private fun confirmPin(feed: Feed, pin: String, trusted: () -> Unit) {
        Ui.dialog(this)
            .setTitle(R.string.multiview_trust_title)
            .setMessage(getString(R.string.multiview_trust_message, feed.host + ":" + feed.port, pin))
            .setPositiveButton(R.string.multiview_trust) { _, _ -> trusted() }
            .setNegativeButton(R.string.multiview_cancel, null)
            .show()
    }

    private fun commit(i: Int, feed: Feed?) {
        feeds[i] = feed
        store.save(feeds)
        if (feed != null && feeds.count { it != null } == 1) { audio = i; store.audio = i }
        buildTiles()
    }

    private fun errorText(reason: Feed.Reason) = getString(when (reason) {
        Feed.Reason.EMPTY -> R.string.multiview_error_empty
        Feed.Reason.NOT_HTTP -> R.string.multiview_error_not_http
        Feed.Reason.NO_HOST -> R.string.multiview_error_no_host
        Feed.Reason.LOGIN_NEEDS_HTTPS -> R.string.multiview_error_login_https
        Feed.Reason.CAMERA_NEEDS_HTTPS -> R.string.multiview_error_camera_https
        Feed.Reason.BAD_URL -> R.string.multiview_error_bad_url
    })

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val STAGGER_MS = 3_000L
        private const val HOLD_MS = 600L
    }
}
