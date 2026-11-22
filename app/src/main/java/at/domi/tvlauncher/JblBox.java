package at.domi.tvlauncher;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Verbindet die (schon einmal gekoppelte) JBL-Box per Bluetooth,
 * bevor ein Rapper-Mix startet. Klappt es nicht, läuft der Ton einfach über den Fernseher.
 */
final class JblBox {
    static final String TAG = "DomiTV";

    /** true, solange ein Rapper-Zufallsmix läuft: nur dann darf die Box verbunden sein. */
    static volatile boolean wanted = false;
    /** Über den Box-Knopf auf der Startseite verbunden: bleibt verbunden, bis du ihn wieder drückst. */
    static volatile boolean manual = false;
    /** Aktueller Zustand (vom Bluetooth-System gemeldet). */
    static volatile boolean connected = false;

    static boolean isJbl(BluetoothDevice d) {
        String n = d == null ? null : d.getName();
        return n != null && n.toUpperCase().contains("JBL");
    }

    /** Box trennen (z. B. wenn du zurück in Domi TV bist oder sie sich von selbst verbindet). */
    static void disconnect(Context ctx) {
        try {
            final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null || !bt.isEnabled()) return;
            bt.getProfileProxy(ctx.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    try {
                        for (BluetoothDevice d : proxy.getConnectedDevices()) {
                            if (!isJbl(d)) continue;
                            Method m = BluetoothA2dp.class.getMethod("disconnect", BluetoothDevice.class);
                            Log.i(TAG, "JBL trennen: " + m.invoke(proxy, d));
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, "JBL trennen fehlgeschlagen", e);
                    } finally {
                        try { bt.closeProfileProxy(BluetoothProfile.A2DP, proxy); } catch (Throwable ignored) { }
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) { }
            }, BluetoothProfile.A2DP);
        } catch (Throwable e) {
            Log.e(TAG, "Bluetooth nicht verfügbar", e);
        }
    }

    /**
     * Box verbinden und erst DANN weitermachen (z. B. YouTube starten).
     * Grund: Wechselt der Ton mitten im Lied vom Fernseher zur Box, bleibt YouTube hängen.
     * Ist die Box aus oder nicht gekoppelt, geht es nach kurzer Zeit ohne Box weiter.
     */
    static void connectThen(final Context ctx, final Runnable then) {
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] done = {false};
        final Runnable go = new Runnable() {
            public void run() {
                if (done[0]) return;
                done[0] = true;
                then.run();
            }
        };
        try {
            final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice box = null;
            if (bt != null && bt.isEnabled()) for (BluetoothDevice d : bt.getBondedDevices()) if (isJbl(d)) { box = d; break; }
            if (box == null) { go.run(); return; }
            final BluetoothDevice target = box;
            bt.getProfileProxy(ctx.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, final BluetoothProfile proxy) {
                    try {
                        if (proxy.getConnectedDevices().contains(target)) {
                            connected = true;
                            h.post(go);
                        } else {
                            wanted = true;
                            Method m = BluetoothA2dp.class.getMethod("connect", BluetoothDevice.class);
                            Log.i(TAG, "JBL verbinden (vor dem Start): " + m.invoke(proxy, target));
                            // warten, bis sie verbunden ist (höchstens 7 Sekunden)
                            final long t0 = android.os.SystemClock.elapsedRealtime();
                            h.post(new Runnable() {
                                public void run() {
                                    if (done[0]) return;
                                    boolean on = false;
                                    try { on = proxy.getConnectedDevices().contains(target); } catch (Throwable ignored) { }
                                    if (on) { connected = true; h.postDelayed(go, 700); return; }
                                    if (android.os.SystemClock.elapsedRealtime() - t0 > 7000) { Log.i(TAG, "JBL nicht erreichbar, ohne Box weiter"); go.run(); return; }
                                    h.postDelayed(this, 300);
                                }
                            });
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, "JBL", e);
                        h.post(go);
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) { }
            }, BluetoothProfile.A2DP);
            h.postDelayed(go, 9000); // Sicherheit
        } catch (Throwable e) {
            go.run();
        }
    }

    static void connect(Context ctx) {
        wanted = true;
        try {
            final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null) return;
            if (!bt.isEnabled()) bt.enable();
            BluetoothDevice box = null;
            for (BluetoothDevice d : bt.getBondedDevices()) {
                if (isJbl(d)) { box = d; break; }
            }
            if (box == null) { Log.i(TAG, "Keine gekoppelte JBL-Box gefunden"); return; }
            final BluetoothDevice target = box;
            bt.getProfileProxy(ctx.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    try {
                        List<BluetoothDevice> on = proxy.getConnectedDevices();
                        if (!on.contains(target)) {
                            Method m = BluetoothA2dp.class.getMethod("connect", BluetoothDevice.class);
                            Object ok = m.invoke(proxy, target);
                            Log.i(TAG, "JBL verbinden: " + ok);
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, "JBL-Verbindung fehlgeschlagen", e);
                    } finally {
                        try { bt.closeProfileProxy(BluetoothProfile.A2DP, proxy); } catch (Throwable ignored) { }
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) { }
            }, BluetoothProfile.A2DP);
        } catch (Throwable e) {
            Log.e(TAG, "Bluetooth nicht verfügbar", e);
        }
    }
}
