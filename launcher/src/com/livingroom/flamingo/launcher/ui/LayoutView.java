package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.widget.FrameLayout;
import com.livingroom.flamingo.launcher.data.Sources;

/** What Home needs from a layout. Each layout draws Home and Everything in its own style from the same Screen. */
public abstract class LayoutView extends FrameLayout {
    protected final Sources src;
    protected final Runnable openHomeSettings;
    protected Screen screen;

    protected LayoutView(Context c, Sources src, Runnable openHomeSettings) {
        super(c);
        this.src = src;
        this.openHomeSettings = openHomeSettings;
        setClipChildren(false);
    }

    /** Show s; rebuild only what changed. */
    public abstract void bind(Screen s);

    /** A D-pad / OK / Back key press; false if not handled. */
    public abstract boolean key(int keycode);

    public abstract void showEverything(boolean on);

    public abstract boolean inEverything();

    /** Debug and Home-button: focus the first ("first") or last ("last") item of the visible screen. */
    public abstract void focus(String which);
}
