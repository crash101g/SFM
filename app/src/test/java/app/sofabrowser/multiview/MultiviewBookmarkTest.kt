package app.sofabrowser.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiviewBookmarkTest {

    private fun ok(s: String) = (Feed.parse(s) as Feed.Parsed.Ok).feed

    @Test fun roundTripsPagesAndStreamsInOrder() {
        val feeds = listOf(ok("https://a.example/watch?id=1&x=2"), null, ok("https://b.example/live/index.m3u8"), null)
        val url = MultiviewBookmark.of(feeds)!!
        assertTrue(MultiviewBookmark.isOne(url))
        val back = MultiviewBookmark.feeds(url)
        assertEquals(listOf("https://a.example/watch?id=1&x=2", "https://b.example/live/index.m3u8"), back.map { it.url })
        assertEquals(listOf(Feed.Kind.WEB, Feed.Kind.STREAM), back.map { it.kind })
        assertEquals("Multiview: a.example, b.example", MultiviewBookmark.title(back))
    }

    @Test fun camerasAndLoginsNeverGoIntoABookmark() {
        val camera = ok("https://admin:secret@cam.example/live.m3u8")
        val local = ok("https://192.168.1.20/stream.m3u8")
        val url = MultiviewBookmark.of(listOf(camera, local, ok("https://a.example/")))!!
        assertTrue(!url.contains("secret") && !url.contains("admin") && !url.contains("192.168"))
        assertEquals(2, MultiviewBookmark.leftOut(listOf(camera, local, ok("https://a.example/"))))
        assertNull(MultiviewBookmark.of(listOf(camera, local)))
    }

    @Test fun aVideoFromTheBrowserIsBookmarkedAsItsPage() {
        val video = (Feed.fromBrowser("https://cdn.example/x.m3u8?token=1", "https://site.example/watch/1", false, "UA")
            as Feed.Parsed.Ok).feed
        val back = MultiviewBookmark.feeds(MultiviewBookmark.of(listOf(video))!!)
        assertEquals(listOf("https://site.example/watch/1"), back.map { it.url })
    }

    @Test fun anEditedBookmarkCanOnlyHoldWhatAFeedMay() {
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        val url = MultiviewBookmark.PREFIX + listOf(
            "javascript:alert(1)", "file:///sdcard/x", "intent://x#Intent;end", "https://u:p@cam.example/a.m3u8",
            "https://10.0.0.5/live.m3u8", "http://ok.example/", "https://ok.example/2", "https://ok.example/3",
            "https://ok.example/4", "https://ok.example/5",
        ).joinToString("&") { "f=" + enc(it) }
        val back = MultiviewBookmark.feeds(url)
        assertEquals(listOf("http://ok.example/", "https://ok.example/2", "https://ok.example/3", "https://ok.example/4"),
            back.map { it.url })
        assertTrue(back.all { it.user == null && it.kind != Feed.Kind.CAMERA })
        assertEquals(emptyList<Feed>(), MultiviewBookmark.feeds("https://a.example/"))
        assertEquals(emptyList<Feed>(), MultiviewBookmark.feeds(MultiviewBookmark.PREFIX + "f=%E0%A4%A"))
    }
}
