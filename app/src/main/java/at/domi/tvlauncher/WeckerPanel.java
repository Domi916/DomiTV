package at.domi.tvlauncher;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;

/** Einstellen des Musik-Weckers: ◀ ▶ Stunde/Minute, ▲ ▼ ändern, OK speichern, ↩ zurück. Ganz unten: an/aus. */
final class WeckerPanel {
    final MainActivity a;
    final FrameLayout root;
    int h, m, sel = 0;     // 0 Stunde, 1 Minute, 2 An/Aus
    boolean on;
    TextView hv, mv, sw, info;

    WeckerPanel(MainActivity a) {
        this.a = a;
        h = Wecker.hour(a);
        m = Wecker.minute(a);
        on = Wecker.on(a);
        root = new FrameLayout(a);
        root.setBackgroundColor(0xf0050507);
        root.setClickable(true);
    }

    void open(FrameLayout parent) {
        TextView head = a.text("MUSIK-WECKER", 22, "Bold", 0xff4fc3ff, 0.3f);
        root.addView(head, a.atD(0, 220, 1920, -2));
        head.setGravity(Gravity.CENTER);
        TextView sub = a.text("Zur Uhrzeit startet dein Tagesmix, leise beginnend und langsam lauter", 24, "Medium", 0xaaffffff, 0);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, a.atD(0, 270, 1920, -2));
        hv = big();
        mv = big();
        TextView colon = a.text(":", 200, "Black", Color.WHITE, 0);
        colon.setGravity(Gravity.CENTER);
        root.addView(hv, a.atD(560, 360, 360, 260));
        root.addView(colon, a.atD(910, 360, 100, 260));
        root.addView(mv, a.atD(1000, 360, 360, 260));
        sw = a.text("", 34, "Bold", Color.WHITE, 0);
        sw.setGravity(Gravity.CENTER);
        root.addView(sw, a.atD(760, 690, 400, 100));
        info = a.text("◀ ▶ wählen    ▲ ▼ ändern    OK speichern    ↩ zurück", 22, "Medium", 0x88ffffff, 0);
        info.setGravity(Gravity.CENTER);
        root.addView(info, a.atD(0, 860, 1920, -2));
        parent.addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(300).start();
        paint();
    }

    TextView big() {
        TextView t = a.text("", 200, "Black", Color.WHITE, -0.02f);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    GradientDrawable bg(int c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(a.px(36));
        return d;
    }

    void paint() {
        hv.setText(String.format(Locale.ROOT, "%02d", h));
        mv.setText(String.format(Locale.ROOT, "%02d", m));
        hv.setBackground(sel == 0 ? bg(0x33ffffff) : null);
        mv.setBackground(sel == 1 ? bg(0x33ffffff) : null);
        sw.setText(on ? "● AN" : "○ AUS");
        sw.setTextColor(on ? 0xff111111 : Color.WHITE);
        sw.setBackground(bg(on ? (sel == 2 ? 0xff7fd8ff : 0xff4fc3ff) : (sel == 2 ? 0x55ffffff : 0x22ffffff)));
    }

    void key(String k) {
        if ("back".equals(k)) { close(); return; }
        if ("left".equals(k)) sel = Math.max(0, sel - 1);
        else if ("right".equals(k)) sel = Math.min(2, sel + 1);
        else if ("up".equals(k) || "down".equals(k)) {
            int d = "up".equals(k) ? 1 : -1;
            if (sel == 0) h = (h + d + 24) % 24;
            else if (sel == 1) m = (m + d * 5 + 60) % 60;
            else on = !on;
            if (sel < 2) on = true;          // Zeit ändern = Wecker an
        } else if ("ok".equals(k)) {
            Wecker.set(a, on, h, m);
            a.showToast(on ? "Wecker an: " + Wecker.text(a) + " – dein Tagesmix weckt dich" : "Wecker aus");
            close();
            a.buildCards();
            a.applyAll(false);
            return;
        }
        paint();
    }

    void close() {
        root.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
            public void run() { if (root.getParent() != null) ((FrameLayout) root.getParent()).removeView(root); }
        }).start();
        a.wecker = null;
    }
}
