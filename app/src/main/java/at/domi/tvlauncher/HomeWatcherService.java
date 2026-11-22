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
    /** Zeitpunkt, an dem der Bildschirm ausging: kurz danach nie umleiten, sonst weckt das den Fernseher wieder auf. */
    private long screenOffAt = -100000;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                screenOffAt = SystemClock.elapsedRealtime();
                h.removeCallbacksAndMessages(null);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(power, f);
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(power); } catch (Exception ignored) { }
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

    @Override
    public void onInterrupt() { }
}
