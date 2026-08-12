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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Lets the user manage their text expansion entries (abbreviations). Shows
 * every saved entry, one per row, and offers:
 *
 * <ul>
 * <li>an <b>Add abbreviation</b> button that opens a dialog with the new
 *     abbreviation and its expansion;</li>
 * <li>tapping a row to edit it in the same dialog;</li>
 * <li>a <b>Select</b> and a <b>Delete</b> accessibility action on every row
 *     (also reachable via the options menu), which put the list into select
 *     mode where rows show checkboxes, a select-all checkbox and a button to
 *     delete everything selected;</li>
 * <li>an options menu with Select, Import from a file, Export to a file
 *     (both not implemented yet) and Delete all.</li>
 * </ul>
 *
 * <p>All persistence goes through {@link AbbreviationStorage}; this class
 * only deals with the UI and keeps the list of entries in memory.
 */
public class AbbreviationEditorActivity extends AppCompatActivity {

    // Custom accessibility action ids. Custom ids must be greater than or
    // equal to AccessibilityNodeInfo.ACTION_CUSTOM (0x00002000).
    private static final int ACTION_SELECT_ID = 0x00002000 + 1;
    private static final int ACTION_DELETE_ID = 0x00002000 + 2;

    private AbbreviationStorage storage;
    private final List<Abbreviation> abbreviations = new ArrayList<Abbreviation>();
    private final Set<Integer> selected = new HashSet<Integer>();
    private boolean selectMode;

    private AbbreviationAdapter adapter;
    private CheckBox selectAll;
    private Button deleteSelected;
    private CompoundButton.OnCheckedChangeListener selectAllListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.abbreviation_editor);
        setContentView(R.layout.activity_abbreviation_editor);
        InsetsHelper.apply(this);

        storage = new AbbreviationStorage(this);
        abbreviations.addAll(storage.load());

        adapter = new AbbreviationAdapter();
        ListView list = (ListView) findViewById(R.id.list_abbreviations);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.txt_empty));
        // Long presses are also handled by the list itself: a ListView
        // detects the long press in its own touch handling and fires the
        // item long click listener, which is the path that reliably works
        // for a finger held on a row. The row-level listeners in getView
        // cover the accessibility paths. Both enter select mode
        // idempotently, so either one firing is safe.
        list.setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent,
                            View view, int position, long id) {
                        if (!selectMode) {
                            enterSelectMode(position);
                        }
                        return true;
                    }
                });

        selectAll = (CheckBox) findViewById(R.id.cb_select_all);
        selectAllListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                selected.clear();
                if (isChecked) {
                    for (int i = 0; i < abbreviations.size(); i++) {
                        selected.add(i);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        };

        Button add = (Button) findViewById(R.id.btn_add);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAbbreviationDialog(null, -1);
            }
        });

        deleteSelected = (Button) findViewById(R.id.btn_delete_selected);
        deleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteSelected();
            }
        });

        // Pressing back while in select mode only leaves select mode; a
        // second back press closes the editor.
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (selectMode) {
                            exitSelectMode();
                        } else {
                            finish();
                        }
                    }
                });

        updateSelectModeUi();
    }

    // Adds or removes an entry from the selection and refreshes the rows.
    private void toggleSelected(int position) {
        if (selected.contains(position)) {
            selected.remove(position);
        } else {
            selected.add(position);
        }
        adapter.notifyDataSetChanged();
        updateSelectAllState();
    }

    // Shows the shared add/edit dialog. Passing an existing entry and its
    // position opens it in edit mode with the fields filled in.
    private void showAbbreviationDialog(final Abbreviation existing,
            final int editIndex) {
        final boolean editing = existing != null;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        TextView abbreviationLabel = new TextView(this);
        abbreviationLabel.setText(R.string.abbreviation_editor_abbreviation_label);
        layout.addView(abbreviationLabel);

        final EditText abbreviationInput = new EditText(this);
        abbreviationInput
                .setHint(R.string.abbreviation_editor_abbreviation_hint);
        abbreviationInput.setSingleLine(true);
        layout.addView(abbreviationInput);

        TextView expansionLabel = new TextView(this);
        expansionLabel.setText(R.string.abbreviation_editor_expansion_label);
        layout.addView(expansionLabel);

        final EditText expansionInput = new EditText(this);
        expansionInput.setHint(R.string.abbreviation_editor_expansion_hint);
        expansionInput.setMinLines(2);
        layout.addView(expansionInput);

        if (editing) {
            abbreviationInput.setText(existing.getAbbreviation());
            expansionInput.setText(existing.getExpansion());
            expansionInput.setSelection(expansionInput.length());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(editing ? R.string.abbreviation_editor_edit_title
                : R.string.abbreviation_editor_add_title);
        builder.setView(layout);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, null);
        final AlertDialog dialog = builder.create();
        // The ok button is wired up here instead of in the builder so the
        // dialog can stay open when the input is invalid.
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String abbreviation = abbreviationInput
                                        .getText().toString().trim();
                                String expansion = expansionInput.getText()
                                        .toString().trim();
                                if (abbreviation.isEmpty()
                                        || expansion.isEmpty()) {
                                    Toast.makeText(
                                            AbbreviationEditorActivity.this,
                                            R.string.abbreviation_editor_invalid,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                addOrUpdate(abbreviation, expansion,
                                        editIndex);
                                saveAndRefresh();
                                dialog.dismiss();
                            }
                        });
            }
        });
        dialog.show();
    }

    // Inserts or updates a single entry, keeping at most one entry per
    // abbreviation so later lookups are unambiguous.
    private void addOrUpdate(String abbreviation, String expansion,
            int editIndex) {
        // Remove any other entry with the same abbreviation, adjusting the
        // edited entry's index for the removal of entries before it.
        for (int i = abbreviations.size() - 1; i >= 0; i--) {
            if (i != editIndex
                    && abbreviations.get(i).getAbbreviation()
                            .equals(abbreviation)) {
                abbreviations.remove(i);
                if (editIndex > i) {
                    editIndex--;
                }
            }
        }
        Abbreviation entry = new Abbreviation(abbreviation, expansion);
        if (editIndex >= 0 && editIndex < abbreviations.size()) {
            abbreviations.set(editIndex, entry);
        } else {
            abbreviations.add(entry);
        }
    }

    private void confirmDeleteSingle(final int position) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.abbreviation_editor_delete_single_message)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                abbreviations.remove(position);
                                // The accessibility Delete action can fire
                                // while in select mode, where the row
                                // positions in the selection would no longer
                                // match after the removal, so drop the
                                // selection with the entry.
                                if (selectMode) {
                                    selected.clear();
                                }
                                saveAndRefresh();
                            }
                        })
                .setNegativeButton(android.R.string.no, null).show();
    }

    private void confirmDeleteSelected() {
        final int count = selected.size();
        if (count == 0) {
            Toast.makeText(this, R.string.nothing_to_delete,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(getString(
                        R.string.abbreviation_editor_delete_many_message,
                        count))
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                // Remove in reverse order so earlier indices
                                // stay valid while deleting.
                                List<Integer> positions = new ArrayList<Integer>(
                                        selected);
                                Collections.sort(positions);
                                for (int i = positions.size() - 1; i >= 0;
                                        i--) {
                                    abbreviations.remove(positions.get(i)
                                            .intValue());
                                }
                                selected.clear();
                                saveAndRefresh();
                            }
                        })
                .setNegativeButton(android.R.string.no, null).show();
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.abbreviation_editor_delete_all_message)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                abbreviations.clear();
                                selected.clear();
                                saveAndRefresh();
                            }
                        })
                .setNegativeButton(android.R.string.no, null).show();
    }

    // Persists the list, refreshes the rows and reflects the select mode in
    // the UI. Deleting the last entry leaves select mode.
    private void saveAndRefresh() {
        try {
            storage.save(abbreviations);
        } catch (IOException e) {
            Toast.makeText(this, R.string.abbreviation_editor_save_error,
                    Toast.LENGTH_SHORT).show();
        }
        if (selectMode && abbreviations.isEmpty()) {
            selectMode = false;
            selected.clear();
        }
        adapter.notifyDataSetChanged();
        updateSelectModeUi();
    }

    private void enterSelectMode(int position) {
        selectMode = true;
        if (position >= 0) {
            selected.add(position);
        }
        updateSelectModeUi();
    }

    private void exitSelectMode() {
        selectMode = false;
        selected.clear();
        updateSelectModeUi();
    }

    // Shows or hides the select-all checkbox and the delete-selected button
    // depending on the select mode, and refreshes the Select/Done menu item.
    private void updateSelectModeUi() {
        adapter.notifyDataSetChanged();
        selectAll.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        deleteSelected.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        updateSelectAllState();
        invalidateOptionsMenu();
    }

    // Keeps the select-all checkbox in sync with the selection: checked when
    // every entry is selected, unchecked otherwise.
    private void updateSelectAllState() {
        selectAll.setOnCheckedChangeListener(null);
        selectAll.setChecked(!abbreviations.isEmpty()
                && selected.size() == abbreviations.size());
        selectAll.setOnCheckedChangeListener(selectAllListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.abbreviation_editor, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // While in select mode the first menu item turns into Done.
        menu.findItem(R.id.action_select).setTitle(selectMode
                ? R.string.abbreviation_editor_done
                : R.string.abbreviation_editor_select);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_select) {
            if (selectMode) {
                exitSelectMode();
            } else {
                enterSelectMode(-1);
            }
            return true;
        }
        if (id == R.id.action_delete_all) {
            if (abbreviations.isEmpty()) {
                Toast.makeText(this, R.string.nothing_to_delete,
                        Toast.LENGTH_SHORT).show();
            } else {
                confirmDeleteAll();
            }
            return true;
        }
        // Import from a file and Export to a file are not implemented yet;
        // selecting them intentionally does nothing.
        return super.onOptionsItemSelected(item);
    }

    private class AbbreviationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return abbreviations.size();
        }

        @Override
        public Object getItem(int position) {
            return abbreviations.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(AbbreviationEditorActivity.this)
                        .inflate(R.layout.abbreviation_list_item, parent,
                                false);
            }
            final Abbreviation entry = abbreviations.get(position);
            ((TextView) row.findViewById(R.id.tv_abbreviation))
                    .setText(entry.getAbbreviation());
            ((TextView) row.findViewById(R.id.tv_expansion))
                    .setText(entry.getExpansion());

            // The checkbox is only a visual indicator in select mode; the
            // checked state lives on the row itself so the list item can
            // report it to TalkBack.
            final CheckBox check = (CheckBox) row
                    .findViewById(R.id.cb_abbreviation);
            check.setVisibility(selectMode ? View.VISIBLE : View.GONE);
            ((CheckableLinearLayout) row).setChecked(
                    selected.contains(position));

            // Handle clicks and long presses on the row itself. The row is
            // clickable, so it receives touches directly, and TalkBack
            // performs the click and long-click actions on the row view;
            // without these listeners the row would behave like a static
            // label to screen readers.
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectMode) {
                        toggleSelected(position);
                    } else {
                        showAbbreviationDialog(abbreviations.get(position),
                                position);
                    }
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (!selectMode) {
                        enterSelectMode(position);
                    }
                    return true;
                }
            });

            // Expose the Select and Delete actions to TalkBack's actions
            // menu for every row.
            row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                        AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    // In select mode the entry is announced as a single
                    // checkbox ("brb, be right back, check box, checked")
                    // rather than as separate labels and a checkbox. Double
                    // tapping the row toggles it through the row click.
                    if (selectMode) {
                        info.setCheckable(true);
                        info.setChecked(selected.contains(position));
                        info.setClassName(CheckBox.class.getName());
                    } else {
                        info.addAction(new AccessibilityNodeInfo
                                .AccessibilityAction(ACTION_SELECT_ID,
                                getString(
                                        R.string.abbreviation_editor_select)));
                    }
                    info.addAction(new AccessibilityNodeInfo
                            .AccessibilityAction(ACTION_DELETE_ID,
                            getString(R.string.abbreviation_editor_delete)));
                }

                @Override
                public boolean performAccessibilityAction(View host,
                        int action, Bundle args) {
                    if (action == ACTION_SELECT_ID) {
                        enterSelectMode(position);
                        return true;
                    }
                    if (action == ACTION_DELETE_ID) {
                        confirmDeleteSingle(position);
                        return true;
                    }
                    if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                        // A screen reader long press (or its "long click"
                        // action) reaches the row here; start select mode
                        // with this entry selected.
                        if (!selectMode) {
                            enterSelectMode(position);
                        }
                        return true;
                    }
                    return super.performAccessibilityAction(host, action,
                            args);
                }
            });
            return row;
        }
    }
}
