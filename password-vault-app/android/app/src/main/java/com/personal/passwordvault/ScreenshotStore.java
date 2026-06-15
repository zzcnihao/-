package com.personal.passwordvault;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

public class ScreenshotStore {
    private static final String PREFS = "vault_prefs";
    private static final String KEY_PENDING = "pending_screenshots";

    public static File getShotDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "vault_shots");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static String copyToVault(Context ctx, Uri uri) throws Exception {
        File dest = new File(getShotDir(ctx), UUID.randomUUID().toString() + ".jpg");
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new Exception("无法读取截图");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return dest.getAbsolutePath();
    }

    public static String copyToVault(Context ctx, String sourcePath) throws Exception {
        return copyToVault(ctx, Uri.fromFile(new File(sourcePath)));
    }

    public static void addPending(Context ctx, String path, long time) throws Exception {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray(prefs.getString(KEY_PENDING, "[]"));
        JSONObject obj = new JSONObject();
        obj.put("id", UUID.randomUUID().toString());
        obj.put("path", path);
        obj.put("time", time);
        arr.put(obj);
        prefs.edit().putString(KEY_PENDING, arr.toString()).apply();
    }

    public static int getPendingCount(Context ctx) {
        try {
            return new JSONArray(getPendingJson(ctx)).length();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getPendingJson(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PENDING, "[]");
    }

    public static JSONObject consume(Context ctx, String id) throws Exception {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray(prefs.getString(KEY_PENDING, "[]"));
        JSONArray next = new JSONArray();
        JSONObject found = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.getJSONObject(i);
            if (item.getString("id").equals(id)) found = item;
            else next.put(item);
        }
        if (found == null) return null;
        prefs.edit().putString(KEY_PENDING, next.toString()).apply();
        return found;
    }

    public static String getRecentShotsJson(Context ctx, int limit) {
        File dir = getShotDir(ctx);
        File[] files = dir.listFiles();
        if (files == null) return "[]";
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        JSONArray arr = new JSONArray();
        int count = 0;
        for (File f : files) {
            if (!f.isFile()) continue;
            if (limit > 0 && count >= limit) break;
            try {
                JSONObject obj = new JSONObject();
                obj.put("path", f.getAbsolutePath());
                obj.put("time", f.lastModified());
                obj.put("name", f.getName());
                arr.put(obj);
                count++;
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
    }

    public static boolean isOverlayEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("overlay_enabled", false);
    }

    public static void setOverlayEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("overlay_enabled", enabled).apply();
    }

    public static void saveBubblePosition(Context ctx, int x, int y) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("bubble_x", x).putInt("bubble_y", y).apply();
    }

    public static int getBubbleX(Context ctx, int def) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.contains("bubble_x") ? prefs.getInt("bubble_x", def) : def;
    }

    public static int getBubbleY(Context ctx, int def) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.contains("bubble_y") ? prefs.getInt("bubble_y", def) : def;
    }
}
