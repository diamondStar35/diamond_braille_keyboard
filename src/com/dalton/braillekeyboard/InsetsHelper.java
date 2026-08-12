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

import android.app.Activity;
import android.os.Build;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps activity content inside the safe area on Android 15+ (API 35+),
 * where edge-to-edge is enforced for apps targeting SDK 35+ and every
 * window is drawn behind the status bar, navigation bar and display cutout.
 * The app itself never opts in to edge-to-edge on older versions, so this is
 * the single place that handles the enforced bars.
 *
 * <p>{@link #apply(Activity)} pads the activity's content frame by the
 * system bar and display cutout insets, so list rows, buttons and other
 * content are never hidden behind the bars or a camera cutout. The top
 * padding additionally covers the action bar: under the enforcement the
 * content frame spans the whole window and is drawn behind the action bar
 * as well, so the top inset alone is not enough. The action bar container's
 * measured position (its bottom edge) is exactly where the content must
 * start; the theme's action bar height is used until it is laid out.
 *
 * <p>On Android 14 and older the window already fits the system bars, so
 * this is a no-op there.
 *
 * <p>Every activity calls this from {@code onCreate}, so fixing a screen
 * only means calling it; there is no per-screen layout padding to keep in
 * sync.
 */
public class InsetsHelper {

    private InsetsHelper() {
    }

    /**
     * Apply the safe-area insets to the given activity's content.
     *
     * @param activity The activity whose content should stay inside the
     *            system bars. Should be called after setContentView, or any
     *            time in onCreate for fragments-based screens.
     */
    public static void apply(Activity activity) {
        // Edge-to-edge is only enforced on Android 15+ for apps targeting
        // SDK 35+. Older versions lay the window out within the system bars
        // already, so the insets would only add unwanted gaps.
        if (Build.VERSION.SDK_INT < 35) {
            return;
        }
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        // The action bar container comes from the AppCompat library (every
        // activity in the app uses an AppCompat theme), so its id is a
        // plain compile-time constant.
        final int actionBarId =
                androidx.appcompat.R.id.action_bar_container;
        // Guards the retry below so an action bar that never measures (or is
        // hidden) cannot keep requesting insets dispatches forever.
        final boolean[] retryScheduled = { false };
        ViewCompat.setOnApplyWindowInsetsListener(content,
                new OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(View v,
                            WindowInsetsCompat insets) {
                        Insets bars = insets.getInsets(
                                WindowInsetsCompat.Type.systemBars()
                                        | WindowInsetsCompat.Type
                                                .displayCutout());
                        int top = bars.top;
                        View root = v.getRootView();
                        if (root != null) {
                            View actionBar = root.findViewById(actionBarId);
                            if (actionBar != null) {
                                if (actionBar.getHeight() > 0) {
                                    // The bottom edge already includes the
                                    // status bar offset applied by the
                                    // framework, so it is exactly where the
                                    // content must start.
                                    retryScheduled[0] = false;
                                    top = Math.max(top,
                                            actionBar.getBottom());
                                } else if (!retryScheduled[0]) {
                                    // Not laid out yet: re-dispatch the
                                    // insets once it is measured.
                                    retryScheduled[0] = true;
                                    ViewCompat.requestApplyInsets(v);
                                }
                            }
                        }
                        v.setPadding(bars.left, top, bars.right, bars.bottom);
                        return insets;
                    }
                });
    }
}
