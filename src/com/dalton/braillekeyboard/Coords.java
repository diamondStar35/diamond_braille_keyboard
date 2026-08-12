package com.dalton.braillekeyboard;

import java.util.LinkedList;
import java.util.Queue;

/**
 * A point on the keyboard together with its swipe direction, computed from
 * the difference between the touch position and the position when the finger
 * went down.
 *
 * <p>This was extracted from {@link Pad} so it can be used by the pad
 * implementations and the keyboard view as a plain data type.
 */
public class Coords {
    public static final byte DOT_NONE = 7;
    public static final byte DOT_LEFT = 1;
    public static final byte DOT_RIGHT = 2;
    public static final byte DOT_DOWN = 3;
    public static final byte DOT_UP = 4;

    // The number of finger landings kept to estimate where the user intends
    // to touch each dot, used by the automatic drift correction.
    private static final int HISTORY_SIZE = 5;

    public final int id;

    public int x;
    public int y;

    private int secondX;
    private int secondY;

    // Rolling history of the finger positions matched to this dot, newest
    // last, used to estimate the drift of the dot while typing.
    private final Queue<XY> coordHistory;

    public Coords(int x, int y) {
        this(-1, x, y);
    }

    public Coords(int id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.secondX = x;
        this.secondY = y;
        coordHistory = new LinkedList<XY>();
        coordHistory.add(new XY(x, y));
    }

    public Coords(int[] centre, String point) {
        String[] components = point.split(",");
        x = centre[0] + Integer.parseInt(components[1]);
        y = centre[1] + Integer.parseInt(components[2]);
        id = Integer.parseInt(components[0]);
        coordHistory = new LinkedList<XY>();
        coordHistory.add(new XY(x, y));
    }

    public int getSecondX() {
        return secondX;
    }

    public int getSecondY() {
        return secondY;
    }

    public void setSecondCords(int x, int y) {
        secondX = x;
        secondY = y;
    }

    /**
     * Record a finger landing on this dot and return the difference between
     * the exponentially weighted average of the recent landings and the
     * current position of the dot. The caller uses this difference to nudge
     * the dot towards where the user actually touches.
     */
    public XY getUpdate(int x, int y) {
        if (coordHistory.size() >= HISTORY_SIZE) {
            coordHistory.poll();
        }
        coordHistory.add(new XY(x, y));
        return getXYDifference();
    }

    // The exponentially weighted average of the recorded landings, where the
    // most recent landing carries the most weight, minus the dot position.
    private XY getXYDifference() {
        double total = 0.0d;
        double i = Math.pow(2.0d, coordHistory.size());
        double x = 0.0d;
        double y = 0.0d;
        for (XY item : coordHistory) {
            i /= 2.0d;
            x += (1.0d / i) * item.x;
            y += (1.0d / i) * item.y;
            total += 1.0d / i;
        }
        int newX = (int) (x / total);
        int newY = (int) (y / total);
        return new XY(newX - this.x, newY - this.y);
    }

    /** Shift the dot by the given difference. */
    public void update(XY diff) {
        this.x += diff.x;
        this.y += diff.y;
    }

    /** A 2D difference used by the calibration drift correction. */
    public static class XY {
        public int x;
        public int y;

        public XY(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * The direction of the swipe from the touch position: right or left for
     * horizontal swipes and up or down for vertical ones.
     *
     * @param swap true when the keyboard is used on a portrait screen held in
     *            landscape, in which case the screen axes are swapped.
     * @param invert true when the keyboard itself is inverted (the "Invert
     *            keyboard" setting), in which case the detected direction is
     *            rotated 180 degrees so the gestures keep matching the
     *            physical swipes on the flipped keyboard.
     */
    public byte swipeDirection(int xSwipeThreshold, int ySwipeThreshold,
            boolean swap, boolean invert) {
        byte swipe = swipeDirection(xSwipeThreshold, ySwipeThreshold, swap);
        if (!invert) {
            return swipe;
        }
        switch (swipe) {
        case DOT_LEFT:
            return DOT_RIGHT;
        case DOT_RIGHT:
            return DOT_LEFT;
        case DOT_DOWN:
            return DOT_UP;
        case DOT_UP:
            return DOT_DOWN;
        default:
            return swipe;
        }
    }

    // The direction of the swipe without the keyboard inversion applied,
    // used by {@link #swipeDirection(int, int, boolean, boolean)}.
    private byte swipeDirection(int xSwipeThreshold, int ySwipeThreshold,
            boolean swap) {
        int xDiff = x - secondX;
        int yDiff = y - secondY;
        if (Math.abs(xDiff) > Math.abs(yDiff)) {
            if (xDiff > xSwipeThreshold) {
                return swap ? DOT_LEFT : DOT_RIGHT;
            } else if (xDiff < (0 - xSwipeThreshold)) {
                return swap ? DOT_RIGHT : DOT_LEFT;
            }
        } else if (Math.abs(yDiff) >= Math.abs(xDiff)) {
            // Mirrored like the horizontal branch above: when the keyboard
            // is used on a portrait screen held in landscape (swap), the
            // screen axes are swapped, so the vertical branch must be
            // mirrored exactly like the horizontal one to keep the direction
            // consistent in every configuration.
            if (yDiff > ySwipeThreshold) {
                return swap ? DOT_DOWN : DOT_UP;
            } else if (yDiff < (0 - ySwipeThreshold)) {
                return swap ? DOT_UP : DOT_DOWN;
            }
        }
        return DOT_NONE;
    }

}
