package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

public class ExpandStruct24OfgdbTest extends ch.ehi.ili2db.ExpandStruct24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/ExpandStruct24OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
