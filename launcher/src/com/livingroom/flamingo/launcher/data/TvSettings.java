package com.livingroom.flamingo.launcher.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Picture mode through TCL's shared library (com.tcl.tv.display → TvPqManager), by reflection so a missing
 * or changed library only costs the inline value. Sound and sleep open TCL's own screens.
 */
final class TvSettings {
    /**
     * TCL picture-mode ids → the names TCL's own Settings shows, from TvSettings.apk
     * (PictureManager2.getPictureMappingValues + the pm_* strings). Game ids carry a sub-mode offset.
     */
    static final Map<Integer, String> NAMES = new LinkedHashMap<>();
    /** The SDR modes that can be set inline, in TCL's menu order. HDR/Dolby content has its own set. */
    static final Map<Integer, String> PICTURE = new LinkedHashMap<>();
    static {
        PICTURE.put(10, "Intelligent");
        PICTURE.put(1, "Vivid");
        PICTURE.put(0, "Standard");
        PICTURE.put(4, "Sport");
        PICTURE.put(3, "Movie");
        NAMES.putAll(PICTURE);
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

    /** True when the current mode is an SDR one, so the inline SDR choices apply. */
    boolean canChoose() {
        Integer id = pictureId();
        return id != null && PICTURE.containsKey(id);
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

    /**
     * Sets a picture mode for the current input, exactly as TCL's own Settings does (PictureManager2.setPictureMode:
     * EN_APPLY_CURRENT + FIELD_NORMAL). Never FIELD_GLOBAL: that saves a top-priority override on every input, and
     * the PS5's automatic game mode (ALLM) is then refused ("priority ... too low so return"), 2026-10-30.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    boolean setPicture(int id) {
        if (!useTcl) return false;
        try {
            Object m = pq();
            Class apply = Class.forName("com.tcl.tv.pq.EnApplyType"), field = Class.forName("com.tcl.tv.pq.EnPicModeFieldType"),
                    act = Class.forName("com.tcl.tv.pq.EnTclActType");
            Object ok = m.getClass().getMethod("setPictureMode", int.class, apply, int.class, field, act)
                    .invoke(m, 0, Enum.valueOf(apply, "EN_APPLY_CURRENT"), id, Enum.valueOf(field, "EN_PICTURE_MODE_FIELD_NORMAL"),
                            Enum.valueOf(act, "EN_TCL_ACT_EXEC_SAVE"));
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            return false;
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

    boolean openPicture() { return Sources.start(c, new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.tcl.settings.PictureActivity"))); }
    boolean openSound() { return Sources.start(c, new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.tcl.settings.SoundActivity"))); }
    boolean openSleep() { return Sources.start(c, new Intent("com.tcl.action.sleep")); }
    boolean openAll() { return Sources.start(c, new Intent(Settings.ACTION_SETTINGS)); }
    boolean openWifi() { return Sources.start(c, new Intent(Settings.ACTION_WIFI_SETTINGS)); }
}
