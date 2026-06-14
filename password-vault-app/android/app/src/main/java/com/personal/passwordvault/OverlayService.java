package com.personal.passwordvault;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.util.Locale;

public class OverlayService extends Service {
    public static final String ACTION_SHOW_BUBBLE = "show_bubble";
    public static final String ACTION_HIDE_BUBBLE = "hide_bubble";
    public static final String ACTION_EXIT_BUBBLE = "exit_bubble";
    public static final String BROADCAST_SCREENSHOT_SAVED = "com.personal.passwordvault.SCREENSHOT_SAVED";

    private WindowManager windowManager;
    private View bubbleView;
    private View promptView;
    private View menuView;
    private WindowManager.LayoutParams bubbleLayoutParams;
    private ContentObserver screenshotObserver;
    private long lastShotTime = 0;
    private String lastShotKey = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideMenuRunnable = this::removeBubbleMenuOnly;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
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
            removeBubbleMenu();
            removeBubble();
        } else if (ACTION_EXIT_BUBBLE.equals(action)) {
            exitBubbleMode();
        } else {
            showBubble();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeBubbleMenu();
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

    private void exitBubbleMode() {
        ScreenshotStore.setOverlayEnabled(this, false);
        removeBubbleMenu();
        removeBubble();
        stopSelf();
        Toast.makeText(getApplicationContext(), "已退出悬浮球", Toast.LENGTH_SHORT).show();
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

        bubbleLayoutParams = new WindowManager.LayoutParams(
                size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleLayoutParams.gravity = Gravity.TOP | Gravity.START;
        bubbleLayoutParams.x = dp(16);
        bubbleLayoutParams.y = dp(120);

        tv.setOnTouchListener(new BubbleTouchListener(tv, bubbleLayoutParams));
        bubbleView = tv;
        windowManager.addView(bubbleView, bubbleLayoutParams);
    }

    private void removeBubble() {
        if (bubbleView != null) {
            windowManager.removeView(bubbleView);
            bubbleView = null;
        }
    }

    private class BubbleTouchListener implements View.OnTouchListener {
        private final View bubble;
        private final WindowManager.LayoutParams lp;
        private int initX, initY;
        private float touchX, touchY;
        private boolean moved;

        BubbleTouchListener(View bubble, WindowManager.LayoutParams lp) {
            this.bubble = bubble;
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
                    moved = false;
                    removeBubbleMenu();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (e.getRawX() - touchX);
                    int dy = (int) (e.getRawY() - touchY);
                    if (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) moved = true;
                    lp.x = initX + dx;
                    lp.y = initY + dy;
                    windowManager.updateViewLayout(bubble, lp);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) showBubbleMenu(lp);
                    return true;
                default:
                    return false;
            }
        }
    }

    private void showBubbleMenu(WindowManager.LayoutParams bubbleLp) {
        removeBubbleMenu();

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Color.parseColor("#252542"));
        menu.setBackground(bg);

        Button openBtn = createMenuButton("打开密码本");
        openBtn.setOnClickListener(v -> {
            removeBubbleMenu();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        });

        Button exitBtn = createMenuButton("退出悬浮球");
        exitBtn.setTextColor(Color.parseColor("#ff6b81"));
        exitBtn.setOnClickListener(v -> exitBubbleMode());

        menu.addView(openBtn);
        menu.addView(exitBtn);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(140), WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.max(dp(8), bubbleLp.x - dp(20));
        lp.y = bubbleLp.y + dp(60);

        menuView = menu;
        windowManager.addView(menuView, lp);

        handler.postDelayed(hideMenuRunnable, 5000);
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setPadding(dp(8), dp(10), dp(8), dp(10));
        return btn;
    }

    private void removeBubbleMenuOnly() {
        if (menuView != null) {
            windowManager.removeView(menuView);
            menuView = null;
        }
    }

    private void removeBubbleMenu() {
        handler.removeCallbacks(hideMenuRunnable);
        removeBubbleMenuOnly();
    }

    private void registerScreenshotObserver() {
        screenshotObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                checkLatestScreenshot(null);
            }
        };
        getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, screenshotObserver);
    }

    private void checkLatestScreenshot(Uri directUri) {
        handler.postDelayed(() -> {
            try {
                if (directUri != null) {
                    inspectUri(directUri);
                    return;
                }
                String order = MediaStore.Images.Media.DATE_ADDED + " DESC";
                String[] proj = buildProjection();
                try (Cursor c = getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        proj, null, null, order)) {
                    if (c == null || !c.moveToFirst()) return;
                    Uri uri = ContentUriHelper.getUri(c);
                    inspectRow(c, uri);
                }
            } catch (Exception ignored) {}
        }, 600);
    }

    private void inspectUri(Uri uri) {
        String[] proj = buildProjection();
        try (Cursor c = getContentResolver().query(uri, proj, null, null, null)) {
            if (c == null || !c.moveToFirst()) return;
            inspectRow(c, uri);
        } catch (Exception ignored) {}
    }

    private void inspectRow(Cursor c, Uri uri) {
        String displayName = getColumn(c, MediaStore.Images.Media.DISPLAY_NAME);
        String relativePath = getColumn(c, MediaStore.Images.Media.RELATIVE_PATH);
        String dataPath = getColumn(c, MediaStore.Images.Media.DATA);
        long addedSec = 0;
        int dateIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
        if (dateIdx >= 0) addedSec = c.getLong(dateIdx);

        long addedMs = addedSec > 0 ? addedSec * 1000L : System.currentTimeMillis();
        if (System.currentTimeMillis() - addedMs > 20000) return;

        String key = uri != null ? uri.toString() : (displayName + addedSec);
        if (key.equals(lastShotKey)) return;
        if (!isScreenshot(displayName, relativePath, dataPath)) return;
        if (System.currentTimeMillis() - lastShotTime < 1500) return;

        lastShotTime = System.currentTimeMillis();
        lastShotKey = key;
        if (uri == null && dataPath != null) uri = Uri.fromFile(new java.io.File(dataPath));
        if (uri != null) showSavePrompt(uri);
    }

    private String[] buildProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATA
            };
        }
        return new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATA
        };
    }

    private String getColumn(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        if (idx < 0) return "";
        String v = c.getString(idx);
        return v == null ? "" : v;
    }

    private boolean isScreenshot(String name, String relativePath, String dataPath) {
        String combined = (name + " " + relativePath + " " + dataPath).toLowerCase(Locale.ROOT);
        return combined.contains("screenshot") || combined.contains("screen_shot")
                || combined.contains("screencapture") || combined.contains("screenshots")
                || combined.contains("截屏") || combined.contains("截图")
                || combined.contains("screen-capture") || combined.contains("captures");
    }

    private void showSavePrompt(Uri uri) {
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
            preview.setMaxHeight(dp(140));
            try {
                preview.setImageURI(uri);
            } catch (Exception e) {
                preview.setImageBitmap(BitmapFactory.decodeStream(
                        getContentResolver().openInputStream(uri)));
            }
            root.addView(preview);

            LinearLayout buttons = new LinearLayout(this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setPadding(0, dp(12), 0, 0);

            Button cancel = new Button(this);
            cancel.setText("取消");
            cancel.setAllCaps(false);
            cancel.setOnClickListener(v -> removePrompt());

            Uri shotUri = uri;
            Button ok = new Button(this);
            ok.setText("确定");
            ok.setAllCaps(false);
            ok.setOnClickListener(v -> saveScreenshot(shotUri));

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            buttons.addView(cancel, btnLp);
            buttons.addView(ok, btnLp);
            root.addView(buttons);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                    PixelFormat.TRANSLUCENT
            );
            lp.gravity = Gravity.CENTER;
            lp.dimAmount = 0.5f;
            promptView = root;
            windowManager.addView(promptView, lp);
        });
    }

    private void saveScreenshot(Uri uri) {
        try {
            String saved = ScreenshotStore.copyToVault(this, uri);
            ScreenshotStore.addPending(this, saved, System.currentTimeMillis());
            removePrompt();
            Toast.makeText(getApplicationContext(), "截图已保存，打开密码本可在「截图」页查看", Toast.LENGTH_LONG).show();
            showSavedNotification();
        } catch (Exception e) {
            removePrompt();
            Toast.makeText(getApplicationContext(), "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSavedNotification() {
        String channelId = "vault_saved";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "截图保存", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 2, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("密码本")
                .setContentText("截图已保存到记事本")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(2, n);
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

    private static class ContentUriHelper {
        static Uri getUri(Cursor c) {
            int idIdx = c.getColumnIndex(MediaStore.Images.Media._ID);
            if (idIdx >= 0) {
                long id = c.getLong(idIdx);
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
            }
            return null;
        }
    }
}
