package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.List;

/**
 * Coordinates all keyboard feedback: sounds, played through the active
 * {@link SoundTheme} by {@link SoundThemeManager}, and haptics, delivered
 * through the {@link Vibrator}.
 *
 * <p>Both sound and haptic feedback are gated by their preferences, which are
 * read through {@link Options#getSharedPreferences(Context)} so they honour
 * the device-protected storage used by the settings screen on Android 7+.
 */
public class FeedbackManager {

    private final Context context;
    private final Vibrator vibrator;
    private SoundThemeManager soundThemeManager;
    private boolean released;

    // Reload the sound theme as soon as it is changed in the settings, even
    // while the keyboard is already open.
    private final SharedPreferences.OnSharedPreferenceChangeListener
            themeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(
                        SharedPreferences prefs, String key) {
                    if (context.getString(R.string.pref_sound_theme_key)
                            .equals(key)) {
                        soundThemeManager.reloadTheme();
                    }
                }
            };

    public FeedbackManager(Context context) {
        this.context = context.getApplicationContext();
        vibrator = (Vibrator) context
                .getSystemService(Context.VIBRATOR_SERVICE);
        soundThemeManager = new SoundThemeManager(context);
        reloadTheme();
        Options.getSharedPreferences(this.context)
                .registerOnSharedPreferenceChangeListener(themeListener);
    }

    /**
     * Recreates the sound pipeline after {@link #release()} so a manager can
     * be reused when its keyboard view is shown again. Without this, events
     * after a release would be silently dropped.
     */
    private void ensureActive() {
        if (!released) {
            return;
        }
        released = false;
        soundThemeManager = new SoundThemeManager(context);
        reloadTheme();
        Options.getSharedPreferences(context)
                .registerOnSharedPreferenceChangeListener(themeListener);
    }

    /** (Re)load the sound theme selected in the settings. */
    public void reloadTheme() {
        ensureActive();
        soundThemeManager.reloadTheme();
    }

    /** All themes shipped with the app, for the settings screen. */
    public static List<SoundTheme> getAvailableThemes(Context context) {
        return SoundTheme.listThemes(context);
    }

    /** Vibrate for the given time; the keyboard's single haptics channel. */
    public void vibrate(long milliseconds) {
        if (vibrator != null && milliseconds > 0) {
            vibrator.vibrate(milliseconds);
        }
    }

    /**
     * Fire a feedback event: play the active theme's sound when sound
     * feedback is enabled, and vibrate when haptic feedback is enabled.
     */
    public void emitEvent(FeedbackEvent event) {
        ensureActive();
        if (Options.getBooleanPreference(context,
                R.string.pref_sound_feedback_key, true)) {
            soundThemeManager.playEvent(event);
        }

        if (Options.getBooleanPreference(context,
                R.string.pref_haptic_feedback_key, true)
                && event.isHapticEnabled(context)) {
            long duration = event.vibrationMillis;
            if (duration > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrateEffect(duration,
                            VibrationEffect.DEFAULT_AMPLITUDE);
                } else {
                    vibrate(duration);
                }
            }
        }
    }

    private void vibrateEffect(long milliseconds, int amplitude) {
        vibrator.vibrate(VibrationEffect.createOneShot(milliseconds,
                amplitude));
    }

    /** Release the SoundPool, all loaded sounds and the preference listener. */
    public void release() {
        if (released) {
            return;
        }
        released = true;
        Options.getSharedPreferences(context)
                .unregisterOnSharedPreferenceChangeListener(themeListener);
        soundThemeManager.release();
    }
}
