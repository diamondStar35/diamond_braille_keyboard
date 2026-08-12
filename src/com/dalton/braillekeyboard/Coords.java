package com.dalton.braillekeyboard;

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

    public final int id;

    public int x;
    public int y;

    private int secondX;
    private int secondY;

    public Coords(int x, int y) {
        this(-1, x, y);
    }

    public Coords(int id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.secondX = x;
        this.secondY = y;
    }

    public Coords(int[] centre, String point) {
        String[] components = point.split(",");
        x = centre[0] + Integer.parseInt(components[1]);
        y = centre[1] + Integer.parseInt(components[2]);
        id = Integer.parseInt(components[0]);
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
     * The direction of the swipe from the touch position: right or left for
     * horizontal swipes and up or down for vertical ones.
     *
     * @param swap true when the keyboard is used on a portrait screen held in
     *            landscape, in which case the screen axes are swapped.
     */
    public byte swipeDirection(int xSwipeThreshold, int ySwipeThreshold,
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
