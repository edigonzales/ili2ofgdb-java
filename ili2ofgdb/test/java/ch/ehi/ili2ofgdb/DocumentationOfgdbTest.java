package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

public class DocumentationOfgdbTest extends ch.ehi.ili2db.DocumentationTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/DocumentationOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
