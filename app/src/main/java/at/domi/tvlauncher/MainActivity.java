package at.domi.tvlauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.SearchManager;
import android.net.Uri;
import java.util.Random;
import java.util.concurrent.Callable;
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
    /** Starkes Gerät (z. B. Fire TV Stick 4K): schärfere Bilder, mehr Effekte. */
    boolean HIGH = false;
    static final boolean PHOTO_BG = true;
    final List<Animator> ambient = new ArrayList<>();
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
    /** Hintergrundfoto je App: Netflix, YouTube, Prime = Ken Carson; Disney+, YT Music = Bleood. */
    static final String[] APP_PHOTO = {"0_0", "0_1", "0_2", "1_0", "1_1"};
    int sel = 0, cSel = 0, tSel = 0;
    String zone = "apps";
    final List<Card> cards = new ArrayList<>();
    long pausedAt = 0;
    boolean wokeUp = false;

    // ---------- Ansichten ----------
    FrameLayout root, bgWrap, kbWrap, ringWrap, lift, heroWrap, dock, cont, cardsRow, intro, header, rapStage, rapRow;
    final FrameLayout[] fxw = new FrameLayout[AppDef.APPS.length];
    final List<List<Animator>> fxAnims = new ArrayList<>();
    final List<List<View>> heroLetters = new ArrayList<>();
    final Rapper[] RAP = Rapper.LIST;
    final RapCard[] rapCards = new RapCard[Rapper.LIST.length];
    final Bitmap[] rapGlows = new Bitmap[Rapper.LIST.length];
    int rSel = 0;
    ImageView rapGlow, rapPicA, rapPicB, svA, svB;
    FrameLayout saver;
    TextView svTime, svDate;
    boolean saverOn = false, svUseA = true;
    int svPos = -1;
    static final long SAVER_AFTER_MS = 3 * 60 * 1000;
    boolean rapUseA = true;
    TextView rapName;
    ValueAnimator rapPulse;
    ObjectAnimator rapDrift;
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

    static class RapCard {
        FrameLayout view;
        View ring, play;
    }

    static class Slot {
        View reflect, fx, bar, band;
        FrameLayout sheen;
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
        fixTimeZone();
        loadSound();
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeSet, tf);
        prefs = getSharedPreferences("domitv", MODE_PRIVATE);
        sel = Math.max(0, Math.min(APPS.length - 1, prefs.getInt("sel", 0)));

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.setBackgroundDrawable(new ColorDrawable(BG));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
        S = Math.min(sw / 1920f, sh / 1080f);

        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            HIGH = mi.totalMem >= 1600L * 1024 * 1024 && !am.isLowRamDevice();
        } catch (Exception ignored) { }
        Log.i(HomeWatcherService.TAG, "Gerät stark: " + HIGH);
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
        long up = SystemClock.elapsedRealtime();
        if (up < 90000) intro.setAlpha(0f);
        root.postDelayed(new Runnable() { public void run() { playIntro(true); } }, up < 90000 ? 2500 : 0);
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
        kbWrap = new FrameLayout(this);
        bgWrap.addView(kbWrap, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < APPS.length; i++) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setAlpha(0f);
            iv.setVisibility(View.INVISIBLE);
            bgs[i] = iv;
            kbWrap.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            if ("ytmusic".equals(APPS[i].id)) {
                ringWrap = new FrameLayout(this);
                ringWrap.setAlpha(0f);
                ringWrap.setVisibility(View.INVISIBLE);
                kbWrap.addView(ringWrap, new FrameLayout.LayoutParams(-1, -1));
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
        for (int i = 0; i < APPS.length; i++) buildSceneFx(i);
        if (HIGH) buildAmbient();
        kbWrap.setPivotX(px(1150));
        kbWrap.setPivotY(px(380));
        kenBurns = ObjectAnimator.ofPropertyValuesHolder(kbWrap,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.08f, 1.15f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.08f, 1.15f),
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

        // --- Rapper-Bühne (Hintergrund in der Rapper-Reihe) ---
        rapStage = new FrameLayout(this);
        rapStage.setAlpha(0f);
        rapStage.setVisibility(View.INVISIBLE);
        rapStage.setClipChildren(false);
        root.addView(rapStage, new FrameLayout.LayoutParams(-1, -1));
        View rapDark = new View(this);
        rapDark.setBackgroundColor(0xdc050507);
        rapStage.addView(rapDark, new FrameLayout.LayoutParams(-1, -1));
        rapPicA = new ImageView(this);
        rapPicB = new ImageView(this);
        for (ImageView pv : new ImageView[]{rapPicA, rapPicB}) {
            pv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pv.setAlpha(0f);
            pv.setPivotX(px(1350));
            pv.setPivotY(px(380));
            rapStage.addView(pv, new FrameLayout.LayoutParams(-1, -1));
        }
        rapGlow = new ImageView(this);
        rapGlow.setScaleType(ImageView.ScaleType.FIT_XY);
        rapStage.addView(rapGlow, atD(420, -300, 1900, 1150));
        for (int r = 0; r < RAP.length; r++) rapGlows[r] = Gfx.glow(RAP[r].c1, 0.7f);
        rapName = new TextView(this);
        rapName.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(340));
        rapName.setTypeface(font("Black"));
        rapName.setLetterSpacing(-0.03f);
        rapName.setIncludeFontPadding(false);
        rapName.setSingleLine(true);
        rapName.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        rapName.setVisibility(View.GONE);
        rapStage.addView(rapName, atD(60, 120, 4200, 420));
        rapPulse = ValueAnimator.ofFloat(0f, 1f);
        rapPulse.setDuration(5200);           // langsames Atmen
        rapPulse.setRepeatCount(ValueAnimator.INFINITE);
        rapPulse.setInterpolator(new LinearInterpolator());
        rapPulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float e = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * (Float) va.getAnimatedValue());
                boolean hasPic = shownRapper >= 0 && rapPicCount(shownRapper) > 0;
                rapGlow.setAlpha(hasPic ? 0.05f + 0.06f * e : 0.30f + 0.15f * e);
                float sc = 1f + 0.04f * e;
                rapGlow.setScaleX(sc); rapGlow.setScaleY(sc);
                ImageView cur = rapUseA ? rapPicA : rapPicB;
                if (cur.getAnimation() == null) {
                    float ps = rapBase + 0.012f * e;
                    cur.setScaleX(ps); cur.setScaleY(ps);
                }
            }
        });
        rapDrift = ObjectAnimator.ofFloat(rapName, View.TRANSLATION_X, 0f, -px(900));
        rapDrift.setDuration(24000);
        rapDrift.setRepeatMode(ValueAnimator.REVERSE);
        rapDrift.setRepeatCount(ValueAnimator.INFINITE);
        rapDrift.setInterpolator(SOFT);

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
        GradientDrawable db = Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Gfx.w(0.05f), Gfx.w(0.012f)}, px(54));
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
        root.addView(cont, atD(96, 650, 1728, 760));
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
        LinearLayout head2 = new LinearLayout(this);
        head2.setOrientation(LinearLayout.HORIZONTAL);
        head2.setBaselineAligned(true);
        head2.addView(text("Deine Rapper", 34, "Bold", TXT, -0.025f));
        TextView sub2 = text("Direkt in YT Music", 22, "Medium", Gfx.a(TXT, 0.55f), 0);
        LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(-2, -2);
        cl2.leftMargin = px(18);
        head2.addView(sub2, cl2);
        cont.addView(head2, atD(0, 350, -2, -2));
        rapRow = new FrameLayout(this);
        rapRow.setClipChildren(false);
        cont.addView(rapRow, atD(0, 420, 2000, 260));
        for (int r = 0; r < RAP.length; r++) {
            rapCards[r] = buildRapCard(RAP[r]);
            rapRow.addView(rapCards[r].view, atD(r * 348, 0, 320, 190));
        }

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

        // --- Bildschirmschoner (Rapper-Fotos) ---
        saver = new FrameLayout(this);
        saver.setBackgroundColor(Color.BLACK);
        saver.setVisibility(View.GONE);
        root.addView(saver, new FrameLayout.LayoutParams(-1, -1));
        svA = new ImageView(this);
        svB = new ImageView(this);
        for (ImageView v : new ImageView[]{svA, svB}) {
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setAlpha(0f);
            v.setPivotX(px(1400));
            v.setPivotY(px(400));
            saver.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
        View svShade = new View(this);
        svShade.setBackground(Gfx.grad(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xcc000000, 0x40000000, 0}, 0));
        saver.addView(svShade, new FrameLayout.LayoutParams(-1, -1));
        svTime = text("", 150, "Bold", Color.WHITE, -0.03f);
        svTime.setFontFeatureSettings("tnum");
        saver.addView(svTime, atD(96, 800, -2, -2));
        svDate = text("", 32, "Medium", Gfx.w(0.75f), 0);
        saver.addView(svDate, atD(102, 980, -2, -2));

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

    RapCard buildRapCard(Rapper r) {
        RapCard rc = new RapCard();
        FrameLayout f = new FrameLayout(this);
        final float rad = px(28);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad); }
        });
        f.setClipToOutline(true);
        GradientDrawable base = Gfx.grad(GradientDrawable.Orientation.TL_BR, new int[]{0xff0c0c10, r.c2}, rad);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[]{Gfx.a(r.c1, 0.75f), 0});
        glow.setGradientCenter(0.12f, 1.0f);
        glow.setGradientRadius(px(300));
        GradientDrawable stripe = Gfx.grad(GradientDrawable.Orientation.TL_BR, new int[]{0, Gfx.a(r.c1, 0.16f), 0, 0}, rad);
        GradientDrawable gloss = Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Gfx.w(0.14f), Gfx.w(0), Gfx.w(0)}, rad);
        f.setBackground(new LayerDrawable(new Drawable[]{base, glow, stripe, gloss}));
        f.addView(text("RAPPER", 14, "Bold", Gfx.w(0.7f), 0.28f), atD(22, 22, -2, -2));
        View dot = new View(this);
        dot.setBackground(Gfx.round(r.c1, px(5)));
        f.addView(dot, atD(22 + 92, 26, 10, 10));
        TextView nm = text(r.name.toUpperCase(Locale.ROOT), 40, "Black", Color.WHITE, -0.02f);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2);
        nl.gravity = Gravity.BOTTOM | Gravity.START;
        nl.leftMargin = px(22);
        nl.bottomMargin = px(48);
        f.addView(nm, nl);
        TextView sub = text("Shuffle auf YouTube", 16, "Medium", Gfx.w(0.72f), 0);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2);
        sl.gravity = Gravity.BOTTOM | Gravity.START;
        sl.leftMargin = px(22);
        sl.bottomMargin = px(22);
        f.addView(sub, sl);
        FrameLayout play = new FrameLayout(this);
        play.setBackground(Gfx.round(r.c1, px(25)));
        Gfx.Icon pi = new Gfx.Icon(this, Gfx.Icon.PLAY);
        pi.setColor(Color.WHITE);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(px(22), px(22));
        pl.gravity = Gravity.CENTER;
        pl.leftMargin = px(3);
        play.addView(pi, pl);
        play.setAlpha(0f);
        f.addView(play, atD(320 - 20 - 50, 18, 50, 50));
        View ring = new View(this);
        GradientDrawable rg = Gfx.round(Color.TRANSPARENT, rad);
        rg.setStroke(Math.max(2, px(3)), Gfx.w(0.95f));
        ring.setBackground(rg);
        ring.setAlpha(0f);
        f.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        f.setAlpha(0.62f);
        f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        rc.view = f;
        rc.ring = ring;
        rc.play = play;
        return rc;
    }

    /** Lebendige Hintergründe: kleine Ebenen, die sich nur bewegen (GPU). */
    void buildSceneFx(int i) {
        AppDef a = APPS[i];
        List<Animator> an = new ArrayList<>();
        fxAnims.add(an);
        if ("ytmusic".equals(a.id) && !PHOTO_BG) {
            fxw[i] = ringWrap;
            an.addAll(ringAnims);
            return;
        }
        FrameLayout w = new FrameLayout(this);
        w.setAlpha(0f);
        w.setVisibility(View.INVISIBLE);
        kbWrap.addView(w, new FrameLayout.LayoutParams(-1, -1));
        fxw[i] = w;
        Random rnd = new Random(11 + i * 7);
        if (PHOTO_BG) {
            ImageView tint = new ImageView(this);
            tint.setImageBitmap(Gfx.glow(a.glow, 0.4f));
            tint.setScaleType(ImageView.ScaleType.FIT_XY);
            w.addView(tint, atD(700, -420, 1700, 1100));
            an.add(loop(tint, 6000, rnd,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.18f, 0.32f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1.08f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.95f, 1.08f)));
            return;
        }
        if ("netflix".equals(a.id)) {
            Bitmap st = Gfx.streak(0xffff3040);
            for (int k = 0; k < (HIGH ? 7 : 5); k++) {
                ImageView v = new ImageView(this);
                v.setImageBitmap(st);
                v.setScaleType(ImageView.ScaleType.FIT_XY);
                float wd = 110 + rnd.nextFloat() * 70;
                w.addView(v, atD(1000 + k * (HIGH ? 120 : 165) + rnd.nextFloat() * 40, -160, wd, 1050));
                v.setPivotX(px(wd / 2));
                v.setPivotY(0);
                float r0 = 2 + rnd.nextFloat() * 3;
                an.add(loop(v, 4200 + rnd.nextInt(4000), rnd,
                        PropertyValuesHolder.ofFloat(View.ROTATION, -r0, r0),
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0.2f, 0.75f)));
            }
        } else if ("youtube".equals(a.id)) {
            Bitmap g = Gfx.glow(Color.WHITE, 0.2f);
            float[][] pos = {{820, 60, 1000, 640}, {1180, 300, 900, 560}};
            for (int k = 0; k < pos.length; k++) {
                ImageView v = new ImageView(this);
                v.setImageBitmap(g);
                v.setScaleType(ImageView.ScaleType.FIT_XY);
                w.addView(v, atD(pos[k][0], pos[k][1], pos[k][2], pos[k][3]));
                an.add(loop(v, 9000 + k * 3500, rnd,
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -px(220), px(220)),
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0.35f, 0.95f)));
            }
        } else if ("prime".equals(a.id)) {
            ImageView sun = new ImageView(this);
            sun.setImageBitmap(Gfx.glow(0xff9fe6ff, 0.75f));
            sun.setScaleType(ImageView.ScaleType.FIT_XY);
            w.addView(sun, atD(1400 - 520, 660 - 230, 1040, 460));
            an.add(loop(sun, 5200, rnd,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.88f, 1.18f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.88f, 1.12f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 1f)));
        } else if ("disney".equals(a.id)) {
            Bitmap star = Gfx.dot(0xffcfd8ff);
            for (int k = 0; k < (HIGH ? 34 : 18); k++) {
                ImageView v = new ImageView(this);
                v.setImageBitmap(star);
                v.setScaleType(ImageView.ScaleType.FIT_XY);
                float sz = 18 + rnd.nextFloat() * 26;
                w.addView(v, atD(860 + rnd.nextFloat() * 1020, 30 + rnd.nextFloat() * 600, sz, sz));
                an.add(loop(v, 1300 + rnd.nextInt(2600), rnd,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0.05f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1.25f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.25f)));
            }
        }
    }

    /** Nur auf starken Geräten: schwebende Lichtpartikel + feines, lebendiges Filmkorn. */
    void buildAmbient() {
        FrameLayout dust = new FrameLayout(this);
        bgWrap.addView(dust, new FrameLayout.LayoutParams(-1, -1));
        Bitmap mote = Gfx.dot(0xfffff2e0);
        Random rnd = new Random(42);
        for (int k = 0; k < 30; k++) {
            ImageView v = new ImageView(this);
            v.setImageBitmap(mote);
            v.setScaleType(ImageView.ScaleType.FIT_XY);
            float sz = 8 + rnd.nextFloat() * 22;
            dust.addView(v, atD(rnd.nextFloat() * 1920, 200 + rnd.nextFloat() * 900, sz, sz));
            v.setAlpha(0f);
            float peak = 0.15f + rnd.nextFloat() * 0.45f;
            ObjectAnimator o = ObjectAnimator.ofPropertyValuesHolder(v,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -px(220 + rnd.nextFloat() * 260)),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, px((rnd.nextFloat() - 0.5f) * 160)),
                    PropertyValuesHolder.ofKeyframe(View.ALPHA,
                            android.animation.Keyframe.ofFloat(0f, 0f),
                            android.animation.Keyframe.ofFloat(0.3f, peak),
                            android.animation.Keyframe.ofFloat(0.7f, peak),
                            android.animation.Keyframe.ofFloat(1f, 0f)));
            long ms = 11000 + rnd.nextInt(12000);
            o.setDuration(ms);
            o.setRepeatCount(ValueAnimator.INFINITE);
            o.setInterpolator(new LinearInterpolator());
            o.setCurrentPlayTime((long) (rnd.nextFloat() * ms));
            ambient.add(o);
        }
        // Filmkorn: kleines Rauschbild, gekachelt, springt leicht -> wirkt wie echter Film
        Bitmap nb = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        int[] px = new int[192 * 192];
        for (int i = 0; i < px.length; i++) { int g = rnd.nextInt(256); px[i] = Color.argb(255, g, g, g); }
        nb.setPixels(px, 0, 192, 0, 0, 192, 192);
        android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(getResources(), nb);
        bd.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        final View grain = new View(this);
        grain.setBackground(bd);
        grain.setAlpha(0.035f);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(px(1920 + 200), px(1080 + 200));
        gl.leftMargin = -px(100);
        gl.topMargin = -px(100);
        bgWrap.addView(grain, gl);
        final Random gr = new Random(7);
        ValueAnimator g = ValueAnimator.ofFloat(0f, 1f);
        g.setDuration(1000);
        g.setRepeatCount(ValueAnimator.INFINITE);
        g.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            int f = 0;
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                if ((f++ & 1) != 0) return;   // alle 2 Bilder neu, wie 30-fps-Film
                grain.setTranslationX(gr.nextInt(px(96)));
                grain.setTranslationY(gr.nextInt(px(96)));
            }
        });
        ambient.add(g);
    }

    ObjectAnimator loop(View v, long ms, Random rnd, PropertyValuesHolder... p) {
        ObjectAnimator o = ObjectAnimator.ofPropertyValuesHolder(v, p);
        o.setDuration(ms);
        o.setRepeatMode(ValueAnimator.REVERSE);
        o.setRepeatCount(ValueAnimator.INFINITE);
        o.setInterpolator(SOFT);
        o.setCurrentPlayTime((long) (rnd.nextFloat() * ms));
        return o;
    }

    void pauseFx() {
        for (Animator a : ambient) a.pause();
        for (List<Animator> l : fxAnims) for (Animator a : l) a.pause();
        if (rapPulse != null) { rapPulse.pause(); rapDrift.pause(); }
    }

    void resumeFx() {
        for (Animator a : ambient) { if (!a.isStarted()) a.start(); else a.resume(); }
        if (shownBg >= 0 && shownBg < fxAnims.size())
            for (Animator a : fxAnims.get(shownBg)) { if (!a.isStarted()) a.start(); else a.resume(); }
        if ("rap".equals(shownMode)) { rapPulse.resume(); rapDrift.resume(); }
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
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        List<View> letters = new ArrayList<>();
        for (int c = 0; c < a.name.length(); c++) {
            Gfx.GradientText lt = new Gfx.GradientText(this);
            lt.setText(a.name.substring(c, c + 1));
            lt.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(200));
            lt.setTypeface(font("Black"));
            lt.setIncludeFontPadding(false);
            lt.setSingleLine(true);
            lt.setTextColor(Color.WHITE);
            lt.setPadding(0, 0, 0, px(6));
            lt.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (c > 0) lp.leftMargin = -px(11);
            title.addView(lt, lp);
            letters.add(lt);
        }
        heroLetters.add(letters);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = px(10);
        h.addView(title, tl);
        h.setAlpha(0f);
        h.setVisibility(View.INVISIBLE);
        return h;
    }

    /** Getöntes Glas in der App-Farbe mit hellem Rand. */
    GradientDrawable glassTile(AppDef a) {
        GradientDrawable g = Gfx.grad(GradientDrawable.Orientation.TL_BR, new int[]{Gfx.a(a.t1, 0.42f), Gfx.a(a.t2, 0.18f)}, px(30));
        g.setStroke(Math.max(1, px(1.5f)), Gfx.w(0.28f));
        return g;
    }

    Slot buildSlot(AppDef a, int i) {
        Slot s = new Slot();
        float x = 28 + i * 312, y = 28;
        s.reflect = new View(this);
        GradientDrawable rd = Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Gfx.a(a.t1, 0.42f), Gfx.a(a.t2, 0.08f), Gfx.a(a.t2, 0)}, 0);
        rd.setCornerRadii(new float[]{px(30), px(30), px(30), px(30), 0, 0, 0, 0});
        s.reflect.setBackground(rd);
        s.reflect.setAlpha(0.18f);
        dock.addView(s.reflect, atD(x, y + 166, 280, 120));

        s.glow = new ImageView(this);
        s.glow.setImageBitmap(Gfx.glow(a.glow, 0.42f));
        s.glow.setScaleType(ImageView.ScaleType.FIT_XY);
        s.glow.setAlpha(0f);
        dock.addView(s.glow, atD(x - 150, y - 130, 580, 418));

        s.tile = new FrameLayout(this);
        Drawable[] layers = {
                glassTile(a),
                Gfx.grad(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Gfx.w(0.16f), Gfx.w(0.02f), Gfx.w(0)}, px(30)),
        };
        s.tile.setBackground(new LayerDrawable(layers));
        TextView lbl = text(a.name, 32, "Bold", TXT, -0.035f);
        lbl.setShadowLayer(px(10), 0, px(2), 0x99000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.CENTER;
        s.tile.addView(lbl, lp);
        s.tile.setAlpha(0.82f);
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

        s.sheen = new FrameLayout(this);
        final float srad = px(30);
        s.sheen.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), srad); }
        });
        s.sheen.setClipToOutline(true);
        s.band = new View(this);
        s.band.setBackground(Gfx.grad(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Gfx.w(0), Gfx.w(0.42f), Gfx.w(0)}, 0));
        s.band.setRotation(18);
        s.band.setAlpha(0f);
        s.sheen.addView(s.band, atD(-220, -70, 110, 300));
        dock.addView(s.sheen, atD(x, y, 280, 158));

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
        for (int i = 0; i < rapCards.length; i++) focusRap(rapCards[i], "rap".equals(zone) && i == rSel, anim);
        if ("rap".equals(zone)) setRapper(rSel, anim);
        float par = "rap".equals(zone) ? -2 * 30 : -(sel - (APPS.length - 1) / 2f) * 30;
        mv(bgWrap, anim, 1200).translationX(px(par));
        if (!anim) bgWrap.setTranslationX(px(par));
        focusBtn(btnHome, icHome, "top".equals(zone) && tSel == 0, anim);
        focusBtn(btnSettings, icSettings, "top".equals(zone) && tSel == 1, anim);

        float bx;
        if ("apps".equals(zone)) bx = 96 + 28 + sel * 312 + 140 - 360;
        else bx = 96 + Math.min(cSel, 3) * 436 + 200 - 360;
        float ba = "top".equals(zone) || "rap".equals(zone) ? 0f : 1f;
        mv(beam, anim, 550).translationX(px(bx)).alpha(ba);
        if (!anim) { beam.setTranslationX(px(bx)); beam.setAlpha(ba); }

        float cx = -Math.max(0, cSel - 3) * 436;
        mv(cardsRow, anim, 500).translationX(px(cx));
        if (!anim) cardsRow.setTranslationX(px(cx));
        float rx = 0;
        mv(rapRow, anim, 500).translationX(px(rx));
        if (!anim) rapRow.setTranslationX(px(rx));
        setMode("top".equals(zone) ? "apps" : zone, anim);
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
        for (int k = 0; k < fxw.length; k++) {
            final FrameLayout w = fxw[k];
            if (w == null) continue;
            final int kk = k;
            if (k == i) {
                w.bringToFront();
                w.setVisibility(View.VISIBLE);
                w.animate().alpha(1f).setDuration(anim ? 900 : 0).setStartDelay(0).withEndAction(null).start();
                for (Animator a : fxAnims.get(k)) { if (!a.isStarted()) a.start(); else a.resume(); }
            } else if (w.getVisibility() == View.VISIBLE) {
                w.animate().alpha(0f).setDuration(anim ? 500 : 0).setStartDelay(0).withEndAction(new Runnable() {
                    public void run() {
                        if (shownBg == kk) return;
                        w.setVisibility(View.INVISIBLE);
                        for (Animator a : fxAnims.get(kk)) a.pause();
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
        List<View> lts = heroLetters.get(i);
        if (anim) {
            n.setTranslationX(px(30) * dir);
            n.animate().alpha(1f).translationX(0).setDuration(500).setStartDelay(0).setInterpolator(EASE).withEndAction(null).start();
            for (int j = 0; j < lts.size(); j++) {
                View v = lts.get(j);
                v.animate().cancel();
                v.setAlpha(0f);
                v.setTranslationY(px(90));
                v.setScaleY(1.25f);
                v.animate().alpha(1f).translationY(0).scaleY(1f).setDuration(620).setStartDelay(80 + j * 42)
                        .setInterpolator(EASE).start();
            }
        } else {
            n.setAlpha(1f);
            n.setTranslationX(0);
            for (View v : lts) { v.animate().cancel(); v.setAlpha(1f); v.setTranslationY(0); v.setScaleY(1f); }
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
        float ta = on ? (missing ? 0.6f : 1f) : (missing ? 0.3f : 0.82f);
        long d = anim ? 420 : 0;
        s.tile.animate().scaleX(sc).scaleY(sc).translationY(ty).alpha(ta).setDuration(d).setStartDelay(0).setInterpolator(EASE).start();
        s.fx.animate().scaleX(sc).scaleY(sc).translationY(ty).alpha(on ? 1f : 0f).setDuration(d).setStartDelay(0).setInterpolator(EASE).start();
        s.sheen.animate().scaleX(sc).scaleY(sc).translationY(ty).setDuration(d).setStartDelay(0).setInterpolator(EASE).start();
        final Slot ss = s;
        final int si = i;
        s.glow.animate().scaleX(sc).scaleY(sc).translationY(ty).alpha(on ? 1f : 0f).setDuration(anim ? 480 : 0).setStartDelay(0).setInterpolator(EASE)
                .withEndAction(on && anim ? new Runnable() {
                    public void run() {
                        if (!slotOn[si]) return;
                        ss.glow.animate().scaleX(1.32f).scaleY(1.32f).setDuration(240).setInterpolator(SOFT).withEndAction(new Runnable() {
                            public void run() {
                                if (!slotOn[si]) return;
                                ss.glow.animate().scaleX(1.12f).scaleY(1.12f).setDuration(800).setInterpolator(EASE).withEndAction(null).start();
                            }
                        }).start();
                    }
                } : null).start();
        if (on && anim) {
            s.band.animate().cancel();
            s.band.setTranslationX(0);
            s.band.setAlpha(1f);
            s.band.animate().translationX(px(600)).setDuration(950).setStartDelay(160).setInterpolator(SOFT).start();
        }
        s.bar.animate().scaleX(on ? 1f : 0f).alpha(on ? 1f : 0f).setDuration(anim ? 400 : 0).setStartDelay(0).setInterpolator(EASE).start();
        s.reflect.animate().alpha(on ? 0.55f : 0.18f).setDuration(anim ? 350 : 0).setStartDelay(0).start();
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

    String shownMode = "";

    void setMode(String z, boolean anim) {
        if (z.equals(shownMode) && anim) return;
        final boolean fromRap = "rap".equals(shownMode);
        shownMode = z;
        boolean isCont = "cont".equals(z), isRap = "rap".equals(z), low = isCont || isRap;
        long d = anim ? 700 : 0;
        lift.animate().translationY(low ? -px(400) : 0).alpha(isRap ? 0f : 1f).setDuration(d).setStartDelay(0).setInterpolator(EASE).start();
        heroWrap.animate().alpha(low ? 0f : 1f).setDuration(anim ? 450 : 0).setStartDelay(0).start();
        cont.setVisibility(View.VISIBLE);
        cont.animate().alpha(low ? 1f : 0f).translationY(isRap ? -px(330) : (isCont ? 0 : px(260))).setDuration(d)
                .setStartDelay(isCont && anim && !fromRap ? 120 : 0).setInterpolator(EASE)
                .withEndAction(low ? null : new Runnable() {
                    public void run() { if (!"cont".equals(shownMode) && !"rap".equals(shownMode)) cont.setVisibility(View.INVISIBLE); }
                }).start();
        if (isRap) {
            ui.removeCallbacks(rapCycle);
            ui.postDelayed(rapCycle, 7000);
            rapStage.setVisibility(View.VISIBLE);
            rapStage.animate().alpha(1f).setDuration(anim ? 600 : 0).setStartDelay(0).withEndAction(null).start();
            if (!rapPulse.isStarted()) rapPulse.start(); else rapPulse.resume();
            if (!rapDrift.isStarted()) rapDrift.start(); else rapDrift.resume();
        } else {
            rapStage.animate().alpha(0f).setDuration(anim ? 450 : 0).setStartDelay(0).withEndAction(new Runnable() {
                public void run() {
                    if ("rap".equals(shownMode)) return;
                    rapStage.setVisibility(View.INVISIBLE);
                    rapPulse.pause();
                    rapDrift.pause();
                }
            }).start();
        }
        chev.animate().rotation(isRap ? 180 : 0).alpha(low ? 0.55f : 0.4f).setDuration(anim ? 500 : 0).setInterpolator(EASE).start();
    }

    int shownRapper = -1;
    float rapBase = 1f;
    ValueAnimator rapZoom;

    int rapPicIdx = 0;

    int[] rapCounts;

    int rapPicCount(int r) {
        if (rapCounts == null) {
            rapCounts = new int[RAP.length];
            for (int i = 0; i < RAP.length; i++) rapCounts[i] = countRapPics(i);
        }
        return rapCounts[r];
    }

    int countRapPics(int r) {
        int n = 0;
        try {
            String[] all = getAssets().list("rap");
            if (all != null) for (String f : all) if (f.startsWith(r + "_")) n++;
        } catch (Exception ignored) { }
        return n;
    }

    /** Bilderreihe: erst alle Ken-Carson-Fotos, dann Bleood – alle 7 Sekunden weiter. */
    final List<int[]> rapPlaylist = new ArrayList<>();
    int rapPlayPos = -1;

    List<int[]> playlist() {
        if (rapPlaylist.isEmpty())
            for (int r = 0; r < RAP.length; r++)
                for (int k = 0; k < rapPicCount(r); k++) rapPlaylist.add(new int[]{r, k});
        return rapPlaylist;
    }

    void nextRapPic(boolean anim, long fadeMs) {
        List<int[]> pl = playlist();
        if (pl.isEmpty()) { showRapPic(null, anim, fadeMs); return; }
        rapPlayPos = (rapPlayPos + 1) % pl.size();
        int[] e = pl.get(rapPlayPos);
        showRapPic(loadRapPic(e[0], e[1]), anim, fadeMs);
    }

    final Runnable rapCycle = new Runnable() {
        public void run() {
            if (!"rap".equals(shownMode) || shownRapper < 0) return;
            int n = rapPicCount(shownRapper);
            if (n > 1) {
                rapPicIdx = (rapPicIdx + 1) % n;
                showRapPic(loadRapPic(shownRapper, rapPicIdx), true, 900);
            }
            ui.postDelayed(this, 7000);
        }
    };

    void showRapperPics(int r, boolean anim) {
        rapPicIdx = 0;
        showRapPic(rapPicCount(r) > 0 ? loadRapPic(r, 0) : null, anim, 550);
        ui.removeCallbacks(rapCycle);
        ui.postDelayed(rapCycle, 7000);
    }

    Bitmap loadRapPic(int r, int k) {
        try {
            InputStream in = getAssets().open("rap/" + r + "_" + k + ".jpg");
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            return b;
        } catch (Exception e) { return null; }
    }

    void setRapper(final int r, boolean anim) {
        if (r == shownRapper) return;
        shownRapper = r;
        rapGlow.setImageBitmap(rapGlows[r]);
        showRapperPics(r, anim);
    }

    void showRapPic(Bitmap pic, boolean anim, long fadeMs) {
        final ImageView next = rapUseA ? rapPicB : rapPicA;
        final ImageView old = rapUseA ? rapPicA : rapPicB;
        rapUseA = !rapUseA;
        next.setImageBitmap(pic);
        next.bringToFront();
        rapGlow.bringToFront();
        next.animate().cancel();
        old.animate().cancel();
        if (rapZoom != null) rapZoom.cancel();
        rapZoom = ValueAnimator.ofFloat(1.1f, 1f);
        rapZoom.setDuration(anim ? 7000 : 0);
        rapZoom.setInterpolator(EASE);
        rapZoom.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) { rapBase = (Float) va.getAnimatedValue(); }
        });
        rapZoom.start();
        float target = pic == null ? 0f : 1f;
        if (anim) {
            next.setAlpha(0f);
            next.animate().alpha(target).setDuration(fadeMs).setStartDelay(0).setInterpolator(SOFT).withEndAction(null).start();
            old.animate().alpha(0f).setDuration(fadeMs).setStartDelay(150).withEndAction(null).start();
        } else {
            next.setAlpha(target);
            old.setAlpha(0f);
        }
    }

    void focusRap(RapCard c, boolean on, boolean anim) {
        long d = anim ? 420 : 0;
        float sc = on ? 1.1f : 1f;
        c.view.animate().scaleX(sc).scaleY(sc).translationY(on ? -px(12) : 0).alpha(on ? 1f : 0.62f)
                .setDuration(d).setStartDelay(0).setInterpolator(EASE).start();
        c.ring.animate().alpha(on ? 1f : 0f).setDuration(anim ? 250 : 0).setStartDelay(0).start();
        c.play.animate().alpha(on ? 1f : 0f).scaleX(on ? 1f : 0.6f).scaleY(on ? 1f : 0.6f)
                .setDuration(anim ? 350 : 0).setStartDelay(0).setInterpolator(EASE).start();
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
    /** Fire TV steht oft auf falscher Zeitzone (z. B. UTC): für die Uhr immer Österreich. */
    static void fixTimeZone() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Vienna"));
    }

    final Runnable clockRun = new Runnable() { public void run() { clockTick(); } };

    /** Nach dem Einschalten stimmt die Uhr des Sticks erst, wenn er sie aus dem Internet geholt hat. */
    static volatile boolean timeSynced = false;
    final BroadcastReceiver timeSet = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) { timeSynced = true; clockTick(); }
    };

    boolean timeOk() {
        if (timeSynced) return true;
        // Länger als 2 Minuten an: dann hat der Stick die Zeit sicher schon geholt
        if (android.os.SystemClock.elapsedRealtime() > 120000) { timeSynced = true; return true; }
        return false;
    }

    void clockTick() {
        if (!timeOk()) {
            String gr = "Hallo, " + AppDef.NAME;
            greet.setText(gr);
            date.setText("");
            time.setText("");
            if (svTime != null) { svTime.setText(""); svDate.setText(""); }
            pendingWords = gr;
            if (!introRunning()) setWords(gr);
            iSub.setText("");
            ui.removeCallbacks(clockRun);
            ui.postDelayed(clockRun, 1000);
            return;
        }
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "Gute Nacht" : h < 11 ? "Guten Morgen" : h < 17 ? "Hallo" : h < 22 ? "Guten Abend" : "Gute Nacht";
        String gr = g + ", " + AppDef.NAME;
        String d = TAGE[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + ". " + MON[c.get(Calendar.MONTH)];
        String t = String.format(Locale.ROOT, "%02d:%02d", h, c.get(Calendar.MINUTE));
        greet.setText(gr);
        date.setText(d);
        time.setText(t);
        if (svTime != null) { svTime.setText(t); svDate.setText(d); }
        pendingWords = gr;
        if (!introRunning()) setWords(gr);
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

    String pendingWords = null;

    void playIntro(boolean withSound) {
        if (introAnim != null) introAnim.cancel();
        clockTick();
        if (pendingWords != null) setWords(pendingWords);
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
        if (!on) { cont.setVisibility(View.INVISIBLE); rapStage.setVisibility(View.INVISIBLE); }
        if (on) { kenBurns.resume(); resumeFx(); } else { kenBurns.pause(); pauseFx(); }
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
        resetIdle();
        if (saverOn) { hideSaver(true); return; }
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
            else if ("down".equals(k)) zone = "rap";
            else if ("ok".equals(k) && cSel < cards.size()) {
                Card c = cards.get(cSel);
                if (c.hint) showToast("Öffne eine App, dann erscheint sie hier");
                else openAppFx(c.app, c.view);
            }
        } else if ("rap".equals(zone)) {
            if ("left".equals(k)) rSel = Math.max(0, rSel - 1);
            else if ("right".equals(k)) rSel = Math.min(RAP.length - 1, rSel + 1);
            else if ("up".equals(k)) zone = "cont";
            else if ("back".equals(k)) zone = "apps";
            else if ("ok".equals(k)) openRapper(RAP[rSel], rapCards[rSel].view);
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

    // ---------- Bildschirmschoner ----------
    final Runnable idleRun = new Runnable() {
        public void run() {
            if (introRunning() || launching || saverOn) return;
            showSaver();
        }
    };
    final Runnable saverCycle = new Runnable() {
        public void run() {
            if (!saverOn) return;
            nextSaverPic();
            ui.postDelayed(this, 8000);
        }
    };

    void resetIdle() {
        ui.removeCallbacks(idleRun);
        ui.postDelayed(idleRun, SAVER_AFTER_MS);
    }

    void showSaver() {
        saverOn = true;
        clockTick();
        saver.setVisibility(View.VISIBLE);
        saver.setAlpha(0f);
        saver.animate().alpha(1f).setDuration(1200).setStartDelay(0).withEndAction(new Runnable() {
            public void run() { if (saverOn) { kenBurns.pause(); pauseFx(); } }
        }).start();
        svPos = -1;
        nextSaverPic();
        ui.removeCallbacks(saverCycle);
        ui.postDelayed(saverCycle, 8000);
    }

    String[] saverFiles;

    void nextSaverPic() {
        if (saverFiles == null) {
            try { saverFiles = getAssets().list("saver"); } catch (Exception e) { saverFiles = new String[0]; }
            if (saverFiles == null) saverFiles = new String[0];
            java.util.Arrays.sort(saverFiles);
        }
        if (saverFiles.length == 0) return;
        svPos = (svPos + 1) % saverFiles.length;
        Bitmap pic = null;
        try {
            InputStream in = getAssets().open("saver/" + saverFiles[svPos]);
            pic = BitmapFactory.decodeStream(in);
            in.close();
        } catch (Exception ignored) { }
        final ImageView next = svUseA ? svB : svA, old = svUseA ? svA : svB;
        svUseA = !svUseA;
        next.setImageBitmap(pic);
        next.animate().cancel();
        old.animate().cancel();
        next.setAlpha(0f);
        next.animate().alpha(1f).setDuration(1400).setStartDelay(0).withEndAction(null).start();
        ObjectAnimator z = ObjectAnimator.ofPropertyValuesHolder(next,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.14f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.14f, 1f));
        z.setDuration(9000);
        z.setInterpolator(EASE);
        z.start();
        old.animate().alpha(0f).setDuration(1400).setStartDelay(200).start();
    }

    void hideSaver(boolean anim) {
        if (!saverOn) return;
        saverOn = false;
        ui.removeCallbacks(saverCycle);
        kenBurns.resume();
        resumeFx();
        if (anim) {
            saver.animate().alpha(0f).setDuration(450).setStartDelay(0).withEndAction(new Runnable() {
                public void run() { if (!saverOn) saver.setVisibility(View.GONE); }
            }).start();
        } else saver.setVisibility(View.GONE);
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
        if (a.pkg == null && "ytmusic".equals(a.id)) return openYouTubeMusic();
        if (a.pkg == null) { showToast(a.name + " ist nicht installiert"); return false; }
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(a.pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(a.pkg);
        if (i == null || !start(i)) { showToast(a.name + " lässt sich nicht öffnen"); return false; }
        overridePendingTransition(0, 0);
        remember(a.id);
        return true;
    }

    /** Auf dem Fire TV gibt es keine eigene YT-Music-App: dann den Musik-Bereich in YouTube öffnen. */
    boolean openYouTubeMusic() {
        AppDef yt = AppDef.byId("youtube");
        if (yt == null || yt.pkg == null) { showToast("YouTube nicht gefunden"); return false; }
        // Kanal-Links versteht YouTube auf dem Fire TV nicht: einfach YouTube öffnen
        return openApp(yt);
    }

    // ---------- Kachel zieht auf Vollbild auf und blendet dunkel aus ----------
    FrameLayout launchFx;
    ValueAnimator launchAnim;
    boolean launching = false;

    void openAppFx(final AppDef a, View src) {
        if (launching) return;
        if (a.pkg == null && !("ytmusic".equals(a.id) && AppDef.byId("youtube") != null && AppDef.byId("youtube").pkg != null)) {
            showToast(a.name + " ist nicht installiert"); return;
        }
        launchFx(src, a.t1, a.t2, a.name, new Callable<Boolean>() {
            public Boolean call() { return openApp(a); }
        });
    }

    void openRapper(final Rapper r, View src) {
        if (launching) return;
        launchFx(src, r.c1, r.c2, r.name, new Callable<Boolean>() {
            public Boolean call() { return playRapper(r); }
        });
    }

    /** Startet auf YouTube die Songs des Rappers: zufälliger Einstieg in seine eigene Playlist. */
    boolean playRapper(Rapper r) {
        JblBox.connect(this);
        AppDef yt = AppDef.byId("youtube");
        if (yt == null || yt.pkg == null) { showToast("YouTube nicht gefunden"); return false; }
        // Nur Songs des Rappers, gemischt. MediaWatcher springt bei fremden Videos sofort zum nächsten.
        MediaWatcher.start(this, yt.pkg, Math.max(0, java.util.Arrays.asList(Rapper.LIST).indexOf(r)));
        overridePendingTransition(0, 0);
        return true;
    }

    void launchFx(View src, int t1, int t2, String name, final Callable<Boolean> go) {
        launching = true;
        final int W = root.getWidth(), H = root.getHeight();
        float[] c = centerOf(src);
        final float cx = c[0], cy = c[1];
        final float sx0 = src.getWidth() * src.getScaleX() / W, sy0 = src.getHeight() * src.getScaleY() / H;

        final FrameLayout fx = new FrameLayout(this);
        fx.setBackground(new LayerDrawable(new Drawable[]{
                new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t1, t2}),
                new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Gfx.w(0.18f), Gfx.w(0), Gfx.w(0)}),
        }));
        final float[] rad = {px(30) / sx0};
        fx.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad[0]); }
        });
        fx.setClipToOutline(true);
        final TextView lbl = text(name, 32, "Bold", TXT, -0.035f);
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
                boolean ok = false;
                try { ok = go.call(); } catch (Exception e) { Log.e(HomeWatcherService.TAG, "Start", e); }
                if (!ok) clearLaunchFx(true);
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
            InputStream in = getAssets().open("home/" + i + ".jpg");
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
        // Zurück in Domi TV: Rapper-Mix ist vorbei, JBL-Box wieder trennen
        MediaWatcher.stop();
        JblBox.wanted = false;
        JblBox.disconnect(this);
        hideSystemUi();
        HomeWatcherService.allowAmazonHome = false;
        resetIdle();
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
        else { kenBurns.resume(); resumeFx(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(idleRun);
        hideSaver(false);
        pausedAt = SystemClock.elapsedRealtime();
        kenBurns.pause();
        pauseFx();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(screenOn); } catch (Exception ignored) { }
        try { unregisterReceiver(timeSet); } catch (Exception ignored) { }
        if (player != null) player.release();
        super.onDestroy();
    }
}
