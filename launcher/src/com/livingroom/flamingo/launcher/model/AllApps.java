package com.livingroom.flamingo.launcher.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The All apps screen's content: apps by group (most used first) or A–Z with a letter index; hidden apps kept apart. */
public final class AllApps {
    public enum Group {
        WATCH("Watch"), LISTEN("Listen"), PLAYERS("Players"), GAMES("Games"), TOOLS("TV & tools");
        public final String title;
        Group(String title) { this.title = title; }
    }

    public static final class Section {
        public final Group group;
        public final List<AppEntry> apps = new ArrayList<>();
        Section(Group group) { this.group = group; }
    }

    // ApplicationInfo.CATEGORY_* values (kept here so the model stays plain Java)
    private static final int CAT_GAME = 0, CAT_AUDIO = 1, CAT_VIDEO = 2, CAT_IMAGE = 3;
    /** Apps whose own category is missing or misleading on this TV. */
    private static final Map<String, Group> KNOWN = new HashMap<>();
    static {
        for (String p : new String[]{"com.google.android.youtube.tv", "com.netflix.ninja", "in.startv.hotstar",
                "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv", "com.graymatrix.did",
                "com.disney.disneyplus", "com.jio.media.ondemand", "com.sonyliv", "com.mxplayer.tv"}) KNOWN.put(p, Group.WATCH);
        for (String p : new String[]{"com.spotify.tv.android", "com.google.android.youtube.tvmusic", "com.amazon.mp3",
                "com.jio.media.jiobeats", "com.gaana"}) KNOWN.put(p, Group.LISTEN);
        for (String p : new String[]{"org.xbmc.kodi", "org.videolan.vlc", "com.mxtech.videoplayer.television",
                "com.tcl.ui_mediaCenter", "com.plexapp.android", "org.jellyfin.androidtv"}) KNOWN.put(p, Group.PLAYERS);
    }

    private AllApps() { }

    public static Group groupOf(AppEntry a) {
        Group g = KNOWN.get(a.pkg);
        if (g != null) return g;
        switch (a.category) {
            case CAT_VIDEO: return Group.WATCH;
            case CAT_AUDIO: return Group.LISTEN;
            case CAT_IMAGE: return Group.PLAYERS;
            case CAT_GAME: return Group.GAMES;
            default: return Group.TOOLS;
        }
    }

    private static final Comparator<AppEntry> BY_LABEL = (x, y) -> x.label.compareToIgnoreCase(y.label);

    /** Visible apps (not hidden, not this launcher) by group in Group order; most used first, then A–Z; empty groups dropped. */
    public static List<Section> groups(List<AppEntry> installed, HomeConfig cfg, final Map<String, Long> lastUsed, String selfPkg) {
        Map<Group, Section> by = new EnumMap<>(Group.class);
        for (AppEntry a : visible(installed, cfg, selfPkg)) {
            Group g = groupOf(a);
            Section s = by.get(g);
            if (s == null) by.put(g, s = new Section(g));
            s.apps.add(a);
        }
        Comparator<AppEntry> used = (x, y) -> {
            long ux = lastUsed.getOrDefault(x.pkg, 0L), uy = lastUsed.getOrDefault(y.pkg, 0L);
            return ux != uy ? Long.compare(uy, ux) : BY_LABEL.compare(x, y);
        };
        List<Section> out = new ArrayList<>(by.values());
        for (Section s : out) Collections.sort(s.apps, used);
        return out;
    }

    /** Visible apps alphabetically, ignoring case. */
    public static List<AppEntry> az(List<AppEntry> installed, HomeConfig cfg, String selfPkg) {
        List<AppEntry> out = visible(installed, cfg, selfPkg);
        Collections.sort(out, BY_LABEL);
        return out;
    }

    /** Distinct index letters of an A–Z list, in order. */
    public static List<Character> letters(List<AppEntry> az) {
        List<Character> out = new ArrayList<>();
        for (AppEntry a : az) {
            char l = letter(a);
            if (out.isEmpty() || out.get(out.size() - 1) != l) out.add(l);
        }
        return out;
    }

    /** The app's index letter: its first character upper-cased, or '#' when that isn't a letter. */
    public static char letter(AppEntry a) {
        char c = a.label.isEmpty() ? '#' : Character.toUpperCase(a.label.charAt(0));
        return Character.isLetter(c) ? c : '#';
    }

    /** Installed apps the owner hid, A–Z. */
    public static List<AppEntry> hidden(List<AppEntry> installed, HomeConfig cfg) {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry a : installed) if (cfg.hidden.contains(a.pkg)) out.add(a);
        Collections.sort(out, BY_LABEL);
        return out;
    }

    private static List<AppEntry> visible(List<AppEntry> installed, HomeConfig cfg, String selfPkg) {
        List<AppEntry> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (AppEntry a : installed)
            if (!a.pkg.equals(selfPkg) && !cfg.hidden.contains(a.pkg) && seen.add(a.pkg)) out.add(a);
        return out;
    }
}
