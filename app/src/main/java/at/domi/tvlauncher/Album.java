package at.domi.tvlauncher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Ein Album / Mixtape eines deiner Rapper (Songs in der richtigen Reihenfolge). */
final class Album {
    final int rapper;
    final String title;
    String cover; // Pfad in assets/ (covers/…jpg) oder null
    String type = "album"; // album | ep | single

    boolean isAlbum() { return "album".equals(type); }

    String typeLabel() { return "single".equals(type) ? "SINGLE" : "ep".equals(type) ? "EP" : "ALBUM"; }
    final List<String> tracks = new ArrayList<>();

    static int t0(JSONObject o) {
        JSONArray t = o.optJSONArray("tracks");
        return t == null ? 0 : t.length();
    }

    Album(int rapper, String title) {
        this.rapper = rapper;
        this.title = title;
    }

    /** Rapper, deren Releases gezeigt werden: deine 5 + che */
    static List<Integer> artists() {
        List<Integer> out = new ArrayList<>();
        for (int r = 0; r < Rapper.MIX_OF; r++) out.add(r);
        for (int r = 0; r < Rapper.LIST.length; r++)
            if ("che".equals(Rapper.LIST[r].name) || "Prettifun".equals(Rapper.LIST[r].name)) out.add(r);
        return out;
    }

    /**
     * Releases deiner Rapper aus assets/albums/<name>.json, abwechselnd nach Rapper (neueste zuerst).
     * albums=true: nur Alben, sonst nur EPs und Singles.
     */
    static List<Album> load(Context c, boolean albums) {
        List<List<Album>> per = new ArrayList<>();
        for (int r : artists()) {
            List<Album> list = new ArrayList<>();
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        c.getAssets().open("albums/" + Rapper.slug(Rapper.LIST[r].name) + ".json"), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                JSONArray a = new JSONArray(sb.toString());
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Album al = new Album(r, o.optString("title"));
                    al.cover = o.has("cover") ? o.optString("cover") : null;
                    al.type = o.has("type") ? o.optString("type") : (t0(o) >= 5 ? "album" : t0(o) == 1 ? "single" : "ep");
                    if (al.isAlbum() != albums) continue;
                    JSONArray t = o.getJSONArray("tracks");
                    for (int k = 0; k < t.length(); k++) al.tracks.add(t.getString(k));
                    list.add(al);
                }
            } catch (Exception ignored) { }
            per.add(list);
        }
        // reihum: neuestes Album von jedem Rapper, dann das nächste …
        List<Album> out = new ArrayList<>();
        for (int round = 0; round < 200; round++)
            for (List<Album> l : per) if (round < l.size()) out.add(l.get(round));
        return out;
    }
}
