package app.sofabrowser.multiview

import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException

/**
 * Reads a camera's stream over https to its pinned certificate only (see [PinnedTls]). The login goes in a Basic
 * Authorization header, and only to the camera's own host and port; any other address (a playlist pointing
 * elsewhere, a redirect) is refused. Redirects aren't followed at all.
 */
@UnstableApi
class CameraDataSource(private val feed: Feed, private val tls: PinnedTls) : BaseDataSource(true) {

    private var connection: HttpURLConnection? = null
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val target = dataSpec.uri
        if (!"https".equals(target.scheme, true) || !feed.host.equals(target.host, true) ||
            (if (target.port > 0) target.port else 443) != feed.port) {
            throw HttpDataSource.HttpDataSourceException("Camera feeds load only from the camera itself",
                dataSpec, androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        }
        val conn = (URL(target.toString()).openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = tls.socketFactory
            hostnameVerifier = tls.hostnameVerifier
            instanceFollowRedirects = false
            useCaches = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "SFM")
            setRequestProperty("Accept-Encoding", "identity")
            if (feed.user != null) {
                val basic = Base64.encodeToString("${feed.user}:${feed.password.orEmpty()}".toByteArray(), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $basic")
            }
            if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
                val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) "" else (dataSpec.position + dataSpec.length - 1).toString()
                setRequestProperty("Range", "bytes=${dataSpec.position}-$end")
            }
        }
        connection = conn
        val code = try {
            conn.responseCode
        } catch (e: SSLHandshakeException) {
            close()
            throw HttpDataSource.HttpDataSourceException(e, dataSpec,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        } catch (e: IOException) {
            close()
            throw HttpDataSource.HttpDataSourceException.createForIOException(e, dataSpec, HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        }
        if (code !in 200..299) {
            val headers = conn.headerFields.filterKeys { it != null }
            val message = conn.responseMessage
            close()
            throw HttpDataSource.InvalidResponseCodeException(code, message, null, headers, dataSpec, ByteArray(0))
        }
        val stream = conn.inputStream
        input = stream
        // A server that ignored the Range header sends the whole thing: skip to the position asked for.
        if (code == 200 && dataSpec.position > 0) {
            var skip = dataSpec.position
            while (skip > 0) {
                val n = stream.skip(skip)
                if (n <= 0) {
                    if (stream.read() < 0) break
                    skip--
                } else skip -= n
            }
        }
        val length = conn.contentLengthLong
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            length >= 0 -> if (code == 200) length - dataSpec.position else length
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val want = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), bytesRemaining).toInt()
        val n = try {
            input!!.read(buffer, offset, want)
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(e, DataSpec(uri!!), HttpDataSource.HttpDataSourceException.TYPE_READ)
        }
        if (n < 0) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        connection?.headerFields?.filterKeys { it != null } ?: emptyMap()

    override fun close() {
        runCatching { input?.close() }
        input = null
        connection?.disconnect()
        connection = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val feed: Feed, val tls: PinnedTls) : DataSource.Factory {
        override fun createDataSource(): DataSource = CameraDataSource(feed, tls)
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
    }
}
