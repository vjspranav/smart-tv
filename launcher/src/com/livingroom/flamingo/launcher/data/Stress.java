package com.livingroom.flamingo.launcher.data;

import com.livingroom.flamingo.launcher.model.AppEntry;
import com.livingroom.flamingo.launcher.model.HdmiPort;
import com.livingroom.flamingo.launcher.model.HomeConfig;
import com.livingroom.flamingo.launcher.model.MediaItem;
import com.livingroom.flamingo.launcher.ui.Screen;
import java.util.ArrayList;
import java.util.List;

/** Worst-case data for the overflow checks: very long names and titles, 40 apps, no artwork. */
final class Stress {
    private static final String LONG = "Extraordinarily Long Name That Keeps Going Well Past Any Box";

    private Stress() { }

    /** 40 apps with very long names, spread over every All apps group. */
    static List<AppEntry> apps() {
        List<AppEntry> apps = new ArrayList<>();
        for (int i = 0; i < 40; i++) apps.add(new AppEntry("stress.app" + i, LONG + " " + i, i % 2 == 0, i % 5 - 1));
        return apps;
    }

    static Screen screen(HomeConfig.Layout layout, String clock, String ampm, String date) {
        List<Screen.Slot> main = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            MediaItem m = new MediaItem();
            m.pkg = "stress.main" + i;
            m.title = LONG + " " + LONG;
            m.subtitle = "Season 12 · Episode 345 · " + LONG;
            m.positionMs = 1_000_000;
            m.durationMs = 3_000_000;
            main.add(new Screen.Slot(new AppEntry(m.pkg, LONG + " " + i, true), null, m));
        }
        main.add(new Screen.Slot(null, HdmiPort.resolve(1, "stress.hdmi1", LONG, null, 0), null));
        List<HdmiPort> inputs = new ArrayList<>();
        for (int p = 1; p <= 3; p++) inputs.add(HdmiPort.resolve(p, "stress.hdmi" + p, LONG + " " + p, null, p == 1 ? 0 : 1));
        List<AppEntry> apps = apps();
        MediaItem listen = new MediaItem();
        listen.pkg = "com.spotify.tv.android";
        listen.title = LONG;
        listen.subtitle = LONG;
        listen.playing = true;
        return new Screen(layout, main, inputs, apps, listen, "Extraordinarily Long Mode", clock, ampm, date);
    }
}
