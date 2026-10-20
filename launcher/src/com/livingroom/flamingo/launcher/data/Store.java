package com.livingroom.flamingo.launcher.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Small files in the app's private storage: config.txt, history.txt, art/<key>.png. Written atomically. */
public final class Store {
    private final File dir, art;

    public Store(Context c) {
        dir = c.getFilesDir();
        art = new File(dir, "art");
        art.mkdirs();
    }

    public String read(String name) {
        File f = new File(dir, name);
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public void write(String name, String text) {
        File tmp = new File(dir, name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            return;
        }
        tmp.renameTo(new File(dir, name));
    }

    /** Saves art as PNG under art/<key>.png and returns that relative path. */
    public String saveArt(String key, Bitmap b) {
        String rel = "art/" + key.replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
        File tmp = new File(dir, rel + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            return null;
        }
        tmp.renameTo(new File(dir, rel));
        return rel;
    }

    public Bitmap loadArt(String rel) {
        if (rel == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(new File(dir, rel).getPath(), o);
    }
}
