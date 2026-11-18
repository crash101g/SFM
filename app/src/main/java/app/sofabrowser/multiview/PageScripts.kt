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
     * Sound: one tile has it. Every frame keeps its audio and video elements muted until SFM says otherwise, also when
     * the page tries to unmute them; the top frame passes the setting down to its frames.
     */
    val MUTE = """
        (function () {
          if (window.__sfmSetMuted) return;
          var muted = true;
          function media(t) { return t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO'); }
          function apply() {
            try {
              var els = document.querySelectorAll('video,audio');
              for (var i = 0; i < els.length; i++) {
                if (els[i].muted !== muted) els[i].muted = muted;
                if (!muted && els[i].volume === 0) els[i].volume = 1;
              }
            } catch (e) {}
          }
          function relay() {
            for (var i = 0; i < window.frames.length; i++) {
              try { window.frames[i].postMessage({ __sfmMute: muted }, '*'); } catch (e) {}
            }
          }
          function set(m) { muted = !!m; apply(); relay(); }
          document.addEventListener('play', function (e) { if (media(e.target)) e.target.muted = muted; }, true);
          document.addEventListener('playing', function (e) { if (media(e.target)) e.target.muted = muted; }, true);
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

    fun setMuted(muted: Boolean) = "window.__sfmSetMuted && window.__sfmSetMuted($muted);"
}
