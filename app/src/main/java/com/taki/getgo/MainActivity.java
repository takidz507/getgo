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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * GetGo v1 — Design 1, One-Tap Hero.
 *
 * HONEST v1 LIMIT (see README): this build downloads DIRECT file
 * links only. Site pages (YouTube, TikTok, Instagram, X, …) are
 * detected and refused with a clear message instead of silently
 * saving an HTML page and pretending it is a video. A yt-dlp-style
 * site extractor is NOT bundled in v1 and is NOT claimed to work.
 * Quality and Audio-Only are real user choices that v1 records and
 * honours where the direct-file engine can (audio files, and the
 * note shown when a video file is saved whole because audio
 * extraction needs FFmpeg, which is not in v1).
 */
public class MainActivity extends Activity {
    private static final String[] SITE_HOSTS = {
            "youtube.com", "youtu.be", "tiktok.com", "instagram.com",
            "facebook.com", "fb.watch", "twitter.com", "x.com"
    };

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
        String host = "";
        try {
            host = new URL(rawLink).getHost().toLowerCase();
        } catch (Exception ignored) {
        }
        for (int i = 0; i < SITE_HOSTS.length; i++) {
            if (host.equals(SITE_HOSTS[i]) || host.endsWith("." + SITE_HOSTS[i])) {
                setStatus(getString(R.string.status_no_site_extraction));
                return;
            }
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

        downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                downloadDirect(rawLink, audioOnly, quality);
            }
        });
        downloadThread.start();
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
