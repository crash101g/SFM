package app.sofabrowser.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTest {

    private fun ok(input: String) = (Feed.parse(input) as Feed.Parsed.Ok).feed
    private fun error(input: String) = (Feed.parse(input) as Feed.Parsed.Error).reason

    @Test fun webPage() {
        val f = ok("  https://example.com/live/stream-a ")
        assertEquals(Feed.Kind.WEB, f.kind)
        assertEquals("https://example.com/live/stream-a", f.url)
    }

    @Test fun directStreams() {
        assertEquals(Feed.Kind.STREAM, ok("https://cdn.example.com/hls/master.m3u8?token=1").kind)
        assertEquals(Feed.Kind.STREAM, ok("http://cdn.example.com/a.mpd").kind)
        assertEquals(Feed.Kind.STREAM, ok("https://cdn.example.com/clip.MP4").kind)
    }

    @Test fun onlyHttpAndHttps() {
        for (bad in listOf("rtsp://192.168.1.50/live", "rtsps://cam/1", "file:///sdcard/x.mp4", "content://x/y",
            "javascript:alert(1)", "data:text/html,hi", "intent://x#Intent;end", "ftp://example.com/a.ts", "example.com")) {
            assertEquals(bad, Feed.Reason.NOT_HTTP, error(bad))
        }
        assertEquals(Feed.Reason.EMPTY, error("   "))
    }

    @Test fun iframeTag() {
        val f = ok("""<iframe width="560" src="https://player.example.com/embed/1?a=1&amp;b=2" allowfullscreen></iframe>""")
        assertEquals(Feed.Kind.WEB, f.kind)
        assertEquals("https://player.example.com/embed/1?a=1&b=2", f.url)
        assertEquals("https://player.example.com/e", ok("<iframe src='//player.example.com/e'>").url)
        assertEquals(Feed.Reason.NOT_HTTP, error("""<iframe src="javascript:alert(1)"></iframe>"""))
    }

    @Test fun cameraLoginIsKeptOutOfTheUrl() {
        val f = ok("https://admin:p%40ss:w0rd@192.168.1.60:8443/live/stream.m3u8")
        assertEquals(Feed.Kind.CAMERA, f.kind)
        assertEquals("https://192.168.1.60:8443/live/stream.m3u8", f.url)
        assertEquals("admin", f.user)
        assertEquals("p@ss:w0rd", f.password)
        assertFalse(f.label.contains("w0rd"))
        assertEquals("admin@192.168.1.60:8443", f.label)
        assertEquals(8443, f.port)
        assertNull(f.pin)
    }

    @Test fun loginsNeverGoOverPlainHttp() {
        assertEquals(Feed.Reason.LOGIN_NEEDS_HTTPS, error("http://admin:pw@192.168.1.60/live.m3u8"))
        assertEquals(Feed.Reason.LOGIN_NEEDS_HTTPS, error("http://user:pw@example.com/page"))
    }

    @Test fun localStreamsAreCamerasAndNeedHttps() {
        assertEquals(Feed.Kind.CAMERA, ok("https://cam.local/live.m3u8").kind)
        assertEquals(Feed.Kind.CAMERA, ok("https://10.0.0.5/live.m3u8").kind)
        assertEquals(Feed.Reason.CAMERA_NEEDS_HTTPS, error("http://192.168.1.60/live.m3u8"))
        // A local web page (a camera's own viewer page) is a web feed.
        assertEquals(Feed.Kind.WEB, ok("http://192.168.1.60/").kind)
    }

    @Test fun localHosts() {
        for (h in listOf("10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.0.10", "169.254.1.1", "127.0.0.1", "cam.local",
            "[fe80::1]", "[fd00::5]", "[::1]")) assertTrue(h, Feed.isLocalHost(h))
        for (h in listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.0.1", "example.com", "[2001:db8::1]", "1.2.3"))
            assertFalse(h, Feed.isLocalHost(h))
    }

    @Test fun loginFieldsMakeACameraAndNeedHttps() {
        val f = (Feed.parse("https://cam.example.com/live.m3u8", " admin ", "pw") as Feed.Parsed.Ok).feed
        assertEquals(Feed.Kind.CAMERA, f.kind)
        assertEquals("admin", f.user)
        assertEquals("pw", f.password)
        assertEquals(Feed.Reason.LOGIN_NEEDS_HTTPS, (Feed.parse("http://cam.example.com/live.m3u8", "admin", "pw") as Feed.Parsed.Error).reason)
        // The fields win over a login in the address.
        assertEquals("b", (Feed.parse("https://a:x@cam.local/live.m3u8", "b", "y") as Feed.Parsed.Ok).feed.user)
        // Empty fields leave the address as it is.
        assertEquals(Feed.Kind.WEB, (Feed.parse("https://example.com/", "  ", "") as Feed.Parsed.Ok).feed.kind)
    }

    @Test fun thePhoneNeverSendsALogin() {
        assertEquals(Feed.Reason.LOGIN_OVER_PHONE, (Feed.parseFromPhone("https://admin:pw@192.168.1.60/live.m3u8") as Feed.Parsed.Error).reason)
        assertEquals(Feed.Reason.LOGIN_OVER_PHONE, (Feed.parseFromPhone("<iframe src=\"https://u:p@example.com/e\">") as Feed.Parsed.Error).reason)
        assertEquals(Feed.Reason.NOT_HTTP, (Feed.parseFromPhone("rtsp://192.168.1.60/live") as Feed.Parsed.Error).reason)
        // Without a login a camera address is fine: its certificate is still trusted on the TV.
        val cam = (Feed.parseFromPhone("https://192.168.1.60/live.m3u8") as Feed.Parsed.Ok).feed
        assertEquals(Feed.Kind.CAMERA, cam.kind)
        assertNull(cam.pin)
        assertEquals(Feed.Kind.WEB, (Feed.parseFromPhone("https://example.com/live") as Feed.Parsed.Ok).feed.kind)
    }
}
