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

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.core.content.ContextCompat;

/**
 * The Braille keyboard view: a full-screen touch surface where the user
 * presses Braille dots and performs gestures.
 *
 * <p>The pad geometry and calibration state live in {@link PadController};
 * drawing lives in {@link DotRenderer}, coordinate mapping in
 * {@link TouchMapper}, announcements in {@link KeyboardSpeaker} and touch
 * diagnostics in {@link TouchLog}. This class wires those together, owns the
 * touch dispatch loop and implements the callbacks of
 * {@link ActionHandler.OnActionListener}.
 *
 * You should register an IME listener with this view through
 * {@link #onInitialiseForInput} for the ActionHandler to function, and always
 * call {@link #close()} when done with the view to release resources.
 */
public class BrailleKeyboardView extends android.view.View
        implements PadController.Listener {
    private final AccessibilityManager accessibilityManager;
    private final DotRenderer renderer = new DotRenderer();
    private final PadController padController;
    private final KeyboardSpeaker speaker;
    private FeedbackManager feedbackManager;
    private final ActionHandler.OnActionListener actionListener =
            new ActionHandler.OnActionListener() {

        @Override
        public void onSetDots(boolean dot7, boolean dot8) {
            padController.setDotsSevenEight(dot7, dot8);
        }

        @Override
        public void onText(String format, String text, boolean isPasswordField) {
            speaker.readConsiderPassword(format, text, isPasswordField,
                    Speech.QUEUE_FLUSH);
        }

        @Override
        public void onText(String format, String text, boolean isPasswordField,
                int mode) {
            speaker.readConsiderPassword(format, text, isPasswordField, mode);
        }

        @Override
        public void onNotify(FeedbackEvent event) {
            feedbackManager.emitEvent(event);
        }

        @Override
        public void onFeedbackSettingsChanged() {
            feedbackManager.reloadTheme();
        }

        @Override
        public void onSetLocale(Locale locale) {
            applyLocale(locale);
        }

        @Override
        public void onShrink() {
            applyLocale(Locale.getDefault());
            shrinkKeyboard = true;
            invalidate();
            requestLayout();
            listener.onShrinkStateChanged();
        }

        @Override
        public void onPrivacy() {
            setPrivacy();
        }

        @Override
        public void onShutup() {
            speaker.stop();
        }
    };

    private ActionHandler actionHandler;
    private KeyboardListener listener;
    private boolean shrinkKeyboard;

    public BrailleKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        speaker = new KeyboardSpeaker(context);
        padController = new PadController(context, this);
        feedbackManager = new FeedbackManager(context);
    }

    /**
     * Gets the view ready to receive touch events from the user and
     * facilitates communication with the underlying IME.
     *
     * @param listener
     *            The KeyboardListener implementation to communicate with the
     *            IME.
     * @param speech
     *            The shared speech instance owned by the IME service; it is
     *            created once per process and never replaced, so announcements
     *            from every component stay audible for the whole session.
     */
    public void onInitialiseForInput(Context context, KeyboardListener listener,
            Speech speech) {
        this.listener = listener;
        speaker.attach(speech);
        // Apply the Braille table's locale up front. When the shared engine
        // is still initialising (rare), setLocale reports failure and the
        // translator-ready callback applies it again a moment later.
        applyLocale(listener.getLocale());
        if (SpeechEvent.KEYBOARD_SHOWN.isEnabled(getContext())) {
            // speak() is self-gating: it stays silent while the engine is
            // still starting up instead of queueing into nothing.
            speaker.speak(getContext().getString(R.string.ready),
                    Speech.QUEUE_FLUSH);
        }

        // When we launch the keyboard it should take up the full screen.
        feedbackManager.reloadTheme();
        if (shrinkKeyboard) {
            expandKeyboard();
        }

        if (renderer.params() != null) {
            padController.loadDefaultPad(getWidth(), getHeight());
            invalidate();
            requestLayout();
        }
        actionHandler = new ActionHandler(context);
        actionHandler.setCallback(actionListener);
        actionHandler.setKeyboardListener(listener);
        TouchLog.keyboardState(this, padController, renderer,
                "keyboard initialised");
    }

    public void close() {
        feedbackManager.emitEvent(FeedbackEvent.CLOSE);
        if (SpeechEvent.KEYBOARD_CLOSED.isEnabled(getContext())) {
            speaker.speak(getContext().getString(R.string.closing_keyboard),
                    Speech.QUEUE_FLUSH);
        }
        actionHandler.shutdown();
        applyLocale(Locale.getDefault(), false);
    }

    /** Releases resources when this view will never be used again. */
    public void releaseResources() {
        feedbackManager.release();
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        renderer.setSize(getContext(), w, h);
        padController.loadInitialPad(w, h);
        TouchLog.keyboardState(this, padController, renderer, "size changed");
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (padController.isManualCalibrating()) {
            // During the guided calibration the next dot to touch is shown
            // on the screen as well as spoken.
            renderer.drawCalibrationBanner(canvas, getContext().getString(
                    R.string.calibration_step,
                    padController.getCurrentCalibrationStep() + 1));
        }
        DotRenderer.DisplayParams params = renderer.params();
        if (!shrinkKeyboard
                && params != null
                && Options.getBooleanPreference(
                        getContext(),
                        R.string.pref_show_circles_key,
                        Boolean.parseBoolean(getContext().getString(
                                R.string.pref_show_circles_default)))) {
            // We should show a visual representation of the view according to
            // user preference.
            if (needsTalkBackWarning()) {
                setContentDescription(getContext().getString(
                        getTalkBackWarningMessage()));
            } else {
                setContentDescription(null);
            }

            renderer.drawDots(canvas, padController.getKeys(),
                    params.autoRotate, getWidth(), getHeight());
        } else if (shrinkKeyboard && params != null) {
            CharSequence text = getContext()
                    .getString(R.string.expand_keyboard);
            if (needsTalkBackWarning()) {
                text = getContext()
                        .getString(R.string.expand_keyboard_talkback);
            }
            renderer.drawShrunkLabel(canvas, text);
            setContentDescription(text);
        }
        setPrivacy();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int SHRINK_FACTOR = 2;
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        int desiredWidth = widthSize;
        int desiredHeight = heightSize;
        if (shrinkKeyboard) {
            desiredHeight /= SHRINK_FACTOR;
        }
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(desiredWidth, widthSize);
        } else {
            width = desiredWidth;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredHeight, heightSize);
        } else {
            height = desiredHeight;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        // Only warn about TalkBack when the keyboard cannot receive touches
        // directly (i.e. the accessibility service is not enabled, or the
        // device does not support gesture passthrough regions). With the
        // service enabled on Android 11+ the whole screen is passed through,
        // so the keyboard works and hover events are never delivered.
        if (needsTalkBackWarning()) {
            if (shrinkKeyboard) {
                speaker.speak(getContext()
                        .getString(R.string.expand_keyboard_talkback),
                        Speech.QUEUE_FLUSH);
            } else {
                speaker.speak(getContext().getString(
                        getTalkBackWarningMessage()), Speech.QUEUE_FLUSH);
            }
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        super.onTouchEvent(motionEvent);

        DotRenderer.DisplayParams params = renderer.params();
        if (params == null) {
            // No size known yet, so there is no pad to resolve touches
            // against; consume the event without action.
            return true;
        }
        boolean autoRotate = params.autoRotate;
        // Get the dimensions of the keyboard in keyboard space. If autoRotate
        // is disabled the width is the maximum of the height and the width
        // and the height is the minimum of the two. This is because the user
        // holds the phone in landscape mode, but the screen might be fixed
        // to portrait mode.
        int width = TouchMapper.normalizedWidth(autoRotate, getWidth(),
                getHeight());
        int height = TouchMapper.normalizedHeight(autoRotate, getWidth(),
                getHeight());
        int action = motionEvent.getActionMasked();
        int index = motionEvent.getActionIndex();
        int id = motionEvent.getPointerId(index);
        int x = (int) motionEvent.getX(index);
        int y = (int) motionEvent.getY(index);
        int rawX = x;
        int rawY = y;
        // Swap x and y if the view is being used perpendicular to its
        // intended purpose.
        x = TouchMapper.mapX(autoRotate, getWidth(), getHeight(), rawX, rawY);
        y = TouchMapper.mapY(autoRotate, getWidth(), getHeight(), rawX, rawY);
        Swipe swipe = Swipe.NONE;

        // Record the touch in the diagnostic log so that reports of the
        // keyboard not receiving touches can be traced. TouchLog gates on the
        // logging preference before building anything.
        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            TouchLog.touch(getContext(), padController,
                    TouchLog.actionName(action), motionEvent, id, rawX, rawY,
                    x, y);
        }

        // During a calibration every touch places the next dot instead of
        // typing or gesturing.
        if (padController.isManualCalibrating()) {
            if (action == MotionEvent.ACTION_CANCEL) {
                // The system cancelled the touch sequence (e.g. an incoming
                // call or a system gesture), so don't leave the keyboard
                // stuck in calibration mode.
                padController.abortManualCalibration();
            } else if (action == MotionEvent.ACTION_DOWN
                    || action == MotionEvent.ACTION_POINTER_DOWN) {
                padController.handleManualCalibrationTouch(x, y);
            }
            return true;
        }

        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            if (shrinkKeyboard) {
                expandKeyboard();
            } else {
                padController.onPointerDown(id, x, y);
                // Holding three fingers still starts the guided calibration
                // and holding every dot at once calibrates instantly.
                padController.checkAndScheduleCalibration(width, height);
            }
            break;
        case MotionEvent.ACTION_UP:
            // A finger was lifted, so any pending calibration attempt is
            // abandoned (the fingers must be held down without lifting).
            padController.cancelCalibrationScheduled();
            if (padController.hasPad() && padController.hasPressedDots()) {
                boolean swap = isSwap();
                swipe = padController.resolveMultiFingerSwipe(swap);
                if (swipe != Swipe.NONE) {
                    actionHandler.handleSwipe(getContext(), swipe);
                }
                padController.setDots();
                if (!padController.isHandledSwipe()) {
                    // single finger flicks
                    swipe = padController.resolveSingleSwipe(swap);
                    if (swipe != Swipe.NONE) {
                        actionHandler.handleSwipe(getContext(), swipe);
                    } else { // all swipe attempts failed so resort to
                        // entering character
                        handleTypedCharacter();
                    }
                }
                padController.clearLastDotList();
            }
            TouchLog.gesture(getContext(), padController, swipe, isSwap(),
                    false);
            padController.reset();
            // Nudge the dots towards where the user actually touches them,
            // using the drift measured while typing. This is a no-op when
            // the dot positions are locked (see "Lock dot positions").
            padController.updateKeysAfterTyping();

            if (Options.getBooleanPreference(
                    getContext(),
                    R.string.pref_show_circles_key,
                    Boolean.parseBoolean(getContext().getString(
                            R.string.pref_show_circles_default)))) {
                // redraw to show the new positions of the Braille dots.
                invalidate();
            }
            break;
        case MotionEvent.ACTION_MOVE:
            for (int i = 0; i < motionEvent.getPointerCount(); i++) {
                int pId = motionEvent.getPointerId(i);
                int pX = (int) motionEvent.getX(i);
                int pY = (int) motionEvent.getY(i);
                int mappedX = TouchMapper.mapX(autoRotate, getWidth(),
                        getHeight(), pX, pY);
                int mappedY = TouchMapper.mapY(autoRotate, getWidth(),
                        getHeight(), pX, pY);
                padController.onPointerMove(pId, mappedX, mappedY);
            }
            break;
        case MotionEvent.ACTION_POINTER_UP:
            padController.cancelCalibrationScheduled();
            padController.onPointerMove(id, x, y);
            swipe = padController.resolveMultiFingerSwipe(isSwap());
            if (swipe != Swipe.NONE) {
                actionHandler.handleSwipe(getContext(), swipe);
            }
            padController.setDots();
            if (!padController.isHandledSwipe()) {
                swipe = padController.resolveSingleSwipe(isSwap());
                if (swipe != Swipe.NONE) {
                    // Hold one finger while swiping with another
                    actionHandler.handleSwipe(getContext(), swipe);
                }
            }
            TouchLog.gesture(getContext(), padController, swipe, isSwap(),
                    true);
            break;
        default:
        }
        return true;
    }

    public boolean getShrinkKeyboard() {
        return shrinkKeyboard;
    }

    /** Switches the process resources (and optionally TTS) to the locale. */
    public boolean applyLocale(Locale locale) {
        return applyLocale(locale, true);
    }

    private boolean applyLocale(Locale locale, boolean setTtsLocale) {
        return speaker.applyLocale(locale, setTtsLocale);
    }

    public void emitFeedbackEvent(FeedbackEvent event) {
        if (feedbackManager != null) {
            feedbackManager.emitEvent(event);
        }
    }

    private void expandKeyboard() {
        speaker.speak(getContext().getString(R.string.keyboard_full_screen),
                Speech.QUEUE_FLUSH);
        shrinkKeyboard = false;
        applyLocale(listener.getLocale());
        invalidate();
        requestLayout();
        listener.onShrinkStateChanged();
    }

    private void handleTypedCharacter() {
        byte value = padController.getPressedDotString();
        actionHandler.handleCharacter(getContext(), value);
    }

    // True when the view is used on a portrait screen held in landscape, in
    // which case the touch axes are swapped before any swipe is resolved.
    private boolean isSwap() {
        DotRenderer.DisplayParams params = renderer.params();
        return params != null && TouchMapper.isSwap(params.autoRotate,
                getWidth(), getHeight());
    }

    /**
     * True when a screen reader is on but the keyboard cannot receive touches
     * directly, so the user needs to be told what to do (disable TalkBack on
     * old Android versions, or enable the accessibility service on 11+).
     */
    private boolean needsTalkBackWarning() {
        return accessibilityManager.isTouchExplorationEnabled()
                && !AccessibilityService.canPassthrough(getContext());
    }

    /**
     * The message explaining how to use the keyboard with a screen reader on.
     * On Android 10 and older the passthrough API does not exist, so TalkBack
     * itself has to be disabled; on 11+ the user should enable the
     * accessibility service instead.
     */
    private int getTalkBackWarningMessage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return R.string.switch_off_talkback;
        }
        return R.string.enable_accessibility_service_for_talkback;
    }

    private boolean setPrivacy() {
        if (Options.getBooleanPreference(
                getContext(),
                R.string.pref_privacy_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_privacy_default)))) {
            setBackgroundColor(ContextCompat.getColor(getContext(),
                    android.R.color.black));
            return true;
        } else {
            setBackgroundColor(ContextCompat.getColor(getContext(),
                    android.R.color.transparent));
            return false;
        }
    }

    // PadController.Listener ---------------------------------------------

    @Override
    public void speak(int stringRes) {
        speaker.speak(stringRes);
    }

    @Override
    public void speak(int stringRes, int queueMode) {
        speaker.speak(getContext().getString(stringRes), queueMode);
    }

    @Override
    public void speak(int stringRes, int queueMode, Object... args) {
        speaker.speak(getContext().getString(stringRes, args), queueMode);
    }

    @Override
    public void speak(CharSequence text, int queueMode) {
        speaker.speak(text, queueMode);
    }

    @Override
    public void vibrate(long milliseconds) {
        feedbackManager.vibrate(milliseconds);
    }

    @Override
    public void emitCalibrate() {
        feedbackManager.emitEvent(FeedbackEvent.CALIBRATE);
    }

    @Override
    public boolean useEightDots() {
        return Options.getBooleanPreference(
                getContext(),
                R.string.pref_use_eight_dots_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_use_eight_dots_default)));
    }

    @Override
    public boolean autoRotate() {
        DotRenderer.DisplayParams params = renderer.params();
        return params != null && params.autoRotate;
    }

    @Override
    public boolean portraitSwap() {
        return isSwap();
    }

    @Override
    public int dots() {
        return listener.getDots();
    }
}
