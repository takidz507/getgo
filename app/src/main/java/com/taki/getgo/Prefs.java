package com.taki.getgo;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

/**
 * Tiny preferences helper. No account, no telemetry, no server:
 * everything here lives in the app's own SharedPreferences.
 */
public final class Prefs {
    private static final String FILE = "getgo_prefs";
    private static final String KEY_DARK = "dark_mode";
    private static final String KEY_GLASS = "liquid_glass";
    private static final String KEY_NOTIF_ASKED = "notif_asked";
    private static final String KEY_UPDATE_AVAILABLE = "update_available";
    private static final String KEY_UPDATE_TAG = "update_tag";
    private static final String KEY_UPDATE_URL = "update_url";
    // v2.4 paused download: one paused job at a time (GetGo only
    // ever runs one download). Stored locally only. The partial
    // file itself lives in the engine's temp dir (site) or as a
    // named temp file (direct); these keys are just the recipe
    // to resume it, so a paused job survives leaving the app
    // and even a service restart.
    private static final String KEY_PAUSED_LINK = "paused_link";
    private static final String KEY_PAUSED_AUDIO = "paused_audio";
    private static final String KEY_PAUSED_QUALITY = "paused_quality";
    private static final String KEY_PAUSED_DIRECT = "paused_direct";
    private static final String KEY_PAUSED_PERCENT = "paused_percent";
    private static final String KEY_PAUSED_TEMP = "paused_temp";
    private static final String KEY_PAUSED_DOWNLOADED = "paused_downloaded";

    private Prefs() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isDark(Context ctx) {
        return prefs(ctx).getBoolean(KEY_DARK, false);
    }

    public static void setDark(Context ctx, boolean dark) {
        prefs(ctx).edit().putBoolean(KEY_DARK, dark).apply();
    }

    /**
     * Liquid Glass is DEFAULT OFF. When on, cards become translucent
     * frosted-glass lookalikes. This is an Android lookalike only:
     * Android has no native Apple Liquid Glass, and this code does
     * not claim otherwise (see README).
     */
    public static boolean isGlass(Context ctx) {
        return prefs(ctx).getBoolean(KEY_GLASS, false);
    }

    public static void setGlass(Context ctx, boolean glass) {
        prefs(ctx).edit().putBoolean(KEY_GLASS, glass).apply();
    }

    /**
     * v2.3.1: notification permission is asked ONCE, on the
     * very first launch. If the user denies it, GetGo never
     * nags again — the foreground progress notification still
     * shows, only the finished notification may be hidden by
     * the system.
     */
    public static boolean isNotifAsked(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NOTIF_ASKED, false);
    }

    public static void setNotifAsked(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_NOTIF_ASKED, true).apply();
    }

    /**
     * App-update state, from the GitHub releases check (see
     * AppUpdate). Stored locally only, so the ⋮ badge survives a
     * restart until the next check clears or confirms it. This is
     * NOT an install: tapping Update only opens the release page
     * and the user installs the new APK by hand.
     */
    public static boolean isUpdateAvailable(Context ctx) {
        return prefs(ctx).getBoolean(KEY_UPDATE_AVAILABLE, false);
    }

    public static String getUpdateTag(Context ctx) {
        return prefs(ctx).getString(KEY_UPDATE_TAG, "");
    }

    public static String getUpdateUrl(Context ctx) {
        return prefs(ctx).getString(KEY_UPDATE_URL, "");
    }

    public static void setUpdateAvailable(Context ctx, boolean available,
                                          String tag, String url) {
        prefs(ctx).edit()
                .putBoolean(KEY_UPDATE_AVAILABLE, available)
                .putString(KEY_UPDATE_TAG, tag == null ? "" : tag)
                .putString(KEY_UPDATE_URL, url == null ? "" : url)
                .apply();
    }

    public static void clearUpdateAvailable(Context ctx) {
        setUpdateAvailable(ctx, false, "", "");
    }

    public static boolean hasPausedJob(Context ctx) {
        String link = prefs(ctx).getString(KEY_PAUSED_LINK, "");
        return link != null && !link.isEmpty();
    }

    public static void setPausedJob(Context ctx, String link,
                                    boolean audioOnly, String quality,
                                    boolean direct, int percent,
                                    String tempName, long downloaded) {
        prefs(ctx).edit()
                .putString(KEY_PAUSED_LINK, link == null ? "" : link)
                .putBoolean(KEY_PAUSED_AUDIO, audioOnly)
                .putString(KEY_PAUSED_QUALITY, quality == null ? "" : quality)
                .putBoolean(KEY_PAUSED_DIRECT, direct)
                .putInt(KEY_PAUSED_PERCENT, percent)
                .putString(KEY_PAUSED_TEMP, tempName == null ? "" : tempName)
                .putLong(KEY_PAUSED_DOWNLOADED, downloaded)
                .apply();
    }

    public static void clearPausedJob(Context ctx) {
        prefs(ctx).edit()
                .remove(KEY_PAUSED_LINK)
                .remove(KEY_PAUSED_AUDIO)
                .remove(KEY_PAUSED_QUALITY)
                .remove(KEY_PAUSED_DIRECT)
                .remove(KEY_PAUSED_PERCENT)
                .remove(KEY_PAUSED_TEMP)
                .remove(KEY_PAUSED_DOWNLOADED)
                .apply();
    }

    public static String getPausedLink(Context ctx) {
        return prefs(ctx).getString(KEY_PAUSED_LINK, "");
    }

    public static boolean isPausedAudio(Context ctx) {
        return prefs(ctx).getBoolean(KEY_PAUSED_AUDIO, false);
    }

    public static String getPausedQuality(Context ctx) {
        return prefs(ctx).getString(KEY_PAUSED_QUALITY, "Best");
    }

    public static boolean isPausedDirect(Context ctx) {
        return prefs(ctx).getBoolean(KEY_PAUSED_DIRECT, false);
    }

    public static int getPausedPercent(Context ctx) {
        return prefs(ctx).getInt(KEY_PAUSED_PERCENT, 0);
    }

    public static String getPausedTempName(Context ctx) {
        return prefs(ctx).getString(KEY_PAUSED_TEMP, "");
    }

    public static long getPausedDownloaded(Context ctx) {
        return prefs(ctx).getLong(KEY_PAUSED_DOWNLOADED, 0);
    }

    /**
     * One place that decides every card's background, so light/dark
     * and glass/solid stay consistent across every screen. Callers
     * re-apply this after a toggle changes, instead of hard-coding
     * a drawable in a layout.
     */
    public static void applyCard(View card, Context ctx) {
        boolean dark = isDark(ctx);
        boolean glass = isGlass(ctx);
        int res;
        if (glass) {
            res = dark ? R.drawable.card_glass_dark : R.drawable.card_glass_light;
        } else {
            res = dark ? R.drawable.card_solid_dark : R.drawable.card_solid_light;
        }
        card.setBackgroundResource(res);
    }
}
