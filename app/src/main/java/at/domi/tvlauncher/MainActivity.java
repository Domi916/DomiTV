package at.domi.tvlauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private WebView web;
    private boolean pageReady = false;
    private long pausedAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#030305")));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#030305"));
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
        hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        HomeWatcherService.allowAmazonHome = false;
        long away = pausedAt == 0 ? 0 : SystemClock.elapsedRealtime() - pausedAt;
        if (pageReady) js("window.tv&&tv.onResume(" + away + ")");
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausedAt = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    /** Fernbedienung: Tasten an die Oberfläche weiterreichen. */
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
        if (e.getAction() == KeyEvent.ACTION_DOWN && pageReady) js("window.tv&&tv.key('" + k + "')");
        return true; // Zurück-Taste schließt den Startbildschirm nie
    }

    // ------------------------------------------------------------------
    // Schnittstelle für index.html
    // ------------------------------------------------------------------
    private class Bridge {

        /** Sucht für jede App den installierten Paketnamen (erst per Paket, dann per Name). */
        @JavascriptInterface
        public String resolveApps(String json) {
            JSONObject out = new JSONObject();
            try {
                PackageManager pm = getPackageManager();
                Map<String, String> labels = new HashMap<>();
                collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, labels);
                collect(pm, Intent.CATEGORY_LAUNCHER, labels);

                JSONArray apps = new JSONArray(json);
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject a = apps.getJSONObject(i);
                    String found = null;
                    JSONArray pk = a.optJSONArray("pakete");
                    if (pk != null) {
                        for (int j = 0; j < pk.length() && found == null; j++) {
                            if (labels.containsKey(pk.getString(j))) found = pk.getString(j);
                        }
                    }
                    JSONArray nm = a.optJSONArray("namen");
                    if (found == null && nm != null) {
                        for (int j = 0; j < nm.length() && found == null; j++) {
                            String want = nm.getString(j).toLowerCase(Locale.ROOT).trim();
                            for (Map.Entry<String, String> en : labels.entrySet()) {
                                if (en.getValue().equals(want)) { found = en.getKey(); break; }
                            }
                        }
                    }
                    out.put(a.getString("id"), found == null ? JSONObject.NULL : found);
                    Log.i(HomeWatcherService.TAG, a.getString("id") + " -> " + found);
                }
            } catch (Exception e) {
                Log.e(HomeWatcherService.TAG, "resolveApps", e);
            }
            return out.toString();
        }

        private void collect(PackageManager pm, String category, Map<String, String> labels) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            for (ResolveInfo r : list) {
                String pkg = r.activityInfo.packageName;
                if (labels.containsKey(pkg)) continue;
                CharSequence l = r.loadLabel(pm);
                labels.put(pkg, l == null ? "" : l.toString().toLowerCase(Locale.ROOT).trim());
            }
        }

        /** "high" bei Geräten mit viel Arbeitsspeicher, sonst "low" (z. B. Fire TV Stick 3. Gen). */
        @JavascriptInterface
        public String deviceTier() {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return (mi.totalMem >= 1800L * 1024 * 1024 && !am.isLowRamDevice()) ? "high" : "low";
            } catch (Exception e) {
                return "low";
            }
        }

        @JavascriptInterface
        public boolean launch(String pkg) {
            if (TextUtils.isEmpty(pkg)) return false;
            PackageManager pm = getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return start(i);
        }

        @JavascriptInterface
        public boolean openUri(String uri, String pkg) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                if (!TextUtils.isEmpty(pkg)) i.setPackage(pkg);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return start(i);
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void openAmazonHome() {
            HomeWatcherService.allowAmazonHome = true;
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setPackage(HomeWatcherService.AMAZON_LAUNCHER);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!start(i)) {
                Intent j = getPackageManager().getLaunchIntentForPackage(HomeWatcherService.AMAZON_LAUNCHER);
                if (j != null) { j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); start(j); }
            }
        }

        @JavascriptInterface
        public void openSettings() {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!start(i)) {
                Intent j = new Intent();
                j.setComponent(new ComponentName(HomeWatcherService.AMAZON_LAUNCHER, "com.amazon.tv.launcher.ui.SettingsActivity"));
                j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                start(j);
            }
        }

        private boolean start(final Intent i) {
            try {
                startActivity(i);
                return true;
            } catch (Exception e) {
                Log.e(HomeWatcherService.TAG, "Start fehlgeschlagen: " + i, e);
                return false;
            }
        }
    }
}
