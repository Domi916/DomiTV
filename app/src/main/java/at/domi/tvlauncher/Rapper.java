package at.domi.tvlauncher;

/** Deine Lieblingsrapper – Name, Farbe. Ein Klick startet einen YouTube-Mix. */
final class Rapper {
    static final Rapper[] LIST = {
            new Rapper("Ken Carson", 0xffff2a2a, 0xff2a0000,
                    "80M6sAU9DY4", "vkhsaxlCSmc", "gpbQ4A4tQuU", "YKkMR2l05Rs", "Gbqa9n1XOes", "Ve5jWpIu6Ic", "jao-W5tJkYo", "YxBCe5aaCe0"),
            new Rapper("Bleood", 0xffd0102a, 0xff16000a,
                    "g45uHIKCsYw", "-_7AAffsMRM", "lxl0x4Rj4M4"),
            new Rapper("Protect", 0xff2f7bff, 0xff020a26,
                    "j_7vJBENfJw", "DuY3rGD9LLE", "diNEhHKRRKw", "bShC2XMdYXM", "5ncXdk7ewpk", "6hkt9SPgKpU"),
            new Rapper("OsamaSon", 0xff8cff3a, 0xff061402,
                    "8Zuh44rji24", "aE-9hFktH40", "4DIQDnu3mr4", "Fy7WIdkxwDw", "lTQ8k9uysXo", "UQ13LFqaTF8"),
            new Rapper("Nettspend", 0xffff4fd8, 0xff1c0420,
                    "BRfZe0mLcpw", "9SrWNGvrtVc", "fekiI4Vkt9Y", "wC1ho-3CZcg", "x8gEkJAVd-I", "p1Hq68tFR4g"),
    };

    final String name;
    final int c1, c2;
    /** YouTube-Videos des Rappers: eins wird zufällig gewählt und startet einen Mix. */
    final String[] videos;

    Rapper(String name, int c1, int c2, String... videos) {
        this.name = name;
        this.c1 = c1;
        this.c2 = c2;
        this.videos = videos;
    }
}
