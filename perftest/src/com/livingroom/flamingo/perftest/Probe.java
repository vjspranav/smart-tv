package com.livingroom.flamingo.perftest;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.widget.TextView;

/** scene=P: can a normal app read TCL's picture mode through the com.tcl.tv.display shared library? */
final class Probe extends TextView implements Main.Scene {
    Probe(Context c) {
        super(c);
        setTextColor(Color.WHITE);
        setTextSize(40);
        setPadding(96, 96, 96, 96);
        String r;
        try {
            Class<?> k = Class.forName("com.tcl.tv.pq.TvPqManager");
            Object m = k.getMethod("getInstance").invoke(null);
            r = "PICTURE_MODE=" + k.getMethod("getPictureMode").invoke(m);
        } catch (Throwable t) {
            r = "PICTURE_MODE_ERROR=" + t;
        }
        Log.i("FlamingoProbe", r);
        setText(r);
    }

    @Override public void move(int d) { }
}
