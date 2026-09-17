package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class EnumAll23OfgdbTest extends ch.ehi.ili2db.EnumAll23Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/EnumAll23OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
