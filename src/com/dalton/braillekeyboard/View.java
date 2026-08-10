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
import java.util.Locale;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import androidx.core.view.MotionEventCompat;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.core.content.ContextCompat;

import com.dalton.braillekeyboard.Coords;
import com.dalton.braillekeyboard.Swipe;

/**
 * This View facilitates displaying a Braille keyboard to the user on the entire
 * screen and handling taps and swipes by using the ActionHandler.
 * 
 * This View holds an instance to an implementation of Pad and parses all of
 * it's touch events to the Pad to resolve which dots were hit or nearest the
 * user's touch. The pad and calibration state itself lives in a
 * {@link PadController}; this view owns the touch dispatch, drawing, speech
 * and feedback.
 * 
 * The View will then pass the appropriate swipe and dot pressed events to an
 * ActionHandler to perform the appropriate action.
 * 
 * This View also implements the OnActionListener callback and will display
 * results, send notifications or change View states appropriate to the
 * callbacks received from the ActionHandler.
 * 
 * You should register an IME listener with this View in order for the
 * ActionHandler and this View to function. See
 * onInitialiseForInput(KeyboardListener listener).
 * 
 * You should always call close() when you are done with the View to release
 * resources.
 */
public class View extends android.view.View implements PadController.Listener {
    private final AccessibilityManager accessibilityManager;
    private final Paint circlePaint;
    private final Paint paint;
    private final Rect circleTextBounds = new Rect();
    private final PadController padController;
    private FeedbackManager feedbackManager;
    private final ActionHandler.OnActionListener actionListener = new ActionHandler.OnActionListener() {

        @Override
        public void onSetDots(boolean dot7, boolean dot8) {
            padController.setDotsSevenEight(dot7, dot8);
        }

        @Override
        public void onText(String format, String text, boolean isPasswordField) {
            speech.readConsiderPassword(getContext(), format, text,
                    isPasswordField, Speech.QUEUE_FLUSH);
        }

        @Override
        public void onText(String format, String text, boolean isPasswordField,
                int mode) {
            speech.readConsiderPassword(getContext(), format, text,
                    isPasswordField, mode);
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
            setLocale(locale);
        }

        @Override
        public void onShrink() {
            setLocale(Locale.getDefault());
            shrinkKeyboard = true;
            invalidate();
            requestLayout();
            listener.updateFullscreenMode();
        }

        @Override
        public void onPrivacy() {
            setPrivacy();
        }

        @Override
        public void onShutup() {
            speech.stop();
        }
    };

    private ActionHandler actionHandler;
    private DisplayParams displayParams = null;
    private KeyboardListener listener;
    private boolean shrinkKeyboard;
    private Speech speech;

    public View(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        circlePaint = new Paint();
        accessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        padController = new PadController(context, this);
        feedbackManager = new FeedbackManager(context);
    }

    /**
     * Gets the View ready to receive touch events from the user and facilitates
     * communication with the underlying IME.
     * 
     * @param listener
     *            The KeyboardListener implementation of to communicate with the
     *            IME.
     */
    public void onInitialiseForInput(Context context, KeyboardListener listener) {
        this.listener = listener;

        // Set up speech and announce when it's ready to the user.
        speech = new Speech(getContext(), new Speech.OnReadyListener() {

            @Override
            public void ttsReady() {
                setLocale(View.this.listener.getLocale());
                speech.speak(getContext(),
                        getContext().getString(R.string.ready),
                        Speech.QUEUE_FLUSH);
            }
        });

        // When we launch the keyboard it should take up the full screen.
        feedbackManager.reloadTheme();
        if (shrinkKeyboard) {
            expandKeyboard();
        }

        if (displayParams != null) {
            padController.loadDefaultPad(getWidth(), getHeight());
            invalidate();
            requestLayout();
        }
        actionHandler = new ActionHandler(context);
        actionHandler.setCallback(actionListener);
        actionHandler.setKeyboardListener(listener);
    }

    public void close() {
        feedbackManager.emitEvent(FeedbackEvent.CLOSE);
        speech.shutdown(getContext().getString(R.string.closing_keyboard));
        actionHandler.shutdown();
        setLocale(Locale.getDefault(), false);
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setDisplayParams(w, h);
        padController.loadDefaultPad(w, h);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!shrinkKeyboard
                && displayParams != null
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

            List<Coords> keys = padController.getKeys();
            // For each dot draw a circle on the screen at it's position and
            // write the corresponding dot number in the circle.
            for (int i = 0; i < keys.size(); i++) {
                int x = displayParams.autoRotate || getWidth() >= getHeight() ? keys
                        .get(i).x : keys.get(i).y;
                int y = displayParams.autoRotate || getWidth() >= getHeight() ? keys
                        .get(i).y : keys.get(i).x;
                String text = String.valueOf(i + 1);
                paint.getTextBounds(text, 0, text.length(), circleTextBounds);
                canvas.drawCircle(x, y, displayParams.radius, circlePaint);
                canvas.drawText(text, x, y, paint);
            }
        } else if (shrinkKeyboard) {
            String text = getContext().getString(R.string.expand_keyboard);
            if (needsTalkBackWarning()) {
                text = getContext()
                        .getString(R.string.expand_keyboard_talkback);
            }
            canvas.drawText(text, displayParams.x, displayParams.y, paint);
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
                speech.speak(
                        getContext(),
                        getContext().getString(
                                R.string.expand_keyboard_talkback),
                        Speech.QUEUE_FLUSH);
            } else {
                speech.speak(getContext(),
                        getContext().getString(getTalkBackWarningMessage()),
                        Speech.QUEUE_FLUSH);
            }
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        super.onTouchEvent(motionEvent);
        // Get the height and width of the keyboard.
        // If autoRotate is enabled then the standard dimenssions are correct.
        // If autoRotate is disabled the width is the maximum of the height and
        // the width and the height is the minimum of the two.
        // This is because the user holds the phone in landscape mode, but the
        // screen might be fixed to portrate mode. It makes more sense to use
        // the keyboard in landscape mode and the user doesn't care about
        // orientation of the screen.
        int width = displayParams.autoRotate ? getWidth() : Math.max(
                getWidth(), getHeight());
        int height = displayParams.autoRotate ? getHeight() : Math.min(
                getWidth(), getHeight());
        int action = MotionEventCompat.getActionMasked(motionEvent);
        int index = MotionEventCompat.getActionIndex(motionEvent);
        int id = MotionEventCompat.getPointerId(motionEvent, index);
        int x = (int) MotionEventCompat.getX(motionEvent, index);
        int y = (int) MotionEventCompat.getY(motionEvent, index);

        // Swap x and y if the view is being used perpendicular to it's intended
        // purpose see above.
        int tempX = x;
        x = displayParams.autoRotate || getWidth() >= getHeight() ? x : y;
        y = displayParams.autoRotate || getWidth() >= getHeight() ? y : tempX;
        Swipe swipe;
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            if (shrinkKeyboard) {
                expandKeyboard();
            } else {
                padController.onPointerDown(id, x, y);
            }
            break;
        case MotionEvent.ACTION_HOVER_EXIT:
        case MotionEvent.ACTION_UP:
            // The pad is deliberately NOT re-calibrated after gestures here.
            // Auto-calibration drifts the dot keys away from the user's
            // typing positions (e.g. dot 4 moving out of reach) when typing
            // quickly, so the keyboard is only ever positioned by the manual
            // calibration (holding three fingers on each side).
            if (!handleVoiceInput()) {
                if (padController.hasPad() && padController.hasPressedDots()) {
                    boolean swap = getHeight() > getWidth()
                            && !displayParams.autoRotate;
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
            }
            padController.reset();

            if (Options.getBooleanPreference(
                    getContext(),
                    R.string.pref_show_circles_key,
                    Boolean.parseBoolean(getContext().getString(
                            R.string.pref_show_circles_default)))) {
                // redraw to show the new positions of the Braille dots.
                invalidate();
            }
            break;
        case MotionEventCompat.ACTION_HOVER_MOVE:
        case MotionEvent.ACTION_MOVE:
            for (int i = 0; i < MotionEventCompat.getPointerCount(motionEvent); i++) {
                int pId = MotionEventCompat.getPointerId(motionEvent, i);
                int pX = (int) MotionEventCompat.getX(motionEvent, i);
                int pY = (int) MotionEventCompat.getY(motionEvent, i);
                int tX = pX;
                pX = displayParams.autoRotate || getWidth() >= getHeight() ? pX : pY;
                pY = displayParams.autoRotate || getWidth() >= getHeight() ? pY : tX;
                padController.onPointerMove(pId, pX, pY);
            }
            break;
        case MotionEventCompat.ACTION_POINTER_UP:
            if (!padController.setPad(id, width, height)) {
                padController.onPointerMove(id, x, y);
                swipe = padController.resolveMultiFingerSwipe(getHeight() > getWidth() && !displayParams.autoRotate);
                if (swipe != Swipe.NONE) {
                    actionHandler.handleSwipe(getContext(), swipe);
                }
                padController.setDots();
                if (!padController.isHandledSwipe()) {
                    swipe = padController.resolveSingleSwipe(getHeight() > getWidth() && !displayParams.autoRotate);
                    if (swipe != Swipe.NONE) {
                        // Hold one finger while swiping with another
                        actionHandler.handleSwipe(getContext(), swipe);
                    }
                }
            }
            break;
        default:
        }
        return true;
    }

    public boolean getShrinkKeyboard() {
        return shrinkKeyboard;
    }

    public boolean setLocale(Locale locale) {
        return setLocale(locale, true);
    }

    private boolean setLocale(Locale locale, boolean setTTSLocale) {
        if (locale != null) {
            Resources resources = getContext().getResources();
            DisplayMetrics displayMetrics = resources.getDisplayMetrics();
            android.content.res.Configuration conf = resources
                    .getConfiguration();
            if (!conf.locale.equals(locale)) {
                if (!setTTSLocale || (setTTSLocale && speech.setLocale(locale))) {
                    conf.setLocale(locale);
                    resources.updateConfiguration(conf, displayMetrics);

                    return true;
                }
            }
        }
        return false;
    }

    public void emitFeedbackEvent(FeedbackEvent event) {
        if (feedbackManager != null) {
            feedbackManager.emitEvent(event);
        }
    }

    private void expandKeyboard() {
        speech.speak(getContext(),
                getContext().getString(R.string.keyboard_full_screen),
                Speech.QUEUE_FLUSH);
        shrinkKeyboard = false;
        setLocale(listener.getLocale());
        invalidate();
        requestLayout();
        listener.updateFullscreenMode();
    }

    private void handleTypedCharacter() {
        byte value = padController.getPressedDotString();
        actionHandler.handleCharacter(getContext(), value);
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

    private void setDisplayParams(int w, int h) {
        final int CIRCLE_RADIUS = 40;
        final int STROKE_WIDTH = 8;
        final int TEXT_SIZE = 20;
        int strokeWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, STROKE_WIDTH, getContext()
                        .getResources().getDisplayMetrics());
        int textSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE, getContext()
                        .getResources().getDisplayMetrics());
        int radius = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, CIRCLE_RADIUS, getContext()
                        .getResources().getDisplayMetrics());
        boolean autoRotate = Options.getBooleanPreference(
                getContext(),
                R.string.pref_auto_rotate_keyboard_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_auto_rotate_keyboard_default)));
        displayParams = new DisplayParams(strokeWidth, textSize, radius,
                autoRotate);
        paint.setColor(ContextCompat.getColor(getContext(),
                android.R.color.black));
        paint.setTextSize(displayParams.textSize);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        circlePaint.setColor(ContextCompat.getColor(getContext(),
                android.R.color.black));
        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Style.STROKE);
        circlePaint.setStrokeWidth(displayParams.strokeWidth);

        FontMetrics metrics = paint.getFontMetrics();
        float height = Math.abs(metrics.top - metrics.bottom);
        displayParams.x = getWidth() / 2;
        displayParams.y = (getHeight() / 2) + (height / 2);
    }

    private boolean handleVoiceInput() {
        if (System.currentTimeMillis() > padController.getRequiredTouchTime()
                && padController.getDotsDownCount() == 1
                && Options.getBooleanPreference(
                        getContext(),
                        R.string.pref_voice_shortcut_key,
                        Boolean.parseBoolean(getContext().getString(
                                R.string.pref_voice_shortcut_default)))) {
            actionHandler.doVoiceInput(getContext(), false);
            return true;
        }
        return false;
    }

    // PadController.Listener ---------------------------------------------

    @Override
    public void speak(int stringRes) {
        speech.speak(getContext(), getContext().getString(stringRes),
                Speech.QUEUE_FLUSH);
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
        return displayParams.autoRotate;
    }

    @Override
    public boolean portraitSwap() {
        return getHeight() > getWidth() && !displayParams.autoRotate;
    }

    @Override
    public int dots() {
        return listener.getDots();
    }

    private static class DisplayParams {
        public final int strokeWidth;
        public final int textSize;
        public final int radius;
        public final boolean autoRotate;

        public float x;
        public float y;

        public DisplayParams(int strokeWidth, int textSize, int radius,
                boolean autoRotate) {
            this.strokeWidth = strokeWidth;
            this.textSize = textSize;
            this.radius = radius;
            this.autoRotate = autoRotate;
        }
    }
}
