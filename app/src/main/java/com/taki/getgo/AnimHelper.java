package com.taki.getgo;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.HashMap;
import java.util.Map;

/**
 * All GetGo animations live here, so they stay light, short and
 * consistent — and so they can stop implicitly.
 *
 * Implicit off-switch: if the device has animations turned off
 * (Android's animator duration scale = 0, e.g. accessibility /
 * reduced-motion settings), every helper below becomes a no-op
 * and the UI changes instantly. No new setting, no new permission,
 * and no animation is allowed to block a download or a tap.
 *
 * Durations: press 110+140 ms, fades 300 ms, pulse 1000 ms cycle,
 * completion bounce 180+220 ms. Nothing loops forever except the
 * download pulse, which is cancelled the moment a download ends.
 */
public final class AnimHelper {
    private static final AccelerateDecelerateInterpolator EASE =
            new AccelerateDecelerateInterpolator();
    private static final Map<View, ObjectAnimator[]> PULSES =
            new HashMap<View, ObjectAnimator[]>();

    private AnimHelper() {
    }

    public static boolean animationsEnabled(Context ctx) {
        try {
            float scale = Settings.Global.getFloat(
                    ctx.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale != 0f;
        } catch (Exception ignored) {
            return true;
        }
    }

    /** Short shrink-and-return on any tap. */
    public static void press(final View v) {
        if (v == null) {
            return;
        }
        if (!animationsEnabled(v.getContext())) {
            return;
        }
        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(110)
                .setInterpolator(EASE)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                                .setInterpolator(EASE).start();
                    }
                }).start();
    }

    /** Fade a view in from transparent. Used for lists and states. */
    public static void fadeIn(View v) {
        if (v == null) {
            return;
        }
        if (!animationsEnabled(v.getContext())) {
            v.setAlpha(1f);
            return;
        }
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(300).setInterpolator(EASE).start();
    }

    /** Fade a view out, then set it GONE. Runs endAction afterwards. */
    public static void fadeOutToGone(final View v, final Runnable endAction) {
        if (v == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }
        if (!animationsEnabled(v.getContext())) {
            v.setVisibility(View.GONE);
            if (endAction != null) {
                endAction.run();
            }
            return;
        }
        v.animate().alpha(0f).setDuration(250).setInterpolator(EASE)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.setVisibility(View.GONE);
                        v.setAlpha(1f);
                        if (endAction != null) {
                            endAction.run();
                        }
                    }
                }).start();
    }

    /** Gentle pulse while a download runs. Caller must cancel it. */
    public static ObjectAnimator startPulse(View v) {
        if (v == null || !animationsEnabled(v.getContext())) {
            return null;
        }
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.04f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.04f);
        scaleX.setDuration(1000);
        scaleY.setDuration(1000);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setInterpolator(EASE);
        scaleY.setInterpolator(EASE);
        scaleX.start();
        scaleY.start();
        PULSES.put(v, new ObjectAnimator[]{scaleX, scaleY});
        return scaleX;
    }

    public static void stopPulse(View v, ObjectAnimator handle) {
        if (v == null) {
            return;
        }
        // Cancel only the pulse animators, never view.animate() —
        // a completion bounce posted afterwards must survive.
        ObjectAnimator[] pair = PULSES.remove(v);
        if (pair != null) {
            for (int i = 0; i < pair.length; i++) {
                pair[i].cancel();
            }
        }
        if (handle != null) {
            handle.cancel();
        }
        v.setScaleX(1f);
        v.setScaleY(1f);
    }

    /** Small bounce when a download completes: 1 -> 1.12 -> 1. */
    public static void completionBounce(final View v) {
        if (v == null || !animationsEnabled(v.getContext())) {
            return;
        }
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(180)
                .setInterpolator(EASE)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                                .setInterpolator(EASE).start();
                    }
                }).start();
    }
}
