package com.livingroom.flamingo.perftest;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

public class Main extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        View v = new View(this);
        v.setBackgroundColor(Color.BLACK);
        setContentView(v);
    }
}
