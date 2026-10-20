package com.livingroom.flamingo.launcher.data;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import com.livingroom.flamingo.launcher.model.AppEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installed launchable apps (TV and sideloaded), their banners, and when each was last used. */
final class Apps {
    private final Context c;
    private final PackageManager pm;
    private final Map<String, Bitmap> banners = new HashMap<>();

    Apps(Context c, final Runnable onChange) {
        this.c = c;
        this.pm = c.getPackageManager();
        ((LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE)).registerCallback(new LauncherApps.Callback() {
            @Override public void onPackageRemoved(String p, UserHandle u) { forget(p); onChange.run(); }
            @Override public void onPackageAdded(String p, UserHandle u) { forget(p); onChange.run(); }
            @Override public void onPackageChanged(String p, UserHandle u) { forget(p); onChange.run(); }
            @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { onChange.run(); }
            @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { onChange.run(); }
        });
    }

    private synchronized void forget(String pkg) { banners.remove(pkg); }

    /** Leanback apps first, then phone-style apps not already listed. Disabled apps never resolve. */
    List<AppEntry> list() {
        Map<String, AppEntry> out = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER})
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0))
                if (!out.containsKey(r.activityInfo.packageName))
                    out.put(r.activityInfo.packageName, new AppEntry(r.activityInfo.packageName,
                            r.loadLabel(pm).toString(), cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER)));
        return new ArrayList<>(out.values());
    }

    Map<String, Long> lastUsed() {
        Map<String, Long> out = new HashMap<>();
        try {
            long now = System.currentTimeMillis();
            UsageStatsManager u = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            for (UsageStats s : u.queryAndAggregateUsageStats(now - 30L * 86_400_000L, now).values())
                out.put(s.getPackageName(), s.getLastTimeUsed());
        } catch (RuntimeException ignored) { }   // no usage access: fall back to A–Z
        return out;
    }

    /** 320×180 RGB_565 banner; apps without one get their icon centred on a dark tile. */
    synchronized Bitmap banner(String pkg) {
        Bitmap b = banners.get(pkg);
        if (b != null) return b;
        try {
            Drawable d = pm.getApplicationBanner(pkg);
            if (d == null) {
                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (li != null) d = pm.getActivityBanner(li);
            }
            b = d != null ? Art.draw(d, 320, 180, 0xff16161a) : Art.draw(pm.getApplicationIcon(pkg), 320, 180, 0xff16161a);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        banners.put(pkg, b);
        return b;
    }

    boolean launch(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return Sources.start(c, i);
    }
}
