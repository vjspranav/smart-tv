package com.livingroom.flamingo.launcher.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Latest media item per app, as seen in media sessions. Text format: one tab-separated item per line. */
public final class History {
    private static final Pattern NOT_CONTENT = Pattern.compile("(?i)\\b(trailer|teaser|preview)\\b");
    private final Map<String, MediaItem> latest = new LinkedHashMap<>();
    private final String selfPkg;

    public History(String selfPkg) { this.selfPkg = selfPkg; }

    /** Stores m as the app's latest item unless it is a short clip, trailer or the launcher itself. */
    public boolean record(MediaItem m) {
        if (m == null || m.pkg == null || m.pkg.equals(selfPkg)) return false;
        if (m.durationMs > 0 && m.durationMs < 60_000) return false;
        if (m.title != null && m.title.trim().isEmpty()) m.title = null;
        if (m.title != null && NOT_CONTENT.matcher(m.title).find()) return false;
        MediaItem prev = latest.get(m.pkg);
        if (prev != null && !m.playing && m.positionMs == 0 && same(prev.title, m.title)) m.positionMs = prev.positionMs;
        latest.put(m.pkg, m);
        return true;
    }

    public MediaItem latest(String pkg) { return latest.get(pkg); }

    public String serialize() {
        StringBuilder b = new StringBuilder();
        for (MediaItem m : latest.values())
            b.append(esc(m.pkg)).append('\t').append(esc(m.title)).append('\t').append(esc(m.subtitle)).append('\t')
             .append(m.positionMs).append('\t').append(m.durationMs).append('\t').append(m.updatedAt).append('\t')
             .append(m.playing ? 1 : 0).append('\t').append(esc(m.artPath)).append('\t').append(esc(m.intentUri)).append('\n');
        return b.toString();
    }

    /** Never throws; unreadable lines are skipped. */
    public static History parse(String text, String selfPkg) {
        History h = new History(selfPkg);
        if (text == null) return h;
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length != 9) continue;
            try {
                MediaItem m = new MediaItem();
                m.pkg = un(f[0]); m.title = un(f[1]); m.subtitle = un(f[2]);
                m.positionMs = Long.parseLong(f[3]); m.durationMs = Long.parseLong(f[4]); m.updatedAt = Long.parseLong(f[5]);
                m.playing = "1".equals(f[6]); m.artPath = un(f[7]); m.intentUri = un(f[8]);
                if (m.pkg != null) h.latest.put(m.pkg, m);
            } catch (RuntimeException ignored) { }
        }
        return h;
    }

    private static boolean same(String a, String b) { return a == null ? b == null : a.equals(b); }

    // null → "\0", and escape backslash, tab, newline so each item stays on one line
    private static String esc(String s) {
        return s == null ? "\\0" : s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String un(String s) {
        if (s.equals("\\0")) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) { char n = s.charAt(++i); b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n); }
            else b.append(ch);
        }
        return b.toString();
    }
}
