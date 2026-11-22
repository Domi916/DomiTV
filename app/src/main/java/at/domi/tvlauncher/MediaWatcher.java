package at.domi.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rapper-Shuffle: YouTube auf dem Fire TV ignoriert Playlists aus Links und spielt danach
 * "ähnliche" Videos (andere Rapper). Dieser Dienst sieht über die Mediensteuerung, wann in
 * YouTube ein neuer Song beginnt, den nicht wir gestartet haben (Autoplay oder Skip),
 * und startet stattdessen sofort den nächsten Song des Rappers aus der gemischten Liste.
 */
public class MediaWatcher extends NotificationListenerService {
    static final String TAG = "DomiTV";

    // ---------- Warteschlange (wird von MainActivity gesetzt) ----------
    static final List<String> queue = new ArrayList<>();
    static int pos = 0;
    static volatile boolean active = false;
    static String ytPkg;
    static long launchedAt = 0;
    static String ourTitle = null;
    static String rapperName = "";

    static void start(Context c, String pkg, String name, String[] ids) {
        queue.clear();
        Collections.addAll(queue, ids);
        Collections.shuffle(queue);
        pos = 0;
        ytPkg = pkg;
        rapperName = name;
        active = true;
        play(c);
    }

    static void stop() { active = false; }

    static boolean play(Context c) {
        if (queue.isEmpty() || ytPkg == null) return false;
        if (pos >= queue.size()) { Collections.shuffle(queue); pos = 0; }
        String id = queue.get(pos);
        launchedAt = SystemClock.elapsedRealtime();
        ourTitle = null;
        Intent v = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id));
        v.setPackage(ytPkg);
        v.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(v);
            Log.i(TAG, "Shuffle " + rapperName + ": Song " + (pos + 1) + "/" + queue.size() + " " + id);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Shuffle: Start fehlgeschlagen", e);
            return false;
        }
    }

    // ---------- Mediensitzungen von YouTube beobachten ----------
    final Handler h = new Handler(Looper.getMainLooper());
    final Map<MediaController, MediaController.Callback> watched = new HashMap<>();
    MediaSessionManager msm;

    final MediaSessionManager.OnActiveSessionsChangedListener sessions = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(List<MediaController> list) { attach(list); }
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        try {
            msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, MediaWatcher.class);
            msm.addOnActiveSessionsChangedListener(sessions, me, h);
            attach(msm.getActiveSessions(me));
            Log.i(TAG, "MediaWatcher bereit");
        } catch (Exception e) {
            Log.e(TAG, "MediaWatcher: keine Berechtigung", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions); } catch (Exception ignored) { }
        super.onListenerDisconnected();
    }

    void attach(List<MediaController> list) {
        if (list == null) return;
        for (final MediaController mc : list) {
            boolean known = false;
            for (MediaController w : watched.keySet()) if (w.getSessionToken().equals(mc.getSessionToken())) known = true;
            if (known) continue;
            String p = mc.getPackageName();
            if (p == null || !p.contains("youtube")) continue;
            MediaController.Callback cb = new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata m) { onTrack(mc, m); }

                @Override
                public void onPlaybackStateChanged(PlaybackState s) {
                    // Song zu Ende, YouTube würde ein fremdes Video starten: wir übernehmen
                    if (active && s != null && s.getState() == PlaybackState.STATE_STOPPED
                            && SystemClock.elapsedRealtime() - launchedAt > 8000) {
                        next();
                    }
                }

                @Override
                public void onSessionDestroyed() { watched.remove(mc); }
            };
            mc.registerCallback(cb, h);
            watched.put(mc, cb);
            Log.i(TAG, "MediaWatcher: beobachte " + p);
            onTrack(mc, mc.getMetadata());
        }
    }

    void onTrack(MediaController mc, MediaMetadata m) {
        if (!active || m == null) return;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null || t.isEmpty()) return;
        long since = SystemClock.elapsedRealtime() - launchedAt;
        if (since < 7000) {
            // Das ist der Song, den wir gerade gestartet haben
            ourTitle = t;
            return;
        }
        if (t.equals(ourTitle)) return;
        Log.i(TAG, "Shuffle: fremder Song erkannt (" + t + "), nächster Song von " + rapperName);
        next();
    }

    void next() {
        if (!active) return;
        long since = SystemClock.elapsedRealtime() - launchedAt;
        if (since < 3000) return;
        pos++;
        play(this);
    }
}
