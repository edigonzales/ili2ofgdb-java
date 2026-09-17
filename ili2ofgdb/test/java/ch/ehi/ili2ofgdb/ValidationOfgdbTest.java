package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class ValidationOfgdbTest extends ch.ehi.ili2db.ValidationTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/ValidationOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
