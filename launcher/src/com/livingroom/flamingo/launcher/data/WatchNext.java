package com.livingroom.flamingo.launcher.data;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract.WatchNextPrograms;
import android.net.Uri;
import com.livingroom.flamingo.launcher.model.MediaItem;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/** Apps' own "continue watching" rows (readable with READ_TV_LISTINGS on this TV). Latest row per app. */
final class WatchNext {
    private static final String[] COLS = {WatchNextPrograms.COLUMN_PACKAGE_NAME, WatchNextPrograms.COLUMN_TITLE,
            WatchNextPrograms.COLUMN_EPISODE_TITLE, WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            WatchNextPrograms.COLUMN_DURATION_MILLIS, WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            WatchNextPrograms.COLUMN_INTENT_URI, WatchNextPrograms.COLUMN_POSTER_ART_URI};
    private final Context c;
    private final Store store;

    WatchNext(Context c, Store store) {
        this.c = c;
        this.store = store;
    }

    /** Must run off the main thread: it may download poster art. */
    Map<String, MediaItem> latestPerApp() {
        Map<String, MediaItem> out = new HashMap<>();
        Map<String, String> posters = new HashMap<>();
        try (Cursor q = c.getContentResolver().query(WatchNextPrograms.CONTENT_URI, COLS, null, null, null)) {
            while (q != null && q.moveToNext()) {
                MediaItem m = new MediaItem();
                m.pkg = q.getString(0);
                m.title = q.getString(1);
                String ep = q.getString(2), s = q.getString(3), e = q.getString(4);
                m.subtitle = s != null && e != null ? "S" + s + " · E" + e + (ep != null ? " · " + ep : "") : ep;
                m.positionMs = q.getLong(5);
                m.durationMs = q.getLong(6);
                m.updatedAt = q.getLong(7);
                m.intentUri = q.getString(8);
                MediaItem prev = out.get(m.pkg);
                if (m.pkg != null && (prev == null || m.updatedAt > prev.updatedAt)) {
                    out.put(m.pkg, m);
                    posters.put(m.pkg, q.getString(9));
                }
            }
        } catch (RuntimeException ignored) {
            return out;     // permission missing or provider error: continue falls back to media sessions
        }
        for (MediaItem m : out.values()) m.artPath = poster(m, posters.get(m.pkg));
        return out;
    }

    /** Cached as art/wn-<pkg>-<hash>.png so each poster is fetched once. */
    private String poster(MediaItem m, String uri) {
        if (uri == null) return null;
        String key = "wn-" + m.pkg + "-" + Integer.toHexString(uri.hashCode());
        String rel = "art/" + key.replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
        if (store.loadArt(rel) != null) return rel;
        try (InputStream in = open(uri)) {
            Bitmap b = in == null ? null : BitmapFactory.decodeStream(in);
            return b == null ? null : store.saveArt(key, Art.cover(b, 960, 540));
        } catch (Exception e) {
            return null;
        }
    }

    private InputStream open(String uri) throws Exception {
        if (uri.startsWith("http")) {
            HttpURLConnection h = (HttpURLConnection) new URL(uri).openConnection();
            h.setConnectTimeout(5000);
            h.setReadTimeout(8000);
            return h.getInputStream();
        }
        return c.getContentResolver().openInputStream(Uri.parse(uri));
    }
}
