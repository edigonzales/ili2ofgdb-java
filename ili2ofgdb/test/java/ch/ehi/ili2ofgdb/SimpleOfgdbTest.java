package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

/**
 * FGDB flavour of the simple contract, including the offline DDL script test.
 */
public class SimpleOfgdbTest extends ch.ehi.ili2db.SimpleTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/SimpleOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
