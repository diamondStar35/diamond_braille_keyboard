package com.dalton.braillekeyboard;

/**
 * Represents keyboard events that can trigger sound and haptic feedback.
 *
 * <p>Each event knows the key used to refer to it in a theme's config.ini
 * [sounds] section, so the mapping between events and theme entries lives in
 * one place.
 */
public enum FeedbackEvent {
    OPEN("open"),
    CLOSE("close"),
    TYPE("type"),
    TYPE_UPPER("type_upper"),
    DELETE("delete"),
    NEW_LINE("new_line"),
    CALIBRATE("calibrate");

    /** The key used for this event in a theme's config.ini [sounds] section. */
    public final String configKey;

    FeedbackEvent(String configKey) {
        this.configKey = configKey;
    }

    /**
     * Look up the event for a config.ini key, or {@code null} if the key is
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
