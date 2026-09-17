package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class BatchOfgdbTest extends ch.ehi.ili2db.BatchTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/BatchOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
