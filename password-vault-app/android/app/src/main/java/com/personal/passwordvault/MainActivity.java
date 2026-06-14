package com.personal.passwordvault;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private final Handler bubbleHandler = new Handler(Looper.getMainLooper());
    private Runnable showBubbleRunnable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(VaultNativePlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!ScreenshotStore.isOverlayEnabled(this)) return;
        if (!android.provider.Settings.canDrawOverlays(this)) return;

        if (showBubbleRunnable != null) {
            bubbleHandler.removeCallbacks(showBubbleRunnable);
            showBubbleRunnable = null;
        }

        if (hasFocus) {
            OverlayService.sendAction(this, OverlayService.ACTION_HIDE_BUBBLE);
        } else {
            showBubbleRunnable = () -> OverlayService.start(this, true);
            bubbleHandler.postDelayed(showBubbleRunnable, 400);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
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
