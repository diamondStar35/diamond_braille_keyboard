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

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Region;
import android.os.Build;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;

/**
 * Accessibility service that makes the Braille keyboard usable while a screen
 * reader such as TalkBack is turned on.
 *
 * <p>While a screen reader is running it intercepts all touches and gestures on
 * the screen, which is why the keyboard previously required TalkBack to be
 * disabled. Android 11 (API 30) and later lets an accessibility service
 * declare {@linkplain #setTouchExplorationPassthroughRegion(int, Region) touch
 * exploration} and {@linkplain #setGestureDetectionPassthroughRegion(int,
 * Region) gesture detection} passthrough regions: touches and gestures inside
 * those regions are delivered to the app underneath instead of being consumed
 * by the screen reader.
 *
 * <p>The {@link BrailleIME} asks this service to pass the entire screen
 * through while the keyboard window is shown and to clear the region when the
 * keyboard is hidden, so TalkBack behaves normally the rest of the time.
 *
 * <p>This service does not read any screen content and receives no
 * accessibility events; it only manages passthrough regions.
 */
public class AccessibilityService extends android.accessibilityservice.AccessibilityService {

    /** The connected service instance, or null while the service is disabled. */
    private static AccessibilityService instance = null;

    /** True when the IME currently wants the whole screen passed through. */
    private static boolean passthroughWanted = false;

    /** The container of the full-screen keyboard overlay, or null. */
    private static View keyboardOverlayContainer = null;

    /** Caches the result of the enabled-services query for a short time. */
    private static long lastEnabledCheckTime = 0;
    private static boolean serviceEnabledCached = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        // The keyboard may already be open if the service was just enabled.
        applyPassthrough();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This service never needs to react to accessibility events.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The display dimensions may have changed (e.g. rotation) while the
        // keyboard is open, which would leave the old region partially
        // covering the screen. Recompute it so the whole new screen stays
        // passed through.
        applyPassthrough();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (instance == this) {
            instance = null;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    /**
     * Moves the keyboard into a full-screen accessibility-overlay window.
     *
     * <p>An IME window cannot hide the system bars on modern Android (the
     * system keeps them visible while the keyboard is shown), so the only
     * way to make the keyboard truly full-screen is to host it in an
     * accessibility overlay, which is the topmost window layer and covers
     * the status and navigation bars. This is the same approach as the
     * reference keyboard.
     *
     * @param keyboard The keyboard view to move into the overlay; it is
     *            reparented out of the IME window.
     * @return True when the overlay was created; false when this service is
     *         not connected, in which case the caller should keep the
     *         keyboard in the IME window.
     */
    public static boolean showKeyboardOverlay(View keyboard) {
        if (instance == null) {
            return false;
        }
        return instance.showKeyboardOverlayInternal(keyboard);
    }

    /**
     * True when the given keyboard view currently lives in the full-screen
     * overlay window.
     */
    public static boolean isKeyboardHostedInOverlay(View keyboard) {
        return keyboardOverlayContainer != null
                && keyboard.getParent() == keyboardOverlayContainer;
    }

    private boolean showKeyboardOverlayInternal(View keyboard) {
        // Already hosted here: keep the existing window. Recreating it on
        // every call would make the keyboard visibly close and reopen
        // whenever the editor restarts the input session (which browsers do
        // constantly).
        if (isKeyboardHostedInOverlay(keyboard)) {
            return true;
        }
        removeKeyboardOverlay();
        // For an accessibility service this window manager creates overlay
        // windows without needing the SYSTEM_ALERT_WINDOW permission.
        WindowManager windowManager = (WindowManager) getSystemService(
                Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return false;
        }
        // Opaque backing so the app content never shows through the keyboard
        // while it covers the whole screen.
        FrameLayout container = new FrameLayout(getApplicationContext());
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
        keyboardOverlayContainer = container;
        return true;
    }

    /**
     * Removes the full-screen keyboard overlay, if any. The keyboard view is
     * left detached; the IME restores it into its own window if needed.
     */
    public static void removeKeyboardOverlay() {
        if (instance != null) {
            instance.removeKeyboardOverlayInternal();
        }
    }

    private void removeKeyboardOverlayInternal() {
        if (keyboardOverlayContainer == null) {
            return;
        }
        try {
            WindowManager windowManager = (WindowManager) getSystemService(
                    Context.WINDOW_SERVICE);
            if (windowManager != null) {
                // Immediate: the overlay covers the whole screen, so it must
                // disappear synchronously, not on a later frame.
                windowManager.removeViewImmediate(keyboardOverlayContainer);
            }
        } catch (IllegalArgumentException e) {
            // The window was already removed.
        }
        keyboardOverlayContainer = null;
    }

    /**
     * Called by the IME to pass every touch on the screen through to the
     * keyboard (true) or to restore normal screen-reader handling (false).
     */
    public static void setKeyboardPassthrough(boolean active) {
        passthroughWanted = active;
        if (instance != null) {
            instance.applyPassthrough();
        }
    }

    /**
     * True when this service is enabled in the system accessibility settings.
     *
     * <p>The query is a binder call, so the result is cached briefly; this
     * method may be called frequently from drawing code.
     */
    public static boolean isServiceEnabled(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastEnabledCheckTime > 2000) {
            serviceEnabledCached = computeServiceEnabled(context);
            lastEnabledCheckTime = now;
        }
        return serviceEnabledCached;
    }

    /**
     * True when the keyboard can actually work with a screen reader turned on:
     * the service is enabled and the device supports passthrough regions
     * (Android 11, API 30, or later).
     */
    public static boolean canPassthrough(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && isServiceEnabled(context);
    }

    private static boolean computeServiceEnabled(Context context) {
        AccessibilityManager manager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        ComponentName ourService = new ComponentName(context,
                AccessibilityService.class);
        List<AccessibilityServiceInfo> enabled = manager
                .getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo() != null
                    && ourService.equals(new ComponentName(
                            info.getResolveInfo().serviceInfo.packageName,
                            info.getResolveInfo().serviceInfo.name))) {
                return true;
            }
        }
        return false;
    }

    private void applyPassthrough() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Passthrough regions require Android 11 (API 30).
            return;
        }
        WindowManager windowManager = (WindowManager) getSystemService(
                Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }
        Display display = windowManager.getDefaultDisplay();
        Region region;
        if (passthroughWanted) {
            Point size = new Point();
            display.getRealSize(size);
            region = new Region(0, 0, size.x, size.y);
        } else {
            // An empty region clears the passthrough.
            region = new Region();
        }
        setTouchExplorationPassthroughRegion(display.getDisplayId(), region);
        setGestureDetectionPassthroughRegion(display.getDisplayId(), region);
    }
}
