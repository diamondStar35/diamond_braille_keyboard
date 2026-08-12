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
 * One text expansion entry: a short abbreviation and the text it expands to
 * when the abbreviation is typed followed by a space. A pure data class with
 * no behavior, kept separate from the storage and UI code.
 */
public class Abbreviation {

    private final String abbreviation;
    private final String expansion;

    public Abbreviation(String abbreviation, String expansion) {
        this.abbreviation = abbreviation;
        this.expansion = expansion;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public String getExpansion() {
        return expansion;
    }
}
