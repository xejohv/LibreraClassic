package io.github.xejohv.classic;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.foobnix.model.AppState;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * The page of a dedicated player, iPod style: a book and two thin bars.
 *
 * The reader ships with a full Android toolbar of its own - a title row, a row of icons,
 * a footer of eight buttons, a shelf panel and page counters. None of that belongs on a
 * player, so it is hidden whole and replaced with what an iPod shows: the title, the page
 * number, a hairline progress bar, and one list menu opened by a tap in the middle of the
 * page. Settings live in that same list, one level down, as plain rows instead of sliders.
 */
public final class PodReader {

    private static final String TAG = "pod_reader_shell";
    private static final int RED = Color.parseColor("#FF3B30");

    // 这一屏的配色不再写死黑天：白纸配黑字、黑纸配白字，怎么调都还是同一个应用。
    // 下面这些值由 theme(a) 在每次开菜单、每次重画外壳前重算，白天走白卡片、夜里走黑卡片。
    private static int BG = Color.BLACK;
    private static int FG = Color.WHITE;
    private static int DIM = Color.parseColor("#8E8E93");
    private static int LINE = Color.parseColor("#1F1F1F");
    private static int PRESS = Color.parseColor("#2C2C2E");
    private static int CARD = Color.parseColor("#1C1C1E");
    private static int SEP = Color.parseColor("#38383A");
    private static int SHEET = Color.BLACK;
    private static int SEG_TRACK = Color.parseColor("#2C2C2E");
    private static int SEG_ON = Color.parseColor("#636366");
    private static int SEG_OFF = Color.parseColor("#C7C7CC");
    private static int TRACK = Color.parseColor("#48484A");

    /** 阅读纸色五套：白纸、米黄、护眼绿、夜间、深灰；前三套算白天，后两套算夜里。 */
    static final int[] PAPER_BG = {0xFFFFFFFF, 0xFFF5EDDA, 0xFFE4EEE1, 0xFF000000, 0xFF1C1C1E};
    static final int[] PAPER_TX = {0xFF1C1C1E, 0xFF3B3226, 0xFF24302A, 0xFFE6E6E6, 0xFFD8D8DC};
    private static final int PAPER_DEFAULT = 3;

    /** Everything the reader draws around the page: title row, icon row, footer, shelf, counters. */
    private static final int[] OLD_CHROME = {
            R.id.actionBar, R.id.bottomBar, R.id.bottomPanel, R.id.pageshelper,
            R.id.pagesBookmark, R.id.floatingBookmark, R.id.pannelBookTitle,
            R.id.musicButtonPanel, R.id.bookCoverParent, R.id.bookClose,
            R.id.showRewardVideo, R.id.showRewardVideoText, R.id.showRewardVideoButton,
            R.id.toastBrightnessText, R.id.ttsActive, R.id.ttsActive1, R.id.ttsActivePage,
            R.id.drawDock, R.id.pagesTime, R.id.pagesPower, R.id.pagesCountIndicator,
            R.id.seekLine, R.id.seekBar1, R.id.currentSeek, R.id.maxSeek
    };

    /** Which root was last given which backdrop colour, so the ticker does not repaint it. */
    private static java.lang.ref.WeakReference<View> BACKDROP_ROOT;
    /** 「这是菜单卡片还是整页」：让它退场时知道该下滑还是右滑。 */
    private static final java.util.Map<View, String> KINDS = new java.util.WeakHashMap<>();
    /** 正在退场的遮罩，避免连点时重复动画、重复 removeView。 */
    private static final java.util.Set<View> CLOSING =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
    private static int BACKDROP_COLOR = Integer.MIN_VALUE;
    /** The controller of the page on screen; kept so a menu row can re-lay the text. */
    private static java.lang.ref.WeakReference<DocumentController> CONTROLLER;

    private PodReader() {
    }

    // ---------------------------------------------------------------- 纸色与菜单配色
    // 纸色是用户挑的，菜单配色跟着纸色走：挑浅色纸就是白天，挑深色纸就是夜里。
    // 以前这一屏写死黑底，白纸书里弹出一块黑板；现在页纸、页边、菜单都由同一个纸色开关决定。
    private static android.content.SharedPreferences podPrefs(Activity a) {
        return a.getSharedPreferences("PodClassicReader", android.content.Context.MODE_PRIVATE);
    }

    /** 纸色下标：0 白纸 1 米黄 2 护眼 3 夜间 4 深灰；前三套算白天。 */
    static int paper(Activity a) {
        int p = podPrefs(a).getInt("paper", PAPER_DEFAULT);
        return p < 0 || p >= PAPER_BG.length ? PAPER_DEFAULT : p;
    }

    static boolean dayPaper(Activity a) {
        return paper(a) < 3;
    }

    /** 把挑好的纸色写回阅读引擎：正文色与纸色成对，引擎重排后即是用户看到的纸。 */
    private static void applyPaper(Activity a) {
        int idx = paper(a);
        AppState s = AppState.get();
        if (idx < 3) {
            s.colorDayBg = PAPER_BG[idx];
            s.colorDayText = PAPER_TX[idx];
            // 页底 = 纸色。版式书（PDF/漫画）的页面画布用的正是这个色，
            // 不跟着换就会在纸外面再镶一层浅灰，三层颜色
            s.colorDayForeground = PAPER_BG[idx];
        } else {
            s.colorNigthBg = PAPER_BG[idx];
            s.colorNigthText = PAPER_TX[idx];
            s.colorNigthForeground = PAPER_BG[idx];
        }
        // 落盘：正文页的颜色是引擎在「打开书的那一刻」读的，下一次进来必须读到同一套纸色
        try {
            s.save(a);
        } catch (Throwable ignored) {
        }
        podPrefs(a).edit().putBoolean("dayMode", idx < 3).apply();
    }

    /** 一屏配色重算：白天白卡片黑字，夜里黑卡片白字，菜单与纸永远同一套明暗。 */
    private static void theme(Activity a) {
        boolean day = dayPaper(a);
        BG = day ? Color.WHITE : Color.BLACK;
        FG = day ? Color.parseColor("#1C1C1E") : Color.WHITE;
        DIM = day ? Color.parseColor("#6C6C70") : Color.parseColor("#8E8E93");
        LINE = day ? Color.parseColor("#D8D8DC") : Color.parseColor("#1F1F1F");
        PRESS = day ? Color.parseColor("#E5E5EA") : Color.parseColor("#2C2C2E");
        CARD = day ? Color.WHITE : Color.parseColor("#1C1C1E");
        SEP = day ? Color.parseColor("#D1D1D6") : Color.parseColor("#38383A");
        SHEET = day ? Color.parseColor("#F2F2F7") : Color.BLACK;
        SEG_TRACK = day ? Color.parseColor("#E9E9EB") : Color.parseColor("#2C2C2E");
        SEG_ON = day ? Color.WHITE : Color.parseColor("#636366");
        SEG_OFF = day ? Color.parseColor("#3C3C43") : Color.parseColor("#C7C7CC");
        TRACK = day ? Color.parseColor("#C7C7CC") : Color.parseColor("#48484A");
    }

    /** 页码不炫底、不描边，只按纸的明暗取色：浅纸压深灰、深纸提亮灰，两种纸上都读得出。 */
    private static int pageText(Activity a) {
        int bg = PAPER_BG[paper(a)];
        try {
            bg = com.foobnix.pdf.info.wrapper.MagicHelper.getBgColor();
        } catch (Throwable ignored) {
        }
        double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
        return lum > 0.55 ? Color.parseColor("#8A8A8E") : Color.parseColor("#B0B0B6");
    }

    public static void install(Activity a, DocumentController dc) {
        if (a == null) {
            return;
        }
        DocumentController known = dc != null ? dc : resolveController(a);
        // 苹果阅读器的手感：点左边翻上一页，点右边翻下一页，中间那一下才开菜单。
        try {
            com.foobnix.model.AppState.get().tapZoneLeft = com.foobnix.model.AppState.TAP_PREV_PAGE;
            com.foobnix.model.AppState.get().tapZoneRight = com.foobnix.model.AppState.TAP_NEXT_PAGE;
            com.foobnix.model.AppState.get().tapZoneTop = com.foobnix.model.AppState.TAP_DO_NOTHING;
            com.foobnix.model.AppState.get().tapZoneBottom = com.foobnix.model.AppState.TAP_DO_NOTHING;
        } catch (Throwable ignored) {
        }
        // 阅读正文统一左对齐，短行不再被拉伸（英文单词间距更接近 iOS 的观感）
        try {
            com.foobnix.pdf.info.model.BookCSS.get().textAlign =
                    com.foobnix.pdf.info.model.BookCSS.TEXT_ALIGN_LEFT;
        } catch (Throwable ignored) {
        }
        if (known != null) {
            CONTROLLER = new java.lang.ref.WeakReference<>(known);
        }
        LAST_SHOWN_PAGE = -1;
        View root = a.getWindow().getDecorView();
        if (!(root instanceof FrameLayout)) {
            return;
        }
        FrameLayout frame = (FrameLayout) root;
        // A one-time seed: the stock librarian defaults (17, or 24 on a large screen) lay out
        // far too few characters per line on a 1080x1920 iPod panel. Seed a size that puts
        // about 22 characters on a line, once, and never fight the reader's own slider after.
        try {
            android.content.SharedPreferences seed = a.getSharedPreferences("PodClassicReader", android.content.Context.MODE_PRIVATE);
            if (!seed.getBoolean("sizeFixV17", false)) {
                // 一次到底的基线：正文按 11 起排（这台 5 寸屏上大约一行 16～17 个字），
                // 之后只认菜单和右边缘手势，不再每次进来重排一次。
                BookCSS.get().fontSizeSp = 11;
                BookCSS.get().fontSizeSpFoliate = 11;
                BookCSS.get().save(a);
                seed.edit().putBoolean("textSizeSeeded", true)
                        .putBoolean("sizeFixV17", true).apply();
            }
        } catch (Throwable ignored) {
        }
        applyPaper(a);
        theme(a);
        hide(a, OLD_CHROME);
        stripNativeChrome(a);
        blackoutImageBooks(a);
        applyOriginalColors(a);
        insetPages(a);
        paintBackdrop(a);

        View shell = frame.findViewWithTag(TAG);
        if (shell == null) {
            shell = buildShell(a, dc);
            shell.setTag(TAG);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
            frame.addView(shell, lp);
            startTicker(a, shell);
            final long openedAt = android.os.SystemClock.elapsedRealtime();
            shell.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                }

                @Override public void onViewDetachedFromWindow(View v) {
                    addReadSeconds(a, (android.os.SystemClock.elapsedRealtime() - openedAt) / 1000);
                }
            });
        }
        refresh(a, shell);
    }

    /** 版式书按原色、文本书跟随白天/夜间；PodSkin 每次套主题都来问这里。 */
    private static boolean DAY_SCHEME;

    static boolean dayScheme() {
        return DAY_SCHEME;
    }

    /**
     * PDF、DjVu、漫画是「版式书」，页面本身就是一张成品纸，夜里也按原样显示；
     * 只有可重排的文本才跟随用户选的白天/夜间。不然公文会被渲染成黑底青字。
     */
    public static void applyOriginalColors(Activity a) {
        try {
            String path = com.foobnix.model.AppSP.get().lastBookPath;
            if (path == null && a.getIntent() != null && a.getIntent().getData() != null) {
                path = a.getIntent().getData().getPath();
            }
            if (path == null) {
                return;
            }
            String p = path.toLowerCase(java.util.Locale.ROOT);
            boolean layout = p.endsWith(".pdf") || p.endsWith(".djvu") || p.endsWith(".xps")
                    || p.endsWith(".cbz") || p.endsWith(".cbr") || p.endsWith(".zip")
                    || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png");
            boolean day = a.getSharedPreferences("PodClassicReader", android.content.Context.MODE_PRIVATE)
                    .getBoolean("dayMode", false);
            DAY_SCHEME = layout || day;
            com.foobnix.model.AppState.get().isDayNotInvert = layout || day;
            paintBackdrop(a);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 这三条原生外壳不是要藏起来，而是从视图树里摘掉：顶部书名条、底部图标条、封面缩略图条
     * 都由本应用的底部卡片代替，留着只会挡住正文、还各自吃掉一块点击热区。
     * 页脚那层（含页码进度条）留着，它是页码数据的来源，只做不可见处理。
     */
    private static void stripNativeChrome(Activity a) {
        int[] bars = {R.id.actionBar, R.id.bottomBar, R.id.musicButtonPanel,
                R.id.pannelBookTitle, R.id.bookClose, R.id.pagesBookmark, R.id.floatingBookmark};
        for (int id : bars) {
            View v = a.findViewById(id);
            if (v == null) {
                continue;
            }
            // 屏幕上彻底消失：不绘制、不占高度、不吃触摸。
            // 仍留在视图树里，因为页码条/缩略图列表还被阅读引擎引用，摘掉会空指针。
            v.setVisibility(View.GONE);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.height != 0) {
                lp.height = 0;
                v.setLayoutParams(lp);
            }
        }
    }
    /** Keeps the two bars truthful after a page turn. */
    public static void refresh(Activity a) {
        applyOriginalColors(a);
        View root = a == null ? null : a.getWindow().getDecorView();
        if (!(root instanceof ViewGroup)) {
            return;
        }
        View shell = ((ViewGroup) root).findViewWithTag(TAG);
        if (shell != null) {
            refresh(a, shell);
        }
    }

    /** Keeps the page clear of the two thin bars instead of letting the text run under them. */
    private static void insetPages(Activity a) {
        View pager = a.findViewById(R.id.pager2);
        if (pager == null) {
            return;
        }
        ViewGroup.LayoutParams raw = pager.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
        lp.topMargin = dp(a, 14);
        lp.bottomMargin = dp(a, 30);
        pager.setLayoutParams(lp);
        pager.setBackgroundColor(PAPER_BG[paper(a)]);
    }

    /**
     * 版式书（PDF/漫画）按原色显示，底色只在这两类上压成白或黑。
     *
     * 这里曾经无条件写 colorDayBg = 白：结果 EPUB 换了纸色也只在页边生效、正文页永远是白纸——
     * 引擎打开书时读到的正是这里压下去的白。文本书走到这里就原样返回，纸色由 applyPaper 给。
     */
    private static void blackoutImageBooks(Activity a) {
        try {
            String path = com.foobnix.model.AppSP.get().lastBookPath;
            if (path == null && a != null && a.getIntent() != null && a.getIntent().getData() != null) {
                path = a.getIntent().getData().getPath();
            }
            if (path == null) {
                return;
            }
            android.util.Log.d("PodReader", "book path " + path);
            String p = path.toLowerCase(java.util.Locale.ROOT);
            // 版式书不再另压一层底色：页边、页面画布、纸三者同色，
            // 屏幕上就只剩文档本身那一种颜色（以前这里压白，叠出三层色）
            if (p.endsWith(".pdf") || p.endsWith(".djvu") || p.endsWith(".xps")) {
                // 页面画布与页边都纯白：PDF 里“白”是透明，画布一旦染色，整页就跟着变色
                AppState s = AppState.get();
                s.colorDayBg = 0xFFFFFFFF;
                s.colorDayForeground = 0xFFFFFFFF;
                s.colorNigthBg = 0xFFFFFFFF;
                s.colorNigthForeground = 0xFFFFFFFF;
                android.util.Log.d("PodReader", "pdf original colors " + String.format(java.util.Locale.ROOT, "%08X %08X", s.colorDayBg, s.colorNigthBg));
                return;
            }
            // 漫画只有一张图：夜里纯黑、白天纯白，页边不再出现比图更浅的灰带
            if (p.endsWith(".cbz") || p.endsWith(".cbr") || p.endsWith(".zip")
                    || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png")) {
                AppState s = AppState.get();
                int idx = paper(a);
                s.colorDayBg = PAPER_BG[idx];
                s.colorDayForeground = 0xFFFFFFFF;
                s.colorNigthBg = PAPER_BG[idx];
                s.colorNigthForeground = 0xFFFFFFFF;
            }
        } catch (Throwable ignored) {
        }
    }

    /** In daylight the page is white, so nothing behind it may stay black. */
    private static void paintBackdrop(Activity a) {
        // 背板就是纸色；翻页时页边那道缝不再露出另一种颜色。
        // 版式书例外：页面本身是原色白，背板必须同为白，否则白页镶一圈彩边
        int bg = PAPER_BG[paper(a)];
        try {
            String path = com.foobnix.model.AppSP.get().lastBookPath;
            if (path == null && a.getIntent() != null && a.getIntent().getData() != null) {
                path = a.getIntent().getData().getPath();
            }
            if (path != null) {
                String q = path.toLowerCase(java.util.Locale.ROOT);
                if (q.endsWith(".pdf") || q.endsWith(".djvu") || q.endsWith(".xps")) {
                    bg = 0xFFFFFFFF;
                }
            }
        } catch (Throwable ignored) {
        }
        View root = a.getWindow().getDecorView();
        if (root == null) {
            return;
        }
        View known = BACKDROP_ROOT == null ? null : BACKDROP_ROOT.get();
        if (known == root && bg == BACKDROP_COLOR) {
            return;
        }
        BACKDROP_ROOT = new java.lang.ref.WeakReference<>(root);
        BACKDROP_COLOR = bg;
        root.setBackgroundColor(bg);
        View pager = a.findViewById(R.id.pager2);
        if (pager != null) {
            pager.setBackgroundColor(bg);
        }
    }
    private static void startTicker(final Activity a, final View shell) {
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (a.isFinishing() || shell.getParent() == null) {
                    return;
                }
                refresh(a, shell);
                h.postDelayed(this, 900);
            }
        }, 900);
    }

    /** The way back stays visible for a moment after a page turn, then fades out. */
    private static int LAST_SHOWN_PAGE = -1;
    private static final Handler TOP_HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable HIDE_TOP;

    private static void showTopForAWhile(final View shell) {
        final View top = shell.findViewWithTag(TAG + "_top");
        if (top == null) {
            return;
        }
        TOP_HANDLER.removeCallbacks(HIDE_TOP);
        top.animate().cancel();
        top.setVisibility(View.VISIBLE);
        top.setAlpha(1f);
        HIDE_TOP = new Runnable() {
            @Override public void run() {
                // 翻页后短暂出现，随后完全隐去——屏幕上不留一条幽灵标题。
                // 点击区仍在（透明度为 0），左上角那一条随时能回书库。
                top.animate().alpha(0f).setDuration(260).start();
            }
        };
        TOP_HANDLER.postDelayed(HIDE_TOP, 2500);
    }

    private static void refresh(Activity a, View shell) {
        paintBackdrop(a);
        // 引擎在切日/夜、换页时会把它自己的顶栏、底栏、进度条重新亮出来，这里每一拍再压一次
        hide(a, OLD_CHROME);
        TextView page = shell.findViewWithTag(TAG + "_page");
        TextView title = shell.findViewWithTag(TAG + "_title");
        View fill = shell.findViewWithTag(TAG + "_fill");
        if (title != null) {
            TextView old = a.findViewById(R.id.title);
            if (old != null && old.getText() != null && old.getText().length() > 0) {
                title.setText(old.getText());
            }
        }
        int cur = 0;
        int max = 0;
        View probe = a.findViewById(R.id.seekBar1);
        if (probe instanceof ProgressBar) {
            cur = ((ProgressBar) probe).getProgress();
            max = ((ProgressBar) probe).getMax();
        }
        if (page != null) {
            page.setText((cur + 1) + " / " + Math.max(1, max + 1));
            page.setTextColor(pageText(a));
        }
        boolean first = LAST_SHOWN_PAGE == -1;
        if (cur != LAST_SHOWN_PAGE) {
            LAST_SHOWN_PAGE = cur;
            if (first) {
                // 打开一本书时「‹ 书库」露一下，之后翻页不再冒出来打扰阅读
                // 开书时不再露「‹ 书库」：它压在正文第一行上，苹果阅读器不这么干
            }
        }
        savePosition(a, cur + 1, Math.max(1, max + 1));
        if (fill != null && fill.getParent() instanceof View) {
            // 正文上不画进度轨道：那条线在纸面上太重，右下角的页码已经够用。
            // 轨道留着、只是透明，仍然可以从底部横向拖动跳页。
            fill.setBackgroundColor(Color.TRANSPARENT);
            View track = (View) fill.getParent();
            track.setBackgroundColor(Color.TRANSPARENT);
            int width = track.getWidth();
            if (width > 0) {
                float ratio = max <= 0 ? 0f : Math.min(1f, (cur + 1) / (float) (max + 1));
                fill.getLayoutParams().width = Math.max(2, (int) (width * ratio));
                fill.requestLayout();
            }
        }
    }

    private static View buildShell(Activity a, DocumentController dc) {
        theme(a);
        FrameLayout shell = new FrameLayout(a);
        shell.setBackgroundColor(Color.TRANSPARENT);

        // top: one slim row - the way back only. It floats over the page instead of
        // painting a black band (which would show in daylight mode), appears when the
        // book opens and fades away while reading.
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(Color.TRANSPARENT);
        top.setTag(TAG + "_top");
        // 常驻标题和返回字样都不再出现：这一层透明，只留左上角一小块可点热区
        top.setAlpha(0f);

        LinearLayout topRow = new LinearLayout(a);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(dp(a, 14), dp(a, 2), dp(a, 14), dp(a, 2));

        TextView back = new TextView(a);
        back.setText("\u2039 \u4e66\u5e93");
        back.setTextColor(RED);
        back.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 13));
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setPadding(0, dp(a, 3), dp(a, 12), dp(a, 3));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // the shelf is the page underneath this one
                ((Activity) v.getContext()).finish();
            }
        });
        topRow.addView(back, new LinearLayout.LayoutParams(-2, -2));

        top.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        shell.addView(top, topLp);

        // bottom: the page number and a hairline progress bar, always on screen.
        // Tapping the number opens the keypad; the bar itself can be dragged to scrub.
        LinearLayout bottom = new LinearLayout(a);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(Color.TRANSPARENT);
        bottom.setPadding(dp(a, 16), dp(a, 2), dp(a, 16), dp(a, 4));

        TextView page = new TextView(a);
        page.setTag(TAG + "_page");
        page.setTextColor(pageText(a));
        page.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 12));
        page.setGravity(Gravity.RIGHT);
        page.setPadding(0, dp(a, 2), 0, dp(a, 2));
        page.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Activity act = (Activity) v.getContext();
                FrameLayout host = decor(act);
                openJump(act, CONTROLLER == null ? null : CONTROLLER.get(), host);
            }
        });
        bottom.addView(page, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout zone = new FrameLayout(a);
        zone.setPadding(0, dp(a, 5), 0, dp(a, 5));
        LinearLayout track = new LinearLayout(a);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackgroundColor(Color.parseColor("#3A3A3C"));
        track.setTag(TAG + "_track");
        View fill = new View(a);
        fill.setTag(TAG + "_fill");
        fill.setBackgroundColor(FG);
        track.addView(fill, new LinearLayout.LayoutParams(2, dp(a, 2)));
        zone.addView(track, new FrameLayout.LayoutParams(-1, dp(a, 2), Gravity.BOTTOM));
        zone.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                int action = e.getActionMasked();
                if (action != android.view.MotionEvent.ACTION_DOWN
                        && action != android.view.MotionEvent.ACTION_MOVE) {
                    return false;
                }
                DocumentController live = CONTROLLER == null ? null : CONTROLLER.get();
                if (live == null) {
                    return false;
                }
                int w = v.getWidth();
                if (w <= 0) {
                    return false;
                }
                int pages = 0;
                try {
                    pages = live.getPageCount();
                } catch (Throwable ignored) {
                }
                if (pages <= 0) {
                    return false;
                }
                float ratio = Math.min(1f, Math.max(0f, e.getX() / (float) w));
                int target = Math.max(1, Math.min(pages, (int) Math.round(ratio * pages)));
                try {
                    live.onGoToPage(target);
                } catch (Throwable ignored) {
                }
                return true;
            }
        });
        bottom.addView(zone, new LinearLayout.LayoutParams(-1, dp(a, 14)));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        shell.addView(bottom, bottomLp);
        // the middle of the page opens the menu, the sides still turn pages
        View hot = new View(a);
        hot.setBackgroundColor(Color.TRANSPARENT);
        hot.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openMenu((Activity) v.getContext(), dc); }
        });
        FrameLayout.LayoutParams hotLp = new FrameLayout.LayoutParams(
                (int) (a.getResources().getDisplayMetrics().widthPixels * 0.40f), dp(a, 120), Gravity.CENTER);
        shell.addView(hot, hotLp);

        // the two edges belong to the page, not to the pager: a long drag down the very
        // edge changes brightness on the left and text size on the right. A drag only
        // counts once it has travelled far enough, so a page turn never starts a slider.
        shell.addView(edge(a, true), edgeLp(a, Gravity.LEFT));
        shell.addView(edge(a, false), edgeLp(a, Gravity.RIGHT));

        return shell;
    }

    /** The rim below the title bar and above the page bar, so the back mark stays clickable. */
    private static FrameLayout.LayoutParams edgeLp(Activity a, int gravity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(a, 44), -1, gravity);
        lp.topMargin = 0;
        lp.bottomMargin = 0;
        return lp;
    }

    // ---------------------------------------------------------------- edge drags
    // iOS puts controls on the edges: a finger that stays on the rim and moves a long
    // way is changing something, a finger that just brushes the rim is not. The HUD is a
    // small card in the middle, shown only while the finger is down.

    private static final int EDGE_STEP = 60;   // dp of travel before the drag engages
    private static final int FONT_MIN = 8;
    private static final int FONT_MAX = 18;

    private static View edge(final Activity a, final boolean brightness) {
        View strip = new View(a);
        strip.setBackgroundColor(Color.TRANSPARENT);
        strip.setClickable(true);
        final int stepPx = dp(a, EDGE_STEP);
        final int screenH = a.getResources().getDisplayMetrics().heightPixels;

        strip.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private boolean engaged;
            private int startSize;
            private float startBright;

            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startY = e.getRawY();
                        engaged = false;
                        startSize = BookCSS.get().fontSizeSp;
                        float cur = a.getWindow().getAttributes().screenBrightness;
                        startBright = cur < 0 ? 0.6f : cur;
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float dy = e.getRawY() - startY;
                        if (!engaged && Math.abs(dy) >= stepPx) {
                            engaged = true;
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        }
                        if (!engaged) {
                            return true;
                        }
                        // down is less, up is more, the way a slider under the finger works
                        float travel = -dy / (screenH * 0.55f);
                        if (brightness) {
                            float level = Math.min(1f, Math.max(0.15f, startBright + travel));
                            setBrightness(a, level);
                            hud(a, "\u4eae\u5ea6  " + (int) Math.round(level * 100) + "%", true);
                        } else {
                            hud(a, "\u5b57\u53f7  " + pendingSize(startSize, dy, stepPx), false);
                        }
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (engaged) {
                            if (!brightness) {
                                int size = pendingSize(startSize, e.getRawY() - startY, stepPx);
                                if (size != BookCSS.get().fontSizeSp) {
                                    applyFontSize(a, null, size);
                                }
                            }
                            hideHudSoon(a);
                        }
                        return true;
                }
                return false;
            }
        });
        return strip;
    }

    /** Two points of text per 60dp of travel, held inside the sensible range. */
    private static int pendingSize(int start, float dy, int stepPx) {
        int step = (int) (-dy / stepPx);
        int size = start + step * 2;
        return Math.min(FONT_MAX, Math.max(FONT_MIN, size));
    }

    private static java.lang.ref.WeakReference<View> HUD;

    /** A slim card on the edge the finger is on: brightness on the left, text size on the right. */
    private static void hud(Activity a, String text, boolean left) {
        FrameLayout root = decor(a);
        if (root == null) {
            return;
        }
        View known = HUD == null ? null : HUD.get();
        TextView tv;
        if (known instanceof TextView && known.getParent() != null) {
            tv = (TextView) known;
            tv.setText(text);
        } else {
            tv = new TextView(a);
            tv.setTextColor(FG);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 14));
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#E61C1C1E"));
            bg.setCornerRadius(dp(a, 12));
            tv.setBackground(bg);
            tv.setMinWidth(dp(a, 92));
            tv.setTag(TAG + "_hud");
            root.addView(tv, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            HUD = new java.lang.ref.WeakReference<>(tv);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tv.getLayoutParams();
        lp.gravity = (left ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
        lp.leftMargin = dp(a, 8);
        lp.rightMargin = dp(a, 8);
        tv.setLayoutParams(lp);
        tv.animate().cancel();
        tv.setAlpha(1f);
        tv.setVisibility(View.VISIBLE);
    }
    private static void hideHudSoon(final Activity a) {
        final View tv = HUD == null ? null : HUD.get();
        if (tv == null) {
            return;
        }
        // 像 iOS 的音量提示那样：松手之后先停一会儿再淡出，不是立刻消失
        tv.animate().alpha(0f).setDuration(200).setStartDelay(900).withEndAction(new Runnable() {
            @Override public void run() {
                if (tv.getParent() != null) {
                    tv.setVisibility(View.GONE);
                }
            }
        }).start();
    }

    // ---------------------------------------------------------------- iPod chrome
    // Built in the iOS language: a centred title bar, grouped cards on #1C1C1E,
    // inset hairlines, grey values, a red tick for the current choice.


    private static final int ITEM_CHEVRON = 0;
    private static final int ITEM_CHECK = 1;
    private static final int ITEM_DANGER = 2;
    private static final int ITEM_PLAIN = 3;
    private static final int[] SIZES = {8, 11, 14, 17};
    private static final String[] SIZE_NAMES = {"小", "标准", "大", "大号"};
    private static final String[] FONT_KEYS = {"", "NotoSansCJK", "NotoSerifCJK", "DroidSansMono"};
    private static final String[] FONT_NAMES = {"默认", "黑体", "衬线", "等宽"};
    private static final String[] BRIGHT_NAMES = {"暗", "中", "亮"};
    private static final float[] BRIGHT_VALUES = {0.35f, 0.6f, 0.95f};

    private static final class Page {
        final FrameLayout veil;
        final LinearLayout body;

        Page(FrameLayout veil, LinearLayout body) {
            this.veil = veil;
            this.body = body;
        }
    }

    private static FrameLayout decor(Activity a) {
        return a.getWindow().getDecorView() instanceof FrameLayout
               ? (FrameLayout) a.getWindow().getDecorView() : null;
    }

    /** A full screen page with an iOS title bar; null backLabel means this is a root page. */
    private static Page newPage(final Activity a, FrameLayout root, String title, String backLabel,
                                final Runnable onBack) {
        theme(a);
        final FrameLayout veil = new FrameLayout(a);
        veil.setTag(TAG + "_menu");
        veil.setBackgroundColor(SHEET);

        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(SHEET);
        column.addView(navBar(a, title, backLabel, new Runnable() {
            @Override public void run() {
                close(veil);
                if (onBack != null) {
                    onBack.run();
                }
            }
        }, backLabel == null ? new Runnable() {
            @Override public void run() { close(veil); }
        } : null), new LinearLayout.LayoutParams(-1, dp(a, 56)));
        column.addView(hairline(a));

        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        sv.setBackgroundColor(SHEET);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 36));
        sv.addView(body, new android.widget.ScrollView.LayoutParams(-1, -2));
        column.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        veil.addView(column, new FrameLayout.LayoutParams(-1, -1));
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        // 二级页从右侧推入，和 iOS 的导航推页一致；返回时再滑出去
        KINDS.put(veil, "page");
        final float width = root.getWidth() > 0 ? root.getWidth() : dp(a, 360);
        veil.setTranslationX(width);
        veil.animate().translationX(0f).setDuration(230)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        return new Page(veil, body);
    }

    private static View navBar(Activity a, String title, String backLabel, final Runnable onBack,
                              final Runnable onDone) {
        FrameLayout bar = new FrameLayout(a);
        bar.setBackgroundColor(SHEET);

        TextView center = new TextView(a);
        center.setText(title);
        center.setTextColor(FG);
        center.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 16));
        center.setTypeface(Typeface.DEFAULT_BOLD);
        center.setGravity(Gravity.CENTER);
        bar.addView(center, new FrameLayout.LayoutParams(-1, -1));

        if (backLabel != null) {
            TextView back = new TextView(a);
            back.setText("\u2039 " + backLabel);
            back.setTextColor(RED);
            back.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 15));
            back.setGravity(Gravity.CENTER_VERTICAL);
            back.setPadding(dp(a, 16), 0, dp(a, 16), 0);
            back.setOnClickListener(new View.OnClickListener()
            {
                @Override public void onClick(View v) {
                    if (onBack != null) {
                        onBack.run();
                    }
                }
            });
            bar.addView(back, new FrameLayout.LayoutParams(-2, -1, Gravity.LEFT));
        }

        if (onDone != null) {
            TextView done = new TextView(a);
            done.setText("\u5b8c\u6210");
            done.setTextColor(RED);
            done.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 15));
            done.setGravity(Gravity.CENTER_VERTICAL);
            done.setPadding(dp(a, 16), 0, dp(a, 16), 0);
            done.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            });
            bar.addView(done, new FrameLayout.LayoutParams(-2, -1, Gravity.RIGHT));
        }
        return bar;
    }

    private static void openMenu(final Activity a, final DocumentController dc) {
        final FrameLayout root = decor(a);
        if (root == null) {
            return;
        }
        final DocumentController live = dc != null ? dc : resolveController(a);
        theme(a);

        // 防御：同一棵树上只留一张菜单。上一张还在走关闭动画时又开一张，
        // 就会出现两张菜单叠在一起，addView 也可能报“子视图已有父级”。
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            View stale = root.getChildAt(i);
            if (stale != null && (TAG + "_menu").equals(stale.getTag())) {
                root.removeView(stale);
            }
        }

        final FrameLayout veil = new FrameLayout(a);
        veil.setTag(TAG + "_menu");
        veil.setBackgroundColor(Color.parseColor("#66000000"));
        veil.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { close(veil); }
        });

        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadii(new float[]{dp(a, 16), dp(a, 16), dp(a, 16), dp(a, 16), 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setClickable(true);

        // 卡片头：中间一根抓手，右边一个「完成」——认得出是 iOS 的 sheet，关得掉也不靠猜
        FrameLayout head = new FrameLayout(a);
        head.setPadding(dp(a, 16), dp(a, 6), dp(a, 16), 0);
        View grip = new View(a);
        grip.setBackgroundColor(SEP);
        FrameLayout.LayoutParams gripLp = new FrameLayout.LayoutParams(dp(a, 36), dp(a, 4));
        gripLp.gravity = Gravity.CENTER_HORIZONTAL;
        head.addView(grip, gripLp);
        TextView done = new TextView(a);
        done.setText("\u5b8c\u6210");
        done.setTextColor(RED);
        done.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 14));
        done.setPadding(dp(a, 10), dp(a, 2), 0, dp(a, 4));
        done.setClickable(true);
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { close(veil); }
        });
        head.addView(done, new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        // 回书库原来只靠系统返回键：翻页时左边那条「‹ 书库」已经淡到看不见，
        // 菜单里又没有出口，只能乱按。给它一个和「完成」对称的位置。
        TextView shelf = new TextView(a);
        shelf.setText("\u2039 \u4e66\u5e93");
        shelf.setTextColor(RED);
        shelf.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 14));
        shelf.setPadding(0, dp(a, 2), dp(a, 10), dp(a, 4));
        shelf.setClickable(true);
        shelf.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                close(veil);
                a.finish();
            }
        });
        head.addView(shelf, new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        sheet.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // 卡片不再挂牌「阅读」：分组小标题已经说明这是阅读设置，多一行标题只是多一行灰字。

        // 这一屏里能改的东西就放在这里改完：字号、字体、外观是分段控件，亮度一根滑杆。
        // 原来那版还有一个「显示」二级页，四行设置要再点进去一层，而且字号/亮度
        // 和左右边缘手势说的是同一件事，重复又绕。
        LinearLayout look = new LinearLayout(a);
        look.setOrientation(LinearLayout.VERTICAL);
        look.setPadding(dp(a, 14), 0, dp(a, 14), dp(a, 6));

        final LinearLayout[] sizeSeg = new LinearLayout[1];
        sizeSeg[0] = (LinearLayout) segmented(a, SIZE_NAMES, sizeIndex(), new Pick() {
            @Override public void onPick(int index) {
                applyFontSize(a, live, SIZES[index]);
                setSegSelected(sizeSeg[0], index);
            }
        });
        look.addView(labeled(a, "\u5b57\u53f7", sizeSeg[0]));

        final LinearLayout[] fontSeg = new LinearLayout[1];
        fontSeg[0] = (LinearLayout) segmented(a, FONT_NAMES, fontIndex(), new Pick() {
            @Override public void onPick(int index) {
                applyFont(a, live, fontPack(index));
                setSegSelected(fontSeg[0], index);
            }
        });
        look.addView(labeled(a, "\u5b57\u4f53", fontSeg[0]));

        look.addView(labeled(a, "\u80cc\u666f", swatches(a, new Pick() {
            @Override public void onPick(int index) {
                close(veil);
                pickPaper(a, live, index);
            }
        })));

        // 亮度：和左边缘上滑是同一件事，这里给一根滑杆，改完就生效，菜单不用关
        LinearLayout bright = new LinearLayout(a);
        bright.setOrientation(LinearLayout.VERTICAL);
        LinearLayout brightHead = new LinearLayout(a);
        brightHead.setOrientation(LinearLayout.HORIZONTAL);
        brightHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView brightLabel = new TextView(a);
        brightLabel.setText("\u4eae\u5ea6");
        brightLabel.setTextColor(DIM);
        brightLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 12));
        brightHead.addView(brightLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView brightValue = new TextView(a);
        brightValue.setTextColor(DIM);
        brightValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 12));
        brightHead.addView(brightValue, new LinearLayout.LayoutParams(-2, -2));
        bright.addView(brightHead, new LinearLayout.LayoutParams(-1, -2));

        android.widget.SeekBar bar = new android.widget.SeekBar(a);
        bar.setMax(85);
        int level = Math.round(freshBrightness(a) * 100f);
        int progress = Math.min(85, Math.max(0, level - 15));
        bar.setProgress(progress);
        brightValue.setText((progress + 15) + "%");
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int value, boolean fromUser) {
                int percent = value + 15;
                brightValue.setText(percent + "%");
                setBrightness(a, percent / 100f);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) { }
        });
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(FG));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(FG));
        bar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(TRACK));
        bar.setPadding(0, dp(a, 2), 0, dp(a, 2));
        bright.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        look.addView(bright, lookLp(a));
        sheet.addView(look, new LinearLayout.LayoutParams(-1, -2));

        // 目录 / 书签 / 跳转 收成一行三个等宽入口：iOS 阅读器的底栏就是这个形状，
        // 三行标签加三个尖括号既占高度又多一道「要往里点」的心理成本。
        LinearLayout tools = new LinearLayout(a);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(dp(a, 6), dp(a, 2), dp(a, 6), dp(a, 2));
        tools.addView(tool(a, "\u76ee\u5f55", new Runnable() {
            @Override public void run() {
                close(veil);
                openToc(a, live, root);
            }
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        tools.addView(tool(a, "\u4e66\u7b7e", new Runnable() {
            @Override public void run() {
                close(veil);
                openBookmarks(a, live, root);
            }
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        tools.addView(tool(a, "\u8df3\u8f6c", new Runnable() {
            @Override public void run() {
                close(veil);
                openJump(a, live, root);
            }
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        sheet.addView(dividerRow(a));
        sheet.addView(tools, new LinearLayout.LayoutParams(-1, -2));
        String day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        long today = readSeconds(a, day);
        if (today >= 60) {
            TextView foot = new TextView(a);
            foot.setText("\u4eca\u65e5\u5df2\u8bfb " + (today / 60) + " \u5206\u949f");
            foot.setTextColor(DIM);
            foot.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 11));
            foot.setGravity(Gravity.CENTER);
            foot.setPadding(0, dp(a, 10), 0, dp(a, 2));
            sheet.addView(foot, new LinearLayout.LayoutParams(-1, -2));
        }
        sheet.setPadding(0, 0, 0, dp(a, 8));

        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        sheetLp.leftMargin = dp(a, 8);
        sheetLp.rightMargin = dp(a, 8);
        sheetLp.bottomMargin = dp(a, 10);
        veil.addView(sheet, sheetLp);
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));

        // 从底部升上来：iOS 的弹出感来自这一下位移，不是突然出现一块卡片
        veil.setAlpha(0f);
        sheet.setTranslationY(dp(a, 420));
        veil.animate().alpha(1f).setDuration(160).start();
        sheet.animate().translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /** 一行放完：左边灰色小标题，右边控件占满剩下的宽度，省掉每个设置项头顶那一行字。 */
    private static View labeled(Activity a, String label, View control) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(a, 5), 0, dp(a, 5));
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 12));
        r.addView(t, new LinearLayout.LayoutParams(dp(a, 44), -2));
        r.addView(control, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    /** 底栏三个入口之一：等宽、居中、只一个字面，没有尖括号也没有二级说明。 */
    private static View tool(Activity a, String label, final Runnable action) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextColor(FG);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 14));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(0, dp(a, 12), 0, dp(a, 12));
        t.setBackground(selector(a));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return t;
    }

    /** One row of the sheet: label on the left, optional value and a chevron on the right. */
    private static View row(final Activity a, String label, String value, final Runnable action) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(a, 44));
        r.setPadding(dp(a, 18), dp(a, 4), dp(a, 18), dp(a, 4));
        r.setBackground(selector(a));

        TextView l = new TextView(a);
        l.setText(label);
        l.setTextColor(FG);
        l.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 16));
        l.setSingleLine(true);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null && !value.isEmpty()) {
            TextView val = new TextView(a);
            val.setText(value);
            val.setTextColor(DIM);
            val.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 13));
            r.addView(val, new LinearLayout.LayoutParams(-2, -2));
        }
        TextView arrow = new TextView(a);
        arrow.setText("\u203a");
        arrow.setTextColor(DIM);
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 15));
        arrow.setPadding(dp(a, 8), 0, 0, 0);
        r.addView(arrow, new LinearLayout.LayoutParams(-2, -2));
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (action != null) { action.run(); }
            }
        });
        return r;
    }

    private static View dividerRow(Activity a) {
        View d = new View(a);
        d.setBackgroundColor(SEP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(a, 1));
        lp.leftMargin = dp(a, 18);
        d.setLayoutParams(lp);
        return d;
    }
    // ---------------------------------------------------------------- bookmarks
    // Kept by the shell itself, in one preference file: a book is its absolute path, a
    // bookmark is the page number. The reader's own bookmark dialog is an Android popup,
    // which has no place on a player, so the list is drawn as one more iOS page.

    private static final String PREF_BOOKMARKS = "PodBookmarks";

    /** Page the book was left on, so the shelf can say how far the reader got. */
    private static void savePosition(Activity a, int page, int count) {
        DocumentController dc = resolveController(a);
        String key = bookKey(dc);
        if (key == null) {
            return;
        }
        android.content.SharedPreferences p = bookmarks(a);
        if (p.getInt("pos:" + key, 0) == page && p.getInt("cnt:" + key, 0) == count) {
            return;
        }
        p.edit().putInt("pos:" + key, page).putInt("cnt:" + key, count).apply();
    }

    /** Reading time per day, so the menu can say how long the book has been open today. */
    private static void addReadSeconds(Activity a, long seconds) {
        if (seconds <= 0 || seconds > 6 * 3600) {
            return;
        }
        String day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        android.content.SharedPreferences p = bookmarks(a);
        p.edit()
                .putLong("read:" + day, p.getLong("read:" + day, 0) + seconds)
                .putLong("readTotal", p.getLong("readTotal", 0) + seconds)
                .apply();
    }

    private static long readSeconds(Activity a, String day) {
        return bookmarks(a).getLong("read:" + day, 0);
    }
    private static android.content.SharedPreferences bookmarks(Activity a) {
        return a.getSharedPreferences(PREF_BOOKMARKS, android.content.Context.MODE_PRIVATE);
    }

    /** The open book as a preference key; null when the page has no book yet. */
    private static String bookKey(DocumentController dc) {
        if (dc == null) {
            return null;
        }
        try {
            java.io.File f = dc.getCurrentBook();
            return f == null ? null : f.getAbsolutePath();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static java.util.List<Integer> bookmarkPages(Activity a, DocumentController dc) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        String key = bookKey(dc);
        if (key == null) {
            return out;
        }
        java.util.Set<String> raw = bookmarks(a).getStringSet(key, null);
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            try {
                out.add(Integer.parseInt(s));
            } catch (Throwable ignored) {
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    private static int currentPage(DocumentController dc) {
        try {
            return dc.getCurentPageFirst1();
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private static boolean toggleBookmark(Activity a, DocumentController dc) {
        String key = bookKey(dc);
        if (key == null) {
            return false;
        }
        int page = currentPage(dc);
        java.util.Set<String> now = new java.util.HashSet<>();
        java.util.Set<String> old = bookmarks(a).getStringSet(key, null);
        if (old != null) {
            now.addAll(old);
        }
        boolean added;
        if (now.contains(String.valueOf(page))) {
            now.remove(String.valueOf(page));
            added = false;
        } else {
            now.add(String.valueOf(page));
            added = true;
        }
        java.util.Set<String> copy = new java.util.HashSet<>(now);
        bookmarks(a).edit().putStringSet(key, copy).apply();
        return added;
    }

    private static String bookmarksValue(Activity a, DocumentController dc) {
        int n = bookmarkPages(a, dc).size();
        return n > 0 ? n + " 个" : null;
    }

    private static void openBookmarks(final Activity a, final DocumentController dc, final FrameLayout root) {
        final Page p = newPage(a, root, "书签", "阅读", new Runnable() {
            @Override public void run() {
                openMenu(a, dc);
            }
        });
        final LinearLayout listCard = card(a);
        fillBookmarks(a, p, listCard, dc);
        p.body.addView(listCard, listCard.getLayoutParams());
    }

    private static void fillBookmarks(final Activity a, final Page p, final LinearLayout listCard,
                                      final DocumentController dc) {
        listCard.removeAllViews();
        final int page = currentPage(dc);
        final java.util.List<Integer> pages = bookmarkPages(a, dc);
        final boolean marked = pages.contains(page);

        // the first row is the switch for this very page, the way iOS puts the action first
        addItem(listCard, item(a, marked ? "移除书签" : "加书签",
                "第 " + page + " 页", marked ? ITEM_DANGER : ITEM_CHEVRON, new Runnable() {
                    @Override public void run() {
                        toggleBookmark(a, dc);
                        fillBookmarks(a, p, listCard, dc);
                    }
                }));

        if (pages.isEmpty()) {
            return;
        }
        for (final int mark : pages) {
            addDivider(a, listCard);
            final boolean isHere = mark == page;
            View row = item(a, "第 " + mark + " 页", isHere ? "正在阅读" : null,
                    isHere ? ITEM_CHECK : ITEM_PLAIN, new Runnable() {
                        @Override public void run() {
                            try {
                                dc.onGoToPage(mark);
                            } catch (Throwable ignored) {
                            }
                            close(p.veil);
                        }
                    });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    removeBookmark(a, dc, mark);
                    fillBookmarks(a, p, listCard, dc);
                    return true;
                }
            });
            addItem(listCard, row);
        }
        if (!pages.isEmpty()) {
            addDivider(a, listCard);
            addItem(listCard, item(a, "清空本书书签", pages.size() + " 个", ITEM_DANGER, new Runnable() {
                @Override public void run() {
                    String key = bookKey(dc);
                    if (key != null) {
                        bookmarks(a).edit().remove(key).apply();
                    }
                    fillBookmarks(a, p, listCard, dc);
                    android.widget.Toast.makeText(a, "已清空本书书签", android.widget.Toast.LENGTH_SHORT).show();
                }
            }));
        }
    }

    private static void removeBookmark(Activity a, DocumentController dc, int page) {
        String key = bookKey(dc);
        if (key == null) {
            return;
        }
        java.util.Set<String> now = new java.util.HashSet<>();
        java.util.Set<String> old = bookmarks(a).getStringSet(key, null);
        if (old != null) {
            now.addAll(old);
        }
        now.remove(String.valueOf(page));
        bookmarks(a).edit().putStringSet(key, new java.util.HashSet<>(now)).apply();
        android.widget.Toast.makeText(a, "已删除书签", android.widget.Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示：字号、字体、页面、亮度合成一页。Every option is a row inside its own card,
     * so a change is one tap away instead of two pages deep, and the page stays on screen
     * when only brightness changes.
     */
    /** 一行小标题，标明下面那一条控件管什么。 */
    private static TextView microLabel(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 12));
        t.setPadding(dp(a, 4), dp(a, 4), 0, dp(a, 2));
        return t;
    }

    private static LinearLayout.LayoutParams lookLp(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(a, 2);
        return lp;
    }

    /** 点过一格之后重画整条分段控件，选中的格子自己走过去，菜单不用关掉再开。 */
    private static void setSegSelected(LinearLayout holder, int selected) {
        if (holder == null) {
            return;
        }
        float d = holder.getContext().getResources().getDisplayMetrics().density;
        for (int i = 0; i < holder.getChildCount(); i++) {
            View v = holder.getChildAt(i);
            if (!(v instanceof TextView)) {
                continue;
            }
            TextView t = (TextView) v;
            GradientDrawable seg = new GradientDrawable();
            seg.setCornerRadius(7 * d);
            boolean on = i == selected;
            seg.setColor(on ? SEG_ON : Color.TRANSPARENT);
            t.setBackground(seg);
            t.setTextColor(on ? FG : SEG_OFF);
            t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    /** A tap on one segment of an iOS style control. */
    private interface Pick {
        void onPick(int index);
    }

    private static View segmented(Activity a, String[] labels, int selected, final Pick pick) {
        LinearLayout holder = new LinearLayout(a);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SEG_TRACK);
        bg.setCornerRadius(dp(a, 9));
        holder.setBackground(bg);
        holder.setPadding(dp(a, 2), dp(a, 2), dp(a, 2), dp(a, 2));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = new TextView(a);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 14));
            GradientDrawable seg = new GradientDrawable();
            seg.setCornerRadius(dp(a, 7));
            if (i == selected) {
                seg.setColor(SEG_ON);
                t.setTextColor(FG);
                t.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                seg.setColor(Color.TRANSPARENT);
                t.setTextColor(SEG_OFF);
            }
            t.setBackground(seg);
            t.setPadding(0, dp(a, 6), 0, dp(a, 6));
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (pick != null) {
                        pick.onPick(idx);
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            holder.addView(t, lp);
        }
        return holder;
    }

    /** A card that holds exactly one segmented control, with iOS side insets. */
    private static LinearLayout segCard(Activity a) {
        LinearLayout c = card(a);
        c.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
        return c;
    }

    /**
     * 显示：字号、字体、页面、亮度。字号/字体/页面是分段控件，亮度是连续滑杆，
     * 顶部一行实时预览当前字号，改动一次即生效，不来回翻页。
     */
    private static void openDisplay(final Activity a, final DocumentController dc, final FrameLayout root) {
        final Page p = newPage(a, root, "显示", "阅读", new Runnable() {
            @Override public void run() {
                openMenu(a, dc);
            }
        });
        final DocumentController live = dc != null ? dc : resolveController(a);

        TextView preview = new TextView(a);
        preview.setText("春江潮水连海平，海上明月共潮生。");
        preview.setTextColor(FG);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_PX, (int) (BookCSS.get().fontSizeSp * a.getResources().getDisplayMetrics().scaledDensity));
        preview.setPadding(dp(a, 6), dp(a, 6), dp(a, 6), dp(a, 10));
        p.body.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        p.body.addView(sectionTitle(a, "字号"));
        LinearLayout sizeCard = segCard(a);
        sizeCard.addView(segmented(a, SIZE_NAMES, sizeIndex(), new Pick() {
            @Override public void onPick(int index) {
                close(p.veil);
                applyFontSize(a, live, SIZES[index]);
            }
        }), new LinearLayout.LayoutParams(-1, -2));
        p.body.addView(sizeCard, sizeCard.getLayoutParams());

        p.body.addView(sectionTitle(a, "字体"));
        LinearLayout fontCard = segCard(a);
        fontCard.addView(segmented(a, FONT_NAMES, fontIndex(), new Pick() {
            @Override public void onPick(int index) {
                close(p.veil);
                applyFont(a, live, fontPack(index));
            }
        }), new LinearLayout.LayoutParams(-1, -2));
        p.body.addView(fontCard, fontCard.getLayoutParams());

        p.body.addView(sectionTitle(a, "页面"));
        LinearLayout pageCard = segCard(a);
        final boolean day = AppState.get().isDayNotInvert;
        pageCard.addView(segmented(a, new String[]{"白天", "夜间"}, day ? 0 : 1, new Pick() {
            @Override public void onPick(int index) {
                close(p.veil);
                setDay(a, live, index == 0);
            }
        }), new LinearLayout.LayoutParams(-1, -2));
        p.body.addView(pageCard, pageCard.getLayoutParams());

        LinearLayout brightCard = card(a);
        brightCard.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(a);
        label.setText("亮度");
        label.setTextColor(FG);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 17));
        head.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView value = new TextView(a);
        value.setTextColor(DIM);
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 15));
        head.addView(value, new LinearLayout.LayoutParams(-2, -2));
        brightCard.addView(head, new LinearLayout.LayoutParams(-1, -2));

        android.widget.SeekBar bar = new android.widget.SeekBar(a);
        bar.setMax(85);
        int level = Math.round(freshBrightness(a) * 100f);
        int progress = Math.min(85, Math.max(0, level - 15));
        bar.setProgress(progress);
        value.setText((progress + 15) + "%");
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int value0, boolean fromUser) {
                int percent = value0 + 15;
                value.setText(percent + "%");
                setBrightness(a, percent / 100f);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) { }
        });
        int track = Color.parseColor("#48484A");
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        bar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(track));
        bar.setPadding(0, dp(a, 8), 0, dp(a, 8));
        brightCard.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        p.body.addView(brightCard, brightCard.getLayoutParams());
        p.body.addView(hint(a, "只改本机屏幕，不改书的排版。"));
    }

    private static float freshBrightness(Activity a) {
        float cur = a.getWindow().getAttributes().screenBrightness;
        return cur < 0 ? 0.6f : cur;
    }
    /**
     * 跳转：本机没有装输入法，所以这里不给系统键盘机会——页码用自绘数字键盘输入。
     *
     * 版式按 iOS 的弹窗来：居中的圆角卡片、一条条细线分格，数字压在网格里。
     * 不再铺一整屏浅灰、也不再让十二个白圆块各自浮着——那才是它不协调的原因。
     */
    private static void openJump(final Activity a, final DocumentController dc, final FrameLayout root) {
        if (root == null) {
            return;
        }
        final DocumentController live = dc != null ? dc : resolveController(a);
        if (live == null) {
            android.widget.Toast.makeText(a, "还没有打开书本", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        theme(a);

        int curGuess = 1;
        int pages = 0;
        try {
            curGuess = currentPage(live) + 1;
            pages = live.getPageCount();
        } catch (Throwable ignored) {
        }
        final int cur = curGuess;
        final int total = pages;
        final StringBuilder typed = new StringBuilder();

        final FrameLayout veil = new FrameLayout(a);
        veil.setTag(TAG + "_menu");
        veil.setBackgroundColor(0x59000000);

        final LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(CARD);
        cg.setCornerRadius(dp(a, 16));
        panel.setBackground(cg);
        panel.setClickable(true);

        TextView title = new TextView(a);
        title.setText("跳转到页面");
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 17));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(FG);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(a, 18), 0, 0);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        final TextView display = new TextView(a);
        display.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 38));
        display.setTypeface(Typeface.DEFAULT_BOLD);
        display.setTextColor(FG);
        display.setGravity(Gravity.CENTER);
        display.setPadding(0, dp(a, 8), 0, dp(a, 2));
        panel.addView(display, new LinearLayout.LayoutParams(-1, -2));

        TextView detail = new TextView(a);
        detail.setText("共 " + total + " 页");
        detail.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 13));
        detail.setTextColor(DIM);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, 0, 0, dp(a, 14));
        panel.addView(detail, new LinearLayout.LayoutParams(-1, -2));

        final TextView jump = new TextView(a);
        final Runnable paint = new Runnable() {
            @Override public void run() {
                display.setText(typed.length() == 0 ? String.valueOf(cur) : typed.toString());
                jump.setTextColor(typed.length() == 0 ? DIM : RED);
            }
        };

        String[][] keys = {
                {"1", "2", "3"},
                {"4", "5", "6"},
                {"7", "8", "9"},
                {"", "0", "\u232b"}
        };
        for (int r = 0; r < keys.length; r++) {
            panel.addView(splitLine(a, SEP));
            LinearLayout oneRow = new LinearLayout(a);
            oneRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < keys[r].length; c++) {
                if (c > 0) {
                    View sep = new View(a);
                    sep.setBackgroundColor(SEP);
                    oneRow.addView(sep, new LinearLayout.LayoutParams(dp(a, 1), dp(a, 52)));
                }
                final String k = keys[r][c];
                TextView b = new TextView(a);
                b.setText(k);
                b.setGravity(Gravity.CENTER);
                b.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 24));
                b.setTextColor(FG);
                b.setBackground(selector(a));
                if (k.length() > 0) {
                    b.setClickable(true);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            if ("\u232b".equals(k)) {
                                if (typed.length() > 0) {
                                    typed.setLength(typed.length() - 1);
                                }
                            } else if (typed.length() < 6) {
                                typed.append(k);
                            }
                            paint.run();
                        }
                    });
                }
                oneRow.addView(b, new LinearLayout.LayoutParams(0, -1, 1f));
            }
            panel.addView(oneRow, new LinearLayout.LayoutParams(-1, dp(a, 52)));
        }

        panel.addView(splitLine(a, SEP));
        LinearLayout actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView cancel = new TextView(a);
        cancel.setText("取消");
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 16));
        cancel.setTextColor(FG);
        cancel.setBackground(selector(a));
        cancel.setClickable(true);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                close(veil);
            }
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -1, 1f));

        View asep = new View(a);
        asep.setBackgroundColor(SEP);
        actions.addView(asep, new LinearLayout.LayoutParams(dp(a, 1), dp(a, 50)));

        jump.setText("跳转");
        jump.setGravity(Gravity.CENTER);
        jump.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 16));
        jump.setTypeface(Typeface.DEFAULT_BOLD);
        jump.setBackground(selector(a));
        jump.setClickable(true);
        jump.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int target = 0;
                try {
                    target = Integer.parseInt(typed.toString());
                } catch (Throwable ignored) {
                }
                if (target <= 0) {
                    target = cur;
                }
                if (total > 0 && target > total) {
                    target = total;
                }
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                close(veil);
                try {
                    live.onGoToPage(target);
                } catch (Throwable ignored) {
                }
            }
        });
        actions.addView(jump, new LinearLayout.LayoutParams(0, -1, 1f));
        panel.addView(actions, new LinearLayout.LayoutParams(-1, dp(a, 50)));

        paint.run();

        int w = dp(a, 300);
        if (root.getWidth() > 0) {
            w = Math.min(w, root.getWidth() - dp(a, 48));
        }
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(w, -2, Gravity.CENTER);
        veil.addView(panel, plp);
        veil.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                close(veil);
            }
        });
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        KINDS.put(veil, "alert");
        veil.setAlpha(0f);
        veil.animate().alpha(1f).setDuration(150).start();
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
    }

    /** 卡片里的一条分隔线：1dp，给定颜色。 */
    private static View splitLine(Activity a, int color) {
        View v = new View(a);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(a, 1)));
        return v;
    }

    private static void openSize(final Activity a, final DocumentController dc, final FrameLayout root) {
        final Page p = newPage(a, root, "字号", "显示", new Runnable() {
            @Override public void run() {
                openMenu(a, dc);
            }
        });
        LinearLayout c = card(a);
        int cur = sizeIndex();
        for (int i = 0; i < SIZES.length; i++) {
            if (i > 0) {
                addDivider(a, c);
            }
            final int sp = SIZES[i];
            addItem(c, item(a, SIZE_NAMES[i], null, i == cur ? ITEM_CHECK : ITEM_PLAIN, new Runnable() {
                @Override public void run() {
                    close(p.veil);
                    applyFontSize(a, dc, sp);
                }
            }));
        }
        p.body.addView(c, c.getLayoutParams());
        p.body.addView(hint(a, "点一下即生效，文字按新字号重新排版。"));
    }

    private static void openFont(final Activity a, final DocumentController dc, final FrameLayout root) {
        final Page p = newPage(a, root, "字体", "显示", new Runnable() {
            @Override public void run() {
                openMenu(a, dc);
            }
        });
        LinearLayout c = card(a);
        int cur = fontIndex();
        for (int i = 0; i < FONT_NAMES.length; i++) {
            if (i > 0) {
                addDivider(a, c);
            }
            final BookCSS.FontPack pack = fontPack(i);
            addItem(c, item(a, FONT_NAMES[i], null, i == cur ? ITEM_CHECK : ITEM_PLAIN, new Runnable() {
                @Override public void run() {
                    close(p.veil);
                    applyFont(a, dc, pack);
                }
            }));
        }
        p.body.addView(c, c.getLayoutParams());
        p.body.addView(hint(a, "字体来自系统与内置字体包，缺省时只用内置字体。"));
    }

    private static void openPage(final Activity a, final DocumentController dc, final FrameLayout root) {
        final Page p = newPage(a, root, "页面", "显示", new Runnable() {
            @Override public void run() {
                openMenu(a, dc);
            }
        });
        final boolean day = AppState.get().isDayNotInvert;
        LinearLayout c = card(a);
        addItem(c, item(a, "白天", null, day ? ITEM_CHECK : ITEM_PLAIN, new Runnable() {
            @Override public void run() {
                close(p.veil);
                setDay(a, dc, true);
            }
        }));
        addDivider(a, c);
        addItem(c, item(a, "夜间", null, day ? ITEM_PLAIN : ITEM_CHECK, new Runnable() {
            @Override public void run() {
                close(p.veil);
                setDay(a, dc, false);
            }
        }));
        p.body.addView(c, c.getLayoutParams());
        p.body.addView(hint(a, "白天是白纸黑字，夜间是黑底白字；顶栏和进度条始终是黑色。"));
    }

    private static void openBright(final Activity a, final FrameLayout root) {
        final Page p = newPage(a, root, "亮度", "显示", new Runnable() {
            @Override public void run() {
                openMenu(a, null);
            }
        });
        LinearLayout c = card(a);
        int cur = brightIndex(a);
        for (int i = 0; i < BRIGHT_NAMES.length; i++) {
            if (i > 0) {
                addDivider(a, c);
            }
            final float v = BRIGHT_VALUES[i];
            addItem(c, item(a, BRIGHT_NAMES[i], null, i == cur ? ITEM_CHECK : ITEM_PLAIN, new Runnable() {
                @Override public void run() {
                    close(p.veil);
                    setBrightness(a, v);
                }
            }));
        }
        p.body.addView(c, c.getLayoutParams());
        p.body.addView(hint(a, "只调阅读页的屏亮度，不改系统亮度。"));
    }

    private static void setBrightness(Activity a, float v) {
        try {
            WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            lp.screenBrightness = v;
            a.getWindow().setAttributes(lp);
        } catch (Throwable ignored) {
        }
    }

    private static int brightIndex(Activity a) {
        float cur = a.getWindow().getAttributes().screenBrightness;
        if (cur < 0) {
            return BRIGHT_VALUES.length - 1;
        }
        int best = 0;
        float delta = 9f;
        for (int i = 0; i < BRIGHT_VALUES.length; i++) {
            float diff = Math.abs(cur - BRIGHT_VALUES[i]);
            if (diff < delta) {
                delta = diff;
                best = i;
            }
        }
        return best;
    }

    private static int sizeIndex() {
        int cur = BookCSS.get().fontSizeSp;
        int best = 1;
        int delta = 999;
        for (int i = 0; i < SIZES.length; i++) {
            int diff = Math.abs(cur - SIZES[i]);
            if (diff < delta) {
                delta = diff;
                best = i;
            }
        }
        return best;
    }

    private static int fontIndex() {
        String cur = BookCSS.get().displayFontName;
        if (cur == null) {
            return 0;
        }
        String lc = cur.toLowerCase();
        for (int i = FONT_KEYS.length - 1; i > 0; i--) {
            if (lc.contains(FONT_KEYS[i].toLowerCase())) {
                return i;
            }
        }
        return 0;
    }

    private static String fontName() {
        return FONT_NAMES[fontIndex()];
    }

    private static BookCSS.FontPack fontPack(int i) {
        String key = FONT_KEYS[i];
        if (key == null || key.isEmpty()) {
            return new BookCSS.FontPack(BookCSS.CHARIS_SIL);
        }
        try {
            for (BookCSS.FontPack pack : BookCSS.get().getAllFontsPacks()) {
                if (pack.dispalyName != null && pack.dispalyName.toLowerCase().contains(key.toLowerCase())) {
                    return pack;
                }
            }
        } catch (Throwable ignored) {
        }
        return new BookCSS.FontPack(key.contains("Mono") ? BookCSS.COURIER : key);
    }

    private static void applyFont(Activity a, DocumentController dc, BookCSS.FontPack pack) {
        try {
            BookCSS.get().resetAll(pack);
            BookCSS.get().save(a);
        } catch (Throwable ignored) {
        }
        DocumentController live = dc != null ? dc : resolveController(a);
        if (live != null) {
            try {
                live.restartWithAnnotationsSaved();
            } catch (Throwable ignored) {
            }
        }
    }
    /** The page knows its controller even when the activity was not ready during onResume. */
    private static DocumentController resolveController(Activity a) {
        if (CONTROLLER != null && CONTROLLER.get() != null) {
            return CONTROLLER.get();
        }
        if (a instanceof com.foobnix.pdf.search.activity.HorizontalViewActivity) {
            try {
                return ((com.foobnix.pdf.search.activity.HorizontalViewActivity) a).getPodController();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void applyFontSize(Activity a, DocumentController dc, int sp) {
        BookCSS.get().fontSizeSp = sp;
        BookCSS.get().fontSizeSpFoliate = sp;
        try {
            BookCSS.get().save(a);
        } catch (Throwable ignored) {
        }
        DocumentController live = dc != null ? dc : resolveController(a);
        android.util.Log.i("PodReader", "font size " + sp + " dc=" + live);
        if (live != null) {
            try {
                live.restartWithAnnotationsSaved();
            } catch (Throwable t) {
                android.util.Log.i("PodReader", "font restart failed " + t);
            }
        }
    }


    /** 五格纸色：选中那格套一圈红环，点一次就换纸，纸色同时决定白天还是夜间。 */
    private static View swatches(final Activity a, final Pick pick) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final int sel = paper(a);
        for (int i = 0; i < PAPER_BG.length; i++) {
            final int idx = i;
            View dot = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(PAPER_BG[i]);
            g.setStroke(dp(a, sel == i ? 3 : 1),
                    sel == i ? RED : Color.parseColor(dayPaper(a) ? "#C7C7CC" : "#48484A"));
            dot.setBackground(g);
            dot.setClickable(true);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (pick != null) {
                        pick.onPick(idx);
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 28), dp(a, 28));
            lp.rightMargin = dp(a, 14);
            row.addView(dot, lp);
        }
        return row;
    }

    /**
     * 换纸：先落色，再让这一页就地换色。
     *
     * 之前这里走 restartWithAnnotationsSaved()，整本书要重新解析，五秒多；而纸色只是颜色，
     * 让引擎换一套样式再重画当前页就够了（见 HorizontalViewActivity#podApplyPaper）。
     */
    private static void pickPaper(Activity a, DocumentController dc, int idx) {
        boolean day = idx < 3;
        boolean flip = AppState.get().isDayNotInvert != day;
        podPrefs(a).edit().putInt("paper", idx).apply();
        applyPaper(a);
        theme(a);
        AppState s = AppState.get();
        s.isDayNotInvert = day;
        DAY_SCHEME = day;
        try {
            s.save(a);
        } catch (Throwable ignored) {
        }
        DocumentController live = dc != null ? dc : resolveController(a);
        if (live != null && a instanceof com.foobnix.pdf.search.activity.HorizontalViewActivity) {
            try {
                android.util.Log.i("PodReader", "paper live " + idx + " flip=" + flip);
                ((com.foobnix.pdf.search.activity.HorizontalViewActivity) a).podApplyPaper();
                paintBackdrop(a);
                return;
            } catch (Throwable t2) {
                android.util.Log.i("PodReader", "paper live failed " + t2);
            }
        }
        if (live != null) {
            try {
                live.restartWithAnnotationsSaved();
            } catch (Throwable ignored) {
            }
        }
        paintBackdrop(a);
    }

    private static void setDay(Activity a, DocumentController dc, boolean day) {
        // 记住用户选的外观：PDF/漫画这类版式书按原色显示，回到文本时才用它。
        a.getSharedPreferences("PodClassicReader", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("dayMode", day).apply();
        AppState s = AppState.get();
        s.isDayNotInvert = day;
        DAY_SCHEME = day;
        try {
            s.save(a);
        } catch (Throwable ignored) {
        }
        DocumentController live = dc != null ? dc : resolveController(a);
        android.util.Log.i("PodReader", "setDay " + day + " dc=" + live);
        // 明暗也只是配色：能就地重画就别把整本书重开一遍
        if (live != null && a instanceof com.foobnix.pdf.search.activity.HorizontalViewActivity) {
            try {
                ((com.foobnix.pdf.search.activity.HorizontalViewActivity) a).podApplyPaper();
                return;
            } catch (Throwable t2) {
                android.util.Log.i("PodReader", "setDay live failed " + t2);
            }
        }
        if (live != null) {
            try {
                live.restartWithAnnotationsSaved();
                return;
            } catch (Throwable t2) {
                android.util.Log.i("PodReader", "setDay restart failed " + t2);
            }
        }
        // last resort: the page re-reads the setting only when it is built again
        try {
            a.recreate();
        } catch (Throwable ignored) {
        }
    }

    private static void turnPage(Activity a, boolean next) {
        try {
            if (a instanceof com.foobnix.pdf.search.activity.HorizontalViewActivity) {
                if (next) {
                    ((com.foobnix.pdf.search.activity.HorizontalViewActivity) a).nextPage();
                } else {
                    ((com.foobnix.pdf.search.activity.HorizontalViewActivity) a).prevPage();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void click(Activity a, int id) {
        View v = a.findViewById(id);
        if (v != null) {
            v.performClick();
        }
    }

    private static LinearLayout card(Activity a) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(a, 12));
        c.setBackground(bg);
        c.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(a, 8);
        lp.bottomMargin = dp(a, 8);
        c.setLayoutParams(lp);
        return c;
    }

    private static void addItem(LinearLayout card, View v) {
        card.addView(v, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void addDivider(Activity a, LinearLayout card) {
        View d = new View(a);
        d.setBackgroundColor(SEP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(a, 1));
        lp.leftMargin = dp(a, 16);
        card.addView(d, lp);
    }

    private static View sectionTitle(Activity a, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextColor(DIM);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 12));
        v.setPadding(dp(a, 4), dp(a, 12), dp(a, 4), dp(a, 4));
        return v;
    }

    private static View hint(Activity a, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextColor(DIM);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 12));
        v.setPadding(dp(a, 4), dp(a, 10), dp(a, 4), dp(a, 4));
        return v;
    }

    private static View item(Activity a, String label, String value, int kind, final Runnable action) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(a, 48));
        r.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 10));
        r.setBackground(selector(a));

        TextView l = new TextView(a);
        l.setText(label);
        l.setTextColor(kind == ITEM_DANGER ? RED : FG);
        l.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 16));
        l.setSingleLine(true);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));

        if (value != null && !value.isEmpty()) {
            TextView val = new TextView(a);
            val.setText(value);
            val.setTextColor(DIM);
            val.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 13));
            val.setPadding(dp(a, 10), 0, 0, 0);
            r.addView(val, new LinearLayout.LayoutParams(-2, -2));
        }

        if (kind == ITEM_CHEVRON) {
            TextView arrow = new TextView(a);
            arrow.setText("\u203a");
            arrow.setTextColor(DIM);
            arrow.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 15));
            arrow.setPadding(dp(a, 8), 0, 0, 0);
            r.addView(arrow, new LinearLayout.LayoutParams(-2, -2));
        } else if (kind == ITEM_CHECK) {
            TextView check = new TextView(a);
            check.setText("\u2713");
            check.setTextColor(RED);
            check.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 16));
            check.setTypeface(Typeface.DEFAULT_BOLD);
            r.addView(check, new LinearLayout.LayoutParams(-2, -2));
        }

        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (action != null) {
                    action.run();
                }
            }
        });
        return r;
    }
    // ---------------------------------------------------------------- the contents page
    // The reader's own contents is an Android popup with an English header over a grey list.
    // On a player the contents are just another list page: the chapter, its page, a tap to go.

    private static void openToc(final Activity a, DocumentController dc, final FrameLayout root) {
        final DocumentController live = dc != null ? dc : resolveController(a);
        final Page p = newPage(a, root, "\u76ee\u5f55", "\u9605\u8bfb", new Runnable() {
            @Override public void run() { openMenu(a, resolveController(a)); }
        });
        final LinearLayout list = card(a);
        p.body.addView(list, list.getLayoutParams());
        final TextView empty = new TextView(a);
        empty.setText("\u6ca1\u6709\u627e\u5230\u76ee\u5f55");
        empty.setTextColor(DIM);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 15));
        empty.setPadding(dp(a, 16), dp(a, 16), dp(a, 16), dp(a, 16));
        empty.setVisibility(View.GONE);
        p.body.addView(empty, new LinearLayout.LayoutParams(-2, -2));
        if (live == null) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        java.util.List<com.foobnix.pdf.info.model.OutlineLinkWrapper> known = null;
        try {
            known = live.getCurrentOutline();
        } catch (Throwable ignored) {
        }
        if (known != null && !known.isEmpty()) {
            fillToc(a, list, empty, p, live, known);
            return;
        }
        try {
            live.loadOutline(new com.foobnix.android.utils.ResultResponse<java.util.List<com.foobnix.pdf.info.model.OutlineLinkWrapper>>() {
                @Override public boolean onResultRecive(java.util.List<com.foobnix.pdf.info.model.OutlineLinkWrapper> result) {
                    fillToc(a, list, empty, p, live, result);
                    return false;
                }
            });
        } catch (Throwable t) {
            empty.setVisibility(View.VISIBLE);
        }
    }

    /** Scrolls the parent page so the given row sits about a third of the way down. */
    private static void scrollToShow(View v) {
        int y = 0;
        View cur = v;
        while (cur != null) {
            y += cur.getTop();
            android.view.ViewParent up = cur.getParent();
            if (up instanceof android.widget.ScrollView) {
                int inset = (int) (90 * v.getResources().getDisplayMetrics().density);
                ((android.widget.ScrollView) up).smoothScrollTo(0, Math.max(0, y - inset));
                return;
            }
            cur = up instanceof View ? (View) up : null;
        }
    }
    private static void fillToc(final Activity a, final LinearLayout list, final TextView empty, final Page p,
                                final DocumentController dc,
                                java.util.List<com.foobnix.pdf.info.model.OutlineLinkWrapper> links) {
        list.removeAllViews();
        if (links == null || links.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        DocumentController live = dc != null ? dc : resolveController(a);
        int cur = 1;
        try {
            cur = currentPage(live);
        } catch (Throwable ignored) {
        }
        int currentIndex = -1;
        for (int i = 0; i < links.size(); i++) {
            com.foobnix.pdf.info.model.OutlineLinkWrapper l = links.get(i);
            if (l != null && l.targetPage > 0 && l.targetPage <= cur) {
                currentIndex = i;
            }
        }
        boolean first = true;
        for (int i = 0; i < links.size(); i++) {
            final com.foobnix.pdf.info.model.OutlineLinkWrapper link = links.get(i);
            if (link == null) {
                continue;
            }
            String label = null;
            try {
                label = link.getTitleAsString();
            } catch (Throwable ignored) {
            }
            if (label == null || label.trim().isEmpty()) {
                continue;
            }
            String value = link.targetPage > 0 ? String.valueOf(link.targetPage) : null;
            if (!first) {
                addDivider(a, list);
            }
            first = false;
            final boolean current = i == currentIndex;
            View rowView = item(a, label.trim(), value, ITEM_PLAIN, dp(a, Math.min(3, link.level) * 14),
                    new Runnable() {
                        @Override public void run() {
                            if (link.targetPage > 0) {
                                try {
                                    dc.onGoToPage(link.targetPage);
                                } catch (Throwable ignored) {
                                }
                            }
                            close(p.veil);
                        }
                    });
            if (current && rowView instanceof LinearLayout) {
                LinearLayout rl = (LinearLayout) rowView;
                View bar = new View(a);
                bar.setBackgroundColor(RED);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(a, 3), dp(a, 26));
                blp.rightMargin = dp(a, 10);
                rl.addView(bar, 0, blp);
                for (int k = rl.getChildCount() - 1; k >= 1; k--) {
                    View kid = rl.getChildAt(k);
                    if (kid instanceof TextView) {
                        ((TextView) kid).setTextColor(RED);
                        ((TextView) kid).setTypeface(Typeface.DEFAULT_BOLD);
                    }
                }
            }
            if (current) {
                final View scrollee = rowView;
                scrollee.post(new Runnable() {
                    @Override public void run() { scrollToShow(scrollee); }
                });
            }
            addItem(list, rowView);
        }
    }
    private static View item(Activity a, String label, String value, int kind, int indent,
                             final Runnable action) {
        View r = item(a, label, value, kind, action);
        r.setPadding(r.getPaddingLeft() + indent, r.getPaddingTop(), r.getPaddingRight(), r.getPaddingBottom());
        if (r instanceof ViewGroup && ((ViewGroup) r).getChildCount() > 0) {
            View first = ((ViewGroup) r).getChildAt(0);
            if (first instanceof TextView) {
                ((TextView) first).setMaxLines(1);
                ((TextView) first).setSingleLine(true);
                ((TextView) first).setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
        }
        return r;
    }
    /**
     * The MENU key opens the same list a tap in the middle of the page opens, and closes
     * whatever page of it is showing. On a device with no navigation bar, the key has to do
     * exactly what the on-screen menu does - otherwise it wakes the reader's stock Android
     * toolbars, which do not belong on a player.
     */
    public static boolean toggleMenu(Activity a) {
        FrameLayout root = decor(a);
        if (root == null) {
            return false;
        }
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            View child = root.getChildAt(i);
            if (child != null && (TAG + "_menu").equals(child.getTag())) {
                close(child);
                return true;
            }
        }
        openMenu(a, resolveController(a));
        return true;
    }
    /** 返回键先收菜单：收掉了就返回 true。 */
    public static boolean consumeBack(Activity a) {
        FrameLayout root = decor(a);
        if (root == null) {
            return false;
        }
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            View child = root.getChildAt(i);
            if (child != null && (TAG + "_menu").equals(child.getTag())) {
                close(child);
                return true;
            }
        }
        return false;
    }
    private static void close(final View veiled) {
        if (!(veiled instanceof ViewGroup) || veiled.getParent() == null) {
            return;
        }
        final ViewGroup veil = (ViewGroup) veiled;
        if (!CLOSING.add(veil)) {
            return;
        }
        final ViewGroup parent = (ViewGroup) veil.getParent();
        if ("alert".equals(KINDS.get(veil))) {
            // 弹窗：整体淡出，卡片微微缩回，不是像菜单那样往下滑
            View card = veil.getChildCount() > 0 ? veil.getChildAt(0) : null;
            if (card != null) {
                card.animate().scaleX(0.94f).scaleY(0.94f).setDuration(140).start();
            }
            veil.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
                @Override public void run() {
                    parent.removeView(veil);
                }
            }).start();
            return;
        }
        if ("page".equals(KINDS.get(veil))) {
            // 整页：从右边滑走
            float width = parent.getWidth() > 0 ? parent.getWidth() : 1080f;
            veil.animate().translationX(width).setDuration(210)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            parent.removeView(veil);
                        }
                    }).start();
            return;
        }
        View sheet = veil.getChildCount() > 0 ? veil.getChildAt(0) : null;
        if (sheet != null) {
            float drop = sheet.getHeight() > 0 ? sheet.getHeight() : 420;
            sheet.animate().translationY(drop).setDuration(170)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
        }
        veil.animate().alpha(0f).setDuration(170).withEndAction(new Runnable() {
            @Override public void run() {
                parent.removeView(veil);
            }
        }).start();
    }
    private static StateListDrawable selector(Activity a) {
        StateListDrawable sd = new StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(PRESS));
        sd.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return sd;
    }

    private static View hairline(Activity a) {
        View v = new View(a);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(a, 1)));
        return v;
    }

    private static void hide(Activity a, int[] ids) {
        for (int id : ids) {
            View v = a.findViewById(id);
            if (v != null) {
                v.setVisibility(View.GONE);
            }
        }
    }

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
    }
}
