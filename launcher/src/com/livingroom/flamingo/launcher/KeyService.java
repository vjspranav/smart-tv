package com.livingroom.flamingo.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import com.livingroom.flamingo.launcher.model.RemoteKeys;

/**
 * Makes the remote's shortcut buttons work again (see RemoteKeys). Runs in its own small process (":keys") so a
 * busy Home screen can never delay a key: every key passes through here, and all but the mapped few are released
 * at once. Debug: `setprop log.tag.FlamingoKeys DEBUG` logs how long each key waited before reaching us.
 */
public final class KeyService extends AccessibilityService {
    private static final String TAG = "FlamingoKeys";

    @Override protected boolean onKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (Log.isLoggable(TAG, Log.DEBUG))
            Log.d(TAG, "key " + k + " " + (e.getAction() == KeyEvent.ACTION_DOWN ? "down" : "up")
                    + " waited " + (SystemClock.uptimeMillis() - e.getEventTime()) + " ms");
        if (!RemoteKeys.mapped(k)) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) open(RemoteKeys.target(k));
        return true;   // down and up both consumed, so TCL's own (now dead) handler never sees a half press
    }

    private void open(String target) {
        Intent i;
        if (RemoteKeys.SETTINGS.equals(target)) i = new Intent(Settings.ACTION_SETTINGS);
        else {
            i = getPackageManager().getLeanbackLaunchIntentForPackage(target);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(target);
            if (i == null) { Log.w(TAG, target + " is not installed"); return; }
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { startActivity(i); } catch (RuntimeException ex) { Log.w(TAG, "could not open " + target, ex); }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }
}
