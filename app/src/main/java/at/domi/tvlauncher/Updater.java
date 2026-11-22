package at.domi.tvlauncher;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * "Update suchen": holt die neueste Domi-TV-Version von GitHub und installiert sie direkt am Fernseher.
 * Läuft automatisch beim Start (höchstens alle 3 Stunden) und auf Wunsch (Menü-Taste lange drücken).
 */
final class Updater {
    static final String TAG = "DomiTV";
    static final String BASE = "https://github.com/Domi916/DomiTV/releases/latest/download/";
    static volatile boolean running = false;

    static int installedVersion(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; }
        catch (Exception e) { return 0; }
    }

    static void check(final Activity a, final boolean manual) {
        if (running) return;
        final SharedPreferences p = a.getSharedPreferences("domitv", Context.MODE_PRIVATE);
        long last = p.getLong("updCheck", 0);
        if (!manual && System.currentTimeMillis() - last < 3 * 3600 * 1000L) return;
        p.edit().putLong("updCheck", System.currentTimeMillis()).apply();
        running = true;
        final Handler ui = new Handler(Looper.getMainLooper());
        if (manual) Toast.makeText(a, "Suche nach Updates …", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int latest = Integer.parseInt(read(BASE + "version.txt").trim());
                    int mine = installedVersion(a);
                    Log.i(TAG, "Update: installiert " + mine + ", neu " + latest);
                    if (latest <= mine) {
                        if (manual) toast(ui, a, "Domi TV ist aktuell (Version " + mine + ")");
                        return;
                    }
                    toast(ui, a, "Neue Version " + latest + " wird geladen …");
                    install(a, BASE + "DomiTV.apk");
                } catch (Exception e) {
                    Log.e(TAG, "Update fehlgeschlagen", e);
                    if (manual) toast(ui, a, "Update nicht möglich: " + e.getMessage());
                } finally {
                    running = false;
                }
            }
        }).start();
    }

    static void toast(Handler ui, final Context c, final String s) {
        ui.post(new Runnable() { public void run() { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); } });
    }

    static HttpURLConnection open(String url) throws Exception {
        // GitHub leitet zum Download-Server weiter: Weiterleitungen selbst folgen
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "DomiTV");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                url = c.getHeaderField("Location");
                c.disconnect();
                continue;
            }
            if (code != 200) throw new Exception("HTTP " + code);
            return c;
        }
        throw new Exception("zu viele Weiterleitungen");
    }

    static String read(String url) throws Exception {
        HttpURLConnection c = open(url);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l).append('\n');
        r.close();
        c.disconnect();
        return sb.toString();
    }

    /** Lädt die APK in eine Installations-Sitzung; Android fragt dann einmal "Installieren?". */
    static void install(Context ctx, String url) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(ctx.getPackageName());
        int id = pi.createSession(sp);
        PackageInstaller.Session s = pi.openSession(id);
        HttpURLConnection c = open(url);
        InputStream in = c.getInputStream();
        OutputStream out = s.openWrite("DomiTV.apk", 0, c.getContentLength() > 0 ? c.getContentLength() : -1);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        s.fsync(out);
        out.close();
        in.close();
        c.disconnect();
        Intent cb = new Intent(ctx, Result.class);
        PendingIntent pi2 = PendingIntent.getBroadcast(ctx, id, cb, PendingIntent.FLAG_UPDATE_CURRENT);
        s.commit(pi2.getIntentSender());
        s.close();
    }

    /** Ergebnis der Installation: Bestätigung anzeigen oder Fehler melden. */
    public static class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -99);
            Log.i(TAG, "Update-Status " + st + " " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(confirm);
                }
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                Toast.makeText(c, "Update fehlgeschlagen: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), Toast.LENGTH_LONG).show();
            }
        }
    }
}
