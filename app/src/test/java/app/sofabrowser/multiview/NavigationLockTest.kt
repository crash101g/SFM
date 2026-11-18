package app.sofabrowser.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLockTest {

    @Test fun staysOnItsSite() {
        val lock = NavigationLock("https://www.example.com/live")
        assertTrue(lock.allows("https://player.example.com/embed/2", redirect = false))
        assertTrue(lock.allows("http://example.com/other", redirect = false))
        assertFalse(lock.allows("https://ads.example.net/landing", redirect = false))
    }

    @Test fun otherSchemesAreAlwaysRefused() {
        val lock = NavigationLock("https://example.com/")
        for (u in listOf("intent://example.com#Intent;end", "market://details?id=x", "javascript:alert(1)", "file:///sdcard",
            "data:text/html,x", "about:blank")) assertFalse(u, lock.allows(u, redirect = true))
    }

    @Test fun crossSiteRedirectOnlyWhileTheFirstPageLoads() {
        val lock = NavigationLock("https://example.com/")
        assertTrue(lock.allows("https://example.tv/live", redirect = true))
        // The site it landed on is home now.
        assertTrue(lock.allows("https://cdn.example.tv/x", redirect = false))
        lock.pageFinished()
        assertFalse(lock.allows("https://ad.example.org/", redirect = true))
        assertFalse(lock.allows("https://example.com/", redirect = false))
    }

    @Test fun scriptNavigationToAnotherSiteIsRefusedEvenAtFirst() {
        val lock = NavigationLock("https://example.com/")
        assertFalse(lock.allows("https://popunder.example.org/", redirect = false))
    }

    @Test fun loginInTheAddressDoesNotFoolTheHost() {
        val lock = NavigationLock("https://example.com/")
        assertFalse(lock.allows("https://example.com@evil.example.org/", redirect = false))
        assertEquals("example.org", NavigationLock.siteOf("https://example.com:pw@www.example.org:8443/x"))
    }

    @Test fun resetStartsOver() {
        val lock = NavigationLock("https://example.com/")
        lock.pageFinished()
        lock.reset("https://other.example.net/")
        assertTrue(lock.allows("https://www.example.net/", redirect = false))
        assertFalse(lock.allows("https://example.com/", redirect = false))
    }

    @Test fun siteOf() {
        assertEquals("bbc.co.uk", NavigationLock.siteOf("https://www.bbc.co.uk/iplayer"))
        assertEquals("192.168.1.5", NavigationLock.siteOf("http://192.168.1.5:8080/"))
        assertNull(NavigationLock.siteOf("ftp://example.com/"))
        assertNull(NavigationLock.siteOf("https:///nohost"))
    }
}
