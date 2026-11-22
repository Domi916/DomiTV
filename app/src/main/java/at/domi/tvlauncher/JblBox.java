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

    static void connect(Context ctx) {
        try {
            final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null) return;
            if (!bt.isEnabled()) bt.enable();
            BluetoothDevice box = null;
            for (BluetoothDevice d : bt.getBondedDevices()) {
                String n = d.getName();
                if (n != null && n.toUpperCase().contains("JBL")) { box = d; break; }
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
