package at.domi.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deine Musik-Daten (nur auf dem Fernseher gespeichert):
 *  - Favoriten ♥ (▼ während eines Songs)
 *  - gesperrte Songs (▲ während eines Songs: kommt nie wieder)
 *  - neu erschienene Songs deiner Rapper
 *  - Wochen-Rückblick (was du diese Woche gehört hast)
 */
final class Library {

    static SharedPreferences p(Context c) { return c.getSharedPreferences("domitv", Context.MODE_PRIVATE); }

    // ---------------- Favoriten ----------------
    static List<String[]> favorites(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString("favs", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.getString("id"), o.optString("title"), o.optString("artist")});
            }
        } catch (Exception ignored) { }
        return out;
    }

    static boolean isFavorite(Context c, String id) {
        for (String[] f : favorites(c)) if (f[0].equals(id)) return true;
        return false;
    }

    /** Favorit an/aus. Gibt true zurück, wenn er jetzt Favorit ist. */
    static boolean toggleFavorite(Context c, String id, String title, String artist) {
        try {
            JSONArray a = new JSONArray(p(c).getString("favs", "[]"));
            JSONArray n = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.getString("id").equals(id)) { removed = true; continue; }
                n.put(o);
            }
            if (!removed) n.put(new JSONObject().put("id", id).put("title", title == null ? "" : title).put("artist", artist == null ? "" : artist));
            p(c).edit().putString("favs", n.toString()).apply();
            return !removed;
        } catch (Exception e) { return false; }
    }

    // ---------------- Gesperrte Songs ----------------
    static Set<String> blocked(Context c) {
        return new HashSet<>(p(c).getStringSet("blocked", new HashSet<String>()));
    }

    static void block(Context c, String id) {
        Set<String> s = blocked(c);
        s.add(id);
        p(c).edit().putStringSet("blocked", s).apply();
    }

    // ---------------- Neu erschienen ----------------
    /**
     * Vergleicht die Songlisten deiner Rapper mit dem, was die App schon kannte.
     * Beim allerersten Mal wird nur gemerkt (sonst wäre alles "neu").
     * Liefert Einträge {rapperIndex, videoId, titel, zeitpunkt}, höchstens 14 Tage alt.
     */
    static List<String[]> newSongs(Context c) {
        SharedPreferences sp = p(c);
        long now = System.currentTimeMillis();
        try {
            JSONObject seen = new JSONObject(sp.getString("seenSongs", "{}"));
            JSONArray fresh = new JSONArray(sp.getString("newSongs", "[]"));
            boolean changed = false;
            for (int r = 0; r < Rapper.MIX_OF; r++) {
                Rapper rap = Rapper.LIST[r];
                String key = Rapper.slug(rap.name);
                List<String[]> all = rap.songsWithTitles(c);
                if (all.isEmpty()) continue;
                JSONArray known = seen.optJSONArray(key);
                Set<String> k = new HashSet<>();
                if (known != null) for (int i = 0; i < known.length(); i++) k.add(known.getString(i));
                JSONArray upd = new JSONArray();
                for (String[] s : all) {
                    upd.put(s[0]);
                    if (known != null && !k.contains(s[0])) {
                        fresh.put(new JSONObject().put("r", r).put("id", s[0]).put("t", s[1]).put("at", now));
                        changed = true;
                    }
                }
                if (known == null || known.length() != upd.length()) { seen.put(key, upd); changed = true; }
            }
            // ältere als 14 Tage entfernen
            JSONArray keep = new JSONArray();
            for (int i = 0; i < fresh.length(); i++) {
                JSONObject o = fresh.getJSONObject(i);
                if (now - o.getLong("at") < 14L * 24 * 3600 * 1000) keep.put(o);
                else changed = true;
            }
            if (changed) sp.edit().putString("seenSongs", seen.toString()).putString("newSongs", keep.toString()).apply();
            List<String[]> out = new ArrayList<>();
            for (int i = keep.length() - 1; i >= 0; i--) {
                JSONObject o = keep.getJSONObject(i);
                out.add(new String[]{String.valueOf(o.getInt("r")), o.getString("id"), o.getString("t"), String.valueOf(o.getLong("at"))});
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ---------------- Wochen-Rückblick ----------------
    static String weekKey() {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.WEEK_OF_YEAR);
    }

    static void record(Context c, String artist, String title) {
        try {
            SharedPreferences sp = p(c);
            JSONObject w = new JSONObject(sp.getString("week", "{}"));
            if (!weekKey().equals(w.optString("key"))) w = new JSONObject().put("key", weekKey());
            JSONObject ar = w.optJSONObject("artists");
            if (ar == null) ar = new JSONObject();
            ar.put(artist, ar.optInt(artist) + 1);
            JSONObject so = w.optJSONObject("songs");
            if (so == null) so = new JSONObject();
            String key = artist + " – " + title;
            so.put(key, so.optInt(key) + 1);
            w.put("artists", ar).put("songs", so).put("total", w.optInt("total") + 1);
            sp.edit().putString("week", w.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** {Top-Rapper, Anzahl Songs gesamt, Top-Song} oder null, wenn diese Woche noch wenig lief. */
    static String[] weekRecap(Context c) {
        try {
            JSONObject w = new JSONObject(p(c).getString("week", "{}"));
            if (!weekKey().equals(w.optString("key")) || w.optInt("total") < 5) return null;
            return new String[]{top(w.optJSONObject("artists")), String.valueOf(w.optInt("total")), top(w.optJSONObject("songs"))};
        } catch (Exception e) { return null; }
    }

    static String top(JSONObject o) {
        if (o == null) return "";
        String best = "";
        int n = -1;
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (o.optInt(k) > n) { n = o.optInt(k); best = k; }
        }
        return best;
    }

    // ---------------- Verlauf (für Zeitreise / Song der Woche) ----------------
    /** Jeder gehörte Song: Zeit, Video-ID, Rapper, Titel (files/history.tsv, ~400 Tage). */
    static synchronized void history(Context c, String id, String artist, String title) {
        if (id == null) return;
        try {
            java.io.File f = new java.io.File(c.getFilesDir(), "history.tsv");
            if (f.length() > 600000) trimHistory(f);
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(System.currentTimeMillis() + "\t" + id + "\t" + artist.replace("\t", " ") + "\t"
                    + title.replace("\t", " ").replace("\n", " ") + "\n");
            w.close();
        } catch (Exception ignored) { }
    }

    static void trimHistory(java.io.File f) {
        try {
            long cut = System.currentTimeMillis() - 400L * 86400000L;
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String l;
            while ((l = r.readLine()) != null) {
                try { if (Long.parseLong(l.substring(0, l.indexOf('\t'))) >= cut) sb.append(l).append('\n'); } catch (Exception ignored) { }
            }
            r.close();
            java.io.FileWriter w = new java.io.FileWriter(f, false);
            w.write(sb.toString());
            w.close();
        } catch (Exception ignored) { }
    }

    /**
     * Zeitreise-Karte: {Überschrift, Video-ID, Rapper, Titel}.
     * Vorrang: heute vor einem Jahr → vor einem Monat → dein Song der Woche (mind. 3× gehört).
     */
    static String[] timeTravel(Context c) {
        try {
            java.io.File f = new java.io.File(c.getFilesDir(), "history.tsv");
            if (!f.exists()) return null;
            long now = System.currentTimeMillis(), day = 86400000L;
            long[][] win = {{365 * day + day / 2, 365 * day - day / 2 - day}, {31 * day, 29 * day}, {7 * day, 0}};
            String[] head = {"Heute vor 1 Jahr", "Vor einem Monat", "Dein Song der Woche"};
            int[] min = {1, 2, 3};
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.split("\t", 4);
                if (p.length == 4) rows.add(p);
            }
            r.close();
            for (int w = 0; w < 3; w++) {
                java.util.Map<String, Integer> n = new java.util.HashMap<>();
                java.util.Map<String, String[]> any = new java.util.HashMap<>();
                for (String[] p : rows) {
                    long age = now - Long.parseLong(p[0]);
                    if (age <= win[w][0] && age >= win[w][1]) {
                        Integer k = n.get(p[1]);
                        n.put(p[1], k == null ? 1 : k + 1);
                        any.put(p[1], p);
                    }
                }
                String best = null;
                int bn = 0;
                for (java.util.Map.Entry<String, Integer> e : n.entrySet()) if (e.getValue() > bn) { bn = e.getValue(); best = e.getKey(); }
                if (best != null && bn >= min[w]) {
                    String[] p = any.get(best);
                    return new String[]{head[w], p[1], p[2], p[3]};
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    static int rapperIndexByName(String name) {
        for (int i = 0; i < Rapper.LIST.length; i++) if (Rapper.LIST[i].name.equalsIgnoreCase(name)) return i;
        return 0;
    }
}
