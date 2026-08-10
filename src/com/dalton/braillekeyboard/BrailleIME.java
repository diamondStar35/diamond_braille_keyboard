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

    private Parser brailleParser;
    private View brailleView = null;
    private final TextComposer textComposer = new TextComposer(this);
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
        emojiEngine = new EmojiEngine(this, this);
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
    // (com.dalton.braillekeyboard.View), which shadows android.view.View here.
    public android.view.View onCreateInputView() {
        super.onCreateInputView();
        brailleView = (View) getLayoutInflater().inflate(
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
        // remove any existing selection.
        selectAll = false;
        mark = -1;

        // Disable prediction (composing text) by default.  Our manual
        // differential update logic in compose() is much more robust and
        // prevents duplication bugs in apps that don't handle composing spans
        // correctly (e.g. Chrome address bar, Star Taxi).
        textComposer.setPredictionOn(false);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        // While the keyboard window is shown, pass the whole screen through to
        // the keyboard so it receives raw touches and gestures even with
        // TalkBack turned on (see AccessibilityService).
        AccessibilityService.setKeyboardPassthrough(true);
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
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        // The keyboard window is gone, so TalkBack can handle the screen
        // normally again.
        AccessibilityService.setKeyboardPassthrough(false);
        emojiMode = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            textComposer.finishComposingText(false);
        }

        if (brailleView != null) {
            brailleView.close();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Make sure the screen is never left in passthrough mode.
        AccessibilityService.setKeyboardPassthrough(false);
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

    private void brailleParserReady(int status) {
        if (status == Parser.STATUS_OK) {
            if (brailleView != null) {
                brailleView.setLocale(getLocale());
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
            textComposer.finishComposingText(true);
            emojiEngine.onEmojiModeEntered();
        } else {
            emojiEngine.speak(getString(R.string.emoji_mode_disabled));
        }
    }

    @Override
    public String handleTypedCharacter(byte dots) {
        if (emojiMode) {
            emojiEngine.handleInput(dots);
            return null;
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
        int inputType = getCurrentInputEditorInfo().inputType;
        return (inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
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
            keyDownUp(ic, KeyEvent.KEYCODE_ENTER);
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

    private boolean setSelection(int start, int end) {
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
        requestHideSelf(0);
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
