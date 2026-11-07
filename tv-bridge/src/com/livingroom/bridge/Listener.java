package com.livingroom.bridge;

import android.app.SearchManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.media.session.MediaSession;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.Iterator;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the TV's active media session and exposes it on 127.0.0.1:8770 (TV-local only).
 * The Pi reaches it through `adb forward`. Being an enabled notification listener is what
 * Android requires to call MediaSessionManager.getActiveSessions().
 */
public class Listener extends NotificationListenerService {
    static final String TAG = "RemoteBridge";
    static final int PORT = 8770;
    private ServerSocket server;
    private volatile boolean running;
    private byte[] artCache;
    private String artCacheId;

    @Override
    public void onCreate() {
        super.onCreate();
        startServer();
    }

    @Override
    public void onListenerConnected() {
        startServer();
        if (handler == null) handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(this::track);
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
        super.onDestroy();
    }

    private synchronized void startServer() {
        if (running) return;
        running = true;
        Thread thread = new Thread(() -> {
            try (ServerSocket socket = new ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"))) {
                socket.setReuseAddress(true);
                server = socket;
                while (running) {
                    Socket client = socket.accept();
                    new Thread(() -> handle(client), "bridge-client").start();
                }
            } catch (Exception e) {
                Log.w(TAG, "server stopped: " + e);
            } finally {
                running = false;
            }
        }, "bridge-server");
        thread.setDaemon(true);
        thread.start();
    }

    // ---- HTTP ----------------------------------------------------------------------------

    private void handle(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) return;
            while (true) {  // drain headers
                String header = in.readLine();
                if (header == null || header.isEmpty()) break;
            }
            String[] parts = line.split(" ");
            String target = parts.length > 1 ? parts[1] : "/";
            String path = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
            Map<String, String> query = parseQuery(target.contains("?") ? target.substring(target.indexOf('?') + 1) : "");
            OutputStream out = c.getOutputStream();
            try {
                if (path.equals("/state")) {
                    send(out, 200, "application/json", state().getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/wait")) {
                    // Long poll: answers as soon as anything the remote shows changes (or after ms).
                    long ms = 20000;
                    try { if (query.get("ms") != null) ms = Math.max(0, Math.min(30000, Long.parseLong(query.get("ms")))); } catch (NumberFormatException ignored) { }
                    waitForChange(query.get("sig"), ms);
                    send(out, 200, "application/json", state().getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/volume")) {
                    String result = volume(query.get("set"), query.get("mute"));
                    send(out, result == null ? 200 : 409, "application/json",
                         (result == null ? "{\"ok\":true}" : "{\"error\":" + quote(result) + "}").getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/art")) {
                    byte[] art = art();
                    if (art == null) send(out, 404, "application/json", "{\"error\":\"no artwork\"}".getBytes());
                    else send(out, 200, "image/jpeg", art);
                } else if (path.equals("/apps")) {
                    send(out, 200, "application/json", apps().getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/icon")) {
                    byte[] icon = icon(query.get("pkg"));
                    if (icon == null) send(out, 404, "application/json", "{\"error\":\"no icon\"}".getBytes());
                    else send(out, 200, "image/png", icon);
                } else if (path.equals("/launch") || path.equals("/view") || path.equals("/search")) {
                    String result = start(path, query);
                    send(out, result == null ? 200 : 409, "application/json",
                         (result == null ? "{\"ok\":true}" : "{\"error\":" + quote(result) + "}").getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/notifications")) {
                    send(out, 200, "application/json", notifications().getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/a11y/dump") || path.equals("/a11y/press")) {
                    A11y a = A11y.instance;
                    String body;
                    if (a == null) body = "{\"ok\":false,\"error\":\"accessibility service not enabled\"}";
                    else if (path.equals("/a11y/dump")) body = a.dump();
                    else { String r = a.press(query.get("id"), query.get("text")); body = r == null ? "{\"ok\":true}" : "{\"ok\":false,\"error\":" + quote(r) + "}"; }
                    send(out, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
                } else if (path.startsWith("/ime/")) {
                    send(out, 200, "application/json", ime(path, query).getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/cmd")) {
                    String result = command(query.get("a"), query.get("ms"));
                    send(out, result == null ? 200 : 409, "application/json",
                         (result == null ? "{\"ok\":true}" : "{\"error\":" + quote(result) + "}").getBytes(StandardCharsets.UTF_8));
                } else {
                    send(out, 404, "application/json", "{\"error\":\"not found\"}".getBytes());
                }
            } catch (SecurityException e) {
                send(out, 503, "application/json", "{\"error\":\"notification access not granted\"}".getBytes());
            }
        } catch (Exception e) {
            Log.w(TAG, "request failed: " + e);
        }
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws Exception {
        String head = "HTTP/1.0 " + code + " OK\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> map = new HashMap<>();
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try { map.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), "UTF-8")); }
                catch (Exception e) { map.put(pair.substring(0, eq), pair.substring(eq + 1)); }
            }
        }
        return map;
    }

    // ---- Media session -------------------------------------------------------------------
    // Sessions are tracked through callbacks and cached. Reading MediaController.getMetadata()
    // on every poll copies the full artwork bitmap across binder, which is heavy on this TV.

    static final class Entry {
        MediaController controller;
        MediaController.Callback callback;
        volatile MediaMetadata meta;
        volatile PlaybackState state;
        volatile List<MediaSession.QueueItem> queue;
    }
    private final Map<MediaSession.Token, Entry> entries = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile List<MediaSession.Token> order = new java.util.ArrayList<>();
    private android.os.Handler handler;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::onSessions;
    private volatile boolean tracking;

    private void track() {
        if (tracking) return;
        try {
            if (handler == null) handler = new android.os.Handler(android.os.Looper.getMainLooper());
            MediaSessionManager m = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, Listener.class);
            m.addOnActiveSessionsChangedListener(sessionsChanged, me, handler);
            tracking = true;
            onSessions(m.getActiveSessions(me));
        } catch (SecurityException e) {
            Log.w(TAG, "no notification access yet");
        }
    }

    @Override
    public void onListenerDisconnected() {
        try { ((MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE)).removeOnActiveSessionsChangedListener(sessionsChanged); } catch (Exception ignored) { }
        tracking = false;
        for (Entry e : entries.values()) try { e.controller.unregisterCallback(e.callback); } catch (Exception ignored) { }
        entries.clear();
    }

    private void onSessions(List<MediaController> list) {
        List<MediaSession.Token> next = new java.util.ArrayList<>();
        if (list != null) for (MediaController c : list) {
            MediaSession.Token token = c.getSessionToken();
            next.add(token);
            if (entries.containsKey(token)) continue;
            final Entry e = new Entry();
            e.controller = c;
            e.meta = c.getMetadata();
            e.state = c.getPlaybackState();
            e.queue = c.getQueue();
            e.callback = new MediaController.Callback() {
                @Override public void onMetadataChanged(MediaMetadata m) { e.meta = m; changed(); }
                @Override public void onPlaybackStateChanged(PlaybackState st) { e.state = st; changed(); }
                @Override public void onQueueChanged(List<MediaSession.QueueItem> q) { e.queue = q; changed(); }
                @Override public void onSessionDestroyed() { entries.remove(token); changed(); }
            };
            c.registerCallback(e.callback, handler);
            entries.put(token, e);
        }
        for (MediaSession.Token t : new java.util.ArrayList<>(entries.keySet())) {
            if (!next.contains(t)) {
                Entry gone = entries.remove(t);
                if (gone != null) try { gone.controller.unregisterCallback(gone.callback); } catch (Exception ignored) { }
            }
        }
        order = next;
        changed();
    }

    // ---- Change notifications ----------------------------------------------------------------
    // Media callbacks bump `version`; volume, power and the foreground app have no reliable callback,
    // so a waiting request also re-reads them every 400 ms (all TV-local calls, cheap).
    private final Object changes = new Object();
    private volatile long version;
    private volatile long noteVersion;
    private void changed() { synchronized (changes) { version++; changes.notifyAll(); } }

    /** Everything a phone would redraw for, except the moving playback position. */
    private String signature() {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        StringBuilder b = new StringBuilder();
        b.append(version).append('|').append(noteVersion).append('|').append(pm.isInteractive())
         .append('|').append(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
         .append('|').append(am.isStreamMute(android.media.AudioManager.STREAM_MUSIC))
         .append('|').append(foreground());
        return Integer.toHexString(b.toString().hashCode());
    }

    private void waitForChange(String since, long ms) {
        long end = SystemClock.elapsedRealtime() + ms;
        if (since == null) return;
        while (true) {
            if (!since.equals(signature())) return;
            long left = end - SystemClock.elapsedRealtime();
            if (left <= 0) return;
            synchronized (changes) {
                try { changes.wait(Math.min(400, left)); } catch (InterruptedException e) { return; }
            }
        }
    }

    // ---- Volume ------------------------------------------------------------------------------
    private String volume(String set, String mute) {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int stream = android.media.AudioManager.STREAM_MUSIC;
        try {
            if (set != null) {
                int v = Integer.parseInt(set);
                if (v < 0 || v > am.getStreamMaxVolume(stream)) return "volume out of range";
                am.setStreamVolume(stream, v, 0);
                if (am.getStreamVolume(stream) != v) return "volume did not change";
            }
            if (mute != null) am.adjustStreamVolume(stream, mute.equals("1") ? android.media.AudioManager.ADJUST_MUTE
                    : mute.equals("toggle") ? android.media.AudioManager.ADJUST_TOGGLE_MUTE : android.media.AudioManager.ADJUST_UNMUTE, 0);
        } catch (NumberFormatException e) {
            return "bad volume";
        } catch (SecurityException e) {
            return "volume change not allowed";
        }
        changed();
        return null;
    }

    private Entry pick() {
        track();
        Entry fallback = null, first = null;
        for (MediaSession.Token t : order) {
            Entry e = entries.get(t);
            if (e == null) continue;
            if (first == null) first = e;
            if (e.state != null && e.state.getState() == PlaybackState.STATE_PLAYING) return e;
            if (fallback == null && e.meta != null) fallback = e;
        }
        return fallback != null ? fallback : first;
    }

    private static long livePosition(PlaybackState state) {
        if (state == null) return -1;
        long position = state.getPosition();
        if (position < 0) return -1;
        if (state.getState() == PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0) {
            long elapsed = SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
            position += (long) (elapsed * state.getPlaybackSpeed());
        }
        return Math.max(0, position);
    }

    private static Bitmap bitmap(MediaMetadata meta) {
        if (meta == null) return null;
        for (String key : new String[]{MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
                                       MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            Bitmap b = meta.getBitmap(key);
            if (b != null && b.getWidth() > 8 && b.getHeight() > 8) return b;
        }
        return null;
    }

    private static String artUri(MediaMetadata meta) {
        if (meta == null) return null;
        for (String key : new String[]{MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                                       MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
            String uri = meta.getString(key);
            if (uri != null && (uri.startsWith("http://") || uri.startsWith("https://"))) return uri;
        }
        return null;
    }

    private static String text(MediaMetadata meta, String... keys) {
        if (meta == null) return null;
        for (String key : keys) {
            CharSequence value = meta.getText(key);
            if (value != null && value.toString().trim().length() > 0) return value.toString().trim();
        }
        return null;
    }

    /** Stable for the same artwork: changes only when the content (not the Java object) changes. */
    private static String artKey(String pkg, MediaMetadata meta, Bitmap art) {
        if (art == null) return null;
        String title = text(meta, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String artist = text(meta, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        return Integer.toHexString((pkg + "|" + title + "|" + artist + "|" + art.getWidth() + "x" + art.getHeight()).hashCode());
    }

    // Foreground app from usage events, read incrementally (cheap; replaces `dumpsys activity`).
    private volatile String foreground;
    private long foregroundChecked;
    private synchronized String foreground() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            long from = foregroundChecked == 0 ? now - 6L * 3600000L : foregroundChecked - 2000;
            android.app.usage.UsageEvents events = usm.queryEvents(from, now);
            android.app.usage.UsageEvents.Event ev = new android.app.usage.UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(ev);
                if (ev.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) foreground = ev.getPackageName();
            }
            foregroundChecked = now;
        } catch (Exception ignored) { }
        return foreground;
    }

    private String state() {
        StringBuilder json = new StringBuilder("{");
        json.append("\"ok\":true,\"now\":").append(SystemClock.elapsedRealtime());
        // Device state, so the Pi does not need to run dumpsys on every poll.
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        json.append(",\"interactive\":").append(pm.isInteractive());
        json.append(",\"volume\":").append(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        json.append(",\"volume_max\":").append(am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
        json.append(",\"muted\":").append(am.isStreamMute(android.media.AudioManager.STREAM_MUSIC));
        json.append(",\"foreground\":").append(quote(foreground()));
        json.append(",\"sig\":").append(quote(signature()));
        json.append(",\"notes\":").append(noteVersion);
        Entry e = pick();
        if (e == null) return json.append(",\"session\":false}").toString();
        PlaybackState state = e.state;
        MediaMetadata meta = e.meta;
        String pkg = e.controller.getPackageName();
        Bitmap art = bitmap(meta);
        long duration = meta == null ? -1 : meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
        json.append(",\"session\":true,\"package\":").append(quote(pkg));
        json.append(",\"state\":").append(state == null ? -1 : state.getState());
        json.append(",\"speed\":").append(state == null ? 0 : state.getPlaybackSpeed());
        json.append(",\"actions\":").append(state == null ? 0 : state.getActions());
        json.append(",\"position_ms\":").append(livePosition(state));
        json.append(",\"duration_ms\":").append(duration > 0 ? duration : -1);
        json.append(",\"title\":").append(quote(text(meta, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
        json.append(",\"artist\":").append(quote(text(meta, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                                                       MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)));
        json.append(",\"album\":").append(quote(text(meta, MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)));
        json.append(",\"art_id\":").append(quote(artKey(pkg, meta, art)));
        json.append(",\"art_uri\":").append(quote(artUri(meta)));
        json.append(",\"active_queue_id\":").append(state == null ? -1 : state.getActiveQueueItemId());
        json.append(",\"queue\":[");
        List<MediaSession.QueueItem> queue = e.queue;
        if (queue != null) {
            int n = 0;
            for (MediaSession.QueueItem item : queue) {
                if (n++ >= 40) break;
                if (n > 1) json.append(',');
                CharSequence title = item.getDescription().getTitle(), sub = item.getDescription().getSubtitle();
                json.append("{\"id\":").append(item.getQueueId())
                    .append(",\"title\":").append(quote(title == null ? null : title.toString()))
                    .append(",\"subtitle\":").append(quote(sub == null ? null : sub.toString())).append('}');
            }
        }
        json.append(']');
        return json.append("}").toString();
    }

    private synchronized byte[] art() {
        Entry e = pick();
        if (e == null) return null;
        MediaMetadata meta = e.meta;
        Bitmap art = bitmap(meta);
        if (art == null) return null;
        String id = artKey(e.controller.getPackageName(), meta, art);
        if (id.equals(artCacheId)) return artCache;   // encode each artwork once
        float scale = Math.min(1f, 640f / Math.max(art.getWidth(), art.getHeight()));
        Bitmap scaled = scale < 1f ? Bitmap.createScaledBitmap(art, Math.round(art.getWidth() * scale),
                                                               Math.round(art.getHeight() * scale), true) : art;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, out);
        if (scaled != art) scaled.recycle();
        artCacheId = id;
        artCache = out.toByteArray();
        return artCache;
    }

    /** Returns null on success, else a reason. */
    private String command(String action, String msParam) {
        Entry e = pick();
        if (e == null) return "no media session";
        if (action == null) return "missing action";
        MediaController c = e.controller;
        MediaController.TransportControls t = c.getTransportControls();
        PlaybackState state = e.state;
        long actions = state == null ? 0 : state.getActions();
        long ms = 0;
        try { if (msParam != null) ms = Long.parseLong(msParam); } catch (NumberFormatException ex) { return "bad ms"; }
        switch (action) {
            case "play": t.play(); return null;
            case "pause": t.pause(); return null;
            case "toggle":
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) t.pause(); else t.play();
                return null;
            case "next": t.skipToNext(); return null;
            case "queue": t.skipToQueueItem(ms); return null;
            case "previous": t.skipToPrevious(); return null;
            case "seek":
            case "skip": {
                long target = action.equals("seek") ? ms : livePosition(state) + ms;
                MediaMetadata meta = e.meta;
                long duration = meta == null ? -1 : meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (duration > 0) target = Math.min(target, duration - 1000);
                target = Math.max(0, target);
                if ((actions & PlaybackState.ACTION_SEEK_TO) != 0) { t.seekTo(target); return null; }
                if (action.equals("skip")) {
                    if (ms > 0 && (actions & PlaybackState.ACTION_FAST_FORWARD) != 0) { t.fastForward(); return null; }
                    if (ms < 0 && (actions & PlaybackState.ACTION_REWIND) != 0) { t.rewind(); return null; }
                }
                return "this app does not allow seeking";
            }
            default: return "unknown action";
        }
    }

    // ---- Apps ------------------------------------------------------------------------------

    private String apps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
        Map<String, Long> lastUsed = new HashMap<>();
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60L * 86400000L, now);
            if (stats != null) for (UsageStats u : stats) {
                Long prev = lastUsed.get(u.getPackageName());
                if (prev == null || u.getLastTimeUsed() > prev) lastUsed.put(u.getPackageName(), u.getLastTimeUsed());
            }
        } catch (Exception ignored) { }
        StringBuilder json = new StringBuilder("{\"apps\":[");
        Map<String, Boolean> seen = new HashMap<>();
        for (ResolveInfo r : found) {
            String pkg = r.activityInfo.packageName;
            if (seen.containsKey(pkg) || pkg.equals(getPackageName())) continue;
            seen.put(pkg, true);
            ApplicationInfo ai = r.activityInfo.applicationInfo;
            if (seen.size() > 1) json.append(',');
            Long used = lastUsed.get(pkg);
            json.append("{\"package\":").append(quote(pkg))
                .append(",\"label\":").append(quote(String.valueOf(r.loadLabel(pm))))
                .append(",\"category\":").append(ai.category)
                .append(",\"system\":").append((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                .append(",\"banner\":").append(r.activityInfo.banner != 0 || ai.banner != 0)
                .append(",\"last_used\":").append(used == null ? 0 : used).append('}');
        }
        return json.append("]}").toString();
    }

    private byte[] icon(String pkg) {
        if (pkg == null) return null;
        try {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            Drawable d = null;
            if (intent != null) {
                d = pm.getActivityBanner(intent);
                if (d == null) d = pm.getActivityIcon(intent);
            }
            if (d == null) d = pm.getApplicationBanner(pkg);
            if (d == null) d = pm.getApplicationIcon(pkg);
            Bitmap bitmap;
            if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                bitmap = ((BitmapDrawable) d).getBitmap();
            } else {
                int w = Math.max(1, d.getIntrinsicWidth()), h = Math.max(1, d.getIntrinsicHeight());
                if (w <= 1 || h <= 1) { w = 320; h = 180; }
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                d.setBounds(0, 0, w, h);
                d.draw(canvas);
            }
            float scale = Math.min(1f, 480f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
            if (scale < 1f) bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Launch an app, open a link, or run a Google TV search. Returns null on success. */
    private String start(String path, Map<String, String> q) {
        try {
            Intent intent;
            if (path.equals("/launch")) {
                String pkg = q.get("pkg");
                if (pkg == null) return "missing package";
                PackageManager pm = getPackageManager();
                intent = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
                if (intent == null) return "app cannot be opened";
            } else if (path.equals("/view")) {
                String uri = q.get("uri");
                if (uri == null) return "missing link";
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                if (q.get("pkg") != null) intent.setPackage(q.get("pkg"));
            } else {
                String text = q.get("q");
                if (text == null || text.trim().isEmpty()) return "missing search";
                intent = new Intent("android.search.action.GLOBAL_SEARCH").putExtra(SearchManager.QUERY, text);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (intent.resolveActivity(getPackageManager()) == null) return "no app on the TV can open this";
            startActivity(intent);
            return null;
        } catch (Exception e) {
            return "could not open: " + e.getClass().getSimpleName();
        }
    }

    // ---- Notifications -----------------------------------------------------------------------------

    private final ArrayDeque<String> recent = new ArrayDeque<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Notification n = sbn.getNotification();
            if (sbn.isOngoing() || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            if (sbn.getPackageName().equals(getPackageName()) || sbn.getPackageName().equals("android")) return;
            CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (title == null && text == null) return;
            String label;
            try { label = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(sbn.getPackageName(), 0))); }
            catch (Exception e) { label = sbn.getPackageName(); }
            String item = "{\"key\":" + quote(sbn.getKey() + "@" + sbn.getPostTime()) + ",\"package\":" + quote(sbn.getPackageName())
                    + ",\"app\":" + quote(label) + ",\"title\":" + quote(title == null ? null : title.toString())
                    + ",\"text\":" + quote(text == null ? null : text.toString()) + ",\"time\":" + sbn.getPostTime() + "}";
            synchronized (recent) {
                recent.addFirst(item);
                while (recent.size() > 20) recent.removeLast();
            }
            noteVersion++;
            changed();
        } catch (Exception ignored) { }
    }

    private String notifications() {
        StringBuilder json = new StringBuilder("{\"notifications\":[");
        synchronized (recent) {
            Iterator<String> it = recent.iterator();
            boolean first = true;
            while (it.hasNext()) { if (!first) json.append(','); json.append(it.next()); first = false; }
        }
        return json.append("]}").toString();
    }

    // ---- Phone keyboard -----------------------------------------------------------------------------

    private String ime(String path, Map<String, String> q) {
        Keyboard k = Keyboard.instance;
        if (k == null) return "{\"ready\":false}";
        if (path.equals("/ime/set") && q.get("text") != null) k.setText(q.get("text"));
        if (path.equals("/ime/submit")) k.submit();
        boolean active = k.active();
        String text = active && !k.password() ? k.text() : null;
        return "{\"ready\":true,\"active\":" + active + ",\"password\":" + k.password()
                + ",\"hint\":" + quote(k.hint()) + ",\"text\":" + quote(text) + "}";
    }

    static String quoteJson(String s) { return quote(s); }

    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch)); else b.append(ch);
            }
        }
        return b.append('"').toString();
    }
}
