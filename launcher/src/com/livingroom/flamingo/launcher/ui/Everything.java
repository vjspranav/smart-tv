package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.HdmiPort;
import java.util.ArrayList;
import java.util.List;

/**
 * The Everything screen shared by the layouts: banner rows (Inputs, Apps, TV, Home screen) under a head
 * whose big title always describes the focused item. Rows page instead of scrolling so no tile is ever half
 * visible. A Style gives each layout its own type, corners and focus mark; behaviour is identical.
 * The tile builders are also used for A's Home row.
 */
public final class Everything extends FrameLayout {
    public static final int W = 1920, SIDE = 96, TILE_W = 300, TILE_H = 169, GAP = 36;
    private static final int ROWS_TOP = 300, ROW_H = 250, ROWS_SHOWN = 3;

    /** How a layout dresses Everything. */
    public static final class Style {
        int radius = 16, outset = 10;
        float grow = 1.08f, dim = 1f, titleSpacing = 0f, titlePx = 84, metaPx = 32, eyebrowPx = 24, labelPx = 22;
        boolean underline, numbered;
        // N: banners grey until focused, a red dot in the focus ring, inputs with an LED, mono caps meta, dot-grid backdrops
        boolean mono, redDot, led, metaCaps, dots;
        Typeface title, meta, caps, bold, black, mid;
        int metaColor = 0xccffffff, base = 0xff0a0507;   // base: All apps' backdrop colour
        int panel = 0x1fffffff, panelStroke = 0x1fffffff; // fixed (text) tiles

        /** A's ring around the tile, or G's bar under it (h: the tile's height, which places the bar). */
        public void mark(View v, boolean on, int h) {
            Drawable d = null;
            if (on && underline) d = new InsetDrawable(Kit.rounded(Color.WHITE, 2, 0, 0), 0, h + outset - 4, 0, -outset);
            else if (on) d = Kit.ring(v, radius, 5, outset, Color.WHITE);
            if (on && redDot) {
                int w = v.getLayoutParams() != null && v.getLayoutParams().width > 0 ? v.getLayoutParams().width : v.getWidth();
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(Dots.RED);
                android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new Drawable[]{d,
                        new InsetDrawable(dot, w - 34, 20, 20, h - 34)});
                ld.setPaddingMode(android.graphics.drawable.LayerDrawable.PADDING_MODE_STACK);   // the ring's outset must not grow the dot
                d = ld;
            }
            if (mono && v instanceof android.view.ViewGroup && ((android.view.ViewGroup) v).getChildAt(0) instanceof ImageView)
                Dots.light((ImageView) ((android.view.ViewGroup) v).getChildAt(0), on);
            v.setTag(Kit.OUTSET, on ? outset : 0);
            v.setForeground(d);
            v.animate().scaleX(on ? grow : 1f).scaleY(on ? grow : 1f).setDuration(150).start();
        }

        /** A · Cinema: rounded tiles, a white ring, Inter throughout. */
        public static Style cinema(Context c) {
            Style s = new Style();
            s.bold = Fonts.get(c, Fonts.INTER, 700);
            s.black = s.title = Fonts.get(c, Fonts.INTER, 900);
            s.mid = s.meta = s.caps = Fonts.get(c, Fonts.INTER, 500);
            return s;
        }

        /** G · Editorial: sharp tiles, a bar under the focused one, the rest dimmed; mono labels, serif italic line. */
        public static Style editorial(Context c) {
            Style s = cinema(c);
            s.radius = 6;
            s.grow = 1.04f;
            s.outset = 14;
            s.underline = true;
            s.numbered = true;
            s.dim = 0.5f;
            s.titleSpacing = -0.035f;
            s.titlePx = 84;
            s.meta = Fonts.get(c, Fonts.SERIF_ITALIC);
            s.metaPx = 34;
            s.metaColor = 0xd9ffffff;
            s.caps = Fonts.get(c, Fonts.MONO);
            s.eyebrowPx = 20;
            s.labelPx = 18;
            s.base = 0xff05030a;
            return s;
        }

        /** N · Nothing: soft widget tiles on a dot grid, grey banners that light up in colour on focus, a red dot. */
        public static Style nothing(Context c) {
            Style s = cinema(c);
            s.radius = 28;
            s.grow = 1.05f;
            s.mono = s.led = s.metaCaps = s.dots = true;   // no red dot on tiles (owner: looked off)
            s.title = Fonts.get(c, Fonts.DOTO, 900);
            s.titlePx = 84;
            s.titleSpacing = 0.02f;
            s.meta = s.caps = Fonts.get(c, Fonts.MONO);
            s.metaPx = 24;
            s.metaColor = 0x99ffffff;
            s.eyebrowPx = 20;
            s.labelPx = 18;
            s.base = 0xff000000;
            s.panel = 0xff121212;
            s.panelStroke = 0x1cffffff;
            return s;
        }
    }

    private final Sources src;
    private final Style st;
    private final Runnable leave;
    private final ViewOutlineProvider clip;
    private final TextView eyebrow, title, meta;
    private final LinearLayout rows;
    private final List<View> tiles = new ArrayList<>();
    private final List<Sections.Item> items = new ArrayList<>();
    private final List<Integer> rowOf = new ArrayList<>();
    private final List<LinearLayout> strips = new ArrayList<>();
    private int focus = 0, firstRow = 0;
    private int[] stripFirst = new int[0];
    private Screen screen;
    private List<Sections.Section> secs;
    private Choice choice;
    private boolean shown, moved;   // moved: the owner has moved since opening, so refreshes keep their place

    /** leave: Back, or Up from the top row (back to Home). */
    public Everything(Context c, Sources src, Style st, Runnable leave) {
        super(c);
        this.src = src;
        this.st = st;
        this.leave = leave;
        final int r = st.radius;
        clip = new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        };
        setClipChildren(false);
        LinearLayout head = column(c);
        eyebrow = caps(c, st.eyebrowPx, 0xb3ffffff);
        title = Kit.label(c, st.title, st.titlePx, Color.WHITE);
        title.setLetterSpacing(st.titleSpacing);
        Kit.fit(title, st.titlePx, 52);
        meta = Kit.label(c, st.meta, st.metaPx, st.metaColor);
        if (st.metaCaps) { meta.setAllCaps(true); meta.setLetterSpacing(0.12f); }
        head.addView(eyebrow, lp(-1, -2, 0));
        head.addView(title, lp(-1, -2, 14));
        head.addView(meta, lp(-1, -2, 8));
        addView(head, at(1300, -2, SIDE, 80));
        rows = column(c);
        rows.setClipChildren(false);
        addView(rows, at(-1, -2, 0, ROWS_TOP));
    }

    /** shownNow: the owner is on this screen, so focus stays on the same item across data updates. */
    public void bind(Screen s, List<Sections.Section> sections, boolean shownNow) {
        screen = s;
        secs = sections;
        shown = shownNow;
        build();
    }

    private void build() {
        String keep = shown && moved && focus < items.size() ? items.get(focus).key : null;
        rows.removeAllViews();
        tiles.clear();
        items.clear();
        rowOf.clear();
        strips.clear();
        Context c = getContext();
        for (int r = 0; r < secs.size(); r++) {
            Sections.Section sec = secs.get(r);
            LinearLayout row = new LinearLayout(c) {
                @Override public boolean hasOverlappingRendering() { return false; }   // see frame()
            };
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClipChildren(false);
            TextView label = caps(c, st.labelPx, 0x8cffffff);
            label.setText(st.numbered ? String.format("%02d — %s", r + 1, sec.title) : sec.title);
            row.addView(label, lp(-2, -2, 0));
            LinearLayout strip = new LinearLayout(c);
            strip.setClipChildren(false);
            for (Sections.Item it : sec.items) {
                View t = tile(it);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(TILE_W, TILE_H);
                p.rightMargin = GAP;
                strip.addView(t, p);
                tiles.add(t);
                items.add(it);
                rowOf.add(r);
            }
            // a focused tile grows and draws its mark outside: keep 30px so it never covers the heading
            row.addView(strip, lp(-2, TILE_H, 30));
            strips.add(strip);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, ROW_H);
            rp.leftMargin = SIDE;
            rows.addView(row, rp);
        }
        stripFirst = new int[strips.size()];
        focus = 0;
        if (keep != null) for (int i = 0; i < items.size(); i++) if (items.get(i).key.equals(keep)) { focus = i; break; }
        if (!tiles.isEmpty()) {
            mark(tiles.get(focus), true);
            describe(items.get(focus));
            page(false);
        }
    }

    // ---------- tiles ----------

    public View banner(String pkg, String label) {
        FrameLayout f = frame();
        Bitmap b = src.banner(pkg);
        if (b != null) {
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (st.mono) Dots.setMono(iv, b); else iv.setImageBitmap(b);
            iv.setContentDescription(label);
            iv.setOutlineProvider(clip);
            iv.setClipToOutline(true);
            f.addView(iv, new LayoutParams(-1, -1));
        } else {
            f.setBackground(st.led ? Kit.rounded(st.panel, st.radius, 1, st.panelStroke) : Kit.rounded(0xff1d1d22, st.radius, 0, 0));
            TextView t = Kit.label(getContext(), st.bold, 34, Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setText(label);
            LayoutParams p = new LayoutParams(-1, -2, Gravity.CENTER);
            p.leftMargin = p.rightMargin = 16;
            f.addView(t, p);
        }
        return f;
    }

    public View inputTile(HdmiPort p) {
        FrameLayout f = frame();
        boolean on = p.state == HdmiPort.State.ON;
        if (st.led) {
            // N: a plain widget with an LED: red and glowing when the device is on, dark otherwise
            f.setBackground(Kit.rounded(st.panel, st.radius, 1, st.panelStroke));
            if (on) {
                View glow = new View(getContext());
                glow.setBackground(Kit.rounded(0x40ff2a1a, 17, 0, 0));
                glow.setTag(Kit.BACKDROP, true);
                LayoutParams gp = new LayoutParams(34, 34, Gravity.TOP | Gravity.END);
                gp.topMargin = 16;
                gp.rightMargin = 16;
                f.addView(glow, gp);
            }
            View led = new View(getContext());
            led.setBackground(Kit.rounded(on ? Dots.RED : 0xff333333, 8, 0, 0));
            led.setTag(Kit.BACKDROP, true);
            LayoutParams lp = new LayoutParams(16, 16, Gravity.TOP | Gravity.END);
            lp.topMargin = 25;
            lp.rightMargin = 25;
            f.addView(led, lp);
            TextView name = Kit.label(getContext(), st.bold, 36, Color.WHITE);
            name.setText(p.name);
            Kit.fit(name, 36, 26);
            f.addView(name, bottom(24, 24, 54));
            TextView s = Kit.label(getContext(), st.caps, 18, 0x99ffffff);
            s.setLetterSpacing(0.15f);
            s.setAllCaps(true);
            s.setText("HDMI " + p.port + " · " + Sections.state(p));
            f.addView(s, bottom(24, 24, 24));
            return f;
        }
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                p.state == HdmiPort.State.EMPTY ? new int[]{0xff1d1d22, 0xff16161a} : new int[]{0xff00439c, 0xff0070d1});
        g.setCornerRadius(st.radius);
        f.setBackground(g);
        TextView name = Kit.label(getContext(), st.black, 38, Color.WHITE);
        name.setText(p.name);
        Kit.fit(name, 38, 26);
        f.addView(name, bottom(40, 20, 50));
        TextView s = Kit.label(getContext(), st.mid, 24, on ? Color.WHITE : 0x99ffffff);
        s.setText((on ? "● " : "○ ") + Sections.state(p));
        f.addView(s, bottom(40, 0, 18));
        return f;
    }

    private View tile(Sections.Item it) {
        switch (it.kind) {
            case INPUT: return inputTile(it.port);
            case APP: return banner(it.key, it.label);
            case ALLAPPS: {
                FrameLayout f = frame();
                ImageView iv = new ImageView(getContext());
                if (st.mono) Dots.setMono(iv, mosaic()); else iv.setImageBitmap(mosaic());
                iv.setOutlineProvider(clip);
                iv.setClipToOutline(true);
                iv.setTag(Kit.BACKDROP, true);   // the tile's own picture; its labels sit on it by design
                f.addView(iv, new LayoutParams(-1, -1));
                TextView name = Kit.label(getContext(), st.black, 36, Color.WHITE);
                name.setText(it.label);
                f.addView(name, bottom(22, 22, 52));
                TextView n = caps(getContext(), 18, 0xccffffff);
                n.setText(it.value);
                f.addView(n, bottom(22, 22, 22));
                return f;
            }
            default: {
                FrameLayout f = frame();
                f.setBackground(Kit.rounded(st.panel, st.radius, 1, st.panelStroke));
                Icons.Kind icon = Icons.forKey(it.key);
                int textLeft = 22;
                if (icon != null) {
                    View ic = new View(getContext());
                    ic.setBackground(new Icons(icon, 0xe6ffffff));
                    ic.setTag(Kit.BACKDROP, true);   // decoration inside its own tile, not a solid under the label
                    LayoutParams ip = new LayoutParams(46, 46, Gravity.TOP | Gravity.START);
                    ip.leftMargin = 18;
                    ip.topMargin = 13;
                    f.addView(ic, ip);
                    textLeft = 18 + 46 + 12;
                }
                TextView l = Kit.label(getContext(), st.mid, 24, 0x99ffffff);
                l.setText(it.label);
                f.addView(l, inTile(textLeft, 24, 22));
                TextView v = Kit.label(getContext(), st.black, 40, Color.WHITE);
                v.setText(it.value.isEmpty() ? "Open" : it.value);
                v.setTag("value");
                Kit.fit(v, 40, 26);
                f.addView(v, bottom(22, 22, 22));
                return f;
            }
        }
    }

    /** Tile container. Not clipped itself, so its focus mark (foreground) can draw outside; content clips. */
    private FrameLayout frame() {
        // Fading a tile must not allocate an offscreen layer per frame; its background and image blending
        // slightly differently at partial alpha is invisible in a 160 ms fade
        return new FrameLayout(getContext()) {
            @Override public boolean hasOverlappingRendering() { return false; }
        };
    }

    /** Focus mark (see Style.mark). Also used for A's Home row. */
    public void mark(View v, boolean on) { st.mark(v, on, TILE_H); }

    public int pad() { return (int) Math.ceil(TILE_W * (st.grow - 1) / 2) + st.outset; }

    public float grow() { return st.grow; }

    public int outset() { return st.outset; }

    private Bitmap mosaic;
    private String mosaicKey = "";

    /**
     * The All apps tile's picture: a tilted wall of the installed apps' banners under a dark fade, baked once into
     * one small bitmap (redrawn only when the apps change), so it costs a single image draw like any banner.
     */
    private Bitmap mosaic() {
        List<Bitmap> pics = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        // round-robin across groups so the wall mixes colours instead of showing one group
        for (int round = 0; pics.size() < 12 && round < 12; round++)
            for (com.livingroom.flamingo.launcher.model.AllApps.Section g : screen.groups) {
                if (round >= g.apps.size() || pics.size() >= 12) continue;
                Bitmap b = src.banner(g.apps.get(round).pkg);
                if (b == null) continue;
                pics.add(b);
                key.append(g.apps.get(round).pkg).append(',');
            }
        if (mosaic != null && key.toString().equals(mosaicKey)) return mosaic;
        mosaicKey = key.toString();
        Bitmap out = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        cv.drawColor(0xff141418);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
        if (!pics.isEmpty()) {
            cv.save();
            cv.rotate(-12, TILE_W / 2f, TILE_H / 2f);
            int w = 112, h = 63, gap = 8, k = 0;
            android.graphics.Path round = new android.graphics.Path();
            for (int row = 0; row < 4; row++)
                for (int col = 0; col < 4; col++) {
                    float x = -64 + col * (w + gap) + (row % 2) * 40, y = -52 + row * (h + gap);
                    android.graphics.RectF r = new android.graphics.RectF(x, y, x + w, y + h);
                    round.reset();
                    round.addRoundRect(r, 6, 6, android.graphics.Path.Direction.CW);
                    cv.save();
                    cv.clipPath(round);
                    cv.drawBitmap(pics.get(k++ % pics.size()), null, r, p);
                    cv.restore();
                }
            cv.restore();
        }
        android.graphics.Paint shade = new android.graphics.Paint();
        shade.setShader(new android.graphics.LinearGradient(0, 0, 0, TILE_H, new int[]{0x4d000000, 0x99000000, 0xf2000000},
                new float[]{0f, 0.45f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, TILE_W, TILE_H, shade);
        mosaic = out;
        return out;
    }

    // ---------- head ----------

    private void describe(Sections.Item it) {
        eyebrow.setText(it.kind == Sections.Kind.INPUT ? "INPUT · HDMI " + it.port.port : sectionName(it).toUpperCase());
        title.setText(it.label);
        meta.setText(choice != null && choice.item == it ? "← → choose · OK to apply · Back to cancel"
                : it.kind == Sections.Kind.INPUT ? Sections.state(it.port) + " · press OK to switch"
                : it.kind == Sections.Kind.APP ? (it.value.isEmpty() ? "Press OK to open" : "Now playing · " + it.value)
                : it.kind == Sections.Kind.ALLAPPS ? it.value + " · by group or A to Z" : it.value);
    }

    private static String sectionName(Sections.Item it) {
        switch (it.kind) {
            case APP: case ALLAPPS: return "Apps";
            case SETTING: return "TV";
            case HOME: return "Home screen";
            default: return "Inputs";
        }
    }

    // ---------- paging (rows and tiles outside the window fade out instead of being cut off) ----------

    private void page(boolean animate) {
        int row = rowOf.get(focus);
        if (row < firstRow) firstRow = row;
        if (row >= firstRow + ROWS_SHOWN) firstRow = row - ROWS_SHOWN + 1;
        float ty = -firstRow * ROW_H;
        if (animate) rows.animate().translationY(ty).setDuration(200).start(); else rows.setTranslationY(ty);
        for (int r = 0; r < rows.getChildCount(); r++)
            Kit.fade(rows.getChildAt(r), r >= firstRow && r < firstRow + ROWS_SHOWN ? 1f : 0f, animate);
        // horizontal: every strip shows one whole page; the focused strip pages to keep its tile in view
        View focused = tiles.get(focus);
        LinearLayout fs = strips.get(row);
        int idx = fs.indexOfChild(focused);
        int f = Math.min(stripFirst[row], idx);
        while (idx >= f + visibleCount(fs, f)) f += Math.max(1, visibleCount(fs, f) - 1);
        stripFirst[row] = f;
        for (int r = 0; r < strips.size(); r++) {
            LinearLayout strip = strips.get(r);
            if (strip.getChildCount() == 0) continue;
            int first = Math.min(stripFirst[r], strip.getChildCount() - 1), n = visibleCount(strip, first);
            float tx = -first * (TILE_W + GAP);
            if (strip.getChildAt(first).getWidth() > 0) tx = -strip.getChildAt(first).getLeft();
            boolean anim = animate && r == row;
            if (anim) strip.animate().translationX(tx).setDuration(200).start(); else strip.setTranslationX(tx);
            for (int i = 0; i < strip.getChildCount(); i++) {
                View v = strip.getChildAt(i);
                // in view: full, or dimmed beside the focused tile (G); out of view: hidden
                float a = i >= first && i < first + n ? (v == focused ? 1f : st.dim) : 0f;
                Kit.fade(v, a, animate && (anim || st.dim < 1f));
            }
        }
    }

    /** How many tiles starting at `first` fit fully inside the screen's safe width. */
    private int visibleCount(LinearLayout strip, int first) {
        int room = W - 2 * SIDE - 2 * pad(), n = 0, used = 0;
        for (int i = first; i < strip.getChildCount(); i++) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) strip.getChildAt(i).getLayoutParams();
            used += p.width + (n > 0 ? GAP : 0);
            if (used > room) break;
            n++;
        }
        return Math.max(1, n);
    }

    // ---------- keys ----------

    public boolean key(int k) {
        if (tiles.isEmpty()) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_UP) leave.run();
            return true;
        }
        if (choice != null) return chooserKey(k);
        if (k == KeyEvent.KEYCODE_BACK) { leave.run(); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            Sections.Item it = items.get(focus);
            if (!it.choices.isEmpty()) { choice = new Choice(it); showChoice(); }
            else it.action.run();
            return true;
        }
        int n = Nav.find(tiles, focus, k, rows);
        if (n < 0) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) leave.run();
            return true;
        }
        moved = true;
        move(n, true);
        return true;
    }

    private void move(int n, boolean animate) {
        mark(tiles.get(focus), false);
        focus = n;
        mark(tiles.get(n), true);
        describe(items.get(n));
        page(animate);
    }

    private boolean chooserKey(int k) {
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            choice.move(k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            showChoice();
        } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            src.setPicture(choice.value());
            choice = null;
        } else if (k == KeyEvent.KEYCODE_BACK) {
            choice = null;
            build();
        }
        return true;
    }

    private void showChoice() {
        TextView v = tiles.get(focus).findViewWithTag("value");
        if (v != null) v.setText(choice.display());
        describe(choice.item);
    }

    /** Opening the screen: forget any chooser, start at the first tile. */
    public void reset(boolean shownNow) {
        shown = shownNow;
        choice = null;
        moved = false;
        if (tiles.isEmpty()) return;
        firstRow = 0;
        java.util.Arrays.fill(stripFirst, 0);
        move(0, false);
    }

    public void focus(boolean last) {
        if (!tiles.isEmpty()) move(last ? tiles.size() - 1 : 0, false);
    }

    // ---------- small helpers ----------

    private TextView caps(Context c, float px, int color) {
        TextView t = Kit.label(c, st.caps, px, color);
        t.setLetterSpacing(0.2f);
        t.setAllCaps(true);
        return t;
    }

    private static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
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

    private static LayoutParams inTile(int x, int y, int right) {
        LayoutParams p = at(-1, -2, x, y);
        p.rightMargin = right;
        return p;
    }

    private static LayoutParams bottom(int left, int right, int fromBottom) {
        LayoutParams p = new LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        p.leftMargin = left;
        p.rightMargin = right;
        p.bottomMargin = fromBottom;
        return p;
    }
}
