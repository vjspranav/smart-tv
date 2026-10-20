package com.livingroom.flamingo.launcher;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.util.Log;

/** Debug-only checks of what this TV lets a normal app read. Started with `--es probe read|setpic`. */
final class Probe {
    private static final String TAG = "FlamingoProbe";

    static void run(Context c, String what, int pic) {
        if ("setpic".equals(what)) { setPicture(pic); picture(); return; }
        watchNext(c);
        picture();
        inputs(c);
    }

    private static void watchNext(Context c) {
        String[] cols = {TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME, TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS};
        try (Cursor q = c.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null, null)) {
            Log.i(TAG, "WATCHNEXT_ROWS=" + (q == null ? "null" : q.getCount()));
            for (int i = 0; q != null && i < 20 && q.moveToNext(); i++)
                Log.i(TAG, "WATCHNEXT " + q.getString(0) + " | " + q.getString(1) + " | " + q.getString(2) + " | " + q.getLong(3) + " | " + q.getLong(4));
        } catch (Throwable t) {
            Log.i(TAG, "WATCHNEXT_ERROR=" + t);
        }
    }

    private static Object pq() throws Exception {
        Class<?> k = Class.forName("com.tcl.tv.pq.TvPqManager");
        return k.getMethod("getInstance").invoke(null);
    }

    private static void picture() {
        try {
            Object m = pq();
            Log.i(TAG, "PICTURE_MODE=" + m.getClass().getMethod("getPictureMode").invoke(m));
        } catch (Throwable t) {
            Log.i(TAG, "PICTURE_MODE_ERROR=" + t);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setPicture(int mode) {
        try {
            Object m = pq();
            Class field = Class.forName("com.tcl.tv.pq.EnPicModeFieldType");
            Class act = Class.forName("com.tcl.tv.pq.EnTclActType");
            Object ok = m.getClass().getMethod("setPictureMode", int.class, int.class, field, act)
                    .invoke(m, 0, mode, Enum.valueOf(field, "EN_PICTURE_MODE_FIELD_GLOBAL"), Enum.valueOf(act, "EN_TCL_ACT_EXEC_SAVE"));
            Log.i(TAG, "SET_PICTURE_MODE " + mode + " -> " + ok);
        } catch (Throwable t) {
            Log.i(TAG, "SET_PICTURE_MODE_ERROR=" + t);
        }
    }

    private static void inputs(Context c) {
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo in : tim.getTvInputList()) {
            String cec = "";
            try {  // getHdmiDeviceInfo() is a hidden API
                Object h = TvInputInfo.class.getMethod("getHdmiDeviceInfo").invoke(in);
                if (h != null) cec = " cec=" + h.getClass().getMethod("getDisplayName").invoke(h)
                        + " port=" + h.getClass().getMethod("getPortId").invoke(h);
            } catch (Throwable t) { cec = " cec_error=" + t.getClass().getSimpleName(); }
            Log.i(TAG, "INPUT " + in.getId() + " type=" + in.getType() + " parent=" + in.getParentId()
                    + " label=" + in.loadLabel(c) + " state=" + tim.getInputState(in.getId()) + cec);
        }
    }
}
