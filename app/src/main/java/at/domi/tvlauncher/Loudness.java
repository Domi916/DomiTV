package at.domi.tvlauncher;

import android.media.audiofx.DynamicsProcessing;
import android.util.Log;

/**
 * Lautstärke-Ausgleich während Musik läuft: leise Songs etwas lauter, laute etwas leiser
 * (Kompressor + Begrenzer auf der Gesamt-Ausgabe). Klappt nur, wenn Fire OS das erlaubt.
 */
final class Loudness {
    static DynamicsProcessing dp;

    static void on() {
        if (dp != null) return;
        try {
            DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                    false, 0, true, 1, false, 0, true).build();
            dp = new DynamicsProcessing(0, 0, cfg);
            DynamicsProcessing.MbcBand b = dp.getMbcBandByChannelIndex(0, 0);
            b.setEnabled(true);
            b.setAttackTime(15f);
            b.setReleaseTime(250f);
            b.setRatio(3f);
            b.setThreshold(-26f);
            b.setKneeWidth(8f);
            b.setPostGain(7f);
            dp.setMbcBandAllChannelsTo(0, b);
            DynamicsProcessing.Limiter lim = dp.getLimiterByChannelIndex(0);
            lim.setEnabled(true);
            lim.setThreshold(-1.5f);
            lim.setRatio(10f);
            lim.setAttackTime(2f);
            lim.setReleaseTime(120f);
            dp.setLimiterAllChannelsTo(lim);
            dp.setEnabled(true);
            Log.i("DomiTV", "Lautstärke-Ausgleich an");
        } catch (Throwable e) {
            Log.e("DomiTV", "Lautstärke-Ausgleich nicht möglich", e);
            dp = null;
        }
    }

    static void off() {
        try { if (dp != null) { dp.setEnabled(false); dp.release(); } } catch (Throwable ignored) { }
        dp = null;
    }
}
