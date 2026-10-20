package com.livingroom.flamingo.launcher.model;

/** A launchable app. leanback = has a TV (leanback) launcher entry. */
public final class AppEntry {
    public final String pkg, label;
    public final boolean leanback;

    public AppEntry(String pkg, String label, boolean leanback) {
        this.pkg = pkg;
        this.label = label;
        this.leanback = leanback;
    }
}
