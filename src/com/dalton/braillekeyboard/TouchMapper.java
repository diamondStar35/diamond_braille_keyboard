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

/**
 * Maps between raw view coordinates and keyboard coordinates.
 *
 * <p>The user holds the phone in landscape, but the screen may be fixed to
 * portrait mode. When {@code autoRotate} is disabled the keyboard axes follow
 * the physical device orientation: the keyboard "width" is always the larger
 * screen dimension, and on a portrait screen the touch axes are swapped.
 */
final class TouchMapper {

    private TouchMapper() {
    }

    /** True when the keyboard is laid out along the wider screen dimension. */
    static boolean isLandscapeLayout(boolean autoRotate, int width,
            int height) {
        return autoRotate || width >= height;
    }

    /** Maps a raw view X onto the keyboard X axis. */
    static int mapX(boolean autoRotate, int width, int height, int x, int y) {
        return isLandscapeLayout(autoRotate, width, height) ? x : y;
    }

    /** Maps a raw view Y onto the keyboard Y axis. */
    static int mapY(boolean autoRotate, int width, int height, int x, int y) {
        return isLandscapeLayout(autoRotate, width, height) ? y : x;
    }

    /** The keyboard-space width for the given raw view dimensions. */
    static int normalizedWidth(boolean autoRotate, int width, int height) {
        return autoRotate ? width : Math.max(width, height);
    }

    /** The keyboard-space height for the given raw view dimensions. */
    static int normalizedHeight(boolean autoRotate, int width, int height) {
        return autoRotate ? height : Math.min(width, height);
    }

    /**
     * True when the view is used on a portrait screen held in landscape, in
     * which case swipes must be resolved on swapped axes.
     */
    static boolean isSwap(boolean autoRotate, int width, int height) {
        return height > width && !autoRotate;
    }
}
