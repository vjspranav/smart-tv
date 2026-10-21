package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.MediaItem;
import java.util.ArrayList;
import java.util.List;

/**
 * N · Nothing. Home: black dot grid, a big dot-matrix clock, a "Continue" widget showing the focused item's show in
 * greyscale with a dotted progress bar, and the five main items as mono pills (focused: white, with the red dot).
 * Everything and All apps: the shared screens in N's dress (widget tiles, grey banners that light up on focus).
 */
public final class LayoutN extends LayoutView {
    private static final int SIDE = 96, WIDGET_W = 740, WIDGET_H = 420, WIDGET_R = 32, RING = 10;
    private static final int PILL_H = 96, PILL_GAP = 24, PILL_R = 48;
    private static final float PILL_GROW = 1.04f;

    private final Typeface bold, mono;
    private final FrameLayout homeGroup, widget;
    private final Everything all;
    private final TextView clock, date, wLabel, wTitle, wMeta;
    private final ImageView wArt;
    private final View wBar;
    private final Dots.Bar bar = new Dots.Bar();
    private final LinearLayout pills;
    private final List<TextView> pillViews = new ArrayList<>();
    private final Hero.Cache arts = new Hero.Cache(6);
    private int mainFocus;
    private boolean onWidget, everything;
    private String artKey = "";

    public LayoutN(Context c, Sources src, Runnable openHomeSettings, Runnable openAllApps) {
        super(c, src, openHomeSettings, openAllApps);
        bold = Fonts.get(c, Fonts.INTER, 700);
        mono = Fonts.get(c, Fonts.MONO);
        setBackground(Dots.grid(getResources()));   // opaque: the only full-screen fill

        homeGroup = new FrameLayout(c);
        homeGroup.setClipChildren(false);
        addView(homeGroup, new LayoutParams(-1, -1));
        clock = Kit.label(c, Fonts.get(c, Fonts.DOTO, 900), 220, Color.WHITE);
        clock.setLetterSpacing(0.02f);
        homeGroup.addView(clock, at(-2, -2, SIDE - 8, 70));
        date = caps(c, 24, 0x99ffffff);
        homeGroup.addView(date, at(-2, -2, SIDE, 352));   // clock: 220 px Doto ≈ 264 px line, ends at 334

        // Continue widget, top right; artwork baked greyscale with its shade, text on top
        widget = new FrameLayout(c) {
            @Override public boolean hasOverlappingRendering() { return false; }
        };
        widget.setBackground(Kit.rounded(0xff121212, WIDGET_R, 1, 0x1cffffff));
        wArt = new ImageView(c);
        wArt.setScaleType(ImageView.ScaleType.FIT_XY);
        wArt.setTag(Kit.BACKDROP, true);
        wArt.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), WIDGET_R); }
        });
        wArt.setClipToOutline(true);
        widget.addView(wArt, new LayoutParams(-1, -1));
        wLabel = caps(c, 18, 0x99ffffff);
        widget.addView(wLabel, inset(-1, 36, Gravity.TOP));
        wTitle = Kit.label(c, bold, 52, Color.WHITE);
        wTitle.setLetterSpacing(-0.02f);
        Kit.fit(wTitle, 52, 34);
        widget.addView(wTitle, inset(-1, 124, Gravity.BOTTOM));   // clear of the meta line (82 + 30 + 12)
        wMeta = caps(c, 20, 0x99ffffff);
        widget.addView(wMeta, inset(-1, 82, Gravity.BOTTOM));
        wBar = new View(c);
        wBar.setBackground(bar);
        widget.addView(wBar, inset(16, 40, Gravity.BOTTOM));
        homeGroup.addView(widget, at(WIDGET_W, WIDGET_H, 1920 - SIDE - WIDGET_W, 80));

        pills = new LinearLayout(c);
        pills.setClipChildren(false);
        LayoutParams pp = new LayoutParams(-1, PILL_H, Gravity.BOTTOM);
        pp.leftMargin = SIDE;
        pp.rightMargin = SIDE;
        pp.bottomMargin = 150;
        homeGroup.addView(pills, pp);
        TextView hint = caps(c, 18, 0x66ffffff);
        hint.setText("↓ EVERYTHING");
        LayoutParams hp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hp.bottomMargin = 64;
        homeGroup.addView(hint, hp);

        all = new Everything(c, src, Everything.Style.nothing(c), () -> showEverything(false));
        all.setAlpha(0f);
        all.setVisibility(INVISIBLE);
        addView(all, new LayoutParams(-1, -1));
    }

    // ---------- data ----------

    @Override public void bind(Screen s) {
        boolean rebuild = screen == null || !sameMain(screen, s);
        screen = s;
        clock.setText(s.clock);
        SpannableString d = new SpannableString("● " + s.date.toUpperCase() + (s.ampm.isEmpty() ? "" : " · " + s.ampm.toUpperCase()));
        d.setSpan(new ForegroundColorSpan(Dots.RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        date.setText(d);
        if (rebuild) buildPills();
        for (Screen.Slot sl : s.main) art(sl);
        showSlot(false);
        all.bind(s, Sections.build(s, src, openHomeSettings, openAllApps), everything);
    }

    private static boolean sameMain(Screen a, Screen b) {
        if (a.main.size() != b.main.size()) return false;
        for (int i = 0; i < a.main.size(); i++) if (!a.main.get(i).key().equals(b.main.get(i).key())) return false;
        return true;
    }

    private void buildPills() {
        pills.removeAllViews();
        pillViews.clear();
        for (int i = 0; i < screen.main.size(); i++) {
            TextView t = new TextView(getContext()) {
                @Override public boolean hasOverlappingRendering() { return false; }
            };
            t.setTypeface(mono);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setIncludeFontPadding(false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(28, 0, 28, 0);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
            p.leftMargin = i == 0 ? 0 : PILL_GAP;
            pills.addView(t, p);
            pillViews.add(t);
        }
        mainFocus = Math.min(mainFocus, Math.max(0, pillViews.size() - 1));
    }

    /** Pills, then the widget for the focused item. */
    private void showSlot(boolean animate) {
        if (screen.main.isEmpty()) return;
        for (int i = 0; i < pillViews.size(); i++) {
            TextView t = pillViews.get(i);
            boolean on = i == mainFocus && !onWidget, here = i == mainFocus;
            String name = screen.main.get(i).name();
            if (here) {
                SpannableString sp = new SpannableString("● " + name);
                sp.setSpan(new ForegroundColorSpan(Dots.RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                t.setText(sp);
            } else t.setText(name);
            // focused: white; the item the widget shows while the widget has focus: outlined brighter
            t.setBackground(on ? Kit.rounded(Color.WHITE, PILL_R, 0, 0) : Kit.rounded(0, PILL_R, 2, here ? 0x99ffffff : 0x30ffffff));
            t.setTextColor(on ? Color.BLACK : here ? Color.WHITE : 0xb3ffffff);
            t.setTag(Kit.OUTSET, 0);
            float g = on ? PILL_GROW : 1f;
            if (animate) t.animate().scaleX(g).scaleY(g).setDuration(150).start(); else { t.setScaleX(g); t.setScaleY(g); }
        }
        widget.setForeground(onWidget ? Kit.ring(widget, WIDGET_R, 4, RING, Color.WHITE) : null);
        widget.setTag(Kit.OUTSET, onWidget ? RING : 0);

        Screen.Slot s = screen.main.get(mainFocus);
        MediaItem m = s.cont;
        if (s.port != null) {
            wLabel.setText("Input · HDMI " + s.port.port);
            wTitle.setText(s.port.name);
            wMeta.setText(Sections.state(s.port) + " · OK to switch");
        } else if (m != null && m.title != null) {
            wLabel.setText("Continue · " + s.name());
            wTitle.setText(m.title);
            String meta = Hero.meta(m);
            wMeta.setText(meta.isEmpty() ? "OK to resume" : meta);
        } else {
            wLabel.setText("Open · " + s.name());
            wTitle.setText(s.name());
            wMeta.setText("Nothing to continue");
        }
        float frac = m != null && m.durationMs > 0 ? Math.min(1f, (float) m.positionMs / m.durationMs) : -1f;
        wBar.setVisibility(frac > 0 ? VISIBLE : INVISIBLE);
        if (frac > 0) bar.set(frac);
        String key = s.key() + "|" + (m != null ? m.artPath : "");
        if (!key.equals(artKey)) {
            artKey = key;
            wArt.setImageBitmap(art(s));
            if (animate) { wArt.setAlpha(0f); wArt.animate().alpha(1f).setDuration(200).start(); }
        }
    }

    /**
     * The widget's picture, baked once per show: the artwork cropped to the widget in greyscale under N's shade
     * (dark at the bottom and left, where the text sits), or a faint white glow when there's no artwork.
     */
    private Bitmap art(Screen.Slot s) {
        String key = s.key() + "|" + (s.cont != null ? s.cont.artPath : "");
        Bitmap b = arts.get(key);
        if (b != null) return b;
        Bitmap a = src.art(s.cont);
        b = Bitmap.createBitmap(WIDGET_W, WIDGET_H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(0xff121212);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        if (a != null) {
            p.setColorFilter(Dots.GREY);
            float scale = Math.max((float) WIDGET_W / a.getWidth(), (float) WIDGET_H / a.getHeight());
            int sw = Math.round(WIDGET_W / scale), sh = Math.round(WIDGET_H / scale);
            int sx = (a.getWidth() - sw) / 2, sy = (a.getHeight() - sh) / 2;
            cv.drawBitmap(a, new Rect(sx, sy, sx + sw, sy + sh), new Rect(0, 0, WIDGET_W, WIDGET_H), p);
            Paint shade = new Paint(Paint.DITHER_FLAG);
            shade.setShader(new LinearGradient(0, 0, 0, WIDGET_H, new int[]{0x99121212, 0x40121212, 0xf2121212},
                    new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, WIDGET_W, WIDGET_H, shade);
            shade.setShader(new LinearGradient(0, 0, WIDGET_W, 0, new int[]{0xb3121212, 0x00121212},
                    new float[]{0f, 0.7f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, WIDGET_W, WIDGET_H, shade);
        } else {
            p.setShader(new RadialGradient(WIDGET_W * 0.8f, WIDGET_H * 0.2f, WIDGET_W * 0.6f, 0x22ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, WIDGET_W, WIDGET_H, p);
        }
        arts.put(key, b);
        return b;
    }

    // ---------- keys ----------

    @Override public boolean key(int k) {
        if (everything) return all.key(k);
        if (screen == null || screen.main.isEmpty()) return true;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                if (onWidget) return true;
                int n = mainFocus + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                if (n < 0 || n >= pillViews.size()) return true;
                mainFocus = n;
                showSlot(true);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!onWidget) { onWidget = true; showSlot(true); }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (onWidget) { onWidget = false; showSlot(true); } else showEverything(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                src.open(screen.main.get(mainFocus));   // the widget continues the same app or input
                return true;
            default:
                return true;
        }
    }

    @Override public void showEverything(boolean on) {
        if (on == everything) return;
        everything = on;
        onWidget = false;
        if (screen != null) showSlot(false);
        fade(homeGroup, !on);
        fade(all, on);
        if (on) all.reset(true);
    }

    private static void fade(final View v, boolean show) {
        v.animate().cancel();
        if (show) {
            v.setVisibility(VISIBLE);
            v.setTranslationY(30);
            v.animate().alpha(1f).translationY(0).setDuration(220).withLayer().start();
        } else {
            v.animate().alpha(0f).setDuration(160).withLayer().withEndAction(() -> v.setVisibility(INVISIBLE)).start();
        }
    }

    @Override public boolean inEverything() { return everything; }

    @Override public void focus(String which) {
        boolean last = "last".equals(which);
        if (everything) { all.focus(last); return; }
        if (pillViews.isEmpty()) return;
        onWidget = false;
        mainFocus = last ? pillViews.size() - 1 : 0;
        showSlot(false);
    }

    // ---------- helpers ----------

    private TextView caps(Context c, float px, int color) {
        TextView t = Kit.label(c, mono, px, color);
        t.setLetterSpacing(0.2f);
        t.setAllCaps(true);
        return t;
    }

    private static LayoutParams at(int w, int h, int x, int y) {
        LayoutParams p = new LayoutParams(w, h, Gravity.TOP | Gravity.START);
        p.leftMargin = x;
        p.topMargin = y;
        return p;
    }

    /** Inside the widget: 36 px padding on the sides; y from the top or bottom edge. */
    private static LayoutParams inset(int h, int y, int vertical) {
        LayoutParams p = new LayoutParams(-1, h, vertical | Gravity.START);
        p.leftMargin = 36;
        p.rightMargin = 36;
        if (vertical == Gravity.TOP) p.topMargin = y; else p.bottomMargin = y;
        return p;
    }
}
