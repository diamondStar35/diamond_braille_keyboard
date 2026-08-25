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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.media.SoundPool;
import android.util.Log;

/**
 * Where a theme's sound samples come from.
 *
 * <p>Themes shipped in the APK live in the assets and load through an
 * {@link android.content.res.AssetFileDescriptor}; themes the user installed
 * live in a directory and load by path. This is the seam that lets
 * {@link SoundThemeManager} play both without knowing which is which - it
 * asks the theme for its source and hands it sample references from the
 * manifest.
 *
 * <p>A reference is a path relative to the theme root, exactly as written in
 * {@code config.json}. The sentinels {@link SoundTheme#SOUND_SYSTEM} and
 * {@link SoundTheme#SOUND_NONE} are handled by the caller and never reach a
 * source.
 */
interface SampleSource {

    /**
     * Load one sample into the pool.
     *
     * @param pool The pool to load into.
     * @param reference The sample's path, relative to the theme root.
     * @return The SoundPool sample id, or 0 when the sample could not be
     *         loaded (the value SoundPool itself uses for failure).
     */
    int load(SoundPool pool, String reference);

    /**
     * Open one sample for reading, for copying it somewhere else - exporting
     * a theme to an archive, or duplicating it. The caller closes the
     * stream.
     *
     * @throws IOException when the sample cannot be opened.
     */
    InputStream open(String reference) throws IOException;

    /** Samples packaged inside the APK, under {@code assets/sounds/}. */
    final class Assets implements SampleSource {
        private static final String TAG = "SampleSource";

        private final Context context;
        private final String folderName;

        Assets(Context context, String folderName) {
            this.context = context.getApplicationContext();
            this.folderName = folderName;
        }

        @Override
        public int load(SoundPool pool, String reference) {
            String path = path(reference);
            try {
                return pool.load(context.getAssets().openFd(path), 1);
            } catch (IOException e) {
                Log.e(TAG, "Failed to load asset sample " + path, e);
                return 0;
            }
        }

        @Override
        public InputStream open(String reference) throws IOException {
            return context.getAssets().open(path(reference));
        }

        private String path(String reference) {
            return SoundTheme.ASSETS_ROOT + "/" + folderName + "/"
                    + reference;
        }
    }

    /** Samples in an installed theme's directory. */
    final class Files implements SampleSource {
        private static final String TAG = "SampleSource";

        private final File themeDirectory;

        Files(File themeDirectory) {
            this.themeDirectory = themeDirectory;
        }

        @Override
        public int load(SoundPool pool, String reference) {
            File sample = resolve(reference);
            if (sample == null) {
                return 0;
            }
            try {
                return pool.load(sample.getPath(), 1);
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to load sample " + sample, e);
                return 0;
            }
        }

        @Override
        public InputStream open(String reference) throws IOException {
            File sample = resolve(reference);
            if (sample == null) {
                throw new IOException("no such sample: " + reference);
            }
            return new FileInputStream(sample);
        }

        // Resolve a reference inside the theme directory, refusing anything
        // that climbs out of it. Import validation rejects such references
        // long before they get here, but a manifest is user-supplied data
        // and the component that turns it into a path is the right place to
        // be certain about it.
        private File resolve(String reference) {
            if (reference == null || reference.isEmpty()) {
                return null;
            }
            try {
                File root = themeDirectory.getCanonicalFile();
                File sample = new File(root, reference).getCanonicalFile();
                String rootPath = root.getPath() + File.separator;
                if (!sample.getPath().startsWith(rootPath)) {
                    Log.e(TAG, "Refusing sample outside theme: " + reference);
                    return null;
                }
                return sample.isFile() ? sample : null;
            } catch (IOException e) {
                Log.e(TAG, "Failed to resolve sample " + reference, e);
                return null;
            }
        }
    }
}
