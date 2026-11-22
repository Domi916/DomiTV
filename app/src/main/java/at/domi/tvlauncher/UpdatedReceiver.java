package at.domi.tvlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Nach einem Update startet Domi TV von selbst wieder. */
public class UpdatedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Log.i("DomiTV", "Update installiert, starte Domi TV");
        Intent start = new Intent(c, MainActivity.class);
        start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { c.startActivity(start); } catch (Exception e) { Log.e("DomiTV", "Neustart nach Update", e); }
    }
}
