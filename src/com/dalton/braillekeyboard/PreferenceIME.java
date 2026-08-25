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

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Root settings menu. Each section listed here opens in its own activity
 * (see {@link SectionActivity}).
 */
public class PreferenceIME extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.settings_name);
            InsetsHelper.apply(this);
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new Settings()).commit();
        } catch (Throwable e) {
            StartupErrorActivity.report(this, "PreferenceIME.onCreate", e);
            finish();
        }
    }

    public static class Settings extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState,
                String rootKey) {
            // The preferences live in device-protected storage so the keyboard
            // works on the lock screen (direct boot). Perform the migration up
            // front so the section screens read the same storage.
            Options.getSharedPreferences(getActivity());

            setPreferencesFromResource(R.xml.ime_preferences, rootKey);

            openSection(R.string.pref_category_keyboard_key,
                    SectionActivity.SECTION_KEYBOARD);
            openSection(R.string.pref_category_braille_key,
                    SectionActivity.SECTION_BRAILLE);
            openSection(R.string.pref_category_braille_input_key,
                    SectionActivity.SECTION_BRAILLE_INPUT);
            openSection(R.string.pref_category_feedback_key,
                    SectionActivity.SECTION_FEEDBACK);
            openSection(R.string.pref_text_to_speech_key,
                    SectionActivity.SECTION_TEXT_TO_SPEECH);
            openSection(R.string.pref_category_misc_key,
                    SectionActivity.SECTION_MISC);
            openSection(R.string.pref_category_backup_key,
                    SectionActivity.SECTION_BACKUP);

            Preference preference = findPreference(getActivity().getString(
                    R.string.pref_app_version_key));
            if (preference != null) {
                try {
                    String versionCode = getActivity().getPackageManager()
                            .getPackageInfo(getActivity().getPackageName(), 0).versionName;
                    preference.setTitle(String.format(
                            getActivity()
                                    .getString(R.string.pref_app_version_title),
                            versionCode));
                } catch (Exception e) {
                    preference.setEnabled(false);
                }
            }
        }

        // Launch the activity for a settings section when its header is tapped.
        private void openSection(final int keyResource, final int section) {
            Preference preference = findPreference(getString(keyResource));
            if (preference != null) {
                preference.setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                startActivity(SectionActivity.newIntent(
                                        getActivity(), section));
                                return true;
                            }
                        });
            }
        }
    }
}
