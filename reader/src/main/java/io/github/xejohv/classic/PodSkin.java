package io.github.xejohv.classic;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.model.BookCSS;

/**
 * Librera Classic look, offline contract, and the trimmed-down settings of a dedicated player.
 *
 * Skin: black chrome, white icons and labels, red accent, applied right after the
 * persisted settings are loaded and before TintUtil caches the tint, so the indigo
 * defaults can never come back.
 *
 * Offline: the app ships without the INTERNET permission, so no surface may offer
 * a network action. Every such entry point is hidden here instead of promising
 * something that would only fail.
 *
 * Settings: this device has four reading settings that matter - text size, typeface
 * colour of page and text, and brightness. Everything else is either fixed in code or
 * removed from the panel, so the player never shows a knob it does not need.
 */
public final class PodSkin {

    public static final int BG = Color.BLACK;
    public static final int TEXT = Color.WHITE;
    public static final int ACCENT = Color.parseColor("#FF3B30");
    public static final int MUTED = Color.parseColor("#8E8E93");

    /** Settings rows that exist only to reach the network: update, store rating, mail, web, beta, pro, libreraX. */
    private static final int[] ONLINE_ROWS = {
            R.id.updateToNewApp, R.id.onRateIt, R.id.onMailSupport, R.id.openWeb, R.id.openBeta,
            R.id.downloadPRO, R.id.libreraXGithub, R.id.whatIsNew, R.id.libraryLicenses,
            R.id.signIn
    };

    /** Library section of the full settings: file types, search paths, rescan - all decided here, not by the user. */
    private static final int[] LIBRARY_ROWS = {
            R.id.section1, R.id.searchPaths, R.id.onUpdateLibrary, R.id.onSyncLibrary,
            R.id.supportPDF, R.id.supportXPS, R.id.supportDJVU, R.id.supportEPUB, R.id.supportFB2,
            R.id.supportRTF, R.id.supportODT, R.id.supportDOCX, R.id.supportMOBI, R.id.supportCBZ,
            R.id.supportZIP, R.id.supportTXT, R.id.supportArch, R.id.supportOther, R.id.moreLybraryettings
    };

    /** Rows of the reading panel that a dedicated player does not expose. */
    private static final int[] READING_PANEL_EXTRA = {
            R.id.onRecent, R.id.onPageFlip, R.id.bookCut, R.id.onRotate, R.id.onCrop,
            R.id.onFullScreen, R.id.onTTS, R.id.onPin, R.id.topPanelLine, R.id.topPanelLineDiv,
            R.id.epubEngineRow, R.id.pdfEngineRow, R.id.annotationsSaveRow, R.id.annotationsSaveTop,
            R.id.isPreText, R.id.isLineBreaksText, R.id.isLineTitleBoldText, R.id.isCharacterEncoding,
            R.id.characterEncoding, R.id.downloadFonts, R.id.moreFontSettings, R.id.moreReadColorSettings,
            R.id.onKeyCode, R.id.isCropBorders,
            // volume keys belong to the player, not to page turns; no dictionary, no blue filter
            R.id.isUseVolumeKeys, R.id.isReverseKyes, R.id.dictionaryRow, R.id.dictionaryTop,
            R.id.isRememberDictionary, R.id.selectedDictionaly,
            R.id.isEnableBlueFilter, R.id.onBlueFilter, R.id.textCustomizeFontBGColor,
            // brightness follows the slider only (no "system brightness" tick), the typeface has no
            // extra pills, and the colour row keeps just day and night instead of eight swatches
            R.id.autoSettings, R.id.fontNamePreview2, R.id.downloadFonts, R.id.ta, R.id.tb, R.id.tc,
            R.id.preColors
    };

    private PodSkin() {
    }

    public static void apply(AppState st) {
        if (st == null) {
            return;
        }
        // chrome
        st.appTheme = AppState.THEME_DARK_OLED;
        st.tintThemeColor = BG;
        st.isUiTextColor = true;
        st.uiTextColor = TEXT;
        // page furniture: page number / clock / battery readouts
        st.statusBarColorDay = TEXT;
        st.statusBarColorNight = TEXT;
        // book page: night scheme (black page, light text), so a page looks like the player
        st.isDayNotInvert = io.github.xejohv.classic.PodReader.dayScheme();
        // plain paper: no wallpaper texture behind the text, the page is the page
        st.isUseBGImageDay = false;
        st.isUseBGImageNight = false;
        // reflowable books are laid out by the text engine, so text size, typeface and
        // line breaks can actually change the page instead of scaling a picture of it
        st.epubEngine = AppState.EPUB_ENGINE_FOLIATE;


        applyOffline(st);
        applyFormats(st);
        applyReadingMode();
    }

    /** Every format the build can open is on, with no window to choose between them. */
    public static void applyFormats(AppState st) {
        st.supportPDF = true;
        // XPS 由 MuPDF 画
        st.supportXPS = true;
        st.supportDJVU = true;
        st.supportEPUB = true;
        st.supportFB2 = true;
        st.supportRTF = true;
        st.supportODT = true;
        st.supportDOCX = true;
        st.supportMOBI = true;
        st.supportCBZ = true;
        st.supportZIP = true;
        st.supportArch = true;
        st.supportOther = true;
        st.supportTXT = true;
    }

    /**
     * One page mode only: left/right. The reader no longer asks the user to pick between
     * rolling text and book pages, and never remembers a rolling choice from an older build.
     */
    public static void applyReadingMode() {
        try {
            AppSP.get().readingMode = AppState.READING_MODE_BOOK;
        } catch (Throwable ignored) {
        }
    }

    /** The reader is a book, not a scrapbook: no TTS, no scrolling mode, no library panel, one page mode. */
    private static final int[] READER_CHROME = {
            R.id.playListParent, R.id.playListTabsScroll, R.id.playlistRecycleView,
            R.id.textToSpeach, R.id.textToSpeachTop, R.id.ttsControlsSlot, R.id.ttsControlsSlotTop,
            R.id.autoScroll, R.id.nextTypeBootom, R.id.onModeChange, R.id.modeName,
            R.id.onShowSearch
    };

    public static void applyReaderChrome(Activity a) {
        if (a == null) {
            return;
        }
        hide(a.getWindow().getDecorView(), READER_CHROME);
        disableSoftKeyboard(a);
    }

    /** No keyboard on a dedicated player: the text fields that remain never raise the IME. */
    public static void disableSoftKeyboard(Activity a) {
        if (a == null || a.getWindow() == null) {
            return;
        }
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        killEditors(a.getWindow().getDecorView());
    }

    public static void killEditors(View root) {
        if (root == null) {
            return;
        }
        if (root instanceof EditText) {
            ((EditText) root).setShowSoftInputOnFocus(false);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                killEditors(g.getChildAt(i));
            }
        }
    }

    /** Nothing in this build talks to the network: switch off the settings that would. */
    public static void applyOffline(AppState st) {
        st.isShowCloudsLine = false;
        st.isRememberDictionary = false;
        // one page mode: the format table that used to send epubs to the scrolling view is off
        st.isPrefFormatMode = false;
        // a double tap turns the page, it never starts reading the book out loud
        if (st.doubleClickAction1 == AppState.DOUBLE_CLICK_START_STOP_TTS) {
            st.doubleClickAction1 = AppState.DOUBLE_CLICK_ADJUST_PAGE;
        }
        // volume keys keep their system meaning; the player owns the volume bar
        st.isUseVolumeKeys = false;
        if (st.rememberDict1 != null && st.rememberDict1.startsWith("web:")) {
            st.rememberDict1 = "";
        }
    }

    /** Hides the settings rows and the selection toolbar's translation button. */
    public static void hideOnlineSurfaces(View root) {
        hide(root, ONLINE_ROWS);
        hide(root, LIBRARY_ROWS);
    }

    /**
     * The reading panel: the tab strip that leads to three other settings screens goes away
     * whole, and so does every row this player does not need. What is left is text size,
     * typeface, page/text colour and brightness.
     */
    public static void applyMinimalPrefs(View root) {
        if (root == null) {
            return;
        }
        View more = root.findViewById(R.id.moreSettings);
        if (more != null && more.getParent() instanceof View) {
            // the whole strip: reading settings / advanced / status bar
            ((View) more.getParent()).setVisibility(View.GONE);
        }
        hide(root, new int[]{
                R.id.moreSettings, R.id.moreSettingsDiv, R.id.performanceSettigns, R.id.statusBarSettings,
                R.id.musicianSettings, R.id.musicianSettingsDiv
        });
        hide(root, READING_PANEL_EXTRA);
    }


    /**
     * The settings screen of a dedicated reader: where the books live, what version this is,
     * and one line telling the owner where to drop a file. Everything else that used to be
     * here - profiles, search paths, engines, themes, gestures, widgets, sync - is gone.
     */
    public static void applyPodSettings(View root, Activity a) {
        View scroll = root == null ? null : root.findViewById(R.id.scroll);
        if (!(scroll instanceof ScrollView) || a == null) {
            return;
        }
        ViewGroup box = (ViewGroup) scroll;
        box.removeAllViews();

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(BG);
        col.setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 16));

        col.addView(caption(a, "书库"));
        col.addView(row(a, "图书目录", "/sdcard/Books"));
        col.addView(line(a));

        col.addView(caption(a, "关于"));
        col.addView(row(a, "版本", versionName(a)));
        col.addView(line(a));

        TextView note = new TextView(a);
        note.setText("把 EPUB / PDF / TXT 放进 Books 文件夹，回到阅读会自动出现。");
        note.setTextColor(MUTED);
        note.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 13));
        note.setPadding(0, dp(a, 18), 0, 0);
        note.setLineSpacing(dp(a, 4), 1f);
        col.addView(note);

        box.addView(col, new ScrollView.LayoutParams(-1, -2));
    }

    private static TextView caption(Activity a, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextColor(MUTED);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 12));
        v.setPadding(0, dp(a, 14), 0, dp(a, 8));
        v.setTypeface(Typeface.DEFAULT);
        return v;
    }

    private static View row(Activity a, String label, String value) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(a, 14), 0, dp(a, 14));

        TextView l = new TextView(a);
        l.setText(label);
        l.setTextColor(TEXT);
        l.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 16));
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1));

        TextView v = new TextView(a);
        v.setText(value);
        v.setTextColor(MUTED);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(a, 14));
        v.setGravity(Gravity.RIGHT);
        r.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        return r;
    }

    private static View line(Activity a) {
        View v = new View(a);
        v.setBackgroundColor(Color.parseColor("#1F1F1F"));
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(a, 1)));
        return v;
    }

    private static String versionName(Activity a) {
        try {
            return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "10.1.14-classic";
        }
    }

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
    }
    private static void hide(View root, int[] ids) {
        if (root == null) {
            return;
        }
        for (int id : ids) {
            View v = root.findViewById(id);
            if (v != null) {
                v.setVisibility(View.GONE);
            }
        }
    }
}
