package com.livingroom.flamingo.launcher.model;

/** Something the owner watched or listened to, from a media session or a Watch Next row. */
public final class MediaItem {
    public String pkg, title, subtitle, artPath, intentUri;
    public long positionMs, durationMs, updatedAt;
    public boolean playing;

    /** The better "continue" item for one app: one with a title wins; otherwise the more recent. */
    public static MediaItem pick(MediaItem a, MediaItem b) {
        if (a == null || b == null) return a != null ? a : b;
        if ((a.title == null) != (b.title == null)) return a.title != null ? a : b;
        return a.updatedAt >= b.updatedAt ? a : b;
    }
}
