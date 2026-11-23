package io.github.xejohv.classic;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 设备门面：iPod Classic 风格主界面，常驻 :main 进程。
 *
 * 阅读器在 :reader 进程，只有点击「阅读」才拉起；退出阅读立即回到本界面。
 */
public class ClassicHomeActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFFE4002B;
    private static final int LINE = 0xFF1C1C1C;
    private static final String READER_PKG = "io.github.xejohv.libreraclassic";
    private static final String READER_ACT = "com.foobnix.ui2.MainTabs2";

    private FrameLayout root;
    private LinearLayout body;
    private TextView title, clock, nowTitle, nowTime, menuBtn;
    private ProgressBar progress;
    private TextView playBtn;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean locked;
    private int screen = 0; // 0 菜单, 1 音乐列表

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildChrome();
        if (PodMusic.get(this).tracks().isEmpty()) {
            showMenu();
        } else {
            showMusic();
        }
        ticker();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterKiosk();
        refreshNowPlaying();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } catch (Throwable ignored) {
        }
    }

    /** Kiosk：device owner 时静默锁定；否则退化为系统屏幕固定，仍不允许退出。 */
    private void enterKiosk() {
        if (locked) return;
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
                locked = true;
                return;
            }
            startLockTask();
            locked = true;
        } catch (Throwable ignored) {
        }
    }

    // ---------------- UI ----------------

    private void buildChrome() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // 顶部状态条
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(8), dp(16), dp(4));
        menuBtn = label("MENU", 12, ACCENT, Typeface.BOLD);
        menuBtn.setPadding(dp(2), dp(6), dp(12), dp(6));
        menuBtn.setOnClickListener(v -> showMenu());
        clock = label("", 13, DIM, Typeface.NORMAL);
        title = label("iPod", 17, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        TextView right = label("", 13, DIM, Typeface.NORMAL);
        right.setGravity(Gravity.END);
        top.addView(menuBtn, new LinearLayout.LayoutParams(-2, -2));
        top.addView(clock, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        col.addView(rule(), new LinearLayout.LayoutParams(-1, dp(1)));

        // 主体
        ScrollView sc = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sc.addView(body);
        col.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        // 底部正在播放
        col.addView(rule(), new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(14));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        nowTitle = label("未在播放", 13, TEXT, Typeface.NORMAL);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        nowTime = label("", 11, DIM, Typeface.NORMAL);
        playBtn = label("▶", 18, ACCENT, Typeface.BOLD);
        playBtn.setGravity(Gravity.CENTER);
        playBtn.setPadding(dp(12), dp(4), dp(4), dp(4));
        playBtn.setOnClickListener(v -> {
            PodMusic.get(this).toggle();
            refreshNowPlaying();
        });
        row.addView(nowTitle, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(nowTime, new LinearLayout.LayoutParams(-2, -2));
        row.addView(playBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        bottom.addView(row);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LINE));
        bottom.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        col.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private TextView label(String s, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(sp));
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", style));
        t.setIncludeFontPadding(false);
        return t;
    }

    private View rule() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        return v;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---------------- 页面 ----------------

    private void showMenu() {
        screen = 0;
        title.setText("iPod");
        body.removeAllViews();
        addRow("音乐", "歌曲 · 播放列表", () -> showMusic());
        addRow("阅读", "书库 · 继续阅读", this::openReader);
        addRow("设置", "外观 · 系统", () -> toast("设置将并入主界面"));
        addRow("关于", "版本 Classic", () -> toast("Librera Classic · 启动器"));
    }

    private void showMusic() {
        screen = 1;
        title.setText("歌曲");
        body.removeAllViews();
        List<PodMusic.Track> tracks = PodMusic.get(this).tracks();
        if (tracks.isEmpty()) {
            TextView empty = label("未找到音乐\n把文件放进 Music 目录后重进本页", 13, DIM, Typeface.NORMAL);
            empty.setPadding(dp(16), dp(24), dp(16), dp(16));
            body.addView(empty);
            return;
        }
        for (int i = 0; i < tracks.size(); i++) {
            final int idx = i;
            PodMusic.Track t = tracks.get(i);
            String sub = TextUtils.isEmpty(t.artist) ? " " : t.artist;
            addRow(t.title, sub, () -> {
                PodMusic.get(this).play(idx);
                refreshNowPlaying();
            });
        }
    }

    private interface Action {
        void run();
    }

    private void addRow(String main, String sub, Action action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        row.setBackgroundColor(BG);
        row.setOnClickListener(v -> action.run());

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView m = label(main, 16, TEXT, Typeface.NORMAL);
        m.setSingleLine(true);
        m.setEllipsize(TextUtils.TruncateAt.END);
        TextView s = label(sub, 11, DIM, Typeface.NORMAL);
        s.setSingleLine(true);
        texts.addView(m);
        texts.addView(s);

        TextView chev = label("›", 20, DIM, Typeface.NORMAL);
        chev.setGravity(Gravity.CENTER);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(chev, new LinearLayout.LayoutParams(dp(28), dp(44)));

        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        body.addView(rule(), new LinearLayout.LayoutParams(-1, dp(1)));
    }

    // ---------------- 行为 ----------------

    /** 阅读：显式组件跨进程拉起 :reader，返回值不等待，退出即回本界面。 */
    private void openReader() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(READER_PKG, READER_ACT));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Throwable e) {
            toast("阅读模块未就绪：" + e.getClass().getSimpleName());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (screen == 1) {
                showMenu();
            }
            return true; // 永远不退出到系统桌面
        }
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------- 播放进度 ----------------

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshNowPlaying();
            ui.postDelayed(this, 1000);
        }
    };

    private void ticker() {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, 1000);
    }

    private void refreshNowPlaying() {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        PodMusic m = PodMusic.get(this);
        PodMusic.Track t = m.current();
        nowTitle.setText(t == null ? "未在播放" : t.title);
        int pos = m.position(), dur = m.duration();
        nowTime.setText(fmt(pos) + " / " + fmt(dur));
        progress.setProgress(dur <= 0 ? 0 : (int) (1000L * pos / dur));
        playBtn.setText(m.isPlaying() ? "❚❚" : "▶");
    }

    private String fmt(int ms) {
        int s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}