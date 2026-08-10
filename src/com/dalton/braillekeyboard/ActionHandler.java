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
import android.inputmethodservice.Keyboard;
import androidx.core.content.ContextCompat;
import android.view.inputmethod.InputMethodManager;

import com.dalton.braillekeyboard.EditingController.Granularity;
import com.dalton.braillekeyboard.Options.KeyboardEcho;

/**
 * ActionHandler maps gestures from a View to the higher level actions of the
 * keyboard: text editing, voice input, settings and feedback toggles.
 *
 * <p>Editing operations are delegated to {@link EditingController}; this
 * class decides which operation a swipe or typed character maps to and
 * delivers the results through the {@link OnActionListener} callback.
 *
 * <p>A View should instantiate this class once upon initialisation and call
 * its handleSwipe or handleCharacter methods to perform actions. Before such
 * activity the view shall set the IME listener by calling
 * setKeyboardListener(KeyboardListener listener) and also set the callback by
 * calling setCallback(OnActionListener callback). You should always call the
 * shutdown() method when you are done.
 */
public class ActionHandler {
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
     * Swipe type. This method will perform the appropriate action for the
     * received Swipe gesture. If need be the appropriate callbacks will be
     * invoked.
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

        value = normaliseSwipe(value);
        String message = null;
        FeedbackEvent feedbackEvent = FeedbackEvent.TYPE;
        // Every handled gesture emits the generic TYPE feedback event unless it
        // sets a more specific one (DELETE, NEW_LINE, COMMAND) below; NONE
        // returns early and the default case suppresses the event entirely.
        boolean notify = true;
        boolean setDots = false;
        boolean considerPassword = false;
        // states for dots 7 and 8
        boolean dots[] = { false, false };
        boolean fastDoubleSwipe = fastDoubleSwipe(value, DOUBLE_TOUCH_THRESHOLD);

        switch (value) {
        case ONE_LEFT:
            editingController.moveLeft(context, Granularity.CHARACTER);
            break;
        case ONE_RIGHT:
            editingController.moveRight(context, Granularity.CHARACTER);
            break;
        case ONE_DOWN:
            boolean soundOn = Options.getBooleanPreference(context,
                    R.string.pref_sound_feedback_key, true);
            boolean hapticOn = Options.getBooleanPreference(context,
                    R.string.pref_haptic_feedback_key, true);
            if (soundOn && hapticOn) {
                // Both on -> turn both off
                Options.writeBooleanPreference(context,
                        R.string.pref_sound_feedback_key, false);
                Options.writeBooleanPreference(context,
                        R.string.pref_haptic_feedback_key, false);
                message = context.getString(R.string.keyboard_feedback_none);
            } else if (!soundOn && !hapticOn) {
                // Both off -> vibrate only
                Options.writeBooleanPreference(context,
                        R.string.pref_haptic_feedback_key, true);
                message = context.getString(R.string.keyboard_feedback_vibrate);
            } else if (hapticOn) {
                // Vibrate only -> sound only
                Options.writeBooleanPreference(context,
                        R.string.pref_haptic_feedback_key, false);
                Options.writeBooleanPreference(context,
                        R.string.pref_sound_feedback_key, true);
                message = context.getString(R.string.keyboard_feedback_sound);
            } else {
                // Sound only -> both on
                Options.writeBooleanPreference(context,
                        R.string.pref_haptic_feedback_key, true);
                message = context.getString(R.string.keyboard_feedback_all);
            }
            feedbackEvent = FeedbackEvent.COMMAND;
            callback.onFeedbackSettingsChanged();
            break;
        case ONE_UP:
            message = editingController.getInput(Granularity.CHARACTER);
            considerPassword = true;
            break;
        case TWO_LEFT:
            editingController.moveLeft(context, Granularity.WORD);
            break;
        case TWO_RIGHT:
            editingController.moveRight(context, Granularity.WORD);
            break;
        case TWO_UP:
            message = editingController.getInput(Granularity.WORD);
            considerPassword = true;
            break;
        case TWO_DOWN:
            KeyboardEcho echo = KeyboardEcho.valueOf(Integer.parseInt(Options
                    .getStringPreference(context,
                            R.string.pref_echo_feedback_key,
                            KeyboardEcho.CHARACTER.getValue())));
            echo = KeyboardEcho.next(echo);
            Options.writeStringPreference(context,
                    R.string.pref_echo_feedback_key, echo.getValue());
            message = context.getString(echo.resource);
            feedbackEvent = FeedbackEvent.COMMAND;
            break;
        case TWO_FINGERS_DOWN: // switch to the next Braille table
            feedbackEvent = FeedbackEvent.COMMAND;
            message = listener.switchTable();
            message = message == null ? context
                    .getString(R.string.no_braille_table) : message;
            callback.onSetLocale(listener.getLocale());
            break;
        case DOTS_FOUR_SIX_RIGHT:
            // The "closing keyboard" announcement is owned by View.close() so
            // it honours the "Keyboard closed" speech event.
            listener.closeKeyboard();
            break;
        case THREE_LEFT:
            editingController.moveLeft(context, Granularity.LINE);
            break;
        case THREE_RIGHT:
            editingController.moveRight(context, Granularity.LINE);
            break;
        case THREE_UP:
            message = editingController.getInput(Granularity.LINE);
            considerPassword = true;
            break;
        case THREE_DOWN:
            if (listener.getDots() == 8) {
                setDots = true;
                dots[0] = true;
            } else {
                message = context.getString(R.string.unknown_character);
            }
            break;
        case THREE_FINGERS_LEFT:
            feedbackEvent = FeedbackEvent.COMMAND;
            listener.toggleEmojiMode();
            break;
        case THREE_FINGERS_DOWN: // submit the text / perform editor action
            if (!listener.submitText()) {
                // Submit fell back to inserting a newline.
                feedbackEvent = FeedbackEvent.NEW_LINE;
                message = context.getString(R.string.newline);
            }
            break;
        case FOUR_LEFT:
            feedbackEvent = FeedbackEvent.DELETE;
            editingController.backspace(context, Granularity.CHARACTER,
                    fastDoubleSwipe);
            break;
        case FOUR_RIGHT:
            if (fastDoubleSwipe) {
                if (editingController.handleDoubleSpace(context)) {
                    break;
                }
            }
            editingController.typeCharacter(context, (int) ' ', " ");
            break;
        case FOUR_DOWN: // insert newline explicitly
            feedbackEvent = FeedbackEvent.NEW_LINE;
            editingController.typeCharacter(context, Keyboard.KEYCODE_DONE,
                    context.getString(R.string.newline));
            break;
        case FOUR_UP:
            Options.switchBooleanPreference(context, R.string.pref_privacy_key,
                    Boolean.parseBoolean(context
                            .getString(R.string.pref_privacy_default)));
            callback.onPrivacy();
            message = Options.getBooleanPreference(context,
                    R.string.pref_privacy_key, Boolean.parseBoolean(context
                            .getString(R.string.pref_privacy_default))) ? context
                    .getString(R.string.privacy_enabled) : context
                    .getString(R.string.privacy_disabled);
            feedbackEvent = FeedbackEvent.COMMAND;
            break;
        case FIVE_LEFT:
            feedbackEvent = FeedbackEvent.DELETE;
            editingController.backspace(context, Granularity.WORD,
                    fastDoubleSwipe);
            break;
        case FIVE_DOWN:
            feedbackEvent = FeedbackEvent.COMMAND;
            if (fastDoubleSwipe) {
                message = context.getString(R.string.show_input_switcher);
                inputManager.showInputMethodPicker();
            } else {
                message = context.getString(R.string.swipe_confirm_input);
            }
            break;
        case FIVE_UP:
            feedbackEvent = FeedbackEvent.COMMAND;
            if (fastDoubleSwipe) {
                callback.onSetLocale(Locale.getDefault());
                message = context.getString(R.string.show_settings);
                Intent intent = new Intent(context, PreferenceIME.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                message = context.getString(R.string.swipe_confirm_settings);
            }
            break;
        case SIX_LEFT:
            feedbackEvent = FeedbackEvent.DELETE;
            editingController.backspace(context, Granularity.LINE,
                    fastDoubleSwipe);
            break;
        case SIX_RIGHT:
            editingController.nextAction(context);
            break;
        case SIX_UP:
            editingController.selectAction(context);
            break;
        case SIX_DOWN:
            if (listener.getDots() == 8) {
                setDots = true;
                dots[1] = true;
            } else {
                message = context.getString(R.string.unknown_character);
            }
            break;
        case HOLD_SIX_LEFT:
            feedbackEvent = FeedbackEvent.DELETE;
            editingController.moveLeft(context, Granularity.ALL);
            break;
        case HOLD_SIX_RIGHT:
            editingController.moveRight(context, Granularity.ALL);
            break;
        case HOLD_SIX_DOWN:
            feedbackEvent = FeedbackEvent.COMMAND;
            boolean echoPassword = Options.switchBooleanPreference(context,
                    R.string.pref_echo_passwords_key, false);
            message = echoPassword ? context
                    .getString(R.string.speak_passwords) : context
                    .getString(R.string.no_password_echo);
            break;
        case HOLD_SIX_UP:
            message = editingController.getInput(Granularity.ALL);
            considerPassword = true;
            break;
        case HOLD_THREE_LEFT:
            feedbackEvent = FeedbackEvent.DELETE;
            editingController.backspace(context, Granularity.ALL,
                    fastDoubleSwipe);
            break;
        case HOLD_THREE_RIGHT:
            feedbackEvent = FeedbackEvent.COMMAND;
            int brailleType = listener.switchBrailleType();
            message = brailleType == 8 ? context
                    .getString(R.string.grade_computer) : context
                    .getString(R.string.grade_literary);
            callback.onSetLocale(listener.getLocale());
            break;
        case HOLD_THREE_DOWN:
            feedbackEvent = FeedbackEvent.COMMAND;
            message = listener.switchTable();
            message = message == null ? context
                    .getString(R.string.no_braille_table) : message;
            callback.onSetLocale(listener.getLocale());
            break;
        case HOLD_THREE_UP:
            doVoiceInput(context, fastDoubleSwipe);
            break;
        case HOLD_ONE_RIGHT:
            message = context
                    .getString(listener.toggleMark() ? R.string.set_mark
                            : R.string.unset_mark);
            break;
        case HOLD_ONE_LEFT:
            feedbackEvent = FeedbackEvent.COMMAND;
            message = context.getString(R.string.keyboard_shrink);
            callback.onShrink();
            break;
        case HOLD_ONE_DOWN:
            CharSequence text = listener.getAllText().text;
            if (text != null) {
                message = String.format(context.getString(R.string.word_count),
                        EditingUtilities.lineCount(text),
                        EditingUtilities.wordCount(text),
                        EditingUtilities.characterCount(text));
            }
            break;
        case HOLD_ONE_UP:
            Options.switchBooleanPreference(context,
                    R.string.pref_auto_caps_key, Boolean.parseBoolean(context
                            .getString(R.string.pref_auto_caps_default)));
            message = Options.getBooleanPreference(context,
                    R.string.pref_auto_caps_key, Boolean.parseBoolean(context
                            .getString(R.string.pref_auto_caps_default))) ? context
                    .getString(R.string.auto_caps_enabled) : context
                    .getString(R.string.auto_caps_disabled);
            feedbackEvent = FeedbackEvent.COMMAND;
            break;
        case HOLD_FOUR_LEFT:
            editingController.doSpellCheck(context, SpellChecker.Direction.LEFT,
                    0, listener.getCursor());
            break;
        case HOLD_FOUR_RIGHT:
            editingController.doSpellCheck(context,
                    SpellChecker.Direction.RIGHT, 0, listener.getCursor());
            break;
        case HOLD_FOUR_DOWN:
            editingController.nextSpellCheckSuggestion(context);
            break;
        case HOLD_FOUR_UP:
            editingController.previousSpellCheckSuggestion(context);
            break;
        case NONE:
            return false;
        default:
            notify = false;
        }

        // Invoke the notification callback
        if (notify) {
            callback.onNotify(feedbackEvent);
        }
        if (message != null) {
            // Only invoke onText callback if there is a message to send i.e. it
            // wasn't already handled.
            callback.onText("%s", message,
                    considerPassword ? listener.isPasswordField() : false);
        }

        if (setDots) { // Dots 7 or 8 were triggered
            callback.onSetDots(dots[0], dots[1]);
        }

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

    // Some swipe actions should resolve to the same thing eg. dots 4 and 5
    // swipe right.
    private static Swipe normaliseSwipe(Swipe swipe) {
        if (swipe == Swipe.FIVE_RIGHT) {
            return Swipe.FOUR_RIGHT;
        }
        return swipe;
    }

}
