package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

public class MetaConfigOfgdbTest extends ch.ehi.ili2db.MetaConfigTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/MetaConfigOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
