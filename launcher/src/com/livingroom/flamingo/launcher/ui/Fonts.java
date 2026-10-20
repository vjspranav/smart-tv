package com.livingroom.flamingo.launcher.ui;

import android.content.Context;
import android.graphics.Typeface;
import java.util.HashMap;
import java.util.Map;

/** Bundled OFL fonts. INTER and DOTO are variable fonts: pass the weight (100–900). */
public final class Fonts {
    public static final String INTER = "fonts/InterTight.ttf", DOTO = "fonts/Doto.ttf",
            SERIF = "fonts/InstrumentSerif-Regular.ttf", SERIF_ITALIC = "fonts/InstrumentSerif-Italic.ttf",
            MONO = "fonts/SpaceMono-Regular.ttf", MONO_BOLD = "fonts/SpaceMono-Bold.ttf";
    private static final Map<String, Typeface> CACHE = new HashMap<>();

    private Fonts() { }

    public static Typeface get(Context c, String file, int weight) {
        String key = file + '@' + weight;
        Typeface t = CACHE.get(key);
        if (t == null) {
            boolean variable = file.equals(INTER) || file.equals(DOTO);
            Typeface.Builder b = new Typeface.Builder(c.getAssets(), file);
            if (variable) b.setFontVariationSettings("'wght' " + weight);
            t = b.build();
            if (t == null) t = Typeface.DEFAULT;
            CACHE.put(key, t);
        }
        return t;
    }

    public static Typeface get(Context c, String file) { return get(c, file, 400); }
}
