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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.os.Vibrator;
import android.util.Log;
import android.util.TypedValue;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.dalton.braillekeyboard.Options.KeyboardType;
import com.dalton.braillekeyboard.Coords;

/**
 * Static methods to load a default pad from android settings or to calculate
 * dot positions according to supplied dimensions.
 * 
 * There is also a method that handles calibration and selecting a keyboard
 * based on the position of fingers.
 * 
 * If you add a new Pad you'll need to update both of these methods so it can be
 * used.
 */
public class PadUtilities {
    private static final int ERROR_DP = 120; // 2/3 inch
    private static final int MAX_SCREEN_SIZE = 160 * 6; // 6 inch

    private static void setPadStyle(Context context, Pad pad) {
        String style = Options.getStringPreference(context,
                R.string.pref_keyboard_style_key,
                context.getString(R.string.pref_keyboard_style_normal_value));

        if (style.equals(context
                .getString(R.string.pref_keyboard_style_slate_value))) {
            pad.makeSlateLayout();
        } else if (style.equals(context
                .getString(R.string.pref_keyboard_style_top_bottom_value))) {
            pad.swapTopBottom();
        }
    }

    public static Pad selectPad(Context context, Coords[] coords, int width,
            int height, boolean portrait, boolean useEightDots) {
        Pad pad;
        boolean autoSet = Options.getBooleanPreference(context,
                R.string.pref_auto_match_keyboard_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_auto_match_keyboard_default)));
        boolean invert = Options.getBooleanPreference(context,
                R.string.pref_keyboard_invert_key, Boolean.parseBoolean(context
                        .getString(R.string.pref_keyboard_invert_default)));
        // Swipe directions follow the keyboard inversion XOR the independent
        // "Invert gestures" setting, so gestures can be flipped without
        // flipping the dot layout (and vice versa).
        boolean gestureInvert = invert ^ Options.getBooleanPreference(context,
                R.string.pref_invert_gestures_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_invert_gestures_default)));
        KeyboardType keyboard = KeyboardType
                .valueOf(Integer.parseInt(Options.getStringPreference(
                        context,
                        R.string.pref_default_keyboard_key,
                        context.getString(R.string.pref_default_keyboard_default))));
        List<Coords> keyList = Arrays.asList(coords);
        // Fix degenerate calibrations before building the pad, unless the
        // keyboard type is auto-detected from the finger positions.
        if (!autoSet && keyboard != KeyboardType.AUTO) {
            validateCalibrationGeometry(context, keyList, keyboard, width,
                    height);
        }
        if (autoSet || keyboard == KeyboardType.AUTO) {
            List<Coords> keys = Arrays.asList(coords);
            int left = VerticalPad.getColumn(keys, VerticalPad.Column.LEFT);
            int right = VerticalPad.getColumn(keys, VerticalPad.Column.RIGHT);
            int leftCount = 0;
            int rightCount = 0;
            int errorMargin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, ERROR_DP, context
                            .getResources().getDisplayMetrics());
            for (Coords coord : keys) {
                if (VerticalPad.isInKeyColumn(coord, left, errorMargin)) {
                    ++leftCount;
                } else if (VerticalPad.isInKeyColumn(coord, right, errorMargin)) {
                    ++rightCount;
                }
            }

            int expectedOneSide = useEightDots ? 4 : 3;
            if (leftCount == expectedOneSide && rightCount == expectedOneSide) {
                pad = new VerticalPad(context, coords, width, height, portrait,
                        invert, gestureInvert, useEightDots);
            } else {
                pad = new HorizontalPad(context, coords, width, height,
                        portrait, invert, gestureInvert, useEightDots);
            }
        } else {
            if (keyboard == KeyboardType.HORIZONTAL) {
                pad = new HorizontalPad(context, coords, width, height,
                        portrait, invert, gestureInvert, useEightDots);
            } else {
                pad = new VerticalPad(context, coords, width, height, portrait,
                        invert, gestureInvert, useEightDots);
            }
        }

        // Apply the keyboard style (slate / top-bottom) so the setting works
        // for calibrated keyboards too.  The calibration split is a clean
        // 3/3 column split (see VerticalPad.sortKeys), so the style transform
        // reorders the dots as requested instead of scattering them.
        setPadStyle(context, pad);
        return pad;
    }

    /**
     * Check a set of calibrated dots for degenerate geometries and fix them
     * in place. Two problems are corrected: a collapsed axis (all the dots in
     * a line) is re-expanded around the centre of the dots, and the two
     * columns of a horizontal layout that overlap or cross each other are
     * split apart. The user is told about the correction by a distinct
     * double vibration and an accessibility announcement.
     *
     * @throws IllegalArgumentException when there are too few dots.
     */
    public static void validateCalibrationGeometry(Context context,
            List<Coords> coords, KeyboardType type, int width, int height) {
        if (coords.size() < 6) {
            throw new IllegalArgumentException("Too few dots");
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Coords c : coords) {
            minX = Math.min(minX, c.x);
            maxX = Math.max(maxX, c.x);
            minY = Math.min(minY, c.y);
            maxY = Math.max(maxY, c.y);
        }
        int deltaX = maxX - minX;
        int deltaY = maxY - minY;
        boolean healed = false;
        if (type == KeyboardType.VERTICAL) {
            // The dots should form two columns; a nearly flat calibration
            // has no vertical separation to place dots 1-3 over 4-6, so
            // stretch the dots horizontally around their centre.
            if (deltaY < deltaX / 2) {
                int centerX = (deltaX / 2) + minX;
                float scale = (deltaY * 1.2f) / (deltaX > 0 ? deltaX : 1);
                if (scale > 1.0f || scale <= 0.0f) {
                    scale = 0.5f;
                }
                for (Coords c : coords) {
                    c.x = Math.round((c.x - centerX) * scale) + centerX;
                    c.setSecondCords(c.x, c.y);
                }
                healed = true;
            }
        } else {
            // A horizontal layout with no horizontal separation is stretched
            // vertically around the centre of the dots.
            if (deltaX < deltaY / 2) {
                int centerY = (deltaY / 2) + minY;
                float scale = (deltaX * 1.2f) / (deltaY > 0 ? deltaY : 1);
                if (scale > 1.0f || scale <= 0.0f) {
                    scale = 0.5f;
                }
                for (Coords c : coords) {
                    c.y = Math.round((c.y - centerY) * scale) + centerY;
                    c.setSecondCords(c.x, c.y);
                }
                healed = true;
            }
            // The two rows of a horizontal layout must not overlap: sort the
            // dots by X and split any overlapping columns apart.
            Collections.sort(coords, Pad.comparatorX);
            int half = coords.size() / 2;
            if (coords.get(half).x <= coords.get(half - 1).x) {
                float density = context.getResources().getDisplayMetrics().density;
                if (density <= 0.0f) {
                    density = 1.0f;
                }
                int gap = Math.round(30.0f * density);
                int overlap = (coords.get(half - 1).x - coords.get(half).x)
                        + gap;
                int shift = overlap / 2;
                for (int i = 0; i < coords.size(); i++) {
                    Coords c = coords.get(i);
                    if (i < half) {
                        c.x -= shift;
                    } else {
                        c.x += shift;
                    }
                    c.setSecondCords(c.x, c.y);
                }
                healed = true;
            }
        }
        if (healed) {
            Log.i("PadUtilities", "Soft-Healing applied: "
                    + (type == KeyboardType.VERTICAL ? "VERTICAL"
                            : "TABLETOP/HORIZONTAL"));
            Vibrator vibrator = (Vibrator) context
                    .getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                long[] pattern = { 0, 60, 80, 60 };
                vibrator.vibrate(pattern, -1);
            }
            AccessibilityManager manager = (AccessibilityManager) context
                    .getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager != null && manager.isEnabled()) {
                AccessibilityEvent event = AccessibilityEvent.obtain();
                event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
                event.setClassName("com.dalton.braillekeyboard.BrailleView");
                event.setPackageName(context.getPackageName());
                String msg = "Geometria di calibrazione ottimizzata automaticamente.";
                Locale currentLocale = context.getResources()
                        .getConfiguration().locale;
                if (currentLocale != null
                        && !"it".equals(currentLocale.getLanguage())) {
                    msg = "Calibration geometry optimized automatically.";
                }
                event.getText().add(msg);
                manager.sendAccessibilityEvent(event);
            }
        }
    }

    public static Pad displayDefaultPad(Context context, int width, int height,
            boolean portrait, boolean useEightDots) {
        Pad pad;
        boolean invert = Options.getBooleanPreference(context,
                R.string.pref_keyboard_invert_key, Boolean.parseBoolean(context
                        .getString(R.string.pref_keyboard_invert_default)));
        boolean gestureInvert = invert ^ Options.getBooleanPreference(context,
                R.string.pref_invert_gestures_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_invert_gestures_default)));
        KeyboardType keyboard = KeyboardType
                .valueOf(Integer.parseInt(Options.getStringPreference(
                        context,
                        R.string.pref_default_keyboard_key,
                        context.getString(R.string.pref_default_keyboard_default))));
        // When the layout is auto-detected, remember the type of the last
        // calibration so the keyboard doesn't flip between layouts.
        int lastLayoutOrdinal = Options.getIntPreference(context,
                R.string.pref_last_calibrated_layout_key, -1);
        if (keyboard == KeyboardType.AUTO && lastLayoutOrdinal != -1) {
            keyboard = KeyboardType.values()[lastLayoutOrdinal];
        }
        int maxScreen = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, MAX_SCREEN_SIZE, context
                        .getResources().getDisplayMetrics());
        int screen = (int) Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2));
        if (keyboard == KeyboardType.HORIZONTAL
                || (screen > maxScreen && KeyboardType.AUTO == keyboard)) {
            Coords[] coords = null;
            int prefKey = HorizontalPad.getPrefKey(portrait, invert);
            if ((coords = Pad.load(context, width, height, prefKey, portrait)) != null) {
                pad = new HorizontalPad(context, coords, width, height,
                        portrait, invert, gestureInvert, useEightDots);
            } else {
                pad = HorizontalPad.displayDefaultPad(context, width, height,
                        portrait, invert, gestureInvert, useEightDots);
            }
        } else {
            Coords[] coords = null;
            int prefKey = VerticalPad.getPrefKey(portrait, invert);
            if ((coords = Pad.load(context, width, height, prefKey, portrait)) != null) {
                pad = new VerticalPad(context, coords, width, height, portrait,
                        invert, gestureInvert, useEightDots);
            } else {
                pad = VerticalPad.displayDefaultPad(context, width, height,
                        portrait, invert, gestureInvert, useEightDots);
            }
        }

        setPadStyle(context, pad);
        return pad;
    }
}
