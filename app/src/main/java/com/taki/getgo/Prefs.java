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
