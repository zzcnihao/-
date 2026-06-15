package com.personal.passwordvault;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OverlayService extends Service {
    public static final String ACTION_SHOW_BUBBLE = "show_bubble";
    public static final String ACTION_HIDE_BUBBLE = "hide_bubble";
    public static final String ACTION_EXIT_BUBBLE = "exit_bubble";
    public static final String BROADCAST_SCREENSHOT_SAVED = "com.personal.passwordvault.SCREENSHOT_SAVED";

    private static final int MENU_DISMISS_MS = 12000;
    private static final int TIP_DISMISS_MS = 2000;
    private static final String DEFAULT_BUBBLE_COLOR = "#7EC8E3";

    private WindowManager windowManager;
    private View bubbleView;
    private View menuView;
    private View tipView;
    private WindowManager.LayoutParams bubbleLayoutParams;
    private ContentObserver screenshotObserver;
    private long lastShotTime = 0;
    private String lastShotKey = "";
    private long lastPermToast = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideMenuRunnable = this::removeBubbleMenuOnly;
    private final Runnable hideTipRunnable = this::removeTip;
    private final Runnable bubbleRestoreRunnable = this::tryRestoreBubbleAfterHide;

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
            refreshBubbleOrSchedule();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (ScreenshotStore.isOverlayEnabled(this)) {
            OverlayService.start(getApplicationContext(), true);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(hideMenuRunnable);
        handler.removeCallbacks(hideTipRunnable);
        handler.removeCallbacks(bubbleRestoreRunnable);
        removeBubbleMenu();
        removeBubble();
        removeTip();
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
            NotificationChannel ch = new NotificationChannel(channelId, "密拾后台", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("密拾")
                .setContentText("后台运行中，截图时可收纳到密拾")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void refreshBubbleOrSchedule() {
        if (!ScreenshotStore.isOverlayEnabled(this)) return;
        if (!android.provider.Settings.canDrawOverlays(this)) return;
        if (VaultCache.isBubbleTemporarilyHidden(this)) {
            removeBubble();
            long until = VaultCache.getBubbleHiddenUntil(this);
            long delay = Math.max(500, until - System.currentTimeMillis());
            handler.removeCallbacks(bubbleRestoreRunnable);
            handler.postDelayed(bubbleRestoreRunnable, delay);
            return;
        }
        handler.removeCallbacks(bubbleRestoreRunnable);
        showBubble();
    }

    private void tryRestoreBubbleAfterHide() {
        if (!ScreenshotStore.isOverlayEnabled(this)) return;
        if (!android.provider.Settings.canDrawOverlays(this)) return;
        if (VaultCache.isBubbleTemporarilyHidden(this)) {
            long until = VaultCache.getBubbleHiddenUntil(this);
            long delay = Math.max(500, until - System.currentTimeMillis());
            handler.postDelayed(bubbleRestoreRunnable, delay);
            return;
        }
        showBubble();
    }

    private void showBubble() {
        if (!android.provider.Settings.canDrawOverlays(this)) return;
        if (VaultCache.isBubbleTemporarilyHidden(this)) {
            removeBubble();
            return;
        }
        if (!ScreenshotStore.isOverlayEnabled(this)) return;

        int sizeDp = VaultCache.getBubbleSizeDp(this, 56);
        int size = dp(sizeDp);
        float alpha = VaultCache.getBubbleAlpha(this, 1f);
        String colorHex = VaultCache.getBubbleColor(this, DEFAULT_BUBBLE_COLOR);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        try {
            bg.setColor(Color.parseColor(colorHex));
        } catch (Exception e) {
            bg.setColor(Color.parseColor(DEFAULT_BUBBLE_COLOR));
        }
        bg.setStroke(dp(3), Color.parseColor("#FFFFFF"));

        TextView tv;
        if (bubbleView instanceof TextView) {
            tv = (TextView) bubbleView;
            tv.setText("\uD83D\uDD10");
            tv.setTextSize(sizeDp * 0.38f);
            tv.setAlpha(alpha);
            tv.setBackground(bg);
            if (bubbleLayoutParams != null) {
                bubbleLayoutParams.width = size;
                bubbleLayoutParams.height = size;
                windowManager.updateViewLayout(bubbleView, bubbleLayoutParams);
            }
            return;
        }

        tv = new TextView(this);
        tv.setText("\uD83D\uDD10");
        tv.setTextSize(sizeDp * 0.38f);
        tv.setGravity(Gravity.CENTER);
        tv.setAlpha(alpha);
        tv.setBackground(bg);

        bubbleLayoutParams = new WindowManager.LayoutParams(
                size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleLayoutParams.gravity = Gravity.TOP | Gravity.START;
        bubbleLayoutParams.x = ScreenshotStore.getBubbleX(this, dp(16));
        bubbleLayoutParams.y = ScreenshotStore.getBubbleY(this, dp(120));

        tv.setOnTouchListener(new BubbleTouchListener(tv, bubbleLayoutParams));
        bubbleView = tv;
        windowManager.addView(bubbleView, bubbleLayoutParams);
    }

    private void removeBubble() {
        if (bubbleView != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
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
                    ScreenshotStore.saveBubblePosition(OverlayService.this, lp.x, lp.y);
                    if (!moved) showBubbleMenu(lp);
                    return true;
                default:
                    return false;
            }
        }
    }

    private void showBubbleMenu(WindowManager.LayoutParams bubbleLp) {
        removeBubbleMenu();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(dp(18));
        card.setColor(Color.parseColor("#FFF5F8"));
        card.setStroke(dp(2), Color.parseColor("#FFD6E0"));
        menu.setBackground(card);

        TextView title = new TextView(this);
        title.setText("密拾小贴纸");
        title.setTextColor(Color.parseColor("#FF6B9D"));
        title.setTextSize(14);
        title.setPadding(dp(6), dp(4), dp(6), dp(8));
        menu.addView(title);

        addMenuItem(menu, "新增账号密钥", () -> { removeBubbleMenu(); showAddAccountDialog(); });
        addMenuItem(menu, "新增网址书签", () -> { removeBubbleMenu(); showAddUrlDialog(); });
        addMenuItem(menu, "截屏监听开关", () -> { removeBubbleMenu(); toggleScreenshotListen(); });
        addMenuItem(menu, "一键查看最近截图", () -> { removeBubbleMenu(); showRecentScreenshots(); });
        addMenuItem(menu, "快速搜密码", () -> { removeBubbleMenu(); showQuickSearchDialog(); });
        addMenuItem(menu, "临时私密剪贴板", () -> { removeBubbleMenu(); showTempClipboardDialog(); });
        addMenuItem(menu, "悬浮球隐藏", () -> { removeBubbleMenu(); hideBubbleTemporarily(); });
        addMenuItem(menu, "打开APP主页", () -> { removeBubbleMenu(); openMainActivity(); });

        scroll.addView(menu);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(200), dp(340),
                overlayType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.max(dp(8), bubbleLp.x - dp(24));
        lp.y = bubbleLp.y + dp(64);

        menuView = scroll;
        windowManager.addView(menuView, lp);
        handler.removeCallbacks(hideMenuRunnable);
        handler.postDelayed(hideMenuRunnable, MENU_DISMISS_MS);
    }

    private void addMenuItem(LinearLayout parent, String label, Runnable action) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setTextColor(Color.parseColor("#5C4B51"));
        item.setTextSize(13);
        item.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setCornerRadius(dp(12));
        itemBg.setColor(Color.parseColor("#D4EAF7"));
        item.setBackground(itemBg);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.bottomMargin = dp(6);
        item.setOnClickListener(v -> action.run());
        parent.addView(item, ilp);
    }

    private void removeBubbleMenuOnly() {
        if (menuView != null) {
            try {
                windowManager.removeView(menuView);
            } catch (Exception ignored) {}
            menuView = null;
        }
    }

    private void removeBubbleMenu() {
        handler.removeCallbacks(hideMenuRunnable);
        removeBubbleMenuOnly();
    }

    private void openMainActivity() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    private void hideBubbleTemporarily() {
        VaultCache.hideBubbleFor(this, 30000);
        removeBubble();
        handler.removeCallbacks(bubbleRestoreRunnable);
        handler.postDelayed(bubbleRestoreRunnable, 30000);
        Toast.makeText(getApplicationContext(), "悬浮球已隐藏 30 秒", Toast.LENGTH_SHORT).show();
    }

    private void toggleScreenshotListen() {
        boolean next = !VaultCache.isScreenshotListenEnabled(this);
        VaultCache.setScreenshotListenEnabled(this, next);
        Toast.makeText(getApplicationContext(), next ? "截屏监听已开启" : "截屏监听已关闭", Toast.LENGTH_SHORT).show();
    }

    private EditText newField(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setPadding(dp(8), dp(8), dp(8), dp(8));
        return et;
    }

    private void showAddAccountDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        EditText platform = newField("平台名称");
        EditText account = newField("账号");
        EditText password = newField("密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText url = newField("网址（可选）");
        form.addView(platform);
        form.addView(account);
        form.addView(password);
        form.addView(url);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("新增账号密钥")
                .setView(form)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = platform.getText().toString().trim();
            String acc = account.getText().toString().trim();
            String pwd = password.getText().toString();
            if (name.isEmpty() || acc.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(getApplicationContext(), "请填写平台、账号和密码", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject entry = new JSONObject();
                entry.put("type", "account");
                entry.put("name", name);
                entry.put("account", acc);
                entry.put("password", pwd);
                String urlStr = url.getText().toString().trim();
                if (!urlStr.isEmpty()) entry.put("url", urlStr);
                entry.put("category", "其他");
                VaultCache.addPendingEntry(this, entry);
                Toast.makeText(getApplicationContext(), "已加入待同步队列", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "保存失败", Toast.LENGTH_SHORT).show();
            }
        }));
        showOverlayDialog(dialog);
    }

    private void showAddUrlDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        EditText url = newField("网址");
        EditText name = newField("站点名称");
        form.addView(url);
        form.addView(name);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("新增网址书签")
                .setView(form)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String urlStr = url.getText().toString().trim();
            String siteName = name.getText().toString().trim();
            if (urlStr.isEmpty() || siteName.isEmpty()) {
                Toast.makeText(getApplicationContext(), "请填写网址和站点名称", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject entry = new JSONObject();
                entry.put("type", "url");
                entry.put("name", siteName);
                entry.put("url", urlStr);
                entry.put("category", "学习");
                VaultCache.addPendingEntry(this, entry);
                Toast.makeText(getApplicationContext(), "书签已加入待同步", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "保存失败", Toast.LENGTH_SHORT).show();
            }
        }));
        showOverlayDialog(dialog);
    }

    private void showQuickSearchDialog() {
        EditText query = newField("搜索名称或账号");
        query.setPadding(dp(16), dp(12), dp(16), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("快速搜密码")
                .setView(query)
                .setPositiveButton("搜索", null)
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String q = query.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty()) {
                Toast.makeText(getApplicationContext(), "请输入关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showSearchResults(q);
        }));
        showOverlayDialog(dialog);
    }

    private void showSearchResults(String q) {
        JSONArray index = VaultCache.getSearchIndex(this);
        List<String> labels = new ArrayList<>();
        List<String> passwords = new ArrayList<>();
        for (int i = 0; i < index.length(); i++) {
            try {
                JSONObject item = index.getJSONObject(i);
                String name = item.optString("name", "");
                String account = item.optString("account", "");
                String pwd = item.optString("password", "");
                if (pwd.isEmpty()) continue;
                String hay = (name + " " + account).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
                labels.add((name.isEmpty() ? account : name) + " / " + account);
                passwords.add(pwd);
            } catch (Exception ignored) {}
        }
        if (labels.isEmpty()) {
            Toast.makeText(getApplicationContext(), "未找到匹配项", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] arr = labels.toArray(new String[0]);
        AlertDialog result = new AlertDialog.Builder(this)
                .setTitle("搜索结果（点击复制密码）")
                .setItems(arr, (d, which) -> copyToClipboard(passwords.get(which)))
                .setNegativeButton("关闭", null)
                .create();
        showOverlayDialog(result);
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("vault", text));
            Toast.makeText(getApplicationContext(), "密码已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTempClipboardDialog() {
        String existing = VaultCache.getTempClipboard(this);
        if (!existing.isEmpty()) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("临时私密剪贴板")
                    .setMessage(existing)
                    .setPositiveButton("复制", (d, w) -> copyToClipboard(existing))
                    .setNeutralButton("覆盖", (d, w) -> showTempClipboardInput())
                    .setNegativeButton("关闭", null)
                    .create();
            showOverlayDialog(dialog);
            return;
        }
        showTempClipboardInput();
    }

    private void showTempClipboardInput() {
        EditText input = newField("输入临时文本（5分钟有效）");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("临时私密剪贴板")
                .setView(input)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText().toString();
            if (text.trim().isEmpty()) {
                Toast.makeText(getApplicationContext(), "内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            VaultCache.setTempClipboard(this, text, 300000);
            Toast.makeText(getApplicationContext(), "已保存，5 分钟后自动清除", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        showOverlayDialog(dialog);
    }

    private void showRecentScreenshots() {
        List<File> files = collectRecentShotFiles(3);
        if (files.isEmpty()) {
            Toast.makeText(getApplicationContext(), "暂无最近截图", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.parseColor("#FFF5F8"));
        row.setBackground(bg);
        for (File f : files) {
            ImageView iv = new ImageView(this);
            int side = dp(88);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
            lp.setMargins(dp(4), 0, dp(4), 0);
            try {
                iv.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
            } catch (Exception ignored) {}
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(iv, lp);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("最近截图")
                .setView(row)
                .setNegativeButton("关闭", null)
                .create();
        showOverlayDialog(dialog);
    }

    private List<File> collectRecentShotFiles(int max) {
        Set<String> seen = new HashSet<>();
        List<File> out = new ArrayList<>();
        try {
            JSONArray pending = new JSONArray(ScreenshotStore.getPendingJson(this));
            for (int i = pending.length() - 1; i >= 0 && out.size() < max * 2; i--) {
                JSONObject o = pending.getJSONObject(i);
                String path = o.optString("path", "");
                if (path.isEmpty() || seen.contains(path)) continue;
                File f = new File(path);
                if (f.exists()) {
                    seen.add(path);
                    out.add(f);
                }
            }
        } catch (Exception ignored) {}
        File dir = ScreenshotStore.getShotDir(this);
        File[] all = dir.listFiles();
        if (all != null) {
            for (File f : all) {
                if (!f.isFile()) continue;
                String p = f.getAbsolutePath();
                if (seen.contains(p)) continue;
                seen.add(p);
                out.add(f);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        if (out.size() > max) return out.subList(0, max);
        return out;
    }

    private void showOverlayDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(overlayType());
        }
        dialog.show();
    }

    private void showShotTipNearBubble() {
        if (bubbleView == null || bubbleLayoutParams == null) return;
        removeTip();
        TextView tip = new TextView(this);
        tip.setText("截图已收纳 \u2728");
        tip.setTextColor(Color.parseColor("#5C4B51"));
        tip.setTextSize(12);
        tip.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.parseColor("#D4EAF7"));
        bg.setStroke(dp(1), Color.parseColor("#5BA4D9"));
        tip.setBackground(bg);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = bubbleLayoutParams.x;
        lp.y = Math.max(dp(8), bubbleLayoutParams.y - dp(36));
        tipView = tip;
        windowManager.addView(tipView, lp);
        handler.removeCallbacks(hideTipRunnable);
        handler.postDelayed(hideTipRunnable, TIP_DISMISS_MS);
    }

    private void removeTip() {
        if (tipView != null) {
            try {
                windowManager.removeView(tipView);
            } catch (Exception ignored) {}
            tipView = null;
        }
    }

    private void registerScreenshotObserver() {
        screenshotObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                checkLatestScreenshot(null);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                checkLatestScreenshot(uri);
            }
        };
        getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, screenshotObserver);
    }

    private void checkLatestScreenshot(Uri directUri) {
        handler.postDelayed(() -> {
            try {
                if (!VaultCache.isScreenshotListenEnabled(this) || !ScreenshotStore.isOverlayEnabled(this)) return;
                if (!hasMediaPermission()) {
                    maybeShowPermToast();
                    return;
                }
                if (directUri != null) {
                    inspectUri(directUri);
                    return;
                }
                long sinceSec = (System.currentTimeMillis() / 1000L) - 60L;
                String selection = MediaStore.Images.Media.DATE_ADDED + " >= ?";
                String[] args = { String.valueOf(sinceSec) };
                String order = MediaStore.Images.Media.DATE_ADDED + " DESC";
                String[] proj = buildProjection();
                try (Cursor c = getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        proj, selection, args, order)) {
                    if (c == null || !c.moveToFirst()) return;
                    Uri uri = ContentUriHelper.getUri(c);
                    inspectRow(c, uri);
                }
            } catch (Exception ignored) {}
        }, 900);
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
        String bucket = getColumn(c, MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
        long addedSec = 0;
        long modifiedSec = 0;
        int dateIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
        if (dateIdx >= 0) addedSec = c.getLong(dateIdx);
        int modIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED);
        if (modIdx >= 0) modifiedSec = c.getLong(modIdx);

        long addedMs = addedSec > 0 ? addedSec * 1000L : System.currentTimeMillis();
        long modifiedMs = modifiedSec > 0 ? modifiedSec * 1000L : addedMs;
        long imageMs = Math.max(addedMs, modifiedMs);
        if (System.currentTimeMillis() - imageMs > 45000) return;

        String key = uri != null ? uri.toString() : (displayName + addedSec);
        if (key.equals(lastShotKey)) return;
        if (!isScreenshot(displayName, relativePath, dataPath, bucket)) return;
        if (System.currentTimeMillis() - lastShotTime < 1200) return;

        lastShotTime = System.currentTimeMillis();
        lastShotKey = key;
        if (uri == null && dataPath != null) uri = Uri.fromFile(new File(dataPath));
        if (uri != null) autoSaveScreenshot(uri);
    }

    private void autoSaveScreenshot(Uri uri) {
        final Uri shotUri = uri;
        new Thread(() -> {
            try {
                String saved = ScreenshotStore.copyToVault(OverlayService.this, shotUri);
                ScreenshotStore.addPending(OverlayService.this, saved, System.currentTimeMillis());
                MediaDeleteHelper.tryDeleteScreenshot(OverlayService.this, shotUri);
                handler.post(() -> {
                    Toast.makeText(getApplicationContext(), "截图已收纳 \u2728", Toast.LENGTH_SHORT).show();
                    showShotTipNearBubble();
                    Intent bc = new Intent(BROADCAST_SCREENSHOT_SAVED);
                    bc.setPackage(getPackageName());
                    sendBroadcast(bc);
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(getApplicationContext(), "截图收纳失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String[] buildProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.DATA
            };
        }
        return new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATA
        };
    }

    private String getColumn(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        if (idx < 0) return "";
        String v = c.getString(idx);
        return v == null ? "" : v;
    }

    private boolean isScreenshot(String name, String relativePath, String dataPath, String bucket) {
        String combined = (name + " " + relativePath + " " + dataPath + " " + bucket).toLowerCase(Locale.ROOT);
        if (combined.contains("screenshot") || combined.contains("screen_shot")
                || combined.contains("screencapture") || combined.contains("screenshots")
                || combined.contains("截屏") || combined.contains("截图") || combined.contains("屏幕截图")
                || combined.contains("screen-capture") || combined.contains("captures")
                || combined.contains("screenrecorder") || combined.contains("screen_recorder")
                || combined.contains("longshot") || combined.contains("screen cap")
                || combined.contains("screenrecord") || combined.contains("smartshot")) {
            return true;
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.startsWith("screenshot")
                || lowerName.startsWith("scr_")
                || lowerName.startsWith("screen-")
                || name.contains("截屏")
                || name.contains("截图")) {
            return true;
        }
        String pathOnly = (relativePath + " " + dataPath).toLowerCase(Locale.ROOT);
        return pathOnly.contains("/screenshots")
                || pathOnly.contains("\\screenshots")
                || pathOnly.contains("screen_shot")
                || pathOnly.contains("screencapture")
                || pathOnly.contains("截屏")
                || pathOnly.contains("截图");
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void maybeShowPermToast() {
        long now = System.currentTimeMillis();
        if (now - lastPermToast < 15000) return;
        lastPermToast = now;
        Toast.makeText(getApplicationContext(), "请授予密拾相册读取权限，否则无法识别截图", Toast.LENGTH_LONG).show();
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
