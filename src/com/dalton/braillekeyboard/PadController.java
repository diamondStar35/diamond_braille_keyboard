package com.dalton.braillekeyboard;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Owns the state of the Braille pad: the current {@link Pad}, the dots
 * currently pressed down, the calibration bookkeeping and the swipe
 * resolution, together with the logic that positions the pad.
 *
 * <p>Extracted from {@link com.dalton.braillekeyboard.BrailleKeyboardView} so the view is
 * left with touch dispatch, drawing and speech concerns while all pad state
 * lives here. The view feeds translated touch coordinates in through the
 * {@code onPointer*} methods and asks for swipes and dot patterns back.
 *
 * <p>Calibration is started by holding the fingers still for
 * {@link #LONG_HOLD_DELAY}: holding three fingers starts the guided
 * calibration where each subsequent touch places the next dot, while holding
 * every dot of the keyboard at once calibrates instantly. A successful
 * calibration is saved under a layout key specific to the current orientation
 * and screen size and locks the dot positions so they cannot drift while
 * typing. When unlocked, the pad nudges its dots towards where the user
 * actually touches them (see {@link Pad#updateKeys}).
 */
public class PadController {
    private static final byte NO_DOTS = 0;
    private static final long LONG_HOLD_DELAY = 1200;
    private static final long QUICK_VIBRATION = 25;
    private static final long MEDIUM_VIBRATION = 125;
    private static final long LONG_VIBRATION = 300;
    private static final int MOVE_CANCEL_DISTANCE = 40;
    private static final int MAX_DOTS = 8;

    /** Reports pad events back to the owning view. */
    public interface Listener {
        /** Speak a string resource. */
        void speak(int stringRes);

        /** Speak a string resource with the given TTS queue mode. */
        void speak(int stringRes, int queueMode);

        /** Speak a formatted string resource with the given queue mode. */
        void speak(int stringRes, int queueMode, Object... args);

        /** Speak raw text with the given TTS queue mode. */
        void speak(CharSequence text, int queueMode);

        /** Vibrate for the given number of milliseconds. */
        void vibrate(long milliseconds);

        /** Fire the calibration feedback event. */
        void emitCalibrate();

        /** Whether the user has enabled the eight dot keyboard. */
        boolean useEightDots();

        /** Whether the keyboard rotates with the device. */
        boolean autoRotate();

        /** True when the view is held portrait-wise but used in landscape. */
        boolean portraitSwap();

        /** The number of dots of the active Braille table. */
        int dots();

        /** Ask the owning view to redraw itself. */
        void invalidate();
    }

    private final Context context;
    private final Listener listener;

    private final List<Coords> lastDotList = new ArrayList<Coords>();
    private final List<Coords> manualCalibrationDots = new ArrayList<Coords>();
    private final Handler calibrationHandler = new Handler(
            Looper.getMainLooper());

    private Coords[] dotsDown = new Coords[MAX_DOTS];
    private Pad pad;
    private boolean dot7;
    private boolean dot8;
    private boolean handledSwipe = false;
    private Runnable calibrationRunnable;
    private int currentCalibrationStep;
    private boolean isManualCalibrating;
    private int calibrationWidth;
    private int calibrationHeight;

    public PadController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    /**
     * Record a finger going down. Also starts the timer for how long the
     * fingers must be held down before a calibration attempt is accepted.
     *
     * <p>A fresh finger landing starts a new gesture attempt: when a
     * touch-hold gesture has already been handled (e.g. hold dot 1 and swipe
     * dot 4), the user can repeat it by lifting and re-touching the swiped
     * dot while still holding the first, without the previously handled
     * gesture blocking the new one.
     */
    public void onPointerDown(int id, int x, int y) {
        handledSwipe = false;
        if (!updatePointer(id, x, y, true) && id < dotsDown.length) {
            // add a new unique dot to the list of dots that were pushed.
            dotsDown[id] = new Coords(id, x, y);
        }
    }

    /** Record a finger moving. Movement cancels any pending calibration. */
    public void onPointerMove(int id, int x, int y) {
        for (int i = 0; i < dotsDown.length; i++) {
            if (dotsDown[i] != null && dotsDown[i].id == id) {
                // The calibration requires the fingers to be held still; any
                // finger moving away from where it went down aborts it.
                if (Math.abs(x - dotsDown[i].x) > MOVE_CANCEL_DISTANCE
                        || Math.abs(y - dotsDown[i].y) > MOVE_CANCEL_DISTANCE) {
                    cancelCalibrationScheduled();
                }
                break;
            }
        }
        updatePointer(id, x, y, false);
    }

    /**
     * Schedule a calibration attempt if the current finger configuration is
     * one of the two valid calibration starts: exactly three fingers down
     * (guided one-by-one calibration) or every dot of the keyboard down
     * (instant calibration). The attempt only completes if the fingers are
     * still held after {@link #LONG_HOLD_DELAY}.
     */
    public void checkAndScheduleCalibration(int width, int height) {
        cancelCalibrationScheduled();
        if (isManualCalibrating) {
            return;
        }
        calibrationWidth = width;
        calibrationHeight = height;
        int totalDots = listener.dots();
        if (totalDots == -1) {
            totalDots = 6;
        }
        int dotsDownCount = countDotsDown(dotsDown);
        boolean canStartStepByStep = dotsDownCount == 3;
        boolean canStartInstant = dotsDownCount == totalDots;
        if (!canStartStepByStep && !canStartInstant) {
            return;
        }
        Diagnostics.log(context, "calibration attempt scheduled: "
                + (canStartInstant ? "instant (" + dotsDownCount
                        + " fingers)" : "guided (3 fingers)"));
        final boolean instant = canStartInstant;
        final boolean stepByStep = canStartStepByStep;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (isManualCalibrating) {
                    return;
                }
                int currentCount = countDotsDown(dotsDown);
                if (instant) {
                    int total = listener.dots();
                    if (total == -1) {
                        total = 6;
                    }
                    if (currentCount == total) {
                        isManualCalibrating = true;
                        currentCalibrationStep = 0;
                        manualCalibrationDots.clear();
                        finishManualCalibrationInstant(-1, calibrationWidth,
                                calibrationHeight);
                    }
                } else if (stepByStep && currentCount == 3) {
                    isManualCalibrating = true;
                    currentCalibrationStep = 0;
                    manualCalibrationDots.clear();
                    Diagnostics.log(context,
                            "calibration started: guided one-by-one");
                    listener.speak(R.string.calibration_init_message,
                            Speech.QUEUE_FLUSH);
                    listener.speak(R.string.calibration_step,
                            Speech.QUEUE_ADD, 1);
                    listener.vibrate(MEDIUM_VIBRATION);
                    lastDotList.clear();
                    reset();
                    listener.invalidate();
                }
                calibrationRunnable = null;
            }
        };
        calibrationRunnable = runnable;
        calibrationHandler.postDelayed(runnable, LONG_HOLD_DELAY);
    }

    /** Abandon a pending calibration attempt (e.g. a finger was lifted). */
    public void cancelCalibrationScheduled() {
        if (calibrationRunnable != null) {
            calibrationHandler.removeCallbacks(calibrationRunnable);
            calibrationRunnable = null;
        }
    }

    /**
     * Abort a calibration in progress (e.g. the touch sequence was cancelled
     * by the system) and return the keyboard to normal typing.
     */
    public void abortManualCalibration() {
        cancelCalibrationScheduled();
        Diagnostics.log(context, "calibration aborted (touch cancelled)");
        isManualCalibrating = false;
        currentCalibrationStep = 0;
        manualCalibrationDots.clear();
        lastDotList.clear();
        reset();
    }

    /** True while a guided or instant calibration is in progress. */
    public boolean isManualCalibrating() {
        return isManualCalibrating;
    }

    /** The number of dots placed so far in the guided calibration. */
    public int getCurrentCalibrationStep() {
        return currentCalibrationStep;
    }

    /**
     * Record a touch as the next dot of the guided one-by-one calibration.
     * When the last dot is placed the calibration finishes.
     */
    public void handleManualCalibrationTouch(int x, int y) {
        int totalDots = listener.dots();
        if (totalDots == -1) {
            totalDots = 6;
        }
        manualCalibrationDots.add(new Coords(currentCalibrationStep, x, y));
        Diagnostics.log(context, "calibration touch step="
                + (currentCalibrationStep + 1) + " at (" + x + "," + y + ")");
        listener.vibrate(QUICK_VIBRATION);
        currentCalibrationStep++;
        if (currentCalibrationStep >= totalDots) {
            finishManualCalibration();
        } else {
            listener.speak(R.string.calibration_step, Speech.QUEUE_FLUSH,
                    currentCalibrationStep + 1);
            listener.invalidate();
        }
    }

    // Finish the guided calibration with the dots collected one by one.
    private void finishManualCalibration() {
        isManualCalibrating = false;
        int width = calibrationWidth;
        int height = calibrationHeight;
        Coords[] dots = manualCalibrationDots
                .toArray(new Coords[manualCalibrationDots.size()]);
        applyCreaseAvoidance(dots, width, height);
        try {
            if (!selectPad(dots, width, height)) {
                listener.speak(R.string.keyboard_error);
            } else {
                String dynamicKey = getDynamicCalibrationKey(width, height);
                pad.saveStringKey(context, dynamicKey,
                        listener.portraitSwap(), dots);
                Options.writeBooleanPreference(context,
                        R.string.pref_lock_calibration_key, true);
                Options.writeIntPreference(context,
                        R.string.pref_last_calibrated_layout_key,
                        pad.getKeyboardType().ordinal());
                Diagnostics.log(context, "calibration finished (guided), "
                        + "saved under " + dynamicKey);
                listener.speak(R.string.calibration_success);
                listener.emitCalibrate();
            }
        } catch (IllegalArgumentException e) {
            Diagnostics.log(context,
                    "calibration failed (guided): " + e.getMessage());
            listener.speak(context.getString(R.string.keyboard_error) + ". "
                    + e.getMessage(), Speech.QUEUE_FLUSH);
            listener.vibrate(QUICK_VIBRATION);
        }
        manualCalibrationDots.clear();
        reset();
        listener.invalidate();
    }

    // Finish the instant calibration using the fingers currently held down.
    private void finishManualCalibrationInstant(int id, int width, int height) {
        int totalDots = listener.dots();
        if (totalDots == -1) {
            totalDots = 6;
        }
        setDotsSevenEight(false, false);
        Coords[] dots = new Coords[totalDots];
        for (int i = 0, j = 0; i < dotsDown.length && j < dots.length; i++) {
            if (dotsDown[i] != null) {
                int localX = dotsDown[i].getSecondX();
                int localY = dotsDown[i].getSecondY();
                int j2 = j + 1;
                dots[j] = new Coords(j2, localX, localY);
                j = j2;
            }
        }
        isManualCalibrating = false;
        applyCreaseAvoidance(dots, width, height);
        try {
            if (!selectPad(dots, width, height)) {
                listener.speak(R.string.keyboard_error);
                listener.vibrate(QUICK_VIBRATION);
            } else {
                String dynamicKey = getDynamicCalibrationKey(width, height);
                pad.saveStringKey(context, dynamicKey,
                        listener.portraitSwap(), dots);
                Options.writeBooleanPreference(context,
                        R.string.pref_lock_calibration_key, true);
                Options.writeIntPreference(context,
                        R.string.pref_last_calibrated_layout_key,
                        pad.getKeyboardType().ordinal());
                Diagnostics.log(context, "calibration finished (instant), "
                        + "saved under " + dynamicKey);
                listener.speak(pad.padString);
                listener.speak(R.string.calibration_success,
                        Speech.QUEUE_ADD);
                listener.emitCalibrate();
            }
        } catch (IllegalArgumentException e) {
            Diagnostics.log(context,
                    "calibration failed (instant): " + e.getMessage());
            listener.speak(context.getString(R.string.keyboard_error) + ". "
                    + e.getMessage(), Speech.QUEUE_FLUSH);
            listener.vibrate(QUICK_VIBRATION);
        }
        lastDotList.clear();
        reset();
        listener.invalidate();
    }

    // On large screens (foldables and tablets) the fold in the middle of the
    // screen makes dots sitting on the crease hard to reach, so shift any dot
    // close to the horizontal centre away from it.
    private void applyCreaseAvoidance(Coords[] dots, int width, int height) {
        float density = context.getResources().getDisplayMetrics().density;
        if (density <= 0.0f) {
            density = 1.0f;
        }
        int viewSmallestWidthDp = (int) (Math.min(width, height) / density);
        if (viewSmallestWidthDp < 600) {
            return;
        }
        int centerX = width / 2;
        int marginPx = (int) (30.0f * density);
        for (Coords dot : dots) {
            if (dot != null) {
                int dist = Math.abs(dot.x - centerX);
                if (dist < marginPx) {
                    int originalX = dot.x;
                    if (dot.x <= centerX) {
                        dot.x = centerX - marginPx;
                    } else {
                        dot.x = centerX + marginPx;
                    }
                    android.util.Log.i("BrailleView", "Crease Avoidance: shifted dot "
                            + dot.id + " from " + originalX + " to " + dot.x);
                }
            }
        }
    }

    // The preference key of the calibration layout saved for the current
    // orientation and screen size, so a layout calibrated in one orientation
    // is never replayed onto a differently sized keyboard.
    private String getDynamicCalibrationKey(int width, int height) {
        String baseKey = context.getString(R.string.pref_custom_dots_key);
        float ratio = width / height;
        String orientation = ratio >= 0.85f && ratio <= 1.15f ? "_SQUARE"
                : height > width ? "_PORTRAIT" : "_LANDSCAPE";
        float density = context.getResources().getDisplayMetrics().density;
        if (density <= 0.0f) {
            density = 1.0f;
        }
        int viewSmallestWidthDp = (int) (Math.min(width, height) / density);
        String size = viewSmallestWidthDp >= 600 ? "_LARGE" : "_SMALL";
        return baseKey + orientation + size;
    }

    /**
     * Load the pad for the given dimensions. When the dot positions are
     * locked, the calibration saved for this orientation and screen size is
     * restored; otherwise the default pad is used.
     */
    public void loadInitialPad(int w, int h) {
        boolean locked = Options.getBooleanPreference(context,
                R.string.pref_lock_calibration_key, false);
        if (locked) {
            int width = listener.autoRotate() ? w : Math.max(w, h);
            int height = listener.autoRotate() ? h : Math.min(w, h);
            String dynamicKey = getDynamicCalibrationKey(width, height);
            Coords[] savedDots = Pad.loadStringKey(context, width, height,
                    dynamicKey, listener.portraitSwap());
            if (savedDots != null && selectPad(savedDots, width, height)) {
                return;
            }
        }
        loadDefaultPad(w, h);
    }

    /**
     * Let the pad adjust its dot positions towards where the user actually
     * touches them, using the drift measured while typing. This is a no-op
     * when the dot positions are locked.
     */
    public void updateKeysAfterTyping() {
        if (pad != null) {
            pad.updateKeys(context, listener.portraitSwap());
        }
    }

    /** Clear all transient touch state after a gesture completes. */
    public void reset() {
        for (int i = 0; i < dotsDown.length; i++) {
            dotsDown[i] = null;
        }
        handledSwipe = false;
    }

    public boolean hasPad() {
        return pad != null;
    }

    public boolean hasPressedDots() {
        return pressedDotString() != NO_DOTS;
    }

    /** The bit string of the dots currently pressed. */
    public byte getPressedDotString() {
        return pressedDotString();
    }

    public boolean isHandledSwipe() {
        return handledSwipe;
    }

    /**
     * Try to resolve the gesture into a multi-finger swipe. When a swipe is
     * found the controller marks the gesture as handled and returns it;
     * otherwise {@link Swipe#NONE} is returned.
     */
    public Swipe resolveMultiFingerSwipe(boolean swap) {
        if (handledSwipe || pad == null) {
            return Swipe.NONE;
        }
        Swipe swipe = pad.getMultiFingerSwipe(dotsDown, swap);
        if (swipe != Swipe.NONE) {
            handledSwipe = true;
        }
        return swipe;
    }

    /**
     * Try to resolve the gesture into a single-finger swipe. When a swipe is
     * found the controller marks the gesture as handled and returns it;
     * otherwise {@link Swipe#NONE} is returned.
     */
    public Swipe resolveSingleSwipe(boolean swap) {
        if (handledSwipe || pad == null) {
            return Swipe.NONE;
        }
        Swipe swipe = handledSwipeAction(dotsDown, swap);
        if (swipe != Swipe.NONE) {
            handledSwipe = true;
        }
        return swipe;
    }

    /** Sort the pressed dots into their actual Braille positions. */
    public void setDots() {
        if (pad == null) {
            return;
        }
        // Sort the dots into their actual positions eg. dotsDown[0] = dot1
        // dotsDown[1] = dot 2 etc.
        // Previous ordering is based on the order that fingers hit the screen.
        dotsDown = pad.getBrailleDots(dotsDown, listener.dots());
    }

    /** Clear the dots recorded by the previous three-finger touch. */
    public void clearLastDotList() {
        lastDotList.clear();
    }

    /** Update the dots 7 and 8 state. */
    public void setDotsSevenEight(boolean dot7, boolean dot8) {
        if (!dot7 && !dot8) {
            this.dot7 = dot7;
            this.dot8 = dot8;
        }

        if (dot7) {
            this.dot7 = dot7;
        }
        if (dot8) {
            this.dot8 = dot8;
        }
    }

    /** The name of the active pad type, for the diagnostic log. */
    public String getPadTypeName() {
        return pad != null ? pad.getKeyboardType().name() : "none";
    }

    /**
     * A description of the current swipe state for the diagnostic log (swap
     * and invert flags plus the raw and used direction of every finger), or
     * "pad=null" when no pad is active.
     */
    public String describeSwipe(boolean swap) {
        if (pad == null) {
            return "pad=null";
        }
        return pad.getSwipeDiagnostics(dotsDown, swap);
    }

    /** The keys of the active pad, limited to the dots in use. */
    public List<Coords> getKeys() {
        List<Coords> keys = new ArrayList<Coords>();
        List<Coords> padKeys = pad.getKeys();
        int dots = listener.dots();
        // should always be == dots, but handle errors cleanly
        if (dots == -1) {
            dots = padKeys.size();
        }
        int dotsInUse = Math.min(padKeys.size(), dots);
        keys.addAll(padKeys.subList(0, dotsInUse));
        return keys;
    }

    /** Set the pad using a default pad for the given dimensions. */
    public void loadDefaultPad(int w, int h) {
        int width = listener.autoRotate() ? w : Math.max(w, h);
        int height = listener.autoRotate() ? h : Math.min(w, h);
        if (!setDefaultPad(w, h, width, height)) {
            listener.speak(R.string.keyboard_error);
        }
    }

    private boolean updatePointer(int id, int x, int y, boolean reset) {
        for (int i = 0; i < dotsDown.length; i++) {
            if (dotsDown[i] != null) {
                if (dotsDown[i].id == id) {
                    if (reset) {
                        dotsDown[i] = new Coords(id, x, y);
                    }
                    dotsDown[i].setSecondCords(x, y);
                    return true;
                }
            }
        }
        return false;
    }

    // Set the pad using a default pad.
    private boolean setDefaultPad(int w, int h, int padWidth, int padHeight) {
        boolean useEightDots = listener.useEightDots();
        try {
            pad = PadUtilities.displayDefaultPad(context, padWidth, padHeight,
                    h > w && !listener.autoRotate(), useEightDots);
            return true;
        } catch (IllegalArgumentException e) {
            // handled below
        }
        return false;
    }

    // Display a pad according to the possitioning of the fingers (user
    // calibration)
    private boolean selectPad(Coords[] dots, int width, int height) {
        boolean useEightDots = listener.useEightDots();
        try {
            pad = PadUtilities.selectPad(context, dots, width, height,
                    listener.portraitSwap(), useEightDots);
            return true;
        } catch (IllegalArgumentException e) {
            // handled below
        }
        return false;
    }

    private byte pressedDotString() {
        byte mask = 1;
        byte value = 0;

        // See what dots of the first six are pressed.
        for (int i = 0; i < dotsDown.length - 2; i++) {
            if (dotsDown[i] != null) {
                // it's present so set the bit in the bitstring.
                value |= mask;
            }
            mask <<= 1;
        }

        // special case for setting dots 7 and 8.
        // They can be activated by pressing them on the screen or using a
        // swipe gesture.
        if (dot7 || dotsDown[6] != null) {
            value |= mask;
        }
        mask <<= 1;
        if (dot8 || dotsDown[7] != null) {
            value |= mask;
        }
        return value;
    }

    private Swipe handledSwipeAction(Coords[] coords, boolean swap) {
        try {
            return pad.getSwipe(coords, swap);
        } catch (NullPointerException npe) { // can be null if invalidate
            // somehow is called
        }
        return Swipe.NONE;
    }

    private static int countDotsDown(Coords[] dots) {
        int count = 0;
        for (Coords coords : dots) {
            if (coords != null) {
                ++count;
            }
        }
        return count;
    }
}
