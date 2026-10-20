package com.livingroom.flamingo.launcher.data;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Bitmap helpers: everything is scaled once, stored small and as RGB_565 (half the memory of ARGB). */
final class Art {
    private Art() { }

    /** Center-crops src to exactly w×h, RGB_565. */
    static Bitmap cover(Bitmap src, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        float s = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        int cw = Math.round(w / s), ch = Math.round(h / s);
        int x = (src.getWidth() - cw) / 2, y = (src.getHeight() - ch) / 2;
        new Canvas(out).drawBitmap(src, new Rect(x, y, x + cw, y + ch), new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** Draws d into a w×h RGB_565 bitmap over bg; d is fitted inside with its aspect kept. */
    static Bitmap draw(Drawable d, int w, int h, int bg) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        c.drawColor(bg);
        int iw = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : w, ih = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : h;
        float s = Math.min((float) w / iw, (float) h / ih);
        int dw = Math.round(iw * s), dh = Math.round(ih * s);
        d.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
        d.draw(c);
        return out;
    }
}
