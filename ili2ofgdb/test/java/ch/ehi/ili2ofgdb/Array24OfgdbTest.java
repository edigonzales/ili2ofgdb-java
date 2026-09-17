package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class Array24OfgdbTest extends ch.ehi.ili2db.Array24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/Array24OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
