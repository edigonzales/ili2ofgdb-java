package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

public class MetaInfo23OfgdbTest extends ch.ehi.ili2db.MetaInfo23Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/MetaInfo23OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
