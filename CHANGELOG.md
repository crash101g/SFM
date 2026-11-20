# Changelog

## 1.2.4-sfm.6 (2026-11-19)

- **Reorder bookmarks:** on the home screen, Menu (or hold OK) on a bookmark → **Move**, then Left/Right and OK, as
  for Quick access. The same menu opens, renames and removes it. The Bookmarks list follows the same order; new
  bookmarks still come first.
- **Clear Most visited:** on the home screen, hold OK (or Menu) on a Most visited site for **Remove from Most
  visited** or **Clear Most visited**. History is kept; a site comes back once it is visited often again.
- **Sound on/off for a page's video:** a new **Sound on/off** item in the browser menu, and a **Mute / Unmute**
  button on the video bar (also for SFM's own player). It works on videos a page starts muted, without reaching the
  page's own Unmute button; a video at volume 0 comes back at full volume.
- **Multiview tiles hide pages' own "Unmute" buttons**, which only covered the picture: SFM decides which tile is
  heard. In the browser they stay, as the page's only way to turn its sound on. (A My filters rule hiding one also
  hid it in the browser, leaving a page that starts muted with no way to unmute.)
- **Fix: ads hidden on pages opened right after SFM starts.** A page that asked for its element hiding before the
  filter lists had loaded (a few seconds on a TV) got none and kept its ads, such as the chat-bubble pop-ups; it is
  now answered once they have.

## 1.2.4-sfm.5 (2026-11-19)

- **Named Multiview bookmarks:** Bookmark these feeds asks for a name ("News", "Saturday games" …), suggesting the
  one the set already has. In Bookmarks, a Multiview bookmark lists its feeds under its name instead of its long
  address.
- **Rename any bookmark:** the pencil button beside each bookmark in Bookmarks renames it; its address stays shown.

## 1.2.4-sfm.4 (2026-11-19)

- **Fix: stream pages behind Cloudflare's bot check now play in Multiview.** Web tiles took the browser's user agent,
  by default desktop Chrome on Windows, which doesn't match what pages can see of the TV. Cloudflare Turnstile
  refused that, so the page never got its stream and JW Player showed "There was a problem providing access to
  protected content". Tiles now present as Chrome on Android, which is what the TV is. The browser's own setting is
  unchanged.
- **Element hiding in Multiview web tiles:** the filter lists' and My filters' `##` rules now hide ad boxes and
  overlays in tiles, as in the browser, and invisible page-covering ad links are removed. Off on sites you allowed in
  the browser.
- **No more chat-bubble pop-up ads:** My filters now also hides "in-page push" ads (`##in-page-message`), the casino,
  dating and VPN bubbles some stream pages draw over the video. If you never edited My filters, it gets the new rule
  by itself; if you did, add `##in-page-message` to it.
- **Multiview bookmarks:** Menu → **Bookmark these feeds** saves the feeds on screen as one bookmark; opening it
  shows those feeds side by side. Web pages and streams only; camera logins never go into a bookmark.
- **Fix: Add to Multiview no longer adds copies.** A page already in Multiview opens it with that feed picked
  ("Already in Multiview as feed 2") instead of filling the empty slots with copies; a video sent again from the same
  page replaces its old copy.
- **Clean view in Multiview** (Menu → Clean view): no title bars or selection frame, just the feeds. Left and Right
  play that side's sound and mute the other; OK mutes or unmutes.
- **Fix: moving the sound no longer pauses a feed.** Each tile is its own media session, and Android paused the tile
  that lost audio focus when another got the sound; a video paused just after a switch is now started again.
- **Fix:** removing the feed that had the sound gives it to the first feed left, instead of leaving every tile muted.

## 1.2.4-sfm.3 (2026-11-18)

- **My filters:** your own ad-block rules in uBlock syntax, edited in Settings → My filters or from the phone
  remote page, applied as soon as you save. An `@@` exception rule beats every list.
- **Fix for JW Player feeds:** the bundled hosts list blocked domains JW Player needs, so some web feeds showed
  "There was a problem providing access to protected content". My filters starts with exceptions for
  `connatix.com`, `jwpltx.com` and `entitlements.jwplayer.com`.
- My filters is kept on the TV, never downloaded; it can be edited or turned off but not removed.

## 1.2.4-sfm.2 (2026-11-18)

- **Auto-reload for web feeds that never start:** a web tile reloads itself if no video plays within 20 s (then
  40 s, 60 s), or if its player shows an error screen with nothing playing. After 3 tries it stops and says so.
  Feeds that have played are left alone when paused or buffering, and a broken ad video beside a working stream
  doesn't count.
- **Within SFM's rules:** a reload always goes to the feed's own saved address with the navigation lock reset. A page
  refused with 401 or 403 is never reloaded by itself, and feeds reload one at a time.
- Menu → **Auto-reload: on/off** (on by default). Reloading a feed by hand starts its count over.

## 1.2.4-sfm.1 (2026-11-18)

First SFM release: the Sofa fork 1.2.4-tvcouch.1 plus **Multiview**. SFM installs as its own app
(`app.sofabrowser.sfm`), beside Sofa and the Sofa fork. See SFM.md.

- **Multiview** (its own launcher entry, SFM Multiview): up to four web pages, HLS/DASH/progressive streams and IP
  cameras on one screen, one with the sound. Arrows pick a tile, OK gives it the sound, Menu (or hold OK) for full
  screen, reload, press play in a page, mute and Edit feeds.
- **Locked down:** web tiles stay on their own site, open no pop-ups and have WebRTC removed. Every feed is http(s)
  only. Cameras use https pinned to the certificate you trusted on the TV, and a rejected login (401/403) is never
  retried. Feeds and camera logins are stored encrypted.
- **From the phone:** the phone remote gets a Multiview section to send, replace and remove feeds (never camera
  logins, which are typed on the TV), and its "Open" and the iPhone Shortcut add feeds while Multiview is on.
- **From the browser:** **Add to Multiview** in the menu adds the page; **Send to Multiview** on the video bar sends
  the video playing, asked for with the page's own headers and cookies.
- **Video bar:** Left and Right now step through all its buttons; the ones on the right were unreachable with the
  remote.

## 1.2.4-tvcouch.1 (2026-11-08)

- **Clearly an unofficial fork:** the app is called **Sofa (fork)** on the TV, its banner carries a FORK badge, and
  Settings → About says it is an unofficial fork, not made or supported by Sofa's developer, with links to both
  projects. README, FORK.md, SECURITY.md and the store listing say the same, and send problem reports to this fork.

## 1.2.3-tvcouch.1 (2026-11-08)

Fork of Sofa 1.2.2 with features from [TVCouchBrowser](https://github.com/crash101g/tvcouchbrowser); see FORK.md.

- **Phone remote, keyboard and trackpad:** menu → Phone shows a QR code to scan on the same Wi-Fi.
- **Clean up** in the menu: Clean up page, Remove element (remembered per site), and automatic clean-up per site.
- **Extra filter lists** switch in Settings (uBlock Origin lists, AdGuard Popups, Peter Lowe), off by default.
- YouTube ad stripping, captchas never blocked, alert loops capped, and "Leave this page?" never traps Back.
- **Updates** now come from this fork's GitHub releases, for copies signed with the fork's key.

## 1.2.2 (2026-10-30)

**Sofa Player**

- **4K and 60 fps on more TVs:** Auto now plays the stream's best picture, such as 4K or 1440p at 60 fps, when the
  TV's hardware decoder says it plays it. Before, many TVs got a smaller picture, often at 30 fps, because of the
  frame rates their maker listed. If such a picture drops frames, Auto moves to a smaller one and says so.
- **Steps down instead of giving up:** when the TV can't decode one of the stream's pictures, Sofa Player goes on
  from the same point with another one (smaller, or another codec), and says which over the video and in Video info.
- **One decoder per video:** the page's own video now rests while Sofa Player plays, which frees its hardware
  decoder and some memory, and helps a second 4K video in a row find one. When Sofa Player only gets a software
  decoder, it keeps to 1080p at 30 fps, or hands the video back to the site's player.
- **Open videos in Sofa Player straight from the page:** a video you start on the page yourself, with sound and a
  minute or longer, goes fullscreen in Sofa Player at once, without Back and Watch. Muted previews and short ads
  stay on the page. On by default; Settings → "Open videos in Sofa Player" turns it off.
- Fixed: on Back from Sofa Player, a muted preview elsewhere on the page could get the position instead of the video
  you watched.

**Watch**

- **Watch by itself** (Settings, off by default): runs Watch on its own once a page has loaded, on every site or only
  on the sites you choose in the menu. It runs once per page and stops as soon as you press a key.
- Watch now reaches a video under an invisible ad layer, and when a layer you can see covers the video, it says so
  instead of "No video found".

**Play in another app**

- Players that can't open Sofa's copy of a stream, such as C Media Player, get the site's address instead.

**Ad blocking**

- Sofa offers Stronger ad blocking (EasyList and EasyPrivacy) once at start, with what it costs. Settings changes it
  any time.
- The bundled lists are refreshed: about 77,000 StevenBlack and 51,000 HaGeZi pop-up domains.

**Other**

- A link sent from another app or a phone remote while Sofa's menu is open now closes the menu and loads.
- The old-WebView warning now starts below version 143.
- `SECURITY.md` says how to report a security problem privately.

## 1.2.1 (2026-10-27)

**Sofa Player**

- **Your choices stay:** the quality, sound and subtitles you pick on the bar now stay for the next videos, on every
  site: the same quality or the nearest lower one, and the same language for sound and subtitles. Private browsing
  uses them but saves nothing new. Settings → Sofa Player → "Forget my player choices" goes back to Auto and the
  TV's own settings.
- **Starts sharp:** with HLS streams, Sofa Player starts at the quality the site's player was showing, instead of a
  lower one for the first seconds.
- **Says why:** when Watch leaves a video with the site's player (protected, no stream Sofa Player can open, or one
  it can't read or play), a short note in fullscreen says why, and Video info keeps the reason.
- Fixed: after a video Sofa Player couldn't take over, later videos in the same session could stay with the site's
  player, because Sofa looked for their stream only once.

## 1.2.0 (2026-10-26)

**Sofa Player**

- **Sofa's own video player:** in fullscreen, Sofa now plays the site's stream in its own player when it can: 4K at
  full resolution on 4K TVs, HDR when the stream and the TV have it, and a choice of quality, sound and subtitles on
  the bar. It takes over at the same moment, without a jump, and "Site's player" on the bar goes back. Protected
  videos and videos under a minute stay with the site's player. Settings → Sofa Player turns it off.
- At the end of a video, the site's player plays the last moment, so the site moves on as usual (its next episode
  or end screen).
- **HDR stays HDR:** when a stream offers HDR and SDR versions, Auto keeps to the HDR ones on HDR TVs (and to SDR on
  others), so a film doesn't change look halfway.
- **Quality:** pick one by hand and it stays for that site until Sofa closes.
- **Subtitles** follow the TV's caption setting. When the stream has none of its own, the page's subtitle files are
  used, and the ones the site was showing stay on.
- If Sofa Player only gets a software decoder while the TV has a hardware one, it pauses the site's video and tries
  once more.
- Match frame rate uses the stream's own frame rate, and Video info shows the stream's format, the decoder and the
  frames shown and dropped.

**Play in another app**

- **Works on many more sites:** Sofa now hands over the whole stream the site's player uses, not just one part of
  it, with the page's subtitle files and the position, and with what the site expects from a player. Sites that
  only let the browser read their stream get a private copy that VLC and Just Player can open.
- **Settings → Play in…** chooses the app, or "Ask every time" to pick on each press.
- **Always play in another app (experimental, off by default)** sends fullscreen videos straight to that app.
- When a video can't be handed over (protected, only inside the page, or no player installed), fullscreen says why.

**Game pads**

- Xbox, PlayStation, 8BitDo and other pads work like in a console browser: the left stick moves the pointer, the
  right stick scrolls, A selects, B goes back, X watches the page's video, Y searches, and the bumpers and triggers
  go back, forward and page up and down. In fullscreen video they play, seek and switch subtitles. Settings → Game
  controller shows every button.

**Links and images**

- **Hold OK on a link or an image** for its menu: open it in another app, copy or share its address, or download
  it (links to files and pictures). Anywhere else, holding OK still switches to scroll mode.

**Better ad blocking**

- **Filter lists** in the formats of uBlock Origin, Adblock Plus and hosts files: Sofa now understands rules for
  parts of a site (such as `/ads/banner`), rule options, exceptions, and rules that hide the empty boxes ads leave
  behind, including ads a page adds after it has loaded.
- **Stronger ad blocking:** one switch in Settings adds EasyList and EasyPrivacy.
- **Settings → Filter lists:** add lists by address, switch each one on or off, see how many rules each brings,
  update them all at once, or go back to the copy that came with Sofa.
- **Automatic list updates,** every few days, if you want them: Sofa asks once.
- The two lists Sofa came with stay on. A blocklist address you set yourself is kept as a list of its own.
- Blocking still happens entirely on the TV, and lists are downloaded without cookies.
- **Faster start:** lists are stored ready to use, so they load in a moment instead of being read again each time.

**Far fewer pop-ups**

- A press anywhere on a page no longer opens an ad in place of the page: windows that a script opens to another
  site are blocked unless you pressed a link to it, and so are the invisible links that pop-under networks lay over
  a page. Your press then usually reaches what you were pressing.
- A press on a link can't also open an ad, and a page can't send itself to an ad site as it opens a window.
- The pop-up rules of filter lists now count too.
- Ads with a broken security certificate no longer interrupt the page with a warning; only the page itself can.
- Buttons that open another site's window on purpose (some "Sign in with…" or payment buttons) are blocked like
  pop-ups too: "Allow once" in the menu opens them.

**Smaller things**

- **0.75x** playback speed.
- "Continue from" no longer turns up on another video or page.
- **Fixed:** after changing a setting, the focus went back to the top of Settings; it now stays on that setting.

## 1.1.0 (2026-10-20)

**Watching video**

- **Watch:** press Play on the remote, or Watch in the menu, and Sofa finds the video on the page and plays it
  fullscreen, even when it comes from another site.
- **New buttons in fullscreen:**
  - subtitles on and off, when the video has them;
  - picture-in-picture, on TVs that support it;
  - Video info: the video's quality and frame rate, and whether the TV decodes it in hardware;
  - Play in another app: open the video in VLC, MX Player or another player. Android asks the first time and Sofa
    remembers; hold OK on the button to pick another one.
- **Continue where you stopped** in long films.
- **Sharper video on 4K TVs:** in fullscreen, sites can now send their 4K stream.
- **Smoother films (experimental):** turn on "Match frame rate" in Settings, and the TV switches to the film's
  frame rate, such as 24 per second, while it plays fullscreen. Only on TVs that support it; the screen goes dark
  for a moment when it switches.
- **The screen stays on** while a video plays, and can go to sleep again when you pause.

**Fewer ads and pop-ups**

- Pop-ups and pages that jump to ad sites on their own are blocked, now with a second list made for pop-up ads.
  Links you press still work, and "Allow once" in the menu opens something that was blocked.
- Fewer "please turn off your ad blocker" messages.
- The Ad block button in the menu shows how many things were blocked on the page.

**Browsing and settings**

- **Find in page,** typed or spoken.
- **Keep history for** 7, 30 or 90 days, always or never. Your choice from 1.0.0 carries over.
- **Protected video** (the kind streaming services lock): ask for each site, always allow, or always block.
- Sofa tells you when your TV's web engine is very old and needs an update, and About shows its version.
- **Fixed:** ad blocking and its count now work from the moment a page starts loading.

## 1.0.0 (2026-10-19)

First public release.

- **Remote-control browsing:** a D-pad mouse pointer that accelerates while held, with hover, drag, edge
  scrolling, a scroll mode, and a D-pad mode for sites built for keyboards.
- **Back-key menu:** address, navigation, bookmarks, history, per-site ad blocking, "Browse as" user agents and
  zoom.
- **Home screen:** your own quick-access tiles, most visited sites and recent bookmarks.
- **Video:** a fullscreen control bar with seeking, play/pause, scrubbing and playback speed.
- **Privacy:**
  - ads and trackers blocked on the device, and third-party cookies blocked;
  - no Do Not Track header, and a desktop user agent by default, so Sofa blends in;
  - private browsing in a separate WebView profile where the WebView supports it;
  - no analytics, accounts or servers of its own.
- **Search:** you choose the engine at first start, from twelve. Suggestions come from your bookmarks and history,
  and voice search runs in Sofa's own screen.
- **Downloads:** pause and resume, automatic retries, and downloads that keep going in the background.
- **Themes:** Dark and Light, or the same as the TV.
- **Updates:** opt-in update checks for copies installed from GitHub.
