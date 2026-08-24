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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;

import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.braille.translate.TableInfo;
import com.googlecode.eyesfree.braille.translate.TranslatorClient.OnInitListener;

/**
 * Acts as a layer of abstraction between the Android BrailleTranslator service
 * and the Braille IME itself.
 * 
 * You should instantiate this class in the InputMethodService implementation
 * and use it to back translate Braille patterns to text.
 * 
 * You should implement the Listener callback as a method of
 * verifying when the service is ready for input.
 * 
 * This class provides methods to backtranslate Braille to text, switch Braille
 * tables and grades and list installed Braille tables.
 * 
 * You should always call the destroy() method when you are finished with the
 * instance to release system resources.
 */
public class Parser {

    // The BrailleTranslator can currently accept back translation requests.
    public static final int STATUS_OK = 1;
    // The back translator is in the preparing state and can not yet receive
    // requests.
    public static final int STATUS_PREPARING = 0;
    // The BackTranslator is in the error state and can't accept requests.
    public static final int STATUS_ERROR = -1;
    // This instance has been shutdown and can not be used.
    public static final int STATUS_TABLE_ERROR = 2;

    /**
     * A callback which is invoked with a status flag when the BrailleTranslator
     * responds on initial setup.
     */
    public interface Listener {

        /**
         * Invoked when the translator has responded and transfered from the
         * preparing state to some other state.
         * 
         * @param status
         *            The status of the BrailleTranslator. This can be STATUS_OK
         *            or STATUS_ERROR currently.
         */
        void onTranslatorReady(int status);
    }

    private final MyTranslatorClient client;
    private final SharedPreferences sharedPref;
    private final Listener listener;
    private final List<String> tableIds;

    private BrailleTranslator translator;
    private List<TableInfo> tables;
    private int status = STATUS_PREPARING;

    /**
     * Construct a Parser instance.
     * 
     * @param context
     *            The application context.
     * @param listener
     *            An implementation of Listener which will be
     *            invoked when the BrailleTranslator has left the preparing
     *            state.
     */
    public Parser(final Context context, Listener listener) {
        this.listener = listener;
        sharedPref = Options.getSharedPreferences(context);
        String[] ids = context.getResources().getStringArray(
                R.array.braille_tables);
        tableIds = Arrays.asList(ids);

        client = new MyTranslatorClient(context, new OnInitListener() {

            @Override
            public void onInit(int status) {
                ready(context, status);
            }
        });
    }

    /**
     * Release system resources held by this instance.
     */
    public void destroy() {
        if (client != null) {
            client.destroy();
        }
        status = STATUS_ERROR;
    }

    /**
     * Get the currently active BrailleType according to the BrailleType
     * preference and return it.
     * 
     * @param context
     *            The application context.
     * @return The active BrailleType.
     */
    public BrailleType getBrailleType(Context context) {
        String stored = sharedPref.getString(
                context.getString(R.string.pref_braille_type_key),
                context.getString(R.string.pref_braille_type_default));
        BrailleType brailleType;
        try {
            brailleType = BrailleType.valueOf(Integer.parseInt(stored));
        } catch (NumberFormatException e) {
            // A corrupted or foreign value must never take the keyboard
            // down; fall back to the default type.
            brailleType = BrailleType.valueOf(Integer.parseInt(context
                    .getString(R.string.pref_braille_type_default)));
        }
        return brailleType;
    }

    // Memoisation of getTable(Context). Resolving the active table sorts the
    // whole catalogue and rebuilds the configured-id set, which used to run
    // on every typed cell. The cache is self-invalidating: the three cheap
    // in-memory inputs are re-read each call and compared, so any change -
    // from the settings screen or from the table-switching gestures below -
    // simply misses and recomputes once.
    private BrailleType cachedTableBrailleType;
    private Set<String> cachedTableConfigured;
    private String cachedTableActiveId;
    private TableInfo cachedTable;

    /**
     * Get the active Braille table.
     *
     * @param context
     *            The application context.
     * @return The active Braille table as determined by the application
     *         preferences.
     */
    public TableInfo getTable(Context context) {
        BrailleType brailleType = getBrailleType(context);
        Set<String> configured = getConfiguredTableIds(context, brailleType);

        // The last table the user was on wins if it still matches the active
        // Braille type and is still one of the configured tables.
        String activeId = sharedPref.getString(
                context.getString(R.string.pref_braille_active_table_key),
                context.getString(R.string.pref_braille_table_auto));

        if (cachedTable != null
                && cachedTableBrailleType == brailleType
                && java.util.Objects.equals(cachedTableActiveId, activeId)
                && java.util.Objects.equals(cachedTableConfigured,
                        configured)) {
            return cachedTable;
        }

        TableInfo result = resolveTable(context, brailleType, configured,
                activeId);
        cachedTableBrailleType = brailleType;
        cachedTableConfigured = configured;
        cachedTableActiveId = activeId;
        cachedTable = result;
        return result;
    }

    /** The uncached resolution logic behind {@link #getTable(Context)}. */
    private TableInfo resolveTable(Context context, BrailleType brailleType,
            Set<String> configured, String activeId) {
        if (!context.getString(R.string.pref_braille_table_auto)
                .equals(activeId)) {
            TableInfo active = findTableById(activeId);
            if (active != null
                    && TableFilter.matchesBrailleType(active, brailleType)
                    && configured.contains(activeId)) {
                return active;
            }
        }

        // Otherwise use the first configured table (in the settings display
        // order), and finally fall back to the best default for the locale.
        for (String id : orderedConfiguredIds(context, brailleType)) {
            TableInfo table = findTableById(id);
            if (table != null
                    && TableFilter.matchesBrailleType(table, brailleType)) {
                return table;
            }
        }
        return findDefaultTableInfo(brailleType);
    }

    /**
     * Return the set of table ids the user has checked for the given Braille
     * type in the settings.  These are the tables that can be switched between
     * while typing.  A legacy value stored as a single string (the old single
     * table choice) is migrated automatically, and if nothing is configured
     * yet the best default table for the type is seeded.
     * 
     * @param context
     *            The application context.
     * @param brailleType
     *            The type of tables to return.
     * @return The set of configured table ids, never empty.
     */
    public Set<String> getConfiguredTableIds(Context context,
            BrailleType brailleType) {
        int prefKey = brailleType == BrailleType.COMPUTER
                ? R.string.pref_braille_computer_table_key
                : R.string.pref_braille_literary_table_key;
        Set<String> configured = Options.getStringSetOrString(context, prefKey,
                null);
        Set<String> ids = new HashSet<String>();
        if (configured != null) {
            ids.addAll(configured);
        }
        // The legacy "auto" value meant "use the default table for this type".
        ids.remove(context.getString(R.string.pref_braille_table_auto));
        if (ids.isEmpty()) {
            // Nothing configured yet: seed with the best table for this type
            // so table switching has a sensible starting point and the
            // settings screen can show it checked.
            ids.add(getDefaultId(context, brailleType));
            Options.writeStringSetPreference(context, prefKey, ids);
        }
        return ids;
    }

    // The configured table ids in the settings display order (locale order).
    private List<String> orderedConfiguredIds(Context context,
            BrailleType brailleType) {
        List<String> ordered = new ArrayList<String>();
        Set<String> configured = getConfiguredTableIds(context, brailleType);
        List<TableInfo> tables = getTables(brailleType);
        if (tables != null) {
            for (TableInfo table : tables) {
                if (configured.contains(table.getId())) {
                    ordered.add(table.getId());
                }
            }
        }
        return ordered;
    }

    /**
     * Return a list of supported Braille tables for the given BrailleType by
     * the BrailleTranslator.
     * 
     * @param context
     *            The application context.
     * @param brailleType
     *            The type of tables to be returned.
     * @return The list of tables.
     */
    public List<TableInfo> getTables(BrailleType brailleType) {
        // Compare tables by their display name in alphabetical order.
        Comparator<TableInfo> comparator = new Comparator<TableInfo>() {
            @Override
            public int compare(TableInfo o1, TableInfo o2) {
                return TableNames.getDisplayName(o1).compareTo(
                        TableNames.getDisplayName(o2));
            }
        };

        if (tables != null) {
            List<TableInfo> filteredTables = TableFilter.filterTables(
                    tables, brailleType, tableIds);
            Collections.sort(filteredTables, comparator);
            return filteredTables;
        }
        return null;
    }

    /**
     * Toggle the active Braille type. If the current type is literary switches
     * to computer and if the current type is computer switches to literary.
     * 
     * @param context
     *            The application context.
     * @return The new BrailleType.
     */
    public BrailleType switchBrailleType(Context context) {
        BrailleType brailleType = getBrailleType(context).switchType();
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(context.getString(R.string.pref_braille_type_key),
                String.valueOf(brailleType.prefValue()));
        // apply() updates the in-memory value synchronously so the subsequent
        // setTranslator() call sees the new setting immediately.
        editor.apply();
        setTranslator(context);
        return brailleType;
    }

    /**
     * Switch to the next Braille table matching the active BrailleType from
     * the tables the user has checked in the settings. If there are no more
     * in the list switch to the first one.
     * 
     * @param context
     *            The application context.
     * @return A String describing the new table which can be shown to the user.
     */
    public String switchTable(Context context) {
        BrailleType brailleType = getBrailleType(context);
        // get tables matching only the active brailleType.
        List<TableInfo> tables = getTables(brailleType);
        // Retrieve the list of tables that can be switched between while
        // typing, i.e. the ones checked in the settings.
        Set<String> configured = getConfiguredTableIds(context, brailleType);
        // Get the active Braille table.
        TableInfo defaultTable = getTable(context);

        if (defaultTable == null || tables == null) {
            return null;
        }

        // Move tablePosition to point at the active table in the table list.
        int tablePosition;
        for (tablePosition = 0; tablePosition < tables.size(); tablePosition++) {
            if (tables.get(tablePosition).getId().equals(defaultTable.getId())) {
                break;
            }
        }

        // Move in a circular motion through the tables list until we find the
        // next table in the list that is checked in the settings. This will
        // traverse to the end of the list and then return to the start and work
        // it's way back to the active table if there are no checked tables.
        int i = 0;
        while (i++ < tables.size()) {
            tablePosition = tablePosition < tables.size() - 1 ? tablePosition + 1
                    : 0;
            TableInfo table = tables.get(tablePosition);
            if (configured.contains(table.getId())) {
                setActiveTable(context, table);

                // Found a table, return a formatted String describing it.
                return TableNames.describeTable(context, table);
            }
        }
        return null;
    }

    /**
     * Apply the configured default table preference.  If the user chose a
     * specific table it is activated (and the Braille type switched to match)
     * so the keyboard always opens on it.  If the user chose "Use the last
     * language" the current state is left untouched.
     * 
     * @param context
     *            The application context.
     */
    public void applyStartupTable(Context context) {
        String defaultTable = sharedPref.getString(
                context.getString(R.string.pref_default_braille_table_key),
                context.getString(R.string.pref_default_braille_table_last));
        if (context.getString(R.string.pref_default_braille_table_last)
                .equals(defaultTable)) {
            // Open on the table the user was last using.
            return;
        }
        TableInfo table = findTableById(defaultTable);
        if (table == null) {
            return;
        }
        BrailleType brailleType = table.isEightDot() ? BrailleType.COMPUTER
                : BrailleType.LITERARY;
        // Keep the default table inside the configured set so getTable()
        // honours it even if the user later unchecks it in the settings.
        Set<String> configured = getConfiguredTableIds(context, brailleType);
        if (!configured.contains(table.getId())) {
            configured.add(table.getId());
            int prefKey = brailleType == BrailleType.COMPUTER
                    ? R.string.pref_braille_computer_table_key
                    : R.string.pref_braille_literary_table_key;
            Options.writeStringSetPreference(context, prefKey, configured);
        }
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(context.getString(R.string.pref_braille_type_key),
                String.valueOf(brailleType.prefValue()));
        editor.putString(
                context.getString(R.string.pref_braille_active_table_key),
                table.getId());
        editor.apply();
        setTranslator(context);
    }

    /**
     * Back translate an array of Braille dots to text.
     * 
     * @param context
     *            The application context.
     * @param cellBytes
     *            An array of bytes representing Braille dot patterns. Each byte
     *            represents a single Braille cell. Each cell is represented by
     *            a byte which is a bit string which indicates whether dots are
     *            on or off. The MSB of an 8 bit bitstring represents dot 8
     *            while the lsb represents dot 1. 0b11111111 means all 8 dots
     *            are pressed while 0 means no dots are active.
     * @return The back translation String or null if no backtranslation was
     *         possible.
     */
    public String backTranslate(Context context, Byte[] cellBytes) {
        // Convert from a Byte[] to a byte[]O
        byte[] cells = new byte[cellBytes.length + 2];
        // Pad the cells so that we have spaces on each size. This makes the
        // back translation work properly.
        cells[0] = 0;
        cells[cells.length - 1] = 0;

        for (int i = 0; i < cellBytes.length; i++) {
            cells[i + 1] = 0;
            if (cellBytes[i] != null) { // should never be null
                cells[i + 1] = cellBytes[i].byteValue();
            }
        }

        String text = null;
        if (status == STATUS_OK) {
            text = translator.backTranslate(cells);
            text = handleUnknownPatterns(context, text, cells);
        }
        return text != null ? text.trim() : text;
    }

    // Called when the BrailleTranslator becomes ready.
    private void ready(Context context, int translatorClientStatus) {
        if (client != null
                && translatorClientStatus == MyTranslatorClient.SUCCESS) {
            status = STATUS_OK;
            tables = client.getTables();
            setTranslator(context);
            applyStartupTable(context);
        } else {
            status = STATUS_ERROR;
        }
        listener.onTranslatorReady(status);
    }

    // Sets the translator to the active table.
    public boolean setTranslator(Context context) {
        TableInfo table = getTable(context);
        if (table != null
                && (status == STATUS_OK || status == STATUS_TABLE_ERROR)) {
            translator = client.getTranslator(table.getId());
            status = translator == null ? STATUS_TABLE_ERROR : STATUS_OK;
            return true;
        }
        return false;
    }

    // Find a whitelisted table by id across all supported tables.
    private TableInfo findTableById(String id) {
        if (tables == null) {
            return null;
        }
        for (TableInfo table : tables) {
            if (tableIds.contains(table.getId()) && table.getId().equals(id)) {
                return table;
            }
        }
        return null;
    }

    /**
     * Look up a supported Braille table by its id, for example when building
     * the "Default braille table" list in the settings.
     * 
     * @param id
     *            The id of the table to look up.
     * @return The TableInfo or null if it is not a supported table.
     */
    public TableInfo getTableInfoById(String id) {
        return findTableById(id);
    }

    // Set the app preferences to have a new active table. Set the translator to
    // use this table.
    private void setActiveTable(Context context, TableInfo table) {
        if (table != null) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(
                    context.getString(R.string.pref_braille_active_table_key),
                    table.getId());
            editor.apply();
            setTranslator(context);
        }
    }

    // Returns an English readable pattern of the given byte value that
    // represents the pressed dots. For example 0b101 would return 13. See
    // BrailleTranslator details for how dots are encoded as bytes.
    private static String computeCellValue(byte value) {
        StringBuilder sb = new StringBuilder();
        int mask = 1;

        // We start from the right of the bit string at dot 1 and work our way
        // towards the left of the bit string adding dots to the output string
        // that are set to 1.
        for (int i = 1; i <= 8; i++) {
            if ((mask & value) != 0) {
                // dot is active here write it to the output.
                sb.append(String.valueOf(i));
            }
            mask <<= 1;
        }

        // Return the output string or nothing if no dots are set.
        return sb.length() > 0 ? sb.toString() : "";
    }

    // The BrailleTranslator can populate the output string with garbage for
    // unknown Braille patterns. Remove these from the string.
    // These are of the form \dotpattern/ eg. \12/ if dots 12 is unknown.
    private String handleUnknownPatterns(Context context, String text,
            byte[] cells) {
        for (byte cell : cells) {
            String value = "\\" + computeCellValue(cell) + "/";
            if (text.contains(value)) {
                text = text.replace(value, "");
            }
        }
        return text;
    }

    private TableInfo findDefaultTableInfo(BrailleType brailleType) {
        List<TableInfo> filteredTables = getTables(brailleType);
        if (filteredTables == null) {
            return null;
        }

        TableInfo best = null;
        for (TableInfo info : filteredTables) {
            if (TableFilter.betterTable(info, best)) {
                best = info;
            }
        }
        return best;
    }

    public String getDefaultId(Context context, BrailleType brailleType) {
        TableInfo table = findDefaultTableInfo(brailleType);
        if (table != null) {
            return table.getId();
        }

        if (brailleType == BrailleType.COMPUTER) {
            return context
                    .getString(R.string.pref_braille_computer_table_default);
        } else {
            return context
                    .getString(R.string.pref_braille_literary_table_default);
        }
    }
}
