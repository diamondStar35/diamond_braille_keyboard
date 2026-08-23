package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable description of a sound theme, loaded from an asset folder under
 * {@code assets/sounds/<folder>/config.ini}.
 *
 * <p>Every theme is identified by a stable {@link #id} declared in the
 * [meta] section of its config. The id is what the app persists in the
 * preferences and what it uses to switch themes, so the underlying asset
 * folder can be renamed without breaking saved settings. Themes without an
 * explicit id fall back to their folder name.
 *
 * <p>The [sounds] section maps {@link FeedbackEvent}s to either a sound file
 * name (relative to the theme folder), {@link #SOUND_SYSTEM} (play the
 * platform's built-in key click) or {@link #SOUND_NONE} (silence).
 */
public class SoundTheme {
    private static final String TAG = "SoundTheme";

    private static final String ASSETS_ROOT = "sounds";
    private static final String CONFIG_FILE = "config.ini";
    private static final String SECTION_META = "meta";
    private static final String SECTION_SOUNDS = "sounds";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_AUTHOR = "author";
    private static final String KEY_VERSION = "version";

    /** The id of the "no sounds" option in the settings. */
    public static final String ID_OFF = "off";

    /** Sentinel sound value meaning "play the platform key click". */
    public static final String SOUND_SYSTEM = "system";

    /** Sentinel sound value meaning "play nothing". */
    public static final String SOUND_NONE = "none";

    /** The stable id used to save and load this theme. */
    public final String id;

    /** The asset folder the theme lives in. */
    public final String folderName;

    /** The human readable name shown in the settings. */
    public final String displayName;

    /** Optional author metadata from the config. */
    public final String author;

    /** Optional version metadata from the config. */
    public final String version;

    private final Map<FeedbackEvent, String> sounds = new HashMap<>();

    private SoundTheme(String id, String folderName, String displayName,
            String author, String version) {
        this.id = id;
        this.folderName = folderName;
        this.displayName = displayName;
        this.author = author;
        this.version = version;
    }

    /**
     * The sound configured for an event: a file name relative to the theme
     * folder, {@link #SOUND_SYSTEM} or {@link #SOUND_NONE}. Returns
     * {@code null} when the theme does not configure the event.
     */
    public String getSound(FeedbackEvent event) {
        return sounds.get(event);
    }

    /** Themes shipped in the assets, parsed once per process. */
    private static volatile List<SoundTheme> themeCache;

    /**
     * All themes shipped in the assets, in no particular order. Themes whose
     * config.ini cannot be read are skipped.
     */
    public static List<SoundTheme> listThemes(Context context) {
        // Themes ship inside the APK and cannot change at runtime, so the
        // parsed list is cached for the process; this used to re-open and
        // parse every config.ini on each keyboard open.
        List<SoundTheme> cached = themeCache;
        if (cached != null) {
            return cached;
        }
        List<SoundTheme> themes = new ArrayList<>();
        AssetManager assets = context.getAssets();
        try {
            String[] folders = assets.list(ASSETS_ROOT);
            if (folders == null) {
                return themes;
            }
            for (String folder : folders) {
                SoundTheme theme = load(context, folder);
                if (theme != null) {
                    themes.add(theme);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to list sound themes", e);
        }
        themeCache = themes;
        return themes;
    }

    /**
     * Load the theme matching a stored preference value. The value is first
     * matched against the stable theme {@link #id}; for backwards
     * compatibility with settings saved before ids existed it also falls back
     * to matching the folder name.
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

    // Parse the config.ini in the given asset folder.
    private static SoundTheme load(Context context, String folderName) {
        String iniPath = ASSETS_ROOT + "/" + folderName + "/" + CONFIG_FILE;
        String id = folderName;
        String name = folderName;
        String author = "";
        String version = "";
        Map<FeedbackEvent, String> sounds = new HashMap<>();

        try {
            InputStream is = context.getAssets().open(iniPath);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, "UTF-8"));
            String line;
            String section = "";
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")
                        || line.startsWith(";")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1)
                            .toLowerCase();
                    continue;
                }
                int equalsIndex = line.indexOf('=');
                if (equalsIndex <= 0) {
                    continue;
                }
                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();
                if (SECTION_META.equals(section)) {
                    if (KEY_ID.equalsIgnoreCase(key)) {
                        id = value;
                    } else if (KEY_NAME.equalsIgnoreCase(key)) {
                        name = value;
                    } else if (KEY_AUTHOR.equalsIgnoreCase(key)) {
                        author = value;
                    } else if (KEY_VERSION.equalsIgnoreCase(key)) {
                        version = value;
                    }
                } else if (SECTION_SOUNDS.equals(section)) {
                    FeedbackEvent event = FeedbackEvent.fromConfigKey(key);
                    if (event != null) {
                        sounds.put(event, value);
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read sound theme " + iniPath, e);
            return null;
        }

        SoundTheme theme = new SoundTheme(id, folderName, name, author,
                version);
        theme.sounds.putAll(sounds);
        return theme;
    }
}
