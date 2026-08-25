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

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable description of a sound theme, read from a {@code config.json}
 * manifest.
 *
 * <p>A theme comes from one of two places, described by {@link Origin}:
 * shipped in the APK under {@code assets/sounds/<folder>/}, or installed by
 * the user in a directory of its own (see {@link ThemeLibrary}). Both are
 * parsed the same way and played the same way - {@link #sampleSource} is what
 * hides the difference from {@link SoundThemeManager}.
 *
 * <p>Every theme is identified by a stable {@link #id} declared in the
 * manifest. The id is what the app persists in the preferences and what it
 * uses to switch themes, so the folder underneath can be renamed without
 * breaking saved settings. A theme with no explicit id falls back to its
 * folder name.
 *
 * <p>The {@code sounds} object maps {@link FeedbackEvent} keys to either a
 * sample path relative to the theme root, {@link #SOUND_SYSTEM} (play the
 * platform's built-in key click) or {@link #SOUND_NONE} (silence).
 *
 * <h3>Manifest</h3>
 *
 * <pre>
 * {
 *   "format": 1,
 *   "id": "theme_1",
 *   "name": "Theme 1",
 *   "author": "ABK",
 *   "version": "1.0",
 *   "sounds": { "type": "type.ogg", "delete": "system", ... }
 * }
 * </pre>
 */
public class SoundTheme {
    private static final String TAG = "SoundTheme";

    /** Assets directory holding the themes shipped with the app. */
    static final String ASSETS_ROOT = "sounds";

    /** The manifest file name, in the assets and in installed themes alike. */
    static final String CONFIG_FILE = "config.json";

    /**
     * Manifest layout this build writes and understands. A file declaring a
     * higher version still loads: unknown keys are ignored, so a theme from
     * a later build plays whatever this one recognises rather than failing.
     */
    static final int FORMAT_VERSION = 1;

    private static final String KEY_FORMAT = "format";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_AUTHOR = "author";
    private static final String KEY_VERSION = "version";
    private static final String KEY_SOUNDS = "sounds";

    // A manifest is small by nature; anything this size is not one, and
    // reading it into memory to parse would be the wrong move.
    private static final int MAX_CONFIG_BYTES = 256 * 1024;

    /** The id of the "no sounds" option in the settings. */
    public static final String ID_OFF = "off";

    /** Sentinel sound value meaning "play the platform key click". */
    public static final String SOUND_SYSTEM = "system";

    /** Sentinel sound value meaning "play nothing". */
    public static final String SOUND_NONE = "none";

    /** Where a theme comes from, and therefore what may be done to it. */
    public enum Origin {
        /** Shipped in the APK. Read-only: cannot be edited or deleted. */
        BUILT_IN,
        /** Installed by the user. Editable, deletable, exportable. */
        USER
    }

    /** The stable id used to save and load this theme. */
    public final String id;

    /**
     * The asset folder for a built-in theme, or the directory name for an
     * installed one. Kept for settings saved before ids existed, which store
     * this instead of the id.
     */
    public final String folderName;

    /** The human readable name shown in the settings. */
    public final String displayName;

    /** Optional author metadata from the manifest. */
    public final String author;

    /** Optional version metadata from the manifest. */
    public final String version;

    /** Where this theme came from. */
    public final Origin origin;

    /** The directory an installed theme lives in; null when built in. */
    public final File directory;

    private final Map<FeedbackEvent, String> sounds =
            new HashMap<FeedbackEvent, String>();

    private SoundTheme(String id, String folderName, String displayName,
            String author, String version, Origin origin, File directory) {
        this.id = id;
        this.folderName = folderName;
        this.displayName = displayName;
        this.author = author;
        this.version = version;
        this.origin = origin;
        this.directory = directory;
    }

    /**
     * The sound configured for an event: a sample path relative to the theme
     * root, {@link #SOUND_SYSTEM} or {@link #SOUND_NONE}. Returns
     * {@code null} when the theme does not configure the event.
     */
    public String getSound(FeedbackEvent event) {
        return sounds.get(event);
    }

    /** Whether the user may edit or delete this theme. */
    public boolean isEditable() {
        return origin == Origin.USER;
    }

    /**
     * A copy of this theme's event bindings, safe for the caller to modify.
     * Events the theme does not configure are absent rather than mapped to
     * {@link #SOUND_NONE}.
     */
    public Map<FeedbackEvent, String> soundMap() {
        return new HashMap<FeedbackEvent, String>(sounds);
    }

    /**
     * Build a manifest.
     *
     * <p>Events are written in declaration order rather than whatever order
     * a map happens to iterate in, so a manifest reads the way the settings
     * screen lists the events and two exports of the same theme produce
     * identical bytes.
     *
     * @param sounds The event bindings; unconfigured events are written as
     *            {@link #SOUND_NONE} so the file always describes all of
     *            them.
     * @return The manifest text, ready to write as {@code config.json}.
     */
    static String manifest(String id, String name, String author,
            String version, Map<FeedbackEvent, String> sounds) {
        try {
            JSONObject soundsObject = new JSONObject();
            for (FeedbackEvent event : FeedbackEvent.values()) {
                String value = sounds.get(event);
                soundsObject.put(event.configKey,
                        value == null || value.isEmpty() ? SOUND_NONE : value);
            }
            JSONObject root = new JSONObject();
            root.put(KEY_FORMAT, FORMAT_VERSION);
            root.put(KEY_ID, id);
            root.put(KEY_NAME, name);
            root.put(KEY_AUTHOR, author == null ? "" : author);
            root.put(KEY_VERSION, version == null ? "" : version);
            root.put(KEY_SOUNDS, soundsObject);
            return root.toString(2);
        } catch (JSONException e) {
            // Only thrown for null keys, and every key here is a constant.
            throw new IllegalStateException("cannot build manifest", e);
        }
    }

    /**
     * Where this theme's samples load from. Built-in themes read from the
     * assets, installed ones from their own directory; the caller does not
     * need to know which.
     */
    SampleSource sampleSource(Context context) {
        return origin == Origin.BUILT_IN
                ? new SampleSource.Assets(context, folderName)
                : new SampleSource.Files(directory);
    }

    // ---- Listing --------------------------------------------------------

    /**
     * The parsed list together with the epoch it was built at. The two are
     * held in one immutable object and published through a single volatile
     * write, so a reader can never pair a stale list with a current epoch -
     * which is exactly what two separate fields would allow, and would show
     * up as a theme that stays invisible after being installed.
     */
    private static final class Snapshot {
        final int epoch;
        final List<SoundTheme> themes;

        Snapshot(int epoch, List<SoundTheme> themes) {
            this.epoch = epoch;
            this.themes = themes;
        }
    }

    private static volatile Snapshot snapshot;

    /**
     * Every theme available to the user: those shipped in the assets first,
     * then those they installed. Themes whose manifest cannot be read are
     * skipped.
     *
     * <p>Parsing is cached for the {@link ThemeLibrary#epoch()} it happened
     * at. Assets cannot change while the process runs, but installed themes
     * can, and a cache with no way to go stale would leave a freshly
     * installed or edited theme invisible until the app was killed.
     */
    public static List<SoundTheme> listThemes(Context context) {
        int epoch = ThemeLibrary.epoch();
        Snapshot cached = snapshot;
        if (cached != null && cached.epoch == epoch) {
            return cached.themes;
        }

        List<SoundTheme> themes = new ArrayList<SoundTheme>();
        Set<String> ids = new HashSet<String>();

        // Built-ins first, so their ids win any collision: a theme installed
        // from a file must never shadow the default the app falls back to.
        for (SoundTheme theme : listBuiltIn(context)) {
            if (ids.add(theme.id.toLowerCase())) {
                themes.add(theme);
            }
        }
        for (SoundTheme theme : listInstalled(context)) {
            if (ids.add(theme.id.toLowerCase())) {
                themes.add(theme);
            } else {
                Log.w(TAG, "Ignoring installed theme with duplicate id "
                        + theme.id);
            }
        }

        List<SoundTheme> result = Collections.unmodifiableList(themes);
        snapshot = new Snapshot(epoch, result);
        return result;
    }

    // Themes shipped in the APK.
    private static List<SoundTheme> listBuiltIn(Context context) {
        List<SoundTheme> themes = new ArrayList<SoundTheme>();
        AssetManager assets = context.getAssets();
        try {
            String[] folders = assets.list(ASSETS_ROOT);
            if (folders == null) {
                return themes;
            }
            for (String folder : folders) {
                String path = ASSETS_ROOT + "/" + folder + "/" + CONFIG_FILE;
                String manifest = readAsset(context, path);
                if (manifest == null) {
                    continue;
                }
                SoundTheme theme = parse(manifest, folder, Origin.BUILT_IN,
                        null);
                if (theme != null) {
                    themes.add(theme);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to list built-in sound themes", e);
        }
        return themes;
    }

    // Themes the user installed.
    private static List<SoundTheme> listInstalled(Context context) {
        List<SoundTheme> themes = new ArrayList<SoundTheme>();
        for (File directory : ThemeLibrary.installedDirectories(context)) {
            SoundTheme theme = readFrom(directory);
            if (theme != null) {
                themes.add(theme);
            }
        }
        return themes;
    }

    /**
     * Read the theme in a directory: an installed one, or one still staged
     * from an archive and not yet in the library.
     *
     * @return The theme, or {@code null} when the directory holds no
     *         readable manifest.
     */
    static SoundTheme readFrom(File directory) {
        String manifest = readFile(new File(directory, CONFIG_FILE));
        if (manifest == null) {
            return null;
        }
        return parse(manifest, directory.getName(), Origin.USER, directory);
    }

    /**
     * Load the theme matching a stored preference value. The value is first
     * matched against the stable theme {@link #id}; for settings saved
     * before ids existed it also falls back to matching the folder name.
     *
     * @param context The application context.
     * @param id The stored theme id (may be a legacy folder name).
     * @return The theme, or {@code null} if it cannot be resolved.
     */
    public static SoundTheme loadById(Context context, String id) {
        if (id == null) {
            return null;
        }
        for (SoundTheme theme : listThemes(context)) {
            if (theme.id.equalsIgnoreCase(id)
                    || theme.folderName.equalsIgnoreCase(id)) {
                return theme;
            }
        }
        return null;
    }

    // ---- Parsing --------------------------------------------------------

    /**
     * Parse a manifest into a theme.
     *
     * @param manifest The manifest text.
     * @param folderName The asset folder or theme directory name, used as
     *            the fallback id and name.
     * @param origin Where the theme came from.
     * @param directory The theme's directory, for an installed theme.
     * @return The theme, or {@code null} when the manifest is unusable.
     */
    static SoundTheme parse(String manifest, String folderName, Origin origin,
            File directory) {
        JSONObject root;
        try {
            root = new JSONObject(manifest);
        } catch (JSONException e) {
            Log.e(TAG, "Malformed manifest for theme " + folderName, e);
            return null;
        }

        int format = root.optInt(KEY_FORMAT, FORMAT_VERSION);
        if (format > FORMAT_VERSION) {
            // Newer than this build understands. Load it anyway - unknown
            // keys are ignored and unknown events fall back to silence - so
            // a theme shared from a later version still plays what it can.
            Log.w(TAG, "Theme " + folderName + " declares format " + format
                    + ", newer than " + FORMAT_VERSION);
        }

        String id = optString(root, KEY_ID, folderName);
        String name = optString(root, KEY_NAME, folderName);
        String author = optString(root, KEY_AUTHOR, "");
        String version = optString(root, KEY_VERSION, "");

        SoundTheme theme = new SoundTheme(id, folderName, name, author,
                version, origin, directory);

        JSONObject soundsObject = root.optJSONObject(KEY_SOUNDS);
        if (soundsObject != null) {
            java.util.Iterator<String> keys = soundsObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                FeedbackEvent event = FeedbackEvent.fromConfigKey(key);
                if (event == null) {
                    // An event this build does not have. Ignored rather than
                    // rejected, so a manifest from a later version loads.
                    continue;
                }
                String value = optString(soundsObject, key, null);
                if (value != null && !value.isEmpty()) {
                    theme.sounds.put(event, value);
                }
            }
        }
        return theme;
    }

    // JSONObject.optString returns the string "null" for a JSON null, which
    // would be taken for a sample called "null"; this returns the fallback.
    private static String optString(JSONObject object, String key,
            String fallback) {
        if (object.isNull(key)) {
            return fallback;
        }
        String value = object.optString(key, null);
        return value == null ? fallback : value.trim();
    }

    // ---- Reading --------------------------------------------------------

    private static String readAsset(Context context, String path) {
        try {
            return readStream(context.getAssets().open(path));
        } catch (IOException e) {
            Log.e(TAG, "Failed to read manifest " + path, e);
            return null;
        }
    }

    private static String readFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            return readStream(new FileInputStream(file));
        } catch (IOException e) {
            Log.e(TAG, "Failed to read manifest " + file, e);
            return null;
        }
    }

    private static String readStream(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                if (buffer.size() + read > MAX_CONFIG_BYTES) {
                    throw new IOException("manifest larger than "
                            + MAX_CONFIG_BYTES + " bytes");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString("UTF-8");
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                // The content is already read or already failed; a failure
                // to close adds nothing the caller can act on.
            }
        }
    }
}
