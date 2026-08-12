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
import java.util.List;

/**
 * Lets the user choose which action each swipe gesture performs: the single
 * dot swipes (up, down, left and right for every dot), the multi-finger
 * swipes (two and three fingers in every direction) and the dots 4 and 6
 * swipes used to close the keyboard.
 *
 * <p>The list is generated from the {@link Swipe} enum, so adding a new
 * swipe gesture automatically adds it here.
 */
public class GesturesActivity extends GestureSettingsActivity {

    @Override
    protected List<Swipe> getGestures() {
        List<Swipe> gestures = new ArrayList<Swipe>();
        for (Swipe swipe : Swipe.values()) {
            if (swipe.isConfigurable() && !swipe.isTouchHold()) {
                gestures.add(swipe);
            }
        }
        return gestures;
    }

    @Override
    protected int getTitleResource() {
        return R.string.pref_customize_gestures_title;
    }
}
