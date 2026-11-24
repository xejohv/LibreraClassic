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

    /** One folder, decided here: everything the reader shows lives in /sdcard/Download/Books (plus旧位置兼容). */
    private final List<File> roots = new ArrayList<>();
    private LinearLayout listBox;
    /** 0 = 最近（打开过的），1 = 全部，2 = 回收站。书库打开就落在「最近」。 */
    private int mode = 0;
    private final TextView[] segTabs = new TextView[3];
    /** 顶栏右上角的「导入」：把 Download 里的电子书搬进 Download/Books。 */
    private TextView importBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // MTP 根目录不支持“新建文件夹”（自建目录会被登记成普通文件、在电脑上只显示一个空白图标），
        // 所以书库挂在系统已有的 Download 下面的二级文件夹：Download/Books，电脑上能正常进。
        roots.add(new File(Environment.getExternalStorageDirectory(), "Download/Books"));
        // 兼容旧位置：根目录 Books、Documents/Books 里的书也照样能被看到
        roots.add(new File(Environment.getExternalStorageDirectory(), "Books"));
        roots.add(new File(Environment.getExternalStorageDirectory(), "Documents/Books"));
        setContentView(buildUi());
        PodSkin.disableSoftKeyboard(this);
        PodCovers.prune(this);
        reload();
    }

    /** 实体快门键：在书库里按一下，回音乐界面（跟左上角「音乐」同一个出口）。 */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_CAMERA || keyCode == android.view.KeyEvent.KEYCODE_FOCUS) {
            if (event.getRepeatCount() == 0) {
                if (keyCode == android.view.KeyEvent.KEYCODE_CAMERA) {
                    podFocusSawCamera = true;
                    podKeyHandler.removeCallbacks(podFocusFallback);
                    podKeyHandler.removeCallbacks(podFocusCommit);
                    android.util.Log.d("PodKey", "lib CAMERA down");
                    // 从书库离开 = 阅读后台清零
                    libraryToMusic();
                } else {
                    podFocusSawCamera = false;
                    podFocusToggled = false;
                    podKeyHandler.removeCallbacks(podFocusFallback);
                    podKeyHandler.postDelayed(podFocusFallback, 1200);
                    android.util.Log.d("PodKey", "lib FOCUS down");
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_FOCUS || keyCode == android.view.KeyEvent.KEYCODE_CAMERA) {
            if (keyCode == android.view.KeyEvent.KEYCODE_FOCUS) {
                podKeyHandler.removeCallbacks(podFocusFallback);
                boolean cameraSeen = podFocusSawCamera;
                podFocusSawCamera = false;
                android.util.Log.d("PodKey", "lib FOCUS up cameraSeen=" + cameraSeen);
                if (!cameraSeen && !podFocusToggled) {
                    podKeyHandler.postDelayed(podFocusCommit, 180);
                }
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 人在书库就等于没在看书：从书库离开音乐时阅读后台要清掉，下次快门进书库
        try {
            getSharedPreferences("PodSwitch", MODE_MULTI_PROCESS).edit()
                    .putBoolean("haveBook", false).remove("book").apply();
        } catch (Throwable ignored) {
        }
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
        title.setText(t("阅读", "Books"));
        title.setTextColor(Color.parseColor(COL_TEXT));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(17));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new FrameLayout.LayoutParams(-1, -1));

        TextView back = new TextView(this);
        back.setText(t("\u2039 音乐", "\u2039 Music"));
        back.setTextColor(Color.parseColor(COL_RED));
        back.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(16));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(16), 0, dp(16), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishToMusic(); }
        });
        bar.addView(back, new FrameLayout.LayoutParams(-2, -1, Gravity.LEFT));

        // 右上角：一键把 Download 里的电子书搬进书库
        TextView imp = new TextView(this);
        imp.setTextColor(Color.parseColor(COL_RED));
        imp.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(15));
        imp.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        imp.setPadding(dp(10), 0, dp(16), 0);
        imp.setBackground(selector());
        imp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startImport(); }
        });
        bar.addView(imp, new FrameLayout.LayoutParams(-2, -1, Gravity.RIGHT));
        importBtn = imp;

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

        String[] names = {t("最近", "Recent"), t("全部", "All"), t("回收站", "Trash")};
        for (int i = 0; i < 3; i++) {
            final int which = i;
            TextView tab = new TextView(this);
            tab.setText(names[i]);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(13));
            tab.setGravity(Gravity.CENTER);
            tab.setMinWidth(dp(56));
            tab.setPadding(dp(8), dp(6), dp(8), dp(6));
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
        refreshImportLabel();
        // 回收站：满 7 天的在这里清掉，剩下的按删除时间列出
        io.github.xejohv.classic.Trash.purge();
        if (mode == 2) {
            renderTrash();
            return;
        }
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
            head.setText(mode == 0 ? t("还没有在读的书", "Nothing in progress") : t("没有找到书", "No books found"));
            head.setTextColor(Color.parseColor(COL_TEXT));
            head.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(17));
            head.setGravity(Gravity.CENTER);
            empty.addView(head, new LinearLayout.LayoutParams(-1, -2));

            TextView hint = new TextView(this);
            hint.setText(mode == 0 ? t("打开一本书，它就会出现在这里", "Open a book and it shows up here")
                    : t("把电子书放进 " + rootDir.getName() + " 文件夹", "Drop e-books into the " + rootDir.getName() + " folder"));
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
            stat.setText(t("今天读了 " + (today / 60) + " 分钟", "Read " + (today / 60) + " min today"));
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
        // 长按不是菜单，是删除：删之前问一句，删之后进回收站，7 天内都捞得回来
        wrap.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                confirmTrash(f);
                return true;
            }
        });
        return wrap;
    }

    private void confirmTrash(final File f) {
        podAlert(t("移到回收站？", "Move to Trash?"),
                t("《" + title(f) + "》会移进回收站，7 天后自动删除；这段时间里可以在「回收站」里恢复。",
                        "《" + title(f) + "》 will move to Trash and be deleted in 7 days. You can restore it from Trash meanwhile."),
                t("移到回收站", "Move to Trash"), true,
                new Runnable() {
                    @Override public void run() {
                        File slot = io.github.xejohv.classic.Trash.moveIn(f);
                        toast(slot == null ? t("删除失败：文件被占用或没有权限", "Could not delete: file is in use or not permitted") : t("已移到回收站，7 天后删除", "Moved to Trash. Removed after 7 days"));
                        reload();
                    }
                });
    }

    /** 回收站列表：点按恢复，长按立刻删除。 */
    private void renderTrash() {
        java.util.List<File> items = io.github.xejohv.classic.Trash.list();
        View spacer = new View(this);
        spacer.setBackgroundColor(Color.TRANSPARENT);
        listBox.addView(spacer, new LinearLayout.LayoutParams(-1, dp(8)));

        if (items.isEmpty()) {
            TextView head = new TextView(this);
            head.setText(t("回收站是空的", "Trash is empty"));
            head.setTextColor(Color.parseColor(COL_TEXT));
            head.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(17));
            head.setGravity(Gravity.CENTER);
            head.setPadding(dp(8), dp(70), dp(8), 0);
            listBox.addView(head, new LinearLayout.LayoutParams(-1, -2));

            TextView hint = new TextView(this);
            hint.setText(t("在书库里长按一本书，就会移到这里", "Long-press a book in the library to move it here"));
            hint.setTextColor(Color.parseColor(COL_DIM));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(13));
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(dp(8), dp(8), dp(8), 0);
            listBox.addView(hint, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        LinearLayout card = card();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                View d = new View(this);
                d.setBackgroundColor(Color.parseColor(COL_SEP));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
                lp.leftMargin = dp(16);
                card.addView(d, lp);
            }
            card.addView(trashRow(items.get(i)), new LinearLayout.LayoutParams(-1, -2));
        }
        listBox.addView(card, card.getLayoutParams());

        TextView note = new TextView(this);
        note.setText(t("点一下恢复，长按立刻删除。满 7 天自动清理。", "Tap to restore, long-press to delete now. Auto-cleaned after 7 days."));
        note.setTextColor(Color.parseColor(COL_STAT));
        note.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(12));
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(18);
        listBox.addView(note, nlp);
    }

    private View trashRow(final File slot) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setMinimumHeight(dp(60));
        wrap.setPadding(dp(16), dp(12), dp(16), dp(12));
        wrap.setBackground(selector());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        String full = io.github.xejohv.classic.Trash.nameOf(slot);
        int dot = full.lastIndexOf('.');
        name.setText(dot > 0 ? full.substring(0, dot) : full);
        name.setTextColor(Color.parseColor(COL_NAME));
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(15));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(name);

        TextView meta = new TextView(this);
        int days = io.github.xejohv.classic.Trash.daysLeft(slot);
        meta.setText(days <= 0 ? t("即将清理", "Cleaning up") : t("还剩 " + days + " 天", days + " days left"));
        meta.setTextColor(Color.parseColor(COL_META));
        meta.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(12));
        meta.setPadding(0, dp(3), 0, 0);
        col.addView(meta);

        wrap.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = new TextView(this);
        arrow.setText("\u21BA");
        arrow.setTextColor(Color.parseColor(COL_ARROW));
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(17));
        wrap.addView(arrow, new LinearLayout.LayoutParams(-2, -2));

        wrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmRestore(slot); }
        });
        wrap.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                confirmDrop(slot);
                return true;
            }
        });
        return wrap;
    }

    private void confirmRestore(final File slot) {
        podAlert(t("恢复这本书？", "Restore this book?"),
                t("《" + io.github.xejohv.classic.Trash.nameOf(slot) + "》会放回书库。",
                        "《" + io.github.xejohv.classic.Trash.nameOf(slot) + "》 will go back to the library."),
                t("恢复", "Restore"), false,
                new Runnable() {
                    @Override public void run() {
                        boolean ok = io.github.xejohv.classic.Trash.restore(slot);
                        toast(ok ? t("已恢复到书库", "Restored to library") : t("恢复失败", "Restore failed"));
                        reload();
                    }
                });
    }

    private void confirmDrop(final File slot) {
        podAlert(t("立刻删除？", "Delete now?"),
                t("《" + io.github.xejohv.classic.Trash.nameOf(slot) + "》将被彻底删除，无法恢复。",
                        "《" + io.github.xejohv.classic.Trash.nameOf(slot) + "》 will be deleted permanently."),
                t("删除", "Delete"), true,
                new Runnable() {
                    @Override public void run() {
                        io.github.xejohv.classic.Trash.drop(slot);
                        reload();
                    }
                });
    }

    /** 书库自己画的那几处文字跟系统语言走（英文界面里不再夹中文按钮） */
    private static String t(String zh, String en) {
        return java.util.Locale.getDefault().getLanguage().startsWith("en") ? en : zh;
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    /**
     * 应用自己的确认框：奶油色圆角卡片 + 一条发丝线 + 两枚文字按钮，
     * 跟书库同一套纸色，不走系统 AlertDialog 那套黑框大字。
     */
    private void podAlert(String title, String message, String okLabel, boolean destructive, final Runnable onOk) {
        final android.app.Dialog d = new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.parseColor(COL_NAME));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(17));
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(dp(22), dp(22), dp(22), 0);
        root.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView m = new TextView(this);
        m.setText(message);
        m.setTextColor(Color.parseColor(COL_META));
        m.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(13));
        m.setLineSpacing(dp(4), 1f);
        m.setPadding(dp(22), dp(10), dp(22), dp(20));
        root.addView(m, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(this);
        line.setBackgroundColor(Color.parseColor(COL_SEP));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        TextView cancel = new TextView(this);
        cancel.setText(t("取消", "Cancel"));
        cancel.setTextColor(Color.parseColor(COL_META));
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(15));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(14), 0, dp(14));
        cancel.setBackground(selector());
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        bar.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor(COL_SEP));
        bar.addView(divider, new LinearLayout.LayoutParams(dp(1), -1));

        TextView ok = new TextView(this);
        ok.setText(okLabel);
        ok.setTextColor(Color.parseColor(destructive ? "#B3261E" : "#7A5326"));
        ok.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(15));
        ok.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(0, dp(14), 0, dp(14));
        ok.setBackground(selector());
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                onOk.run();
            }
        });
        bar.addView(ok, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
        card.setColor(Color.parseColor(COL_CARD));
        card.setCornerRadius(dp(14));
        root.setBackground(card);
        root.setClipToOutline(true);

        d.setContentView(root);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.5f;
            int screen = getResources().getDisplayMetrics().widthPixels;
            lp.width = Math.min(dp(330), screen - dp(56));
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            w.setAttributes(lp);
        }
        d.show();
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
    // —— 实体快门：半按 / 全按的区分 ——
    // 真机上全按会先发 FOCUS(DOWN) 再发 CAMERA。若在 FOCUS 按下时就切歌，全按会顺带把播放切一次。
    // 因此半按改成「抬起后确认」：FOCUS 抬起且期间没出现 CAMERA 才切歌；出现 CAMERA 就是全按。
    // 兜底 1.2s：个别固件只发 FOCUS 按下不发抬起时，按住不放也能切。
    private boolean podFocusSawCamera;
    private boolean podFocusToggled;
    private final android.os.Handler podKeyHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable podFocusCommit = new Runnable() {
        @Override public void run() {
            if (!podFocusSawCamera && !podFocusToggled) {
                podFocusToggled = true;
                android.util.Log.d("PodKey", "focus commit");
                toggleMusic();
            }
        }
    };
    private final Runnable podFocusFallback = new Runnable() {
        @Override public void run() {
            if (!podFocusSawCamera && !podFocusToggled) {
                podFocusToggled = true;
                android.util.Log.d("PodKey", "focus fallback (no up event)");
                toggleMusic();
            }
        }
    };

    /** 半按快门：切一下播放/暂停（阅读器进程直接向启动器的播放服务发指令）。 */
    private void toggleMusic() {
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), "io.github.xejohv.podclassicxzs.service.MediaService");
            i.setAction("action_play_pause");
            startService(i);
        } catch (Throwable ignored) {
        }
    }

    /** singleTask 复用旧实例时也要把书架重画一遍，避免“点阅读进来是空的/停在旧状态”。 */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        try {
            reload();
        } catch (Throwable ignored) {
        }
    }

    /** 从书库返回音乐：书库和留在后台的书页一起清掉，阅读任务不残留。 */
    private void libraryToMusic() {
        try {
            getSharedPreferences("PodSwitch", MODE_MULTI_PROCESS).edit()
                    .putBoolean("haveBook", false).remove("book").apply();
        } catch (Throwable ignored) {
        }
        finishAffinity();
        try {
            // 纯组件启动：带 ACTION_MAIN/CATEGORY_HOME 会把桌面拽去“主界面任务”，
            // 之后再用 NEW_TASK 拉阅读器就可能落在音乐界面背后（点了像没反应）
            Intent home = new Intent();
            home.setClassName(getPackageName(), "io.github.xejohv.podclassicxzs.activity.MainActivity");
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        } catch (Throwable ignored) {
        }
    }

    private void finishToMusic() {
        finish();
        try {
            // 纯组件启动：带 ACTION_MAIN/CATEGORY_HOME 会把桌面拽去“主界面任务”，
            // 之后再用 NEW_TASK 拉阅读器就可能落在音乐界面背后（点了像没反应）
            Intent home = new Intent();
            home.setClassName(getPackageName(), "io.github.xejohv.podclassicxzs.activity.MainActivity");
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        } catch (Throwable ignored) {
        }
    }

    /** 电脑上能正常打开的落书位置：Download 根目录（MTP 里就是普通文件夹）。 */
    private static File downloadDir() {
        return new File(Environment.getExternalStorageDirectory(), "Download");
    }

    /** Download 根目录里还没搬进书库、但阅读器认得的所有电子书。 */
    private static List<File> pendingInDownload() {
        List<File> out = new ArrayList<>();
        File[] all = downloadDir().listFiles();
        if (all == null) return out;
        for (File f : all) {
            if (f.isDirectory()) continue;
            if (org.ebookdroid.BookType.isSupportedExtByPath(f.getName())) out.add(f);
        }
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    private void refreshImportLabel() {
        if (importBtn == null) return;
        int n = pendingInDownload().size();
        importBtn.setText(n > 0 ? t("导入 " + n, "Import " + n) : t("导入", "Import"));
        importBtn.setTextColor(Color.parseColor(n > 0 ? COL_RED : COL_DIM));
    }

    private void startImport() {
        final List<File> pending = pendingInDownload();
        if (pending.isEmpty()) {
            toast(t("Download 里没有可导入的电子书", "No e-books in Download"));
            return;
        }
        podAlert(t("导入 " + pending.size() + " 本书？", "Import " + pending.size() + " book(s)?"),
                t("会从 Download 移进 Download/Books，然后出现在书架上。同名文件自动加序号。",
                        "They move from Download into Download/Books and show up on the shelf. Same-name files get a number."),
                t("导入", "Import"), false,
                new Runnable() {
                    @Override public void run() {
                        int ok = doImport(pending);
                        toast(ok > 0 ? t("已导入 " + ok + " 本", "Imported " + ok) : t("导入失败：文件被占用或没有权限", "Import failed: file in use or not permitted"));
                        reload();
                    }
                });
    }

    /** 搬书：先改名，跨卷改不动就拷贝再删；最后按新位置重新登记媒体库。 */
    private int doImport(List<File> pending) {
        File books = roots.get(0);
        if (!books.isDirectory() && !books.mkdirs()) {
            books = new File(Environment.getExternalStorageDirectory(), "Books");
            books.mkdirs();
        }
        int ok = 0;
        for (File src : pending) {
            File dst = uniqueIn(books, src.getName());
            if (moveFile(src, dst)) {
                ok++;
                try {
                    android.media.MediaScannerConnection.scanFile(this,
                            new String[]{src.getAbsolutePath(), dst.getAbsolutePath()}, null, null);
                } catch (Throwable ignored) {
                }
            }
        }
        return ok;
    }

    private static File uniqueIn(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String tail = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            f = new File(dir, base + " (" + i + ")" + tail);
            if (!f.exists()) return f;
        }
        return f;
    }

    private static boolean moveFile(File src, File dst) {
        if (src.renameTo(dst)) return true;
        java.io.InputStream in = null;
        java.io.OutputStream out = null;
        try {
            in = new java.io.FileInputStream(src);
            out = new java.io.FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } catch (Throwable t) {
            try { dst.delete(); } catch (Throwable ignored) {
            }
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {
            }
            try { if (out != null) out.close(); } catch (Throwable ignored) {
            }
        }
        try {
            return src.delete();
        } catch (Throwable t) {
            return false;
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