package com.dalton.braillekeyboard;

import android.content.Context;

/**
 * Represents the speech announcements the keyboard can make, each of which
 * the user can enable or disable in the "Speech events" settings screen.
 *
 * <p>Each event carries the preference key, title and description used by
 * the settings screen, so the settings screen can be populated directly from
 * this enum and the gating logic reads the same preference. Adding a new
 * speech event only requires a new enum member plus the strings it
 * references.
 */
public enum SpeechEvent {
    /** Announce that the keyboard is ready when it is shown. */
    KEYBOARD_SHOWN(
            R.string.pref_speech_event_keyboard_shown_key,
            R.string.pref_speech_event_keyboard_shown_title,
            R.string.pref_speech_event_keyboard_shown_summary, true),
    /** Announce that the keyboard is being closed. */
    KEYBOARD_CLOSED(
            R.string.pref_speech_event_keyboard_closed_key,
            R.string.pref_speech_event_keyboard_closed_title,
            R.string.pref_speech_event_keyboard_closed_summary, true),
    /**
     * Announce a misspelled word while typing. This was previously the
     * "Echo misspellings" feedback setting; the preference key is unchanged
     * so existing user settings carry over.
     */
    MISSPELLING(
            R.string.pref_echo_misspellings_key,
            R.string.pref_echo_misspellings_title,
            R.string.pref_echo_misspellings_summary, true);

    /** The preference key storing whether this announcement is enabled. */
    public final int keyResource;

    /** The title shown in the "Speech events" settings screen. */
    public final int titleResource;

    /** The description shown in the "Speech events" settings screen. */
    public final int summaryResource;

    /** The default value of the preference. */
    public final boolean defaultValue;

    SpeechEvent(int keyResource, int titleResource, int summaryResource,
            boolean defaultValue) {
        this.keyResource = keyResource;
        this.titleResource = titleResource;
        this.summaryResource = summaryResource;
        this.defaultValue = defaultValue;
    }

    /** Whether this announcement is enabled by the user. */
    public boolean isEnabled(Context context) {
        return Options.getBooleanPreference(context, keyResource,
                defaultValue);
    }
}
