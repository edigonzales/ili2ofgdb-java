package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class MultilingualText24OfgdbTest extends ch.ehi.ili2db.MultilingualText24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/MultilingualText24OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
