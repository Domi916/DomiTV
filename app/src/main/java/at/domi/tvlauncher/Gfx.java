package at.domi.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/** Zeichen-Hilfen: alles, was teuer ist, wird einmal in ein Bild gezeichnet. */
final class Gfx {

    static int a(int color, float alpha) {
        return Color.argb(Math.round(alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    static int w(float alpha) { return a(Color.WHITE, alpha); }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static GradientDrawable grad(GradientDrawable.Orientation o, int[] colors, float radius) {
        GradientDrawable d = new GradientDrawable(o, colors);
        d.setCornerRadius(radius);
        return d;
    }

    /** Weicher, farbiger Lichtfleck (für das Leuchten unter der gewählten Kachel). */
    /** Kleines Rauschbild für Filmkorn. */
    static Bitmap grain(int n) {
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        int[] p = new int[n * n];
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < p.length; i++) { int g = r.nextInt(256); p[i] = 0xff000000 | (g << 16) | (g << 8) | g; }
        b.setPixels(p, 0, n, 0, 0, n, n);
        return b;
    }

    static Bitmap glow(int color, float alpha) {
        int W = 290, H = 210;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(W / 2f, H / 2f, H / 2f,
                new int[]{a(color, alpha), a(color, alpha * 0.45f), a(color, 0)}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        c.save();
        c.scale(W / (float) H, 1, W / 2f, H / 2f);
        c.drawCircle(W / 2f, H / 2f, H / 2f, p);
        c.restore();
        return b;
    }

    /** Leuchtender Punkt (Startanimation). */
    static Bitmap dot(int color) {
        int S = 90;
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(S / 2f, S / 2f, S / 2f,
                new int[]{Color.WHITE, a(color, 0.95f), a(color, 0.35f), a(color, 0)},
                new float[]{0, 0.12f, 0.35f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(S / 2f, S / 2f, S / 2f, p);
        return b;
    }

    /** Lichtkegel über der gewählten Kachel. */
    static Bitmap beam() {
        int W = 360, H = 540;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, H, new int[]{w(0), w(0.025f), w(0.075f), w(0)},
                new float[]{0, 0.6f, 0.85f, 1}, Shader.TileMode.CLAMP));
        p.setMaskFilter(new BlurMaskFilter(10, BlurMaskFilter.Blur.NORMAL));
        Path path = new Path();
        path.moveTo(165, 0); path.lineTo(195, 0); path.lineTo(310, H); path.lineTo(50, H); path.close();
        c.drawPath(path, p);
        return b;
    }

    /** Leuchtender Ring (YT Music). */
    static Bitmap pinkRing() {
        int S = 560;
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(10);
        g.setColor(Color.argb(90, 255, 58, 120));
        g.setMaskFilter(new BlurMaskFilter(12, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(S / 2f, S / 2f, 266, g);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        p.setColor(Color.argb(170, 255, 130, 175));
        c.drawCircle(S / 2f, S / 2f, 266, p);
        return b;
    }

    /** Farbverlaufs-Ring für die Startanimation. */
    static Bitmap introRing() {
        int S = 340;
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        base.setStyle(Paint.Style.STROKE);
        base.setStrokeWidth(2);
        base.setColor(w(0.06f));
        c.drawCircle(170, 170, 150, base);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setShader(new SweepGradient(170, 170, new int[]{
                0xffe01b2a, 0xffff3a78, 0xff5a7aff, 0xff1aa6dc, 0xffe8e8f0, 0xffe01b2a}, null));
        c.save();
        c.rotate(-90, 170, 170);
        c.drawCircle(170, 170, 150, p);
        c.restore();
        return b;
    }

    /** Weicher, senkrechter Lichtstreifen (Netflix-Hintergrund). */
    static Bitmap streak(int color) {
        int W = 80, H = 600;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, H, new int[]{a(color, 0), a(color, 0.85f), a(color, 0)},
                new float[]{0, 0.42f, 1}, Shader.TileMode.CLAMP));
        p.setMaskFilter(new BlurMaskFilter(16, BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(new android.graphics.RectF(26, 10, 54, H - 10), 14, 14, p);
        return b;
    }

    /** Umriss-Schrift (riesiger Rapper-Name im Hintergrund). */
    static class OutlineText extends TextView {
        OutlineText(Context c) {
            super(c);
            getPaint().setStyle(Paint.Style.STROKE);
            getPaint().setStrokeWidth(3);
        }

        void stroke(float px) { getPaint().setStrokeWidth(px); invalidate(); }
    }

    /** Text mit Verlauf von weiß nach halbtransparent (große App-Namen). */
    static class GradientText extends TextView {
        GradientText(Context c) { super(c); }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            getPaint().setShader(new LinearGradient(0, 0, 0, h, new int[]{Color.WHITE, Color.WHITE, w(0.5f)},
                    new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
        }
    }

    /** Kleine Symbole: Haus, Regler, Pfeil, Play. */
    static class Icon extends View {
        static final int HOME = 0, SETTINGS = 1, CHEVRON = 2, PLAY = 3, SPEAKER = 4, PARTY = 5, ALARM = 6;
        final int type;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Icon(Context c, int type) {
            super(c);
            this.type = type;
            p.setStyle(type == PLAY ? Paint.Style.FILL : Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            setColor(w(0.92f));
        }

        void setColor(int col) { p.setColor(col); invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 24f;
            c.save();
            c.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
            c.scale(s, s);
            p.setStrokeWidth(type == CHEVRON ? 2.2f : 1.8f);
            path.reset();
            if (type == HOME) {
                path.moveTo(4, 10.5f); path.lineTo(12, 4); path.lineTo(20, 10.5f); path.lineTo(20, 19.5f);
                path.lineTo(14.5f, 19.5f); path.lineTo(14.5f, 14); path.lineTo(9.5f, 14); path.lineTo(9.5f, 19.5f);
                path.lineTo(4, 19.5f); path.close();
                c.drawPath(path, p);
            } else if (type == SETTINGS) {
                c.drawLine(4, 7, 13, 7, p); c.drawLine(17, 7, 20, 7, p);
                c.drawLine(4, 17, 7, 17, p); c.drawLine(11, 17, 20, 17, p);
                c.drawCircle(15, 7, 2, p); c.drawCircle(9, 17, 2, p);
            } else if (type == PARTY) {
                // Party: großer Funkel-Stern + kleiner Stern
                path.moveTo(10, 3); path.quadTo(11, 10, 17, 11); path.quadTo(11, 12, 10, 19);
                path.quadTo(9, 12, 3, 11); path.quadTo(9, 10, 10, 3); path.close();
                c.drawPath(path, p);
                path.reset();
                path.moveTo(18.5f, 14.5f); path.quadTo(19, 17, 21, 17.5f); path.quadTo(19, 18, 18.5f, 20.5f);
                path.quadTo(18, 18, 16, 17.5f); path.quadTo(18, 17, 18.5f, 14.5f); path.close();
                c.drawPath(path, p);
            } else if (type == SPEAKER) {
                // Box: Gehäuse mit zwei Lautsprechern
                android.graphics.RectF r = new android.graphics.RectF(6, 3.5f, 18, 20.5f);
                c.drawRoundRect(r, 3, 3, p);
                c.drawCircle(12, 14.5f, 3.2f, p);
                c.drawCircle(12, 7.8f, 1.2f, p);
            } else if (type == ALARM) {
                // Wecker: Zifferblatt mit zwei Glocken
                c.drawCircle(12, 13, 7, p);
                c.drawLine(12, 9.5f, 12, 13, p); c.drawLine(12, 13, 14.8f, 14.5f, p);
                c.drawLine(3.8f, 7, 7, 4, p); c.drawLine(20.2f, 7, 17, 4, p);
            } else if (type == CHEVRON) {
                path.moveTo(6, 9); path.lineTo(12, 15); path.lineTo(18, 9);
                c.drawPath(path, p);
            } else {
                path.moveTo(7, 4.5f); path.lineTo(20, 12); path.lineTo(7, 19.5f); path.close();
                c.drawPath(path, p);
            }
            c.restore();
        }
    }
}
