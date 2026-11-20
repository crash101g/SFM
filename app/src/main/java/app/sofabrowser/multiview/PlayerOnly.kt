package app.sofabrowser.multiview

import java.net.URI

/**
 * "Player only" web tiles. Many stream pages are an ad-laden page around one embedded player frame; on the onn, four of
 * those pages share one WebView process and fill its memory. Once a tile has seen its video play in a frame that sits
 * directly in the page, it remembers that frame's address ([Feed.player]) and from then on loads [page]: a bare page
 * holding only that frame, under the feed's own address (WebView's loadDataWithBaseURL), so the player is embedded and
 * referred as it was on the site. Measured on one such feed: 8 frames instead of 193, a third of the JavaScript heap,
 * and none of the page's pop-up ads or trap links. Plain Kotlin, so the rules are unit-tested.
 */
object PlayerOnly {

    private const val MAX_URL = 2048

    /**
     * The player frame a page reported, or null if it can't be used: http(s), at most [MAX_URL] characters, from the
     * origin WebView vouches for ([origin], so a frame can only name itself), and not the page itself.
     */
    fun learn(reported: String, origin: String, page: String): String? {
        if (reported.length > MAX_URL) return null
        val u = runCatching { URI(reported) }.getOrNull() ?: return null
        val scheme = u.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = u.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if (u.rawUserInfo != null) return null
        val port = if (u.port > 0) ":${u.port}" else ""
        if (!origin.trimEnd('/').equals("$scheme://$host$port", ignoreCase = true)) return null
        if (reported.substringBefore('#') == page.substringBefore('#')) return null
        return reported.substringBefore('#')
    }

    /** Whether a saved [player] address may be loaded: the same checks, against its own origin. */
    fun valid(player: String): Boolean {
        val u = runCatching { URI(player) }.getOrNull() ?: return false
        val origin = "${u.scheme}://${u.host}${if (u.port > 0) ":${u.port}" else ""}"
        return learn(player, origin, "") == player
    }

    /** The bare page: one full-size frame holding [player], which must have passed [learn]. */
    fun page(player: String): String {
        val src = player.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">" +
            "<style>html,body{margin:0;height:100%;overflow:hidden;background:#000}" +
            "iframe{display:block;border:0;width:100%;height:100%}</style></head><body>" +
            "<iframe src=\"$src\" allow=\"autoplay; fullscreen; encrypted-media; picture-in-picture\" allowfullscreen>" +
            "</iframe></body></html>"
    }
}
