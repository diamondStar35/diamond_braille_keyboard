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
 * Implements a horizontal style Braille Pad.
 * 
 * This is mor like a conventional perkins Brailler where dots are arranged from
 * left to right. Dot 3 on the far left, then dot 2, dot 1, and on the right
 * hand they span 4, 5, 6 where 6 is the far right dot.
 * 
 * This implements the appropriate methods to facilitate loading, drawing and
 * saving/restoring a pad of this nature.
 */
public class HorizontalPad extends Pad {
    private static final int MAX_HORIZONTAL_DISTANCE = 80; // 2/3 inch;

    public HorizontalPad(Context context, Coords[] coords, int width,
            int height, boolean portrait, boolean invert, boolean useEightDots) {
        super(context, coords, width, height, R.string.pad_horizontal, invert);
        save(context, getPrefKey(portrait, invert), portrait);
        sortKeys(keys, useEightDots);
    }

    public static int getPrefKey(boolean portrait, boolean invert) {
        if (portrait && !invert) {
            return R.string.pref_keyboard_save_horizontal_portrait_key;
        } else if (!portrait && !invert) {
            return R.string.pref_keyboard_save_horizontal_landscape_key;
        } else if (portrait && invert) {
            return R.string.pref_keyboard_save_horizontal_portrait_invert_key;
        } else {
            return R.string.pref_keyboard_save_horizontal_landscape_invert_key;
        }
    }

    public static Pad displayDefaultPad(Context context, int width, int height,
            boolean portrait, boolean invert, boolean useEightDots) {
        int offsetWidth = Math.min(width / 8, (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, MAX_HORIZONTAL_DISTANCE, context
                        .getResources().getDisplayMetrics()));
        Coords[] coords = new Coords[6];
        // compute the coordinates
        int y = height / 2;
        int x = offsetWidth;
        for (int i = 0; i < coords.length / 2; i++) {
            coords[i] = new Coords(x, y);
            x += offsetWidth;
        }
        x = width - offsetWidth;
        for (int i = coords.length / 2; i < coords.length; i++) {
            coords[i] = new Coords(x, y);
            x -= offsetWidth;
        }
        return new HorizontalPad(context, coords, width, height, portrait,
                invert, useEightDots);
    }

    private void insertSpecialDots() {
        int xGapLeft = getXGap(keys.subList(0, 3));
        int yGapLeft = getYGap(keys.subList(0, 3));
        int xGapRight = getXGap(keys.subList(3, 6));
        int yGapRight = getYGap(keys.subList(3, 6));
        int leftX = keys.get(2).x - xGapLeft;
        int leftY = keys.get(2).y - yGapLeft;
        int rightX = keys.get(5).x + xGapRight;
        int rightY = keys.get(5).y - yGapRight;
        keys.add(new Coords(leftX < 0 ? 0 : leftX, leftY < 0 ? 0 : leftY));
        keys.add(new Coords(rightX > viewWidth ? viewWidth : rightX,
                rightY < 0 ? 0 : rightY));
    }

    // Arrange the finger positions into the Braille dots.  The six (or
    // eight) positions are split by X into a left group and a right group.
    // When the keyboard is inverted the groups are swapped so dots 1-3 sit
    // on the physical right (the reverse landscape hold) and dots 4-6 on the
    // left, matching the tabletop pad of the reference fork and the vertical
    // pad in every orientation.  The old portrait-only flip put the dots the
    // wrong way round for the inverted keyboard and broke the gesture
    // attribution on this pad.
    private void sortKeys(List<Coords> coords, boolean useEightDots) {
        Collections.sort(coords, comparatorX);
        if (keys.size() == 6) {
            if (invert) {
                // Dots 1-3 take the three rightmost positions in order and
                // dots 4-6 the three leftmost reversed, so the keyboard
                // reads 6,5,4 | 1,2,3 from left to right.
                Coords d1 = keys.get(3);
                Coords d2 = keys.get(4);
                Coords d3 = keys.get(5);
                Coords d4 = keys.get(2);
                Coords d5 = keys.get(1);
                Coords d6 = keys.get(0);
                keys.set(0, d1);
                keys.set(1, d2);
                keys.set(2, d3);
                keys.set(3, d4);
                keys.set(4, d5);
                keys.set(5, d6);
            } else {
                // Dots 1-3 take the three leftmost positions (dot 3 on the
                // far left) and dots 4-6 the three rightmost.
                Collections.swap(keys, 0, 2);
            }
            if (useEightDots) {
                insertSpecialDots();
            }
        } else if (keys.size() == 8) {
            List<Coords> sorted = new ArrayList<Coords>(keys);
            keys.clear();
            if (invert) {
                // 8,6,5,4 | 1,2,3,7 from left to right.
                keys.add(sorted.get(4));
                keys.add(sorted.get(5));
                keys.add(sorted.get(6));
                keys.add(sorted.get(3));
                keys.add(sorted.get(2));
                keys.add(sorted.get(1));
                keys.add(sorted.get(7));
                keys.add(sorted.get(0));
            } else {
                // 7,3,2,1 | 4,5,6,8 from left to right.
                keys.add(sorted.get(3));
                keys.add(sorted.get(2));
                keys.add(sorted.get(1));
                keys.add(sorted.get(4));
                keys.add(sorted.get(5));
                keys.add(sorted.get(6));
                keys.add(sorted.get(0));
                keys.add(sorted.get(7));
            }
        }
    }

    @Override
    public Swipe getSwipe(Coords[] coords, boolean swap) {
        return getGenericSwipeAction(coords, swap);
    }

    @Override
    public Options.KeyboardType getKeyboardType() {
        return Options.KeyboardType.HORIZONTAL;
    }
}
