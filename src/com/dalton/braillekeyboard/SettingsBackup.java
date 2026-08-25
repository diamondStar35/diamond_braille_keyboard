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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

/**
 * Writes every keyboard setting out to a file the user chooses, and reads one
 * back again.
 *
 * <p>The file is plain XML in exactly the layout Android uses for its own
 * SharedPreferences files - a {@code <map>} of typed entries - so a backup is
 * readable, hand-editable, and interchangeable with the preferences file the
 * system itself stores.
 *
 * <p>Restoring replaces the whole set of settings rather than merging into
 * it: a backup is a snapshot of the keyboard at a moment in time, and
 * half-applying one would leave a configuration that never actually existed.
 *
 * <p>Both operations do file I/O and must be called off the main thread.
 */
final class SettingsBackup {

    private static final String TAG_MAP = "map";
    private static final String TAG_SET = "set";
    private static final String TAG_STRING = "string";
    private static final String TAG_BOOLEAN = "boolean";
    private static final String TAG_INT = "int";
    private static final String TAG_LONG = "long";
    private static final String TAG_FLOAT = "float";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";
    private static final String ENCODING = "utf-8";

    private SettingsBackup() {
    }

    /** Suggested file name for a new backup, stamped so backups can coexist. */
    static String suggestedFileName() {
        return "braille_keyboard_settings_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        .format(new Date())
                + ".xml";
    }

    /**
     * Writes the current settings to the document the user picked.
     *
     * @return true when the file was written in full.
     */
    static boolean export(Context context, Uri destination) {
        Map<String, ?> values = Options.getSharedPreferences(context).getAll();
        if (values == null || values.isEmpty()) {
            // Nothing configured yet. An empty file would look like a working
            // backup and restore to nothing, so refuse to write one.
            Diagnostics.log(context, "settings export skipped: no settings");
            return false;
        }

        OutputStream output = null;
        try {
            // "wt" truncates. The picker lets the user overwrite an existing
            // file, and a plain "w" leaves any bytes past the end of the new
            // content in place - a shorter backup written over a longer one
            // would end in trailing junk and fail to parse on the way back.
            output = context.getContentResolver().openOutputStream(
                    destination, "wt");
            if (output == null) {
                return false;
            }
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(output, ENCODING);
            xml.startDocument(ENCODING, Boolean.TRUE);
            xml.text("\n");
            xml.startTag(null, TAG_MAP);
            xml.text("\n");
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                writeEntry(xml, entry.getKey(), entry.getValue());
            }
            xml.endTag(null, TAG_MAP);
            xml.text("\n");
            xml.endDocument();
            xml.flush();
            Diagnostics.log(context, "settings exported, entries="
                    + values.size());
            return true;
        } catch (Exception e) {
            // Anything from a revoked permission to a full disk; the caller
            // only needs to know the backup is not usable.
            Diagnostics.log(context, "settings export failed: " + e);
            return false;
        } finally {
            close(output);
        }
    }

    // One typed entry, in the same shape Android writes for its own
    // preferences file. A type that cannot be represented is skipped rather
    // than written in a form that would not read back.
    private static void writeEntry(XmlSerializer xml, String key, Object value)
            throws IOException {
        if (key == null || value == null) {
            return;
        }
        if (value instanceof Boolean) {
            writeValueTag(xml, TAG_BOOLEAN, key, value.toString());
        } else if (value instanceof Integer) {
            writeValueTag(xml, TAG_INT, key, value.toString());
        } else if (value instanceof Long) {
            writeValueTag(xml, TAG_LONG, key, value.toString());
        } else if (value instanceof Float) {
            writeValueTag(xml, TAG_FLOAT, key, value.toString());
        } else if (value instanceof String) {
            xml.startTag(null, TAG_STRING);
            xml.attribute(null, ATTR_NAME, key);
            xml.text((String) value);
            xml.endTag(null, TAG_STRING);
            xml.text("\n");
        } else if (value instanceof Set) {
            xml.startTag(null, TAG_SET);
            xml.attribute(null, ATTR_NAME, key);
            xml.text("\n");
            for (Object member : (Set<?>) value) {
                if (member == null) {
                    continue;
                }
                xml.startTag(null, TAG_STRING);
                xml.text(member.toString());
                xml.endTag(null, TAG_STRING);
                xml.text("\n");
            }
            xml.endTag(null, TAG_SET);
            xml.text("\n");
        }
    }

    private static void writeValueTag(XmlSerializer xml, String tag,
            String key, String value) throws IOException {
        xml.startTag(null, tag);
        xml.attribute(null, ATTR_NAME, key);
        xml.attribute(null, ATTR_VALUE, value);
        xml.endTag(null, tag);
        xml.text("\n");
    }

    /**
     * Replaces the current settings with the ones in the document the user
     * picked. The file is parsed in full before anything is written, so a
     * malformed or unrelated file leaves the existing settings untouched.
     *
     * @return true when the settings were replaced.
     */
    static boolean restore(Context context, Uri source) {
        Map<String, Object> values;
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(source);
            if (input == null) {
                return false;
            }
            values = parse(input);
        } catch (Exception e) {
            // A file that is not one of ours, or is not readable at all.
            Diagnostics.log(context, "settings import failed to parse: " + e);
            return false;
        } finally {
            close(input);
        }

        if (values.isEmpty()) {
            Diagnostics.log(context, "settings import failed: no entries");
            return false;
        }

        SharedPreferences.Editor editor = Options.getSharedPreferences(context)
                .edit();
        // A restore is a replacement, not a merge: settings absent from the
        // backup must go back to their defaults rather than linger from
        // whatever was configured before.
        editor.clear();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> members = (Set<String>) value;
                editor.putStringSet(key, members);
            }
        }
        // commit() rather than apply(): the toast has to report what actually
        // reached storage, and this already runs off the main thread.
        if (!editor.commit()) {
            Diagnostics.log(context, "settings import failed to commit");
            return false;
        }
        Options.invalidateCaches();
        Diagnostics.log(context, "settings imported, entries=" + values.size());
        return true;
    }

    // Parse a preferences document into typed values. Throws when the file is
    // not a preferences map at all; an unknown tag inside a valid map is
    // skipped, so a file written by a later version still restores what this
    // version understands.
    private static Map<String, Object> parse(InputStream input)
            throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(input, null);

        Map<String, Object> values = new HashMap<String, Object>();
        boolean sawMap = false;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if (TAG_MAP.equals(tag)) {
                    sawMap = true;
                } else if (!sawMap) {
                    // The root is something else entirely; this is not a
                    // preferences file.
                    throw new XmlPullParserException(
                            "not a preferences file: root <" + tag + ">");
                } else {
                    String key = parser.getAttributeValue(null, ATTR_NAME);
                    Object value = readValue(parser, tag, key);
                    if (key != null && value != null) {
                        values.put(key, value);
                    }
                }
            }
            event = parser.next();
        }
        if (!sawMap) {
            throw new XmlPullParserException("not a preferences file: no map");
        }
        return values;
    }

    // Read one entry. Returns null for a tag this version does not know, or
    // for a value that does not parse as the type its tag declares.
    private static Object readValue(XmlPullParser parser, String tag,
            String key) throws XmlPullParserException, IOException {
        String raw = parser.getAttributeValue(null, ATTR_VALUE);
        try {
            if (TAG_BOOLEAN.equals(tag)) {
                return raw == null ? null : Boolean.valueOf(raw);
            } else if (TAG_INT.equals(tag)) {
                return Integer.valueOf(raw);
            } else if (TAG_LONG.equals(tag)) {
                return Long.valueOf(raw);
            } else if (TAG_FLOAT.equals(tag)) {
                return Float.valueOf(raw);
            } else if (TAG_STRING.equals(tag)) {
                // A <string> with no name is a member of an enclosing <set>,
                // which readSet() consumes itself; reaching one here means a
                // stray tag with nothing to key it by.
                return key == null ? null : parser.nextText();
            } else if (TAG_SET.equals(tag)) {
                return readSet(parser);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    // Collect the <string> members of a <set>, preserving their order so a
    // restored file reads back the way it was written.
    private static Set<String> readSet(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        Set<String> members = new LinkedHashSet<String>();
        int depth = parser.getDepth();
        int event = parser.next();
        while (event != XmlPullParser.END_DOCUMENT
                && !(event == XmlPullParser.END_TAG
                        && parser.getDepth() <= depth)) {
            if (event == XmlPullParser.START_TAG
                    && TAG_STRING.equals(parser.getName())) {
                members.add(parser.nextText());
            }
            event = parser.next();
        }
        return members;
    }

    private static void close(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Nothing useful left to do: the caller already has its
                // result from whether the write or the parse completed.
            }
        }
    }
}
