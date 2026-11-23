package at.domi.tvlauncher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/** Hör-Statistik aus dem Verlauf (Fernseher + YouTube Music): Woche, Top-Rapper, Top-Song je Rapper. */
final class Stats {
    static final long SONG_MS = 165000;      // ~2:45 pro Song (Durchschnitt deiner Rapper)

    int weekPlays;
    String weekTop;                          // Top-Rapper der Woche
    final Map<String, Integer> weekBy = new HashMap<>();
    final Map<String, Integer> allBy = new HashMap<>();
    final Map<String, String> topSong = new HashMap<>();

    static volatile Stats last;

    static long weekStart() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Vienna"));
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() > System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, -7);
        return c.getTimeInMillis();
    }

    /** Im Hintergrund aufrufen. */
    static Stats compute(Context c) {
        Stats s = new Stats();
        Map<String, Map<String, Integer>> songs = new HashMap<>();
        long ws = weekStart();
        try {
            File f = new File(c.getFilesDir(), "history.tsv");
            if (f.exists()) {
                BufferedReader r = new BufferedReader(new FileReader(f));
                String l;
                while ((l = r.readLine()) != null) {
                    String[] p = l.split("\t", 4);
                    if (p.length < 4) continue;
                    long t;
                    try { t = Long.parseLong(p[0]); } catch (Exception e) { continue; }
                    String who = p[2];
                    inc(s.allBy, who);
                    Map<String, Integer> m = songs.get(who);
                    if (m == null) songs.put(who, m = new HashMap<>());
                    inc(m, p[3]);
                    if (t >= ws) { s.weekPlays++; inc(s.weekBy, who); }
                }
                r.close();
            }
        } catch (Exception ignored) { }
        int best = 0;
        for (Map.Entry<String, Integer> e : s.weekBy.entrySet()) if (e.getValue() > best) { best = e.getValue(); s.weekTop = e.getKey(); }
        for (Map.Entry<String, Map<String, Integer>> e : songs.entrySet()) {
            String bt = null;
            int bn = 0;
            for (Map.Entry<String, Integer> x : e.getValue().entrySet()) if (x.getValue() > bn) { bn = x.getValue(); bt = x.getKey(); }
            if (bt != null) s.topSong.put(e.getKey(), bt);
        }
        last = s;
        return s;
    }

    static void inc(Map<String, Integer> m, String k) {
        Integer v = m.get(k);
        m.put(k, v == null ? 1 : v + 1);
    }

    String weekTime() {
        long min = weekPlays * SONG_MS / 60000;
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }

    int plays(Map<String, Integer> m, String who) {
        Integer v = m.get(who);
        return v == null ? 0 : v;
    }
}
