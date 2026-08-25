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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;

/**
 * The on-device library of installed sound themes: where they live, what is
 * in it, and when it last changed.
 *
 * <p>Themes are stored unpacked, one directory per theme, each holding a
 * {@code config.json} manifest and its sample files. The {@code .st} archive
 * is only a transport format - it is unpacked on import and zipped back up on
 * export, and is never what the keyboard plays from, because
 * {@link android.media.SoundPool} cannot read a sample out of an archive.
 *
 * <h3>Why device-protected storage</h3>
 *
 * <p>The keyboard runs before the device is first unlocked, which is why
 * {@link Options#getSharedPreferences} keeps the preferences in
 * device-protected storage. Themes have to follow: left in the default
 * (credential-protected) files directory, every installed theme would go
 * silent on the lock screen while the built-in ones carried on working, since
 * assets are always readable. That failure reads as a theme bug and is
 * nothing of the sort.
 *
 * <h3>The epoch</h3>
 *
 * <p>Everything that reads the library caches: {@link SoundTheme} caches the
 * parsed list, {@link SoundThemeManager} caches the samples it has loaded
 * into its pool. Both are correct only for as long as nothing has changed on
 * disk. {@link #epoch()} is the counter they compare against, and
 * {@link #invalidate()} is what every mutation must call.
 */
final class ThemeLibrary {

    /** Directory under the device-protected files dir holding all themes. */
    private static final String THEMES_DIR = "themes";

    /** Suffix marking a working copy created by the editor. */
    static final String EDITING_SUFFIX = ".editing";

    /** Prefix marking a half-unpacked import, never a visible theme. */
    static final String STAGING_PREFIX = ".staging-";

    /**
     * Bumped by every install, edit and delete. Readers hold the value they
     * last saw and reload when it moves. Starts at 1 so a cached "never
     * loaded" sentinel of 0 or -1 always looks stale.
     */
    private static final AtomicInteger epoch = new AtomicInteger(1);

    private ThemeLibrary() {
    }

    /** The current library epoch; see the class documentation. */
    static int epoch() {
        return epoch.get();
    }

    /**
     * Record that the library changed on disk, so every cache built from it
     * is rebuilt on next use. Must be called by anything that installs,
     * edits or deletes a theme.
     */
    static void invalidate() {
        epoch.incrementAndGet();
    }

    /**
     * The directory holding all installed themes, whether or not it exists
     * yet. Nothing is created; see {@link #createThemesDirectory}.
     */
    static File themesDirectory(Context context) {
        Context deviceContext = context
                .createDeviceProtectedStorageContext();
        return new File(deviceContext.getFilesDir(), THEMES_DIR);
    }

    /**
     * The themes directory, created if it does not exist yet. Only for the
     * write paths - installing, editing - so that merely listing themes,
     * which the keyboard does as it opens, never writes to disk.
     *
     * @return The themes directory, or {@code null} when it cannot be
     *         created.
     */
    static File createThemesDirectory(Context context) {
        File themes = themesDirectory(context);
        if (!themes.isDirectory() && !themes.mkdirs()) {
            return null;
        }
        return themes;
    }

    /**
     * The directories of every installed theme, ignoring working copies and
     * half-finished imports. Whether each one holds a readable manifest is
     * for the caller to decide - this only reports what is on disk.
     */
    static List<File> installedDirectories(Context context) {
        List<File> directories = new ArrayList<File>();
        // listFiles() returns null when the directory does not exist, which
        // is the normal state until the first theme is installed.
        File[] entries = themesDirectory(context).listFiles();
        if (entries == null) {
            return directories;
        }
        for (File entry : entries) {
            if (entry.isDirectory() && !isTransient(entry.getName())) {
                directories.add(entry);
            }
        }
        return directories;
    }

    /**
     * Whether a directory name belongs to a working copy or a staged import
     * rather than to an installed theme.
     */
    static boolean isTransient(String directoryName) {
        return directoryName.startsWith(STAGING_PREFIX)
                || directoryName.endsWith(EDITING_SUFFIX);
    }

    // ---- Mutating the library -------------------------------------------

    /** What to do when an imported theme has the id of one already here. */
    enum Collision {
        /** Overwrite the installed theme; the id stays the same. */
        REPLACE,
        /** Install alongside it under a fresh id and a distinguished name. */
        KEEP_BOTH
    }

    /**
     * The installed theme an incoming one would collide with, or
     * {@code null} when its id is free.
     */
    static SoundTheme collidingTheme(Context context, String id) {
        if (id == null) {
            return null;
        }
        for (SoundTheme theme : SoundTheme.listThemes(context)) {
            if (theme.origin == SoundTheme.Origin.USER
                    && theme.id.equalsIgnoreCase(id)) {
                return theme;
            }
        }
        return null;
    }

    /**
     * Whether an id belongs to a theme shipped with the app.
     *
     * <p>Built-in ids are reserved. Letting an imported theme take one would
     * shadow the theme the keyboard falls back to, so a file claiming
     * {@code system_default} is refused rather than quietly renamed.
     */
    static boolean isReservedId(Context context, String id) {
        if (id == null) {
            return false;
        }
        if (SoundTheme.ID_OFF.equalsIgnoreCase(id)) {
            return true;
        }
        for (SoundTheme theme : SoundTheme.listThemes(context)) {
            if (theme.origin == SoundTheme.Origin.BUILT_IN
                    && theme.id.equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Move a validated, staged theme into the library.
     *
     * <p>The move is a directory rename, so a theme is either fully
     * installed or not installed at all - there is no window in which the
     * library holds a half-written one.
     *
     * @param collision What to do if the id is already taken; ignored when
     *            it is free.
     * @return The installed theme, re-read from its final location.
     * @throws ThemeException with a user-facing reason.
     */
    static SoundTheme commit(Context context, ThemeArchive.Staged staged,
            Collision collision) throws ThemeException {
        SoundTheme theme = staged.theme;
        if (isReservedId(context, theme.id)) {
            throw new ThemeException(R.string.theme_import_failed_reserved,
                    "reserved id " + theme.id);
        }

        SoundTheme existing = collidingTheme(context, theme.id);
        File target;
        if (existing != null && collision == Collision.REPLACE) {
            // Take the installed theme out of the way first: its directory
            // name is the one the replacement should keep.
            target = existing.directory;
            if (!deleteRecursively(target)) {
                throw new ThemeException(R.string.theme_import_failed_storage,
                        "cannot replace " + target);
            }
        } else {
            String name = theme.displayName;
            String id = theme.id;
            if (existing != null) {
                // Keeping both: a fresh id, and a name that tells them apart
                // in the list.
                id = newId();
                name = context.getString(R.string.theme_copy_name, name);
                rewriteManifest(staged.directory, theme, id, name);
            }
            target = freeDirectory(context, slugify(id));
        }

        if (target == null || !staged.directory.renameTo(target)) {
            throw new ThemeException(R.string.theme_import_failed_storage,
                    "cannot move staged theme into the library");
        }
        invalidate();

        SoundTheme installed = SoundTheme.readFrom(target);
        if (installed == null) {
            // The manifest read cleanly in staging, so this means the move
            // itself went wrong; do not leave a broken theme in the list.
            deleteRecursively(target);
            invalidate();
            throw new ThemeException(R.string.theme_import_failed_storage,
                    "installed theme is unreadable");
        }
        return installed;
    }

    /**
     * Copy a theme under a new id and name. Works for a built-in as well as
     * an installed one, which is how someone starts from a theme they like
     * rather than from silence.
     *
     * @throws ThemeException with a user-facing reason.
     */
    static SoundTheme duplicate(Context context, SoundTheme source)
            throws ThemeException {
        File staging = createStagingDirectory(context);
        if (staging == null) {
            throw new ThemeException(R.string.theme_copy_failed,
                    "cannot create staging directory");
        }
        String id = newId();
        String name = context.getString(R.string.theme_copy_name,
                source.displayName);
        try {
            materialize(context, source, staging, id, name);
            SoundTheme staged = SoundTheme.readFrom(staging);
            if (staged == null) {
                throw new ThemeException(R.string.theme_copy_failed,
                        "copied manifest is unreadable");
            }
            return commit(context, new ThemeArchive.Staged(staging, staged),
                    Collision.KEEP_BOTH);
        } catch (ThemeException e) {
            deleteRecursively(staging);
            throw e;
        }
    }

    /**
     * Write a copy of a theme into a directory, in the layout an installed
     * theme uses: manifest at the root, samples under {@code sounds/}.
     *
     * <p>Built-in themes keep their samples flat beside their manifest, so
     * this is also where that older layout is normalised - after it, every
     * theme outside the assets has the same shape.
     */
    private static void materialize(Context context, SoundTheme source,
            File target, String id, String name) throws ThemeException {
        File soundsDir = new File(target, ThemeArchive.SOUNDS_DIR);
        if (!soundsDir.isDirectory() && !soundsDir.mkdirs()) {
            throw new ThemeException(R.string.theme_copy_failed,
                    "cannot create " + soundsDir);
        }
        SampleSource samples = source.sampleSource(context);
        Map<FeedbackEvent, String> bindings = source.soundMap();
        // The same naming the archive writer uses, so a duplicated theme and
        // an exported one lay their samples out identically.
        Map<String, String> plan = ThemeArchive.planSampleNames(bindings);
        for (Map.Entry<String, String> sample : plan.entrySet()) {
            copyStream(samples, sample.getKey(),
                    new File(target, sample.getValue()));
        }
        Map<FeedbackEvent, String> copied =
                new HashMap<FeedbackEvent, String>();
        for (Map.Entry<FeedbackEvent, String> binding : bindings.entrySet()) {
            String destination = plan.get(binding.getValue());
            copied.put(binding.getKey(),
                    destination != null ? destination : binding.getValue());
        }
        writeManifest(target, SoundTheme.manifest(id, name, source.author,
                source.version, copied));
    }

    private static void copyStream(SampleSource samples, String reference,
            File destination) throws ThemeException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = samples.open(reference);
            output = new FileOutputStream(destination);
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                output.write(chunk, 0, read);
            }
        } catch (IOException e) {
            throw new ThemeException(R.string.theme_copy_failed,
                    "cannot copy sample " + reference, e);
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    // Rewrite a staged manifest under a new id and name, keeping its
    // bindings. Used when keeping both copies of a colliding import.
    private static void rewriteManifest(File directory, SoundTheme theme,
            String id, String name) throws ThemeException {
        writeManifest(directory, SoundTheme.manifest(id, name, theme.author,
                theme.version, theme.soundMap()));
    }

    private static void writeManifest(File directory, String manifest)
            throws ThemeException {
        OutputStream output = null;
        try {
            output = new FileOutputStream(
                    new File(directory, SoundTheme.CONFIG_FILE));
            output.write(manifest.getBytes("UTF-8"));
        } catch (IOException e) {
            throw new ThemeException(R.string.theme_copy_failed,
                    "cannot write manifest", e);
        } finally {
            closeQuietly(output);
        }
    }

    /**
     * Remove an installed theme. Built-in themes cannot be removed.
     *
     * <p>When the theme being removed is the active one, the preference is
     * moved to the default first, so the keyboard never ends up pointing at
     * a theme that no longer exists.
     *
     * @return true when the theme was removed.
     */
    static boolean delete(Context context, SoundTheme theme) {
        if (theme == null || !theme.isEditable() || theme.directory == null) {
            return false;
        }
        String active = Options.getStringPreference(context,
                R.string.pref_sound_theme_key,
                context.getString(R.string.pref_sound_theme_default));
        if (theme.id.equalsIgnoreCase(active)
                || theme.folderName.equalsIgnoreCase(active)) {
            Options.writeStringPreference(context,
                    R.string.pref_sound_theme_key,
                    context.getString(R.string.pref_sound_theme_default));
        }
        boolean removed = deleteRecursively(theme.directory);
        invalidate();
        return removed;
    }

    /** A fresh theme id. Random, so two devices never mint the same one. */
    static String newId() {
        return UUID.randomUUID().toString();
    }

    // ---- Directories ----------------------------------------------------

    /**
     * A new, empty staging directory for an import or a copy in progress.
     * Staging names are excluded from listings, so a half-written theme is
     * never shown to the user.
     */
    static File createStagingDirectory(Context context) {
        File themes = createThemesDirectory(context);
        if (themes == null) {
            return null;
        }
        for (int attempt = 0; attempt < 100; attempt++) {
            File staging = new File(themes,
                    STAGING_PREFIX + System.nanoTime());
            if (staging.mkdir()) {
                return staging;
            }
        }
        return null;
    }

    // An unused directory named after the slug, disambiguated when taken:
    // two different ids can slug to the same name.
    private static File freeDirectory(Context context, String slug) {
        File themes = createThemesDirectory(context);
        if (themes == null) {
            return null;
        }
        File candidate = new File(themes, slug);
        for (int suffix = 2; candidate.exists() && suffix < 1000; suffix++) {
            candidate = new File(themes, slug + "-" + suffix);
        }
        return candidate.exists() ? null : candidate;
    }

    /** Delete a file or a directory and everything under it. */
    static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }

    /**
     * Remove staging directories and editor working copies left behind by a
     * crash or a kill. Safe to call at any time: nothing outside a theme's
     * own lifetime refers to them.
     */
    static void sweepTransient(Context context) {
        File[] entries = themesDirectory(context).listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()
                    && entry.getName().startsWith(STAGING_PREFIX)) {
                deleteRecursively(entry);
            }
        }
    }

    private static void closeQuietly(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Nothing the caller could act on.
            }
        }
    }

    /**
     * A directory name derived from a theme id, safe to use as a path.
     *
     * <p>An id can arrive from a manifest written by someone else, so it is
     * never used as a path directly: everything outside a conservative
     * character set is replaced, and the result is length-limited. Uniqueness
     * is the caller's problem - two ids can slug to the same name, and the
     * installer is what resolves that.
     *
     * @return A safe directory name, never empty.
     */
    static String slugify(String id) {
        if (id == null) {
            return "theme";
        }
        StringBuilder slug = new StringBuilder(id.length());
        for (int i = 0; i < id.length() && slug.length() < 48; i++) {
            char c = id.charAt(i);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            slug.append(safe ? c : '_');
        }
        // A name of only replacement characters, or an empty id, would give
        // every such theme the same directory; fall back to a fixed stem and
        // let the installer disambiguate.
        String result = slug.toString();
        return result.replace("_", "").isEmpty() ? "theme" : result;
    }
}
