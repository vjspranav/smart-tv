package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.AppEntry;
import com.livingroom.flamingo.launcher.model.HdmiPort;
import com.livingroom.flamingo.launcher.model.HomeConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Home screen settings, in the current layout's dress: the layout (A / G / N), the five main items (move,
 * replace) and the HDMI names. Same rows and metrics as Everything, so all three rows fit without paging.
 * Every change is saved at once; Home rebinds (and swaps layout) from the saved config.
 */
public final class SettingsView extends FrameLayout {
    private static final int SIDE = Everything.SIDE, TILE_H = Everything.TILE_H, GAP = Everything.GAP;
    private static final int ROWS_TOP = 300, ROW_H = 250, CARD_W = 420;
    private static final List<String> PRESETS = Arrays.asList("PlayStation 5", "Xbox", "Nintendo Switch", "Apple TV",
            "Fire TV", "Chromecast", "Set-top box", "Soundbar", "PC");

    private final Sources src;
    private final Everything.Style st;
    private final Everything tiles;   // tile builders only (never shown)
    private final Runnable close;
    private final TextView eyebrow, title, meta;
    private final LinearLayout rows;
    private final List<List<View>> grid = new ArrayList<>();
    private HomeConfig cfg;
    private Screen screen;
    private int row, col;
    private boolean moving;   // the focused main item is picked up: ← → moves it
    private Picker picker;
    private EditText editor;
    private int editingPort;

    public SettingsView(Context c, Sources src, Everything.Style st, Runnable close) {
        super(c);
        this.src = src;
        this.st = st;
        this.close = close;
        tiles = new Everything(c, src, st, close);
        setBackground(st.dots ? Dots.grid(getResources()) : new android.graphics.drawable.ColorDrawable(st.base));
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
        addView(rows, at(-1, -2, SIDE, ROWS_TOP));
    }

    public void bind(HomeConfig cfg, Screen s) {
        this.cfg = cfg;
        screen = s;
        build();
    }

    private void build() {
        rows.removeAllViews();
        grid.clear();
        Context c = getContext();
        // 01 Layout
        List<View> cards = new ArrayList<>();
        for (HomeConfig.Layout l : HomeConfig.Layout.values()) cards.add(layoutCard(c, l));
        addRow(c, "Layout", cards, CARD_W);
        // 02 Home row: what Home shows now, in order
        List<View> slots = new ArrayList<>();
        for (Screen.Slot s : screen.main) slots.add(s.app != null ? tiles.banner(s.app.pkg, s.app.label) : tiles.inputTile(s.port));
        addRow(c, "Home row", slots, Everything.TILE_W);
        // 03 HDMI names
        List<View> ins = new ArrayList<>();
        for (HdmiPort p : screen.inputs) ins.add(nameTile(c, p));
        addRow(c, "HDMI names", ins, Everything.TILE_W);
        row = Math.min(row, grid.size() - 1);
        while (row > 0 && grid.get(row).isEmpty()) row--;
        col = grid.get(row).isEmpty() ? 0 : Math.min(col, grid.get(row).size() - 1);
        restyle();
    }

    private void addRow(Context c, String name, List<View> items, int w) {
        LinearLayout r = column(c);
        r.setClipChildren(false);
        TextView label = caps(c, st.labelPx, 0x8cffffff);
        label.setText(st.numbered ? String.format("%02d — %s", grid.size() + 1, name) : name);
        r.addView(label, lp(-2, -2, 0));
        LinearLayout strip = new LinearLayout(c);
        strip.setClipChildren(false);
        for (View v : items) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, TILE_H);
            p.rightMargin = GAP;
            strip.addView(v, p);
        }
        r.addView(strip, lp(-2, TILE_H, 30));
        rows.addView(r, new LinearLayout.LayoutParams(-2, ROW_H));
        grid.add(items);
    }

    /** A layout's card, lettered in that layout's own type. */
    private View layoutCard(Context c, HomeConfig.Layout l) {
        FrameLayout f = frame(c);
        String name = Sections.layoutName(l);
        Typeface face;
        if (l == HomeConfig.Layout.G) {
            f.setBackground(Kit.rounded(0xff05030a, st.radius, 1, 0x26ffffff));
            face = Fonts.get(c, Fonts.SERIF_ITALIC);
        } else if (l == HomeConfig.Layout.N) {
            GradientDrawable g = Kit.rounded(0xff000000, st.radius, 1, 0x26ffffff);
            f.setBackground(g);
            face = Fonts.get(c, Fonts.DOTO, 900);
        } else {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TR_BL, new int[]{0xff5a0d18, 0xff0a0507});
            g.setCornerRadius(st.radius);
            f.setBackground(g);
            face = Fonts.get(c, Fonts.INTER, 900);
        }
        TextView n = Kit.label(c, face, 52, Color.WHITE);
        n.setText(name);
        LayoutParams np = new LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        np.leftMargin = np.rightMargin = 26;
        np.bottomMargin = 54;
        f.addView(n, np);
        TextView d = caps(c, 16, 0x99ffffff);
        d.setText(l == cfg.layout ? "● In use" : l == HomeConfig.Layout.A ? "Artwork · banners"
                : l == HomeConfig.Layout.G ? "Big type · artwork" : "Mono · dot grid");
        if (l == cfg.layout) d.setTextColor(l == HomeConfig.Layout.N ? Dots.RED : Color.WHITE);
        LayoutParams dp = new LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        dp.leftMargin = dp.rightMargin = 26;
        dp.bottomMargin = 24;
        f.addView(d, dp);
        return f;
    }

    private View nameTile(Context c, HdmiPort p) {
        FrameLayout f = frame(c);
        f.setBackground(Kit.rounded(st.panel, st.radius, 1, st.panelStroke));
        TextView l = caps(c, 18, 0x99ffffff);
        l.setText("HDMI " + p.port);
        LayoutParams lp = new LayoutParams(-1, -2, Gravity.TOP | Gravity.START);
        lp.leftMargin = lp.rightMargin = 22;
        lp.topMargin = 24;
        f.addView(l, lp);
        TextView v = Kit.label(c, st.black, 40, Color.WHITE);
        v.setText(p.name);
        Kit.fit(v, 40, 26);
        LayoutParams vp = new LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        vp.leftMargin = vp.rightMargin = 22;
        vp.bottomMargin = 22;
        f.addView(v, vp);
        return f;
    }

    private FrameLayout frame(Context c) {
        return new FrameLayout(c) {
            @Override public boolean hasOverlappingRendering() { return false; }
        };
    }

    // ---------- focus and the head ----------

    private void restyle() {
        for (int r = 0; r < grid.size(); r++)
            for (int i = 0; i < grid.get(r).size(); i++) st.mark(grid.get(r).get(i), r == row && i == col, TILE_H);
        describe();
    }

    private void describe() {
        if (grid.get(row).isEmpty()) return;
        if (row == 0) {
            HomeConfig.Layout l = HomeConfig.Layout.values()[col];
            eyebrow.setText("HOME SCREEN · LAYOUT");
            title.setText(Sections.layoutName(l));
            meta.setText(l == cfg.layout ? "In use" : "OK to switch to " + Sections.layoutName(l));
        } else if (row == 1) {
            Screen.Slot s = screen.main.get(col);
            eyebrow.setText("HOME SCREEN · HOME ROW · " + (col + 1) + " OF " + screen.main.size());
            title.setText(s.name());
            meta.setText(moving ? "← → to move · OK when done" : "OK to move or replace");
        } else {
            HdmiPort p = screen.inputs.get(col);
            eyebrow.setText("HOME SCREEN · HDMI " + p.port);
            title.setText(p.name);
            meta.setText("OK to rename");
        }
    }

    // ---------- keys ----------

    /** False only for keys left to Home (none today). */
    public boolean key(int k) {
        if (editor != null) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) finishEdit();
            return true;
        }
        if (picker != null) { picker.key(k); return true; }
        if (moving) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                col = cfg.moveMain(col, k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                src.saveConfig(cfg);   // Home rebinds this view with the new order; focus stays on the moved item
            } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BACK) {
                moving = false;
                describe();
            }
            return true;
        }
        switch (k) {
            case KeyEvent.KEYCODE_BACK: close.run(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int n = col + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                if (n >= 0 && n < grid.get(row).size()) { col = n; restyle(); }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int n = row + (k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
                while (n >= 0 && n < grid.size() && grid.get(n).isEmpty()) n += k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1;
                if (n < 0) { close.run(); return true; }   // ↑ from the top: back to Everything, like its own top row
                if (n >= grid.size()) return true;
                row = n;
                col = Math.min(col, grid.get(row).size() - 1);
                restyle();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                activate();
                return true;
            default:
                return true;
        }
    }

    private void activate() {
        if (grid.get(row).isEmpty()) return;
        if (row == 0) {
            HomeConfig.Layout l = HomeConfig.Layout.values()[col];
            if (l == cfg.layout) return;
            cfg.layout = l;
            src.saveConfig(cfg);   // Home swaps the layout and reopens these settings in the new dress
        } else if (row == 1) {
            final int slot = col;
            showPicker(screen.main.get(slot).name(), Arrays.asList("Move", "Replace with…"), i -> {
                if (i == 0) { editMain(); moving = true; describe(); } else replaceMenu(slot);
            });
        } else {
            final HdmiPort p = screen.inputs.get(col);
            List<String> opts = new ArrayList<>(PRESETS);
            opts.add("HDMI " + p.port + " (default)");
            opts.add("Type a name…");
            showPicker("Name HDMI " + p.port, opts, i -> {
                if (i < PRESETS.size()) rename(p.port, PRESETS.get(i));
                else if (i == PRESETS.size()) rename(p.port, null);
                else startEdit(p);
            });
        }
    }

    /** The row as Home shows it right now becomes the saved row, so slot numbers always match what's on screen. */
    private void editMain() {
        List<String> keys = new ArrayList<>();
        for (Screen.Slot s : screen.main) keys.add(s.key());
        cfg.main = keys;
    }

    private void replaceMenu(final int slot) {
        final List<String> keys = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (HdmiPort p : screen.inputs) { keys.add("hdmi:" + p.port); names.add(p.name + " · HDMI " + p.port); }
        for (AppEntry a : screen.az) { keys.add(a.pkg); names.add(a.label); }
        showPicker("Replace " + screen.main.get(slot).name(), names, i -> {
            editMain();
            cfg.replaceMain(slot, keys.get(i));
            src.saveConfig(cfg);
        });
    }

    private void rename(int port, String name) {
        cfg.setHdmiName(port, name);
        src.saveConfig(cfg);
    }

    private void startEdit(HdmiPort p) {
        editingPort = p.port;
        editor = new EditText(getContext());
        editor.setSingleLine(true);
        editor.setText(p.name);
        editor.setSelectAllOnFocus(true);
        editor.setTextColor(Color.WHITE);
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 40);
        editor.setTypeface(st.bold);
        editor.setBackground(Kit.rounded(0xff1d1d22, st.radius, 2, Color.WHITE));
        editor.setPadding(28, 0, 28, 0);
        editor.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editor.setOnEditorActionListener((v, action, e) -> { finishEdit(); return true; });
        addView(editor, at(900, 100, (1920 - 900) / 2, 200));
        eyebrow.setText("HOME SCREEN · HDMI " + p.port);
        title.setText("Rename");
        meta.setText("Type a name · OK when done · empty for the default");
        editor.requestFocus();
        editor.post(() -> {
            InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void finishEdit() {
        if (editor == null) return;
        String name = editor.getText().toString();
        InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        removeView(editor);
        editor = null;
        rename(editingPort, name);
        describe();
    }

    // ---------- a small list chooser ----------

    private interface Pick { void picked(int i); }

    private void showPicker(String heading, List<String> options, Pick pick) {
        picker = new Picker(getContext(), heading, options, pick);
        addView(picker, new LayoutParams(-1, -1));
    }

    private void closePicker() {
        if (picker == null) return;
        removeView(picker);
        picker = null;
    }

    /** A centred panel of rows; long lists page so no row is ever half shown. */
    private final class Picker extends FrameLayout {
        private static final int ROW = 72, SHOWN = 9;
        private final List<TextView> views = new ArrayList<>();
        private final Pick pick;
        private int at, first;

        Picker(Context c, String heading, List<String> options, Pick pick) {
            super(c);
            this.pick = pick;
            setBackgroundColor(0xb3000000);
            LinearLayout panel = column(c);
            panel.setBackground(Kit.rounded(st.dots ? 0xff121212 : 0xff17171c, Math.max(8, st.radius), 1, 0x26ffffff));
            panel.setPadding(28, 28, 28, 28);
            TextView t = caps(c, 20, 0x99ffffff);
            t.setText(heading);
            panel.addView(t, new LinearLayout.LayoutParams(-1, -2));
            for (String o : options) {
                TextView r = Kit.label(c, st.mid, 32, Color.WHITE);
                r.setText(o);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(24, 0, 24, 0);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, ROW - 6);
                p.topMargin = 6;
                panel.addView(r, p);
                views.add(r);
            }
            addView(panel, new LayoutParams(720, -2, Gravity.CENTER));
            show();
        }

        private void show() {
            if (at < first) first = at;
            if (at >= first + SHOWN) first = at - SHOWN + 1;
            for (int i = 0; i < views.size(); i++) {
                TextView r = views.get(i);
                r.setVisibility(i >= first && i < first + SHOWN ? VISIBLE : GONE);
                boolean on = i == at;
                r.setBackground(on ? Kit.rounded(Color.WHITE, Math.max(6, st.radius / 2), 0, 0) : null);
                r.setTextColor(on ? Color.BLACK : Color.WHITE);
            }
        }

        void key(int k) {
            if (k == KeyEvent.KEYCODE_BACK) { closePicker(); return; }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                at = Math.max(0, Math.min(views.size() - 1, at + (k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
                show();
            } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                closePicker();
                pick.picked(at);
            }
        }
    }

    /** After a layout switch: stay on the card just chosen. */
    public void focusLayout() {
        row = 0;
        col = cfg.layout.ordinal();
        restyle();
    }

    public void focus(String which) {
        row = "last".equals(which) ? grid.size() - 1 : 0;
        while (row > 0 && grid.get(row).isEmpty()) row--;
        col = "last".equals(which) && !grid.get(row).isEmpty() ? grid.get(row).size() - 1 : 0;
        restyle();
    }

    // ---------- helpers ----------

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
}
