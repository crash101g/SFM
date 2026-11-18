package app.sofabrowser.multiview

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import android.webkit.CookieManager
import app.sofabrowser.R
import app.sofabrowser.browser.PageHeaders

/**
 * A direct stream or an IP camera, played by ExoPlayer. Only http and https are loaded, by the player and by every
 * playlist entry it follows; a camera loads only from itself, over TLS to its pinned certificate ([CameraDataSource]).
 * A 401 or 403 is never retried, by the player or by the tile ([Reconnect]); other errors reconnect with a growing wait.
 */
@OptIn(UnstableApi::class)
@SuppressLint("ViewConstructor")
class StreamTile(context: Context, index: Int, feed: Feed) : Tile(context, index, feed) {

    private val surface = SurfaceView(context)
    private var player: ExoPlayer? = null
    private var tls: PinnedTls? = null
    private var attempt = 0
    private var stopped = false

    init {
        setContent(surface)
    }

    override fun start() {
        release()
        stopped = false
        if (feed.kind == Feed.Kind.CAMERA && feed.pin == null) {
            fail(context.getString(R.string.multiview_camera_untrusted))
            return
        }
        showStatus(context.getString(R.string.multiview_connecting))
        val sources: DataSource.Factory = if (feed.kind == Feed.Kind.CAMERA) {
            val pinned = PinnedTls(feed.pin!!, feed.host)
            tls = pinned
            CameraDataSource.Factory(feed, pinned)
        } else {
            val userAgent = feed.userAgent ?: USER_AGENT
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                // https never falls back to http on a redirect.
                .setAllowCrossProtocolRedirects(false)
                .setConnectTimeoutMs(TIMEOUT_MS)
                .setReadTimeoutMs(TIMEOUT_MS)
            // Playlists can name any address; anything that isn't http(s) is refused before it's opened.
            ResolvingDataSource.Factory(http) { spec ->
                val scheme = spec.uri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") throw HttpDataSource.HttpDataSourceException(
                    "Only http and https streams play in Multiview", spec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, HttpDataSource.HttpDataSourceException.TYPE_OPEN)
                // A video sent from the browser: asked for as the page's player asked (as Sofa Player does), with the
                // site's cookies read fresh from the browser, only where that player would have sent them.
                val frame = feed.frame ?: return@Factory spec
                spec.withAdditionalHeaders(PageHeaders.forRequest(spec.uri.toString(), frame, userAgent,
                    { url -> runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull() }, media = feed.media) - "User-Agent")
            }
        }
        val p = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(sources).setLoadErrorHandlingPolicy(NoLoginRetry()))
            // Four players share the TV's memory: a few seconds of buffer each is plenty for live feeds.
            .setLoadControl(DefaultLoadControl.Builder().setTargetBufferBytes(BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(false).build())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), false)
            .build()
        p.setVideoSurfaceView(surface)
        p.volume = if (audible) 1f else 0f
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> { attempt = 0; showStatus(null) }
                    // A live feed that "ends" has dropped; go again.
                    Player.STATE_ENDED -> retry()
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val stop = Reconnect.stopFor(responseCode(error), tls?.mismatch == true)
                if (stop != null) {
                    fail(context.getString(when (stop) {
                        Reconnect.Stop.LOGIN_REJECTED -> R.string.multiview_login_rejected
                        // A browser video's address often carries a token that runs out.
                        Reconnect.Stop.FORBIDDEN -> if (feed.frame != null) R.string.multiview_video_expired else R.string.multiview_forbidden
                        Reconnect.Stop.CERT_CHANGED -> R.string.multiview_cert_changed
                    }))
                } else retry()
            }
        })
        // A file (a video sent from the browser, say) loops; a live stream that ends has dropped (onPlaybackStateChanged).
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.setMediaItem(MediaItem.fromUri(Uri.parse(feed.url)))
        p.playWhenReady = true
        p.prepare()
        player = p
    }

    private fun retry() {
        if (stopped) return
        releasePlayer()
        attempt++
        val wait = Reconnect.delayMs(attempt)
        showStatus(context.getString(R.string.multiview_reconnecting, (wait / 1000).toInt()))
        main.postDelayed({ if (!stopped) start() }, wait)
    }

    /** Stops for good, with [message]; only the user starts it again (Reload, or editing the feed). */
    private fun fail(message: String) {
        stopped = true
        main.removeCallbacksAndMessages(null)
        releasePlayer()
        showStatus(message)
    }

    override fun reload() {
        attempt = 0
        start()
    }

    override fun release() {
        main.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun applyAudio(on: Boolean) {
        player?.volume = if (on) 1f else 0f
    }

    /** Never lets the player retry a load the server refused for the login (or a pin that no longer matches). */
    private inner class NoLoginRetry : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            if (Reconnect.stopFor(responseCode(loadErrorInfo.exception), tls?.mismatch == true) != null) return C.TIME_UNSET
            return super.getRetryDelayMsFor(loadErrorInfo)
        }
    }

    companion object {
        private const val USER_AGENT = "SFM"
        private const val TIMEOUT_MS = 10_000
        private const val BUFFER_BYTES = 16 * 1024 * 1024

        /** The HTTP status behind an error, if there was one. */
        fun responseCode(error: Throwable?): Int? {
            var e = error
            var depth = 0
            while (e != null && depth++ < 10) {
                if (e is HttpDataSource.InvalidResponseCodeException) return e.responseCode
                e = e.cause
            }
            return null
        }
    }
}
