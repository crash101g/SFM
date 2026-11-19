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
import app.sofabrowser.browser.CursorLayout
import app.sofabrowser.phone.PhoneRemote
import app.sofabrowser.phone.PhoneServer
import app.sofabrowser.ui.Ui
import app.sofabrowser.ui.px
import java.util.concurrent.Executors

/**
 * SFM's Multiview: up to four feeds on one screen, one of them with the sound.
 *
 * Remote: the arrows pick a tile; OK gives it the sound; holding OK, or Menu, opens the options; 1–4 give that feed the
 * sound and 0 mutes all; Back leaves full screen, then Multiview.
 *
 * The paired phone works here too (Menu → Phone remote shows the QR code): its remote and trackpad move around like the
 * D-pad, and its Multiview section sends feeds. Those go through the same checks as typed ones, never carry a camera
 * login (the phone link is plain http), and a camera's certificate is still trusted only here, on the TV.
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
    /** Clean view ([FeedStore.clean]): no title bars or frame, and Left/Right give the sound to that side. */
    private var clean = false
    private var okDownAt = -1L
    private var menuFromHold = false

    /** The feeds as last built, for the phone's server threads. */
    @Volatile private var shown: List<Feed?> = List(Feed.MAX) { null }
    private val phone by lazy { PhoneRemote(this, phoneHost) }

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
        clean = store.clean
        buildTiles()
        phone.start()
    }

    override fun onResume() {
        super.onResume()
        // "Add to Multiview" from the browser (in memory, never from the intent).
        MultiviewInbox.take()?.let { h ->
            if (h.feeds != null) showBookmark(h.feeds) else add(h.url, fromPhone = false, video = h.takeIf { it.video })
        }
    }

    /**
     * A Multiview bookmark: shows its [set] instead of the current feeds. Asks first if that would drop a feed the
     * bookmark doesn't have (a camera's login and trusted certificate would go with it).
     */
    private fun showBookmark(set: List<Feed>) {
        val urls = set.map { it.url }.toSet()
        val dropped = feeds.filterNotNull().filter { it.url !in urls && it.frame !in urls }
        val apply = {
            feeds = (set + List(Feed.MAX - set.size) { null }).toMutableList()
            store.save(feeds)
            if (audio >= set.size) { audio = 0; store.audio = 0 }
            selected = 0
            buildTiles()
        }
        if (set.map { it.url } == feeds.map { it?.url }.filterNotNull() && feeds.drop(set.size).all { it == null }) return
        if (dropped.isEmpty()) return apply()
        Ui.dialog(this)
            .setTitle(R.string.multiview_bookmark_replace_title)
            .setMessage(getString(R.string.multiview_bookmark_replace_message, dropped.joinToString("\n") { it.label }))
            .setPositiveButton(R.string.multiview_bookmark_replace) { _, _ -> apply() }
            .setNegativeButton(R.string.multiview_cancel, null)
            .show()
    }

    /** Saves the current feeds as one bookmark that opens Multiview with them ([MultiviewBookmark]). */
    private fun bookmarkFeeds() {
        val url = MultiviewBookmark.of(feeds) ?: return toast(getString(R.string.multiview_bookmark_none))
        val title = MultiviewBookmark.title(MultiviewBookmark.feeds(url))
        app.sofabrowser.data.Db.get(this).addBookmark(title, url)
        val left = MultiviewBookmark.leftOut(feeds)
        toast(getString(R.string.multiview_bookmark_saved, title) +
            if (left > 0) "\n" + resources.getQuantityString(R.plurals.multiview_bookmark_cameras, left, left) else "")
    }

    /** Puts [input] in the first empty slot, or asks which feed to replace; the usual checks and camera trust apply. */
    private fun add(input: String, fromPhone: Boolean, video: MultiviewInbox.Handoff? = null) {
        // Already here (added twice, or the button pressed again): no copy. A video sent again from the same page
        // takes its old slot, with the fresh stream address.
        val same = Feed.slotOf(feeds, input, video?.frame)
        if (same >= 0) {
            if (video != null) return save(same, input, feeds[same], fromPhone = fromPhone, video = video)
            tiles.indexOfFirst { it.index == same }.takeIf { it >= 0 }?.let { selected = it; refreshMarks() }
            return toast(getString(R.string.multiview_already, same + 1))
        }
        val free = feeds.indexOfFirst { it == null }
        if (free >= 0) return save(free, input, null, fromPhone = fromPhone, video = video)
        val names = (0 until Feed.MAX).map { getString(R.string.multiview_slot, it + 1, feeds[it]?.label.orEmpty()) }
        Ui.dialog(this)
            .setTitle(R.string.multiview_replace_title)
            .setItems(names.toTypedArray()) { _, which -> save(which, input, feeds[which], fromPhone = fromPhone, video = video) }
            .setNegativeButton(R.string.multiview_cancel, null)
            .show()
    }

    override fun onStop() {
        // Nothing plays in the background: four decoders and pages are let go as soon as Multiview is left.
        phone.stop()
        releaseTiles()
        super.onStop()
    }

    override fun onDestroy() {
        phone.destroy()
        probes.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildTiles() {
        releaseTiles()
        shown = feeds.toList()
        val present = feeds.withIndex().filter { it.value != null }
        empty.visibility = if (present.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        present.forEachIndexed { pos, (i, feed) ->
            val tile = if (feed!!.kind == Feed.Kind.WEB) WebTile(this, i, feed, reloadGate) else StreamTile(this, i, feed)
            tile.titlesShown = titles && !clean
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
            t.highlighted = pos == selected && tiles.size > 1 && !clean
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
        if (clean && grid.solo < 0) return moveSound(dx, dy)
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

    /** Clean view: the tile to that side (or this one, at the edge) is picked and gets the sound; the rest are muted. */
    private fun moveSound(dx: Int, dy: Int) {
        if (tiles.isEmpty()) return
        val cols = 2
        val col = selected % cols + dx
        val row = selected / cols + dy
        val next = row * cols + col
        if (col in 0 until cols && row >= 0 && next in tiles.indices && !(tiles.size == 2 && dy != 0)) selected = next
        val tile = tiles[selected]
        if (audio != tile.index) { audio = tile.index; store.audio = audio; refreshMarks() }
        tile.flashSound()
    }

    private fun setClean(on: Boolean) {
        clean = on
        store.clean = on
        tiles.forEach { it.titlesShown = titles && !clean }
        refreshMarks()
        if (on) toast(getString(R.string.multiview_clean_hint))
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
        items += getString(if (store.autoReload) R.string.multiview_menu_autoreload_on else R.string.multiview_menu_autoreload_off) to
            { store.autoReload = !store.autoReload }
        items += getString(if (clean) R.string.multiview_menu_clean_on else R.string.multiview_menu_clean_off) to { setClean(!clean) }
        if (!clean) items += getString(R.string.multiview_menu_titles) to { titles = !titles; tiles.forEach { it.titlesShown = titles } }
        items += getString(R.string.multiview_menu_edit) to { editFeeds() }
        items += getString(R.string.multiview_menu_bookmark) to { bookmarkFeeds() }
        items += getString(R.string.multiview_menu_phone) to { phone.pairDialog() }
        Ui.dialog(this)
            .setTitle(tile.feed.label)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    /** Auto-reload takes turns: one feed reloads, and the next waits [RELOAD_GAP_MS] (more than a page's start). */
    private val reloadGate = object : WebTile.ReloadGate {
        private var nextAt = 0L
        override val enabled get() = store.autoReload
        override fun free(now: Long) = now >= nextAt
        override fun reserve(now: Long) { nextAt = now + RELOAD_GAP_MS }
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
            .setNeutralButton(R.string.multiview_menu_phone) { _, _ -> phone.pairDialog() }
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
        // The login has fields of its own: easier than typing "user:password@" into an address with the TV keyboard,
        // and the only way to give one to a feed sent from the phone. The password is never filled in or shown.
        val user = EditText(this).apply {
            setText(old?.user.orEmpty())
            setHint(R.string.multiview_user_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSingleLine = true
        }
        val password = EditText(this).apply {
            setHint(if (old?.password.isNullOrEmpty()) R.string.multiview_password_hint else R.string.multiview_password_keep)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSingleLine = true
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48.px, 16.px, 48.px, 0)
            addView(input)
            addView(Ui.text(context, 22, Ui.p.textMid).apply { setText(R.string.multiview_login_label); setPadding(0, 24.px, 0, 0) })
            addView(user)
            addView(password)
        }
        val b = Ui.dialog(this)
            .setTitle(getString(R.string.multiview_feed_title, i + 1))
            .setView(box)
            .setPositiveButton(R.string.multiview_save) { _, _ ->
                // An empty password box keeps the stored one, for the same user.
                val pw = password.text.toString().ifEmpty { old?.password?.takeIf { old.user == user.text.toString().trim() } }
                save(i, input.text.toString(), old, user.text.toString(), pw)
            }
            .setNegativeButton(R.string.multiview_cancel, null)
        if (old != null) b.setNeutralButton(R.string.multiview_remove) { _, _ -> commit(i, null) }
        b.show()
    }

    private fun save(i: Int, text: String, old: Feed?, user: String? = null, password: String? = null, fromPhone: Boolean = false,
                     video: MultiviewInbox.Handoff? = null) {
        val parsed = when {
            video != null -> Feed.fromBrowser(text, video.frame, video.media, video.userAgent)
            fromPhone -> Feed.parseFromPhone(text)
            else -> Feed.parse(text, user, password)
        }
        var feed = when (val r = parsed) {
            is Feed.Parsed.Error -> { toast(errorText(r.reason)); return }
            is Feed.Parsed.Ok -> r.feed
        }
        // A video from the browser, saved again unchanged in Edit feeds, keeps the page it plays for.
        if (video == null && old?.frame != null && feed.url == old.url && feed.kind != Feed.Kind.CAMERA) {
            feed = feed.copy(kind = Feed.Kind.STREAM, frame = old.frame, media = old.media, userAgent = old.userAgent)
        }
        // A camera address without a login (from the phone, or the fields left empty) keeps the login it had.
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
        if (feed != null && feeds[i] == null) toast(getString(R.string.multiview_added, i + 1))
        feeds[i] = feed
        store.save(feeds)
        if (feed != null && feeds.count { it != null } == 1) { audio = i; store.audio = i }
        // The feed with the sound was removed: the first one left gets it, rather than nothing being heard.
        if (feed == null && audio == i) feeds.indexOfFirst { it != null }.let { audio = it; store.audio = it }
        buildTiles()
    }

    private fun errorText(reason: Feed.Reason) = getString(when (reason) {
        Feed.Reason.EMPTY -> R.string.multiview_error_empty
        Feed.Reason.NOT_HTTP -> R.string.multiview_error_not_http
        Feed.Reason.NO_HOST -> R.string.multiview_error_no_host
        Feed.Reason.LOGIN_NEEDS_HTTPS -> R.string.multiview_error_login_https
        Feed.Reason.CAMERA_NEEDS_HTTPS -> R.string.multiview_error_camera_https
        Feed.Reason.BAD_URL -> R.string.multiview_error_bad_url
        Feed.Reason.LOGIN_OVER_PHONE -> R.string.multiview_error_login_phone
    })

    // The phone (PhoneRemote): the trackpad and arrows work as the D-pad, and feeds come in through setFeed.

    private val phoneHost = object : PhoneRemote.Host {
        override val cursor: CursorLayout? = null
        override val web: android.webkit.WebView? = null
        override fun pageInFront() = false
        override fun openFromPhone(url: String) {}
        override fun zoom(zoomIn: Boolean) {}
        override fun removeAtPointer() {}
        override fun cleanUp() {}

        /** "Open" (and the iPhone Shortcut) adds the address to the first empty slot, or asks on the TV which to replace. */
        override fun openRawFromPhone(input: String): Boolean {
            if (Feed.parseFromPhone(input) is Feed.Parsed.Error) return false
            main.post { add(input, fromPhone = true) }
            return true
        }

        override fun feeds(): List<String?> = shown.map { it?.label }

        override fun setFeed(slot: Int, input: String): PhoneServer.FeedReply {
            if (slot !in 0 until Feed.MAX) return PhoneServer.FeedReply.BAD_SLOT
            if (input.isEmpty()) {
                main.post { if (feeds[slot] != null) commit(slot, null) }
                return PhoneServer.FeedReply.OK
            }
            val parsed = when (val r = Feed.parseFromPhone(input)) {
                is Feed.Parsed.Ok -> r.feed
                is Feed.Parsed.Error -> return when (r.reason) {
                    Feed.Reason.LOGIN_OVER_PHONE -> PhoneServer.FeedReply.LOGIN_OVER_PHONE
                    Feed.Reason.NOT_HTTP -> PhoneServer.FeedReply.NOT_HTTP
                    else -> PhoneServer.FeedReply.INVALID
                }
            }
            main.post { save(slot, input, feeds[slot], fromPhone = true) }
            // A camera, or the camera already in this slot at a new address (it keeps its login): its certificate is
            // checked, and trusted if it changed, at the TV.
            val old = shown[slot]
            val camera = parsed.kind == Feed.Kind.CAMERA ||
                old?.kind == Feed.Kind.CAMERA && old.host.equals(parsed.host, true) && old.port == parsed.port
            return if (camera) PhoneServer.FeedReply.CONFIRM_ON_TV else PhoneServer.FeedReply.OK
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val STAGGER_MS = 3_000L
        private const val HOLD_MS = 600L
        private const val RELOAD_GAP_MS = 10_000L
    }
}
