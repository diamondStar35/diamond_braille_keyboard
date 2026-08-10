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

/**
 * Used to filter Braille tables by their varying types.
 *
 * <p>LITERARY refers to all 6 dot tables (grades 1 and 2). COMPUTER refers to
 * all 8 dot tables. ALL refers to both computer and literary tables.
 */
public enum BrailleType {
    ALL(0), COMPUTER(8), LITERARY(6);

    public final int dots;

    BrailleType(int dots) {
        this.dots = dots;
    }

    /**
     * For backwards compatibility BrailleType is stored as an integer where
     * 0 = COMPUTER and all other values return LITERARY.
     *
     * @param value
     *            The preference value for the BrailleType.
     * @return COMPUTER for value 0, LITERARY for all other values.
     */
    public static BrailleType valueOf(int value) {
        switch (value) {
        case 0:
            return COMPUTER;
        default:
            return LITERARY;
        }
    }

    /**
     * Toggle the BrailleType and return the new type. This makes COMPUTER
     * LITERARY and LITERARY COMPUTER. It does not make sense to toggle ALL
     * so ALL is returned.
     *
     * @return LITERARY if the current state is COMPUTER, COMPUTER if the
     *         current state is LITERARY or ALL if the current state is ALL.
     */
    public BrailleType switchType() {
        switch (this) {
        case LITERARY:
            return COMPUTER;
        case COMPUTER:
            return LITERARY;
        default:
            return this;
        }
    }

    /**
     * Convert a BrailleType to an integer. This is most useful for
     * backwards compatibility with preferences.
     *
     * @return 0 for COMPUTER Braille, 1 for LITERARY and 2 for ALL.
     */
    public int prefValue() {
        switch (this) {
        case COMPUTER:
            return 0;
        case LITERARY:
            return 1;
        default:
            return 2;
        }
    }
}
