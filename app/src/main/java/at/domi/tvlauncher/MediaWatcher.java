package at.domi.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
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
 * Rapper-Shuffle über YouTube.
 * YouTube auf dem Fire TV nimmt nur einzelne Videos an und spielt danach "ähnliche" Videos.
 * Dieser Dienst beobachtet über die Mediensteuerung, was in YouTube läuft:
 *  - kurz vor Songende startet er selbst den nächsten Song des Rappers (YouTube kommt gar nicht zum Autoplay)
 *  - startet trotzdem ein fremder Song, wird er stumm geschaltet und sofort ersetzt
 *  - Songtext-/Audio-Anzeige von Domi TV liegt als Overlay darüber (siehe LyricsOverlay)
 */
public class MediaWatcher extends NotificationListenerService {
    static final String TAG = "DomiTV";

    // ---------- Warteschlange ----------
    static final List<String> queue = new ArrayList<>();
    static int pos = 0;
    static volatile boolean active = false;
    static String ytPkg;
    static long launchedAt = 0;
    static String ourTitle = null;
    static String rapperName = "";
    static int rapperIndex = 0;
    static MediaWatcher instance;

    static void start(Context c, String pkg, int index) {
        Rapper r = Rapper.LIST[index];
        queue.clear();
        Collections.addAll(queue, r.videos);
        Collections.shuffle(queue);
        pos = 0;
        ytPkg = pkg;
        rapperName = r.name;
        rapperIndex = index;
        active = true;
        appCtx = c.getApplicationContext();
        log(c, "Shuffle gestartet: " + r.name + " (" + queue.size() + " Songs)");
        HomeWatcherService.overlayShow(index);
        play(c);
    }

    static void stop() {
        if (active) log(null, "Shuffle beendet");
        active = false;
        unmute();
        HomeWatcherService.overlayHide();
    }

    static Context appCtx;

    /** Eigenes Protokoll: adb shell run-as at.domi.tvlauncher cat files/shuffle.log */
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

    /** ⏩ während des Shuffles: nächster Song des Rappers. */
    static void skip(Context c) {
        if (!active) return;
        pos++;
        HomeWatcherService.overlaySwitching();
        play(c);
    }

    static boolean play(Context c) {
        if (queue.isEmpty() || ytPkg == null) return false;
        if (pos >= queue.size()) { Collections.shuffle(queue); pos = 0; }
        String id = queue.get(pos);
        launchedAt = SystemClock.elapsedRealtime();
        ourTitle = null;
        curDur = 0;
        lastPos = -1;
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

    // ---------- Ton kurz stumm, solange ein fremder Song ersetzt wird ----------
    static boolean muted = false;
    static final Handler mh = new Handler(Looper.getMainLooper());
    static final Runnable unmuteLater = new Runnable() { public void run() { unmute(); } };

    static void mute() {
        if (appCtx == null || muted) return;
        try {
            AudioManager am = (AudioManager) appCtx.getSystemService(AUDIO_SERVICE);
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            muted = true;
            mh.removeCallbacks(unmuteLater);
            mh.postDelayed(unmuteLater, 9000); // Sicherheit: nie stumm hängen bleiben
        } catch (Exception ignored) { }
    }

    static void unmute() {
        mh.removeCallbacks(unmuteLater);
        if (appCtx == null || !muted) return;
        try {
            AudioManager am = (AudioManager) appCtx.getSystemService(AUDIO_SERVICE);
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        } catch (Exception ignored) { }
        muted = false;
    }

    // ---------- Position im aktuellen Song ----------
    static long curDur = 0, lastPos = -1, lastPosAt = 0, trackAt = 0;
    static boolean playing = true;
    static float speed = 1f;
    static MediaController current;

    static void trackStarted(MediaMetadata m) {
        curDur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        lastPos = -1;
        trackAt = SystemClock.elapsedRealtime();
    }

    /** Aktuelle Position im Song in ms (aus YouTube, sonst geschätzt). */
    static long position() {
        long now = SystemClock.elapsedRealtime();
        if (lastPos >= 0) return lastPos + (playing ? (long) ((now - lastPosAt) * speed) : 0);
        return trackAt > 0 ? now - trackAt : 0;
    }

    static boolean positionKnown() { return lastPos >= 0; }

    /** Zurück-Taste: YouTube pausieren. */
    static void pauseYouTube() {
        try { if (current != null) current.getTransportControls().pause(); } catch (Exception ignored) { }
    }

    // ---------- Mediensitzungen beobachten ----------
    final Handler h = new Handler(Looper.getMainLooper());
    final Map<MediaController, MediaController.Callback> watched = new HashMap<>();
    MediaSessionManager msm;

    final MediaSessionManager.OnActiveSessionsChangedListener sessions = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(List<MediaController> list) { attach(list); }
    };

    /** Kurz vor Songende selbst weiterschalten, damit YouTube kein fremdes Video startet. */
    final Runnable endCheck = new Runnable() {
        @Override
        public void run() {
            h.postDelayed(this, 400);
            if (!active || curDur <= 0 || !positionKnown() || !playing) return;
            if (SystemClock.elapsedRealtime() - launchedAt < 8000) return;
            if (position() >= curDur - 1500) {
                log(MediaWatcher.this, "Songende erreicht, nächster Song (pos=" + position() + " dur=" + curDur + ")");
                pos++;
                HomeWatcherService.overlaySwitching();
                play(MediaWatcher.this);
            }
        }
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        appCtx = getApplicationContext();
        try {
            msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, MediaWatcher.class);
            msm.addOnActiveSessionsChangedListener(sessions, me, h);
            attach(msm.getActiveSessions(me));
            log(this, "MediaWatcher bereit");
        } catch (Exception e) {
            log(this, "MediaWatcher: keine Berechtigung " + e);
        }
        h.removeCallbacks(endCheck);
        h.post(endCheck);
    }

    @Override
    public void onListenerDisconnected() {
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions); } catch (Exception ignored) { }
        h.removeCallbacks(endCheck);
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
                public void onMetadataChanged(MediaMetadata m) { current = mc; onTrack(m); }

                @Override
                public void onPlaybackStateChanged(PlaybackState s) {
                    if (s == null) return;
                    current = mc;
                    playing = s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING;
                    if (s.getPosition() > 0) {
                        lastPos = s.getPosition();
                        lastPosAt = s.getLastPositionUpdateTime() > 0 ? s.getLastPositionUpdateTime() : SystemClock.elapsedRealtime();
                        speed = s.getPlaybackSpeed() > 0 ? s.getPlaybackSpeed() : 1f;
                    }
                }

                @Override
                public void onSessionDestroyed() { watched.remove(mc); }
            };
            mc.registerCallback(cb, h);
            watched.put(mc, cb);
            current = mc;
            log(this, "beobachte " + p);
            onTrack(mc.getMetadata());
        }
    }

    void onTrack(MediaMetadata m) {
        if (!active || m == null) return;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null || t.isEmpty()) return;
        String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        long since = SystemClock.elapsedRealtime() - launchedAt;
        if (since < 7000) {
            // der Song, den wir gerade gestartet haben
            if (!t.equals(ourTitle)) {
                trackStarted(m);
                log(this, "unser Song: " + t + " / " + artist + " dur=" + curDur);
                unmute();
                HomeWatcherService.overlayTrack(t, curDur);
            }
            ourTitle = t;
            return;
        }
        if (t.equals(ourTitle)) return;
        // Songtext-/Audio-Modus von YouTube meldet die Song-Version mit anderem Titel: gleicher Song
        String author = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        String r = norm(rapperName);
        if (norm(t).contains(r) || (artist != null && norm(artist).contains(r))
                || (author != null && norm(author).contains(r)) || sameSong(t, ourTitle)) {
            log(this, "bleibt: " + t + " / " + artist);
            if (!sameSong(t, ourTitle)) {
                trackStarted(m);
                HomeWatcherService.overlayTrack(t, curDur);
            }
            ourTitle = t;
            return;
        }
        log(this, "fremd: " + t + " / " + artist + " -> stumm + nächster Song");
        mute();
        HomeWatcherService.overlaySwitching();
        if (since < 3000) return;
        pos++;
        play(this);
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
        if (x.equals(y)) return true;
        return (y.length() >= 6 && x.contains(y)) || (x.length() >= 6 && y.contains(x));
    }
}
