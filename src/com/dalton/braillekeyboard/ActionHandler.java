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

import java.util.Locale;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.view.inputmethod.InputMethodManager;

/**
 * ActionHandler maps gestures from a View to the higher level actions of the
 * keyboard: text editing, voice input, settings and feedback toggles.
 *
 * <p>Which action a gesture performs is decided by the gesture itself:
 * every {@link Swipe} knows the {@link KeyboardAction} bound to it (either
 * the user's choice from the "Customize gestures" settings screen or its
 * default), and each action knows how to perform itself through the
 * {@link ActionContext} this class implements. Editing operations are
 * delegated to {@link EditingController}; this class only wires the gesture
 * to its action and delivers the results through the
 * {@link OnActionListener} callback.
 *
 * <p>A View should instantiate this class once upon initialisation and call
 * its handleSwipe or handleCharacter methods to perform actions. Before such
 * activity the view shall set the IME listener by calling
 * setKeyboardListener(KeyboardListener listener) and also set the callback by
 * calling setCallback(OnActionListener callback). You should always call the
 * shutdown() method when you are done.
 */
public class ActionHandler implements ActionContext {
    // The maximum time between two identical swipe patterns which constitutes a
    // double swipe.
    private static final long DOUBLE_TOUCH_THRESHOLD = 1300;

    /**
     * Listener for handling the results of requests to the input methods. You
     * should implement these callbacks in your View and display the results to
     * the user.
     */
    public interface OnActionListener {
        /**
         * Deliver a string of text to the view as output of a certain action
         * that was performed. This might be some sort of message, a key name to
         * echo or some other text. Your UI should communicate this to the user
         * somehow in the form of audible or visual representation whatever is
         * appropriate for the use case.
         *
         * @param format
         *            Format string to be used to display the message.
         * @param text
         *            Any text of the message.
         * @param isPasswordField
         *            True if it should be displayed with the same rules of
         *            showing passwords.
         */
        void onText(String format, String text, boolean isPasswordField);

        void onText(String format, String text, boolean isPasswordField,
                int mode);

        /**
         * Called when a notification should be delivered to the user.
         */
        void onNotify(FeedbackEvent event);

        void onFeedbackSettingsChanged();

        /**
         * Called when dots 7 and 8 should be set in the View.
         *
         * @param dot7
         *            Whether dot7 is pressed.
         * @param dot8
         *            Whether dot8 is pressed.
         */
        void onSetDots(boolean dot7, boolean dot8);

        /**
         * Called when the View should update it's Locale. This is generally
         * called when the Braille table is changed because there is the
         * possibility for language change.
         */
        void onSetLocale(Locale locale);

        /**
         * Called when the View should shrink itself.
         */
        void onShrink();

        /**
         * Called when the View should update the state of it's privacy mode.
         */
        void onPrivacy();

        void onShutup();
    }

    private final InputMethodManager inputManager;
    private final VoiceInput voiceInput = new VoiceInput();
    private final EditingController editingController;
    private final Context context;

    private KeyboardListener listener;
    private OnActionListener callback;
    private long lastTouchTime = 0; // Time screen was last touched.
    private Swipe lastSwipe = Swipe.NONE; // Type of last gesture.

    /**
     * Create a new ActionHandler for the given context.
     *
     * @param context
     *            The application context.
     */
    public ActionHandler(Context context) {
        this.context = context;
        inputManager = (InputMethodManager) context
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        editingController = new EditingController(context, null);
    }

    /**
     * Set the callback to deliver results to the View interracting with this
     * instance.
     *
     * @param callback
     *            The View's implementation of ActionHandler.OnActionListener
     *            listening for updates.
     */
    public void setCallback(OnActionListener callback) {
        this.callback = callback;
        editingController.setCallback(callback);
    }

    /**
     * Set the listener so that the ActionHandler can communicate with the
     * underlying IME.
     *
     * @param listener
     *            The KeyboardListener for the current input session.
     */
    public void setKeyboardListener(KeyboardListener listener) {
        this.listener = listener;
        editingController.setListener(listener);
    }

    /**
     * Releases system resources. This should be called just before the
     * interracting View gets destroyed.
     */
    public void shutdown() {
        voiceInput.destroy();
        editingController.destroy();
    }

    /**
     * Handle swipe actions delivered from the interracting View.
     *
     * Each View should deliver a swipe action as defined by the generic
     * Swipe type. This method looks up the {@link KeyboardAction} bound to
     * the gesture and performs it, delivering any results through the
     * callbacks. If the gesture is disabled (bound to {@code NONE}) or not a
     * real gesture, nothing happens and false is returned.
     *
     * @param context
     *            The application context.
     * @param value
     *            The Swipe value from the View.
     * @return true if the Swipe was handled otherwise false.
     */
    public boolean handleSwipe(Context context, Swipe value) {
        // Disable all swipes while voice input is in progress.
        if (voiceInput.isListening()) {
            return true;
        }

        boolean fastDoubleSwipe = fastDoubleSwipe(value, DOUBLE_TOUCH_THRESHOLD);
        KeyboardAction action = value.getBoundAction(context);
        if (action == null || action == KeyboardAction.NONE) {
            return false;
        }

        action.perform(this, fastDoubleSwipe);
        lastSwipe = value; // update the last swipe
        return true;
    }

    /**
     * Handle typing a Braille character into the underlying IME. The character
     * is delivered as a byte value representing the dot pattern and will be
     * converted and written as a standard textual character by the IME.
     *
     * @param context
     *            The application context.
     * @param value
     *            The byte value which represents the dot pattern to type. This
     *            is a bitstring that represents a Braille pattern where dot 8
     *            is represented by the MSB and dot 1 by the LSB. A value of 0
     *            means no dots are present and a value of 0b11111111 means all
     *            8 dots are pressed.
     */
    public void handleCharacter(Context context, byte value) {
        // Can't type while voice input is in progress.
        if (voiceInput.isListening()) {
            return;
        }

        if (listener.isEmojiMode()) {
            listener.handleTypedCharacter(value);
            return;
        }

        lastSwipe = Swipe.NONE;
        String result;
        if ((result = listener.handleTypedCharacter(value)) == null) {
            // IME couldn't handle the dot pattern propergate the error to the
            // callback.
            callback.onText("%s",
                    context.getString(R.string.unknown_character), false);
        } else {
            callback.onNotify(FeedbackEvent.TYPE);

            // Decide what to deliver to the callback such as a key echo or
            // autocompletion string.
            String character = EditingController.echoCharacter(context, result);
            result = character == null ? "" : character;
            if (!(result = result.trim()).equals("")) {
                callback.onText("%s", result.toString(),
                        listener.isPasswordField());
            }
        }

        // dots 7 and 8 should now be unset
        callback.onSetDots(false, false);
    }

    // Handle voice input.
    public boolean doVoiceInput(final Context context, boolean fastDoubleSwipe) {
        // Check for the "dangerous permission" for Android 6 and higher.
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Fast double swipe to show the permission dialog so we don't
            // surprise the user having no screen reader or talking keyboard in
            // focus.
            if (!fastDoubleSwipe) {
                callback.onText("%s",
                        context.getString(R.string.voice_input_enable), false);
            } else { // Show permission dialog
                Intent intent = new Intent(context, IntentActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setAction(context
                        .getString(R.string.action_record_audio_permission));
                intent.putExtra(
                        context.getString(R.string.require_record_audio_now),
                        true);
                context.startActivity(intent);
            }
            return false; // We didn't do voice input.
        }

        VoiceInput.TextReadyListener textReadyListener = new VoiceInput.TextReadyListener() {

            @Override
            public void onTextReady(String text) {
                // Write the text and send it back to the callback.
                if (text != null && text.length() > 0) {
                    CharSequence before = listener.getTextBeforeCursor(1);
                    if (before != null && before.length() > 0
                            && !Character.isWhitespace(before.charAt(0))) {
                        listener.onKey(' ');
                    }
                    listener.commitText(text, 1);
                    callback.onText("%s", text, listener.isPasswordField());
                }
            }

            @Override
            public void onError(int error) {
                callback.onText("%s", String.format(
                        context.getString(R.string.voice_input_error), error),
                        false);
            }
        };

        if (voiceInput.start(context, textReadyListener)) {
            callback.onShutup();
        } else {
            callback.onText("%s",
                    context.getString(R.string.voice_input_is_not_available),
                    false);
            return false;
        }
        return true;
    }

    // Return true if the same gesture was typed quickly in succession.
    private boolean fastDoubleSwipe(Swipe swipe, long threshold) {
        if ((lastTouchTime + threshold) > System.currentTimeMillis()
                && lastSwipe == swipe) {
            lastTouchTime = 0;
            return true;
        } else {
            lastSwipe = swipe;
            lastTouchTime = System.currentTimeMillis();
            return false;
        }
    }

    // ActionContext ---------------------------------------------------------

    @Override
    public Context context() {
        return context;
    }

    @Override
    public EditingController editing() {
        return editingController;
    }

    @Override
    public KeyboardListener listener() {
        return listener;
    }

    @Override
    public void speak(String message, boolean considerPassword) {
        if (message == null) {
            return;
        }
        callback.onText("%s", message,
                considerPassword ? listener.isPasswordField() : false);
    }

    @Override
    public void speak(int stringRes) {
        callback.onText("%s", context.getString(stringRes), false);
    }

    @Override
    public void notify(FeedbackEvent event) {
        callback.onNotify(event);
    }

    @Override
    public void setDots(boolean dot7, boolean dot8) {
        callback.onSetDots(dot7, dot8);
    }

    @Override
    public void feedbackSettingsChanged() {
        callback.onFeedbackSettingsChanged();
    }

    @Override
    public void setLocale(Locale locale) {
        callback.onSetLocale(locale);
    }

    @Override
    public void shrinkKeyboard() {
        callback.onShrink();
    }

    @Override
    public void privacyChanged() {
        callback.onPrivacy();
    }

    @Override
    public void showInputMethodPicker() {
        inputManager.showInputMethodPicker();
    }

    @Override
    public void openSettings() {
        Intent intent = new Intent(context, PreferenceIME.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    public void voiceInput(boolean fastDoubleSwipe) {
        doVoiceInput(context, fastDoubleSwipe);
    }
}
