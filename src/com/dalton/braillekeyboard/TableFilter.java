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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.googlecode.eyesfree.braille.translate.TableInfo;

/**
 * Pure helpers for filtering and ranking the Braille tables returned by the
 * translator.
 *
 * <p>Tables are matched against a {@link BrailleType}, deduplicated by their
 * {@link TableNames display name} and ranked so the settings lists and the
 * default table choice are stable. All methods are stateless; the whitelist
 * of supported table ids is passed in by the caller.
 */
final class TableFilter {

    private TableFilter() {
    }

    // Filter a list of tables and return the filtered list.
    // Tables must be declared in the arrays.xml file in the braille_tables
    // array.
    // tables must match the specified BrailleType.
    // Tables that would be displayed with the same name (same locale and
    // grade) are collapsed to a single representative so the settings lists
    // don't show repeated labels.
    static List<TableInfo> filterTables(List<TableInfo> tables,
            BrailleType brailleType, List<String> tableIds) {
        Map<String, TableInfo> representatives =
                new LinkedHashMap<String, TableInfo>();

        for (TableInfo table : tables) {
            if (tableIds.contains(table.getId())
                    && matchesBrailleType(table, brailleType)) {
                String key = tableKey(table);
                TableInfo current = representatives.get(key);
                if (current == null || preferTable(table, current)) {
                    representatives.put(key, table);
                }
            }
        }
        return new ArrayList<TableInfo>(representatives.values());
    }

    // True when the given table is a better default choice than the current
    // best: the one whose locale most closely matches the device locale.
    static boolean betterTable(TableInfo first, TableInfo second) {
        Locale firstLocale = first.getLocale();
        Locale secondLocale = second != null ? second.getLocale() : Locale.ROOT;
        return matchRank(firstLocale, Locale.getDefault()) > matchRank(
                secondLocale, Locale.getDefault());
    }

    // Checks if a given Braille table matches the given BrailleType filter.
    static boolean matchesBrailleType(TableInfo table,
            BrailleType brailleType) {
        if (brailleType == BrailleType.ALL) {
            return true;
        }

        if (brailleType == BrailleType.LITERARY) {
            // Literary tables are six dot and not computer braille. A few six
            // dot computer braille tables exist (for example en-US-comp6) and
            // must not show up in the literary list. Grade 0 tables are
            // legitimate literary tables for some languages (e.g. Dutch,
            // Kurdish, Sami) and must not be excluded.
            return !table.isEightDot() && !isComputerBraille(table);
        } else if (brailleType == BrailleType.COMPUTER) {
            return table.isEightDot();
        }
        return false;
    }

    // True if the table is a computer braille table. Computer braille is
    // normally eight dot, but a few six dot computer braille tables exist as
    // well (for example en-US-comp6).
    private static boolean isComputerBraille(TableInfo table) {
        return table.isEightDot() || table.getId().contains("comp");
    }

    // A key identifying tables that would be displayed with the same name.
    // Tables with the same display name share a key and are collapsed to a
    // single representative by filterTables().
    private static String tableKey(TableInfo table) {
        return TableNames.getDisplayName(table);
    }

    // When two tables share a display name, the literary table is preferred
    // over a computer braille table, and an eight dot table over a six dot
    // computer braille table.
    private static boolean preferTable(TableInfo first, TableInfo second) {
        boolean firstComputer = isComputerBraille(first);
        boolean secondComputer = isComputerBraille(second);
        if (firstComputer != secondComputer) {
            return !firstComputer;
        }
        if (first.isEightDot() != second.isEightDot()) {
            return first.isEightDot();
        }
        return false;
    }

    private static int matchRank(Locale first, Locale second) {
        int ret = first.getLanguage().equals(second.getLanguage()) ? 1 : 0;
        if (ret > 0) {
            ret += (first.getCountry().equals(second.getCountry()) ? 1 : 0);
            if (ret > 1) {
                ret += (first.getVariant().equals(second.getVariant()) ? 1 : 0);
            }
        }
        return ret;
    }
}
