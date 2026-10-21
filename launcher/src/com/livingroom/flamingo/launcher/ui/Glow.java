package com.livingroom.flamingo.launcher.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import java.util.HashMap;
import java.util.Map;

/** Backdrop for slots with no artwork: a soft glow in the app's brand colour, drawn once and cached. */
final class Glow {
    private static final Map<String, Integer> BRAND = new HashMap<>();
    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    static {
        BRAND.put("com.google.android.youtube.tv", 0xffff0033);
        BRAND.put("com.netflix.ninja", 0xffe50914);
        BRAND.put("in.startv.hotstar", 0xff1f80e0);
        BRAND.put("com.amazon.amazonvideo.livingroom", 0xff00a8e1);
        BRAND.put("com.spotify.tv.android", 0xff1db954);
    }

    private Glow() { }

    static int color(String key) {
        Integer c = BRAND.get(key);
        return c != null ? c : key.startsWith("hdmi:") ? 0xff0070d1 : 0xff5b5b66;
    }

    static Bitmap forKey(String key) {
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        b = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xff0a0507);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(340, 80, 280, color(key), 0x000a0507, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 480, 270, p);
        CACHE.put(key, b);
        return b;
    }
}
