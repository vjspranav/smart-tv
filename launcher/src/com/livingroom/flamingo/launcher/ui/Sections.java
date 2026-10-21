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
    public enum Kind { INPUT, LISTEN, APP, ALLAPPS, SETTING, HOME }

    public static final class Item {
        public final Kind kind;
        public final String key, label, value;   // value: state, track, or current setting ("" if none)
        public final Runnable action;
        public final List<String> choices;       // non-empty → OK opens an inline chooser instead of action
        public final HdmiPort port;              // INPUT only

        Item(Kind kind, String key, String label, String value, Runnable action, List<String> choices, HdmiPort port) {
            this.kind = kind;
            this.key = key;
            this.label = label;
            this.value = value == null ? "" : value;
            this.action = action;
            this.choices = choices == null ? new ArrayList<String>() : choices;
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

    /** Sections in order: Inputs, Listen (if Spotify is installed), Apps (starting with All apps), TV, Home. */
    public static List<Section> build(Screen s, final Sources src, final Runnable openHomeSettings, final Runnable openAllApps) {
        List<Section> out = new ArrayList<>();
        Section inputs = new Section("Inputs");
        for (final HdmiPort p : s.inputs)
            inputs.items.add(new Item(Kind.INPUT, "hdmi:" + p.port, p.name, state(p), () -> src.openInput(p), null, p));
        out.add(inputs);

        boolean spotify = false;
        for (AppEntry a : s.apps) spotify |= a.pkg.equals(AppOrder.SPOTIFY);
        if (spotify) {
            Section listen = new Section("Listen");
            String track = s.listen == null || s.listen.title == null ? "Open Spotify"
                    : s.listen.title + (s.listen.subtitle != null ? " — " + s.listen.subtitle : "");
            listen.items.add(new Item(Kind.LISTEN, "listen:" + AppOrder.SPOTIFY, "Spotify", track, () -> src.openApp(AppOrder.SPOTIFY), null, null));
            out.add(listen);
        }

        Section apps = new Section("Apps");
        // All apps leads the row so it's one step from wherever you enter the row (owner: easier to reach)
        apps.items.add(new Item(Kind.ALLAPPS, "allapps", "All apps", s.az.size() + " apps", openAllApps, null, null));
        for (final AppEntry a : s.apps) apps.items.add(new Item(Kind.APP, a.pkg, a.label, "", () -> src.openApp(a.pkg), null, null));
        out.add(apps);

        Section tv = new Section("TV");
        tv.items.add(new Item(Kind.SETTING, "picture", "Picture", s.picture, src::openPicture, src.pictureChoices(), null));
        tv.items.add(new Item(Kind.SETTING, "sound", "Sound", "Open", src::openSound, null, null));
        tv.items.add(new Item(Kind.SETTING, "sleep", "Sleep timer", "Open", src::openSleep, null, null));
        tv.items.add(new Item(Kind.SETTING, "wifi", "Wi-Fi", src.wifi().isEmpty() ? "Open" : src.wifi(), src::openWifi, null, null));
        tv.items.add(new Item(Kind.SETTING, "all", "All settings", "Open", src::openAllSettings, null, null));
        out.add(tv);

        Section home = new Section("Home screen");
        home.items.add(new Item(Kind.HOME, "home", "Home screen", layoutName(s.layout), openHomeSettings, null, null));
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
