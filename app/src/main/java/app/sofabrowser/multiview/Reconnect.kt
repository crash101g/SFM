package app.sofabrowser.multiview

/**
 * When a stream or camera tile tries again after an error. Plain Kotlin, unit-tested.
 *
 * A rejected login (401) or a refused one (403) is never retried, by the player or by the tile: retrying a wrong
 * password only locks the account on many cameras, and hammers it with the credentials. Neither is a certificate that
 * no longer matches its pin. Everything else is retried with a growing wait.
 */
object Reconnect {

    enum class Stop { LOGIN_REJECTED, FORBIDDEN, CERT_CHANGED }

    /** Why the tile must stop for good after an HTTP [code] (null when there was no HTTP response), or null to retry. */
    fun stopFor(code: Int?, certMismatch: Boolean = false): Stop? = when {
        certMismatch -> Stop.CERT_CHANGED
        code == 401 -> Stop.LOGIN_REJECTED
        code == 403 -> Stop.FORBIDDEN
        else -> null
    }

    /** Wait before reconnect number [attempt] (1, 2, …): 5 s, 10 s, 20 s, 40 s, then a minute at most. */
    fun delayMs(attempt: Int): Long {
        val n = attempt.coerceIn(1, 8)
        return minOf(5_000L shl (n - 1), 60_000L)
    }
}
