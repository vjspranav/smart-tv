package com.livingroom.flamingo.launcher.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import com.livingroom.flamingo.launcher.model.MediaItem;

/**
 * Full-screen backdrops baked into one half-resolution bitmap (artwork + shades), so a layout draws a single
 * full-screen image per frame. Large 16:9 artwork fills the screen; anything smaller or another shape
 * (Netflix's badged stills, album art) is drawn sharp as a card over a glow in its own colour.
 */
final class Hero {
    static final int W = 960, H = 540;

    private Hero() { }

    static boolean fullBleed(Bitmap art) {
        float aspect = (float) art.getWidth() / art.getHeight();
        return art.getWidth() >= 900 && Math.abs(aspect - 16f / 9f) < 0.08f;
    }

    /**
     * @param glow       art is a brand glow (always fills)
     * @param shade      left-to-right shade colours, with stops at pos (null: evenly spaced)
     * @param card       where a card goes, in 960×540 space
     * @param cornerFade darken the top-right corner for a clock
     */
    static Bitmap compose(Bitmap art, boolean glow, int[] shade, float[] pos, RectF card, boolean cornerFade) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        Paint sp = new Paint(Paint.DITHER_FLAG);
        sp.setShader(new LinearGradient(0, 0, W, 0, shade, pos, Shader.TileMode.CLAMP));
        boolean fill = glow || fullBleed(art);
        if (fill) {
            float sc = Math.max((float) W / art.getWidth(), (float) H / art.getHeight());
            float dw = art.getWidth() * sc, dh = art.getHeight() * sc;
            cv.drawBitmap(art, null, new RectF((W - dw) / 2, (H - dh) / 2, (W + dw) / 2, (H + dh) / 2), pt);
            cv.drawRect(0, 0, W, H, sp);
        } else {
            float sc = Math.min(card.width() / art.getWidth(), card.height() / art.getHeight());
            float cw = art.getWidth() * sc, ch = art.getHeight() * sc;
            RectF r = new RectF(card.right - cw, card.centerY() - ch / 2, card.right, card.centerY() + ch / 2);
            cv.drawColor(0xff0a0507);
            // The artwork's average colour, lifted so dark stills still give a visible glow
            float[] hsv = new float[3];
            Color.colorToHSV(Bitmap.createScaledBitmap(art, 1, 1, true).getPixel(0, 0), hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.4f);
            hsv[2] = Math.max(hsv[2], 0.6f);
            int avg = Color.HSVToColor(hsv) & 0x00ffffff;
            pt.setShader(new RadialGradient(r.centerX(), r.centerY(), 520, new int[]{avg | 0x99000000, avg | 0x33000000, 0}, null,
                    Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, pt);
            cv.drawRect(0, 0, W, H, sp);   // shade under the card, so the card itself stays undimmed
            pt.setShader(null);
            pt.setColor(0xff000000);
            pt.setShadowLayer(18, 0, 6, 0x99000000);
            cv.drawRoundRect(r, 8, 8, pt);
            pt.clearShadowLayer();
            int save = cv.save();
            Path clip = new Path();
            clip.addRoundRect(r, 8, 8, Path.Direction.CW);
            cv.clipPath(clip);
            cv.drawBitmap(art, null, r, pt);
            cv.restoreToCount(save);
        }
        if (cornerFade) {
            pt.setShader(new RadialGradient(W, 0, 235, new int[]{0x99000000, 0x4d000000, 0}, null, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, pt);
        }
        b.prepareToDraw();
        return b;
    }

    /** "S1 · E1 · 46 min left", or whichever parts exist. */
    static String meta(MediaItem m) {
        String left = m.durationMs > 0 && m.positionMs > 0 ? Math.max(1, (m.durationMs - m.positionMs) / 60000) + " min left" : null;
        if (m.subtitle != null && left != null) return m.subtitle + " · " + left;
        return m.subtitle != null ? m.subtitle : left != null ? left : "";
    }

    /** Small most-recently-used cache for composed backdrops (~2 MB each). */
    static final class Cache extends java.util.LinkedHashMap<String, Bitmap> {
        private final int max;
        Cache(int max) { super(8, 0.75f, true); this.max = max; }
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap> e) { return size() > max; }
    }
}
