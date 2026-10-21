package com.livingroom.flamingo.launcher.model;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class AllAppsTest {
    static AppEntry app(String pkg, String label, int category) { return new AppEntry(pkg, label, true, category); }

    static List<AppEntry> installed() {
        return Arrays.asList(
                app("com.netflix.ninja", "Netflix", -1), app("com.google.android.youtube.tv", "YouTube", -1),
                app("com.spotify.tv.android", "Spotify", -1), app("org.videolan.vlc", "VLC", -1),
                app("com.tcl.dashboard", "TCL Home", -1), app("com.android.vending", "Play Store", -1),
                app("x.unknown.audio", "Radio Garden", 1), app("x.unknown.video", "Mubi", 2),
                app("x.unknown.game", "Asphalt", 0), app("x.unknown.tool", "file manager", -1),
                app("self.launcher", "Flamingo", -1));
    }

    static List<String> pkgs(List<AppEntry> l) {
        List<String> out = new ArrayList<>();
        for (AppEntry a : l) out.add(a.pkg);
        return out;
    }

    @Test public void knownAppsAndCategoriesPickTheGroup() {
        assertEquals(AllApps.Group.WATCH, AllApps.groupOf(app("com.netflix.ninja", "Netflix", -1)));
        assertEquals(AllApps.Group.LISTEN, AllApps.groupOf(app("com.spotify.tv.android", "Spotify", -1)));
        assertEquals(AllApps.Group.PLAYERS, AllApps.groupOf(app("org.videolan.vlc", "VLC", -1)));
        assertEquals(AllApps.Group.LISTEN, AllApps.groupOf(app("x.unknown.audio", "Radio Garden", 1)));
        assertEquals(AllApps.Group.WATCH, AllApps.groupOf(app("x.unknown.video", "Mubi", 2)));
        assertEquals(AllApps.Group.GAMES, AllApps.groupOf(app("x.unknown.game", "Asphalt", 0)));
        assertEquals(AllApps.Group.TOOLS, AllApps.groupOf(app("x.unknown.tool", "file manager", -1)));
    }

    @Test public void groupsInOrderMostUsedFirstThenAlphabeticalNoHiddenNoSelfNoEmpty() {
        Map<String, Long> used = new HashMap<>();
        used.put("com.netflix.ninja", 50L);
        List<AllApps.Section> g = AllApps.groups(installed(), HomeConfig.defaults(), used, "self.launcher");
        List<AllApps.Group> order = new ArrayList<>();
        for (AllApps.Section s : g) order.add(s.group);
        assertEquals(Arrays.asList(AllApps.Group.WATCH, AllApps.Group.LISTEN, AllApps.Group.PLAYERS,
                AllApps.Group.GAMES, AllApps.Group.TOOLS), order);
        assertEquals(Arrays.asList("com.netflix.ninja", "x.unknown.video", "com.google.android.youtube.tv"), pkgs(g.get(0).apps));
        assertEquals(Arrays.asList("x.unknown.tool", "com.android.vending"), pkgs(g.get(4).apps));   // TCL Home hidden by default
    }

    @Test public void azIsCaseInsensitiveAndSkipsHidden() {
        List<AppEntry> az = AllApps.az(installed(), HomeConfig.defaults(), "self.launcher");
        assertEquals(Arrays.asList("Asphalt", "file manager", "Mubi", "Netflix", "Play Store", "Radio Garden", "Spotify", "VLC", "YouTube"),
                labels(az));
        assertEquals(Arrays.asList('A', 'F', 'M', 'N', 'P', 'R', 'S', 'V', 'Y'), AllApps.letters(az));
        assertEquals('#', AllApps.letter(app("x", "9Now", -1)));
    }

    @Test public void hiddenListAndUnhide() {
        HomeConfig c = HomeConfig.defaults();
        assertEquals(Collections.singletonList("com.tcl.dashboard"), pkgs(AllApps.hidden(installed(), c)));
        c.hidden.remove("com.tcl.dashboard");
        assertTrue(AllApps.hidden(installed(), c).isEmpty());
    }

    static List<String> labels(List<AppEntry> l) {
        List<String> out = new ArrayList<>();
        for (AppEntry a : l) out.add(a.label);
        return out;
    }
}
