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
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
 * v2.2 adds a dedicated Library screen (Play/Share/Delete per
 * item), a ⋮ menu with a GitHub-releases app-update check that
 * only lights a badge and opens the release page (no silent
 * install), layered liquid-glass cards, and one pill shape for
 * all secondary buttons.
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
    // v2.3: the extractor process, engine state and download
    // thread live in DownloadEngine / DownloadService now, so a
    // download survives this Activity. Nothing here owns them.

    private EditText linkInput;
    private Spinner qualitySpinner;
    private RadioGroup modeGroup;
    private Button downloadButton;
    private Button cancelButton;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView statusText;
    private LinearLayout recentContainer;
    private TextView recentEmpty;
    private TextView updateDot;
    private ScrollView scrollView;
    private View pasteCard;
    private View recentCard;

    private boolean appliedDark;
    private boolean appliedGlass;
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
        recentEmpty = (TextView) findViewById(R.id.recent_empty);
        updateDot = (TextView) findViewById(R.id.update_dot);
        scrollView = (ScrollView) findViewById(R.id.scroll_view);
        pasteCard = findViewById(R.id.paste_card);
        recentCard = findViewById(R.id.recent_card);

        applyCards();
        refreshQualityChoices();
        refreshLists();
        refreshUpdateDot();
        // Silent background check on launch: a new release only
        // lights the dot, it never downloads or installs anything.
        checkForUpdate(false);

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
                DownloadService.cancelDownload(MainActivity.this);
                setStatus(getString(R.string.status_cancelled));
                finishProgressUi();
            }
        });

        Button downloadsButton = (Button) findViewById(R.id.nav_downloads);
        downloadsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animatePress(v);
                startActivity(new Intent(MainActivity.this, LibraryActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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

        Button menuButton = (Button) findViewById(R.id.menu_button);
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animatePress(v);
                showTopMenu(v);
            }
        });
    }

    /** ⋮ menu: Library, Settings, and the app-update check. */
    private void showTopMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(getString(R.string.menu_library));
        menu.getMenu().add(getString(R.string.menu_settings));
        if (Prefs.isUpdateAvailable(this)) {
            menu.getMenu().add(getString(R.string.menu_update_available)
                    + " (" + Prefs.getUpdateTag(this) + ")");
        }
        menu.getMenu().add(getString(R.string.menu_check_update));
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String title = item.getTitle().toString();
                if (title.equals(getString(R.string.menu_library))) {
                    startActivity(new Intent(MainActivity.this,
                            LibraryActivity.class));
                } else if (title.equals(getString(R.string.menu_settings))) {
                    startActivity(new Intent(MainActivity.this,
                            SettingsActivity.class));
                } else if (title.startsWith(
                        getString(R.string.menu_update_available))) {
                    openUpdatePage();
                } else if (title.equals(getString(R.string.menu_check_update))) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.update_checking),
                            Toast.LENGTH_SHORT).show();
                    checkForUpdate(true);
                }
                return true;
            }
        });
        menu.show();
    }

    private void openUpdatePage() {
        String url = Prefs.getUpdateUrl(this);
        if (url == null || url.length() == 0) {
            url = AppUpdate.RELEASES_PAGE;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void refreshUpdateDot() {
        updateDot.setVisibility(Prefs.isUpdateAvailable(this)
                ? View.VISIBLE : View.GONE);
    }

    /** notify = true only when the user tapped Check for updates. */
    private void checkForUpdate(final boolean notify) {
        AppUpdate.check(this, new AppUpdate.Callback() {
            @Override
            public void onResult(boolean available, String tag, String url) {
                refreshUpdateDot();
                if (!notify) {
                    return;
                }
                if (available) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.update_available) + " " + tag,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.update_none),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure() {
                if (notify) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.update_check_failed),
                            Toast.LENGTH_LONG).show();
                }
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
        refreshUpdateDot();
        // Android 13+ wants an explicit export flag for dynamic
        // receivers; older devices do not have that overload, so
        // the flag branch only runs where it exists.
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(progressReceiver,
                    new android.content.IntentFilter(
                            DownloadService.ACTION_PROGRESS),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(progressReceiver,
                    new android.content.IntentFilter(
                            DownloadService.ACTION_PROGRESS));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(progressReceiver);
        } catch (Exception ignored) {
            // Receiver was never registered (e.g. early pause).
        }
    }

    private void applyCards() {
        Prefs.applyCard(pasteCard, this);
        Prefs.applyCard(recentCard, this);
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
        // v2.2: the main screen keeps only Recent (3 chips). The full
        // list lives in LibraryActivity, so this no longer renders it.
        List<DownloadIndex.Entry> entries = DownloadIndex.read(this);
        recentContainer.removeAllViews();

        boolean empty = entries.isEmpty();
        recentEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

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
        fadeIn(recentContainer);
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
        if (DownloadService.isRunning()) {
            setStatus(getString(R.string.status_busy));
            return;
        }
        // Completed (non-foreground) notifications need the runtime
        // notification permission on Android 13+; the foreground
        // progress notification shows even without it, so the
        // download starts either way.
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    1);
        }
        final boolean audioOnly = isAudioOnly();
        final String quality = qualitySpinner.getSelectedItem() != null
                ? qualitySpinner.getSelectedItem().toString() : "Best";
        progressBar.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        AnimHelper.fadeIn(progressBar);
        AnimHelper.fadeIn(cancelButton);
        pulseAnimator = AnimHelper.startPulse(downloadButton);
        setProgress(0, getString(R.string.status_connecting));
        // v2.3: the service owns the download, so leaving the app
        // (or the phone sleeping the UI) does not stop it. The
        // notification carries the live percentage.
        DownloadService.startDownload(this, rawLink, audioOnly, quality);
    }

    /** Progress broadcasts from DownloadService, while this screen is open. */
    private android.content.BroadcastReceiver progressReceiver =
            new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context,
                                      Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    int pct = intent.getIntExtra(
                            DownloadService.EXTRA_PERCENT, -1);
                    String text = intent.getStringExtra(
                            DownloadService.EXTRA_TEXT);
                    String status = intent.getStringExtra(
                            DownloadService.EXTRA_STATUS);
                    String saved = intent.getStringExtra(
                            DownloadService.EXTRA_SAVED_FILE);
                    boolean finished = intent.getBooleanExtra(
                            DownloadService.EXTRA_FINISHED, false);
                    if (status != null && !status.isEmpty()) {
                        setStatus(status);
                    }
                    if (pct >= 0) {
                        setProgress(pct, text != null ? text : "");
                    }
                    if (saved != null && !saved.isEmpty()) {
                        AnimHelper.completionBounce(downloadButton);
                        refreshLists();
                    }
                    if (finished) {
                        finishProgressUi();
                    }
                }
            };


    /**
     * Direct-file detection, used ONLY to pick the fast path.
     * It is not a site allowlist: a link that is not a direct
     * file is not refused, it is sent to the extractor.
     */

    /**
     * Real yt-dlp format selector for the spinner choice. A capped
     * choice is a real cap: there is deliberately NO final "/best"
     * fallback, so a video with no stream at or below the cap
     * fails with a clear message instead of silently downloading
     * a higher quality than the user chose. "Best" is the only
     * uncapped choice.
     */

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

    /**
     * Move a finished extractor file into Download/GetGo via
     * MediaStore (same destination and index format as v1), then
     * delete the temp copy. Returns via UI status, like v1.
     */

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

}
