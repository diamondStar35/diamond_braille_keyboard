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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.util.TypedValue;

/**
 * Describes a Braille keyboard.
 * 
 * There are various types of Braille keyboards and you are free to implement
 * your own as long as they can provide the arrangement of the dots and the
 * swiping implementation.
 * 
 * See HorizontalPad or VerticalPad for examples.
 * 
 */
public abstract class Pad {
    private static final int DOT_FOUR = 3;
    private static final int DOT_SEVEN = 6;
    private static final int MAX_DOTS = 8;

    private final int SWIPE_MARGINE;

    public final int padString;

    private final int swipeThreshold;

    public enum Column {
        LEFT, RIGHT;
    }

    protected final List<Coords> keys = new ArrayList<Coords>(MAX_DOTS);

    protected static final Comparator<Coords> comparatorX = new Comparator<Coords>() {
        @Override
        public int compare(Coords o1, Coords o2) {
            return (int) (o1.x - o2.x);
        }
    };

    protected static final Comparator<Coords> comparatorY = new Comparator<Coords>() {
        @Override
        public int compare(Coords o1, Coords o2) {
            return (int) (o1.y - o2.y);
        }
    };

    protected final int viewWidth;
    protected final int viewHeight;

    protected final boolean invert;

    // The drift measured for each dot from the finger positions matched to
    // it while typing, consumed by updateKeys() when the dot positions are
    // not locked.
    private final Coords.XY[] differences = new Coords.XY[MAX_DOTS];

    public Pad(Context context, Coords[] coords, int width, int height,
            int padString, boolean invert) {
        SWIPE_MARGINE = Integer.parseInt(Options.getStringPreference(context,
                R.string.pref_swipe_sensitivity_key,
                context.getString(R.string.default_swipe_sensitivity)));
        this.invert = invert;
        this.padString = padString;
        viewHeight = height;
        viewWidth = width;
        swipeThreshold = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, SWIPE_MARGINE, context
                        .getResources().getDisplayMetrics());
        for (Coords coord : coords) {
            keys.add(coord);
        }
    }

    /** The type of this pad, used to remember the last calibrated layout. */
    public abstract Options.KeyboardType getKeyboardType();

    public Coords[] getBrailleDots(Coords[] coords, int dots) {
        dots = dots > keys.size() ? keys.size() : dots;
        if (dots > keys.size()) {
            throw new IllegalArgumentException("Requires " + dots
                    + " keys only " + keys.size() + " set");
        }
        int[] total = getAverageLeftRightColumns();
        List<Coords> list = new ArrayList<Coords>();
        for (Coords coord : coords) {
            if (coord != null) {
                list.add(coord);
            }
        }
        Coords[] brailleDots = new Coords[coords.length];
        while (list.size() > 0) {
            matchDotToCoord(dots, total, list, brailleDots);
        }
        return brailleDots;
    }

    private void matchDotToCoord(int dots, int[] total, List<Coords> list,
            Coords[] outputDots) {
        Coords coords = list.remove(0);
        int leftRight = Math.abs(coords.x - total[0]) <= Math.abs(coords.x
                - total[1]) ? 0 : 1;
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < dots; i++) {
            int j = getColumn(i) == Column.LEFT ? 0 : 1;
            if (j == leftRight) {
                int result = getDistance(i, coords);
                if (result < bestDistance) {
                    if (outputDots[i] != null) {
                        if (result < getDistance(i, outputDots[i])) {
                            list.add(outputDots[i]);
                            outputDots[i] = null;
                            // The displaced dot no longer belongs to this
                            // key, so its measured drift does not apply.
                            differences[i] = null;
                        } else {
                            continue;
                        }
                    }
                    bestDistance = result;
                    best = i;
                }
            }
        }
        // sometimes we may not resolve a dot eg. 4 fingers in the left column
        // of a six dot keyboard. Also seems to crash when typing really
        // quickly. Could have something to do with wrapid touch events, but
        // unsure.
        if (best >= 0) {
            // Record where this finger landed on the dot; updateKeys() uses
            // these landings to nudge the dots towards the user's typing
            // positions.
            differences[best] = keys.get(best).getUpdate(coords.x, coords.y);
            outputDots[best] = coords;
        }
    }

    private int getDistance(int key, Coords coord) {
        double horizontal = Math.pow(Math.abs(coord.x - keys.get(key).x), 2);
        double vertical = Math.pow(Math.abs(coord.y - keys.get(key).y), 2);
        int result = (int) Math.sqrt(horizontal + vertical);
        return result;
    }

    private int[] getAverageLeftRightColumns() {
        int total[] = { 0, 0 };
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i) != null) {
                int j = getColumn(i) == Column.LEFT ? 0 : 1;
                total[j] += keys.get(i).x;
            }
        }
        total[0] /= (keys.size() / 2);
        total[1] /= (keys.size() / 2);
        return total;
    }

    protected Column getColumn(int key) {
        return key < DOT_FOUR || key == DOT_SEVEN ? Column.LEFT : Column.RIGHT;
    }

    /**
     * Nudge the dots towards where the user actually touches them, based on
     * the drift measured while typing. The whole left hand column and the
     * whole right hand column move together so the relative geometry of the
     * dots is preserved. Each correction applies only a fifth of the measured
     * drift so the dots converge slowly, and corrections larger than 40 px
     * are ignored because they mean the hands were repositioned rather than
     * the dots drifting. This is a no-op when the dot positions are locked.
     *
     * @param portrait true when the keyboard is used on a portrait screen
     *            held in landscape.
     */
    public void updateKeys(Context context, boolean portrait) {
        if (Options.getBooleanPreference(context,
                R.string.pref_lock_calibration_key, false)) {
            return;
        }
        Coords.XY[] newDiff = { new Coords.XY(0, 0), new Coords.XY(0, 0) };
        int[] count = { 0, 0 };
        for (int i = 0; i < differences.length; i++) {
            int j = getColumn(i) == Column.LEFT ? 0 : 1;
            if (differences[i] != null) {
                count[j]++;
                newDiff[j].x += differences[i].x;
                newDiff[j].y += differences[i].y;
                differences[i] = null;
            }
        }
        for (int column = 0; column < 2; column++) {
            if (count[column] > 0) {
                newDiff[column].x /= count[column];
                newDiff[column].y /= count[column];
                double dist = Math.sqrt(newDiff[column].x
                        * newDiff[column].x + newDiff[column].y
                        * newDiff[column].y);
                if (dist <= 40.0d) {
                    newDiff[column].x = Math.round(newDiff[column].x * 0.2f);
                    newDiff[column].y = Math.round(newDiff[column].y * 0.2f);
                } else {
                    newDiff[column].x = 0;
                    newDiff[column].y = 0;
                }
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            int j = getColumn(i) == Column.LEFT ? 0 : 1;
            if (keys.get(i) != null) {
                keys.get(i).update(newDiff[j]);
            }
        }
    }

    public Swipe getMultiFingerSwipe(Coords[] coords, boolean swap) {
        int fingersDown = 0;  // physical down
        int fingersUp = 0;    // physical up
        int fingersLeft = 0;  // physical left
        int fingersRight = 0; // physical right
        for (int i = coords.length - 1; i >= 0; i--) {
            if (coords[i] != null) {
                // The direction is already described in the physical direction
                // of the user, so each finger is counted by the way it is
                // swiped. When the keyboard is inverted the directions are
                // rotated 180 degrees so the swipes keep matching the
                // physical movements on the flipped keyboard.
                byte dir = coords[i].swipeDirection(swipeThreshold,
                        swipeThreshold, swap, invert);
                if (dir == Coords.DOT_DOWN) {
                    fingersDown++;
                } else if (dir == Coords.DOT_LEFT) {
                    fingersLeft++;
                } else if (dir == Coords.DOT_UP) {
                    fingersUp++;
                } else if (dir == Coords.DOT_RIGHT) {
                    fingersRight++;
                }
            }
        }
        if (fingersDown == 2) {
            return Swipe.TWO_FINGERS_DOWN;
        }
        if (fingersDown == 3) {
            return Swipe.THREE_FINGERS_DOWN;
        }
        if (fingersUp == 2) {
            return Swipe.TWO_FINGERS_UP;
        }
        if (fingersUp == 3) {
            return Swipe.THREE_FINGERS_UP;
        }
        if (fingersLeft == 2) {
            return Swipe.TWO_FINGERS_LEFT;
        }
        if (fingersLeft == 3) {
            return Swipe.THREE_FINGERS_LEFT;
        }
        if (fingersRight == 2) {
            return Swipe.TWO_FINGERS_RIGHT;
        }
        if (fingersRight == 3) {
            return Swipe.THREE_FINGERS_RIGHT;
        }
        return Swipe.NONE;
    }

    protected Swipe getGenericSwipeAction(Coords[] coords, boolean swap) {
        final int REQUIRED_BITS = 3;
        StringBuilder sb = new StringBuilder();
        for (int i = coords.length - 1; i >= 0; i--) {
            String bitString = "";
            if (coords[i] != null) {
                // When the keyboard is inverted the raw direction is rotated
                // 180 degrees (see {@link Coords#swipeDirection(int, int,
                // boolean, boolean)}) so the bit string keeps describing the
                // physical swipe on the flipped keyboard.
                byte direction = coords[i].swipeDirection(swipeThreshold,
                        swipeThreshold, swap, invert);
                bitString = Integer.toBinaryString(direction);
            }
            // zero padding
            for (int j = 0; j < REQUIRED_BITS - bitString.length(); j++) {
                sb.append("0");
            }
            sb.append(bitString);
        }

        for (int i = 0; i < sb.length(); i += 3) {
            if (!sb.substring(i, i + 3).equals("000")
                    && !sb.substring(i, i + 3).equals("111")) {
                return Swipe.valueOf(Integer.parseInt(sb.toString(), 2));
            }
        }
        return Swipe.NONE;
    }

    abstract Swipe getSwipe(Coords[] coords, boolean swap);

    /**
     * A description of the current swipe state for the diagnostic log: whether
     * the screen axes are swapped, whether the keyboard is inverted, and for
     * each finger the raw swipe direction before the keyboard inversion is
     * applied together with the direction actually used. This lets reports of
     * "gestures are not inverted" be traced back to exactly what the keyboard
     * detected.
     */
    public String getSwipeDiagnostics(Coords[] coords, boolean swap) {
        StringBuilder sb = new StringBuilder();
        sb.append("swap=").append(swap).append(",invert=").append(invert);
        sb.append(",fingers=[");
        boolean first = true;
        for (int i = coords.length - 1; i >= 0; i--) {
            if (coords[i] != null) {
                if (!first) {
                    sb.append(' ');
                }
                first = false;
                byte raw = coords[i].swipeDirection(swipeThreshold,
                        swipeThreshold, swap, false);
                byte direction = coords[i].swipeDirection(swipeThreshold,
                        swipeThreshold, swap, invert);
                sb.append("id").append(coords[i].id)
                        .append(":raw=").append(directionName(raw))
                        .append(",dir=").append(directionName(direction));
            }
        }
        sb.append(']');
        return sb.toString();
    }

    // The name of a swipe direction, for the diagnostic log.
    private static String directionName(byte direction) {
        switch (direction) {
        case Coords.DOT_LEFT:
            return "LEFT";
        case Coords.DOT_RIGHT:
            return "RIGHT";
        case Coords.DOT_UP:
            return "UP";
        case Coords.DOT_DOWN:
            return "DOWN";
        default:
            return "NONE";
        }
    }

    protected static int getXGap(List<Coords> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i).x;
        }
        return getGap(array);
    }

    protected static int getYGap(List<Coords> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i).y;
        }
        return getGap(array);
    }

    private static int getGap(int[] array) {
        int[] gaps = new int[array.length - 1];
        for (int i = 1; i < array.length; i++) {
            gaps[i - 1] = Math.abs(array[i] - array[i - 1]);
        }
        int difference = 0;
        for (int gap : gaps) {
            difference += gap;
        }
        return difference / gaps.length;
    }

    protected void save(Context context, int prefKey, boolean portrait) {
        Set<String> points = new HashSet<String>();
        for (Coords key : keys) {
            points.add(savePointString(key, portrait));
        }
        Options.writeStringSetPreference(context, prefKey, points);
    }

    // Save the dot positions under a preference whose key is only known at
    // runtime (the per orientation and screen size calibration layout).
    protected void saveStringKey(Context context, String prefKey,
            boolean portrait) {
        Set<String> points = new HashSet<String>();
        for (Coords key : keys) {
            points.add(savePointString(key, portrait));
        }
        Options.writeStringSetPreferenceStringKey(context, prefKey, points);
    }

    // Save the given calibrated dots, rather than the arranged keys, under a
    // runtime preference key. The dots are re-arranged when they are loaded.
    protected void saveStringKey(Context context, String prefKey,
            boolean portrait, Coords[] customDots) {
        Set<String> points = new HashSet<String>();
        for (Coords key : customDots) {
            points.add(savePointString(key, portrait));
        }
        Options.writeStringSetPreferenceStringKey(context, prefKey, points);
    }

    public static Coords[] load(Context context, int viewWidth, int viewHeight,
            int prefKey, boolean portrait) {
        Set<String> points = Options.getStringSetPreference(context, prefKey,
                null);
        if (points != null) {
            int[] centre = { portrait ? viewHeight / 2 : viewWidth / 2,
                    portrait ? viewWidth / 2 : viewHeight / 2 };
            Coords[] coords = new Coords[points.size()];
            int i = 0;
            for (String point : points) {
                coords[i++] = new Coords(centre, point);
            }
            return coords;
        }
        return null;
    }

    // Load the dot positions stored under a runtime preference key (the per
    // orientation and screen size calibration layout). The stored points are
    // returned in the order they were saved, identified by their dot ids.
    public static Coords[] loadStringKey(Context context, int viewWidth,
            int viewHeight, String prefKey, boolean portrait) {
        Set<String> points = Options.getStringSetPreferenceStringKey(context,
                prefKey, null);
        if (points != null && !points.isEmpty()) {
            int[] centre = { portrait ? viewHeight / 2 : viewWidth / 2,
                    portrait ? viewWidth / 2 : viewHeight / 2 };
            Coords[] coords = new Coords[points.size()];
            int i = 0;
            for (String point : points) {
                coords[i++] = new Coords(centre, point);
            }
            List<Coords> sorted = new ArrayList<Coords>(Arrays.asList(coords));
            Collections.sort(sorted, new Comparator<Coords>() {
                @Override
                public int compare(Coords c1, Coords c2) {
                    return c1.id - c2.id;
                }
            });
            return sorted.toArray(new Coords[sorted.size()]);
        }
        return null;
    }

    private String savePointString(Coords key, boolean portrait) {
        int centre[] = { portrait ? viewHeight / 2 : viewWidth / 2,
                portrait ? viewWidth / 2 : viewHeight / 2 };
        StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(key.id));
        sb.append(',');
        sb.append(String.valueOf(key.x - centre[0]));
        sb.append(',');
        sb.append(String.valueOf(key.y - centre[1]));
        return sb.toString();
    }

    public List<Coords> getKeys() {
        return keys;
    }

    public void makeSlateLayout() {
        int right = 3;
        for (int i = 0; i < right; i++) {
            Collections.swap(keys, i, right + i);
        }

        if (keys.size() == 8) {
            Collections.swap(keys, 6, 7);
        }
    }

    public void swapTopBottom() {
        Collections.swap(keys, 0, 2);
        Collections.swap(keys, 3, 5);
    }
}
