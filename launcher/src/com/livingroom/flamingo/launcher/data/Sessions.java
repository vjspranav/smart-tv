package com.livingroom.flamingo.launcher.data;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import com.livingroom.flamingo.launcher.model.MediaItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Watches active media sessions (needs the MediaListener grant) and turns them into MediaItems. */
final class Sessions {
    interface Sink { void onItem(MediaItem m); }

    private final Store store;
    private final Sink sink;
    private final Handler bg;
    private final Map<MediaController, MediaController.Callback> watched = new HashMap<>();
    private final Map<String, String> artFor = new HashMap<>();   // pkg → title its art was saved for

    Sessions(Context c, Store store, Handler bg, Sink sink) {
        this.store = store;
        this.sink = sink;
        this.bg = bg;
        MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        ComponentName me = new ComponentName(c, MediaListener.class);
        try {
            msm.addOnActiveSessionsChangedListener(this::watch, me, bg);
            bg.post(() -> watch(msm.getActiveSessions(me)));
        } catch (SecurityException e) {
            // listener not granted yet: Watch Next still works; run tools/grant-launcher.sh
        }
    }

    private void watch(List<MediaController> controllers) {
        if (controllers == null) controllers = new ArrayList<>();
        for (MediaController old : new ArrayList<>(watched.keySet()))
            if (!controllers.contains(old)) old.unregisterCallback(watched.remove(old));
        for (final MediaController mc : controllers) {
            if (watched.containsKey(mc)) continue;
            MediaController.Callback cb = new MediaController.Callback() {
                @Override public void onMetadataChanged(MediaMetadata md) { update(mc); }
                @Override public void onPlaybackStateChanged(PlaybackState s) { update(mc); }
            };
            mc.registerCallback(cb, bg);
            watched.put(mc, cb);
            update(mc);
        }
    }

    private void update(MediaController mc) {
        MediaMetadata md = mc.getMetadata();
        PlaybackState st = mc.getPlaybackState();
        if (md == null && st == null) return;
        MediaItem m = new MediaItem();
        m.pkg = mc.getPackageName();
        m.updatedAt = System.currentTimeMillis();
        if (md != null) {
            m.title = first(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            m.subtitle = first(md, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ALBUM);
            m.durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        }
        if (st != null) {
            m.playing = st.getState() == PlaybackState.STATE_PLAYING;
            m.positionMs = Math.max(0, st.getPosition());
        }
        m.artPath = art(m, md);
        sink.onItem(m);
    }

    /** Saves the session's artwork once per title, scaled to 960×540. */
    private String art(MediaItem m, MediaMetadata md) {
        String key = "s-" + m.pkg;
        if (md == null) return null;
        if (m.title != null && m.title.equals(artFor.get(m.pkg))) return "art/" + key.replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
        Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (b == null) return null;
        String rel = store.saveArt(key, Art.fit(b, 960, 540));
        if (rel != null) artFor.put(m.pkg, m.title);
        return rel;
    }

    private static String first(MediaMetadata md, String... keys) {
        for (String k : keys) {
            CharSequence v = md.getText(k);
            if (v != null && v.toString().trim().length() > 0) return v.toString();
        }
        return null;
    }
}
