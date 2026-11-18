package app.sofabrowser.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiviewInboxTest {

    @Test fun takenOnce() {
        MultiviewInbox.put(MultiviewInbox.Handoff("https://example.com/"), now = 1_000)
        assertEquals("https://example.com/", MultiviewInbox.take(now = 2_000)?.url)
        assertNull(MultiviewInbox.take(now = 2_000))
    }

    @Test fun expires() {
        MultiviewInbox.put(MultiviewInbox.Handoff("https://example.com/"), now = 1_000)
        assertNull(MultiviewInbox.take(now = 1_000 + MultiviewInbox.TTL_MS + 1))
        // Gone after the expired take too.
        assertNull(MultiviewInbox.take(now = 1_001))
    }

    @Test fun latestWins() {
        MultiviewInbox.put(MultiviewInbox.Handoff("https://a.example/"), now = 1_000)
        MultiviewInbox.put(MultiviewInbox.Handoff("https://b.example/"), now = 1_500)
        assertEquals("https://b.example/", MultiviewInbox.take(now = 2_000)?.url)
    }
}
