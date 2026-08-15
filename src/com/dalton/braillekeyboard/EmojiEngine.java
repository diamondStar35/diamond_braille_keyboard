package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmojiEngine {
    private final Context context;
    private final KeyboardListener listener;
    private final SharedPreferences prefs;

    private List<List<String>> categories = new ArrayList<>();
    private List<String> favorites = new ArrayList<>();

    private int currentCategory = 0; // 0-11 for predefined, 12 for favorites
    private int currentRow = 0; // 0-15
    private int currentEmoji = -1; // 0-9
    
    private int movingFavoriteIndex = -1; // -1 if not moving

    // Category mapping: braille chord -> category index
    // 1-10 (a-j) = 0-9. dots 12356 = 10, dots 23456 = 11, dots 3456 = 12 (favorites)
    private static final Map<Byte, Integer> CATEGORY_MAP = new HashMap<>();
    // Row mapping: k-z + w -> row index 0-15
    private static final Map<Byte, Integer> ROW_MAP = new HashMap<>();
    // Emoji mapping: lower a-j -> emoji index 0-9
    private static final Map<Byte, Integer> EMOJI_MAP = new HashMap<>();

    static {
        // Categories
        CATEGORY_MAP.put((byte) 1, 0); // a (dot 1)
        CATEGORY_MAP.put((byte) 3, 1); // b (dots 12)
        CATEGORY_MAP.put((byte) 9, 2); // c (dots 14)
        CATEGORY_MAP.put((byte) 25, 3); // d (dots 145)
        CATEGORY_MAP.put((byte) 17, 4); // e (dots 15)
        CATEGORY_MAP.put((byte) 11, 5); // f (dots 124)
        CATEGORY_MAP.put((byte) 27, 6); // g (dots 1245)
        CATEGORY_MAP.put((byte) 19, 7); // h (dots 125)
        CATEGORY_MAP.put((byte) 10, 8); // i (dots 24)
        CATEGORY_MAP.put((byte) 26, 9); // j (dots 245)
        CATEGORY_MAP.put((byte) 55, 10); // 12356
        CATEGORY_MAP.put((byte) 62, 11); // 23456
        CATEGORY_MAP.put((byte) 60, 12); // 3456 (number sign) -> favorites

        // Rows (k-z + w)
        ROW_MAP.put((byte) 5, 0); // k (dots 13)
        ROW_MAP.put((byte) 7, 1); // l (dots 123)
        ROW_MAP.put((byte) 13, 2); // m (dots 134)
        ROW_MAP.put((byte) 29, 3); // n (dots 1345)
        ROW_MAP.put((byte) 21, 4); // o (dots 135)
        ROW_MAP.put((byte) 15, 5); // p (dots 1234)
        ROW_MAP.put((byte) 31, 6); // q (dots 12345)
        ROW_MAP.put((byte) 23, 7); // r (dots 1235)
        ROW_MAP.put((byte) 14, 8); // s (dots 234)
        ROW_MAP.put((byte) 30, 9); // t (dots 2345)
        ROW_MAP.put((byte) 37, 10); // u (dots 136)
        ROW_MAP.put((byte) 39, 11); // v (dots 1236)
        ROW_MAP.put((byte) 58, 12); // w (dots 2456)
        ROW_MAP.put((byte) 45, 13); // x (dots 1346)
        ROW_MAP.put((byte) 61, 14); // y (dots 13456)
        ROW_MAP.put((byte) 53, 15); // z (dots 1356)

        // Emojis (lower numbers / a-j)
        EMOJI_MAP.put((byte) 2, 0); // lower 1 (dot 2)
        EMOJI_MAP.put((byte) 6, 1); // lower 2 (dots 23)
        EMOJI_MAP.put((byte) 18, 2); // lower 3 (dots 25)
        EMOJI_MAP.put((byte) 50, 3); // lower 4 (dots 256)
        EMOJI_MAP.put((byte) 34, 4); // lower 5 (dots 26)
        EMOJI_MAP.put((byte) 22, 5); // lower 6 (dots 235)
        EMOJI_MAP.put((byte) 54, 6); // lower 7 (dots 2356)
        EMOJI_MAP.put((byte) 38, 7); // lower 8 (dots 236)
        EMOJI_MAP.put((byte) 20, 8); // lower 9 (dots 35)
        EMOJI_MAP.put((byte) 52, 9); // lower 0 (dots 356)
    }

    private String[] categoryNames;

    private static final String ROW_NOTATION = "KLMNOPQRSTUVWXYZ";

    private final Speech speech;

    public EmojiEngine(Context context, KeyboardListener listener, Speech speech) {
        this.context = context;
        this.categoryNames = new String[] {
            context.getString(R.string.emoji_category_smileys),
            context.getString(R.string.emoji_category_animals),
            context.getString(R.string.emoji_category_food),
            context.getString(R.string.emoji_category_activity),
            context.getString(R.string.emoji_category_travel),
            context.getString(R.string.emoji_category_objects),
            context.getString(R.string.emoji_category_symbols),
            context.getString(R.string.emoji_category_people1),
            context.getString(R.string.emoji_category_people2),
            context.getString(R.string.emoji_category_other),
            context.getString(R.string.emoji_category_flags1),
            context.getString(R.string.emoji_category_flags2),
            context.getString(R.string.emoji_category_favorites)
        };
        this.listener = listener;
        this.prefs = context.getSharedPreferences("EmojiFavorites", Context.MODE_PRIVATE);
        this.speech = speech;
        loadEmojis();
        loadFavorites();
    }

    private void loadEmojis() {
        categories.clear();
        for (int i = 0; i < 12; i++) {
            List<String> list = new ArrayList<>();
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("emoji/category_" + i + ".txt")));
                String line;
                while ((line = br.readLine()) != null) {
                    list.add(line);
                }
                br.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            categories.add(list);
        }
    }

    private void loadFavorites() {
        favorites.clear();
        int count = prefs.getInt("fav_count", 0);
        for (int i = 0; i < count; i++) {
            String emoji = prefs.getString("fav_" + i, "");
            if (!emoji.isEmpty()) {
                favorites.add(emoji);
            }
        }
    }

    private void saveFavorites() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("fav_count", favorites.size());
        for (int i = 0; i < favorites.size(); i++) {
            editor.putString("fav_" + i, favorites.get(i));
        }
        editor.apply();
    }

    public void speak(String text) {
        if (speech != null) {
            speech.speak(context, text, 0);
        }
    }

    public void onEmojiModeEntered() {
        speech.speak(context, context.getString(R.string.emoji_mode_enabled, categoryNames[currentCategory] + ". " + getRowInfo()), 0);
    }

    public void handleInput(byte dots) {
        // Navigation shortcuts
        if (dots == 4) { // Prev emoji (dot 3)
            moveEmoji(-1);
            return;
        } else if (dots == 32) { // Next emoji (dot 6)
            moveEmoji(1);
            return;
        } else if (dots == 24) { // Prev row (dots 45)
            moveRow(-1);
            return;
        } else if (dots == 48) { // Next row (dots 56)
            moveRow(1);
            return;
        } else if (dots == 8) { // Prev category (dot 4)
            moveCategory(-1);
            return;
        } else if (dots == 16) { // Next category (dot 5)
            moveCategory(1);
            return;
        } else if (dots == 56) { // Insert (dots 456)
            insertEmoji();
            return;
        } else if (dots == 36) { // Add to favorites (dots 36)
            addToFavorites();
            return;
        } else if (dots == 40) { // Remove from favorites (dots 46)
            removeFromFavorites();
            return;
        } else if (dots == 33) { // Mark for move (dots 16)
            markForMove();
            return;
        }

        if (CATEGORY_MAP.containsKey(dots)) {
            currentCategory = CATEGORY_MAP.get(dots);
            currentRow = 0;
            currentEmoji = -1;
            speech.speak(context, context.getString(R.string.emoji_category_info, categoryNames[currentCategory], getRowInfo()), 0);
        } else if (ROW_MAP.containsKey(dots)) {
            currentRow = ROW_MAP.get(dots);
            currentEmoji = -1;
            speech.speak(context, getRowInfo(), 0);
        } else if (EMOJI_MAP.containsKey(dots)) {
            currentEmoji = EMOJI_MAP.get(dots);
            speakCurrentEmoji();
        } else {
            speech.speak(context, context.getString(R.string.emoji_unknown_command), 0);
        }
    }

    private void moveEmoji(int offset) {
        if (currentEmoji == -1) {
            currentEmoji = offset > 0 ? 0 : Math.max(0, getEmojiCountInCurrentRow() - 1);
        } else {
            currentEmoji += offset;
            if (currentEmoji < 0) currentEmoji = 0;
            
            int max = getEmojiCountInCurrentRow();
            if (currentEmoji >= max) currentEmoji = max > 0 ? max - 1 : 0;
        }

        speakCurrentEmoji();
    }

    private void moveRow(int offset) {
        currentRow += offset;
        if (currentRow < 0) currentRow = 0;
        if (currentRow > 15) currentRow = 15;
        currentEmoji = -1;
        speech.speak(context, getRowInfo(), 0);
    }

    private void moveCategory(int offset) {
        currentCategory += offset;
        if (currentCategory < 0) currentCategory = 0;
        if (currentCategory > 12) currentCategory = 12;
        currentRow = 0;
        currentEmoji = -1;
        speech.speak(context, context.getString(R.string.emoji_category_info, categoryNames[currentCategory], getRowInfo()), 0);
    }

    private void insertEmoji() {
        if (movingFavoriteIndex != -1) {
            // We are moving a favorite to the current position
            if (currentCategory != 12) {
                speech.speak(context, context.getString(R.string.emoji_favorites_move_error), 0);
                return;
            }
            if (movingFavoriteIndex >= 0 && movingFavoriteIndex < favorites.size()) {
                String toMove = favorites.remove(movingFavoriteIndex);
                int targetIndex = currentRow * 10 + currentEmoji;
                if (targetIndex > favorites.size()) {
                    targetIndex = favorites.size();
                }
                favorites.add(targetIndex, toMove);
                saveFavorites();
                speech.speak(context, context.getString(R.string.emoji_favorites_moved, String.valueOf(ROW_NOTATION.charAt(targetIndex / 10)), (targetIndex % 10) + 1), 0);
            }
            movingFavoriteIndex = -1;
            return;
        }

        String emoji = getEmojiAt(currentCategory, currentRow, currentEmoji);
        if (emoji != null) {
            listener.commitText(emoji, 1);
            speech.speak(context, context.getString(R.string.emoji_inserted, emoji), 0);
        } else {
            speech.speak(context, context.getString(R.string.emoji_not_found), 0);
        }
    }

    private void addToFavorites() {
        String emoji = getEmojiAt(currentCategory, currentRow, currentEmoji);
        if (emoji != null && currentCategory != 12) {
            if (!favorites.contains(emoji)) {
                favorites.add(emoji);
                saveFavorites();
                speech.speak(context, context.getString(R.string.emoji_favorites_added, emoji), 0);
            } else {
                speech.speak(context, context.getString(R.string.emoji_favorites_already_exists), 0);
            }
        }
    }

    private void removeFromFavorites() {
        if (currentEmoji == -1) return;
        if (currentCategory == 12) {
            int index = currentRow * 10 + currentEmoji;
            if (index < favorites.size()) {
                String removed = favorites.remove(index);
                saveFavorites();
                speech.speak(context, context.getString(R.string.emoji_favorites_removed, removed), 0);
            }
        } else {
            speech.speak(context, context.getString(R.string.emoji_favorites_remove_error), 0);
        }
    }

    private void markForMove() {
        if (currentEmoji == -1) return;
        if (currentCategory == 12) {
            int index = currentRow * 10 + currentEmoji;
            if (index < favorites.size()) {
                movingFavoriteIndex = index;
                String emoji = favorites.get(index);
                speech.speak(context, context.getString(R.string.emoji_favorites_move_selected, emoji), 0);
            }
        } else {
            speech.speak(context, context.getString(R.string.emoji_favorites_move_start_error), 0);
        }
    }

    private void speakCurrentEmoji() {
        String emoji = getEmojiAt(currentCategory, currentRow, currentEmoji);
        if (emoji != null) {
            String pos = categoryNames[currentCategory].charAt(0) + ", " + 
                         ROW_NOTATION.charAt(currentRow) + ", " + 
                         (currentEmoji + 1);
            speech.speak(context, context.getString(R.string.emoji_position_info, emoji, pos), 0);
        } else {
            speech.speak(context, context.getString(R.string.emoji_empty), 0);
        }
    }

    private String getRowInfo() {
        int count = getEmojiCountInCurrentRow();
        if (count == 0) {
            return context.getString(R.string.emoji_row_empty, String.valueOf(ROW_NOTATION.charAt(currentRow)));
        } else {
            return context.getString(R.string.emoji_row_info, String.valueOf(ROW_NOTATION.charAt(currentRow)), count);
        }
    }

    private int getEmojiCountInCurrentRow() {
        List<String> list = currentCategory == 12 ? favorites : categories.get(currentCategory);
        int startIndex = currentRow * 10;
        if (startIndex >= list.size()) return 0;
        int remaining = list.size() - startIndex;
        return Math.min(remaining, 10);
    }

    private String getEmojiAt(int category, int row, int col) {
        List<String> list = category == 12 ? favorites : categories.get(category);
        int index = row * 10 + col;
        if (index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }
}
