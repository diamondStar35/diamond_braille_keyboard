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

import java.util.HashMap;
import java.util.Map;

import android.content.Context;

import com.googlecode.eyesfree.braille.translate.TableInfo;

/**
 * Human readable display names for the supported Braille tables.
 *
 * <p>Holds the static id -&gt; display name mapping and the logic to build a
 * display name for a {@link TableInfo}, used by {@link Parser} for the
 * table lists and by the settings screen.
 */
public class TableNames {

    // Static mapping from every Braille table ID to a clean, human-readable
    // display name.  Tables that represent the same logical entry share a
    // name so that TableFilter.filterTables() collapses them into one
    // representative.
    private static final Map<String, String> TABLE_NAMES =
            new HashMap<String, String>();
    static {
        TABLE_NAMES.put("af-g2", "Afrikaans Grade 2");
        TABLE_NAMES.put("akk-g1", "Akkadian Grade 1 (United States)");
        TABLE_NAMES.put("akk-g1-akkborger", "Akkadian Grade 1 (Borger)");
        TABLE_NAMES.put("akk-g1-3", "Akkadian Grade 1 (Borger)");
        TABLE_NAMES.put("akk-g1-4", "Akkadian Grade 1 (United States)");
        TABLE_NAMES.put("ar-g1", "Arabic Grade 1");
        TABLE_NAMES.put("ar-g2", "Arabic Grade 2");
        TABLE_NAMES.put("ar-comp8", "Arabic Computer Braille");
        TABLE_NAMES.put("as-g1", "Assamese Grade 1");
        TABLE_NAMES.put("as-IN-g1", "Assamese India Grade 1");
        TABLE_NAMES.put("aw-IN-g1", "Awadhi India Grade 1");
        TABLE_NAMES.put("awa-g1", "Awadhi Grade 1");
        TABLE_NAMES.put("ba-g1", "Bashkir Grade 1");
        TABLE_NAMES.put("be-g1", "Belarusian Grade 1 (Detailed)");
        TABLE_NAMES.put("be-g1-2", "Belarusian Grade 1");
        TABLE_NAMES.put("be-comp8", "Belarusian Computer Braille");
        TABLE_NAMES.put("be-IN-g1", "Belarusian India Grade 1");
        TABLE_NAMES.put("bg-g1", "Bulgarian Grade 1");
        TABLE_NAMES.put("bg-g1-2", "Bulgarian Grade 1");
        TABLE_NAMES.put("bg-comp8", "Bulgarian Computer Braille");
        TABLE_NAMES.put("bh-g1", "Bihari Grade 1");
        TABLE_NAMES.put("bh-g1-2", "Bihari Grade 1");
        TABLE_NAMES.put("bn-g1", "Bengali Grade 1");
        TABLE_NAMES.put("bo-g1", "Tibetan Grade 1");
        TABLE_NAMES.put("bo-comp8", "Tibetan Computer Braille");
        TABLE_NAMES.put("br-IN-g1", "Braj India Grade 1");
        TABLE_NAMES.put("bra-g1", "Braj Grade 1");
        TABLE_NAMES.put("ca-g1", "Catalan Grade 1");
        TABLE_NAMES.put("ca-g1-2", "Catalan Grade 1");
        TABLE_NAMES.put("chr-g1", "Cherokee Grade 1");
        TABLE_NAMES.put("ckb-g1", "Sorani Kurdish Grade 1");
        TABLE_NAMES.put("ckb-g1-2", "Sorani Kurdish Grade 1");
        TABLE_NAMES.put("cmn-CN-g1", "Chinese China Grade 1 (Traditional)");
        TABLE_NAMES.put("cmn-CN-g1-cmntwocell", "Chinese China Grade 1 (Two-cell)");
        TABLE_NAMES.put("cmn-TW-g1", "Chinese Taiwan Grade 1 (Bopomofo)");
        TABLE_NAMES.put("cop-g1", "Coptic Grade 1");
        TABLE_NAMES.put("cop-comp8", "Coptic Computer Braille");
        TABLE_NAMES.put("cs-g1", "Czech Grade 1");
        TABLE_NAMES.put("cs-comp8", "Czech Computer Braille");
        TABLE_NAMES.put("cy-g1", "Welsh Grade 1");
        TABLE_NAMES.put("cy-g2", "Welsh Grade 2");
        TABLE_NAMES.put("cy-g2-2", "Welsh Grade 2");
        TABLE_NAMES.put("da-g1", "Danish Grade 1 (1993)");
        TABLE_NAMES.put("da-g1-ddp", "Danish Grade 1 (2022)");
        TABLE_NAMES.put("da-g1-3", "Danish Grade 1 (1993)");
        TABLE_NAMES.put("da-g1-4", "Danish Grade 1 (1993)");
        TABLE_NAMES.put("da-g1-5", "Danish Grade 1 (1993)");
        TABLE_NAMES.put("da-g2", "Danish Grade 2 (1993)");
        TABLE_NAMES.put("da-g2-ddp", "Danish Grade 2 (2022)");
        TABLE_NAMES.put("da-g2-3", "Danish Grade 2 (1993)");
        TABLE_NAMES.put("da-comp8", "Danish Computer Braille (2022)");
        TABLE_NAMES.put("da-comp8-ddp", "Danish Computer Braille (1993)");
        TABLE_NAMES.put("da-comp8-3", "Danish Computer Braille (2022)");
        TABLE_NAMES.put("da-comp8-4", "Danish Computer Braille (1993)");
        TABLE_NAMES.put("da-comp8-5", "Danish Computer Braille (2022)");
        TABLE_NAMES.put("da-comp8-6", "Danish Computer Braille (1993)");
        TABLE_NAMES.put("da-comp8-7", "Danish Computer Braille (1993)");
        TABLE_NAMES.put("de-comp6", "German Computer Braille");
        TABLE_NAMES.put("de-g0", "German Grade 0");
        TABLE_NAMES.put("de-g0-2", "German Grade 0 (Detailed)");
        TABLE_NAMES.put("de-g1", "German Grade 1");
        TABLE_NAMES.put("de-g1-2", "German Grade 1 (Detailed)");
        TABLE_NAMES.put("de-g2", "German Grade 2");
        TABLE_NAMES.put("de-g2-2", "German Grade 2 (Detailed)");
        TABLE_NAMES.put("de-comp8", "German Computer Braille");
        TABLE_NAMES.put("dra-g1", "Dravidian Grade 1");
        TABLE_NAMES.put("dra-comp8", "Dravidian Computer Braille");
        TABLE_NAMES.put("el-g1", "Greek Grade 1");
        TABLE_NAMES.put("en-g1", "Unified English Grade 1");
        TABLE_NAMES.put("en-g2", "Unified English Grade 2");
        TABLE_NAMES.put("en-g3", "English Grade 3");
        TABLE_NAMES.put("en-CA-comp8", "English Canada Computer Braille (Canadian)");
        TABLE_NAMES.put("en-GB-g1", "English UK Grade 1");
        TABLE_NAMES.put("en-GB-g2", "English UK Grade 2");
        TABLE_NAMES.put("en-GB-g2-bauk", "English UK Grade 2");
        TABLE_NAMES.put("en-GB-comp8", "English UK Computer Braille");
        TABLE_NAMES.put("en-IN-g1", "English India Grade 1");
        TABLE_NAMES.put("en-US-comp6", "English US Computer Braille");
        TABLE_NAMES.put("en-US-g1", "English US Grade 1");
        TABLE_NAMES.put("en-US-g2", "English US Grade 2");
        TABLE_NAMES.put("en-US-g2-ebae", "English US Grade 2");
        TABLE_NAMES.put("en-US-comp8", "English US Computer Braille");
        TABLE_NAMES.put("en-US-comp8-2", "English US Computer Braille");
        TABLE_NAMES.put("eo-g1", "Esperanto Grade 1");
        TABLE_NAMES.put("eo-g1-2", "Esperanto Grade 1");
        TABLE_NAMES.put("es-g1", "Spanish Grade 1");
        TABLE_NAMES.put("es-g1-2", "Spanish Grade 1");
        TABLE_NAMES.put("es-g2", "Spanish Grade 2");
        TABLE_NAMES.put("es-comp8", "Spanish Computer Braille");
        TABLE_NAMES.put("es-NO-g1", "Spanish Grade 1 (Norwegian)");
        TABLE_NAMES.put("et-g1", "Estonian Grade 1");
        TABLE_NAMES.put("et-comp8", "Estonian Computer Braille");
        TABLE_NAMES.put("fa-g1", "Persian Grade 1");
        TABLE_NAMES.put("fa-comp8", "Persian Computer Braille");
        TABLE_NAMES.put("fi-g1", "Finnish Grade 1");
        TABLE_NAMES.put("fi-comp8", "Finnish Computer Braille");
        TABLE_NAMES.put("fil-g2", "Filipino Grade 2");
        TABLE_NAMES.put("fr-g1", "French Grade 1");
        TABLE_NAMES.put("fr-g2", "French Grade 2");
        TABLE_NAMES.put("fr-comp8", "French Computer Braille");
        TABLE_NAMES.put("ga-g1", "Irish Grade 1");
        TABLE_NAMES.put("ga-g2", "Irish Grade 2");
        TABLE_NAMES.put("gd-g1", "Gaelic Grade 1");
        TABLE_NAMES.put("gd-comp8", "Gaelic Computer Braille");
        TABLE_NAMES.put("gez-g1", "Geez Grade 1");
        TABLE_NAMES.put("gon-g1", "Gondi Grade 1");
        TABLE_NAMES.put("gon-g1-2", "Gondi Grade 1");
        TABLE_NAMES.put("grc-EN-g1", "Ancient Greek Grade 1 (Composed)");
        TABLE_NAMES.put("grc-EN-g1-grcinternationalen", "Ancient Greek Grade 1 (Decomposed)");
        TABLE_NAMES.put("grc-ES-g1", "Ancient Greek Grade 1 (International Spanish)");
        TABLE_NAMES.put("gu-g1", "Gujarati Grade 1");
        TABLE_NAMES.put("gu-IN-g1", "Gujarati India Grade 1");
        TABLE_NAMES.put("haw-g1", "Hawaiian Grade 1");
        TABLE_NAMES.put("hbo-g1", "Ancient Hebrew Grade 1 (International Hebrew Braille Code)");
        TABLE_NAMES.put("hbo-g1-ihbcmcallister", "Ancient Hebrew Grade 1 (IHBC McAllister)");
        TABLE_NAMES.put("hbo-g1-katz", "Ancient Hebrew Grade 1 (Katz)");
        TABLE_NAMES.put("he-comp8", "Hebrew Computer Braille (Modern)");
        TABLE_NAMES.put("he-IL-g1", "Hebrew Israel Grade 1 (Modern)");
        TABLE_NAMES.put("hi-g1", "Hindi Grade 1");
        TABLE_NAMES.put("hi-IN-g1", "Hindi India Grade 1");
        TABLE_NAMES.put("hr-g1", "Croatian Grade 1");
        TABLE_NAMES.put("hr-g1-2", "Croatian Grade 1");
        TABLE_NAMES.put("hr-comp8", "Croatian Computer Braille");
        TABLE_NAMES.put("hr-comp8-2", "Croatian Computer Braille");
        TABLE_NAMES.put("hu-g1", "Hungarian Grade 1");
        TABLE_NAMES.put("hu-g2", "Hungarian Grade 2");
        TABLE_NAMES.put("hu-comp8", "Hungarian Computer Braille");
        TABLE_NAMES.put("hy-g1", "Armenian Grade 1");
        TABLE_NAMES.put("hy-comp8", "Armenian Computer Braille");
        TABLE_NAMES.put("is-g1", "Icelandic Grade 1");
        TABLE_NAMES.put("is-g1-2", "Icelandic Grade 1");
        TABLE_NAMES.put("it-g1", "Italian Grade 1 (Monza)");
        TABLE_NAMES.put("it-comp8", "Italian Computer Braille");
        TABLE_NAMES.put("iu-g1", "Inuktitut Grade 1");
        TABLE_NAMES.put("ja-g1", "Japanese Grade 1 (Rokuten Kanji)");
        TABLE_NAMES.put("ja-comp8", "Japanese Computer Braille (Kantenji)");
        TABLE_NAMES.put("ja-comp8-2", "Japanese Computer Braille (Kantenji)");
        TABLE_NAMES.put("ka-g1", "Georgian Grade 1");
        TABLE_NAMES.put("ka-IN-g1", "Georgian India Grade 1");
        TABLE_NAMES.put("kha-g1", "Khasi Grade 1");
        TABLE_NAMES.put("kk-g1", "Kazakh Grade 1");
        TABLE_NAMES.put("km-g1", "Khmer Grade 1 (Cambodian)");
        TABLE_NAMES.put("kmr-g0", "Kurmanji Kurdish Grade 0");
        TABLE_NAMES.put("kn-g1", "Kannada Grade 1");
        TABLE_NAMES.put("ko-g1", "Korean Grade 1");
        TABLE_NAMES.put("ko-g1-2", "Korean Grade 1 (2006)");
        TABLE_NAMES.put("ko-g2", "Korean Grade 2");
        TABLE_NAMES.put("ko-g2-2", "Korean Grade 2 (2006)");
        TABLE_NAMES.put("kok-g1", "Konkani Grade 1");
        TABLE_NAMES.put("kok-g1-2", "Konkani Grade 1");
        TABLE_NAMES.put("kru-g1", "Kurukh Grade 1");
        TABLE_NAMES.put("kru-g1-2", "Kurukh Grade 1");
        TABLE_NAMES.put("ks-IN-g1", "Kashmiri India Grade 1");
        TABLE_NAMES.put("lg-g1", "Luganda Grade 1");
        TABLE_NAMES.put("lo-g1", "Lao Grade 1 (Laotian)");
        TABLE_NAMES.put("lt-g1", "Lithuanian Grade 1");
        TABLE_NAMES.put("lt-g1-2", "Lithuanian Grade 1");
        TABLE_NAMES.put("lt-comp8", "Lithuanian Computer Braille");
        TABLE_NAMES.put("lv-g1", "Latvian Grade 1");
        TABLE_NAMES.put("lv-g1-2", "Latvian Grade 1");
        TABLE_NAMES.put("mi-g1", "Maori Grade 1");
        TABLE_NAMES.put("mk-g1", "Macedonian Grade 1");
        TABLE_NAMES.put("ml-g1", "Malayalam Grade 1");
        TABLE_NAMES.put("ml-IN-g1", "Malayalam India Grade 1");
        TABLE_NAMES.put("mn-comp8", "Mongolian Computer Braille");
        TABLE_NAMES.put("mn-comp8-2", "Mongolian Computer Braille");
        TABLE_NAMES.put("mn-IN-g1", "Mongolian India Grade 1");
        TABLE_NAMES.put("mni-g1", "Manipuri Grade 1");
        TABLE_NAMES.put("mr-g1", "Marathi Grade 1");
        TABLE_NAMES.put("mr-IN-g1", "Marathi India Grade 1");
        TABLE_NAMES.put("ms-g2", "Malay Grade 2");
        TABLE_NAMES.put("mt-g1", "Maltese Grade 1");
        TABLE_NAMES.put("mt-comp8", "Maltese Computer Braille");
        TABLE_NAMES.put("mun-g1", "Munda Grade 1");
        TABLE_NAMES.put("mun-g1-2", "Munda Grade 1");
        TABLE_NAMES.put("mwr-g1", "Marwari Grade 1");
        TABLE_NAMES.put("mwr-g1-2", "Marwari Grade 1");
        TABLE_NAMES.put("my-g1", "Burmese Grade 1");
        TABLE_NAMES.put("my-g2", "Burmese Grade 2");
        TABLE_NAMES.put("ne-g1", "Nepali Grade 1");
        TABLE_NAMES.put("ne-g1-2", "Nepali Grade 1");
        TABLE_NAMES.put("nl-g0", "Dutch Grade 0");
        TABLE_NAMES.put("nl-comp8", "Dutch Computer Braille");
        TABLE_NAMES.put("no-g0", "Norwegian Grade 0");
        TABLE_NAMES.put("no-g1", "Norwegian Grade 1");
        TABLE_NAMES.put("no-g1-2", "Norwegian Grade 1");
        TABLE_NAMES.put("no-g2", "Norwegian Grade 2");
        TABLE_NAMES.put("no-g3", "Norwegian Grade 3");
        TABLE_NAMES.put("no-comp8", "Norwegian Computer Braille (6-dot fallback)");
        TABLE_NAMES.put("no-comp8-2", "Norwegian Computer Braille");
        TABLE_NAMES.put("no-comp8-3", "Norwegian Computer Braille");
        TABLE_NAMES.put("np-IN-g1", "Nepali India Grade 1");
        TABLE_NAMES.put("ny-g1", "Chichewa Grade 1");
        TABLE_NAMES.put("or-g1", "Oriya Grade 1");
        TABLE_NAMES.put("or-IN-g1", "Oriya India Grade 1");
        TABLE_NAMES.put("ovd-g0", "Elfdalian Grade 0");
        TABLE_NAMES.put("ovd-comp8", "Elfdalian Computer Braille");
        TABLE_NAMES.put("pa-g1", "Punjabi Grade 1");
        TABLE_NAMES.put("pi-g1", "Pali Grade 1");
        TABLE_NAMES.put("pi-g1-2", "Pali Grade 1");
        TABLE_NAMES.put("pl-g1", "Polish Grade 1");
        TABLE_NAMES.put("pl-g1-2", "Polish Grade 1");
        TABLE_NAMES.put("pl-comp8", "Polish Computer Braille");
        TABLE_NAMES.put("pt-comp6", "Portuguese Computer Braille");
        TABLE_NAMES.put("pt-g1", "Portuguese Grade 1");
        TABLE_NAMES.put("pt-g2", "Portuguese Grade 2");
        TABLE_NAMES.put("pt-g2-2", "Portuguese Grade 2");
        TABLE_NAMES.put("pt-comp8", "Portuguese Computer Braille");
        TABLE_NAMES.put("pu-IN-g1", "Punjabi India Grade 1");
        TABLE_NAMES.put("ro-g0", "Romanian Grade 0");
        TABLE_NAMES.put("ro-g1", "Romanian Grade 1");
        TABLE_NAMES.put("ro-comp8", "Romanian Computer Braille");
        TABLE_NAMES.put("ru-comp6", "Russian Computer Braille");
        TABLE_NAMES.put("ru-g1", "Russian Grade 1");
        TABLE_NAMES.put("ru-comp8", "Russian Computer Braille");
        TABLE_NAMES.put("rw-g1", "Kinyarwanda Grade 1");
        TABLE_NAMES.put("sa-g1", "Sanskrit Grade 1");
        TABLE_NAMES.put("sa-IN-g1", "Sanskrit India Grade 1");
        TABLE_NAMES.put("sah-g1", "Sakha Grade 1");
        TABLE_NAMES.put("sd-g1", "Sindhi Grade 1");
        TABLE_NAMES.put("se-g1", "Northern Sami Grade 1");
        TABLE_NAMES.put("si-IN-g1", "Sinhala India Grade 1");
        TABLE_NAMES.put("sin-g1", "Sinhala Grade 1");
        TABLE_NAMES.put("sk-g1", "Slovak Grade 1");
        TABLE_NAMES.put("sk-g1-2", "Slovak Grade 1");
        TABLE_NAMES.put("sk-g1-3", "Slovak Grade 1");
        TABLE_NAMES.put("sl-comp8", "Slovene Computer Braille");
        TABLE_NAMES.put("sl-SI-g1", "Slovene Slovenia Grade 1");
        TABLE_NAMES.put("smi-g0", "Sami Grade 0");
        TABLE_NAMES.put("smi-comp8", "Sami Computer Braille");
        TABLE_NAMES.put("sr-g1", "Serbian Grade 1");
        TABLE_NAMES.put("st-g1", "Sotho Grade 1");
        TABLE_NAMES.put("st-g2", "Sotho Grade 2");
        TABLE_NAMES.put("sv-g0", "Swedish Grade 0 (Normal)");
        TABLE_NAMES.put("sv-g0-2", "Swedish Grade 0 (Detailed)");
        TABLE_NAMES.put("sv-g0-3", "Swedish Grade 0 (Phonetics)");
        TABLE_NAMES.put("sv-g1", "Swedish Grade 1 (Normal)");
        TABLE_NAMES.put("sv-g1-2", "Swedish Grade 1 (Detailed)");
        TABLE_NAMES.put("sv-g2", "Swedish Grade 2 (Normal)");
        TABLE_NAMES.put("sv-g2-2", "Swedish Grade 2 (Detailed)");
        TABLE_NAMES.put("sv-comp8", "Swedish Computer Braille (1989)");
        TABLE_NAMES.put("sv-comp8-2", "Swedish Computer Braille (1996)");
        TABLE_NAMES.put("sv-comp8-3", "Swedish Computer Braille (Detailed)");
        TABLE_NAMES.put("sv-comp8-4", "Swedish Computer Braille (Normal)");
        TABLE_NAMES.put("sv-comp8-5", "Swedish Computer Braille (Detailed)");
        TABLE_NAMES.put("sv-comp8-6", "Swedish Computer Braille (Normal)");
        TABLE_NAMES.put("sv-comp8-7", "Swedish Computer Braille (Detailed)");
        TABLE_NAMES.put("sv-comp8-8", "Swedish Computer Braille (Normal)");
        TABLE_NAMES.put("sw-KE-g1", "Swahili Kenya Grade 1");
        TABLE_NAMES.put("sw-KE-g1-2", "Swahili Kenya Grade 1 (.)");
        TABLE_NAMES.put("sw-KE-g1-3", "Swahili Kenya Grade 1 (.)");
        TABLE_NAMES.put("sw-KE-g1-4", "Swahili Kenya Grade 1 (.4)");
        TABLE_NAMES.put("sw-KE-g1-5", "Swahili Kenya Grade 1 (.5)");
        TABLE_NAMES.put("sw-KE-g2", "Swahili Kenya Grade 2");
        TABLE_NAMES.put("syc-g1", "Syriac Grade 1");
        TABLE_NAMES.put("ta-g1", "Tamil Grade 1");
        TABLE_NAMES.put("ta-g1-2", "Tamil Grade 1");
        TABLE_NAMES.put("ta-comp8", "Tamil Computer Braille");
        TABLE_NAMES.put("te-g1", "Telugu Grade 1");
        TABLE_NAMES.put("te-IN-g1", "Telugu India Grade 1");
        TABLE_NAMES.put("th-g0", "Thai Grade 0");
        TABLE_NAMES.put("th-g1", "Thai Grade 1");
        TABLE_NAMES.put("th-g2", "Thai Grade 2");
        TABLE_NAMES.put("th-comp8", "Thai Computer Braille");
        TABLE_NAMES.put("tr-g1", "Turkish Grade 1");
        TABLE_NAMES.put("tr-g1-2", "Turkish Grade 1");
        TABLE_NAMES.put("tr-g2", "Turkish Grade 2");
        TABLE_NAMES.put("tr-comp8", "Turkish Computer Braille");
        TABLE_NAMES.put("tr-comp8-2", "Turkish Computer Braille");
        TABLE_NAMES.put("tt-g1", "Tatar Grade 1");
        TABLE_NAMES.put("uga-g1", "Ugaritic Grade 1");
        TABLE_NAMES.put("uk-g1", "Ukrainian Grade 1");
        TABLE_NAMES.put("uk-g1-2", "Ukrainian Grade 1 (Detailed)");
        TABLE_NAMES.put("uk-comp8", "Ukrainian Computer Braille");
        TABLE_NAMES.put("ur-g1", "Urdu Grade 1");
        TABLE_NAMES.put("ur-g2", "Urdu Grade 2");
        TABLE_NAMES.put("uz-g1", "Uzbek Grade 1");
        TABLE_NAMES.put("ve-g1", "Venda Grade 1");
        TABLE_NAMES.put("ve-g2", "Venda Grade 2");
        TABLE_NAMES.put("vi-g0", "Vietnamese Grade 0 (Vietnam)");
        TABLE_NAMES.put("vi-g1", "Vietnamese Grade 1 (Saigon)");
        TABLE_NAMES.put("vi-g1-vietnam", "Vietnamese Grade 1 (Vietnam)");
        TABLE_NAMES.put("vi-g2", "Vietnamese Grade 2 (Vietnam)");
        TABLE_NAMES.put("vi-comp8", "Vietnamese Computer Braille (Vietnam)");
        TABLE_NAMES.put("xh-g2", "Xhosa Grade 2");
        TABLE_NAMES.put("yi-g1", "Yiddish Grade 1");
        TABLE_NAMES.put("yue-HK-g1", "Cantonese Hong Kong Grade 1");
        TABLE_NAMES.put("zh-CHN-g1", "Chinese Grade 1");
        TABLE_NAMES.put("ipa", "English Grade 1 (Ipa)");
        TABLE_NAMES.put("af-g1", "Afrikaans Grade 1");
        TABLE_NAMES.put("boxes", "English Computer Braille (Box drawing)");
        TABLE_NAMES.put("cuneiform-compact", "English Computer Braille (Cuneiform compact)");
        TABLE_NAMES.put("cuneiform", "English Computer Braille (Cuneiform)");
        TABLE_NAMES.put("de-chess", "German Grade 1 (Chess)");
        TABLE_NAMES.put("en-chess", "English Grade 1 (Chess)");
        TABLE_NAMES.put("en-nabcc", "English Grade 1 (Nabcc)");
        TABLE_NAMES.put("en-ueb-math", "English Grade 2 (Ueb math)");
        TABLE_NAMES.put("en-us-interline", "English US Grade 1 (Interline)");
        TABLE_NAMES.put("en-us-mathtext", "English US Grade 1 (Math)");
        TABLE_NAMES.put("en-CA-g1", "English Canada Grade 1");
        TABLE_NAMES.put("eo-g1-x-system", "Esperanto Grade 1 (X-system)");
        TABLE_NAMES.put("ethio-g1", "am Grade 1 (Ethiopic)");
        TABLE_NAMES.put("kh-in-g1", "Khasi India Grade 1");
        TABLE_NAMES.put("nso-za-g1", "nso Grade 1");
        TABLE_NAMES.put("nso-za-g2", "nso Grade 2");
        TABLE_NAMES.put("ru-litbrl-detailed", "Russian Grade 1 (Literary detailed)");
        TABLE_NAMES.put("ru-litbrl", "Russian Grade 1 (Literary)");
        TABLE_NAMES.put("sr-Cyrl", "Serbian Grade 1 (Cyrillic)");
        TABLE_NAMES.put("tsn-za-g1", "tsn Grade 1");
        TABLE_NAMES.put("tsn-za-g2", "tsn Grade 2");
        TABLE_NAMES.put("unicode-braille", "Unicode Braille");
        TABLE_NAMES.put("xh-za-g1", "Xhosa Grade 1");
        TABLE_NAMES.put("zhcn-cbs", "Chinese China Grade 1 (Common braille system)");
        TABLE_NAMES.put("zu-za-g1", "zu Grade 1");
        TABLE_NAMES.put("zu-za-g2", "zu Grade 2");

    }

    /**
     * Returns the human-readable display name for a table.
     * Names are looked up from the static TABLE_NAMES mapping.
     * If the table ID is not found, a fallback is constructed from
     * the locale and grade.
     */
    public static String getDisplayName(TableInfo table) {
        String name = TABLE_NAMES.get(table.getId());
        if (name != null) {
            return name;
        }
        // Fallback for any table not in the map.
        StringBuilder text = new StringBuilder();
        text.append(table.getLocale().getDisplayLanguage());
        String country = table.getLocale().getDisplayCountry();
        if (country != null && !country.isEmpty()) {
            text.append(' ').append(country);
        }
        if (table.isEightDot()) {
            text.append(" Computer Braille");
        } else {
            text.append(" Grade ").append(table.getGrade());
        }
        return text.toString();
    }

    /**
     * Returns a human readable description of the table, suitable for display
     * or speech.
     * 
     * @param context
     *            The application context.
     * @param table
     *            The table to describe.
     * @return The human readable table description.
     */
    public static String describeTable(Context context, TableInfo table) {
        return getDisplayName(table);
    }
}
