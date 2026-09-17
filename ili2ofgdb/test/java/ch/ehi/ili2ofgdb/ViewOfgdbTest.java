package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class ViewOfgdbTest extends ch.ehi.ili2db.ViewTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/ViewOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
