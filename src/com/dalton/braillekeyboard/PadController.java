package com.dalton.braillekeyboard;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;

/**
 * Owns the state of the Braille pad: the current {@link Pad}, the dots
 * currently pressed down, the two-handed calibration bookkeeping and the
 * swipe resolution, together with the logic that positions the pad.
 *
 * <p>Extracted from {@link com.dalton.braillekeyboard.View} so the view is
 * left with touch dispatch
 * dispatch, drawing and speech concerns while all pad state lives here. The
 * view feeds translated touch coordinates in through the {@code onPointer*}
 * methods and asks for swipes and dot patterns back.
 */
public class PadController {
    private static final byte NO_DOTS = 0;
    private static final long LONG_HOLD_DELAY = 1200;
    private static final int MAX_DOTS = 8;

    /** Reports pad events back to the owning view. */
    public interface Listener {
        /** Speak a string resource. */
        void speak(int stringRes);

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
    }

    private final Context context;
    private final Listener listener;

    private final List<Coords> lastDotList = new ArrayList<Coords>();
    private Coords[] dotsDown = new Coords[MAX_DOTS];
    private Pad pad;
    private long requiredTouchTime = 0;
    private boolean dot7;
    private boolean dot8;
    private boolean handledSwipe = false;

    public PadController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    /**
     * Record a finger going down. Also starts the timer for how long the
     * fingers must be held down before a calibration attempt is accepted.
     */
    public void onPointerDown(int id, int x, int y) {
        // store the time at which the user must hold their fingers down for
        // if they want to calibrate. Only record the time for the first touch.
        requiredTouchTime = requiredTouchTime == 0 ? System
                .currentTimeMillis() + LONG_HOLD_DELAY : requiredTouchTime;

        if (!updatePointer(id, x, y, true) && id < dotsDown.length) {
            // add a new unique dot to the list of dots that were pushed.
            dotsDown[id] = new Coords(id, x, y);
        }
    }

    /** Record a finger moving. */
    public void onPointerMove(int id, int x, int y) {
        updatePointer(id, x, y, false);
    }

    /**
     * Attempt to complete a two-handed calibration using the finger that was
     * just lifted. Returns true if the gesture was consumed by calibration.
     */
    public boolean setPad(int id, int width, int height) {
        final int TOTAL_DOTS = 6;
        final int ONE_SIDE = 3;
        // For whatever reason we won't be able to set a pad
        if (requiredTouchTime > System.currentTimeMillis()
                || countDotsDown(dotsDown) != ONE_SIDE) {
            return false;
        }
        if (lastDotList.size() != ONE_SIDE && lastDotList.size() != 0) {
            lastDotList.clear();
            return false;
        }

        // Add the first three dots to the current dot list.
        for (int i = 0; i < lastDotList.size(); i++) {
            Coords coord = lastDotList.get(i);
            dotsDown[ONE_SIDE + i] = new Coords(ONE_SIDE + id, coord.x,
                    coord.y);
        }

        if (countDotsDown(dotsDown) == TOTAL_DOTS) {
            setDotsSevenEight(false, false);
            Coords[] sixDots = new Coords[TOTAL_DOTS];

            for (int i = 0, j = 0; i < dotsDown.length
                    && j < sixDots.length; i++) {
                if (dotsDown[i] != null) {
                    int localX = dotsDown[i].getSecondX();
                    int localY = dotsDown[i].getSecondY();
                    sixDots[j++] = new Coords(localX, localY);
                }
            }
            boolean result;
            if ((result = selectPad(sixDots, width, height))) {
                listener.speak(pad.padString);
                listener.emitCalibrate();
            } else {
                listener.speak(R.string.keyboard_error);
                listener.emitCalibrate();
            }
            lastDotList.clear();
            reset();
            return result;
        } else {
            // Add the first three dots that have been tuched to a member
            // variable for reference on the second touch of three fingers
            for (int i = 0; i < dotsDown.length; i++) {
                if (dotsDown[i] != null) {
                    lastDotList.add(dotsDown[i]);
                    dotsDown[i] = null;
                }
            }
            listener.speak(R.string.keyboard_next_three);
            listener.emitCalibrate();
            return true;
        }
    }

    /** Clear all transient touch state after a gesture completes. */
    public void reset() {
        requiredTouchTime = 0;
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

    public long getRequiredTouchTime() {
        return requiredTouchTime;
    }

    public int getDotsDownCount() {
        return countDotsDown(dotsDown);
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
