/*
 * Copyright (C) 2016 The Soft Braille Keyboard Authors
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;

/**
 * Allows the Braille IME to interface with the Android TTS service. This class
 * provides a lot of helper methods that help speak messages in text in the
 * appropriate format at the right times according to local app settings. It
 * ultimately saves a lot of code duplication.
 *
 * <p>One instance owns one engine for its whole life. The engine itself is
 * replaceable through {@link #ensureFresh}, which starts a replacement
 * asynchronously and only swaps it in once it reports ready, so callers can
 * keep a {@code final} reference to this object forever and never end up
 * holding a wrapper whose engine died. Utterances requested while an engine is
 * still starting up are queued and spoken the moment it becomes ready rather
 * than dropped.
 *
 * You should always call shutdown() when you are finished with the service.
 */
public class Speech {

    /**
     * Implement this to receive a callback when the Android speech service is
     * ready.
     */
    public interface OnReadyListener {

        /**
         * Called when the tts is ready for use.
         */
        void ttsReady();
    }

    // Flush speech utterances
    public static final int QUEUE_FLUSH = TextToSpeech.QUEUE_FLUSH;
    // Queue speech utterances
    public static final int QUEUE_ADD = TextToSpeech.QUEUE_ADD;

    private static final int MAX_SPEECH_LENGTH = 3900;
    private static final String SHUTDOWN_ID = "SHUTDOWN";
    // Announcements queued while an engine starts up. Bounded so a TTS engine
    // that never becomes ready cannot grow the queue without limit; when it
    // does become ready the user hears recent speech, not a backlog.
    private static final int MAX_PENDING_UTTERANCES = 8;

    /** One utterance held back until an engine is ready to speak it. */
    private static final class PendingUtterance {
        final String text;
        final int queueMode;
        final HashMap<String, String> params;
        final String id;

        PendingUtterance(String text, int queueMode,
                HashMap<String, String> params, String id) {
            this.text = text;
            this.queueMode = queueMode;
            this.params = params;
            this.id = id;
        }
    }

    private final Context appContext;
    private final AudioManager audioManager;
    private final Map<String, String> speechMap = new HashMap<String, String>();
    // Callbacks from the TTS service arrive on binder threads; every mutation
    // of the engine state is funnelled onto the main thread through this so
    // the fields below need no locking.
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<PendingUtterance> pending =
            new ArrayList<PendingUtterance>();

    // The engine currently able to speak, or null while none is ready.
    private TextToSpeech tts;
    // An engine that is still starting up, or null when none is in flight.
    // Held so it can be shut down if this instance is disposed of before its
    // initialisation completes.
    private TextToSpeech starting;
    // The settings the live (or starting) engine was built from; a change of
    // these is what makes an engine stale.
    private String engineName;
    private boolean useAccessibilityVolume;
    // The locale last applied successfully, reapplied to any replacement
    // engine so a language switch survives the swap.
    private Locale locale;
    private boolean disposed;

    private final UtteranceProgressListener progressListener = new UtteranceProgressListener() {

        @Override
        public void onStart(String utteranceId) {
            audioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }

        @Override
        @Deprecated
        public void onError(String utteranceId) {
        }

        @Override
        public void onDone(final String utteranceId) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
            if (SHUTDOWN_ID.equals(utteranceId)) {
                // Runs on a binder thread; engine state is only ever touched
                // on the main thread.
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        dispose();
                    }
                });
            }
        }
    };

    private final OnAudioFocusChangeListener audioFocusChangeListener = new OnAudioFocusChangeListener() {

        @Override
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        }
    };

    /**
     * Construct a Speech instance for integration with the Android tts service
     * and various helper methods specific for SBK.
     *
     * @param context
     *            The application context.
     * @param listener
     *            The callback that will be invoked when the tts service is
     *            ready for use.
     */
    public Speech(final Context context, final OnReadyListener listener) {
        appContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        audioManager = (AudioManager) appContext
                .getSystemService(Context.AUDIO_SERVICE);

        // Some symbols are not spoken natively by TTS engines, so add them into
        // the map from strings.xml
        setSpeechMap(context, speechMap);

        startEngine(listener);
    }

    /**
     * Starts a fresh engine if the settings it would be built from changed, or
     * if no engine is alive to speak with. The replacement initialises in the
     * background and only takes over once it reports ready, so the current
     * engine - if there is one - keeps speaking in the meantime and the
     * keyboard is never left holding a dead one.
     *
     * <p>Call this at the points where a new engine is worth having, typically
     * when the keyboard opens. It is cheap and does nothing when the live
     * engine already matches the settings.
     *
     * @param context
     *            The application context.
     */
    public void ensureFresh(Context context) {
        if (disposed || starting != null) {
            // Either this instance is finished with, or a replacement is
            // already on its way; a second one would only race the first.
            return;
        }
        if (tts != null && matchesSettings(context)) {
            return;
        }
        Diagnostics.log(context, "starting a fresh speech engine (live="
                + (tts != null) + ")");
        startEngine(null);
    }

    // Do the settings the live engine was built from still match the ones
    // configured now?
    private boolean matchesSettings(Context context) {
        return TextUtils.equals(engineName, Options.getStringPreference(context,
                R.string.pref_text_to_speech_engine_key, null))
                && useAccessibilityVolume == Options.getBooleanPreference(
                        context, R.string.pref_use_accessibility_volume_key,
                        false);
    }

    // Build an engine from the current settings. The engine object is handed
    // to the ready callback through a holder rather than read back from a
    // field: onInit can fire before the TextToSpeech constructor has even
    // returned, and an engine that arrived "too early" used to be discarded,
    // leaving the keyboard permanently silent.
    private void startEngine(final OnReadyListener listener) {
        final boolean accessibilityVolume = Options.getBooleanPreference(
                appContext, R.string.pref_use_accessibility_volume_key, false);
        final String engine = Options.getStringPreference(appContext,
                R.string.pref_text_to_speech_engine_key, null);
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(appContext,
                new TextToSpeech.OnInitListener() {

                    @Override
                    public void onInit(final int status) {
                        // Hop to the main thread: by the time this runs the
                        // constructor above has returned, so holder[0] is set
                        // whichever thread the service called back on.
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                onEngineInit(status, holder[0], engine,
                                        accessibilityVolume, listener);
                            }
                        });
                    }
                }, engine);
        starting = holder[0];
    }

    // Adopt a newly initialised engine, or drop it if it failed or is no
    // longer wanted. Always runs on the main thread.
    private void onEngineInit(int status, TextToSpeech engine,
            String newEngineName, boolean accessibilityVolume,
            OnReadyListener listener) {
        if (engine != starting) {
            // Superseded while starting up; it is nobody's engine now.
            engine.shutdown();
            return;
        }
        starting = null;
        if (status != TextToSpeech.SUCCESS || disposed) {
            engine.shutdown();
            if (status != TextToSpeech.SUCCESS) {
                Diagnostics.log(appContext,
                        "speech engine failed to initialise status=" + status);
                pending.clear();
            }
            return;
        }

        // The engine this one replaces has had its turn; releasing it here
        // rather than up front is what keeps speech continuous across a
        // settings change.
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        tts = engine;
        engineName = newEngineName;
        useAccessibilityVolume = accessibilityVolume;

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(accessibilityVolume
                        ? AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                        : AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        engine.setAudioAttributes(audioAttributes);
        engine.setOnUtteranceProgressListener(progressListener);
        if (locale != null) {
            setLocale(locale);
        }

        // Whether speech is alive was invisible in problem reports, which is
        // exactly the question a "the keyboard went silent" report asks.
        Diagnostics.log(appContext, "speech engine ready engine="
                + (newEngineName != null ? newEngineName : "system default")
                + " accessibilityVolume=" + accessibilityVolume
                + " queued=" + pending.size());
        speakPending();
        if (listener != null) {
            listener.ttsReady();
        }
    }

    // Speak whatever was asked for while the engine was starting up.
    private void speakPending() {
        if (pending.isEmpty()) {
            return;
        }
        List<PendingUtterance> queued =
                new ArrayList<PendingUtterance>(pending);
        pending.clear();
        for (PendingUtterance utterance : queued) {
            ttsSpeak(utterance.text, utterance.queueMode, utterance.params,
                    utterance.id);
        }
    }

    // Hold an utterance until an engine is ready for it, honouring the
    // queueing mode the caller asked for: a flush drops what was waiting, in
    // the same way it would have interrupted speech already in progress.
    private void enqueue(String text, int queueMode,
            HashMap<String, String> params, String id) {
        if (queueMode == QUEUE_FLUSH) {
            pending.clear();
        } else if (pending.size() >= MAX_PENDING_UTTERANCES) {
            pending.remove(0);
        }
        pending.add(new PendingUtterance(text, queueMode, params, id));
    }

    /**
     * Releases the android tts resources. You should always call this method
     * when you are finished with the service.
     *
     * @param message
     *            The message to be spoken on shutdown if any.
     */
    public void shutdown(String message) {
        if (message != null && tts != null) {
            // dispose() follows from the utterance's onDone callback.
            ttsSpeak(message, QUEUE_FLUSH, null, SHUTDOWN_ID);
        } else {
            dispose();
        }
    }

    // Release both the live engine and any replacement still starting up.
    // This instance speaks no more afterwards.
    private void dispose() {
        disposed = true;
        pending.clear();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (starting != null) {
            starting.shutdown();
            starting = null;
        }
    }

    /**
     * Speak a string of text using the specific queuing mode.
     * 
     * @param context
     *            The application context.
     * @param text
     *            The text to be spoken.
     * @param mode
     *            The queuing mode to be used see QUEUE_ADD and QUEUE_FLUSH.
     */
    public void speak(Context context, CharSequence text, int mode) {
        speak(context, "%s", text, mode);
    }

    /**
     * Speak a string of text using a format string and the specific queuing
     * mode.
     * 
     * @param context
     *            The application context.
     * @param format
     *            The format string to be used to speak text.
     * @param text
     *            The text to be spoken.
     * @param mode
     *            The queuing mode to be used see QUEUE_ADD and QUEUE_FLUSH.
     */
    public void speak(Context context, String format, CharSequence text,
            int mode) {
        if (text != null) {
            if (text.equals(" ")) {
                // say "space
                text = context.getString(R.string.space);
            } else if (text.length() < 2 && text.length() > 0
                    && Character.isUpperCase(text.charAt(0))) {
                // announce capitalisation
                text = String.format(context.getString(R.string.capital), text);
            } else if (text.equals("\n")) {
                // newline
                text = context.getString(R.string.newline);
            } else if (text.toString().trim().equals("")) {
                // say "blank"
                text = context.getString(R.string.blank);
            }

            String textToSpeak = String.format(format,
                    extractPunctuation(text.toString()));
            // Speak the text ensuring that we don't overflow the buffer.
            divideAndSpeak(textToSpeak, mode, null);
        }
    }

    /**
     * Speak a password as a series of asterisks.
     * 
     * @param context
     *            The application context.
     * @param text
     *            The password to be spoken as asterisks.
     */
    public void speakPassword(Context context, String text) {
        speakPassword(context, "%s", text, QUEUE_FLUSH);
    }

    /**
     * Speak a password as asterisks with the given queuing strategy.
     * 
     * @param context
     *            The application context.
     * @param formatter
     *            Formatter string to speak the text of the password.
     * @param text
     *            The text of the password to be spoken as asterisks.
     * @param mode
     *            The queuing mode see QUEUE_FLUSH and QUEUE_ADD.
     */
    public void speakPassword(Context context, String formatter, String text,
            int mode) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append('*');
        }
        speak(context, String.format(formatter, sb.toString()), mode);
    }

    /**
     * Given the current state of application preferences decide how a password
     * should be spoken either read normally or said as asterisks.
     * 
     * @param context
     *            The application context.
     * @param format
     *            The formatter string to speak the text.
     * @param text
     *            The text to be spoken normally or as a password.
     * @param isPasswordField
     *            true if this text field is of type password.
     * @param mode
     *            The queuing mode to use for this utterance see QUEUE_ADD and
     *            QUEUE_FLUSH.
     */
    public void readConsiderPassword(Context context, String format,
            String text, boolean isPasswordField, int mode) {
        if (!isPasswordField
                || Options.getBooleanPreference(context,
                        R.string.pref_echo_passwords_key, false)) {
            speak(context, format, text, mode);
        } else {
            speakPassword(context, format, text, mode);
        }
    }

    /**
     * Set the locale of the tts engine.
     * 
     * @param locale
     *            The locale to set the engine to.
     * @return true if the engine could be set to this locale false if the
     *         engine does not support the locale.
     */
    public boolean setLocale(Locale locale) {
        if (tts != null
                && tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(locale);
            // Remembered so a replacement engine starts in the same language
            // instead of falling back to the system default.
            this.locale = locale;
            return true;
        }
        return false;
    }

    public void stop() {
        pending.clear();
        if (tts != null) {
            tts.stop();
        }
    }

    // Divide a long utterance into segments before passing it to the Android
    // tts service for speaking.
    private void divideAndSpeak(final String text, final int queueMode,
            final HashMap<String, String> params) {
        int end = MAX_SPEECH_LENGTH < text.length() ? MAX_SPEECH_LENGTH : text
                .length();
        end = getBestEnd(text, end);

        ttsSpeak(text.substring(0, end), queueMode, params, null);

        for (int i = end; i < text.length(); i += MAX_SPEECH_LENGTH) {
            end = (i + MAX_SPEECH_LENGTH) < text.length() ? (i + MAX_SPEECH_LENGTH)
                    : text.length();
            end = getBestEnd(text, end);

            ttsSpeak(text.substring(i, end), QUEUE_ADD, null, null);
        }
    }

    private void ttsSpeak(String text, int queueMode,
            HashMap<String, String> params, String id) {
        if (disposed) {
            return;
        }
        if (tts == null) {
            // No engine yet: hold on to the utterance instead of dropping it,
            // otherwise everything said in the moment before the engine
            // finishes starting up - the keyboard's own "ready" announcement
            // included - is lost silently.
            enqueue(text, queueMode, params, id);
            return;
        }

        if (id == null) {
            id = String.valueOf(System.currentTimeMillis());
        }

        Bundle bundle = new Bundle();
        if (params != null) {
            for (String key : params.keySet()) {
                bundle.putString(key, params.get(key));
            }
        }

        tts.speak(text, queueMode, bundle, id);
    }

    // Find the best endpoint to speak until.
    // This is either the current endpoint if it is the actual end of the text.
    // Otherwise we back track until a white space separator so that the
    // segments of speech sound clean.
    private static int getBestEnd(String text, int end) {
        // separators to divide segments at eg. whitespace so it sounds clean.
        String[] items = { " ", "\n" };
        int bestEnd = end;
        if (text.length() != end) {
            bestEnd = -1;

            // the endpoint isn't actually the end of the text String.
            // back track and pick the closest separator index.
            for (int i = 0; i < items.length; i++) {
                // store the temporary segment separator index.
                int temp = text.substring(0, end).lastIndexOf(items[i]);
                // pick the longest segment.
                if (temp > bestEnd) {
                    bestEnd = temp;
                }
            }
        }
        return bestEnd < end && bestEnd > 0 ? bestEnd : end;
    }

    // If the string is just one character make sure we speak the actual
    // punctuation symbol for it. Otherwise it can be spoken natively.
    private String extractPunctuation(String text) {
        String symbol = null;
        if (text.length() == 1) {
            symbol = speechMap.get(text.substring(0, 1));
        }
        return symbol == null ? text : symbol;
    }

    // Many symbols are not spoken by tts properly.
    // Load the translations into a map to be used at speaking time from
    // strings.xml.
    private static void setSpeechMap(Context context,
            Map<String, String> punctuationSpokenEquivalentsMap) {
        // Symbols that most TTS engines can't speak
        punctuationSpokenEquivalentsMap.put("?",
                context.getString(R.string.punctuation_questionmark));
        punctuationSpokenEquivalentsMap.put(" ",
                context.getString(R.string.punctuation_space));
        punctuationSpokenEquivalentsMap.put("\n",
                context.getString(R.string.newline));
        punctuationSpokenEquivalentsMap.put(",",
                context.getString(R.string.punctuation_comma));
        punctuationSpokenEquivalentsMap.put(".",
                context.getString(R.string.punctuation_dot));
        punctuationSpokenEquivalentsMap.put("!",
                context.getString(R.string.punctuation_exclamation));
        punctuationSpokenEquivalentsMap.put("(",
                context.getString(R.string.punctuation_open_paren));
        punctuationSpokenEquivalentsMap.put(")",
                context.getString(R.string.punctuation_close_paren));
        punctuationSpokenEquivalentsMap.put("\"",
                context.getString(R.string.punctuation_double_quote));
        punctuationSpokenEquivalentsMap.put("\'",
                context.getString(R.string.punctuation_single_quote));
        punctuationSpokenEquivalentsMap.put("/",
                context.getString(R.string.punctuation_slash));
        punctuationSpokenEquivalentsMap.put("\\",
                context.getString(R.string.punctuation_backslash));
        punctuationSpokenEquivalentsMap.put(";",
                context.getString(R.string.punctuation_semicolon));
        punctuationSpokenEquivalentsMap.put(":",
                context.getString(R.string.punctuation_colon));
        punctuationSpokenEquivalentsMap.put("{",
                context.getString(R.string.punctuation_left_brace));
        punctuationSpokenEquivalentsMap.put("}",
                context.getString(R.string.punctuation_right_brace));
    }
}
