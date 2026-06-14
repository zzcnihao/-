package com.personal.passwordvault;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(VaultNativePlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (ScreenshotStore.isOverlayEnabled(this) && android.provider.Settings.canDrawOverlays(this)) {
            OverlayService.start(this, true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ScreenshotStore.isOverlayEnabled(this)) {
            OverlayService.sendAction(this, OverlayService.ACTION_HIDE_BUBBLE);
        }
        notifyWeb();
    }

    private void notifyWeb() {
        if (getBridge() == null || getBridge().getWebView() == null) return;
        boolean enabled = ScreenshotStore.isOverlayEnabled(this);
        getBridge().getWebView().evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('vault-overlay-changed',{detail:{enabled:" + enabled + "}}))", null);
        if (ScreenshotStore.getPendingCount(this) > 0) {
            getBridge().getWebView().evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('vault-screenshot-saved'))", null);
        }
    }
}
