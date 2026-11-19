package app.sofabrowser.multiview

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A bookmark that opens Multiview with a set of feeds ("sfm:multiview?f=…&f=…"), made with Multiview's "Bookmark these
 * feeds". Plain Kotlin, so the rules are unit-tested.
 *
 * - It holds web pages and streams only. Cameras are left out: their logins and pinned certificates stay in the
 *   encrypted feed list, never in a bookmark. So is a video sent from the browser, whose stream address is short-lived
 *   and needs its page's cookies; its page is bookmarked instead.
 * - Opening one puts every address through [Feed.parseFromPhone]: http(s) only, no logins, and anything that would be a
 *   camera is dropped.
 * - Only the browser's own bookmark, home-screen and address-bar choices open it; links from other apps and the phone
 *   are http(s) only, and pages can't navigate to it.
 */
object MultiviewBookmark {

    const val PREFIX = "sfm:multiview?"

    fun isOne(url: String) = url.startsWith(PREFIX)

    /** The bookmark address for [feeds], or null when none of them can be bookmarked. */
    fun of(feeds: List<Feed?>): String? {
        val urls = feeds.mapNotNull { f -> f?.let(::bookmarkable) }.distinct().take(Feed.MAX)
        if (urls.isEmpty()) return null
        return PREFIX + urls.joinToString("&") { "f=" + URLEncoder.encode(it, "UTF-8") }
    }

    /** How many of [feeds] [of] leaves out (cameras). */
    fun leftOut(feeds: List<Feed?>) = feeds.count { it != null && bookmarkable(it) == null }

    /** The bookmark's title: "Multiview: site, site". */
    fun title(feeds: List<Feed>) = "Multiview: " + feeds.joinToString(", ") { it.host.removePrefix("www.") }

    /** The most a bookmark's name keeps, in characters. */
    const val MAX_NAME = 80

    /** The name typed for a bookmark: trimmed, on one line, at most [MAX_NAME] characters; [default] when left blank. */
    fun name(typed: String, default: String): String =
        typed.replace(Regex("\\s+"), " ").trim().take(MAX_NAME).trim().ifEmpty { default }

    /** The feeds in a bookmark address, in order; empty if it holds none that may be used. */
    fun feeds(url: String): List<Feed> {
        if (!isOne(url)) return emptyList()
        return url.removePrefix(PREFIX).split('&')
            .filter { it.startsWith("f=") }
            .mapNotNull { runCatching { URLDecoder.decode(it.removePrefix("f="), "UTF-8") }.getOrNull() }
            .mapNotNull { (Feed.parseFromPhone(it) as? Feed.Parsed.Ok)?.feed?.takeIf { f -> f.kind != Feed.Kind.CAMERA } }
            .distinctBy { it.url }
            .take(Feed.MAX)
    }

    private fun bookmarkable(f: Feed): String? = when {
        f.kind == Feed.Kind.CAMERA || f.user != null -> null
        f.frame != null -> f.frame
        else -> f.url
    }
}
