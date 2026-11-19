package app.sofabrowser.multiview

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedSlotTest {

    private fun ok(s: String) = (Feed.parse(s) as Feed.Parsed.Ok).feed

    private val feeds = listOf(ok("https://a.example/stream/1.php"), ok("https://a.example/stream/2.php"), null, null)

    @Test fun aPageAlreadyShownIsFoundNotAddedAgain() {
        assertEquals(1, Feed.slotOf(feeds, "https://a.example/stream/2.php", null))
        assertEquals(0, Feed.slotOf(feeds, "  https://a.example/stream/1.php ", null))
        assertEquals(-1, Feed.slotOf(feeds, "https://a.example/stream/3.php", null))
        assertEquals(-1, Feed.slotOf(feeds, "not a url", null))
    }

    @Test fun aVideoIsMatchedByItsPage() {
        val video = (Feed.fromBrowser("https://cdn.example/x.m3u8?t=1", "https://b.example/watch", false, null) as Feed.Parsed.Ok).feed
        val withVideo = listOf(video, null, null, null)
        // The same page's video again, with a new short-lived address: same slot.
        assertEquals(0, Feed.slotOf(withVideo, "https://cdn.example/x.m3u8?t=2", "https://b.example/watch"))
        // A video from a page that is itself a feed.
        assertEquals(1, Feed.slotOf(feeds, "https://cdn.example/y.m3u8", "https://a.example/stream/2.php"))
        assertEquals(-1, Feed.slotOf(withVideo, "https://cdn.example/x.m3u8?t=2", "https://c.example/other"))
    }
}
