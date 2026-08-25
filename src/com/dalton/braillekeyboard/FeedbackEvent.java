package com.dalton.braillekeyboard;

import android.content.Context;

/**
 * Represents keyboard events that can trigger sound and haptic feedback.
 *
 * <p>Each event knows the key used to refer to it in the "sounds" object of
 * a theme's config.json manifest, so the mapping between events and theme
 * entries lives in one place. Each event also carries its haptic
 * configuration: the default vibration length and the preference that lets
 * the user enable or disable the vibration for that event in the "Haptic
 * feedback events" settings screen.
 */
public enum FeedbackEvent {
    OPEN("open", 50,
            R.string.pref_haptic_event_keyboard_shown_key,
            R.string.pref_haptic_event_keyboard_shown_title,
            R.string.pref_haptic_event_keyboard_shown_summary, true),
    CLOSE("close", 200,
            R.string.pref_haptic_event_keyboard_closed_key,
            R.string.pref_haptic_event_keyboard_closed_title,
            R.string.pref_haptic_event_keyboard_closed_summary, true),
    TYPE("type", 25,
            R.string.pref_haptic_event_typing_key,
            R.string.pref_haptic_event_typing_title,
            R.string.pref_haptic_event_typing_summary, true),
    TYPE_UPPER("type_upper", 35,
            R.string.pref_haptic_event_capital_key,
            R.string.pref_haptic_event_capital_title,
            R.string.pref_haptic_event_capital_summary, true),
    DELETE("delete", 30,
            R.string.pref_haptic_event_delete_key,
            R.string.pref_haptic_event_delete_title,
            R.string.pref_haptic_event_delete_summary, true),
    NEW_LINE("new_line", 25,
            R.string.pref_haptic_event_new_line_key,
            R.string.pref_haptic_event_new_line_title,
            R.string.pref_haptic_event_new_line_summary, true),
    CALIBRATE("calibrate", 125,
            R.string.pref_haptic_event_calibration_key,
            R.string.pref_haptic_event_calibration_title,
            R.string.pref_haptic_event_calibration_summary, true),
    /** A misspelled word is announced while typing. */
    MISSPELLING("misspelling", 80,
            R.string.pref_haptic_event_misspelling_key,
            R.string.pref_haptic_event_misspelling_title,
            R.string.pref_haptic_event_misspelling_summary, true),
    /** Generic commands such as switching tables or toggling emoji mode. */
    COMMAND("command", 30,
            R.string.pref_haptic_event_commands_key,
            R.string.pref_haptic_event_commands_title,
            R.string.pref_haptic_event_commands_summary, true);

    /** The key naming this event in a theme manifest's "sounds" object. */
    public final String configKey;

    /** The default vibration length for this event, in milliseconds. */
    public final long vibrationMillis;

    /** The preference key that enables or disables the haptic for this event. */
    public final int hapticKeyResource;

    /** The title shown in the "Haptic feedback events" settings screen. */
    public final int hapticTitleResource;

    /** The description shown in the "Haptic feedback events" settings screen. */
    public final int hapticSummaryResource;

    /** The default value of the haptic preference. */
    public final boolean hapticDefault;

    FeedbackEvent(String configKey, long vibrationMillis,
            int hapticKeyResource, int hapticTitleResource,
            int hapticSummaryResource, boolean hapticDefault) {
        this.configKey = configKey;
        this.vibrationMillis = vibrationMillis;
        this.hapticKeyResource = hapticKeyResource;
        this.hapticTitleResource = hapticTitleResource;
        this.hapticSummaryResource = hapticSummaryResource;
        this.hapticDefault = hapticDefault;
    }

    /** Whether the haptic feedback for this event is enabled by the user. */
    public boolean isHapticEnabled(Context context) {
        return Options.getBooleanPreference(context, hapticKeyResource,
                hapticDefault);
    }

    /**
     * Look up the event for a manifest key, or {@code null} if the key is
     * not a known event.
     */
    public static FeedbackEvent fromConfigKey(String key) {
        for (FeedbackEvent event : values()) {
            if (event.configKey.equalsIgnoreCase(key)) {
                return event;
            }
        }
        return null;
    }
}
