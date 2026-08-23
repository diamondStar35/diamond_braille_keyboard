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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;

/**
 * Persists the user's abbreviations to a plain text file in INI format:
 *
 * <pre>
 * # Soft Braille Keyboard abbreviations
 * [abbreviations]
 * brb=be right back
 * addr=123 Main St
 * </pre>
 *
 * <p>The abbreviations are deliberately kept out of SharedPreferences so
 * that the file can be exported and imported on its own, without dragging
 * the rest of the user's settings along. The file lives in device-protected
 * storage, like the app's other settings, so the abbreviations are available
 * to the keyboard on the lock screen (direct boot).
 *
 * <p>The file is cached in memory after the first read, so opening the
 * editor or looking an entry up while typing never reads the disk more than
 * once per process. Writes are atomic (temporary file + rename) so a crash
 * in the middle of a save cannot leave a truncated file behind.
 */
public class AbbreviationStorage {

    private static final String FILE_NAME = "abbreviations.ini";

    // A copy of the last data written to or read from disk, so load() is a
    // no-op on subsequent calls within the same process.
    private static List<Abbreviation> cache;
    private static boolean cacheLoaded;

    private final File file;

    // The keyboard looks up abbreviations on every space press; handing it a
    // shared instance avoids re-resolving the device-protected files
    // directory (a context allocation) per keystroke. The data itself is
    // already process-cached above.
    private static volatile AbbreviationStorage shared;

    /** The shared instance for lookups while typing. */
    public static AbbreviationStorage getInstance(Context context) {
        AbbreviationStorage result = shared;
        if (result == null) {
            synchronized (AbbreviationStorage.class) {
                result = shared;
                if (result == null) {
                    result = new AbbreviationStorage(context);
                    shared = result;
                }
            }
        }
        return result;
    }

    public AbbreviationStorage(Context context) {
        file = new File(context.createDeviceProtectedStorageContext()
                .getFilesDir(), FILE_NAME);
    }

    /**
     * Read all entries, or return the in-memory cache when it is already
     * loaded. A missing or unreadable file simply yields an empty list.
     *
     * @return A new list of the stored entries (a copy, so callers can edit
     *         it without corrupting the cache).
     */
    public List<Abbreviation> load() {
        return new ArrayList<Abbreviation>(cached());
    }

    // The in-memory list, reading the file on first use in this process.
    // The cache itself is static (shared by every instance, so the editor
    // and the keyboard see the same data), but the first read needs the
    // file location of this instance.
    private List<Abbreviation> cached() {
        if (!cacheLoaded) {
            cache = readFromDisk();
            cacheLoaded = true;
        }
        return cache;
    }

    /**
     * Write all entries to disk and refresh the in-memory cache. The write is
     * atomic: the data is written to a temporary file first, which is then
     * moved into place.
     *
     * @param abbreviations The complete list of entries to store.
     * @throws IOException If the file cannot be written.
     */
    public void save(List<Abbreviation> abbreviations) throws IOException {
        writeToDisk(abbreviations);
        cache = new ArrayList<Abbreviation>(abbreviations);
        cacheLoaded = true;
    }

    /**
     * Look up the expansion for an abbreviation, matching case-insensitively
     * so an abbreviation typed at the start of a sentence (capitalised by
     * auto-capitalisation) still expands. Uses the in-memory cache, so this
     * is cheap to call from the keyboard on every space press.
     *
     * @param abbreviation The typed text before the cursor.
     * @return The stored expansion, or null if the abbreviation is not
     *         defined.
     */
    public String findExpansion(String abbreviation) {
        // Iterate the cached list directly: this is called by the keyboard
        // on every space press, so avoid copying the list each time.
        for (Abbreviation entry : cached()) {
            if (entry.getAbbreviation().equalsIgnoreCase(abbreviation)) {
                return entry.getExpansion();
            }
        }
        return null;
    }

    private List<Abbreviation> readFromDisk() {
        List<Abbreviation> abbreviations = new ArrayList<Abbreviation>();
        if (!file.exists()) {
            return abbreviations;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip blank lines, comments and section headers. Comments
                // and section headers make the file readable as an INI file.
                if (line.isEmpty() || line.charAt(0) == '#'
                        || line.charAt(0) == ';' || line.charAt(0) == '[') {
                    continue;
                }
                int equals = indexOfUnescapedEquals(line);
                if (equals < 0) {
                    // A line without '=' is malformed; ignore it.
                    continue;
                }
                String abbreviation = unescape(line.substring(0, equals));
                String expansion = unescape(line.substring(equals + 1));
                if (!abbreviation.isEmpty()) {
                    abbreviations.add(new Abbreviation(abbreviation,
                            expansion));
                }
            }
        } catch (IOException e) {
            // A missing or unreadable file simply yields no entries.
        }
        return abbreviations;
    }

    private void writeToDisk(List<Abbreviation> abbreviations)
            throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        // Write to a temporary file first, then rename it into place so a
        // crash mid-write cannot leave a truncated abbreviations file.
        File temp = new File(parent, file.getName() + ".tmp");
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            writer.write("# Soft Braille Keyboard abbreviations\n");
            writer.write("[abbreviations]\n");
            for (Abbreviation abbreviation : abbreviations) {
                writer.write(escape(abbreviation.getAbbreviation()));
                writer.write('=');
                writer.write(escape(abbreviation.getExpansion()));
                writer.write('\n');
            }
        }
        if (!temp.renameTo(file)) {
            // On some platforms renameTo refuses to replace an existing
            // file; remove it first as a fallback.
            if (file.exists() && !file.delete()) {
                temp.delete();
                throw new IOException("Could not replace " + file);
            }
            if (!temp.renameTo(file)) {
                temp.delete();
                throw new IOException("Could not write " + file);
            }
        }
    }

    // Escape '\\', newlines and '=' so any text can round-trip through the
    // INI file. The '=' escape matters for abbreviations, which are looked
    // up verbatim; newlines keep multi-line expansions on a single line.
    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                escaped.append("\\\\");
            } else if (c == '\n') {
                escaped.append("\\n");
            } else if (c == '=') {
                escaped.append("\\=");
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static String unescape(String value) {
        StringBuilder unescaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'n') {
                    unescaped.append('\n');
                } else if (next == '=') {
                    unescaped.append('=');
                } else if (next == '\\') {
                    unescaped.append('\\');
                } else {
                    unescaped.append('\\').append(next);
                }
            } else {
                unescaped.append(c);
            }
        }
        return unescaped.toString();
    }

    // Find the first '=' that is not part of an escape sequence.
    private static int indexOfUnescapedEquals(String line) {
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            if (c == '=' && !escaped) {
                return i;
            }
            escaped = false;
        }
        return -1;
    }
}
