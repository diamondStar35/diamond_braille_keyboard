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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;

/**
 * Reads and writes {@code .st} sound theme archives.
 *
 * <p>An archive is a ZIP whose first entry is a stored, uncompressed
 * {@code mimetype} file - the trick ODF and EPUB use - so a theme can be
 * recognised by its bytes at a fixed offset rather than by its extension or
 * by whatever MIME type a content provider decides to report.
 *
 * <pre>
 * theme.st
 * |-- mimetype        STORED, first entry
 * |-- config.json
 * `-- sounds/
 *       type.ogg
 * </pre>
 *
 * <p>Nothing plays from an archive. Importing unpacks it into a staging
 * directory, validates every part of it, and only then does
 * {@link ThemeLibrary} move it into the library; exporting is the reverse.
 * {@link android.media.SoundPool} cannot read a sample out of a ZIP, which is
 * what makes the unpacked directory the real installed form.
 */
final class ThemeArchive {

    private static final String TAG = "ThemeArchive";

    /** The magic string in the first entry of every archive. */
    static final String MIMETYPE = "application/x-sbk-sound-theme";

    /** The name of that first entry. */
    private static final String MIMETYPE_ENTRY = "mimetype";

    /** Directory inside an archive, and an installed theme, holding samples. */
    static final String SOUNDS_DIR = "sounds";

    /** File extension for an archive. */
    static final String EXTENSION = ".st";

    // Ceilings. A theme is nine short samples and a small manifest; anything
    // beyond these is either broken or hostile. Sizes are measured while
    // reading, never taken from ZipEntry.getSize(), which is a claim made by
    // the archive rather than a fact about it.
    private static final int MAX_ENTRIES = 64;
    private static final long MAX_ENTRY_BYTES = 8L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;

    /** Longest sample accepted, in microseconds. These are UI blips. */
    private static final long MAX_SAMPLE_MICROS = 10L * 1000 * 1000;

    private ThemeArchive() {
    }

    /**
     * A validated, unpacked theme sitting in staging, not yet in the library.
     * Either {@link ThemeLibrary#commit} it or {@link #discard} it - leaving
     * one behind wastes space until the next sweep.
     */
    static final class Staged {
        /** The staging directory holding the unpacked theme. */
        final File directory;

        /** The theme parsed from the staged manifest. */
        final SoundTheme theme;

        Staged(File directory, SoundTheme theme) {
            this.directory = directory;
            this.theme = theme;
        }
    }

    /** Delete a staged theme that will not be committed. */
    static void discard(Staged staged) {
        if (staged != null) {
            ThemeLibrary.deleteRecursively(staged.directory);
        }
    }

    // ---- Reading --------------------------------------------------------

    /**
     * Unpack and validate an archive into a staging directory.
     *
     * <p>Everything is checked before the caller is given anything: the
     * magic entry, every entry's destination, the size ceilings, the
     * manifest, and that every sample the manifest names is present and
     * actually decodes as audio. A theme that fails here has not touched the
     * library.
     *
     * @throws ThemeException with a user-facing reason.
     */
    static Staged stage(Context context, InputStream input)
            throws ThemeException {
        File staging = ThemeLibrary.createStagingDirectory(context);
        if (staging == null) {
            throw new ThemeException(R.string.theme_import_failed_storage,
                    "cannot create staging directory");
        }
        try {
            unpack(input, staging);
            SoundTheme theme = readManifest(staging);
            verifySamples(theme, staging);
            return new Staged(staging, theme);
        } catch (ThemeException e) {
            ThemeLibrary.deleteRecursively(staging);
            throw e;
        } catch (RuntimeException e) {
            ThemeLibrary.deleteRecursively(staging);
            throw new ThemeException(R.string.theme_import_failed_damaged,
                    "unexpected failure staging theme", e);
        }
    }

    // Unpack every entry into the staging directory, refusing anything that
    // does not belong in a theme.
    private static void unpack(InputStream input, File staging)
            throws ThemeException {
        ZipInputStream zip = new ZipInputStream(input);
        long total = 0;
        int count = 0;
        boolean sawMagic = false;
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) {
                    throw new ThemeException(R.string.theme_import_failed_large,
                            "more than " + MAX_ENTRIES + " entries");
                }
                String name = entry.getName();

                if (count == 1) {
                    // The magic has to be first, which is the whole point of
                    // storing it uncompressed: a reader can identify the
                    // format without scanning the archive.
                    if (!MIMETYPE_ENTRY.equals(name)) {
                        throw new ThemeException(
                                R.string.theme_import_failed_not_a_theme,
                                "first entry is " + name);
                    }
                    String magic = new String(readCapped(zip,
                            MIMETYPE.length() + 16L), "UTF-8").trim();
                    if (!MIMETYPE.equals(magic)) {
                        throw new ThemeException(
                                R.string.theme_import_failed_not_a_theme,
                                "magic is " + magic);
                    }
                    sawMagic = true;
                    zip.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }

                File destination = resolveEntry(staging, name);
                File parent = destination.getParentFile();
                if (parent != null && !parent.isDirectory()
                        && !parent.mkdirs()) {
                    throw new ThemeException(
                            R.string.theme_import_failed_storage,
                            "cannot create " + parent);
                }
                total += copyCapped(zip, destination,
                        Math.min(MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - total));
                zip.closeEntry();
            }
        } catch (ThemeException e) {
            throw e;
        } catch (IOException e) {
            throw new ThemeException(R.string.theme_import_failed_damaged,
                    "cannot read archive", e);
        } finally {
            try {
                zip.close();
            } catch (IOException e) {
                // The content is already read or already failed.
            }
        }
        if (!sawMagic) {
            throw new ThemeException(R.string.theme_import_failed_not_a_theme,
                    "empty archive");
        }
    }

    /**
     * Turn an entry name into the file it may be written to, refusing
     * anything that escapes the staging directory.
     *
     * <p>An entry name is text chosen by whoever built the archive. An entry
     * called {@code ../../shared_prefs/prefs.xml} would overwrite the user's
     * whole configuration during what looks like installing a theme, so the
     * destination is canonicalized and checked to be inside staging - the
     * name itself is never trusted, not even after stripping the parts that
     * look dangerous.
     */
    private static File resolveEntry(File staging, String name)
            throws ThemeException {
        if (name == null || name.isEmpty() || name.contains("\0")) {
            throw new ThemeException(R.string.theme_import_failed_damaged,
                    "empty entry name");
        }
        try {
            File root = staging.getCanonicalFile();
            File destination = new File(root, name).getCanonicalFile();
            if (!destination.getPath().startsWith(
                    root.getPath() + File.separator)) {
                throw new ThemeException(
                        R.string.theme_import_failed_not_a_theme,
                        "entry escapes the theme: " + name);
            }
            return destination;
        } catch (IOException e) {
            throw new ThemeException(R.string.theme_import_failed_damaged,
                    "cannot resolve entry " + name, e);
        }
    }

    // Read the manifest out of a staged theme.
    private static SoundTheme readManifest(File staging)
            throws ThemeException {
        File manifest = new File(staging, SoundTheme.CONFIG_FILE);
        if (!manifest.isFile()) {
            throw new ThemeException(R.string.theme_import_failed_not_a_theme,
                    "no " + SoundTheme.CONFIG_FILE);
        }
        SoundTheme theme = SoundTheme.readFrom(staging);
        if (theme == null) {
            throw new ThemeException(R.string.theme_import_failed_damaged,
                    "manifest does not parse");
        }
        if (theme.displayName == null || theme.displayName.isEmpty()) {
            throw new ThemeException(R.string.theme_import_failed_damaged,
                    "manifest has no name");
        }
        return theme;
    }

    // Every sample the manifest names must exist and decode as audio. A
    // theme that fails this check would install cleanly and then fall silent
    // mid-typing, with nothing to tell the user why.
    private static void verifySamples(SoundTheme theme, File staging)
            throws ThemeException {
        SampleSource source = new SampleSource.Files(staging);
        for (Map.Entry<FeedbackEvent, String> binding
                : theme.soundMap().entrySet()) {
            String reference = binding.getValue();
            if (SoundTheme.SOUND_NONE.equalsIgnoreCase(reference)
                    || SoundTheme.SOUND_SYSTEM.equalsIgnoreCase(reference)) {
                continue;
            }
            InputStream probe = null;
            try {
                // Goes through the same resolver the player uses, so a
                // reference that escapes the theme is refused here too.
                probe = source.open(reference);
            } catch (IOException e) {
                throw new ThemeException(
                        R.string.theme_import_failed_missing_sound,
                        "missing sample " + reference, e);
            } finally {
                closeQuietly(probe);
            }
            verifyDecodes(new File(staging, reference), reference);
        }
    }

    // Confirm a file is audio the media framework can actually play, using
    // the same framework SoundPool loads through.
    private static void verifyDecodes(File sample, String reference)
            throws ThemeException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(sample.getPath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) {
                    continue;
                }
                if (format.containsKey(MediaFormat.KEY_DURATION)
                        && format.getLong(MediaFormat.KEY_DURATION)
                                > MAX_SAMPLE_MICROS) {
                    throw new ThemeException(
                            R.string.theme_import_failed_sound_too_long,
                            "sample too long: " + reference);
                }
                return;
            }
            throw new ThemeException(R.string.theme_import_failed_bad_sound,
                    "no audio track in " + reference);
        } catch (IOException e) {
            throw new ThemeException(R.string.theme_import_failed_bad_sound,
                    "cannot decode " + reference, e);
        } catch (IllegalArgumentException e) {
            throw new ThemeException(R.string.theme_import_failed_bad_sound,
                    "not media: " + reference, e);
        } finally {
            extractor.release();
        }
    }

    // ---- Writing --------------------------------------------------------

    /**
     * Write a theme to an archive.
     *
     * <p>Built-in themes keep their samples flat beside their manifest,
     * which is the layout the assets have always used; archives always put
     * them under {@code sounds/}. Exporting a built-in therefore rewrites
     * the sample references as it copies, so every archive - and every theme
     * installed from one - has the same shape regardless of where it came
     * from.
     *
     * @throws ThemeException with a user-facing reason.
     */
    static void write(Context context, SoundTheme theme, OutputStream output)
            throws ThemeException {
        SampleSource source = theme.sampleSource(context);
        Map<FeedbackEvent, String> bindings = theme.soundMap();
        ZipOutputStream zip = new ZipOutputStream(output);
        try {
            writeMagic(zip);

            // Copy each distinct sample once, then rewrite the bindings onto
            // the names it was given here.
            Map<String, String> plan = planSampleNames(bindings);
            for (Map.Entry<String, String> sample : plan.entrySet()) {
                writeSample(zip, source, sample.getKey(), sample.getValue());
            }
            Map<FeedbackEvent, String> written =
                    new java.util.HashMap<FeedbackEvent, String>();
            for (Map.Entry<FeedbackEvent, String> binding
                    : bindings.entrySet()) {
                String archived = plan.get(binding.getValue());
                written.put(binding.getKey(),
                        archived != null ? archived : binding.getValue());
            }

            zip.putNextEntry(new ZipEntry(SoundTheme.CONFIG_FILE));
            zip.write(SoundTheme.manifest(theme.id, theme.displayName,
                    theme.author, theme.version, written).getBytes("UTF-8"));
            zip.closeEntry();
            zip.finish();
        } catch (IOException e) {
            throw new ThemeException(R.string.theme_export_failed,
                    "cannot write archive", e);
        }
    }

    /**
     * Decide where each distinct sample goes under {@code sounds/}.
     *
     * <p>Several events routinely share one sample, which must be copied
     * once and referenced twice. Two <em>different</em> samples can also
     * share a file name - a theme carrying {@code a/click.ogg} and
     * {@code b/click.ogg} - and flattening both to {@code sounds/click.ogg}
     * would silently bind one event to the other's sound. Distinct
     * references therefore always get distinct names.
     *
     * @param bindings Event bindings, sentinels included; those are skipped.
     * @return Sample reference to the path it takes in the archive.
     */
    static Map<String, String> planSampleNames(
            Map<FeedbackEvent, String> bindings) {
        Map<String, String> planned = new java.util.LinkedHashMap<String,
                String>();
        java.util.Set<String> used = new java.util.HashSet<String>();
        for (String reference : bindings.values()) {
            if (reference == null
                    || SoundTheme.SOUND_NONE.equalsIgnoreCase(reference)
                    || SoundTheme.SOUND_SYSTEM.equalsIgnoreCase(reference)
                    || planned.containsKey(reference)) {
                continue;
            }
            String name = new File(reference).getName();
            if (name.isEmpty()) {
                name = "sound";
            }
            String candidate = name;
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            for (int suffix = 2; !used.add(candidate) && suffix < 1000;
                    suffix++) {
                candidate = stem + "-" + suffix + extension;
            }
            planned.put(reference, SOUNDS_DIR + "/" + candidate);
        }
        return planned;
    }

    private static void writeSample(ZipOutputStream zip, SampleSource source,
            String reference, String archived) throws ThemeException {
        InputStream input = null;
        try {
            input = source.open(reference);
            zip.putNextEntry(new ZipEntry(archived));
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                zip.write(chunk, 0, read);
            }
            zip.closeEntry();
        } catch (IOException e) {
            throw new ThemeException(R.string.theme_export_failed,
                    "cannot copy sample " + reference, e);
        } finally {
            closeQuietly(input);
        }
    }

    // The magic entry has to be stored rather than deflated, which means
    // filling in the size and CRC by hand - ZipOutputStream only computes
    // those for entries it compresses.
    private static void writeMagic(ZipOutputStream zip) throws IOException {
        byte[] magic = MIMETYPE.getBytes("UTF-8");
        ZipEntry entry = new ZipEntry(MIMETYPE_ENTRY);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(magic.length);
        entry.setCompressedSize(magic.length);
        CRC32 crc = new CRC32();
        crc.update(magic);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(magic);
        zip.closeEntry();
    }

    // ---- Plumbing -------------------------------------------------------

    private static byte[] readCapped(InputStream input, long limit)
            throws IOException {
        java.io.ByteArrayOutputStream buffer =
                new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[512];
        int read;
        while ((read = input.read(chunk)) != -1) {
            if (buffer.size() + read > limit) {
                throw new IOException("entry longer than " + limit);
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    // Copy one entry to disk, counting the bytes actually read rather than
    // trusting the size the archive declares.
    private static long copyCapped(InputStream input, File destination,
            long limit) throws ThemeException {
        if (limit <= 0) {
            throw new ThemeException(R.string.theme_import_failed_large,
                    "archive over " + MAX_TOTAL_BYTES + " bytes");
        }
        OutputStream output = null;
        long written = 0;
        try {
            output = new FileOutputStream(destination);
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                written += read;
                if (written > limit) {
                    throw new ThemeException(
                            R.string.theme_import_failed_large,
                            "entry over " + limit + " bytes: "
                                    + destination.getName());
                }
                output.write(chunk, 0, read);
            }
            return written;
        } catch (IOException e) {
            throw new ThemeException(R.string.theme_import_failed_damaged,
                    "cannot write " + destination, e);
        } finally {
            closeQuietly(output);
        }
    }

    private static void closeQuietly(java.io.Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Nothing the caller could act on.
            }
        }
    }
}
