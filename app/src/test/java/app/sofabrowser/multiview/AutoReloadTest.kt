package app.sofabrowser.multiview

import app.sofabrowser.multiview.AutoReload.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoReloadTest {

    @Test fun reloadsAFeedThatNeverStartsWithGrowingWaits() {
        val r = AutoReload()
        r.loaded(0, manual = true)
        assertEquals(Action.NONE, r.check(19_999))
        assertEquals(Action.RELOAD, r.check(20_000))
        // Reloaded: the second wait is 40 s.
        r.loaded(20_000, manual = false)
        assertEquals(Action.NONE, r.check(59_999))
        assertEquals(Action.RELOAD, r.check(60_000))
        r.loaded(60_000, manual = false)
        assertEquals(Action.RELOAD, r.check(120_000))
        assertEquals(3, r.attempts)
        // After three, it gives up once and then stays quiet.
        r.loaded(120_000, manual = false)
        assertEquals(Action.GIVE_UP, r.check(200_000))
        assertEquals(Action.NONE, r.check(400_000))
    }

    @Test fun reloadByHandStartsOver() {
        val r = AutoReload()
        r.loaded(0, manual = true)
        r.check(20_000); r.loaded(20_000, false)
        r.check(60_000); r.loaded(60_000, false)
        r.check(120_000); r.loaded(120_000, false)
        assertEquals(Action.GIVE_UP, r.check(200_000))
        r.loaded(300_000, manual = true)
        assertEquals(0, r.attempts)
        assertEquals(Action.RELOAD, r.check(320_000))
    }

    @Test fun aFeedThatPlaysIsLeftAloneEvenPausedOrBuffering() {
        val r = AutoReload()
        r.loaded(0, true)
        r.playing(5_000)
        // Then nothing more is reported (paused, buffering): never reloaded.
        assertEquals(Action.NONE, r.check(500_000))
    }

    @Test fun anErrorScreenReloadsOnlyIfItStaysAndNothingPlays() {
        val r = AutoReload()
        r.loaded(0, true)
        r.playing(5_000)
        r.error(30_000)
        assertEquals(Action.NONE, r.check(31_000)) // not 2 s yet
        assertEquals(Action.RELOAD, r.check(32_000))
    }

    @Test fun anErrorBesideAPlayingVideoIsIgnored() {
        val r = AutoReload()
        r.loaded(0, true)
        r.playing(28_000)
        r.error(28_500) // a broken ad video next to the stream
        r.playing(30_000)
        r.error(30_500)
        assertEquals(Action.NONE, r.check(32_600))
    }

    @Test fun aPlayerThatRecoversIsLeftAlone() {
        val r = AutoReload()
        r.loaded(0, true)
        r.playing(5_000)
        r.error(30_000)
        r.playing(31_000) // recovered by itself: the error is forgotten
        assertEquals(Action.NONE, r.check(40_000))
    }

    @Test fun aRefusedLoginIsNeverReloaded() {
        val r = AutoReload()
        r.loaded(0, true)
        r.refusedLogin()
        assertEquals(Action.NONE, r.check(1_000_000))
        // Until it is loaded again (by hand).
        r.loaded(1_000_000, true)
        assertEquals(Action.RELOAD, r.check(1_020_000))
    }

    @Test fun aDueReloadWaitsItsTurn() {
        val r = AutoReload()
        r.loaded(0, true)
        // Another feed is reloading: nothing yet, and the try isn't used up.
        assertEquals(Action.NONE, r.check(20_000, allowed = false))
        assertEquals(0, r.attempts)
        assertEquals(Action.RELOAD, r.check(22_000, allowed = true))
        assertEquals(1, r.attempts)
    }

    @Test fun aFullGridWaitsLongerBeforeReloading() {
        assertEquals(60_000L, AutoReload.waitFor(1))
        assertEquals(240_000L, AutoReload.waitFor(4))
        assertEquals(60_000L, AutoReload.waitFor(0))
        val r = AutoReload()
        r.loaded(0, manual = true)
        // Four web tiles: still starting at 3 minutes is normal.
        assertEquals(Action.NONE, r.check(180_000, afterMs = AutoReload.waitFor(4)))
        assertEquals(Action.RELOAD, r.check(240_000, afterMs = AutoReload.waitFor(4)))
        // The next wait doubles.
        r.loaded(240_000, manual = false)
        assertEquals(Action.NONE, r.check(700_000, afterMs = AutoReload.waitFor(4)))
        assertEquals(Action.RELOAD, r.check(720_000, afterMs = AutoReload.waitFor(4)))
    }
}
