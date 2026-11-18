package app.sofabrowser.multiview

/**
 * The browser's "Add to Multiview": the page address, handed over in memory. MultiviewActivity is exported (for the
 * launcher) and so ignores everything in the intent that starts it; another app could put a feed there. Nothing outside
 * SFM's own process can reach this. An address is taken once, and only within [TTL_MS] of being left, so one that
 * never arrived (Multiview failed to open) doesn't turn up as a surprise feed later.
 */
object MultiviewInbox {

    private var pending: String? = null
    private var at = 0L

    const val TTL_MS = 30_000L

    @Synchronized
    fun put(url: String, now: Long = System.currentTimeMillis()) {
        pending = url
        at = now
    }

    @Synchronized
    fun take(now: Long = System.currentTimeMillis()): String? {
        val url = pending.takeIf { now - at in 0..TTL_MS }
        pending = null
        return url
    }
}
