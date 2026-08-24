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

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Process-wide catcher of uncaught exceptions.
 *
 * <p>The IME service runs in the same process as the rest of the app, so one
 * handler sees crashes from everywhere - the keyboard, the translator client
 * and any activity. When a crash happens it writes the report into the shared
 * diagnostics log (always, even with logging disabled, so the file users can
 * share from MainActivity always contains it), saves it for
 * {@link StartupErrorActivity}, and posts a notification linking to that
 * screen. Afterwards the previous system handler runs so Android still does
 * its normal kill-and-restart.
 *
 * <p>A dialog cannot be shown at the moment a service crashes - a service has
 * no window and the process is already going down - which is exactly why this
 * class persists everything instead.
 */
final class CrashGuard {

    private static final String CHANNEL_ID = "crash";
    private static final int NOTIFICATION_ID = 1;
    private static volatile boolean installed;

    private CrashGuard() {
    }

    /** Installs the handler once per process; safe to call repeatedly. */
    static void install(final Context context) {
        if (installed) {
            return;
        }
        synchronized (CrashGuard.class) {
            if (installed) {
                return;
            }
            final Context app = context.getApplicationContext();
            final Thread.UncaughtExceptionHandler previous =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(Thread thread,
                                Throwable throwable) {
                            try {
                                handle(app, thread, throwable);
                            } catch (Throwable ignored) {
                                // Reporting must never be the thing that
                                // breaks the crash handling.
                            }
                            if (previous != null) {
                                // The system handler kills (and restarts)
                                // the process; everything needed for the
                                // report is on disk by now.
                                previous.uncaughtException(thread, throwable);
                            }
                        }
                    });
            installed = true;
        }
    }

    private static void handle(Context app, Thread thread,
            Throwable throwable) {
        String where = "uncaught in thread " + thread.getName();
        String report = CrashReporter.format(where, throwable);
        // Into the shareable log even when logging is off...
        Diagnostics.logAlways(app, "CRASH " + report);
        // ...and persisted so StartupErrorActivity can show the details.
        CrashReporter.save(app, report);
        notifyCrash(app, report);
    }

    private static void notifyCrash(Context app, String report) {
        try {
            NotificationManager manager =
                    (NotificationManager) app.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID,
                        app.getString(R.string.startup_error_title),
                        NotificationManager.IMPORTANCE_HIGH));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && app.checkSelfPermission(
                            Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                // Without the permission the notification would be dropped
                // silently; the report is still logged and saved.
                return;
            }
            Intent intent = new Intent(app, StartupErrorActivity.class);
            intent.putExtra(StartupErrorActivity.EXTRA_REPORT, report);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pending = PendingIntent.getActivity(app, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? new Notification.Builder(app, CHANNEL_ID)
                            : new Notification.Builder(app);
            builder.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(app.getString(
                            R.string.startup_error_title))
                    .setContentText(app.getString(
                            R.string.startup_error_message))
                    .setContentIntent(pending)
                    .setAutoCancel(true);
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (Throwable ignored) {
            // Reporting must never be the thing that breaks the crash
            // handling.
        }
    }
}
