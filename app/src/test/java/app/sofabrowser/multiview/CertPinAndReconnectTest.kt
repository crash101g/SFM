package app.sofabrowser.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class CertPinAndReconnectTest {

    private val cert = "a camera certificate".toByteArray()
    private val other = "another certificate".toByteArray()

    @Test fun pinFormat() {
        val pin = CertPin.of(cert)
        assertEquals(95, pin.length)
        assertTrue(pin.matches(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")))
        assertEquals(CertPin.format(MessageDigest.getInstance("SHA-256").digest(cert)), pin)
    }

    @Test fun onlyThePinnedCertificateMatches() {
        val pin = CertPin.of(cert)
        assertTrue(CertPin.matches(pin, cert))
        assertTrue(CertPin.matches(pin.lowercase().replace(":", ""), cert))
        assertFalse(CertPin.matches(pin, other))
        assertFalse(CertPin.matches(null, cert))
        assertFalse(CertPin.matches(pin, null))
        assertFalse(CertPin.matches("", cert))
        assertFalse(CertPin.matches("AB:CD", cert))
        assertNull(CertPin.parse("zz".repeat(32)))
    }

    @Test fun loginErrorsAreNeverRetried() {
        assertEquals(Reconnect.Stop.LOGIN_REJECTED, Reconnect.stopFor(401))
        assertEquals(Reconnect.Stop.FORBIDDEN, Reconnect.stopFor(403))
        assertEquals(Reconnect.Stop.CERT_CHANGED, Reconnect.stopFor(null, certMismatch = true))
        assertEquals(Reconnect.Stop.CERT_CHANGED, Reconnect.stopFor(500, certMismatch = true))
    }

    @Test fun otherErrorsAreRetried() {
        for (code in listOf(null, 404, 500, 502, 503)) assertNull("$code", Reconnect.stopFor(code))
    }

    @Test fun backoff() {
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L), (1..6).map { Reconnect.delayMs(it) })
        assertEquals(5_000L, Reconnect.delayMs(0))
        assertEquals(60_000L, Reconnect.delayMs(1000))
    }
}
