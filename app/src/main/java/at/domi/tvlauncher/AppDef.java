package at.domi.tvlauncher;

import android.graphics.Color;

/** Deine Apps in dieser Reihenfolge. Hier kannst du Namen, Farben und Pakete ändern. */
final class AppDef {

    static final String NAME = "Domi";

    static final AppDef[] APPS = {
            new AppDef("netflix", "Netflix", "Serien & Filme", "#e01b2a", "#8a111c", "#22040a",
                    new String[]{"com.netflix.ninja"}, new String[]{"netflix"}),
            new AppDef("youtube", "YouTube", "Videos & Creator", "#c4c4d2", "#55555e", "#17171b",
                    new String[]{"com.amazon.firetv.youtube", "com.google.android.youtube.tv"}, new String[]{"youtube"}),
            new AppDef("prime", "Prime Video", "Filme & Serien", "#1aa6dc", "#0f6482", "#051c25",
                    new String[]{"com.amazon.firebat", "com.amazon.avod", "com.amazon.amazonvideo.livingroom"},
                    new String[]{"prime video", "amazon prime video"}),
            new AppDef("disney", "Disney+", "Familie & Blockbuster", "#5a7aff", "#2640a8", "#0a1033",
                    new String[]{"com.disney.disneyplus"}, new String[]{"disney+", "disney plus"}),
            new AppDef("ytmusic", "YT Music", "Musik & Playlists", "#ff3a78", "#8a1a48", "#22051a",
                    new String[]{"com.amazon.firetv.youtube.music", "com.google.android.youtube.tvmusic"},
                    new String[]{"youtube music", "yt music"}),
    };

    final String id, name, kind;
    final int glow, t1, t2;
    final String[] pakete, namen;
    String pkg; // gefundenes installiertes Paket

    AppDef(String id, String name, String kind, String glow, String t1, String t2, String[] pakete, String[] namen) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.glow = Color.parseColor(glow);
        this.t1 = Color.parseColor(t1);
        this.t2 = Color.parseColor(t2);
        this.pakete = pakete;
        this.namen = namen;
    }

    static AppDef byId(String id) {
        for (AppDef a : APPS) if (a.id.equals(id)) return a;
        return null;
    }
}
