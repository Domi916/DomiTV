package at.domi.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Iterator;
import java.util.TimeZone;

/**
 * Was du am Handy auf YouTube Music von deinen Rappern hörst, zählt im Rückblick mit:
 * GitHub holt täglich deinen Verlauf (nur Songs deiner Rapper) nach assets/ytm.json, die App übernimmt neue Tage.
 */
final class YtmSync {
    static final String URL_JSON = "https://raw.githubusercontent.com/Domi916/DomiTV/main/app/src/main/assets/ytm.json";

    /** true, wenn neue Songs dazugekommen sind. */
    static boolean run(Context c) {
        boolean any = false;
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(URL_JSON + "?t=" + System.currentTimeMillis() / 600000).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            if (h.getResponseCode() != 200) return false;
            JSONObject days = new JSONObject(Drops.read(h.getInputStream())).optJSONObject("days");
            if (days == null) return false;
            java.util.Set<String> seen = new java.util.HashSet<>(p0(c).getStringSet("ytmSeen", new java.util.HashSet<String>()));
            SharedPreferences p = Library.p(c);
            String done = p.getString("ytmDone", "");
            StringBuilder nd = new StringBuilder(done);
            Iterator<String> it = days.keys();
            String thisWeek = Library.weekKey();
            while (it.hasNext()) {
                String day = it.next();
                if (done.contains("|" + day + "|")) continue;
                String[] d = day.split("-");
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Vienna"));
                cal.clear();
                cal.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]), 15, 0, 0);
                Calendar wk = (Calendar) cal.clone();
                wk.setFirstDayOfWeek(Calendar.MONDAY);
                boolean sameWeek = thisWeek.equals(wk.get(Calendar.YEAR) + "-" + wk.get(Calendar.WEEK_OF_YEAR));
                JSONArray a = days.getJSONArray(day);
                for (int i = 0; i < a.length(); i++) {
                    JSONArray e = a.getJSONArray(i);
                    if (!seen.add(day + ":" + e.getString(0))) continue;
                    any = true;
                    String title = LyricsOverlay.cleanTitle(e.getString(2), e.getString(1));
                    Library.history(c, cal.getTimeInMillis(), e.getString(0), e.getString(1), title);
                    if (sameWeek) Library.record(c, e.getString(1), title);
                }
                // heute wird morgen nochmal ergänzt – erst abhaken, wenn der Tag vorbei ist
                if (day.compareTo(daysAgo(3)) < 0) nd.append("|").append(day).append("|");
            }
            // nur die letzten Tage merken (ältere sind als "done" abgehakt)
            java.util.Set<String> keep = new java.util.HashSet<>();
            for (String k : seen) if (k.compareTo(daysAgo(4)) >= 0) keep.add(k);
            p.edit().putStringSet("ytmSeen", keep).putString("ytmDone", nd.length() > 6000 ? nd.substring(nd.length() - 6000) : nd.toString()).apply();
        } catch (Exception ignored) { }
        return any;
    }

    static SharedPreferences p0(Context c) { return Library.p(c); }

    static String daysAgo(int n) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Vienna"));
        c.add(Calendar.DAY_OF_MONTH, -n);
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String today() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Vienna"));
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
