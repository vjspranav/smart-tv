package com.livingroom.flamingo.perftest;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** N · Nothing: static dot-grid window background, widgets, pills, optional spinning record. */
final class SceneN extends FrameLayout implements Main.Scene {
    private final TextView[] pills = new TextView[5];
    private final GradientDrawable pillOff, pillOn;
    private int focus = 1;

    SceneN(Context c, Window w, boolean spin) {
        super(c);
        BitmapDrawable grid = new BitmapDrawable(c.getResources(), Art.dotTile());
        grid.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        w.setBackgroundDrawable(grid);        // drawn by the window, never invalidated by us

        TextView clock = SceneA.text(c, 170, true, Color.WHITE);
        clock.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        clock.setText("20:42");
        addView(clock, SceneA.lp(-2, -2, 96, 60));

        int y = 330, h = 330;
        addView(widget(c, "INPUT"), SceneA.lp(680, h, 96, y));
        FrameLayout listen = widget(c, "LISTEN");
        ImageView rec = new ImageView(c);
        rec.setImageBitmap(Art.record());
        rec.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        listen.addView(rec, SceneA.lp(300, 300, 180, 40));
        addView(listen, SceneA.lp(420, h, 800, y));
        addView(widget(c, "TV"), SceneA.lp(588, h, 1236, y));
        if (spin) {
            ObjectAnimator a = ObjectAnimator.ofFloat(rec, View.ROTATION, 0f, 360f).setDuration(4000);
            a.setInterpolator(new LinearInterpolator());
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.start();
        }

        pillOff = pill(0x00000000, 0x4dffffff);
        pillOn = pill(Color.WHITE, Color.WHITE);
        LinearLayout row = new LinearLayout(c);
        for (int i = 0; i < 5; i++) {
            TextView p = SceneA.text(c, 34, false, Color.WHITE);
            p.setTypeface(Typeface.MONOSPACE);
            p.setGravity(Gravity.CENTER);
            p.setText(Art.NAMES[i]);
            p.setBackground(pillOff);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 150, 1);
            lp.leftMargin = i == 0 ? 0 : 28;
            row.addView(p, lp);
            pills[i] = p;
        }
        LayoutParams rp = SceneA.lp(-1, 150, 96, 0);
        rp.rightMargin = 96;
        rp.bottomMargin = 120;
        rp.gravity = Gravity.BOTTOM;
        addView(row, rp);
        on(focus, true);
    }

    private static GradientDrawable pill(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(999);
        d.setColor(fill);
        d.setStroke(2, stroke);
        return d;
    }

    private FrameLayout widget(Context c, String label) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(48);
        bg.setColor(0xff121212);
        bg.setStroke(2, 0x1cffffff);
        f.setBackground(bg);
        TextView t = SceneA.text(c, 22, false, 0x80ffffff);
        t.setTypeface(Typeface.MONOSPACE);
        t.setLetterSpacing(0.18f);
        t.setText(label);
        f.addView(t, SceneA.lp(-2, -2, 40, 34));
        return f;
    }

    private void on(int i, boolean yes) {
        TextView p = pills[i];
        p.setBackground(yes ? pillOn : pillOff);
        p.setTextColor(yes ? Color.BLACK : Color.WHITE);
        p.animate().scaleX(yes ? 1.05f : 1f).scaleY(yes ? 1.05f : 1f).setDuration(150).start();
    }

    @Override public void move(int d) {
        int n = focus + d;
        if (n < 0 || n > 4) return;
        on(focus, false);
        focus = n;
        on(n, true);
    }
}
