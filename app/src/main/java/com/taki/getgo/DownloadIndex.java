package com.taki.getgo;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local index of files this app downloaded, kept in the app's private
 * files directory. One line per download: name|sizeBytes|uri|timeMillis.
 * Filenames are sanitised before saving, so '|' never appears in a name.
 */
public final class DownloadIndex {
    public static final class Entry {
        public final String name;
        public final long sizeBytes;
        public final String uri;
        public final long timeMillis;

        public Entry(String name, long sizeBytes, String uri, long timeMillis) {
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.uri = uri;
            this.timeMillis = timeMillis;
        }
    }

    private DownloadIndex() {
    }

    private static File indexFile(Context ctx) {
        return new File(ctx.getFilesDir(), "downloads_index.txt");
    }

    public static synchronized List<Entry> read(Context ctx) {
        List<Entry> out = new ArrayList<Entry>();
        File f = indexFile(ctx);
        if (!f.exists()) {
            return out;
        }
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split("\\|", -1);
                if (parts.length != 4) {
                    continue;
                }
                try {
                    out.add(new Entry(parts[0], Long.parseLong(parts[1]),
                            parts[2], Long.parseLong(parts[3])));
                } catch (NumberFormatException ignored) {
                    // Skip a corrupt line, keep the rest.
                }
            }
            r.close();
        } catch (Exception ignored) {
        }
        Collections.reverse(out);
        return out;
    }

    /** Remove one entry from the index (used by Library delete). */
    public static synchronized void remove(Context ctx, Entry target) {
        List<Entry> all = read(ctx);
        List<Entry> kept = new ArrayList<Entry>();
        for (int i = 0; i < all.size(); i++) {
            Entry e = all.get(i);
            if (e.uri.equals(target.uri) && e.name.equals(target.name)) {
                continue;
            }
            kept.add(e);
        }
        // The file stores oldest-first; read() reverses, so reverse back.
        Collections.reverse(kept);
        try {
            FileOutputStream fos = new FileOutputStream(indexFile(ctx), false);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            for (int i = 0; i < kept.size(); i++) {
                Entry e = kept.get(i);
                w.write(e.name + "|" + e.sizeBytes + "|" + e.uri + "|"
                        + e.timeMillis + "\n");
            }
            w.close();
        } catch (Exception ignored) {
        }
    }

    public static synchronized void append(Context ctx, Entry e) {
        try {
            FileOutputStream fos = new FileOutputStream(indexFile(ctx), true);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            w.write(e.name + "|" + e.sizeBytes + "|" + e.uri + "|" + e.timeMillis + "\n");
            w.close();
        } catch (Exception ignored) {
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
