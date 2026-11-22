package at.domi.tvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Beobachtet, welches Fenster gerade im Vordergrund ist.
 * Sobald die Amazon-Startseite erscheint (Home-Taste, Einschalten, Neustart),
 * wird sofort Domi TV geöffnet. Die Amazon-Einstellungen bleiben unberührt.
 */
public class HomeWatcherService extends AccessibilityService {

    static final String TAG = "DomiTV";
    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";

    /** true, wenn du die Amazon-Startseite bewusst über Domi TV geöffnet hast. */
    static volatile boolean allowAmazonHome = false;

    private long lastLaunch = 0;

    // ---------- Songtext-/Audio-Anzeige über YouTube ----------
    static HomeWatcherService instance;
    LyricsOverlay overlay;

    static void overlayShow(final int rapper) {
        final HomeWatcherService s = instance;
        if (s == null) return;
        s.h.post(new Runnable() { public void run() {
            if (s.overlay == null) s.overlay = new LyricsOverlay(s);
            s.overlay.show(rapper);
        } });
    }

    static void overlayHide() {
        final HomeWatcherService s = instance;
        if (s == null) return;
        s.h.post(new Runnable() { public void run() { if (s.overlay != null) s.overlay.hide(); } });
    }

    static void overlayTrack(final String title, final String artist, final long dur) {
        final HomeWatcherService s = instance;
        if (s == null) return;
        s.h.post(new Runnable() { public void run() { if (s.overlay != null) s.overlay.track(title, artist, dur); } });
    }

    static void overlaySwitching() {
        final HomeWatcherService s = instance;
        if (s == null) return;
        s.h.post(new Runnable() { public void run() { if (s.overlay != null) s.overlay.switching(); } });
    }
    /** Zeitpunkt, an dem der Bildschirm ausging: kurz danach nie umleiten, sonst weckt das den Fernseher wieder auf. */
    private long screenOffAt = -100000;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if ("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED".equals(i.getAction())) {
                // Box hat sich von selbst verbunden, aber es läuft kein Rapper-Mix: wieder trennen
                int st = i.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                android.bluetooth.BluetoothDevice d = i.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                if (st == 2 && JblBox.isJbl(d) && !JblBox.wanted) {
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() { if (!JblBox.wanted) JblBox.disconnect(HomeWatcherService.this); }
                    }, 1500);
                }
                return;
            }
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                screenOffAt = SystemClock.elapsedRealtime();
                h.removeCallbacksAndMessages(null);
                JblBox.wanted = false;
            }
        }
    };

    /** Nur zum Testen im Emulator: Anzeige mit einem Song zeigen. */
    private final BroadcastReceiver test = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int r = i.getIntExtra("rapper", 0);
            String t = i.getStringExtra("title");
            if (overlay == null) overlay = new LyricsOverlay(HomeWatcherService.this);
            if ("hide".equals(t)) { overlay.hide(); return; }
            MediaWatcher.appCtx = getApplicationContext();
            MediaWatcher.active = true;
            MediaWatcher.rapperIndex = r;
            MediaWatcher.trackAt = SystemClock.elapsedRealtime() - i.getIntExtra("at", 0);
            MediaWatcher.lastPos = -1;
            overlay.show(r);
            overlay.track(t, i.getStringExtra("artist"), i.getLongExtra("dur", 0));
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
            Log.i(TAG, "Tastenfilter aktiv, flags=" + info.flags);
        } catch (Exception ex) { Log.e(TAG, "Tastenfilter", ex); }
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) registerReceiver(test, new IntentFilter("at.domi.tvlauncher.TEST_OVERLAY"));
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        registerReceiver(power, f);
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(power); } catch (Exception ignored) { }
        try { unregisterReceiver(test); } catch (Exception ignored) { }
        if (overlay != null) overlay.hide();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private boolean awake() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return false;
        return SystemClock.elapsedRealtime() - screenOffAt > 6000;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = event.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        Log.i(TAG, "Fenster: " + pkg + " / " + cls);

        if (pkg.equals(getPackageName())) {
            allowAmazonHome = false;
            return;
        }

        if (pkg.equals(AMAZON_LAUNCHER)) {
            if (allowAmazonHome) return;
            if (cls.contains("Settings")) return;
            if (!cls.contains("Home")) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastLaunch < 1200) return;
            if (!awake()) return; // Fernseher wird gerade ausgeschaltet: in Ruhe lassen
            lastLaunch = now;
            // kurz warten: Beim Ausschalten blitzt die Amazon-Startseite manchmal auf
            h.removeCallbacksAndMessages(null);
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!awake() || allowAmazonHome) return;
                    Intent i = new Intent(HomeWatcherService.this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    try {
                        startActivity(i);
                        Log.i(TAG, "Amazon-Startseite erkannt, Domi TV geöffnet");
                    } catch (Exception e) {
                        Log.e(TAG, "Konnte Domi TV nicht öffnen", e);
                    }
                }
            }, 700);
            return;
        }

        // Eine andere App ist offen (z. B. Netflix): ab jetzt wieder umleiten
        if (!pkg.equals("com.android.systemui") && !pkg.startsWith("com.amazon.tv.")) {
            allowAmazonHome = false;
        }
    }

    /** Während des Rapper-Shuffles: ⏩ (Vorspulen) springt zum nächsten Song des Rappers. */
    @Override
    protected boolean onKeyEvent(android.view.KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == android.view.KeyEvent.ACTION_UP) Log.i(TAG, "Taste " + k + " shuffle=" + MediaWatcher.active);
        if (MediaWatcher.active && (k == android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || k == android.view.KeyEvent.KEYCODE_MEDIA_NEXT)) {
            if (e.getAction() == android.view.KeyEvent.ACTION_UP) MediaWatcher.skip(this);
            return true;
        }
        // ☰ Menü-Taste: Anzeige wechseln (Songtext → Audio → Video)
        if (MediaWatcher.active && k == android.view.KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == android.view.KeyEvent.ACTION_UP) {
                if (overlay == null) overlay = new LyricsOverlay(this);
                overlay.cycleMode();
            }
            return true;
        }
        // Zurück während des Shuffles: Musik aus, zurück zu Domi TV
        if (MediaWatcher.active && k == android.view.KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == android.view.KeyEvent.ACTION_UP) {
                MediaWatcher.pauseYouTube();
                MediaWatcher.stop();
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                try { startActivity(i); } catch (Exception ignored) { }
            }
            return true;
        }
        // ⏪ (Zurückspulen) während des Shuffles: Shuffle aus, du kannst in YouTube frei wählen
        if (MediaWatcher.active && (k == android.view.KeyEvent.KEYCODE_MEDIA_REWIND
                || k == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
            if (e.getAction() == android.view.KeyEvent.ACTION_UP) {
                MediaWatcher.log(this, "Shuffle aus per ⏪");
                MediaWatcher.stop();
                android.widget.Toast.makeText(this, "Shuffle aus – du kannst jetzt frei wählen", android.widget.Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() { }
}
