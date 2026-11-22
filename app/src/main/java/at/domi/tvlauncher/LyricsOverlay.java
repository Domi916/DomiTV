package at.domi.tvlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domi-TV-Anzeige über YouTube während des Rapper-Shuffles:
 *  SONGTEXT – Foto + mitlaufender Songtext
 *  AUDIO    – großes Foto + Titel + Fortschritt
 *  VIDEO    – nichts drüber, YouTube-Video
 * ☰ wechselt die Anzeige, OK = Pause, ◀ ▶ vorheriger/nächster Song.
 */
final class LyricsOverlay {
    static final String TAG = "DomiTV";
    static final int LYRICS = 0, AUDIO = 1, VIDEO = 2;
    static final String[] MODE_NAMES = {"Songtext", "Audio", "Video"};
    static final PathInterpolator EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    final Context c;
    final WindowManager wm;
    final Handler ui = new Handler(Looper.getMainLooper());
    final SharedPreferences prefs;
    FrameLayout root, badgeHost;
    ImageView photo, glow, glow2;
    View scrim;
    TextView label, title, status, hint, modeBadge, timeL, timeR, pauseIcon;
    View barBg, barFill;
    LinearLayout lyricBox;      // alle Zeilen untereinander, gleitet nach oben
    FrameLayout lyricClip;      // Bereich, in dem der Songtext läuft
    static final float ANCHOR = 470; // Höhe der aktuellen Zeile (Design-Pixel)
    static final PathInterpolator SMOOTH = new PathInterpolator(0.42f, 0f, 0.18f, 1f);
    boolean added = false;
    int mode;
    int rapper = 0;
    int accent = Color.WHITE;
    ValueAnimator breathe, zoom;

    // Songtext
    final List<Long> times = new ArrayList<>();
    final List<String> texts = new ArrayList<>();
    String plain = null;
    long offset = 0;
    long shift;
    int shownLine = -2;
    int request = 0;
    String currentTitle = "";
    boolean approx = false;
    boolean wasPlaying = true;
    // Songtext-Schoner: nach 20 s ohne Tastendruck wird der Songtext riesig
    long lastKey = android.os.SystemClock.elapsedRealtime();
    boolean big = false;

    void poke() {
        lastKey = android.os.SystemClock.elapsedRealtime();
        if (big) setBig(false);
    }

    void setBig(boolean on) {
        big = on;
        long d = on ? 1400 : 500;
        float a = on ? 0f : 1f;
        for (View v : new View[]{label, title, status, hint, barBg, barFill, timeL, timeR})
            if (v != null) v.animate().alpha(a).setDuration(d).start();
        photo.animate().alpha(on ? 0.35f : 1f).setDuration(d).start();
        lyricClip.setPivotX(px(140));
        lyricClip.setPivotY(px(ANCHOR));
        float sc = on ? 1.22f : 1f;
        lyricClip.animate().scaleX(sc).scaleY(sc).setDuration(d).setInterpolator(EASE).start();
    }

    LyricsOverlay(Context c) {
        this.c = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        prefs = c.getSharedPreferences("domitv", Context.MODE_PRIVATE);
        mode = prefs.getInt("anzeige", LYRICS);
        shift = 0; // Songtext läuft immer live mit, kein manuelles Verschieben
    }

    float px(float v) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return Math.max(dm.widthPixels, dm.heightPixels) / 1920f * v;
    }

    Typeface font(String w) {
        try { return Typeface.createFromAsset(c.getAssets(), "fonts/Inter-" + w + ".otf"); }
        catch (Exception e) { return Typeface.DEFAULT_BOLD; }
    }

    TextView text(float size, String w, int color) {
        TextView t = new TextView(c);
        t.setTextColor(color);
        t.setTypeface(font(w));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setIncludeFontPadding(false);
        return t;
    }

    FrameLayout.LayoutParams at(float x, float y, float w, float h) {
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(w < 0 ? (int) w : (int) px(w), h < 0 ? (int) h : (int) px(h));
        l.leftMargin = (int) px(x);
        l.topMargin = (int) px(y);
        return l;
    }

    void build() {
        root = new FrameLayout(c);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(true);
        root.setClipChildren(false);

        // Foto rechts, leicht zoomend
        photo = new ImageView(c);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        // farbiges Leuchten in der Farbe des Rappers
        glow = new ImageView(c);
        glow.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(glow, at(-500, -300, 1700, 1300));
        glow2 = new ImageView(c);
        glow2.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(glow2, at(1000, 520, 1300, 900));
        scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF5000000, 0xE6000000, 0x99000000, 0x33000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF0000000, 0x00000000}));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, (int) px(420));
        bl.gravity = Gravity.BOTTOM;
        root.addView(bottom, bl);
        // feines Korn gegen Banding
        View grain = new View(c);
        grain.setBackground(new android.graphics.drawable.BitmapDrawable(c.getResources(), Gfx.grain(256)) {{
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        }});
        grain.setAlpha(0.05f);
        root.addView(grain, new FrameLayout.LayoutParams(-1, -1));

        // Kopfzeile oben links
        label = text(22, "Bold", Color.WHITE);
        label.setLetterSpacing(0.18f);
        root.addView(label, at(140, 96, -2, -2));

        // Songtext: alle Zeilen in einer Liste, die weich nach oben gleitet (wie Apple Music)
        lyricClip = new FrameLayout(c);
        lyricClip.setClipChildren(true);
        root.addView(lyricClip, new FrameLayout.LayoutParams(-1, -1));
        lyricBox = new LinearLayout(c);
        lyricBox.setOrientation(LinearLayout.VERTICAL);
        lyricClip.addView(lyricBox, at(140, 0, 1300, -2));


        // Titel + Fortschritt unten
        title = text(56, "Black", Color.WHITE);
        title.setSingleLine(true);
        title.setLetterSpacing(-0.02f);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(title, at(140, 852, 1150, -2));
        status = text(22, "Medium", 0x99FFFFFF);
        root.addView(status, at(140, 930, 1000, -2));
        barBg = new View(c);
        barBg.setBackground(roundRect(0x33FFFFFF, px(3)));
        root.addView(barBg, at(140, 976, 1640, 6));
        barFill = new View(c);
        barFill.setPivotX(0);
        root.addView(barFill, at(140, 976, 1640, 6));
        timeL = text(20, "SemiBold", 0x99FFFFFF);
        timeL.setFontFeatureSettings("tnum");
        root.addView(timeL, at(140, 996, -2, -2));
        timeR = text(20, "SemiBold", 0x99FFFFFF);
        timeR.setFontFeatureSettings("tnum");
        timeR.setGravity(Gravity.END);
        root.addView(timeR, at(1580, 996, 200, -2));
        hint = text(20, "Medium", 0x73FFFFFF);
        hint.setGravity(Gravity.END);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2);
        hl.gravity = Gravity.TOP | Gravity.END;
        hl.rightMargin = (int) px(140);
        hl.topMargin = (int) px(98);
        root.addView(hint, hl);

        // Pause-Symbol
        pauseIcon = text(150, "Black", Color.WHITE);
        pauseIcon.setText("❚❚");
        pauseIcon.setAlpha(0f);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2);
        pl.gravity = Gravity.CENTER;
        root.addView(pauseIcon, pl);

        modeBadge = text(30, "Bold", Color.WHITE);
        modeBadge.setPadding((int) px(36), (int) px(18), (int) px(36), (int) px(18));
        modeBadge.setBackground(roundRect(0xE6111114, px(40)));
        modeBadge.setAlpha(0f);
    }

    static GradientDrawable roundRect(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    WindowManager.LayoutParams params() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    void show(int rapperIndex) {
        rapper = rapperIndex;
        if (root == null) build();
        Rapper r = Rapper.LIST[rapper];
        accent = r.c1;
        label.setText(MediaWatcher.albumTitle != null
                ? r.name.toUpperCase(Locale.ROOT) + "  ·  " + MediaWatcher.albumTitle.toUpperCase(Locale.ROOT)
                : r.isMix() ? "DEINE RAPPER  ·  MIX" : r.isFav() ? "DEINE FAVORITEN" : r.name.toUpperCase(Locale.ROOT) + "  ·  SHUFFLE");
        label.setTextColor(accent);
        barFill.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{accent, Color.WHITE}) {{
            setCornerRadius(px(3));
        }});
        glow.setImageBitmap(Gfx.glow(accent, 0.55f));
        glow2.setImageBitmap(Gfx.glow(accent, 0.35f));
        try {
            InputStream in = c.getAssets().open("rap/" + rapper + "_0.jpg");
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            photo.setImageBitmap(b);
        } catch (Exception e) {
            photo.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                    new int[]{(r.c1 & 0x00FFFFFF) | 0x88000000, r.c2, 0xFF000000}));
        }
        title.setText("");
        clearLyrics("");
        status.setText("Lädt …");
        hint.setText("◀ ▶ Song   OK Pause   ▼ ♥ Favorit   ▲ nie wieder   ☰ Anzeige   ↩ Ende");
        if (!added) {
            try {
                wm.addView(root, params());
                badgeHost = new FrameLayout(c);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2);
                bp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                bp.topMargin = (int) px(70);
                badgeHost.addView(modeBadge, bp);
                wm.addView(badgeHost, params());
                added = true;
            } catch (Exception e) {
                Log.e(TAG, "Overlay nicht möglich", e);
                return;
            }
        }
        startAmbient();
        lastKey = android.os.SystemClock.elapsedRealtime();
        applyMode(false);
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    /** Langsames Atmen des Leuchtens + Zoom aufs Foto. */
    void startAmbient() {
        if (breathe != null) breathe.cancel();
        breathe = ValueAnimator.ofFloat(0f, 1f);
        breathe.setDuration(6000);
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float e = (Float) a.getAnimatedValue();
                glow.setAlpha(0.35f + 0.3f * e);
                glow.setScaleX(0.95f + 0.1f * e);
                glow.setScaleY(0.95f + 0.1f * e);
                glow2.setAlpha(0.45f - 0.25f * e);
            }
        });
        breathe.start();
        if (zoom != null) zoom.cancel();
        zoom = ValueAnimator.ofFloat(1.0f, 1.08f);
        zoom.setDuration(24000);
        zoom.setRepeatMode(ValueAnimator.REVERSE);
        zoom.setRepeatCount(ValueAnimator.INFINITE);
        zoom.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float z = (Float) a.getAnimatedValue();
                photo.setScaleX(z);
                photo.setScaleY(z);
            }
        });
        zoom.start();
    }

    void hide() {
        ui.removeCallbacks(tick);
        if (big) { big = false; lyricClip.setScaleX(1f); lyricClip.setScaleY(1f); photo.setAlpha(1f);
            for (View v : new View[]{label, title, status, hint, barBg, barFill, timeL, timeR}) v.setAlpha(1f); }
        request++;
        if (breathe != null) breathe.cancel();
        if (zoom != null) zoom.cancel();
        if (!added) return;
        try { wm.removeView(root); } catch (Exception ignored) { }
        try { wm.removeView(badgeHost); } catch (Exception ignored) { }
        added = false;
    }

    void badge(String s) {
        modeBadge.setText(s);
        modeBadge.animate().cancel();
        modeBadge.setAlpha(1f);
        modeBadge.animate().alpha(0f).setStartDelay(1400).setDuration(500).start();
    }

    void cycleMode() {
        mode = (mode + 1) % 3;
        prefs.edit().putInt("anzeige", mode).apply();
        applyMode(true);
    }

    void applyMode(boolean announce) {
        if (root == null) return;
        root.animate().cancel();
        root.animate().alpha(mode == VIDEO ? 0f : 1f).setDuration(400).start();
        lyricClip.animate().alpha(mode == LYRICS ? 1f : 0f).setDuration(400).start();
        // Audio: Foto heller, Titel größer
        scrim.animate().alpha(mode == AUDIO ? 0.75f : 1f).setDuration(500).start();
        if (announce) badge("Anzeige: " + MODE_NAMES[mode]);
        updateStatus();
    }

    /** ◀ ▶: Songtext verschieben (wird gemerkt). */
    void shift(long d) {
        shift += d;
        prefs.edit().putLong("lyricShift", shift).apply();
        shownLine = -2;
        float sec = -shift / 1000f;
        badge(String.format(Locale.ROOT, "Songtext %+.1f s", sec));
    }

    void switching() {
        if (root == null || !added) return;
        status.setText("Nächster Song …");
        clearLyrics("");
    }

    void clearLyrics(String msg) {
        times.clear();
        texts.clear();
        plain = null;
        shownLine = -2;
        lyricBox.removeAllViews();
        lyricBox.animate().cancel();
        lyricBox.setTranslationY(px(ANCHOR));
    }

    void track(String ytTitle, String ytArtist, final long videoDur) {
        if (root == null || !added) return;
        final Rapper r = Rapper.LIST[rapper];
        String who = r.members()[0];
        for (String m : r.members()) {
            String n = MediaWatcher.norm(m);
            if (MediaWatcher.hasWord(ytTitle, n) || MediaWatcher.hasWord(ytArtist, n)) { who = m; break; }
        }
        if (r.isMix()) label.setText(who.toUpperCase(Locale.ROOT) + "  ·  MIX");
        if (r.isFav()) label.setText(who.toUpperCase(Locale.ROOT) + "  ·  FAVORITEN");
        final String artistName = who;
        final String song = cleanTitle(ytTitle, who);
        currentTitle = song;
        title.setText(song);
        updateFav();
        title.setAlpha(0f);
        title.setTranslationY(px(20));
        title.animate().alpha(1f).translationY(0).setDuration(600).setInterpolator(EASE).start();
        clearLyrics("");
        status.setText("Songtext wird gesucht …");
        final int req = ++request;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Lyrics ly = fetch(artistName, song, videoDur);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (req != request) return;
                        times.clear();
                        texts.clear();
                        plain = null;
                        approx = false;
                        if (ly != null) {
                            times.addAll(ly.times);
                            texts.addAll(ly.texts);
                            offset = ly.offset;
                            approx = ly.approx;
                        }
                        buildLines();
                        shownLine = -2;
                        updateStatus();
                        tick.run();
                    }
                });
            }
        }).start();
    }

    /** ♥ hinter dem Titel, wenn der Song ein Favorit ist. */
    void updateFav() {
        if (title == null) return;
        String id = MediaWatcher.currentId();
        boolean fav = id != null && Library.isFavorite(c, id);
        String t = currentTitle;
        title.setText(fav ? t + "  ♥" : t);
    }

    /** Eine TextView pro Songtext-Zeile. */
    void buildLines() {
        lyricBox.removeAllViews();
        for (String tx : texts) {
            TextView t = text(64, "Black", Color.WHITE);
            t.setText(tx);
            t.setMaxLines(3);
            t.setLineSpacing(0, 1.04f);
            t.setLetterSpacing(-0.02f);
            t.setAlpha(0f);
            t.setPivotX(0);
            t.setScaleX(0.9f);
            t.setScaleY(0.9f);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
            l.bottomMargin = (int) px(30);
            lyricBox.addView(t, l);
        }
        // genug Höhe, damit alle Zeilen ausgelegt werden (sonst schneidet der Bildschirm sie ab)
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) lyricBox.getLayoutParams();
        lp.height = (int) px(texts.size() * 260 + 1400);
        lyricBox.setLayoutParams(lp);
        lyricBox.setTranslationY(px(ANCHOR));
        shownLine = -2;
    }

    /** Aktuelle Zeile in die Mitte gleiten lassen, Nachbarzeilen weich abdunkeln. */
    void focusLine(int idx, boolean anim) {
        int n = lyricBox.getChildCount();
        if (n == 0) return;
        View cur = lyricBox.getChildAt(Math.max(0, Math.min(n - 1, idx)));
        if (cur.getHeight() == 0) { shownLine = -2; return; } // noch nicht ausgelegt: beim nächsten Tick
        float target = px(ANCHOR) - (cur.getTop() + cur.getHeight() / 2f);
        if (idx < 0) target += px(140); // vor der ersten Zeile: erste Zeile wartet etwas tiefer
        long d = anim ? 760 : 0;
        lyricBox.animate().cancel();
        lyricBox.animate().translationY(target).setDuration(d).setInterpolator(SMOOTH).start();
        int c0 = idx < 0 ? -1 : idx;
        for (int i = 0; i < n; i++) {
            View v = lyricBox.getChildAt(i);
            int dist = i - c0;
            // darüber schnell weg, darunter sanft auslaufend
            float a = dist == 0 ? 1f : dist == -1 ? 0.32f : dist == -2 ? 0.14f : dist < 0 ? 0f
                    : dist == 1 ? 0.45f : dist == 2 ? 0.26f : dist == 3 ? 0.12f : 0f;
            float sc = dist == 0 ? 1f : 0.9f;
            if (Math.abs(v.getAlpha() - a) < 0.01f && Math.abs(v.getScaleX() - sc) < 0.01f) continue;
            v.setPivotY(v.getHeight() / 2f);
            v.animate().alpha(a).scaleX(sc).scaleY(sc).setDuration(d).setInterpolator(SMOOTH).start();
        }
    }

    void updateStatus() {
        if (status == null) return;
        if (!times.isEmpty()) status.setText(approx ? "Songtext (ungefähr)" : "Songtext live");
        else if (request > 0 && !currentTitle.isEmpty()) status.setText(mode == LYRICS ? "Kein Songtext gefunden" : "");
    }

    static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ui.postDelayed(this, 60);
            if (mode == VIDEO) return;
            long pos = MediaWatcher.position();
            long dur = MediaWatcher.curDur;
            // Fortschritt
            if (dur > 0) {
                barFill.setScaleX(Math.max(0f, Math.min(1f, pos / (float) dur)));
                timeL.setText(fmt(pos));
                timeR.setText(fmt(dur));
                barBg.setVisibility(View.VISIBLE);
                barFill.setVisibility(View.VISIBLE);
            } else {
                barBg.setVisibility(View.INVISIBLE);
                barFill.setVisibility(View.INVISIBLE);
                timeL.setText(fmt(pos));
                timeR.setText("");
            }
            // Pause anzeigen
            boolean pl = MediaWatcher.isPlaying();
            if (pl != wasPlaying) {
                wasPlaying = pl;
                pauseIcon.animate().alpha(pl ? 0f : 0.9f).scaleX(pl ? 1.3f : 1f).scaleY(pl ? 1.3f : 1f).setDuration(300).start();
                lyricClip.animate().alpha(mode == LYRICS ? (pl ? 1f : 0.35f) : 0f).setDuration(300).start();
            }
            if (mode != LYRICS || times.isEmpty()) { if (big) setBig(false); return; }
            if (!big && pl && android.os.SystemClock.elapsedRealtime() - lastKey > 20000) setBig(true);
            long t = pos - offset - shift + 250; // Übergang beginnt minimal vorher, wirkt flüssiger
            int idx = -1;
            for (int i = 0; i < times.size(); i++) { if (times.get(i) <= t) idx = i; else break; }
            if (idx != shownLine) {
                shownLine = idx;
                focusLine(idx, true);
            }
        }
    };

    // ---------------- Songtext laden ----------------
    static final class Lyrics {
        final List<Long> times = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        String plain;
        long offset;
        boolean approx;
    }

    static String cleanTitle(String t, String artist) {
        String s = t == null ? "" : t;
        s = s.replaceAll("[\\(\\[][^\\)\\]]*[\\)\\]]", " ");                // (Official Video) [..]
        s = s.replaceAll("(?i)\\b(official|music|video|audio|visualizer|lyrics?|hd|4k)\\b", " ");
        s = s.replaceAll("(?i)\\s(ft\\.?|feat\\.?)\\s.*$", " ");
        String a = Pattern.quote(artist);
        s = s.replaceAll("(?i)^\\s*" + a + "\\s*[-–—:|]\\s*", "");
        s = s.replaceAll("(?i)\\s*[-–—|]\\s*" + a + "\\s*$", "");
        s = s.replaceAll("(?i)^\\s*" + a + "\\s+", "");
        s = s.replaceAll("[\"“”]", "").replaceAll("\\s+", " ").trim();
        s = s.replaceAll("^[-–—:|]\\s*|\\s*[-–—:|]$", "").trim();
        return s.isEmpty() ? (t == null ? "" : t) : s;
    }

    static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(7000);
        con.setRequestProperty("User-Agent", "DomiTV/1.0 (Fire TV launcher)");
        BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        con.disconnect();
        return sb.toString();
    }

    static Lyrics fetch(String artist, String song, long videoDur) {
        try {
            String url = "https://lrclib.net/api/search?artist_name=" + URLEncoder.encode(artist, "UTF-8")
                    + "&track_name=" + URLEncoder.encode(song, "UTF-8");
            JSONArray arr = new JSONArray(get(url));
            if (arr.length() == 0) {
                arr = new JSONArray(get("https://lrclib.net/api/search?q=" + URLEncoder.encode(artist + " " + song, "UTF-8")));
            }
            JSONObject best = null;
            double bestScore = -1e9;
            String na = MediaWatcher.norm(artist), ns = MediaWatcher.norm(song);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                double sc = 0;
                if (MediaWatcher.norm(o.optString("artistName")).contains(na)) sc += 50;
                String tn = MediaWatcher.norm(o.optString("trackName"));
                if (tn.equals(ns)) sc += 40; else if (tn.contains(ns) || ns.contains(tn)) sc += 15;
                if (!o.isNull("syncedLyrics") && o.optString("syncedLyrics").length() > 10) sc += 30;
                if (o.optBoolean("instrumental")) sc -= 100;
                if (videoDur > 0) sc -= Math.min(30, Math.abs(o.optDouble("duration", 0) * 1000 - videoDur) / 2000.0);
                if (sc > bestScore) { bestScore = sc; best = o; }
            }
            if (best == null || bestScore < 20) return null;
            Lyrics ly = new Lyrics();
            String synced = best.isNull("syncedLyrics") ? null : best.optString("syncedLyrics");
            if (synced != null && synced.length() > 10) {
                Matcher m = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]\\s*(.*)").matcher("");
                for (String line : synced.split("\n")) {
                    m.reset(line.trim());
                    if (!m.matches()) continue;
                    long t = Integer.parseInt(m.group(1)) * 60000L + (long) (Double.parseDouble(m.group(2)) * 1000);
                    String tx = m.group(3).trim();
                    ly.times.add(t);
                    ly.texts.add(tx.isEmpty() ? "♪" : tx);
                }
            } else if (!best.isNull("plainLyrics")) {
                // nur Text ohne Zeiten: gleichmäßig über den Song verteilen (ungefähr mitlaufend)
                List<String> pl = new ArrayList<>();
                for (String l : best.optString("plainLyrics").split("\n")) if (!l.trim().isEmpty()) pl.add(l.trim());
                long dur = videoDur > 0 ? videoDur : (long) (best.optDouble("duration", 150) * 1000);
                long startAt = 6000, span = Math.max(30000, dur - startAt - 8000);
                for (int i = 0; i < pl.size(); i++) {
                    ly.times.add(startAt + span * i / Math.max(1, pl.size()));
                    ly.texts.add(pl.get(i));
                }
                ly.approx = true;
            }
            // Musikvideos haben oft ein längeres Intro als der Song: Unterschied am Anfang ausgleichen
            long trackDur = (long) (best.optDouble("duration", 0) * 1000);
            long diff = videoDur - trackDur;
            ly.offset = (videoDur > 0 && trackDur > 0 && diff > 4000 && diff < 45000) ? diff : 0;
            Log.i(TAG, "Songtext: " + best.optString("trackName") + " / " + best.optString("artistName")
                    + " Zeilen=" + ly.times.size() + " offset=" + ly.offset);
            return ly.times.isEmpty() && ly.plain == null ? null : ly;
        } catch (Exception e) {
            Log.e(TAG, "Songtext laden fehlgeschlagen", e);
            return null;
        }
    }
}
