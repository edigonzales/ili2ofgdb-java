package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

public class ExtRefOfgdbTest extends ch.ehi.ili2db.ExtRefTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/ExtRefOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
