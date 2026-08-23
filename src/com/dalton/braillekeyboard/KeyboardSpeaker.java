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
 * <p>A new engine is created for each input session by {@link #create}; see
 * the note in {@code BrailleIME.onCreate} on why the underlying engine is a
 * process-wide singleton.
 */
final class KeyboardSpeaker {

    private final Context context;
    private Speech speech;

    KeyboardSpeaker(Context context) {
        this.context = context;
    }

    /**
     * Creates the TTS wrapper for this input session. The ready listener runs
     * once the engine finished initialising.
     */
    void create(Speech.OnReadyListener readyListener) {
        speech = new Speech(context, readyListener);
    }

    /** Speaks a string resource, flushing whatever was being said. */
    void speak(int stringRes) {
        speech.speak(context, context.getString(stringRes),
                Speech.QUEUE_FLUSH);
    }

    void speak(int stringRes, int queueMode, Object... formatArgs) {
        speech.speak(context, context.getString(stringRes, formatArgs),
                queueMode);
    }

    void speak(CharSequence text, int queueMode) {
        speech.speak(context, text, queueMode);
    }

    /**
     * Reads text to the user, spelling it out or staying silent when the
     * field is a password field (see {@link Speech#readConsiderPassword}).
     */
    void readConsiderPassword(String format, String text,
            boolean isPasswordField, int queueMode) {
        speech.readConsiderPassword(context, format, text, isPasswordField,
                queueMode);
    }

    void stop() {
        speech.stop();
    }

    /**
     * Speaks an optional farewell utterance and releases the engine once it
     * finished playing. The engine is never shut down synchronously so the
     * farewell is not cut off.
     */
    void shutdown(String farewell) {
        speech.shutdown(farewell);
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
        if (locale == null) {
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
