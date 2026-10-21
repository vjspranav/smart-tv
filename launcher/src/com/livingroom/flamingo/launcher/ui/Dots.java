package com.livingroom.flamingo.launcher.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
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

    /** The background: black with one dot per 24 px cell, repeated by a bitmap shader (one opaque fill per frame). */
    public static Drawable grid(Resources r) {
        Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xff000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x1fffffff);
        new Canvas(b).drawCircle(12, 12, 1.7f, p);
        BitmapDrawable d = new BitmapDrawable(r, b);
        d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
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
