package at.domi.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domi-TV-Anzeige über YouTube während des Rapper-Shuffles:
 *  SONGTEXT  – Foto des Rappers + mitlaufender Songtext (von lrclib.net)
 *  AUDIO     – Foto des Rappers + Songtitel
 *  VIDEO     – nichts drüber, man sieht das YouTube-Video
 * Umschalten mit der Menü-Taste (☰).
 */
final class LyricsOverlay {
    static final String TAG = "DomiTV";
    static final int LYRICS = 0, AUDIO = 1, VIDEO = 2;
    static final String[] MODE_NAMES = {"Songtext", "Audio", "Video"};

    final Context c;
    final WindowManager wm;
    final Handler ui = new Handler(Looper.getMainLooper());
    final SharedPreferences prefs;
    FrameLayout root;
    ImageView photo;
    TextView label, title, hint, status, modeBadge;
    TextView[] lines = new TextView[5];
    LinearLayout lyricBox;
    boolean added = false;
    int mode;
    int rapper = 0;

    // Songtext
    final List<Long> times = new ArrayList<>();
    final List<String> texts = new ArrayList<>();
    String plain = null;
    long offset = 0;
    int shownLine = -2;
    int request = 0;
    String currentTitle = "";

    LyricsOverlay(Context c) {
        this.c = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        prefs = c.getSharedPreferences("domitv", Context.MODE_PRIVATE);
        mode = prefs.getInt("anzeige", LYRICS);
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

    void build() {
        root = new FrameLayout(c);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(true);

        photo = new ImageView(c);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF2000000, 0xC8000000, 0x55000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE0000000, 0x00000000}));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, (int) px(360));
        bl.gravity = Gravity.BOTTOM;
        root.addView(bottom, bl);

        // Songtext-Zeilen
        lyricBox = new LinearLayout(c);
        lyricBox.setOrientation(LinearLayout.VERTICAL);
        float[] sizes = {40, 46, 78, 46, 40};
        for (int i = 0; i < 5; i++) {
            TextView t = text(sizes[i], i == 2 ? "ExtraBold" : "Bold", Color.WHITE);
            t.setMaxLines(2);
            t.setLineSpacing(0, 1.05f);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
            l.topMargin = (int) px(i == 2 ? 30 : 18);
            l.bottomMargin = (int) px(i == 2 ? 12 : 0);
            lyricBox.addView(t, l);
            lines[i] = t;
        }
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams((int) px(1250), -2);
        ll.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        ll.leftMargin = (int) px(140);
        ll.topMargin = -(int) px(60);
        root.addView(lyricBox, ll);

        // Titel unten links
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        label = text(22, "Bold", Color.WHITE);
        label.setLetterSpacing(0.14f);
        title = text(52, "ExtraBold", Color.WHITE);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status = text(24, "Medium", 0x99FFFFFF);
        info.addView(label);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = (int) px(10);
        info.addView(title, tl);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = (int) px(10);
        info.addView(status, sl);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams((int) px(1200), -2);
        il.gravity = Gravity.BOTTOM | Gravity.START;
        il.leftMargin = (int) px(140);
        il.bottomMargin = (int) px(90);
        root.addView(info, il);

        // Hinweis unten rechts
        hint = text(22, "Medium", 0x8CFFFFFF);
        hint.setGravity(Gravity.END);
        hint.setText("☰  Anzeige    ⏩  nächster Song    ⏪  Shuffle aus    ↩  Ende");
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2);
        hl.gravity = Gravity.BOTTOM | Gravity.END;
        hl.rightMargin = (int) px(96);
        hl.bottomMargin = (int) px(96);
        root.addView(hint, hl);

        // kurzer Hinweis beim Umschalten der Anzeige (auch im Video-Modus sichtbar)
        modeBadge = text(30, "Bold", Color.WHITE);
        modeBadge.setPadding((int) px(34), (int) px(18), (int) px(34), (int) px(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC111114);
        bg.setCornerRadius(px(40));
        modeBadge.setBackground(bg);
        modeBadge.setAlpha(0f);
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

    FrameLayout badgeHost;

    void show(int rapperIndex) {
        rapper = rapperIndex;
        if (root == null) build();
        Rapper r = Rapper.LIST[rapper];
        label.setText(r.name.toUpperCase() + "  ·  SHUFFLE");
        label.setTextColor(r.c1);
        lines[2].setTextColor(Color.WHITE);
        try {
            InputStream in = c.getAssets().open("rap/" + rapper + "_0.jpg");
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            photo.setImageBitmap(b);
        } catch (Exception ignored) { }
        title.setText("");
        clearLyrics("");
        status.setText("Lädt …");
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
        applyMode(false);
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    void hide() {
        ui.removeCallbacks(tick);
        request++;
        if (!added) return;
        try { wm.removeView(root); } catch (Exception ignored) { }
        try { wm.removeView(badgeHost); } catch (Exception ignored) { }
        added = false;
    }

    void cycleMode() {
        mode = (mode + 1) % 3;
        prefs.edit().putInt("anzeige", mode).apply();
        applyMode(true);
    }

    void applyMode(boolean announce) {
        if (root == null) return;
        root.animate().cancel();
        root.animate().alpha(mode == VIDEO ? 0f : 1f).setDuration(350).start();
        lyricBox.setVisibility(mode == LYRICS ? View.VISIBLE : View.INVISIBLE);
        if (announce) {
            modeBadge.setText("Anzeige: " + MODE_NAMES[mode]);
            modeBadge.animate().cancel();
            modeBadge.setAlpha(1f);
            modeBadge.animate().alpha(0f).setStartDelay(1600).setDuration(500).start();
        }
        updateStatus();
    }

    /** Ein fremdes Video wird gerade ersetzt. */
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
        for (TextView t : lines) t.setText("");
        lines[2].setText(msg);
    }

    /** Neuer Song des Rappers läuft: Titel zeigen und Songtext laden. */
    void track(String ytTitle, final long videoDur) {
        if (root == null || !added) return;
        final Rapper r = Rapper.LIST[rapper];
        final String song = cleanTitle(ytTitle, r.name);
        currentTitle = song;
        title.setText(song);
        clearLyrics("");
        status.setText("Songtext wird gesucht …");
        final int req = ++request;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Lyrics ly = fetch(r.name, song, videoDur);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (req != request) return;
                        times.clear();
                        texts.clear();
                        plain = null;
                        if (ly != null) {
                            times.addAll(ly.times);
                            texts.addAll(ly.texts);
                            plain = ly.plain;
                            offset = ly.offset;
                        }
                        shownLine = -2;
                        updateStatus();
                        tick.run();
                    }
                });
            }
        }).start();
    }

    void updateStatus() {
        if (status == null) return;
        if (!times.isEmpty()) status.setText(MediaWatcher.positionKnown() ? "Songtext synchron" : "Songtext");
        else if (plain != null) status.setText("Songtext (nicht synchron)");
        else if (request > 0 && !currentTitle.isEmpty()) status.setText(mode == LYRICS ? "Kein Songtext gefunden" : "");
    }

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ui.postDelayed(this, 150);
            if (mode != LYRICS) return;
            if (times.isEmpty()) {
                if (plain != null && shownLine != -3) {
                    shownLine = -3;
                    String[] p = plain.split("\n");
                    for (int i = 0; i < 5; i++) lines[i].setText(i < p.length ? p[i] : "");
                    styleLines(0);
                }
                return;
            }
            long t = MediaWatcher.position() - offset;
            int idx = -1;
            for (int i = 0; i < times.size(); i++) { if (times.get(i) <= t) idx = i; else break; }
            if (idx == shownLine) return;
            shownLine = idx;
            for (int k = -2; k <= 2; k++) {
                int j = idx + k;
                lines[k + 2].setText(j >= 0 && j < texts.size() ? texts.get(j) : (j == -1 && idx == -1 ? "" : ""));
            }
            if (idx == -1) lines[3].setText(texts.isEmpty() ? "" : texts.get(0));
            styleLines(idx);
            // sanft nach oben gleiten
            lyricBox.setTranslationY(px(36));
            lyricBox.animate().translationY(0).setDuration(320).start();
            lines[2].setAlpha(0.35f);
            lines[2].animate().alpha(1f).setDuration(320).start();
        }
    };

    void styleLines(int idx) {
        int[] alpha = {0x40, 0x66, 0xFF, 0x80, 0x55};
        for (int i = 0; i < 5; i++) lines[i].setTextColor((alpha[i] << 24) | 0xFFFFFF);
    }

    // ---------------- Songtext laden ----------------
    static final class Lyrics {
        final List<Long> times = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        String plain;
        long offset;
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
                ly.plain = best.optString("plainLyrics");
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
