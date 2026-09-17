package ch.ehi.ili2ofgdb;

import ch.ehi.openfgdb4j.NativeLoader;

public class InitOfgdbApi implements ch.ehi.ili2db.base.Ili2dbLibraryInit {
    private static int refc = 0;

    @Override
    public synchronized void init() {
        refc++;
        if (refc == 1) {
            NativeLoader.load();
        }
    }

    @Override
    public synchronized void end() {
        if (refc > 0) {
            refc--;
        }
    }
}
