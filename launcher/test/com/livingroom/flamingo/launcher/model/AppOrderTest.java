package com.livingroom.flamingo.launcher.model;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class AppOrderTest {
    static AppEntry app(String pkg, String label) { return new AppEntry(pkg, label, true); }

    static List<String> pkgs(List<AppEntry> l) {
        List<String> out = new ArrayList<>();
        for (AppEntry a : l) out.add(a.pkg);
        return out;
    }

    @Test public void spotifyFirstThenRecentThenAlpha() {
        List<AppEntry> installed = Arrays.asList(app("b.zee", "ZEE5"), app("com.spotify.tv.android", "Spotify"),
                app("c.kodi", "kodi"), app("a.apple", "Apple TV"), app("d.vlc", "VLC"));
        Map<String, Long> used = new HashMap<>();
        used.put("d.vlc", 500L);
        used.put("b.zee", 900L);
        assertEquals(Arrays.asList("com.spotify.tv.android", "b.zee", "d.vlc", "a.apple", "c.kodi"),
                pkgs(AppOrder.others(installed, HomeConfig.defaults(), used, "self")));
    }

    @Test public void excludesMainSelfAndDuplicates() {
        List<AppEntry> installed = Arrays.asList(app("com.netflix.ninja", "Netflix"), app("self", "Flamingo"),
                app("x.app", "X"), app("x.app", "X again"));
        assertEquals(Collections.singletonList("x.app"),
                pkgs(AppOrder.others(installed, HomeConfig.defaults(), new HashMap<>(), "self")));
    }

    @Test public void mainSlotsSkipMissingButKeepHdmi() {
        Set<String> installed = new HashSet<>(Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja",
                "com.amazon.amazonvideo.livingroom"));
        assertEquals(Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja",
                "com.amazon.amazonvideo.livingroom", "hdmi:1"), AppOrder.mainSlots(HomeConfig.defaults(), installed));
    }
}
