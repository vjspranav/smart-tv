package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.TextView;

/** Shared building blocks that enforce the no-overflow and focus-never-clips rules. */
public final class Kit {
    /** View tag key holding how far (px) a view draws outside its bounds (focus ring), for Overflow. */
    public static final int OUTSET = 0x7f0f0001;
    /** View tag key: true on full-bleed artwork that text is allowed to sit on. */
    public static final int BACKDROP = 0x7f0f0002;

    /** Rounded 16px clip for tiles and artwork. */
    public static final ViewOutlineProvider ROUNDED_16 = new ViewOutlineProvider() {
        @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 16); }
    };

    private Kit() { }

    /** Single-line label that ellipsizes at the end; never wraps. */
    public static TextView label(Context c, Typeface tf, float px, int color) {
        TextView t = new TextView(c);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Shrinks the text (down to minPx) until it fits its laid-out width; past that, ellipsis takes over. */
    public static void fit(final TextView t, final float maxPx, final float minPx) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, maxPx);
        t.addOnLayoutChangeListener((v, l, top, r, b, ol, ot, or, ob) -> {
            int avail = t.getWidth() - t.getPaddingLeft() - t.getPaddingRight();
            if (avail <= 0) return;
            Paint p = new Paint(t.getPaint());
            float size = maxPx;
            CharSequence s = t.getText();
            while (size > minPx) {
                p.setTextSize(size);
                if (p.measureText(s, 0, s.length()) <= avail) break;
                size -= 2;
            }
            final float fitted = Math.max(size, minPx);
            if (Math.abs(fitted - t.getTextSize()) > 0.5f) t.post(() -> t.setTextSize(TypedValue.COMPLEX_UNIT_PX, fitted));
        });
    }

    /**
     * Lets focused children of row grow without being clipped: turns off clipping from row up to the root,
     * and pads row by the growth plus the ring outset on every side.
     */
    public static void focusRoom(ViewGroup row, int childW, int childH, float maxScale, int outsetPx) {
        int padX = (int) Math.ceil(childW * (maxScale - 1f) / 2f) + outsetPx;
        int padY = (int) Math.ceil(childH * (maxScale - 1f) / 2f) + outsetPx;
        row.setPadding(row.getPaddingLeft() + padX, row.getPaddingTop() + padY,
                row.getPaddingRight() + padX, row.getPaddingBottom() + padY);
        noClip(row);
    }

    /** Turns off child and padding clipping for v and every ancestor. */
    public static void noClip(View v) {
        for (View cur = v; cur != null; ) {
            if (cur instanceof ViewGroup) {
                ((ViewGroup) cur).setClipChildren(false);
                ((ViewGroup) cur).setClipToPadding(false);
            }
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
    }

    /** A focus ring drawn outsetPx outside the view's bounds; set it as the view's foreground. */
    public static Drawable ring(View v, int radiusPx, int strokePx, int outsetPx, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radiusPx + outsetPx);
        g.setStroke(strokePx, color);
        v.setTag(OUTSET, outsetPx);
        return new InsetDrawable(g, -outsetPx);
    }

    /**
     * Fades v to alpha a (instantly when !animate) without touching its other animations: cancelling the
     * animator would also stop a focused tile's grow, leaving it the wrong size.
     */
    public static void fade(View v, float a, boolean animate) {
        v.animate().alpha(a).setDuration(animate ? 160 : 0).start();
        if (!animate) v.setAlpha(a);
    }

    public static GradientDrawable rounded(int fill, int radiusPx, int strokePx, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }
}
