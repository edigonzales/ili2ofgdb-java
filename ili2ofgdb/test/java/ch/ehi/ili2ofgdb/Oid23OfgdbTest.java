package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class Oid23OfgdbTest extends ch.ehi.ili2db.Oid23Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/Oid23OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
