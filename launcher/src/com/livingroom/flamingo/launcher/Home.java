package com.livingroom.flamingo.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

public class Home extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        View v = new View(this);
        v.setBackgroundColor(Color.BLACK);
        setContentView(v);
    }
}
