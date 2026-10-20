package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.widget.FrameLayout;
import com.livingroom.flamingo.launcher.data.Sources;
import com.livingroom.flamingo.launcher.model.HomeConfig;

/** Placeholder until Task 11. */
public final class SettingsView extends FrameLayout {
    private final Runnable close;

    public SettingsView(Context c, Sources src, HomeConfig.Layout style, Runnable close) {
        super(c);
        this.close = close;
        setBackgroundColor(0xff000000);
    }

    public void bind(HomeConfig cfg, Screen s) { }
    public boolean key(int k) { if (k == android.view.KeyEvent.KEYCODE_BACK) { close.run(); return true; } return true; }
    public void focus(String which) { }
}
