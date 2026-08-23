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
import android.os.Vibrator;
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
public class BrailleKeyboardView extends android.view.View implements PadController.Listener {
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
            listener.onShrinkStateChanged();
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
    private final Vibrator vibrator;

    public BrailleKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        circlePaint = new Paint();
        accessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
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
                setLocale(BrailleKeyboardView.this.listener.getLocale());
                if (SpeechEvent.KEYBOARD_SHOWN.isEnabled(getContext())) {
                    speech.speak(getContext(),
                            getContext().getString(R.string.ready),
                            Speech.QUEUE_FLUSH);
                }
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
        logKeyboardState("keyboard initialised");
    }

    public void close() {
        feedbackManager.emitEvent(FeedbackEvent.CLOSE);
        speech.shutdown(SpeechEvent.KEYBOARD_CLOSED.isEnabled(getContext())
                ? getContext().getString(R.string.closing_keyboard) : null);
        actionHandler.shutdown();
        setLocale(Locale.getDefault(), false);
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setDisplayParams(w, h);
        padController.loadInitialPad(w, h);
        logKeyboardState("size changed");
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (padController.isManualCalibrating()) {
            // During the guided calibration the next dot to touch is shown
            // on the screen as well as spoken.
            paint.setTextSize(50.0f);
            String msg = getContext().getString(
                    R.string.calibration_step,
                    padController.getCurrentCalibrationStep() + 1);
            canvas.drawText(msg, 50.0f, 100.0f, paint);
            // Restore the label size used for the dot circles.
            if (displayParams != null) {
                paint.setTextSize(displayParams.textSize);
            }
        }
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
        int rawX = x;
        int rawY = y;

        // Swap x and y if the view is being used perpendicular to it's intended
        // purpose see above.
        int tempX = x;
        x = displayParams.autoRotate || getWidth() >= getHeight() ? x : y;
        y = displayParams.autoRotate || getWidth() >= getHeight() ? y : tempX;
        Swipe swipe = Swipe.NONE;

        // Record the touch in the diagnostic log so that reports of the
        // keyboard not receiving touches can be traced.
        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_UP
                || action == MotionEventCompat.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            logTouch(actionName(action), motionEvent, id, rawX, rawY, x, y);
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
        case MotionEvent.ACTION_HOVER_EXIT:
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
            Diagnostics.log(getContext(), "gesture: swipe=" + swipe.name()
                    + " handled=" + padController.isHandledSwipe()
                    + " action=" + actionName(swipe) + " "
                    + padController.describeSwipe(isSwap()));
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
            Diagnostics.log(getContext(), "gesture(pointer_up): swipe="
                    + swipe.name() + " handled="
                    + padController.isHandledSwipe() + " action="
                    + actionName(swipe) + " "
                    + padController.describeSwipe(isSwap()));
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
        listener.onShrinkStateChanged();
    }

    private void handleTypedCharacter() {
        byte value = padController.getPressedDotString();
        actionHandler.handleCharacter(getContext(), value);
    }

    // True when the view is used on a portrait screen held in landscape, in
    // which case the touch axes are swapped before any swipe is resolved.
    private boolean isSwap() {
        return getHeight() > getWidth() && !displayParams.autoRotate;
    }

    // The name of the action bound to the resolved gesture, for the
    // diagnostic log.
    private String actionName(Swipe swipe) {
        if (swipe == Swipe.NONE) {
            return "none";
        }
        return swipe.getBoundAction(getContext()).name();
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

    // Write the keyboard geometry, orientation and dot placement to the
    // diagnostic log, used to diagnose reports of the keyboard not
    // responding or the dots not being where the user expects them.
    private void logKeyboardState(String reason) {
        if (!Diagnostics.isEnabled(getContext())) {
            return;
        }
        StringBuilder sb = new StringBuilder(reason);
        sb.append(" view=").append(getWidth()).append('x')
                .append(getHeight());
        int[] location = new int[2];
        getLocationOnScreen(location);
        sb.append(" onScreen=(").append(location[0]).append(',')
                .append(location[1]).append(")-(")
                .append(location[0] + getWidth()).append(',')
                .append(location[1] + getHeight()).append(')');
        Rect frame = new Rect();
        getWindowVisibleDisplayFrame(frame);
        sb.append(" window=").append(frame.toShortString());
        sb.append(" rotation=")
                .append(Diagnostics.rotationLabel(getContext()));
        sb.append(" invert=").append(Options.getBooleanPreference(
                getContext(), R.string.pref_keyboard_invert_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_keyboard_invert_default))));
        sb.append(" autoRotate=")
                .append(displayParams != null ? displayParams.autoRotate
                        : '?');
        if (!padController.hasPad()) {
            sb.append(" pad=null");
        } else {
            sb.append(" padType=").append(padController.getPadTypeName());
            List<Coords> keys = padController.getKeys();
            sb.append(" dots=");
            for (int i = 0; i < keys.size(); i++) {
                Coords key = keys.get(i);
                sb.append(i + 1).append('(').append(key.x).append(',')
                        .append(key.y).append(')');
                if (i + 1 < keys.size()) {
                    sb.append(' ');
                }
            }
        }
        Diagnostics.log(getContext(), sb.toString());
    }

    // Write a single touch to the diagnostic log with both the raw screen
    // coordinates and the coordinates mapped onto the keyboard.
    private void logTouch(String event, MotionEvent motionEvent, int id,
            int rawX, int rawY, int x, int y) {
        StringBuilder sb = new StringBuilder("touch ");
        sb.append(event).append(" ptr=")
                .append(MotionEventCompat.getPointerCount(motionEvent))
                .append(" id=").append(id).append(" raw=(").append(rawX)
                .append(',').append(rawY).append(") mapped=(").append(x)
                .append(',').append(y).append(')');
        if (padController.isManualCalibrating()) {
            sb.append(" calibrating");
        }
        sb.append(" pressed=0x").append(Integer
                .toHexString(padController.getPressedDotString()));
        sb.append(nearestDot(x, y));
        Diagnostics.log(getContext(), sb.toString());
    }

    // The dot closest to a touch and its distance, used to see whether the
    // dot positions match where the user actually touches the screen.
    private String nearestDot(int x, int y) {
        if (!padController.hasPad()) {
            return "";
        }
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        List<Coords> keys = padController.getKeys();
        for (int i = 0; i < keys.size(); i++) {
            Coords key = keys.get(i);
            int dx = key.x - x;
            int dy = key.y - y;
            int distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) {
            return "";
        }
        return " near=dot" + (bestIndex + 1) + "("
                + Math.round(Math.sqrt(bestDistance)) + "px)";
    }

    private static String actionName(int action) {
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            return "down";
        case MotionEventCompat.ACTION_POINTER_DOWN:
            return "pointer_down";
        case MotionEvent.ACTION_UP:
            return "up";
        case MotionEventCompat.ACTION_POINTER_UP:
            return "pointer_up";
        case MotionEvent.ACTION_CANCEL:
            return "cancel";
        case MotionEvent.ACTION_MOVE:
            return "move";
        case MotionEventCompat.ACTION_HOVER_MOVE:
            return "hover_move";
        case MotionEvent.ACTION_HOVER_EXIT:
            return "hover_exit";
        default:
            return "action_" + action;
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

    // PadController.Listener ---------------------------------------------

    @Override
    public void speak(int stringRes) {
        speech.speak(getContext(), getContext().getString(stringRes),
                Speech.QUEUE_FLUSH);
    }

    @Override
    public void speak(int stringRes, int queueMode) {
        speech.speak(getContext(), getContext().getString(stringRes),
                queueMode);
    }

    @Override
    public void speak(int stringRes, int queueMode, Object... args) {
        speech.speak(getContext(), getContext().getString(stringRes, args),
                queueMode);
    }

    @Override
    public void speak(CharSequence text, int queueMode) {
        speech.speak(getContext(), text, queueMode);
    }

    @Override
    public void vibrate(long milliseconds) {
        vibrator.vibrate(milliseconds);
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
