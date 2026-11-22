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
            // ---- Rapper, die dir gefallen könnten (zweite Reihe) ----
            new Rapper("Destroy Lonely", "UCx95IwHfzVDujNhphVJAMMA", 0xffb05cff, 0xff14051f,
                    "UfNuombVZb4", "9vdgd4F9CuI", "6M1rd4n9e44", "UmJphrg_tfg", "-H4w1Z65ZzI", "QTmRmPDS9tw"),
            new Rapper("Playboi Carti", "UCtFVBgpF47QYf1FbPcp96cA", 0xffff3b3b, 0xff1a0303,
                    "KnumAWWWgUE", "nMDOSLEVdBw", "u5ms1nds9eI", "F6iYcXynA4s"),
            new Rapper("che", "UC000000000000000000000", 0xff3bd6ff, 0xff021820,
                    "dm_CO-OUChs", "1pNvG6T4JLw", "OZ9b_psvEC8", "TYhKVpozk5w", "PeVmY14vaLo", "9QVTedHyTX0", "4tjhN4iSPtM", "8SQ2dFmEAj8"),
            new Rapper("Yeat", "UC000000000000000000000", 0xffffc23b, 0xff1d1402,
                    "_f0ftiBSvRs", "qAAifLfNnKQ", "mbbq2gnOcQo", "jMB38Jb3b0Q", "oDvMcJZqYrY", "bHDWrnlIqfQ", "URCH1W1yiug"),
            new Rapper("xaviersobased", "UC000000000000000000000", 0xff7dff9a, 0xff031a08,
                    "i1XqpO6TYkk", "KxF2Ok4zRIw", "Dr3mGAT5JLU", "ZjOPBoFe358"),
    };

    /** Die ersten OWN Einträge sind "Deine Rapper", der Rest "Rapper, die dir gefallen könnten". */
    static final int OWN = 5;

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
