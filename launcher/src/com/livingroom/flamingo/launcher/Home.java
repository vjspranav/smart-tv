package com.livingroom.flamingo.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

public class Home extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String probe = getIntent().getStringExtra("probe");
        if (probe != null) { Probe.run(this, probe, getIntent().getIntExtra("pic", -1)); finish(); return; }
        final View v;
        if (getIntent().getBooleanExtra("kittest", false)) v = com.livingroom.flamingo.launcher.ui.Debug.kitTest(this);
        else { v = new View(this); v.setBackgroundColor(Color.BLACK); }
        setContentView(v);
        if (getIntent().getBooleanExtra("check", false))
            v.postDelayed(() -> com.livingroom.flamingo.launcher.ui.Overflow.check(v), 1500);
    }
}
