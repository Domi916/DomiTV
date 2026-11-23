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
    /** Nach dem Starten eines Songs: der erste neue Titel, den YouTube meldet, ist unserer (egal wie lange das Laden dauert). */
    static boolean awaitingOurs = false;
    static int stuck = 0;
    int scan = 0;
    static String prevTitle = null;
    static String rapperName = "";
    static String[] members = new String[0];
    static int rapperIndex = 0;
    static MediaWatcher instance;

    static void start(Context c, String pkg, int index) { start(c, pkg, index, null); }

    /** Album / Mixtape in der richtigen Reihenfolge. */
    static String albumTitle = null;

    /** Stimmungs-Shuffle (Rage / Chill / …): Songs aller Rapper, Schlüssel aus Moods.KEYS. */
    static String mood = null;

    static void startMood(Context c, String pkg, String key) {
        albumTitle = null;
        mood = key;
        startQueue(c, pkg, Rapper.FAV, Moods.songs(c, key), true, null);
    }

    /** "Mehr wie das": der aktuelle Song läuft weiter, danach kommen ähnliche Songs aller Rapper. */
    static int moreLikeThis(Context c) {
        if (!active) return 0;
        List<String> sim = Moods.similar(c, currentId(), 30);
        if (sim.isEmpty()) return 0;
        while (queue.size() > pos + 1) queue.remove(queue.size() - 1);
        queue.addAll(sim);
        mood = "like";
        albumTitle = null;
        members = Rapper.LIST[Rapper.FAV].members();
        log(c, "Mehr wie das: " + sim.size() + " ähnliche Songs");
        return sim.size();
    }

    static void startAlbum(Context c, String pkg, int index, List<String> tracks, String title) {
        mood = null;
        albumTitle = title;
        startQueue(c, pkg, index, new ArrayList<>(tracks), false, null);
    }

    /** firstId: dieser Song zuerst (z. B. "Neu erschienen"), danach gemischt. */
    static void start(Context c, String pkg, int index, String firstId) {
        albumTitle = null;
        mood = null;
        startQueue(c, pkg, index, Rapper.LIST[index].songs(c), true, firstId);
    }

    static void startQueue(Context c, String pkg, int index, List<String> songs, boolean shuffle, String firstId) {
        Rapper r = Rapper.LIST[index];
        queue.clear();
        queue.addAll(songs);
        members = r.members();
        if (shuffle) Collections.shuffle(queue);
        if (firstId != null) { queue.remove(firstId); queue.add(0, firstId); }
        pos = 0;
        ytPkg = pkg;
        rapperName = r.name;
        rapperIndex = index;
        active = true;
        foreignInRow = 0;
        appCtx = c.getApplicationContext();
        unmuteHard(c);
        log(c, "Shuffle gestartet: " + (mood != null ? "Stimmung " + mood : r.name) + " (" + queue.size() + " Songs)");
        HomeWatcherService.overlayShow(index);
        // Lautstärke-Angleichung aus: sie hat die Lautstärke hörbar pumpen lassen
        Loudness.off();
        JblBox.wanted = true;
        final Context app = c.getApplicationContext();
        // erst die Box verbinden, dann YouTube starten (sonst bleibt YouTube beim Ton-Wechsel hängen)
        JblBox.connectThen(app, new Runnable() {
            public void run() { if (active) play(app); }
        });
    }

    static void stop() {
        if (active) log(null, "Shuffle beendet");
        active = false;
        unmute();
        Loudness.off();
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

    /** ◀: vorheriger Song (oder dieser Song von vorne, wenn es der erste ist). */
    static void previous(Context c) {
        if (!active) return;
        if (pos > 0) pos--;
        HomeWatcherService.overlaySwitching();
        play(c);
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
        if (pos >= queue.size()) { if (albumTitle == null) Collections.shuffle(queue); pos = 0; }
        String id = queue.get(pos);
        launchedAt = SystemClock.elapsedRealtime();
        prevTitle = ourTitle;
        ourTitle = null;
        awaitingOurs = true;
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

    /** Falls ein früherer Lauf stumm hängen geblieben ist: Ton sicher wieder an. */
    static void unmuteHard(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(AUDIO_SERVICE);
            if (am.isStreamMute(AudioManager.STREAM_MUSIC)) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        } catch (Exception ignored) { }
        muted = false;
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
    static int foreignInRow = 0;

    // Ersatz-Uhr, falls YouTube keine Position meldet: zählt nur, während wirklich abgespielt wird
    static long wallBase = 0, wallStart = 0;

    static void trackStarted(MediaMetadata m) {
        curDur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        lastPos = -1;
        trackAt = SystemClock.elapsedRealtime();
        wallBase = 0;
        wallStart = playing ? trackAt : 0;
    }

    static void applyState(PlaybackState s) {
        if (s == null) return;
        long now = SystemClock.elapsedRealtime();
        boolean nowPlaying = s.getState() == PlaybackState.STATE_PLAYING;
        if (nowPlaying && wallStart == 0) wallStart = now;
        if (!nowPlaying && wallStart > 0) { wallBase += now - wallStart; wallStart = 0; }
        playing = nowPlaying;
        if (s.getPosition() > 0) {
            lastPos = s.getPosition();
            lastPosAt = s.getLastPositionUpdateTime() > 0 ? s.getLastPositionUpdateTime() : now;
            speed = s.getPlaybackSpeed() > 0 ? s.getPlaybackSpeed() : 1f;
        }
    }

    /** Aktuelle Position im Song in ms (aus YouTube, sonst nur gezählte Abspielzeit). */
    static long position() {
        long now = SystemClock.elapsedRealtime();
        if (lastPos >= 0) return lastPos + (playing ? (long) ((now - lastPosAt) * speed) : 0);
        return wallBase + (wallStart > 0 ? now - wallStart : 0);
    }

    static boolean isPlaying() { return playing; }

    /** Nach einem Ton-Wechsel (Box kam dazu): YouTube anstupsen, damit es weiterspielt. */
    static void resumePlay() {
        try { if (active && current != null) current.getTransportControls().play(); } catch (Exception ignored) { }
    }

    /** OK / ⏯ während der Anzeige: Pause bzw. weiter. */
    static void togglePause() {
        try {
            if (current == null) return;
            if (playing) current.getTransportControls().pause();
            else current.getTransportControls().play();
        } catch (Exception ignored) { }
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
            // manche Apps melden die Position nur auf Nachfrage
            try { if (active && current != null) applyState(current.getPlaybackState()); } catch (Exception ignored) { }
            long since = SystemClock.elapsedRealtime() - launchedAt;
            // alle 3 s: neue YouTube-Sitzungen suchen (YouTube baut seine Sitzung manchmal neu auf)
            if (active && msm != null && ++scan % 8 == 0) {
                try { attach(msm.getActiveSessions(new ComponentName(MediaWatcher.this, MediaWatcher.class))); } catch (Exception ignored) { }
            }
            // Song läuft schon, wurde aber nicht erkannt (z. B. gleicher Titel wie vorher): nochmal prüfen
            if (active && awaitingOurs && since > 4000 && current != null) {
                try {
                    PlaybackState ps = current.getPlaybackState();
                    MediaMetadata md = current.getMetadata();
                    if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING && md != null) {
                        if (since > 15000) prevTitle = null;   // "gleicher Titel wie vorher" gilt nicht mehr
                        onTrack(md);
                    }
                } catch (Exception ignored) { }
            }
            // Notbremse: Song lädt seit 30 Sekunden nicht -> nächster Song, nach 3 Fehlversuchen Schluss
            if (active && awaitingOurs && SystemClock.elapsedRealtime() - launchedAt > 30000) {
                stuck++;
                unmute();
                if (stuck >= 3) {
                    log(MediaWatcher.this, "YouTube lädt nicht, Shuffle beendet");
                    stop();
                } else {
                    log(MediaWatcher.this, "Song lädt nicht, nächster Versuch");
                    pos++;
                    HomeWatcherService.overlaySwitching();
                    play(MediaWatcher.this);
                }
            }
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
                    current = mc;
                    applyState(s);
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
        boolean stale = prevTitle != null && t.equals(prevTitle) && since < 15000;
        if (!stale && ((awaitingOurs && since < 60000) || since < 7000)) {
            awaitingOurs = false;
            stuck = 0;
            // der Song, den wir gerade gestartet haben
            if (!t.equals(ourTitle)) {
                trackStarted(m);
                foreignInRow = 0;
                log(this, "unser Song: " + t + " / " + artist + " dur=" + curDur);
                Library.record(this, whoSings(t, artist), LyricsOverlay.cleanTitle(t, whoSings(t, artist)));
                Library.history(this, currentId(), whoSings(t, artist), LyricsOverlay.cleanTitle(t, whoSings(t, artist)));
                unmute();
                HomeWatcherService.overlayTrack(t, artist, curDur);
            }
            ourTitle = t;
            return;
        }
        if (t.equals(ourTitle)) return;
        // Songtext-/Audio-Modus von YouTube meldet die Song-Version mit anderem Titel: gleicher Song
        String author = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        boolean mine = sameSong(t, ourTitle);
        for (String mem : members) {
            String r = norm(mem);
            if (hasWord(t, r) || hasWord(artist, r) || hasWord(author, r)) mine = true;
        }
        if (mine) {
            log(this, "bleibt: " + t + " / " + artist);
            if (!sameSong(t, ourTitle)) {
                trackStarted(m);
                HomeWatcherService.overlayTrack(t, artist, curDur);
            }
            ourTitle = t;
            return;
        }
        // Schutz gegen Endlosschleife (z. B. Werbung, die vor jedem Song kommt)
        foreignInRow++;
        if (foreignInRow > 2) {
            log(this, "fremd (" + t + "), aber schon " + foreignInRow + "x hintereinander: lasse laufen (Werbung?)");
            unmute();
            return;
        }
        log(this, "fremd: " + t + " / " + artist + " -> stumm + nächster Song");
        mute();
        HomeWatcherService.overlaySwitching();
        if (since < 3000) return;
        pos++;
        play(this);
    }

    /** Welcher Rapper singt? (für Mix/Favoriten und den Wochen-Rückblick) */
    static String whoSings(String title, String artist) {
        for (String m : members) {
            String n = norm(m);
            if (hasWord(artist, n) || hasWord(title, n)) return m;
        }
        return members.length > 0 ? members[0] : rapperName;
    }

    /** Video-ID des Songs, der gerade läuft. */
    static String currentId() {
        return queue.isEmpty() || pos >= queue.size() ? null : queue.get(pos);
    }

    /** ▼: aktueller Song als Favorit an/aus. */
    static boolean toggleFavorite(Context c) {
        String id = currentId();
        if (id == null) return false;
        return Library.toggleFavorite(c, id, ourTitle, ourTitle == null ? rapperName : whoSings(ourTitle, null));
    }

    /** ▲: Song nie wieder spielen und gleich weiter. */
    static void blockCurrent(Context c) {
        String id = currentId();
        if (id == null) return;
        Library.block(c, id);
        log(c, "gesperrt: " + id + " " + ourTitle);
        queue.remove(pos);
        if (queue.isEmpty()) return;
        HomeWatcherService.overlaySwitching();
        play(c);
    }

    static String norm(String x) {
        if (x == null) return "";
        return x.toLowerCase()
                .replaceAll("[\\(\\[][^\\)\\]]*[\\)\\]]", " ")
                .replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Ganzes Wort: "che" passt zu "che - Bae", aber nicht zu "Cherry". */
    static boolean hasWord(String text, String normName) {
        if (text == null || normName.isEmpty()) return false;
        return (" " + norm(text) + " ").contains(" " + normName + " ");
    }

    /** "Ken Carson - margiela (Official Music Video)" und "margiela" sind derselbe Song. */
    static boolean sameSong(String a, String b) {
        String x = norm(a), y = norm(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        return (y.length() >= 6 && x.contains(y)) || (x.length() >= 6 && y.contains(x));
    }
}
