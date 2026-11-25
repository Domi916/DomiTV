package at.domi.tvlauncher;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Verbindet die (schon einmal gekoppelte) JBL-Box per Bluetooth,
 * bevor ein Rapper-Mix startet. Klappt es nicht, läuft der Ton einfach über den Fernseher.
 *
 * Alle Bluetooth-Aufrufe laufen in einem eigenen Thread: Hängt das Bluetooth des Sticks
 * (z. B. mitten im Verbinden), darf das nie die Anzeige und die Fernbedienung einfrieren.
 */
final class JblBox {
    static final String TAG = "DomiTV";

    /** true, solange ein Rapper-Zufallsmix läuft: nur dann darf die Box verbunden sein. */
    static volatile boolean wanted = false;
    /** Über den Box-Knopf auf der Startseite verbunden: bleibt verbunden, bis du ihn wieder drückst. */
    static volatile boolean manual = false;
    /** Aktueller Zustand (vom Bluetooth-System gemeldet). */
    static volatile boolean connected = false;
    /** true, solange vor dem Start auf die Box gewartet wird. */
    static volatile boolean waiting = false;
    /** Wann der Song nach dem Warten gestartet wurde und ob die Box da schon verbunden war. */
    static volatile long goAt = 0;
    static volatile boolean boxAtGo = false;

    static final Handler main = new Handler(Looper.getMainLooper());
    static Handler bg;

    /** Eigener Thread für alle Bluetooth-Aufrufe. */
    static synchronized Handler bg() {
        if (bg == null) {
            HandlerThread t = new HandlerThread("DomiTV-Bluetooth");
            t.start();
            bg = new Handler(t.getLooper());
        }
        return bg;
    }

    static boolean isJbl(BluetoothDevice d) {
        String n = d == null ? null : d.getName();
        return n != null && n.toUpperCase().contains("JBL");
    }

    interface WithProxy { void run(BluetoothAdapter bt, BluetoothProfile proxy); }

    /** A2DP-Zugang holen und die Arbeit damit im Bluetooth-Thread erledigen. */
    static void withA2dp(final Context ctx, final boolean enable, final WithProxy work, final Runnable failed) {
        bg().post(new Runnable() {
            public void run() {
                try {
                    final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
                    if (bt == null) { if (failed != null) failed.run(); return; }
                    if (!bt.isEnabled()) {
                        if (!enable) { if (failed != null) failed.run(); return; }
                        bt.enable();
                    }
                    boolean ok = bt.getProfileProxy(ctx.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                        @Override
                        public void onServiceConnected(int profile, final BluetoothProfile proxy) {
                            // kommt im Haupt-Thread an: sofort in den Bluetooth-Thread weitergeben
                            bg().post(new Runnable() {
                                public void run() {
                                    try { work.run(bt, proxy); } catch (Throwable e) {
                                        Log.e(TAG, "JBL", e);
                                        if (failed != null) failed.run();
                                    }
                                }
                            });
                        }

                        @Override
                        public void onServiceDisconnected(int profile) { }
                    }, BluetoothProfile.A2DP);
                    if (!ok && failed != null) failed.run();
                } catch (Throwable e) {
                    Log.e(TAG, "Bluetooth nicht verfügbar", e);
                    if (failed != null) failed.run();
                }
            }
        });
    }

    static BluetoothDevice bondedBox(BluetoothAdapter bt) {
        try {
            for (BluetoothDevice d : bt.getBondedDevices()) if (isJbl(d)) return d;
        } catch (Throwable ignored) { }
        return null;
    }

    static void close(BluetoothAdapter bt, BluetoothProfile proxy) {
        try { bt.closeProfileProxy(BluetoothProfile.A2DP, proxy); } catch (Throwable ignored) { }
    }

    /** Box trennen (z. B. wenn du zurück in Domi TV bist oder sie sich von selbst verbindet). */
    static void disconnect(Context ctx) {
        withA2dp(ctx, false, new WithProxy() {
            public void run(BluetoothAdapter bt, BluetoothProfile proxy) {
                try {
                    for (BluetoothDevice d : proxy.getConnectedDevices()) {
                        if (!isJbl(d)) continue;
                        Method m = BluetoothA2dp.class.getMethod("disconnect", BluetoothDevice.class);
                        Log.i(TAG, "JBL trennen: " + m.invoke(proxy, d));
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "JBL trennen fehlgeschlagen", e);
                } finally {
                    close(bt, proxy);
                }
            }
        }, null);
    }

    /**
     * Box verbinden und erst DANN weitermachen (z. B. YouTube starten).
     * Grund: Wechselt der Ton mitten im Lied vom Fernseher zur Box, bleibt YouTube hängen.
     * Ist die Box aus oder nicht gekoppelt, geht es nach kurzer Zeit ohne Box weiter.
     * then läuft immer im Haupt-Thread, genau einmal.
     */
    static void connectThen(final Context ctx, final Runnable then) {
        final boolean[] done = {false};
        final Runnable go = new Runnable() {
            public void run() {
                if (done[0]) return;
                done[0] = true;
                waiting = false;
                goAt = SystemClock.elapsedRealtime();
                boxAtGo = connected;
                then.run();
            }
        };
        final Runnable goNow = new Runnable() { public void run() { main.post(go); } };
        main.postDelayed(go, 17000); // Sicherheit: spätestens dann ohne Box weiter
        withA2dp(ctx, false, new WithProxy() {
            public void run(BluetoothAdapter bt, final BluetoothProfile proxy) {
                final BluetoothDevice target = bondedBox(bt);
                if (target == null) { goNow.run(); return; }
                if (proxy.getConnectedDevices().contains(target)) {
                    connected = true;
                    close(bt, proxy);
                    goNow.run();
                    return;
                }
                wanted = true;
                waiting = true;
                try {
                    Method m = BluetoothA2dp.class.getMethod("connect", BluetoothDevice.class);
                    Log.i(TAG, "JBL verbinden (vor dem Start): " + m.invoke(proxy, target));
                } catch (Throwable e) {
                    Log.e(TAG, "JBL verbinden", e);
                    close(bt, proxy);
                    goNow.run();
                    return;
                }
                HomeWatcherService.overlayStatus("JBL-Box verbindet …");
                // warten, bis sie verbunden ist (höchstens 15 Sekunden).
                // Ist die Box aus, meldet Bluetooth nach ein paar Sekunden "getrennt" -> dann sofort ohne Box.
                final long t0 = SystemClock.elapsedRealtime();
                final boolean[] sawConnecting = {false};
                final BluetoothAdapter adapter = bt;
                bg().post(new Runnable() {
                    public void run() {
                        if (done[0]) { close(adapter, proxy); return; }
                        int st = -1;
                        try { st = proxy.getConnectionState(target); } catch (Throwable ignored) { }
                        long dt = SystemClock.elapsedRealtime() - t0;
                        if (st == BluetoothProfile.STATE_CONNECTED) {
                            connected = true;
                            MediaWatcher.log(ctx, "JBL-Box verbunden nach " + dt / 100 / 10f + " s");
                            HomeWatcherService.overlayStatus("Lädt …");
                            close(adapter, proxy);
                            main.postDelayed(go, 1200);   // Ton-Weg kurz zur Ruhe kommen lassen
                            return;
                        }
                        if (st == BluetoothProfile.STATE_CONNECTING) sawConnecting[0] = true;
                        boolean failed = sawConnecting[0] && st == BluetoothProfile.STATE_DISCONNECTED && dt > 2500;
                        if (failed || dt > 15000) {
                            MediaWatcher.log(ctx, "JBL-Box nicht erreichbar (" + (failed ? "aus/weg" : "Zeit um") + "), ohne Box weiter");
                            HomeWatcherService.overlayStatus("Lädt …");
                            close(adapter, proxy);
                            goNow.run();
                            return;
                        }
                        bg().postDelayed(this, 300);
                    }
                });
            }
        }, goNow);
    }

    static void connect(Context ctx) {
        wanted = true;
        withA2dp(ctx, true, new WithProxy() {
            public void run(BluetoothAdapter bt, BluetoothProfile proxy) {
                try {
                    BluetoothDevice target = bondedBox(bt);
                    if (target == null) { Log.i(TAG, "Keine gekoppelte JBL-Box gefunden"); return; }
                    if (!proxy.getConnectedDevices().contains(target)) {
                        Method m = BluetoothA2dp.class.getMethod("connect", BluetoothDevice.class);
                        Log.i(TAG, "JBL verbinden: " + m.invoke(proxy, target));
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "JBL-Verbindung fehlgeschlagen", e);
                } finally {
                    close(bt, proxy);
                }
            }
        }, null);
    }
}
