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

---

# GetGo v2 — Build & Verification Report (NOT yet built in Actions)

## Result: CODE COMPLETE, BUILD NOT PROVEN — do not present v2 as working yet

- Version: versionCode `2`, versionName `2.0.0` (app/build.gradle).
- Package, Design 1, Settings, animations, legal note: UNCHANGED
  from v1 (verified by reading the v1 files before editing;
  no layout, style, Prefs, DownloadIndex or AnimHelper file
  was modified in v2).
- New dependency (pinned, never floating):
  `io.github.junkfood02.youtubedl-android:library:0.18.1`
  and `:ffmpeg:0.18.1`. aria2c deliberately NOT included.
- New code (all in MainActivity.java, single-file change on
  purpose, to avoid cross-file mismatches):
  - Generic routing: direct file extensions → v1 direct path;
    everything else → yt-dlp extractor. No site allowlist.
  - Engine init once: `YoutubeDL.init` + `FFmpeg.init`,
    with a distinct "engine failed to start" status.
  - Title preview via `YoutubeDL.getInfo` + reflection on
    `getTitle()` only — best-effort, never a gate. If the
    preview fails, the execute step is still attempted and
    its result is what is reported.
  - Real format selectors (see README): capped choices have
    no final "/best" fallback. Audio: bestaudio + FFmpeg
    `--extract-audio` to MP3 at 128K / 96K / best VBR (0).
  - `--no-playlist`, `--no-mtime`, `--restrict-filenames`
    on every extractor request.
  - Progress via the sample app's `Function3<Float, Long,
    String, Unit>` callback, called as
    `execute(request, "getgo-download", callback)`.
  - Cancel via `destroyProcessById("getgo-download")` in
    addition to the v1 thread interrupt.
  - Output: yt-dlp writes to `getExternalFilesDir/getgo-yt`
    (a plain path it can write), then the app moves the
    finished file into Download/GetGo via MediaStore and
    indexes it exactly like v1. Stale temp files are cleared
    before each extractor run so the newest-file scan cannot
    pick up an old download.
- Manifest: `extractNativeLibs="true"` and
  `requestLegacyExternalStorage="true"` added, as the
  upstream library README requires. No new permission.
- Strings: version label → v2.0.0, audio note updated for
  the direct-vs-site distinction, new engine/found/no-file/
  site-failed strings added. No string, ID, drawable or
  style referenced in Java is missing (checked by script,
  see below).

## Workflow fix (v1 run 41700130593)

- v1 failure: `android-actions/setup-android@v3` failed with
  sdkmanager "Failed to find package 'tools'" (exit 1) BEFORE
  Gradle, so Build debug APK and Upload APK were skipped.
- v2 fix in `.github/workflows/build-apk.yml`:
  - Removed the setup-android step entirely. ubuntu-latest
    already ships an Android SDK (ANDROID_HOME /
    ANDROID_SDK_ROOT pre-set), so the step was redundant
    and was the failing step.
  - Replaced `./gradlew` with the official
    `gradle/actions/setup-gradle@v3` + plain `gradle
    assembleDebug --stacktrace`. Reason, not hidden: the
    repo has NO gradlew wrapper script (only
    gradle-wrapper.properties), so `./gradlew` would have
    failed next even after the setup-android fix. One
    attempt was made to generate the wrapper with the local
    Gradle 8.11.1 + JDK 21; it failed with the known VM
    daemon loopback error ("Could not receive a message
    from the daemon") and was NOT retried in a loop, per
    the task constraint — the workflow was changed instead.
  - Workflow name ("Build GetGo debug APK"), job name
    ("build") and artifact name ("GetGo-debug", path
    `app/build/outputs/apk/debug/app-debug.apk`) are
    UNCHANGED from v1, as required.

## Local verification actually done (and its limits)

- All XML files parse (Python ElementTree over every XML
  in the project, excluding build/).
- Workflow YAML parses; job, steps and artifact verified.
- Cross-file script: every R.string / R.id / R.drawable /
  R.style referenced in Java exists in resources — zero
  missing. Java brace/paren counts balance in every file
  (MainActivity: 157/157 braces, 577/577 parens).
- Upstream API used is only what the upstream README and
  sample app confirm: init, FFmpeg.init, getInfo/getTitle,
  YoutubeDLRequest + addOption, execute(request, processId,
  Function3 callback), destroyProcessById. The one
  deliberately unpinned surface is VideoInfo's package
  (inferred from the library's main package); it is used
  ONLY via reflection for the title, so a change there
  degrades to "no title preview", not a build failure.
- Gradle was NOT run locally (known VM daemon failure, see
  above). The manual aapt2/d8 pipeline was NOT used for v2:
  it cannot resolve external AAR/Python dependencies, and
  pretending otherwise would be a fake build.
- **Therefore: v2 is NOT byte-verified and NOT run on a
  phone. The Actions build (after the parent pushes) is the
  required proof for: dependency resolution, compilation,
  APK size, signature, and that the extractor actually
  starts on a device. Until that run succeeds, do NOT claim
  "YouTube works" — claim only "v2 code is complete and
  ready for an Actions build".**

## What still does NOT work in v2 (honest limits)

- yt-dlp auto-update: NOT implemented. A site that breaks
  after yt-dlp changes will need a library/app update.
- Login-only / DRM / paywalled content: NOT supported,
  by design (see README legal limits).
- Playlists: only the single linked video is downloaded
  (--no-playlist), never the whole playlist.
- Resume: NOT implemented. Cancel discards progress.
- Share-sheet receiving, in-app player, Arabic UI,
  ABI splits: NOT implemented (see README proposals list).
- On-device runtime (engine first-run extraction time,
  a real YouTube/TikTok/Instagram download, dark/glass
  appearance): NOT tested in v2.

## Unfinished / not changed (explicit)

- NOT pushed to GitHub, per the task instruction. The
  parent must push (git, as with v1) and then check the
  Actions run before delivering any v2 APK.
- The v1 manual APK in `build/manual/` was NOT rebuilt
  and must NOT be presented as v2 — it is direct-links-only.
- `~/workspace/downloader-app/SPEC.md` and
  `~/workspace/downloader-designs/` were NOT overwritten.
- No PDF/EPUB reader, music player, 158 tools, flashcards,
  MCP, torrent, or course-login feature was added, per
  Taqi's confirmed scope ("download capability only").
- No proprietary or unclear-licence component was added.
