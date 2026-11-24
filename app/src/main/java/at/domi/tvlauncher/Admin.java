package at.domi.tvlauncher;

import android.app.Activity;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Nur für den Wecker auf älteren Fire-TV-Versionen: darf den Stick schlafen legen ("Bildschirm sperren").
 * Ohne das wacht der Stick nicht "richtig" auf und der Fernseher bleibt aus.
 */
public class Admin extends DeviceAdminReceiver {

    static ComponentName me(Context c) { return new ComponentName(c, Admin.class); }

    static boolean active(Context c) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm != null && dpm.isAdminActive(me(c));
        } catch (Exception e) { return false; }
    }

    /** Stick schlafen legen. true = hat geklappt. */
    static boolean lock(Context c) {
        if (c == null || !active(c)) return false;
        try {
            ((DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE)).lockNow();
            return true;
        } catch (Exception e) { return false; }
    }

    /** Einmalig fragen (nur nötig, wenn der Stick die neue Methode nicht kann). */
    static boolean askIfNeeded(Activity a) {
        if (android.os.Build.VERSION.SDK_INT >= 28 || active(a)) return false;
        try {
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, me(a));
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Damit der Musik-Wecker den Fernseher einschalten kann, muss Domi TV den Stick kurz schlafen legen dürfen.");
            a.startActivity(i);
            return true;
        } catch (Exception e) { return false; }
    }
}
