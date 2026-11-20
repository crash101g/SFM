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

**Player only** (Menu → **Player only: on**, the default): many stream pages are an ad-laden page around one embedded
player frame. The first time a web feed's video plays in a frame that sits directly in the page, the tile remembers
that frame (it names itself, and must match the origin WebView vouches for; http(s) only) and from then on loads a bare
page of SFM's holding only that frame, under the feed's own address, so the player is embedded and referred as on the
site. On the onn with four stream feeds: 8 frames per page instead of about 190, a third of the JavaScript heap, none
of the page's pop-up ads or trap links, three tiles playing within 1½ minutes of Reload all feeds instead of 4, and
none caught frozen in 10 minutes. If the player alone doesn't start before auto-reload's wait, the tile goes back to
the whole page and learns again. Four videos still cost the TV most of its processor (WebView decodes in hardware but
composes each video frame in the app), so two or three feeds remain smoother than four.

**Clean view** (Menu → **Clean view: on**, remembered): no title bars and no orange frame, just the feeds. Left and
Right give the sound to that side at once and mute the others; a speaker shows on the tile for a moment. OK still
mutes or unmutes, and Menu still has everything else. Pages' own "Unmute" buttons are hidden in tiles (in every
view), since SFM decides which tile is heard; they stay in the browser, where they're the only way to turn a page's
sound on. Don't hide such a button with a My filters rule: My filters applies in the browser too.

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

### Multiview bookmarks

Menu → **Bookmark these feeds** saves the feeds on screen as one bookmark, under a name you give it (it suggests
"Multiview: site, site", or the name the same set already has). Rename one later with the pencil button beside it in
Bookmarks (any bookmark can be renamed there), where it lists its feeds under the name instead of its address. Opening it from
Bookmarks, the home screen or the address bar's suggestions opens Multiview with exactly those feeds, so you can keep
several sets and switch between them. If that would remove a feed the bookmark doesn't have, Multiview asks first.

It holds web pages and streams only: cameras are left out, so camera logins and trusted certificates stay in the
encrypted feed list and never reach the bookmarks database. A video sent from the browser is bookmarked as its page,
since its stream address doesn't last. Only the browser's own bookmark, home-screen and address-bar choices open one
(address `sfm:multiview?f=…`): links from other apps and the phone are http(s) only, and pages can't navigate to it.
Each address in it goes through the phone's feed rules when opened (http(s) only, no logins, nothing that would be a
camera), so an edited bookmark can't add more.

### My filters

Your own ad-block rules, in uBlock Origin / AdBlock Plus syntax, as in the desktop multiview's `my-filters.txt`. Edit
them in Settings → **My filters** (also listed under Filter lists), or from the phone page's **My filters** box. They
apply in the browser and Multiview as soon as they're saved. An exception (`@@||example.com^`) beats every list, as
in uBlock.

My filters starts with the exceptions JW Player needs, because the bundled StevenBlack hosts list blocks them:
`@@||connatix.com^`, `@@||jwpltx.com^` and `@@||entitlements.jwplayer.com^` (its licence check). Without them, JW
Player stops with "Playback error: There was a problem providing access to protected content", and auto-reload
can't help. It also hides "in-page push" ads (`##in-page-message`): the chat-bubble pop-ups (casino, dating, VPN
offers) some stream pages draw over the video with their own inline script, which no request block can stop. My
filters is kept on the TV (`filters/my-filters.txt.gz`), never downloaded, and can't be removed, only edited or
switched off. A My filters that is still exactly an earlier version's default (never edited) is updated to the
current one at start; an edited one is never touched.

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

**User agent.** Web tiles always present as Chrome on Android (the browser's "mobile" mode, with client hints to
match), whatever the browser's own user-agent setting. The browser's default, desktop Chrome on Windows, doesn't match
what a page can see of an ARM TV (`navigator.platform`, no `window.chrome`), and bot checks such as Cloudflare
Turnstile refuse the mismatch; stream pages behind one then never get their stream token, and JW Player shows "There
was a problem providing access to protected content" (its message for a 403 on the stream).

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
- Cosmetic filtering in web tiles is element hiding and trap links only: "Remove element" choices and "Clean up this
  site automatically" from the browser don't apply to tiles yet.

## Auto-reload for web feeds that never start

Built on `main` for the next release (after 1.2.4-sfm.1). Some stream sites need a reload or two before their player
starts. The desktop multiview's `auto_reload` handles this, and SFM follows it, within its own rules:

- **When:** a web tile reloads itself if no video has started playing within 60 s per web tile on screen (1 minute
  for one, 4 minutes for four) of loading, or if its player shows
  an error screen (JW Player or video.js errors, or a failed `<video>`) that is still there 2 s later while nothing on
  the page plays. Videos in embedded player frames count. A feed that is paused or buffering is never reloaded, and
  nor is a broken ad video next to a working stream.
- **How often:** the wait grows (×2, ×3), and after 3 automatic reloads the tile stops trying and says
  so. Reload this feed or Reload all feeds, from the menu, starts the count over.
- **Why so long with several:** all web tiles share the TV's one WebView process. On the onn with four ad-heavy stream
  pages, the last one took about 4 minutes to start, each page's Cloudflare check included. A reload before then only
  restarted that check, and repeated checks got the streams refused (403); 20 s was the first wait until 1.2.4-sfm.7.
- **One at a time:** automatic reloads take turns with each other and with the 3 s start-up stagger, so a site never
  gets several feeds reloading at once.
- **Same address only:** a reload loads the feed's own saved address and resets the navigation lock to it. It never
  follows wherever the page had gone.
- **No retry on a refused login:** a page whose main document answered 401 or 403 is not reloaded, consistent with
  the camera rule. The tile says why.
- **Web tiles only:** stream and camera tiles already reconnect on their own (`Reconnect`).
- **Setting:** on by default; Menu → **Auto-reload: on/off** switches it for all tiles. The times and tries are the
  desktop app's (20 s, 3 tries).

How: every 2 s each frame's document-start script (`PageScripts.watch`) reports "playing" (a video's time moves) or
"error" over a message channel with a random name per tile. It sends one letter and gets nothing back, so a page that
fakes it can only stop or cause reloads of its own feed, at most 3. `AutoReload` makes the decisions (plain Kotlin,
unit-tested), and the activity hands out turns. Without WebView's message listeners a tile just doesn't reload by
itself.

## Cosmetic filtering in web tiles

Element hiding and trap links are built (1.2.4-sfm.4): each `WebTile` installs `InPageBlocking` with its own `PageKey`,
as `Browser` does, and it is off on sites the browser allows, like the tiles' network blocking. Still to do:
`RemovedElements` and `CleanUp` in tiles, and measuring memory and CPU with four filtered tiles on the onn. The
original plan:

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

## Planned: page or video from the browser menu

A future release, not started. Today the menu's **Add to Multiview** adds the page, and only the fullscreen video
bar's **Send to Multiview** sends the video's stream. The menu button would offer both, without going fullscreen
first:

- **The choice:** on a page with a video, Add to Multiview asks **This page** or **Its video**. On a page without one
  it adds the page as now, with no question.
- **The same lookup and rules as Send to Multiview:** the video's own address, or the manifest its page fetched,
  found as "Play in…" does. The tile asks for it with the page's headers and cookies (`PageHeaders`). Protected (DRM)
  videos offer the page only, and private browsing still hides the button.
- **A video that hasn't started:** the page often fetches its stream only once playback starts, so there may be
  nothing to send yet. Then **Its video** says to start the video first (or adds the page instead) rather than
  guessing at a file the page merely mentions, which may be an ad.
- **The same hand-off:** in memory through `MultiviewInbox`, never in the intent.

To check first: that `collectSources` (the page's report of its videos) works outside fullscreen, where the menu is
used. Today it runs for the fullscreen video bar.

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

