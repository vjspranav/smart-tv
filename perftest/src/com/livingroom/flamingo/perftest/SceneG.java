package com.livingroom.flamingo.perftest;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * G · Columns: five numbered columns, the focused one wide (2.6 units) and showing art.
 * layout mode: widths animate through LayoutParams (re-layout every frame) — the faithful version.
 * transform mode: every column is laid out once at wide width; positions move by translationX,
 * the top rule by scaleX, the art by alpha. No layout pass while moving.
 */
final class SceneG extends FrameLayout implements Main.Scene {
    private static final int LEFT = 96, TOP = 160, BOTTOM = 100, GAP = 28, WIDE = 26, NARROW = 10; // units in tenths
    private final boolean layoutMode;
    private final Col[] cols = new Col[5];
    private final LinearLayout row;
    private int focus = 1, unitPx;

    private static final class Col {
        FrameLayout root; View rule; ImageView art; TextView sub;
    }

    SceneG(Context c, boolean layoutMode) {
        super(c);
        this.layoutMode = layoutMode;
        setBackgroundColor(0xff0b0a09);
        TextView label = SceneA.text(c, 22, false, 0x80ffffff);
        label.setText("THE LIVING ROOM");
        label.setLetterSpacing(0.2f);
        addView(label, SceneA.lp(-2, -2, LEFT, 70));

        int usable = 1920 - 2 * LEFT - 4 * GAP;
        unitPx = usable * 10 / (4 * NARROW + WIDE);
        row = layoutMode ? new LinearLayout(c) : null;
        if (layoutMode) addView(row, lp(1920 - 2 * LEFT, 0));
        for (int i = 0; i < 5; i++) {
            Col col = build(c, i);
            cols[i] = col;
            if (layoutMode) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, i == focus ? WIDE : NARROW);
                p.leftMargin = i == 0 ? 0 : GAP;
                row.addView(col.root, p);
            } else {
                addView(col.root, lp(unitPx * WIDE / 10, LEFT));
                col.root.setTranslationX(x(i, focus));
                col.rule.setPivotX(0);
                col.rule.setScaleX(i == focus ? 1f : (float) NARROW / WIDE);
            }
            col.art.setAlpha(i == focus ? 1f : 0f);
            col.sub.setAlpha(i == focus ? 1f : 0f);
        }
    }

    private LayoutParams lp(int w, int left) {
        LayoutParams p = new LayoutParams(w, 1080 - TOP - BOTTOM);
        p.leftMargin = left;
        p.topMargin = TOP;
        return p;
    }

    private Col build(Context c, int i) {
        Col col = new Col();
        col.root = new FrameLayout(c);
        col.rule = new View(c);
        col.rule.setBackgroundColor(Color.WHITE);
        col.root.addView(col.rule, new LayoutParams(-1, 3));
        TextView num = SceneA.text(c, 64, false, Color.WHITE);
        num.setTypeface(Typeface.MONOSPACE);
        num.setText("0" + (i + 1));
        col.root.addView(num, SceneA.lp(-1, -2, 0, 24));
        col.art = new ImageView(c);
        col.art.setImageBitmap(Art.hero(i));
        col.art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        col.art.setOutlineProvider(SceneA.ROUNDED);
        col.art.setClipToOutline(true);
        col.root.addView(col.art, SceneA.lp(-1, 330, 0, 140));
        TextView name = SceneA.text(c, 56, true, Color.WHITE);
        name.setText(Art.NAMES[i]);
        col.root.addView(name, SceneA.lp(-1, -2, 0, 520));
        col.sub = SceneA.text(c, 34, false, 0xd9ffffff);
        col.sub.setText("Stranger Things, S4 E7 · 38 min left");
        col.root.addView(col.sub, SceneA.lp(-1, -2, 0, 600));
        return col;
    }

    /** Left edge of column i (relative to LEFT) when column f is the wide one. */
    private int x(int i, int f) {
        int px = 0;
        for (int k = 0; k < i; k++) px += unitPx * (k == f ? WIDE : NARROW) / 10 + GAP;
        return px;
    }

    @Override public void move(int d) {
        int n = focus + d;
        if (n < 0 || n > 4) return;
        final int old = focus;
        focus = n;
        cols[old].art.animate().alpha(0f).setDuration(180).withLayer().start();
        cols[old].sub.animate().alpha(0f).setDuration(120).start();
        cols[n].art.animate().alpha(1f).setDuration(220).withLayer().start();
        cols[n].sub.animate().alpha(1f).setDuration(220).start();
        if (layoutMode) {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f).setDuration(220);
            final int nn = n;
            a.addUpdateListener(va -> {
                float t = (float) va.getAnimatedValue();
                ((LinearLayout.LayoutParams) cols[old].root.getLayoutParams()).weight = WIDE + (NARROW - WIDE) * t;
                ((LinearLayout.LayoutParams) cols[nn].root.getLayoutParams()).weight = NARROW + (WIDE - NARROW) * t;
                row.requestLayout();
            });
            a.start();
        } else {
            for (int i = 0; i < 5; i++) cols[i].root.animate().translationX(x(i, n)).setDuration(220).start();
            cols[old].rule.animate().scaleX((float) NARROW / WIDE).setDuration(220).start();
            cols[n].rule.animate().scaleX(1f).setDuration(220).start();
        }
    }
}
