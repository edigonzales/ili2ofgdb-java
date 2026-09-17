package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

public class MultilingualTextOfgdbTest extends ch.ehi.ili2db.MultilingualTextTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/MultilingualTextOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
