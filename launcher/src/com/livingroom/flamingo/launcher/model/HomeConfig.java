package com.livingroom.flamingo.launcher.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Owner's launcher settings: layout, the five main slots, HDMI names, hidden apps, All apps view. Text format, one key per line. */
public final class HomeConfig {
    public enum Layout { A, G, N }

    public Layout layout = Layout.A;
    /** Package names, or "hdmi:N" for an input. */
    public List<String> main = new ArrayList<>();
    public Map<Integer, String> hdmiNames = new TreeMap<>();
    /** Packages left out of All apps (TCL's own launchers by default). */
    public Set<String> hidden = new LinkedHashSet<>();
    /** All apps opens alphabetically instead of by group (the default). */
    public boolean appsAz;

    public static HomeConfig defaults() {
        HomeConfig c = new HomeConfig();
        c.main = new ArrayList<>(Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja",
                "in.startv.hotstar", "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv"));
        c.hdmiNames.put(1, "PlayStation 5");
        c.hidden.addAll(Arrays.asList("com.tcl.dashboard", "com.tcl.tv.tclhome_passive"));
        return c;
    }

    /** Moves main slot i one step (dir -1 or +1), trading places with its neighbour; returns where it is now. */
    public int moveMain(int i, int dir) {
        int j = i + dir;
        if (i < 0 || i >= main.size() || j < 0 || j >= main.size()) return i;
        main = new ArrayList<>(main);
        String t = main.get(i);
        main.set(i, main.get(j));
        main.set(j, t);
        return j;
    }

    /** Puts key in slot i; if it was already on the row, it trades places with what slot i held. */
    public void replaceMain(int i, String key) {
        if (i < 0 || i >= main.size()) return;
        main = new ArrayList<>(main);
        int was = main.indexOf(key);
        if (was >= 0) main.set(was, main.get(i));
        main.set(i, key);
    }

    /** The owner's name for an input; blank or null goes back to the TV's own name. */
    public void setHdmiName(int port, String name) {
        if (name == null || name.trim().isEmpty()) hdmiNames.remove(port);
        else hdmiNames.put(port, name.trim());
    }

    public String serialize() {
        StringBuilder b = new StringBuilder("layout=").append(layout.name()).append('\n');
        b.append("main=").append(String.join(",", main)).append('\n');
        for (Map.Entry<Integer, String> e : hdmiNames.entrySet())
            b.append("hdmi.").append(e.getKey()).append('=').append(escape(e.getValue())).append('\n');
        b.append("hidden=").append(String.join(",", hidden)).append('\n');
        b.append("appsaz=").append(appsAz).append('\n');
        return b.toString();
    }

    /** Never throws; anything unreadable falls back to the defaults. */
    public static HomeConfig parse(String text) {
        HomeConfig d = defaults();
        if (text == null || text.isEmpty()) return d;
        try {
            HomeConfig c = defaults();
            boolean any = false;
            for (String line : text.split("\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq), v = unescape(line.substring(eq + 1));
                if (k.equals("layout")) { c.layout = Layout.valueOf(v); any = true; }
                else if (k.equals("main")) { c.main = v.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(v.split(","))); any = true; }
                else if (k.startsWith("hdmi.")) { c.hdmiNames.put(Integer.parseInt(k.substring(5)), v); any = true; }
                else if (k.equals("hidden")) { c.hidden = new LinkedHashSet<>(v.isEmpty() ? new ArrayList<String>() : Arrays.asList(v.split(","))); any = true; }
                else if (k.equals("appsaz")) { c.appsAz = Boolean.parseBoolean(v); any = true; }
            }
            return any ? c : d;
        } catch (RuntimeException e) {
            return d;
        }
    }

    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\n", "\\n"); }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) { char n = s.charAt(++i); b.append(n == 'n' ? '\n' : n); }
            else b.append(ch);
        }
        return b.toString();
    }
}
