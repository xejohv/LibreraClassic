package io.github.xejohv.classic;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** iPod 风格书库：与音乐主界面同一套黑底/红点/白字语言，菜单只保留“书库 / 下载”两档。 */
public class ClassicReaderActivity extends Activity {

    /** 书库 = 木纹书架：底是木，字是暖白，卡片是羊皮纸，跟纯黑的音乐界面区分开。 */
    private static final String COL_TEXT = "#FFF6E7";
    private static final String COL_DIM = "#DCC6A5";
    private static final String COL_LINE = "#5E3D24";
    private static final String COL_RED = "#FFE1B0";
    private static final String COL_CARD = "#F8ECD9";
    private static final String COL_SEP = "#D2BE9F";
    private static final String COL_NAME = "#3B2B1C";
    private static final String COL_META = "#7C6650";
    private static final String COL_ARROW = "#A38A6C";
    private static final String COL_STAT = "#F2E1C4";

    /** One folder, decided here: everything the reader shows lives in /sdcard/Books. */
    private final List<File> roots = new ArrayList<>();
    private LinearLayout listBox;
    /** 0 = 最近（打开过的），1 = 全部。 */
    private int mode = 1;
    private final TextView[] segTabs = new TextView[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roots.add(new File(Environment.getExternalStorageDirectory(), "Books"));
        setContentView(buildUi());
        PodSkin.disableSoftKeyboard(this);
        PodCovers.prune(this);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // coming back from a page: the row has to say where the reader got to
        if (listBox != null) {
            reload();
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new WoodBackdrop());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        // an iOS title bar: the way back sits on the left, the title is centred
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(Color.TRANSPARENT);

        TextView title = new TextView(this);
        title.setText("阅读");
        title.setTextColor(Color.parseColor(COL_TEXT));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(17));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new FrameLayout.LayoutParams(-1, -1));

        TextView back = new TextView(this);
        back.setText("\u2039 音乐");
        back.setTextColor(Color.parseColor(COL_RED));
        back.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(16));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(16), 0, dp(16), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishToMusic(); }
        });
        bar.addView(back, new FrameLayout.LayoutParams(-2, -1, Gravity.LEFT));

        column.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor(COL_LINE));
        column.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        // 最近 / 全部：选中的一格被填满，未选中的只是一行字
        LinearLayout segRow = new LinearLayout(this);
        segRow.setGravity(Gravity.CENTER);
        segRow.setPadding(0, dp(12), 0, dp(2));

        LinearLayout track = new LinearLayout(this);
        android.graphics.drawable.GradientDrawable trackBg = new android.graphics.drawable.GradientDrawable();
        trackBg.setColor(Color.parseColor("#73301C0D"));
        trackBg.setCornerRadius(dp(9));
        track.setBackground(trackBg);
        track.setPadding(dp(2), dp(2), dp(2), dp(2));

        String[] names = {"最近", "全部"};
        for (int i = 0; i < 2; i++) {
            final int which = i;
            TextView tab = new TextView(this);
            tab.setText(names[i]);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(13));
            tab.setGravity(Gravity.CENTER);
            tab.setMinWidth(dp(74));
            tab.setPadding(dp(10), dp(6), dp(10), dp(6));
            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (mode != which) {
                        mode = which;
                        paintTabs();
                        reload();
                    }
                }
            });
            segTabs[i] = tab;
            track.addView(tab, new LinearLayout.LayoutParams(-2, -2));
        }
        segRow.addView(track, new LinearLayout.LayoutParams(-2, -2));
        column.addView(segRow, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(16), dp(6), dp(16), dp(32));
        scroll.addView(listBox, new ScrollView.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    private void reload() {
        File rootDir = roots.get(0);
        listBox.removeAllViews();
        paintTabs();
        List<File> files = new ArrayList<>();
        collect(rootDir, files);

        // 书架出来之后，后台把 mobi/azw3 先转成 epub：点开那本书时不必再干等
        MobiPreconvert.kick(files);

        // 最近：只看被打开过的书，按最后打开时间倒序，最多八本
        if (mode == 0) {
            List<File> recent = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                if (seen(files.get(i)) > 0) {
                    recent.add(files.get(i));
                }
            }
            Collections.sort(recent, new java.util.Comparator<File>() {
                @Override public int compare(File a, File b) { return Long.compare(seen(b), seen(a)); }
            });
            if (recent.size() > 8) {
                recent = recent.subList(0, 8);
            }
            files = recent;
        }

        // the shelf speaks for itself: no path, no caption, just the books
        View spacer = new View(this);
        spacer.setBackgroundColor(Color.TRANSPARENT);
        listBox.addView(spacer, new LinearLayout.LayoutParams(-1, dp(8)));

        if (files.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(8), dp(70), dp(8), dp(8));

            TextView head = new TextView(this);
            head.setText(mode == 0 ? "还没有在读的书" : "没有找到书");
            head.setTextColor(Color.parseColor(COL_TEXT));
            head.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(17));
            head.setGravity(Gravity.CENTER);
            empty.addView(head, new LinearLayout.LayoutParams(-1, -2));

            TextView hint = new TextView(this);
            hint.setText(mode == 0 ? "打开一本书，它就会出现在这里" : "把电子书放进 " + rootDir.getName() + " 文件夹");
            hint.setTextColor(Color.parseColor(COL_DIM));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(13));
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(8), 0, 0);
            empty.addView(hint, new LinearLayout.LayoutParams(-1, -2));
            listBox.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        LinearLayout card = card();
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) {
                View d = new View(this);
                d.setBackgroundColor(Color.parseColor(COL_SEP));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
                lp.leftMargin = dp(72);
                card.addView(d, lp);
            }
            card.addView(row(files.get(i)), new LinearLayout.LayoutParams(-1, -2));
        }
        listBox.addView(card, card.getLayoutParams());

        // 一天读了多少，只用一句话放在书下面，不占一行设置
        long today = todayRead();
        if (today >= 60) {
            TextView stat = new TextView(this);
            stat.setText("今天读了 " + (today / 60) + " 分钟");
            stat.setTextColor(Color.parseColor(COL_STAT));
            stat.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(12));
            stat.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
            slp.topMargin = dp(18);
            listBox.addView(stat, slp);
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor(COL_CARD));
        bg.setCornerRadius(dp(12));
        c.setBackground(bg);
        c.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        c.setLayoutParams(lp);
        return c;
    }

    private View row(final File f) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setMinimumHeight(dp(68));
        wrap.setPadding(dp(16), dp(10), dp(16), dp(10));
        wrap.setBackground(selector());

        // the standing cover: artwork when the cache has it, otherwise the spine
        // block carrying the first character of the title
        FrameLayout cover = new FrameLayout(this);
        // 没有封面时不做一块死灰：由书名定一个稳定的色相，做一张上浅下深的封皮
        float[] hsv = {Math.abs(title(f).hashCode()) % 360, 0.40f, 0.55f};
        int top = Color.HSVToColor(hsv);
        hsv[2] = 0.32f;
        int bottom = Color.HSVToColor(hsv);
        android.graphics.drawable.GradientDrawable coverBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        coverBg.setCornerRadius(dp(5));
        cover.setBackground(coverBg);
        cover.setClipToOutline(true);

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(art, new FrameLayout.LayoutParams(-1, -1));

        TextView initial = new TextView(this);
        initial.setText(spine(f));
        initial.setTextColor(Color.parseColor("#F2F2F7"));
        initial.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(20));
        initial.setTypeface(Typeface.DEFAULT_BOLD);
        initial.setGravity(Gravity.CENTER);
        cover.addView(initial, new FrameLayout.LayoutParams(-1, -1));
        PodCovers.bind(this, f, art, initial);
        LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(dp(42), dp(56));
        coverLp.rightMargin = dp(14);
        wrap.addView(cover, coverLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(title(f));
        name.setTextColor(Color.parseColor(COL_NAME));
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(16));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(name);

        TextView meta = new TextView(this);
        // a book that was read says where it was left, the way a shelf on a player does
        int last = lastPage(f);
        String base = ext(f) + " \u00b7 " + size(f);
        int total = 0;
        try {
            total = getSharedPreferences("PodBookmarks", MODE_PRIVATE).getInt("cnt:" + f.getAbsolutePath(), 0);
        } catch (Throwable ignored) {
        }
        if (last > 0 && total > 1) {
            int pct = Math.max(1, Math.min(99, (int) Math.round(last * 100.0 / total)));
            meta.setText(base + " \u00b7 " + pct + "%");
        } else {
            meta.setText(last > 0 ? base + " \u00b7 \u7b2c " + last + " \u9875" : base);
        }
        meta.setTextColor(Color.parseColor(COL_META));
        meta.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(12));
        meta.setSingleLine(true);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(3), 0, 0);
        textCol.addView(meta);

        wrap.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = new TextView(this);
        arrow.setText("\u203a");
        arrow.setTextColor(Color.parseColor(COL_ARROW));
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(17));
        arrow.setPadding(dp(6), 0, 0, 0);
        wrap.addView(arrow, new LinearLayout.LayoutParams(-2, -2));

        wrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(f); }
        });
        return wrap;
    }

    /** Page the reader left this book on; 0 when it was never opened. */
    private int lastPage(File f) {
        try {
            return getSharedPreferences("PodBookmarks", MODE_PRIVATE).getInt("pos:" + f.getAbsolutePath(), 0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** The title without its extension: the format is already on the second line. */
    private static String title(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        return base.trim().isEmpty() ? n : base.trim();
    }

    /** The first visible character of the title, the way a spine prints it. */
    private static String spine(File f) {
        String base = title(f);
        if (base.isEmpty()) {
            return "?";
        }
        String first = base.substring(0, 1);
        return first.toUpperCase(java.util.Locale.ROOT);
    }

    /** Pressed rows go grey, the way a list on a player does. */
    private android.graphics.drawable.StateListDrawable selector() {
        android.graphics.drawable.StateListDrawable sd = new android.graphics.drawable.StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.ColorDrawable(Color.parseColor("#2E2A1A0E")));
        sd.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return sd;
    }
    private void open(File f) {
        // one book view only: the paged reader. A scrolling mode left over from an older
        // session would send the book to the other viewer, so it is written back first and
        // the paged activity is named outright instead of going through the opener.
        try {
            com.foobnix.model.AppSP.get().readingMode = com.foobnix.model.AppState.READING_MODE_BOOK;
            com.foobnix.model.AppSP.get().save();
        } catch (Throwable ignored) {
        }
        try {
            getSharedPreferences("PodBookmarks", MODE_PRIVATE).edit()
                    .putLong("seen:" + f.getAbsolutePath(), System.currentTimeMillis()).apply();
        } catch (Throwable ignored) {
        }
        String[] targets = {"com.foobnix.pdf.search.activity.HorizontalViewActivity", "com.foobnix.OpenerActivity"};
        for (String target : targets) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setClassName(getPackageName(), target);
                i.setDataAndType(Uri.fromFile(f), mime(f));
                i.putExtra("EXTRA_FILE", f.getAbsolutePath());
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Throwable ignored) {
            }
        }
        android.widget.Toast.makeText(this, "无法打开：" + f.getName(), android.widget.Toast.LENGTH_SHORT).show();
    }

    /**
     * 退出阅读：阅读器自己收尾，再把桌面（音乐主界面）唤到前台。
     * 合并后没有悬浮返回条，菜单键/书库左上的返回就是唯一出口。
     */
    private void finishToMusic() {
        finish();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setClassName(getPackageName(), "io.github.xejohv.podclassicxzs.activity.MainActivity");
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        } catch (Throwable ignored) {
        }
    }

    private static void collect(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] all = dir.listFiles();
        if (all == null) return;
        for (File f : all) {
            if (f.isDirectory()) continue;
            // the shelf lists whatever the reader can actually open: the format table is the
            // single source of truth, so a new format never has to be added in two places
            if (org.ebookdroid.BookType.isSupportedExtByPath(f.getName())) out.add(f);
        }
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    }

    private static String ext(File f) {
        String n = f.getName();
        int p = n.lastIndexOf('.');
        return p >= 0 ? n.substring(p + 1).toLowerCase() : "";
    }

    /** Mime type the reader registered for this format; unknown names fall back to any. */
    private static String mime(File f) {
        try {
            org.ebookdroid.BookType t = org.ebookdroid.BookType.getByUri(f.getName());
            if (t != null && t != org.ebookdroid.BookType.NULL) {
                String m = t.getFirstMimeTime();
                if (m != null && !m.isEmpty()) return m;
            }
        } catch (Throwable ignored) {
        }
        return "*/*";
    }

    private static String size(File f) {
        long kb = Math.max(1, f.length() / 1024);
        return kb >= 1024 ? String.format("%.1f MB", kb / 1024.0) : kb + " KB";
    }

    /** 选中的那一格填满，未选中的只是一行字。 */
    private void paintTabs() {
        for (int i = 0; i < segTabs.length; i++) {
            TextView tab = segTabs[i];
            if (tab == null) continue;
            boolean on = i == mode;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(7));
            bg.setColor(on ? Color.parseColor("#FFF6E6") : Color.TRANSPARENT);
            tab.setBackground(bg);
            tab.setTextColor(Color.parseColor(on ? "#4A3220" : "#F0DDBD"));
            tab.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    /** 这本书最后一次被打开的时间；0 表示从没打开过。 */
    /** 今天已经读了多少秒；阅读器离开前台时结算。 */
    private long todayRead() {
        try {
            String day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                    .format(new java.util.Date());
            return getSharedPreferences("PodBookmarks", MODE_PRIVATE).getLong("read:" + day, 0L);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private long seen(File f) {
        try {
            return getSharedPreferences("PodBookmarks", MODE_PRIVATE).getLong("seen:" + f.getAbsolutePath(), 0L);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}