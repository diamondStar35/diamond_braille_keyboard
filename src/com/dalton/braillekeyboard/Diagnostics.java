package com.dalton.braillekeyboard;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

/**
 * Writes diagnostic information to a log file so problems with the keyboard
 * (for example the keyboard not responding) can be reproduced and reported.
 *
 * <p>All logging is gated behind the "Enable logging" setting; when it is off
 * nothing is written. The log file lives in the app-specific external storage
 * directory (falling back to internal storage when external storage is not
 * available) and is trimmed so it never grows unbounded. Writes are serialized
 * on a single background thread so the keyboard never waits on disk I/O and
 * the order of the log lines is preserved.
 */
public final class Diagnostics {
    private static final String FILE_NAME = "soft_braille_keyboard.log";
    private static final long MAX_FILE_SIZE = 256 * 1024;
    private static final long TRIM_TO_SIZE = 64 * 1024;
    private static final Object LOCK = new Object();
    private static final ExecutorService writer = Executors
            .newSingleThreadExecutor();
    private static File logFile;

    private Diagnostics() {
    }

    /** Whether diagnostic logging is enabled by the user. */
    public static boolean isEnabled(Context context) {
        return Options.getBooleanPreference(context,
                R.string.pref_logging_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_logging_default)));
    }

    /** The log file, created lazily on first access. */
    public static File getLogFile(Context context) {
        synchronized (LOCK) {
            if (logFile == null) {
                File dir = context.getExternalFilesDir(null);
                if (dir == null) {
                    dir = context.getFilesDir();
                }
                logFile = new File(dir, FILE_NAME);
            }
            return logFile;
        }
    }

    /**
     * Append a line to the log file when logging is enabled. The write
     * happens on a background thread; the line order is preserved.
     */
    public static void log(final Context context, final String message) {
        if (!isEnabled(context)) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        writer.execute(new Runnable() {
            @Override
            public void run() {
                writeLog(appContext, message);
            }
        });
    }

    /**
     * Wait until all log lines queued so far have been written to disk.
     * Used right before the log file is shared, so the file handed to the
     * share sheet is complete.
     */
    public static void flush() {
        try {
            // The no-op marker runs after every previously queued line on
            // the single writer thread, so waiting on it is enough.
            writer.submit(new Runnable() {
                @Override
                public void run() {
                }
            }).get();
        } catch (Exception e) {
            // Logging must never break the keyboard.
        }
    }

    /**
     * Delete the log file. The deletion runs on the writer thread so it is
     * serialized with any pending writes; the file is recreated lazily on
     * the next logged line.
     */
    public static void clearLogs(final Context context) {
        final Context appContext = context.getApplicationContext();
        writer.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    File file = getLogFile(appContext);
                    if (file.exists()) {
                        file.delete();
                    }
                }
            }
        });
    }

    // Append a timestamped line, trimming the file first when it has grown
    // too large. Runs on the single writer thread.
    private static void writeLog(Context context, String message) {
        synchronized (LOCK) {
            try {
                File file = getLogFile(context);
                if (file.exists() && file.length() > MAX_FILE_SIZE) {
                    trim(file);
                }
                String line = timestamp() + " " + message + "\n";
                FileOutputStream out = new FileOutputStream(file, true);
                try {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                } finally {
                    out.close();
                }
            } catch (IOException e) {
                // Logging must never break the keyboard.
            }
        }
    }

    // Keep only the last TRIM_TO_SIZE bytes, starting at a line boundary.
    private static void trim(File file) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            long length = raf.length();
            long start = Math.max(0, length - TRIM_TO_SIZE);
            raf.seek(start);
            byte[] tail = new byte[(int) (length - start)];
            raf.readFully(tail);
            // Discard the partial first line so the file always starts at a
            // line boundary.
            int skip = 0;
            while (skip < tail.length && tail[skip] != '\n') {
                skip++;
            }
            byte[] keep = Arrays.copyOfRange(tail,
                    Math.min(skip + 1, tail.length), tail.length);
            FileOutputStream out = new FileOutputStream(file, false);
            try {
                out.write(keep);
            } finally {
                out.close();
            }
        } finally {
            raf.close();
        }
    }

    /**
     * Write a snapshot of the device, the screen and the keyboard settings.
     * Used when the keyboard opens and when the logs are shared.
     */
    public static void logDeviceInfo(Context context) {
        StringBuilder sb = new StringBuilder("=== Device info ===");
        sb.append("\nmanufacturer=").append(Build.MANUFACTURER);
        sb.append("\nmodel=").append(Build.MODEL);
        sb.append("\ndevice=").append(Build.DEVICE);
        sb.append("\nproduct=").append(Build.PRODUCT);
        sb.append("\nAndroid=").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(')');
        sb.append("\nbuild=").append(Build.DISPLAY);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        sb.append("\nscreen px=").append(dm.widthPixels).append('x')
                .append(dm.heightPixels);
        sb.append("\ndensity=").append(dm.density).append(", dpi=")
                .append(dm.densityDpi).append(", scaledDensity=")
                .append(dm.scaledDensity);
        sb.append("\nrotation=").append(rotationLabel(context));
        sb.append("\norientation=")
                .append(context.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE
                                ? "landscape" : "portrait");
        sb.append("\nsettings:");
        sb.append("\n  invertKeyboard=").append(Options
                .getBooleanPreference(context,
                        R.string.pref_keyboard_invert_key,
                        Boolean.parseBoolean(context.getString(
                                R.string.pref_keyboard_invert_default))));
        sb.append("\n  autoRotateKeyboard=").append(Options
                .getBooleanPreference(context,
                        R.string.pref_auto_rotate_keyboard_key,
                        Boolean.parseBoolean(context.getString(
                                R.string.pref_auto_rotate_keyboard_default))));
        sb.append("\n  keyboardStyle=").append(Options.getStringPreference(
                context, R.string.pref_keyboard_style_key, "0"));
        sb.append("\n  defaultKeyboard=").append(Options.getStringPreference(
                context, R.string.pref_default_keyboard_key,
                context.getString(R.string.pref_default_keyboard_default)));
        // Mirrors the code-level fallback used by loadInitialPad and
        // updateKeys, so the log reflects what the keyboard actually does
        // before the settings screen has materialised the XML default.
        sb.append("\n  lockCalibration=").append(Options
                .getBooleanPreference(context,
                        R.string.pref_lock_calibration_key, false));
        sb.append("\n  useEightDots=").append(Options.getBooleanPreference(
                context, R.string.pref_use_eight_dots_key,
                Boolean.parseBoolean(context.getString(
                        R.string.pref_use_eight_dots_default))));
        sb.append("\n  showCircles=").append(Options.getBooleanPreference(
                context, R.string.pref_show_circles_key,
                Boolean.parseBoolean(context.getString(
                        R.string.pref_show_circles_default))));
        sb.append("\n  autoCaps=").append(Options.getBooleanPreference(
                context, R.string.pref_auto_caps_key,
                Boolean.parseBoolean(context.getString(
                        R.string.pref_auto_caps_default))));
        sb.append("\n  privacy=").append(Options.getBooleanPreference(
                context, R.string.pref_privacy_key,
                Boolean.parseBoolean(context.getString(
                        R.string.pref_privacy_default))));
        sb.append("\n  swipeSensitivity=").append(Options.getStringPreference(
                context, R.string.pref_swipe_sensitivity_key,
                context.getString(R.string.default_swipe_sensitivity)));
        sb.append("\n  accessibilityServiceEnabled=")
                .append(AccessibilityService.isServiceEnabled(context));
        log(context, sb.toString());
    }

    /** A human readable description of the current screen rotation. */
    public static String rotationLabel(Context context) {
        switch (getRotation(context)) {
        case Surface.ROTATION_90:
            return "ROTATION_90 (landscape)";
        case Surface.ROTATION_180:
            return "ROTATION_180 (reverse portrait)";
        case Surface.ROTATION_270:
            return "ROTATION_270 (reverse landscape)";
        case Surface.ROTATION_0:
        default:
            return "ROTATION_0 (portrait)";
        }
    }

    // The current display rotation. Context.getDisplay() is only available
    // on Android 11+, so older versions go through the window manager.
    private static int getRotation(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Display display = context.getDisplay();
            if (display != null) {
                return display.getRotation();
            }
        }
        WindowManager wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        return wm != null ? wm.getDefaultDisplay().getRotation()
                : Surface.ROTATION_0;
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
    }
}
