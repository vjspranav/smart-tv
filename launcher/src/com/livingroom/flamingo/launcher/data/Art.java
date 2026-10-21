package com.livingroom.flamingo.launcher.data;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Bitmap helpers: everything is scaled once and stored small. Banners are RGB_565; full-screen art is ARGB_8888 so dark gradients don't band. */
final class Art {
    private Art() { }

    /**
     * Scales src down (never up) to fit inside w×h, keeping its shape, ARGB_8888. Layouts decide how to show it:
     * large 16:9 art goes full screen, anything smaller or another shape is shown as a card.
     */
    static Bitmap fit(Bitmap src, int w, int h) {
        float s = Math.min(1f, Math.min((float) w / src.getWidth(), (float) h / src.getHeight()));
        int ow = Math.max(1, Math.round(src.getWidth() * s)), oh = Math.max(1, Math.round(src.getHeight() * s));
        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(0, 0, src.getWidth(), src.getHeight()), new Rect(0, 0, ow, oh), new Paint(Paint.FILTER_BITMAP_FLAG));
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
