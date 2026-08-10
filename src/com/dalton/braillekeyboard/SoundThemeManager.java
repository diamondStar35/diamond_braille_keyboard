package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the {@link SoundPool} and loads/plays the sounds of the active sound
 * theme.
 *
 * <p>The active theme is resolved from the {@code SOUND_THEME} preference by
 * its stable id (see {@link SoundTheme}). {@link #reloadTheme()} swaps the
 * loaded sounds whenever the preference changes (or the keyboard opens) and
 * skips the work when the theme has not actually changed.
 *
 * <p>SoundPool loads asynchronously, so an event that fires while its sound
 * is still loading is queued and played as soon as that sample becomes
 * available.
 */
public class SoundThemeManager {
    private static final String TAG = "SoundThemeManager";
    private static final int MAX_STREAMS = 5;

    private final Context context;
    private final Map<FeedbackEvent, Integer> soundIds = new HashMap<>();
    private final Set<Integer> loadedSounds = new HashSet<>();
    private final List<FeedbackEvent> pendingEvents = new ArrayList<>();

    private SoundPool soundPool;
    private SoundTheme activeTheme;

    public SoundThemeManager(Context context) {
        this.context = context.getApplicationContext();
        soundPool = createSoundPool();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool pool, int sampleId,
                    int status) {
                if (status == 0) {
                    loadedSounds.add(sampleId);
                    playPendingSoundsFor(sampleId);
                } else {
                    // The sample failed to load; drop events queued for it so
                    // they are not retried forever.
                    dropPendingSoundsFor(sampleId);
                }
            }
        });
    }

    private static SoundPool createSoundPool() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        return new SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attributes)
                .build();
    }

    /**
     * (Re)load the theme selected in the preferences. The "Off" id (or an
     * unresolvable id) silences all sound events.
     */
    public void reloadTheme() {
        String stored = Options.getStringPreference(context,
                R.string.pref_sound_theme_key,
                context.getString(R.string.pref_sound_theme_default));
        SoundTheme theme = SoundTheme.ID_OFF.equalsIgnoreCase(stored) ? null
                : SoundTheme.loadById(context, stored);
        // Avoid unloading and reloading a theme that is already active; this
        // is the common case when the keyboard opens.
        if (activeTheme != null && theme != null
                && activeTheme.id.equalsIgnoreCase(theme.id)) {
            return;
        }
        setActiveTheme(theme);
    }

    private void setActiveTheme(SoundTheme theme) {
        unloadAll();
        activeTheme = theme;
        if (theme == null) {
            return;
        }
        AssetManager assets = context.getAssets();
        String themePath = "sounds/" + theme.folderName;
        for (FeedbackEvent event : FeedbackEvent.values()) {
            String file = theme.getSound(event);
            if (file == null || SoundTheme.SOUND_NONE.equalsIgnoreCase(file)
                    || SoundTheme.SOUND_SYSTEM.equalsIgnoreCase(file)) {
                continue;
            }
            try {
                int soundId = soundPool.load(
                        assets.openFd(themePath + "/" + file), 1);
                soundIds.put(event, soundId);
            } catch (IOException e) {
                Log.e(TAG, "Failed to load sound " + file + " for " + event, e);
            }
        }
    }

    private void unloadAll() {
        for (Integer soundId : soundIds.values()) {
            soundPool.unload(soundId);
        }
        soundIds.clear();
        loadedSounds.clear();
        pendingEvents.clear();
    }

    /**
     * Play the sound configured for the event in the active theme. Does
     * nothing when there is no active theme, the event is mapped to
     * {@link SoundTheme#SOUND_NONE} or the sound has not been configured.
     */
    public void playEvent(FeedbackEvent event) {
        if (activeTheme == null) {
            return;
        }
        String sound = activeTheme.getSound(event);
        if (sound == null || SoundTheme.SOUND_NONE.equalsIgnoreCase(sound)) {
            return;
        }
        if (SoundTheme.SOUND_SYSTEM.equalsIgnoreCase(sound)) {
            AudioManager audioManager = (AudioManager) context
                    .getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 1.0f);
            }
            return;
        }
        Integer soundId = soundIds.get(event);
        if (soundId != null) {
            playOrQueue(event, soundId);
        }
    }

    private void playOrQueue(FeedbackEvent event, int soundId) {
        if (soundPool == null) {
            return;
        }
        if (loadedSounds.contains(soundId)) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } else if (!pendingEvents.contains(event)) {
            pendingEvents.add(event);
        }
    }

    // Drop queued events whose sound failed to load.
    private void dropPendingSoundsFor(int sampleId) {
        Iterator<FeedbackEvent> iterator = pendingEvents.iterator();
        while (iterator.hasNext()) {
            FeedbackEvent event = iterator.next();
            Integer soundId = soundIds.get(event);
            if (soundId != null && soundId == sampleId) {
                iterator.remove();
            }
        }
    }

    // Play any queued events whose sound has just become available.
    private void playPendingSoundsFor(int sampleId) {
        if (soundPool == null) {
            return;
        }
        Iterator<FeedbackEvent> iterator = pendingEvents.iterator();
        while (iterator.hasNext()) {
            FeedbackEvent event = iterator.next();
            Integer soundId = soundIds.get(event);
            if (soundId != null && soundId == sampleId) {
                soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
                iterator.remove();
            }
        }
    }

    /** Release the SoundPool and all loaded sounds. */
    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundIds.clear();
        loadedSounds.clear();
        pendingEvents.clear();
        activeTheme = null;
    }
}
