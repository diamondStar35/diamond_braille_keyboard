package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The gestures and touch-hold commands that can be performed on the
 * keyboard, together with the mapping from the bit-string representations
 * computed by {@link Pad} to the semantic gesture.
 *
 * <p>Each gesture knows how it should be displayed to the user (for example
 * "Swipe dot 2 upwards" or "Hold dot 1 and swipe dot 4 to the right") and
 * which {@link KeyboardAction} it performs by default. The user can rebind
 * any gesture to any action in the "Customize gestures" and "Customize
 * touch-hold commands" settings screens; the binding is stored in a
 * preference per gesture (see {@link #getPreferenceKey()}) so adding a new
 * gesture only requires a new enum member plus its display data, default
 * action and bit-string value.
 */
public enum Swipe {
    // ------------------------------ dot swipes ------------------------------
    ONE_LEFT(dot(1, Direction.LEFT), KeyboardAction.MOVE_LEFT_CHARACTER),
    ONE_RIGHT(dot(1, Direction.RIGHT), KeyboardAction.MOVE_RIGHT_CHARACTER),
    ONE_UP(dot(1, Direction.UP), KeyboardAction.CYCLE_FEEDBACK),
    ONE_DOWN(dot(1, Direction.DOWN), KeyboardAction.READ_CHARACTER),
    TWO_LEFT(dot(2, Direction.LEFT), KeyboardAction.MOVE_LEFT_WORD),
    TWO_RIGHT(dot(2, Direction.RIGHT), KeyboardAction.MOVE_RIGHT_WORD),
    TWO_UP(dot(2, Direction.UP), KeyboardAction.CYCLE_ECHO),
    TWO_DOWN(dot(2, Direction.DOWN), KeyboardAction.READ_WORD),
    THREE_LEFT(dot(3, Direction.LEFT), KeyboardAction.MOVE_LEFT_LINE),
    THREE_RIGHT(dot(3, Direction.RIGHT), KeyboardAction.MOVE_RIGHT_LINE),
    THREE_UP(dot(3, Direction.UP), KeyboardAction.DOT_7),
    THREE_DOWN(dot(3, Direction.DOWN), KeyboardAction.READ_LINE),
    FOUR_LEFT(dot(4, Direction.LEFT), KeyboardAction.BACKSPACE_CHARACTER),
    FOUR_RIGHT(dot(4, Direction.RIGHT), KeyboardAction.SPACE),
    FOUR_UP(dot(4, Direction.UP), KeyboardAction.NEW_LINE),
    FOUR_DOWN(dot(4, Direction.DOWN), KeyboardAction.TOGGLE_PRIVACY),
    FIVE_LEFT(dot(5, Direction.LEFT), KeyboardAction.BACKSPACE_WORD),
    FIVE_RIGHT(dot(5, Direction.RIGHT), KeyboardAction.SPACE),
    FIVE_UP(dot(5, Direction.UP), KeyboardAction.INPUT_METHOD_PICKER),
    FIVE_DOWN(dot(5, Direction.DOWN), KeyboardAction.OPEN_SETTINGS),
    SIX_LEFT(dot(6, Direction.LEFT), KeyboardAction.BACKSPACE_LINE),
    SIX_RIGHT(dot(6, Direction.RIGHT), KeyboardAction.NEXT_EDIT_ACTION),
    SIX_UP(dot(6, Direction.UP), KeyboardAction.DOT_8),
    SIX_DOWN(dot(6, Direction.DOWN), KeyboardAction.PERFORM_EDIT_ACTION),

    // --------------------------- multi-finger swipes -------------------------
    TWO_FINGERS_DOWN(fingers(2, Direction.DOWN), KeyboardAction.SWITCH_TABLE),
    TWO_FINGERS_UP(fingers(2, Direction.UP), KeyboardAction.NONE),
    TWO_FINGERS_LEFT(fingers(2, Direction.LEFT), KeyboardAction.NONE),
    TWO_FINGERS_RIGHT(fingers(2, Direction.RIGHT), KeyboardAction.NONE),
    THREE_FINGERS_DOWN(fingers(3, Direction.DOWN), KeyboardAction.SUBMIT_TEXT),
    THREE_FINGERS_LEFT(fingers(3, Direction.LEFT),
            KeyboardAction.TOGGLE_COMMAND_MODE),
    THREE_FINGERS_UP(fingers(3, Direction.UP), KeyboardAction.NONE),
    THREE_FINGERS_RIGHT(fingers(3, Direction.RIGHT), KeyboardAction.TOGGLE_EMOJI),

    // ----------------------------- touch-hold --------------------------------
    // Hold dot 1 and swipe one of dots 4, 5 or 6.
    HOLD_1_SWIPE_4_LEFT(hold(1, 4, Direction.LEFT), KeyboardAction.TOGGLE_MARK),
    HOLD_1_SWIPE_4_RIGHT(hold(1, 4, Direction.RIGHT), KeyboardAction.SHRINK_KEYBOARD),
    HOLD_1_SWIPE_4_UP(hold(1, 4, Direction.UP), KeyboardAction.TOGGLE_AUTO_CAPS),
    HOLD_1_SWIPE_4_DOWN(hold(1, 4, Direction.DOWN), KeyboardAction.WORD_COUNT),
    HOLD_1_SWIPE_5_LEFT(hold(1, 5, Direction.LEFT), KeyboardAction.TOGGLE_MARK),
    HOLD_1_SWIPE_5_RIGHT(hold(1, 5, Direction.RIGHT), KeyboardAction.SHRINK_KEYBOARD),
    HOLD_1_SWIPE_5_UP(hold(1, 5, Direction.UP), KeyboardAction.TOGGLE_AUTO_CAPS),
    HOLD_1_SWIPE_5_DOWN(hold(1, 5, Direction.DOWN), KeyboardAction.WORD_COUNT),
    HOLD_1_SWIPE_6_LEFT(hold(1, 6, Direction.LEFT), KeyboardAction.TOGGLE_MARK),
    HOLD_1_SWIPE_6_RIGHT(hold(1, 6, Direction.RIGHT), KeyboardAction.SHRINK_KEYBOARD),
    HOLD_1_SWIPE_6_UP(hold(1, 6, Direction.UP), KeyboardAction.TOGGLE_AUTO_CAPS),
    HOLD_1_SWIPE_6_DOWN(hold(1, 6, Direction.DOWN), KeyboardAction.WORD_COUNT),
    // Hold dot 2 and swipe one of dots 4, 5 or 6.
    HOLD_2_SWIPE_4_LEFT(hold(2, 4, Direction.LEFT), KeyboardAction.NONE),
    HOLD_2_SWIPE_4_RIGHT(hold(2, 4, Direction.RIGHT), KeyboardAction.NONE),
    HOLD_2_SWIPE_4_UP(hold(2, 4, Direction.UP), KeyboardAction.NONE),
    HOLD_2_SWIPE_4_DOWN(hold(2, 4, Direction.DOWN), KeyboardAction.NONE),
    HOLD_2_SWIPE_5_LEFT(hold(2, 5, Direction.LEFT), KeyboardAction.NONE),
    HOLD_2_SWIPE_5_RIGHT(hold(2, 5, Direction.RIGHT), KeyboardAction.NONE),
    HOLD_2_SWIPE_5_UP(hold(2, 5, Direction.UP), KeyboardAction.NONE),
    HOLD_2_SWIPE_5_DOWN(hold(2, 5, Direction.DOWN), KeyboardAction.NONE),
    HOLD_2_SWIPE_6_LEFT(hold(2, 6, Direction.LEFT), KeyboardAction.NONE),
    HOLD_2_SWIPE_6_RIGHT(hold(2, 6, Direction.RIGHT), KeyboardAction.NONE),
    HOLD_2_SWIPE_6_UP(hold(2, 6, Direction.UP), KeyboardAction.NONE),
    HOLD_2_SWIPE_6_DOWN(hold(2, 6, Direction.DOWN), KeyboardAction.NONE),
    // Hold dot 3 and swipe one of dots 4, 5 or 6.
    HOLD_3_SWIPE_4_LEFT(hold(3, 4, Direction.LEFT), KeyboardAction.SWITCH_BRAILLE_TYPE),
    HOLD_3_SWIPE_4_RIGHT(hold(3, 4, Direction.RIGHT),
            KeyboardAction.DELETE_ALL),
    HOLD_3_SWIPE_4_UP(hold(3, 4, Direction.UP), KeyboardAction.VOICE_INPUT),
    HOLD_3_SWIPE_4_DOWN(hold(3, 4, Direction.DOWN), KeyboardAction.SWITCH_TABLE),
    HOLD_3_SWIPE_5_LEFT(hold(3, 5, Direction.LEFT), KeyboardAction.SWITCH_BRAILLE_TYPE),
    HOLD_3_SWIPE_5_RIGHT(hold(3, 5, Direction.RIGHT),
            KeyboardAction.DELETE_ALL),
    HOLD_3_SWIPE_5_UP(hold(3, 5, Direction.UP), KeyboardAction.VOICE_INPUT),
    HOLD_3_SWIPE_5_DOWN(hold(3, 5, Direction.DOWN), KeyboardAction.SWITCH_TABLE),
    HOLD_3_SWIPE_6_LEFT(hold(3, 6, Direction.LEFT), KeyboardAction.SWITCH_BRAILLE_TYPE),
    HOLD_3_SWIPE_6_RIGHT(hold(3, 6, Direction.RIGHT),
            KeyboardAction.DELETE_ALL),
    HOLD_3_SWIPE_6_UP(hold(3, 6, Direction.UP), KeyboardAction.VOICE_INPUT),
    HOLD_3_SWIPE_6_DOWN(hold(3, 6, Direction.DOWN), KeyboardAction.SWITCH_TABLE),
    // Hold dot 4 and swipe one of dots 1, 2 or 3.
    HOLD_4_SWIPE_1_LEFT(hold(4, 1, Direction.LEFT),
            KeyboardAction.SPELL_CHECK_LEFT),
    HOLD_4_SWIPE_1_RIGHT(hold(4, 1, Direction.RIGHT),
            KeyboardAction.SPELL_CHECK_RIGHT),
    HOLD_4_SWIPE_1_UP(hold(4, 1, Direction.UP),
            KeyboardAction.PREVIOUS_SPELL_SUGGESTION),
    HOLD_4_SWIPE_1_DOWN(hold(4, 1, Direction.DOWN),
            KeyboardAction.NEXT_SPELL_SUGGESTION),
    HOLD_4_SWIPE_2_LEFT(hold(4, 2, Direction.LEFT),
            KeyboardAction.SPELL_CHECK_LEFT),
    HOLD_4_SWIPE_2_RIGHT(hold(4, 2, Direction.RIGHT),
            KeyboardAction.SPELL_CHECK_RIGHT),
    HOLD_4_SWIPE_2_UP(hold(4, 2, Direction.UP),
            KeyboardAction.PREVIOUS_SPELL_SUGGESTION),
    HOLD_4_SWIPE_2_DOWN(hold(4, 2, Direction.DOWN),
            KeyboardAction.NEXT_SPELL_SUGGESTION),
    HOLD_4_SWIPE_3_LEFT(hold(4, 3, Direction.LEFT),
            KeyboardAction.SPELL_CHECK_LEFT),
    HOLD_4_SWIPE_3_RIGHT(hold(4, 3, Direction.RIGHT),
            KeyboardAction.SPELL_CHECK_RIGHT),
    HOLD_4_SWIPE_3_UP(hold(4, 3, Direction.UP),
            KeyboardAction.PREVIOUS_SPELL_SUGGESTION),
    HOLD_4_SWIPE_3_DOWN(hold(4, 3, Direction.DOWN),
            KeyboardAction.NEXT_SPELL_SUGGESTION),
    // Hold dot 5 and swipe one of dots 1, 2 or 3.
    HOLD_5_SWIPE_1_LEFT(hold(5, 1, Direction.LEFT), KeyboardAction.NONE),
    HOLD_5_SWIPE_1_RIGHT(hold(5, 1, Direction.RIGHT), KeyboardAction.NONE),
    HOLD_5_SWIPE_1_UP(hold(5, 1, Direction.UP), KeyboardAction.NONE),
    HOLD_5_SWIPE_1_DOWN(hold(5, 1, Direction.DOWN), KeyboardAction.NONE),
    HOLD_5_SWIPE_2_LEFT(hold(5, 2, Direction.LEFT), KeyboardAction.NONE),
    HOLD_5_SWIPE_2_RIGHT(hold(5, 2, Direction.RIGHT), KeyboardAction.NONE),
    HOLD_5_SWIPE_2_UP(hold(5, 2, Direction.UP), KeyboardAction.NONE),
    HOLD_5_SWIPE_2_DOWN(hold(5, 2, Direction.DOWN), KeyboardAction.NONE),
    HOLD_5_SWIPE_3_LEFT(hold(5, 3, Direction.LEFT), KeyboardAction.NONE),
    HOLD_5_SWIPE_3_RIGHT(hold(5, 3, Direction.RIGHT), KeyboardAction.NONE),
    HOLD_5_SWIPE_3_UP(hold(5, 3, Direction.UP), KeyboardAction.NONE),
    HOLD_5_SWIPE_3_DOWN(hold(5, 3, Direction.DOWN), KeyboardAction.NONE),
    // Hold dot 6 and swipe one of dots 1, 2 or 3.
    HOLD_6_SWIPE_1_LEFT(hold(6, 1, Direction.LEFT), KeyboardAction.MOVE_TO_START),
    HOLD_6_SWIPE_1_RIGHT(hold(6, 1, Direction.RIGHT), KeyboardAction.MOVE_TO_END),
    HOLD_6_SWIPE_1_UP(hold(6, 1, Direction.UP), KeyboardAction.READ_ALL),
    HOLD_6_SWIPE_1_DOWN(hold(6, 1, Direction.DOWN),
            KeyboardAction.TOGGLE_PASSWORD_ECHO),
    HOLD_6_SWIPE_2_LEFT(hold(6, 2, Direction.LEFT), KeyboardAction.MOVE_TO_START),
    HOLD_6_SWIPE_2_RIGHT(hold(6, 2, Direction.RIGHT), KeyboardAction.MOVE_TO_END),
    HOLD_6_SWIPE_2_UP(hold(6, 2, Direction.UP), KeyboardAction.READ_ALL),
    HOLD_6_SWIPE_2_DOWN(hold(6, 2, Direction.DOWN),
            KeyboardAction.TOGGLE_PASSWORD_ECHO),
    HOLD_6_SWIPE_3_LEFT(hold(6, 3, Direction.LEFT), KeyboardAction.MOVE_TO_START),
    HOLD_6_SWIPE_3_RIGHT(hold(6, 3, Direction.RIGHT), KeyboardAction.MOVE_TO_END),
    HOLD_6_SWIPE_3_UP(hold(6, 3, Direction.UP), KeyboardAction.READ_ALL),
    HOLD_6_SWIPE_3_DOWN(hold(6, 3, Direction.DOWN),
            KeyboardAction.TOGGLE_PASSWORD_ECHO),

    // ------------------------------- special --------------------------------
    NONE, UNKNOWN;

    /** The direction of a swipe. */
    public enum Direction {
        UP, DOWN, LEFT, RIGHT;
    }

    private enum Kind {
        DOT_SWIPE, MULTI_FINGER, HOLD, SPECIAL;
    }

    /** The shape of a gesture: which dots, how many fingers, which way. */
    private static final class Shape {
        final Kind kind;
        final int dot;
        final int fingers;
        final int holdDot;
        final int swipeDot;
        final Direction direction;

        Shape(Kind kind, int dot, int fingers, int holdDot, int swipeDot,
                Direction direction) {
            this.kind = kind;
            this.dot = dot;
            this.fingers = fingers;
            this.holdDot = holdDot;
            this.swipeDot = swipeDot;
            this.direction = direction;
        }
    }

    private static Shape dot(int dot, Direction direction) {
        return new Shape(Kind.DOT_SWIPE, dot, 0, 0, 0, direction);
    }

    private static Shape fingers(int fingers, Direction direction) {
        return new Shape(Kind.MULTI_FINGER, 0, fingers, 0, 0, direction);
    }

    private static Shape hold(int holdDot, int swipeDot, Direction direction) {
        return new Shape(Kind.HOLD, 0, 0, holdDot, swipeDot, direction);
    }

    private final Shape shape;
    private final KeyboardAction defaultAction;

    Swipe(Shape shape, KeyboardAction defaultAction) {
        this.shape = shape;
        this.defaultAction = defaultAction;
    }

    Swipe() {
        this(new Shape(Kind.SPECIAL, 0, 0, 0, 0, null), KeyboardAction.NONE);
    }

    /** Whether this gesture appears in the customization screens. */
    public boolean isConfigurable() {
        return shape.kind != Kind.SPECIAL;
    }

    /** Whether this is a touch-hold command (hold a dot, swipe another). */
    public boolean isTouchHold() {
        return shape.kind == Kind.HOLD;
    }

    /** Whether this is a multi-finger gesture (swipe with two or more
     *  fingers, not bound to any particular dot). */
    public boolean isMultiFinger() {
        return shape.kind == Kind.MULTI_FINGER;
    }

    /** The preference key storing the user's binding for this gesture. */
    public String getPreferenceKey() {
        return "GESTURE_" + name();
    }

    /** The action currently bound to this gesture. */
    public KeyboardAction getBoundAction(Context context) {
        SharedPreferences prefs = Options.getSharedPreferences(context);
        String stored = prefs.getString(getPreferenceKey(), null);
        for (KeyboardAction action : KeyboardAction.values()) {
            if (action.name().equals(stored)) {
                return action;
            }
        }
        return defaultAction;
    }

    /** Bind this gesture to an action. */
    public void setBoundAction(Context context, KeyboardAction action) {
        Options.getSharedPreferences(context).edit()
                .putString(getPreferenceKey(), action.name()).apply();
    }

    /** How this gesture is displayed to the user, e.g. "Swipe dot 2 upwards". */
    public String getDisplayTitle(Context context) {
        switch (shape.kind) {
        case DOT_SWIPE:
            return context.getString(R.string.gesture_dot_swipe_title,
                    shape.dot,
                    context.getString(directionWordResource(shape.direction,
                            false)));
        case MULTI_FINGER:
            return context.getString(R.string.gesture_fingers_title,
                    context.getString(directionWordResource(shape.direction,
                            true)), shape.fingers);
        case HOLD:
            return context.getString(R.string.gesture_hold_title,
                    shape.holdDot, shape.swipeDot,
                    context.getString(directionWordResource(shape.direction,
                            false)));
        default:
            return name();
        }
    }

    // "upwards"/"downwards"/"to the left"/"to the right" for dot swipes and
    // "up"/"down"/"left"/"right" for finger counts.
    private static int directionWordResource(Direction direction,
            boolean shortForm) {
        switch (direction) {
        case UP:
            return shortForm ? R.string.direction_up
                    : R.string.direction_upwards;
        case DOWN:
            return shortForm ? R.string.direction_down
                    : R.string.direction_downwards;
        case LEFT:
            return shortForm ? R.string.direction_left
                    : R.string.direction_to_the_left;
        case RIGHT:
            return shortForm ? R.string.direction_right
                    : R.string.direction_to_the_right;
        }
        return 0;
    }

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
        final int VALUE_HOLD_TWO_FOUR_LEFT = 568;
        final int VALUE_HOLD_TWO_FOUR_RIGHT = 1080;
        final int VALUE_HOLD_TWO_FOUR_DOWN = 1592;
        final int VALUE_HOLD_TWO_FOUR_UP = 2104;
        final int VALUE_HOLD_TWO_FIVE_LEFT = 4152;
        final int VALUE_HOLD_TWO_FIVE_RIGHT = 8248;
        final int VALUE_HOLD_TWO_FIVE_DOWN = 12344;
        final int VALUE_HOLD_TWO_FIVE_UP = 16440;
        final int VALUE_HOLD_TWO_SIX_LEFT = 32824;
        final int VALUE_HOLD_TWO_SIX_RIGHT = 65592;
        final int VALUE_HOLD_TWO_SIX_DOWN = 98360;
        final int VALUE_HOLD_TWO_SIX_UP = 131128;
        final int VALUE_HOLD_FIVE_ONE_LEFT = 28673;
        final int VALUE_HOLD_FIVE_ONE_RIGHT = 28674;
        final int VALUE_HOLD_FIVE_ONE_DOWN = 28675;
        final int VALUE_HOLD_FIVE_ONE_UP = 28676;
        final int VALUE_HOLD_FIVE_TWO_LEFT = 28680;
        final int VALUE_HOLD_FIVE_TWO_RIGHT = 28688;
        final int VALUE_HOLD_FIVE_TWO_DOWN = 28696;
        final int VALUE_HOLD_FIVE_TWO_UP = 28704;
        final int VALUE_HOLD_FIVE_THREE_LEFT = 28736;
        final int VALUE_HOLD_FIVE_THREE_RIGHT = 28800;
        final int VALUE_HOLD_FIVE_THREE_DOWN = 28864;
        final int VALUE_HOLD_FIVE_THREE_UP = 28928;
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
        case VALUE_FIVE_DOWN:
            return FIVE_DOWN;
        case VALUE_FIVE_UP:
            return FIVE_UP;
        case VALUE_SIX_LEFT:
            return SIX_LEFT;
        case VALUE_SIX_RIGHT:
            return SIX_RIGHT;
        case VALUE_SIX_DOWN:
            return SIX_DOWN;
        case VALUE_SIX_UP:
            return SIX_UP;
        case VALUE_HOLD_SIX_ONE_RIGHT:
            return HOLD_6_SWIPE_1_RIGHT;
        case VALUE_HOLD_SIX_TWO_RIGHT:
            return HOLD_6_SWIPE_2_RIGHT;
        case VALUE_HOLD_SIX_THREE_RIGHT:
            return HOLD_6_SWIPE_3_RIGHT;
        case VALUE_HOLD_SIX_ONE_LEFT:
            return HOLD_6_SWIPE_1_LEFT;
        case VALUE_HOLD_SIX_TWO_LEFT:
            return HOLD_6_SWIPE_2_LEFT;
        case VALUE_HOLD_SIX_THREE_LEFT:
            return HOLD_6_SWIPE_3_LEFT;
        case VALUE_HOLD_SIX_ONE_DOWN:
            return HOLD_6_SWIPE_1_DOWN;
        case VALUE_HOLD_SIX_TWO_DOWN:
            return HOLD_6_SWIPE_2_DOWN;
        case VALUE_HOLD_SIX_THREE_DOWN:
            return HOLD_6_SWIPE_3_DOWN;
        case VALUE_HOLD_SIX_ONE_UP:
            return HOLD_6_SWIPE_1_UP;
        case VALUE_HOLD_SIX_TWO_UP:
            return HOLD_6_SWIPE_2_UP;
        case VALUE_HOLD_SIX_THREE_UP:
            return HOLD_6_SWIPE_3_UP;
        case VALUE_HOLD_THREE_SIX_LEFT:
            return HOLD_3_SWIPE_6_LEFT;
        case VALUE_HOLD_THREE_FIVE_LEFT:
            return HOLD_3_SWIPE_5_LEFT;
        case VALUE_HOLD_THREE_FOUR_LEFT:
            return HOLD_3_SWIPE_4_LEFT;
        case VALUE_HOLD_THREE_SIX_RIGHT:
            return HOLD_3_SWIPE_6_RIGHT;
        case VALUE_HOLD_THREE_FIVE_RIGHT:
            return HOLD_3_SWIPE_5_RIGHT;
        case VALUE_HOLD_THREE_FOUR_RIGHT:
            return HOLD_3_SWIPE_4_RIGHT;
        case VALUE_HOLD_THREE_SIX_UP:
            return HOLD_3_SWIPE_6_UP;
        case VALUE_HOLD_THREE_FIVE_UP:
            return HOLD_3_SWIPE_5_UP;
        case VALUE_HOLD_THREE_FOUR_UP:
            return HOLD_3_SWIPE_4_UP;
        case VALUE_HOLD_THREE_SIX_DOWN:
            return HOLD_3_SWIPE_6_DOWN;
        case VALUE_HOLD_THREE_FIVE_DOWN:
            return HOLD_3_SWIPE_5_DOWN;
        case VALUE_HOLD_THREE_FOUR_DOWN:
            return HOLD_3_SWIPE_4_DOWN;
        case VALUE_HOLD_ONE_SIX_DOWN:
            return HOLD_1_SWIPE_6_DOWN;
        case VALUE_HOLD_ONE_FOUR_DOWN:
            return HOLD_1_SWIPE_4_DOWN;
        case VALUE_HOLD_ONE_FIVE_DOWN:
            return HOLD_1_SWIPE_5_DOWN;
        case VALUE_HOLD_ONE_SIX_UP:
            return HOLD_1_SWIPE_6_UP;
        case VALUE_HOLD_ONE_FIVE_UP:
            return HOLD_1_SWIPE_5_UP;
        case VALUE_HOLD_ONE_FOUR_UP:
            return HOLD_1_SWIPE_4_UP;
        case VALUE_HOLD_ONE_SIX_RIGHT:
            return HOLD_1_SWIPE_6_RIGHT;
        case VALUE_HOLD_ONE_FIVE_RIGHT:
            return HOLD_1_SWIPE_5_RIGHT;
        case VALUE_HOLD_ONE_FOUR_RIGHT:
            return HOLD_1_SWIPE_4_RIGHT;
        case VALUE_HOLD_ONE_SIX_LEFT:
            return HOLD_1_SWIPE_6_LEFT;
        case VALUE_HOLD_ONE_FIVE_LEFT:
            return HOLD_1_SWIPE_5_LEFT;
        case VALUE_HOLD_ONE_FOUR_LEFT:
            return HOLD_1_SWIPE_4_LEFT;
        case VALUE_HOLD_FOUR_ONE_LEFT:
            return HOLD_4_SWIPE_1_LEFT;
        case VALUE_HOLD_FOUR_TWO_LEFT:
            return HOLD_4_SWIPE_2_LEFT;
        case VALUE_HOLD_FOUR_THREE_LEFT:
            return HOLD_4_SWIPE_3_LEFT;
        case VALUE_HOLD_FOUR_ONE_RIGHT:
            return HOLD_4_SWIPE_1_RIGHT;
        case VALUE_HOLD_FOUR_TWO_RIGHT:
            return HOLD_4_SWIPE_2_RIGHT;
        case VALUE_HOLD_FOUR_THREE_RIGHT:
            return HOLD_4_SWIPE_3_RIGHT;
        case VALUE_HOLD_FOUR_ONE_DOWN:
            return HOLD_4_SWIPE_1_DOWN;
        case VALUE_HOLD_FOUR_TWO_DOWN:
            return HOLD_4_SWIPE_2_DOWN;
        case VALUE_HOLD_FOUR_THREE_DOWN:
            return HOLD_4_SWIPE_3_DOWN;
        case VALUE_HOLD_FOUR_ONE_UP:
            return HOLD_4_SWIPE_1_UP;
        case VALUE_HOLD_FOUR_TWO_UP:
            return HOLD_4_SWIPE_2_UP;
        case VALUE_HOLD_FOUR_THREE_UP:
            return HOLD_4_SWIPE_3_UP;
        case VALUE_HOLD_TWO_FOUR_LEFT:
            return HOLD_2_SWIPE_4_LEFT;
        case VALUE_HOLD_TWO_FOUR_RIGHT:
            return HOLD_2_SWIPE_4_RIGHT;
        case VALUE_HOLD_TWO_FOUR_DOWN:
            return HOLD_2_SWIPE_4_DOWN;
        case VALUE_HOLD_TWO_FOUR_UP:
            return HOLD_2_SWIPE_4_UP;
        case VALUE_HOLD_TWO_FIVE_LEFT:
            return HOLD_2_SWIPE_5_LEFT;
        case VALUE_HOLD_TWO_FIVE_RIGHT:
            return HOLD_2_SWIPE_5_RIGHT;
        case VALUE_HOLD_TWO_FIVE_DOWN:
            return HOLD_2_SWIPE_5_DOWN;
        case VALUE_HOLD_TWO_FIVE_UP:
            return HOLD_2_SWIPE_5_UP;
        case VALUE_HOLD_TWO_SIX_LEFT:
            return HOLD_2_SWIPE_6_LEFT;
        case VALUE_HOLD_TWO_SIX_RIGHT:
            return HOLD_2_SWIPE_6_RIGHT;
        case VALUE_HOLD_TWO_SIX_DOWN:
            return HOLD_2_SWIPE_6_DOWN;
        case VALUE_HOLD_TWO_SIX_UP:
            return HOLD_2_SWIPE_6_UP;
        case VALUE_HOLD_FIVE_ONE_LEFT:
            return HOLD_5_SWIPE_1_LEFT;
        case VALUE_HOLD_FIVE_ONE_RIGHT:
            return HOLD_5_SWIPE_1_RIGHT;
        case VALUE_HOLD_FIVE_ONE_DOWN:
            return HOLD_5_SWIPE_1_DOWN;
        case VALUE_HOLD_FIVE_ONE_UP:
            return HOLD_5_SWIPE_1_UP;
        case VALUE_HOLD_FIVE_TWO_LEFT:
            return HOLD_5_SWIPE_2_LEFT;
        case VALUE_HOLD_FIVE_TWO_RIGHT:
            return HOLD_5_SWIPE_2_RIGHT;
        case VALUE_HOLD_FIVE_TWO_DOWN:
            return HOLD_5_SWIPE_2_DOWN;
        case VALUE_HOLD_FIVE_TWO_UP:
            return HOLD_5_SWIPE_2_UP;
        case VALUE_HOLD_FIVE_THREE_LEFT:
            return HOLD_5_SWIPE_3_LEFT;
        case VALUE_HOLD_FIVE_THREE_RIGHT:
            return HOLD_5_SWIPE_3_RIGHT;
        case VALUE_HOLD_FIVE_THREE_DOWN:
            return HOLD_5_SWIPE_3_DOWN;
        case VALUE_HOLD_FIVE_THREE_UP:
            return HOLD_5_SWIPE_3_UP;
        default:
            return UNKNOWN;
        }
    }
}
