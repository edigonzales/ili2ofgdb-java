package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class Dataset10OfgdbTest extends ch.ehi.ili2db.Dataset10Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/Dataset10OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
