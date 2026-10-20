package com.livingroom.flamingo.perftest;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/**
 * Picks a scene from intent extras: scene=A|G|N, mode=layout|transform (G), record=on|off (N).
 * Focus is handled here, not by Android's focus search, so every scene moves the same way.
 */
public class Main extends Activity {
    interface Scene { void move(int delta); }

    private Scene scene;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String s = getIntent().getStringExtra("scene");
        View v;
        if ("G".equals(s)) v = new SceneG(this, "layout".equals(getIntent().getStringExtra("mode")));
        else if ("P".equals(s)) v = new Probe(this);
        else if ("N".equals(s)) v = new SceneN(this, getWindow(), !"off".equals(getIntent().getStringExtra("record")));
        else v = new SceneA(this);
        scene = (Scene) v;
        setContentView(v);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        int d = k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        if (d == 0) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) scene.move(d);
        return true;
    }
}
