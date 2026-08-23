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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Options {
    // Only attempt the device-protected storage migration once per process.
    private static boolean migrationAttempted = false;
    // Only migrate the legacy string table preferences once per process.
    private static boolean tableMigrationAttempted = false;

    // ---- Hot preference cache ------------------------------------------
    //
    // Last-read values keyed by the resolved preference key name.
    // SharedPreferences are already in-memory, so this cache exists to skip
    // the work wrapped around every read from hot paths - the
    // device-protected context allocation and the key-resource string
    // resolution - which used to run per keystroke, gesture and frame.
    //
    // Any change to any preference clears the whole map, so semantics are
    // identical to reading straight through: settings apply immediately,
    // exactly as before the cache existed.
    private static final ConcurrentHashMap<String, Object> hotValues =
            new ConcurrentHashMap<String, Object>();
    private static final Object[] hotListenerLock = new Object[0];
    private static SharedPreferences.OnSharedPreferenceChangeListener
            hotListener;

    private static void registerHotListener(SharedPreferences prefs) {
        if (hotListener != null) {
            return;
        }
        synchronized (hotListenerLock) {
            if (hotListener != null) {
                return;
            }
            SharedPreferences.OnSharedPreferenceChangeListener listener =
                    new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(
                        SharedPreferences sharedPreferences, String key) {
                    // Writes are rare; dropping everything is simpler and
                    // just as correct as tracking the single changed key.
                    hotValues.clear();
                }
            };
            // The registry keeps only a weak reference to the listener, so
            // hold a strong one for the life of the process.
            prefs.registerOnSharedPreferenceChangeListener(listener);
            hotListener = listener;
        }
    }

    /**
     * Get the SharedPreferences for this app. On Android 7.0+ the preferences
     * live in device-protected storage so that the keyboard works on the lock
     * screen before the device is unlocked (direct boot). Existing
     * preferences are migrated from credential-protected storage on first
     * access.
     * 
     * @param context
     *            The application context.
     * @return The SharedPreferences instance.
     */
    public static SharedPreferences getSharedPreferences(Context context) {
        // The preferences live in device-protected storage so that the
        // keyboard works on the lock screen before the device is unlocked
        // (direct boot). Existing preferences are migrated from
        // credential-protected storage on first access.
        Context deviceContext = context
                .createDeviceProtectedStorageContext();
        if (!migrationAttempted) {
            migrationAttempted = true;
            String prefName = context.getPackageName() + "_preferences";
            // Returns false if the source doesn't exist, which is fine
            // (either already moved or a fresh install).
            deviceContext.moveSharedPreferencesFrom(context, prefName);
        }
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(deviceContext);
        migrateLegacyTablePreferences(context, prefs);
        registerHotListener(prefs);
        return prefs;
    }

    // The literary and computer Braille table preferences used to be stored
    // as a single string (the old single table choice) but are now stored as
    // a string set (multi-select).  The settings screen reads them through a
    // MultiSelectListPreference which calls getStringSet() while the screen
    // is being inflated and crashes on a legacy string value, so the value
    // must be converted before the screen inflates.  This runs on every
    // access but is a no-op once the values are sets.
    private static void migrateLegacyTablePreferences(Context context,
            SharedPreferences prefs) {
        if (tableMigrationAttempted) {
            return;
        }
        tableMigrationAttempted = true;
        String auto = context.getString(R.string.pref_braille_table_auto);
        int[] keys = { R.string.pref_braille_computer_table_key,
                R.string.pref_braille_literary_table_key };
        for (int keyRes : keys) {
            String key = context.getString(keyRes);
            try {
                prefs.getStringSet(key, null);
                // Already a set (or absent); nothing to migrate.
            } catch (ClassCastException e) {
                // Legacy value stored as a single string.
                String value = prefs.getString(key, null);
                if (value != null) {
                    Set<String> set = new java.util.HashSet<String>();
                    // The old "auto" value meant the default for the type;
                    // the caller reseeds the default when the set is empty.
                    if (!auto.equals(value)) {
                        set.add(value);
                    }
                    prefs.edit().putStringSet(key, set).apply();
                }
            }
        }
    }
    public interface OptionList {
        OptionList[] getValues();

        int getResource();

        String getValue();
    }

    public enum KeyboardFeedback implements OptionList {
        NONE(0, R.string.keyboard_feedback_none), VIBRATE(1,
                R.string.keyboard_feedback_vibrate), SOUND(2,
                R.string.keyboard_feedback_sound), ALL(3,
                R.string.keyboard_feedback_all);

        public final int value;
        public final int resource;

        KeyboardFeedback(int value, int resource) {
            this.value = value;
            this.resource = resource;
        }

        public static KeyboardFeedback valueOf(int value) {
            for (KeyboardFeedback keyboardFeedback : values()) {
                if (keyboardFeedback.value == value) {
                    return keyboardFeedback;
                }
            }
            throw new IllegalArgumentException("Invalid value: " + value);
        }

        public static KeyboardFeedback next(KeyboardFeedback feedback) {
            int value = feedback.value + 1 >= values().length ? 0
                    : feedback.value + 1;
            return values()[value];
        }

        public OptionList[] getValues() {
            return values();
        }

        public String getValue() {
            return String.valueOf(value);
        }

        public int getResource() {
            return resource;
        }
    }

    public enum KeyboardEcho implements OptionList {
        NONE(0, R.string.keyboard_echo_none), CHARACTER(1,
                R.string.keyboard_echo_character), WORD(2,
                R.string.keyboard_echo_word), ALL(3, R.string.keyboard_echo_all);

        public final int value;
        public final int resource;

        KeyboardEcho(int value, int resource) {
            this.value = value;
            this.resource = resource;
        }

        public static KeyboardEcho valueOf(int value) {
            for (KeyboardEcho keyboardEcho : values()) {
                if (keyboardEcho.value == value) {
                    return keyboardEcho;
                }
            }
            throw new IllegalArgumentException("Invalid value: " + value);
        }

        public static KeyboardEcho next(KeyboardEcho echo) {
            int value = echo.value + 1 >= values().length ? 0 : echo.value + 1;
            return values()[value];
        }

        public OptionList[] getValues() {
            return values();
        }

        public String getValue() {
            return String.valueOf(value);
        }

        public int getResource() {
            return resource;
        }
    }

    public enum KeyboardType {
        AUTO, VERTICAL, HORIZONTAL;

        public static KeyboardType valueOf(int value) {
            switch (value) {
            case 1:
                return VERTICAL;
            case 2:
                return HORIZONTAL;
            default:
                return AUTO;
            }
        }
    }

    public static boolean getBooleanPreference(Context context, int resource,
            boolean defaultValue) {
        String key = context.getString(resource);
        Object cached = hotValues.get(key);
        if (cached instanceof Boolean) {
            return (Boolean) cached;
        }
        boolean value = getSharedPreferences(context).getBoolean(key,
                defaultValue);
        hotValues.put(key, value);
        return value;
    }

    public static String getStringPreference(Context context, int resource,
            String defaultValue) {
        String key = context.getString(resource);
        Object cached = hotValues.get(key);
        if (cached instanceof String) {
            return (String) cached;
        }
        String value = getSharedPreferences(context).getString(key,
                defaultValue);
        hotValues.put(key, value);
        return value;
    }

    public static boolean switchBooleanPreference(Context context,
            int resource, boolean defaultValue) {
        SharedPreferences sharedPref = getSharedPreferences(context);
        boolean pref = getBooleanPreference(context, resource, defaultValue);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(context.getString(resource), !pref);
        // apply() updates the in-memory values synchronously and writes to
        // disk asynchronously, avoiding disk I/O on the calling thread.
        editor.apply();
        return !pref;
    }

    public static void writeStringPreference(Context context, int resource,
            String value) {
        SharedPreferences sharedPref = getSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(context.getString(resource), value);
        editor.apply();
    }

    public static void writeBooleanPreference(Context context, int key,
            boolean value) {
        SharedPreferences.Editor editor = getSharedPreferences(context)
                .edit();
        editor.putBoolean(context.getString(key), value);
        editor.apply();
    }

    public static Set<String> getStringSetPreference(Context context,
            int resource, Set<String> defaultValue) {
        SharedPreferences sharedPref = getSharedPreferences(context);
        return sharedPref.getStringSet(context.getString(resource),
                defaultValue);
    }

    /**
     * Read a preference as a string set, transparently migrating a value that
     * was previously stored as a single string (the old single table choice)
     * into a one element set.  Needed because the literary and computer
     * Braille table preferences changed from a single selection to multi
     * selection.
     *
     * @param context
     *            The application context.
     * @param resource
     *            The resource id of the preference key.
     * @param defaultValue
     *            The set to return if the preference has no value at all.
     * @return The stored set, the migrated single string wrapped in a set or
     *         the default value.
     */
    public static Set<String> getStringSetOrString(Context context,
            int resource, Set<String> defaultValue) {
        SharedPreferences sharedPref = getSharedPreferences(context);
        String key = context.getString(resource);
        try {
            Set<String> set = sharedPref.getStringSet(key, null);
            if (set != null) {
                return set;
            }
        } catch (ClassCastException e) {
            // Stored as a string (legacy value), handled below.
        }
        try {
            String value = sharedPref.getString(key, null);
            if (value != null) {
                Set<String> set = new java.util.HashSet<String>();
                set.add(value);
                return set;
            }
        } catch (ClassCastException e) {
            // Stored as a set but getString failed; fall through.
        }
        return defaultValue;
    }

    public static void writeStringSetPreference(Context context, int resource,
            Set<String> value) {
        SharedPreferences sharedPref = getSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putStringSet(context.getString(resource), value);
        editor.apply();
    }

    public static int getIntPreference(Context context, int resource,
            int defaultValue) {
        SharedPreferences sharedPref = getSharedPreferences(context);
        return sharedPref.getInt(context.getString(resource), defaultValue);
    }

    public static void writeIntPreference(Context context, int resource,
            int value) {
        SharedPreferences sharedPref = getSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(context.getString(resource), value);
        editor.apply();
    }

    public static Set<String> getStringSetPreferenceStringKey(Context context,
            String key, Set<String> defaultValue) {
        SharedPreferences sharedPref = getSharedPreferences(context);
        return sharedPref.getStringSet(key, defaultValue);
    }

    public static void writeStringSetPreferenceStringKey(Context context,
            String key, Set<String> value) {
        SharedPreferences sharedPref = getSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putStringSet(key, value);
        editor.apply();
    }
}
