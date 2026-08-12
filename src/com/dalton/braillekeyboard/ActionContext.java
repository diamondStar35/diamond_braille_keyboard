package com.dalton.braillekeyboard;

import java.util.Locale;

import android.content.Context;

/**
 * The services a {@link KeyboardAction} needs in order to perform itself:
 * the text editing controller, the keyboard (IME) listener, and the ways an
 * action can report its result to the user (speech, feedback events, dots 7
 * and 8) or change keyboard state.
 *
 * <p>{@link ActionHandler} implements this interface, so each action's
 * {@link KeyboardAction#perform(ActionContext, boolean)} implementation can
 * stay declarative: it decides what to do and lets this context deliver the
 * effects. This keeps the action vocabulary free of any input-method
 * plumbing.
 */
public interface ActionContext {
    /** The application context, for resources and preferences. */
    Context context();

    /** The text editing operations the action can perform. */
    EditingController editing();

    /** The keyboard listener, for IME level operations. */
    KeyboardListener listener();

    /**
     * Speak a message to the user.
     *
     * @param message The message, or {@code null} to speak nothing.
     * @param considerPassword Whether the message must obey password echo
     *            rules (e.g. never speak the content of a password field).
     */
    void speak(String message, boolean considerPassword);

    /** Speak a string resource (never password protected). */
    void speak(int stringRes);

    /** Emit a sound/haptic feedback event for this action. */
    void notify(FeedbackEvent event);

    /** Set the pressed state of dots 7 and 8. */
    void setDots(boolean dot7, boolean dot8);

    /** The sound theme or other feedback settings changed. */
    void feedbackSettingsChanged();

    /** The locale changed (e.g. after switching braille tables). */
    void setLocale(Locale locale);

    /** Shrink the keyboard to its small bar form. */
    void shrinkKeyboard();

    /** The privacy screen state changed. */
    void privacyChanged();

    /** Show the system input method picker. */
    void showInputMethodPicker();

    /** Open the keyboard settings screen. */
    void openSettings();

    /** Start (or ask permission for) voice dictation. */
    void voiceInput(boolean fastDoubleSwipe);
}
