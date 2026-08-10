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
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;

/**
 * Wraps the platform spell checker service behind a single persistent
 * {@link SpellCheckerSession} that the keyboard shares for the whole input
 * session.
 *
 * <p>One session is shared by the word-level typing check
 * ({@link #checkWord}) and the sentence-level text check
 * ({@link #checkSpelling}). The session is deliberately persistent: creating
 * a session per query is neither necessary nor desirable. To keep it
 * reliable it is managed: {@link #ensureSpellCheckerSession()} verifies the
 * session exists and is still connected before every query, and replaces it
 * when the spell checker service process has died or restarted, so a dead
 * session can never silently swallow a request.
 *
 * <p>Note that on some devices the spell checker service process is frozen
 * by aggressive battery optimisation shortly after each bind (the connection
 * stays up, so this is not detected as a disconnection); users of such
 * devices should exempt the spell checker's hosting app (e.g. Gboard) from
 * background freezing in the system battery settings.
 */
public class SpellChecker {
    private static final int MAX_SUGGESTIONS = 8;
    private static final int MAX_SENTENCE_LENGTH = 200;
    private static final int SENTENCES_TO_CONSIDER = 6;

    public interface SpellingSuggestionsReadyListener {
        void suggestionsReady(Suggestion result);
    }

    public enum Direction {
        LEFT, UNDER_CURSOR, RIGHT;
    }

    private final SpellCheckerSessionListener spellCheckerListener = new SpellCheckerSessionListener() {
        @Override
        public void onGetSuggestions(final SuggestionsInfo[] arg0) {
            // Result of a word-level getSuggestions() query (see checkWord).
            if (arg0 != null && arg0.length > 0 && wordListener != null
                    && wordText != null) {
                SuggestionsInfo info = arg0[0];
                int attributes = info.getSuggestionsAttributes();
                int count = info.getSuggestionsCount();
                Suggestion suggestion = null;
                // A word the checker did not flag as in the dictionary is
                // treated as misspelled. Some services report a typo with the
                // RESULT_ATTR_LOOKS_LIKE_TYPO attribute, others with no
                // attribute at all; requiring the dictionary bit to be clear
                // covers both without depending on the suggestion count.
                if ((attributes & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) == 0
                        && isPotentialWord(wordText)) {
                    suggestion = new Suggestion(wordText, wordOffset);
                    for (int i = 0; i < count; i++) {
                        suggestion.results.add(info.getSuggestionAt(i));
                    }
                }
                SpellingSuggestionsReadyListener listener = wordListener;
                wordListener = null;
                listener.suggestionsReady(suggestion);
            }
        }

        /**
         * Callback for
         * {@link SpellCheckerSession#getSentenceSuggestions(TextInfo[], int)}
         *
         * @param results
         *            an array of {@link SentenceSuggestionsInfo}s. These
         *            results are suggestions for {@link TextInfo}s queried by
         *            {@link SpellCheckerSession#getSentenceSuggestions(TextInfo[], int)}
         *            .
         */
        @Override
        @SuppressLint("NewApi")
        public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] arg0) {
            // The framework returns one SentenceSuggestionsInfo per TextInfo
            // queried, but third-party spell checker services have been known
            // to return null, empty or extra entries. Tolerate those instead
            // of throwing: an exception on the checker's callback thread
            // (swallowed or not) would otherwise prevent every later
            // misspelling announcement.
            SentenceSuggestionsInfo ssi = (arg0 != null && arg0.length > 0)
                    ? arg0[0] : null;
            Suggestion results = null;
            if (ssi != null) {
                int start = direction == Direction.LEFT ? ssi
                        .getSuggestionsCount() - 1 : 0;
                int end = direction == Direction.LEFT ? -1 : ssi
                        .getSuggestionsCount();
                int step = end < start ? -1 : 1;
                int i = start;
                while (i != end && results == null) {
                    int offset = ssi.getOffsetAt(i) + startOffset;
                    int length = ssi.getLengthAt(i);
                    boolean matched = isDirection(direction, cursor, length,
                            offset);
                    if (matched) {
                        results = compileSuggestions(
                                ssi.getSuggestionsInfoAt(i), length, offset);
                    }
                    i += step;
                }
            }

            boolean moreToExpand = expandOffsets(true);
            if (results != null || !moreToExpand) {
                listener.suggestionsReady(results);
            } else {
                doSpellCheck();
            }
        }
    };

    private final Context context;
    private SpellCheckerSession spellCheckerSession;
    private SpellingSuggestionsReadyListener listener;
    private int cursor;
    private Direction direction;
    private String text;
    private int startOffset;
    private int endOffset;
    // State for the word-level getSuggestions() path (see checkWord).
    private SpellingSuggestionsReadyListener wordListener;
    private String wordText;
    private int wordOffset;

    public SpellChecker(Context context) {
        this.context = context;
        ensureSpellCheckerSession();
    }

    /**
     * Make sure the spell checker session is alive before a query is sent,
     * creating a new one if it is missing or its connection to the spell
     * checker service has been lost (for example when the service process
     * was killed or restarted by the system).
     *
     * <p>This is checked before every request so that a dead session can
     * never silently swallow a query. The session is otherwise persistent
     * for the whole input session; the underlying service binding is reused
     * across queries.
     */
    private synchronized void ensureSpellCheckerSession() {
        if (spellCheckerSession == null
                || spellCheckerSession.isSessionDisconnected()) {
            if (spellCheckerSession != null) {
                spellCheckerSession.close();
                spellCheckerSession = null;
            }
            final TextServicesManager tsm = (TextServicesManager) context
                    .getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
            if (tsm != null) {
                spellCheckerSession = tsm.newSpellCheckerSession(null, null,
                        spellCheckerListener, true);
            }
        }
    }

    public boolean checkSpelling(SpellingSuggestionsReadyListener listener,
            String text, int cursor, Direction direction) {
        if (isSpellCheckAvailable() && text.length() > 0) {
            this.cursor = cursor;
            this.direction = direction;
            this.text = text;
            this.listener = listener;
            initOffsets();
            doSpellCheck();
            return true;
        } else {
            return false;
        }
    }

    @SuppressLint("NewApi")
    private void doSpellCheck() {
        ensureSpellCheckerSession();
        if (spellCheckerSession == null) {
            listener.suggestionsReady(null);
            return;
        }
        spellCheckerSession.cancel();

        // Append a space (" ") to the input string to the spelling checker.
        // This resolves some edge cases like a word followed by a period
        // without a following space.
        String submitted = text.substring(startOffset, endOffset) + " ";
        spellCheckerSession.getSentenceSuggestions(
                new TextInfo[] { new TextInfo(submitted) }, MAX_SUGGESTIONS);
    }

    /**
     * Check a single word directly with the platform's word-level spell
     * checker ({@link SpellCheckerSession#getSuggestions}). This is the API
     * that keyboard apps actually use; the sentence-level API
     * ({@code getSentenceSuggestions}) is not answered by every spell checker
     * service on every device.
     *
     * @param listener Receives the {@link Suggestion} when the word is
     *            misspelled, or {@code null} when it is spelled correctly.
     * @param word The word to check.
     * @param offset The position of the word in the current text.
     * @return true if the query was sent, false if the word is empty or no
     *         spell checker session could be created.
     */
    public boolean checkWord(SpellingSuggestionsReadyListener listener,
            String word, int offset) {
        if (word.length() == 0) {
            return false;
        }
        ensureSpellCheckerSession();
        if (spellCheckerSession == null) {
            return false;
        }
        this.wordListener = listener;
        this.wordText = word;
        this.wordOffset = offset;
        spellCheckerSession.getSuggestions(new TextInfo(word), MAX_SUGGESTIONS);
        return true;
    }

    public void destroy() {
        if (spellCheckerSession != null) {
            spellCheckerSession.close();
            spellCheckerSession = null;
        }
    }

    public boolean isSpellCheckAvailable() {
        return spellCheckerSession != null;
    }

    private Suggestion compileSuggestions(SuggestionsInfo suggestionInfo,
            int length, int offset) {
        if (suggestionInfo.getSuggestionsAttributes() == SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY
                || !isPotentialWord(text.substring(offset, offset + length))) {
            return null;
        }

        Suggestion suggestion = new Suggestion(text.substring(offset, length
                + offset), offset);
        for (int i = 0; i < suggestionInfo.getSuggestionsCount(); i++) {
            suggestion.results.add(suggestionInfo.getSuggestionAt(i));
        }

        return suggestion;
    }

    private static boolean isDirection(Direction direction, int cursor,
            int length, int offset) {
        switch (direction) {
        case UNDER_CURSOR:
            return cursor >= offset && cursor < (length + offset);
        case LEFT:
            return (length + offset) <= cursor;
        case RIGHT:
            return offset > cursor;
        default:
            throw new IllegalArgumentException(
                    "No implementation for direction = " + direction);
        }
    }

    private static boolean isPotentialWord(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void initOffsets() {
        startOffset = Math.max(0, cursor - MAX_SENTENCE_LENGTH);
        endOffset = Math.min(cursor + MAX_SENTENCE_LENGTH, text.length());
        expandOffsets(false);
    }

    private boolean expandOffsets(boolean shrinkVisitedRegion) {
        int tempOffset;
        boolean expanded;
        switch (direction) {
        case UNDER_CURSOR:
            return false;
        case LEFT:
            tempOffset = Math.max(0, startOffset - MAX_SENTENCE_LENGTH
                    * SENTENCES_TO_CONSIDER);
            expanded = startOffset != tempOffset;
            if (shrinkVisitedRegion) {
                endOffset = startOffset;
            }
            startOffset = tempOffset;
            break;
        case RIGHT:
            tempOffset = Math.min(text.length(), endOffset
                    + MAX_SENTENCE_LENGTH * SENTENCES_TO_CONSIDER);
            expanded = endOffset != tempOffset;
            if (shrinkVisitedRegion) {
                startOffset = endOffset;
            }
            endOffset = tempOffset;
            break;
        default:
            throw new IllegalArgumentException("No implementation for: "
                    + direction);
        }

        if (expanded) {
            normaliseOffsets();
        }
        return expanded;
    }

    private void normaliseOffsets() {
        for (int i = startOffset; i >= 0; i--) {
            startOffset = i;
            if (Character.isWhitespace(text.charAt(i))) {
                break;
            }
        }

        for (int i = endOffset; i < text.length(); i++) {
            endOffset = i;
            if (Character.isWhitespace(text.charAt(i))) {
                break;
            }
        }
    }

    public static class Suggestion {
        public final List<String> results = new ArrayList<String>();
        public final int offset;
        private int current = 0;
        private int length = -1;

        public Suggestion(String word, int offset) {
            this.offset = offset;
            results.add(word);
        }

        public String next() {
            current = ++current >= results.size() ? 0 : current;
            return results.get(current);
        }

        public String prev() {
            current = --current < 0 ? results.size() - 1 : current;
            return results.get(current);
        }

        public String getCurrent() {
            return results.get(current);
        }

        public void setLength() {
            length = results.get(current).length();
        }

        public int getLength() {
            return length == -1 ? results.get(0).length() : length;
        }

        public boolean isMisspelledWord() {
            return current == 0;
        }
    }
}
