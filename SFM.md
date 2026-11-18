# SFM

SFM is [the Sofa TV Browser fork](https://github.com/crash101g/sofa-tv-browser) (see [FORK.md](FORK.md)) with a
**Multiview** mode added: up to four web pages, streams and IP cameras on one TV screen, one of them with the sound.
It brings the desktop [multiview](https://github.com/crash101g/multiview) idea to Android TV, on Sofa's WebView, ad
blocking and ExoPlayer.

- Package: `app.sofabrowser.sfm` (test builds `app.sofabrowser.sfm.debug`), so it installs beside Sofa and the
  Sofa fork with its own data.
- Multiview: `app.sofabrowser.multiview.MultiviewActivity`, with its own launcher entry, **SFM Multiview**.
  The browser itself is unchanged and is the **SFM** entry.
- Tested on the Walmart onn 4K Pro (Android 14).

## Using it

Open **SFM Multiview**. With no feeds, press OK to add some (Menu → **Edit feeds** later). A feed is one of:

| You type | It plays as |
|---|---|
| `https://example.com/live` or an `<iframe …>` tag copied from a page | **Web** tile: the page in a locked-down WebView |
| `https://cdn.example.com/live/master.m3u8` (`.m3u8 .mpd .mp4 .ts .webm .mkv …`) | **Stream** tile: ExoPlayer |
| `https://user:password@192.168.1.60/live.m3u8`, or any https stream on the home network | **Camera** tile: ExoPlayer, pinned TLS |

One feed fills the screen, two sit side by side, three or four make a 2×2 grid.

| Remote | Does |
|---|---|
| Arrows | Pick a tile (orange frame) |
| OK | Give that tile the sound (again: mute it) |
| Hold OK, or Menu | Options: sound, full screen, reload, reload all, press play in the page, mute all, titles, edit feeds |
| 1–4 / 0 | Sound to feed 1–4 / mute all |
| Back | Leave full screen, then Multiview |

### From the browser

In SFM's browser, the menu's **Add to Multiview** button (the four-tile icon, next to **Add to home**) adds the page
you're on. It opens Multiview with the page in the first empty slot, or asks which feed to replace. It isn't offered in
private browsing, since adding saves the address.

**Send to Multiview** on the fullscreen video bar (next to **Play in…**) sends the video itself. SFM finds the stream as
"Play in…" does: the video's own address, or else the manifest its page fetched. It becomes a stream tile labelled
"site (video)". The tile asks for the stream as the page's player did, using Sofa Player's rules (`PageHeaders`):
the page or frame as Referer and Origin, the browser's User-Agent, and Sec-Fetch headers. The site's cookies are read
fresh from the browser at each request, only where the page's player would send them, and never stored with the feed.
Protected (DRM) videos and private browsing are refused. A file loops, and a live stream reconnects. Many sites put
an expiring token in the stream address, so a tile that gets a 403 says to send the video again or add the page.

Both hand-offs go to Multiview in memory (`MultiviewInbox`), not in the intent: Multiview is exported for the launcher and
ignores intent data. The address is taken once, and only within 30 s, so one that never arrived can't turn up later.

### From the phone

The phone remote works in Multiview too. Pair it from **Edit feeds → Phone remote** (or Menu → **Phone remote**), or
from the browser's **Phone** menu: it is one pairing for the whole app. While Multiview is on the TV, the phone page
shows a **Multiview feeds** section: the four slots (labels only), **Send to TV** with a slot or "first empty", and ✕
to remove a feed. The page's own **Open** box, and the iPhone "Send to TV" Shortcut, add to the first empty slot, or
ask on the TV which feed to replace. The phone's remote and trackpad move around Multiview like the D-pad.

Camera logins are typed on the TV: **Edit feeds** has Username and Password fields (an empty password keeps the saved
one).

Web feeds start 3 s apart, since many stream sites fail when several players start at once. Nothing plays once you
leave Multiview: the players and pages are released in `onStop`.

## Security design

These are deliberate. Changes must keep them; the unit tests in `app/src/test/java/app/sofabrowser/multiview` cover the
rules.

**http(s) only.** `Feed.parse` accepts only `http` and `https` (no `rtsp`, `file`, `content`, `data`, `javascript`,
`intent`…), also inside an `<iframe>` tag. Stream tiles refuse any non-http(s) address a playlist names
(`ResolvingDataSource` check), and https never falls back to http on a redirect. Stored feeds are parsed again when
loaded.

**Navigation lock.** A web tile's main frame stays on its own site (registrable domain, `NavigationLock`). A server
redirect to another site is allowed only while the first page loads, then that site becomes home. Script navigation to
another site is always refused, and so is anything that isn't http(s) (`intent:`, `market:` …). Sub-frames aren't locked
because embed players live in them; the ad blocker filters them. New windows are always refused, and JS dialogs are
dismissed.

**WebRTC off.** `PageScripts.NO_WEBRTC` runs at document start in every frame and removes `RTCPeerConnection` and the
rest of the API, plus `navigator.mediaDevices`/`getUserMedia`, as non-configurable properties. Same-origin child windows
(about:blank iframes) are cleaned on `contentWindow` access and when inserted. Every permission request (camera,
microphone, MIDI, protected media …) and geolocation is denied. On WebViews without document-start scripts it falls back
to injecting at page start, which is weaker. Unlike the desktop app, there is no switch to turn WebRTC on.

**Ad blocking.** Sofa's `AdBlocker` filters every subresource of every frame.

**Camera cert pinning.** Camera tiles are https only, and a login in the address makes a feed a camera, so logins never
go over plain http. When you add a camera, SFM does a TLS handshake only, with no HTTP request and no login, to read its
certificate. It shows the SHA-256 and pins it if you choose **Trust**. After that, `PinnedTls` accepts only that exact
leaf certificate (constant-time compare). The pin replaces CA and hostname checks, which self-signed camera certs can't
pass, so it is stricter, not looser. A changed certificate stops the tile ("certificate has changed") until you edit the
feed and trust the new one. `CameraDataSource` sends the Basic login only to the camera's own host and port, follows no
redirects, and refuses any other address a playlist names.

**No retry on 401.** A 401 or 403, or a pin mismatch, is never retried: not by ExoPlayer (`NoLoginRetry` returns
`TIME_UNSET`) and not by the tile (`Reconnect.stopFor`). The tile shows why and waits for you to reload or fix the feed.
Retrying a wrong password locks accounts on many cameras. Other errors reconnect after 5, 10, 20, 40 s, then once a
minute. Web pages asking for an HTTP login are cancelled.

**Stored secrets.** The feed list (camera logins and pins) is AES-GCM encrypted with an Android Keystore key, as the
phone-pairing token is. On TVs whose keystore fails it stays in app-private storage unencrypted, as the token does.
Labels show `user@host`, never the password, and the login is kept out of the feed URL entirely.

**The phone link.** It is plain http on the home network, with the fork's protections (128-bit token, same-subnet
private addresses only, lock-out after 5 wrong tokens). So:

- A feed with a login in it is refused by the TV (`Feed.parseFromPhone`, reply `login`) before anything is stored.
  Logins are typed on the TV, and only ever go to the camera over pinned TLS.
- A phone-sent feed passes the same checks as a typed one (http(s) only and the rest).
- A phone can't trust a camera certificate. A camera sent from the phone gets the reply `confirm`, and the trust
  prompt appears on the TV.
- `GET /feeds` returns labels only (`user@host`), never addresses with secrets, passwords or pins.
- Feeds can be changed only while Multiview is in front (otherwise `409 not_multiview`).
- One server and token serve the whole app (`PhoneHub`). Commands go to the screen in front, and the server stops
  when neither the browser nor Multiview is started.

**Starting it.** The activity is exported for the launcher (and `adb shell am start`), so it ignores the intent's data
and extras. Another app can't hand it a feed.

## Building

JDK 17+ and the Android SDK (platform 36, build-tools 36.1.0):

```bash
./gradlew testStandardDebugUnitTest
./gradlew assembleStandardDebug
adb install -r app/build/outputs/apk/standard/debug/*.apk
adb shell am start -n app.sofabrowser.sfm.debug/app.sofabrowser.multiview.MultiviewActivity
```

GitHub Actions (**Build SFM APK**, `.github/workflows/build.yml`) runs the tests and builds the debug APK on every push
and pull request. The APK is attached to the run as an artifact.

## Not done yet

- A video whose stream works only inside its page (the site checks something besides headers and cookies) won't
  play in a tile; add the page instead.
- No RTSP: cameras must offer an https stream (HLS, DASH or progressive) that ExoPlayer plays. RTSPS is planned; see
  below.
- No auto-reload of web feeds that never start playing; use Reload. Planned; see below.
- No cosmetic filtering (element hiding) in web tiles, only network blocking. Planned; see below.

## Planned: auto-reload for web feeds that never start

A future release, not started. Some stream sites need a reload or two before their player starts. The desktop
multiview's `auto_reload` handles this, and SFM would follow it, within its own rules:

- **When:** a web tile reloads itself if no video has started playing within 20 s of loading, or if its player shows
  an error screen (JW Player or video.js errors, or a failed `<video>`) that is still there 2 s later while nothing on
  the page plays. Videos in embedded player frames count. A feed that is paused or buffering is never reloaded, and
  nor is a broken ad video next to a working stream.
- **How often:** the wait grows (20 s, 40 s, 60 s …), and after 3 automatic reloads the tile stops trying and says
  so. Reload this feed or Reload all feeds, from the menu, starts the count over.
- **One at a time:** automatic reloads take turns with each other and with the 3 s start-up stagger, so a site never
  gets several feeds reloading at once.
- **Same address only:** a reload loads the feed's own saved address and resets the navigation lock to it. It never
  follows wherever the page had gone.
- **No retry on a refused login:** a page whose main document answered 401 or 403 is not reloaded, consistent with
  the camera rule. The tile says why.
- **Web tiles only:** stream and camera tiles already reconnect on their own (`Reconnect`).
- **Setting:** on by default, with an off switch, and the times and tries as in the desktop app
  (`auto_reload_after` 20 s, `auto_reload_tries` 3).

How: the page reports "playing" and "error" from a document-start script (as the mute script does), and the
decisions sit in a plain-Kotlin class with unit tests, like `Reconnect`.

## Planned: cosmetic filtering in web tiles

A future release, not started. Web tiles block ad and tracker requests, but they don't hide the empty ad boxes,
banners and overlays left behind, which the browser does. The browser's code for it is reusable as it is, so this is
mostly wiring:

- **Element hiding:** install `InPageBlocking` in each `WebTile`, with its own `PageKey`, as `Browser` does. It uses
  the filter lists' `##` rules, the page's own selectors plus the generic ones for the class and id names it sees,
  in one style sheet that also covers ads added later.
- **Trap links:** the same script removes the near-invisible, page-covering links pop-under networks lay over a
  page. In a tile the first remote press would otherwise go to the ad. The navigation lock already stops the ad from
  replacing the feed; this stops the press being wasted.
- **The browser's per-site choices apply:** sites allowed in the browser (ad blocking off) stay unfiltered in tiles.
  Elements removed with "Remove element" (`RemovedElements`) are hidden in tiles too, and "Clean up this site
  automatically" runs `CleanUp` there, so cookie walls and sticky bars come off. Clean-up keeps anything holding a
  video, so the feed itself is never hidden.

Rules it keeps (from `InPageBlocking`, unchanged):

- The page-to-SFM channel has a random name per WebView. A page that finds it can only ask which of its own elements
  would be hidden, or report a blocked pop-up. It can't load addresses, read settings or run anything in SFM.
- Selectors go in as CSS rules one at a time, so a broken or hostile one is dropped and can't run script.
- It needs WebView's document-start scripts and message listeners. Without them tiles get network blocking only, as
  the browser does.

To check first: memory and CPU with four filtered tiles on the onn. The rule index is shared (one `AdBlocker`), so the
extra cost should be the small per-page script, but it needs measuring on the TV.

## Planned: RTSPS cameras (Wyze)

A future feature, not started. Wyze cameras offer only RTSP (with the Wyze RTSP firmware), TLS-only on port 322, so
today they can't be added. The desktop multiview already plays them. Rules it must keep, so it doesn't weaken the
design above:

- **`rtsps://` only, never plain `rtsp://`.** A camera login over unencrypted RTSP would cross the network in the
  clear, which the http(s)-only rule exists to prevent. As in the desktop app, an `rtsp://…:322` address (what the Wyze
  app shows) becomes `rtsps://`; any other `rtsp://` is refused.
- **Pinned like the https cameras:** the trust-on-first-use certificate prompt on the TV, the same `PinnedTls`
  check before any login is sent, and a stop if the certificate changes.
- **No retry on 401/403**, by the player or the tile, as `Reconnect.stopFor` does now.
- **Logins** come from the TV's Username/Password fields and stay encrypted in `FeedStore`; never from the phone.
- **One more scheme, nothing else:** `Feed.parse` gains `rtsps` for camera feeds only (not web or browser videos), and
  the unit tests cover it.

To check first: Media3's RTSP module (`media3-exoplayer-rtsp`) must play RTSP over TLS and accept a custom socket
factory for the pin. If it can't, SFM needs a small RTSPS client of its own, or the camera's stream relayed as https.

