package at.domi.tvlauncher;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/** Wetter für Gries am Brenner (Open-Meteo, kostenlos, ohne Konto). Alle 30 Minuten neu. */
final class Weather {
    static final String URL_ = "https://api.open-meteo.com/v1/forecast?latitude=47.04&longitude=11.48"
            + "&current=temperature_2m,weather_code&hourly=weather_code,precipitation_probability"
            + "&forecast_days=1&timezone=Europe%2FVienna";

    interface Listener { void onWeather(String now, String hint); }

    static String now = "", hint = "";
    static long fetchedAt = 0;

    static volatile boolean loading = false;

    static void load(final Listener l) {
        if (loading || System.currentTimeMillis() - fetchedAt < 30 * 60 * 1000L) { l.onWeather(now, hint); return; }
        loading = true;
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                try {
                    try {
                        fromWttr();
                    } catch (Exception e1) {
                        Log.e("DomiTV", "Wetter (wttr.in): " + e1);
                        fromOpenMeteo();
                    }
                    fetchedAt = System.currentTimeMillis();
                    Log.i("DomiTV", "Wetter: " + now + " " + hint);
                } catch (Exception e) {
                    Log.e("DomiTV", "Wetter: " + e);
                    fetchedAt = System.currentTimeMillis() - 28 * 60 * 1000L; // in 2 Minuten nochmal
                } finally {
                    loading = false;
                }
                ui.post(new Runnable() { public void run() { l.onWeather(now, hint); } });
            }
        }).start();
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "curl/8.0 DomiTV");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    /** wttr.in (läuft auch auf älteren Geräten zuverlässig) */
    static void fromWttr() throws Exception {
        JSONObject o = new JSONObject(get("https://wttr.in/Gries%20am%20Brenner?format=j1&lang=de"));
        JSONObject cur = o.getJSONArray("current_condition").getJSONObject(0);
        int code = cur.getInt("weatherCode");
        now = icon(wwo(code)) + "  " + cur.getString("temp_C") + "°";
        hint = "";
        String curKind = kind(wwo(code));
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        JSONArray hours = o.getJSONArray("weather").getJSONObject(0).getJSONArray("hourly");
        for (int i = 0; i < hours.length(); i++) {
            JSONObject x = hours.getJSONObject(i);
            int hh = Integer.parseInt(x.getString("time")) / 100;
            if (hh <= h) continue;
            String k = kind(wwo(x.getInt("weatherCode")));
            if (!k.isEmpty() && !k.equals(curKind)) { hint = k + " ab " + hh + " Uhr"; break; }
        }
    }

    static void fromOpenMeteo() throws Exception {
        JSONObject o = new JSONObject(get(URL_));
        JSONObject cur = o.getJSONObject("current");
        int code = cur.getInt("weather_code");
        now = icon(code) + "  " + Math.round(cur.getDouble("temperature_2m")) + "°";
        hint = "";
        JSONArray codes = o.getJSONObject("hourly").getJSONArray("weather_code");
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String curKind = kind(code);
        for (int i = h + 1; i < Math.min(24, codes.length()); i++) {
            String k = kind(codes.getInt(i));
            if (!k.isEmpty() && !k.equals(curKind)) { hint = k + " ab " + i + " Uhr"; break; }
        }
    }

    /** wttr.in/WWO-Code → WMO-Code (für icon/kind) */
    static int wwo(int c) {
        if (c == 113) return 0;
        if (c == 116) return 2;
        if (c == 119 || c == 122) return 3;
        if (c == 143 || c == 248 || c == 260) return 45;
        if (c == 200 || (c >= 386 && c <= 395)) return 95;
        if (c == 179 || c == 227 || c == 230 || (c >= 320 && c <= 338) || (c >= 368 && c <= 377) || c == 182 || c == 185 || c == 350) return 73;
        if (c == 176 || (c >= 263 && c <= 314) || (c >= 353 && c <= 359)) return 61;
        return 3;
    }

    /** WMO-Wettercode → Symbol */
    static String icon(int c) {
        if (c == 0) return "☀";
        if (c <= 2) return "⛅";
        if (c == 3) return "☁";
        if (c == 45 || c == 48) return "🌫";
        if (c >= 71 && c <= 77 || c == 85 || c == 86) return "❄";
        if (c >= 95) return "⚡";
        if (c >= 51) return "☂";
        return "☁";
    }

    static String kind(int c) {
        if (c >= 71 && c <= 77 || c == 85 || c == 86) return "Schnee";
        if (c >= 95) return "Gewitter";
        if (c >= 51 && c <= 67 || c >= 80 && c <= 82) return "Regen";
        return "";
    }
}
