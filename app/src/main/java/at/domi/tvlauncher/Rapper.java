package at.domi.tvlauncher;

/** Deine Lieblingsrapper – Name, Farbe. Ein Klick startet einen YouTube-Mix. */
final class Rapper {
    static final Rapper[] LIST = {
            new Rapper("Ken Carson", "UCaG2V4fBeGjBVuLQu7bCcGg", 0xffff2a2a, 0xff2a0000,
                    "80M6sAU9DY4", "vkhsaxlCSmc", "gpbQ4A4tQuU", "YKkMR2l05Rs", "Ve5jWpIu6Ic", "jao-W5tJkYo", "YxBCe5aaCe0",
                    "TMzs6GvsZXU", "KMoHAuvwN3Y", "7wAx6jeXM7o", "SC6A7WoKugQ", "nYPkghSmZAs", "yZTUSTHSSJU", "8De1G041tQI"),
            new Rapper("Bleood", "UC8CEtaDirOYBKHKWoW62S2A", 0xffd0102a, 0xff16000a,
                    "g45uHIKCsYw", "LwHFzGHQnXk", "FqNA3F1JKIQ", "PixcdDll-XU", "zGXyqfEOwz8", "wiBrmjBRJz0", "czaMOYTakP8"),
            new Rapper("Protect", "UCCgpOoFCHl46dgijTtOQAVA", 0xff2f7bff, 0xff020a26,
                    "j_7vJBENfJw", "DuY3rGD9LLE", "diNEhHKRRKw", "bShC2XMdYXM", "5ncXdk7ewpk", "6hkt9SPgKpU"),
            new Rapper("OsamaSon", "UCh_YNYvD3VX1wepTg1ydUaA", 0xff8cff3a, 0xff061402,
                    "8Zuh44rji24", "aE-9hFktH40", "4DIQDnu3mr4", "Fy7WIdkxwDw", "lTQ8k9uysXo", "5EJ5Ci5UGgk", "6kTjJwPv5V0", "sXYXT70ICnw"),
            new Rapper("Nettspend", "UC6CvS47L6uTuPSPhinE5jKA", 0xffff4fd8, 0xff1c0420,
                    "BRfZe0mLcpw", "9SrWNGvrtVc", "fekiI4Vkt9Y", "wC1ho-3CZcg", "x8gEkJAVd-I", "aUtDhHNTJUE", "PvPGEBaXMRs", "KoH6Fpju-Y4"),
    };

    final String name;
    /** YouTube-Kanal des Künstlers: seine Uploads-Playlist (UU…) enthält nur seine eigenen Songs. */
    final String channel;
    final int c1, c2;
    /** YouTube-Videos des Rappers: eins wird zufällig gewählt und startet einen Mix. */
    final String[] videos;

    Rapper(String name, String channel, int c1, int c2, String... videos) {
        this.name = name;
        this.channel = channel;
        this.c1 = c1;
        this.c2 = c2;
        this.videos = videos;
    }
}
