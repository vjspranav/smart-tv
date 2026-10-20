package com.livingroom.flamingo.perftest;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A · Cinema: full-screen artwork crossfading as focus moves along a row of banners. */
final class SceneA extends FrameLayout implements Main.Scene {
    private static final String[] TITLE = {"Lo-fi beats", "Stranger Things", "The Night Manager", "The Boys", "PlayStation 5"};
    private final ImageView under, over;
    private final ImageView[] tiles = new ImageView[5];
    private final TextView eyebrow, title;
    private final GradientDrawable ring = new GradientDrawable();
    private int focus = 1;

    SceneA(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
        under = full(c);
        over = full(c);
        under.setImageBitmap(Art.hero(focus));

        eyebrow = text(c, 24, false, 0xb3ffffff);
        eyebrow.setLetterSpacing(0.2f);
        title = text(c, 110, true, Color.WHITE);
        TextView meta = text(c, 34, false, 0xccffffff);
        meta.setText("S4 · E7 · 38 min left");
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(eyebrow);
        info.addView(title);
        info.addView(meta);
        addView(info, lp(1100, LayoutParams.WRAP_CONTENT, 96, 250));

        ring.setCornerRadius(18);
        ring.setStroke(5, Color.WHITE);
        LinearLayout row = new LinearLayout(c);
        for (int i = 0; i < 5; i++) {
            ImageView t = new ImageView(c);
            t.setImageBitmap(Art.banner(i));
            t.setScaleType(ImageView.ScaleType.FIT_XY);
            t.setOutlineProvider(ROUNDED);
            t.setClipToOutline(true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 194, 1);
            p.leftMargin = i == 0 ? 0 : 40;
            row.addView(t, p);
            tiles[i] = t;
        }
        addView(row, lp(LayoutParams.MATCH_PARENT, 194, 96, 0));
        ((LayoutParams) row.getLayoutParams()).gravity = Gravity.BOTTOM;
        ((LayoutParams) row.getLayoutParams()).rightMargin = 96;
        ((LayoutParams) row.getLayoutParams()).bottomMargin = 100;
        show(focus, false);
    }

    @Override public void move(int d) {
        int n = focus + d;
        if (n < 0 || n > 4) return;
        int old = focus;
        focus = n;
        tiles[old].animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        tiles[old].setForeground(null);
        show(n, true);
    }

    private void show(int i, boolean animate) {
        tiles[i].setForeground(ring);
        tiles[i].animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start();
        eyebrow.setText((i == 4 ? "SWITCH TO " : "CONTINUE ON ") + Art.NAMES[i].toUpperCase());
        title.setText(TITLE[i]);
        if (!animate) return;
        // Snap any half-finished fade, then fade the new art in on top (hardware layer, alpha only)
        over.animate().cancel();
        if (over.getDrawable() != null) under.setImageDrawable(over.getDrawable());
        over.setImageBitmap(Art.hero(i));
        over.setAlpha(0f);
        over.animate().alpha(1f).setDuration(220).withLayer().start();
    }

    private ImageView full(Context c) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return v;
    }

    static TextView text(Context c, float px, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LayoutParams lp(int w, int h, int left, int top) {
        LayoutParams p = new LayoutParams(w, h);
        p.leftMargin = left;
        p.topMargin = top;
        return p;
    }

    static final ViewOutlineProvider ROUNDED = new ViewOutlineProvider() {
        @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 18); }
    };
}
