package com.livingroom.flamingo.perftest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Dummy artwork, generated once per process and cached. Real art arrives in Plan 2. */
final class Art {
    static final String[] NAMES = {"YouTube", "Netflix", "Hotstar", "Prime Video", "PlayStation"};
    static final int[] COLORS = {0xffff0033, 0xffe50914, 0xff1f80e0, 0xff00a8e1, 0xff0070d1};

    private static final Bitmap[] HERO = new Bitmap[5];
    private static final Bitmap[] BANNER = new Bitmap[5];
    private static Bitmap dot, record;

    /** 960x540 RGB_565, already dimmed on the left so no overlay has to be drawn per frame. */
    static Bitmap hero(int i) {
        if (HERO[i] != null) return HERO[i];
        Bitmap b = Bitmap.createBitmap(960, 540, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        c.drawColor(0xff0a0507);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(690, 150, 520, COLORS[i], 0x000a0507, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 960, 540, p);
        p.setShader(new LinearGradient(0, 0, 600, 0, 0xe0000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 960, 540, p);
        return HERO[i] = b;
    }

    static Bitmap banner(int i) {
        if (BANNER[i] != null) return BANNER[i];
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        c.drawColor(COLORS[i]);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(40);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(NAMES[i], 160, 104, p);
        return BANNER[i] = b;
    }

    /** 24px tile with one 2px dot at 9% white, for a static tiled background. */
    static Bitmap dotTile() {
        if (dot != null) return dot;
        Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x17ffffff);
        c.drawCircle(12, 12, 2, p);
        return dot = b;
    }

    static Bitmap record() {
        if (record != null) return record;
        Bitmap b = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        for (int r = 148; r > 50; r -= 4) {
            p.setColor((r / 4) % 2 == 0 ? 0xff1b1b1b : 0xff0a0a0a);
            c.drawCircle(150, 150, r, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.drawCircle(150, 150, 40, p);
        p.setColor(Color.BLACK);
        c.drawCircle(150, 150, 6, p);
        p.setColor(0xffff2a1a);               // a mark so the rotation is visible
        c.drawCircle(150, 120, 5, p);
        return record = b;
    }
}
