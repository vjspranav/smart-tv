package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.MediaItem;
import java.util.ArrayList;
import java.util.List;

/**
 * A · Cinema. Home: the focused app's last show fills the screen; real banners along the bottom.
 * Everything: banner rows (Inputs, Listen, Apps, TV, Home screen) under a dimmed poster; the big title
 * always describes the focused item. Rows page instead of scrolling so no tile is ever half visible.
 */
public final class LayoutA extends LayoutView {
    private static final int SIDE = Everything.SIDE, TILE_W = Everything.TILE_W, TILE_H = Everything.TILE_H, GAP = Everything.GAP;
    // Text column width; artwork shown as a card sits to its right (see compose)
    private static final int INFO_W = 1060;
    /** Where small artwork's card goes (half-resolution hero space): right of the text, under the clock, above the row. */
    private static final android.graphics.RectF CARD = new android.graphics.RectF((SIDE + INFO_W) / 2f + 26, 60, 960 - SIDE / 2f, 300);

    private final Typeface black, mid;
    private final ImageView under, over;
    private final FrameLayout homeGroup;
    private final Everything all;
    private final TextView eyebrow, title, meta, clock;
    private final View progress, progressFill;
    private final LinearLayout mainRow;
    private final List<View> mainTiles = new ArrayList<>();
    private int mainFocus = 0;
    private boolean everything;
    private String heroKey = "";
    // Composited heroes for the main slots (~2 MB each; Home and Everything variants), keyed by slot and artwork
    private final java.util.Map<String, Bitmap> heroes = new java.util.LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap> e) { return size() > 8; }
    };

    public LayoutA(Context c, Sources src, Runnable openHomeSettings, Runnable openAllApps) {
        super(c, src, openHomeSettings, openAllApps);
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

        all = new Everything(c, src, Everything.Style.cinema(c), () -> showEverything(false));
        mainRow = new LinearLayout(c);
        homeGroup.addView(mainRow, at(-2, -2, 0, 0, Gravity.BOTTOM | Gravity.START, SIDE - pad()));
        ((LayoutParams) mainRow.getLayoutParams()).bottomMargin = 96 - pad();

        all.setAlpha(0f);
        all.setVisibility(INVISIBLE);
        addView(all, new LayoutParams(-1, -1));
        Kit.focusRoom(mainRow, TILE_W, TILE_H, all.grow(), all.outset());

    }

    private int pad() { return all.pad(); }

    // ---------- data ----------

    @Override public void bind(Screen s) {
        boolean rebuildMain = screen == null || !sameMain(screen, s);
        screen = s;
        clock.setText(s.clock + (s.ampm.isEmpty() ? "" : " " + s.ampm));
        if (rebuildMain) buildMain();
        for (Screen.Slot sl : s.main) hero(sl);
        showSlot(mainFocus, false);
        all.bind(s, Sections.build(s, src, openHomeSettings, openAllApps), everything);
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
            View t = s.app != null ? all.banner(s.app.pkg, s.app.label) : all.inputTile(s.port);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(TILE_W, TILE_H);
            p.leftMargin = i == 0 ? 0 : GAP + 8;
            mainRow.addView(t, p);
            mainTiles.add(t);
        }
        mainFocus = Math.min(mainFocus, Math.max(0, mainTiles.size() - 1));
        if (!mainTiles.isEmpty()) all.mark(mainTiles.get(mainFocus), true);
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
            meta.setText(Hero.meta(m));
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
        b = Hero.compose(art != null ? art : Glow.forKey(s.key()), art == null, new int[]{0xe6000000, 0x8c000000, 0x00000000}, null, CARD, true);
        heroes.put(key, b);
        return b;
    }

    /** Everything's backdrop: the slot's artwork under a much darker shade, also baked into one bitmap. */
    private Bitmap dimmed(Screen.Slot s) {
        String key = s.key() + "|" + (s.cont != null ? s.cont.artPath : "") + "|dim";
        Bitmap b = heroes.get(key);
        if (b != null) return b;
        Bitmap art = src.art(s.cont);
        b = Hero.compose(art != null ? art : Glow.forKey(s.key()), art == null, new int[]{0xfa000000, 0xf0000000, 0xd9000000}, null, CARD, false);
        heroes.put(key, b);
        return b;
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

    // ---------- keys ----------

    @Override public boolean key(int k) {
        if (!everything) {
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_LEFT) {
                int n = mainFocus + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                if (n < 0 || n >= mainTiles.size()) return true;
                all.mark(mainTiles.get(mainFocus), false);
                mainFocus = n;
                all.mark(mainTiles.get(n), true);
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
        return all.key(k);
    }

    @Override public void showEverything(boolean on) {
        if (on == everything) return;
        everything = on;
        heroKey = "";   // swap to the dimmed (or normal) composite of the current slot
        showSlot(mainFocus, false);
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
        if (!everything) {
            if (mainTiles.isEmpty()) return;
            all.mark(mainTiles.get(mainFocus), false);
            mainFocus = "last".equals(which) ? mainTiles.size() - 1 : 0;
            all.mark(mainTiles.get(mainFocus), true);
            showSlot(mainFocus, false);
        } else all.focus("last".equals(which));
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
