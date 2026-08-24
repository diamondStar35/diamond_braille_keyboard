/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dalton.braillekeyboard;

import java.util.Locale;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

/**
 * The speech facade of the keyboard: every announcement goes through here,
 * including password-aware reading and locale application.
 *
 * <p>The facade does not own an engine. It is attached to the one
 * process-wide {@link Speech} instance that {@code BrailleIME.onCreate}
 * creates and shares between the view and the emoji/command engines, so the
 * underlying TTS engine is constructed once per process instead of being torn
 * down and re-initialised on every keyboard open (which both delayed opening
 * and raced the asynchronous initialisation against wrapper replacement,
 * permanently silencing whichever wrapper lost).
 */
final class KeyboardSpeaker {

    private final Context context;
    private Speech speech;

    KeyboardSpeaker(Context context) {
        this.context = context;
    }

    /**
     * Attaches the shared speech instance owned by the IME service. Must be
     * called before any announcement is made.
     */
    void attach(Speech sharedSpeech) {
        speech = sharedSpeech;
    }

    /** Speaks a string resource, flushing whatever was being said. */
    void speak(int stringRes) {
        if (speech == null) {
            return;
        }
        speech.speak(context, context.getString(stringRes),
                Speech.QUEUE_FLUSH);
    }

    void speak(int stringRes, int queueMode, Object... formatArgs) {
        if (speech == null) {
            return;
        }
        speech.speak(context, context.getString(stringRes, formatArgs),
                queueMode);
    }

    void speak(CharSequence text, int queueMode) {
        if (speech == null) {
            return;
        }
        speech.speak(context, text, queueMode);
    }

    /**
     * Reads text to the user, spelling it out or staying silent when the
     * field is a password field (see {@link Speech#readConsiderPassword}).
     */
    void readConsiderPassword(String format, String text,
            boolean isPasswordField, int queueMode) {
        if (speech == null) {
            return;
        }
        speech.readConsiderPassword(context, format, text, isPasswordField,
                queueMode);
    }

    void stop() {
        if (speech == null) {
            return;
        }
        speech.stop();
    }

    /** Applies the locale to the speech engine first, then to resources. */
    boolean applyLocale(Locale locale) {
        return applyLocale(locale, true);
    }

    /**
     * Switches the keyboard's locale: mutates the process resources so
     * strings resolve in the Braille table's language, optionally after
     * switching the speech engine to match. When the engine locale cannot be
     * set, the resources are left untouched so text and speech agree.
     */
    boolean applyLocale(Locale locale, boolean setTtsLocale) {
        if (locale == null || speech == null) {
            return false;
        }
        Resources resources = context.getResources();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        android.content.res.Configuration conf = resources.getConfiguration();
        if (!conf.locale.equals(locale)) {
            if (!setTtsLocale || speech.setLocale(locale)) {
                conf.setLocale(locale);
                resources.updateConfiguration(conf, displayMetrics);
                return true;
            }
        }
        return false;
    }
}
