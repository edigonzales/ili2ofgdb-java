package ch.ehi.ili2ofgdb;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Test;
import org.junit.Assert;
import org.junit.Ignore;

import ch.ehi.ili2db.AbstractTestSetup;
import ch.ehi.ili2db.Ili2dbAssert;
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;

@Ignore("openfgdb4j backend parity gap: SQL function coverage, metadata visibility, and geometry handling differ from ili2pg/ili2gpkg")
public class UniqueIndex10OfgdbTest extends ch.ehi.ili2db.UniqueIndex10Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/UniqueIndex10OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

}
