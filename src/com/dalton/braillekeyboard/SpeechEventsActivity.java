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

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

/**
 * Lets the user choose which speech announcements the keyboard makes.
 *
 * <p>Each {@link SpeechEvent} is shown as a checkbox with its title and
 * description, so adding a new speech event only requires a new enum member.
 */
public class SpeechEventsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.pref_speech_events_title);
        InsetsHelper.apply(this);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new Settings()).commit();
    }

    public static class Settings extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState,
                String rootKey) {
            // The preferences live in device-protected storage so the keyboard
            // works on the lock screen (direct boot), just like the other
            // settings screens.
            Options.getSharedPreferences(getActivity());
            getPreferenceManager().setStorageDeviceProtected();

            PreferenceScreen screen = getPreferenceManager()
                    .createPreferenceScreen(getContext());
            for (SpeechEvent event : SpeechEvent.values()) {
                CheckBoxPreference preference = new CheckBoxPreference(
                        getContext());
                preference.setKey(getString(event.keyResource));
                preference.setTitle(event.titleResource);
                preference.setSummary(event.summaryResource);
                preference.setDefaultValue(event.defaultValue);
                screen.addPreference(preference);
            }
            setPreferenceScreen(screen);
        }
    }
}
