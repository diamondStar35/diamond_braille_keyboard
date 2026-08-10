package com.dalton.braillekeyboard;

/**
 * The gesture actions that can be performed on the keyboard, together with
 * the mapping from the bit-string representations computed by {@link Pad}
 * to the semantic actions.
 *
 * <p>This was extracted from {@link Pad} so the swipe vocabulary is a
 * self-contained type.
 */
public enum Swipe {
    NONE, UNKNOWN, ONE_LEFT, ONE_RIGHT, ONE_UP, ONE_DOWN, TWO_LEFT, TWO_RIGHT, TWO_DOWN, TWO_UP, THREE_LEFT, THREE_RIGHT, THREE_DOWN, THREE_UP, FOUR_LEFT, FOUR_RIGHT, FOUR_UP, FOUR_DOWN, FIVE_LEFT, FIVE_RIGHT, FIVE_DOWN, FIVE_UP, SIX_LEFT, SIX_RIGHT, SIX_DOWN, SIX_UP, HOLD_SIX_LEFT, HOLD_SIX_RIGHT, HOLD_SIX_UP, HOLD_SIX_DOWN, HOLD_THREE_LEFT, HOLD_THREE_RIGHT, HOLD_THREE_UP, HOLD_THREE_DOWN, HOLD_ONE_UP, HOLD_ONE_DOWN, HOLD_ONE_LEFT, HOLD_ONE_RIGHT, HOLD_FOUR_LEFT, HOLD_FOUR_RIGHT, HOLD_FOUR_DOWN, HOLD_FOUR_UP, TWO_FINGERS_DOWN, THREE_FINGERS_DOWN, DOTS_FOUR_SIX_RIGHT, EMOJI_MODE, THREE_FINGERS_LEFT;

    public static Swipe valueOf(int value) {
        // bit strings to represent different swipe actions
        final int VALUE_NONE = 0;
        final int VALUE_ONE_LEFT = 1;
        final int VALUE_ONE_RIGHT = 2;
        final int VALUE_ONE_DOWN = 3;
        final int VALUE_ONE_UP = 4;
        final int VALUE_TWO_LEFT = 8;
        final int VALUE_TWO_RIGHT = 16;
        final int VALUE_TWO_DOWN = 24;
        final int VALUE_TWO_UP = 32;
        final int VALUE_TWO_FINGERS_DOWN = 27;
        final int VALUE_TWO_FINGERS_DOWN_PARTIAL_1 = 31;
        final int VALUE_TWO_FINGERS_DOWN_PARTIAL_2 = 59;
        final int VALUE_DOTS_FOUR_SIX_RIGHT = 66560;
        final int VALUE_THREE_LEFT = 64;
        final int VALUE_THREE_RIGHT = 128;
        final int VALUE_THREE_DOWN = 192;
        final int VALUE_THREE_UP = 256;
        final int VALUE_FOUR_LEFT = 512;
        final int VALUE_FOUR_RIGHT = 1024;
        final int VALUE_FOUR_DOWN = 1536;
        final int VALUE_FOUR_UP = 2048;
        final int VALUE_FIVE_LEFT = 4096;
        final int VALUE_FIVE_RIGHT = 8192;
        final int VALUE_FIVE_DOWN = 12288;
        final int VALUE_FIVE_UP = 16384;
        final int VALUE_SIX_LEFT = 32768;
        final int VALUE_SIX_RIGHT = 65536;
        final int VALUE_SIX_DOWN = 98304;
        final int VALUE_SIX_UP = 131072;
        final int VALUE_HOLD_SIX_ONE_LEFT = 229377;
        final int VALUE_HOLD_SIX_TWO_LEFT = 229384;
        final int VALUE_HOLD_SIX_THREE_LEFT = 229440;
        final int VALUE_HOLD_SIX_ONE_RIGHT = 229378;
        final int VALUE_HOLD_SIX_TWO_RIGHT = 229392;
        final int VALUE_HOLD_SIX_THREE_RIGHT = 229504;
        final int VALUE_HOLD_SIX_ONE_DOWN = 229379;
        final int VALUE_HOLD_SIX_TWO_DOWN = 229400;
        final int VALUE_HOLD_SIX_THREE_DOWN = 229568;
        final int VALUE_HOLD_SIX_ONE_UP = 229380;
        final int VALUE_HOLD_SIX_TWO_UP = 229408;
        final int VALUE_HOLD_SIX_THREE_UP = 229632;
        final int VALUE_HOLD_THREE_FOUR_LEFT = 960;
        final int VALUE_HOLD_THREE_FIVE_LEFT = 4544;
        final int VALUE_HOLD_THREE_SIX_LEFT = 33216;
        final int VALUE_HOLD_THREE_FOUR_RIGHT = 1472;
        final int VALUE_HOLD_THREE_FIVE_RIGHT = 8640;
        final int VALUE_HOLD_THREE_SIX_RIGHT = 65984;
        final int VALUE_HOLD_THREE_FOUR_DOWN = 1984;
        final int VALUE_HOLD_THREE_FIVE_DOWN = 12736;
        final int VALUE_HOLD_THREE_SIX_DOWN = 98752;
        final int VALUE_HOLD_THREE_FOUR_UP = 2496;
        final int VALUE_HOLD_THREE_FIVE_UP = 16832;
        final int VALUE_HOLD_THREE_SIX_UP = 131520;
        final int VALUE_HOLD_ONE_FOUR_UP = 2055;
        final int VALUE_HOLD_ONE_FIVE_UP = 16391;
        final int VALUE_HOLD_ONE_SIX_UP = 131078;
        final int VALUE_HOLD_ONE_FOUR_DOWN = 1543;
        final int VALUE_HOLD_ONE_FIVE_DOWN = 12295;
        final int VALUE_HOLD_ONE_SIX_DOWN = 98311;
        final int VALUE_HOLD_ONE_FOUR_RIGHT = 1031;
        final int VALUE_HOLD_ONE_FIVE_RIGHT = 8199;
        final int VALUE_HOLD_ONE_SIX_RIGHT = 65543;
        final int VALUE_HOLD_ONE_FOUR_LEFT = 519;
        final int VALUE_HOLD_ONE_FIVE_LEFT = 4103;
        final int VALUE_HOLD_ONE_SIX_LEFT = 32775;
        final int VALUE_HOLD_FOUR_ONE_LEFT = 3585;
        final int VALUE_HOLD_FOUR_TWO_LEFT = 3592;
        final int VALUE_HOLD_FOUR_THREE_LEFT = 3648;
        final int VALUE_HOLD_FOUR_ONE_RIGHT = 3586;
        final int VALUE_HOLD_FOUR_TWO_RIGHT = 3600;
        final int VALUE_HOLD_FOUR_THREE_RIGHT = 3712;
        final int VALUE_HOLD_FOUR_ONE_DOWN = 3587;
        final int VALUE_HOLD_FOUR_TWO_DOWN = 3608;
        final int VALUE_HOLD_FOUR_THREE_DOWN = 3776;
        final int VALUE_HOLD_FOUR_ONE_UP = 3588;
        final int VALUE_HOLD_FOUR_TWO_UP = 3616;
        final int VALUE_HOLD_FOUR_THREE_UP = 3840;
        switch (value) {
        case VALUE_NONE:
            return NONE;
        case VALUE_ONE_LEFT:
            return ONE_LEFT;
        case VALUE_ONE_RIGHT:
            return ONE_RIGHT;
        case VALUE_ONE_DOWN:
            return ONE_DOWN;
        case VALUE_ONE_UP:
            return ONE_UP;
        case VALUE_TWO_LEFT:
            return TWO_LEFT;
        case VALUE_TWO_RIGHT:
            return TWO_RIGHT;
        case VALUE_TWO_DOWN:
            return TWO_DOWN;
        case VALUE_TWO_UP:
            return TWO_UP;
        case VALUE_TWO_FINGERS_DOWN:
        case VALUE_TWO_FINGERS_DOWN_PARTIAL_1:
        case VALUE_TWO_FINGERS_DOWN_PARTIAL_2:
            return TWO_FINGERS_DOWN;
        case VALUE_DOTS_FOUR_SIX_RIGHT:
            return DOTS_FOUR_SIX_RIGHT;
        case VALUE_THREE_LEFT:
            return THREE_LEFT;
        case VALUE_THREE_RIGHT:
            return THREE_RIGHT;
        case VALUE_THREE_DOWN:
            return THREE_DOWN;
        case VALUE_THREE_UP:
            return THREE_UP;
        case VALUE_FOUR_LEFT:
            return FOUR_LEFT;
        case VALUE_FOUR_RIGHT:
            return FOUR_RIGHT;
        case VALUE_FOUR_DOWN:
            return FOUR_DOWN;
        case VALUE_FOUR_UP:
            return FOUR_UP;
        case VALUE_FIVE_LEFT:
            return FIVE_LEFT;
        case VALUE_FIVE_RIGHT:
            return FIVE_RIGHT;
        case VALUE_FIVE_UP:
            return FIVE_UP;
        case VALUE_FIVE_DOWN:
            return FIVE_DOWN;
        case VALUE_SIX_LEFT:
            return SIX_LEFT;
        case VALUE_SIX_RIGHT:
            return SIX_RIGHT;
        case VALUE_SIX_DOWN:
            return SIX_DOWN;
        case VALUE_SIX_UP:
            return SIX_UP;
        case VALUE_HOLD_SIX_ONE_RIGHT:
        case VALUE_HOLD_SIX_TWO_RIGHT:
        case VALUE_HOLD_SIX_THREE_RIGHT:
            return HOLD_SIX_RIGHT;
        case VALUE_HOLD_SIX_ONE_LEFT:
        case VALUE_HOLD_SIX_TWO_LEFT:
        case VALUE_HOLD_SIX_THREE_LEFT:
            return HOLD_SIX_LEFT;
        case VALUE_HOLD_SIX_ONE_DOWN:
        case VALUE_HOLD_SIX_TWO_DOWN:
        case VALUE_HOLD_SIX_THREE_DOWN:
            return HOLD_SIX_DOWN;
        case VALUE_HOLD_SIX_ONE_UP:
        case VALUE_HOLD_SIX_TWO_UP:
        case VALUE_HOLD_SIX_THREE_UP:
            return HOLD_SIX_UP;
        case VALUE_HOLD_THREE_SIX_LEFT:
        case VALUE_HOLD_THREE_FIVE_LEFT:
        case VALUE_HOLD_THREE_FOUR_LEFT:
            return HOLD_THREE_LEFT;
        case VALUE_HOLD_THREE_SIX_RIGHT:
        case VALUE_HOLD_THREE_FIVE_RIGHT:
        case VALUE_HOLD_THREE_FOUR_RIGHT:
            return HOLD_THREE_RIGHT;
        case VALUE_HOLD_THREE_SIX_UP:
        case VALUE_HOLD_THREE_FIVE_UP:
        case VALUE_HOLD_THREE_FOUR_UP:
            return HOLD_THREE_UP;
        case VALUE_HOLD_THREE_SIX_DOWN:
        case VALUE_HOLD_THREE_FIVE_DOWN:
        case VALUE_HOLD_THREE_FOUR_DOWN:
            return HOLD_THREE_DOWN;
        case VALUE_HOLD_ONE_SIX_DOWN:
        case VALUE_HOLD_ONE_FOUR_DOWN:
            return HOLD_ONE_DOWN;
        case VALUE_HOLD_ONE_FIVE_DOWN:
            return EMOJI_MODE;
        case VALUE_HOLD_ONE_SIX_UP:
        case VALUE_HOLD_ONE_FIVE_UP:
        case VALUE_HOLD_ONE_FOUR_UP:
            return HOLD_ONE_UP;
        case VALUE_HOLD_ONE_SIX_RIGHT:
        case VALUE_HOLD_ONE_FIVE_RIGHT:
        case VALUE_HOLD_ONE_FOUR_RIGHT:
            return HOLD_ONE_RIGHT;
        case VALUE_HOLD_ONE_SIX_LEFT:
        case VALUE_HOLD_ONE_FIVE_LEFT:
        case VALUE_HOLD_ONE_FOUR_LEFT:
            return HOLD_ONE_LEFT;
        case VALUE_HOLD_FOUR_ONE_LEFT:
        case VALUE_HOLD_FOUR_TWO_LEFT:
        case VALUE_HOLD_FOUR_THREE_LEFT:
            return HOLD_FOUR_LEFT;
        case VALUE_HOLD_FOUR_ONE_RIGHT:
        case VALUE_HOLD_FOUR_TWO_RIGHT:
        case VALUE_HOLD_FOUR_THREE_RIGHT:
            return HOLD_FOUR_RIGHT;
        case VALUE_HOLD_FOUR_ONE_DOWN:
        case VALUE_HOLD_FOUR_TWO_DOWN:
        case VALUE_HOLD_FOUR_THREE_DOWN:
            return HOLD_FOUR_DOWN;
        case VALUE_HOLD_FOUR_ONE_UP:
        case VALUE_HOLD_FOUR_TWO_UP:
        case VALUE_HOLD_FOUR_THREE_UP:
            return HOLD_FOUR_UP;
        default:
            return UNKNOWN;
        }
    }
}
