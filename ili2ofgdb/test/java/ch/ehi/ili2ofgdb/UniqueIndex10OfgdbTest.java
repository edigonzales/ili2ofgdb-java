package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

/**
 * FGDB flavour of the unique index contract. The file geodatabase has no attribute indexes, so
 * {@link OfgdbTestSetup#supportsUniqueConstraints()} reports {@code false} and the shared test
 * skips itself explicitly.
 */
public class UniqueIndex10OfgdbTest extends ch.ehi.ili2db.UniqueIndex10Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/UniqueIndex10OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

}
