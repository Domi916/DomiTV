package at.domi.tvlauncher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Tagesmix: jeden Tag neu, 30 Songs deiner Rapper –
 * deine Favoriten, neu erschienene Songs und Songs, die du lange nicht mehr gehört hast.
 */
final class DailyMix {
    static List<String> build(Context c) {
        long day = System.currentTimeMillis() / 86400000L;
        Random rnd = new Random(day * 7919L);
        Set<String> blocked = Library.blocked(c);
        // in den letzten 14 Tagen gehört
        Set<String> recent = new HashSet<>();
        try {
            File f = new File(c.getFilesDir(), "history.tsv");
            long cut = System.currentTimeMillis() - 14L * 86400000L;
            if (f.exists()) {
                BufferedReader r = new BufferedReader(new FileReader(f));
                String l;
                while ((l = r.readLine()) != null) {
                    String[] p = l.split("\t", 3);
                    try { if (p.length > 1 && Long.parseLong(p[0]) >= cut) recent.add(p[1]); } catch (Exception ignored) { }
                }
                r.close();
            }
        } catch (Exception ignored) { }

        List<String> out = new ArrayList<>();
        List<String> favs = new ArrayList<>();
        for (String[] f : Library.favorites(c)) favs.add(f[0]);
        Collections.shuffle(favs, rnd);
        addSome(out, favs, 10, blocked);
        List<String> fresh = new ArrayList<>();
        for (String[] n : Library.newSongs(c)) fresh.add(n[0]);
        Collections.shuffle(fresh, rnd);
        addSome(out, fresh, 6, blocked);
        List<String> old = new ArrayList<>(), all = new ArrayList<>();
        for (int r = 0; r < Rapper.MIX_OF; r++) {
            for (String id : Rapper.LIST[r].songs(c)) {
                all.add(id);
                if (!recent.contains(id)) old.add(id);
            }
        }
        Collections.shuffle(old, rnd);
        addSome(out, old, 30 - out.size(), blocked);
        Collections.shuffle(all, rnd);
        addSome(out, all, 30 - out.size(), blocked);
        Collections.shuffle(out, rnd);
        return out;
    }

    static void addSome(List<String> out, List<String> src, int n, Set<String> blocked) {
        for (String id : src) {
            if (n <= 0) return;
            if (id == null || id.length() != 11 || blocked.contains(id) || out.contains(id)) continue;
            out.add(id);
            n--;
        }
    }
}
