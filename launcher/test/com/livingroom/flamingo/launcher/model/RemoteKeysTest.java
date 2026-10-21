package com.livingroom.flamingo.launcher.model;

import static org.junit.Assert.*;
import org.junit.Test;

public class RemoteKeysTest {
    @Test public void appButtonsOpenTheirApps() {
        assertEquals("com.netflix.ninja", RemoteKeys.target(4062));
        assertEquals("com.google.android.youtube.tv", RemoteKeys.target(4065));
    }

    @Test public void settingsButtonOpensSettings() {
        assertEquals(RemoteKeys.SETTINGS, RemoteKeys.target(83));   // KEYCODE_NOTIFICATION: the TCL remote's gear
    }

    @Test public void everythingElsePassesThrough() {
        assertNull(RemoteKeys.target(4099));   // Prime: not mapped (owner: not for now)
        assertNull(RemoteKeys.target(19));     // D-pad up
        assertNull(RemoteKeys.target(3));      // Home
    }

    // Real lines from this TV's log (TCL's WindowManager policy logs every remote key)
    private static final String NETFLIX_UP = "10-21 22:43:13.251 D/WindowManager(  717): handleKeyBeforeQueueing event=KeyEvent { action=ACTION_UP, keyCode=4062, scanCode=566, metaState=0, flags=0x8, repeatCount=0, eventTime=982039, downTime=981754, deviceId=2, source=0x301, displayId=-1 }";
    private static final String SETTINGS_UP = "10-21 22:43:11.940 D/WindowManager(  717): handleKeyBeforeQueueing event=KeyEvent { action=ACTION_UP, keyCode=KEYCODE_NOTIFICATION, scanCode=561, metaState=0, flags=0x8, repeatCount=0, eventTime=980729, downTime=980443, deviceId=2, source=0x301, displayId=-1 }";

    @Test public void readsTheReleasedKeyFromTheLog() {
        assertEquals(4062, RemoteKeys.releasedKey(NETFLIX_UP));
        assertEquals(83, RemoteKeys.releasedKey(SETTINGS_UP));
    }

    @Test public void ignoresPressesRepeatsAndOtherLines() {
        assertEquals(-1, RemoteKeys.releasedKey(NETFLIX_UP.replace("ACTION_UP", "ACTION_DOWN")));
        assertEquals(-1, RemoteKeys.releasedKey(NETFLIX_UP.replace("flags=0x8", "flags=0x28")));   // FLAG_CANCELED
        assertEquals(-1, RemoteKeys.releasedKey("10-21 22:43:11.657 I/TclPhoneWindowManagerEx(  717): mTclShortCutKeyManager.handleShortCutKey"));
        assertEquals(-1, RemoteKeys.releasedKey("handleKeyBeforeQueueing event=KeyEvent { action=ACTION_UP, keyCode=KEYCODE_DPAD_UP, scanCode=103,"));
        assertEquals(-1, RemoteKeys.releasedKey(null));
    }
}
