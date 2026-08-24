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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Shown when a screen of the app failed to start. Presents the error to the
 * user with a Copy button so it can be reported to the developer, and a Close
 * button that simply leaves the app.
 *
 * <p>The UI is built in code rather than from a layout resource on purpose:
 * this screen must be the one thing that can never fail to come up when
 * something else just crashed.
 */
public class StartupErrorActivity extends AppCompatActivity {

    /** Extra carrying the report text when the activity is launched. */
    static final String EXTRA_REPORT = "report";

    // When crash loops run (an IME rebind storm, or an app the user keeps
    // re-launching that fails every time), report() and this screen would
    // otherwise stack dialogs and notifications every few milliseconds. The
    // first failure always shows; rapid repeats are logged and saved only.
    private static volatile long lastReportStartedAt;

    /**
     * Saves the crash and shows the error dialog instead of the failing
     * screen. Call from an entry activity's catch block; the failing
     * activity should finish itself afterwards.
     *
     * @param failedActivity The activity whose startup failed; used as the
     *            launching context and for where the report points at.
     * @param where A short name of the failing component, e.g.
     *            "MainActivity.onCreate".
     * @param throwable The exception that was caught.
     */
    public static void report(AppCompatActivity failedActivity,
            String where, Throwable throwable) {
        try {
            String reportText = CrashReporter.format(where, throwable);
            CrashReporter.save(failedActivity, reportText);
            Diagnostics.logAlways(failedActivity,
                    "startup failure in " + where + ": " + throwable);
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastReportStartedAt < 1500) {
                // A screen is already coming up for a near-simultaneous
                // failure (or a crash loop is running); stacking more of
                // them helps nobody. The log and saved file have it all.
                return;
            }
            lastReportStartedAt = now;
            Intent intent = new Intent(failedActivity,
                    StartupErrorActivity.class);
            intent.putExtra(EXTRA_REPORT, reportText);
            failedActivity.startActivity(intent);
        } catch (Throwable ignored) {
            // Reporting must never be the thing that crashes.
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Tapping the crash notification may be the first thing that starts
        // this process after a crash, so make sure the guard exists here too
        // (idempotent).
        CrashGuard.install(this);
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable e) {
            // This screen must come up no matter what, even if its own
            // setup fails; fall back to the most primitive view possible.
            try {
                TextView fallback = new TextView(this);
                fallback.setText(R.string.startup_error_title);
                setContentView(fallback);
                setTitle(R.string.startup_error_title);
            } catch (Throwable ignored) {
                finish();
            }
        }
    }

    private void buildUi() {

        String report = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_REPORT) : null;
        if (report == null || report.length() == 0) {
            report = CrashReporter.load(this);
        }
        if (report == null) {
            report = getString(R.string.startup_error_no_details);
        }
        final String reportText = report;

        int padding = dp(16);

        TextView message = new TextView(this);
        message.setText(R.string.startup_error_message);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        message.setPadding(padding, padding, padding, dp(8));

        TextView details = new TextView(this);
        details.setText(reportText);
        details.setTextIsSelectable(true);
        details.setTypeface(android.graphics.Typeface.MONOSPACE);
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        details.setMovementMethod(new ScrollingMovementMethod());
        details.setPadding(padding, dp(8), padding, padding);
        ScrollView detailsScroll = new ScrollView(this);
        detailsScroll.addView(details);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        Button copy = new Button(this);
        copy.setText(android.R.string.copy);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyReport(reportText);
            }
        });
        Button close = new Button(this);
        close.setText(R.string.startup_error_close);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        buttons.addView(copy);
        buttons.addView(close);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(message);
        root.addView(detailsScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(buttons);
        setContentView(root);
        setTitle(R.string.startup_error_title);
    }

    private void copyReport(String reportText) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        getString(R.string.startup_error_title), reportText));
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT)
                        .show();
            }
        } catch (Throwable ignored) {
            // Reporting must never be the thing that crashes.
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics());
    }
}
