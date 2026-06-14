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

    public static boolean isOverlayEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("overlay_enabled", false);
    }

    public static void setOverlayEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("overlay_enabled", enabled).apply();
    }
}
