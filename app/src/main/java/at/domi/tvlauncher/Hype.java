package at.domi.tvlauncher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Locale;

/**
 * Hype-Intro beim Start eines Rappers / einer Stimmung (~1,8 s):
 * Foto zoomt rein, drei Schläge (808-Kick, selbst erzeugt) – Name knallt rein, dann die Zeile darunter,
 * dann fliegt die Kamera ins Bild und YouTube startet.
 */
final class Hype {
    static final int RATE = 44100;
    static short[] kickPcm;
    static AudioTrack track;

    /** 808-Kick: Sinus, der von ~130 Hz auf 45 Hz fällt, mit Klick am Anfang. */
    static synchronized short[] pcm() {
        if (kickPcm != null) return kickPcm;
        int n = (int) (RATE * 0.42);
        short[] b = new short[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double f = 45 + 85 * Math.exp(-t * 26);
            ph += 2 * Math.PI * f / RATE;
            double env = Math.exp(-t * 7.0) * Math.min(1, t * 900);
            double v = Math.sin(ph) * env;
            if (t < 0.004) v += (Math.random() * 2 - 1) * 0.35 * (1 - t / 0.004);   // Klick
            v = Math.tanh(v * 1.6) / Math.tanh(1.6);                              // etwas Sättigung
            b[i] = (short) (v * 30000);
        }
        kickPcm = b;
        return b;
    }

    static void kick(final float vol) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    short[] p = pcm();
                    AudioTrack t = new AudioTrack(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                            new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                            p.length * 2, AudioTrack.MODE_STATIC, 0);
                    t.write(p, 0, p.length);
                    t.setVolume(vol);
                    t.play();
                    Thread.sleep(600);
                    t.release();
                } catch (Throwable ignored) { }
            }
        }).start();
    }

    static void play(final Activity a, final FrameLayout fx, int idx, String name, String sub, final int color, final Runnable done) {
        final float u = a.getResources().getDisplayMetrics().widthPixels / 1920f;
        final Handler h = new Handler(Looper.getMainLooper());
        final FrameLayout L = new FrameLayout(a);
        L.setBackgroundColor(Color.BLACK);
        L.setClipChildren(false);
        fx.addView(L, new FrameLayout.LayoutParams(-1, -1));

        // Foto (Rapper) oder Farbfläche (Stimmung)
        final ImageView ph = new ImageView(a);
        ph.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bm = null;
        if (idx >= 0) {
            try {
                InputStream in = a.getAssets().open("rap/" + idx + "_0.jpg");
                bm = BitmapFactory.decodeStream(in);
                in.close();
            } catch (Exception ignored) { }
        }
        if (bm != null) ph.setImageBitmap(bm);
        else ph.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                new int[]{(color & 0x00ffffff) | 0xcc000000, (color & 0x00ffffff) | 0x33000000, 0xff000000}));
        ph.setAlpha(0f);
        ph.setScaleX(1.35f);
        ph.setScaleY(1.35f);
        L.addView(ph, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(a);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55000000, 0x33000000, 0xdd000000}));
        L.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        // Farb-Glow, der bei jedem Schlag aufblitzt
        final View glow = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{(color & 0x00ffffff) | 0xaa000000, 0});
        g.setGradientRadius(900 * u);
        g.setGradientCenter(0.5f, 0.62f);
        glow.setBackground(g);
        glow.setAlpha(0f);
        L.addView(glow, new FrameLayout.LayoutParams(-1, -1));

        final TextView nm = new TextView(a);
        nm.setText(name.toUpperCase(Locale.ROOT));
        nm.setTextColor(Color.WHITE);
        nm.setTypeface(((MainActivity) a).font("Black"));
        nm.setLetterSpacing(-0.02f);
        nm.setSingleLine(true);
        nm.setGravity(Gravity.CENTER);
        // Größe so wählen, dass der Name sicher auf den Bildschirm passt
        float size = 190 * u;
        android.graphics.Paint tp = new android.graphics.Paint();
        tp.setTypeface(nm.getTypeface());
        tp.setTextSize(size);
        float w = tp.measureText(nm.getText().toString()) * 1.08f;
        if (w > 1680 * u) size *= 1680 * u / w;
        nm.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        nm.setShadowLayer(40 * u, 0, 0, (color & 0x00ffffff) | 0xcc000000);
        nm.setAlpha(0f);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, -2);
        nl.gravity = Gravity.CENTER_VERTICAL;
        nl.topMargin = (int) (60 * u);
        L.addView(nm, nl);
        final TextView sb = new TextView(a);
        sb.setText(sub);
        sb.setTextColor(color);
        sb.setTypeface(((MainActivity) a).font("Bold"));
        sb.setTextSize(TypedValue.COMPLEX_UNIT_PX, 38 * u);
        sb.setLetterSpacing(0.3f);
        sb.setGravity(Gravity.CENTER);
        sb.setSingleLine(true);
        sb.setAlpha(0f);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, -2);
        sl.gravity = Gravity.CENTER_VERTICAL;
        sl.topMargin = (int) (60 * u + size * 0.75f + 40 * u);
        L.addView(sb, sl);

        final View flash = new View(a);
        flash.setBackgroundColor(Color.WHITE);
        flash.setAlpha(0f);
        L.addView(flash, new FrameLayout.LayoutParams(-1, -1));

        final Runnable[] beat = new Runnable[3];
        // Schlag 1: Foto + Blitz
        beat[0] = new Runnable() {
            public void run() {
                kick(0.85f);
                ph.animate().alpha(1f).setDuration(160).start();
                ph.animate().scaleX(1.12f).scaleY(1.12f).setDuration(1700).setInterpolator(new DecelerateInterpolator(2f)).start();
                hit(flash, glow, 0.55f);
                shake(L, 26 * u);
            }
        };
        // Schlag 2: Name knallt rein
        beat[1] = new Runnable() {
            public void run() {
                kick(0.95f);
                nm.setScaleX(2.4f);
                nm.setScaleY(2.4f);
                nm.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190)
                        .setInterpolator(new OvershootInterpolator(1.4f)).start();
                hit(flash, glow, 0.35f);
                shake(L, 34 * u);
            }
        };
        // Schlag 3: Zeile darunter
        beat[2] = new Runnable() {
            public void run() {
                kick(0.7f);
                sb.setTranslationY(40 * u);
                sb.animate().alpha(1f).translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
                hit(flash, glow, 0.2f);
                shake(L, 14 * u);
            }
        };
        h.postDelayed(beat[0], 60);
        h.postDelayed(beat[1], 480);
        h.postDelayed(beat[2], 900);
        // Kamera fliegt ins Bild, dann schwarz → YouTube
        h.postDelayed(new Runnable() {
            public void run() {
                L.setPivotX(L.getWidth() / 2f);
                L.setPivotY(L.getHeight() * 0.55f);
                L.animate().scaleX(1.6f).scaleY(1.6f).alpha(0f).setDuration(480)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator(1.6f)).start();
            }
        }, 1420);
        h.postDelayed(done, 1880);
    }

    static void hit(View flash, View glow, float f) {
        flash.animate().cancel();
        flash.setAlpha(f);
        flash.animate().alpha(0f).setDuration(260).start();
        glow.animate().cancel();
        glow.setAlpha(1f);
        glow.animate().alpha(0.35f).setDuration(420).start();
    }

    static void shake(final View v, final float amp) {
        final long t0 = android.os.SystemClock.uptimeMillis();
        v.post(new Runnable() {
            public void run() {
                long t = android.os.SystemClock.uptimeMillis() - t0;
                if (t > 180) { v.setTranslationX(0); v.setTranslationY(0); return; }
                float k = amp * (1f - t / 180f);
                v.setTranslationX((float) (Math.random() * 2 - 1) * k);
                v.setTranslationY((float) (Math.random() * 2 - 1) * k * 0.6f);
                v.postOnAnimation(this);
            }
        });
    }
}
