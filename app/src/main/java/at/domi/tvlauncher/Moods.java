package at.domi.tvlauncher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Stimmungs-Shuffle: alle Songs deiner Rapper nach Tempo/Energie sortiert (Daten: assets/moods.json, von GitHub). */
final class Moods {
    static final String[] KEYS = {"rage", "chill", "nacht", "auto"};
    static final String[] LABEL = {"Rage", "Chill", "Nachts", "Auto fahren"};
    static final String[] SUB = {"Hart, laut, schnell", "Runterkommen", "Dunkel und ruhig", "Mitnicken, Fenster runter"};
    static final int[] C1 = {0xffff3d1f, 0xff2fd6c3, 0xff6a5cff, 0xffffb02e};
    static final int[] C2 = {0xff2a0500, 0xff021a18, 0xff0a0626, 0xff241500};

    static JSONObject data;

    static JSONObject load(Context c) {
        if (data != null) return data;
        try {
            InputStream in = c.getAssets().open("moods.json");
            byte[] b = new byte[in.available()];
            int n = 0;
            while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
            in.close();
            data = new JSONObject(new String(b, 0, n, "UTF-8"));
        } catch (Exception e) {
            data = new JSONObject();
        }
        return data;
    }

    static int index(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return 0;
    }

    static List<String> songs(Context c, String key) {
        List<String> out = new ArrayList<>();
        JSONArray a = load(c).optJSONArray(key);
        Set<String> blocked = Library.blocked(c);
        if (a != null) for (int i = 0; i < a.length(); i++) {
            String id = a.optString(i);
            if (id.length() == 11 && !blocked.contains(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    /** Tempo des Songs (110–200 BPM) oder 0, wenn unbekannt. */
    static int bpm(Context c, String id) {
        JSONObject b = load(c).optJSONObject("bpm");
        return b == null || id == null ? 0 : b.optInt(id);
    }
}
