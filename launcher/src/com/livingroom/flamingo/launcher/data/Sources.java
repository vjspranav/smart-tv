package com.livingroom.flamingo.launcher.data;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;
import com.livingroom.flamingo.launcher.model.AllApps;
import com.livingroom.flamingo.launcher.model.AppEntry;
import com.livingroom.flamingo.launcher.model.AppOrder;
import com.livingroom.flamingo.launcher.model.HdmiPort;
import com.livingroom.flamingo.launcher.model.History;
import com.livingroom.flamingo.launcher.model.HomeConfig;
import com.livingroom.flamingo.launcher.model.MediaItem;
import com.livingroom.flamingo.launcher.ui.Screen;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The launcher's single data hub. Slow work (package queries, Watch Next, TCL calls) runs on one
 * background thread; snapshot() only reads what's already loaded, so Home never waits on it.
 */
public final class Sources {
    private static Sources instance;

    private final Context c;
    private final Store store;
    private final Handler bg, main = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new ArrayList<>();
    private Apps apps;
    private Hdmi hdmi;
    private TvSettings tv;

    // Loaded state, replaced wholesale on the background thread and read on the main thread
    private volatile HomeConfig config;
    private volatile History history;
    private volatile List<AppEntry> appList = Collections.emptyList();
    private volatile Map<String, Long> lastUsed = Collections.emptyMap(), timeUsed = Collections.emptyMap();
    private volatile Map<String, MediaItem> watchNext = Collections.emptyMap();
    private volatile List<HdmiPort> ports = Collections.emptyList();
    private volatile String picture = "", wifi = "";

    // ~2 MB per 960×540 image; holds the five main slots plus Listen
    private final Map<String, Bitmap> artCache = new java.util.LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > 6; }
    };

    private final Runnable refresh = this::loadAll, notifyAll = this::fire, saveHistory = this::persistHistory;

    public static synchronized Sources get(Context c) {
        if (instance == null) instance = new Sources(c.getApplicationContext());
        return instance;
    }

    private Sources(Context c) {
        this.c = c;
        this.store = new Store(c);
        HandlerThread t = new HandlerThread("flamingo-sources", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        t.start();
        bg = new Handler(t.getLooper());
        config = HomeConfig.parse(store.read("config.txt"));
        history = History.parse(store.read("history.txt"), c.getPackageName());
        bg.post(() -> {
            apps = new Apps(c, this::requestRefresh);
            hdmi = new Hdmi(c, this::requestRefresh);
            tv = new TvSettings(c, tclAllowed);
            new Sessions(c, store, bg, this::onSession);
            loadAll();
        });
    }

    /** Debug (extra notcl): set before the first get() to run as if TCL's library were missing. */
    public static boolean tclAllowed = true;

    public void listen(Runnable onChange) { listeners.add(onChange); }

    public void unlisten(Runnable onChange) { listeners.remove(onChange); }

    public void requestRefresh() {
        bg.removeCallbacks(refresh);
        bg.postDelayed(refresh, 300);
    }

    private void loadAll() {
        if (apps == null) return;
        appList = apps.list();
        lastUsed = apps.lastUsed();
        timeUsed = apps.timeUsed();
        ports = hdmi.ports(config.hdmiNames);
        picture = tv.pictureName();
        wifi = tv.wifiName();
        watchNext = new WatchNext(c, store).latestPerApp();
        warmArt();
        main.post(notifyAll);
    }

    private void onSession(MediaItem m) {
        History h = History.parse(history.serialize(), c.getPackageName());
        if (!h.record(m)) return;
        history = h;
        bg.removeCallbacks(saveHistory);
        bg.postDelayed(saveHistory, 5000);
        main.removeCallbacks(notifyAll);
        main.post(notifyAll);
    }

    private void persistHistory() { store.write("history.txt", history.serialize()); }

    private void fire() { for (Runnable r : new ArrayList<>(listeners)) r.run(); }

    public HomeConfig config() { return HomeConfig.parse(config.serialize()); }

    public void saveConfig(HomeConfig cfg) {
        config = HomeConfig.parse(cfg.serialize());
        bg.post(() -> store.write("config.txt", config.serialize()));
        requestRefresh();
        main.post(notifyAll);
    }

    /** Everything a layout needs, from already-loaded state. stress=true swaps in worst-case data. */
    public Screen snapshot(boolean stress) {
        if (stress) return allApps(Stress.screen(config.layout, clock(), ampm(), date()), Stress.apps(), config);
        HomeConfig cfg = config;
        Map<String, AppEntry> byPkg = new HashMap<>();
        Set<String> installed = new HashSet<>();
        for (AppEntry a : appList) { byPkg.put(a.pkg, a); installed.add(a.pkg); }
        Map<Integer, HdmiPort> portByNum = new HashMap<>();
        for (HdmiPort p : ports) portByNum.put(p.port, p);
        List<Screen.Slot> main = new ArrayList<>();
        for (String key : AppOrder.mainSlots(cfg, installed)) {
            if (key.startsWith("hdmi:")) {
                HdmiPort p = portByNum.get(Integer.parseInt(key.substring(5)));
                if (p != null) main.add(new Screen.Slot(null, p, null));
            } else {
                main.add(new Screen.Slot(byPkg.get(key), null, continueFor(key)));
            }
        }
        List<AppEntry> others = AppOrder.others(appList, cfg, lastUsed, c.getPackageName());
        MediaItem listen = history.latest(AppOrder.SPOTIFY);
        return allApps(new Screen(cfg.layout, main, ports, others, listen, picture, clock(), ampm(), date()), appList, cfg);
    }

    private Screen allApps(Screen s, List<AppEntry> all, HomeConfig cfg) {
        s.groups = AllApps.groups(all, cfg, timeUsed, c.getPackageName());
        s.az = AllApps.az(all, cfg, c.getPackageName());
        s.hidden = AllApps.hidden(all, cfg);
        s.appsAz = cfg.appsAz;
        return s;
    }

    private MediaItem continueFor(String pkg) { return MediaItem.pick(history.latest(pkg), watchNext.get(pkg)); }

    private String clock() {
        return DateFormat.format(DateFormat.is24HourFormat(c) ? "HH:mm" : "h:mm", new Date()).toString();
    }

    private String ampm() { return DateFormat.is24HourFormat(c) ? "" : DateFormat.format("a", new Date()).toString(); }

    private String date() { return DateFormat.format("EEEE d MMMM", new Date()).toString(); }

    // ---- actions used by layouts ----

    public Bitmap banner(String pkg) { return apps == null ? null : apps.banner(pkg); }

    /** Decoded artwork, cached; loadAll() decodes the main slots' art in the background so focus moves never decode. */
    public Bitmap art(MediaItem m) {
        if (m == null || m.artPath == null) return null;
        synchronized (artCache) {
            Bitmap b = artCache.get(m.artPath);
            if (b != null) return b;
        }
        Bitmap b = store.loadArt(m.artPath);
        if (b != null) synchronized (artCache) { artCache.put(m.artPath, b); }
        return b;
    }

    private void warmArt() {
        HomeConfig cfg = config;
        for (String key : cfg.main) if (!key.startsWith("hdmi:")) art(continueFor(key));
        art(history.latest(AppOrder.SPOTIFY));
    }

    public boolean open(Screen.Slot s) {
        if (s.port != null) return hdmi.open(s.port);
        if (s.cont != null && s.cont.intentUri != null) {
            try {
                if (start(c, Intent.parseUri(s.cont.intentUri, Intent.URI_INTENT_SCHEME))) return true;
            } catch (Exception ignored) { }
        }
        return apps.launch(s.app.pkg);
    }

    public boolean openApp(String pkg) { return apps.launch(pkg); }

    // ---- All apps ----

    public void setHidden(String pkg, boolean hide) {
        HomeConfig cfg = config();
        if (hide) cfg.hidden.add(pkg); else cfg.hidden.remove(pkg);
        saveConfig(cfg);
    }

    public void setAppsAz(boolean az) {
        HomeConfig cfg = config();
        if (cfg.appsAz == az) return;
        cfg.appsAz = az;
        saveConfig(cfg);
    }

    /** Puts pkg on the Home row in place of slot `index` (or moves it there if it's already on the row). */
    public void pin(String pkg, int index) {
        HomeConfig cfg = config();
        int at = cfg.main.indexOf(pkg);
        if (at >= 0) cfg.main.set(at, cfg.main.get(index));
        if (index >= 0 && index < cfg.main.size()) cfg.main.set(index, pkg); else cfg.main.add(pkg);
        saveConfig(cfg);
    }

    public boolean appInfo(String pkg) {
        return start(c, new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + pkg)));
    }

    public boolean openInput(HdmiPort p) { return hdmi.open(p); }

    public boolean openPicture() { return tv.openPicture(); }
    public boolean openSound() { return tv.openSound(); }
    public boolean openSleep() { return tv.openSleep(); }
    public boolean openAllSettings() { return tv.openAll(); }
    public boolean openWifi() { return tv.openWifi(); }

    /** Connected network name, or "" when unknown. */
    public String wifi() { return wifi; }

    static boolean start(Context c, Intent i) {
        if (i == null) return false;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Debug: logs the current snapshot under tag FlamingoDump. */
    public void dump() {
        Screen s = snapshot(false);
        Log.i("FlamingoDump", "layout=" + s.layout + " picture=" + s.picture + " wifi=" + wifi + " clock=" + s.clock + s.ampm + " date=" + s.date);
        for (Screen.Slot sl : s.main)
            Log.i("FlamingoDump", "main " + sl.key() + " name=" + sl.name() + (sl.port != null ? " state=" + sl.port.state : "")
                    + (sl.cont != null ? " cont=" + sl.cont.title + " | " + sl.cont.subtitle + " pos=" + sl.cont.positionMs
                    + "/" + sl.cont.durationMs + " art=" + sl.cont.artPath + " intent=" + (sl.cont.intentUri != null) : ""));
        for (HdmiPort p : s.inputs) Log.i("FlamingoDump", "input " + p.port + " " + p.name + " " + p.state + " " + p.inputId);
        for (AppEntry a : s.apps) Log.i("FlamingoDump", "app " + a.pkg + " " + a.label);
        Log.i("FlamingoDump", "listen " + (s.listen == null ? "none" : s.listen.title + " | " + s.listen.subtitle + " playing=" + s.listen.playing));
    }
}
