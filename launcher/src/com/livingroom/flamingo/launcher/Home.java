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
        View v = new View(this);
        v.setBackgroundColor(Color.BLACK);
        setContentView(v);
    }
}
