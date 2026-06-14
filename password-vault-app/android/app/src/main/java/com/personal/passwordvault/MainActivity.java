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
    }
}
