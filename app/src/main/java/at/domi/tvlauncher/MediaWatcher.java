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

    static void stop() { if (active) log(null, "Shuffle beendet (zurück in Domi TV)"); active = false; }

    static Context appCtx;
    /** Eigenes Protokoll in einer Datei, damit nichts verloren geht: adb shell run-as at.domi.tvlauncher cat files/shuffle.log */
    static synchronized void log(Context c, String msg) {
        Log.i(TAG, "Shuffle: " + msg);
        if (c != null) appCtx = c.getApplicationContext();
        if (appCtx == null) return;
        try {
            java.io.File f = new java.io.File(appCtx.getFilesDir(), "shuffle.log");
            if (f.length() > 200000) f.delete();
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date()) + " " + msg + "\n");
            w.close();
        } catch (Exception ignored) { }
    }

    /** ⏩-Taste während des Shuffles: nächster Song des Rappers. */
    static void skip(Context c) {
        if (!active) return;
        pos++;
        play(c);
    }

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
            log(c, rapperName + ": Song " + (pos + 1) + "/" + queue.size() + " " + id);
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
            log(this, "MediaWatcher bereit");
        } catch (Exception e) {
            log(this, "MediaWatcher: keine Berechtigung " + e);
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
                    if (s == null) return;
                    if (s.getPosition() >= 0) {
                        lastPos = s.getPosition();
                        lastPosAt = s.getLastPositionUpdateTime() > 0 ? s.getLastPositionUpdateTime() : SystemClock.elapsedRealtime();
                        playing = s.getState() == PlaybackState.STATE_PLAYING;
                        speed = s.getPlaybackSpeed() > 0 ? s.getPlaybackSpeed() : 1f;
                    }
                }

                @Override
                public void onSessionDestroyed() { watched.remove(mc); }
            };
            mc.registerCallback(cb, h);
            watched.put(mc, cb);
            log(this, "beobachte " + p);
            onTrack(mc, mc.getMetadata());
        }
    }

    // Wo steht der aktuelle Song? Damit unterscheiden wir "Song zu Ende" (Autoplay) von "du hast selbst etwas gewählt".
    static long curDur = 0, lastPos = -1, lastPosAt = 0;
    static boolean playing = false;
    static float speed = 1f;

    static void trackStarted(MediaMetadata m) {
        curDur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        lastPos = -1;
    }

    /** true = der vorige Song war (fast) zu Ende, also hat YouTube von selbst weitergemacht. */
    static boolean endedNaturally() {
        if (curDur <= 0 || lastPos < 0) return true; // unbekannt: wie bisher behandeln
        long pos = lastPos + (playing ? (long) ((SystemClock.elapsedRealtime() - lastPosAt) * speed) : 0);
        return pos >= curDur - 12000;
    }

    void onTrack(MediaController mc, MediaMetadata m) {
        if (!active || m == null) return;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null || t.isEmpty()) return;
        long since = SystemClock.elapsedRealtime() - launchedAt;
        if (since < 7000) {
            // Das ist der Song, den wir gerade gestartet haben
            if (!t.equals(ourTitle)) { trackStarted(m); log(this, "unser Song: " + t + " / " + m.getString(MediaMetadata.METADATA_KEY_ARTIST) + " dur=" + curDur); }
            ourTitle = t;
            return;
        }
        if (t.equals(ourTitle)) return;
        // Songtext-/Audio-Modus: YouTube wechselt dann auf die Song-Version mit anderem Titel.
        // Alles, was vom Rapper ist oder zum aktuellen Song passt, bleibt.
        String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String author = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        String r = norm(rapperName);
        if (norm(t).contains(r) || (artist != null && norm(artist).contains(r))
                || (author != null && norm(author).contains(r)) || sameSong(t, ourTitle)) {
            log(this, "bleibt: " + t + " / " + artist + " / " + author);
            if (!sameSong(t, ourTitle)) trackStarted(m);
            ourTitle = t;
            return;
        }
        if (!endedNaturally()) {
            // Mitten im Song etwas anderes gestartet: das hast du selbst ausgesucht, Shuffle hört auf
            log(this, "selbst gewählt? " + t + " / " + artist + " pos=" + lastPos + " dur=" + curDur + " -> Shuffle aus");
            active = false;
            return;
        }
        log(this, "fremd: " + t + " / " + artist + " pos=" + lastPos + " dur=" + curDur + " -> nächster Song");
        next();
    }

    static String norm(String x) {
        if (x == null) return "";
        return x.toLowerCase()
                .replaceAll("[\\(\\[][^\\)\\]]*[\\)\\]]", " ")
                .replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** "Ken Carson - margiela (Official Music Video)" und "margiela" sind derselbe Song. */
    static boolean sameSong(String a, String b) {
        String x = norm(a), y = norm(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        return x.contains(y) || y.contains(x);
    }

    void next() {
        if (!active) return;
        long since = SystemClock.elapsedRealtime() - launchedAt;
        if (since < 3000) return;
        pos++;
        play(this);
    }
}
