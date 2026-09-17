package ch.ehi.ili2ofgdb;

import org.junit.Ignore;
import org.junit.Test;

import ch.ehi.ili2db.AbstractTestSetup;

/**
 * FGDB flavour of the unique index contract. The file geodatabase has no attribute indexes; the
 * contract test is skipped explicitly.
 */
public class UniqueIndex24OfgdbTest extends ch.ehi.ili2db.UniqueIndex24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/UniqueIndex24OfgdbTest.gdb";

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
