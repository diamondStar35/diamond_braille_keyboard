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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.EngineInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.dalton.braillekeyboard.BrailleType;
import com.dalton.braillekeyboard.Options.KeyboardEcho;
import com.dalton.braillekeyboard.Options.KeyboardFeedback;
import com.dalton.braillekeyboard.Options.OptionList;
import com.googlecode.eyesfree.braille.translate.TableInfo;

/**
 * A settings section displayed in its own activity. The section to show is
 * selected by {@link #EXTRA_SECTION}; each section loads its own preference
 * resource and applies any dynamic population it needs (braille tables, TTS
 * engines, echo/feedback options).
 */
public class SectionActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION = "section";

    public static final int SECTION_KEYBOARD = 1;
    public static final int SECTION_BRAILLE = 2;
    public static final int SECTION_FEEDBACK = 3;
    public static final int SECTION_TEXT_TO_SPEECH = 4;
    public static final int SECTION_MISC = 5;

    public static Intent newIntent(Context context, int section) {
        Intent intent = new Intent(context, SectionActivity.class);
        intent.putExtra(EXTRA_SECTION, section);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int section = getIntent().getIntExtra(EXTRA_SECTION, SECTION_KEYBOARD);
        setTitle(sectionTitle(section));
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, Settings.newInstance(section))
                .commit();
    }

    private int sectionTitle(int section) {
        switch (section) {
        case SECTION_BRAILLE:
            return R.string.pref_category_braille_title;
        case SECTION_FEEDBACK:
            return R.string.pref_category_feedback_title;
        case SECTION_TEXT_TO_SPEECH:
            return R.string.pref_text_to_speech_title;
        case SECTION_MISC:
            return R.string.pref_category_misc_title;
        default:
            return R.string.pref_category_keyboard_title;
        }
    }

    public static class Settings extends PreferenceFragmentCompat {
        private static final String ARG_SECTION = "section";

        private Parser brailleParser;
        private int section;
        private TextToSpeech tts;

        public static Settings newInstance(int section) {
            Settings fragment = new Settings();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION, section);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState,
                String rootKey) {
            // The preferences live in device-protected storage so the keyboard
            // works on the lock screen (direct boot). Make the settings screen
            // read and write the same storage. The credential ->
            // device-protected migration runs before the screen loads its
            // values, so freshly changed settings can't be clobbered by a
            // later one-shot migration.
            Options.getSharedPreferences(getActivity());
            getPreferenceManager().setStorageDeviceProtected();

            section = getArguments().getInt(ARG_SECTION,
                    SectionActivity.SECTION_KEYBOARD);
            switch (section) {
            case SectionActivity.SECTION_BRAILLE:
                setPreferencesFromResource(R.xml.prefs_braille, rootKey);
                addTables();
                break;
            case SectionActivity.SECTION_FEEDBACK:
                setPreferencesFromResource(R.xml.prefs_feedback, rootKey);
                
                // Populate Sound Theme ListPreference
                ListPreference themePref = (ListPreference) findPreference(getString(R.string.pref_sound_theme_key));
                if (themePref != null) {
                    List<SoundTheme> themeList = FeedbackManager
                            .getAvailableThemes(getActivity());

                    // Add "Off" option at the top
                    String[] entries = new String[themeList.size() + 1];
                    String[] entryValues = new String[themeList.size() + 1];

                    entries[0] = getString(R.string.label_sound_off);
                    entryValues[0] = SoundTheme.ID_OFF;

                    for (int i = 0; i < themeList.size(); i++) {
                        entries[i + 1] = themeList.get(i).displayName;
                        entryValues[i + 1] = themeList.get(i).id;
                    }

                    themePref.setEntries(entries);
                    themePref.setEntryValues(entryValues);
                }

                addOptions(findPreference(getString(
                        R.string.pref_echo_feedback_key)),
                        KeyboardEcho.ALL);
                break;
            case SectionActivity.SECTION_TEXT_TO_SPEECH:
                setPreferencesFromResource(R.xml.prefs_tts, rootKey);
                addTTSList(findPreference(getString(
                        R.string.pref_text_to_speech_engine_key)));
                break;
            case SectionActivity.SECTION_MISC:
                setPreferencesFromResource(R.xml.prefs_misc, rootKey);
                break;
            default:
                setPreferencesFromResource(R.xml.prefs_keyboard, rootKey);
                // The "Default braille table" list is populated from the
                // tables the user has checked in the braille section.
                addTables();
                break;
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (brailleParser != null) {
                brailleParser.destroy();
            }
            if (tts != null) {
                tts.shutdown();
            }
        }

        private void addTables() {
            brailleParser = new Parser(getActivity(),
                    new Parser.Listener() {

                        @Override
                        public void onTranslatorReady(int status) {
                            if (section == SectionActivity.SECTION_BRAILLE) {
                                populateTables(status);
                            } else {
                                populateDefaultTable(status);
                            }
                        }
                    });
        }

        // The tables are only available once the translator reports ready, so
        // this is invoked from the Parser callback rather than directly.
        private void populateTables(int status) {
            MultiSelectListPreference compBraille = findPreference(getString(R.string.pref_braille_computer_table_key));
            MultiSelectListPreference literaryBraille = findPreference(getString(R.string.pref_braille_literary_table_key));

            List<String> entries = new ArrayList<String>();
            List<String> entryValues = new ArrayList<String>();

            List<TableInfo> tables = new ArrayList<TableInfo>();
            if (status == Parser.STATUS_OK) {
                tables = brailleParser.getTables(BrailleType.LITERARY);
            }
            java.util.Set<String> litIds = brailleParser.getConfiguredTableIds(getActivity(), BrailleType.LITERARY);
            populateWithTables(tables, entries, entryValues, litIds);
            literaryBraille.setEntries(entries.toArray(new String[entries.size()]));
            literaryBraille.setEntryValues(entryValues.toArray(new String[entryValues.size()]));
            literaryBraille.setValues(litIds);

            entries.clear();
            entryValues.clear();
            if (status == Parser.STATUS_OK) {
                tables = brailleParser.getTables(BrailleType.COMPUTER);
            }
            java.util.Set<String> compIds = brailleParser.getConfiguredTableIds(getActivity(), BrailleType.COMPUTER);
            populateWithTables(tables, entries, entryValues, compIds);
            compBraille.setEntries(entries.toArray(new String[entries.size()]));
            compBraille.setEntryValues(entryValues.toArray(new String[entryValues.size()]));
            compBraille.setValues(compIds);
        }

        // Populate the "Default braille table" list in the keyboard settings
        // with "Use the last language" followed by every table the user has
        // checked in the braille table settings.
        private void populateDefaultTable(int status) {
            ListPreference defaultTable = findPreference(getActivity()
                    .getString(R.string.pref_default_braille_table_key));
            if (defaultTable == null) {
                return;
            }
            List<String> entries = new ArrayList<String>();
            List<String> entryValues = new ArrayList<String>();
            entries.add(getActivity().getString(
                    R.string.pref_default_braille_table_last_entry));
            entryValues.add(getActivity().getString(
                    R.string.pref_default_braille_table_last));

            Set<String> seen = new HashSet<String>();
            if (status == Parser.STATUS_OK) {
                BrailleType[] types = { BrailleType.LITERARY,
                        BrailleType.COMPUTER };
                for (BrailleType type : types) {
                    for (String id : brailleParser.getConfiguredTableIds(
                            getActivity(), type)) {
                        if (!seen.add(id)) {
                            continue;
                        }
                        TableInfo table = brailleParser.getTableInfoById(id);
                        if (table != null) {
                            entries.add(TableNames.describeTable(
                                    getActivity(), table));
                            entryValues.add(table.getId());
                        }
                    }
                }
            }
            defaultTable.setEntries(entries
                    .toArray(new String[entries.size()]));
            defaultTable.setEntryValues(entryValues
                    .toArray(new String[entryValues.size()]));
        }

        private void addOptions(ListPreference pref, OptionList option) {
            OptionList[] types = option.getValues();
            CharSequence[] entries = new CharSequence[types.length];
            CharSequence[] entryValues = new CharSequence[entries.length];
            for (int i = 0; i < entries.length; i++) {
                entries[i] = getString(types[i].getResource());
                entryValues[i] = types[i].getValue();
            }
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void populateWithTables(List<TableInfo> tables,
                List<String> entries, List<String> entryValues, java.util.Set<String> selectedIds) {
            for (TableInfo table : tables) {
                if (selectedIds.contains(table.getId())) {
                    entries.add(TableNames.describeTable(getActivity(), table));
                    entryValues.add(table.getId());
                }
            }
            for (TableInfo table : tables) {
                if (!selectedIds.contains(table.getId())) {
                    entries.add(TableNames.describeTable(getActivity(), table));
                    entryValues.add(table.getId());
                }
            }
        }

        private void addTTSList(final ListPreference preference) {
            tts = new TextToSpeech(getActivity(),
                    new TextToSpeech.OnInitListener() {

                        @Override
                        public void onInit(int status) {
                            doEnginesList(preference);
                        }
                    });
        }

        private void doEnginesList(ListPreference preference) {
            if (tts == null) {
                return;
            }
            List<EngineInfo> engines = tts.getEngines();
            tts.shutdown();
            tts = null;
            Collections.sort(engines, new Comparator<EngineInfo>() {
                @Override
                public int compare(EngineInfo o1, EngineInfo o2) {
                    return o1.label.toLowerCase(Locale.getDefault()).compareTo(
                            o2.label.toLowerCase(Locale.getDefault()));
                }
            });

            CharSequence[] entries = new CharSequence[engines.size()];
            CharSequence[] entryValues = new CharSequence[engines.size()];

            for (int i = 0; i < engines.size(); i++) {
                String label = engines.get(i).label;
                String name = engines.get(i).name;
                entryValues[i] = name.subSequence(0, name.length());
                entries[i] = label.subSequence(0, label.length());
            }

            preference.setEntries(entries);
            preference.setEntryValues(entryValues);
        }
    }
}
