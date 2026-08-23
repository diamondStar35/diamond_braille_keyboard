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
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import androidx.core.content.ContextCompat;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import com.googlecode.eyesfree.braille.translate.TableInfo;

/**
 * Implementation of an Input method service for Android.
 * 
 * Specifically, this IME service implements the capabilities to support Braille
 * input from a View and several editing capabilities.
 * 
 * You should not instantiate this class directly rather it will create it's own
 * View with the onCreateView method and set this service in that View to
 * facilitate communication between the View and the IME. You should communicate
 * according to the KeyboardListener interface and consult that for further
 * documentation.
 * 
 */
public class BrailleIME extends InputMethodService implements KeyboardListener,
        TextComposer.Host {
    private boolean emojiMode = false;
    private EmojiEngine emojiEngine;
    private boolean commandMode = false;
    private CommandModeEngine commandModeEngine;

    private Parser brailleParser;
    private BrailleKeyboardView brailleView = null;
    private final TextComposer textComposer = new TextComposer(this);
    /**
     * The single access point to the editor: tracks the cursor through
     * {@link #onUpdateSelection} and through every write issued via
     * {@link #getCurrentInputConnection}, saving repeated full-document
     * round-trips on the editing hot path.
     */
    private final EditorGateway editor = new EditorGateway();
    private int cursor = -1;
    private int mark = -1;
    private boolean selectAll = false;

    // TextComposer.Host --------------------------------------------------
    // getCurrentInputConnection() and getCurrentInputEditorInfo() are
    // provided by InputMethodService and satisfy the interface directly.

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void clearSelectAll() {
        mark = -1;
        selectAll = false;
    }

    // InputMethodService lifecycle ---------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        // The emoji and command engines share a single Speech instance: Speech
        // keeps a single static TTS engine, and each new Speech constructor
        // replaces it with a freshly-initialising engine. Constructing two
        // Speech instances back-to-back here orphaned the first one's engine
        // before its asynchronous initialisation finished, permanently
        // silencing it (the emoji engine). View creates its own Speech later,
        // when the keyboard is first shown.
        Speech speech = new Speech(this, new Speech.OnReadyListener() {
            @Override
            public void ttsReady() {
            }
        });
        emojiEngine = new EmojiEngine(this, this, speech);
        commandModeEngine = new CommandModeEngine(this, this, speech);
        if (brailleParser == null) {
            brailleParser = new Parser(this,
                    new Parser.Listener() {

                        @Override
                        public void onTranslatorReady(int status) {
                            brailleParserReady(status);
                        }
                    });
        }
    }

    @Override
    // Fully qualified: the app's own keyboard view class is also called View
    // in other projects; here it is BrailleKeyboardView, so no shadowing.
    public android.view.View onCreateInputView() {
        super.onCreateInputView();
        // If a previous keyboard view is still hosted in the full-screen
        // overlay, drop that stale window before creating the new view.
        KeyboardOverlayHost.removeOverlay();
        // A replaced keyboard view is never used again; release its sound
        // resources so discarded views cannot leak SoundPools or listeners.
        if (brailleView != null) {
            brailleView.releaseResources();
        }
        brailleView = (BrailleKeyboardView) getLayoutInflater().inflate(
                R.layout.keyboard, null);

        if (!Options.getBooleanPreference(this,
                R.string.pref_has_asked_record_audio_key, false)) {
            Options.switchBooleanPreference(this,
                    R.string.pref_has_asked_record_audio_key, false);
            // Android 6+ show a permission dialog for record audio dangerous
            // permission.
            // Only do this once on the very first run though.
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(this, IntentActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setAction(getString(R.string.action_record_audio_permission));
                startActivity(intent);
            }
        }
        return brailleView;
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        // The editor state of the previous session says nothing about this
        // one; tracking resumes from the framework's next selection update.
        editor.invalidate();
        // remove any existing selection.
        selectAll = false;
        mark = -1;
        commandMode = false;
        if (commandModeEngine != null) {
            commandModeEngine.reset();
        }

        // Disable prediction (composing text) by default.  Our manual
        // differential update logic in compose() is much more robust and
        // prevents duplication bugs in apps that don't handle composing spans
        // correctly (e.g. Chrome address bar, Star Taxi).
        textComposer.setPredictionOn(false);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd, int candidatesStart,
            int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        editor.onSelectionChanged(newSelStart, newSelEnd);
    }

    @Override
    public InputConnection getCurrentInputConnection() {
        return editor.track(super.getCurrentInputConnection());
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        // While the keyboard window is shown, pass the whole screen through to
        // the keyboard so it receives raw touches and gestures even with
        // TalkBack turned on (see AccessibilityService).
        AccessibilityService.setKeyboardPassthrough(true);
        if (!restarting) {
            // The first show for this input field is the most useful place to
            // record the device and keyboard state for problem reports.
            Diagnostics.log(this, "keyboard shown, app="
                    + (info != null && info.packageName != null
                            ? info.packageName : "unknown")
                    + " inputType=0x" + Integer.toHexString(
                            info != null ? info.inputType : 0));
            Diagnostics.logDeviceInfo(this);
        }
        if (!restarting && brailleView != null) {
            // Tell the user the keyboard is ready, but only the first time it
            // starts for this input field, not restarts. That'll be annoying.
            brailleView.onInitialiseForInput(this, this);
            brailleView.emitFeedbackEvent(FeedbackEvent.OPEN);
        }
        // If the user configured a specific default Braille table, activate it
        // whenever the keyboard opens.  "Use the last language" leaves the
        // table untouched.
        if (!restarting) {
            brailleParser.applyStartupTable(this);
        }
        brailleParser.setTranslator(this);
        // Full-screen mode hosts the keyboard in a topmost accessibility
        // overlay covering the whole screen (system bars included), so a
        // notification cannot close it while typing. Otherwise make sure the
        // keyboard lives in the IME window's input frame. Placement is
        // idempotent and only runs on genuine shows (never on mere input
        // restarts, which browsers trigger constantly).
        if (!restarting) {
            applyPlacement();
        }
    }

    /**
     * Puts the keyboard view in the window its configuration calls for: the
     * topmost accessibility overlay when Full-screen mode is on (the IME
     * window keeps running underneath it), otherwise the IME window's own
     * input frame.
     *
     * <p>Only explicit events may call this - a genuine show, a shrink or
     * expand gesture, or opening the keyboard. InputMethodService's internal
     * lifecycle hooks must never reach placement, otherwise they could
     * resurrect the overlay while the framework is processing a close.
     */
    private void applyPlacement() {
        if (brailleView == null) {
            return;
        }
        boolean wasOverlay = KeyboardOverlayHost.isInOverlay(brailleView);
        if (isFullscreenPreferred()) {
            if (!KeyboardOverlayHost.showOverlay(brailleView)) {
                // The accessibility service cannot host windows right now;
                // keep the keyboard usable inside the IME window.
                KeyboardOverlayHost.attachToInputFrame(brailleView,
                        imeInputFrame());
            }
        } else {
            KeyboardOverlayHost.removeOverlay();
            KeyboardOverlayHost.attachToInputFrame(brailleView,
                    imeInputFrame());
        }
        boolean isOverlay = KeyboardOverlayHost.isInOverlay(brailleView);
        if (wasOverlay != isOverlay) {
            Diagnostics.log(this, "full-screen overlay "
                    + (isOverlay ? "shown" : "removed"));
        }
    }

    /** The frame of the IME window that hosts the keyboard view. */
    private ViewGroup imeInputFrame() {
        Window window = getWindow().getWindow();
        if (window == null) {
            return null;
        }
        return (ViewGroup) window.getDecorView().findViewById(
                android.R.id.inputArea);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        Diagnostics.log(this, "keyboard hidden");
        // The keyboard window is gone, so TalkBack can handle the screen
        // normally again.
        AccessibilityService.setKeyboardPassthrough(false);
        emojiMode = false;
        commandMode = false;
        if (commandModeEngine != null) {
            commandModeEngine.reset();
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            textComposer.finishComposingText(false);
        }

        if (brailleView != null) {
            brailleView.close();
        }
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        // The keyboard window is really gone now (unlike onFinishInputView,
        // which also runs on mere input restarts), so take the full-screen
        // overlay down with it.
        KeyboardOverlayHost.removeOverlay();
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        // A genuine show of the keyboard window; make sure the keyboard view
        // lives where the current configuration wants it.
        applyPlacement();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Make sure the screen is never left in passthrough mode.
        AccessibilityService.setKeyboardPassthrough(false);
        // Nor under a stale full-screen keyboard overlay.
        KeyboardOverlayHost.removeOverlay();
        if (brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // The view dictates whether we are using the full screen.
        // If the keyboard is being used it will always take up the whole
        // screen.
        // If the keyboard is in the shrink state it will not use the full
        // screen.
        return brailleView != null ? !brailleView.getShrinkKeyboard() : false;
    }

    /**
     * Called after the keyboard view toggled between shrunken and expanded.
     * Updates the IME window size and moves the keyboard view between the
     * full-screen overlay and the IME window immediately, instead of waiting
     * for the next genuine show.
     */
    @Override
    public void onShrinkStateChanged() {
        updateFullscreenMode();
        applyPlacement();
    }

    // Whether the user wants the keyboard to cover the whole screen.
    private boolean isFullscreenPreferred() {
        boolean fullscreen = Options.getBooleanPreference(this,
                R.string.pref_keyboard_fullscreen_key,
                Boolean.parseBoolean(getString(
                        R.string.pref_keyboard_fullscreen_default)));
        return fullscreen
                && (brailleView == null || !brailleView.getShrinkKeyboard());
    }

    private void brailleParserReady(int status) {
        if (status == Parser.STATUS_OK) {
            if (brailleView != null) {
                brailleView.applyLocale(getLocale());
            }
        }
    }

    // KeyboardListener ---------------------------------------------------

    @Override
    public ExtractedText getAllText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return null;
        }
        return ic.getExtractedText(new ExtractedTextRequest(), 0);
    }

    @Override
    public CharSequence getTextBeforeCursor(int n) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return null;
        }
        return ic.getTextBeforeCursor(n, 0);
    }

    @Override
    public CharSequence getTextAfterCursor(int n) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return null;
        }
        return ic.getTextAfterCursor(n, 0);
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return null;
        }
        return ic.getSelectedText(flags);
    }

    @Override
    public boolean setSelection() {
        int cursor = 0;
        if (!selectAll) {
            cursor = getCursor();
        }

        int[] positions = getSelectionBoundaries(cursor);
        return mark >= 0 && positions != null ? setSelection(positions[0],
                positions[1]) : false;
    }

    @Override
    public boolean setSelection(int cursor) {
        // Disable any selection first.
        if (selectAll) {
            toggleMark();
            selectAll = false;
        }
        textComposer.finishComposingText();
        this.cursor = cursor;

        // Set the cursor to the new requested position.
        return setSelection(cursor, cursor);
    }

    @Override
    public boolean performContextMenuAction(int id) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        return ic.performContextMenuAction(id);
    }

    @Override
    public boolean deleteSurroundingText(int before, int after) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        selectAll = false;
        return ic.deleteSurroundingText(before, after);
    }

    @Override
    public boolean deleteSelection() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        int cursor = 0;
        if (!selectAll) {
            cursor = getCursor();
        }
        int[] positions = getSelectionBoundaries(cursor);
        setSelection(positions[1], positions[1]);
        return ic.deleteSurroundingText(positions[1] - positions[0], 0);
    }

    @Override
    public boolean toggleMark() {
        int cursor = getCursor();
        if (cursor == mark || selectAll) {
            mark = -1;
            selectAll = false;
        } else {
            mark = cursor;
        }
        return mark != -1 ? true : false;
    }

    @Override
    public int getCursor() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            cursor = -1;
            return cursor;
        }
        // Prefer the tracked position (no round-trip); fall back to pulling
        // the document only while tracking has not caught up.
        int tracked = editor.getCursor(ic);
        if (tracked != EditorGateway.UNKNOWN) {
            cursor = tracked;
            return cursor;
        }
        ExtractedText extractedText = getAllText();
        if (extractedText != null) {
            if (extractedText.startOffset + extractedText.selectionStart == extractedText.startOffset
                    + extractedText.selectionEnd) {
                cursor = extractedText.startOffset
                        + extractedText.selectionStart;
            }
        } else {
            cursor = -1;
        }
        return cursor;
    }

    @Override
    public boolean deselect() {
        if (!selectAll) {
            int cursor = getCursor();
            return setSelection(cursor, cursor);
        } else { // todo fix this eg. moving the cursor should remove select all
            return false;
        }
    }

    @Override
    public boolean setCursorToStartOfSelection() {
        cursor = Math.min(getCursor(), mark);
        return setSelection(cursor, cursor);
    }

    @Override
    public boolean selectAll() {
        getCursor();
        ExtractedText text = getAllText();
        if (text != null) {
            mark = text.text.length();
            setSelection(0, mark + 1);
            selectAll = true;
        }
        return selectAll;
    }

    @Override
    public boolean isSelectAll() {
        return selectAll;
    }

    @Override
    public Locale getLocale() {
        if (brailleParser != null) {
            TableInfo table = brailleParser.getTable(this);
            return table != null ? table.getLocale() : null;
        }
        return null;
    }

    @Override
    public int getDots() {
        if (brailleParser != null) {
            return brailleParser.getBrailleType(this).dots;
        }
        return -1;
    }

    @Override
    public boolean isEmojiMode() {
        return emojiMode;
    }

    @Override
    public void toggleEmojiMode() {
        emojiMode = !emojiMode;
        if (emojiMode) {
            // Command mode and emoji mode are exclusive: entering one leaves
            // the other.
            commandMode = false;
            if (commandModeEngine != null) {
                commandModeEngine.reset();
            }
            textComposer.finishComposingText(true);
            emojiEngine.onEmojiModeEntered();
        } else {
            emojiEngine.speak(getString(R.string.emoji_mode_disabled));
        }
    }

    @Override
    public boolean isCommandMode() {
        return commandMode;
    }

    @Override
    public void toggleCommandMode() {
        commandMode = !commandMode;
        if (commandMode) {
            // Command mode and emoji mode are exclusive: entering one leaves
            // the other.
            emojiMode = false;
            textComposer.finishComposingText(true);
            commandModeEngine.onModeEntered();
        } else {
            commandModeEngine.onModeExited();
        }
    }

    @Override
    public boolean handleCommandSwipe(Swipe swipe) {
        return commandModeEngine.handleGesture(swipe);
    }

    @Override
    public boolean setSelection(int start, int end) {
        return setSelectionRange(start, end);
    }

    @Override
    public String handleTypedCharacter(byte dots) {
        if (emojiMode) {
            emojiEngine.handleInput(dots);
            return null;
        }
        if (commandMode) {
            // Command mode intercepts every cell: the command cells perform
            // their editing command (and announce it), while any other cell
            // fires the normal typing event but is not translated and types
            // nothing. Returning "" (not null) keeps the typing echo path
            // from announcing "unknown character".
            commandModeEngine.handleInput(dots);
            return "";
        }
        return textComposer.handleTypedCharacter(brailleParser, dots);
    }

    @Override
    public int switchBrailleType() {
        // Finalise any in-progress Braille cells under the current table's
        // rules first, so a mid-word switch doesn't re-interpret cells that
        // were already typed (e.g. computer Braille digits turned into
        // literary letters). Only newly typed cells use the new table.
        textComposer.finishComposingText(true);
        if (brailleParser != null) {
            return brailleParser.switchBrailleType(this).dots;
        }
        return -1;
    }

    @Override
    public String switchTable() {
        // Finalise any in-progress Braille cells under the current table's
        // rules first, so a mid-word switch doesn't re-interpret cells that
        // were already typed (e.g. contracted vs uncontracted). Only newly
        // typed cells use the new table.
        textComposer.finishComposingText(true);
        if (brailleParser != null) {
            return brailleParser.switchTable(this);
        }
        return null;
    }

    @Override
    public boolean isPasswordField() {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null) {
            return false;
        }
        int inputType = info.inputType;
        // Only text fields can be password fields. Compare the whole
        // variation field against the exact hidden-password variations
        // instead of a single bit: TYPE_TEXT_VARIATION_PASSWORD is only one
        // bit of the variation mask, so masking with it alone also matched
        // unrelated variations that share that bit (e.g. WEB_EMAIL_ADDRESS
        // and FILTER), making ordinary web-form fields look like password
        // fields. VISIBLE_PASSWORD is deliberately excluded: apps set it
        // when the user toggles "show password", so the text is visible and
        // the keyboard should echo it normally.
        if ((inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    @Override
    public void onKey(int keyCode) {
        InputConnection ic = getCurrentInputConnection();
        // disable selection
        if (selectAll) {
            toggleMark();
            selectAll = false;
        }
        textComposer.finishComposingText();
        switch (keyCode) {
        case Keyboard.KEYCODE_DELETE:
            ic.deleteSurroundingText(1, 0);
            break;
        case Keyboard.KEYCODE_DONE:
        case '\n':
            // Commit a literal newline instead of sending a KEYCODE_ENTER key
            // event. Chat apps (Discord, Google Messages) configure their
            // compose fields with an IME_ACTION_SEND, so the framework routes
            // a raw Enter key to that action and sends the message; a
            // committed "\n" character is inserted as text and cannot be
            // intercepted as a send.
            ic.commitText("\n", 1);
            break;
        default:
            if (keyCode >= '0' && keyCode <= '9') {
                keyDownUp(ic, keyCode - '0' + KeyEvent.KEYCODE_0);
            } else {
                ic.commitText(String.valueOf((char) keyCode), 1);
            }
            break;
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(InputConnection ic, int keyEventCode) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    private boolean setSelectionRange(int start, int end) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        textComposer.finishComposingText();
        return ic.setSelection(start, end);
    }

    private int[] getSelectionBoundaries(int cursor) {
        int[] array = null;
        ExtractedText text = getAllText();
        if (text != null) {
            mark = mark > text.text.length() ? text.text.length() : mark;
            array = new int[2];
            array[0] = Math.min(cursor, mark);
            array[1] = Math.max(cursor, mark);
            array[1] = array[1] < text.text.length() ? array[1] + 1 : array[1];
        }
        return array;
    }

    @Override
    public void finishComposingText() {
        textComposer.finishComposingText(true);
    }

    @Override
    public void commitText(String text, int newCursorPosition) {
        textComposer.finishComposingText();
        InputConnection ic = getCurrentInputConnection();
        if (selectAll) {
            toggleMark();
            selectAll = false;
        }
        textComposer.updateShiftState();
        text = textComposer.capitalise(text.subSequence(0, text.length()))
                .toString();
        ic.commitText(text, newCursorPosition);
    }

    @Override
    public void closeKeyboard() {
        Diagnostics.log(this, "keyboard closing");
        // Take the full-screen overlay down right here, synchronously, like
        // the reference keyboard does. It covers the whole screen, so waiting
        // for the framework to report the hide can leave the device stuck
        // behind it.
        KeyboardOverlayHost.removeOverlay();
        requestHideSelf(0);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Escape hatch for full-screen mode: the overlay covers the entire
        // screen including the system bars, so BACK must close the keyboard.
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown()
                && brailleView != null
                && KeyboardOverlayHost.isInOverlay(brailleView)) {
            closeKeyboard();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public String translateOnly(byte dots) {
        return textComposer.translateOnly(brailleParser, dots);
    }

    @Override
    public boolean submitText() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        if (ic == null || info == null) {
            return false;
        }

        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        String packageName = info.packageName;

        // Messaging apps often use IME_ACTION_NONE (0), IME_ACTION_UNSPECIFIED (0), or IME_ACTION_DONE (6)
        // for multiline text fields, but we want the submit gesture to actually SEND the message.
        // We override the action to IME_ACTION_SEND (4) for known messaging apps.
        if (packageName != null && (
            packageName.equals("org.telegram.messenger") ||
            packageName.equals("org.telegram.BifToGram") ||
            packageName.equals("ir.blindgram.messenger") ||
            packageName.equals("tw.nekomimi.nekogram") ||
            packageName.equals("com.fmwhatsapp") ||
            packageName.equals("com.facebook.orca") ||
            packageName.equals("com.telegram.plus") ||
            packageName.equals("com.google.android.apps.messaging") ||
            packageName.equals("dk.bearware.gui") ||
            packageName.equals("com.samsung.android.messaging") ||
            packageName.equals("com.whatsapp.w4b") ||
            packageName.equals("com.whatsapp"))) {

            action = EditorInfo.IME_ACTION_SEND;
        }

        if (action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
            return true;
        } else {
            ic.commitText("\n", 1);
            return false;
        }
    }
}
