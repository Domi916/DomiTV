package at.domi.tvlauncher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.util.Calendar;

/**
 * Musik-Wecker: zur eingestellten Uhrzeit weckt der Stick sich auf, startet den Tagesmix
 * und dreht die Lautstärke in einer Minute langsam hoch.
 */
public class Wecker extends BroadcastReceiver {
    static SharedPreferences p(Context c) { return Library.p(c); }

    static boolean on(Context c) { return p(c).getBoolean("weckerOn", false); }
    static int hour(Context c) { return p(c).getInt("weckerH", 7); }
    static int minute(Context c) { return p(c).getInt("weckerM", 0); }

    static void set(Context c, boolean on, int h, int m) {
        p(c).edit().putBoolean("weckerOn", on).putInt("weckerH", h).putInt("weckerM", m).apply();
        schedule(c);
    }

    static PendingIntent pi(Context c) {
        return PendingIntent.getBroadcast(c, 4711, new Intent(c, Wecker.class), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Nächsten Weckzeitpunkt planen (oder abbrechen). */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pi(c));
        if (!on(c)) return;
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, hour(c));
        t.set(Calendar.MINUTE, minute(c));
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis() + 30000) t.add(Calendar.DAY_OF_MONTH, 1);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi(c));
    }

    static String text(Context c) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hour(c), minute(c));
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        schedule(c);   // gleich für morgen planen
        if (!on(c)) return;
        try {
            // Bildschirm einschalten -> per HDMI-CEC geht meist auch der Fernseher an
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            @SuppressWarnings("deprecation")
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "domitv:wecker");
            wl.acquire(60000);
        } catch (Exception ignored) { }
        final AudioManager a = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        final int target = a.getStreamVolume(AudioManager.STREAM_MUSIC);
        try { a.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, target / 5), 0); } catch (Exception ignored) { }
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra("wecker", true);
        c.startActivity(i);
        // in 60 s langsam auf die alte Lautstärke
        final Handler h = new Handler(Looper.getMainLooper());
        final int start = Math.max(1, target / 5);
        for (int s = 1; s <= 12; s++) {
            final int step = s;
            h.postDelayed(new Runnable() {
                public void run() {
                    try { a.setStreamVolume(AudioManager.STREAM_MUSIC, start + (Math.max(target, 4) - start) * step / 12, 0); } catch (Exception ignored) { }
                }
            }, 8000L + s * 5000L);
        }
    }
}
