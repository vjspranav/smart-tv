package com.livingroom.flamingo.launcher.model;

import static org.junit.Assert.*;
import org.junit.Test;

public class PosterUrlTest {
    @Test public void amazonBadgeAndTextOverlaysRemoved() {
        String in = "https://images-eu.ssl-images-amazon.com/images/S/pv-target-images/d1a5._UR1920,1080_."
                + "_PIpvoutofapprecs-hvm-badge-white-bk-630,TopRight,-30,30_._ZANEW%2520MOVIE,1290,34,121,570,AmazonEmberBold,75,0,5,13_QL65_._FMjpg_.png";
        assertEquals("https://images-eu.ssl-images-amazon.com/images/S/pv-target-images/d1a5._UR1920,1080_._FMjpg_.png", PosterUrl.clean(in));
    }

    @Test public void hotstarAskedForFullWidth() {
        assertEquals("https://img1.hotstarext.com/image/upload/f_auto,w_1920/sources/r1/cms/prod/2937/1734433842937-h",
                PosterUrl.clean("https://img1.hotstarext.com/image/upload/f_auto,w_544/sources/r1/cms/prod/2937/1734433842937-h"));
    }

    @Test public void otherUrlsUnchanged() {
        String apple = "https://is1-ssl.mzstatic.com/image/thumb/QjeOvUUckvoGUutRYnuVWA/1920x1080.jpg";
        assertEquals(apple, PosterUrl.clean(apple));
        String amazonPlain = "https://images-eu.ssl-images-amazon.com/images/S/pv-target-images/3dbd._FMjpg_.jpg";
        assertEquals(amazonPlain, PosterUrl.clean(amazonPlain));
        assertNull(PosterUrl.clean(null));
    }
}
