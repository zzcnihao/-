package com.personal.passwordvault;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import java.util.Locale;

public class OverlayService extends Service {
    public static final String ACTION_SHOW_BUBBLE = "show_bubble";
    public static final String ACTION_HIDE_BUBBLE = "hide_bubble";

    private WindowManager windowManager;
    private View bubbleView;
    private View promptView;
    private ContentObserver screenshotObserver;
    private long lastShotTime = 0;
    private String lastShotPath = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static void start(Context ctx, boolean showBubble) {
        Intent intent = new Intent(ctx, OverlayService.class);
        if (showBubble) intent.setAction(ACTION_SHOW_BUBBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, OverlayService.class));
    }

    public static void sendAction(Context ctx, String action) {
        Intent intent = new Intent(ctx, OverlayService.class);
        intent.setAction(action);
        ctx.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground(1, buildNotification());
        registerScreenshotObserver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_HIDE_BUBBLE.equals(action)) {
            removeBubble();
        } else {
            showBubble();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeBubble();
        removePrompt();
        if (screenshotObserver != null) {
            getContentResolver().unregisterContentObserver(screenshotObserver);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        String channelId = "vault_overlay";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "密码本后台", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("密码本")
                .setContentText("后台运行中，截图时可保存到记事本")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void showBubble() {
        if (bubbleView != null || !android.provider.Settings.canDrawOverlays(this)) return;

        int size = dp(56);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#6c63ff"));

        TextView tv = new TextView(this);
        tv.setText("📝");
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(bg);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(16);
        lp.y = dp(120);

        tv.setOnTouchListener(new BubbleTouchListener(lp));
        tv.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

        bubbleView = tv;
        windowManager.addView(bubbleView, lp);
    }

    private void removeBubble() {
        if (bubbleView != null) {
            windowManager.removeView(bubbleView);
            bubbleView = null;
        }
    }

    private class BubbleTouchListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams lp;
        private int initX, initY;
        private float touchX, touchY;

        BubbleTouchListener(WindowManager.LayoutParams lp) {
            this.lp = lp;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initX = lp.x;
                    initY = lp.y;
                    touchX = e.getRawX();
                    touchY = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = initX + (int) (e.getRawX() - touchX);
                    lp.y = initY + (int) (e.getRawY() - touchY);
                    windowManager.updateViewLayout(v, lp);
                    return true;
                default:
                    return false;
            }
        }
    }

    private void registerScreenshotObserver() {
        screenshotObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                checkLatestScreenshot();
            }
        };
        getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, screenshotObserver);
    }

    private void checkLatestScreenshot() {
        handler.postDelayed(() -> {
            try {
                String[] proj = {
                        MediaStore.Images.Media.DATA,
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.DISPLAY_NAME
                };
                try (Cursor c = getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        proj, null, null,
                        MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 1")) {
                    if (c == null || !c.moveToFirst()) return;
                    String path = c.getString(0);
                    long added = c.getLong(1) * 1000L;
                    if (path == null || path.equals(lastShotPath)) return;
                    if (System.currentTimeMillis() - added > 15000) return;
                    if (!isScreenshotPath(path)) return;
                    if (System.currentTimeMillis() - lastShotTime < 2000) return;
                    lastShotTime = System.currentTimeMillis();
                    lastShotPath = path;
                    showSavePrompt(path);
                }
            } catch (Exception ignored) {}
        }, 500);
    }

    private boolean isScreenshotPath(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("screenshot") || p.contains("screen_shot") || p.contains("截屏")
                || p.contains("截图") || p.contains("screencapture") || p.contains("screenshots");
    }

    private void showSavePrompt(String path) {
        handler.post(() -> {
            if (!android.provider.Settings.canDrawOverlays(this)) return;
            removePrompt();

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(16), dp(16), dp(16), dp(16));
            GradientDrawable card = new GradientDrawable();
            card.setCornerRadius(dp(16));
            card.setColor(Color.parseColor("#1a1a2e"));
            root.setBackground(card);

            TextView title = new TextView(this);
            title.setText("保存到记事本？");
            title.setTextColor(Color.WHITE);
            title.setTextSize(16);
            title.setPadding(0, 0, 0, dp(8));
            root.addView(title);

            ImageView preview = new ImageView(this);
            preview.setAdjustViewBounds(true);
            preview.setMaxHeight(dp(120));
            preview.setImageBitmap(BitmapFactory.decodeFile(path));
            root.addView(preview);

            LinearLayout buttons = new LinearLayout(this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setPadding(0, dp(12), 0, 0);

            Button cancel = new Button(this);
            cancel.setText("取消");
            cancel.setAllCaps(false);
            cancel.setOnClickListener(v -> removePrompt());

            Button ok = new Button(this);
            ok.setText("确定");
            ok.setAllCaps(false);
            ok.setOnClickListener(v -> {
                try {
                    String saved = ScreenshotStore.copyToVault(this, path);
                    ScreenshotStore.addPending(this, saved, System.currentTimeMillis());
                } catch (Exception ignored) {}
                removePrompt();
            });

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            buttons.addView(cancel, btnLp);
            buttons.addView(ok, btnLp);
            root.addView(buttons);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            lp.gravity = Gravity.CENTER;
            promptView = root;
            windowManager.addView(promptView, lp);
        });
    }

    private void removePrompt() {
        if (promptView != null) {
            windowManager.removeView(promptView);
            promptView = null;
        }
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
