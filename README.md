# GetGo

A simple, easy, fun, open-source Android downloader.
By Taki — GitHub: takidz507 — planned repo: https://github.com/takidz507/getgo

> **Repo note:** the repo above does not exist yet at the time of
> this v1 build. The link is the planned home, not a published page.
> Do not treat it as live until the project is published.

## What GetGo v1 does

- **Design 1 — One-Tap Hero:** paste a link on top, one large
  central Download button, recent downloads as chips below,
  bottom navigation with Downloads and Settings only.
- **Paste a direct file link**, choose **Video or Audio Only**,
  choose a quality, and download with a progress bar and Cancel.
- **Local library** of files this app downloaded, saved to
  `Download/GetGo` on the device. Tap a file to try to open it
  with another app.
- **Settings (simple):** Dark mode toggle, Liquid Glass toggle
  (default OFF), a small About card, a GitHub line, and a legal note.
- **No account, no ads, no telemetry, no server.** Your files
  stay on your device. Only the `INTERNET` and
  `ACCESS_NETWORK_STATE` permissions are requested — no storage,
  location, contact, or any other permission.

## What GetGo v1 does NOT do — read this before judging it

This is a verified **partial** v1, not a clone of desktop OmniGet,
and it does not pretend otherwise:

- **No YouTube / TikTok / Instagram / X site extraction.**
  Those domains are detected and refused with a clear in-app
  message. A yt-dlp-style extractor is NOT bundled in v1, so
  pasting a video-page link will not produce a video, and the app
  will not silently save the page's HTML and call it a video.
  If a page returns HTML instead of a file, the download is refused.
- **No torrent / magnet.** Deliberately out of scope.
- **No Udemy / Hotmart course downloader with login.** Out of scope.
- **No audio extraction.** If you pick Audio Only for a video
  file, v1 saves the original file whole and says so. Real audio
  extraction needs FFmpeg, which is not in v1.
- **No in-app player, no flashcards, no MCP, no 158 tools.**
  Those belong to desktop OmniGet, not to this simple app.
- **Quality** is a real choice in the UI. For a direct file the
  file is saved as-is (there is only one file to save); quality
  will matter for site streams if a future extractor is added.
- **Pause/resume:** Cancel stops a download; resume of a partial
  download is not in v1.

## Polish in v1 (inside the locked scope)

- Beautiful empty states for Recent and Library, instead of
  blank space, when nothing has been downloaded yet.
- Light press feedback (a short shrink-and-return on buttons)
  and a 300 ms fade-in when the lists refresh — no heavy motion,
  nothing that slows the app.

## Animations in v1

All animations live in `AnimHelper.java`, are under 350 ms
except the download pulse, and add no permission or scope:

- **Download button, on press:** 110 ms shrink to 0.96, then
  140 ms return to 1.0.
- **Download button, while downloading:** a gentle pulse,
  scale 1.0 → 1.04 on a 1000 ms cycle, cancelled the moment
  the download ends, fails, or is cancelled.
- **Progress:** animated progress-bar steps on Android 24+
  (`setProgress(percent, true)`), plain steps below that.
  The progress bar and Cancel button fade in on start and
  fade out on finish, instead of appearing / vanishing.
- **Screen transitions:** a soft fade in/out between Home and
  Settings, both ways.
- **State transitions:** lists fade in on refresh, the status
  text fades in on completion, and the Liquid Glass toggle
  change fades the About card.
- **On completion:** a small bounce of the hero button
  (1.0 → 1.12 → 1.0, 180 + 220 ms). No sound, no dialog, no
  confetti — the bounce is the whole celebration.
- **Implicit off-switch:** if Android's animator duration
  scale is 0 (animations off / reduced motion), every helper
  above becomes an instant change. Nothing loops, blocks, or
  delays a tap or a download because of animation.

Honestly left out (not implemented, not hidden):

- Lottie / JSON animations — would add a dependency and size.
- A button that morphs into a progress ring — more complex
  custom drawing for a v1 that must stay simple.
- Shared-element transitions between screens — the fade
  already gives a smooth feel without the fragility.
- Haptics / vibration on completion — deliberately not added;
  it is easy to add later if Taqi wants it, and it needs no
  permission via `performHapticFeedback`, but it was not asked
  for and v1 keeps feedback visual only.
- One card-background decision (`Prefs.applyCard`) for every
  screen, so light / dark and solid / glass always match:
  solid cards when Liquid Glass is off, translucent frosted
  cards when it is on, in both themes.
- Regular font weights only, rounded cards, smooth Telegram-like
  simplicity. No heavy bold type.

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
  builds `assembleDebug`. That workflow has **not been run**,
  because the repo is not published yet — it is included so the
  published project can build on Actions.
- The APK in this delivery was built with the manual
  aapt2 / d8 pipeline from the same sources (Gradle on this VM
  has a known daemon loopback failure, so it was not retried
  in a loop). It is byte-verified (see BUILD.md), but it has
  **not been run on a phone yet** — on-device testing of the
  download flow is still needed.

## Creative ideas proposed but NOT implemented

These would change scope or add work, so they were left out
on purpose and need Taqi's explicit say-so before being added:

1. A yt-dlp-style site extractor (YouTube etc.) — large,
   needs a Python / youtubedl-android runtime, breaks often.
2. FFmpeg audio extraction for Audio Only — native library,
   larger APK, more complexity.
3. Share-sheet integration (receive links shared from other
   apps) — new intent filter, small scope change.
4. Resume for interrupted downloads (HTTP Range + partial files).
5. An in-app media player, and Arabic UI localisation.

## License

GPL-3.0. See `LICENSE`. Developer credit in the app: Taki
(small engraved-style text in Settings → About, not a banner).
