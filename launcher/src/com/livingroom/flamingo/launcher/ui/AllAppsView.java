package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.AllApps;
import com.livingroom.flamingo.launcher.model.AppEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * All apps, shared behaviour for every layout: Groups / A–Z / Hidden views, the view switch, letter jumps,
 * focus movement, and the hold-OK (or Menu) options. Subclasses only build and style the views.
 */
public abstract class AllAppsView extends FrameLayout {
    public enum Mode { GROUPS, AZ, HIDDEN }

    /** What a focusable view stands for. */
    protected static final class Target {
        final AppEntry app;            // an app tile
        final Mode mode;               // a view-switch option
        final Character letter;        // a letter in the A–Z index
        final AllApps.Group group;     // a group in the side list (null with app == null and mode == null: "Hidden")

        Target(AppEntry app, Mode mode, Character letter, AllApps.Group group) {
            this.app = app;
            this.mode = mode;
            this.letter = letter;
            this.group = group;
        }

        String key() {
            return app != null ? "app:" + app.pkg : mode != null ? "mode:" + mode : letter != null ? "letter:" + letter
                    : group != null ? "group:" + group : "hidden";
        }
    }

    protected final Sources src;
    private final Runnable close;
    protected Screen screen;
    protected Mode mode;
    private Mode before = Mode.GROUPS;          // where Back from Hidden returns to
    protected final List<View> items = new ArrayList<>();
    protected final List<Target> targets = new ArrayList<>();
    protected int focus;
    private boolean okHeld, longPressed, moved;   // moved: the owner has moved focus, so data refreshes keep it
    private Menu menu;

    protected AllAppsView(Context c, Sources src, Runnable close) {
        super(c);
        this.src = src;
        this.close = close;
        setClipChildren(false);
    }

    public void bind(Screen s) {
        boolean first = screen == null;
        screen = s;
        if (first) mode = s.appsAz ? Mode.AZ : Mode.GROUPS;
        if (menu != null) return;      // don't rebuild under an open menu; it refreshes on close
        rebuild(moved && focus < targets.size() ? targets.get(focus).key() : null);
    }

    private void rebuild(String keep) {
        items.clear();
        targets.clear();
        build();
        focus = firstApp();
        if (keep != null) for (int i = 0; i < targets.size(); i++) if (targets.get(i).key().equals(keep)) { focus = i; break; }
        if (!items.isEmpty()) moved(-1, focus, false);
    }

    /** Subclass builds every view for the current mode, calling add() for each focusable one in reading order. */
    protected abstract void build();

    /** Focus moved from old (-1: fresh build) to now: restyle, describe, page. */
    protected abstract void moved(int old, int now, boolean animate);

    protected final void add(View v, Target t) {
        items.add(v);
        targets.add(t);
    }

    protected static Target app(AppEntry a) { return new Target(a, null, null, null); }
    protected static Target mode(Mode m) { return new Target(null, m, null, null); }
    protected static Target letter(char l) { return new Target(null, null, l, null); }
    protected static Target group(AllApps.Group g) { return new Target(null, null, null, g); }
    protected static Target hiddenEntry() { return new Target(null, null, null, null); }

    /** The apps currently shown, in order (for A–Z and Hidden). */
    protected List<AppEntry> list() { return mode == Mode.HIDDEN ? screen.hidden : screen.az; }

    protected int firstApp() {
        for (int i = 0; i < targets.size(); i++) if (targets.get(i).app != null) return i;
        return 0;
    }

    /** Index of the first app whose letter (A–Z) or group (Groups) matches t. */
    protected int jumpTarget(Target t) {
        for (int i = 0; i < targets.size(); i++) {
            AppEntry a = targets.get(i).app;
            if (a == null) continue;
            if (t.letter != null && AllApps.letter(a) == t.letter) return i;
            if (t.group != null && AllApps.groupOf(a) == t.group) return i;
        }
        return -1;
    }

    /** The section an app belongs to, for headings. */
    protected AllApps.Group groupOf(AppEntry a) { return AllApps.groupOf(a); }

    // ---------- keys ----------

    /** Full key events (OK is acted on release so a long press can open the options). */
    public boolean dispatch(KeyEvent e) {
        int k = e.getKeyCode();
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
        if (menu != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && (!ok || e.getRepeatCount() == 0)) menu.key(k);
            return true;
        }
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) { okHeld = true; longPressed = false; }
                else if (okHeld && !longPressed) { longPressed = true; openMenu(); }
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                if (okHeld && !longPressed) activate();
                okHeld = false;
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (k == KeyEvent.KEYCODE_MENU) { openMenu(); return true; }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (mode == Mode.HIDDEN) { mode = before; rebuild("hidden"); } else close.run();
            return true;
        }
        int n = Nav.find(items, focus, k, this);
        if (n >= 0) move(n, true);
        return true;
    }

    protected void move(int n, boolean animate) {
        moved = true;
        int old = focus;
        focus = n;
        moved(old, n, animate);
    }

    private void activate() {
        if (targets.isEmpty()) return;
        Target t = targets.get(focus);
        if (t.app != null) src.openApp(t.app.pkg);
        else if (t.mode != null) {
            if (t.mode == mode) return;
            mode = t.mode;
            src.setAppsAz(mode == Mode.AZ);
            rebuild("mode:" + mode);
        } else if (t.letter != null || t.group != null) {
            int n = jumpTarget(t);
            if (n >= 0) move(n, true);
        } else {
            before = mode;
            mode = Mode.HIDDEN;
            rebuild(null);
        }
    }

    public void focus(String which) {
        if (items.isEmpty()) return;
        move("last".equals(which) ? items.size() - 1 : firstApp(), false);
    }

    // ---------- options (hold OK or Menu) ----------

    private void openMenu() {
        if (targets.isEmpty() || targets.get(focus).app == null) return;
        final AppEntry a = targets.get(focus).app;
        List<String> rows = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        rows.add("Open");
        acts.add(() -> src.openApp(a.pkg));
        rows.add("Pin to Home row");
        acts.add(() -> pinMenu(a));
        boolean hidden = mode == Mode.HIDDEN;
        rows.add(hidden ? "Unhide" : "Hide");
        acts.add(() -> src.setHidden(a.pkg, !hidden));
        rows.add("App info");
        acts.add(() -> src.appInfo(a.pkg));
        showMenu(a.label, rows, acts);
    }

    private void pinMenu(final AppEntry a) {
        List<String> rows = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        for (int i = 0; i < screen.main.size(); i++) {
            final int slot = i;
            Screen.Slot s = screen.main.get(i);
            if (s.key().equals(a.pkg)) continue;
            rows.add("In place of " + s.name());
            acts.add(() -> src.pin(a.pkg, slot));
        }
        showMenu("Pin " + a.label, rows, acts);
    }

    private void showMenu(String title, List<String> rows, List<Runnable> acts) {
        if (menu != null) removeView(menu);
        menu = new Menu(getContext(), title, rows, acts);
        addView(menu, new LayoutParams(-1, -1));
    }

    private void closeMenu() {
        if (menu == null) return;
        removeView(menu);
        menu = null;
        rebuild(focus < targets.size() ? targets.get(focus).key() : null);
    }

    /** Menu panel look; layouts override to match their style. */
    protected Typeface menuFont() { return Fonts.get(getContext(), Fonts.INTER, 600); }
    protected int menuPanelColor() { return 0xff17171c; }
    protected int menuRadius() { return 24; }

    private final class Menu extends FrameLayout {
        private final List<TextView> rows = new ArrayList<>();
        private final List<Runnable> acts;
        private int at;

        Menu(Context c, String title, List<String> labels, List<Runnable> acts) {
            super(c);
            this.acts = acts;
            setBackgroundColor(0xb3000000);
            LinearLayout panel = new LinearLayout(c);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackground(Kit.rounded(menuPanelColor(), menuRadius(), 1, 0x26ffffff));
            panel.setPadding(28, 28, 28, 28);
            TextView t = Kit.label(c, menuFont(), 26, 0x99ffffff);
            t.setText(title);
            panel.addView(t, new LinearLayout.LayoutParams(-1, -2));
            for (String l : labels) {
                TextView r = Kit.label(c, menuFont(), 32, Color.WHITE);
                r.setText(l);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(24, 0, 24, 0);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 72);
                p.topMargin = rows.isEmpty() ? 20 : 6;
                panel.addView(r, p);
                rows.add(r);
            }
            addView(panel, new LayoutParams(620, -2, Gravity.CENTER));
            style();
        }

        private void style() {
            for (int i = 0; i < rows.size(); i++) {
                boolean on = i == at;
                rows.get(i).setBackground(on ? Kit.rounded(Color.WHITE, menuRadius() / 2, 0, 0) : null);
                rows.get(i).setTextColor(on ? Color.BLACK : Color.WHITE);
            }
        }

        void key(int k) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU) { closeMenu(); return; }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                at = Math.max(0, Math.min(rows.size() - 1, at + (k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
                style();
            } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                Runnable r = acts.get(at);
                boolean opensAnother = rows.get(at).getText().toString().startsWith("Pin to");
                if (!opensAnother) closeMenu();
                r.run();
            }
        }
    }
}
