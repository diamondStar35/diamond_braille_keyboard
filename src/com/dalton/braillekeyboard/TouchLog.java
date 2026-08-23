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
import android.graphics.Rect;
import android.view.MotionEvent;

/**
 * Writes keyboard geometry and touch traces to the diagnostic log. These
 * reports are how problems like "the keyboard does not respond" or "the dots
 * are not where I touch" get diagnosed remotely.
 *
 * <p>Every method checks {@link Diagnostics#isEnabled} before building any
 * string, so a disabled log costs one preference read and no allocations -
 * these methods sit directly on the touch path.
 */
final class TouchLog {

    private TouchLog() {
    }

    /**
     * Logs the keyboard geometry, orientation and dot placement. Called when
     * the view is created, resized or initialised for an input session.
     */
    static void keyboardState(BrailleKeyboardView view, PadController pads,
            DotRenderer renderer, String reason) {
        Context context = view.getContext();
        if (!Diagnostics.isEnabled(context)) {
            return;
        }
        StringBuilder sb = new StringBuilder(reason);
        sb.append(" view=").append(view.getWidth()).append('x')
                .append(view.getHeight());
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        sb.append(" onScreen=(").append(location[0]).append(',')
                .append(location[1]).append(")-(")
                .append(location[0] + view.getWidth()).append(',')
                .append(location[1] + view.getHeight()).append(')');
        Rect frame = new Rect();
        view.getWindowVisibleDisplayFrame(frame);
        sb.append(" window=").append(frame.toShortString());
        sb.append(" rotation=").append(Diagnostics.rotationLabel(context));
        sb.append(" invert=").append(Options.getBooleanPreference(context,
                R.string.pref_keyboard_invert_key,
                Boolean.parseBoolean(context.getString(
                        R.string.pref_keyboard_invert_default))));
        sb.append(" autoRotate=")
                .append(renderer.params() != null
                        ? renderer.params().autoRotate : '?');
        if (!pads.hasPad()) {
            sb.append(" pad=null");
        } else {
            sb.append(" padType=").append(pads.getPadTypeName());
            List<Coords> keys = pads.getKeys();
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
        Diagnostics.log(context, sb.toString());
    }

    /**
     * Logs a single touch with both its raw coordinates and its mapped
     * keyboard coordinates.
     */
    static void touch(Context context, PadController pads, String event,
            MotionEvent motionEvent, int pointerId, int rawX, int rawY,
            int mappedX, int mappedY) {
        if (!Diagnostics.isEnabled(context)) {
            return;
        }
        StringBuilder sb = new StringBuilder("touch ");
        sb.append(event).append(" ptr=")
                .append(motionEvent.getPointerCount())
                .append(" id=").append(pointerId)
                .append(" raw=(").append(rawX).append(',').append(rawY)
                .append(") mapped=(").append(mappedX).append(',')
                .append(mappedY).append(')');
        if (pads.isManualCalibrating()) {
            sb.append(" calibrating");
        }
        sb.append(" pressed=0x").append(Integer
                .toHexString(pads.getPressedDotString()));
        sb.append(nearestDot(pads, mappedX, mappedY));
        Diagnostics.log(context, sb.toString());
    }

    /**
     * Logs the outcome of a finished gesture: which swipe was resolved and
     * which action it is bound to.
     */
    static void gesture(Context context, PadController pads, Swipe swipe,
            boolean swapAxes, boolean multiFinger) {
        if (!Diagnostics.isEnabled(context)) {
            return;
        }
        StringBuilder sb = new StringBuilder(
                multiFinger ? "gesture(pointer_up): " : "gesture: ");
        sb.append("swipe=").append(swipe.name())
                .append(" handled=").append(pads.isHandledSwipe())
                .append(" action=").append(boundActionName(context, swipe))
                .append(' ').append(pads.describeSwipe(swapAxes));
        Diagnostics.log(context, sb.toString());
    }

    /** The name of the action bound to the resolved gesture. */
    private static String boundActionName(Context context, Swipe swipe) {
        if (swipe == Swipe.NONE) {
            return "none";
        }
        return swipe.getBoundAction(context).name();
    }

    /** The dot closest to a touch and its distance in pixels. */
    private static String nearestDot(PadController pads, int x, int y) {
        if (!pads.hasPad()) {
            return "";
        }
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        List<Coords> keys = pads.getKeys();
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

    /** The name of a MotionEvent action, for the diagnostic log. */
    static String actionName(int action) {
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            return "down";
        case MotionEvent.ACTION_POINTER_DOWN:
            return "pointer_down";
        case MotionEvent.ACTION_UP:
            return "up";
        case MotionEvent.ACTION_POINTER_UP:
            return "pointer_up";
        case MotionEvent.ACTION_CANCEL:
            return "cancel";
        case MotionEvent.ACTION_MOVE:
            return "move";
        case MotionEvent.ACTION_HOVER_MOVE:
            return "hover_move";
        case MotionEvent.ACTION_HOVER_EXIT:
            return "hover_exit";
        default:
            return "action_" + action;
        }
    }
}
