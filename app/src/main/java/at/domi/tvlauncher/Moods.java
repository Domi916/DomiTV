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
    static final String[] KEYS = {"rage", "chill", "nacht", "auto", "like", "daily"};
    static final String[] LABEL = {"Rage", "Chill", "Nachts", "Auto fahren", "Mehr wie das", "Tagesmix"};
    static final String[] SUB = {"Hart, laut, schnell", "Runterkommen", "Dunkel und ruhig", "Mitnicken, Fenster runter", "Ähnliche Songs", "Jeden Tag neu"};
    static final int[] C1 = {0xffff3d1f, 0xff2fd6c3, 0xff6a5cff, 0xffffb02e, 0xffff5c8a, 0xff4fc3ff};
    static final int[] C2 = {0xff2a0500, 0xff021a18, 0xff0a0626, 0xff241500, 0xff22040f, 0xff031826};
    /** Karten in der Stimmungs-Reihe (ohne "Mehr wie das"). */
    static final int ROW = 4;

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

    /** Songs, die ähnlich klingen wie {@code id}: gleiche Stimmungen + ähnliches Tempo, von allen deinen Rappern. */
    static List<String> similar(Context c, String id, int n) {
        JSONObject d = load(c);
        java.util.Map<String, Integer> mask = new java.util.HashMap<>();
        for (int k = 0; k < ROW; k++) {
            JSONArray a = d.optJSONArray(KEYS[k]);
            if (a == null) continue;
            for (int i = 0; i < a.length(); i++) {
                String x = a.optString(i);
                Integer m = mask.get(x);
                mask.put(x, (m == null ? 0 : m) | (1 << k));
            }
        }
        Integer mine = id == null ? null : mask.get(id);
        int me = mine == null ? (1 << 3) : mine;
        int bpm = bpm(c, id);
        Set<String> blocked = Library.blocked(c);
        final java.util.Map<String, Float> score = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, Integer> e : mask.entrySet()) {
            if (e.getKey().equals(id) || blocked.contains(e.getKey())) continue;
            int both = Integer.bitCount(e.getValue() & me), diff = Integer.bitCount(e.getValue() ^ me);
            float sc = both * 2f - diff;
            int b2 = bpm(c, e.getKey());
            if (bpm > 0 && b2 > 0) sc -= Math.abs(bpm - b2) / 20f;
            sc += (float) Math.random() * 0.8f;   // jedes Mal etwas anders
            score.put(e.getKey(), sc);
        }
        List<String> out = new ArrayList<>(score.keySet());
        java.util.Collections.sort(out, new java.util.Comparator<String>() {
            public int compare(String a, String b) { return Float.compare(score.get(b), score.get(a)); }
        });
        if (out.size() > n) out = new ArrayList<>(out.subList(0, n));
        java.util.Collections.shuffle(out);
        return out;
    }

    /** Tempo des Songs (110–200 BPM) oder 0, wenn unbekannt. */
    static int bpm(Context c, String id) {
        JSONObject b = load(c).optJSONObject("bpm");
        return b == null || id == null ? 0 : b.optInt(id);
    }
}
