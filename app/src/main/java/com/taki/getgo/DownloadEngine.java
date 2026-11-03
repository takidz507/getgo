package com.taki.getgo;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

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

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * v2.3 download engine, extracted verbatim from MainActivity's
 * download code (same routing, same format selectors, same
 * MediaStore destination, same index format). It owns no UI:
 * it talks only through Listener, so both MainActivity (UI
 * progress) and DownloadService (notification progress) can
 * run the SAME engine, instead of two copies drifting apart.
 *
 * Context is always the application context, so a download
 * survives its starting Activity being destroyed.
 */
public final class DownloadEngine {
    public interface Listener {
        void onStatus(String text);
        void onProgress(int percent, String text);
        void onSaved(String fileName, String quality);
        void onFinished();
    }

    private static final String EXTRACTOR_PROCESS_ID = "getgo-download";
    private static boolean engineReady = false;
    private static boolean ytdlpUpdateAttempted = false;

    private final Context ctx;
    private final Listener listener;
    private volatile boolean cancelled;
    private Thread thread;

    public DownloadEngine(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    public void start(final String rawLink, final boolean audioOnly,
                      final String quality) {
        cancelled = false;
        final boolean direct = isDirectFileLink(rawLink);
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (direct) {
                    downloadDirect(rawLink, audioOnly, quality);
                } else {
                    downloadViaExtractor(rawLink, audioOnly, quality);
                }
            }
        });
        thread.start();
    }

    public boolean isRunning() {
        return thread != null && thread.isAlive();
    }

    public void cancel() {
        cancelled = true;
        if (thread != null) {
            thread.interrupt();
        }
        try {
            YoutubeDL.getInstance().destroyProcessById(EXTRACTOR_PROCESS_ID);
        } catch (Exception ignored) {
        }
    }

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

    private void downloadViaExtractor(final String rawLink,
            final boolean audioOnly, final String quality) {
        try {
            if (!engineReady) {
                listener.onStatus(ctx.getString(R.string.status_preparing_engine));
                try {
                    YoutubeDL.getInstance().init(ctx);
                    FFmpeg.getInstance().init(ctx);
                    engineReady = true;
                } catch (Exception e) {
                    listener.onStatus(ctx.getString(R.string.status_engine_failed)
                            + " " + e.getMessage());
                    listener.onFinished();
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
                    listener.onStatus(ctx.getString(R.string.status_updating_ytdlp));
                    YoutubeDL.getInstance().updateYoutubeDL(ctx,
                            YoutubeDL.UpdateChannel.NIGHTLY.INSTANCE);
                    String v = null;
                    try {
                        v = YoutubeDL.getInstance().version(ctx);
                    } catch (Exception ignored) {
                        // Version display is a nicety, not a gate.
                    }
                    listener.onStatus(ctx.getString(R.string.status_ytdlp_updated)
                            + " " + (v != null ? v.trim() : ""));
                } catch (Exception e) {
                    listener.onStatus(ctx.getString(R.string.status_ytdlp_update_failed)
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
                        listener.onStatus(ctx.getString(R.string.status_found)
                                + " " + title.toString().trim());
                    }
                }
            } catch (Exception ignored) {
                // Preview is a nicety, not a gate.
            }
            if (cancelled) {
                listener.onStatus(ctx.getString(R.string.status_cancelled));
                listener.onFinished();
                return;
            }

            File tempDir = new File(ctx.getExternalFilesDir(null), "getgo-yt");
            if (!tempDir.exists() && !tempDir.mkdirs() && !tempDir.exists()) {
                listener.onStatus(ctx.getString(R.string.status_failed) + " temp dir");
                listener.onFinished();
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
                                listener.onProgress(pct, text);
                            }
                            return Unit.INSTANCE;
                        }
                    };
            YoutubeDL.getInstance().execute(request, EXTRACTOR_PROCESS_ID, callback);

            if (cancelled) {
                listener.onStatus(ctx.getString(R.string.status_cancelled));
                listener.onFinished();
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
                listener.onStatus(ctx.getString(R.string.status_no_file));
                listener.onFinished();
                return;
            }
            moveTempFileToLibrary(best, quality);
        } catch (YoutubeDLException e) {
            // yt-dlp's own failure: unsupported site, login-only
            // content, DRM, or a site that changed. Say so plainly
            // instead of blaming the network or the user.
            listener.onStatus(ctx.getString(R.string.status_site_failed)
                    + " " + (e.getMessage() != null ? e.getMessage() : ""));
        } catch (Exception e) {
            listener.onStatus(ctx.getString(R.string.status_failed)
                    + " " + (e.getMessage() != null ? e.getMessage() : ""));
        } finally {
            listener.onFinished();
        }
    }

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
                savedUri = ctx.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (savedUri == null) {
                    throw new Exception("MediaStore insert returned null");
                }
                out = ctx.getContentResolver().openOutputStream(savedUri);
            } else {
                File dir = new File(
                        ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GetGo");
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
            DownloadIndex.append(ctx, new DownloadIndex.Entry(
                    fileName, size, savedUri.toString(), System.currentTimeMillis()));
            listener.onProgress(100, DownloadIndex.formatSize(size));
            listener.onStatus(ctx.getString(R.string.status_done) + " " + fileName
                    + " (" + quality + ")");
            listener.onSaved(fileName, quality);
        } catch (Exception e) {
            listener.onStatus(ctx.getString(R.string.status_failed)
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
                listener.onStatus(ctx.getString(R.string.status_http_error) + " " + code);
                listener.onFinished();
                return;
            }
            String contentType = conn.getContentType();
            if (contentType != null && contentType.startsWith("text/html")) {
                // A web page is not a file. Refuse honestly instead
                // of saving HTML and calling it a video.
                listener.onStatus(ctx.getString(R.string.status_no_site_extraction));
                listener.onFinished();
                return;
            }
            fileName = fileNameFrom(conn, rawLink);
            long total = conn.getContentLengthLong();
            boolean isVideoFile = fileName.toLowerCase().endsWith(".mp4")
                    || fileName.toLowerCase().endsWith(".mkv")
                    || fileName.toLowerCase().endsWith(".webm");
            if (audioOnly && isVideoFile) {
                listener.onStatus(ctx.getString(R.string.status_audio_note));
            }

            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE,
                        contentType != null ? contentType.split(";")[0] : "application/octet-stream");
                values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/GetGo");
                savedUri = ctx.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (savedUri == null) {
                    throw new Exception("MediaStore insert returned null");
                }
                out = ctx.getContentResolver().openOutputStream(savedUri);
            } else {
                File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GetGo");
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
                    listener.onStatus(ctx.getString(R.string.status_cancelled));
                    listener.onFinished();
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
                        listener.onProgress(pct, DownloadIndex.formatSize(downloaded)
                                + " / " + DownloadIndex.formatSize(total));
                    } else {
                        listener.onProgress(0, DownloadIndex.formatSize(downloaded));
                    }
                }
            }
            out.close();
            out = null;
            DownloadIndex.append(ctx, new DownloadIndex.Entry(
                    fileName, downloaded, savedUri.toString(), System.currentTimeMillis()));
            listener.onProgress(100, DownloadIndex.formatSize(downloaded));
            String done = ctx.getString(R.string.status_done) + " " + fileName;
            if (audioOnly && isVideoFile) {
                done += " — " + ctx.getString(R.string.status_audio_note);
            }
            // Quality is a real choice for future site streams; for a
            // direct file the file is saved as-is, and v1 says so.
            done += " (" + quality + ")";
            listener.onStatus(done);
            listener.onSaved(fileName, quality);
        } catch (Exception e) {
            listener.onStatus(ctx.getString(R.string.status_failed) + " " + e.getMessage());
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
            listener.onFinished();
        }
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
