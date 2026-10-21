package com.livingroom.flamingo.launcher.model;

import java.util.HashMap;
import java.util.Map;

/**
 * What the TCL remote's shortcut buttons do. TCL routes them through TCSCore (com.tcl.ttvs, disabled) and
 * the Settings button through Google TV's dashboard (disabled), so Flamingo maps them itself.
 * Key codes as this TV reports them (venus_IR_input.kl + TCL's policy): 2026-10-21 capture.
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

    /** The mapped key codes, for the service's quick reject. */
    public static boolean mapped(int keyCode) { return MAP.containsKey(keyCode); }
}
