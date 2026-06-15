package com.personal.passwordvault;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

public class VaultCache {
    private static final String PREFS = "vault_prefs";
    private static final String KEY_INDEX = "vault_search_index";
    private static final String KEY_PENDING = "pending_vault_entries";
    private static final String KEY_CLIP = "temp_clipboard";
    private static final String KEY_CLIP_EXP = "temp_clipboard_exp";
    private static final String KEY_SHOT_LISTEN = "screenshot_listen";
    private static final String KEY_BUBBLE_SIZE = "bubble_size";
    private static final String KEY_BUBBLE_ALPHA = "bubble_alpha";
    private static final String KEY_BUBBLE_COLOR = "bubble_color";
    private static final String KEY_BUBBLE_HIDDEN_UNTIL = "bubble_hidden_until";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void setSearchIndex(Context ctx, String json) {
        prefs(ctx).edit().putString(KEY_INDEX, json == null ? "[]" : json).apply();
    }

    public static JSONArray getSearchIndex(Context ctx) {
        try {
            return new JSONArray(prefs(ctx).getString(KEY_INDEX, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void addPendingEntry(Context ctx, JSONObject entry) throws Exception {
        SharedPreferences p = prefs(ctx);
        JSONArray arr = new JSONArray(p.getString(KEY_PENDING, "[]"));
        if (!entry.has("id")) entry.put("id", UUID.randomUUID().toString());
        entry.put("time", System.currentTimeMillis());
        arr.put(entry);
        p.edit().putString(KEY_PENDING, arr.toString()).apply();
    }

    public static String getPendingEntriesJson(Context ctx) {
        return prefs(ctx).getString(KEY_PENDING, "[]");
    }

    public static JSONArray consumeAllPending(Context ctx) throws Exception {
        SharedPreferences p = prefs(ctx);
        JSONArray arr = new JSONArray(p.getString(KEY_PENDING, "[]"));
        p.edit().putString(KEY_PENDING, "[]").apply();
        return arr;
    }

    public static void setTempClipboard(Context ctx, String text, long expireMs) {
        long exp = expireMs > 0 ? System.currentTimeMillis() + expireMs : 0;
        prefs(ctx).edit().putString(KEY_CLIP, text).putLong(KEY_CLIP_EXP, exp).apply();
    }

    public static String getTempClipboard(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long exp = p.getLong(KEY_CLIP_EXP, 0);
        if (exp > 0 && System.currentTimeMillis() > exp) {
            p.edit().remove(KEY_CLIP).remove(KEY_CLIP_EXP).apply();
            return "";
        }
        return p.getString(KEY_CLIP, "");
    }

    public static boolean isScreenshotListenEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SHOT_LISTEN, true);
    }

    public static void setScreenshotListenEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_SHOT_LISTEN, enabled).apply();
    }

    public static int getBubbleSizeDp(Context ctx, int def) {
        return prefs(ctx).getInt(KEY_BUBBLE_SIZE, def);
    }

    public static void setBubbleSizeDp(Context ctx, int dp) {
        prefs(ctx).edit().putInt(KEY_BUBBLE_SIZE, dp).apply();
    }

    public static float getBubbleAlpha(Context ctx, float def) {
        return prefs(ctx).getFloat(KEY_BUBBLE_ALPHA, def);
    }

    public static void setBubbleAlpha(Context ctx, float alpha) {
        prefs(ctx).edit().putFloat(KEY_BUBBLE_ALPHA, alpha).apply();
    }

    public static String getBubbleColor(Context ctx, String def) {
        return prefs(ctx).getString(KEY_BUBBLE_COLOR, def);
    }

    public static void setBubbleColor(Context ctx, String color) {
        prefs(ctx).edit().putString(KEY_BUBBLE_COLOR, color).apply();
    }

    public static long getBubbleHiddenUntil(Context ctx) {
        return prefs(ctx).getLong(KEY_BUBBLE_HIDDEN_UNTIL, 0);
    }

    public static void hideBubbleFor(Context ctx, long millis) {
        prefs(ctx).edit().putLong(KEY_BUBBLE_HIDDEN_UNTIL, System.currentTimeMillis() + millis).apply();
    }

    public static boolean isBubbleTemporarilyHidden(Context ctx) {
        long until = getBubbleHiddenUntil(ctx);
        if (until <= 0) return false;
        if (System.currentTimeMillis() < until) return true;
        prefs(ctx).edit().remove(KEY_BUBBLE_HIDDEN_UNTIL).apply();
        return false;
    }
}
