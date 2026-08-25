/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

/**
 * The sound theme library: every theme the keyboard can use, built-in and
 * installed alike.
 *
 * <p>Tapping a theme starts using it. Long pressing one opens the actions it
 * supports - the same actions a screen reader reaches through the row's
 * long-click action, which is why the row itself carries the listener rather
 * than only the list.
 *
 * <p>Built-in themes cannot be deleted, so Delete is left out of their popup
 * entirely rather than shown and refused: an action that is never going to
 * work should not be offered.
 */
public class SoundThemeManagerActivity extends AppCompatActivity {

    /** Cache subdirectory exposed through the FileProvider for sharing. */
    private static final String SHARE_DIR = "shared_themes";

    private static final String STATE_PENDING_THEME = "pendingThemeId";

    private final List<SoundTheme> themes = new ArrayList<SoundTheme>();
    private ThemeAdapter adapter;

    /**
     * The theme an export or share is running for. Held as an id rather than
     * an object, and saved with the instance state, because the file picker
     * can outlive this activity - a rotation while it is open would
     * otherwise lose track of what was being exported.
     */
    private String pendingThemeId;

    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_theme_manager);
        setTitle(R.string.theme_manager_title);
        InsetsHelper.apply(this);

        if (savedInstanceState != null) {
            pendingThemeId = savedInstanceState.getString(
                    STATE_PENDING_THEME);
        }

        // Clear out anything a previous session left half-unpacked before
        // listing, so a crash during an import cannot waste space forever.
        ThemeLibrary.sweepTransient(this);

        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(
                        "application/octet-stream"),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            exportTo(pendingTheme(), uri);
                        }
                    }
                });
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            stageImport(uri);
                        }
                    }
                });

        adapter = new ThemeAdapter();
        ListView list = (ListView) findViewById(R.id.list_themes);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.txt_empty));
        // The list detects a long press in its own touch handling, which is
        // the path that works for a finger held on a row; the row-level
        // listener in getView covers the accessibility path. Both open the
        // same popup, and opening it twice is harmless.
        list.setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent,
                            View view, int position, long id) {
                        showActions(position);
                        return true;
                    }
                });
        reload();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_THEME, pendingThemeId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The list is rebuilt on the way back in: a theme may have been
        // installed, renamed or removed while this screen was in the
        // background.
        reload();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sound_theme_manager, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new_theme) {
            // Creating a theme means editing it, and the editor is not
            // built yet; say so rather than making an empty silent theme
            // the user cannot change.
            Toast.makeText(this, R.string.theme_not_implemented,
                    Toast.LENGTH_LONG).show();
            return true;
        } else if (id == R.id.action_import_theme) {
            // Every type: ".st" is an unknown extension and providers report
            // its type inconsistently, so filtering would hide the very file
            // the user came to pick. What it actually is gets checked when
            // it is read.
            importLauncher.launch(new String[] { "*/*" });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- The list -------------------------------------------------------

    private void reload() {
        themes.clear();
        themes.addAll(SoundTheme.listThemes(this));
        adapter.notifyDataSetChanged();
    }

    /** The id of the theme the keyboard is set to, or null when sound is off. */
    private String activeId() {
        String stored = Options.getStringPreference(this,
                R.string.pref_sound_theme_key,
                getString(R.string.pref_sound_theme_default));
        return SoundTheme.ID_OFF.equalsIgnoreCase(stored) ? null : stored;
    }

    private boolean isActive(SoundTheme theme) {
        String active = activeId();
        return active != null && (theme.id.equalsIgnoreCase(active)
                || theme.folderName.equalsIgnoreCase(active));
    }

    // Start using a theme. The keyboard picks the change up through its own
    // preference listener, so there is nothing else to notify.
    private void select(SoundTheme theme) {
        Options.writeStringPreference(this, R.string.pref_sound_theme_key,
                theme.id);
        adapter.notifyDataSetChanged();
        Toast.makeText(this, getString(R.string.theme_selected,
                theme.displayName), Toast.LENGTH_SHORT).show();
    }

    // ---- Actions --------------------------------------------------------

    private void showActions(int position) {
        if (position < 0 || position >= themes.size()) {
            return;
        }
        final SoundTheme theme = themes.get(position);

        // Built-in themes cannot be deleted, so the entry is absent for
        // them; everything else applies to every theme.
        final List<CharSequence> labels = new ArrayList<CharSequence>();
        final List<Integer> actions = new ArrayList<Integer>();
        labels.add(getString(R.string.theme_action_edit));
        actions.add(R.string.theme_action_edit);
        if (theme.isEditable()) {
            labels.add(getString(R.string.theme_action_delete));
            actions.add(R.string.theme_action_delete);
        }
        labels.add(getString(R.string.theme_action_export));
        actions.add(R.string.theme_action_export);
        labels.add(getString(R.string.theme_action_share));
        actions.add(R.string.theme_action_share);
        labels.add(getString(R.string.theme_action_info));
        actions.add(R.string.theme_action_info);

        new AlertDialog.Builder(this)
                .setTitle(theme.displayName)
                .setItems(labels.toArray(new CharSequence[labels.size()]),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                perform(actions.get(which), theme);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void perform(int action, SoundTheme theme) {
        if (action == R.string.theme_action_edit) {
            Toast.makeText(this, R.string.theme_not_implemented,
                    Toast.LENGTH_LONG).show();
        } else if (action == R.string.theme_action_delete) {
            confirmDelete(theme);
        } else if (action == R.string.theme_action_export) {
            pendingThemeId = theme.id;
            exportLauncher.launch(suggestedFileName(theme));
        } else if (action == R.string.theme_action_share) {
            share(theme);
        } else if (action == R.string.theme_action_info) {
            showInfo(theme);
        }
    }

    private void confirmDelete(final SoundTheme theme) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_delete_title)
                .setMessage(getString(R.string.theme_delete_message,
                        theme.displayName))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                boolean deleted = ThemeLibrary.delete(
                                        SoundThemeManagerActivity.this,
                                        theme);
                                Toast.makeText(
                                        SoundThemeManagerActivity.this,
                                        deleted ? R.string.theme_deleted
                                                : R.string
                                                        .theme_delete_failed,
                                        Toast.LENGTH_LONG).show();
                                reload();
                            }
                        })
                .show();
    }

    // Each detail gets its own label and its own value view. A single
    // setMessage() would put all six in one TextView, which a screen reader
    // reads out as one uninterrupted run rather than as items the user can
    // step through.
    private void showInfo(SoundTheme theme) {
        View content = LayoutInflater.from(this).inflate(
                R.layout.dialog_theme_info, null);
        ((TextView) content.findViewById(R.id.tv_info_name))
                .setText(theme.displayName);
        ((TextView) content.findViewById(R.id.tv_info_version))
                .setText(orUnknown(theme.version));
        ((TextView) content.findViewById(R.id.tv_info_author))
                .setText(orUnknown(theme.author));

        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_info_title)
                .setView(content)
                .setPositiveButton(R.string.theme_info_close, null)
                .show();
    }

    private String orUnknown(String value) {
        return value == null || value.isEmpty()
                ? getString(R.string.pref_version_unknown) : value;
    }

    // The theme an in-flight export belongs to, resolved fresh: the library
    // may have changed while the picker was open.
    private SoundTheme pendingTheme() {
        return pendingThemeId == null ? null
                : SoundTheme.loadById(this, pendingThemeId);
    }

    // A file name for an exported theme, built from its display name so the
    // user recognises it in their file manager.
    private String suggestedFileName(SoundTheme theme) {
        String name = theme.displayName.replaceAll("[^A-Za-z0-9 _-]", "_")
                .trim();
        return (name.isEmpty() ? "sound_theme" : name)
                + ThemeArchive.EXTENSION;
    }

    // ---- Export, share, import ------------------------------------------

    private void exportTo(final SoundTheme theme, final Uri destination) {
        if (theme == null) {
            Toast.makeText(this, R.string.theme_export_failed,
                    Toast.LENGTH_LONG).show();
            return;
        }
        final Context context = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                OutputStream output = null;
                try {
                    // "wt" truncates, so exporting over a larger file cannot
                    // leave a tail of the old one behind.
                    output = context.getContentResolver()
                            .openOutputStream(destination, "wt");
                    if (output == null) {
                        throw new ThemeException(R.string.theme_export_failed,
                                "cannot open " + destination);
                    }
                    ThemeArchive.write(context, theme, output);
                    output.flush();
                    Diagnostics.log(context, "exported sound theme "
                            + theme.id);
                    toastLater(context, R.string.theme_exported);
                } catch (ThemeException e) {
                    fail(context, e);
                } catch (Exception e) {
                    fail(context, new ThemeException(
                            R.string.theme_export_failed, "export failed", e));
                } finally {
                    closeQuietly(output);
                }
            }
        }, "theme-export").start();
    }

    // Zip the theme into the cache, then hand that file to the share sheet
    // through the FileProvider - the same route the log file already takes.
    private void share(final SoundTheme theme) {
        final Context context = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                OutputStream output = null;
                try {
                    File directory = new File(context.getCacheDir(),
                            SHARE_DIR);
                    if (!directory.isDirectory() && !directory.mkdirs()) {
                        throw new ThemeException(R.string.theme_share_failed,
                                "cannot create " + directory);
                    }
                    // One file per theme, overwritten each time, so repeated
                    // sharing cannot fill the cache.
                    File archive = new File(directory,
                            ThemeLibrary.slugify(theme.id)
                                    + ThemeArchive.EXTENSION);
                    output = new java.io.FileOutputStream(archive);
                    ThemeArchive.write(context, theme, output);
                    output.flush();
                    closeQuietly(output);
                    output = null;
                    startShare(context, theme, archive);
                } catch (ThemeException e) {
                    fail(context, e);
                } catch (Exception e) {
                    fail(context, new ThemeException(
                            R.string.theme_share_failed, "share failed", e));
                } finally {
                    closeQuietly(output);
                }
            }
        }, "theme-share").start();
    }

    private void startShare(final Context context, final SoundTheme theme,
            final File archive) {
        onMainThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Uri uri = FileProvider.getUriForFile(context,
                            context.getPackageName() + ".fileprovider",
                            archive);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_SUBJECT, getString(
                            R.string.theme_share_subject, theme.displayName));
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent,
                            getString(R.string.theme_action_share)));
                } catch (Exception e) {
                    // Sharing must never crash the screen, for example when
                    // the file cannot be exposed through the FileProvider.
                    Diagnostics.log(context, "share sound theme failed: " + e);
                    Toast.makeText(context, R.string.theme_share_failed,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // Unpack and validate off the main thread, then install, or come back to
    // ask what to do about a theme that is already here.
    private void stageImport(final Uri uri) {
        final Context context = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream input = null;
                try {
                    input = context.getContentResolver().openInputStream(uri);
                    if (input == null) {
                        throw new ThemeException(
                                R.string.theme_import_failed_damaged,
                                "cannot open " + uri);
                    }
                    final ThemeArchive.Staged staged = ThemeArchive.stage(
                            context, input);
                    final SoundTheme existing = ThemeLibrary.collidingTheme(
                            context, staged.theme.id);
                    onMainThread(new Runnable() {
                        @Override
                        public void run() {
                            if (existing == null) {
                                install(staged,
                                        ThemeLibrary.Collision.REPLACE);
                            } else {
                                askAboutCollision(staged, existing);
                            }
                        }
                    });
                } catch (ThemeException e) {
                    fail(context, e);
                } catch (Exception e) {
                    fail(context, new ThemeException(
                            R.string.theme_import_failed_damaged,
                            "import failed", e));
                } finally {
                    closeQuietly(input);
                }
            }
        }, "theme-import").start();
    }

    // The id is already in the library. Name both versions, so the choice is
    // about themes the user recognises rather than about ids.
    private void askAboutCollision(final ThemeArchive.Staged staged,
            SoundTheme existing) {
        if (isFinishing()) {
            ThemeArchive.discard(staged);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_collision_title)
                .setMessage(getString(R.string.theme_collision_message,
                        existing.displayName, orUnknown(existing.version),
                        orUnknown(staged.theme.version)))
                .setPositiveButton(R.string.theme_collision_replace,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                install(staged,
                                        ThemeLibrary.Collision.REPLACE);
                            }
                        })
                .setNeutralButton(R.string.theme_collision_keep_both,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                install(staged,
                                        ThemeLibrary.Collision.KEEP_BOTH);
                            }
                        })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                ThemeArchive.discard(staged);
                            }
                        })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        // Dismissed without choosing; the staged copy would
                        // otherwise sit there until the next sweep.
                        ThemeArchive.discard(staged);
                    }
                })
                .show();
    }

    // Commit a staged theme. The move is a rename, so this is quick enough
    // to do on the main thread.
    private void install(ThemeArchive.Staged staged,
            ThemeLibrary.Collision collision) {
        try {
            SoundTheme installed = ThemeLibrary.commit(this, staged,
                    collision);
            Toast.makeText(this, getString(R.string.theme_imported,
                    installed.displayName), Toast.LENGTH_LONG).show();
        } catch (ThemeException e) {
            ThemeArchive.discard(staged);
            Diagnostics.log(this, "theme import failed: " + e.getMessage());
            Toast.makeText(this, e.messageResource, Toast.LENGTH_LONG).show();
        }
        reload();
    }

    // ---- Plumbing -------------------------------------------------------

    private void fail(Context context, ThemeException e) {
        Diagnostics.log(context, "sound theme operation failed: "
                + e.getMessage());
        toastLater(context, e.messageResource);
    }

    private void toastLater(final Context context, final int message) {
        onMainThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onMainThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }

    private static void closeQuietly(java.io.Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Nothing the caller could act on.
            }
        }
    }

    private class ThemeAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return themes.size();
        }

        @Override
        public Object getItem(int position) {
            return themes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(SoundThemeManagerActivity.this)
                        .inflate(R.layout.sound_theme_list_item, parent,
                                false);
            }
            final SoundTheme theme = themes.get(position);
            ((TextView) row.findViewById(R.id.tv_theme_name))
                    .setText(theme.displayName);
            ((TextView) row.findViewById(R.id.tv_theme_detail))
                    .setText(describe(theme));

            // Handle clicks and long presses on the row itself. The row is
            // clickable, so it receives touches directly, and TalkBack
            // performs the click and long-click actions on the row view;
            // without these listeners the row would behave like a static
            // label to screen readers and the actions popup would be
            // unreachable.
            final int index = position;
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(theme);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showActions(index);
                    return true;
                }
            });
            return row;
        }

        // The second line: where the theme came from, and whether it is the
        // one in use. Both are what someone needs in order to choose.
        private String describe(SoundTheme theme) {
            String origin = getString(theme.isEditable()
                    ? R.string.theme_detail_custom
                    : R.string.theme_detail_builtin);
            return isActive(theme)
                    ? origin + ", " + getString(R.string.theme_detail_active)
                    : origin;
        }
    }
}
