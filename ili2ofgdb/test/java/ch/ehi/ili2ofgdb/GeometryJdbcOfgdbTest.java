package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.interlis.iom.IomObject;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GeometryJdbcOfgdbTest {
    private static final String TEST_OUT = "test/data/MultiPolyline24/";
    private static final String FGDBFILENAME = "build/test-ofgdb/GeometryJdbcOfgdbTest.gdb";
    private final AbstractTestSetup setup = new OfgdbTestSetup(FGDBFILENAME);

    private void importXtf() throws Exception {
        setup.resetDb();
        File data = new File(TEST_OUT, "MultiPolyline24.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(true);
        config.setCreateFk(Config.CREATE_FK_YES);
        config.setCreateNumChecks(true);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setImportTid(true);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        config.setDefaultSrsCode("2056");
        setup.setXYParams(config);
        Ili2db.run(config, null);
    }

    @Test
    public void geometryColumnExposesWkbBytes() throws Exception {
        importXtf();
        try (Connection conn = setup.createConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT geomattr1 FROM " + setup.prefixName("classa1"))) {
            assertTrue(rs.next());
            byte[] value = rs.getBytes(1);
            assertNotNull(value);
            assertTrue(value.length > 0);
            assertTrue(rs.getObject(1) instanceof byte[]);
            IomObject geom = new OfgdbWkb2iox().read(value);
            assertEquals("MULTIPOLYLINE", geom.getobjecttag());
        }
    }

    @Test
    public void unknownColumnFailsWithSQLException() throws Exception {
        importXtf();
        try (Connection conn = setup.createConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT does_not_exist FROM " + setup.prefixName("classa1"));
            fail("expected SQLException for unknown column");
        } catch (SQLException e) {
            assertTrue(e.getMessage().toLowerCase().contains("unknown column"));
        }
    }
}
