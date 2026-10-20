package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.view.View;

/** Entry points for debug-only screens. */
public final class Debug {
    private Debug() { }

    public static View kitTest(Context c) { return KitTest.build(c); }
}
