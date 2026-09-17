package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

public class FilterImportOfgdbTest extends ch.ehi.ili2db.FilterImportTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/FilterImportOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
