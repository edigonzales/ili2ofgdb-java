package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

public class Json24OfgdbTest extends ch.ehi.ili2db.Json24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/Json24OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
