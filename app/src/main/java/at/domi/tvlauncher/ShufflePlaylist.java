package at.domi.tvlauncher;

import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baut aus einer Liste von Video-IDs eine temporäre YouTube-Playlist (so wie "watch_videos"
 * im Browser). YouTube antwortet mit einer Weiterleitung, in der die Playlist-ID steht.
 * Diese ID kann die YouTube-App auf dem Fire TV ganz normal abspielen.
 */
final class ShufflePlaylist {
    static final Pattern LIST = Pattern.compile("list=(TL[A-Za-z0-9_-]+)");

    static String create(final List<String> ids) {
        final String[] out = {null};
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    StringBuilder q = new StringBuilder();
                    for (String id : ids) { if (q.length() > 0) q.append(','); q.append(id); }
                    c = (HttpURLConnection) new URL("https://www.youtube.com/watch_videos?video_ids=" + q).openConnection();
                    c.setInstanceFollowRedirects(false);
                    c.setConnectTimeout(2500);
                    c.setReadTimeout(2500);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
                    c.setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+");
                    c.getResponseCode();
                    String loc = c.getHeaderField("Location");
                    if (loc != null) {
                        Matcher m = LIST.matcher(URLDecoder.decode(loc, "UTF-8"));
                        if (m.find()) out[0] = m.group(1);
                    }
                    Log.i("DomiTV", "Shuffle-Playlist: " + out[0] + " (" + loc + ")");
                } catch (Throwable e) {
                    Log.e("DomiTV", "Shuffle-Playlist fehlgeschlagen", e);
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        });
        t.start();
        try { t.join(3500); } catch (InterruptedException ignored) { }
        return out[0];
    }
}
