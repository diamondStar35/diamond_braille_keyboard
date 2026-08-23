/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
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

import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import androidx.core.content.ContextCompat;
import android.util.TypedValue;

/**
 * Draws the visual representation of the Braille keyboard: the dot circles
 * with their numbers, the calibration banner shown during guided calibration,
 * and the label of the shrunken keyboard. Owns the paints and the display
 * metrics (dot radius, stroke width, label size) derived from the view size.
 */
final class DotRenderer {

    private final Paint paint = new Paint();
    private final Paint circlePaint = new Paint();
    private final Rect circleTextBounds = new Rect();
    private DisplayParams params;

    /** Display metrics and orientation flags for the current view size. */
    static final class DisplayParams {
        public final int strokeWidth;
        public final int textSize;
        public final int radius;
        public final boolean autoRotate;

        // Centre point used to draw the shrunken keyboard label.
        public float x;
        public float y;

        DisplayParams(int strokeWidth, int textSize, int radius,
                boolean autoRotate) {
            this.strokeWidth = strokeWidth;
            this.textSize = textSize;
            this.radius = radius;
            this.autoRotate = autoRotate;
        }
    }

    /** The parameters from the last {@link #setSize}, or null before then. */
    DisplayParams params() {
        return params;
    }

    /**
     * Recomputes the display metrics for a new view size and configures the
     * paints. Reads the auto-rotate preference, since it decides whether the
     * keyboard rotates with the device.
     */
    void setSize(Context context, int width, int height) {
        final int CIRCLE_RADIUS = 40;
        final int STROKE_WIDTH = 8;
        final int TEXT_SIZE = 20;
        int strokeWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, STROKE_WIDTH, context
                        .getResources().getDisplayMetrics());
        int textSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE, context
                        .getResources().getDisplayMetrics());
        int radius = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, CIRCLE_RADIUS, context
                        .getResources().getDisplayMetrics());
        boolean autoRotate = Options.getBooleanPreference(context,
                R.string.pref_auto_rotate_keyboard_key,
                Boolean.parseBoolean(context.getString(
                        R.string.pref_auto_rotate_keyboard_default)));
        params = new DisplayParams(strokeWidth, textSize, radius, autoRotate);
        paint.setColor(ContextCompat.getColor(context,
                android.R.color.black));
        paint.setTextSize(params.textSize);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        circlePaint.setColor(ContextCompat.getColor(context,
                android.R.color.black));
        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Style.STROKE);
        circlePaint.setStrokeWidth(params.strokeWidth);

        FontMetrics metrics = paint.getFontMetrics();
        float textHeight = Math.abs(metrics.top - metrics.bottom);
        params.x = width / 2;
        params.y = (height / 2) + (textHeight / 2);
    }

    /** Draws the "touch dot n" banner shown during guided calibration. */
    void drawCalibrationBanner(Canvas canvas, String message) {
        paint.setTextSize(50.0f);
        canvas.drawText(message, 50.0f, 100.0f, paint);
        // Restore the label size used for the dot circles.
        if (params != null) {
            paint.setTextSize(params.textSize);
        }
    }

    /**
     * Draws one numbered circle per dot at its calibrated position.
     *
     * @param autoRotate Whether the keyboard rotates with the device; decides
     *            whether dot coordinates are mapped onto swapped axes on a
     *            portrait screen (see {@link TouchMapper}).
     */
    void drawDots(Canvas canvas, List<Coords> keys, boolean autoRotate,
            int viewWidth, int viewHeight) {
        for (int i = 0; i < keys.size(); i++) {
            Coords key = keys.get(i);
            int x = TouchMapper.mapX(autoRotate, viewWidth, viewHeight,
                    key.x, key.y);
            int y = TouchMapper.mapY(autoRotate, viewWidth, viewHeight,
                    key.x, key.y);
            String text = String.valueOf(i + 1);
            paint.getTextBounds(text, 0, text.length(), circleTextBounds);
            canvas.drawCircle(x, y, params.radius, circlePaint);
            canvas.drawText(text, x, y, paint);
        }
    }

    /** Draws the centred label of the shrunken keyboard. */
    void drawShrunkLabel(Canvas canvas, CharSequence text) {
        canvas.drawText(text, 0, text.length(), params.x, params.y, paint);
    }
}
