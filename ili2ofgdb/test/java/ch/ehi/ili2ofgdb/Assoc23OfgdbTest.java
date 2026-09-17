package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class Assoc23OfgdbTest extends ch.ehi.ili2db.Assoc23Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/Assoc23OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
