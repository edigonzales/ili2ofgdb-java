package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.wkb.Wkb2iox;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CurveContractOfgdbTest {
    private static final String TEST_OUT = "test/data/Datatypes23/";
    private static final String TEST_DB_DIR = "build/test-ofgdb";

    @Test
    public void lineCurvesRemainArcsWhenStrokeArcsDisabled() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/CurveContractLineCurved.gdb");
        importFile(setup, "Datatypes23Line.xtf", false);
        String geom = readGeometryAsString(
                setup,
                "SELECT straightsarcs2d FROM " + setup.prefixName("line2") + " WHERE t_ili_tid='Line2.1'");
        assertTrue("expected ARC segment but got: " + geom, geom.contains("ARC {"));
    }

    @Test
    public void lineCurvesAreLinearizedWhenStrokeArcsEnabled() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/CurveContractLineLinearized.gdb");
        importFile(setup, "Datatypes23Line.xtf", true);
        String geom = readGeometryAsString(
                setup,
                "SELECT straightsarcs2d FROM " + setup.prefixName("line2") + " WHERE t_ili_tid='Line2.1'");
        assertFalse("did not expect ARC segment with strokeArcs enabled: " + geom, geom.contains("ARC {"));
    }

    @Test
    public void surfaceCurvesRemainArcsWhenStrokeArcsDisabled() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/CurveContractSurfaceCurved.gdb");
        importFile(setup, "Datatypes23Surface.xtf", false);
        String geom = readGeometryAsString(
                setup,
                "SELECT surfacearcs2d FROM " + setup.prefixName("surface2") + " WHERE t_ili_tid='Surface2.1'");
        assertTrue("expected ARC segment but got: " + geom, geom.contains("ARC {"));
    }

    @Test
    public void surfaceCurvesAreLinearizedWhenStrokeArcsEnabled() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/CurveContractSurfaceLinearized.gdb");
        importFile(setup, "Datatypes23Surface.xtf", true);
        String geom = readGeometryAsString(
                setup,
                "SELECT surfacearcs2d FROM " + setup.prefixName("surface2") + " WHERE t_ili_tid='Surface2.1'");
        assertFalse("did not expect ARC segment with strokeArcs enabled: " + geom, geom.contains("ARC {"));
    }

    private static void importFile(OfgdbTestSetup setup, String xtfFilename, boolean strokeArcsEnabled) throws Exception {
        setup.resetDb();
        File data = new File(TEST_OUT, xtfFilename);
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(true);
        config.setCreateFk(Config.CREATE_FK_YES);
        config.setCreateNumChecks(true);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setImportTid(true);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        if (strokeArcsEnabled) {
            config.setStrokeArcs(Config.STROKE_ARCS_ENABLE);
        }
        Ili2db.run(config, null);
    }

    private static String readGeometryAsString(OfgdbTestSetup setup, String sql) throws Exception {
        try (Connection connection = setup.createConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            byte[] value = rs.getBytes(1);
            assertNotNull(value);
            IomObject iomGeom = new Wkb2iox().read(value);
            return iomGeom.toString();
        }
    }
}
