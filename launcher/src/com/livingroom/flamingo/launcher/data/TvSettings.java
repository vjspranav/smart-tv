package com.livingroom.flamingo.launcher.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Picture mode through TCL's shared library (com.tcl.tv.display → TvPqManager), by reflection so a missing
 * or changed library only costs the displayed value. All setting changes belong to TCL's own screens.
 */
final class TvSettings {
    /**
     * TCL picture-mode ids → the names TCL's own Settings shows, from TvSettings.apk
     * (PictureManager2.getPictureMappingValues + the pm_* strings). Game ids carry a sub-mode offset.
     */
    static final Map<Integer, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put(10, "Intelligent");
        NAMES.put(1, "Vivid");
        NAMES.put(0, "Standard");
        NAMES.put(4, "Sport");
        NAMES.put(3, "Movie");
        for (int g = 5; g <= 7; g++) NAMES.put(g, "Game");
        NAMES.put(8, "PC");
        NAMES.put(12, "Photo");
        NAMES.put(27, "IMAX");
        NAMES.put(60, "Filmmaker");
        int[] hdr = {16, 31}; // HDR10 and HLG blocks share one layout
        for (int b : hdr) {
            NAMES.put(b, "Standard");
            NAMES.put(b + 1, "Vivid");
            NAMES.put(b + 3, "Movie");
            NAMES.put(b + 4, "Sport");
            for (int g = 5; g <= 7; g++) NAMES.put(b + g, "Game");
            NAMES.put(b + 8, "PC");
            NAMES.put(b + 10, "Intelligent");
        }
        NAMES.put(29, "Filmmaker");
        NAMES.put(43, "Filmmaker");
        NAMES.put(46, "Dolby Vision Bright");
        NAMES.put(47, "Dolby Vision Dark");
        NAMES.put(48, "Dolby Vision Game");
        NAMES.put(51, "Dolby Vision IQ");
    }

    private final Context c;
    private final boolean useTcl;

    TvSettings(Context c, boolean useTcl) {
        this.c = c;
        this.useTcl = useTcl;
    }

    /** Current picture mode name, or "" when it can't be read or isn't one we know (the tile then says Open). */
    String pictureName() {
        Integer id = pictureId();
        String n = id == null ? null : NAMES.get(id);
        return n != null ? n : "";
    }

    Integer pictureId() {
        if (!useTcl) return null;
        try {
            Object m = pq();
            return (Integer) m.getClass().getMethod("getPictureMode").invoke(m);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Connected Wi-Fi network name; "" if not connected or the name is hidden (needs location permission). */
    @SuppressWarnings("deprecation")
    String wifiName() {
        try {
            android.net.wifi.WifiManager w = c.getSystemService(android.net.wifi.WifiManager.class);
            String ssid = w.getConnectionInfo().getSSID();
            if (ssid == null || ssid.contains("<unknown")) return "";
            return ssid.replaceAll("^\"|\"$", "");
        } catch (Throwable t) {
            return "";
        }
    }

    private static Object pq() throws Exception {
        return Class.forName("com.tcl.tv.pq.TvPqManager").getMethod("getInstance").invoke(null);
    }

    boolean openPicture() { return Sources.start(c, new Intent(Settings.ACTION_DISPLAY_SETTINGS).setComponent(new ComponentName("com.android.tv.settings", "com.tcl.settings.PictureActivity"))); }
    boolean openSound() { return Sources.start(c, new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.tcl.settings.SoundActivity"))); }
    boolean openSleep() { return Sources.start(c, new Intent("com.tcl.action.sleep")); }
    boolean openAll() { return Sources.start(c, new Intent(Settings.ACTION_SETTINGS)); }
    boolean openWifi() { return Sources.start(c, new Intent(Settings.ACTION_WIFI_SETTINGS)); }
}
