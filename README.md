# GetGo

A simple, easy, fun, open-source Android downloader.
By Taki — GitHub: takidz507 — https://github.com/takidz507/getgo

## What GetGo v2 does

- **Design 1 — One-Tap Hero, unchanged:** paste a link on top,
  one large central Download button, recent downloads as chips
  below, bottom navigation with Downloads and Settings only.
- **Download capability ONLY — not a full OmniGet clone.**
  No PDF/EPUB reader, no music player, no 158 tools, no
  flashcards, no MCP, no torrent, no course login. Those
  desktop-OmniGet features are deliberately out of scope.
- **Site downloads via yt-dlp:** paste ANY link yt-dlp
  supports — YouTube, TikTok, Instagram, X, Facebook, Vimeo,
  and the rest of yt-dlp's extractor list — and it goes through
  the same generic flow: title preview, real quality choice,
  Video or Audio Only, download, local library. There is no
  per-site allowlist in v2; if yt-dlp recognises the link it
  is attempted, and if it does not, the failure is shown
  honestly instead of saving an HTML page as a fake video.
- **Real quality, not a fake spinner:** Video quality maps to
  a real yt-dlp format selector (Best, or a height cap of
  1080p / 720p / 480p / 360p). A capped choice has NO final
  "/best" fallback, so a video with no stream at or below the
  cap fails clearly instead of silently downloading a higher
  quality than chosen. Audio Only uses bestaudio + FFmpeg
  extraction to MP3 (128K / 96K / best VBR).
- **Direct file links still work** (.mp4, .mp3, .pdf, .zip, …)
  on the fast v1 path, without touching the extractor.
  Playlists are NOT downloaded whole: `--no-playlist` is
  always passed, so a playlist link downloads only that video.
- **Local library** of files this app downloaded, saved to
  `Download/GetGo` on the device. Tap a file to try to open it
  with another app.
- **Settings (simple, unchanged):** Dark mode toggle, Liquid
  Glass toggle (default OFF), a small About card, a GitHub
  line, and a legal note.
- **No account, no ads, no telemetry, no server.** Your files
  stay on your device. Permissions are `INTERNET` and
  `ACCESS_NETWORK_STATE` only — the extractor needs no new
  permission, because it writes under the app's own external
  files dir first and the app then moves the finished file
  into Download/GetGo via MediaStore, exactly as v1 did.

## What GetGo v2 does NOT do — read this before judging it

- **No DRM breaking, no paywall bypass, no login for an
  account you do not own.** Sites that need those simply
  fail in the extractor, and that is by design, not a bug
  to be "fixed" by a bypass.
- **No torrent / magnet.** Deliberately out of scope.
- **No Udemy / Hotmart course downloader with login.**
  Out of scope.
- **No in-app player, no flashcards, no MCP, no 158 tools.**
  Those belong to desktop OmniGet, not to this simple app.
- **No resume:** Cancel stops a download; resume of a partial
  download is not in v2.
- **Site extractors break when sites change.** yt-dlp's
  extractors are maintained upstream; a site that worked
  yesterday can fail tomorrow until yt-dlp is updated. GetGo
  v2 does NOT auto-update yt-dlp in-app (that is a future
  option, not a hidden behaviour).

## Size — v2 will be much larger than v1, honestly

- v1 was 38,028 bytes because it had zero dependencies.
- v2 bundles a Python runtime + yt-dlp + FFmpeg for all four
  ABIs (arm64-v8a, armeabi-v7a, x86, x86_64). The exact APK
  size is **not claimed here** because v2 has NOT been built
  in Actions yet (see Build). Expect tens of megabytes, not
  kilobytes. ABI splits could shrink it later, at the cost of
  per-ABI artifacts, and are NOT done in v2.

## Licenses in v2 — all open source, no proprietary component

- **GetGo itself: GPL-3.0.** See `LICENSE`.
- **youtubedl-android 0.18.1** (`io.github.junkfood02.youtubedl-android:library`
  and `:ffmpeg`, by yausername): **GPL-3.0**, as listed on
  the upstream repository and on Maven Central. This is
  compatible with GetGo's GPL-3.0; no licence conflict was
  introduced by adding it.
- **yt-dlp** (bundled inside youtubedl-android, lazy
  extractors build): **Unlicense** (public-domain-like),
  permissive and compatible with GPL-3.0 distribution.
- **FFmpeg** (bundled via the `:ffmpeg` artifact, used for
  merging video+audio and for `--extract-audio`): free
  software under the LGPL/GPL family. GetGo adds no
  proprietary codec, extractor, or binary of its own.
- **Python 3.8 runtime** (bundled inside youtubedl-android):
  **Python Software Foundation licence**, permissive.
- **No component was added whose licence is unclear or
  proprietary.** If a future component's licence is unclear,
  it must be flagged and left out, not silently included.

## Polish in v2 (inside the locked scope)

Same as v1, unchanged: beautiful empty states for Recent
and Library, light press feedback, and the AnimHelper
animations (press shrink, download pulse, fades, completion
bounce), all respecting Android's animator-duration-scale = 0
(animations off = instant changes). See v1 README/BUILD for
the full animation list; nothing was added that loops,
blocks, or delays a tap or download.

## Liquid Glass — honest wording

The Liquid Glass toggle is an **Android frosted-glass lookalike**
(translucent cards with a soft stroke). Android has no native
Apple Liquid Glass, and GetGo does not claim to use it. It is
off by default so the app stays light and fast.

## Legal note

Download only content you have the right to save. GetGo does
not break DRM or paywalls, and does not download paid courses
you did not buy. The same note appears inside the app, in
Settings and under the home screen.

## Build

- Gradle project (see `app/build.gradle`, `settings.gradle`).
  A GitHub Actions workflow (`.github/workflows/build-apk.yml`)
  builds `assembleDebug` and uploads it as artifact
  `GetGo-debug`. Workflow name and artifact name are unchanged
  from v1; the v1 failure is fixed in v2 (see BUILD.md).
- **v2 has NOT been built yet.** Local Gradle on this VM
  fails with the known daemon loopback error, and the manual
  aapt2/d8 pipeline from v1 cannot build external AAR/Python
  dependencies, so it was NOT used or pretended to work for
  v2. The Actions build is the required proof step, and it
  must be checked before anyone claims "YouTube works".
- **On-device testing is still required** before any claim
  that site downloads work on a phone: first-run engine
  initialisation, a real site download, and dark/glass
  appearance on a device are all untested in v2.

## Creative ideas proposed but NOT implemented

These would change scope or add work, so they were left out
on purpose and need Taqi's explicit say-so before being added:

1. yt-dlp auto-update in-app (STABLE / NIGHTLY channel).
2. Share-sheet integration (receive links shared from other
   apps) — new intent filter, small scope change.
3. Resume for interrupted downloads (HTTP Range + partial files).
4. ABI splits to shrink the APK.
5. An in-app media player, and Arabic UI localisation.

## License

GPL-3.0. See `LICENSE`. Developer credit in the app: Taki
(small engraved-style text in Settings → About, not a banner).
