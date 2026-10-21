package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
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
 * G · Editorial. Home: the five main items as a big bold list, the focused one bright with its resume line, its
 * artwork behind a dark left fade. Everything: the shared banner rows in G's dress (sharp tiles, a bar under the
 * focused one, mono labels, serif italic line) over the same artwork, darkened.
 */
public final class LayoutG extends LayoutView {
    private static final int SIDE = 115, TOP = 173;
    private static final RectF CARD = new RectF(620, 70, 912, 330);
    private static final int[] SHADE = {0xff05030a, 0xe605030a, 0x3305030a, 0x0005030a};
    private static final float[] SHADE_POS = {0f, 0.34f, 0.72f, 1f};
    private static final int[] DIM = {0xfa05030a, 0xf205030a, 0xdd05030a};

    private final Typeface black, mid, mono;
    private final ImageView under, over;
    private final FrameLayout homeGroup;
    private final Everything all;
    private final TextView date;
    private final LinearLayout list, resume;
    private final TextView resumeText;
    private final View bar, barFill;
    private final List<TextView> names = new ArrayList<>();
    private final Hero.Cache heroes = new Hero.Cache(8);
    private int mainFocus;
    private String heroKey = "";
    private boolean everything;

    public LayoutG(Context c, Sources src, Runnable openHomeSettings, Runnable openAllApps) {
        super(c, src, openHomeSettings, openAllApps);
        black = Fonts.get(c, Fonts.INTER, 900);
        mid = Fonts.get(c, Fonts.INTER, 500);
        mono = Fonts.get(c, Fonts.MONO);

        under = backdrop(c);
        over = backdrop(c);

        homeGroup = new FrameLayout(c);
        homeGroup.setClipChildren(false);
        addView(homeGroup, new LayoutParams(-1, -1));
        date = Kit.label(c, mono, 22, 0x8cffffff);
        date.setLetterSpacing(0.2f);
        homeGroup.addView(date, at(-2, -2, SIDE, 86));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        homeGroup.addView(list, at(1150, -2, SIDE, TOP));
        resume = new LinearLayout(c);
        resume.setOrientation(LinearLayout.VERTICAL);
        resumeText = Kit.label(c, mid, 30, 0xe6ffffff);
        resume.addView(resumeText, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout track = new FrameLayout(c);
        bar = new View(c);
        bar.setBackground(Kit.rounded(0x38ffffff, 2, 0, 0));
        barFill = new View(c);
        barFill.setBackground(Kit.rounded(Color.WHITE, 2, 0, 0));
        track.addView(bar, new LayoutParams(-1, -1));
        track.addView(barFill, new LayoutParams(0, -1));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(560, 4);
        tp.topMargin = 16;
        resume.addView(track, tp);
        TextView hint = Kit.label(c, mid, 26, 0x73ffffff);
        hint.setText("↓ Everything");
        LayoutParams hp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        hp.leftMargin = SIDE;
        hp.bottomMargin = 64;
        homeGroup.addView(hint, hp);

        all = new Everything(c, src, Everything.Style.editorial(c), () -> showEverything(false));
        all.setAlpha(0f);
        all.setVisibility(INVISIBLE);
        addView(all, new LayoutParams(-1, -1));
    }

    // ---------- data ----------

    @Override public void bind(Screen s) {
        boolean rebuild = screen == null || !sameMain(screen, s);
        screen = s;
        date.setText(shortDate(s.date) + " · " + s.clock + (s.ampm.isEmpty() ? "" : " " + s.ampm.toUpperCase()));
        if (rebuild) buildList();
        for (Screen.Slot sl : s.main) hero(sl);
        showSlot(false);
        all.bind(s, Sections.build(s, src, openHomeSettings, openAllApps), everything);
    }

    private static boolean sameMain(Screen a, Screen b) {
        if (a.main.size() != b.main.size()) return false;
        for (int i = 0; i < a.main.size(); i++) if (!a.main.get(i).key().equals(b.main.get(i).key())) return false;
        return true;
    }

    private static String shortDate(String d) {   // "Wednesday 21 October" → "WED 21 OCT"
        String[] p = d.split(" ");
        if (p.length < 3) return d.toUpperCase();
        return (cut(p[0]) + " " + p[1] + " " + cut(p[2])).toUpperCase();
    }

    private static String cut(String w) { return w.length() > 3 ? w.substring(0, 3) : w; }

    private void buildList() {
        list.removeAllViews();
        names.clear();
        for (Screen.Slot sl : screen.main) {
            TextView t = Kit.label(getContext(), black, 84, Color.WHITE);
            t.setLetterSpacing(-0.035f);
            t.setText(sl.name());
            t.setAlpha(0.25f);
            list.addView(t, new LinearLayout.LayoutParams(-1, -2));
            names.add(t);
        }
        mainFocus = Math.min(mainFocus, Math.max(0, names.size() - 1));
    }

    private void showSlot(boolean animate) {
        if (screen.main.isEmpty()) return;
        for (int i = 0; i < names.size(); i++) {
            float a = i == mainFocus ? 1f : 0.25f;
            if (animate) names.get(i).animate().alpha(a).setDuration(150).start(); else names.get(i).setAlpha(a);
        }
        Screen.Slot s = screen.main.get(mainFocus);
        MediaItem m = s.cont;
        if (s.port != null) resumeText.setText("⏻ Switch to HDMI " + s.port.port + " · " + Sections.state(s.port));
        else if (m != null && m.title != null) {
            String meta = Hero.meta(m);
            resumeText.setText("▶ Resume · " + m.title + (meta.isEmpty() ? "" : " · " + meta));
        } else resumeText.setText("▶ Open " + s.name());
        float frac = m != null && m.durationMs > 0 ? Math.min(1f, (float) m.positionMs / m.durationMs) : -1f;
        ((View) bar.getParent()).setVisibility(frac > 0 ? VISIBLE : GONE);
        if (frac > 0) {
            LayoutParams fp = (LayoutParams) barFill.getLayoutParams();
            fp.width = Math.round(560 * frac);
            barFill.setLayoutParams(fp);
        }
        if (resume.getParent() != null) list.removeView(resume);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = 10;
        rp.bottomMargin = 26;
        list.addView(resume, mainFocus + 1, rp);
        if (animate) { resume.setAlpha(0f); resume.animate().alpha(1f).setDuration(180).start(); }
        String key = s.key() + "|" + (m != null ? m.artPath : "") + (everything ? "|dim" : "");
        if (!key.equals(heroKey)) {
            heroKey = key;
            crossfade(everything ? dimmed(s) : hero(s), animate);
        }
    }

    /** The slot's artwork with G's left fade, baked into one opaque bitmap (one full-screen draw per frame). */
    private Bitmap hero(Screen.Slot s) {
        String key = s.key() + "|" + (s.cont != null ? s.cont.artPath : "");
        Bitmap b = heroes.get(key);
        if (b != null) return b;
        Bitmap art = src.art(s.cont);
        b = Hero.compose(art != null ? art : Glow.forKey(s.key()), art == null, SHADE, SHADE_POS, CARD, false);
        heroes.put(key, b);
        return b;
    }

    /** Everything's backdrop: the same artwork, much darker, so the rows and head read cleanly over it. */
    private Bitmap dimmed(Screen.Slot s) {
        String key = s.key() + "|" + (s.cont != null ? s.cont.artPath : "") + "|dim";
        Bitmap b = heroes.get(key);
        if (b != null) return b;
        Bitmap art = src.art(s.cont);
        b = Hero.compose(art != null ? art : Glow.forKey(s.key()), art == null, DIM, null, CARD, false);
        heroes.put(key, b);
        return b;
    }

    /** The old image sits under the new one only while it fades in. */
    private void crossfade(Bitmap b, boolean animate) {
        over.animate().cancel();
        boolean had = over.getDrawable() != null;
        if (had) under.setImageDrawable(over.getDrawable());
        over.setImageBitmap(b);
        if (!animate || !had) { over.setAlpha(1f); under.setVisibility(INVISIBLE); return; }
        under.setVisibility(VISIBLE);
        over.setAlpha(0f);
        over.animate().alpha(1f).setDuration(220).withEndAction(() -> under.setVisibility(INVISIBLE)).start();
    }

    // ---------- keys ----------

    @Override public boolean key(int k) {
        if (everything) return all.key(k);
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
            int n = mainFocus + (k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            if (n >= names.size()) { showEverything(true); return true; }
            if (n < 0) return true;
            mainFocus = n;
            showSlot(true);
            return true;
        }
        if ((k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) && !screen.main.isEmpty())
            src.open(screen.main.get(mainFocus));
        return true;
    }

    @Override public void showEverything(boolean on) {
        if (on == everything) return;
        everything = on;
        heroKey = "";   // swap to the darkened (or normal) composite of the current slot
        showSlot(false);
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
        if (names.isEmpty()) return;
        mainFocus = last ? names.size() - 1 : 0;
        showSlot(false);
    }

    // ---------- helpers ----------

    private ImageView backdrop(Context c) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setTag(Kit.BACKDROP, true);
        addView(v, new LayoutParams(-1, -1));
        return v;
    }

    private static LayoutParams at(int w, int h, int x, int y) {
        LayoutParams p = new LayoutParams(w, h, Gravity.TOP | Gravity.START);
        p.leftMargin = x;
        p.topMargin = y;
        return p;
    }
}
