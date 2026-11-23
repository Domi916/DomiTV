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
                    HttpURLConnection c = (HttpURLConnection) new URL(URL_).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    JSONObject o = new JSONObject(sb.toString());
                    JSONObject cur = o.getJSONObject("current");
                    int code = cur.getInt("weather_code");
                    long temp = Math.round(cur.getDouble("temperature_2m"));
                    now = icon(code) + "  " + temp + "°";
                    // nächster Wetterwechsel heute (Regen / Schnee / Gewitter)
                    hint = "";
                    JSONArray codes = o.getJSONObject("hourly").getJSONArray("weather_code");
                    int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    String curKind = kind(code);
                    for (int i = h + 1; i < Math.min(24, codes.length()); i++) {
                        String k = kind(codes.getInt(i));
                        if (!k.isEmpty() && !k.equals(curKind)) { hint = k + " ab " + i + " Uhr"; break; }
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
