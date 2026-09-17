package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;

/**
 * FGDB flavour of the unique index contract. The file geodatabase has no attribute indexes, so
 * {@link OfgdbTestSetup#supportsUniqueConstraints()} reports {@code false} and the shared test
 * skips itself explicitly.
 */
public class UniqueIndex24OfgdbTest extends ch.ehi.ili2db.UniqueIndex24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/UniqueIndex24OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

}
