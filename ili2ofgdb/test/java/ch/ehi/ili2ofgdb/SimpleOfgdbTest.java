package ch.ehi.ili2ofgdb;

import org.junit.Ignore;
import org.junit.Test;

import ch.ehi.ili2db.AbstractTestSetup;

/**
 * FGDB flavour of the simple contract. The offline DDL script test is skipped explicitly: the
 * JDBC driver executes DDL directly, there is no script collection.
 */
public class SimpleOfgdbTest extends ch.ehi.ili2db.SimpleTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/SimpleOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    @Test
    @Ignore("the ili2ofgdb driver executes DDL directly; there is no offline DDL script collection")
    public void createScriptFromIliCoord() throws Exception {
    }
}
