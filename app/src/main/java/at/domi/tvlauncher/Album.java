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
    final List<String> tracks = new ArrayList<>();

    Album(int rapper, String title) {
        this.rapper = rapper;
        this.title = title;
    }

    /** Alle Alben deiner Rapper, abwechselnd gemischt (neueste zuerst), aus assets/albums/<name>.json */
    static List<Album> load(Context c) {
        List<List<Album>> per = new ArrayList<>();
        for (int r = 0; r < Rapper.MIX_OF; r++) {
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
                    JSONArray t = o.getJSONArray("tracks");
                    for (int k = 0; k < t.length(); k++) al.tracks.add(t.getString(k));
                    if (al.tracks.size() >= 5) list.add(al);
                }
            } catch (Exception ignored) { }
            per.add(list);
        }
        // reihum: neuestes Album von jedem Rapper, dann das nächste …
        List<Album> out = new ArrayList<>();
        for (int round = 0; round < 30; round++)
            for (List<Album> l : per) if (round < l.size()) out.add(l.get(round));
        return out;
    }
}
