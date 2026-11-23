package at.domi.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Angekündigte Releases deiner Rapper (Countdown). Die Liste kommt jeden Tag frisch von GitHub,
 * damit ein neuer Drop auch ohne App-Update auftaucht. Ohne Internet: die mitgelieferte Liste.
 */
final class Drops {
    static final String URL_JSON = "https://raw.githubusercontent.com/Domi916/DomiTV/main/app/src/main/assets/drops.json";

    static final class Drop {
        String artist, title, cover, type;
        int rapper;
        long at;              // Release: 0:00 Uhr österreichische Zeit
        List<String> tracks = new ArrayList<>();

        boolean out() { return System.currentTimeMillis() >= at; }

        String key() { return MediaWatcher.norm(artist + " " + title); }
    }

    static List<Drop> parse(String json) {
        List<Drop> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Drop d = new Drop();
                d.artist = o.optString("artist");
                d.title = o.optString("title");
                d.cover = o.isNull("cover") ? null : o.optString("cover", null);
                d.type = o.optString("type", "album");
                d.rapper = Library.rapperIndexByName(d.artist);
                if (d.rapper < 0) continue;
                String[] p = o.optString("date").split("-");
                Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Vienna"));
                c.clear();
                c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
                d.at = c.getTimeInMillis();
                JSONArray t = o.optJSONArray("tracks");
                if (t != null) for (int k = 0; k < t.length(); k++) d.tracks.add(t.getString(k));
                // nach dem Release noch 3 Tage zeigen
                if (System.currentTimeMillis() - d.at < 3 * 86400000L) out.add(d);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Gemerkte Liste (aus dem Netz, sonst aus der App). */
    static List<Drop> cached(Context c) {
        File f = new File(c.getFilesDir(), "drops.json");
        try {
            InputStream in = f.exists() ? new java.io.FileInputStream(f) : c.getAssets().open("drops.json");
            return parse(read(in));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Im Hintergrund: frische Liste + Cover holen. true, wenn sich etwas geändert hat. */
    static boolean refresh(Context c) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(URL_JSON + "?t=" + System.currentTimeMillis() / 600000).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            if (h.getResponseCode() != 200) return false;
            String s = read(h.getInputStream());
            new JSONArray(s);   // gültig?
            File f = new File(c.getFilesDir(), "drops.json");
            String old = f.exists() ? read(new java.io.FileInputStream(f)) : "";
            for (Drop d : parse(s)) cover(c, d);
            if (s.equals(old)) return false;
            FileOutputStream o = new FileOutputStream(f);
            o.write(s.getBytes("UTF-8"));
            o.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Cover vom Drop (einmal laden, dann aus dem Speicher). */
    static Bitmap cover(Context c, Drop d) {
        if (d.cover == null || d.cover.isEmpty()) return null;
        File f = new File(c.getCacheDir(), "drop_" + Integer.toHexString(d.cover.hashCode()) + ".jpg");
        try {
            if (!f.exists()) {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return null;
                HttpURLConnection h = (HttpURLConnection) new URL(d.cover).openConnection();
                h.setConnectTimeout(8000);
                h.setReadTimeout(10000);
                InputStream in = h.getInputStream();
                FileOutputStream o = new FileOutputStream(f);
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
                o.close();
                in.close();
            }
            BitmapFactory.Options op = new BitmapFactory.Options();
            op.inSampleSize = 2;
            return BitmapFactory.decodeFile(f.getPath(), op);
        } catch (Exception e) {
            return null;
        }
    }

    static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /** "in 3 T 14 Std" / "in 5 Std 20 Min" / "Jetzt da" */
    static String countdown(Drop d) {
        long ms = d.at - System.currentTimeMillis();
        if (ms <= 0) return "Jetzt da";
        long min = ms / 60000, h = min / 60, t = h / 24;
        if (t >= 1) return "in " + t + " T " + (h % 24) + " Std";
        if (h >= 1) return "in " + h + " Std " + (min % 60) + " Min";
        return "in " + Math.max(1, min) + " Min";
    }
}
