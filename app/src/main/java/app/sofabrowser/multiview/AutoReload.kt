package app.sofabrowser.multiview

/**
 * When a web tile reloads itself (as the desktop multiview's `auto_reload` does). Plain Kotlin with the time passed in,
 * so the rules are unit-tested; [WebTile] feeds it what its page reports and acts on [check].
 *
 * - A feed that hasn't started playing [afterMs] after loading is reloaded; the wait grows with each try (×2, ×3 …).
 *   After [maxTries] automatic reloads it gives up and says so. [waitFor] gives the wait for a grid of web tiles: they
 *   share the TV's one WebView process, so with four, each takes minutes to start (its Cloudflare check included), and
 *   reloading sooner only restarts that check and gets the stream refused.
 * - A feed that has played is reloaded only for a player error screen still there [ERROR_HOLD_MS] later, while nothing
 *   on the page plays. Paused or buffering feeds are never reloaded; nor is a broken ad video beside a working stream.
 * - A page refused with 401 or 403 is never reloaded, as cameras never retry a refused login.
 * - A reload by hand starts the count over.
 */
class AutoReload(private val afterMs: Long = AFTER_MS, private val maxTries: Int = MAX_TRIES) {

    enum class Action { NONE, RELOAD, GIVE_UP }

    private var loadedAt = 0L
    private var played = false
    private var lastPlaying = Long.MIN_VALUE
    private var errorSince: Long? = null
    private var tries = 0
    private var gaveUp = false
    private var refused = false

    /** Automatic reloads so far, for "try 2 of 3". */
    val attempts get() = tries

    /** The tile (re)loaded its page; by hand ([manual]) the count starts over. */
    fun loaded(now: Long, manual: Boolean) {
        if (manual) { tries = 0; gaveUp = false }
        loadedAt = now
        played = false
        lastPlaying = Long.MIN_VALUE
        errorSince = null
        refused = false
    }

    /** A video in some frame of the page is playing (its time moves). */
    fun playing(now: Long) {
        played = true
        lastPlaying = now
        errorSince = null
    }

    /** A frame shows a player error with nothing playing in it. */
    fun error(now: Long) {
        if (errorSince == null) errorSince = now
    }

    /** The page's main document answered 401 or 403. */
    fun refusedLogin() {
        refused = true
    }

    /**
     * What to do now; RELOAD and GIVE_UP are given once each, until the next [loaded]. With [allowed] false (another
     * feed is reloading) a due reload waits, and is given at a later check.
     */
    fun check(now: Long, allowed: Boolean = true, afterMs: Long = this.afterMs): Action {
        if (refused || gaveUp) return Action.NONE
        val due = if (!played) {
            now - loadedAt >= afterMs * (tries + 1)
        } else {
            val since = errorSince
            since != null && now - since >= ERROR_HOLD_MS && now - lastPlaying > PLAYING_FRESH_MS
        }
        if (!due) return Action.NONE
        if (tries < maxTries && !allowed) return Action.NONE
        if (tries >= maxTries) { gaveUp = true; return Action.GIVE_UP }
        tries++
        return Action.RELOAD
    }

    companion object {
        const val AFTER_MS = 20_000L
        /** The first wait per web tile in the grid: one tile 60 s, four tiles 4 minutes. */
        const val AFTER_MS_PER_WEB_TILE = 60_000L

        /** The first wait for a grid with [webTiles] web tiles. */
        fun waitFor(webTiles: Int): Long = AFTER_MS_PER_WEB_TILE * webTiles.coerceAtLeast(1)
        const val MAX_TRIES = 3
        const val ERROR_HOLD_MS = 2_000L
        /** A "playing" report this recent means something on the page still plays (pages report every 2 s). */
        const val PLAYING_FRESH_MS = 5_000L
    }
}
