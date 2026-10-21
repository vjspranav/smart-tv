package com.livingroom.flamingo.launcher.ui;

import android.graphics.RectF;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug check for the no-overflow rule, run on the TV after layout. Reports:
 *  clip    — a view that draws (with transforms and focus-ring outset) is partly cut off by the screen or a clipping ancestor
 *            (a view scrolled completely out of a clipping list is not a problem and is skipped)
 *  text    — a TextView's text is wider than its box and was not ellipsized
 *  overlap — a TextView's drawn text intersects another text, or a tile/image that isn't its ancestor
 *            (artwork tagged Kit.BACKDROP is allowed behind text)
 */
public final class Overflow {
    public static final String TAG = "FlamingoOverflow";

    private Overflow() { }

    public static List<String> check(View root) {
        List<String> out = new ArrayList<>();
        List<TextView> texts = new ArrayList<>();
        List<RectF> textRects = new ArrayList<>();
        List<View> solids = new ArrayList<>();
        List<RectF> solidRects = new ArrayList<>();
        walk(root, root, out, texts, textRects, solids, solidRects);
        for (int i = 0; i < texts.size(); i++) {
            for (int j = i + 1; j < texts.size(); j++)
                if (RectF.intersects(textRects.get(i), textRects.get(j)))
                    out.add("OVERFLOW overlap " + name(texts.get(i)) + " / " + name(texts.get(j))
                            + " " + textRects.get(i).toShortString() + " " + textRects.get(j).toShortString());
            for (int j = 0; j < solids.size(); j++) {
                View s = solids.get(j);
                if (s != texts.get(i) && !isAncestor(s, texts.get(i)) && !isAncestor(texts.get(i), s)
                        && RectF.intersects(textRects.get(i), solidRects.get(j)))
                    out.add("OVERFLOW overlap " + name(texts.get(i)) + " / " + name(s));
            }
        }
        for (String s : out) Log.w(TAG, s);
        Log.i(TAG, "DONE " + out.size());
        return out;
    }

    private static void walk(View v, View root, List<String> out, List<TextView> texts, List<RectF> textRects,
                             List<View> solids, List<RectF> solidRects) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.05f || v.getWidth() == 0 || v.getHeight() == 0) return;
        if (v != root) {
            Object o = v.getTag(Kit.OUTSET);
            float outset = o instanceof Integer ? (Integer) o : 0;
            RectF r = toRoot(v, root, new RectF(-outset, -outset, v.getWidth() + outset, v.getHeight() + outset));
            if (scrolledAway(v, root, r)) return;
            String why = draws(v) ? clipped(v, root, r) : null;
            if (why != null) out.add("OVERFLOW clip " + name(v) + " " + why);
            if ((v instanceof ImageView || v.getBackground() != null) && !Boolean.TRUE.equals(v.getTag(Kit.BACKDROP))) {
                solids.add(v);
                solidRects.add(toRoot(v, root, new RectF(0, 0, v.getWidth(), v.getHeight())));
            }
        }
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Layout l = t.getLayout();
            if (l != null && t.getText().length() > 0) {
                int avail = t.getWidth() - t.getTotalPaddingLeft() - t.getTotalPaddingRight();
                boolean ellipsized = l.getEllipsisCount(0) > 0;
                if (!ellipsized && (l.getLineCount() > t.getMaxLines() || l.getLineWidth(0) > avail + 1))
                    out.add("OVERFLOW text " + name(t));
                // Single-line layouts are laid out very wide, so place the drawn text from the gravity instead
                float w = Math.min(l.getLineWidth(0), avail), left = t.getTotalPaddingLeft();
                int hg = android.view.Gravity.getAbsoluteGravity(t.getGravity(), t.getLayoutDirection()) & android.view.Gravity.HORIZONTAL_GRAVITY_MASK;
                if (hg == android.view.Gravity.CENTER_HORIZONTAL) left += (avail - w) / 2;
                else if (hg == android.view.Gravity.RIGHT) left += avail - w;
                float top = t.getTotalPaddingTop() + l.getLineTop(0), right = left + w;
                float bottom = t.getTotalPaddingTop() + l.getLineBottom(Math.min(l.getLineCount(), t.getMaxLines()) - 1);
                texts.add(t);
                textRects.add(toRoot(t, root, new RectF(left, top, right, bottom)));
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), root, out, texts, textRects, solids, solidRects);
        }
    }

    /** Maps a rect in v's own coordinates to root coordinates, applying every transform on the way. */
    private static RectF toRoot(View v, View root, RectF r) {
        for (View cur = v; cur != root && cur != null; cur = (View) cur.getParent()) {
            if (!cur.getMatrix().isIdentity()) cur.getMatrix().mapRect(r);
            r.offset(cur.getLeft() - (cur.getParent() instanceof View ? ((View) cur.getParent()).getScrollX() : 0),
                    cur.getTop() - (cur.getParent() instanceof View ? ((View) cur.getParent()).getScrollY() : 0));
            if (!(cur.getParent() instanceof View)) break;
        }
        return r;
    }

    /** True if r lies entirely outside some clipping ancestor (e.g. a list item scrolled out of view). */
    private static boolean scrolledAway(View v, View root, RectF r) {
        for (View p = (View) v.getParent(); p != null && p != root; p = p.getParent() instanceof View ? (View) p.getParent() : null)
            if (p instanceof ViewGroup && ((ViewGroup) p).getClipChildren()
                    && !RectF.intersects(r, toRoot(p, root, new RectF(0, 0, p.getWidth(), p.getHeight())))) return true;
        return false;
    }

    private static String clipped(View v, View root, RectF r) {
        float e = 0.5f;
        if (r.left < -e || r.top < -e || r.right > root.getWidth() + e || r.bottom > root.getHeight() + e)
            return "past screen " + r.toShortString();
        for (View p = (View) v.getParent(); p != null && p != root; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p instanceof ViewGroup && ((ViewGroup) p).getClipChildren()) {
                RectF pr = toRoot(p, root, new RectF(0, 0, p.getWidth(), p.getHeight()));
                if (r.left < pr.left - e || r.top < pr.top - e || r.right > pr.right + e || r.bottom > pr.bottom + e)
                    return "clipped by " + name(p);
            }
        }
        return null;
    }

    /** Pure layout containers draw nothing, so only their children can be cut off. */
    private static boolean draws(View v) {
        return !(v instanceof ViewGroup) || v.getBackground() != null || v.getForeground() != null;
    }

    private static boolean isAncestor(View a, View v) {
        for (Object p = v.getParent(); p instanceof View; p = ((View) p).getParent()) if (p == a) return true;
        return false;
    }

    private static String name(View v) {
        String s = v.getClass().getSimpleName();
        if (v instanceof TextView) s += " \"" + ((TextView) v).getText() + "\"";
        else if (v.getContentDescription() != null) s += " [" + v.getContentDescription() + "]";
        return s;
    }
}
