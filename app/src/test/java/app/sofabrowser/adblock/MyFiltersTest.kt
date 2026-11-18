package app.sofabrowser.adblock

import jp.hazuki.yuzubrowser.adblock.getContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyFiltersTest {

    private fun compile(text: String) = CompiledList.parse(text.reader().buffered())

    private fun RuleSet.blocks(url: String, page: String = "https://stream.example/watch"): Boolean {
        val u = RequestUrl(url)
        val pageUrl = RequestUrl(page)
        return pageAllowed(pageUrl) && blocks(u, getContentType(u, false, mapOf("Accept" to "*/*")), pageUrl.host)
    }

    /** The lines the bundled StevenBlack hosts list has for these players. */
    private val hosts = """
        0.0.0.0 connatix.com
        0.0.0.0 cdn.connatix.com
        0.0.0.0 vid.connatix.com
        0.0.0.0 jwpltx.com
        0.0.0.0 prd.jwpltx.com
        0.0.0.0 entitlements.jwplayer.com
        0.0.0.0 ads.example
    """.trimIndent()

    @Test fun everyInstallHasMyFiltersAndItIsLocal() {
        val mine = FilterList.defaults().single { it.local }
        assertEquals(FilterList.MY_FILTERS, mine.id)
        assertTrue(mine.local)
        assertFalse(mine.bundled)
        assertNull(mine.url)
        assertTrue(mine.enabled)
        // An install from before My filters gets it added.
        val (complete, _) = FilterList.complete(FilterList.defaults().filterNot { it.local })
        assertTrue(complete.any { it.local })
    }

    @Test fun theDefaultExceptionsUnblockThePlayersAgainstTheHostsList() {
        val without = RuleSet.Builder().apply { add(compile(hosts)) }.build()
        assertTrue(without.blocks("https://cdn.connatix.com/player.js"))
        assertTrue(without.blocks("https://entitlements.jwplayer.com/v1/check"))

        val with = RuleSet.Builder().apply { add(compile(FilterList.MY_FILTERS_DEFAULT)); add(compile(hosts)) }.build()
        assertFalse(with.blocks("https://cdn.connatix.com/player.js"))
        assertFalse(with.blocks("https://vid.connatix.com/x.m3u8"))
        assertFalse(with.blocks("https://prd.jwpltx.com/v1/ping"))
        assertFalse(with.blocks("https://entitlements.jwplayer.com/v1/check"))
        // Everything else the lists block stays blocked.
        assertTrue(with.blocks("https://ads.example/banner.js"))
    }

    @Test fun myFiltersCanAlsoBlock() {
        val r = RuleSet.Builder().apply { add(compile("||annoying.example^\n")); add(compile(hosts)) }.build()
        assertTrue(r.blocks("https://cdn.annoying.example/a.js"))
    }

    @Test fun theDefaultIsWithinTheLimitAndHasThreeRules() {
        assertTrue(FilterList.MY_FILTERS_DEFAULT.length < FilterList.MY_FILTERS_MAX)
        assertEquals(3, compile(FilterList.MY_FILTERS_DEFAULT).rules)
    }
}
