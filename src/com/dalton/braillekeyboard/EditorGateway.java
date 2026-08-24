/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
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
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;

/**
 * The single access point to the editor's {@link InputConnection}.
 *
 * <p>Every read/write issued by the keyboard goes through here, which buys
 * two things:
 *
 * <ul>
 * <li><b>Selection tracking.</b> The framework reports every cursor movement
 * (ours and the app's) through
 * {@link #onSelectionChanged(int, int)}, fed by
 * {@code InputMethodService.onUpdateSelection}. Writes made through this
 * gateway adjust the tracked position immediately. Code that needs the
 * cursor therefore avoids the expensive round-trip of pulling the entire
 * document through {@code getExtractedText}, which previously ran several
 * times per gesture and made editing latency scale with document size.
 * <li><b>A consistent view.</b> Wrapped connections adjust the tracking on
 * their way through, so callers that hold an {@link InputConnection} (the
 * composing logic, batch edits) stay accounted for too.
 * </ul>
 *
 * <p>The tracking is deliberately conservative: anything that cannot be
 * accounted for precisely (composing spans, external bulk changes) marks the
 * position unknown, and the next query falls back to asking the editor -
 * exactly what happened before this class existed.
 */
final class EditorGateway {

    /** Value of the tracked cursor while it is not known. */
    static final int UNKNOWN = -1;

    private final Context context;
    // Set from BrailleIME when diagnostic logging is on, so the trace below
    // costs one boolean check per edit instead of building strings always.
    private boolean traceEnabled;

    private InputConnection wrapped;
    private InputConnection wrappedFrom;

    private int cursor = UNKNOWN;
    private int selectionStart = UNKNOWN;
    private int selectionEnd = UNKNOWN;

    EditorGateway(Context context) {
        this.context = context;
    }

    /** Enables/disables the editor-operation trace for problem reports. */
    void setTraceEnabled(boolean enabled) {
        traceEnabled = enabled;
    }

    private void trace(String what) {
        if (traceEnabled) {
            Diagnostics.log(context, what);
        }
    }

    /**
     * Feed the latest selection reported by the editor (from
     * {@code onUpdateSelection}). A collapsed selection pins down the
     * cursor; a ranged one leaves the last known insertion point alone,
     * matching how {@link KeyboardListener#getCursor()} has always behaved
     * while text is selected.
     */
    void onSelectionChanged(int newStart, int newEnd) {
        selectionStart = newStart;
        selectionEnd = newEnd;
        if (newStart >= 0 && newStart == newEnd) {
            cursor = newStart;
        }
    }

    /** Drops the tracked position; the next query asks the editor again. */
    void invalidate() {
        cursor = UNKNOWN;
        selectionStart = UNKNOWN;
        selectionEnd = UNKNOWN;
        wrapped = null;
        wrappedFrom = null;
    }

    /** The best known cursor position, asking the editor only if needed. */
    int getCursor(InputConnection ic) {
        if (cursor != UNKNOWN || ic == null) {
            return cursor;
        }
        trace("ic:cursor pull (tracking missed)");
        ExtractedText extracted = ic.getExtractedText(new ExtractedTextRequest(),
                0);
        if (extracted == null) {
            return UNKNOWN;
        }
        if (extracted.selectionStart == extracted.selectionEnd) {
            cursor = extracted.startOffset + extracted.selectionStart;
        }
        return cursor;
    }

    /**
     * Returns an {@link InputConnection} that mirrors every mutating call
     * into the tracking state. The wrapper is reused while the underlying
     * connection does not change.
     */
    InputConnection track(InputConnection ic) {
        if (ic == null) {
            return null;
        }
        if (wrapped != null && wrappedFrom == ic) {
            return wrapped;
        }
        wrapped = new TrackingConnection(ic);
        wrappedFrom = ic;
        return wrapped;
    }

    private void onCommitted(CharSequence text) {
        if (text != null && cursor != UNKNOWN) {
            cursor += text.length();
        }
    }

    private void onDeleted(int before, int after) {
        if (cursor != UNKNOWN) {
            if (before <= cursor) {
                cursor -= before;
            } else {
                cursor = 0;
            }
        }
    }

    private void onSelected(int position) {
        cursor = position;
        selectionStart = position;
        selectionEnd = position;
    }

    /**
     * Forwards every call to the real connection and adjusts the tracked
     * cursor for the calls that move it. Composing-specific calls mark the
     * position unknown because their net cursor effect depends on the
     * editor's composing span handling.
     */
    private final class TrackingConnection implements InputConnection {
        private final InputConnection delegate;

        TrackingConnection(InputConnection delegate) {
            this.delegate = delegate;
        }

        @Override
        public CharSequence getTextBeforeCursor(int n, int flags) {
            return delegate.getTextBeforeCursor(n, flags);
        }

        @Override
        public CharSequence getTextAfterCursor(int n, int flags) {
            return delegate.getTextAfterCursor(n, flags);
        }

        @Override
        public CharSequence getSelectedText(int flags) {
            return delegate.getSelectedText(flags);
        }

        @Override
        public int getCursorCapsMode(int reqModes) {
            return delegate.getCursorCapsMode(reqModes);
        }

        @Override
        public ExtractedText getExtractedText(ExtractedTextRequest request,
                int flags) {
            return delegate.getExtractedText(request, flags);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength,
                int afterLength) {
            trace("ic:del(" + beforeLength + ',' + afterLength + ')');
            boolean ok = delegate.deleteSurroundingText(beforeLength,
                    afterLength);
            if (ok) {
                onDeleted(beforeLength, afterLength);
            }
            return ok;
        }

        @Override
        public boolean deleteSurroundingTextInCodePoints(int beforeLength,
                int afterLength) {
            boolean ok = delegate.deleteSurroundingTextInCodePoints(
                    beforeLength, afterLength);
            if (ok) {
                // Code point counts do not map 1:1 onto char positions;
                // re-sync from the editor instead of guessing.
                cursor = UNKNOWN;
            }
            return ok;
        }

        @Override
        public boolean setComposingText(CharSequence text,
                int newCursorPosition) {
            boolean ok = delegate.setComposingText(text, newCursorPosition);
            if (ok) {
                cursor = UNKNOWN; // net position depends on the composing span
            }
            return ok;
        }

        @Override
        public boolean setComposingRegion(int start, int end) {
            boolean ok = delegate.setComposingRegion(start, end);
            if (ok) {
                cursor = UNKNOWN;
            }
            return ok;
        }

        @Override
        public boolean finishComposingText() {
            boolean ok = delegate.finishComposingText();
            if (ok) {
                cursor = UNKNOWN;
            }
            return ok;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            trace("ic:commit(len=" + (text == null ? -1 : text.length()) + ')');
            boolean ok = delegate.commitText(text, newCursorPosition);
            if (ok) {
                onCommitted(text);
            }
            return ok;
        }

        @Override
        public boolean commitCompletion(CompletionInfo text) {
            return delegate.commitCompletion(text);
        }

        @Override
        public boolean commitCorrection(CorrectionInfo correctionInfo) {
            return delegate.commitCorrection(correctionInfo);
        }

        @Override
        public boolean setSelection(int start, int end) {
            trace("ic:sel(" + start + ',' + end + ')');
            boolean ok = delegate.setSelection(start, end);
            if (ok && start >= 0 && start == end) {
                onSelected(start);
            } else if (ok) {
                selectionStart = start;
                selectionEnd = end;
            }
            return ok;
        }

        @Override
        public boolean performEditorAction(int editorAction) {
            return delegate.performEditorAction(editorAction);
        }

        @Override
        public boolean performContextMenuAction(int id) {
            return delegate.performContextMenuAction(id);
        }

        @Override
        public boolean beginBatchEdit() {
            return delegate.beginBatchEdit();
        }

        @Override
        public boolean endBatchEdit() {
            return delegate.endBatchEdit();
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            return delegate.sendKeyEvent(event);
        }

        @Override
        public boolean clearMetaKeyStates(int states) {
            return delegate.clearMetaKeyStates(states);
        }

        @Override
        public boolean reportFullscreenMode(boolean enabled) {
            return delegate.reportFullscreenMode(enabled);
        }

        @Override
        public boolean performPrivateCommand(String action, Bundle data) {
            return delegate.performPrivateCommand(action, data);
        }

        @Override
        public boolean requestCursorUpdates(int cursorUpdateMode) {
            return delegate.requestCursorUpdates(cursorUpdateMode);
        }

        @Override
        public boolean commitContent(InputContentInfo inputContentInfo,
                int flags, Bundle opts) {
            return delegate.commitContent(inputContentInfo, flags, opts);
        }

        @Override
        public void closeConnection() {
            delegate.closeConnection();
        }

        @Override
        public Handler getHandler() {
            return delegate.getHandler();
        }
    }
}
