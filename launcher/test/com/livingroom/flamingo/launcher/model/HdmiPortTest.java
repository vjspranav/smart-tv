package com.livingroom.flamingo.launcher.model;

import static org.junit.Assert.*;
import org.junit.Test;

public class HdmiPortTest {
    @Test public void userNameBeatsCecBeatsDefault() {
        assertEquals("Console", HdmiPort.resolve(1, "id", "Console", "PlayStation 5", 1).name);
        assertEquals("PlayStation 5", HdmiPort.resolve(1, "id", null, "PlayStation 5", 1).name);
        assertEquals("HDMI 2", HdmiPort.resolve(2, "id", null, null, 1).name);
        assertEquals("HDMI 2", HdmiPort.resolve(2, "id", "  ", null, 1).name);
    }

    @Test public void states() {
        assertEquals(HdmiPort.State.ON, HdmiPort.resolve(1, "id", null, "PlayStation 5", 0).state);
        assertEquals(HdmiPort.State.STANDBY, HdmiPort.resolve(1, "id", null, "PlayStation 5", 1).state);
        assertEquals(HdmiPort.State.EMPTY, HdmiPort.resolve(2, "id", null, null, 1).state);
        assertEquals(HdmiPort.State.EMPTY, HdmiPort.resolve(2, "id", null, null, null).state);
    }
}
