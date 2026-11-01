package com.livingroom.flamingo.launcher.ui;

import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.AppEntry;
import com.livingroom.flamingo.launcher.model.AppOrder;
import com.livingroom.flamingo.launcher.model.HdmiPort;
import java.util.ArrayList;
import java.util.List;

/**
 * The Everything screen's content, built once for every layout so all three do exactly the same things.
 * Layouts only decide how sections and items look and how focus moves.
 */
public final class Sections {
    public enum Kind { INPUT, APP, ALLAPPS, SETTING, HOME }

    public static final class Item {
        public final Kind kind;
        public final String key, label, value;   // value: state, current setting, or Spotify's track ("" if none)
        public final Runnable action;
        public final HdmiPort port;              // INPUT only

        Item(Kind kind, String key, String label, String value, Runnable action, HdmiPort port) {
            this.kind = kind;
            this.key = key;
            this.label = label;
            this.value = value == null ? "" : value;
            this.action = action;
            this.port = port;
        }
    }

    public static final class Section {
        public final String title;
        public final List<Item> items = new ArrayList<>();

        Section(String title) { this.title = title; }
    }

    private Sections() { }

    public static String state(HdmiPort p) {
        return p.state == HdmiPort.State.ON ? "On" : p.state == HdmiPort.State.STANDBY ? "Standby" : "Empty";
    }

    /** Sections in order: Inputs, Apps (starting with All apps), TV, Home. */
    public static List<Section> build(Screen s, final Sources src, final Runnable openHomeSettings, final Runnable openAllApps) {
        List<Section> out = new ArrayList<>();
        Section inputs = new Section("Inputs");
        for (final HdmiPort p : s.inputs)
            inputs.items.add(new Item(Kind.INPUT, "hdmi:" + p.port, p.name, state(p), () -> src.openInput(p), p));
        out.add(inputs);

        Section apps = new Section("Apps");
        // All apps leads the row so it's one step from wherever you enter the row (owner: easier to reach)
        apps.items.add(new Item(Kind.ALLAPPS, "allapps", "All apps", s.az.size() + " apps", openAllApps, null));
        // Spotify's tile carries what's playing (a separate Listen row only repeated the app; owner: redundant)
        String track = s.listen == null || s.listen.title == null ? ""
                : s.listen.title + (s.listen.subtitle != null ? " — " + s.listen.subtitle : "");
        for (final AppEntry a : s.apps)
            apps.items.add(new Item(Kind.APP, a.pkg, a.label, a.pkg.equals(AppOrder.SPOTIFY) ? track : "", () -> src.openApp(a.pkg), null));
        out.add(apps);

        Section tv = new Section("TV");
        tv.items.add(new Item(Kind.SETTING, "picture", "Picture", s.picture, src::openPicture, null));
        tv.items.add(new Item(Kind.SETTING, "sound", "Sound", "Open", src::openSound, null));
        tv.items.add(new Item(Kind.SETTING, "sleep", "Sleep timer", "Open", src::openSleep, null));
        tv.items.add(new Item(Kind.SETTING, "wifi", "Wi-Fi", src.wifi().isEmpty() ? "Open" : src.wifi(), src::openWifi, null));
        tv.items.add(new Item(Kind.SETTING, "all", "All settings", "Open", src::openAllSettings, null));
        out.add(tv);

        Section home = new Section("Home screen");
        home.items.add(new Item(Kind.HOME, "home", "Home screen", layoutName(s.layout), openHomeSettings, null));
        out.add(home);
        return out;
    }

    public static String layoutName(com.livingroom.flamingo.launcher.model.HomeConfig.Layout l) {
        switch (l) {
            case G: return "Editorial";
            case N: return "Nothing";
            default: return "Cinema";
        }
    }
}
