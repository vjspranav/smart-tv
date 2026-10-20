package com.livingroom.flamingo.launcher.model;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public class HomeConfigTest {
    @Test public void defaults() {
        HomeConfig c = HomeConfig.defaults();
        assertEquals(HomeConfig.Layout.A, c.layout);
        assertEquals(Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja", "in.startv.hotstar",
                "com.amazon.amazonvideo.livingroom", "hdmi:1"), c.main);
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
}
