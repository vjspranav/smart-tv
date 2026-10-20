package com.livingroom.flamingo.launcher.data;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.livingroom.flamingo.launcher.model.HdmiPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HDMI inputs from TvInputManager. Top-level HDMI inputs ("HDMI 1".."HDMI 3") are the ports; a CEC device
 * shows up as a child input whose parent is its port and whose label is the device's own name ("PlayStation 5").
 */
final class Hdmi {
    private final Context c;
    private final TvInputManager tim;

    Hdmi(Context c, final Runnable onChange) {
        this.c = c;
        this.tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        tim.registerCallback(new TvInputManager.TvInputCallback() {
            @Override public void onInputStateChanged(String id, int state) { onChange.run(); }
            @Override public void onInputAdded(String id) { onChange.run(); }
            @Override public void onInputRemoved(String id) { onChange.run(); }
        }, new Handler(Looper.getMainLooper()));
    }

    List<HdmiPort> ports(Map<Integer, String> userNames) {
        Map<String, Integer> portOf = new HashMap<>();
        Map<Integer, String> cec = new HashMap<>();
        List<TvInputInfo> all = tim.getTvInputList();
        for (TvInputInfo in : all) {
            if (in.getType() != TvInputInfo.TYPE_HDMI || in.getParentId() != null) continue;
            int n = number(in.loadLabel(c).toString());
            if (n > 0) portOf.put(in.getId(), n);
        }
        for (TvInputInfo in : all)
            if (in.getParentId() != null && portOf.containsKey(in.getParentId()))
                cec.put(portOf.get(in.getParentId()), in.loadLabel(c).toString());
        List<HdmiPort> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : portOf.entrySet())
            out.add(HdmiPort.resolve(e.getValue(), e.getKey(), userNames.get(e.getValue()), cec.get(e.getValue()), state(e.getKey())));
        Collections.sort(out, (a, b) -> a.port - b.port);
        return out;
    }

    private Integer state(String id) {
        try { return tim.getInputState(id); } catch (RuntimeException e) { return null; }
    }

    private static int number(String label) {
        String d = label.replaceAll("[^0-9]", "");
        try { return d.isEmpty() ? -1 : Integer.parseInt(d); } catch (NumberFormatException e) { return -1; }
    }

    boolean open(HdmiPort p) {
        return Sources.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("content://android.media.tv/passthrough/" + Uri.encode(p.inputId))));
    }
}
