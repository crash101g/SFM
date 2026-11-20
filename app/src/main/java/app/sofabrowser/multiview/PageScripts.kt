package app.sofabrowser.multiview

/** Scripts every web tile runs at document start, in every frame. */
object PageScripts {

    /**
     * WebRTC off: removes the peer-to-peer API before the page's own scripts run, so P2P video-sharing scripts see
     * "no WebRTC" and fall back to plain downloads (no upload of the stream to strangers, no IP leak through ICE). Frames
     * get it too; same-origin child windows a page makes itself (about:blank iframes, where document-start scripts may
     * not run) are cleaned the moment the page reaches them through contentWindow, and when they are added. Camera and
     * microphone requests are refused separately (onPermissionRequest).
     */
    val NO_WEBRTC = """
        (function () {
          var names = ['RTCPeerConnection', 'webkitRTCPeerConnection', 'mozRTCPeerConnection', 'RTCDataChannel',
            'RTCSessionDescription', 'RTCIceCandidate', 'RTCRtpSender', 'RTCRtpReceiver', 'RTCRtpTransceiver',
            'RTCSctpTransport', 'RTCDtlsTransport', 'RTCIceTransport', 'RTCCertificate', 'RTCTrackEvent',
            'RTCPeerConnectionIceEvent', 'RTCDataChannelEvent', 'RTCDTMFSender', 'RTCError', 'RTCErrorEvent',
            'RTCEncodedAudioFrame', 'RTCEncodedVideoFrame', 'RTCRtpScriptTransform'];
          function scrub(w) {
            if (!w || w.__sfmNoRtc) return;
            for (var i = 0; i < names.length; i++) {
              try { Object.defineProperty(w, names[i], { value: undefined, writable: false, configurable: false }); }
              catch (e) { try { delete w[names[i]]; } catch (_) {} }
            }
            try { Object.defineProperty(w.navigator, 'mediaDevices', { value: undefined, configurable: false }); } catch (e) {}
            try { Object.defineProperty(w.navigator, 'getUserMedia', { value: undefined, configurable: false }); } catch (e) {}
            try { Object.defineProperty(w.navigator, 'webkitGetUserMedia', { value: undefined, configurable: false }); } catch (e) {}
            try { Object.defineProperty(w, '__sfmNoRtc', { value: true, configurable: false }); } catch (e) {}
          }
          scrub(window);
          try {
            var d = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow');
            Object.defineProperty(HTMLIFrameElement.prototype, 'contentWindow', {
              configurable: false, enumerable: d.enumerable,
              get: function () { var w = d.get.call(this); try { scrub(w); } catch (e) {} return w; }
            });
          } catch (e) {}
          try {
            new MutationObserver(function (list) {
              for (var i = 0; i < list.length; i++) {
                var added = list[i].addedNodes;
                for (var j = 0; j < added.length; j++) {
                  var n = added[j];
                  if (n.tagName === 'IFRAME') { try { scrub(n.contentWindow); } catch (e) {} }
                }
              }
            }).observe(document, { childList: true, subtree: true });
          } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Pages' own "Unmute" buttons, hidden in tiles only: there SFM decides which tile is heard (and re-mutes a page that
     * unmutes itself), so they do nothing but cover the picture. In the browser they stay, as they are the page's only
     * way to turn its sound on. CSS only, and exact names, never "contains", so it can't hide a whole player whose class
     * merely mentions muting.
     */
    val HIDE_UNMUTE = """
        (function () {
          if (window.__sfmHideUnmute) return;
          try { Object.defineProperty(window, '__sfmHideUnmute', { value: true }); } catch (e) {}
          var css = '#unmute,#unmute-button,#unmuteButton,#unmute-btn,.unmute,.unmute-button,.unmute-btn,.unmuteButton,' +
            'button[aria-label="Unmute" i],button[title="Unmute" i]{display:none!important}';
          function add() {
            var root = document.documentElement;
            if (!root) return false;
            var st = document.createElement('style');
            st.textContent = css;
            (document.head || root).appendChild(st);
            return true;
          }
          if (!add()) document.addEventListener('DOMContentLoaded', add, { once: true });
        })();
    """.trimIndent()

    /**
     * Sound: one tile has it. Every frame keeps its audio and video elements muted until SFM says otherwise, also when
     * the page tries to unmute them; the top frame passes the setting down to its frames. Moving the sound must not stop
     * a feed: each tile is its own media session, and Android pauses the one that loses audio focus, so a video paused
     * just after a switch is started again (muted, it doesn't take focus back).
     */
    val MUTE = """
        (function () {
          if (window.__sfmSetMuted) return;
          var muted = true;
          // When SFM last switched the sound. Android gives audio focus to the tile that starts making sound, and the
          // WebView pauses the one that loses it; a video paused within KEEP_MS of a switch is started again.
          var switched = 0, KEEP_MS = 15000;
          function resume(v) {
            if (v.ended || !v.paused || Date.now() - switched > KEEP_MS) return;
            try { var p = v.play(); if (p && p.catch) p.catch(function () {}); } catch (e) {}
          }
          function media(t) { return t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO'); }
          function apply() {
            try {
              var els = document.querySelectorAll('video,audio');
              for (var i = 0; i < els.length; i++) {
                if (els[i].muted !== muted) els[i].muted = muted;
                if (!muted && els[i].volume === 0) els[i].volume = 1;
                if (els[i].paused && els[i].currentTime > 0) resume(els[i]);
              }
            } catch (e) {}
          }
          function relay() {
            for (var i = 0; i < window.frames.length; i++) {
              try { window.frames[i].postMessage({ __sfmMute: muted }, '*'); } catch (e) {}
            }
          }
          // Only a real change counts as a switch: the top frame repeats the setting to its frames every 2 s.
          function set(m) { if (muted !== !!m) switched = Date.now(); muted = !!m; apply(); relay(); }
          document.addEventListener('play', function (e) { if (media(e.target)) e.target.muted = muted; }, true);
          document.addEventListener('playing', function (e) { if (media(e.target)) e.target.muted = muted; }, true);
          document.addEventListener('pause', function (e) {
            var v = e.target;
            if (media(v)) setTimeout(function () { resume(v); }, 300);
          }, true);
          document.addEventListener('volumechange', function (e) {
            if (media(e.target) && e.target.muted !== muted) e.target.muted = muted;
          }, true);
          window.addEventListener('message', function (e) {
            if (e.source === window.parent && e.source !== window && e.data && typeof e.data.__sfmMute === 'boolean') set(e.data.__sfmMute);
          });
          Object.defineProperty(window, '__sfmSetMuted', { value: set, writable: false, configurable: false });
          setInterval(function () { apply(); if (window.parent === window) relay(); }, 2000);
        })();
    """.trimIndent()

    /**
     * Auto-reload ([AutoReload]): every 2 s each frame tells SFM, over its own channel [key], whether a video in it is
     * playing (its time moves) or shows a player error with nothing playing (a failed video, or JW Player's or video.js's
     * error screen). It sends one letter and takes nothing back. A page that fakes it can only keep its own feed from
     * being reloaded, or have it reloaded, at most 3 times.
     */
    fun watch(key: String) = """
        (function () {
          var ch = window['$key'];
          if (!ch || window.__sfmWatch) return;
          try { Object.defineProperty(window, '__sfmWatch', { value: true }); } catch (e) {}
          var last = new WeakMap(), named = false;
          setInterval(function () {
            var playing = false, failed = false;
            var vs = document.querySelectorAll('video');
            for (var i = 0; i < vs.length; i++) {
              var v = vs[i], t = v.currentTime, before = last.get(v);
              last.set(v, t);
              if (!v.paused && !v.ended && v.readyState >= 3 && before !== undefined && t > before) playing = true;
              if (v.error) failed = true;
            }
            if (!playing) {
              var e = document.querySelector('.jw-error, .jw-state-error, .vjs-error');
              if (e && e.getClientRects().length) failed = true;
            }
            try { if (playing) ch.postMessage('p'); else if (failed) ch.postMessage('e'); } catch (e) {}
            // Player only: a frame sitting directly in the page whose video plays names itself, once.
            try {
              if (playing && !named && window !== window.top && window.parent === window.top) { named = true; ch.postMessage('f' + location.href); }
            } catch (e) {}
          }, 2000);
        })();
    """.trimIndent()

    fun setMuted(muted: Boolean) = "window.__sfmSetMuted && window.__sfmSetMuted($muted);"
}
