package app.sofabrowser.multiview

import java.net.URI

/**
 * One Multiview tile's source. Plain Kotlin with no Android types, so the rules are unit-tested.
 *
 * - [Kind.WEB]: a web page or embed player, shown in a locked-down WebView.
 * - [Kind.STREAM]: a direct HLS, DASH or progressive stream, played by ExoPlayer.
 * - [Kind.CAMERA]: a stream from an IP camera on the home network, or one that needs a login. Always https, and only
 *   to the one certificate the user trusted when adding it ([pin]); the login goes only to that host.
 *
 * [url] never holds a login: [user] and [password] are kept apart, so nothing that shows or logs the address can
 * leak them.
 */
data class Feed(
    val kind: Kind,
    val url: String,
    val user: String? = null,
    val password: String? = null,
    /** SHA-256 of the camera's certificate, as [CertPin.format] writes it; null until the user trusts one. */
    val pin: String? = null,
) {
    enum class Kind { WEB, STREAM, CAMERA }

    val host: String get() = runCatching { URI(url).host }.getOrNull().orEmpty()

    /** The port the URL connects to. */
    val port: Int get() = runCatching { URI(url).let { if (it.port > 0) it.port else if (it.scheme == "https") 443 else 80 } }.getOrDefault(443)

    /** For the tile's title bar: the host, with "user@" in front for a camera that logs in (never the password). */
    val label: String get() = (if (user != null) "$user@" else "") + host + if (port != 443 && port != 80) ":$port" else ""

    sealed class Parsed {
        data class Ok(val feed: Feed) : Parsed()
        data class Error(val reason: Reason) : Parsed()
    }

    enum class Reason { EMPTY, NOT_HTTP, NO_HOST, LOGIN_NEEDS_HTTPS, CAMERA_NEEDS_HTTPS, BAD_URL }

    companion object {
        const val MAX = 4

        private val STREAM_EXTENSIONS = listOf(".m3u8", ".mpd", ".mp4", ".m4v", ".ts", ".webm", ".mkv", ".mp3", ".aac", ".m4a")
        private val IFRAME_SRC = Regex("""<iframe\b[^>]*?\bsrc\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)

        /**
         * Reads what the user typed: an address, or an `<iframe>` tag copied from a page (its src is used). Only http
         * and https are accepted: no rtsp, file, content, data, javascript or intent addresses. A login in the address
         * ("https://user:pass@host/…") makes the feed a camera, which must be https; so must a stream on a private
         * or link-local address or a ".local" host, since those carry self-signed certificates that only a pin can
         * check.
         */
        fun parse(input: String): Parsed {
            var text = input.trim()
            if (text.isEmpty()) return Parsed.Error(Reason.EMPTY)
            IFRAME_SRC.find(text)?.let { text = it.groupValues[2].trim().replace("&amp;", "&") }
            if (text.startsWith("//")) text = "https:$text"
            val uri = runCatching { URI(text) }.getOrElse { return Parsed.Error(Reason.BAD_URL) }
            val scheme = uri.scheme?.lowercase() ?: return Parsed.Error(Reason.NOT_HTTP)
            if (scheme != "http" && scheme != "https") return Parsed.Error(Reason.NOT_HTTP)
            val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return Parsed.Error(Reason.NO_HOST)
            val login = uri.rawUserInfo
            // Rebuilt from its raw parts without the login, so nothing outside the Feed ever sees it.
            val clean = scheme + "://" + host + (if (uri.port > 0) ":${uri.port}" else "") + uri.rawPath.orEmpty()
                .ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
            val stream = isStreamPath(uri.rawPath.orEmpty())
            val local = isLocalHost(host)
            if (login != null) {
                if (scheme != "https") return Parsed.Error(Reason.LOGIN_NEEDS_HTTPS)
                val colon = login.indexOf(':')
                val user = decode(if (colon < 0) login else login.substring(0, colon))
                val password = if (colon < 0) "" else decode(login.substring(colon + 1))
                return Parsed.Ok(Feed(Kind.CAMERA, clean, user, password))
            }
            if (stream && local) {
                if (scheme != "https") return Parsed.Error(Reason.CAMERA_NEEDS_HTTPS)
                return Parsed.Ok(Feed(Kind.CAMERA, clean))
            }
            return Parsed.Ok(Feed(if (stream) Kind.STREAM else Kind.WEB, clean))
        }

        fun isStreamPath(path: String): Boolean {
            val p = path.lowercase()
            return STREAM_EXTENSIONS.any { p.endsWith(it) }
        }

        /** Private (RFC 1918), loopback, link-local and unique-local addresses, and mDNS ".local" names. */
        fun isLocalHost(host: String): Boolean {
            val h = host.lowercase().removePrefix("[").removeSuffix("]")
            if (h.endsWith(".local") || h == "localhost") return true
            if (':' in h) return h == "::1" || h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") ||
                    h.startsWith("feb") || h.startsWith("fc") || h.startsWith("fd")
            val parts = h.split('.')
            if (parts.size != 4) return false
            val b = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
            return b[0] == 10 || b[0] == 127 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
                    (b[0] == 169 && b[1] == 254)
        }

        private fun decode(s: String) = java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    }
}
