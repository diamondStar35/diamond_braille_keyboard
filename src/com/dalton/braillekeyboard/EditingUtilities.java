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

import android.view.inputmethod.ExtractedText;

/**
 * A series of handy utilities for editing and manipulating text through an IME.
 * 
 */
public class EditingUtilities {
    public static final int MAX_WORD_LENGTH = 30;
    public static final int MAX_LINE_LENGTH = 1000;
    public static final String WORD_SEPARATORS = " \n\t";
    public static final String LINE_SEPARATOR = "\n";

    public static Word getBlock(String before, String after, String sep) {
        int start = before.length();
        int end = -1;
        // now work outwards until we find a separator
        while (start > 0 && !matchesSeparator(before.charAt(start - 1), sep)) {
            --start;
        }
        while (++end < after.length()
                && !matchesSeparator(after.charAt(end), sep)) {
        }

        return new Word(before.substring(start) + after.substring(0, end),
                before.length() - start, end);
    }

    private static Word skipSeparator(String before, String after, String sep,
            int maxSkip) {
        int start = before.length();
        int end = -1;
        // Make before[start] and after[end] point to characters that
        // are non-separators.
        int skip = 0;
        while ((maxSkip == -1 || skip < maxSkip) && start > 0
                && matchesSeparator(before.charAt(start - 1), sep)) {
            --start;
            ++skip;
        }

        skip = 0;
        while ((maxSkip == -1 || skip <= maxSkip) && ++end < after.length()
                && matchesSeparator(after.charAt(end), sep)) {
            ++skip;
        }
        return new Word("", before.length() - start, end);
    }

    private static Word getBlockForMovement(String before, String after,
            String sep, int maxSkip) {
        Word initialSpace = skipSeparator(before, after, sep, maxSkip);
        Word word = getBlock(
                before.substring(0, before.length() - initialSpace.charsBefore),
                after.substring(initialSpace.charsAfter), sep);
        Word spaceAfter = skipSeparator(
                before.substring(0, before.length() - initialSpace.charsBefore
                        - word.charsBefore),
                after.substring(initialSpace.charsAfter + word.charsAfter),
                sep, maxSkip);
        // Update the before and after parameters with the whitespace we skipped
        if (initialSpace.charsBefore > 0) {
            word.charsBefore += initialSpace.charsBefore;
        }
        if (initialSpace.charsAfter == 0) {
            word.charsAfter += spaceAfter.charsAfter;
        } else {
            word.charsAfter = initialSpace.charsAfter;
        }
        return word;
    }

    private static boolean matchesSeparator(char character, String sep) {
        for (int i = 0; i < sep.length(); i++) {
            if (sep.charAt(i) == character) {
                return true;
            }
        }
        return false;
    }

    public static Word moveToPreviousCharacter(KeyboardListener listener) {
        int cursor = listener.getCursor();
        if (cursor == -1) {
            return null;
        }
        Word word = new Word("", 0, 0);
        if (cursor > 0) {
            CharSequence text = listener.getTextBeforeCursor(1);
            if (text != null) {
                word = new Word(text.toString(), 1, 0);
                word.moveLeft = true;
            } else {
                return null;
            }
            listener.setSelection(cursor - 1);
        }
        return word;
    }

    public static Word moveToNextCharacter(KeyboardListener listener) {
        int cursor = listener.getCursor();
        CharSequence text = listener.getTextAfterCursor(2);
        if (cursor == -1 || text == null) {
            return null;
        }
        Word word = new Word("", 0, 0);

        if (text.length() > 1) {
            word = new Word(text.subSequence(1, text.length()).toString(), 0, 1);
            word.moveRight = true;
        }
        listener.setSelection(cursor + 1);
        return word;
    }

    private static Word moveToPrevious(KeyboardListener listener,
            String separator, int maxLength, int maxSkip) {
        int cursor = listener.getCursor();
        CharSequence before = listener.getTextBeforeCursor(maxLength);
        CharSequence after = listener.getTextAfterCursor(maxLength);
        if (cursor == -1 || before == null || after == null) {
            return null;
        }

        Word word = getBlockForMovement(before.toString(), after.toString(),
                separator, maxSkip);
        if (word.charsBefore == 0) {
            word.word = "";
        } else {
            listener.setSelection(cursor - word.charsBefore);
            after = listener.getTextAfterCursor(maxLength);
            word.word = getBlock("", after.toString(), separator).word;
            word.moveLeft = true;
        }
        return word;
    }

    private static Word moveToNext(KeyboardListener listener, String separator,
            int maxLength, int maxSkip) {
        int cursor = listener.getCursor();
        CharSequence before = listener.getTextBeforeCursor(maxLength);
        CharSequence after = listener.getTextAfterCursor(maxLength);
        if (cursor == -1 || after == null || before == null) {
            return null;
        }

        Word word = getBlockForMovement(before.toString(), after.toString(),
                separator, maxSkip);
        if (word.charsAfter == 0) {
            word.word = "";
        } else {
            listener.setSelection(cursor + word.charsAfter);
            after = listener.getTextAfterCursor(maxLength);
            word.word = getBlock("", after == null ? "" : after.toString(),
                    separator).word;

            if (after != null && after.length() > 0) {
                word.moveRight = true;
            }
        }

        return word;
    }

    public static Word moveToPreviousWord(KeyboardListener listener) {
        return moveToPrevious(listener, WORD_SEPARATORS, MAX_WORD_LENGTH, -1);
    }

    public static Word moveToNextWord(KeyboardListener listener) {
        return moveToNext(listener, WORD_SEPARATORS, MAX_WORD_LENGTH, -1);
    }

    public static Word moveToPreviousLine(KeyboardListener listener) {
        return moveToPrevious(listener, LINE_SEPARATOR, MAX_LINE_LENGTH, 1);
    }

    public static Word moveToNextLine(KeyboardListener listener) {
        return moveToNext(listener, LINE_SEPARATOR, MAX_LINE_LENGTH, 1);
    }

    public static Word moveToHome(KeyboardListener listener) {
        Word word = new Word("", 0, 0);
        listener.setSelection(0);
        return word;
    }

    public static Word moveToEnd(KeyboardListener listener) {
        ExtractedText extractedText = listener.getAllText();
        if (extractedText == null) {
            return null;
        }

        if (extractedText.text != null) {
            int end = extractedText.text.length() + extractedText.startOffset;
            listener.setSelection(end);
        }
        return new Word("", 0, 0);
    }

    // Move the cursor to the first letter of the current word: the word
    // whose letters end at or contain the cursor. If the cursor sits on a
    // separator, the current word is the next word ahead.
    public static Word moveToStartOfWord(KeyboardListener listener) {
        int cursor = listener.getCursor();
        CharSequence before = listener.getTextBeforeCursor(MAX_WORD_LENGTH);
        if (cursor < 0 || before == null) {
            return null;
        }
        int start = before.length();
        while (start > 0 && !isWordSeparator(before.charAt(start - 1))) {
            --start;
        }
        if (start == before.length()) {
            // The cursor is on a separator: move to the start of the next
            // word ahead, skipping any separators.
            CharSequence after = listener.getTextAfterCursor(MAX_WORD_LENGTH);
            if (after == null) {
                return null;
            }
            int forward = 0;
            while (forward < after.length()
                    && isWordSeparator(after.charAt(forward))) {
                ++forward;
            }
            if (forward >= after.length()) {
                Word word = new Word("", 0, 0);
                word.moveRight = false;
                return word; // no next word: end of text
            }
            int wordEnd = forward;
            while (wordEnd < after.length()
                    && !isWordSeparator(after.charAt(wordEnd))) {
                ++wordEnd;
            }
            listener.setSelection(cursor + forward);
            Word word = new Word(after.subSequence(forward, wordEnd)
                    .toString(), 0, forward);
            word.moveRight = true;
            return word;
        }
        listener.setSelection(cursor - (before.length() - start));
        Word word = new Word(before.subSequence(start, before.length())
                .toString(), before.length() - start, 0);
        word.moveLeft = true;
        return word;
    }

    // Move the cursor to immediately after the last letter of the current
    // word. If the cursor sits on a separator, the current word is the next
    // word ahead.
    public static Word moveToEndOfWord(KeyboardListener listener) {
        int cursor = listener.getCursor();
        CharSequence after = listener.getTextAfterCursor(MAX_WORD_LENGTH);
        if (cursor < 0 || after == null) {
            return null;
        }
        int end = 0;
        while (end < after.length() && !isWordSeparator(after.charAt(end))) {
            ++end;
        }
        if (end == 0) {
            // The cursor is on a separator: skip to the end of the next word.
            int forward = 0;
            while (forward < after.length()
                    && isWordSeparator(after.charAt(forward))) {
                ++forward;
            }
            if (forward >= after.length()) {
                Word word = new Word("", 0, 0);
                word.moveRight = false;
                return word; // no next word: end of text
            }
            int wordEnd = forward;
            while (wordEnd < after.length()
                    && !isWordSeparator(after.charAt(wordEnd))) {
                ++wordEnd;
            }
            listener.setSelection(cursor + wordEnd);
            Word word = new Word(after.subSequence(forward, wordEnd)
                    .toString(), 0, wordEnd);
            word.moveRight = true;
            return word;
        }
        CharSequence before = listener.getTextBeforeCursor(MAX_WORD_LENGTH);
        StringBuilder wordText = new StringBuilder();
        if (before != null) {
            int start = before.length();
            while (start > 0 && !isWordSeparator(before.charAt(start - 1))) {
                --start;
            }
            wordText.append(before.subSequence(start, before.length()));
        }
        wordText.append(after.subSequence(0, end));
        listener.setSelection(cursor + end);
        Word word = new Word(wordText.toString(), 0, end);
        word.moveRight = true;
        return word;
    }

    // Move the cursor to the first letter of the current line. Empty lines
    // are not skipped: the cursor rests on the blank line itself.
    public static Word moveToStartOfLine(KeyboardListener listener) {
        int cursor = listener.getCursor();
        CharSequence before = listener.getTextBeforeCursor(MAX_LINE_LENGTH);
        CharSequence after = listener.getTextAfterCursor(MAX_LINE_LENGTH);
        if (cursor < 0 || before == null || after == null) {
            return null;
        }
        int start = before.length();
        while (start > 0 && before.charAt(start - 1) != '\n') {
            --start;
        }
        listener.setSelection(cursor - (before.length() - start));
        Word word = new Word(lineText(before, start, after), 0, 0);
        word.moveLeft = true;
        return word;
    }

    // Move the cursor to immediately after the last letter of the current
    // line (before the line break, or at the end of the text).
    public static Word moveToEndOfLine(KeyboardListener listener) {
        int cursor = listener.getCursor();
        CharSequence before = listener.getTextBeforeCursor(MAX_LINE_LENGTH);
        CharSequence after = listener.getTextAfterCursor(MAX_LINE_LENGTH);
        if (cursor < 0 || before == null || after == null) {
            return null;
        }
        int end = 0;
        while (end < after.length() && after.charAt(end) != '\n') {
            ++end;
        }
        int start = before.length();
        while (start > 0 && before.charAt(start - 1) != '\n') {
            --start;
        }
        listener.setSelection(cursor + end);
        Word word = new Word(lineText(before, start, after), 0, end);
        word.moveRight = true;
        return word;
    }

    // Move to the start of the previous paragraph. Paragraphs are blocks of
    // one or more non-blank lines separated by blank lines. Returns a Word
    // describing the paragraph that was moved to; moveLeft is false when the
    // cursor is already at the start of the text.
    public static Word moveToPreviousParagraph(KeyboardListener listener) {
        ExtractedText extractedText = listener.getAllText();
        if (extractedText == null || extractedText.text == null) {
            return null;
        }
        String text = extractedText.text.toString();
        int cursor = listener.getCursor();
        if (cursor < 0) {
            return null;
        }
        String[] lines = text.split("\n", -1);
        int current = lineAt(text, cursor);
        int target = current;
        if (isBlankLine(lines[target])) {
            // The cursor is on a blank line: the run before the blank lines
            // is the previous paragraph.
            while (target > 0 && isBlankLine(lines[target])) {
                --target;
            }
            if (target == current) {
                Word word = new Word("", 0, 0);
                word.moveLeft = false;
                return word; // the whole text before is blank
            }
            while (target > 0 && !isBlankLine(lines[target - 1])) {
                --target;
            }
            return paragraphWord(listener, lines, target, cursor);
        }
        // The cursor is inside a paragraph: walk to its start, then across
        // the blank lines to the run before it.
        while (target > 0 && !isBlankLine(lines[target - 1])) {
            --target;
        }
        while (target > 0 && isBlankLine(lines[target - 1])) {
            --target;
        }
        while (target > 0 && !isBlankLine(lines[target - 1])) {
            --target;
        }
        if (target == current) {
            Word word = new Word("", 0, 0);
            word.moveLeft = false;
            return word; // no previous paragraph: start of text
        }
        return paragraphWord(listener, lines, target, cursor);
    }

    // Move to the start of the next paragraph. Paragraphs are blocks of one
    // or more non-blank lines separated by blank lines. Returns a Word
    // describing the paragraph that was moved to; moveRight is false when
    // the cursor is already at the end of the text.
    public static Word moveToNextParagraph(KeyboardListener listener) {
        ExtractedText extractedText = listener.getAllText();
        if (extractedText == null || extractedText.text == null) {
            return null;
        }
        String text = extractedText.text.toString();
        int cursor = listener.getCursor();
        if (cursor < 0) {
            return null;
        }
        String[] lines = text.split("\n", -1);
        int current = lineAt(text, cursor);
        int target = current;
        if (isBlankLine(lines[target])) {
            // Skip the blank lines the cursor is on; if anything non-blank
            // follows, it is the next paragraph.
            while (target < lines.length - 1 && isBlankLine(lines[target])) {
                ++target;
            }
            if (isBlankLine(lines[target])) {
                Word word = new Word("", 0, 0);
                word.moveRight = false;
                return word; // the rest of the text is blank
            }
        } else {
            // Walk to the end of the current run, across any blank lines, and
            // on to the start of the next run.
            while (target < lines.length - 1
                    && !isBlankLine(lines[target + 1])) {
                ++target;
            }
            while (target < lines.length - 1 && isBlankLine(lines[target + 1])) {
                ++target;
            }
            ++target;
            if (target >= lines.length) {
                Word word = new Word("", 0, 0);
                word.moveRight = false;
                return word; // no next paragraph: end of text
            }
        }
        return paragraphWord(listener, lines, target, cursor);
    }

    private static boolean isWordSeparator(char character) {
        return matchesSeparator(character, WORD_SEPARATORS);
    }

    /** True when the character separates words (space, newline, tab). */
    public static boolean isWordSeparatorChar(char character) {
        return isWordSeparator(character);
    }

    // The text of the line starting at index 'start' of 'before', extended
    // with the characters of 'after' up to the next line break.
    private static String lineText(CharSequence before, int start,
            CharSequence after) {
        StringBuilder line = new StringBuilder(
                before.subSequence(start, before.length()));
        if (after != null) {
            for (int i = 0; i < after.length() && after.charAt(i) != '\n'; i++) {
                line.append(after.charAt(i));
            }
        }
        return line.toString();
    }

    // A line is blank when it is empty or contains only whitespace.
    private static boolean isBlankLine(String line) {
        return line.trim().isEmpty();
    }

    // The index of the line (0 based) containing the given text position.
    private static int lineAt(String text, int position) {
        int line = 0;
        int limit = Math.min(position, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                ++line;
            }
        }
        return line;
    }

    // Build the Word describing a move to the start of the paragraph whose
    // first line is 'target': the paragraph text and the distance moved.
    private static Word paragraphWord(KeyboardListener listener,
            String[] lines, int target, int cursor) {
        int runEnd = target;
        while (runEnd < lines.length - 1 && !isBlankLine(lines[runEnd + 1])) {
            ++runEnd;
        }
        StringBuilder paragraph = new StringBuilder(lines[target]);
        for (int i = target + 1; i <= runEnd; i++) {
            paragraph.append('\n').append(lines[i]);
        }
        int dest = 0;
        for (int i = 0; i < target; i++) {
            dest += lines[i].length() + 1;
        }
        listener.setSelection(dest);
        Word word = new Word(paragraph.toString(), cursor - dest, 0);
        word.moveLeft = true;
        return word;
    }

    public static String getCharacter(KeyboardListener listener) {
        CharSequence text = listener.getTextAfterCursor(1);
        return text == null ? null : text.toString();
    }

    public static Word getWord(KeyboardListener listener) {
        CharSequence before = listener.getTextBeforeCursor(MAX_WORD_LENGTH);
        CharSequence after = listener.getTextAfterCursor(MAX_WORD_LENGTH);

        if (after != null && before != null) {
            return getBlock(before.toString(), after.toString(),
                    WORD_SEPARATORS);
        }
        return null;
    }

    public static Word getLine(KeyboardListener listener) {
        CharSequence before = listener.getTextBeforeCursor(MAX_LINE_LENGTH);
        CharSequence after = listener.getTextAfterCursor(MAX_LINE_LENGTH);
        if (before != null && after != null) {
            return getBlock(before.toString(), after.toString(), LINE_SEPARATOR);
        }
        return null;
    }

    public static String getAllText(KeyboardListener listener) {
        String text = null;
        ExtractedText extractedText = listener.getAllText();
        if (extractedText != null) {
            if (extractedText.text != null) {
                text = extractedText.text.toString();
            }
        }
        return text;
    }

    public static Word skipSepBackwards(KeyboardListener listener, String sep) {
        CharSequence after = "";
        CharSequence before = listener.getTextBeforeCursor(MAX_LINE_LENGTH);
        if (after != null && before != null) {
            Word word = skipSeparator(before.toString(), after.toString(), sep,
                    -1);
            int cursor = listener.getCursor();

            if (cursor > 0) {
                listener.setSelection(cursor - word.charsBefore);
                return word;
            }
        }
        return null;
    }

    public static class Word {
        public int charsAfter;
        public int charsBefore;
        public String word;
        public boolean moveLeft;
        public boolean moveRight;

        public Word(String word, int charsBefore, int charsAfter) {
            this.word = word;
            this.charsBefore = charsBefore;
            this.charsAfter = charsAfter;
        }
    }

    public static int characterCount(CharSequence text) {
        return text != null ? text.length() : 0;
    }

    public static int wordCount(CharSequence text) {
        if (characterCount(text) == 0) {
            return 0;
        }
        return text.toString().split("\\s+").length;
    }

    public static int lineCount(CharSequence text) {
        if (characterCount(text) == 0) {
            return 0;
        }
        return text.toString().split("\\n").length;
    }
}
