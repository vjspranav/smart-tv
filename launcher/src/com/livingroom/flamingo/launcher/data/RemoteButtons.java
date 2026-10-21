package com.livingroom.flamingo.launcher.data;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import com.livingroom.flamingo.launcher.model.RemoteKeys;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Opens apps for the remote's shortcut buttons (see RemoteKeys) by following TCL's key lines in the system log.
 * Passive: no key passes through Flamingo, so hold-to-repeat and every other key stay untouched. Needs READ_LOGS
 * (tools/grant-launcher.sh). Runs while MediaListener is bound, which the system keeps up even with an app in front.
 */
public final class RemoteButtons {
    private static final String TAG = "FlamingoKeys";
    private static Thread thread;
    private static volatile Process proc;

    private RemoteButtons() { }

    public static synchronized void start(Context c) {
        if (thread != null) return;
        Context app = c.getApplicationContext();
        thread = new Thread(() -> follow(app), "remote-buttons");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        if (thread == null) return;
        thread.interrupt();
        Process p = proc;
        if (p != null) p.destroy();
        thread = null;
    }

    private static void follow(Context c) {
        while (!Thread.currentThread().isInterrupted()) {
            // -T now: only lines from here on, so a restart never replays an old press
            String since = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            try {
                proc = new ProcessBuilder("logcat", "-T", since, "-s", "WindowManager:D").redirectErrorStream(true).start();
                BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                for (String line; (line = r.readLine()) != null; ) {
                    int k = RemoteKeys.releasedKey(line);
                    if (k >= 0 && RemoteKeys.mapped(k)) open(c, RemoteKeys.target(k));
                }
            } catch (Exception e) {
                Log.w(TAG, "log reader stopped", e);
            } finally {
                if (proc != null) proc.destroy();
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
        }
    }

    private static void open(Context c, String target) {
        Intent i;
        if (RemoteKeys.SETTINGS.equals(target)) i = new Intent(Settings.ACTION_SETTINGS);
        else {
            i = c.getPackageManager().getLeanbackLaunchIntentForPackage(target);
            if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(target);
            if (i == null) { Log.w(TAG, target + " is not installed"); return; }
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { c.startActivity(i); } catch (RuntimeException e) { Log.w(TAG, "could not open " + target, e); }
    }
}
