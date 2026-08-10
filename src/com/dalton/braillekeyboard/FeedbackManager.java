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
    private final SoundThemeManager soundThemeManager;
    private final Vibrator vibrator;

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

    /** (Re)load the sound theme selected in the settings. */
    public void reloadTheme() {
        soundThemeManager.reloadTheme();
    }

    /** All themes shipped with the app, for the settings screen. */
    public static List<SoundTheme> getAvailableThemes(Context context) {
        return SoundTheme.listThemes(context);
    }

    /**
     * Fire a feedback event: play the active theme's sound when sound
     * feedback is enabled, and vibrate when haptic feedback is enabled.
     */
    public void emitEvent(FeedbackEvent event) {
        if (Options.getBooleanPreference(context,
                R.string.pref_sound_feedback_key, true)) {
            soundThemeManager.playEvent(event);
        }

        if (Options.getBooleanPreference(context,
                R.string.pref_haptic_feedback_key, true)
                && event.isHapticEnabled(context) && vibrator != null) {
            long duration = event.vibrationMillis;
            if (duration > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration,
                            VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
            }
        }
    }

    /** Release the SoundPool, all loaded sounds and the preference listener. */
    public void release() {
        Options.getSharedPreferences(context)
                .unregisterOnSharedPreferenceChangeListener(themeListener);
        soundThemeManager.release();
    }
}
