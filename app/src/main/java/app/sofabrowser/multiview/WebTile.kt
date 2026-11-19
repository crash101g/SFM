package app.sofabrowser.multiview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.sofabrowser.Prefs
import app.sofabrowser.R
import app.sofabrowser.adblock.AdBlocker
import app.sofabrowser.browser.WebViews
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * A web page or embed player. What it may do is narrower than Sofa's browser tab:
 *
 * - **Navigation lock** ([NavigationLock]): the feed stays on its own site; pages can't send it anywhere else.
 * - **No pop-ups**: every new window is refused, and nothing opens other apps (intent:, market: …).
 * - **WebRTC off** ([PageScripts.NO_WEBRTC]), and every camera, microphone or other permission request is denied.
 * - **Ad blocking**: Sofa's filter lists on every request of every frame.
 * - No file or content access, no geolocation, no mixed content (as Sofa's [WebViews.configure] sets them), and HTTP
 *   logins asked for by a page are cancelled, never retried.
 * - **Auto-reload** ([AutoReload]) when the feed never starts or its player shows an error, at most 3 times, one feed
 *   at a time ([gate]), never after a 401/403, and always to the feed's own address.
 */
@SuppressLint("ViewConstructor")
class WebTile(context: Context, index: Int, feed: Feed, private val gate: ReloadGate) : Tile(context, index, feed) {

    /** Shared by the tiles: whether auto-reload is on, and turns so only one feed reloads at a time. */
    interface ReloadGate {
        val enabled: Boolean
        fun free(now: Long): Boolean
        fun reserve(now: Long)
    }

    private val autoReload = AutoReload()
    /** The page's channel for auto-reload; a random name, so pages can't guess another tile's. */
    private val watchKey = "sfm" + java.util.UUID.randomUUID().toString().replace("-", "")
    private var watching = false
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (watching && gate.enabled) when (autoReload.check(now, gate.free(now))) {
                AutoReload.Action.RELOAD -> {
                    gate.reserve(now)
                    showStatus(context.getString(R.string.multiview_autoreload, autoReload.attempts, AutoReload.MAX_TRIES))
                    main.postDelayed({ showStatus(null) }, 4_000)
                    load(manual = false)
                }
                AutoReload.Action.GIVE_UP ->
                    showStatus(context.getString(R.string.multiview_autoreload_gave_up, AutoReload.MAX_TRIES))
                AutoReload.Action.NONE -> {}
            }
            main.postDelayed(this, TICK_MS)
        }
    }

    private val blocker = AdBlocker.get(context)
    private val lock = NavigationLock(feed.url)
    private var web: WebView? = null
    @Volatile private var pageUrl: String? = feed.url
    @Volatile private var pageHost: String? = feed.host
    private var scriptsAtStart = false

    override fun start() {
        if (web == null) build()
        load(manual = true)
        main.removeCallbacks(tick)
        main.postDelayed(tick, TICK_MS)
    }

    /** Loads the feed's own saved address, never wherever the page had gone, with the navigation lock reset to it. */
    private fun load(manual: Boolean) {
        val w = web ?: return
        autoReload.loaded(SystemClock.uptimeMillis(), manual)
        lock.reset(feed.url)
        w.stopLoading()
        w.loadUrl(feed.url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun build() {
        // As in Sofa's browser: test builds only, so pages can be inspected from a computer over adb.
        if (app.sofabrowser.BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        val w = WebView(context)
        WebViews.configure(w, Prefs(context))
        // Tiles present as what the TV is, Chrome on Android, whatever the browser's user-agent setting. Its default,
        // desktop Chrome on Windows, doesn't match what pages can see of an ARM TV (navigator.platform, no
        // window.chrome), and bot checks such as Cloudflare Turnstile refuse that, so stream pages that need one
        // never get their stream.
        WebViews.applyUserAgent(w, TILE_USER_AGENT, "")
        w.settings.javaScriptEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.settings.setSupportMultipleWindows(true)
        w.settings.javaScriptCanOpenWindowsAutomatically = false
        w.settings.allowFileAccess = false
        w.settings.allowContentAccess = false
        w.isFocusable = false
        w.isFocusableInTouchMode = false
        w.setBackgroundColor(android.graphics.Color.BLACK)
        scriptsAtStart = WebViews.addDocumentStartScript(w, PageScripts.NO_WEBRTC) &&
            WebViews.addDocumentStartScript(w, PageScripts.MUTE)
        // Auto-reload needs the page's reports; without message listeners the tile just doesn't reload by itself.
        watching = scriptsAtStart && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        if (watching) {
            WebViewCompat.addWebMessageListener(w, watchKey, setOf("*")) { _, message, _, _, _ ->
                val now = SystemClock.uptimeMillis()
                when (message.data) {
                    "p" -> autoReload.playing(now)
                    "e" -> autoReload.error(now)
                }
            }
            watching = WebViews.addDocumentStartScript(w, PageScripts.watch(watchKey))
        }
        w.webViewClient = Client()
        w.webChromeClient = Chrome()
        if (web == null) setContent(w) else replaceContent(w)
        web = w
        applyAudio(audible)
    }

    /** Reload by hand (the menu): the auto-reload count starts over. */
    override fun reload() {
        showStatus(null)
        if (web == null) return start()
        load(manual = true)
    }

    override fun release() {
        main.removeCallbacksAndMessages(null)
        web?.let {
            it.stopLoading()
            it.loadUrl("about:blank")
            (it.parent as? android.view.ViewGroup)?.removeView(it)
            it.destroy()
        }
        web = null
    }

    override fun applyAudio(on: Boolean) {
        web?.evaluateJavascript(PageScripts.setMuted(!on), null)
    }

    override fun tap() {
        val w = web ?: return
        val x = w.width / 2f
        val y = w.height / 2f
        val t = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { i, action ->
            val e = MotionEvent.obtain(t, t + i * 50, action, x, y, 0)
            e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            w.dispatchTouchEvent(e)
            e.recycle()
        }
    }

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val scheme = request.url.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return true
            if (!request.isForMainFrame) return false
            if (lock.allows(url, request.isRedirect)) return false
            blocker.countBlocked()
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pageUrl = url
            pageHost = Uri.parse(url).host
            if (!scriptsAtStart) {
                // Older WebViews: no document-start scripts, so the top frame gets them as early as it can.
                view.evaluateJavascript(PageScripts.NO_WEBRTC, null)
                view.evaluateJavascript(PageScripts.MUTE, null)
            }
            view.evaluateJavascript(PageScripts.setMuted(!audible), null)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (url != "about:blank") lock.pageFinished()
            if (!scriptsAtStart) view.evaluateJavascript(PageScripts.MUTE, null)
            view.evaluateJavascript(PageScripts.setMuted(!audible), null)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            blocker.intercept(request, pageUrl, pageHost)

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            // A page that refuses us is not reloaded again and again, as a camera never retries a refused login.
            val code = response.statusCode
            if (request.isForMainFrame && (code == 401 || code == 403)) {
                autoReload.refusedLogin()
                showStatus(context.getString(R.string.multiview_page_refused, code))
            }
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: android.webkit.HttpAuthHandler, host: String, realm: String) {
            // Pages don't get a login prompt, and a login isn't retried.
            handler.cancel()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // With several pages on a TV the system may stop a renderer to free memory. Without this the whole app
            // would go down with it; instead this tile starts over after a moment.
            if (view !== web) return true
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.destroy()
            web = null
            showStatus(context.getString(R.string.multiview_page_stopped))
            main.postDelayed({
                showStatus(null)
                build()
                start()
            }, 3_000)
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            blocker.countBlocked()
            return false
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: android.webkit.JsPromptResult): Boolean {
            result.cancel()
            return true
        }

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
            result.confirm()
            return true
        }
    }

    private companion object {
        const val TICK_MS = 2_000L
        /** [WebViews.applyUserAgent]'s mode for Chrome on Android, with client hints to match. */
        const val TILE_USER_AGENT = "mobile"
    }
}
