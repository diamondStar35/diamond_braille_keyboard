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

import java.io.File;
import java.util.List;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

/**
 * This is the MainActivity of the application.
 * 
 * This activity is shown when the user opens the app from the app screen. Most
 * of the app's logic is handled as part of the IME, but this activity provides
 * the UI for the user to enable this keyboard, practice in a text field,
 * navigate to the Settings screen and to navigate to the user manual.
 */
public class MainActivity extends AppCompatActivity {

    // Request code for the notification permission.
    private static final int REQUEST_NOTIFICATIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            // Same process as the IME: catch crashes process-wide here too,
            // in case the app is opened before the keyboard ever started.
            CrashGuard.install(this);
            setContentView(R.layout.activity_main);
            InsetsHelper.apply(this);
            updateUIStates();
            requestNotificationsOnce();
        } catch (Throwable e) {
            StartupErrorActivity.report(this, "MainActivity.onCreate", e);
            finish();
        }
    }

    // Ask for the notification permission on first launch (Android 13+).
    // Without it the crash notification from the IME service would be
    // dropped silently. Only asked once; the user can still grant it later
    // through the system settings.
    private void requestNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (Options.getBooleanPreference(this,
                R.string.pref_asked_notifications_key, false)) {
            return;
        }
        Options.writeBooleanPreference(this,
                R.string.pref_asked_notifications_key, true);
        ActivityCompat.requestPermissions(this, new String[] {
                Manifest.permission.POST_NOTIFICATIONS },
                REQUEST_NOTIFICATIONS);
    }

    // Called when we gain or lose focus.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // If we have focus update the state of our buttons as system
            // settings might have changed.
            updateUIStates();
        }
    }

    // Triggered when the user clicks the enable keyboard button.
    public void onKeyboardSettings(View view) {
        // The result is not needed, so start the activity without the
        // deprecated startActivityForResult().
        startActivity(new Intent(
                android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));
    }

    // Triggered when the user clicks the enable accessibility service button.
    // Accessibility services can only be enabled by the user from the system
    // settings, so we take them there.
    public void onAccessibilitySettings(View view) {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    // Triggered when the button to change default input method is pressed.
    public void onDefaultInputMethod(View view) {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.showInputMethodPicker();
    }

    // Triggered when the user clicks the button to read the manual.
    // Visit the appropriate url for the documentation for the current Locale in
    // the web browser.
    public void onURL(View view) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(getString(R.string.info_url)));
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, PreferenceIME.class);
            startActivity(intent);
        } else if (id == R.id.action_abbreviation_editor) {
            startActivity(new Intent(this, AbbreviationEditorActivity.class));
        } else if (id == R.id.action_sound_theme_manager) {
            startActivity(new Intent(this, SoundThemeManagerActivity.class));
        } else if (id == R.id.action_share_logs) {
            shareLogs();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Send the diagnostic log file to another app through the share sheet.
    private void shareLogs() {
        File logFile = Diagnostics.getLogFile(this);
        if (logFile.exists() && logFile.length() > 0) {
            try {
                // Record that the logs were shared and refresh the device
                // snapshot so the file the user sends is as complete as
                // possible.
                Diagnostics.logDeviceInfo(this);
                Diagnostics.log(this, "log file shared from the main screen");
                // Make sure the lines above are on disk before the file is
                // handed to the share sheet.
                Diagnostics.flush();
                Uri uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", logFile);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT,
                        getString(R.string.app_name) + " log file");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent,
                        getString(R.string.action_share_logs)));
            } catch (Exception e) {
                // Sharing the log file must never crash the app (for example
                // if the file cannot be exposed through the FileProvider).
                Diagnostics.log(this, "share logs failed: " + e);
                Toast.makeText(this, R.string.share_logs_failed,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.no_log_file, Toast.LENGTH_LONG)
                    .show();
        }
    }

    // Update the state of buttons (clickable) or not and decide whether to show
    // the sample text field.
    private void updateUIStates() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> list = inputManager.getEnabledInputMethodList();
        Button btnEnable = (Button) findViewById(R.id.btn_enable);
        Button btnDefaultKeyboard = (Button) findViewById(R.id.btn_default_keyboard);
        Button btnAccessibility = (Button) findViewById(R.id.btn_accessibility);
        EditText text = (EditText) findViewById(R.id.txt_practice);
        btnEnable.setEnabled(true);
        btnDefaultKeyboard.setEnabled(false);
        text.setVisibility(View.INVISIBLE);

        // Gesture passthrough regions only exist on Android 11+, so there is
        // nothing to enable on older versions.
        btnAccessibility.setVisibility(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? View.VISIBLE : View.GONE);
        // Once the service is enabled the button has done its job.
        btnAccessibility.setEnabled(
                !AccessibilityService.isServiceEnabled(this));

        for (InputMethodInfo info : list) {
            if (info.getPackageName().equals(getPackageName())) {
                // sbk is enabled as an input method, may or may not be default.
                btnEnable.setEnabled(false);
                btnDefaultKeyboard.setEnabled(true);
                String id = Settings.Secure.getString(getContentResolver(),
                        Settings.Secure.DEFAULT_INPUT_METHOD);
                if (info.getId().equals(id)) {
                    // SBK is default so disable make sbk default button and
                    // show the sample text field.
                    btnDefaultKeyboard.setEnabled(false);
                    text.setVisibility(View.VISIBLE);
                }
                return;
            }
        }
    }
}
