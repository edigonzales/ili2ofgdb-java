package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import org.junit.Test;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;

public class BooleanEncodingOfgdbTest {
    private static final String TEST_DATA_DIR = "test/data";
    private static final String TEST_DB_DIR = "build/test-ofgdb";

    @Test
    public void scalarBooleanStoredAsSmallintAndReadableAsBoolean() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/BooleanEncodingScalar.gdb");
        setup.resetDb();

        File data = new File(TEST_DATA_DIR + "/Datatypes23/Datatypes23Attr.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(true);
        config.setCreateFk(Config.CREATE_FK_YES);
        config.setCreateNumChecks(true);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setImportTid(true);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        Ili2db.run(config, null);

        try (Connection jdbcConnection = setup.createConnection();
                Statement stmt = jdbcConnection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT aBoolean FROM " + setup.prefixName("classattr") + " ORDER BY t_id ASC")) {
            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1));
            assertEquals(1, rs.getInt(1));
            Object raw = rs.getObject(1);
            assertNotNull(raw);
            assertTrue(raw instanceof Number);
            assertEquals(1, ((Number) raw).intValue());
        }
        try (Connection jdbcConnection = setup.createConnection();
                ResultSet cols = jdbcConnection.getMetaData().getColumns(null, null, setup.prefixName("classattr"), "aBoolean")) {
            assertTrue(cols.next());
            assertEquals(Types.SMALLINT, cols.getInt("DATA_TYPE"));
            assertEquals("SMALLINT", cols.getString("TYPE_NAME"));
        }

        String domainDefinition = OfgdbTestCatalogue.itemDefinition(config.getDbfile(), "INTERLIS_BOOLEAN");
        assertNotNull(domainDefinition);
        assertEquals("esriFieldTypeSmallInteger", OfgdbTestCatalogue.findFirstTagText(domainDefinition, "FieldType"));
    }

    @Test
    public void coalescedBooleanArrayKeepsBooleanText() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/BooleanEncodingArray.gdb");
        setup.resetDb();

        File data = new File(TEST_DATA_DIR + "/Array/Array23a.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(true);
        config.setCreateFk(Config.CREATE_FK_YES);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setImportTid(true);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        config.setArrayTrafo(Config.ARRAY_TRAFO_COALESCE);
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);

        try (Connection jdbcConnection = setup.createConnection();
                Statement stmt = jdbcConnection.createStatement()) {
            try (ResultSet rsNull = stmt.executeQuery(
                    "SELECT aBoolean FROM " + setup.prefixName("datatypes") + " WHERE t_ili_tid='100'")) {
                assertTrue(rsNull.next());
                assertNull(rsNull.getString(1));
                assertTrue(rsNull.wasNull());
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT aBoolean FROM " + setup.prefixName("datatypes") + " WHERE t_ili_tid='101'")) {
                assertTrue(rs.next());
                String value = rs.getString(1);
                assertEquals("[true]", value);
                assertNotEquals("[1]", value);
            }
        }
    }

}
