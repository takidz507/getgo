# GetGo v1 — Build & Verification Report

## Result: BUILD SUCCEEDED (byte-verified, NOT run on a phone)

- APK: `build/manual/GetGo-v1.0.0-debug.apk`
- Size: 38,028 bytes
- SHA-256: `5399daead4a70be2ac74ec5da479b88ec2523adae52ad0f1ddf169a0addd3526`
- File type: `Android package (APK), with AndroidManifest.xml, with APK Signing Block`
- Package: `com.taki.getgo`, versionCode `1`, versionName `1.0.0`
- Label: `GetGo`, launchable activity: `com.taki.getgo.MainActivity`
- minSdk `26`, targetSdk `34`, compileSdk `34`
- Permissions (badging, exactly two, no others):
  `android.permission.INTERNET`, `android.permission.ACCESS_NETWORK_STATE`
- Signature: apksigner verify — v2 scheme true, v3 scheme true,
  1 signer. v1 scheme false is expected, not a failure: v1 (JAR)
  signing is for pre-Android-7 devices and minSdk here is 26,
  so v2+v3 cover every device this app can install on. An
  explicit `--v1-signing-enabled true` flag was tried and did
  not change this; it was not chased further because it cannot
  affect installability on minSdk 26+.
- Contents (16 entries): AndroidManifest.xml, classes.dex,
  resources.arsc, 2 layouts, 8 drawables, META-INF signature files.
- Dex classes verified with dexdump: MainActivity,
  SettingsActivity, Prefs, DownloadIndex (+Entry), AnimHelper,
  and R classes — all present, no missing class.
- Build script: `build/build-manual.sh` (aapt2 compile/link,
  javac, d8, zipalign, apksigner). Full log: `build/build.log`.
  Warnings only: javac source-8 obsolescence warnings and a
  deprecation note; no errors.
- Gradle was NOT run locally (known VM daemon loopback failure;
  not retried in a loop, per the task constraint). The Gradle
  project structure and `.github/workflows/build-apk.yml`
  (assembleDebug) are included for Actions, but that workflow
  has NOT run, because the repo is not published yet.

## Build fixes made along the way (not hidden)

1. `android:style` → `style` on the ProgressBar (aapt2 link error).
2. d8 needs explicit `.class` files, not a directory input.
3. Script `cd` left zipalign in the wrong directory — fixed
   with a subshell for the `aapt add` step.
4. First badging showed empty version and implied legacy
   storage/phone permissions — fixed with `<uses-sdk>` in the
   manifest and `--version-code 1 --version-name 1.0.0` on link.
   Badging is now clean (see above).

## What works in code (structure-verified, runtime untested)

- Design 1: paste field on top, large central Download button,
  recent chips below, bottom nav Downloads / Settings only.
- Paste from clipboard, Video / Audio Only choice, quality
  spinner (video: Best/1080p/720p/480p/360p; audio: Best/128/96).
- Direct-file download with progress, Cancel, filename from
  Content-Disposition or URL, saved via MediaStore to
  `Download/GetGo` (API 29+) or app external Downloads on older
  versions, plus a local index file for Recent/Library.
- Dark mode toggle, Liquid Glass toggle (default OFF,
  Android frosted-glass lookalike, NOT Apple Liquid Glass),
  small engraved "Taki" credit in Settings → About,
  GitHub line `GitHub: takidz507 — github.com/takidz507/getgo`.
- Legal note in-app (Settings + home footer).

## What does NOT work in v1 (honest limits)

- YouTube / TikTok / Instagram / X / Facebook site extraction:
  NOT implemented, NOT bundled, refused with a clear message.
  Do not present this APK as downloading from those sites.
- Audio extraction (FFmpeg): NOT implemented; a video file
  picked in Audio Only mode is saved whole, with that note shown.
- Torrent/magnet, course-login downloading, in-app player,
  flashcards, MCP: NOT implemented, deliberately out of scope.
- Quality for direct files: the file is saved as-is; quality
  is recorded/shown but cannot change a single direct file.
- Resume: NOT implemented; Cancel stops and discards progress.
- Runtime behaviour (tap flow, real download, dark/glass
  appearance on a phone) has NOT been tested on a device.
  On-device testing by Taqi is still required before any
  claim that "it works on a phone".

## Animations — implemented

In `app/src/main/java/com/taki/getgo/AnimHelper.java`,
all light, all respecting Android's animator-duration-scale = 0
(system animations off = instant changes, no new setting):

- Button press: 110 ms shrink to 0.96, 140 ms return.
- Download pulse: scale 1.0 → 1.04, 1000 ms cycle, cancelled
  on end / failure / cancel.
- Animated progress steps (API 24+), progress bar + Cancel
  fade in on start, fade out on finish.
- Fade in/out screen transition Home ↔ Settings.
- Lists fade in on refresh; status fades in on completion;
  About card fades on Liquid Glass toggle change.
- Completion bounce: hero button 1.0 → 1.12 → 1.0
  (180 + 220 ms). No sound, no dialog, no confetti.

## Animations — left out (honest)

- Lottie / JSON animations (dependency + size).
- Button morphing into a progress ring (custom drawing,
  too much for a simple v1).
- Shared-element transitions (fade already gives smoothness).
- Haptics on completion (visual feedback only in v1).

## Unfinished / not changed

- GitHub repo `takidz507/getgo` does NOT exist yet — NOT
  published, per the task instruction. The repo link in the app
  and README is the planned link only and will 404 until published.
- GitHub Actions workflow NOT run (needs the published repo).
- `~/workspace/downloader-app/SPEC.md` and
  `~/workspace/downloader-designs/` were NOT overwritten.
- No new permissions were added beyond INTERNET +
  ACCESS_NETWORK_STATE. No torrent, course-login, or extra
  tools were added.
- Creative proposals NOT implemented (need Taqi's explicit
  say-so): yt-dlp site extractor, FFmpeg audio extraction,
  share-sheet receiving, resume, in-app player, Arabic UI
  localisation. See README for the full list.
