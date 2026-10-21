package com.livingroom.flamingo.launcher.ui;

import com.livingroom.flamingo.launcher.model.AllApps;
import com.livingroom.flamingo.launcher.model.AppEntry;
import com.livingroom.flamingo.launcher.model.HdmiPort;
import com.livingroom.flamingo.launcher.model.HomeConfig;
import com.livingroom.flamingo.launcher.model.MediaItem;
import java.util.List;

/** Immutable snapshot of everything a layout draws. Built by Sources, never blocks. */
public final class Screen {
    /** One of the five main slots: an app (with its continue item) or an HDMI input. */
    public static final class Slot {
        public final AppEntry app;      // null for an input
        public final HdmiPort port;     // null for an app
        public final MediaItem cont;    // continue item, may be null

        public Slot(AppEntry app, HdmiPort port, MediaItem cont) {
            this.app = app;
            this.port = port;
            this.cont = cont;
        }

        public String key() { return app != null ? app.pkg : "hdmi:" + port.port; }
        public String name() { return app != null ? app.label : port.name; }
    }

    public final HomeConfig.Layout layout;
    public final List<Slot> main;
    public final List<HdmiPort> inputs;
    public final List<AppEntry> apps;
    public final MediaItem listen;          // Spotify, may be null
    public final String picture;            // current picture mode name, or "Open"
    public final String clock, ampm, date;  // ampm empty in 24-hour mode
    // All apps: by group, A–Z, the hidden ones, and which view the owner last used
    public List<AllApps.Section> groups = java.util.Collections.emptyList();
    public List<AppEntry> az = java.util.Collections.emptyList(), hidden = java.util.Collections.emptyList();
    public boolean appsAz;

    public Screen(HomeConfig.Layout layout, List<Slot> main, List<HdmiPort> inputs, List<AppEntry> apps,
                  MediaItem listen, String picture, String clock, String ampm, String date) {
        this.layout = layout;
        this.main = main;
        this.inputs = inputs;
        this.apps = apps;
        this.listen = listen;
        this.picture = picture;
        this.clock = clock;
        this.ampm = ampm;
        this.date = date;
    }
}
