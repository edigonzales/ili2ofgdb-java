package ch.ehi.ili2ofgdb;

import org.junit.Ignore;
import org.junit.Test;

import ch.ehi.ili2db.AbstractTestSetup;

/**
 * FGDB flavour of the unique index contract. The file geodatabase has no attribute indexes; the
 * contract test is skipped explicitly.
 */
public class UniqueIndex10OfgdbTest extends ch.ehi.ili2db.UniqueIndex10Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/UniqueIndex10OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    @Test
    @Ignore("the file geodatabase has no unique constraints/attribute indexes")
    public void importIli() throws Exception {
    }
}
