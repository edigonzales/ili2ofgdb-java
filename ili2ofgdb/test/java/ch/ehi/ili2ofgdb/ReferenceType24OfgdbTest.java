package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

public class ReferenceType24OfgdbTest extends ch.ehi.ili2db.ReferenceType24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/ReferenceType24OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
