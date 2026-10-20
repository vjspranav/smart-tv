package com.livingroom.flamingo.launcher.ui;

import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;
import java.util.List;

/** One focus rule for every layout: move to the nearest item in the pressed direction (layout positions, no transforms). */
public final class Nav {
    private Nav() { }

    /** Index of the best candidate from `from` in the direction of the D-pad key, or -1. */
    public static int find(List<View> items, int from, int keycode, View root) {
        if (from < 0 || from >= items.size()) return items.isEmpty() ? -1 : 0;
        RectF a = rect(items.get(from), root);
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            if (i == from || items.get(i).getVisibility() != View.VISIBLE) continue;
            RectF b = rect(items.get(i), root);
            float primary, ortho;
            switch (keycode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT: primary = b.left - a.right + 1; ortho = Math.abs(b.centerY() - a.centerY()); break;
                case KeyEvent.KEYCODE_DPAD_LEFT: primary = a.left - b.right + 1; ortho = Math.abs(b.centerY() - a.centerY()); break;
                case KeyEvent.KEYCODE_DPAD_DOWN: primary = b.top - a.bottom + 1; ortho = Math.abs(b.centerX() - a.centerX()); break;
                case KeyEvent.KEYCODE_DPAD_UP: primary = a.top - b.bottom + 1; ortho = Math.abs(b.centerX() - a.centerX()); break;
                default: return -1;
            }
            if (primary <= 0) continue;
            float score = primary + 2.5f * ortho;
            if (score < bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    /** v's layout rect in root coordinates, ignoring transient transforms (scale, scroll animations). */
    static RectF rect(View v, View root) {
        RectF r = new RectF(0, 0, v.getWidth(), v.getHeight());
        for (View cur = v; cur != null && cur != root; cur = cur.getParent() instanceof View ? (View) cur.getParent() : null)
            r.offset(cur.getLeft(), cur.getTop());
        return r;
    }
}
