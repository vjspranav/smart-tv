package com.livingroom.flamingo.launcher.model;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public class HomeConfigTest {
    @Test public void defaults() {
        HomeConfig c = HomeConfig.defaults();
        assertEquals(HomeConfig.Layout.A, c.layout);
        assertEquals(Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja", "in.startv.hotstar",
                "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv"), c.main);
        assertEquals("PlayStation 5", c.hdmiNames.get(1));
    }

    @Test public void roundTrip() {
        HomeConfig c = HomeConfig.defaults();
        c.layout = HomeConfig.Layout.N;
        c.main = Arrays.asList("com.netflix.ninja", "hdmi:2");
        c.hdmiNames.put(2, "Apple TV");
        HomeConfig r = HomeConfig.parse(c.serialize());
        assertEquals(HomeConfig.Layout.N, r.layout);
        assertEquals(c.main, r.main);
        assertEquals("Apple TV", r.hdmiNames.get(2));
        assertEquals("PlayStation 5", r.hdmiNames.get(1));
    }

    @Test public void hiddenAndAzRoundTrip() {
        HomeConfig c = HomeConfig.defaults();
        assertTrue(c.hidden.contains("com.tcl.dashboard"));
        assertTrue(c.hidden.contains("com.tcl.tv.tclhome_passive"));
        assertFalse(c.appsAz);            // All apps opens by group unless the owner switches to A–Z
        c.hidden.clear();
        c.appsAz = true;
        HomeConfig r = HomeConfig.parse(c.serialize());
        assertTrue(r.hidden.isEmpty());   // an emptied list stays empty, not back to the defaults
        assertTrue(r.appsAz);
    }

    @Test public void awkwardNamesSurvive() {
        HomeConfig c = HomeConfig.defaults();
        c.hdmiNames.put(3, "a=b\nc\\d");
        assertEquals("a=b\nc\\d", HomeConfig.parse(c.serialize()).hdmiNames.get(3));
    }

    @Test public void emptyAndGarbageGiveDefaults() {
        assertEquals(HomeConfig.defaults().serialize(), HomeConfig.parse("").serialize());
        assertEquals(HomeConfig.defaults().serialize(), HomeConfig.parse("garbage=\u0000\nlayout=Q").serialize());
        assertEquals(HomeConfig.defaults().serialize(), HomeConfig.parse(null).serialize());
    }

    @Test public void moveMainShiftsOneStep() {
        HomeConfig c = HomeConfig.defaults();
        assertEquals(1, c.moveMain(0, 1));
        assertEquals("com.netflix.ninja", c.main.get(0));
        assertEquals("com.google.android.youtube.tv", c.main.get(1));
        assertEquals(0, c.moveMain(0, -1));   // already first: stays
        assertEquals(4, c.moveMain(4, 1));    // already last: stays
    }

    @Test public void replaceMainSwapsWhenAlreadyOnTheRow() {
        HomeConfig c = HomeConfig.defaults();
        c.replaceMain(0, "hdmi:1");
        assertEquals("hdmi:1", c.main.get(0));
        c.replaceMain(1, "hdmi:1");             // already in slot 0: the two trade places
        assertEquals("hdmi:1", c.main.get(1));
        assertEquals("com.netflix.ninja", c.main.get(0));
        assertEquals(5, c.main.size());
    }

    @Test public void blankHdmiNameFallsBackToDefault() {
        HomeConfig c = HomeConfig.defaults();
        c.setHdmiName(2, "  Xbox  ");
        assertEquals("Xbox", c.hdmiNames.get(2));
        c.setHdmiName(2, "   ");
        assertFalse(c.hdmiNames.containsKey(2));
        c.setHdmiName(1, null);
        assertFalse(c.hdmiNames.containsKey(1));
        c.setHdmiName(3, "A\nB");
        assertEquals("A\nB", HomeConfig.parse(c.serialize()).hdmiNames.get(3));
    }
}
