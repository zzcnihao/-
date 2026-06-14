package com.personal.passwordvault;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private BroadcastReceiver screenshotReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(VaultNativePlugin.class);
        super.onCreate(savedInstanceState);
        screenshotReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().evaluateJavascript(
                            "window.dispatchEvent(new CustomEvent('vault-screenshot-saved'))", null);
                }
            }
        };
        IntentFilter filter = new IntentFilter(OverlayService.BROADCAST_SCREENSHOT_SAVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenshotReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        if (screenshotReceiver != null) {
            unregisterReceiver(screenshotReceiver);
        }
        super.onDestroy();
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
    }
}
