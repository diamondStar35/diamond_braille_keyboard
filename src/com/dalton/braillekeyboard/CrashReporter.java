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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.os.Build;

/**
 * Formats and stores startup crash reports shown by
 * {@link StartupErrorActivity}. The latest report is also persisted so it
 * survives until the user gets a chance to read and copy it.
 */
final class CrashReporter {

    private static final String FILE_NAME = "startup_error.txt";

    private CrashReporter() {
    }

    /**
     * Builds the human- and developer-readable text of a crash: where it
     * happened, when, on what device, followed by the full stack trace.
     */
    static String format(String where, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("time=").append(new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        sb.append('\n').append("where=").append(where);
        sb.append('\n').append("device=").append(Build.MANUFACTURER)
                .append(' ').append(Build.MODEL);
        sb.append('\n').append("android=").append(Build.VERSION.SDK_INT);
        sb.append("\n\n");
        StringWriter stack = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stack));
        sb.append(stack);
        return sb.toString();
    }

    /** Persists the report to the app's files directory. */
    static void save(Context context, String report) {
        try {
            File file = new File(context.createDeviceProtectedStorageContext()
                    .getFilesDir(), FILE_NAME);
            Files.write(file.toPath(),
                    report.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // Reporting must never be the thing that crashes.
        }
    }

    /** The last saved report, or null when there is none. */
    static String load(Context context) {
        try {
            File file = new File(context.createDeviceProtectedStorageContext()
                    .getFilesDir(), FILE_NAME);
            if (!file.exists()) {
                return null;
            }
            return new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
