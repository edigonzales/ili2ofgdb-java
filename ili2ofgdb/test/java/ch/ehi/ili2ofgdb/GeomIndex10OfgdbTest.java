package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class GeomIndex10OfgdbTest extends ch.ehi.ili2db.GeomIndex10Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/GeomIndex10OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
