package com.livingroom.flamingo.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.HomeConfig;
import com.livingroom.flamingo.launcher.ui.AllAppsGrid;
import com.livingroom.flamingo.launcher.ui.Everything;
import com.livingroom.flamingo.launcher.ui.AllAppsView;
import com.livingroom.flamingo.launcher.ui.Debug;
import com.livingroom.flamingo.launcher.ui.LayoutA;
import com.livingroom.flamingo.launcher.ui.LayoutG;
import com.livingroom.flamingo.launcher.ui.LayoutN;
import com.livingroom.flamingo.launcher.ui.LayoutView;
import com.livingroom.flamingo.launcher.ui.Overflow;
import com.livingroom.flamingo.launcher.ui.SettingsView;

/**
 * The launcher's only activity. Hosts the chosen layout (or Home screen settings) and routes keys.
 * Debug extras: reset (config back to defaults), appsaz=true|false, layout=A|G|N, screen=home|everything|settings|allapps, focus=first|last, stress, check, dump, probe, kittest, notcl.
 */
public class Home extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private Sources src;
    private FrameLayout root;
    private LayoutView layout;
    private SettingsView settings;
    private AllAppsView allApps;
    private HomeConfig.Layout shown;
    private boolean stress;
    private final Runnable rebind = this::rebind, tick = new Runnable() {
        @Override public void run() {
            rebind();
            h.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 50);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        String probe = in.getStringExtra("probe");
        if (probe != null) { Probe.run(this, probe, in.getIntExtra("pic", -1)); finish(); return; }
        if (in.getBooleanExtra("notcl", false)) Sources.tclAllowed = false;
        if (in.getBooleanExtra("kittest", false)) { debugScreen(Debug.kitTest(this)); return; }
        src = Sources.get(this);
        if (in.getBooleanExtra("dump", false)) {
            src.listen(new Runnable() { public void run() { src.dump(); src.unlisten(this); finish(); } });
            return;
        }
        stress = in.getBooleanExtra("stress", false);
        getWindow().setBackgroundDrawable(null);   // the layout covers the screen; avoid a full-screen fill per frame
        root = new FrameLayout(this);
        root.setClipChildren(false);
        setContentView(root);
        if (in.getBooleanExtra("reset", false)) src.saveConfig(HomeConfig.defaults());
        if (in.hasExtra("appsaz")) src.setAppsAz(in.getBooleanExtra("appsaz", false));
        String forced = in.getStringExtra("layout");
        if (forced != null) {
            HomeConfig cfg = src.config();
            cfg.layout = HomeConfig.Layout.valueOf(forced);
            src.saveConfig(cfg);
        }
        rebind();
        src.listen(rebind);
        String screen = in.getStringExtra("screen"), focus = in.getStringExtra("focus");
        if ("everything".equals(screen)) layout.showEverything(true);
        if ("settings".equals(screen)) showSettings(true);
        if ("allapps".equals(screen)) { layout.showEverything(true); showAllApps(true); }
        if (focus != null) {
            if (allApps != null) allApps.focus(focus); else if (settings != null) settings.focus(focus); else layout.focus(focus);
        }
        if (in.getBooleanExtra("check", false)) h.postDelayed(() -> Overflow.check(root), 2500);
    }

    private void debugScreen(final View v) {
        setContentView(v);
        if (getIntent().getBooleanExtra("check", false)) v.postDelayed(() -> Overflow.check(v), 1500);
    }

    /** Re-reads data; rebuilds the layout only when the owner picked a different one. */
    private void rebind() {
        HomeConfig.Layout want = src.config().layout;
        if (layout == null || want != shown) {
            if (layout != null) root.removeView(layout);
            Runnable hs = () -> showSettings(true), aa = () -> showAllApps(true);
            layout = want == HomeConfig.Layout.G ? new LayoutG(this, src, hs, aa)
                    : want == HomeConfig.Layout.N ? new LayoutN(this, src, hs, aa)
                    : new LayoutA(this, src, hs, aa);
            root.addView(layout, 0, new FrameLayout.LayoutParams(-1, -1));
            shown = want;
            if (settings != null) {   // picked a new layout in settings: reopen them in its dress
                root.removeView(settings);
                settings = null;
                showSettings(true);
                settings.focusLayout();
            }
        }
        layout.bind(src.snapshot(stress));
        if (settings != null) settings.bind(src.config(), src.snapshot(stress));
        if (allApps != null) allApps.bind(src.snapshot(stress));
    }

    /** All apps in the current layout's style. The layout underneath stops drawing while it's open. */
    private void showAllApps(boolean on) {
        if (on && allApps == null) {
            allApps = new AllAppsGrid(this, src, Everything.Style.forLayout(this, src.config().layout), () -> showAllApps(false));
            root.addView(allApps, new FrameLayout.LayoutParams(-1, -1));
            allApps.bind(src.snapshot(stress));
            layout.setVisibility(View.INVISIBLE);
        } else if (!on && allApps != null) {
            root.removeView(allApps);
            allApps = null;
            layout.setVisibility(View.VISIBLE);
            rebind();
        }
    }

    private void showSettings(boolean on) {
        if (on && settings == null) {
            settings = new SettingsView(this, src, Everything.Style.forLayout(this, shown), () -> showSettings(false));
            root.addView(settings, new FrameLayout.LayoutParams(-1, -1));
            settings.bind(src.config(), src.snapshot(stress));
            layout.setVisibility(View.INVISIBLE);   // covered: don't draw it underneath
        } else if (!on && settings != null) {
            root.removeView(settings);
            settings = null;
            layout.setVisibility(View.VISIBLE);
            rebind();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null) { src.requestRefresh(); h.removeCallbacks(tick); h.post(tick); }
    }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (src != null) src.unlisten(rebind);
    }

    /** Home pressed while already home: leave Everything/settings and focus the first slot. */
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (layout == null) return;
        showAllApps(false);
        showSettings(false);
        layout.showEverything(false);
        layout.focus("first");
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (allApps != null && k != KeyEvent.KEYCODE_HOME && k != KeyEvent.KEYCODE_VOLUME_UP
                && k != KeyEvent.KEYCODE_VOLUME_DOWN && k != KeyEvent.KEYCODE_VOLUME_MUTE) return allApps.dispatch(e);
        boolean ours = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT
                || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_BACK;
        if (!ours || layout == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            boolean done = settings != null ? settings.key(k) : layout.key(k);
            if (!done && k == KeyEvent.KEYCODE_BACK && settings != null) showSettings(false);
        }
        return true;   // Back on Home does nothing: we are home
    }
}
