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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.LinearLayout;

/**
 * The row of the abbreviation list. It implements {@link Checkable} so the
 * list reports the row's selection state to TalkBack in select mode: the row
 * itself is announced as a checkbox ("brb, be right back, check box,
 * checked") instead of presenting a separate, focusable checkbox next to the
 * entry text.
 *
 * <p>The visible {@link CheckBox} is purely decorative: it has no listeners
 * and taps on it fall through to the row, so {@link #setChecked(boolean)}
 * (called from the adapter when the selection changes) is the only thing
 * that moves it.
 */
public class CheckableLinearLayout extends LinearLayout implements Checkable {

    private boolean checked;

    public CheckableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setChecked(boolean checked) {
        this.checked = checked;
        CheckBox box = (CheckBox) findViewById(R.id.cb_abbreviation);
        if (box != null) {
            box.setChecked(checked);
        }
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }
}
