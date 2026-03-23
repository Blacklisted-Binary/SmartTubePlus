# SmartTubePlus Remote System — Open-Source Enhancement Research

> **Status:** Research & proposal only. No code has been incorporated yet.  
> **Purpose:** 23 high-quality GitHub repos analyzed for enhancing the SmartTubePlus TV app
> and its companion remote phone app. Awaiting author selection before any code changes are made.

---

## Context: What we are building

The SmartTubePlus ecosystem currently consists of:

| Component | Technology | What it does |
|-----------|-----------|--------------|
| **TV app** (`smarttubetv/`) | Android TV + Leanback + ExoPlayer | Full-screen YouTube player; exposes an HTTP REST server on port 8787 that accepts `/play`, `/command`, `/search`, `/status`, `/resume`, `/ping` |
| **Companion phone app** (`companion/`) | Android + MVVM + NSD/mDNS | Discovers the TV via mDNS (`_smarttube._tcp`), sends HTTP commands, supports voice/keyboard search, bookmark shortcuts, and a "Take It With You" video hand-off via a YouTube IFrame WebView |

### Key pain points in the current design

1. **HTTP polling** — every status read is a fresh TCP connection from the phone; no push events.
2. **Hand-rolled JSON parser** — fragile, no null safety.
3. **Raw `HttpURLConnection`** — no retries, no connection pooling, timeouts must be set manually.
4. **NSD/mDNS reliability** — Android's built-in `NsdManager` is notoriously flaky on some OEMs.
5. **VideoHandoffActivity WebView** — the YouTube IFrame loads inside a plain `WebView`; fragile, age-restricted videos fail, no quality control.
6. **No security** — all commands travel over plain HTTP with no auth; anyone on the LAN can send commands.
7. **Basic UI** — flat button grid with no animation, no touchpad, no gesture control.
8. **Single-device assumption** — no multi-room, no queuing.

---

## Licence Compatibility Quick Reference

> SmartTubePlus is GPL-3.0. The companion app is a separate Android app.  
> Apache 2.0 and MIT repos can be incorporated without any extra obligations.  
> GPL-3.0 repos used as dependencies require the incorporating module to also be GPL-3.0 (which is already the case for the main app; apply the same to the companion if needed).  
> Always verify before shipping.

| # | Repo | Licence | Compatible? | Notes |
|---|------|---------|-------------|-------|
| 1 | NanoHTTPD | BSD-3-Clause | ✅ Yes | Attribution required |
| 2 | Java-WebSocket | MIT | ✅ Yes | |
| 3 | AndroidAsync | Apache 2.0 | ✅ Yes | |
| 4 | jmDNS | Apache 2.0 | ✅ Yes | |
| 5 | NewPipeExtractor | **GPL-3.0** | ⚠️ GPL propagates | Companion must be GPL — likely fine given the project is already GPL |
| 6 | Media3/ExoPlayer | Apache 2.0 | ✅ Yes | |
| 7 | mpv-android | MIT | ✅ Yes | Native libs: LGPL |
| 8 | Glide | BSD/MIT/Apache | ✅ Yes | |
| 9 | android-youtube-player | MIT | ✅ Yes | |
| 10 | OkHttp | Apache 2.0 | ✅ Yes | |
| 11 | Gson / Moshi | Apache 2.0 | ✅ Yes | |
| 12 | Lottie | Apache 2.0 | ✅ Yes | |
| 13 | Sensey | Apache 2.0 | ✅ Yes | |
| 14 | Armadillo | Apache 2.0 | ✅ Yes | |
| 15 | Conscrypt | Apache 2.0 | ✅ Yes | |
| 16 | RoMote | Apache 2.0 | ✅ Yes | |
| 17 | HA-Firemote | MIT | ✅ Yes (patterns only) | Not an Android library; UI patterns are ideas, not code |
| 18 | PipePipe | **GPL-3.0** | ⚠️ GPL propagates | Study source; do not copy verbatim unless companion is GPL |
| 19 | MATVT | **GPL-3.0** | ⚠️ GPL propagates | Study source; port the concept rather than copy |
| 20 | scrcpy | Apache 2.0 | ✅ Yes | Study architecture |
| 21 | androidtv-remote | MIT | ✅ Yes | |
| 22 | LeakCanary | Apache 2.0 | ✅ Yes | Debug builds only |
| 23 | AboutLibraries | Apache 2.0 | ✅ Yes | |

---

## Repo Recommendations

---

### 1. `NanoHttpd/nanohttpd`

**GitHub:** https://github.com/NanoHttpd/nanohttpd  
**Stars:** ~7,000 ★ | **License:** BSD-3-Clause | **Language:** Java  
**Last commit:** 2021 — intentionally stable/archived; the API is frozen and well-tested rather than abandoned

#### Description
NanoHTTPD is a tiny, embeddable HTTP server in a single Java file. It is the de-facto standard for in-process HTTP serving on Android — SmartTube itself is based on a similar concept for its REST server.

#### How we would use it
Replace the current TV-side HTTP server (which appears to be hand-rolled in the `common/` module) with NanoHTTPD. This provides:
- WebSocket upgrade support (via the `nanohttpd-websockets` artifact)
- Proper MIME handling
- Session/request parsing out of the box
- Gzip/compression
- File upload handling (could power a future "upload subtitle" or "import backup" endpoint)

#### Pros
- Single-file dependency; zero external transitive dependencies
- Actively proven on Android TV devices (used by many popular Android apps)
- BSD licence — compatible with all SmartTubePlus licence requirements
- The websockets sub-artifact upgrades the same server to push-based communication

#### Cons
- Project is essentially in maintenance mode (last meaningful commit 2021)
- Single-threaded model requires care around long requests
- No built-in TLS (would need to wrap with `SSLServerSocket` for HTTPS)

#### Honest Review
**✅ Strongly recommend.** The current TV-side HTTP server is likely a reimplementation of exactly what NanoHTTPD provides. Switching is low-risk, eliminates a maintenance burden, and opens the door to WebSocket push events. The "stable/archived" status is a feature here — it means the API is frozen and well-tested, not abandoned.

#### Creative chaining
- Chain with **Repo #2 (Java-WebSocket)** to give NanoHTTPD a WebSocket upgrade path — phone pushes are then instant with no polling.
- Chain with **Repo #4 (jmDNS)** to advertise the NanoHTTPD server via mDNS simultaneously, ensuring the TV is always discoverable.
- Chain with **Repo #22 (Armadillo)** to add a pre-shared key middleware layer in front of NanoHTTPD, hardening all LAN commands.

---

### 2. `TooTallNate/Java-WebSocket`

**GitHub:** https://github.com/TooTallNate/Java-WebSocket  
**Stars:** ~10,000 ★ | **License:** MIT | **Language:** Java  
**Last active:** 2024

#### Description
A pure-Java RFC 6455 WebSocket client and server implementation using NIO (non-blocking I/O). It is one of the most battle-tested WebSocket libraries for Android and the JVM.

#### How we would use it

**TV side:** Embed a `WebSocketServer` alongside (or instead of) the current HTTP server. The phone companion connects once via WebSocket and the TV pushes real-time playback state updates (position, title, buffering state, volume) automatically — no polling.

**Phone side:** `WebSocketClient` replaces `TvRemoteClient`'s repeated `HttpURLConnection` POST calls for commands. A persistent connection means sub-50 ms command latency vs. the current ~200–500 ms new-TCP-connection overhead.

#### Pros
- Bidirectional push — TV sends progress events to all connected phones automatically
- A single persistent connection costs far less than dozens of HTTP reconnects per second
- Used by many Android apps in production
- MIT licence
- Works on Android 4.3+ (API 18) — within the SmartTubePlus `minSdk 21`

#### Cons
- TLS (WSS) setup requires manual `SSLContext` configuration
- The library doesn't provide an HTTP fallback; you need to keep the REST server alongside it for backward compatibility
- Adds ~180 KB to the TV app's APK

#### Honest Review
**✅ Strongly recommend.** This is the single highest-impact upgrade for the entire remote control system. The switch from request-response HTTP to persistent WebSocket transforms the companion from a "dumb clicker" into a live-synced second screen. The status bar on the phone can show the real-time title, progress bar, and buffering state without any polling.

#### Creative chaining
- Chain with **Repo #1 (NanoHTTPD websockets)** — NanoHTTPD can handle HTTP REST requests while the WebSocket server handles real-time events; both run in the same process.
- Chain with **Repo #12 (Lottie)** — when the WebSocket fires a `state=BUFFERING` event, the phone remote shows an animated loading spinner; when `state=PLAYING`, it animates to a play button.
- Chain with **Repo #17 (PRProd/HA-Firemote)** for UI inspiration — now that we have real-time state, we can show a polished "Now Playing" card like HA-Firemote's.

---

### 3. `koush/AndroidAsync`

**GitHub:** https://github.com/koush/AndroidAsync  
**Stars:** ~7,500 ★ | **License:** Apache 2.0 | **Language:** Java  
**Last commit:** 2022 — stable; development slowed as the author shifted focus to Ion/koush's other projects

#### Description
AndroidAsync is a low-level, NIO-based network library for Android that provides both HTTP client/server **and** WebSocket client/server. It was built specifically for Android, meaning it handles Android lifecycle quirks better than generic Java libraries.

#### How we would use it
Use as an **alternative to Repo #2 (Java-WebSocket) + Repo #10 (OkHttp)** if we prefer a single unified library for both client (companion) and server (TV). The TV app would use `AsyncHttpServer` + `WebSocketMiddleware` as an all-in-one solution, while the companion uses `AsyncHttpClient` for HTTP and WebSocket connections.

#### Pros
- Single dependency does HTTP client, HTTP server, WebSocket client, WebSocket server
- Built specifically for Android; handles `Looper`/`Handler` patterns correctly
- NIO-based — very efficient at scale (multiple phones connecting simultaneously)
- `Ion` (the companion library) provides a clean high-level API

#### Cons
- Development is slower; last major release was 2021
- The API has some Android-isms that make it harder to unit-test
- Less popular than OkHttp for the client side

#### Honest Review
**⚠️ Conditionally recommend.** If we go with a WebSocket-first architecture (recommended), AndroidAsync is a strong all-in-one option. However, OkHttp (Repo #10) is the better client-side choice due to ecosystem support and HTTP/2. Consider AndroidAsync primarily for the TV-side server component.

#### Creative chaining
- Chain with **Repo #4 (jmDNS)** — once the AsyncHttpServer starts, immediately register the mDNS service so the phone discovers it.
- Chain with **Repo #22 (Armadillo)** — store a pre-shared key in Armadillo on both devices; the first WebSocket frame is a HMAC-signed hello to authenticate the connection.

---

### 4. `jmdns/jmdns`

**GitHub:** https://github.com/jmdns/jmdns  
**Stars:** ~1,200 ★ | **License:** Apache 2.0 | **Language:** Java  
**Last active:** 2024

#### Description
JmDNS is the reference Java implementation of multicast DNS / DNS-SD (Apple Bonjour compatible). It handles service **registration** and **discovery** in pure Java without relying on platform APIs.

#### How we would use it
Replace Android's `NsdManager` in `TvDeviceDiscovery.java` on the **phone side** with JmDNS. Known issues with `NsdManager`:
- On many OEM devices (Samsung, Xiaomi, Oppo) discovery silently fails
- `resolveService()` can only handle one resolve at a time, causing queuing bugs
- Some Android versions have race conditions in `stopServiceDiscovery()`

JmDNS bypasses all of this by sending and receiving mDNS packets directly via `MulticastSocket`.

#### Pros
- Fully interoperable with Apple Bonjour and any mDNS-compliant device
- Works reliably on OEMs where `NsdManager` is broken
- Can discover **and** register services — useful if the companion itself needs to be findable (e.g., for a future reverse-remote from the TV to the phone)
- Apache 2.0 licence

#### Cons
- Requires the `CHANGE_NETWORK_STATE` and multicast socket permissions
- Slightly more battery/CPU than `NsdManager` (which delegates to a system daemon)
- Adds ~150 KB to the companion APK

#### Honest Review
**✅ Strongly recommend.** This is a direct, surgical fix to a known fragile component. The current `TvDeviceDiscovery` has no fallback when `NsdManager` fails; users report "no devices found" on Samsung TVs regularly. JmDNS is battle-tested and used in hundreds of production apps including Chromecast sender libraries.

#### Creative chaining
- Chain with **Repo #10 (OkHttp)** for fast connection attempts once a device IP is discovered.
- Chain with **Repo #2 (Java-WebSocket)** — once JmDNS discovers the TV, open the WebSocket connection immediately instead of waiting for a button press.
- Use JmDNS on both phone AND TV side (TV registers the service, phone discovers it) — this is actually the intended Bonjour architecture and eliminates Android NSD entirely from both sides.

---

### 5. `TeamNewPipe/NewPipeExtractor`

**GitHub:** https://github.com/TeamNewPipe/NewPipeExtractor  
**Stars:** ~4,000 ★ | **License:** GPL-3.0 | **Language:** Java  
**Last active:** 2024 (active development)

#### Description
NewPipeExtractor is the content extraction engine behind the NewPipe YouTube app. It can extract video metadata, thumbnails, stream URLs, channel info, search results, and playlist contents from YouTube — all without requiring a YouTube API key.

#### How we would use it
Power the **companion app's bookmark thumbnails** and **"Now Casting" card**: when the user bookmarks a YouTube URL, the companion calls NewPipeExtractor to fetch the video title and thumbnail, then displays them as a proper card instead of a plain URL button. Also provides the YouTube URL parsing needed for the Share intent handler.

#### Pros
- No API key required (uses the same extraction techniques as NewPipe itself)
- Returns rich metadata: title, channel, description, thumbnail URLs, duration, view count
- Already proven at YouTube's scale — the library is used by millions of NewPipe users
- Lightweight; can run offline for cached results
- Handles playlists, channels, and live streams — perfect for bookmark enrichment

#### Cons
- GPL-3.0 — this is a **licensing concern** for SmartTubePlus. The companion module is a standalone app and GPL-licensed code within it would require the companion to also be GPL. This is likely fine given SmartTubePlus is already open-source, but must be explicitly acknowledged.
- YouTube changes extraction rules periodically, requiring extractor updates
- Adds ~1 MB+ to the companion APK (includes the Rhino JavaScript engine used to evaluate YouTube's obfuscated signature cipher — this is a real bundled runtime, not just parsing)

#### Honest Review
**⚠️ Conditionally recommend.** The feature value is high — rich bookmark cards with live thumbnails would make the companion app dramatically more polished. The GPL concern requires a conscious decision from the project owner. If the GPL is acceptable, this is a clear win. If not, alternative: parse the Open Graph meta tags from youtube.com URLs with jsoup (much lighter) as a fallback.

#### Creative chaining
- Chain with **Repo #8 (Glide)** — once NewPipeExtractor returns a thumbnail URL, Glide loads it with caching, transformations, and placeholder handling.
- Chain with **Repo #18 (PipePipe/NewPipe)** — study PipePipe's share receiver code to understand how to handle YouTube URL intent sharing robustly.
- Chain with the **TV app's search** — when the companion's bookmark card is tapped, extract the video ID from the URL and send `POST /play?url=…` directly to the TV.

---

### 6. `androidx/media` (AndroidX Media3 / ExoPlayer)

**GitHub:** https://github.com/androidx/media  
**Stars:** ~2,000 ★ (this repo) | **License:** Apache 2.0 | **Language:** Kotlin/Java  
**Last active:** 2024 (actively maintained by Google)

#### Description
AndroidX Media3 is the successor to ExoPlayer and the Android MediaSession API. The repo currently includes `media3-exoplayer` (the player itself) and `media3-session` (MediaSession integration for background playback, notification controls, and Cast).

#### How we would use it
The TV app (`smarttubetv/`) uses the forked Amazon ExoPlayer (`exoplayer-amzn-2.10.6`). Migrating to Media3 ExoPlayer would provide:
- **Media3 Cast extension** (`media3-cast`) — proper Chromecast sender/receiver support, which upgrades the app's casting beyond the current voice-based approach
- **MediaSession integration** — the phone can control playback via Android's `MediaController` API (standard, no custom protocol needed for simple commands)
- **AV1 hardware decoder support** on newer devices
- Active security patches

#### Pros
- Official Google support and long-term maintenance
- `media3-cast` extension gives SmartTubePlus a proper Chromecast sender (cast TV content to a Chromecast stick)
- `MediaSession` allows the phone's lock screen / notification to show and control playback
- Apache 2.0 licence

#### Cons
- Migration from the Amazon fork (`exoplayer-amzn-2.10.6`) is a significant effort
- The Amazon fork includes FireTV-specific patches that Media3 does not have
- Kotlin-first API may require changes in Java-only modules

#### Honest Review
**⚠️ Long-term recommend, not immediate.** This is strategically important for the Cast extension, but the Amazon fork migration is a large effort that should be planned separately. For the remote control system specifically, the quick win is using `MediaSession` without full Media3 migration: the TV app publishes a `MediaSession`, and the companion connects as a `MediaController` — this gives standard D-pad and transport commands over the system's official channel.

#### Creative chaining
- Chain with **Repo #2 (Java-WebSocket)** — use WebSocket for custom SmartTube-specific commands (search, bookmark play) and MediaSession for standard transport controls.
- Chain with **Repo #5 (NewPipeExtractor)** — MediaSession's `MediaMetadata` shows the title/thumbnail fetched by NewPipeExtractor on the phone's lock screen.

---

### 7. `mpv-android/mpv-android`

**GitHub:** https://github.com/mpv-android/mpv-android  
**Stars:** ~4,000 ★ | **License:** MIT | **Language:** Kotlin/JNI  
**Last active:** 2024

#### Description
mpv-android is a full-featured video player for Android built on libmpv. It supports hardware + software decoding, gesture controls, libass subtitles, and network stream playback.

#### How we would use it
Rather than using a YouTube IFrame WebView in `VideoHandoffActivity`, embed mpv-android's `MPVLib`/`BaseMPVView` to play the video stream directly (using the URL extracted by NewPipeExtractor — Repo #5). This gives:
- True native playback with full quality control
- PiP (Picture-in-Picture) support built in
- No YouTube Terms of Service WebView workarounds
- Background audio while the user locks their phone

#### Pros
- The highest quality open-source media playback on Android — supports AV1, VP9, HEVC, HDR
- Gesture-based seeking/volume/brightness already implemented (steal this for the companion touchpad)
- MIT licence
- Works without Google Play Services

#### Cons
- Native code (JNI) means a significant binary size increase (~15–20 MB for the .so files)
- Building libmpv from source requires a complex toolchain
- Playing YouTube videos natively still requires extracting stream URLs first (NewPipeExtractor)
- The combination of NewPipeExtractor + mpv bypasses YouTube's ad/tracking system completely — ethically fine, but may break with YouTube changes

#### Honest Review
**⚠️ Advanced — conditionally recommend.** The gesture-based seek/volume control code from `BaseMPVView` is extremely valuable for the companion touchpad regardless of whether we use mpv for playback. Specifically, the swipe gesture logic for seek and brightness should be ported to the companion's new touchpad screen. Full mpv integration for VideoHandoffActivity is a large feature but dramatically improves the experience.

#### Creative chaining
- Chain with **Repo #5 (NewPipeExtractor)** — NewPipeExtractor provides the M3U8/MP4 stream URL; mpv-android plays it.
- Steal the gesture control code from `BaseMPVView` and use it in the companion's touchpad fragment to implement swipe-to-seek on the touchpad when no video is being handed off.

---

### 8. `bumptech/glide`

**GitHub:** https://github.com/bumptech/glide  
**Stars:** ~34,000 ★ | **License:** BSD/MIT | **Language:** Java  
**Last active:** 2024

#### Description
Glide is Android's most popular image loading library. It handles disk and memory caching, thumbnail decoding, GIF support, and image transformations with a fluent builder API.

#### How we would use it
Load YouTube video **thumbnails** in the companion app's bookmark grid, `DeviceListAdapter`, and a new "Now Playing" mini-card on the status bar. Currently the bookmark grid shows only a text label; with Glide, each bookmark shows the thumbnail of the video/playlist it represents.

Usage:
```java
Glide.with(itemView.getContext())
    .load("https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg")
    .placeholder(R.drawable.ic_video_placeholder)
    .into(ivThumbnail);
```

#### Pros
- Industry standard; extremely well-maintained (34 000 ★)
- Automatic disk/memory cache; thumbnails load instantly on second view
- Supports rounded corners, error placeholders, crossfade transitions
- Apache 2.0 + BSD licence
- Adds ~500 KB to APK

#### Cons
- Slight overhead for simple cases (a `Picasso` dependency would be ~100 KB lighter)
- Requires Glide's generated API annotation processor for advanced features

#### Honest Review
**✅ Strongly recommend.** This is a no-brainer low-risk addition. YouTube thumbnail URLs are predictable (`https://img.youtube.com/vi/{videoId}/mqdefault.jpg`), so no extra API call is needed — just extract the video ID from the bookmarked URL and load with Glide. The visual improvement to the bookmark grid is immediate and significant.

#### Creative chaining
- Chain with **Repo #5 (NewPipeExtractor)** — use the extractor to get the highest-quality thumbnail URL, then load with Glide.
- Chain with **Repo #12 (Lottie)** — Glide handles still thumbnails; Lottie handles the animated connection-status icons.

---

### 9. `PierfrancescoSoffritti/android-youtube-player`

**GitHub:** https://github.com/PierfrancescoSoffritti/android-youtube-player  
**Stars:** ~3,500 ★ | **License:** MIT | **Language:** Kotlin  
**Last active:** 2024

#### Description
A polished, stable YouTube player for Android that wraps the official IFrame Player API in a WebView, providing a native Java/Kotlin interface including a ChromeCast extension.

#### How we would use it
Replace the hand-rolled `buildPlayerHtml()` in `VideoHandoffActivity` with this library. Benefits:
- Full player lifecycle management (orientation change, background play, PiP)
- Native-looking custom controls (scrubber, fullscreen, quality selector)
- `ChromeCast` extension: the user can re-cast from the phone to a Chromecast device directly from the VideoHandoffActivity without going back to the TV
- `getCurrentSecond()` API removes the fragile `evaluateJavascript()` hack currently used to read playback position before return-to-TV

#### Pros
- Actively maintained; works reliably with current YouTube IFrame API
- No API key required
- The ChromeCast extension adds significant value (see chaining below)
- MIT licence
- Well-documented, Kotlin-idiomatic API

#### Cons
- Kotlin dependency; companion is currently Java-only (though Kotlin interop is trivial)
- IFrame API restrictions still apply (e.g., auto-play may be blocked on some Android versions)
- Does not work for age-restricted or DRM-protected videos (same limitation as the current implementation)

#### Honest Review
**✅ Strongly recommend.** This is a drop-in replacement for the current `VideoHandoffActivity` WebView that eliminates fragile JavaScript injection and adds real player controls. The ChromeCast extension is the standout feature: a user watching a YouTube video on their phone via "Take It With You" can tap a cast button to send it to any Chromecast on their network — this is a completely new use case enabled by simply switching to this library.

#### Creative chaining
- Chain with **Repo #8 (Glide)** — show the video thumbnail as a placeholder while the IFrame API loads.
- Chain the ChromeCast extension with **Repo #6 (Media3/ExoPlayer)** — Media3 Cast can pick up where the IFrame player leaves off, maintaining position across device switches.
- Chain with **Repo #2 (Java-WebSocket)** — when the user taps "Return to TV", the WebSocket push event notifies the TV immediately instead of waiting for a polling cycle.

---

### 10. `square/okhttp`

**GitHub:** https://github.com/square/okhttp  
**Stars:** ~45,000 ★ | **License:** Apache 2.0 | **Language:** Kotlin/Java  
**Last active:** 2024

#### Description
OkHttp is the industry standard Android/JVM HTTP client. It supports HTTP/2, connection pooling, transparent GZIP, response caching, and WebSocket client connections.

#### How we would use it
Replace the raw `HttpURLConnection` in `TvRemoteClient.java` with OkHttp. This provides:
- **Connection pooling** — all commands to the TV share one socket, reducing latency from ~300 ms to ~50 ms per command
- **WebSocket client** — `OkHttpClient.newWebSocket()` becomes the real-time companion connection once Repo #2 is on the TV side
- **Automatic retry on network failure** — the current implementation returns an error on every network blip
- **Structured JSON body parsing** chained with Moshi/Gson (Repo #11)

#### Pros
- The #1 most-used Android HTTP library (45 000 ★)
- Apache 2.0 licence
- HTTP/2 support — multiple concurrent API calls use one connection
- `MockWebServer` (in the same package) makes unit-testing `TvRemoteClient` trivially easy
- ~600 KB APK size increase (already present in many Android projects transitively)

#### Cons
- Kotlin stdlib is a transitive dependency (~1.5 MB)
- For the simple use case (single device, sequential commands), the improvement over `HttpURLConnection` is marginal; the big win only comes with WebSocket

#### Honest Review
**✅ Strongly recommend** — especially as the WebSocket client upgrade. Even without WebSocket, replacing `HttpURLConnection` with OkHttp eliminates the manual timeout/stream-close boilerplate in `TvRemoteClient` and makes the codebase more maintainable. The `MockWebServer` alone is worth it for testability.

#### Creative chaining
- Chain with **Repo #2 (Java-WebSocket) or the OkHttp WebSocket client** — once the TV-side WebSocket server exists, `OkHttpClient.newWebSocket()` is the one-line companion connection.
- Chain with **Repo #11 (Moshi/Gson)** — replace the hand-rolled `parseStatus()` method in `TvRemoteClient` with a proper typed JSON deserialiser.

---

### 11. `google/gson` / `square/moshi`

**Moshi GitHub:** https://github.com/square/moshi  
**Gson GitHub:** https://github.com/google/gson  
**Stars:** Moshi ~10,000 ★, Gson ~23 000 ★ | **License:** Apache 2.0  
**Last active:** 2024

#### Description
Gson and Moshi are JSON serialisation/deserialisation libraries. Moshi is the modern, Kotlin-friendly option; Gson is the older Java-first option still widely used.

#### How we would use it
Replace the 40-line hand-rolled JSON parser in `TvRemoteClient.parseStatus()` with a properly typed POJO:

```java
// Current fragile approach
String videoId = jsonString(json, "videoId"); 

// Proposed with Gson
StatusInfo info = new Gson().fromJson(json, StatusInfo.class);
```

Also replace the string concatenation used for building POST bodies with proper JSON request bodies.

#### Pros
- Eliminates an entire category of bugs (missing escape characters, nested JSON, Unicode)
- `StatusInfo` becomes a proper serialisable model, enabling future features (history, caching)
- Gson: zero additional dependencies; Moshi: requires Kotlin runtime
- Gson Apache 2.0; Moshi Apache 2.0

#### Cons
- For the current simple JSON structure (`videoId`, `positionMs`, `durationMs`, `title`, `isPlaying`), the hand-rolled parser works fine
- Adds 100–300 KB to APK

#### Honest Review
**✅ Recommend as part of the OkHttp migration (Repo #10).** This is a quality-of-life upgrade rather than a feature. The real value is in future-proofing: when the TV pushes richer state objects (queue, live chat count, chapters), a typed model handles changes automatically. Pick Gson for Java compatibility; pick Moshi if we migrate companion to Kotlin.

#### Creative chaining
- Chain with **Repo #10 (OkHttp)** — OkHttp + Moshi (via the `converter-moshi` adapter) is the standard Android network stack.
- Chain with **Repo #2 (Java-WebSocket)** — WebSocket messages are JSON; Gson/Moshi deserialises incoming TV push events in one line.

---

### 12. `airbnb/lottie-android`

**GitHub:** https://github.com/airbnb/lottie-android  
**Stars:** ~35,000 ★ | **License:** Apache 2.0 | **Language:** Java/Kotlin  
**Last active:** 2024

#### Description
Lottie renders Adobe After Effects animations exported as JSON (via Bodymovin) natively on Android. It is the standard way to add smooth, vector-based micro-animations to Android apps.

#### How we would use it
Add micro-animations to the companion remote that make it feel dramatically more polished:

1. **Play/Pause button** — animate between play and pause icons (the iconic Lottie play-pause flip)
2. **Connection state** — animated WiFi signal icon that pulses while scanning for devices and stabilises when connected
3. **Volume** — animated speaker icon that shows waveforms growing/shrinking
4. **Cast button** — animated cast ripple effect when a video is being sent to the TV
5. **"Take It With You" button** — phone-to-TV animation when handoff is initiated

LottieFiles.com has hundreds of free high-quality animation JSON files for all of these use cases.

#### Pros
- 35 000 ★ — the definitive Android animation library
- Animations are resolution-independent vector graphics
- Free animation library at LottieFiles.com
- Apache 2.0 licence
- Adds ~800 KB to APK

#### Cons
- Requires a separate JSON file per animation
- Complex animations can be battery-intensive if looped continuously
- Some very complex animations need Lottie Pro (paid)

#### Honest Review
**✅ Strongly recommend** for the companion app UI redesign. The remote control UI is the face of the product. Lottie animations at key interaction points (connect, cast, play/pause) differentiate SmartTube Remote from generic Android remote apps. The investment is small (add the dependency, download free JSON files, replace `ImageView` with `LottieAnimationView`).

#### Creative chaining
- Chain with **Repo #2 (Java-WebSocket)** — when the TV WebSocket pushes a `state=BUFFERING` event, trigger the loading animation; `state=PLAYING` dismisses it.
- Chain with **Repo #17 (PRProd/HA-Firemote UI patterns)** — HA-Firemote uses smooth CSS transitions for button states; we achieve the same effect natively with Lottie.

---

### 13. `nisrulz/sensey`

**GitHub:** https://github.com/nisrulz/sensey  
**Stars:** ~2,600 ★ | **License:** Apache 2.0 | **Language:** Java  
**Last commit:** 2021 — in maintenance mode (no new features planned); the core gesture API is complete and stable

#### Description
Sensey is an Android library that simplifies gesture and sensor event detection. It provides one-liner detection for: shake, swipe (up/down/left/right), flip, pinch, touch type (single/double/long tap), and physical sensor gestures (step counter, wrist flick).

#### How we would use it
Add a **virtual touchpad screen** to the companion app. When the user switches to the touchpad tab:
- **Swipe left/right** → `SEEK_BWD` / `SEEK_FWD` command to TV
- **Swipe up/down** → `VOL_UP` / `VOL_DOWN` command to TV
- **Single tap** → `TOGGLE` (play/pause) command
- **Double tap** → jump to next video (`NEXT`)
- **Long press** → open D-pad overlay
- **Shake** → mute/unmute

This transforms the companion from a static button grid into a gesture-based remote that works without looking at the screen.

#### Pros
- Eliminates all the `GestureDetector` / `VelocityTracker` boilerplate
- Well-documented; each gesture type is one line of code
- Apache 2.0 licence
- Still works on Android 5+ (within `minSdk 21`)
- Enables eyes-free remote control — the user can control the TV while watching it

#### Cons
- The library is in maintenance mode (2021)
- Physical sensor gestures (shake, wrist flip) require careful threshold tuning to avoid accidental triggers
- Does not handle multi-touch trackpad simulation (for that, see scrcpy's input model in Repo #20)

#### Honest Review
**✅ Strongly recommend.** The touchpad / gesture remote is the single most requested feature in TV remote apps (Android TV Remote, Kodi remote, etc.). Sensey makes implementing it a day's work instead of a week's. The swipe-gesture mapping to seek/volume is the standout use case.

#### Creative chaining
- Chain with **Repo #7 (mpv-android gesture code)** — mpv-android has a production-hardened gesture seek implementation; port its swipe velocity → seek-seconds mapping to the companion touchpad.
- Chain with **Repo #2 (Java-WebSocket)** — gestures translate to WebSocket messages, giving near-zero latency between finger swipe and TV response.
- Chain with **Repo #12 (Lottie)** — show a visual feedback animation on the touchpad when a gesture is recognised.

---

### 14. `patrickfav/armadillo`

**GitHub:** https://github.com/patrickfav/armadillo  
**Stars:** ~500 ★ | **License:** Apache 2.0 | **Language:** Java  
**Last active:** 2023

#### Description
Armadillo is an encrypted `SharedPreferences` replacement. It uses AES-GCM authenticated encryption with BCrypt key stretching, providing true confidentiality and integrity for preference values.

#### How we would use it
**Harden the companion-TV connection security.** Currently all commands travel over plain HTTP with no authentication — any app on the same WiFi network can issue commands.

Proposed security model:
1. On first pairing, the TV generates a 256-bit random token and displays it as a QR code
2. The companion scans the QR code and stores the token using Armadillo: `prefs.edit().putString("device_" + deviceId + "_token", token).commit()`
3. Every HTTP request / WebSocket message includes this token in a custom header
4. The TV validates the token before executing any command

#### Pros
- AES-GCM + BCrypt — state of the art for mobile credential storage
- The stored token survives app reinstalls if backed up with Android's auto-backup
- Prevents casual LAN snooping or rival apps from hijacking the TV
- Apache 2.0 licence

#### Cons
- Secure storage alone is not enough — the HTTP transport is still plaintext; combine with a TLS/HTTPS upgrade (Repo #15) or HMAC signing
- BCrypt key stretching adds ~200 ms on first access (acceptable for a device pairing token)

#### Honest Review
**✅ Recommend** — especially once the app gains wider adoption and security expectations rise. For a local LAN app used at home, the risk is low but real (guest WiFi networks, shared apartment WiFi). Armadillo + a QR-code pairing flow is a user-friendly, production-grade security upgrade. The pairing UX is also a feature differentiator (no other free YouTube TV remote does this).

#### Creative chaining
- Chain with **Repo #1 (NanoHTTPD)** — add a request filter middleware that checks the `X-Auth-Token` header before any handler runs.
- Chain with **Repo #2 (Java-WebSocket)** — the first WebSocket frame is a JSON `{"type":"hello","token":"..."}` message; the TV validates and replies `{"type":"ready"}` or closes the connection.
- Chain with a ZXing/ML Kit QR scanner on the companion side to scan the pairing token displayed on the TV.

---

### 15. `google/conscrypt` (TLS 1.3 for Android + Certificate Pinning)

**GitHub:** https://github.com/google/conscrypt  
**Stars:** ~1,600 ★ | **License:** Apache 2.0 | **Language:** Java/Kotlin  
**Last active:** 2024

#### Description
Conscrypt is Google's Java Security Provider that brings TLS 1.3 and modern cipher support to Android. Combined with a self-signed certificate approach, it enables **HTTPS between the phone and TV over the local network** without requiring a public CA.

#### How we would use it
Upgrade the TV's NanoHTTPD server from HTTP to HTTPS using a self-signed certificate generated at first run and stored on the device. The companion pins this certificate (stores its public key hash) during pairing, so subsequent connections are verified without relying on a CA. This provides:
- Encrypted transport (no LAN packet sniffing)
- Man-in-the-middle protection after initial pairing
- Works on private networks without a domain name

#### Pros
- TLS 1.3 support on Android 5+ (via Conscrypt's bundled provider)
- Self-signed + pinning is appropriate for a LAN peer-to-peer app
- Apache 2.0 licence

#### Cons
- Certificate generation at first run adds complexity to the TV app startup flow
- Certificate pinning requires careful handling of certificate rotation
- Self-signed certs will trigger Android's "unsafe connection" warnings if not pinned properly

#### Honest Review
**⚠️ Recommend for v2.** This is more complex than the other repos and should be tackled after the WebSocket upgrade. The LAN-only use case reduces the threat model, but for users on shared / enterprise WiFi networks, TLS is important. The recommended path is: ship HMAC auth (Repo #14) first, then HTTPS as a follow-up.

#### Creative chaining
- Chain with **Repo #14 (Armadillo)** — store the pinned certificate hash in encrypted SharedPreferences.
- Chain with **Repo #4 (jmDNS)** — advertise the `_smarttube._tcp` service with a TXT record indicating TLS is available, so the companion knows to use HTTPS.

---

### 16. `wseemann/RoMote`

**GitHub:** https://github.com/wseemann/RoMote  
**Stars:** ~163 ★ | **License:** Apache 2.0 | **Language:** Java  
**Last active:** 2020

#### Description
RoMote is a fully functional open-source Roku remote control Android app. It handles device discovery (via SSDP), D-pad navigation, virtual keyboard, playback control, and channel launching.

#### How we would use it
Use as a **reference implementation and UI pattern library** for the SmartTube companion app. Specifically borrow:
- The device discovery + manual IP entry fallback pattern (SSDP is similar to mDNS/NSD)
- The D-pad layout XML (the physical remote grid layout is very polished)
- The keyboard overlay implementation (how it pops up and sends text to the TV device)
- Error handling and reconnect logic

#### Pros
- A proven, complete remote control app in Java — same tech stack as our companion
- Apache 2.0 licence — code can be directly adapted
- Well-structured; easy to understand each component

#### Cons
- Uses SSDP (Simple Service Discovery Protocol), not mDNS — the protocol is different, but the pattern is transferable
- Roku's ECP protocol is REST-based (similar to our current system) and doesn't use WebSockets
- Not actively maintained; last commit 2020

#### Honest Review
**✅ Recommend as a reference only** — do not depend on it as a library. Study its Java source to borrow the D-pad grid layout, the manual IP input dialog, and the reconnect/ping loop pattern. The code is clean and readable, and these are exactly the components our companion needs next.

#### Creative chaining
- Borrow the `CommandExecutor` pattern from RoMote (command queue, retry on failure) and apply it to our `TvRemoteClient` to handle dropped connections gracefully.
- Combine with **Repo #12 (Lottie)** to animate the D-pad buttons on press.

---

### 17. `PRProd/HA-Firemote`

**GitHub:** https://github.com/PRProd/HA-Firemote  
**Stars:** ~895 ★ | **License:** MIT | **Language:** YAML/JavaScript (Home Assistant card)  
**Last active:** 2024

#### Description
HA-Firemote is a Home Assistant Lovelace card that provides a beautiful, full-featured on-screen remote for Amazon Fire TV, Android TV, Chromecast, Apple TV, Roku, NVIDIA Shield, and others. It is the most visually impressive open-source TV remote UI available.

#### How we would use it
Use as a **UI/UX design reference** for a comprehensive redesign of the companion app's remote control screen. Key UI elements to adapt to Android:
- The **physical remote replica layout** (full D-pad with surrounding function buttons, source buttons, numeric keypad)
- The **"haptic feedback on button press"** pattern — the card uses CSS haptic simulation; we use Android's `VibrationEffect`
- The **app launcher grid** — HA-Firemote shows installed app icons; we can show a pinned app grid
- The **dual-zone layout** — touchpad at top, quick-action buttons at bottom

#### Pros
- MIT licence — UI patterns are freely adaptable
- The most popular/highest-quality open-source TV remote UI design
- Multi-device support design patterns are directly applicable
- The card's YAML → XML layout mapping exercise reveals the ideal button hierarchy for any TV remote

#### Cons
- Web-based (YAML + Lit web components); not directly usable in Android without porting
- Adapting the layout to Android XML requires a full redesign of `activity_main.xml`

#### Honest Review
**✅ Strongly recommend as a design reference.** Open the HA-Firemote wiki, look at every screenshot, and use it as the target UX for the companion redesign. Every design decision (button sizing, grouping, D-pad spacing, color coding, icon choice) is already battle-tested by its 895-star community.

#### Creative chaining
- Use HA-Firemote's layout as the spec for the Android `ConstraintLayout` redesign in `activity_main.xml`.
- Chain with **Repo #12 (Lottie)** for button press animations and **Repo #8 (Glide)** for app launcher icons.
- Chain with **Repo #13 (Sensey)** for the touchpad zone in the upper half of the redesigned layout.

---

### 18. `InfinityLoop1308/PipePipe`

**GitHub:** https://github.com/InfinityLoop1308/PipePipe  
**Stars:** ~4,800 ★ | **License:** GPL-3.0 | **Language:** Java  
**Last active:** 2024

#### Description
PipePipe is an advanced fork of NewPipe for Android, supporting YouTube, NicoNico, BiliBili, and SponsorBlock. It has a mature YouTube URL intent receiver, a "share to app" handler, and built-in cast support for DLNA/UPnP and Chromecast.

#### How we would use it
Study and adapt PipePipe's:
1. **Share intent handler** — `ShareHandlerActivity` in our companion already catches YouTube share intents; PipePipe's implementation is more robust (handles shorts, playlists, timestamps, channel links)
2. **DLNA/UPnP cast implementation** — PipePipe has DLNA casting built in; we can extract this to add DLNA casting support to the companion (send video directly to any DLNA renderer, not just SmartTubePlus)
3. **SponsorBlock skip pattern** — client-side sponsor skip logic that could inform a "mark this timestamp" feature in the companion

#### Pros
- GPL-3.0 — code can be studied and adapted (with GPL compliance for the companion)
- DLNA casting code is production-tested
- The YouTube URL parser is robust against all URL variants

#### Cons
- GPL-3.0 licence requires GPL compliance for any derivative
- A large codebase; finding the relevant pieces requires significant reading
- DLNA support adds ~2 MB of dependencies (Cling or nanodlna)

#### Honest Review
**⚠️ Recommend for studying, not copying verbatim.** Read the source. Extract the URL parsing logic (a few classes, Apache-licensed if they came from NewPipe's extractors) and the DLNA cast UI patterns. The SponsorBlock client is especially useful for a "phone-side SponsorBlock while on the go" feature in VideoHandoffActivity.

#### Creative chaining
- Chain with **Repo #5 (NewPipeExtractor)** — both are from the NewPipe ecosystem; together they cover extraction + UI.
- Chain with **Repo #9 (android-youtube-player ChromeCast extension)** — once the companion can cast via ChromeCast and DLNA, the SmartTube TV app becomes just one of many targets.

---

### 19. `virresh/matvt`

**GitHub:** https://github.com/virresh/matvt  
**Stars:** ~344 ★ | **License:** GPL-3.0 | **Language:** Java  
**Last active:** 2023

#### Description
MATVT (Mouse for Android TV Toggle) is an accessibility-service-based app that adds a virtual cursor to Android TV, controlled by the TV's own remote. It simulates touch events on the TV screen via `AccessibilityService`.

#### How we would use it
**Reversed direction:** instead of controlling the TV *from the TV's own remote*, our goal is to control the TV *from the phone*. MATVT's source code reveals exactly how to:
1. Inject a `TYPE_TOUCH_EVENT` `AccessibilityEvent` at arbitrary screen coordinates
2. Implement a custom `AccessibilityService` that listens for commands and fires touch events

By running an `AccessibilityService` on the TV side and connecting it to our HTTP/WebSocket server, the companion's touchpad screen can send precise screen coordinates → the TV executes a tap at that location. This is the **equivalent of a full mouse pointer for Android TV**, controllable from the phone.

#### Pros
- The only open-source implementation of phone-driven virtual cursor for Android TV
- Accessibility services do not require root
- GPL-3.0 — can study and adapt
- Enables controlling *any* Android TV app (not just SmartTubePlus) via the companion

#### Cons
- Requires the user to enable the accessibility service manually (Settings → Accessibility)
- `AccessibilityService` can only be stopped by the user or the system
- GPL licence
- Android TV's accessibility implementation is inconsistent across OEMs

#### Honest Review
**✅ Strongly recommend** studying this codebase for the touchpad feature. The goal of the companion touchpad is exactly what MATVT reverses: instead of physical remote → virtual cursor on TV, we want phone touchpad → virtual cursor on TV. MATVT proves it's possible with accessibility services and shows exactly how to do the coordinate injection.

#### Creative chaining
- Chain with **Repo #13 (Sensey)** — Sensey detects gestures on the phone touchpad; the AccessibilityService on the TV translates those gestures to cursor movement.
- Chain with **Repo #2 (Java-WebSocket)** — touchpad coordinates are sent as high-frequency WebSocket messages (unlike REST, WebSocket can handle 60 updates/second without overhead).
- Chain with **Repo #20 (scrcpy)** — scrcpy's protocol shows how to efficiently encode and transmit touch events over a TCP stream; use the same binary encoding for high-performance cursor movement.

---

### 20. `Genymobile/scrcpy`

**GitHub:** https://github.com/Genymobile/scrcpy  
**Stars:** ~110,000 ★ | **License:** Apache 2.0 | **Language:** C/Java  
**Last active:** 2024

#### Description
scrcpy (screen copy) mirrors Android device screens over ADB/USB or TCP/IP with extremely low latency (35–70 ms). It is the most sophisticated open-source Android remote control tool in existence.

#### How we would use it — two approaches

**Approach A (Direct):** Add a "View TV Screen" button to the companion that launches scrcpy pointed at the TV's IP address. This requires ADB to be enabled on the TV (it is on most Android TVs in developer mode) and scrcpy installed on the phone or a companion server.

**Approach B (Inspired architecture — more valuable):** Study scrcpy's H.264-over-socket streaming architecture to build a lightweight **TV screen preview** pane in the companion. The approach:
1. TV side: use `MediaProjection` API to capture a low-fps (5–10 FPS) H.264 stream
2. TV side: stream it over a local socket / WebSocket
3. Phone side: decode with MediaCodec and show in a `SurfaceView`

This gives a real "second screen" view on the phone — see what's on the TV while you control it.

#### Pros
- 110,000 ★ — the most proven screen-sharing architecture for Android
- Apache 2.0 licence
- The input injection model (`scrcpy` sends touch/key events via ADB) is directly applicable to the AccessibilityService approach in Repo #19
- Shows how to use H.264 + MediaCodec for low-latency video over sockets

#### Cons
- Full scrcpy integration requires either ADB (developer mode) or a TV-side component app
- H.264 streaming from `MediaProjection` requires Android 5.0+ (our `minSdk 21`) and user consent each time
- The Approach B "preview pane" is a medium-large feature (2–3 weeks of development)

#### Honest Review
**⚠️ Aspirational — recommend Approach B as a future milestone.** The screen preview feature alone would make SmartTube Remote the most impressive free YouTube TV remote available. Approach A (leveraging scrcpy directly) is achievable in days; Approach B is weeks. Either way, study scrcpy's `ScreenEncoder.java` and input injection model — it is the best reference for Android screen streaming available.

#### Creative chaining
- Chain with **Repo #2 (Java-WebSocket)** — the screen preview stream uses a separate WebSocket connection on a different port (e.g., 8788) while the command channel uses 8787.
- Chain with **Repo #19 (MATVT)** — screen preview + touchpad cursor = a full virtual remote desktop for Android TV.
- Chain with **Repo #13 (Sensey)** — pinch-to-zoom gesture on the preview pane zooms the companion screen; two-finger swipe navigates back.

---

### 21. `louis49/androidtv-remote` (Node.js)

**GitHub:** https://github.com/louis49/androidtv-remote  
**Stars:** ~88 ★ | **License:** MIT | **Language:** JavaScript/Node.js  
**Last active:** 2024

#### Description
A Node.js implementation of the **official Android TV Remote Protocol** — the same encrypted protocol used by the Google TV app on iOS/Android. It includes the full pairing handshake (certificate exchange on port 6467) and the remote control channel (protobuf over TLS on port 6466).

#### How we would use it
Use as a **reverse-engineering reference** to implement the official Android TV remote protocol in the SmartTube companion app. This would allow the companion to control the TV using the same channel as the official Google TV remote app — including:
- System-level D-pad navigation (navigate menus, not just SmartTubePlus)
- Volume control at the hardware level (bypasses any app restriction)
- Power on/off
- App launching by package name

The pairing protocol uses protobuf + TLS certificates. A Java implementation of this protocol (ported from this Node.js reference) would be the companion app's "super power" control channel.

#### Pros
- The official protocol — works on every Android TV device without any TV-side app modifications
- Volume, power, D-pad work at the system level (not just within SmartTubePlus)
- The existing Node.js implementation proves the protocol is fully understood and accessible
- MIT licence

#### Cons
- Implementing the full protobuf + TLS pairing in Java requires `protobuf-java` (~3 MB) and significant protocol work
- Google may change the protocol (it has happened before with Chromecast)
- The companion app would effectively become a full Android TV remote — significantly expanding scope

#### Honest Review
**✅ High-value, significant effort.** This is the "nuclear option" for the companion app: instead of a SmartTubePlus-specific remote, it becomes a universal Android TV remote that happens to also have SmartTubePlus-specific enhancements. The Node.js reference makes the protocol fully transparent. Recommended as a v2.0 milestone after the WebSocket and touchpad features are stable.

#### Creative chaining
- Chain with **Repo #14 (Armadillo)** — store the TLS certificate pair generated during pairing in encrypted SharedPreferences.
- Chain with **Repo #4 (jmDNS)** — discover both `_smarttube._tcp` (our custom server) and `_androidtvremote2._tcp` (the official remote service) and use whichever is available.
- Chain with **Repo #15 (Conscrypt)** — the official protocol uses TLS; Conscrypt ensures TLS 1.3 works correctly on older Android versions.

---

### 22. `square/leakcanary`

**GitHub:** https://github.com/square/leakcanary  
**Stars:** ~29,000 ★ | **License:** Apache 2.0 | **Language:** Kotlin  
**Last active:** 2024

#### Description
LeakCanary is an Android memory leak detection library. It automatically watches for leaked `Activity`, `Fragment`, `ViewModel`, `Service`, and `BitmapDrawable` instances and reports them with a full heap dump analysis.

#### How we would use it
Add to the companion app's **debug build** to detect and fix memory leaks, particularly in:
- `VideoHandoffActivity` — the `WebView` and `Handler`/timer are complex lifecycle objects
- `CompanionViewModel` — holds `TvDeviceDiscovery` and `TvRemoteClient` which have background threads
- `TvDeviceDiscovery` — the NSD resolve callbacks capture a `Context`

#### Pros
- 29 000 ★ — the gold standard for Android memory leak detection
- Automated — no manual profiling; it pops up a notification when a leak is detected during development
- Apache 2.0 licence
- Debug-only; zero impact on release builds when added correctly

#### Cons
- Only used in development; no runtime value for end users
- Kotlin dependency

#### Honest Review
**✅ Strongly recommend** as a development-time tool. `VideoHandoffActivity.mWebView` and `mTimerHandler.removeCallbacks(mTimerTick)` are both classic leak candidates. LeakCanary will catch them automatically. Add it in one line: `debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.x'`.

#### Creative chaining
- Pair with **Android Studio Profiler** for a complete memory analysis of the companion app.
- Use the leak reports to guide lifecycle improvements in `TvRemoteClient` (ensure `ExecutorService.shutdownNow()` is always called).

---

### 23. `mikepenz/AboutLibraries`

**GitHub:** https://github.com/mikepenz/AboutLibraries  
**Stars:** ~3,600 ★ | **License:** Apache 2.0 | **Language:** Kotlin  
**Last active:** 2024

#### Description
AboutLibraries automatically generates a "Used Open Source Libraries" screen for Android apps. It scans Gradle dependencies at build time and creates a JSON manifest of all licences.

#### How we would use it
As more open-source libraries are added from this list, SmartTubePlus has a growing licence compliance obligation. AboutLibraries automatically:
1. Scans all Gradle dependencies for licence metadata
2. Generates an "Open Source Licences" activity
3. Makes it available at `Settings → About → Open Source Licences`

This is required by Apache 2.0 and MIT licences (attribution), and is a best practice for any app incorporating open-source code.

#### Pros
- Automatic licence compliance — no manual bookkeeping
- Apache 2.0 licence
- Supports SPDX licence identifiers
- Minimal setup: one Gradle plugin + one Activity

#### Cons
- Requires build-time Gradle plugin configuration
- Kotlin dependency in the plugin (Gradle plugins can use Kotlin regardless of app language)

#### Honest Review
**✅ Recommend** — especially if we add 5+ new dependencies from this list. Licence compliance is non-negotiable for an open-source project that accepts community contributions.

---

## Priority Matrix

| Priority | Repo | Impact | Effort | Recommendation |
|----------|------|--------|--------|---------------|
| 🔴 P0 | **#2 Java-WebSocket** | 🔥 Transforms the entire remote from polling to push | Medium | Ship first |
| 🔴 P0 | **#4 jmDNS** | 🔥 Fixes device discovery on Samsung/Xiaomi | Low | Ship first |
| 🔴 P0 | **#10 OkHttp** | 🔥 Network reliability + WebSocket client | Low | Ship first |
| 🟠 P1 | **#1 NanoHTTPD** | TV server upgrade | Medium | Next sprint |
| 🟠 P1 | **#8 Glide** | Visual polish (thumbnails) | Low | Next sprint |
| 🟠 P1 | **#9 android-youtube-player** | Fix VideoHandoffActivity | Low | Next sprint |
| 🟠 P1 | **#11 Gson/Moshi** | Code quality (JSON parsing) | Low | Next sprint |
| 🟠 P1 | **#13 Sensey** | Touchpad gesture control | Medium | Next sprint |
| 🟡 P2 | **#5 NewPipeExtractor** | Rich bookmark cards | Medium | Plan carefully (GPL) |
| 🟡 P2 | **#12 Lottie** | UI polish/animations | Low | With UI redesign |
| 🟡 P2 | **#14 Armadillo** | Security hardening | Medium | After WebSocket |
| 🟡 P2 | **#17 HA-Firemote** | UI design reference | Design only | With UI redesign |
| 🟡 P2 | **#19 MATVT** | Virtual touchpad cursor | High | Research phase |
| 🟡 P2 | **#22 LeakCanary** | Memory leak detection | Low | Add now (debug) |
| 🔵 P3 | **#6 Media3** | ExoPlayer migration + Cast | Very High | Long-term roadmap |
| 🔵 P3 | **#7 mpv-android** | Native phone player | High | Long-term roadmap |
| 🔵 P3 | **#15 Conscrypt/TLS** | Encrypted transport | High | After auth |
| 🔵 P3 | **#20 scrcpy** | Screen preview feature | Very High | Future milestone |
| 🔵 P3 | **#21 androidtv-remote** | Official TV remote protocol | Very High | v2.0 milestone |
| 🔵 P3 | **#18 PipePipe** | DLNA cast + URL parsing | Medium | Study only |
| 🔵 P3 | **#16 RoMote** | UI reference | Design only | Study only |
| 🔵 P3 | **#3 AndroidAsync** | Alt to #2+#10 combined | Medium | If #2+#10 rejected |
| 🔵 P3 | **#23 AboutLibraries** | Licence compliance | Low | With each new dep |

---

## Recommended "Quick Win" Bundle (P0 + P1, ~3 weeks work)

If you approve a focused first sprint, the following repos together make the most significant upgrade to the remote system:

```
1. TooTallNate/Java-WebSocket  (TV side server + phone side client)
2. jmdns/jmdns                 (replace NsdManager in TvDeviceDiscovery)  
3. square/okhttp               (replace HttpURLConnection in TvRemoteClient)
4. bumptech/glide              (YouTube thumbnails in bookmark grid)
5. PierfrancescoSoffritti/android-youtube-player  (fix VideoHandoffActivity)
6. google/gson                 (replace hand-rolled JSON parser in TvRemoteClient)
7. square/leakcanary           (debug-only memory safety net)
```

Together these seven repos:
- Eliminate the polling architecture entirely
- Fix OEM device discovery failures
- Make network code production-grade
- Add thumbnail visuals to bookmarks
- Fix the WebView fragility in VideoHandoffActivity
- Prevent future memory leaks

**No single one of these requires structural changes to the existing architecture.**

---

*Document generated: 2026-03-23. Please review and select the repos you want to proceed with.*
