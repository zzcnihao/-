package com.personal.passwordvault;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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
import org.json.JSONObject;

@CapacitorPlugin(
    name = "VaultNative",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_MEDIA_IMAGES }, alias = "mediaImages"),
        @Permission(strings = { Manifest.permission.READ_EXTERNAL_STORAGE }, alias = "storage")
    }
)
public class VaultNativePlugin extends Plugin {

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
}
