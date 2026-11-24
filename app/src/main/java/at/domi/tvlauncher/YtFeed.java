package at.domi.tvlauncher;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "Neu auf YouTube": die neuesten Videos deiner Rapper (YouTube-RSS der Kanäle, kein Schlüssel nötig). */
final class YtFeed {
    static final String[][] CHANNELS = {
            {"Ken Carson", "UCtjO8cix9XxwOcAv6nww-TQ"},
            {"Bleood", "UCnOs8IGAnAS87jd4PGjTgog"},
            {"Protect", "UCCgpOoFCHl46dgijTtOQAVA"},
            {"OsamaSon", "UCyLjTgYrVIFO2_VSAEO43tA"},
            {"Nettspend", "UC6CvS47L6uTuPSPhinE5jKA"},
            {"slattuhs", "UCTgkoYwnH9O4MeRIkHKmu7g"},
    };

    static final class Video {
        String id, title, artist;
        long at;
        int rapper;
    }

    static volatile List<Video> last = new ArrayList<>();

    /** Gemerkte Liste (vom letzten Mal). */
    static List<Video> cached(Context c) {
        if (!last.isEmpty()) return last;
        try {
            File f = new File(c.getFilesDir(), "ytfeed.txt");
            if (!f.exists()) return last;
            List<Video> out = new ArrayList<>();
            for (String l : Drops.read(new java.io.FileInputStream(f)).split("\n")) {
                String[] p = l.split("\t");
                if (p.length < 4) continue;
                Video v = new Video();
                v.id = p[0]; v.at = Long.parseLong(p[1]); v.artist = p[2]; v.title = p[3];
                v.rapper = Library.rapperIndexByName(v.artist);
                out.add(v);
            }
            last = out;
        } catch (Exception ignored) { }
        return last;
    }

    /** Im Hintergrund: alle Kanäle neu holen (höchstens alle 30 min). true = neue Videos. */
    static boolean refresh(Context c) {
        File f = new File(c.getFilesDir(), "ytfeed.txt");
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 30 * 60000L && !cached(c).isEmpty()) return false;
        List<Video> out = new ArrayList<>();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        Pattern entry = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
        Pattern vid = Pattern.compile("<yt:videoId>([^<]+)</yt:videoId>");
        Pattern title = Pattern.compile("<title>([^<]*)</title>");
        Pattern pub = Pattern.compile("<published>([^<]+)</published>");
        long cut = System.currentTimeMillis() - 60L * 86400000L;
        for (String[] ch : CHANNELS) {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL("https://www.youtube.com/feeds/videos.xml?channel_id=" + ch[1]).openConnection();
                h.setConnectTimeout(6000);
                h.setReadTimeout(8000);
                if (h.getResponseCode() != 200) continue;
                Matcher m = entry.matcher(Drops.read(h.getInputStream()));
                while (m.find()) {
                    String e = m.group(1);
                    Matcher a = vid.matcher(e), b = title.matcher(e), d = pub.matcher(e);
                    if (!a.find() || !b.find() || !d.find()) continue;
                    Video v = new Video();
                    v.id = a.group(1);
                    v.title = unescape(b.group(1));
                    v.artist = ch[0];
                    v.rapper = Library.rapperIndexByName(ch[0]);
                    try { v.at = df.parse(d.group(1).substring(0, 19)).getTime(); } catch (Exception x) { continue; }
                    if (v.at >= cut) out.add(v);
                }
            } catch (Exception ignored) { }
        }
        if (out.isEmpty()) return false;
        Collections.sort(out, new Comparator<Video>() {
            public int compare(Video x, Video y) { return Long.compare(y.at, x.at); }
        });
        if (out.size() > 24) out = new ArrayList<>(out.subList(0, 24));
        boolean changed = last.isEmpty() || !out.get(0).id.equals(last.get(0).id);
        last = out;
        try {
            StringBuilder sb = new StringBuilder();
            for (Video v : out) sb.append(v.id).append('\t').append(v.at).append('\t').append(v.artist).append('\t')
                    .append(v.title.replace('\t', ' ').replace('\n', ' ')).append('\n');
            FileOutputStream o = new FileOutputStream(f);
            o.write(sb.toString().getBytes("UTF-8"));
            o.close();
        } catch (Exception ignored) { }
        return changed;
    }

    static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    static String ago(long at) {
        long h = (System.currentTimeMillis() - at) / 3600000L;
        if (h < 1) return "gerade eben";
        if (h < 24) return "vor " + h + " Std";
        long d = h / 24;
        return d == 1 ? "gestern" : "vor " + d + " Tagen";
    }
}
