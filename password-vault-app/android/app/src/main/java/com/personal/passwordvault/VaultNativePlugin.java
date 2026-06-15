package com.personal.passwordvault;

import android.Manifest;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "VaultNative",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_MEDIA_IMAGES }, alias = "mediaImages"),
        @Permission(strings = { Manifest.permission.READ_EXTERNAL_STORAGE }, alias = "storage")
    }
)
public class VaultNativePlugin extends Plugin {

    private static final int DEFAULT_BUBBLE_SIZE_DP = 56;
    private static final float DEFAULT_BUBBLE_ALPHA = 0.85f;
    private static final String DEFAULT_BUBBLE_COLOR = "#FFFFFFFF";

    @PluginMethod
    public void isNativeApp(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("value", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void hasOverlayPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", Settings.canDrawOverlays(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void getOverlayEnabled(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("enabled", ScreenshotStore.isOverlayEnabled(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void openOverlaySettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getContext().getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        call.resolve();
    }

    @PluginMethod
    public void setOverlayEnabled(PluginCall call) {
        boolean enabled = call.getBoolean("enabled", false);
        ScreenshotStore.setOverlayEnabled(getContext(), enabled);
        if (enabled && Settings.canDrawOverlays(getContext())) {
            OverlayService.start(getContext(), true);
        } else if (!enabled) {
            OverlayService.stop(getContext());
        }
        call.resolve();
    }

    @PluginMethod
    public void startOverlay(PluginCall call) {
        if (!Settings.canDrawOverlays(getContext())) {
            call.reject("需要悬浮窗权限");
            return;
        }
        OverlayService.start(getContext(), true);
        call.resolve();
    }

    @PluginMethod
    public void stopOverlay(PluginCall call) {
        OverlayService.stop(getContext());
        call.resolve();
    }

    @PluginMethod
    public void showBubble(PluginCall call) {
        OverlayService.sendAction(getContext(), OverlayService.ACTION_SHOW_BUBBLE);
        call.resolve();
    }

    @PluginMethod
    public void hideBubble(PluginCall call) {
        OverlayService.sendAction(getContext(), OverlayService.ACTION_HIDE_BUBBLE);
        call.resolve();
    }

    @PluginMethod
    public void getPendingScreenshots(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("items", ScreenshotStore.getPendingJson(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void consumeScreenshot(PluginCall call) {
        String id = call.getString("id");
        if (id == null) {
            call.reject("missing id");
            return;
        }
        try {
            JSONObject item = ScreenshotStore.consume(getContext(), id);
            if (item == null) {
                call.reject("not found");
                return;
            }
            File file = new File(item.getString("path"));
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                in.read(bytes);
            }
            file.delete();
            JSObject ret = new JSObject();
            ret.put("id", id);
            ret.put("time", item.getLong("time"));
            ret.put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void requestMediaPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionForAlias("mediaImages", call, "mediaPerms");
        } else {
            requestPermissionForAlias("storage", call, "mediaPerms");
        }
    }

    @PermissionCallback
    private void mediaPerms(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void syncSearchIndex(PluginCall call) {
        String json = call.getString("json");
        VaultCache.setSearchIndex(getContext(), json);
        call.resolve();
    }

    @PluginMethod
    public void getPendingEntries(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("items", VaultCache.getPendingEntriesJson(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void consumePendingEntries(PluginCall call) {
        try {
            JSONArray arr = VaultCache.consumeAllPending(getContext());
            JSObject ret = new JSObject();
            ret.put("items", arr.toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void setScreenshotListen(PluginCall call) {
        boolean enabled = call.getBoolean("enabled", true);
        VaultCache.setScreenshotListenEnabled(getContext(), enabled);
        call.resolve();
    }

    @PluginMethod
    public void getScreenshotListen(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("enabled", VaultCache.isScreenshotListenEnabled(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void setTempClipboard(PluginCall call) {
        String text = call.getString("text", "");
        long expireMs = call.getLong("expireMs", 300000L);
        VaultCache.setTempClipboard(getContext(), text, expireMs);
        call.resolve();
    }

    @PluginMethod
    public void getTempClipboard(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("text", VaultCache.getTempClipboard(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void setBubbleStyle(PluginCall call) {
        Integer sizeDp = call.getInt("sizeDp");
        if (sizeDp != null) {
            VaultCache.setBubbleSizeDp(getContext(), sizeDp);
        }
        Double alpha = call.getDouble("alpha");
        if (alpha != null) {
            VaultCache.setBubbleAlpha(getContext(), alpha.floatValue());
        }
        String color = call.getString("color");
        if (color != null) {
            VaultCache.setBubbleColor(getContext(), color);
        }
        call.resolve();
    }

    @PluginMethod
    public void getBubbleStyle(PluginCall call) {
        Context ctx = getContext();
        JSObject ret = new JSObject();
        ret.put("sizeDp", VaultCache.getBubbleSizeDp(ctx, DEFAULT_BUBBLE_SIZE_DP));
        ret.put("alpha", VaultCache.getBubbleAlpha(ctx, DEFAULT_BUBBLE_ALPHA));
        ret.put("color", VaultCache.getBubbleColor(ctx, DEFAULT_BUBBLE_COLOR));
        call.resolve(ret);
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("missing url");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void exportShotToGallery(PluginCall call) {
        String id = call.getString("id");
        String path = call.getString("path");
        try {
            if (path == null && id != null) {
                JSONArray pending = new JSONArray(ScreenshotStore.getPendingJson(getContext()));
                for (int i = 0; i < pending.length(); i++) {
                    JSONObject item = pending.getJSONObject(i);
                    if (id.equals(item.optString("id"))) {
                        path = item.optString("path");
                        break;
                    }
                }
            }
            if (path == null || path.isEmpty()) {
                call.reject("missing path");
                return;
            }
            File src = new File(path);
            if (!src.exists() || !src.isFile()) {
                call.reject("file not found");
                return;
            }

            String displayName = src.getName();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/密拾");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Uri destUri = getContext().getContentResolver().insert(collection, values);
            if (destUri == null) {
                call.reject("insert failed");
                return;
            }

            try (FileInputStream in = new FileInputStream(src);
                 OutputStream out = getContext().getContentResolver().openOutputStream(destUri)) {
                if (out == null) {
                    call.reject("open output failed");
                    return;
                }
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContext().getContentResolver().update(destUri, values, null, null);
            }

            JSObject ret = new JSObject();
            ret.put("uri", destUri.toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }
}
