package com.livingroom.flamingo.launcher.model;

import static org.junit.Assert.*;
import org.junit.Test;

public class HistoryTest {
    static MediaItem item(String pkg, String title, long pos, long dur, long at, boolean playing) {
        MediaItem m = new MediaItem();
        m.pkg = pkg; m.title = title; m.subtitle = "sub"; m.positionMs = pos; m.durationMs = dur;
        m.updatedAt = at; m.playing = playing;
        return m;
    }

    @Test public void storesLatestPerApp() {
        History h = new History("self");
        assertTrue(h.record(item("yt", "A", 0, 600_000, 1, true)));
        assertTrue(h.record(item("yt", "B", 0, 600_000, 2, true)));
        assertTrue(h.record(item("nf", "C", 0, 600_000, 3, true)));
        assertEquals("B", h.latest("yt").title);
        assertEquals("C", h.latest("nf").title);
        assertNull(h.latest("prime"));
    }

    @Test public void ignoresShortClipsTrailersAndSelf() {
        History h = new History("self");
        assertFalse(h.record(item("yt", "Ad", 0, 30_000, 1, true)));
        assertFalse(h.record(item("nf", "Official Trailer", 0, 0, 1, true)));
        assertFalse(h.record(item("nf", "Season 2 teaser", 0, 0, 1, true)));
        assertFalse(h.record(item("self", "x", 0, 0, 1, true)));
        assertTrue(h.record(item("yt", "Live stream", 0, 0, 1, true)));   // unknown duration is fine
    }

    @Test public void nullTitleKeptAsNull() {
        History h = new History("self");
        assertTrue(h.record(item("nf", null, 0, 0, 1, true)));
        assertNull(h.latest("nf").title);
        assertTrue(h.record(item("nf", "  ", 0, 0, 2, true)));
        assertNull(h.latest("nf").title);
    }

    @Test public void pauseKeepsPosition() {
        History h = new History("self");
        h.record(item("sp", "Song", 30_000, 200_000, 1, true));
        h.record(item("sp", "Song", 0, 200_000, 2, false));   // paused update with no position
        assertEquals(30_000, h.latest("sp").positionMs);
        assertFalse(h.latest("sp").playing);
    }

    @Test public void roundTrip() {
        History h = new History("self");
        MediaItem m = item("nf", "Tab\there\nnewline", 5, 600_000, 7, false);
        m.artPath = "art/nf.png"; m.intentUri = "intent://x#Intent;end";
        h.record(m);
        h.record(item("yt", null, 0, 0, 8, true));
        History r = History.parse(h.serialize(), "self");
        assertEquals("Tab\there\nnewline", r.latest("nf").title);
        assertEquals("intent://x#Intent;end", r.latest("nf").intentUri);
        assertEquals("art/nf.png", r.latest("nf").artPath);
        assertEquals(600_000, r.latest("nf").durationMs);
        assertNull(r.latest("yt").title);
        assertTrue(r.latest("yt").playing);
    }

    @Test public void corruptParseIsEmpty() {
        assertNull(History.parse("x\ty\n\u0000", "self").latest("x"));
        assertNull(History.parse(null, "self").latest("x"));
    }

    @Test public void continuePrefersTitledThenRecent() {
        MediaItem session = item("nf", null, 0, 0, 100, true);
        MediaItem watchNext = item("nf", "Below", 0, 2_855_000, 50, false);
        assertEquals("Below", MediaItem.pick(session, watchNext).title);
        MediaItem newerTitled = item("nf", "Gilmore Girls", 0, 0, 200, true);
        assertEquals("Gilmore Girls", MediaItem.pick(newerTitled, watchNext).title);
        assertSame(watchNext, MediaItem.pick(null, watchNext));
        assertNull(MediaItem.pick(null, null));
    }
}
