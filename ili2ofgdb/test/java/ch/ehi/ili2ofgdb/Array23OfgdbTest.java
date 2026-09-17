package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class Array23OfgdbTest extends ch.ehi.ili2db.Array23Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/Array23OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
