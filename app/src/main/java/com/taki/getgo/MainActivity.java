package com.taki.getgo;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * GetGo v2 — Design 1, One-Tap Hero, download capability ONLY.
 *
 * v2 adds a fully open-source site extractor (youtubedl-android,
 * a wrapper around the yt-dlp executable + bundled Python, with
 * FFmpeg for merging and audio extraction). It is NOT a full
 * OmniGet clone: no PDF/EPUB reader, no music player, no 158
 * tools, no flashcards, no MCP, no torrent, no course login.
 *
 * Flow, kept generic on purpose (Taqi: "download from anything"):
 * any link yt-dlp supports goes through the SAME flow — title
 * preview via getInfo, real yt-dlp format selector for the chosen
 * quality, Video or Audio Only, download, local library.
 * There is NO per-site allowlist in v2: if yt-dlp recognises the
 * link, it is attempted; if it does not, the failure is shown
 * honestly instead of saving an HTML page and calling it a video.
 * Direct file links (.mp4, .mp3, .pdf, .zip, …) still use the
 * fast v1 direct path and never touch the extractor.
 *
 * Legal limits (unchanged from v1): download only content you
 * have the right to save. No DRM breaking, no paywall bypass,
 * no login for an account you do not own. Sites that need those
 * stay out of scope and will simply fail in the extractor.
 *
 * v2.1 adds a runtime yt-dlp update, because the library's
 * bundled yt-dlp (2025.11.12, library 0.18.1 — still the latest
 * library release on Maven Central) is older than YouTube's
 * SABR change and fails YouTube with HTTP 403. Before the
 * first site download in a process, the app calls
 * YoutubeDL.updateYoutubeDL(NIGHTLY) — nightly, because
 * YouTube fixes ship there weeks before stable — and falls
 * back to the bundled binary if the update fails, so a failed
 * update never blocks a download attempt.
 *
 * API surface used here is only what the upstream README and
 * sample app confirm, plus the update API verified against
 * the 0.18.1 AAR with javap: YoutubeDL.init, FFmpeg.init,
 * YoutubeDL.updateYoutubeDL(Context, UpdateChannel) returning
 * UpdateStatus, YoutubeDL.version(Context),
 * YoutubeDL.getInfo (title, best-effort via reflection — see
 * downloadViaExtractor), YoutubeDLRequest + addOption,
 * YoutubeDL.execute(request, processId, Function3 callback),
 * YoutubeDL.destroyProcessById. No VideoInfo field other than
 * the title is compile-time pinned, so a future library change
 * to that one field cannot silently break the build's other
 * calls; if the title lookup fails, the download is still
 * attempted and its own result is what is reported.
 */
public class MainActivity extends Activity {
    // One process ID for the whole app, so Cancel always refers
    // to the one extractor download that can be running.
    private static final String EXTRACTOR_PROCESS_ID = "getgo-download";
    private static boolean engineReady = false;
    private static boolean ytdlpUpdateAttempted = false;

    private EditText linkInput;
    private Spinner qualitySpinner;
    private RadioGroup modeGroup;
    private Button downloadButton;
    private Button cancelButton;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView statusText;
    private LinearLayout recentContainer;
    private LinearLayout libraryContainer;
    private TextView recentEmpty;
    private TextView libraryEmpty;
    private ScrollView scrollView;
    private View pasteCard;
    private View recentCard;
    private View libraryCard;

    private boolean appliedDark;
    private boolean appliedGlass;
    private volatile boolean cancelled;
    private Thread downloadThread;
    private android.animation.ObjectAnimator pulseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedDark = Prefs.isDark(this);
        appliedGlass = Prefs.isGlass(this);
        if (appliedDark) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        linkInput = (EditText) findViewById(R.id.link_input);
        qualitySpinner = (Spinner) findViewById(R.id.quality_spinner);
        modeGroup = (RadioGroup) findViewById(R.id.mode_group);
        downloadButton = (Button) findViewById(R.id.download_button);
        cancelButton = (Button) findViewById(R.id.cancel_button);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressText = (TextView) findViewById(R.id.progress_text);
        statusText = (TextView) findViewById(R.id.status_text);
        recentContainer = (LinearLayout) findViewById(R.id.recent_container);
        libraryContainer = (LinearLayout) findViewById(R.id.library_container);
        recentEmpty = (TextView) findViewById(R.id.recent_empty);
        libraryEmpty = (TextView) findViewById(R.id.library_empty);
        scrollView = (ScrollView) findViewById(R.id.scroll_view);
        pasteCard = findViewById(R.id.paste_card);
        recentCard = findViewById(R.id.recent_card);
        libraryCard = findViewById(R.id.library_card);

        applyCards();
        refreshQualityChoices();
        refreshLists();

        Button pasteButton = (Button) findViewById(R.id.paste_button);
        pasteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animatePress(v);
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    ClipData clip = cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0
                            && clip.getItemAt(0).getText() != null) {
                        linkInput.setText(clip.getItemAt(0).getText().toString().trim());
                        setStatus(getString(R.string.status_pasted));
                    }
                }
            }
        });

        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                refreshQualityChoices();
            }
        });

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animatePress(v);
                startDownload();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelled = true;
                if (downloadThread != null) {
                    downloadThread.interrupt();
                }
                // For an extractor download, interrupting our thread
                // is NOT enough: the yt-dlp process must be stopped
                // by its own ID. Failure here is harmless (no such
                // process, or the engine was never started).
                try {
                    YoutubeDL.getInstance().destroyProcessById(EXTRACTOR_PROCESS_ID);
                } catch (Exception ignored) {
                }
                setStatus(getString(R.string.status_cancelled));
            }
        });

        Button downloadsButton = (Button) findViewById(R.id.nav_downloads);
        downloadsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animatePress(v);
                refreshLists();
                scrollView.scrollTo(0, 0);
            }
        });

        Button settingsButton = (Button) findViewById(R.id.nav_settings);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animatePress(v);
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                // Soft screen transition, not a hard cut.
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Theme or glass toggled in Settings: re-apply without a
        // stale look. Recreate only when the theme itself changed.
        if (Prefs.isDark(this) != appliedDark) {
            recreate();
            return;
        }
        if (Prefs.isGlass(this) != appliedGlass) {
            appliedGlass = Prefs.isGlass(this);
            applyCards();
        }
        refreshLists();
    }

    private void applyCards() {
        Prefs.applyCard(pasteCard, this);
        Prefs.applyCard(recentCard, this);
        Prefs.applyCard(libraryCard, this);
    }

    /** Light press feedback, via AnimHelper (no-op if animations are off). */
    private void animatePress(final View v) {
        AnimHelper.press(v);
    }

    private void fadeIn(View v) {
        AnimHelper.fadeIn(v);
    }

    private boolean isAudioOnly() {
        return modeGroup.getCheckedRadioButtonId() == R.id.mode_audio;
    }

    private void refreshQualityChoices() {
        String[] items = isAudioOnly()
                ? new String[]{"Best audio", "128 kbps", "96 kbps"}
                : new String[]{"Best", "1080p", "720p", "480p", "360p"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(adapter);
    }

    private void setStatus(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(text);
            }
        });
    }

    private void setProgress(final int percent, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Animated progress on API 24+, plain set on older ones.
                if (Build.VERSION.SDK_INT >= 24) {
                    progressBar.setProgress(percent, true);
                } else {
                    progressBar.setProgress(percent);
                }
                progressText.setText(text);
            }
        });
    }

    private void refreshLists() {
        List<DownloadIndex.Entry> entries = DownloadIndex.read(this);
        recentContainer.removeAllViews();
        libraryContainer.removeAllViews();

        boolean empty = entries.isEmpty();
        recentEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        libraryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        int recentCount = Math.min(3, entries.size());
        for (int i = 0; i < recentCount; i++) {
            final DownloadIndex.Entry e = entries.get(i);
            Button chip = new Button(this);
            chip.setText(shortName(e.name));
            chip.setBackgroundResource(Prefs.isDark(this)
                    ? R.drawable.chip_bg_dark : R.drawable.chip_bg);
            chip.setTextSize(13);
            chip.setAllCaps(false);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    animatePress(v);
                    openEntry(e);
                }
            });
            recentContainer.addView(chip);
        }
        for (int i = 0; i < entries.size(); i++) {
            final DownloadIndex.Entry e = entries.get(i);
            TextView row = new TextView(this);
            row.setText(e.name + "  ·  " + DownloadIndex.formatSize(e.sizeBytes));
            row.setTextSize(14);
            row.setPadding(0, 12, 0, 12);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openEntry(e);
                }
            });
            libraryContainer.addView(row);
        }
        fadeIn(recentContainer);
        fadeIn(libraryContainer);
    }

    private String shortName(String name) {
        if (name.length() <= 22) {
            return name;
        }
        return name.substring(0, 19) + "…";
    }

    private void openEntry(DownloadIndex.Entry e) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(e.uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this,
                    getString(R.string.open_failed) + " " + e.name,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startDownload() {
        final String rawLink = linkInput.getText().toString().trim();
        if (rawLink.isEmpty()) {
            setStatus(getString(R.string.status_empty_link));
            return;
        }
        if (!rawLink.startsWith("http://") && !rawLink.startsWith("https://")) {
            setStatus(getString(R.string.status_bad_link));
            return;
        }
        if (rawLink.startsWith("magnet:") || rawLink.endsWith(".torrent")) {
            setStatus(getString(R.string.status_no_torrent));
            return;
        }
        if (downloadThread != null && downloadThread.isAlive()) {
            setStatus(getString(R.string.status_busy));
            return;
        }
        final boolean audioOnly = isAudioOnly();
        final String quality = qualitySpinner.getSelectedItem() != null
                ? qualitySpinner.getSelectedItem().toString() : "Best";
        cancelled = false;
        progressBar.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        AnimHelper.fadeIn(progressBar);
        AnimHelper.fadeIn(cancelButton);
        pulseAnimator = AnimHelper.startPulse(downloadButton);
        setProgress(0, getString(R.string.status_connecting));

        // Generic routing, no site allowlist: a direct file goes
        // down the fast v1 path; everything else is handed to
        // yt-dlp, which either recognises it or fails honestly.
        final boolean direct = isDirectFileLink(rawLink);
        downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (direct) {
                    downloadDirect(rawLink, audioOnly, quality);
                } else {
                    downloadViaExtractor(rawLink, audioOnly, quality);
                }
            }
        });
        downloadThread.start();
    }

    /**
     * Direct-file detection, used ONLY to pick the fast path.
     * It is not a site allowlist: a link that is not a direct
     * file is not refused, it is sent to the extractor.
     */
    private boolean isDirectFileLink(String rawLink) {
        String path = rawLink;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int h = path.indexOf('#');
        if (h >= 0) {
            path = path.substring(0, h);
        }
        String lower = path.toLowerCase();
        String[] exts = {".mp4", ".mp3", ".m4a", ".mkv", ".webm",
                ".mov", ".avi", ".flac", ".opus", ".wav",
                ".pdf", ".zip", ".txt", ".epub"};
        for (int i = 0; i < exts.length; i++) {
            if (lower.endsWith(exts[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Real yt-dlp format selector for the spinner choice. A capped
     * choice is a real cap: there is deliberately NO final "/best"
     * fallback, so a video with no stream at or below the cap
     * fails with a clear message instead of silently downloading
     * a higher quality than the user chose. "Best" is the only
     * uncapped choice.
     */
    private String formatSelector(String quality) {
        if (quality == null) {
            return "bestvideo+bestaudio/best";
        }
        if (quality.startsWith("1080")) {
            return "bestvideo[height<=1080]+bestaudio/best[height<=1080]";
        }
        if (quality.startsWith("720")) {
            return "bestvideo[height<=720]+bestaudio/best[height<=720]";
        }
        if (quality.startsWith("480")) {
            return "bestvideo[height<=480]+bestaudio/best[height<=480]";
        }
        if (quality.startsWith("360")) {
            return "bestvideo[height<=360]+bestaudio/best[height<=360]";
        }
        return "bestvideo+bestaudio/best";
    }

    private String audioQualityValue(String quality) {
        if (quality != null && quality.startsWith("128")) {
            return "128K";
        }
        if (quality != null && quality.startsWith("96")) {
            return "96K";
        }
        // "Best audio": yt-dlp's 0 = best VBR quality.
        return "0";
    }

    /**
     * Site download via youtubedl-android (yt-dlp + FFmpeg).
     * Steps, in order: init the engine once, preview the title
     * via getInfo (best-effort, never blocks the download on its
     * own), execute yt-dlp into a private temp dir, then move
     * the finished file into Download/GetGo via MediaStore and
     * index it exactly like a v1 direct download.
     *
     * The temp-dir step matters for scoped storage (API 29+):
     * yt-dlp writes a plain file path, which it can do freely
     * under getExternalFilesDir, but not directly into the
     * public Downloads folder. Moving via MediaStore afterwards
     * keeps v1's library location (Download/GetGo) unchanged.
     */
    private void downloadViaExtractor(final String rawLink,
            final boolean audioOnly, final String quality) {
        try {
            if (!engineReady) {
                setStatus(getString(R.string.status_preparing_engine));
                try {
                    YoutubeDL.getInstance().init(this);
                    FFmpeg.getInstance().init(this);
                    engineReady = true;
                } catch (Exception e) {
                    setStatus(getString(R.string.status_engine_failed)
                            + " " + e.getMessage());
                    finishProgressUi();
                    return;
                }
            }

            // v2.1: update yt-dlp once per process, before the
            // first site download. NIGHTLY, not STABLE, because
            // YouTube/SABR fixes land there weeks earlier. A
            // failed update is NOT fatal: the bundled binary
            // stays in place and the download is still attempted,
            // so the user sees the real download result, not an
            // update error. This runs on the download thread,
            // never on the UI thread.
            if (!ytdlpUpdateAttempted) {
                ytdlpUpdateAttempted = true;
                try {
                    setStatus(getString(R.string.status_updating_ytdlp));
                    YoutubeDL.getInstance().updateYoutubeDL(this,
                            YoutubeDL.UpdateChannel.NIGHTLY.INSTANCE);
                    String v = null;
                    try {
                        v = YoutubeDL.getInstance().version(this);
                    } catch (Exception ignored) {
                        // Version display is a nicety, not a gate.
                    }
                    setStatus(getString(R.string.status_ytdlp_updated)
                            + " " + (v != null ? v.trim() : ""));
                } catch (Exception e) {
                    setStatus(getString(R.string.status_ytdlp_update_failed)
                            + " " + (e.getMessage() != null ? e.getMessage() : ""));
                }
            }

            // Title preview, best-effort via reflection (see class
            // header: only getTitle() is used, nothing else from
            // VideoInfo is compile-time pinned). A preview failure
            // does NOT abort: the execute step is the real test,
            // and its failure is what the user is shown.
            try {
                Object info = YoutubeDL.getInstance().getInfo(rawLink);
                if (info != null) {
                    Method getTitle = info.getClass().getMethod("getTitle");
                    Object title = getTitle.invoke(info);
                    if (title != null && !title.toString().trim().isEmpty()) {
                        setStatus(getString(R.string.status_found)
                                + " " + title.toString().trim());
                    }
                }
            } catch (Exception ignored) {
                // Preview is a nicety, not a gate.
            }
            if (cancelled) {
                setStatus(getString(R.string.status_cancelled));
                finishProgressUi();
                return;
            }

            File tempDir = new File(getExternalFilesDir(null), "getgo-yt");
            if (!tempDir.exists() && !tempDir.mkdirs() && !tempDir.exists()) {
                setStatus(getString(R.string.status_failed) + " temp dir");
                finishProgressUi();
                return;
            }
            // Clear stale temp files from a previous run, so the
            // "newest file" scan below cannot pick up an old one.
            File[] stale = tempDir.listFiles();
            if (stale != null) {
                for (int i = 0; i < stale.length; i++) {
                    stale[i].delete();
                }
            }

            YoutubeDLRequest request = new YoutubeDLRequest(rawLink);
            request.addOption("--no-playlist");
            request.addOption("--no-mtime");
            request.addOption("--restrict-filenames");
            request.addOption("-o", tempDir.getAbsolutePath() + "/%(title)s.%(ext)s");
            if (audioOnly) {
                request.addOption("-f", "bestaudio");
                request.addOption("--extract-audio");
                request.addOption("--audio-format", "mp3");
                request.addOption("--audio-quality", audioQualityValue(quality));
            } else {
                request.addOption("-f", formatSelector(quality));
                request.addOption("--merge-output-format", "mp4");
            }

            final Function3<Float, Long, String, Unit> callback =
                    new Function3<Float, Long, String, Unit>() {
                        @Override
                        public Unit invoke(Float progress, Long eta, String line) {
                            if (progress != null && progress.floatValue() >= 0) {
                                int pct = (int) progress.floatValue();
                                if (pct > 100) {
                                    pct = 100;
                                }
                                String text = line != null && !line.isEmpty()
                                        ? line : pct + "%";
                                setProgress(pct, text);
                            }
                            return Unit.INSTANCE;
                        }
                    };
            YoutubeDL.getInstance().execute(request, EXTRACTOR_PROCESS_ID, callback);

            if (cancelled) {
                setStatus(getString(R.string.status_cancelled));
                finishProgressUi();
                return;
            }
            File[] produced = tempDir.listFiles();
            File best = null;
            if (produced != null) {
                for (int i = 0; i < produced.length; i++) {
                    if (produced[i].isFile() && produced[i].length() > 0
                            && (best == null
                            || produced[i].lastModified() > best.lastModified())) {
                        best = produced[i];
                    }
                }
            }
            if (best == null) {
                setStatus(getString(R.string.status_no_file));
                finishProgressUi();
                return;
            }
            moveTempFileToLibrary(best, quality);
        } catch (YoutubeDLException e) {
            // yt-dlp's own failure: unsupported site, login-only
            // content, DRM, or a site that changed. Say so plainly
            // instead of blaming the network or the user.
            setStatus(getString(R.string.status_site_failed)
                    + " " + (e.getMessage() != null ? e.getMessage() : ""));
        } catch (Exception e) {
            setStatus(getString(R.string.status_failed)
                    + " " + (e.getMessage() != null ? e.getMessage() : ""));
        } finally {
            finishProgressUi();
        }
    }

    /**
     * Move a finished extractor file into Download/GetGo via
     * MediaStore (same destination and index format as v1), then
     * delete the temp copy. Returns via UI status, like v1.
     */
    private void moveTempFileToLibrary(File tempFile, String quality) {
        OutputStream out = null;
        Uri savedUri = null;
        try {
            String fileName = tempFile.getName();
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE,
                        "application/octet-stream");
                values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/GetGo");
                savedUri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (savedUri == null) {
                    throw new Exception("MediaStore insert returned null");
                }
                out = getContentResolver().openOutputStream(savedUri);
            } else {
                File dir = new File(
                        getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GetGo");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, fileName);
                savedUri = Uri.fromFile(file);
                out = new FileOutputStream(file);
            }
            if (out == null) {
                throw new Exception("Could not open output stream");
            }
            FileInputStream in = new FileInputStream(tempFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
            out = null;
            long size = tempFile.length();
            tempFile.delete();
            DownloadIndex.append(this, new DownloadIndex.Entry(
                    fileName, size, savedUri.toString(), System.currentTimeMillis()));
            setProgress(100, DownloadIndex.formatSize(size));
            setStatus(getString(R.string.status_done) + " " + fileName
                    + " (" + quality + ")");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AnimHelper.completionBounce(downloadButton);
                    AnimHelper.fadeIn(statusText);
                    refreshLists();
                }
            });
        } catch (Exception e) {
            setStatus(getString(R.string.status_failed)
                    + " " + (e.getMessage() != null ? e.getMessage() : ""));
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void downloadDirect(String rawLink, boolean audioOnly, String quality) {
        HttpURLConnection conn = null;
        OutputStream out = null;
        Uri savedUri = null;
        String fileName = null;
        try {
            URL url = new URL(rawLink);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "GetGo/1.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                setStatus(getString(R.string.status_http_error) + " " + code);
                finishProgressUi();
                return;
            }
            String contentType = conn.getContentType();
            if (contentType != null && contentType.startsWith("text/html")) {
                // A web page is not a file. Refuse honestly instead
                // of saving HTML and calling it a video.
                setStatus(getString(R.string.status_no_site_extraction));
                finishProgressUi();
                return;
            }
            fileName = fileNameFrom(conn, rawLink);
            long total = conn.getContentLengthLong();
            boolean isVideoFile = fileName.toLowerCase().endsWith(".mp4")
                    || fileName.toLowerCase().endsWith(".mkv")
                    || fileName.toLowerCase().endsWith(".webm");
            if (audioOnly && isVideoFile) {
                setStatus(getString(R.string.status_audio_note));
            }

            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE,
                        contentType != null ? contentType.split(";")[0] : "application/octet-stream");
                values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/GetGo");
                savedUri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (savedUri == null) {
                    throw new Exception("MediaStore insert returned null");
                }
                out = getContentResolver().openOutputStream(savedUri);
            } else {
                File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GetGo");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, fileName);
                savedUri = Uri.fromFile(file);
                out = new FileOutputStream(file);
            }
            if (out == null) {
                throw new Exception("Could not open output stream");
            }
            InputStream in = conn.getInputStream();
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;
            long lastUi = 0;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled || Thread.currentThread().isInterrupted()) {
                    setStatus(getString(R.string.status_cancelled));
                    finishProgressUi();
                    try {
                        out.close();
                    } catch (Exception ignored) {
                    }
                    return;
                }
                out.write(buffer, 0, read);
                downloaded += read;
                long now = System.currentTimeMillis();
                if (now - lastUi > 200) {
                    lastUi = now;
                    if (total > 0) {
                        int pct = (int) (downloaded * 100 / total);
                        setProgress(pct, DownloadIndex.formatSize(downloaded)
                                + " / " + DownloadIndex.formatSize(total));
                    } else {
                        setProgress(0, DownloadIndex.formatSize(downloaded));
                    }
                }
            }
            out.close();
            out = null;
            DownloadIndex.append(this, new DownloadIndex.Entry(
                    fileName, downloaded, savedUri.toString(), System.currentTimeMillis()));
            setProgress(100, DownloadIndex.formatSize(downloaded));
            String done = getString(R.string.status_done) + " " + fileName;
            if (audioOnly && isVideoFile) {
                done += " — " + getString(R.string.status_audio_note);
            }
            // Quality is a real choice for future site streams; for a
            // direct file the file is saved as-is, and v1 says so.
            done += " (" + quality + ")";
            setStatus(done);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Gentle completion moment: bounce the hero button
                    // and fade the fresh lists in. No sound, no dialog.
                    AnimHelper.completionBounce(downloadButton);
                    AnimHelper.fadeIn(statusText);
                    refreshLists();
                }
            });
        } catch (Exception e) {
            setStatus(getString(R.string.status_failed) + " " + e.getMessage());
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
            }
            if (conn != null) {
                conn.disconnect();
            }
            finishProgressUi();
        }
    }

    private void finishProgressUi() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AnimHelper.stopPulse(downloadButton, pulseAnimator);
                pulseAnimator = null;
                AnimHelper.fadeOutToGone(progressBar, null);
                AnimHelper.fadeOutToGone(cancelButton, null);
            }
        });
    }

    private String fileNameFrom(HttpURLConnection conn, String rawLink) {
        String disposition = conn.getHeaderField("Content-Disposition");
        if (disposition != null && disposition.contains("filename=")) {
            String name = disposition.substring(disposition.indexOf("filename=") + 9);
            name = name.replace("\"", "").trim();
            if (!name.isEmpty()) {
                return sanitize(name);
            }
        }
        try {
            String path = new URL(rawLink).getPath();
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < path.length()) {
                String name = path.substring(slash + 1);
                int q = name.indexOf('?');
                if (q >= 0) {
                    name = name.substring(0, q);
                }
                if (!name.isEmpty()) {
                    return sanitize(name);
                }
            }
        } catch (Exception ignored) {
        }
        return "getgo_download_" + System.currentTimeMillis();
    }

    private String sanitize(String name) {
        String clean = name.replaceAll("[^A-Za-z0-9._\\-]", "_");
        if (clean.length() > 100) {
            clean = clean.substring(0, 100);
        }
        if (clean.isEmpty() || clean.equals(".") || clean.equals("..")) {
            clean = "getgo_download_" + System.currentTimeMillis();
        }
        return clean;
    }
}
