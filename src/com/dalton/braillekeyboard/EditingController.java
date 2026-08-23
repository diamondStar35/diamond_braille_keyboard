package com.dalton.braillekeyboard;

import android.content.ClipboardManager;
import android.content.Context;

import com.dalton.braillekeyboard.ActionHandler.OnActionListener;
import com.dalton.braillekeyboard.EditingUtilities.Word;
import com.dalton.braillekeyboard.Options.KeyboardEcho;

/**
 * Performs the text editing operations behind the keyboard: cursor movement
 * at various granularities, deletion, the selection-based edit actions
 * (copy/cut/paste/speak/delete/select all), typing and echoing, and spell
 * checking.
 *
 * <p>It is a pure helper of {@link ActionHandler}, which decides which
 * editing operation a gesture maps to; this class owns no input method
 * state beyond what it needs for the current edit session. Spell checking
 * is delegated to a {@link SpellCheckController}. All results are
 * delivered through the {@link OnActionListener} callback, exactly as they
 * were when the logic lived inside ActionHandler.
 */
public class EditingController implements SpellCheckController.TextProvider {

    /** Representation of varying textual granularities from the smallest
     *  level character up until the entire text. */
    public enum Granularity {
        CHARACTER, WORD, LINE, ALL;
    }

    /** Representation of the currently available edit actions that can be
     *  performed on a selection of text. */
    private enum EditAction {
        SELECT_ALL(R.string.select_all), COPY(android.R.string.copy), CUT(
                android.R.string.cut), PASTE(android.R.string.paste), SPEAK(
                R.string.speak_selection), DELETE(R.string.delete_selection);

        // The Android resource for the text UI string for this action.
        public final int resource;

        EditAction(int resource) {
            this.resource = resource;
        }

        // Find the position of this action in the enum values() array.
        private int getIndexOfThis() {
            EditAction[] values = EditAction.values();
            int i;
            for (i = 0; i < values.length; i++) {
                if (this == values[i]) {
                    break;
                }
            }
            return i;
        }

        /**
         * Move in order to the next EditAction in the enum list. If we are at
         * the end of the list it will wrap.
         *
         * @return The new EditAction instance.
         */
        public EditAction next(ClipboardManager clipboard) {
            int i = getIndexOfThis();

            if (++i == values().length) {
                i = 0; // point at first item in list.
            }

            if (values()[i] == PASTE) {
                boolean canPaste;
                // Check that there is text on the clipboard so it makes sense
                // showing paste.
                if (!(clipboard.hasPrimaryClip())) {
                    canPaste = false;
                } else {
                    // This enables the paste menu item, since the clipboard
                    // contains plain text.
                    canPaste = true;
                }
                if (!canPaste) {
                    if (++i == values().length) {
                        i = 0; // Point at start if we exceed the end
                    }
                }
            }

            return values()[i];
        }
    }

    private final ClipboardManager clipboard;
    private final SpellCheckController spellCheckController;
    private KeyboardListener listener;
    private OnActionListener callback;

    private EditAction editAction = EditAction.COPY;

    /**
     * Create an EditingController for the current input session. The
     * {@link #setListener(KeyboardListener)} and
     * {@link #setCallback(OnActionListener)} must be called before any edit
     * operation is performed.
     *
     * @param context The application context.
     * @param callback The OnActionListener to deliver results to.
     */
    public EditingController(Context context, OnActionListener callback) {
        clipboard = (ClipboardManager) context
                .getSystemService(Context.CLIPBOARD_SERVICE);
        spellCheckController = new SpellCheckController(context);
        spellCheckController.setCallback(callback);
        spellCheckController.setTextProvider(this);
        this.callback = callback;
    }

    /** Set the KeyboardListener to edit text through. */
    public void setListener(KeyboardListener listener) {
        this.listener = listener;
        spellCheckController.setListener(listener);
    }

    /** Set the OnActionListener to deliver results to. */
    public void setCallback(OnActionListener callback) {
        this.callback = callback;
        spellCheckController.setCallback(callback);
    }

    /** Releases system resources. Call when the input session ends. */
    public void destroy() {
        spellCheckController.destroy();
    }

    // Move the cursor left by the appropriate granularity and speak the
    // result.
    public void moveLeft(Context context, Granularity granularity) {
        Word word = null;
        listener.finishComposingText();
        if (listener.isSelectAll()) {
            granularity = Granularity.ALL;
            listener.setSelection(0);
        }
        switch (granularity) {
        case CHARACTER:
            word = EditingUtilities.moveToPreviousCharacter(listener);
            break;
        case WORD:
            word = EditingUtilities.moveToPreviousWord(listener);
            break;
        case LINE:
            word = EditingUtilities.moveToPreviousLine(listener);
            break;
        case ALL:
            word = EditingUtilities.moveToHome(listener);
            break;
        default:
        }

        if (word != null) {
            callback.onText("%s",
                    !word.moveLeft ? context.getString(R.string.start_of_text)
                            : word.word,
                    word.moveLeft && listener.isPasswordField());
        }
    }

    // Move the cursor right by the appropriate granularity and speak the
    // result.
    public void moveRight(Context context, Granularity granularity) {
        Word word = null;
        listener.finishComposingText();
        if (listener.isSelectAll()) {
            granularity = Granularity.ALL;
            listener.setSelection(0);
        }
        switch (granularity) {
        case CHARACTER:
            word = EditingUtilities.moveToNextCharacter(listener);
            break;
        case WORD:
            word = EditingUtilities.moveToNextWord(listener);
            break;
        case LINE:
            word = EditingUtilities.moveToNextLine(listener);
            break;
        case ALL:
            word = EditingUtilities.moveToEnd(listener);
            break;
        default:
        }

        if (word != null) {
            callback.onText("%s",
                    !word.moveRight ? context.getString(R.string.end_of_text)
                            : word.word,
                    word.moveRight && listener.isPasswordField());
        }
    }

    // Perform backspace by the specified Granularity.
    public boolean backspace(Context context, Granularity granularity,
            boolean fastDoubleTouch) {
        Word word = null;
        boolean canDelete = true;
        switch (granularity) {
        case CHARACTER:
            // Fast path: one read for the character being deleted and one
            // delete call. The generic move-then-delete dance below costs
            // several binder round-trips per key press.
            listener.finishComposingText();
            CharSequence deleted = listener.getTextBeforeCursor(1);
            if (deleted != null && deleted.length() > 0) {
                listener.deleteSurroundingText(1, 0);
                callback.onText(context.getString(R.string.deleted),
                        deleted.toString(), listener.isPasswordField());
                return true;
            }
            break;
        case WORD:
            // Fast path: pull a window of text before the cursor once, find
            // the preceding separators plus the word before them locally,
            // then issue a single delete. This replaces a sequence of about
            // ten round-trips through the editor (cursor pulls, two moves
            // with their own reads, and the final delete). The announced
            // word is exactly the letters being removed.
            listener.finishComposingText();
            CharSequence windowText = listener.getTextBeforeCursor(
                    EditingUtilities.MAX_LINE_LENGTH);
            if (windowText == null || windowText.length() == 0) {
                break;
            }
            String window = windowText.toString();
            int end = window.length();
            int startOfSeparators = end;
            while (startOfSeparators > 0 && EditingUtilities
                    .isWordSeparatorChar(window.charAt(startOfSeparators - 1))) {
                --startOfSeparators;
            }
            int startOfWord = startOfSeparators;
            while (startOfWord > 0 && !EditingUtilities.isWordSeparatorChar(
                    window.charAt(startOfWord - 1))) {
                --startOfWord;
            }
            int total = end - startOfWord;
            if (total == 0) {
                callback.onText("%s",
                        context.getString(R.string.nothing_to_delete), false);
                return true;
            }
            String spoken = window.substring(startOfWord, startOfSeparators);
            listener.deleteSurroundingText(total, 0);
            callback.onText(context.getString(R.string.deleted), spoken,
                    listener.isPasswordField());
            return true;
        case LINE:
            canDelete = isConfirmed(context, fastDoubleTouch);
            if (canDelete) {
                listener.finishComposingText();
                int cursor = listener.getCursor();
                word = EditingUtilities.getLine(listener);
                if (word != null && (cursor > 0 || word.charsAfter > 0)) {
                    int moveChars = (cursor - word.charsBefore) > 0 ? 1 : 0;
                    listener.setSelection(cursor - moveChars
                            - word.charsBefore);
                    word.charsBefore = word.charsBefore + moveChars
                            + word.charsAfter;
                }
            }
            break;
        case ALL:
            canDelete = isConfirmed(context, fastDoubleTouch);
            if (canDelete) {
                listener.finishComposingText();
                word = EditingUtilities.moveToHome(listener);
                word.word = EditingUtilities.getAllText(listener);
                word.charsBefore = word.word.length();
            }
            break;
        default:
        }
        return performDelete(context, word, canDelete);
    }

    // Perform the currently selected Edit Action on the region.
    public void selectAction(Context context) {
        switch (editAction) {
        case COPY:
            performContextMenuAction(context, true, android.R.id.copy,
                    R.string.copied, R.string.copy_error);
            listener.deselect();
            break;
        case CUT:
            if (performContextMenuAction(context, true, android.R.id.cut,
                    android.R.string.cut, R.string.cut_error)) {
                listener.setCursorToStartOfSelection();
            }
            break;
        case PASTE:
            if (performContextMenuAction(context, false, android.R.id.paste,
                    R.string.pasted, R.string.paste_error)) {
                int cursor = listener.getCursor();
                listener.setSelection(cursor);
            }
            break;
        case SPEAK:
        case DELETE:
            performAction(context, editAction);
            break;
        case SELECT_ALL:
            listener.selectAll();
            callback.onText("%s", context.getString(R.string.selected_all),
                    false);
            break;
        default:
        }
    }

    // Move to the next EditAction and announce it.
    public void nextAction(Context context) {
        editAction = editAction.next(clipboard);
        callback.onText("%s", context.getString(editAction.resource), false);
    }

    // Insert a certain character like a ' ' or '\n'
    public void typeCharacter(Context context, int code, String charName) {
        listener.finishComposingText();
        Word word = EditingUtilities.getWord(listener);
        String message = word == null ? null : word.word.substring(0,
                word.charsBefore);

        // Text expansion: when a space is typed right after an abbreviation,
        // the abbreviation is replaced by its expansion, followed by the
        // space. The expansion is announced so the user hears what was
        // inserted instead of the abbreviation that disappeared.
        if (code == ' ' && message != null && message.length() > 0) {
            String expansion = new AbbreviationStorage(context)
                    .findExpansion(message);
            if (expansion != null) {
                listener.deleteSurroundingText(message.length(), 0);
                listener.commitText(expansion + " ", 1);
                callback.onText("%s", expansion,
                        listener.isPasswordField());
                return;
            }
        }

        listener.onKey(code);

        if ((message = echoWord(context, message)) == null) {
            message = echoCharacter(context, charName);
        }
        callback.onText("%s", message, listener.isPasswordField());

        if (SpeechEvent.MISSPELLING.isEnabled(context)
                && spellCheckController.isSpellCheckAvailable()
                && word != null && word.charsBefore > 0) {
            int cursor = listener.getCursor();
            // The word was captured just before the space was committed, so
            // the typed portion starts (cursor - 1) characters back, past
            // the space. Check only the typed part: when the space splits a
            // word, characters after the cursor belong to the next word.
            String typedWord = word.word.substring(0, word.charsBefore);
            int wordOffset = cursor - 1 - word.charsBefore;
            if (wordOffset >= 0) {
                spellCheckController.checkTypedWord(context, typedWord,
                        wordOffset);
            }
        }
    }

    // Special logic for double space to insert a period followed by a space.
    public boolean handleDoubleSpace(Context context) {
        if (Options.getBooleanPreference(context,
                R.string.pref_double_space_period_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_double_space_period_default)))) {
            CharSequence text = listener.getTextBeforeCursor(2);
            if (text != null) {
                if (text.length() == 2
                        && Character.isWhitespace(text.charAt(1))
                        && Character.isLetterOrDigit(text.charAt(0))) {
                    listener.deleteSurroundingText(1, 0);
                    listener.onKey('.');
                    typeCharacter(context, ' ', " ");
                    return true;
                }
            }
        }
        return false;
    }

    // Get a particular granularity of text from the IME.
    public String getInput(Granularity granularity) {
        String text = "";
        Word word;
        switch (granularity) {
        case CHARACTER:
            text = EditingUtilities.getCharacter(listener);
            break;
        case WORD:
            word = EditingUtilities.getWord(listener);
            text = word == null ? null : word.word;
            break;
        case LINE:
            word = EditingUtilities.getLine(listener);
            text = word == null ? null : word.word;
            break;
        case ALL:
            text = EditingUtilities.getAllText(listener);
            break;
        default:
        }
        return text;
    }

    // SpellCheckController.TextProvider: the whole current text of the editor.
    @Override
    public String getCurrentText() {
        return getInput(Granularity.ALL);
    }

    public void doSpellCheck(final Context context,
            SpellChecker.Direction direction, int move, int cursor) {
        spellCheckController.doSpellCheck(context, direction, move, cursor);
    }

    public void nextSpellCheckSuggestion(Context context) {
        spellCheckController.nextSuggestion(context);
    }

    public void previousSpellCheckSuggestion(Context context) {
        spellCheckController.previousSuggestion(context);
    }

    // Rules for echoing character. Return the character if it should be
    // echoed else null.
    public static String echoCharacter(Context context, String character) {
        if ((Integer.parseInt(Options.getStringPreference(context,
                R.string.pref_echo_feedback_key,
                KeyboardEcho.CHARACTER.getValue())) & KeyboardEcho.CHARACTER.value) != 0) {
            return character;
        }
        return null;
    }

    // Rules for echoing word. Return the word if it should be echoed
    // else null.
    public static String echoWord(Context context, String word) {
        if ((Integer.parseInt(Options.getStringPreference(context,
                R.string.pref_echo_feedback_key,
                KeyboardEcho.CHARACTER.getValue())) & KeyboardEcho.WORD.value) != 0) {
            return word;
        }
        return null;
    }

    // Handle prompting user to confirm an action with a double swipe.
    private boolean isConfirmed(Context context, boolean fastDoubleTouch) {
        if (!fastDoubleTouch) {
            callback.onText("%s", context.getString(R.string.swipe_confirm),
                    false);
            return false;
        }
        return true;
    }

    // Handle these actions by using the inbuilt android context menu action
    private boolean performContextMenuAction(Context context,
            boolean requiresSelection, int code, int successString,
            int errorString) {
        if (!requiresSelection || listener.setSelection()) {
            if (listener.performContextMenuAction(code)) {
                callback.onText("%s", context.getString(successString), false);
                return true;
            } else {
                callback.onText("%s", context.getString(errorString), false);
                return false;
            }
        } else {
            callback.onText("%s", context.getString(R.string.mark_not_set),
                    false);
            return false;
        }
    }

    // These actions aren't implemented by Android so do them ourselves.
    private boolean performAction(Context context, EditAction action) {
        if (listener.setSelection()) {
            CharSequence text = listener.getSelectedText(0);
            listener.deselect();
            switch (action) {
            case SPEAK:
                callback.onText(
                        "%s",
                        text == null ? context.getString(R.string.blank) : text
                                .toString(), listener.isPasswordField());
                break;
            case DELETE:
                if (listener.deleteSelection() && text != null) {
                    callback.onText(context.getString(R.string.deleted),
                            text.toString(), listener.isPasswordField());
                } else {
                    callback.onText("%s",
                            context.getString(R.string.nothing_to_delete),
                            false);
                }
                break;
            default:
                return false;
            }
            return true;
        }
        callback.onText("%s", context.getString(R.string.mark_not_set), false);
        return false;
    }

    // Given the text to delete and a canDelete flag do the actual deletion.
    private boolean performDelete(Context context, Word word,
            boolean canDelete) {
        if (canDelete && word != null) {
            if (word.charsBefore > 0 || word.charsAfter > 0) {
                // cursor moved back word.charsBefore positions, so delete that
                // many chars ahead of the cursor.
                if (listener.deleteSurroundingText(0, word.charsBefore)) {
                    if (word.word != null) {
                        callback.onText(context.getString(R.string.deleted),
                                word.word, listener.isPasswordField());
                    }
                    return true;
                } else {
                    return false;
                }
            } else {
                callback.onText("%s",
                        context.getString(R.string.nothing_to_delete), false);
                return true;
            }
        }
        return false;
    }

}
