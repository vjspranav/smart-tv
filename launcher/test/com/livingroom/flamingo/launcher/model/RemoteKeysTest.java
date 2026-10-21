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
}
