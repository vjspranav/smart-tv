package com.livingroom.bridge;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

/**
 * Invisible keyboard used only while someone types from the phone. The Pi switches to it,
 * edits the focused TV text field through it, then switches back to the TV's own keyboard.
 */
public class Keyboard extends InputMethodService {
    static volatile Keyboard instance;
    private volatile EditorInfo editor;

    @Override public void onCreate() { super.onCreate(); instance = this; }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }
    @Override public boolean onEvaluateInputViewShown() { return false; }
    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        editor = attribute;
    }

    @Override public void onFinishInput() {
        editor = null;
        super.onFinishInput();
    }

    boolean active() {
        EditorInfo e = editor;
        return e != null && e.inputType != 0 && getCurrentInputConnection() != null;
    }

    String hint() {
        EditorInfo e = editor;
        return e == null || e.hintText == null ? null : e.hintText.toString();
    }

    boolean password() {
        EditorInfo e = editor;
        if (e == null) return false;
        int variation = e.inputType & EditorInfo.TYPE_MASK_VARIATION;
        return variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    String text() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return null;
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et != null && et.text != null) return et.text.toString();
        CharSequence before = ic.getTextBeforeCursor(2000, 0), after = ic.getTextAfterCursor(2000, 0);
        return (before == null ? "" : before.toString()) + (after == null ? "" : after.toString());
    }

    /** Replace the whole field with the phone's text. */
    boolean setText(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        String current = text();
        ic.beginBatchEdit();
        ic.finishComposingText();
        int length = current == null ? 0 : current.length();
        ic.setSelection(0, length);
        if (length > 0 && value.isEmpty()) ic.commitText("", 1);
        ic.commitText(value, 1);
        ic.endBatchEdit();
        return true;
    }

    /** Press the field's own action (Search / Go / Done), or Enter. */
    boolean submit() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo e = editor;
        if (ic == null) return false;
        int action = e == null ? EditorInfo.IME_ACTION_UNSPECIFIED : e.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            return ic.performEditorAction(action);
        }
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        return true;
    }
}
