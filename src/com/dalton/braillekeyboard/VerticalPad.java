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
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.util.TypedValue;

/**
 * Implementation of the VerticalPad style Braille keyboard.
 * 
 * This style of keyboard is designed for smart phone or smaller screens. Dots
 * are arranged as follows. Two columns of dots from top to bottom left column
 * is 1, 2, 3 and right is 4, 5 and 6. Dots 7 and 8 are below dots 3 and 6 if
 * present respectively. Users generally hold the screen facing away from them
 * to use this layout.
 */
public class VerticalPad extends Pad {

    private static final int MAX_HORIZONTAL_DISTANCE = 120; // 2/3 inch
    private static final int MAX_VERTICAL_DISTANCE = 80; // 0.5 inch

    public VerticalPad(Context context, Coords[] coords, int width, int height,
            boolean portrait, boolean invert, boolean useEightDots) {
        super(context, coords, width, height, R.string.pad_vertical, invert);
        int prefKey = getPrefKey(portrait, invert);
        save(context, prefKey, portrait);
        sortKeys(keys, portrait);
        if (useEightDots) {
            insertSpecialDots(portrait);
        }
    }

    public static int getPrefKey(boolean portrait, boolean invert) {
        if (portrait && !invert) {
            return R.string.pref_keyboard_save_vertical_portrait_key;
        } else if (!portrait && !invert) {
            return R.string.pref_keyboard_save_vertical_landscape_key;
        } else if (portrait && invert) {
            return R.string.pref_keyboard_save_vertical_portrait_invert_key;
        } else {
            return R.string.pref_keyboard_save_vertical_landscape_invert_key;
        }
    }

    public static Pad displayDefaultPad(Context context, int width, int height,
            boolean portrait, boolean invert, boolean useEightDots) {
        int centreWidth = width / 2;
        int offsetWidth = Math.min(width / 5, (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, MAX_HORIZONTAL_DISTANCE, context
                        .getResources().getDisplayMetrics()));
        int offsetHeight = Math.min(height / 4, (int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        MAX_VERTICAL_DISTANCE, context.getResources()
                                .getDisplayMetrics()));
        Coords[] coords = new Coords[6];
        // compute the coordinates
        int y = offsetHeight;
        for (int i = 0; i < coords.length; i++) {
            if (i % (coords.length / 2) == 0) {
                y = offsetHeight;
            }
            int x;
            if (i < coords.length / 2) {
                x = centreWidth - offsetWidth;
            } else {
                x = centreWidth + offsetWidth;
            }
            coords[i] = new Coords(x, y);
            y += offsetHeight;
        }
        return new VerticalPad(context, coords, width, height, portrait,
                invert, useEightDots);
    }

    // Assign the six finger positions to the dots.  There are exactly three
    // dots per column, so the x sorted positions are simply split in half:
    // the three leftmost fingers become one column and the three rightmost
    // the other, each ordered top to bottom.  This is robust to fingers that
    // don't fall neatly into two well separated columns; the old min/max with
    // a fixed error margin could reject the calibration or, when both hands
    // rested at similar heights, assign the same fingers to both columns and
    // scatter the dots.
    //
    // The invert setting mirrors the layout in every orientation: dots 1-3
    // land on the right column exactly when portrait and invert agree, and
    // the vertical reversal used for the inverted portrait layout is only
    // applied there.  This also makes calibrations respect the invert setting
    // in landscape, which the old code skipped.
    private void sortKeys(List<Coords> coords, boolean portrait) {
        List<Coords> sorted = new ArrayList<Coords>(coords);
        Collections.sort(sorted, comparatorX);
        List<Coords> lowX = new ArrayList<Coords>(sorted.subList(0, 3));
        List<Coords> highX = new ArrayList<Coords>(sorted.subList(3, 6));
        Collections.sort(lowX, comparatorY);
        Collections.sort(highX, comparatorY);
        if (portrait && invert) {
            Collections.reverse(lowX);
            Collections.reverse(highX);
        }
        keys.clear();
        if (invert == portrait) {
            keys.addAll(highX);
            keys.addAll(lowX);
        } else {
            keys.addAll(lowX);
            keys.addAll(highX);
        }
    }

    private void insertSpecialDots(boolean portrait) {
        int yGapLeft = getYGap(keys.subList(0, 3));
        int yGapRight = getYGap(keys.subList(3, 6));
        int leftX = (Collections.min(keys.subList(0, 3), comparatorX).x + Collections
                .max(keys.subList(0, 3), comparatorX).x) / 2;
        int leftY = invert && portrait ? keys.get(2).y - yGapLeft
                : keys.get(2).y + yGapLeft;
        int rightX = (Collections.min(keys.subList(3, 6), comparatorX).x + Collections
                .max(keys.subList(3, 6), comparatorX).x) / 2;
        int rightY = invert && portrait ? keys.get(5).y - yGapRight : keys
                .get(5).y + yGapRight;
        keys.add(new Coords(leftX, leftY > viewHeight ? viewHeight : leftY));
        keys.add(new Coords(rightX, rightY > viewHeight ? viewHeight : rightY));
    }

    public static int getColumn(List<Coords> coords, Column column) {
        if (column == Column.LEFT) {
            return (int) Collections.max(coords, comparatorX).x;
        } else {
            return (int) Collections.min(coords, comparatorX).x;
        }
    }

    public static boolean isInKeyColumn(Coords coord, int compareValue,
            int errorMargin) {
        return Math.abs(compareValue - coord.x) < errorMargin;
    }

    @Override
    public Swipe getSwipe(Coords[] coords, boolean swap) {
        return getGenericSwipeAction(coords, swap);
    }

    @Override
    public Options.KeyboardType getKeyboardType() {
        return Options.KeyboardType.VERTICAL;
    }
}
