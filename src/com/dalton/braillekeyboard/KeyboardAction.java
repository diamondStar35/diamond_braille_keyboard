package com.dalton.braillekeyboard;

import java.util.Locale;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.view.inputmethod.ExtractedText;

import com.dalton.braillekeyboard.EditingController.Granularity;
import com.dalton.braillekeyboard.Options.KeyboardEcho;

/**
 * The actions a gesture or touch-hold command can perform.
 *
 * <p>Each action knows how to perform itself through an
 * {@link ActionContext} (implemented by {@link ActionHandler}), so adding a
 * new action only requires a new enum member, its two display strings and
 * its {@link #perform(ActionContext, boolean)} implementation. The action
 * itself has no knowledge of which gesture triggers it: the gesture to
 * action mapping lives in the gesture's preference (see
 * {@link Swipe#getBoundAction(Context)}).
 *
 * <p>The fast double swipe flag is passed in so that actions can offer a
 * "confirm or extend" behaviour, exactly as the original keyboard did (for
 * example, deleting a whole line or opening the settings screen requires
 * swiping twice quickly).
 */
public enum KeyboardAction {
    NONE(R.string.action_none_title, R.string.action_none_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            // Deliberately does nothing; the gesture is disabled.
        }
    },
    MOVE_LEFT_CHARACTER(R.string.action_move_left_character_title,
            R.string.action_move_left_character_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().moveLeft(ctx.context(), Granularity.CHARACTER);
        }
    },
    MOVE_RIGHT_CHARACTER(R.string.action_move_right_character_title,
            R.string.action_move_right_character_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().moveRight(ctx.context(), Granularity.CHARACTER);
        }
    },
    MOVE_LEFT_WORD(R.string.action_move_left_word_title,
            R.string.action_move_left_word_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().moveLeft(ctx.context(), Granularity.WORD);
        }
    },
    MOVE_RIGHT_WORD(R.string.action_move_right_word_title,
            R.string.action_move_right_word_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().moveRight(ctx.context(), Granularity.WORD);
        }
    },
    MOVE_LEFT_LINE(R.string.action_move_left_line_title,
            R.string.action_move_left_line_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().moveLeft(ctx.context(), Granularity.LINE);
        }
    },
    MOVE_RIGHT_LINE(R.string.action_move_right_line_title,
            R.string.action_move_right_line_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().moveRight(ctx.context(), Granularity.LINE);
        }
    },
    MOVE_TO_START(R.string.action_move_to_start_title,
            R.string.action_move_to_start_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.DELETE);
            ctx.editing().moveLeft(ctx.context(), Granularity.ALL);
        }
    },
    MOVE_TO_END(R.string.action_move_to_end_title,
            R.string.action_move_to_end_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().moveRight(ctx.context(), Granularity.ALL);
        }
    },
    READ_CHARACTER(R.string.action_read_character_title,
            R.string.action_read_character_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.speak(ctx.editing().getInput(Granularity.CHARACTER), true);
        }
    },
    READ_WORD(R.string.action_read_word_title,
            R.string.action_read_word_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.speak(ctx.editing().getInput(Granularity.WORD), true);
        }
    },
    READ_LINE(R.string.action_read_line_title,
            R.string.action_read_line_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.speak(ctx.editing().getInput(Granularity.LINE), true);
        }
    },
    READ_ALL(R.string.action_read_all_title,
            R.string.action_read_all_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.speak(ctx.editing().getInput(Granularity.ALL), true);
        }
    },
    CYCLE_ECHO(R.string.action_cycle_echo_title,
            R.string.action_cycle_echo_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            Context context = ctx.context();
            KeyboardEcho echo = KeyboardEcho.valueOf(Integer.parseInt(Options
                    .getStringPreference(context,
                            R.string.pref_echo_feedback_key,
                            KeyboardEcho.CHARACTER.getValue())));
            echo = KeyboardEcho.next(echo);
            Options.writeStringPreference(context,
                    R.string.pref_echo_feedback_key, echo.getValue());
            ctx.notify(FeedbackEvent.COMMAND);
            ctx.speak(echo.resource);
        }
    },
    CYCLE_FEEDBACK(R.string.action_cycle_feedback_title,
            R.string.action_cycle_feedback_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            Context context = ctx.context();
            boolean soundOn = Options.getBooleanPreference(context,
                    R.string.pref_sound_feedback_key, true);
            boolean hapticOn = Options.getBooleanPreference(context,
                    R.string.pref_haptic_feedback_key, true);
            int message;
            if (soundOn && hapticOn) {
                // Both on -> turn both off
                Options.writeBooleanPreference(context,
                        R.string.pref_sound_feedback_key, false);
                Options.writeBooleanPreference(context,
                        R.string.pref_haptic_feedback_key, false);
                message = R.string.keyboard_feedback_none;
            } else if (!soundOn && !hapticOn) {
                // Both off -> vibrate only
                Options.writeBooleanPreference(context,
                        R.string.pref_haptic_feedback_key, true);
                message = R.string.keyboard_feedback_vibrate;
            } else if (hapticOn) {
                // Vibrate only -> sound only
                Options.writeBooleanPreference(context,
                        R.string.pref_haptic_feedback_key, false);
                Options.writeBooleanPreference(context,
                        R.string.pref_sound_feedback_key, true);
                message = R.string.keyboard_feedback_sound;
            } else {
                // Sound only -> both on
                Options.writeBooleanPreference(context,
                        R.string.pref_haptic_feedback_key, true);
                message = R.string.keyboard_feedback_all;
            }
            ctx.notify(FeedbackEvent.COMMAND);
            ctx.speak(message);
            ctx.feedbackSettingsChanged();
        }
    },
    TOGGLE_PASSWORD_ECHO(R.string.action_toggle_password_echo_title,
            R.string.action_toggle_password_echo_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            Context context = ctx.context();
            ctx.notify(FeedbackEvent.COMMAND);
            boolean echoPassword = Options.switchBooleanPreference(context,
                    R.string.pref_echo_passwords_key, false);
            ctx.speak(echoPassword ? R.string.speak_passwords
                    : R.string.no_password_echo);
        }
    },
    TOGGLE_PRIVACY(R.string.action_toggle_privacy_title,
            R.string.action_toggle_privacy_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            Context context = ctx.context();
            Options.switchBooleanPreference(context, R.string.pref_privacy_key,
                    Boolean.parseBoolean(context
                            .getString(R.string.pref_privacy_default)));
            ctx.privacyChanged();
            boolean enabled = Options.getBooleanPreference(context,
                    R.string.pref_privacy_key,
                    Boolean.parseBoolean(context
                            .getString(R.string.pref_privacy_default)));
            ctx.notify(FeedbackEvent.COMMAND);
            ctx.speak(enabled ? R.string.privacy_enabled
                    : R.string.privacy_disabled);
        }
    },
    TOGGLE_AUTO_CAPS(R.string.action_toggle_auto_caps_title,
            R.string.action_toggle_auto_caps_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            Context context = ctx.context();
            Options.switchBooleanPreference(context,
                    R.string.pref_auto_caps_key,
                    Boolean.parseBoolean(context
                            .getString(R.string.pref_auto_caps_default)));
            boolean enabled = Options.getBooleanPreference(context,
                    R.string.pref_auto_caps_key,
                    Boolean.parseBoolean(context
                            .getString(R.string.pref_auto_caps_default)));
            ctx.notify(FeedbackEvent.COMMAND);
            ctx.speak(enabled ? R.string.auto_caps_enabled
                    : R.string.auto_caps_disabled);
        }
    },
    TOGGLE_MARK(R.string.action_toggle_mark_title,
            R.string.action_toggle_mark_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            Context context = ctx.context();
            ctx.notify(FeedbackEvent.TYPE);
            ctx.speak(ctx.listener().toggleMark() ? R.string.set_mark
                    : R.string.unset_mark);
        }
    },
    BACKSPACE_CHARACTER(R.string.action_backspace_character_title,
            R.string.action_backspace_character_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.DELETE);
            ctx.editing().backspace(ctx.context(), Granularity.CHARACTER,
                    fastDoubleSwipe);
        }
    },
    BACKSPACE_WORD(R.string.action_backspace_word_title,
            R.string.action_backspace_word_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.DELETE);
            ctx.editing().backspace(ctx.context(), Granularity.WORD,
                    fastDoubleSwipe);
        }
    },
    BACKSPACE_LINE(R.string.action_backspace_line_title,
            R.string.action_backspace_line_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.DELETE);
            ctx.editing().backspace(ctx.context(), Granularity.LINE,
                    fastDoubleSwipe);
        }
    },
    DELETE_ALL(R.string.action_delete_all_title,
            R.string.action_delete_all_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.DELETE);
            ctx.editing().backspace(ctx.context(), Granularity.ALL,
                    fastDoubleSwipe);
        }
    },
    SPACE(R.string.action_space_title, R.string.action_space_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            if (fastDoubleSwipe
                    && ctx.editing().handleDoubleSpace(ctx.context())) {
                return;
            }
            ctx.editing().typeCharacter(ctx.context(), (int) ' ', " ");
        }
    },
    NEW_LINE(R.string.action_new_line_title,
            R.string.action_new_line_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.NEW_LINE);
            ctx.editing().typeCharacter(ctx.context(), Keyboard.KEYCODE_DONE,
                    ctx.context().getString(R.string.newline));
        }
    },
    DOT_7(R.string.action_dot7_title, R.string.action_dot7_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            if (ctx.listener().getDots() == 8) {
                ctx.setDots(true, false);
            } else {
                ctx.speak(R.string.unknown_character);
            }
        }
    },
    DOT_8(R.string.action_dot8_title, R.string.action_dot8_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            if (ctx.listener().getDots() == 8) {
                ctx.setDots(false, true);
            } else {
                ctx.speak(R.string.unknown_character);
            }
        }
    },
    SUBMIT_TEXT(R.string.action_submit_text_title,
            R.string.action_submit_text_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            if (!ctx.listener().submitText()) {
                // Submit fell back to inserting a newline.
                ctx.notify(FeedbackEvent.NEW_LINE);
                ctx.speak(R.string.newline);
            } else {
                ctx.notify(FeedbackEvent.TYPE);
            }
        }
    },
    CLOSE_KEYBOARD(R.string.action_close_keyboard_title,
            R.string.action_close_keyboard_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            // Closing is a special case: no typing/command feedback is
            // emitted here. View.close() fires the keyboard-closed feedback
            // event and its speech announcement, so the user only hears and
            // feels the close event itself.
            ctx.listener().closeKeyboard();
        }
    },
    SWITCH_TABLE(R.string.action_switch_table_title,
            R.string.action_switch_table_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            Context context = ctx.context();
            ctx.notify(FeedbackEvent.COMMAND);
            String message = ctx.listener().switchTable();
            ctx.speak(message == null ? context
                    .getString(R.string.no_braille_table) : message, false);
            ctx.setLocale(ctx.listener().getLocale());
        }
    },
    SWITCH_BRAILLE_TYPE(R.string.action_switch_braille_type_title,
            R.string.action_switch_braille_type_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            Context context = ctx.context();
            ctx.notify(FeedbackEvent.COMMAND);
            int brailleType = ctx.listener().switchBrailleType();
            ctx.speak(brailleType == 8 ? R.string.grade_computer
                    : R.string.grade_literary);
            ctx.setLocale(ctx.listener().getLocale());
        }
    },
    TOGGLE_EMOJI(R.string.action_toggle_emoji_title,
            R.string.action_toggle_emoji_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.COMMAND);
            ctx.listener().toggleEmojiMode();
        }
    },
    TOGGLE_COMMAND_MODE(R.string.action_toggle_command_mode_title,
            R.string.action_toggle_command_mode_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.COMMAND);
            ctx.listener().toggleCommandMode();
        }
    },
    NEXT_EDIT_ACTION(R.string.action_next_edit_action_title,
            R.string.action_next_edit_action_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().nextAction(ctx.context());
        }
    },
    PERFORM_EDIT_ACTION(R.string.action_perform_edit_action_title,
            R.string.action_perform_edit_action_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().selectAction(ctx.context());
        }
    },
    INPUT_METHOD_PICKER(R.string.action_input_method_picker_title,
            R.string.action_input_method_picker_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.COMMAND);
            if (fastDoubleSwipe) {
                ctx.speak(R.string.show_input_switcher);
                ctx.showInputMethodPicker();
            } else {
                ctx.speak(R.string.swipe_confirm_input);
            }
        }
    },
    OPEN_SETTINGS(R.string.action_open_settings_title,
            R.string.action_open_settings_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.COMMAND);
            if (fastDoubleSwipe) {
                ctx.setLocale(Locale.getDefault());
                ctx.speak(R.string.show_settings);
                ctx.openSettings();
            } else {
                ctx.speak(R.string.swipe_confirm_settings);
            }
        }
    },
    VOICE_INPUT(R.string.action_voice_input_title,
            R.string.action_voice_input_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.voiceInput(fastDoubleSwipe);
        }
    },
    WORD_COUNT(R.string.action_word_count_title,
            R.string.action_word_count_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            Context context = ctx.context();
            ctx.notify(FeedbackEvent.TYPE);
            // The input connection (and therefore the text) may be gone.
            ExtractedText extracted = ctx.listener().getAllText();
            CharSequence text = extracted != null ? extracted.text : null;
            if (text != null) {
                ctx.speak(String.format(
                        context.getString(R.string.word_count),
                        EditingUtilities.lineCount(text),
                        EditingUtilities.wordCount(text),
                        EditingUtilities.characterCount(text)), false);
            }
        }
    },
    SPELL_CHECK_LEFT(R.string.action_spell_check_left_title,
            R.string.action_spell_check_left_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().doSpellCheck(ctx.context(),
                    SpellChecker.Direction.LEFT, 0, ctx.listener().getCursor());
        }
    },
    SPELL_CHECK_RIGHT(R.string.action_spell_check_right_title,
            R.string.action_spell_check_right_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().doSpellCheck(ctx.context(),
                    SpellChecker.Direction.RIGHT, 0, ctx.listener().getCursor());
        }
    },
    NEXT_SPELL_SUGGESTION(R.string.action_next_spell_suggestion_title,
            R.string.action_next_spell_suggestion_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().nextSpellCheckSuggestion(ctx.context());
        }
    },
    PREVIOUS_SPELL_SUGGESTION(
            R.string.action_previous_spell_suggestion_title,
            R.string.action_previous_spell_suggestion_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.TYPE);
            ctx.editing().previousSpellCheckSuggestion(ctx.context());
        }
    },
    SHRINK_KEYBOARD(R.string.action_shrink_keyboard_title,
            R.string.action_shrink_keyboard_summary) {
        @Override
        public void perform(ActionContext ctx, boolean fastDoubleSwipe) {
            ctx.notify(FeedbackEvent.COMMAND);
            ctx.speak(R.string.keyboard_shrink);
            ctx.shrinkKeyboard();
        }
    };

    /** The title shown in the action picker and as the gesture summary. */
    public final int titleResource;

    /** The description shown in the action picker. */
    public final int summaryResource;

    KeyboardAction(int titleResource, int summaryResource) {
        this.titleResource = titleResource;
        this.summaryResource = summaryResource;
    }

    /** Perform this action. See {@link ActionContext}. */
    public abstract void perform(ActionContext ctx, boolean fastDoubleSwipe);

    /** The display title of this action. */
    public String getTitle(Context context) {
        return context.getString(titleResource);
    }

    /** The description of this action. */
    public String getSummary(Context context) {
        return context.getString(summaryResource);
    }
}
