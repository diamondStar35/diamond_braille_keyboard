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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.dalton.braillekeyboard.ActionHandler.OnActionListener;
import com.dalton.braillekeyboard.SpellChecker.SpellingSuggestionsReadyListener;
import com.dalton.braillekeyboard.SpellChecker.Suggestion;

/**
 * Owns the spell-checking state machine behind the keyboard: checking the
 * current text for misspellings, stepping forwards and backwards through the
 * suggestions and applying corrections.
 *
 * <p>Extracted from {@link EditingController} so spelling concerns are
 * separate from the other editing operations. It talks to the editor
 * exclusively through a {@link KeyboardListener}, reads the current text
 * through a {@link TextProvider} and delivers results through the
 * {@link ActionHandler.OnActionListener} callback.
 */
public class SpellCheckController {

    /** Provides the whole current text of the editor to be checked. */
    public interface TextProvider {
        String getCurrentText();
    }

    private final SpellChecker spellChecker;
    private KeyboardListener listener;
    private OnActionListener callback;
    private TextProvider textProvider;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int directionThroughSuggestionList;
    private SpellChecker.Direction spellingDirection;
    private Suggestion spellingSuggestion;

    /**
     * Create a SpellCheckController for the current input session. The
     * {@link #setListener(KeyboardListener)},
     * {@link #setCallback(OnActionListener)} and
     * {@link #setTextProvider(TextProvider)} must be called before any spell
     * check is performed.
     *
     * @param context
     *            The application context.
     */
    public SpellCheckController(Context context) {
        spellChecker = new SpellChecker(context);
    }

    /** Set the KeyboardListener to edit text through. */
    public void setListener(KeyboardListener listener) {
        this.listener = listener;
    }

    /** Set the OnActionListener to deliver results to. */
    public void setCallback(OnActionListener callback) {
        this.callback = callback;
    }

    /** Set the provider of the whole current text of the editor. */
    public void setTextProvider(TextProvider textProvider) {
        this.textProvider = textProvider;
    }

    /** Releases system resources. Call when the input session ends. */
    public void destroy() {
        spellChecker.destroy();
    }

    /** Whether the platform supports spell checking at all. */
    public boolean isSpellCheckAvailable() {
        return spellChecker.isSpellCheckAvailable();
    }

    /**
     * Check the spelling of the whole current text and speak the result: the
     * first misspelling in the given direction, or a message when the text is
     * clean.
     */
    public void doSpellCheck(final Context context,
            SpellChecker.Direction direction, int move, int cursor) {
        SpellingSuggestionsReadyListener spellingListener = new SpellingSuggestionsReadyListener() {

            @Override
            public void suggestionsReady(Suggestion result) {
                spellingSuggestion = result;
                if (result != null
                        || spellingDirection == SpellChecker.Direction.UNDER_CURSOR) {
                    handleSpellingSuggestion(context);
                } else {
                    callback.onText("%s",
                            context.getString(R.string.no_more_misspellings),
                            false);
                }
            }
        };

        String text = textProvider.getCurrentText();
        spellingDirection = direction;
        directionThroughSuggestionList = move;
        if (text != null && text.length() > 0) {
            if (!spellChecker.checkSpelling(spellingListener, text, cursor,
                    direction)) {
                callback.onText("%s",
                        context.getString(R.string.spellcheck_not_supported),
                        false);
            }
        } else {
            callback.onText("%s", context.getString(R.string.blank), false);
        }
    }

    /**
     * Check the word that was just typed and announce (and vibrate for) a
     * misspelling. Unlike {@link #doSpellCheck} this queries the word
     * directly with the platform's word-level spell checker, which every
     * spell checker service answers (the sentence-level API is not answered
     * by all services on all devices).
     *
     * @param context The application context.
     * @param word The word that was just typed.
     * @param offset The position of the word in the current text.
     */
    public void checkTypedWord(final Context context, final String word,
            final int offset) {
        if (!spellChecker.checkWord(new SpellingSuggestionsReadyListener() {
            @Override
            public void suggestionsReady(Suggestion result) {
                spellingSuggestion = result;
                if (result != null) {
                    spellingDirection = SpellChecker.Direction.UNDER_CURSOR;
                    directionThroughSuggestionList = 0;
                    handleSpellingSuggestion(context);
                }
            }
        }, word, offset)) {
            // No spell checker is available; nothing to do.
        }
    }

    /** Move to the next spelling suggestion for the word under the cursor. */
    public void nextSuggestion(Context context) {
        if (spellingSuggestion != null) {
            if (spellCheckerMatchesWord()) {
                spellingSuggestion.next();
                handleSpellingSuggestion(context);
                return;
            }
        }
        doSpellCheck(context, SpellChecker.Direction.UNDER_CURSOR, 1,
                listener.getCursor());
    }

    /** Move to the previous spelling suggestion for the word under the cursor. */
    public void previousSuggestion(Context context) {
        if (spellingSuggestion != null) {
            if (spellCheckerMatchesWord()) {
                spellingSuggestion.prev();
                handleSpellingSuggestion(context);
                return;
            }
        }
        doSpellCheck(context, SpellChecker.Direction.UNDER_CURSOR, -1,
                listener.getCursor());
    }

    private void handleSpellingSuggestion(Context context) {
        boolean password = false;
        String message = null;
        boolean misspellingAnnounced = false;

        if (spellingSuggestion == null && directionThroughSuggestionList != 0) {
            message = context.getString(R.string.word_correct);
        } else if (spellingSuggestion != null
                && spellingDirection == SpellChecker.Direction.UNDER_CURSOR) {
            spellingDirection = null;
            if (directionThroughSuggestionList > 0) {
                nextSuggestion(context);
            } else if (directionThroughSuggestionList < 0) {
                previousSuggestion(context);
            } else {
                message = context.getString(R.string.word_misspelled);
                misspellingAnnounced = true;
            }
        } else if (spellingSuggestion != null) {
            password = true;
            message = spellingSuggestion.isMisspelledWord() ? String.format(
                    context.getString(R.string.word_correction_misspelled),
                    spellingSuggestion.getCurrent()) : spellingSuggestion
                    .getCurrent();
            listener.setSelection(spellingSuggestion.offset);
            listener.deleteSurroundingText(0, spellingSuggestion.getLength());
            listener.commitText(spellingSuggestion.getCurrent(), 1);
            listener.setSelection(spellingSuggestion.offset);
            spellingSuggestion.setLength();
        }

        if (message != null) {
            callback.onText("%s", message, listener.isPasswordField()
                    && password, Speech.QUEUE_ADD);
        }

        if (misspellingAnnounced) {
            // The spell checker can deliver its results on a background
            // thread; emit the haptic on the main thread, after the
            // announcement, so it can never delay or drop the speech above.
            final OnActionListener cb = callback;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    cb.onNotify(FeedbackEvent.MISSPELLING);
                }
            });
        }
    }

    private boolean spellCheckerMatchesWord() {
        String text = textProvider.getCurrentText();
        if (text != null && spellingSuggestion != null) {
            int offset = spellingSuggestion.offset;
            int length = spellingSuggestion.getLength();
            int cursor = listener.getCursor();
            if (text.length() > 0 && (offset + length) <= text.length()
                    && cursor >= offset && cursor < (offset + length)) {
                return text.substring(offset, offset + length).equals(
                        spellingSuggestion.getCurrent());
            }
        }
        return false;
    }
}
