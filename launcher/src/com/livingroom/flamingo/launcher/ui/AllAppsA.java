package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.AllApps;
import com.livingroom.flamingo.launcher.model.AppEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * A · Cinema All apps: the focused app's name in big type, a Groups | A–Z switch (plus a letter index in A–Z),
 * groups on the left that jump the grid, and A's 16:9 banners with the white ring. Rows page; nothing is half shown.
 */
public final class AllAppsA extends AllAppsView {
    private static final int SIDE = 96, WIN_T = 330, WIN_B = 1016, GAP = 32, ROW_GAP = 34, RING = 10;
    private static final float GROW = 1.08f;

    private final Typeface bold, black, mid;
    private final TextView eyebrow, title, meta, clock;
    private FrameLayout sheet;
    private final List<View> paged = new ArrayList<>();     // views on the sheet that page in and out
    private final List<int[]> spans = new ArrayList<>();    // their [top, bottom, isTile] on the sheet
    private final List<View> rail = new ArrayList<>();
    private final List<TextView> letters = new ArrayList<>();
    private int off, tileW;
    private int baseColor = 0xff0a0507;

    public AllAppsA(Context c, Sources src, Runnable close) {
        super(c, src, close);
        bold = Fonts.get(c, Fonts.INTER, 700);
        black = Fonts.get(c, Fonts.INTER, 900);
        mid = Fonts.get(c, Fonts.INTER, 500);
        eyebrow = caps(c, 22, 0xb3ffffff);
        title = Kit.label(c, black, 72, Color.WHITE);
        Kit.fit(title, 72, 48);
        meta = Kit.label(c, mid, 26, 0xccffffff);
        clock = Kit.label(c, mid, 34, 0xd9ffffff);
    }

    @Override protected void build() {
        removeAllViews();
        paged.clear();
        spans.clear();
        rail.clear();
        letters.clear();
        off = 0;
        Context c = getContext();

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        detach(eyebrow, title, meta, clock);
        head.addView(eyebrow, lp(-1, -2, 0));
        head.addView(title, lp(-1, -2, 10));
        head.addView(meta, lp(-1, -2, 8));
        addView(head, at(1300, -2, SIDE, 56));
        clock.setText(screen.clock + (screen.ampm.isEmpty() ? "" : " " + screen.ampm));
        LayoutParams cp = new LayoutParams(-2, -2, Gravity.END);
        cp.rightMargin = SIDE;
        cp.topMargin = 64;
        addView(clock, cp);

        // render every backdrop colour now, so moving focus never draws one mid-animation (only a handful exist)
        for (AppEntry a : screen.az) glow(Glow.color(a.pkg));
        glow(0xff5b5b66);

        int sheetX;
        if (mode == Mode.HIDDEN) {
            sheetX = SIDE;
            tileW = 320;
        } else {
            LinearLayout sw = new LinearLayout(c);
            sw.setBackground(Kit.rounded(0, 30, 2, 0x40ffffff));
            sw.setPadding(5, 5, 5, 5);
            sw.setClipChildren(false);   // the focused option's ring draws outside the pill
            sw.setClipToPadding(false);
            for (Mode m : new Mode[]{Mode.GROUPS, Mode.AZ}) {
                // The ring sits on a plain frame: a single-line TextView scrolls its own canvas, which shifts a foreground
                FrameLayout o = new FrameLayout(c);
                TextView l = Kit.label(c, m == mode ? bold : mid, 24, m == mode ? Color.BLACK : 0x99ffffff);
                l.setText(m == Mode.GROUPS ? "Groups" : "A–Z");
                l.setGravity(Gravity.CENTER);
                l.setPadding(26, 0, 26, 0);
                o.addView(l, new LayoutParams(-2, -1));
                if (m == mode) o.setBackground(Kit.rounded(Color.WHITE, 25, 0, 0));
                sw.addView(o, new LinearLayout.LayoutParams(-2, 50));
                add(o, mode(m));
            }
            addView(sw, at(-2, 60, SIDE, 236));
            if (mode == Mode.AZ) {
                LinearLayout idx = new LinearLayout(c);
                idx.setClipChildren(false);
                for (char l : AllApps.letters(screen.az)) {
                    FrameLayout f = new FrameLayout(c);   // ring on the frame (see the switch)
                    TextView t = Kit.label(c, bold, 28, 0x59ffffff);
                    t.setText(String.valueOf(l));
                    t.setGravity(Gravity.CENTER);
                    f.addView(t, new LayoutParams(-1, -1));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(46, 50);
                    lp.topMargin = 5;
                    idx.addView(f, lp);
                    letters.add(t);
                    add(f, letter(l));
                }
                addView(idx, at(-2, 60, SIDE + 300, 236));
                sheetX = SIDE;
                tileW = 320;
            } else {
                sheetX = 384;
                tileW = 336;
                buildRail(c);
            }
        }

        sheet = new FrameLayout(c);
        sheet.setClipChildren(false);
        addView(sheet, at(W() - sheetX - SIDE, WIN_B - WIN_T, sheetX, WIN_T));
        int cols = mode == Mode.GROUPS ? 4 : 5, tileH = tileW * 9 / 16, y = 0;
        if (mode == Mode.GROUPS) {
            for (AllApps.Section s : screen.groups) {
                TextView l = caps(c, 22, 0x8cffffff);
                l.setText(s.group.title);
                sheet.addView(l, at(-1, 30, 0, y));
                page(l, y, y + 30, false);
                y += 60;   // heading 30 + 30 clear of a focused tile's growth and ring
                y = grid(s.apps, cols, tileH, y) + 40;
            }
        } else {
            List<AppEntry> apps = list();
            if (apps.isEmpty()) {
                TextView none = Kit.label(c, mid, 30, 0x99ffffff);
                none.setText(mode == Mode.HIDDEN ? "Nothing hidden" : "No apps");
                sheet.addView(none, at(-1, -2, 0, 0));
            }
            y = grid(apps, cols, tileH, 0);
        }
        // The sheet holds all of its content: children laid out past a parent's bounds may not draw once it slides
        ((LayoutParams) sheet.getLayoutParams()).height = Math.max(y, WIN_B - WIN_T);
    }

    private void buildRail(Context c) {
        int y = WIN_T;
        for (AllApps.Section s : screen.groups) {
            View r = railRow(c, s.group.title, String.valueOf(s.apps.size()), false);
            addView(r, at(250, 56, SIDE, y));
            rail.add(r);
            add(r, group(s.group));
            y += 64;
        }
        if (!screen.hidden.isEmpty()) {
            View r = railRow(c, "Hidden", String.valueOf(screen.hidden.size()), true);
            addView(r, at(250, 56, SIDE, y + 16));
            rail.add(r);
            add(r, hiddenEntry());
        }
    }

    private View railRow(Context c, String name, String count, boolean dim) {
        FrameLayout f = new FrameLayout(c);
        TextView n = Kit.label(c, mid, 26, dim ? 0x4dffffff : 0x73ffffff);
        n.setText(name);
        n.setTag("name");
        f.addView(n, at(-1, -2, 18, 0, Gravity.CENTER_VERTICAL));
        ((LayoutParams) n.getLayoutParams()).rightMargin = 60;
        TextView k = Kit.label(c, Fonts.get(c, Fonts.MONO, 400), 18, 0x73ffffff);
        k.setText(count);
        LayoutParams kp = new LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        kp.rightMargin = 14;
        f.addView(k, kp);
        return f;
    }

    /** Lays apps out in rows of `cols` from y; returns the y below the last row. */
    private int grid(List<AppEntry> apps, int cols, int tileH, int y) {
        for (int i = 0; i < apps.size(); i++) {
            int col = i % cols;
            if (i > 0 && col == 0) y += tileH + ROW_GAP;
            View t = banner(apps.get(i));
            sheet.addView(t, at(tileW, tileH, col * (tileW + GAP), y));
            page(t, y, y + tileH, true);
            add(t, app(apps.get(i)));
        }
        return apps.isEmpty() ? y : y + tileH;
    }

    private void page(View v, int top, int bottom, boolean tile) {
        paged.add(v);
        spans.add(new int[]{top, bottom, tile ? 1 : 0});
    }

    private View banner(AppEntry a) {
        FrameLayout f = new FrameLayout(getContext()) {
            @Override public boolean hasOverlappingRendering() { return false; }
        };
        Bitmap b = src.banner(a.pkg);
        if (b != null) {
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(b);
            iv.setContentDescription(a.label);
            iv.setOutlineProvider(Kit.ROUNDED_16);
            iv.setClipToOutline(true);
            f.addView(iv, new LayoutParams(-1, -1));
        } else {
            f.setBackground(Kit.rounded(0xff1d1d22, 16, 0, 0));
            TextView t = Kit.label(getContext(), bold, 30, Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setText(a.label);
            f.addView(t, at(-1, -2, 16, 0, Gravity.CENTER));
            ((LayoutParams) t.getLayoutParams()).rightMargin = 16;
        }
        return f;
    }

    // ---------- focus ----------

    @Override protected void switched() {
        // the new view rises in under the pill as it slides: one short layer, then plain drawing again
        sheet.setAlpha(0f);
        sheet.setTranslationY(16);
        sheet.animate().alpha(1f).translationY(0).setDuration(180).withLayer().start();
        for (View r : rail) { r.setAlpha(0f); r.animate().alpha(1f).setDuration(180).start(); }
    }

    @Override protected void moved(int old, int now, boolean animate) {
        if (old >= 0 && old < items.size()) styleFocus(old, false);
        styleFocus(now, true);
        Target t = targets.get(now);
        describe(t);
        markRailAndLetter(t);
        if (t.app != null) scrollTo(items.get(now), animate);
        else if (old < 0) scrollTo(null, false);
    }

    private void styleFocus(int i, boolean on) {
        View v = items.get(i);
        Target t = targets.get(i);
        if (t.mode != null) {   // switch option: the chosen one is filled white; the focused one is lit and ringed
            boolean chosen = t.mode == mode;
            FrameLayout o = (FrameLayout) v;
            o.setBackground(chosen ? Kit.rounded(Color.WHITE, 25, 0, 0) : on ? Kit.rounded(0x4dffffff, 25, 0, 0) : null);
            ((TextView) o.getChildAt(0)).setTextColor(chosen ? Color.BLACK : on ? Color.WHITE : 0x99ffffff);
            o.setForeground(on ? Kit.ring(o, 25, 3, 7, Color.WHITE) : null);
            o.setTag(Kit.OUTSET, on ? 7 : 0);
            o.animate().scaleX(on ? 1.08f : 1f).scaleY(on ? 1.08f : 1f).setDuration(150).start();
            return;
        }
        if (t.letter != null) {   // letters: just light up, no ring
            ((TextView) ((FrameLayout) v).getChildAt(0)).setTextColor(on ? Color.WHITE : 0x59ffffff);
            v.animate().scaleX(on ? 1.25f : 1f).scaleY(on ? 1.25f : 1f).setDuration(150).start();
            return;
        }
        boolean tile = t.app != null;
        int ring = tile ? RING : 6;
        v.setForeground(on ? Kit.ring(v, tile ? 16 : 30, tile ? 5 : 3, ring, Color.WHITE) : null);
        v.setTag(Kit.OUTSET, on ? ring : 0);
        if (tile) v.animate().scaleX(on ? GROW : 1f).scaleY(on ? GROW : 1f).setDuration(150).start();
    }

    private void describe(Target t) {
        String where = mode == Mode.AZ ? "A–Z" : mode == Mode.HIDDEN ? "Hidden" : null;
        if (t.app != null) {
            AllApps.Group g = groupOf(t.app);
            eyebrow.setText("ALL APPS · " + (where != null ? where + (mode == Mode.AZ ? " · " + AllApps.letter(t.app) : "") : g.title));
            title.setText(t.app.label);
            String cont = continueLine(t.app.pkg);
            meta.setText((cont != null ? cont + " · " : (mode == Mode.GROUPS ? "" : g.title + " · ")) + "OK to open · hold OK for options");
        } else if (t.mode != null) {
            eyebrow.setText("ALL APPS · VIEW");
            title.setText(t.mode == Mode.AZ ? "A to Z" : "By group");
            meta.setText(t.mode == mode ? "Showing now" : "OK to switch");
        } else if (t.letter != null) {
            eyebrow.setText("ALL APPS · A–Z");
            title.setText("Letter " + t.letter);
            meta.setText("OK to jump to " + t.letter);
        } else if (t.group != null) {
            eyebrow.setText("ALL APPS · GROUPS");
            title.setText(t.group.title);
            meta.setText("OK to jump to " + t.group.title);
        } else {
            eyebrow.setText("ALL APPS");
            title.setText("Hidden apps");
            meta.setText(screen.hidden.size() + " hidden · OK to see them · hold OK on one to unhide");
        }
        int brand = t.app != null ? Glow.color(t.app.pkg) : 0xff5b5b66;
        setBackground(glow(brand));
    }

    /**
     * The backdrop as a small pre-rendered bitmap per colour (base blended with a touch of the app's colour toward
     * the top right), drawn scaled: a live full-screen radial gradient costs the GPU ~10 ms a frame on this TV.
     */
    private final java.util.Map<Integer, android.graphics.drawable.BitmapDrawable> glows = new java.util.HashMap<>();

    private android.graphics.drawable.Drawable glow(int brand) {
        android.graphics.drawable.BitmapDrawable d = glows.get(brand);
        if (d != null) return d;
        Bitmap b = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.DITHER_FLAG);
        p.setShader(new android.graphics.RadialGradient(480 * 0.86f, 270 * 0.08f, 275,
                new int[]{blend(baseColor, brand, 0.22f), baseColor}, null, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, 480, 270, p);
        d = new android.graphics.drawable.BitmapDrawable(getResources(), b);
        d.setFilterBitmap(true);
        glows.put(brand, d);
        return d;
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    private String continueLine(String pkg) {
        for (Screen.Slot s : screen.main)
            if (s.key().equals(pkg) && s.cont != null && s.cont.title != null) return "Continue " + s.cont.title;
        return null;
    }

    private void markRailAndLetter(Target t) {
        AllApps.Group g = t.app != null && mode == Mode.GROUPS ? groupOf(t.app) : t.group;
        for (int i = 0; i < rail.size(); i++) {
            View r = rail.get(i);
            Target rt = targets.get(items.indexOf(r));
            boolean on = rt.group != null && rt.group == g;
            TextView n = r.findViewWithTag("name");
            n.setTextColor(on ? Color.WHITE : rt.group == null ? 0x4dffffff : 0x73ffffff);
            n.setTypeface(on ? bold : mid);
            r.setBackground(on ? Kit.rounded(0x14ffffff, 12, 0, 0) : null);
        }
        Character l = t.app != null && mode == Mode.AZ ? Character.valueOf(AllApps.letter(t.app)) : t.letter;   // boxed: t.letter may be null
        for (TextView lt : letters)
            if (t.letter == null) lt.setTextColor(l != null && lt.getText().charAt(0) == l ? Color.WHITE : 0x59ffffff);
    }

    /** Pages the sheet so v (an app tile) is fully in the window; everything not fully inside fades out. */
    private void scrollTo(View v, boolean animate) {
        int winH = WIN_B - WIN_T;
        if (v != null) {
            int i = paged.indexOf(v), top = spans.get(i)[0], bottom = spans.get(i)[1];
            // include the section heading right above a group's first row
            if (i > 0 && spans.get(i - 1)[2] == 0 && spans.get(i - 1)[1] + 30 == top) top = spans.get(i - 1)[0];
            if (top - off < 0) off = top;
            if (bottom - off > winH) off = bottom - winH;
        }
        if (animate) sheet.animate().translationY(-off).setDuration(200).start(); else sheet.setTranslationY(-off);
        // WIN_T / WIN_B already leave room for a focused tile's ring and growth, so "fully inside" is the whole rule
        for (int i = 0; i < paged.size(); i++) {
            int[] s = spans.get(i);
            boolean in = s[0] - off >= 0 && s[1] - off <= winH;
            // a heading shows only with its first row
            if (in && s[2] == 0 && i + 1 < spans.size()) in = spans.get(i + 1)[1] - off <= winH;
            float a = in ? 1f : 0f;
            View pv = paged.get(i);
            Kit.fade(pv, a, animate);
        }
    }

    // ---------- helpers ----------

    private int W() { return 1920; }

    private static void detach(View... vs) {
        for (View v : vs) if (v.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) v.getParent()).removeView(v);
    }

    private TextView caps(Context c, float px, int color) {
        TextView t = Kit.label(c, Fonts.get(c, Fonts.INTER, 500), px, color);
        t.setLetterSpacing(0.2f);
        t.setAllCaps(true);
        return t;
    }

    private static LinearLayout.LayoutParams lp(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        return p;
    }

    private static LayoutParams at(int w, int h, int x, int y) {
        LayoutParams p = new LayoutParams(w, h, Gravity.TOP | Gravity.START);
        p.leftMargin = x;
        p.topMargin = y;
        return p;
    }

    private static LayoutParams at(int w, int h, int x, int y, int gravity) {
        LayoutParams p = new LayoutParams(w, h, gravity);
        p.leftMargin = x;
        p.topMargin = y;
        return p;
    }

    @Override protected Typeface menuFont() { return mid; }
}
