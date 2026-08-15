package com.dalton.braillekeyboard;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.view.inputmethod.ExtractedText;

import com.dalton.braillekeyboard.EditingUtilities.Word;

/**
 * The command mode of the keyboard, similar to the command mode of Braille
 * Screen Input on iOS.
 *
 * <p>While command mode is active, single-finger dot swipes and touch-holds
 * navigate the text field (by character, word, line, paragraph, or to the
 * boundaries of the text) instead of performing their normal actions, and
 * every Braille cell is intercepted: the command cells perform editing
 * commands (selection, copy, cut, paste, delete, select all and reading the
 * selection), while any other cell fires the normal typing event but is
 * neither translated nor typed. Multi-finger gestures keep their normal
 * behaviour.
 *
 * <p>The gesture to command mapping lives in {@link #GESTURE_COMMANDS} and
 * the cell to command mapping in {@link #CELL_COMMANDS}, so making either
 * customizable later only requires replacing the static map with a
 * preference-backed one.
 *
 * <p>Selection works like a text anchor: starting the selection places the
 * anchor at the current cursor position with nothing selected. Moving the
 * cursor right expands the selection, moving left shrinks it until it is
 * empty, after which moving left selects the previous text again. The
 * boundary commands (start/end of text, line or word) establish the
 * selection from the anchor to the destination in one operation, whichever
 * side of the anchor the destination lies on.
 */
public class CommandModeEngine {

    /** The gestures command mode understands. */
    private enum Command {
        MOVE_LEFT_CHARACTER, MOVE_RIGHT_CHARACTER,
        MOVE_LEFT_WORD, MOVE_RIGHT_WORD,
        FIRST_LETTER_OF_WORD, LAST_LETTER_OF_WORD,
        MOVE_PREVIOUS_LINE, MOVE_NEXT_LINE,
        PREVIOUS_PARAGRAPH, NEXT_PARAGRAPH,
        START_OF_TEXT, END_OF_TEXT,
        START_OF_LINE, END_OF_LINE;
    }

    /** The Braille cells command mode understands. */
    private enum CellCommand {
        START_SELECTION, END_SELECTION, COPY, CUT, PASTE,
        DELETE_SELECTION, SELECT_ALL, READ_SELECTION;
    }

    // The command mode gestures. Not customizable yet, but kept in one map
    // so the whole vocabulary can be moved to preferences later.
    private static final Map<Swipe, Command> GESTURE_COMMANDS =
            new EnumMap<Swipe, Command>(Swipe.class);

    // The command mode cells (dots 1-6 bit pattern -> command).
    private static final Map<Byte, CellCommand> CELL_COMMANDS =
            new HashMap<Byte, CellCommand>();

    static {
        GESTURE_COMMANDS.put(Swipe.ONE_LEFT, Command.MOVE_LEFT_CHARACTER);
        GESTURE_COMMANDS.put(Swipe.ONE_RIGHT, Command.MOVE_RIGHT_CHARACTER);
        GESTURE_COMMANDS.put(Swipe.TWO_LEFT, Command.MOVE_PREVIOUS_LINE);
        GESTURE_COMMANDS.put(Swipe.TWO_RIGHT, Command.MOVE_NEXT_LINE);
        GESTURE_COMMANDS.put(Swipe.FOUR_LEFT, Command.FIRST_LETTER_OF_WORD);
        GESTURE_COMMANDS.put(Swipe.FOUR_RIGHT, Command.LAST_LETTER_OF_WORD);
        GESTURE_COMMANDS.put(Swipe.FIVE_LEFT, Command.PREVIOUS_PARAGRAPH);
        GESTURE_COMMANDS.put(Swipe.FIVE_RIGHT, Command.NEXT_PARAGRAPH);
        GESTURE_COMMANDS.put(Swipe.HOLD_1_SWIPE_4_LEFT,
                Command.MOVE_LEFT_WORD);
        GESTURE_COMMANDS.put(Swipe.HOLD_1_SWIPE_4_RIGHT,
                Command.MOVE_RIGHT_WORD);
        GESTURE_COMMANDS.put(Swipe.HOLD_2_SWIPE_5_LEFT,
                Command.START_OF_TEXT);
        GESTURE_COMMANDS.put(Swipe.HOLD_2_SWIPE_5_RIGHT,
                Command.END_OF_TEXT);
        GESTURE_COMMANDS.put(Swipe.HOLD_2_SWIPE_4_LEFT,
                Command.START_OF_LINE);
        GESTURE_COMMANDS.put(Swipe.HOLD_2_SWIPE_4_RIGHT,
                Command.END_OF_LINE);

        // 234 (s) = start selection, 156 = end selection, 14 (c) = copy,
        // 1346 (x) = cut, 1236 (v) = paste, 145 (d) = delete the selection,
        // 2356 (!) = select all, 1245 (g) = read the selection.
        CELL_COMMANDS.put((byte) 14, CellCommand.START_SELECTION);
        CELL_COMMANDS.put((byte) 49, CellCommand.END_SELECTION);
        CELL_COMMANDS.put((byte) 9, CellCommand.COPY);
        CELL_COMMANDS.put((byte) 45, CellCommand.CUT);
        CELL_COMMANDS.put((byte) 39, CellCommand.PASTE);
        CELL_COMMANDS.put((byte) 25, CellCommand.DELETE_SELECTION);
        CELL_COMMANDS.put((byte) 54, CellCommand.SELECT_ALL);
        CELL_COMMANDS.put((byte) 27, CellCommand.READ_SELECTION);
    }

    private final Context context;
    private final KeyboardListener listener;
    private final Speech speech;

    // Whether the selection sub-mode is active. While it is, navigation
    // extends or shrinks the selection instead of just moving the cursor.
    private boolean selectionMode = false;

    // The selection anchor: the position where the selection started. The
    // cursor is the other endpoint; the selected range spans
    // [min(anchor, cursor), max(anchor, cursor)). -1 when there is no
    // selection.
    private int anchor = -1;

    // The other endpoint of the selection: the current cursor position as
    // tracked by the engine. Kept here because the IME only reports a fresh
    // cursor position while the field selection is collapsed, which it is
    // not while we are selecting. -1 when there is no selection.
    private int cursor = -1;

    public CommandModeEngine(Context context, KeyboardListener listener) {
        this.context = context;
        this.listener = listener;
        this.speech = new Speech(context, new Speech.OnReadyListener() {
            @Override
            public void ttsReady() {
            }
        });
    }

    /** Announce the start of command mode and reset any selection state. */
    public void onModeEntered() {
        speech.speak(context, context.getString(R.string.command_mode_enabled),
                Speech.QUEUE_FLUSH);
        selectionMode = false;
        anchor = -1;
        cursor = -1;
    }

    /** Announce the end of command mode and reset any selection state. */
    public void onModeExited() {
        speech.speak(context, context.getString(R.string.command_mode_disabled),
                Speech.QUEUE_FLUSH);
        selectionMode = false;
        anchor = -1;
        cursor = -1;
    }

    /** Reset all mode state without announcing anything. Used when the
     *  input session ends while command mode is still active. */
    public void reset() {
        selectionMode = false;
        anchor = -1;
        cursor = -1;
    }

    /**
     * Handle a Braille cell typed while command mode is active.
     *
     * @return true if the cell was a command and has been performed, false
     *         otherwise (the cell fires the normal typing event but is not
     *         translated or typed).
     */
    public boolean handleInput(byte dots) {
        CellCommand command = CELL_COMMANDS.get(dots);
        if (command == null) {
            // Not a command cell: nothing is typed in command mode, so the
            // selection (if any) is left untouched.
            return false;
        }
        switch (command) {
        case START_SELECTION:
            startSelection();
            break;
        case END_SELECTION:
            endSelection();
            break;
        case COPY:
            copy();
            break;
        case CUT:
            cut();
            break;
        case PASTE:
            paste();
            break;
        case DELETE_SELECTION:
            deleteSelection();
            break;
        case SELECT_ALL:
            selectAll();
            break;
        case READ_SELECTION:
            readSelection();
            break;
        }
        return true;
    }

    /**
     * Handle a gesture performed while command mode is active.
     *
     * @return true if the gesture was consumed (performed or intentionally
     *         ignored) by command mode.
     */
    public boolean handleGesture(Swipe swipe) {
        Command command = GESTURE_COMMANDS.get(swipe);
        if (command == null) {
            // Not part of the command vocabulary: ignore it.
            return true;
        }
        listener.finishComposingText();
        if (!selectionMode) {
            // Moving the cursor without selection mode clears any selection,
            // exactly like a normal keyboard.
            anchor = -1;
            cursor = -1;
        }
        perform(command);
        return true;
    }

    // ------------------------------------------------------------------
    // Cells: selection and editing commands
    // ------------------------------------------------------------------

    // Enter selection mode. Nothing is selected yet: the anchor is placed at
    // the current cursor position, and the user selects whatever comes next.
    private void startSelection() {
        cursor = listener.getCursor();
        anchor = cursor;
        selectionMode = true;
        speak(R.string.command_start_selection);
    }

    // End selection mode. The selection itself stays, exactly like a normal
    // keyboard selection.
    private void endSelection() {
        selectionMode = false;
        speak(R.string.command_end_selection);
    }

    private void copy() {
        if (!applySelection()) {
            speak(R.string.command_no_text_selected);
            return;
        }
        listener.performContextMenuAction(android.R.id.copy);
        speak(R.string.command_copied);
        // Copy does not clear the selection, like copy on a computer.
    }

    private void cut() {
        if (!applySelection()) {
            speak(R.string.command_no_text_selected);
            return;
        }
        if (listener.performContextMenuAction(android.R.id.cut)) {
            anchor = -1;
            cursor = -1;
            speak(R.string.command_cut);
        }
    }

    private void paste() {
        // Pasting needs no selection: it inserts at the cursor (or replaces
        // the selection if one is active).
        if (listener.performContextMenuAction(android.R.id.paste)) {
            anchor = -1;
            cursor = -1;
            speak(R.string.command_pasted);
        } else {
            speak(R.string.paste_error);
        }
    }

    private void deleteSelection() {
        if (!applySelection()) {
            speak(R.string.command_no_text_selected);
            return;
        }
        if (listener.isSelectAll()) {
            listener.deleteSelection();
        } else if (anchor != -1) {
            int start = Math.min(anchor, cursor);
            int end = Math.max(anchor, cursor);
            listener.setSelection(end);
            listener.deleteSurroundingText(end - start, 0);
        }
        anchor = -1;
        cursor = -1;
        speak(R.string.command_selection_deleted);
    }

    private void selectAll() {
        if (listener.isSelectAll()) {
            speak(R.string.command_text_already_selected);
            return;
        }
        listener.selectAll();
        anchor = -1;
        cursor = -1;
        speak(R.string.command_select_all);
    }

    private void readSelection() {
        if (!applySelection()) {
            speak(R.string.command_no_text_selected);
            return;
        }
        CharSequence text = listener.getSelectedText(0);
        if (text == null || text.length() == 0) {
            speak(R.string.command_no_text_selected);
        } else {
            speakDocument(text.toString());
        }
    }

    // Apply the current selection (the anchor range, or select-all) to the
    // underlying input field. Returns false when there is no selection.
    private boolean applySelection() {
        if (listener.isSelectAll()) {
            return listener.setSelection();
        }
        if (anchor != -1 && cursor != -1) {
            int start = Math.min(anchor, cursor);
            int end = Math.max(anchor, cursor);
            return listener.setSelection(start, end);
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Gestures: navigation
    // ------------------------------------------------------------------

    // The current cursor position. While selection mode is active the IME
    // only reports a fresh cursor while the field selection is collapsed, so
    // the engine's own tracked endpoint is used instead of the live cursor;
    // after cut/paste/delete the tracked endpoint is reset, so the live
    // cursor is read again.
    private int currentCursor() {
        return selectionMode && cursor >= 0 ? cursor : listener.getCursor();
    }

    private void perform(Command command) {
        switch (command) {
        case MOVE_LEFT_CHARACTER:
            moveByCharacter(-1);
            break;
        case MOVE_RIGHT_CHARACTER:
            moveByCharacter(1);
            break;
        case MOVE_LEFT_WORD:
            moveByWord(-1);
            break;
        case MOVE_RIGHT_WORD:
            moveByWord(1);
            break;
        case FIRST_LETTER_OF_WORD:
            moveToWordBoundary(-1);
            break;
        case LAST_LETTER_OF_WORD:
            moveToWordBoundary(1);
            break;
        case MOVE_PREVIOUS_LINE:
            moveByLine(-1);
            break;
        case MOVE_NEXT_LINE:
            moveByLine(1);
            break;
        case PREVIOUS_PARAGRAPH:
            moveByParagraph(-1);
            break;
        case NEXT_PARAGRAPH:
            moveByParagraph(1);
            break;
        case START_OF_TEXT:
            moveToTextBoundary(-1);
            break;
        case END_OF_TEXT:
            moveToTextBoundary(1);
            break;
        case START_OF_LINE:
            moveToLineBoundary(-1);
            break;
        case END_OF_LINE:
            moveToLineBoundary(1);
            break;
        }
    }

    private void moveByCharacter(int direction) {
        int position = currentCursor();
        if (position < 0) {
            return;
        }
        int dest = position + direction;
        int length = textLength();
        if (dest < 0) {
            speak(R.string.start_of_text);
            return;
        }
        if (dest > length) {
            speak(R.string.end_of_text);
            return;
        }
        if (selectionMode) {
            moveWithSelection(position, dest);
        } else {
            listener.setSelection(dest);
            speakDocument(textOf(Math.min(position, dest),
                    Math.max(position, dest)));
        }
    }

    private void moveByWord(int direction) {
        int oldCursor = currentCursor();
        Word word = direction < 0
                ? EditingUtilities.moveToPreviousWord(listener)
                : EditingUtilities.moveToNextWord(listener);
        if (word == null) {
            return;
        }
        int newCursor = listener.getCursor();
        if (newCursor == oldCursor) {
            // The cursor could not move: we are at a boundary.
            speak(direction < 0 ? R.string.start_of_text
                    : R.string.end_of_text);
        } else if (selectionMode) {
            moveWithSelection(oldCursor, newCursor);
        } else {
            speakDocument(word.word);
        }
    }

    private void moveToWordBoundary(int direction) {
        int oldCursor = currentCursor();
        Word word = direction < 0
                ? EditingUtilities.moveToStartOfWord(listener)
                : EditingUtilities.moveToEndOfWord(listener);
        if (word == null) {
            return;
        }
        int newCursor = listener.getCursor();
        if (newCursor == oldCursor) {
            speak(direction < 0 ? R.string.start_of_text
                    : R.string.end_of_text);
        } else if (selectionMode) {
            moveWithSelection(oldCursor, newCursor);
        } else {
            speakDocument(word.word);
        }
    }

    private void moveByLine(int direction) {
        int oldCursor = currentCursor();
        Word word = direction < 0
                ? EditingUtilities.moveToPreviousLine(listener)
                : EditingUtilities.moveToNextLine(listener);
        if (word == null) {
            return;
        }
        int newCursor = listener.getCursor();
        if (newCursor == oldCursor) {
            speak(direction < 0 ? R.string.start_of_text
                    : R.string.end_of_text);
        } else if (selectionMode) {
            moveWithSelection(oldCursor, newCursor);
        } else {
            // Empty lines are not skipped: they are announced as blank.
            if (word.word.length() == 0) {
                speak(R.string.blank);
            } else {
                speakDocument(word.word);
            }
        }
    }

    private void moveByParagraph(int direction) {
        int oldCursor = currentCursor();
        Word word = direction < 0
                ? EditingUtilities.moveToPreviousParagraph(listener)
                : EditingUtilities.moveToNextParagraph(listener);
        if (word == null) {
            return;
        }
        int newCursor = listener.getCursor();
        if (newCursor == oldCursor) {
            speak(direction < 0 ? R.string.start_of_text
                    : R.string.end_of_text);
        } else if (selectionMode) {
            moveWithSelection(oldCursor, newCursor);
        } else {
            speakDocument(word.word);
        }
    }

    // Move to the start or end of the text. When selection mode is active
    // the whole range from the anchor to the boundary is selected in one
    // operation, whichever side of the anchor the boundary lies on.
    private void moveToTextBoundary(int direction) {
        if (!selectionMode) {
            if (direction < 0) {
                EditingUtilities.moveToHome(listener);
                speak(R.string.start_of_text);
            } else {
                EditingUtilities.moveToEnd(listener);
                speak(R.string.end_of_text);
            }
            return;
        }
        if (anchor == -1) {
            anchor = listener.getCursor();
            cursor = anchor;
        }
        int dest = direction < 0 ? 0 : textLength();
        selectRangeTo(anchor, dest, direction);
    }

    // Move to the start or end of the current line. Empty lines are not
    // skipped: the cursor rests on the blank line and it is announced as
    // blank. In selection mode the range from the anchor to the boundary is
    // selected in one operation.
    private void moveToLineBoundary(int direction) {
        int oldCursor = currentCursor();
        Word word = direction < 0
                ? EditingUtilities.moveToStartOfLine(listener)
                : EditingUtilities.moveToEndOfLine(listener);
        if (word == null) {
            return;
        }
        int dest = listener.getCursor();
        if (selectionMode) {
            if (anchor == -1) {
                anchor = oldCursor;
                cursor = oldCursor;
            }
            selectRangeTo(anchor, dest, direction);
        } else if (word.word.length() == 0) {
            speak(R.string.blank);
        } else {
            speakDocument(word.word);
        }
    }

    // Establish the selection from the anchor to the destination in one
    // operation and announce it as selected (or as the boundary string when
    // nothing was selected).
    private void selectRangeTo(int from, int to, int direction) {
        cursor = to;
        int start = Math.min(from, to);
        int end = Math.max(from, to);
        listener.setSelection(start, end);
        String text = textOf(start, end);
        if (text.length() == 0) {
            speak(direction < 0 ? R.string.start_of_text
                    : R.string.end_of_text);
        } else {
            speakDocument(text + " "
                    + context.getString(R.string.command_selected_suffix));
        }
    }

    // Move the cursor (and the selection, when selection mode is active)
    // from oldCursor to newCursor, announcing the text that was just
    // selected or unselected.
    private void moveWithSelection(int oldCursor, int newCursor) {
        if (anchor == -1) {
            anchor = oldCursor;
        }
        cursor = newCursor;
        int oldStart = Math.min(anchor, oldCursor);
        int oldEnd = Math.max(anchor, oldCursor);
        int newStart = Math.min(anchor, newCursor);
        int newEnd = Math.max(anchor, newCursor);
        listener.setSelection(newStart, newEnd);
        String selected = context
                .getString(R.string.command_selected_suffix);
        String unselected = context
                .getString(R.string.command_unselected_suffix);
        if (newStart < oldStart) {
            speakDocument(textOf(newStart, oldStart) + " " + selected);
        } else if (newEnd > oldEnd) {
            speakDocument(textOf(oldEnd, newEnd) + " " + selected);
        } else if (newEnd < oldEnd) {
            speakDocument(textOf(newEnd, oldEnd) + " " + unselected);
        } else if (newStart > oldStart) {
            speakDocument(textOf(oldStart, newStart) + " " + unselected);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private int textLength() {
        ExtractedText text = listener.getAllText();
        return text != null && text.text != null ? text.text.length() : 0;
    }

    // The text between two positions (exclusive end), clamped to the field.
    private String textOf(int start, int end) {
        ExtractedText text = listener.getAllText();
        if (text == null || text.text == null || start >= end) {
            return "";
        }
        int length = text.text.length();
        start = Math.max(0, Math.min(start, length));
        end = Math.max(start, Math.min(end, length));
        return text.text.subSequence(start, end).toString();
    }

    // Speak a command message.
    private void speak(int stringRes) {
        speech.speak(context, context.getString(stringRes),
                Speech.QUEUE_FLUSH);
    }

    // Speak document text, obeying the password field echo rules.
    private void speakDocument(String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        speech.readConsiderPassword(context, "%s", text,
                listener.isPasswordField(), Speech.QUEUE_FLUSH);
    }
}
