package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class XtfVersionOfgdbTest extends ch.ehi.ili2db.XtfVersionTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/XtfVersionOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
