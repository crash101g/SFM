package app.sofabrowser.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerOnlyTest {

    private val page = "https://site.example/watch/1"

    @Test fun aFrameNamingItselfIsLearned() {
        assertEquals("https://player.example/embed?id=1&x=2",
            PlayerOnly.learn("https://player.example/embed?id=1&x=2#t", "https://player.example", page))
        assertEquals("http://p.example:8080/e", PlayerOnly.learn("http://p.example:8080/e", "http://p.example:8080/", page))
    }

    @Test fun aFrameCanNameOnlyItselfAndOnlyHttp() {
        // Another origin than the one WebView vouches for.
        assertNull(PlayerOnly.learn("https://evil.example/e", "https://player.example", page))
        assertNull(PlayerOnly.learn("javascript:alert(1)", "https://player.example", page))
        assertNull(PlayerOnly.learn("file:///sdcard/x", "null", page))
        assertNull(PlayerOnly.learn("https://u:p@player.example/e", "https://player.example", page))
        assertNull(PlayerOnly.learn("https://player.example/" + "x".repeat(3000), "https://player.example", page))
        // The page itself is not a player frame.
        assertNull(PlayerOnly.learn(page, "https://site.example", page))
    }

    @Test fun theBarePageHoldsOnlyTheFrameWithItsAddressEscaped() {
        val html = PlayerOnly.page("https://player.example/e?a=1&b=\"><script>")
        assertTrue(html.contains("src=\"https://player.example/e?a=1&amp;b=&quot;&gt;&lt;script&gt;\""))
        assertFalse(html.contains("<script"))
        assertEquals(1, Regex("<iframe").findAll(html).count())
    }

    @Test fun savedPlayersAreCheckedAgain() {
        assertTrue(PlayerOnly.valid("https://player.example/embed?id=1"))
        assertFalse(PlayerOnly.valid("intent://x#Intent;end"))
        assertFalse(PlayerOnly.valid("https://u:p@player.example/e"))
    }
}
