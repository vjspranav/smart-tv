package com.livingroom.flamingo.launcher.model;

import java.util.HashMap;
import java.util.Map;

/**
 * What the TCL remote's shortcut buttons do. TCL hands them to Google TV's launcher (disabled), so Flamingo maps them itself.
 * Key codes as this TV reports them (venus_IR_input.kl + TCL's policy): 2026-10-21 capture. Keys are read from the
 * system log, never intercepted: a key-filtering accessibility service broke hold-to-repeat (volume, fast scroll).
 */
public final class RemoteKeys {
    /** Target meaning "open the TV's Settings". */
    public static final String SETTINGS = "settings";
    private static final Map<Integer, String> MAP = new HashMap<>();

    static {
        MAP.put(83, SETTINGS);                          // gear (KEYCODE_NOTIFICATION)
        MAP.put(4062, "com.netflix.ninja");             // NETFLIX
        MAP.put(4065, "com.google.android.youtube.tv"); // YouTube
    }

    private RemoteKeys() { }

    /** A package to open, SETTINGS, or null when the key should pass through untouched. */
    public static String target(int keyCode) { return MAP.get(keyCode); }

    public static boolean mapped(int keyCode) { return MAP.containsKey(keyCode); }

    /**
     * The key code released in one of TCL's WindowManager log lines ("handleKeyBeforeQueueing event=KeyEvent {
     * action=ACTION_UP, keyCode=4062, ... flags=0x8 ..."), or -1 for anything else: presses, cancelled keys, other lines.
     */
    public static int releasedKey(String line) {
        if (line == null || !line.contains("handleKeyBeforeQueueing") || !line.contains("action=ACTION_UP,")) return -1;
        String flags = field(line, "flags=0x");
        if (flags == null || (Integer.parseInt(flags, 16) & 0x20) != 0) return -1;   // FLAG_CANCELED
        String k = field(line, "keyCode=");
        if ("KEYCODE_NOTIFICATION".equals(k)) return 83;
        try { return Integer.parseInt(k); } catch (NumberFormatException | NullPointerException e) { return -1; }
    }

    private static String field(String line, String key) {
        int i = line.indexOf(key);
        if (i < 0) return null;
        int j = line.indexOf(',', i);
        return j < 0 ? null : line.substring(i + key.length(), j);
    }
}
