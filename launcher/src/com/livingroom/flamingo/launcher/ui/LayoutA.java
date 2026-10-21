package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.HdmiPort;
import com.livingroom.flamingo.launcher.model.MediaItem;
import java.util.ArrayList;
import java.util.List;

/**
 * A · Cinema. Home: the focused app's last show fills the screen; real banners along the bottom.
 * Everything: banner rows (Inputs, Listen, Apps, TV, Home screen) under a dimmed poster; the big title
 * always describes the focused item. Rows page instead of scrolling so no tile is ever half visible.
 */
public final class LayoutA extends LayoutView {
    private static final int W = 1920, H = 1080, SIDE = 96, TILE_W = 300, TILE_H = 169, GAP = 36, RING = 10;
    private static final float GROW = 1.08f;
    // Text column width; artwork shown as a card sits to its right (see compose)
    private static final int INFO_W = 1060;
    private static final int ROWS_TOP = 330, ROW_H = 232, ROWS_SHOWN = 3;

    private final Typeface bold, black, mid;
    private final ImageView under, over;
    private final FrameLayout homeGroup, allGroup;
    private final TextView eyebrow, title, meta, clock, allEyebrow, allTitle, allMeta;
    private final View progress, progressFill;
    private final LinearLayout mainRow, rows;
    private final List<View> mainTiles = new ArrayList<>();
    private final List<View> allTiles = new ArrayList<>();
    private final List<Sections.Item> allItems = new ArrayList<>();
    private final List<Integer> allRowOf = new ArrayList<>();
    private final List<LinearLayout> strips = new ArrayList<>();
    private int mainFocus = 0, allFocus = 0, firstRow = 0;
    private int[] stripFirst = new int[0];
    private boolean everything;
    private Choice choice;
    private String heroKey = "";
    // Composited heroes for the main slots (~2 MB each; Home and Everything variants), keyed by slot and artwork
    private final java.util.Map<String, Bitmap> heroes = new java.util.LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap> e) { return size() > 8; }
    };

    public LayoutA(Context c, Sources src, Runnable openHomeSettings) {
        super(c, src, openHomeSettings);
        bold = Fonts.get(c, Fonts.INTER, 700);
        black = Fonts.get(c, Fonts.INTER, 900);
        mid = Fonts.get(c, Fonts.INTER, 500);
        // No background: the hero bitmap is always opaque and full screen, and every extra full-screen
        // layer costs a fill per frame on this GPU. The window background is removed in Home for the same reason.

        under = backdrop(c);
        over = backdrop(c);

        homeGroup = new FrameLayout(c);
        homeGroup.setClipChildren(false);
        addView(homeGroup, new LayoutParams(-1, -1));
        clock = Kit.label(c, mid, 34, 0xd9ffffff);
        homeGroup.addView(clock, at(-2, -2, 0, 64, Gravity.END, SIDE));
        LinearLayout info = column(c);
        eyebrow = caps(c, 24, 0xb3ffffff);
        title = Kit.label(c, black, 110, Color.WHITE);
        Kit.fit(title, 110, 64);
        meta = Kit.label(c, mid, 34, 0xccffffff);
        info.addView(eyebrow, lp(-1, -2, 0));
        info.addView(title, lp(-1, -2, 18));
        info.addView(meta, lp(-1, -2, 10));
        progress = new View(c);
        progress.setBackground(Kit.rounded(0x38ffffff, 3, 0, 0));
        progressFill = new View(c);
        progressFill.setBackground(Kit.rounded(Color.WHITE, 3, 0, 0));
        FrameLayout bar = new FrameLayout(c);
        bar.addView(progress, new LayoutParams(-1, -1));
        bar.addView(progressFill, new LayoutParams(0, -1));
        info.addView(bar, lp(560, 6, 28));
        homeGroup.addView(info, at(INFO_W, -2, SIDE, 250, Gravity.START, 0));

        mainRow = new LinearLayout(c);
        homeGroup.addView(mainRow, at(-2, -2, 0, 0, Gravity.BOTTOM | Gravity.START, SIDE - pad()));
        ((LayoutParams) mainRow.getLayoutParams()).bottomMargin = 96 - pad();
        Kit.focusRoom(mainRow, TILE_W, TILE_H, GROW, RING);

        allGroup = new FrameLayout(c);
        allGroup.setClipChildren(false);
        allGroup.setAlpha(0f);
        allGroup.setVisibility(INVISIBLE);
        addView(allGroup, new LayoutParams(-1, -1));
        LinearLayout head = column(c);
        allEyebrow = caps(c, 24, 0xb3ffffff);
        allTitle = Kit.label(c, black, 84, Color.WHITE);
        Kit.fit(allTitle, 84, 52);
        allMeta = Kit.label(c, mid, 32, 0xccffffff);
        head.addView(allEyebrow, lp(-1, -2, 0));
        head.addView(allTitle, lp(-1, -2, 14));
        head.addView(allMeta, lp(-1, -2, 8));
        allGroup.addView(head, at(1300, -2, SIDE, 80, Gravity.START, 0));
        rows = column(c);
        rows.setClipChildren(false);
        allGroup.addView(rows, at(-1, -2, 0, ROWS_TOP, Gravity.START, 0));
    }

    private int pad() { return (int) Math.ceil(TILE_W * (GROW - 1) / 2) + RING; }

    // ---------- data ----------

    @Override public void bind(Screen s) {
        boolean rebuildMain = screen == null || !sameMain(screen, s);
        screen = s;
        clock.setText(s.clock + (s.ampm.isEmpty() ? "" : " " + s.ampm));
        if (rebuildMain) buildMain();
        for (Screen.Slot sl : s.main) hero(sl);
        showSlot(mainFocus, false);
        buildAll();
    }

    private static boolean sameMain(Screen a, Screen b) {
        if (a.main.size() != b.main.size()) return false;
        for (int i = 0; i < a.main.size(); i++) if (!a.main.get(i).key().equals(b.main.get(i).key())) return false;
        return true;
    }

    private void buildMain() {
        mainRow.removeAllViews();
        mainTiles.clear();
        for (int i = 0; i < screen.main.size(); i++) {
            Screen.Slot s = screen.main.get(i);
            View t = s.app != null ? banner(s.app.pkg, s.app.label) : inputTile(s.port);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(TILE_W, TILE_H);
            p.leftMargin = i == 0 ? 0 : GAP + 8;
            mainRow.addView(t, p);
            mainTiles.add(t);
        }
        mainFocus = Math.min(mainFocus, Math.max(0, mainTiles.size() - 1));
        if (!mainTiles.isEmpty()) setFocused(mainTiles.get(mainFocus), true);
    }

    private void buildAll() {
        // keep focus across data updates only while the owner is on this screen
        String keep = everything && allFocus < allItems.size() ? allItems.get(allFocus).key : null;
        rows.removeAllViews();
        allTiles.clear();
        allItems.clear();
        allRowOf.clear();
        strips.clear();
        List<Sections.Section> secs = Sections.build(screen, src, openHomeSettings);
        Context c = getContext();
        for (int r = 0; r < secs.size(); r++) {
            Sections.Section sec = secs.get(r);
            LinearLayout row = new LinearLayout(c) {
                @Override public boolean hasOverlappingRendering() { return false; }   // see tileFrame()
            };
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClipChildren(false);
            TextView label = caps(c, 22, 0x8cffffff);
            label.setText(sec.title);
            row.addView(label, lp(-2, -2, 0));
            LinearLayout strip = new LinearLayout(c);
            strip.setClipChildren(false);
            for (Sections.Item it : sec.items) {
                View t = allTile(it);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(it.kind == Sections.Kind.LISTEN ? TILE_W * 2 + GAP : TILE_W, TILE_H);
                p.rightMargin = GAP;
                strip.addView(t, p);
                allTiles.add(t);
                allItems.add(it);
                allRowOf.add(r);
            }
            row.addView(strip, lp(-2, TILE_H, 16));
            strips.add(strip);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, ROW_H);
            rp.leftMargin = SIDE;
            rows.addView(row, rp);
        }
        stripFirst = new int[strips.size()];
        allFocus = 0;
        if (keep != null) for (int i = 0; i < allItems.size(); i++) if (allItems.get(i).key.equals(keep)) { allFocus = i; break; }
        if (!allTiles.isEmpty()) {
            setFocused(allTiles.get(allFocus), true);
            describe(allItems.get(allFocus));
            page(false);
        }
    }

    // ---------- tiles ----------

    private View banner(String pkg, String label) {
        FrameLayout f = tileFrame();
        Bitmap b = src.banner(pkg);
        if (b != null) {
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(b);
            iv.setContentDescription(label);
            iv.setOutlineProvider(Kit.ROUNDED_16);
            iv.setClipToOutline(true);
            f.addView(iv, new LayoutParams(-1, -1));
        } else {
            f.setBackground(Kit.rounded(0xff1d1d22, 16, 0, 0));
            TextView t = Kit.label(getContext(), bold, 34, Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setText(label);
            f.addView(t, at(-1, -2, 0, 0, Gravity.CENTER, 16));
            ((LayoutParams) t.getLayoutParams()).rightMargin = 16;
        }
        return f;
    }

    private View inputTile(HdmiPort p) {
        FrameLayout f = tileFrame();
        boolean on = p.state == HdmiPort.State.ON;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                p.state == HdmiPort.State.EMPTY ? new int[]{0xff1d1d22, 0xff16161a} : new int[]{0xff00439c, 0xff0070d1});
        g.setCornerRadius(16);
        f.setBackground(g);
        TextView name = Kit.label(getContext(), black, 38, Color.WHITE);
        name.setText(p.name);
        Kit.fit(name, 38, 26);
        f.addView(name, at(-1, -2, 20, 0, Gravity.BOTTOM | Gravity.START, 20));
        ((LayoutParams) name.getLayoutParams()).bottomMargin = 50;
        ((LayoutParams) name.getLayoutParams()).rightMargin = 20;
        TextView st = Kit.label(getContext(), mid, 24, on ? Color.WHITE : 0x99ffffff);
        st.setText((on ? "● " : "○ ") + Sections.state(p));
        f.addView(st, at(-1, -2, 20, 0, Gravity.BOTTOM | Gravity.START, 20));
        ((LayoutParams) st.getLayoutParams()).bottomMargin = 18;
        return f;
    }

    private View allTile(Sections.Item it) {
        switch (it.kind) {
            case INPUT: return inputTile(it.port);
            case APP: return banner(it.key, it.label);
            case LISTEN: {
                FrameLayout f = tileFrame();
                f.setBackground(Kit.rounded(0xff10251a, 16, 0, 0));
                Bitmap art = src.art(screen.listen);
                if (art != null) {
                    ImageView iv = new ImageView(getContext());
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(art);
                    iv.setOutlineProvider(Kit.ROUNDED_16);
                    iv.setClipToOutline(true);
                    f.addView(iv, new LayoutParams(TILE_H, TILE_H));
                }
                int left = art != null ? TILE_H + 24 : 24;
                TextView name = Kit.label(getContext(), bold, 34, Color.WHITE);
                name.setText(it.label);
                f.addView(name, at(-1, -2, left, 32, Gravity.START, 0));
                ((LayoutParams) name.getLayoutParams()).rightMargin = 24;
                TextView v = Kit.label(getContext(), mid, 26, 0xccffffff);
                v.setText(it.value);
                f.addView(v, at(-1, -2, left, 92, Gravity.START, 0));
                ((LayoutParams) v.getLayoutParams()).rightMargin = 24;
                return f;
            }
            default: {
                FrameLayout f = tileFrame();
                f.setBackground(Kit.rounded(0x1fffffff, 16, 1, 0x1fffffff));
                Icons.Kind icon = Icons.forKey(it.key);
                int textLeft = 22;
                if (icon != null) {
                    View ic = new View(getContext());
                    ic.setBackground(new Icons(icon, 0xe6ffffff));
                    ic.setTag(Kit.BACKDROP, true);   // decoration inside its own tile, not a solid under the label
                    f.addView(ic, at(46, 46, 18, 13, Gravity.START, 0));
                    textLeft = 18 + 46 + 12;
                }
                TextView l = Kit.label(getContext(), mid, 24, 0x99ffffff);
                l.setText(it.label);
                f.addView(l, at(-1, -2, textLeft, 24, Gravity.START, 0));
                ((LayoutParams) l.getLayoutParams()).rightMargin = 22;
                TextView v = Kit.label(getContext(), black, 40, Color.WHITE);
                v.setText(it.value.isEmpty() ? "Open" : it.value);
                v.setTag("value");
                Kit.fit(v, 40, 26);
                f.addView(v, at(-1, -2, 22, 0, Gravity.BOTTOM | Gravity.START, 0));
                ((LayoutParams) v.getLayoutParams()).bottomMargin = 22;
                ((LayoutParams) v.getLayoutParams()).rightMargin = 22;
                return f;
            }
        }
    }

    /** Tile container. Not clipped itself, so its focus ring (foreground) can draw outside; content clips. */
    private FrameLayout tileFrame() {
        // Fading a tile must not allocate an offscreen layer per frame; its background and image blending
        // slightly differently at partial alpha is invisible in a 160 ms fade
        return new FrameLayout(getContext()) {
            @Override public boolean hasOverlappingRendering() { return false; }
        };
    }

    private void setFocused(View v, boolean on) {
        v.setForeground(on ? Kit.ring(v, 16, 5, RING, Color.WHITE) : null);
        v.setTag(Kit.OUTSET, on ? RING : 0);
        v.animate().scaleX(on ? GROW : 1f).scaleY(on ? GROW : 1f).setDuration(150).start();
    }

    // ---------- hero ----------

    private void showSlot(int i, boolean animate) {
        if (screen.main.isEmpty()) return;
        Screen.Slot s = screen.main.get(Math.min(i, screen.main.size() - 1));
        MediaItem m = s.cont;
        if (s.port != null) {
            eyebrow.setText("INPUT · HDMI " + s.port.port);
            title.setText(s.port.name);
            meta.setText(Sections.state(s.port) + " · press OK to switch");
        } else if (m != null && m.title != null) {
            eyebrow.setText("CONTINUE ON " + s.name().toUpperCase());
            title.setText(m.title);
            meta.setText(metaText(m));
        } else {
            eyebrow.setText(m != null ? "CONTINUE ON " + s.name().toUpperCase() : "OPEN");
            title.setText(s.name());
            meta.setText(m != null && m.subtitle != null ? m.subtitle : "");
        }
        float frac = m != null && m.durationMs > 0 ? Math.min(1f, (float) m.positionMs / m.durationMs) : -1f;
        ((View) progress.getParent()).setVisibility(frac > 0 ? VISIBLE : INVISIBLE);
        if (frac > 0) {
            LayoutParams fp = (LayoutParams) progressFill.getLayoutParams();
            fp.width = Math.round(560 * frac);
            progressFill.setLayoutParams(fp);
        }
        String key = s.key() + "|" + (m != null ? m.artPath : "") + (everything ? "|dim" : "");
        if (!key.equals(heroKey)) {
            heroKey = key;
            crossfade(everything ? dimmed(s) : hero(s), animate);
        }
    }

    /**
     * The hero as one opaque bitmap: artwork (or the brand glow) with Home's left shade and the clock's corner
     * fade drawn in. One full-screen bitmap per frame instead of three full-screen layers keeps the GPU in budget.
     */
    private Bitmap hero(Screen.Slot s) {
        MediaItem m = s.cont;
        String key = s.key() + "|" + (m != null ? m.artPath : "");
        Bitmap b = heroes.get(key);
        if (b != null) return b;
        Bitmap art = src.art(m);
        b = compose(art != null ? art : Glow.forKey(s.key()), art == null, new int[]{0xe6000000, 0x8c000000, 0x00000000}, true);
        heroes.put(key, b);
        return b;
    }

    /** Everything's backdrop: the slot's artwork under a much darker shade, also baked into one bitmap. */
    private Bitmap dimmed(Screen.Slot s) {
        String key = s.key() + "|" + (s.cont != null ? s.cont.artPath : "") + "|dim";
        Bitmap b = heroes.get(key);
        if (b != null) return b;
        Bitmap art = src.art(s.cont);
        b = compose(art != null ? art : Glow.forKey(s.key()), art == null, new int[]{0xfa000000, 0xf0000000, 0xd9000000}, false);
        heroes.put(key, b);
        return b;
    }

    /** Large 16:9 artwork fills the screen; anything smaller or another shape (Netflix's badged stills, album art) becomes a card. */
    private static boolean fullBleed(Bitmap art) {
        float aspect = (float) art.getWidth() / art.getHeight();
        return art.getWidth() >= 900 && Math.abs(aspect - 16f / 9f) < 0.08f;
    }

    /** Composes the backdrop at half resolution (960×540): artwork full-bleed or as a card over its own colour glow, then shades. */
    private static Bitmap compose(Bitmap art, boolean glow, int[] shade, boolean cornerFade) {
        int w = 960, h = 540;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG
                | android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.DITHER_FLAG);
        android.graphics.Paint sp = new android.graphics.Paint(android.graphics.Paint.DITHER_FLAG);
        sp.setShader(new android.graphics.LinearGradient(0, 0, w, 0, shade, null, android.graphics.Shader.TileMode.CLAMP));
        if (glow || fullBleed(art)) {
            float sc = Math.max((float) w / art.getWidth(), (float) h / art.getHeight());
            float dw = art.getWidth() * sc, dh = art.getHeight() * sc;
            cv.drawBitmap(art, null, new android.graphics.RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), pt);
        } else {
            // Card box: right of the text column (INFO_W), under the clock, above the tile row
            float boxL = (SIDE + INFO_W) / 2f + 26, boxR = w - SIDE / 2f, boxT = 60, boxB = 300;
            float sc = Math.min((boxR - boxL) / art.getWidth(), (boxB - boxT) / art.getHeight());
            float cw = art.getWidth() * sc, ch = art.getHeight() * sc;
            android.graphics.RectF card = new android.graphics.RectF(boxR - cw, (boxT + boxB - ch) / 2, boxR, (boxT + boxB + ch) / 2);
            cv.drawColor(0xff0a0507);
            // The artwork's average colour, lifted so dark stills still give a visible glow
            float[] hsv = new float[3];
            Color.colorToHSV(Bitmap.createScaledBitmap(art, 1, 1, true).getPixel(0, 0), hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.4f);
            hsv[2] = Math.max(hsv[2], 0.6f);
            int avg = Color.HSVToColor(hsv);
            pt.setShader(new android.graphics.RadialGradient(card.centerX(), card.centerY(), 520,
                    new int[]{(avg & 0x00ffffff) | 0x99000000, (avg & 0x00ffffff) | 0x33000000, 0x00000000}, null,
                    android.graphics.Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, pt);
            cv.drawRect(0, 0, w, h, sp);   // shade under the card, so the card itself stays undimmed
            pt.setShader(null);
            pt.setColor(0xff000000);
            pt.setShadowLayer(18, 0, 6, 0x99000000);
            cv.drawRoundRect(card, 8, 8, pt);
            pt.clearShadowLayer();
            int save = cv.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(card, 8, 8, android.graphics.Path.Direction.CW);
            cv.clipPath(clip);
            cv.drawBitmap(art, null, card, pt);
            cv.restoreToCount(save);
        }
        if (glow || fullBleed(art)) cv.drawRect(0, 0, w, h, sp);
        if (cornerFade) {
            pt.setShader(new android.graphics.RadialGradient(w, 0, 235, new int[]{0x99000000, 0x4d000000, 0x00000000}, null,
                    android.graphics.Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, pt);
        }
        b.prepareToDraw();
        return b;
    }

    private static String metaText(MediaItem m) {
        String left = m.durationMs > 0 && m.positionMs > 0 ? Math.max(1, (m.durationMs - m.positionMs) / 60000) + " min left" : null;
        if (m.subtitle != null && left != null) return m.subtitle + " · " + left;
        return m.subtitle != null ? m.subtitle : left != null ? left : "";
    }

    /** The old image sits under the new one only while it fades in; otherwise only one full-screen bitmap draws. */
    private void crossfade(Bitmap b, boolean animate) {
        over.animate().cancel();
        boolean had = over.getDrawable() != null;
        if (had) under.setImageDrawable(over.getDrawable());
        over.setImageBitmap(b);
        if (!animate || !had) { over.setAlpha(1f); under.setVisibility(INVISIBLE); return; }
        under.setVisibility(VISIBLE);
        over.setAlpha(0f);
        // ImageView without a background applies alpha to the bitmap directly, so no layer is needed
        over.animate().alpha(1f).setDuration(220).withEndAction(() -> under.setVisibility(INVISIBLE)).start();
    }

    private void describe(Sections.Item it) {
        allEyebrow.setText(it.kind == Sections.Kind.INPUT ? "INPUT · HDMI " + it.port.port : sectionName(it).toUpperCase());
        allTitle.setText(it.label);
        allMeta.setText(choice != null && choice.item == it ? "← → choose · OK to apply · Back to cancel"
                : it.kind == Sections.Kind.INPUT ? Sections.state(it.port) + " · press OK to switch"
                : it.kind == Sections.Kind.APP ? "Press OK to open" : it.value);
    }

    private static String sectionName(Sections.Item it) {
        switch (it.kind) {
            case LISTEN: return "Listen";
            case APP: return "Apps";
            case SETTING: return "TV";
            case HOME: return "Home screen";
            default: return "Inputs";
        }
    }

    // ---------- paging (rows and tiles outside the window fade out instead of being cut off) ----------

    private void page(boolean animate) {
        int row = allRowOf.get(allFocus);
        if (row < firstRow) firstRow = row;
        if (row >= firstRow + ROWS_SHOWN) firstRow = row - ROWS_SHOWN + 1;
        float ty = -firstRow * ROW_H;
        if (animate) rows.animate().translationY(ty).setDuration(200).start(); else rows.setTranslationY(ty);
        for (int r = 0; r < rows.getChildCount(); r++) {
            float a = r >= firstRow && r < firstRow + ROWS_SHOWN ? 1f : 0f;
            View v = rows.getChildAt(r);
            if (animate) v.animate().alpha(a).setDuration(160).start(); else v.setAlpha(a);
        }
        // horizontal: every strip shows one whole page; the focused strip pages to keep its tile in view
        LinearLayout focused = strips.get(row);
        int idx = focused.indexOfChild(allTiles.get(allFocus));
        int f = Math.min(stripFirst[row], idx);
        while (idx >= f + visibleCount(focused, f)) f += Math.max(1, visibleCount(focused, f) - 1);
        stripFirst[row] = f;
        for (int r = 0; r < strips.size(); r++) {
            LinearLayout strip = strips.get(r);
            if (strip.getChildCount() == 0) continue;
            int first = Math.min(stripFirst[r], strip.getChildCount() - 1), shown = visibleCount(strip, first);
            float tx = -first * (TILE_W + GAP);
            if (strip.getChildAt(first).getWidth() > 0) tx = -strip.getChildAt(first).getLeft();
            boolean anim = animate && r == row;
            if (anim) strip.animate().translationX(tx).setDuration(200).start(); else strip.setTranslationX(tx);
            for (int i = 0; i < strip.getChildCount(); i++) {
                float a = i >= first && i < first + shown ? 1f : 0f;
                View v = strip.getChildAt(i);
                if (anim) v.animate().alpha(a).setDuration(160).start(); else { v.animate().cancel(); v.setAlpha(a); }
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

    @Override public boolean key(int k) {
        if (choice != null) return chooserKey(k);
        if (!everything) {
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_LEFT) {
                int n = mainFocus + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                if (n < 0 || n >= mainTiles.size()) return true;
                setFocused(mainTiles.get(mainFocus), false);
                mainFocus = n;
                setFocused(mainTiles.get(n), true);
                showSlot(n, true);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) { showEverything(true); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                if (!screen.main.isEmpty()) src.open(screen.main.get(mainFocus));
                return true;
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) { showEverything(false); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            Sections.Item it = allItems.get(allFocus);
            if (!it.choices.isEmpty()) { choice = new Choice(it); showChoice(); }
            else it.action.run();
            return true;
        }
        int n = Nav.find(allTiles, allFocus, k, rows);
        if (n < 0) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) showEverything(false);
            return true;
        }
        setFocused(allTiles.get(allFocus), false);
        allFocus = n;
        setFocused(allTiles.get(n), true);
        describe(allItems.get(n));
        page(true);
        return true;
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
            buildAll();
        }
        return true;
    }

    private void showChoice() {
        TextView v = allTiles.get(allFocus).findViewWithTag("value");
        if (v != null) v.setText(choice.display());
        describe(choice.item);
    }

    @Override public void showEverything(boolean on) {
        if (on == everything) return;
        everything = on;
        choice = null;
        heroKey = "";   // swap to the dimmed (or normal) composite of the current slot
        showSlot(mainFocus, false);
        fade(homeGroup, !on);
        fade(allGroup, on);
        if (on && !allTiles.isEmpty()) {
            setFocused(allTiles.get(allFocus), false);
            allFocus = 0;
            firstRow = 0;
            java.util.Arrays.fill(stripFirst, 0);
            setFocused(allTiles.get(0), true);
            describe(allItems.get(0));
            page(false);
        }
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
        if (!everything) {
            if (mainTiles.isEmpty()) return;
            setFocused(mainTiles.get(mainFocus), false);
            mainFocus = "last".equals(which) ? mainTiles.size() - 1 : 0;
            setFocused(mainTiles.get(mainFocus), true);
            showSlot(mainFocus, false);
        } else if (!allTiles.isEmpty()) {
            setFocused(allTiles.get(allFocus), false);
            allFocus = "last".equals(which) ? allTiles.size() - 1 : 0;
            setFocused(allTiles.get(allFocus), true);
            describe(allItems.get(allFocus));
            page(false);
        }
    }

    // ---------- small helpers ----------

    private ImageView backdrop(Context c) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setTag(Kit.BACKDROP, true);
        addView(v, new LayoutParams(-1, -1));
        return v;
    }

    private TextView caps(Context c, float px, int color) {
        TextView t = Kit.label(c, mid, px, color);
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

    private static LayoutParams at(int w, int h, int x, int y, int gravity, int side) {
        LayoutParams p = new LayoutParams(w, h, gravity);
        p.leftMargin = (gravity & Gravity.END) == Gravity.END ? 0 : x + side;
        p.rightMargin = (gravity & Gravity.END) == Gravity.END ? side : 0;
        p.topMargin = y;
        return p;
    }
}
