package com.livingroom.flamingo.launcher.model;

/** A launchable app. leanback = has a TV (leanback) launcher entry; category = ApplicationInfo.category (-1 unknown). */
public final class AppEntry {
    public final String pkg, label;
    public final boolean leanback;
    public final int category;

    public AppEntry(String pkg, String label, boolean leanback) { this(pkg, label, leanback, -1); }

    public AppEntry(String pkg, String label, boolean leanback, int category) {
        this.pkg = pkg;
        this.label = label;
        this.leanback = leanback;
        this.category = category;
    }
}
