package com.dalton.braillekeyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.googlecode.eyesfree.braille.translate.TableInfo;

/**
 * Owns the composing buffer of the IME: the Braille cells that make up the
 * text currently being composed, the composing text itself, the caps state
 * and the prediction flag, together with the logic that back-translates cells
 * and writes the resulting text into the editor.
 *
 * <p>Extracted from {@link BrailleIME} so the IME service is left with the
 * service lifecycle, selection and input concerns while all composing logic
 * lives here. The {@link Host} interface exposes the editor state the
 * composer needs from the surrounding IME.
 */
public class TextComposer {

    /** Provides the editor state the composer needs from the IME. */
    public interface Host {
        InputConnection getCurrentInputConnection();

        EditorInfo getCurrentInputEditorInfo();

        Context getContext();

        /** True while the IME has a whole-text selection active. */
        boolean isSelectAll();

        /** Clear the whole-text selection (mark and select-all flag). */
        void clearSelectAll();
    }

    private final Host host;

    private final List<Byte> cells = new ArrayList<Byte>();
    private final StringBuilder composingText = new StringBuilder();

    private int caps;
    private boolean predictionOn;

    public TextComposer(Host host) {
        this.host = host;
    }

    /** Set whether the editor is in composing (prediction) mode. */
    public void setPredictionOn(boolean predictionOn) {
        this.predictionOn = predictionOn;
    }

    /**
     * Back-translate the given dot pattern together with the cells typed so
     * far, write the result into the editor and return the new text that the
     * user should be told about (or {@code null} if it could not be
     * translated).
     *
     * @param parser The Braille translator to use.
     * @param dots The dot pattern that was just typed.
     * @return The difference between the old and new composing text.
     */
    public String handleTypedCharacter(Parser parser, byte dots) {
        if (parser != null) {
            // If we are in number mode (the buffer contains the number sign,
            // dots 3-4-5-6 = 60) and the user typed Dot 6 (32), which acts as
            // a capital sign, break out of number mode so the capital sign
            // takes effect.  This behaviour is specific to English (and
            // potentially others where Dot 6 is the capital sign). For other
            // languages the capital sign might be different (e.g. dots 4-6)
            // or Dot 6 might have a different meaning in number mode.
            TableInfo table = parser.getTable(host.getContext());
            Locale locale = table != null ? table.getLocale() : null;
            boolean isEnglish = locale != null
                    && locale.getLanguage().equals(
                            Locale.ENGLISH.getLanguage());
            if (isEnglish && dots == 32 && cells.contains((byte) 60)) {
                finishComposingText(true);
            }

            String oldText = composingText.toString();
            setCells(dots);
            String text = parser.backTranslate(host.getContext(),
                    cells.toArray(new Byte[cells.size()]));
            if (text != null) {
                text = compose(text.subSequence(0, text.length()));
            } else { // unable to translate this byte string
                cells.remove(cells.size() - 1);
                return null;
            }

            // Return the update to the input field to be read to the user.
            return text != null ? stringDifference(oldText, text) : null;
        }
        return null;
    }

    /**
     * Back-translate the given dot pattern in isolation, without committing
     * anything to the editor or changing the composing buffer.
     *
     * @param parser The Braille translator to use.
     * @param dots The dot pattern to translate.
     * @return The translated text, or {@code null} if it could not be
     *         translated.
     */
    public String translateOnly(Parser parser, byte dots) {
        if (parser != null) {
            setCells(dots);
            String text = parser.backTranslate(host.getContext(),
                    cells.toArray(new Byte[cells.size()]));
            cells.remove(cells.size() - 1);
            return text;
        }
        return null;
    }

    /** Commit and clear the composing buffer. */
    public void finishComposingText() {
        finishComposingText(true);
    }

    /**
     * Clear the composing buffer, optionally committing the composing text to
     * the editor first when in prediction mode.
     *
     * @param commit Whether the composing text should be committed.
     */
    public void finishComposingText(boolean commit) {
        InputConnection ic = host.getCurrentInputConnection();
        if (ic == null) {
            composingText.setLength(0);
            cells.clear();
            return;
        }
        if (composingText.length() > 0) {
            if (predictionOn && commit) {
                ic.commitText(composingText, 1);
            }
            composingText.setLength(0);
        }
        cells.clear();
    }

    /**
     * Capitalise the text if auto-caps is enabled and the IME told us to
     * capitalise this first character.
     */
    public CharSequence capitalise(CharSequence text) {
        if (Options.getBooleanPreference(
                host.getContext(),
                R.string.pref_auto_caps_key,
                Boolean.parseBoolean(host.getContext().getString(
                        R.string.pref_auto_caps_default)))) {
            if (caps != 0 && text != null) {
                if (text.length() > 0) {
                    text = String
                            .valueOf(Character.toUpperCase(text.charAt(0)))
                            + text.subSequence(1, text.length());
                }
            }
        }
        return text;
    }

    /** Refresh the caps mode from the current editor. */
    public void updateShiftState() {
        caps = 0;
        EditorInfo editorInfo = host.getCurrentInputEditorInfo();
        if (editorInfo != null && editorInfo.inputType != InputType.TYPE_NULL) {
            InputConnection ic = host.getCurrentInputConnection();
            if (ic != null) {
                caps = ic.getCursorCapsMode(editorInfo.inputType);
            }
        }
    }

    private String compose(CharSequence text) {
        if (composingText.length() == 0) {
            updateShiftState(); // auto-caps
        }

        InputConnection ic = host.getCurrentInputConnection();
        if (ic == null) {
            return text.toString();
        }
        if (host.isSelectAll()) {
            host.clearSelectAll();
        }

        // Braille is context specific and the text previously can change as
        // the user adds more Braille patterns.
        // First make sure the new text gets capitalised in the appropriate way
        // according to auto-capitalisation rules.
        text = capitalise(text);

        ic.beginBatchEdit();
        try {
            if (predictionOn) {
                // we can use composing text capabilities of android to make
                // life easy and efficient here.
                composingText.setLength(0);
                composingText.append(text);
                ic.setComposingText(composingText.toString(),
                        composingText.length());
            } else if (text.length() > 0) {
                // Differential update: compare the previously written text
                // (composingText) with the new text and only commit the
                // difference.  This avoids duplication bugs in apps that don't
                // handle composing spans or delete/replace operations
                // correctly.
                String prev = composingText.toString();
                String curr = text.toString();

                // Re-sync mechanism: if composingText is empty/out-of-sync but
                // the editor already contains the prefix of what we are about
                // to write, trust the editor.
                CharSequence beforeCursor = ic.getTextBeforeCursor(
                        curr.length(), 0);
                if (beforeCursor != null && beforeCursor.length() > 0
                        && curr.startsWith(beforeCursor.toString())) {
                    // The editor already has the prefix! Use that as 'prev' to
                    // avoid duplicating it.
                    prev = beforeCursor.toString();
                }

                int commonLen = 0;
                while (commonLen < prev.length()
                        && commonLen < curr.length()
                        && prev.charAt(commonLen) == curr.charAt(commonLen)) {
                    commonLen++;
                }

                // 1. Delete the mismatching tail of the previous text.
                int deleteCount = prev.length() - commonLen;
                if (deleteCount > 0) {
                    ic.deleteSurroundingText(deleteCount, 0);
                }

                // 2. Append the new text (the suffix), one character at a time
                //    so fields doing validation don't misbehave.
                if (commonLen < curr.length()) {
                    CharSequence appendText = curr.subSequence(commonLen,
                            curr.length());
                    for (int i = 0; i < appendText.length(); i++) {
                        ic.commitText(appendText.subSequence(i, i + 1), 1);
                    }
                }

                composingText.setLength(0);
                composingText.append(text);
            }
        } finally {
            ic.endBatchEdit();
        }

        // return the new text we wrote if any.
        return text.toString();
    }

    private void setCells(byte dots) {
        if (cells.size() == 0) {
            cells.add((byte) 0);
        }
        cells.add(dots);
    }

    /**
     * Return the difference between two strings so that the user knows what
     * change occurred to the input. If str2 is completely unique to str1 then
     * return the entire string as the input has totally changed. Otherwise
     * return from the point of difference to end of str2 which represents the
     * new text that the user should know about.
     *
     * @param str1 The old text.
     * @param str2 The new text.
     */
    public static String stringDifference(String str1, String str2) {
        int i = -1;
        while (++i < Math.min(str1.length(), str2.length())
                && Character.toLowerCase(str1.charAt(i)) == Character
                        .toLowerCase(str2.charAt(i))) {
        }
        return i >= str2.length() ? str2 : str2.substring(i, str2.length());
    }
}
