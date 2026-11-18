package app.sofabrowser.multiview

import java.security.MessageDigest

/**
 * Camera certificate pins: the SHA-256 of the camera's own (leaf) certificate, written as 32 colon-separated hex
 * pairs. Cameras carry self-signed certificates no CA vouches for, so SFM trusts exactly the one the user saw and
 * accepted when adding the camera ("trust on first use"), and nothing else, ever: a changed certificate stops the
 * tile until the user trusts the new one. Plain Kotlin, unit-tested.
 */
object CertPin {

    fun of(encodedCert: ByteArray): String = format(MessageDigest.getInstance("SHA-256").digest(encodedCert))

    fun format(digest: ByteArray): String = digest.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    /** Constant-time comparison of a certificate against a stored pin; false when either is missing or malformed. */
    fun matches(pin: String?, encodedCert: ByteArray?): Boolean {
        if (pin == null || encodedCert == null) return false
        val want = parse(pin) ?: return false
        return MessageDigest.isEqual(want, MessageDigest.getInstance("SHA-256").digest(encodedCert))
    }

    /** The 32 bytes of a pin as [format] writes it (colons and case optional); null if it isn't one. */
    fun parse(pin: String): ByteArray? {
        val hex = pin.replace(":", "").trim()
        if (hex.length != 64 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
