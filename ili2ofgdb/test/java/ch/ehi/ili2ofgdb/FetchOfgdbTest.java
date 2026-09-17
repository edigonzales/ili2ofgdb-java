package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class FetchOfgdbTest extends ch.ehi.ili2db.FetchTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/FetchOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
