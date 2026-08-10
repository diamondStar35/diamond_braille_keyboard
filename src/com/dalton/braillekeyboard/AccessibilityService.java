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
import android.graphics.Point;
import android.graphics.Region;
import android.os.Build;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

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
            // The user asked for the entire screen to be passed through.
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
