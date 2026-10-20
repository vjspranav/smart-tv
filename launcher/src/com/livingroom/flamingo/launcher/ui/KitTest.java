package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Debug screen (extra kittest=1): four planted overflow problems and clean copies. */
final class KitTest {
    private KitTest() { }

    static View build(Context c) {
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Color.BLACK);
        Typeface tf = Fonts.get(c, Fonts.INTER, 500);

        // planted: focused tile scaled inside a row that clips
        root.addView(row(c, false), at(-2, -2, 96, 120));
        // clean: same row with room for focus
        LinearLayout ok = row(c, true);
        root.addView(ok, at(-2, -2, 96, 360));
        Kit.focusRoom(ok, 300, 120, 1.1f, 8);

        // planted: two labels on top of each other
        root.addView(text(c, tf, "Overlap one"), at(-2, -2, 1100, 120));
        root.addView(text(c, tf, "Overlap two"), at(-2, -2, 1130, 130));
        // clean: same labels apart
        root.addView(text(c, tf, "Apart one"), at(-2, -2, 1300, 360));
        root.addView(text(c, tf, "Apart two"), at(-2, -2, 1300, 440));
        // planted: a label on top of a tile
        View tile = new View(c);
        tile.setBackgroundColor(0xff444444);
        tile.setContentDescription("tile");
        root.addView(tile, at(300, 120, 1300, 560));
        root.addView(text(c, tf, "On a tile"), at(-2, -2, 1320, 590));

        // planted: long text in a narrow box with no ellipsis
        TextView bad = new TextView(c);
        bad.setSingleLine(true);
        bad.setEllipsize(null);
        bad.setHorizontallyScrolling(true);
        bad.setTextSize(40);
        bad.setTextColor(Color.WHITE);
        bad.setText("A very long name that cannot fit");
        root.addView(bad, at(200, -2, 96, 700));
        // clean: same text through Kit.label
        TextView good = Kit.label(c, tf, 40, Color.WHITE);
        good.setText("A very long name that cannot fit");
        root.addView(good, at(200, -2, 96, 820));
        return root;
    }

    private static LinearLayout row(Context c, boolean room) {
        LinearLayout r = new LinearLayout(c);
        for (int i = 0; i < 3; i++) {
            View t = new View(c);
            t.setBackgroundColor(0xff1f80e0);
            t.setContentDescription((room ? "ok-" : "bad-") + i);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(300, 120);
            p.leftMargin = i == 0 ? 0 : 40;
            r.addView(t, p);
            if (i == 0) {
                t.setScaleX(1.1f);
                t.setScaleY(1.1f);
                t.setForeground(Kit.ring(t, 16, 5, 8, Color.WHITE));
            }
        }
        return r;
    }

    private static TextView text(Context c, Typeface tf, String s) {
        TextView t = Kit.label(c, tf, 48, Color.WHITE);
        t.setText(s);
        return t;
    }

    private static FrameLayout.LayoutParams at(int w, int h, int x, int y) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h);
        p.leftMargin = x;
        p.topMargin = y;
        return p;
    }
}
