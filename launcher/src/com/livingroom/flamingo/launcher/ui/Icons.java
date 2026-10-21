package com.livingroom.flamingo.launcher.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Line icons for the fixed tiles (TV settings, Home screen, All apps), drawn on a 24-unit grid; no image files. */
public final class Icons extends Drawable {
    public enum Kind { PICTURE, SOUND, SLEEP, WIFI, SETTINGS, HOME, APPS }

    private final Kind kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    public Icons(Kind kind, int color) {
        this.kind = kind;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Maps a settings/home item key to its icon, or null for items without one. */
    public static Kind forKey(String key) {
        switch (key) {
            case "picture": return Kind.PICTURE;
            case "sound": return Kind.SOUND;
            case "sleep": return Kind.SLEEP;
            case "wifi": return Kind.WIFI;
            case "all": return Kind.SETTINGS;
            case "home": return Kind.HOME;
            case "allapps": return Kind.APPS;
            default: return null;
        }
    }

    @Override public void draw(Canvas c) {
        int save = c.save();
        c.translate(getBounds().left, getBounds().top);
        c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        path.reset();
        switch (kind) {
            case PICTURE:  // screen with a mountain and sun
                r.set(2.5f, 4.5f, 21.5f, 17.5f);
                c.drawRoundRect(r, 2, 2, p);
                path.moveTo(5.5f, 15f); path.lineTo(10f, 10f); path.lineTo(13f, 13f); path.lineTo(15f, 11f); path.lineTo(18.5f, 15f);
                c.drawPath(path, p);
                c.drawCircle(16.5f, 8f, 1.3f, p);
                c.drawLine(8f, 20.5f, 16f, 20.5f, p);
                break;
            case SOUND:  // speaker with two waves
                path.moveTo(3.5f, 9.5f); path.lineTo(7f, 9.5f); path.lineTo(11.5f, 5.5f); path.lineTo(11.5f, 18.5f);
                path.lineTo(7f, 14.5f); path.lineTo(3.5f, 14.5f); path.close();
                c.drawPath(path, p);
                r.set(10f, 8f, 18f, 16f); c.drawArc(r, -50, 100, false, p);
                r.set(9f, 4.5f, 22f, 19.5f); c.drawArc(r, -50, 100, false, p);
                break;
            case SLEEP:  // crescent moon
                path.moveTo(19.5f, 14.5f);
                path.cubicTo(18.3f, 18f, 15f, 20.5f, 11.2f, 20.5f);
                path.cubicTo(6.4f, 20.5f, 3.5f, 17.2f, 3.5f, 12.8f);
                path.cubicTo(3.5f, 9f, 6f, 5.7f, 9.5f, 4.5f);
                path.cubicTo(8.9f, 5.7f, 8.6f, 7f, 8.6f, 8.4f);
                path.cubicTo(8.6f, 12.9f, 12.2f, 16.4f, 16.6f, 16.4f);
                path.cubicTo(17.6f, 16.4f, 18.6f, 15.8f, 19.5f, 14.5f);
                path.close();
                c.drawPath(path, p);
                break;
            case WIFI:  // three arcs and a dot
                r.set(2f, 6f, 22f, 26f); c.drawArc(r, 225, 90, false, p);
                r.set(5.5f, 9.5f, 18.5f, 22.5f); c.drawArc(r, 225, 90, false, p);
                r.set(9f, 13f, 15f, 19f); c.drawArc(r, 225, 90, false, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12f, 19.5f, 1.4f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case SETTINGS:  // gear: ring with eight teeth
                c.drawCircle(12f, 12f, 3f, p);
                c.drawCircle(12f, 12f, 6.5f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine(12f + 6.5f * (float) Math.cos(a), 12f + 6.5f * (float) Math.sin(a),
                            12f + 9f * (float) Math.cos(a), 12f + 9f * (float) Math.sin(a), p);
                }
                break;
            case HOME:  // house
                path.moveTo(3.5f, 11f); path.lineTo(12f, 4f); path.lineTo(20.5f, 11f);
                path.moveTo(6f, 9.5f); path.lineTo(6f, 20f); path.lineTo(18f, 20f); path.lineTo(18f, 9.5f);
                path.moveTo(10f, 20f); path.lineTo(10f, 14.5f); path.lineTo(14f, 14.5f); path.lineTo(14f, 20f);
                c.drawPath(path, p);
                break;
            case APPS:  // 3×3 grid of rounded squares
                for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
                    r.set(3.5f + x * 6.5f, 3.5f + y * 6.5f, 7.5f + x * 6.5f, 7.5f + y * 6.5f);
                    c.drawRoundRect(r, 1.2f, 1.2f, p);
                }
                break;
        }
        c.restoreToCount(save);
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
