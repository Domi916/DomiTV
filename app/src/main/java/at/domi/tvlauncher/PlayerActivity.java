package at.domi.tvlauncher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Eigener Player in Domi TV: spielt NUR die Songs eines Rappers, zufällig gemischt.
 * Nutzt den offiziellen YouTube-Player (IFrame) in einer WebView.
 * Fernbedienung: ▶/◀ nächster/vorheriger Song, OK = Pause, ▼ = Audio-Modus (nur Foto), Zurück = Ende.
 */
public class PlayerActivity extends Activity {
    static final String TAG = "DomiTV";
    WebView web;
    ImageView photo;
    View shade;
    LinearLayout info;
    TextView title, hint, artist;
    Rapper rapper;
    int rIndex;
    boolean audioMode = false;
    static final String PAGE = "https://domi916.github.io/domitv/player.html";
    String pageHtml;
    final Handler ui = new Handler(Looper.getMainLooper());
    final Runnable hideInfo = new Runnable() {
        @Override
        public void run() { if (!audioMode) info.animate().alpha(0f).setDuration(600).start(); }
    };

    float px(float dp) { return getResources().getDisplayMetrics().widthPixels / 1920f * dp; }

    Typeface font(String w) {
        try { return Typeface.createFromAsset(getAssets(), "fonts/Inter-" + w + ".otf"); }
        catch (Exception e) { return Typeface.DEFAULT_BOLD; }
    }

    TextView text(String s, float size, String w, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTypeface(font(w));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        rIndex = Math.max(0, Math.min(Rapper.LIST.length - 1, getIntent().getIntExtra("rapper", 0)));
        rapper = Rapper.LIST[rIndex];

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            InputStream in = getAssets().open("rap/" + rIndex + "_0.jpg");
            Bitmap bm = BitmapFactory.decodeStream(in);
            in.close();
            photo.setImageBitmap(bm);
        } catch (Exception ignored) { }
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            // Die Seite bekommt eine echte https-Adresse, damit YouTube eine gültige Herkunft (Referer) sieht
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest req) {
                if (PAGE.equals(req.getUrl().toString()) && pageHtml != null) {
                    try {
                        return new android.webkit.WebResourceResponse("text/html", "utf-8",
                                new java.io.ByteArrayInputStream(pageHtml.getBytes("UTF-8")));
                    } catch (Exception ignored) { }
                }
                return null;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Domi");
        web.setFocusable(false);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x66000000, 0x00000000}));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, (int) px(420));
        sl.gravity = Gravity.BOTTOM;
        root.addView(shade, sl);

        info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        artist = text(rapper.name.toUpperCase() + "  ·  SHUFFLE", 24, "Bold", rapper.c1);
        artist.setLetterSpacing(0.12f);
        title = text("Lädt …", 56, "Bold", Color.WHITE);
        hint = text("◀ ▶  Song wechseln     OK  Pause     ▼  Audio-Modus     Zurück  Beenden", 22, "Medium", 0xB3FFFFFF);
        info.addView(artist);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = (int) px(8);
        info.addView(title, tl);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = (int) px(18);
        info.addView(hint, hl);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(-1, -2);
        il.gravity = Gravity.BOTTOM;
        il.leftMargin = il.rightMargin = (int) px(96);
        il.bottomMargin = (int) px(80);
        root.addView(info, il);
        setContentView(root);
        hideSystemUi();

        String[] test = getIntent().getStringArrayExtra("test_ids");
        List<String> ids = new ArrayList<>(Arrays.asList(test != null ? test : rapper.videos));
        Collections.shuffle(ids);
        StringBuilder arr = new StringBuilder();
        for (String id : ids) { if (arr.length() > 0) arr.append(','); arr.append('"').append(id).append('"'); }
        String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:fixed;inset:0;width:100%;height:100%}</style></head><body>"
                + "<div id='p'></div><script>"
                + "var ids=[" + arr + "],p,fails=0;"
                + "function shuffle(a){for(var i=a.length-1;i>0;i--){var j=Math.floor(Math.random()*(i+1));var t=a[i];a[i]=a[j];a[j]=t;}return a;}"
                + "function onYouTubeIframeAPIReady(){p=new YT.Player('p',{width:'100%',height:'100%',videoId:ids[0],"
                + "playerVars:{origin:'https://domi916.github.io',autoplay:1,controls:0,rel:0,modestbranding:1,playsinline:1,iv_load_policy:3,fs:0,disablekb:1,playlist:ids.join(',')},"
                + "events:{onReady:function(e){e.target.setVolume(100);e.target.playVideo();},"
                + "onStateChange:function(e){if(e.data==1){fails=0;var d=p.getVideoData();Domi.title(d&&d.title?d.title:'');}"
                + "if(e.data==0){var i=p.getPlaylistIndex();if(i>=ids.length-1){shuffle(ids);p.loadPlaylist(ids,0);}}},"
                + "onError:function(e){Domi.log('Fehler '+e.data);fails++;if(fails>ids.length){Domi.title('Keine Songs abspielbar');return;}"
                + "setTimeout(function(){p.nextVideo();},400);}}});}"
                + "function next(){if(p)p.nextVideo();} function prev(){if(p)p.previousVideo();}"
                + "function toggle(){if(!p)return;if(p.getPlayerState()==1)p.pauseVideo();else p.playVideo();}"
                + "var t=document.createElement('script');t.src='https://www.youtube.com/iframe_api';document.head.appendChild(t);"
                + "</script></body></html>";
        pageHtml = html;
        web.loadUrl(PAGE);
        showInfo();
    }

    class Bridge {
        @JavascriptInterface
        public void title(final String t) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    String clean = t == null ? "" : t.replaceAll("(?i)\\s*[\\(\\[](official|music|video|audio|visualizer|lyric)[^\\)\\]]*[\\)\\]]", "")
                            .replaceAll("(?i)^" + java.util.regex.Pattern.quote(rapper.name) + "\\s*[-–]\\s*", "").trim();
                    title.setText(clean.isEmpty() ? rapper.name : clean);
                    showInfo();
                }
            });
        }

        @JavascriptInterface
        public void log(String m) { Log.i(TAG, "Player: " + m); }
    }

    void js(String f) { web.evaluateJavascript(f + "()", null); }

    void showInfo() {
        info.animate().cancel();
        info.setAlpha(1f);
        shade.setAlpha(1f);
        ui.removeCallbacks(hideInfo);
        ui.postDelayed(hideInfo, 5000);
    }

    void setAudioMode(boolean on) {
        audioMode = on;
        web.animate().alpha(on ? 0f : 1f).setDuration(500).start();
        hint.setText(on ? "◀ ▶  Song wechseln     OK  Pause     ▲  Video     Zurück  Beenden"
                : "◀ ▶  Song wechseln     OK  Pause     ▼  Audio-Modus     Zurück  Beenden");
        showInfo();
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                js("next"); title.setText("…"); showInfo(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                js("prev"); title.setText("…"); showInfo(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                js("toggle"); showInfo(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                setAudioMode(true); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (audioMode) setAudioMode(false); else showInfo();
                return true;
            case KeyEvent.KEYCODE_BACK:
                finish(); return true;
        }
        return super.onKeyDown(k, e);
    }

    void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Musik stoppt, sobald du den Player verlässt
        if (isFinishing() && web != null) web.loadUrl("about:blank");
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) { web.loadUrl("about:blank"); web.destroy(); }
        super.onDestroy();
    }
}
