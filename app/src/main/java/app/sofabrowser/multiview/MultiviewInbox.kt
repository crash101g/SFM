package app.sofabrowser.multiview

/**
 * The browser's "Add to Multiview" (the page) and "Send to Multiview" (the video playing), handed over in memory.
 * MultiviewActivity is exported (for the launcher) and so ignores everything in the intent that starts it; another app
 * could put a feed there. Nothing outside
 * SFM's own process can reach this. An address is taken once, and only within [TTL_MS] of being left, so one that
 * never arrived (Multiview failed to open) doesn't turn up as a surprise feed later.
 */
object MultiviewInbox {

    /** What the browser hands over: an address, and for a video the page and browser it was fetched by. */
    data class Handoff(val url: String, val video: Boolean = false, val frame: String? = null, val media: Boolean = false,
                       val userAgent: String? = null)

    private var pending: Handoff? = null
    private var at = 0L

    const val TTL_MS = 30_000L

    @Synchronized
    fun put(handoff: Handoff, now: Long = System.currentTimeMillis()) {
        pending = handoff
        at = now
    }

    @Synchronized
    fun take(now: Long = System.currentTimeMillis()): Handoff? {
        val handoff = pending.takeIf { now - at in 0..TTL_MS }
        pending = null
        return handoff
    }
}
