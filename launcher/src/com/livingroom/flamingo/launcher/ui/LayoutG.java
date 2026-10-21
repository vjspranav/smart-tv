package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import com.livingroom.flamingo.launcher.data.Sources;

/** Placeholder until its own task: behaves like A. */
public final class LayoutG extends LayoutView {
    private final LayoutA a;

    public LayoutG(Context c, Sources src, Runnable openHomeSettings, Runnable openAllApps) {
        super(c, src, openHomeSettings, openAllApps);
        a = new LayoutA(c, src, openHomeSettings, openAllApps);
        addView(a, new LayoutParams(-1, -1));
    }

    @Override public void bind(Screen s) { a.bind(s); }
    @Override public boolean key(int k) { return a.key(k); }
    @Override public void showEverything(boolean on) { a.showEverything(on); }
    @Override public boolean inEverything() { return a.inEverything(); }
    @Override public void focus(String w) { a.focus(w); }
}
