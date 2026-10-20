package com.livingroom.flamingo.launcher.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AppOrder {
    public static final String SPOTIFY = "com.spotify.tv.android";

    private AppOrder() { }

    /** Everything except the main slots and the launcher itself: Spotify first, then most recently used, then A–Z. */
    public static List<AppEntry> others(List<AppEntry> installed, HomeConfig cfg, final Map<String, Long> lastUsed, String selfPkg) {
        Set<String> seen = new HashSet<>(cfg.main);
        seen.add(selfPkg);
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry a : installed) if (seen.add(a.pkg)) out.add(a);
        Collections.sort(out, (x, y) -> {
            if (x.pkg.equals(SPOTIFY) != y.pkg.equals(SPOTIFY)) return x.pkg.equals(SPOTIFY) ? -1 : 1;
            long ux = lastUsed.getOrDefault(x.pkg, 0L), uy = lastUsed.getOrDefault(y.pkg, 0L);
            if (ux != uy) return Long.compare(uy, ux);
            return x.label.compareToIgnoreCase(y.label);
        });
        return out;
    }

    /** The configured main slots that exist right now; inputs ("hdmi:N") are always kept. */
    public static List<String> mainSlots(HomeConfig cfg, Set<String> installedPkgs) {
        List<String> out = new ArrayList<>();
        for (String s : cfg.main) if (s.startsWith("hdmi:") || installedPkgs.contains(s)) out.add(s);
        return out;
    }
}
