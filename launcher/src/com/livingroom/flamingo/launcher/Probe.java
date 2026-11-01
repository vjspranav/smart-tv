package com.livingroom.flamingo.launcher;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.util.Log;

/** Read-only diagnostics. Picture settings are owned by TCL, including for debug intents. */
final class Probe {
    private static final String TAG = "FlamingoProbe";

    static void run(Context c, String what) {
        if (!"read".equals(what)) {
            Log.w(TAG, "Unsupported read-only probe: " + what);
            return;
        }
        watchNext(c);
        picture();
        inputs(c);
    }

    private static void watchNext(Context c) {
        String[] cols = {TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME, TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO};
        try (Cursor q = c.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null, null)) {
            Log.i(TAG, "WATCHNEXT_ROWS=" + (q == null ? "null" : q.getCount()));
            for (int i = 0; q != null && i < 20 && q.moveToNext(); i++)
                Log.i(TAG, "WATCHNEXT " + q.getString(0) + " | " + q.getString(1) + " | " + q.getString(2) + " | " + q.getLong(3) + " | " + q.getLong(4)
                        + " | poster=" + q.getString(5) + " | thumb=" + q.getString(6) + " | aspect=" + q.getString(7));
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
