package at.domi.tvlauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Domi TV – die komplette Oberfläche, direkt mit Android gezeichnet.
 * Alles Aufwendige (Hintergründe, Leuchten, Ringe) sind fertige Bilder;
 * bewegt wird nur per Transparenz / Verschieben / Skalieren (läuft auf dem Grafikchip).
 */
public class MainActivity extends Activity {

    // ---------- Grundlagen ----------
    static final int BG = 0xff030305;
    static final int TXT = 0xfff5f5f7;
    final PathInterpolator EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
    final PathInterpolator SOFT = new PathInterpolator(0.25f, 0.1f, 0.25f, 1f);
    final Handler ui = new Handler(Looper.getMainLooper());
    float S = 1f;
    final Map<String, Typeface> fonts = new HashMap<>();
    SharedPreferences prefs;

    int px(float v) { return Math.round(v * S); }

    Typeface font(String w) {
        Typeface t = fonts.get(w);
        if (t == null) {
            try { t = Typeface.createFromAsset(getAssets(), "fonts/Inter-" + w + ".otf"); }
            catch (Exception e) { t = Typeface.create("sans-serif", Typeface.BOLD); }
            fonts.put(w, t);
        }
        return t;
    }

    TextView text(String s, float size, String weight, int color, float spacing) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(font(weight));
        t.setTextColor(color);
        t.setLetterSpacing(spacing);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    static FrameLayout.LayoutParams at(int l, int t, int w, int h) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h);
        p.gravity = Gravity.TOP | Gravity.START;
        p.leftMargin = l;
        p.topMargin = t;
        return p;
    }

    FrameLayout.LayoutParams atD(float l, float t, float w, float h) {
        return at(px(l), px(t), w < 0 ? (int) w : px(w), h < 0 ? (int) h : px(h));
    }

    // ---------- Zustand ----------
    final AppDef[] APPS = AppDef.APPS;
    int sel = 0, cSel = 0, tSel = 0;
    String zone = "apps";
    final List<Card> cards = new ArrayList<>();
    long pausedAt = 0;
    boolean wokeUp = false;

    // ---------- Ansichten ----------
    FrameLayout root, bgWrap, ringWrap, lift, heroWrap, dock, cont, cardsRow, intro, header;
    final ImageView[] bgs = new ImageView[APPS.length];
    final View[] heroes = new View[APPS.length];
    final Slot[] slots = new Slot[APPS.length];
    ImageView beam, iGlow, iRing;
    View iSweep, iBack, iFlash;
    final ImageView[] dots = new ImageView[5];
    LinearLayout iWords;
    final List<TextView> words = new ArrayList<>();
    TextView greet, date, time, toast, contSub, iSub;
    Gfx.Icon chev;
    FrameLayout btnHome, btnSettings;
    Gfx.Icon icHome, icSettings;
    ObjectAnimator kenBurns;
    final List<ObjectAnimator> ringAnims = new ArrayList<>();

    static class Slot {
        View reflect, fx, bar;
        ImageView glow;
        FrameLayout tile;
    }

    static class Card {
        AppDef app;
        String title, sub, right;
        boolean hint;
        FrameLayout view;
        View ring;
        View play;
    }

    // ---------- Startsound ----------
    MediaPlayer player;          // spielt den Sound komplett (SoundPool schneidet lange Sounds ab)
    boolean soundReady = false, soundPending = false;
    static final float LAUTSTAERKE = 0.9f;

    final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) { wokeUp = true; }
    };

    // =====================================================================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadSound();
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        prefs = getSharedPreferences("domitv", MODE_PRIVATE);
        sel = Math.max(0, Math.min(APPS.length - 1, prefs.getInt("sel", 0)));

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.setBackgroundDrawable(new ColorDrawable(BG));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
        S = Math.min(sw / 1920f, sh / 1080f);

        resolveApps();
        // Nur für den automatischen Test im Emulator: erste Kachel öffnet Domi TV selbst
        if (getIntent() != null && getIntent().getBooleanExtra("fx_test", false)) APPS[0].pkg = getPackageName();
        buildUi();
        intro.setVisibility(View.VISIBLE);
        setMainVisible(false);
        setContentView(root);
        hideSystemUi();
        clockTick();
        buildCards();
        applyAll(false);
        loadOtherBackgrounds();
        root.post(new Runnable() { public void run() { playIntro(true); } });
    }

    // =====================================================================
    // Aufbau
    // =====================================================================
    void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setClipChildren(false);

        // --- Hintergründe (fertige Bilder) ---
        bgWrap = new FrameLayout(this);
        root.addView(bgWrap, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < APPS.length; i++) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setAlpha(0f);
            iv.setVisibility(View.INVISIBLE);
            bgs[i] = iv;
            bgWrap.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            if ("ytmusic".equals(APPS[i].id)) {
                ringWrap = new FrameLayout(this);
                ringWrap.setAlpha(0f);
                ringWrap.setVisibility(View.INVISIBLE);
                bgWrap.addView(ringWrap, new FrameLayout.LayoutParams(-1, -1));
                Bitmap rb = Gfx.pinkRing();
                for (int r = 0; r < 3; r++) {
                    ImageView ring = new ImageView(this);
                    ring.setImageBitmap(rb);
                    ring.setScaleType(ImageView.ScaleType.FIT_XY);
                    ring.setAlpha(0f);
                    ringWrap.addView(ring, atD(910, -90, 1100, 1100));
                    ObjectAnimator an = ObjectAnimator.ofPropertyValuesHolder(ring,
                            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.12f, 1f),
                            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.12f, 1f),
                            PropertyValuesHolder.ofKeyframe(View.ALPHA,
                                    android.animation.Keyframe.ofFloat(0f, 0f),
                                    android.animation.Keyframe.ofFloat(0.12f, 0.85f),
                                    android.animation.Keyframe.ofFloat(1f, 0f)));
                    an.setDuration(7200);
                    an.setInterpolator(new PathInterpolator(0.2f, 0.6f, 0.4f, 1f));
                    an.setRepeatCount(ValueAnimator.INFINITE);
                    an.setCurrentPlayTime(r * 2400L);
                    ringAnims.add(an);
                }
            }
        }
        bgWrap.setPivotX(px(1150));
        bgWrap.setPivotY(px(380));
        kenBurns = ObjectAnimator.ofPropertyValuesHolder(bgWrap,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.03f, 1.1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.03f, 1.1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, -px(24)),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, px(12)));
        kenBurns.setDuration(38000);
        kenBurns.setRepeatMode(ValueAnimator.REVERSE);
        kenBurns.setRepeatCount(ValueAnimator.INFINITE);
        kenBurns.setInterpolator(SOFT);
        bgs[sel].setImageBitmap(loadBg(sel));

        // --- Lichtkegel ---
        beam = new ImageView(this);
        beam.setImageBitmap(Gfx.beam());
        beam.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(beam, atD(0, 0, 720, 1080));

        // --- Kopfzeile ---
        header = new FrameLayout(this);
        root.addView(header, new FrameLayout.LayoutParams(-1, -1));
        TextView avatar = text(AppDef.NAME.substring(0, 1).toUpperCase(Locale.ROOT), 25, "ExtraBold", 0xff1c1c1e, 0);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable av = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xfff2f2f7, 0xff636366});
        av.setShape(GradientDrawable.OVAL);
        av.setStroke(Math.max(1, px(1)), Gfx.w(0.35f));
        avatar.setBackground(av);
        header.addView(avatar, atD(96, 68, 58, 58));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        greet = text("", 28, "SemiBold", TXT, -0.01f);
        date = text("", 20, "Medium", Gfx.a(TXT, 0.6f), 0);
        who.addView(greet);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = px(5);
        who.addView(date, dl);
        header.addView(who, atD(172, 66, 900, 70));

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(px(32), 0, px(11), 0);
        GradientDrawable pb = Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xdb28282e, 0xdb16161a}, px(37));
        pb.setStroke(Math.max(1, px(1)), Gfx.w(0.14f));
        pill.setBackground(pb);
        time = text("", 31, "SemiBold", TXT, -0.02f);
        time.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.rightMargin = px(26);
        pill.addView(time, tl);
        btnHome = iconButton(Gfx.Icon.HOME);
        icHome = (Gfx.Icon) btnHome.getChildAt(0);
        btnSettings = iconButton(Gfx.Icon.SETTINGS);
        icSettings = (Gfx.Icon) btnSettings.getChildAt(0);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(52), px(52));
        pill.addView(btnHome, bl);
        LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(px(52), px(52));
        bl2.leftMargin = px(12);
        pill.addView(btnSettings, bl2);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, px(74));
        pl.gravity = Gravity.TOP | Gravity.END;
        pl.topMargin = px(60);
        pl.rightMargin = px(96);
        header.addView(pill, pl);

        // --- Hero + Dock ---
        lift = new FrameLayout(this);
        lift.setClipChildren(false);
        root.addView(lift, atD(96, 430, 1728, 560));
        heroWrap = new FrameLayout(this);
        heroWrap.setClipChildren(false);
        lift.addView(heroWrap, atD(0, -32, 1728, 322));
        dock = new FrameLayout(this);
        dock.setClipChildren(false);
        GradientDrawable db = Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Gfx.w(0.085f), Gfx.w(0.03f)}, px(54));
        db.setStroke(Math.max(1, px(1)), Gfx.w(0.12f));
        dock.setBackground(db);
        lift.addView(dock, atD(0, 326, 28 * 2 + APPS.length * 280 + (APPS.length - 1) * 32, 214));

        for (int i = 0; i < APPS.length; i++) {
            AppDef a = APPS[i];
            heroes[i] = buildHero(a, i);
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2);
            hl.gravity = Gravity.BOTTOM | Gravity.START;
            heroWrap.addView(heroes[i], hl);
            slots[i] = buildSlot(a, i);
        }

        // --- Weiter ansehen ---
        cont = new FrameLayout(this);
        cont.setClipChildren(false);
        cont.setAlpha(0f);
        cont.setTranslationY(px(260));
        cont.setVisibility(View.INVISIBLE);
        root.addView(cont, atD(96, 650, 1728, 430));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setBaselineAligned(true);
        head.addView(text("Weiter ansehen", 34, "Bold", TXT, -0.025f));
        contSub = text("Zuletzt geöffnet", 22, "Medium", Gfx.a(TXT, 0.55f), 0);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = px(18);
        head.addView(contSub, cl);
        cont.addView(head, atD(0, 0, -2, -2));
        cardsRow = new FrameLayout(this);
        cardsRow.setClipChildren(false);
        cont.addView(cardsRow, atD(0, 70, 4000, 300));

        // --- Pfeil unten ---
        chev = new Gfx.Icon(this, Gfx.Icon.CHEVRON);
        chev.setColor(Gfx.a(TXT, 0.85f));
        chev.setAlpha(0.4f);
        root.addView(chev, atD(945, 1024, 30, 30));

        // --- Meldung ---
        toast = text("", 24, "SemiBold", TXT, 0);
        toast.setGravity(Gravity.CENTER);
        toast.setPadding(px(30), 0, px(30), 0);
        GradientDrawable tb = Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xeb28282e, 0xeb16161a}, px(32));
        tb.setStroke(Math.max(1, px(1)), Gfx.w(0.14f));
        toast.setBackground(tb);
        toast.setAlpha(0f);
        FrameLayout.LayoutParams tol = new FrameLayout.LayoutParams(-2, px(64));
        tol.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tol.topMargin = px(170);
        root.addView(toast, tol);

        // --- Startanimation ---
        intro = new FrameLayout(this);
        intro.setVisibility(View.GONE);
        intro.setClipChildren(false);
        root.addView(intro, new FrameLayout.LayoutParams(-1, -1));
        iBack = new View(this);
        iBack.setBackgroundColor(BG);
        intro.addView(iBack, new FrameLayout.LayoutParams(-1, -1));
        iGlow = new ImageView(this);
        iGlow.setScaleType(ImageView.ScaleType.FIT_XY);
        try {
            InputStream in = getAssets().open("intro_glow.png");
            iGlow.setImageBitmap(BitmapFactory.decodeStream(in));
            in.close();
        } catch (Exception e) { Log.e(HomeWatcherService.TAG, "glow", e); }
        intro.addView(iGlow, atD(960 - 650, 540 - 650, 1300, 1300));
        iSweep = new View(this);
        iSweep.setBackground(Gfx.grad(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Gfx.w(0), Gfx.w(0.9f), Gfx.w(0)}, 0));
        intro.addView(iSweep, atD(210, 539, 1500, 2));
        iRing = new ImageView(this);
        iRing.setImageBitmap(Gfx.introRing());
        iRing.setScaleType(ImageView.ScaleType.FIT_XY);
        intro.addView(iRing, atD(790, 370, 340, 340));
        for (int d = 0; d < dots.length; d++) {
            ImageView dot = new ImageView(this);
            dot.setImageBitmap(Gfx.dot(APPS[d % APPS.length].glow));
            dot.setScaleType(ImageView.ScaleType.FIT_XY);
            intro.addView(dot, atD(960 - 45, 540 - 45, 90, 90));
            dots[d] = dot;
        }
        iWords = new LinearLayout(this);
        iWords.setOrientation(LinearLayout.HORIZONTAL);
        iWords.setGravity(Gravity.CENTER);
        intro.addView(iWords, atD(0, 452, 1920, 124));
        iSub = text("", 28, "Medium", Gfx.a(TXT, 0.62f), 0.02f);
        iSub.setGravity(Gravity.CENTER);
        intro.addView(iSub, atD(0, 598, 1920, 40));
        iFlash = new View(this);
        GradientDrawable fl = new GradientDrawable();
        fl.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        fl.setColors(new int[]{Gfx.w(0.9f), Gfx.w(0.35f), Gfx.w(0)});
        fl.setGradientCenter(0.5f, 0.5f);
        fl.setGradientRadius(px(820));
        iFlash.setBackground(fl);
        iFlash.setAlpha(0f);
        intro.addView(iFlash, new FrameLayout.LayoutParams(-1, -1));
    }

    String wordsText = "";

    void setWords(String t) {
        if (t.equals(wordsText)) return;
        wordsText = t;
        iWords.removeAllViews();
        words.clear();
        String[] parts = t.split(" ");
        for (int i = 0; i < parts.length; i++) {
            TextView w = text(parts[i], 96, "ExtraBold", Color.WHITE, -0.03f);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) l.leftMargin = px(24);
            iWords.addView(w, l);
            words.add(w);
        }
    }

    FrameLayout iconButton(int type) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(Gfx.round(Gfx.w(0.1f), px(26)));
        Gfx.Icon ic = new Gfx.Icon(this, type);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(24), px(24));
        l.gravity = Gravity.CENTER;
        f.addView(ic, l);
        return f;
    }

    View buildHero(AppDef a, int i) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        String idx = String.format(Locale.ROOT, "%02d / %02d", i + 1, APPS.length);
        meta.addView(text(idx, 22, "Bold", Gfx.a(TXT, 0.92f), 0.04f));
        View line = new View(this);
        line.setBackground(Gfx.round(a.glow, px(1)));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(px(48), Math.max(2, px(2)));
        ll.leftMargin = px(18);
        ll.rightMargin = px(18);
        meta.addView(line, ll);
        meta.addView(text(a.kind.toUpperCase(Locale.GERMAN), 22, "SemiBold", Gfx.a(TXT, 0.64f), 0.18f));
        h.addView(meta);
        Gfx.GradientText title = new Gfx.GradientText(this);
        title.setText(a.name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(200));
        title.setTypeface(font("Black"));
        title.setLetterSpacing(-0.055f);
        title.setIncludeFontPadding(false);
        title.setSingleLine(true);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, px(20), px(6));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = px(10);
        h.addView(title, tl);
        h.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        h.setAlpha(0f);
        h.setVisibility(View.INVISIBLE);
        return h;
    }

    Slot buildSlot(AppDef a, int i) {
        Slot s = new Slot();
        float x = 28 + i * 312, y = 28;
        s.reflect = new View(this);
        GradientDrawable rd = Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Gfx.a(a.t1, 0.42f), Gfx.a(a.t2, 0.08f), Gfx.a(a.t2, 0)}, 0);
        rd.setCornerRadii(new float[]{px(30), px(30), px(30), px(30), 0, 0, 0, 0});
        s.reflect.setBackground(rd);
        s.reflect.setAlpha(0.35f);
        dock.addView(s.reflect, atD(x, y + 166, 280, 120));

        s.glow = new ImageView(this);
        s.glow.setImageBitmap(Gfx.glow(a.glow, 0.42f));
        s.glow.setScaleType(ImageView.ScaleType.FIT_XY);
        s.glow.setAlpha(0f);
        dock.addView(s.glow, atD(x - 150, y - 130, 580, 418));

        s.tile = new FrameLayout(this);
        Drawable[] layers = {
                Gfx.grad(GradientDrawable.Orientation.TL_BR, new int[]{a.t1, a.t2}, px(30)),
                Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Gfx.w(0.18f), Gfx.w(0), Gfx.w(0)}, px(30)),
        };
        s.tile.setBackground(new LayerDrawable(layers));
        TextView lbl = text(a.name, 32, "Bold", TXT, -0.035f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.CENTER;
        s.tile.addView(lbl, lp);
        s.tile.setAlpha(0.62f);
        s.tile.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        dock.addView(s.tile, atD(x, y, 280, 158));

        s.fx = new View(this);
        GradientDrawable ring = Gfx.round(Color.TRANSPARENT, px(30));
        ring.setStroke(Math.max(2, px(3)), Gfx.w(0.95f));
        Drawable[] fx = {
                Gfx.grad(GradientDrawable.Orientation.TL_BR, new int[]{Gfx.w(0), Gfx.w(0.16f), Gfx.w(0)}, px(30)),
                ring,
        };
        s.fx.setBackground(new LayerDrawable(fx));
        s.fx.setAlpha(0f);
        s.fx.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        dock.addView(s.fx, atD(x, y, 280, 158));

        s.bar = new View(this);
        s.bar.setBackground(Gfx.round(Color.WHITE, px(2)));
        s.bar.setAlpha(0f);
        s.bar.setScaleX(0f);
        dock.addView(s.bar, atD(x + 108, y + 172, 64, 4));
        return s;
    }

    // =====================================================================
    // Darstellung
    // =====================================================================
    int shownBg = -1, shownHero = -1;

    void applyAll(boolean anim) {
        int sceneI = sel;
        if ("cont".equals(zone) && cSel < cards.size() && !cards.get(cSel).hint) {
            int k = indexOf(cards.get(cSel).app);
            if (k >= 0) sceneI = k;
        }
        showBg(sceneI, anim);
        showHero(sel, anim);
        for (int i = 0; i < slots.length; i++) focusSlot(i, "apps".equals(zone) && i == sel, anim);
        for (int i = 0; i < cards.size(); i++) focusCard(cards.get(i), "cont".equals(zone) && i == cSel, anim);
        focusBtn(btnHome, icHome, "top".equals(zone) && tSel == 0, anim);
        focusBtn(btnSettings, icSettings, "top".equals(zone) && tSel == 1, anim);

        float bx;
        if ("apps".equals(zone)) bx = 96 + 28 + sel * 312 + 140 - 360;
        else bx = 96 + Math.min(cSel, 3) * 436 + 200 - 360;
        float ba = "top".equals(zone) ? 0f : 1f;
        mv(beam, anim, 550).translationX(px(bx)).alpha(ba);
        if (!anim) { beam.setTranslationX(px(bx)); beam.setAlpha(ba); }

        float cx = -Math.max(0, cSel - 3) * 436;
        mv(cardsRow, anim, 500).translationX(px(cx));
        if (!anim) cardsRow.setTranslationX(px(cx));
        setContMode("cont".equals(zone), anim);
    }

    android.view.ViewPropertyAnimator mv(View v, boolean anim, long ms) {
        android.view.ViewPropertyAnimator a = v.animate().setInterpolator(EASE).setDuration(anim ? ms : 0).setStartDelay(0);
        return a;
    }

    void showBg(final int i, boolean anim) {
        if (i == shownBg) return;
        final int old = shownBg;
        shownBg = i;
        final ImageView n = bgs[i];
        if (n.getDrawable() == null) n.setImageBitmap(loadBg(i));
        n.animate().cancel();
        n.setVisibility(View.VISIBLE);
        if (anim) {
            n.setAlpha(0f);
            n.setScaleX(1.045f); n.setScaleY(1.045f);
            n.animate().alpha(1f).setDuration(600).setStartDelay(0).setInterpolator(SOFT).withEndAction(null).start();
            ObjectAnimator z = ObjectAnimator.ofPropertyValuesHolder(n,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.045f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.045f, 1f));
            z.setDuration(1200);
            z.setInterpolator(EASE);
            z.start();
        } else {
            n.setAlpha(1f); n.setScaleX(1f); n.setScaleY(1f);
        }
        n.bringToFront();
        boolean music = "ytmusic".equals(APPS[i].id);
        if (ringWrap != null) {
            if (music) {
                ringWrap.bringToFront();
                ringWrap.setVisibility(View.VISIBLE);
                ringWrap.animate().alpha(1f).setDuration(anim ? 900 : 0).setStartDelay(0).withEndAction(null).start();
                for (ObjectAnimator r : ringAnims) if (!r.isStarted()) r.start(); else r.resume();
            } else {
                ringWrap.animate().alpha(0f).setDuration(anim ? 500 : 0).withEndAction(new Runnable() {
                    public void run() {
                        ringWrap.setVisibility(View.INVISIBLE);
                        for (ObjectAnimator r : ringAnims) r.pause();
                    }
                }).start();
            }
        }
        if (old >= 0) {
            final ImageView o = bgs[old];
            o.animate().cancel();
            if (anim) {
                o.animate().alpha(0f).setStartDelay(500).setDuration(300).withEndAction(new Runnable() {
                    public void run() { if (shownBg != old) o.setVisibility(View.INVISIBLE); }
                }).start();
            } else {
                o.setAlpha(0f);
                o.setVisibility(View.INVISIBLE);
            }
        }
    }

    void showHero(final int i, boolean anim) {
        if (i == shownHero) return;
        final int old = shownHero;
        shownHero = i;
        int dir = old < 0 ? 1 : (i > old ? 1 : -1);
        View n = heroes[i];
        n.animate().cancel();
        n.setVisibility(View.VISIBLE);
        if (anim) {
            n.setTranslationX(px(40) * dir);
            n.animate().alpha(1f).translationX(0).setDuration(600).setStartDelay(60).setInterpolator(EASE).withEndAction(null).start();
        } else {
            n.setAlpha(1f);
            n.setTranslationX(0);
        }
        if (old >= 0) {
            final View o = heroes[old];
            o.animate().cancel();
            if (anim) {
                o.animate().alpha(0f).translationX(-px(40) * dir).setDuration(380).setStartDelay(0).setInterpolator(EASE)
                        .withEndAction(new Runnable() {
                            public void run() { if (shownHero != old) o.setVisibility(View.INVISIBLE); }
                        }).start();
            } else {
                o.setAlpha(0f);
                o.setVisibility(View.INVISIBLE);
            }
        }
    }

    final boolean[] slotOn = new boolean[APPS.length];

    void focusSlot(int i, boolean on, boolean anim) {
        if (slotOn[i] == on && anim) return;
        slotOn[i] = on;
        Slot s = slots[i];
        boolean missing = APPS[i].pkg == null;
        float sc = on ? 1.12f : 1f, ty = on ? -px(14) : 0;
        float ta = on ? (missing ? 0.6f : 1f) : (missing ? 0.25f : 0.62f);
        long d = anim ? 420 : 0;
        s.tile.animate().scaleX(sc).scaleY(sc).translationY(ty).alpha(ta).setDuration(d).setStartDelay(0).setInterpolator(EASE).start();
        s.fx.animate().scaleX(sc).scaleY(sc).translationY(ty).alpha(on ? 1f : 0f).setDuration(d).setStartDelay(0).setInterpolator(EASE).start();
        s.glow.animate().scaleX(sc).scaleY(sc).translationY(ty).alpha(on ? 1f : 0f).setDuration(anim ? 480 : 0).setStartDelay(0).setInterpolator(EASE).start();
        s.bar.animate().scaleX(on ? 1f : 0f).alpha(on ? 1f : 0f).setDuration(anim ? 400 : 0).setStartDelay(0).setInterpolator(EASE).start();
        s.reflect.animate().alpha(on ? 0.9f : 0.35f).setDuration(anim ? 350 : 0).setStartDelay(0).start();
    }

    void focusCard(Card c, boolean on, boolean anim) {
        long d = anim ? 420 : 0;
        float sc = on ? 1.08f : 1f;
        c.view.animate().scaleX(sc).scaleY(sc).translationY(on ? -px(10) : 0).alpha(on ? 1f : 0.62f)
                .setDuration(d).setStartDelay(0).setInterpolator(EASE).start();
        c.ring.animate().alpha(on ? 1f : 0f).setDuration(anim ? 250 : 0).setStartDelay(0).start();
        if (c.play != null) c.play.animate().alpha(on ? 1f : 0f).scaleX(on ? 1f : 0.7f).scaleY(on ? 1f : 0.7f)
                .setDuration(anim ? 350 : 0).setStartDelay(0).setInterpolator(EASE).start();
    }

    void focusBtn(FrameLayout b, Gfx.Icon ic, boolean on, boolean anim) {
        b.setBackground(Gfx.round(on ? 0xfff5f5f7 : Gfx.w(0.1f), px(26)));
        ic.setColor(on ? 0xff0a0a0c : Gfx.w(0.92f));
        b.animate().scaleX(on ? 1.18f : 1f).scaleY(on ? 1.18f : 1f).setDuration(anim ? 300 : 0).setInterpolator(EASE).start();
    }

    boolean contShown = false;

    void setContMode(boolean on, boolean anim) {
        if (on == contShown && anim) return;
        contShown = on;
        long d = anim ? 700 : 0;
        lift.animate().translationY(on ? -px(400) : 0).setDuration(d).setStartDelay(0).setInterpolator(EASE).start();
        heroWrap.animate().alpha(on ? 0f : 1f).setDuration(anim ? 450 : 0).setStartDelay(0).start();
        cont.setVisibility(View.VISIBLE);
        cont.animate().alpha(on ? 1f : 0f).translationY(on ? 0 : px(260)).setDuration(d).setStartDelay(on && anim ? 120 : 0)
                .setInterpolator(EASE).withEndAction(on ? null : new Runnable() {
                    public void run() { if (!contShown) cont.setVisibility(View.INVISIBLE); }
                }).start();
        chev.animate().rotation(on ? 180 : 0).alpha(on ? 0.55f : 0.4f).setDuration(anim ? 500 : 0).setInterpolator(EASE).start();
    }

    // =====================================================================
    // Weiter ansehen
    // =====================================================================
    void buildCards() {
        cardsRow.removeAllViews();
        cards.clear();
        try {
            JSONArray r = new JSONArray(prefs.getString("recent", "[]"));
            for (int i = 0; i < r.length(); i++) {
                JSONObject o = r.getJSONObject(i);
                AppDef a = AppDef.byId(o.getString("id"));
                if (a == null || a.pkg == null) continue;
                Card c = new Card();
                c.app = a;
                c.title = a.name;
                c.sub = "Zuletzt geöffnet";
                c.right = ago(o.getLong("ts"));
                cards.add(c);
            }
        } catch (Exception ignored) { }
        if (cards.isEmpty()) {
            Card c = new Card();
            c.hint = true;
            c.title = "Noch leer";
            c.sub = "Öffne eine App, dann erscheint sie hier";
            c.right = "";
            cards.add(c);
        }
        for (int i = 0; i < cards.size(); i++) cardsRow.addView(buildCard(cards.get(i)), atD(i * 436, 0, 400, 225));
        if (cSel >= cards.size()) cSel = 0;
    }

    View buildCard(Card c) {
        FrameLayout f = new FrameLayout(this);
        final float rad = px(30);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad); }
        });
        f.setClipToOutline(true);
        int t1 = c.hint ? 0xff2a2a30 : c.app.t1, t2 = c.hint ? 0xff121215 : c.app.t2;
        GradientDrawable base = Gfx.grad(GradientDrawable.Orientation.TL_BR, new int[]{t1, t2}, rad);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[]{c.hint ? 0 : Gfx.a(c.app.glow, 0.6f), 0});
        glow.setGradientCenter(0.78f, 0.22f);
        glow.setGradientRadius(px(260));
        GradientDrawable gloss = Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Gfx.w(0.16f), Gfx.w(0), Gfx.w(0), 0x8c000000}, rad);
        f.setBackground(new LayerDrawable(new Drawable[]{base, glow, gloss}));

        TextView tag = text(c.hint ? "Tipp" : c.app.name, 16, "SemiBold", Gfx.w(0.92f), 0);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(px(14), 0, px(14), 0);
        GradientDrawable tb = Gfx.round(0x52000000, px(17));
        tb.setStroke(Math.max(1, px(1)), Gfx.w(0.18f));
        tag.setBackground(tb);
        f.addView(tag, atD(22, 20, -2, 34));

        if (!c.hint) {
            FrameLayout play = new FrameLayout(this);
            GradientDrawable pb = Gfx.round(Gfx.w(0.22f), px(29));
            pb.setStroke(Math.max(1, px(1)), Gfx.w(0.35f));
            play.setBackground(pb);
            Gfx.Icon pi = new Gfx.Icon(this, Gfx.Icon.PLAY);
            pi.setColor(Color.WHITE);
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(px(26), px(26));
            pl.gravity = Gravity.CENTER;
            pl.leftMargin = px(3);
            play.addView(pi, pl);
            play.setAlpha(0f);
            f.addView(play, atD(400 - 22 - 58, 20, 58, 58));
            c.play = play;
        }
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView ct = text(c.title, 30, "Bold", Color.WHITE, -0.03f);
        ct.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(ct);
        FrameLayout row = new FrameLayout(this);
        TextView cs = text(c.sub, 18, "Medium", Gfx.w(0.78f), 0);
        row.addView(cs, new FrameLayout.LayoutParams(-2, -2));
        TextView cr = text(c.right, 18, "Medium", Gfx.w(0.78f), 0);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2);
        rl.gravity = Gravity.END;
        row.addView(cr, rl);
        LinearLayout.LayoutParams rwl = new LinearLayout.LayoutParams(-1, -2);
        rwl.topMargin = px(8);
        txt.addView(row, rwl);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, -2);
        tl.gravity = Gravity.BOTTOM;
        tl.leftMargin = px(24); tl.rightMargin = px(24); tl.bottomMargin = px(24);
        f.addView(txt, tl);

        View ring = new View(this);
        GradientDrawable rg = Gfx.round(Color.TRANSPARENT, rad);
        rg.setStroke(Math.max(2, px(3)), Gfx.w(0.95f));
        ring.setBackground(rg);
        ring.setAlpha(0f);
        f.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        c.ring = ring;
        f.setAlpha(0.62f);
        f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        c.view = f;
        return f;
    }

    String ago(long ts) {
        long m = Math.round((System.currentTimeMillis() - ts) / 60000.0);
        if (m < 1) return "gerade eben";
        if (m < 60) return "vor " + m + " Min.";
        long h = Math.round(m / 60.0);
        if (h < 24) return "vor " + h + " Std.";
        long t = Math.round(h / 24.0);
        return t == 1 ? "gestern" : "vor " + t + " Tagen";
    }

    void remember(String id) {
        try {
            JSONArray r = new JSONArray(prefs.getString("recent", "[]")), out = new JSONArray();
            JSONObject me = new JSONObject();
            me.put("id", id);
            me.put("ts", System.currentTimeMillis());
            out.put(me);
            for (int i = 0; i < r.length() && out.length() < 8; i++) {
                if (!id.equals(r.getJSONObject(i).getString("id"))) out.put(r.getJSONObject(i));
            }
            prefs.edit().putString("recent", out.toString()).apply();
        } catch (Exception ignored) { }
    }

    // =====================================================================
    // Uhr
    // =====================================================================
    static final String[] TAGE = {"Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"};
    static final String[] MON = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"};
    final Runnable clockRun = new Runnable() { public void run() { clockTick(); } };

    void clockTick() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "Gute Nacht" : h < 11 ? "Guten Morgen" : h < 17 ? "Hallo" : h < 22 ? "Guten Abend" : "Gute Nacht";
        String gr = g + ", " + AppDef.NAME;
        String d = TAGE[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + ". " + MON[c.get(Calendar.MONTH)];
        String t = String.format(Locale.ROOT, "%02d:%02d", h, c.get(Calendar.MINUTE));
        greet.setText(gr);
        date.setText(d);
        time.setText(t);
        setWords(gr);
        iSub.setText(t + " · " + d);
        ui.removeCallbacks(clockRun);
        ui.postDelayed(clockRun, 10000);
    }

    // =====================================================================
    // Startanimation
    // =====================================================================
    ValueAnimator introAnim;
    boolean revealed = true;
    static final float INTRO_S = 4.6f;
    float flyX, flyY, flyK;
    boolean flyReady = false, greetShown = false;

    void playIntro(boolean withSound) {
        if (introAnim != null) introAnim.cancel();
        clockTick();
        if (withSound) playSound();
        intro.animate().cancel();
        intro.setVisibility(View.VISIBLE);
        intro.setAlpha(1f);
        for (TextView w : words) w.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        iSub.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setMainVisible(false);
        greet.setAlpha(0f);
        revealed = false;
        flyReady = false;
        greetShown = false;
        introFrame(0);
        introAnim = ValueAnimator.ofFloat(0f, INTRO_S);
        introAnim.setDuration(Math.round(INTRO_S * 1000));
        introAnim.setStartDelay(100);
        introAnim.setInterpolator(new LinearInterpolator());
        introAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) { introFrame((Float) a.getAnimatedValue()); }
        });
        introAnim.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) { if (!cancelled) endIntro(); }
        });
        introAnim.start();
    }

    /** Ein Bild der Startanimation, s = Sekunden. Nur Transparenz, Größe, Drehung, Position. */
    void introFrame(float s) {
        // 1) Fünf Lichtpunkte in den App-Farben kreisen spiralförmig zur Mitte
        for (int d = 0; d < dots.length; d++) {
            float a0 = (float) (d * Math.PI * 2 / dots.length) - 0.6f;
            float spin = kf(s, new float[]{0.15f, 1.55f}, new float[]{0, 4.4f});
            float r = kf(s, new float[]{0.15f, 1.55f}, new float[]{560, 150});
            ImageView dot = dots[d];
            dot.setTranslationX(px(r * (float) Math.cos(a0 + spin)));
            dot.setTranslationY(px(r * 0.62f * (float) Math.sin(a0 + spin)) + px((1 - r / 560f) * 0));
            dot.setAlpha(kf(s, new float[]{0.15f, 0.6f, 1.35f, 1.7f}, new float[]{0, 1, 1, 0}));
            float ds = kf(s, new float[]{0.15f, 1.35f, 1.7f}, new float[]{0.6f, 1f, 1.8f});
            dot.setScaleX(ds); dot.setScaleY(ds);
        }
        // 2) Ring entsteht, wo die Punkte ankommen; am Ende öffnet er sich wie ein Portal
        iRing.setAlpha(kf(s, new float[]{1.25f, 1.7f, 3.35f, 4.05f}, new float[]{0, 1, 1, 0}));
        float rs = kf(s, new float[]{1.25f, 1.7f, 3.35f, 4.05f}, new float[]{0.8f, 1f, 1.05f, 7f});
        rs *= 1 + 0.12f * pulse(s, 1.45f, 0.3f);
        iRing.setScaleX(rs); iRing.setScaleY(rs);
        iRing.setRotation(kf(s, new float[]{1.25f, 4.05f}, new float[]{-90, 160}));
        // 3) Farbschein blüht auf und dreht sich langsam
        iGlow.setAlpha(kf(s, new float[]{1.0f, 2.1f, 3.35f, 4.1f}, new float[]{0, 0.95f, 0.95f, 0}));
        float gs = kf(s, new float[]{1.0f, 2.1f, 3.35f, 4.1f}, new float[]{0.45f, 1f, 1.08f, 2.3f});
        gs *= 1 + 0.16f * pulse(s, 1.45f, 0.45f);
        iGlow.setAlpha(Math.min(1f, iGlow.getAlpha() + 0.35f * pulse(s, 1.45f, 0.4f)));
        iGlow.setScaleX(gs); iGlow.setScaleY(gs);
        iGlow.setRotation(kf(s, new float[]{1.0f, 4.1f}, new float[]{-40, 70}));
        // 4) Lichtlinie
        iSweep.setAlpha(kf(s, new float[]{1.35f, 1.5f, 1.95f, 2.6f}, new float[]{0, 1, 1, 0}));
        iFlash.setAlpha(0.42f * pulse(s, 1.45f, 0.28f) + 0.12f * pulse(s, 4.25f, 0.35f));
        iSweep.setScaleX(kf(s, new float[]{1.35f, 2.0f}, new float[]{0, 1}));
        // 5) Gruß: Wort für Wort
        for (int i = 0; i < words.size(); i++) {
            float st = 1.5f + i * 0.16f;
            TextView w = words.get(i);
            w.setAlpha(kf(s, new float[]{st, st + 0.55f}, new float[]{0, 1}));
            w.setTranslationY(px(kf(s, new float[]{st, st + 0.75f}, new float[]{56, 0})));
            float ws = kf(s, new float[]{st, st + 0.75f}, new float[]{1.12f, 1f});
            w.setScaleX(ws); w.setScaleY(ws);
        }
        iSub.setAlpha(kf(s, new float[]{2.2f, 2.7f, 3.3f, 3.6f}, new float[]{0, 1, 1, 0}));
        iSub.setTranslationY(px(kf(s, new float[]{2.2f, 2.8f}, new float[]{14, 0})));

        // 6) Übergang: Hintergrund öffnet sich, Gruß fliegt an seinen Platz oben links
        if (!revealed && s >= 3.3f) reveal(true);
        iBack.setAlpha(kf(s, new float[]{3.35f, 4.15f}, new float[]{1, 0}));
        if (s >= 3.3f && !flyReady) prepareFly();
        float f = flyReady ? kf(s, new float[]{3.35f, 4.35f}, new float[]{0, 1}) : 0;
        iWords.setTranslationX(flyX * f);
        iWords.setTranslationY(flyY * f);
        float k = 1 + (flyK - 1) * f;
        iWords.setScaleX(k); iWords.setScaleY(k);
        if (s >= 4.35f && !greetShown) {
            greetShown = true;
            greet.animate().cancel();
            greet.setAlpha(1f);      // kleiner Gruß übernimmt genau an derselben Stelle
            iWords.setAlpha(0f);
        } else if (!greetShown) {
            iWords.setAlpha(1f);
        }
    }

    /** Berechnet, wohin der große Gruß fliegen muss (Position des kleinen Grußes oben). */
    void prepareFly() {
        flyReady = true;
        if (iWords.getChildCount() == 0) return;
        View first = iWords.getChildAt(0), last = iWords.getChildAt(iWords.getChildCount() - 1);
        float textL = iWords.getLeft() + first.getLeft();
        float textR = iWords.getLeft() + last.getRight();
        float textT = iWords.getTop() + first.getTop();
        float textH = first.getHeight();
        View who = (View) greet.getParent();
        float gL = who.getLeft() + greet.getLeft();
        float gT = who.getTop() + greet.getTop();
        float gH = greet.getHeight();
        flyK = gH > 0 && textH > 0 ? gH / textH : 28f / 96f;
        iWords.setPivotX(textL - iWords.getLeft());
        iWords.setPivotY(textT - iWords.getTop());
        flyX = gL - textL;
        flyY = gT - textT;
        if (textR - textL <= 0) flyReady = false;
    }

    /** Kurzer Impuls zum Sound: springt bei c auf 1 und klingt ab. */
    static float pulse(float s, float c, float dec) {
        if (s < c) return 0f;
        return (float) Math.exp(-(s - c) / dec);
    }

    /** Wert zwischen Stützpunkten, jeweils weich auslaufend. */
    float kf(float p, float[] t, float[] v) {
        if (p <= t[0]) return v[0];
        for (int i = 1; i < t.length; i++) {
            if (p <= t[i]) {
                float x = (p - t[i - 1]) / (t[i] - t[i - 1]);
                return v[i - 1] + (v[i] - v[i - 1]) * EASE.getInterpolation(x);
            }
        }
        return v[v.length - 1];
    }

    void setMainVisible(boolean on) {
        int vis = on ? View.VISIBLE : View.INVISIBLE;
        bgWrap.setVisibility(vis);
        beam.setVisibility(vis);
        header.setVisibility(vis);
        lift.setVisibility(vis);
        chev.setVisibility(vis);
        if (!on) cont.setVisibility(View.INVISIBLE);
        if (on) kenBurns.resume(); else kenBurns.pause();
    }

    /** Startseite fährt ins Bild: Hintergrund zoomt heran, Leiste gleitet hoch. */
    void reveal(boolean anim) {
        revealed = true;
        setMainVisible(true);
        if (!kenBurns.isStarted()) kenBurns.start();
        kenBurns.resume();
        View pill = header.getChildAt(header.getChildCount() - 1);
        View avatar = header.getChildAt(0);
        if (anim) {
            ImageView bg = bgs[shownBg >= 0 ? shownBg : sel];
            bg.setScaleX(1.18f); bg.setScaleY(1.18f);
            bg.animate().scaleX(1f).scaleY(1f).setDuration(1500).setStartDelay(0).setInterpolator(EASE).start();
            header.setAlpha(1f); header.setTranslationY(0);
            for (View v : new View[]{pill, avatar, date}) {
                v.setAlpha(0f);
                v.setTranslationY(-px(18));
                v.animate().alpha(1f).translationY(0).setDuration(900).setStartDelay(500).setInterpolator(EASE).start();
            }
            lift.setAlpha(1f); lift.setTranslationY(0);
            heroWrap.setAlpha(0f);
            heroWrap.setTranslationX(-px(60));
            heroWrap.animate().alpha(1f).translationX(0).setDuration(1100).setStartDelay(450).setInterpolator(EASE).start();
            dock.setAlpha(0f);
            dock.setTranslationY(px(140));
            dock.animate().alpha(1f).translationY(0).setDuration(1100).setStartDelay(300).setInterpolator(EASE).start();
            beam.setAlpha(0f);
            beam.animate().alpha(1f).setDuration(900).setStartDelay(800).start();
            chev.setAlpha(0f);
            chev.animate().alpha(0.4f).setDuration(800).setStartDelay(900).start();
        } else {
            header.setAlpha(1f); header.setTranslationY(0);
            for (View v : new View[]{pill, avatar, date}) { v.setAlpha(1f); v.setTranslationY(0); }
            lift.setAlpha(1f); lift.setTranslationY(0);
            heroWrap.setAlpha(1f); heroWrap.setTranslationX(0);
            dock.setAlpha(1f); dock.setTranslationY(0);
            beam.setAlpha(1f);
            chev.setAlpha(0.4f);
        }
    }

    void endIntro() {
        if (!revealed) reveal(false);
        greet.animate().alpha(1f).setDuration(200).start();
        intro.setVisibility(View.GONE);
        iWords.setTranslationX(0); iWords.setTranslationY(0);
        iWords.setScaleX(1); iWords.setScaleY(1); iWords.setAlpha(1);
        iBack.setAlpha(1f);
        iFlash.setAlpha(0f);
        for (TextView w : words) w.setLayerType(View.LAYER_TYPE_NONE, null);
        iSub.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    void skipIntro() {
        if (introAnim != null) introAnim.cancel();
        if (!revealed) reveal(true);
        greet.animate().alpha(1f).setDuration(400).start();
        intro.animate().alpha(0f).setDuration(350).withEndAction(new Runnable() {
            public void run() { endIntro(); intro.setAlpha(1f); }
        }).start();
    }

    boolean introRunning() { return intro.getVisibility() == View.VISIBLE; }

    // =====================================================================
    // Fernbedienung
    // =====================================================================
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String k = null;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: k = "left"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: k = "right"; break;
            case KeyEvent.KEYCODE_DPAD_UP: k = "up"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: k = "down"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A: k = "ok"; break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE: k = "back"; break;
            case KeyEvent.KEYCODE_MENU: k = "menu"; break;
            default: break;
        }
        if (k == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) key(k);
        return true; // Zurück schließt den Startbildschirm nie
    }

    void key(String k) {
        if (launching) return;
        if (introRunning() && !"menu".equals(k)) { skipIntro(); return; }
        int n = APPS.length;
        if ("apps".equals(zone)) {
            if ("left".equals(k)) sel = Math.max(0, sel - 1);
            else if ("right".equals(k)) sel = Math.min(n - 1, sel + 1);
            else if ("down".equals(k)) { if (!cards.isEmpty()) zone = "cont"; }
            else if ("up".equals(k)) { zone = "top"; tSel = 0; }
            else if ("ok".equals(k)) openAppFx(APPS[sel], slots[sel].tile);
            prefs.edit().putInt("sel", sel).apply();
        } else if ("cont".equals(zone)) {
            if ("left".equals(k)) cSel = Math.max(0, cSel - 1);
            else if ("right".equals(k)) cSel = Math.min(cards.size() - 1, cSel + 1);
            else if ("up".equals(k) || "back".equals(k)) zone = "apps";
            else if ("ok".equals(k) && cSel < cards.size()) {
                Card c = cards.get(cSel);
                if (c.hint) showToast("Öffne eine App, dann erscheint sie hier");
                else openAppFx(c.app, c.view);
            }
        } else if ("top".equals(zone)) {
            if ("left".equals(k)) tSel = 0;
            else if ("right".equals(k)) tSel = 1;
            else if ("down".equals(k) || "back".equals(k)) zone = "apps";
            else if ("ok".equals(k)) {
                if (tSel == 0) openAmazonHome(); else openSettings();
                zone = "apps";
            }
        }
        if ("menu".equals(k)) openSettings();
        applyAll(true);
    }

    final Runnable hideToast = new Runnable() { public void run() { toast.animate().alpha(0f).translationY(-px(20)).setDuration(300).start(); } };

    void showToast(String m) {
        toast.setText(m);
        toast.setTranslationY(-px(20));
        toast.animate().alpha(1f).translationY(0).setDuration(400).setInterpolator(EASE).start();
        ui.removeCallbacks(hideToast);
        ui.postDelayed(hideToast, 2600);
    }

    // =====================================================================
    // Apps öffnen
    // =====================================================================
    boolean openApp(AppDef a) {
        if (a.pkg == null) { showToast(a.name + " ist nicht installiert"); return false; }
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(a.pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(a.pkg);
        if (i == null || !start(i)) { showToast(a.name + " lässt sich nicht öffnen"); return false; }
        overridePendingTransition(0, 0);
        remember(a.id);
        return true;
    }

    // ---------- Kachel zieht auf Vollbild auf und blendet dunkel aus ----------
    FrameLayout launchFx;
    ValueAnimator launchAnim;
    boolean launching = false;

    void openAppFx(final AppDef a, View src) {
        if (launching) return;
        if (a.pkg == null) { showToast(a.name + " ist nicht installiert"); return; }
        launching = true;
        final int W = root.getWidth(), H = root.getHeight();
        float[] c = centerOf(src);
        final float cx = c[0], cy = c[1];
        final float sx0 = src.getWidth() * src.getScaleX() / W, sy0 = src.getHeight() * src.getScaleY() / H;

        final FrameLayout fx = new FrameLayout(this);
        fx.setBackground(new LayerDrawable(new Drawable[]{
                new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a.t1, a.t2}),
                new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Gfx.w(0.18f), Gfx.w(0), Gfx.w(0)}),
        }));
        final float[] rad = {px(30) / sx0};
        fx.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad[0]); }
        });
        fx.setClipToOutline(true);
        final TextView lbl = text(a.name, 32, "Bold", TXT, -0.035f);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(32) / sx0);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2);
        ll.gravity = Gravity.CENTER;
        fx.addView(lbl, ll);
        final View dark = new View(this);
        dark.setBackgroundColor(Color.BLACK);
        dark.setAlpha(0f);
        fx.addView(dark, new FrameLayout.LayoutParams(-1, -1));
        fx.setPivotX(W / 2f);
        fx.setPivotY(H / 2f);
        fx.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(fx, new FrameLayout.LayoutParams(-1, -1));
        launchFx = fx;

        launchAnim = ValueAnimator.ofFloat(0f, 1f);
        launchAnim.setDuration(680);
        launchAnim.setInterpolator(new LinearInterpolator());
        launchAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float t = (Float) va.getAnimatedValue();
                float e = EASE.getInterpolation(Math.min(1f, t / 0.72f));
                float sx = sx0 + (1 - sx0) * e, sy = sy0 + (1 - sy0) * e;
                fx.setScaleX(sx);
                fx.setScaleY(sy);
                fx.setTranslationX((cx - W / 2f) * (1 - e));
                fx.setTranslationY((cy - H / 2f) * (1 - e));
                rad[0] = px(30) * (1 - e) / Math.max(sx, 0.01f);
                fx.invalidateOutline();
                lbl.setAlpha(Math.max(0f, 1f - t / 0.28f));
                dark.setAlpha(kf(t, new float[]{0.3f, 1f}, new float[]{0f, 1f}));
            }
        });
        launchAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                if (!openApp(a)) clearLaunchFx(true);
            }
        });
        launchAnim.start();
    }

    /** Mittelpunkt einer Ansicht auf dem Bildschirm (inkl. Verschiebungen). */
    float[] centerOf(View v) {
        float x = v.getLeft() + v.getTranslationX() + v.getWidth() / 2f;
        float y = v.getTop() + v.getTranslationY() + v.getHeight() / 2f;
        View p = (View) v.getParent();
        while (p != null && p != root) {
            x += p.getLeft() + p.getTranslationX();
            y += p.getTop() + p.getTranslationY();
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        }
        return new float[]{x, y};
    }

    void clearLaunchFx(boolean anim) {
        launching = false;
        if (launchAnim != null) launchAnim.cancel();
        final FrameLayout fx = launchFx;
        launchFx = null;
        if (fx == null) return;
        if (anim) {
            fx.animate().alpha(0f).setDuration(450).setStartDelay(80).withEndAction(new Runnable() {
                public void run() { root.removeView(fx); }
            }).start();
        } else root.removeView(fx);
    }

    void openAmazonHome() {
        HomeWatcherService.allowAmazonHome = true;
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setPackage(HomeWatcherService.AMAZON_LAUNCHER);
        if (!start(i)) {
            Intent j = getPackageManager().getLaunchIntentForPackage(HomeWatcherService.AMAZON_LAUNCHER);
            if (j == null || !start(j)) showToast("Fire-TV-Startseite nicht gefunden");
        }
    }

    void openSettings() {
        if (!start(new Intent(Settings.ACTION_SETTINGS))) {
            Intent j = new Intent();
            j.setComponent(new ComponentName(HomeWatcherService.AMAZON_LAUNCHER, "com.amazon.tv.launcher.ui.SettingsActivity"));
            start(j);
        }
    }

    boolean start(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            Log.e(HomeWatcherService.TAG, "Start fehlgeschlagen: " + i, e);
            return false;
        }
    }

    void resolveApps() {
        PackageManager pm = getPackageManager();
        Map<String, String> labels = new HashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, labels);
        collect(pm, Intent.CATEGORY_LAUNCHER, labels);
        for (AppDef a : APPS) {
            a.pkg = null;
            for (String p : a.pakete) if (labels.containsKey(p)) { a.pkg = p; break; }
            if (a.pkg == null) {
                for (String want : a.namen) {
                    for (Map.Entry<String, String> en : labels.entrySet()) {
                        if (en.getValue().equals(want)) { a.pkg = en.getKey(); break; }
                    }
                    if (a.pkg != null) break;
                }
            }
            Log.i(HomeWatcherService.TAG, a.id + " -> " + a.pkg);
        }
    }

    void collect(PackageManager pm, String category, Map<String, String> labels) {
        try {
            List<ResolveInfo> list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
            for (ResolveInfo r : list) {
                String pkg = r.activityInfo.packageName;
                if (labels.containsKey(pkg)) continue;
                CharSequence l = r.loadLabel(pm);
                labels.put(pkg, l == null ? "" : l.toString().toLowerCase(Locale.ROOT).trim());
            }
        } catch (Exception e) { Log.e(HomeWatcherService.TAG, "collect", e); }
    }

    int indexOf(AppDef a) {
        for (int i = 0; i < APPS.length; i++) if (APPS[i] == a) return i;
        return -1;
    }

    // =====================================================================
    // Bilder + Ton
    // =====================================================================
    Bitmap loadBg(int i) {
        try {
            InputStream in = getAssets().open("bg/bg_" + APPS[i].id + ".jpg");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) {
            Log.e(HomeWatcherService.TAG, "Hintergrund " + i, e);
            return null;
        }
    }

    void loadOtherBackgrounds() {
        new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < APPS.length; i++) {
                    if (i == sel) continue;
                    final int k = i;
                    final Bitmap b = loadBg(i);
                    ui.post(new Runnable() { public void run() { if (bgs[k].getDrawable() == null) bgs[k].setImageBitmap(b); } });
                }
            }
        }).start();
    }

    void loadSound() {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            AssetFileDescriptor fd = getAssets().openFd("intro.ogg");
            player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            player.setVolume(LAUTSTAERKE, LAUTSTAERKE);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    soundReady = true;
                    if (soundPending) { soundPending = false; mp.start(); }
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            Log.e(HomeWatcherService.TAG, "Startsound", e);
            player = null;
        }
    }

    void playSound() {
        if (player == null) return;
        try {
            if (!soundReady) { soundPending = true; return; }
            if (player.isPlaying()) player.pause();
            player.seekTo(0);
            player.start();
        } catch (Exception e) { Log.e(HomeWatcherService.TAG, "Startsound abspielen", e); }
    }

    // =====================================================================
    // Lebenszyklus
    // =====================================================================
    void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    boolean first = true;

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        HomeWatcherService.allowAmazonHome = false;
        if (first) { first = false; return; }
        long away = pausedAt == 0 ? 0 : SystemClock.elapsedRealtime() - pausedAt;
        boolean woke = wokeUp;
        wokeUp = false;
        resolveApps();
        clockTick();
        buildCards();
        zone = "apps";
        cSel = 0;
        applyAll(false);
        boolean intro = woke || away > 120000;
        clearLaunchFx(!intro);
        if (intro) playIntro(woke);
        else kenBurns.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausedAt = SystemClock.elapsedRealtime();
        kenBurns.pause();
        for (ObjectAnimator r : ringAnims) r.pause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(screenOn); } catch (Exception ignored) { }
        if (player != null) player.release();
        super.onDestroy();
    }
}
