package at.domi.tvlauncher;

import android.content.Context;

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

/** Die neuesten Releases deiner Rapper (nur Songs, keine Videos). Kommt täglich frisch von GitHub. */
final class Releases {
    static final String URL_JSON = "https://raw.githubusercontent.com/Domi916/DomiTV/main/app/src/main/assets/releases.json";

    static final class Item {
        Album album;
        long at;
    }

    static List<Item> cached(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            File f = new File(c.getFilesDir(), "releases.json");
            InputStream in = f.exists() ? new java.io.FileInputStream(f) : c.getAssets().open("releases.json");
            JSONArray a = new JSONArray(Drops.read(in));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                int r = -1;
                for (int k = 0; k < Rapper.LIST.length; k++)
                    if (Rapper.LIST[k].name.equalsIgnoreCase(o.optString("artist"))) r = k;
                if (r < 0) continue;
                Item it = new Item();
                it.album = new Album(r, o.optString("title"));
                it.album.type = o.optString("type", "single");
                it.album.cover = o.isNull("cover") ? null : o.optString("cover", null);
                JSONArray t = o.getJSONArray("tracks");
                for (int k = 0; k < t.length(); k++) it.album.tracks.add(t.getString(k));
                if (it.album.tracks.isEmpty()) continue;
                String[] p = o.getString("date").split("-");
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Vienna"));
                cal.clear();
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
                it.at = cal.getTimeInMillis();
                out.add(it);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Im Hintergrund: frische Liste holen. true, wenn sich etwas geändert hat. */
    static boolean refresh(Context c) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(URL_JSON + "?t=" + System.currentTimeMillis() / 600000).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            if (h.getResponseCode() != 200) return false;
            String s = Drops.read(h.getInputStream());
            new JSONArray(s);   // gültig?
            File f = new File(c.getFilesDir(), "releases.json");
            String old = f.exists() ? Drops.read(new java.io.FileInputStream(f)) : "";
            if (s.equals(old)) return false;
            FileOutputStream o = new FileOutputStream(f);
            o.write(s.getBytes("UTF-8"));
            o.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** "Heute" / "Gestern" / "vor 5 Tagen" / "12.09.2026" */
    static String when(long at) {
        long d = (System.currentTimeMillis() - at) / 86400000L;
        if (d <= 0) return "Heute";
        if (d == 1) return "Gestern";
        if (d < 31) return "vor " + d + " Tagen";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY);
        f.setTimeZone(TimeZone.getTimeZone("Europe/Vienna"));
        return f.format(new java.util.Date(at));
    }
}
