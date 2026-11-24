package at.domi.tvlauncher;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Song-Quiz: eine Zeile aus dem Songtext, 4 Songs zur Auswahl, 15 s Zeit.
 * 10 Runden, Punkte = 100 + Zeitbonus, Serien-Bonus; Bestenliste (Top 5) bleibt gespeichert.
 * Steuerung: ◀ ▶ ▲ ▼ wählen, OK antworten, ↩ beenden.
 */
final class Quiz {
    static final int ROUNDS = 10;
    static final long TIME = 15000;

    static final class Round {
        String line, answer, rapper, preview;   // preview = 30-s-Hörprobe (Deezer)
        int rapperIdx;
        String[] options = new String[4];
        int correct;
    }

    final MainActivity a;
    final FrameLayout root;
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService bg = Executors.newSingleThreadExecutor();
    final LinkedBlockingQueue<Round> ready = new LinkedBlockingQueue<>();
    final Random rnd = new Random();
    final List<String[]> pool = new ArrayList<>();   // {Rapper, Titel, RapperIndex}

    TextView head, line, scoreV, info, center;
    final TextView[] opt = new TextView[4];
    View timer;
    ValueAnimator timeAnim;
    int round = 0, score = 0, streak = 0, sel = 0;
    boolean answered = true, closed = false, waiting = false;
    Round cur;
    long shownAt;
    int accent = 0xffff3d6e;

    Quiz(MainActivity a) {
        this.a = a;
        root = new FrameLayout(a);
        root.setBackgroundColor(0xff050507);
        root.setClickable(true);
    }

    float px(float d) { return a.px(d); }

    void open(FrameLayout parent) {
        build();
        parent.addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(350).start();
        // Songs aller deiner Rapper (die ersten 6) als Pool
        for (int r = 0; r < Rapper.MIX_OF; r++) {
            for (String[] s : Rapper.LIST[r].songsWithTitles(a)) {
                String t = LyricsOverlay.cleanTitle(s[1], Rapper.LIST[r].name);
                // "Ken Carson, Destroy Lonely - Singapore" -> "Singapore"
                int dash = t.indexOf(" - ");
                if (dash > 0 && MediaWatcher.norm(t.substring(0, dash)).contains(MediaWatcher.norm(Rapper.LIST[r].name)))
                    t = t.substring(dash + 3).trim();
                if (t.toLowerCase(Locale.ROOT).contains("snippet") || t.toLowerCase(Locale.ROOT).contains("unreleased")) continue;
                if (t.length() >= 2 && t.length() <= 40) pool.add(new String[]{Rapper.LIST[r].name, t, String.valueOf(r)});
            }
        }
        for (int i = 0; i < 3; i++) prepare();
        next();
    }

    void build() {
        View glow = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{0x55ff3d6e, 0});
        g.setGradientRadius(px(900));
        g.setGradientCenter(0.5f, 0.0f);
        glow.setBackground(g);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));

        head = a.text("SONG-QUIZ", 22, "Bold", 0xffff3d6e, 0.3f);
        root.addView(head, a.atD(120, 80, -2, -2));
        scoreV = a.text("0 Punkte", 30, "Bold", Color.WHITE, 0);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2);
        sl.gravity = Gravity.END | Gravity.TOP;
        sl.topMargin = (int) px(74);
        sl.rightMargin = (int) px(120);
        root.addView(scoreV, sl);

        line = a.text("", 64, "Black", Color.WHITE, -0.01f);
        line.setGravity(Gravity.CENTER);
        line.setSingleLine(false);
        line.setMaxLines(3);
        line.setHorizontallyScrolling(false);
        line.setEllipsize(null);
        line.setLineSpacing(0, 1.05f);
        root.addView(line, a.atD(160, 210, 1600, 360));
        line.setGravity(Gravity.CENTER);

        View tb = new View(a);
        tb.setBackground(roundBg(0x22ffffff, 4));
        root.addView(tb, a.atD(460, 600, 1000, 8));
        timer = new View(a);
        timer.setBackground(roundBg(0xffff3d6e, 4));
        timer.setPivotX(0);
        root.addView(timer, a.atD(460, 600, 1000, 8));

        for (int i = 0; i < 4; i++) {
            TextView t = a.text("", 34, "Bold", Color.WHITE, 0);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setPadding((int) px(30), 0, (int) px(30), 0);
            opt[i] = t;
            root.addView(t, a.atD(i % 2 == 0 ? 260 : 980, i < 2 ? 680 : 830, 680, 120));
        }
        info = a.text("◀ ▶ ▲ ▼ wählen   OK antworten   ↩ beenden", 20, "Medium", 0x88ffffff, 0);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(-2, -2);
        il.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        il.bottomMargin = (int) px(60);
        root.addView(info, il);
        center = a.text("", 86, "Black", Color.WHITE, -0.02f);
        center.setGravity(Gravity.CENTER);
        center.setAlpha(0f);
        root.addView(center, a.atD(0, 560, 1920, 110));
    }

    GradientDrawable roundBg(int col, float r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(col);
        d.setCornerRadius(px(r));
        return d;
    }

    /** Im Hintergrund eine Runde vorbereiten (Songtext von LRCLIB holen, gute Zeile wählen). */
    void prepare() {
        bg.execute(new Runnable() {
            public void run() {
                for (int tries = 0; tries < 8 && !closed; tries++) {
                    Round r = makeRound();
                    if (r != null) { ready.offer(r); return; }
                }
            }
        });
    }

    Round makeRound() {
        if (pool.size() < 8) return null;
        String[] s = pool.get(rnd.nextInt(pool.size()));
        LyricsOverlay.Lyrics ly = LyricsOverlay.fetch(s[0], s[1], 0);
        if (ly == null) return null;
        List<String> lines = new ArrayList<>(ly.texts);
        if (lines.isEmpty() && ly.plain != null) for (String l : ly.plain.split("\n")) lines.add(l);
        String nt = MediaWatcher.norm(s[1]);
        List<String> good = new ArrayList<>();
        for (String l : lines) {
            String t = l.trim();
            // nicht zu kurz, keine Adlibs, Songtitel darf nicht drinstehen (sonst zu leicht)
            if (t.length() >= 28 && t.length() <= 110 && !MediaWatcher.norm(t).contains(nt)) good.add(t);
        }
        if (good.size() < 3) return null;
        Round r = new Round();
        r.line = good.get(good.size() / 4 + rnd.nextInt(Math.max(1, good.size() / 2)));
        r.answer = s[1];
        r.rapper = s[0];
        r.rapperIdx = Integer.parseInt(s[2]);
        List<String> opts = new ArrayList<>();
        opts.add(s[1]);
        int guard = 0;
        while (opts.size() < 4 && guard++ < 200) {
            String[] o = pool.get(rnd.nextInt(pool.size()));
            // halb so oft vom gleichen Rapper -> schwerer
            boolean same = o[0].equals(s[0]);
            if (!same && rnd.nextBoolean() && guard < 120) continue;
            boolean dup = false;
            for (String x : opts) if (MediaWatcher.norm(x).equals(MediaWatcher.norm(o[1]))) dup = true;
            if (!dup) opts.add(o[1]);
        }
        if (opts.size() < 4) return null;
        r.preview = preview(s[0], s[1]);
        Collections.shuffle(opts, rnd);
        for (int i = 0; i < 4; i++) {
            r.options[i] = opts.get(i);
            if (opts.get(i).equals(s[1])) r.correct = i;
        }
        return r;
    }

    /** 30-Sekunden-Hörprobe des Songs von Deezer (null, wenn es keine gibt). */
    static String preview(String artist, String title) {
        try {
            String q = java.net.URLEncoder.encode("artist:\"" + artist + "\" track:\"" + title + "\"", "UTF-8");
            JSONArray d = new JSONObject(LyricsOverlay.get("https://api.deezer.com/search?limit=10&q=" + q)).optJSONArray("data");
            if (d == null || d.length() == 0)
                d = new JSONObject(LyricsOverlay.get("https://api.deezer.com/search?limit=10&q="
                        + java.net.URLEncoder.encode(artist + " " + title, "UTF-8"))).optJSONArray("data");
            if (d == null) return null;
            String na = MediaWatcher.norm(artist), nt = MediaWatcher.norm(title);
            for (int i = 0; i < d.length(); i++) {
                JSONObject o = d.getJSONObject(i);
                String ra = MediaWatcher.norm(o.optJSONObject("artist") == null ? "" : o.optJSONObject("artist").optString("name"));
                String rt = MediaWatcher.norm(o.optString("title"));
                if (ra.equals(na) && (rt.equals(nt) || rt.contains(nt) || nt.contains(rt)) && o.optString("preview").startsWith("http"))
                    return o.optString("preview");
            }
        } catch (Exception ignored) { }
        return null;
    }

    android.media.MediaPlayer player;
    ValueAnimator fade;

    void playPreview(String url) {
        stopPreview(false);
        if (url == null) return;
        try {
            final android.media.MediaPlayer mp = new android.media.MediaPlayer();
            mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setDataSource(url);
            mp.setVolume(0f, 0f);
            mp.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                public void onPrepared(android.media.MediaPlayer m) {
                    if (player != m || closed) { m.release(); return; }
                    m.start();
                    volumeTo(m, 0f, 1f, 600, false);
                }
            });
            mp.prepareAsync();
            player = mp;
        } catch (Exception e) { player = null; }
    }

    void volumeTo(final android.media.MediaPlayer m, float from, float to, long ms, final boolean release) {
        if (fade != null) fade.cancel();
        fade = ValueAnimator.ofFloat(from, to);
        fade.setDuration(ms);
        fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator v) {
                float f = (Float) v.getAnimatedValue();
                try { m.setVolume(f, f); } catch (Exception ignored) { }
                if (release && v.getAnimatedFraction() >= 1f) { try { m.release(); } catch (Exception ignored) { } }
            }
        });
        fade.start();
    }

    void stopPreview(boolean soft) {
        final android.media.MediaPlayer m = player;
        player = null;
        if (m == null) return;
        if (soft) {
            try { if (m.isPlaying()) { volumeTo(m, 1f, 0f, 700, true); return; } } catch (Exception ignored) { }
        }
        if (fade != null) fade.cancel();
        try { m.release(); } catch (Exception ignored) { }
    }

    void next() {
        if (closed) return;
        if (round >= ROUNDS) { finish(); return; }
        Round r = ready.poll();
        if (r == null) {
            // Runde lädt noch
            waiting = true;
            line.setText("Lade Songtext …");
            for (TextView t : opt) t.setText("");
            ui.postDelayed(new Runnable() { public void run() { if (waiting) next(); } }, 400);
            return;
        }
        waiting = false;
        prepare();
        cur = r;
        round++;
        answered = false;
        sel = 0;
        head.setText("SONG-QUIZ  ·  RUNDE " + round + " / " + ROUNDS);
        line.setText("„" + r.line + "“");
        line.setAlpha(0f);
        line.setTranslationY(px(30));
        line.animate().alpha(1f).translationY(0).setDuration(450).setInterpolator(new DecelerateInterpolator()).start();
        for (int i = 0; i < 4; i++) {
            opt[i].setText(r.options[i]);
            opt[i].setAlpha(0f);
            opt[i].animate().alpha(1f).setStartDelay(150 + i * 70).setDuration(300).start();
        }
        paint();
        playPreview(r.preview);
        shownAt = System.currentTimeMillis();
        if (timeAnim != null) timeAnim.cancel();
        timeAnim = ValueAnimator.ofFloat(1f, 0f);
        timeAnim.setDuration(TIME);
        timeAnim.setInterpolator(new LinearInterpolator());
        timeAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator v) {
                float f = (Float) v.getAnimatedValue();
                timer.setScaleX(f);
                if (f <= 0f && !answered) answer(-1);
            }
        });
        timeAnim.start();
    }

    void paint() {
        for (int i = 0; i < 4; i++) {
            boolean f = i == sel && !answered;
            int bg = 0x1affffff;
            if (answered && cur != null) {
                if (i == cur.correct) bg = 0xff1fbf6a;
                else if (i == sel) bg = 0xffd83a3a;
            } else if (f) bg = Color.WHITE;
            opt[i].setBackground(roundBg(bg, 26));
            opt[i].setTextColor(f ? 0xff111111 : Color.WHITE);
            opt[i].animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(160).start();
        }
    }

    void answer(int i) {
        if (answered) return;
        answered = true;
        if (timeAnim != null) timeAnim.cancel();
        sel = i;
        boolean ok = i == cur.correct;
        long t = System.currentTimeMillis() - shownAt;
        int pts = 0;
        if (ok) {
            streak++;
            pts = 100 + (int) Math.max(0, (TIME - t) / 150) + (streak >= 3 ? 50 : 0);
            score += pts;
            Hype.kick(0.7f);
        } else streak = 0;
        paint();
        scoreV.setText(score + " Punkte");
        center.setText(ok ? "+" + pts : (i < 0 ? "Zeit um" : "Falsch"));
        center.setTextColor(ok ? 0xff3ce38a : 0xffff5a5a);
        center.setScaleX(0.6f);
        center.setScaleY(0.6f);
        center.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator()).start();
        info.setText(cur.rapper + " – " + cur.answer + (streak >= 3 ? "   ·   " + streak + "er-Serie 🔥" : ""));
        ui.postDelayed(new Runnable() { public void run() { stopPreview(true); } }, 900);
        ui.postDelayed(new Runnable() {
            public void run() {
                center.animate().alpha(0f).setDuration(200).start();
                info.setText("◀ ▶ ▲ ▼ wählen   OK antworten   ↩ beenden");
                next();
            }
        }, 1800);
    }

    void finish() {
        if (timeAnim != null) timeAnim.cancel();
        stopPreview(true);
        SharedPreferences p = a.prefs;
        List<int[]> best = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(p.getString("quizBest", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                best.add(new int[]{o.getInt("s"), o.getInt("d")});
            }
        } catch (Exception ignored) { }
        int today = (int) (System.currentTimeMillis() / 86400000L);
        best.add(new int[]{score, today});
        Collections.sort(best, new java.util.Comparator<int[]>() {
            public int compare(int[] x, int[] y) { return y[0] - x[0]; }
        });
        int place = -1;
        for (int i = 0; i < best.size(); i++) if (best.get(i)[0] == score && best.get(i)[1] == today) { place = i; break; }
        while (best.size() > 5) best.remove(best.size() - 1);
        JSONArray out = new JSONArray();
        try { for (int[] b : best) out.put(new JSONObject().put("s", b[0]).put("d", b[1])); } catch (Exception ignored) { }
        p.edit().putString("quizBest", out.toString()).apply();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < best.size(); i++) {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d. MMM", Locale.GERMAN);
            sb.append(i + 1).append(".   ").append(best.get(i)[0]).append(" Punkte   ·   ")
                    .append(f.format(new java.util.Date(best.get(i)[1] * 86400000L))).append(i == place ? "   ← du" : "").append("\n");
        }
        head.setText("SONG-QUIZ  ·  FERTIG");
        line.setText(score + " Punkte" + (place == 0 && score > 0 ? "\nNeuer Rekord!" : ""));
        for (TextView t : opt) { t.setText(""); t.setBackground(null); }
        center.setAlpha(0f);
        info.setText("OK nochmal   ↩ beenden");
        TextView board = a.text("BESTENLISTE\n\n" + sb, 30, "Bold", 0xccffffff, 0);
        board.setGravity(Gravity.CENTER);
        board.setSingleLine(false);
        board.setMaxLines(9);
        board.setTag("board");
        root.addView(board, a.atD(360, 620, 1200, 380));
        if (place == 0 && score > 0) Hype.kick(0.9f);
        round = ROUNDS + 1;
    }

    /** true = Taste verbraucht. */
    boolean key(String k) {
        if ("back".equals(k)) { close(); return true; }
        if (round > ROUNDS) {
            if ("ok".equals(k)) { restart(); }
            return true;
        }
        if (answered || waiting) return true;
        if ("left".equals(k) && sel % 2 == 1) sel--;
        else if ("right".equals(k) && sel % 2 == 0) sel++;
        else if ("up".equals(k) && sel >= 2) sel -= 2;
        else if ("down".equals(k) && sel < 2) sel += 2;
        else if ("ok".equals(k)) { answer(sel); return true; }
        paint();
        return true;
    }

    void restart() {
        View b = root.findViewWithTag("board");
        if (b != null) root.removeView(b);
        round = 0;
        score = 0;
        streak = 0;
        scoreV.setText("0 Punkte");
        next();
    }

    void close() {
        closed = true;
        stopPreview(false);
        if (timeAnim != null) timeAnim.cancel();
        ui.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        root.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
            public void run() {
                if (root.getParent() != null) ((FrameLayout) root.getParent()).removeView(root);
            }
        }).start();
        a.quiz = null;
    }
}
