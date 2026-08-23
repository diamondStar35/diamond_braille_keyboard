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

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Hosts the keyboard view in one of its two possible windows: the topmost
 * full-screen accessibility overlay used by Full-screen mode, or the IME
 * window's own input frame.
 *
 * <p>All methods are main-thread only and idempotent: hosting a view that is
 * already in the requested place does nothing, so callers can invoke them on
 * every genuine show without visual churn. This module owns no policy - it
 * performs no preference lookups and decides nothing about when placement
 * happens; {@link BrailleIME} drives it exclusively from explicit events
 * (window shown, shrink toggled, keyboard closed), which is why no extra
 * guards are needed against framework lifecycle pokes.
 *
 * <p>The overlay window is owned by the {@link AccessibilityService} process
 * singleton because only an accessibility service can create
 * {@link WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY} windows.
 */
final class KeyboardOverlayHost {

    /** The container of the full-screen overlay window, or null when none. */
    private static View overlayContainer;

    private KeyboardOverlayHost() {
    }

    /** True when the given keyboard view currently lives in the overlay. */
    static boolean isInOverlay(View keyboard) {
        return overlayContainer != null
                && keyboard.getParent() == overlayContainer;
    }

    /**
     * Moves the keyboard into a full-screen accessibility overlay covering
     * the whole screen, system bars included, so notifications cannot push
     * the keyboard away while typing.
     *
     * @param keyboard The keyboard view; it is reparented out of its current
     *            window only after the overlay exists.
     * @return True when hosted in the overlay; false when the accessibility
     *         service cannot create windows right now, in which case the
     *         caller should fall back to the IME input frame.
     */
    static boolean showOverlay(View keyboard) {
        if (isInOverlay(keyboard)) {
            return true;
        }
        removeOverlay();
        WindowManager windowManager =
                AccessibilityService.getWindowManager();
        if (windowManager == null) {
            return false;
        }
        // Opaque backing so the app content never shows through the keyboard
        // while it covers the whole screen.
        FrameLayout container = new FrameLayout(keyboard.getContext());
        container.setBackgroundColor(Color.BLACK);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(container, params);
        } catch (RuntimeException e) {
            // The overlay could not be created (e.g. the service is being
            // torn down); the keyboard stays attached where it was.
            return false;
        }
        // Only detach the keyboard once the overlay window exists, so a
        // failure above can never leave it without a window.
        ViewParent parent = keyboard.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(keyboard);
        }
        container.addView(keyboard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlayContainer = container;
        return true;
    }

    /**
     * Removes the full-screen overlay window synchronously, if any. The
     * keyboard view is left attached to the removed container; callers place
     * it elsewhere afterwards.
     */
    static void removeOverlay() {
        if (overlayContainer == null) {
            return;
        }
        try {
            WindowManager windowManager =
                    AccessibilityService.getWindowManager();
            if (windowManager != null) {
                // Immediate: the overlay covers the whole screen, so it must
                // disappear synchronously, not on a later frame.
                windowManager.removeViewImmediate(overlayContainer);
            }
        } catch (IllegalArgumentException e) {
            // The window was already removed.
        }
        overlayContainer = null;
    }

    /**
     * Puts the keyboard view back into the IME window's input frame, unless
     * it is already there. Used when Full-screen mode is off and after the
     * overlay was taken down (for example by the shrink gesture).
     *
     * @param keyboard The keyboard view to host.
     * @param inputFrame The IME window's {@link android.R.id#inputArea}.
     */
    static void attachToInputFrame(View keyboard, ViewGroup inputFrame) {
        if (inputFrame == null || keyboard.getParent() == inputFrame) {
            return;
        }
        ViewParent parent = keyboard.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(keyboard);
        }
        inputFrame.addView(keyboard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
