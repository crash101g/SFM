package app.sofabrowser.multiview

import java.net.InetSocketAddress
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS for cameras: the handshake succeeds only when the camera presents the exact certificate the user pinned, so the
 * login is sent to nothing else. Hostname checks are replaced by the pin (cameras' self-signed certificates rarely
 * name their IP address); the pin is stricter than a CA chain plus hostname, not looser.
 */
class PinnedTls(private val pin: String, private val host: String) {

    /** Set when the last handshake failed because the certificate didn't match, so the tile can say so. */
    @Volatile var mismatch = false
        private set

    private val trust = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            throw CertificateException("not a server")

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull()
            if (!CertPin.matches(pin, leaf?.encoded)) {
                mismatch = true
                throw CertificateException("camera certificate doesn't match its pin")
            }
            mismatch = false
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    val socketFactory: SSLSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }.socketFactory

    /** The pin already proved who the server is; this only keeps the connection to the camera's own host. */
    val hostnameVerifier = HostnameVerifier { h, _ -> h.equals(host, ignoreCase = true) }

    companion object {
        /**
         * Connects to [host]:[port] and returns the SHA-256 pin of the certificate it presents, for the user to look at
         * and accept. Only the TLS handshake happens: no HTTP request is made and no login is sent.
         */
        fun probe(host: String, port: Int, timeoutMs: Int = 8_000): String {
            var seen: X509Certificate? = null
            val capture = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                    throw CertificateException("not a server")

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    seen = chain?.firstOrNull()
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val factory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(capture), null) }.socketFactory
            val raw = java.net.Socket()
            raw.use {
                it.connect(InetSocketAddress(host, port), timeoutMs)
                it.soTimeout = timeoutMs
                (factory.createSocket(it, host, port, true) as SSLSocket).use { tls -> tls.startHandshake() }
            }
            val cert = seen ?: throw CertificateException("the camera presented no certificate")
            return CertPin.of(cert.encoded)
        }
    }
}
