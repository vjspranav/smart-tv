package com.livingroom.bridge;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * Reads the foreground app's on-screen elements and presses player buttons (captions, quality, audio)
 * on request from the Pi. It reacts to nothing by itself.
 */
public class A11y extends AccessibilityService {
    static volatile A11y instance;

    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    private AccessibilityNodeInfo root() {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        if (r != null) return r;
        List<AccessibilityWindowInfo> windows = getWindows();
        for (AccessibilityWindowInfo w : windows) if (w.isActive() && w.getRoot() != null) return w.getRoot();
        return null;
    }

    /** JSON dump of the visible element tree (for mapping an app's player). */
    String dump() {
        AccessibilityNodeInfo r = root();
        if (r == null) return "{\"ok\":false,\"error\":\"no active window\"}";
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"package\":").append(Listener.quoteJson(String.valueOf(r.getPackageName()))).append(",\"nodes\":[");
        int[] count = {0};
        walk(r, 0, sb, count);
        return sb.append("]}").toString();
    }

    private void walk(AccessibilityNodeInfo n, int depth, StringBuilder sb, int[] count) {
        if (n == null || count[0] > 1500 || depth > 40) return;
        CharSequence text = n.getText(), desc = n.getContentDescription();
        String id = n.getViewIdResourceName();
        boolean useful = text != null || desc != null || id != null || n.isClickable() || n.isFocusable();
        if (useful && n.isVisibleToUser()) {
            Rect b = new Rect(); n.getBoundsInScreen(b);
            if (count[0]++ > 0) sb.append(',');
            sb.append("{\"d\":").append(depth)
              .append(",\"cls\":").append(Listener.quoteJson(String.valueOf(n.getClassName())))
              .append(",\"id\":").append(Listener.quoteJson(id))
              .append(",\"text\":").append(Listener.quoteJson(text == null ? null : text.toString()))
              .append(",\"desc\":").append(Listener.quoteJson(desc == null ? null : desc.toString()))
              .append(",\"click\":").append(n.isClickable())
              .append(",\"focus\":").append(n.isFocused())
              .append(",\"checked\":").append(n.isCheckable() ? String.valueOf(n.isChecked()) : "null")
              .append(",\"selected\":").append(n.isSelected())
              .append(",\"b\":[").append(b.left).append(',').append(b.top).append(',').append(b.right).append(',').append(b.bottom).append("]}");
        }
        for (int i = 0; i < n.getChildCount(); i++) walk(n.getChild(i), depth + 1, sb, count);
    }

    /** Press the first visible element whose id, text or description matches (case-insensitive). */
    String press(String id, String label) {
        AccessibilityNodeInfo r = root();
        if (r == null) return "no active window";
        AccessibilityNodeInfo hit = find(r, id, label == null ? null : label.toLowerCase());
        if (hit == null) return "not on screen";
        AccessibilityNodeInfo target = hit;
        while (target != null && !target.isClickable()) target = target.getParent();
        if (target == null) target = hit;
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK) ? null : "the app ignored the press";
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo n, String id, String label) {
        if (n == null) return null;
        if (n.isVisibleToUser()) {
            if (id != null && id.equals(n.getViewIdResourceName())) return n;
            if (label != null) {
                CharSequence t = n.getText(), d = n.getContentDescription();
                if ((t != null && t.toString().toLowerCase().contains(label)) || (d != null && d.toString().toLowerCase().contains(label))) return n;
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo hit = find(n.getChild(i), id, label);
            if (hit != null) return hit;
        }
        return null;
    }
}
