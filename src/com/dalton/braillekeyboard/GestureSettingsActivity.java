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

import java.util.List;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

/**
 * Base activity for the gesture customization screens. Each row is one
 * {@link Swipe} gesture, displayed with its natural language name (e.g.
 * "Swipe dot 2 upwards") and the {@link KeyboardAction} currently bound to
 * it. Tapping a row opens a dialog listing every action, so the mapping is
 * generated entirely from the {@link Swipe} and {@link KeyboardAction}
 * enums: adding a new gesture or action automatically adds it to this
 * screen.
 */
public abstract class GestureSettingsActivity extends AppCompatActivity {

    /** The gestures this screen lets the user rebind. */
    protected abstract List<Swipe> getGestures();

    /** The title of this settings screen. */
    protected abstract int getTitleResource();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getTitleResource());
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
            for (final Swipe swipe : ((GestureSettingsActivity) getActivity())
                    .getGestures()) {
                final Preference preference = new Preference(getContext());
                preference.setTitle(swipe.getDisplayTitle(getContext()));
                preference.setSummary(swipe.getBoundAction(getContext())
                        .getTitle(getContext()));
                preference.setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference clicked) {
                                showActionPicker(swipe, preference);
                                return true;
                            }
                        });
                screen.addPreference(preference);
            }
            setPreferenceScreen(screen);
        }

        // Show the dialog offering every action for the given gesture. This
        // is the standard framework multi-choice dialog, the same one used
        // by the Braille table pickers: each row is a checkbox with the
        // action's title and description. Tapping a row applies that action
        // and closes the dialog immediately; Cancel dismisses without
        // changing anything.
        private void showActionPicker(final Swipe swipe,
                final Preference preference) {
            final KeyboardAction[] actions = KeyboardAction.values();
            // None is declared first in the enum, so it is the first row.
            final CharSequence[] items = new CharSequence[actions.length];
            final boolean[] checked = new boolean[actions.length];
            KeyboardAction current = swipe.getBoundAction(getContext());
            for (int i = 0; i < actions.length; i++) {
                items[i] = actions[i].getTitle(getContext()) + "\n"
                        + actions[i].getSummary(getContext());
                checked[i] = actions[i] == current;
            }

            new AlertDialog.Builder(getActivity())
                    .setTitle(swipe.getDisplayTitle(getContext()))
                    .setMultiChoiceItems(items, checked,
                            new DialogInterface.OnMultiChoiceClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which, boolean isChecked) {
                                    // Picking an action applies it and closes
                                    // the dialog, like a regular picker.
                                    KeyboardAction action = actions[which];
                                    swipe.setBoundAction(getContext(), action);
                                    preference.setSummary(action
                                            .getTitle(getContext()));
                                    dialog.dismiss();
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
}
