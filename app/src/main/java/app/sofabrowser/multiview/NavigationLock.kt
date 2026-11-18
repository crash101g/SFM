package app.sofabrowser.multiview

import app.sofabrowser.adblock.Sites

/**
 * Keeps a web tile on the site it was given, so an ad can't send the feed somewhere else. Plain Kotlin, unit-tested.
 *
 * A main-frame navigation is allowed when it is http(s) and stays on the home site (same registrable domain, so
 * "www." and "player." hosts of the site are fine). A server redirect to another site is allowed only while the
 * feed's first page is still loading ("example.com" sending to "example.tv" on the way in), never after. Anything
 * that isn't http(s) (intent:, market:, javascript:, file:, data:…) is always refused. Sub-frames are not locked:
 * embed players live in them, and the ad blocker filters what they load.
 */
class NavigationLock(homeUrl: String) {

    private var homeSite: String = siteOf(homeUrl).orEmpty()
    private var firstPageDone = false

    /** The feed's first page has finished loading; cross-site redirects stop being allowed. */
    fun pageFinished() {
        firstPageDone = true
    }

    /** The user reloaded the feed or changed its address: start again from [url]. */
    fun reset(url: String) {
        homeSite = siteOf(url).orEmpty()
        firstPageDone = false
    }

    fun allows(url: String, redirect: Boolean): Boolean {
        val site = siteOf(url) ?: return false
        if (homeSite.isEmpty()) return false
        if (site == homeSite) return true
        if (redirect && !firstPageDone) {
            // The site the first page actually lands on becomes home.
            homeSite = site
            return true
        }
        return false
    }

    companion object {
        /** The registrable domain of an http(s) [url]; null for any other scheme or no host. */
        fun siteOf(url: String): String? {
            val u = url.trim()
            val lower = u.lowercase()
            val rest = when {
                lower.startsWith("https://") -> u.substring(8)
                lower.startsWith("http://") -> u.substring(7)
                else -> return null
            }
            var authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
            authority = authority.substringAfterLast('@')
            val host = if (authority.startsWith("[")) authority.substringBefore(']') + "]"
                       else authority.substringBefore(':')
            if (host.isEmpty()) return null
            return Sites.of(host.lowercase().trimEnd('.'))
        }
    }
}
