package com.livingroom.flamingo.launcher.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** N · Nothing's dot-matrix pieces: the faint background grid, a dotted progress bar, and the greyscale filter. */
public final class Dots {
    public static final int RED = 0xffff2a1a;
    /** Greyscale, for N's monochrome banners and artwork (focus brings the colour back). */
    public static final ColorFilter GREY;

    static {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0f);
        GREY = new ColorMatrixColorFilter(m);
    }

    private Dots() { }

    /** View tag key on an N banner ImageView: its colour bitmap (it shows a baked grey copy until focused). */
    public static final int COLOUR = 0x7f0f0003;
    private static final java.util.Map<Bitmap, Bitmap> greys = new java.util.WeakHashMap<>();

    /** b in greyscale, baked once per bitmap: a live colour filter on every banner cost several ms a frame. */
    public static synchronized Bitmap grey(Bitmap b) {
        Bitmap g = greys.get(b);
        if (g == null) {
            g = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(GREY);
            new Canvas(g).drawBitmap(b, 0, 0, p);
            greys.put(b, g);
        }
        return g;
    }

    /** Shows b on iv in grey (mono) and remembers the colour version for focus. */
    public static void setMono(android.widget.ImageView iv, Bitmap b) {
        iv.setTag(COLOUR, b);
        iv.setImageBitmap(grey(b));
    }

    /** Colour on focus, grey otherwise (no-op for an ImageView that isn't mono). */
    public static void light(android.widget.ImageView iv, boolean on) {
        Object c = iv.getTag(COLOUR);
        if (c instanceof Bitmap) iv.setImageBitmap(on ? (Bitmap) c : grey((Bitmap) c));
    }

    private static Bitmap gridBitmap;

    /**
     * The background: black with one dot per 24 px cell, baked once into a full-screen RGB_565 bitmap (4 MB) and
     * drawn 1:1. A repeating 24 px shader looked the same but cost ~7 ms a frame on this GPU.
     */
    public static synchronized Drawable grid(Resources r) {
        if (gridBitmap == null) {
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.RGB_565);
            b.eraseColor(0xff000000);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0x1fffffff);
            for (int y = 12; y < 1080; y += 24) for (int x = 12; x < 1920; x += 24) c.drawCircle(x, y, 1.7f, p);
            gridBitmap = b;
        }
        BitmapDrawable d = new BitmapDrawable(r, gridBitmap);
        d.setFilterBitmap(false);
        return d;
    }

    /** A row of dots: bright up to frac, dim after. */
    public static final class Bar extends Drawable {
        private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float frac;

        public Bar() {
            on.setColor(0xffffffff);
            off.setColor(0x33ffffff);
        }

        public void set(float f) {
            frac = Math.max(0f, Math.min(1f, f));
            invalidateSelf();
        }

        @Override public void draw(Canvas c) {
            float h = getBounds().height(), r = h / 2f * 0.72f, step = h * 1.3f;
            int n = (int) (getBounds().width() / step);
            for (int i = 0; i < n; i++)
                c.drawCircle(getBounds().left + step * i + h / 2f, getBounds().exactCenterY(), r, (i + 0.5f) / n <= frac ? on : off);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
