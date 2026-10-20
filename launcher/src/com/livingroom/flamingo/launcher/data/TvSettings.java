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
    /** TCL picture-mode ids → names, as read from this TV (see investigations/probe-launcher.txt). */
    static final Map<Integer, String> PICTURE = new LinkedHashMap<>();

    private final Context c;
    private final boolean useTcl;

    TvSettings(Context c, boolean useTcl) {
        this.c = c;
        this.useTcl = useTcl;
    }

    /** Current picture mode name, "Mode n" for an unmapped id, or "Open" when it can't be read. */
    String pictureName() {
        Integer id = pictureId();
        if (id == null) return "Open";
        String n = PICTURE.get(id);
        return n != null ? n : "Mode " + id;
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

    /** Sets a picture mode; false if the TV refused or the library is missing. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    boolean setPicture(int id) {
        if (!useTcl) return false;
        try {
            Object m = pq();
            Class field = Class.forName("com.tcl.tv.pq.EnPicModeFieldType"), act = Class.forName("com.tcl.tv.pq.EnTclActType");
            Object ok = m.getClass().getMethod("setPictureMode", int.class, int.class, field, act)
                    .invoke(m, 0, id, Enum.valueOf(field, "EN_PICTURE_MODE_FIELD_GLOBAL"), Enum.valueOf(act, "EN_TCL_ACT_EXEC_SAVE"));
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            return false;
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
