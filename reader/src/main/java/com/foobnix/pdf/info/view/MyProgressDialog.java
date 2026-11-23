package com.foobnix.pdf.info.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;


/**
 * A waiting card, drawn the way a system sheet draws one: a small rounded panel on the
 * dimmed page, a spinner over a line of text, nothing else to press and nothing to
 * dismiss. The old dialog carried the theme's own list layout, a close cross and a
 * hairline spinner, which read as a leftover from another program.
 */
public class MyProgressDialog {

    private static final int CARD = 0xFF1C1C1E;
    private static final int TEXT = 0xFFFFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AlertDialog dialog;
    private final TextView text;

    private MyProgressDialog(Context c, String subtitile) {
        float density = c.getResources().getDisplayMetrics().density;
        int pad = (int) (26 * density + 0.5f);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(pad, pad, pad, pad);
        card.setMinimumWidth((int) (150 * density + 0.5f));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(14 * density);
        card.setBackground(bg);

        ProgressBar wheel = new ProgressBar(c);
        wheel.setIndeterminate(true);
        try {
            wheel.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        } catch (Throwable ignored) {
        }
        card.addView(wheel, new LinearLayout.LayoutParams((int) (26 * density + 0.5f), (int) (26 * density + 0.5f)));

        text = new TextView(c);
        text.setText(subtitile);
        text.setTextColor(TEXT);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-2, -2);
        textLp.topMargin = (int) (14 * density + 0.5f);
        card.addView(text, textLp);

        dialog = new AlertDialog.Builder(c).setView(card).create();
        dialog.setCancelable(false);
        dialog.show();

        // the panel carries its own shape: the window behind it must stay see-through
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        handler.postDelayed(this::dismiss, 30 * 1000);
        dialog.setOnDismissListener(dialog1 -> handler.removeCallbacksAndMessages(null));
    }

    public static MyProgressDialog show(Context c, String subtitile) {
        return new MyProgressDialog(c, subtitile);
    }

    public void dismiss() {
        try {
            dialog.dismiss();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    public void show() {
        dialog.show();
    }

    public void setCancelable(boolean cancelable) {
        dialog.setCancelable(cancelable);
    }

    public void setMessage(CharSequence message) {
        text.setText(message);
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }
}
