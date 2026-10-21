package com.livingroom.flamingo.launcher.model;

/** Asks image CDNs for the clean, full-size artwork behind an app's Watch Next poster. */
public final class PosterUrl {
    private PosterUrl() { }

    public static String clean(String url) {
        if (url == null) return null;
        if (url.contains("images-amazon.com")) return url.replaceAll("\\._(?:PI|ZA)[^.]*?_(?=\\.)", "");   // badge / text overlays
        if (url.contains("hotstarext.com")) return url.replaceFirst("([,/])w_\\d+", "$1w_1920");
        return url;
    }
}
