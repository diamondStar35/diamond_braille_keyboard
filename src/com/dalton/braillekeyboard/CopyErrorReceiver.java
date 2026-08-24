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

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Copies a crash report straight to the clipboard from the crash
 * notification's Copy action - no activity has to open for that.
 *
 * <p>Writing the clipboard from the background is allowed for this app while
 * it is the selected input method (Android 10+ restricts clipboard access to
 * focused apps and the default IME). When it is not the active keyboard, or
 * no report exists, the copy silently does nothing.
 */
public class CopyErrorReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String report = intent.getStringExtra(
                    StartupErrorActivity.EXTRA_REPORT);
            if (report == null || report.length() == 0) {
                report = CrashReporter.load(context);
            }
            if (report != null && report.length() > 0) {
                ClipboardManager clipboard =
                        (ClipboardManager) context.getSystemService(
                                Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText(
                            context.getString(R.string.startup_error_title),
                            report));
                    Toast.makeText(context, R.string.copied,
                            Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Throwable ignored) {
            // Reporting must never be the thing that crashes.
        } finally {
            // Copied or not, acting on the notification dismisses it.
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(CrashGuard.CRASH_NOTIFICATION_ID);
            }
        }
    }
}
