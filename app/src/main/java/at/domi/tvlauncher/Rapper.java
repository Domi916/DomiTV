package at.domi.tvlauncher;

/** Deine Lieblingsrapper – Name, Farbe. Ein Klick öffnet ihre Musik in YT Music. */
final class Rapper {
    static final Rapper[] LIST = {
            new Rapper("Ken Carson", 0xffff2a2a, 0xff2a0000),
            new Rapper("Bleood", 0xffd0102a, 0xff16000a),
            new Rapper("Protect", 0xff2f7bff, 0xff020a26),
            new Rapper("OsamaSon", 0xff8cff3a, 0xff061402),
            new Rapper("Nettspend", 0xffff4fd8, 0xff1c0420),
    };

    final String name;
    final int c1, c2;

    Rapper(String name, int c1, int c2) {
        this.name = name;
        this.c1 = c1;
        this.c2 = c2;
    }
}
