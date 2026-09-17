package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

public class XtfHeaderOfgdbTest extends ch.ehi.ili2db.XtfHeaderTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/XtfHeaderOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }
}
