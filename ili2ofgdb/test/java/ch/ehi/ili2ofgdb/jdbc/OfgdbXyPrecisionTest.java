package ch.ehi.ili2ofgdb.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Test;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2ofgdb.OfgdbTestSetup;
import ch.ehi.sqlgen.generator_impl.ofgdb.GeneratorOfgdb;
import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.wkb.Wkb2iox;
import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.geometry.CoordinatePrecision;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;

/**
 * {@code --fgdbXyResolution}/{@code --fgdbXyTolerance} are effective: the values end up in the
 * geometry field definition of the file geodatabase and coordinates are stored on the requested
 * grid.
 */
public class OfgdbXyPrecisionTest {
    private static final String OUT_DIR = "build/test-ofgdb/";
    /** coordinate of the test transfer file */
    private static final double IMPORTED_X = 2600000.1234;
    private static final double IMPORTED_Y = 1200000.5678;

    @Test
    public void configuredResolutionQuantizesImportedCoordinate() throws Exception {
        String gdb = OUT_DIR + "OfgdbXyPrecisionTestConfigured.gdb";
        String xtf = prepareInput(IMPORTED_X, IMPORTED_Y);
        OfgdbTestSetup setup = new OfgdbTestSetup(gdb);
        setup.resetDb();

        Config config = setup.initConfig(xtf, gdb + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(true);
        config.setDefaultSrsCode("2056");
        // the rounding of ili2db would hide the grid of the file geodatabase
        config.setDisableRounding(true);
        config.setValue(GeneratorOfgdb.XY_RESOLUTION, "0.005");
        config.setValue(GeneratorOfgdb.XY_TOLERANCE, "0.05");
        Ili2db.run(config, null);

        assertEquals(2600000.125, readCoordinate(setup)[0], 1e-4);
        assertEquals(1200000.570, readCoordinate(setup)[1], 1e-4);
        GeometryFieldDefinition geometry = geometryField(gdb, "classa1");
        assertEquals(200.0, geometry.precision().xyScale(), 0.0);
        assertEquals(0.05, geometry.precision().xyTolerance(), 0.0);
    }

    @Test
    public void defaultPrecisionKeepsImportedCoordinate() throws Exception {
        String gdb = OUT_DIR + "OfgdbXyPrecisionTestDefault.gdb";
        String xtf = prepareInput(IMPORTED_X, IMPORTED_Y);
        OfgdbTestSetup setup = new OfgdbTestSetup(gdb);
        setup.resetDb();

        Config config = setup.initConfig(xtf, gdb + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(true);
        config.setDefaultSrsCode("2056");
        // the rounding of ili2db would hide the grid of the file geodatabase
        config.setDisableRounding(true);
        Ili2db.run(config, null);

        assertEquals(IMPORTED_X, readCoordinate(setup)[0], 1e-6);
        assertEquals(IMPORTED_Y, readCoordinate(setup)[1], 1e-6);
        GeometryFieldDefinition geometry = geometryField(gdb, "classa1");
        assertEquals(1000000.0, geometry.precision().xyScale(), 0.0);
        assertEquals(0.00001, geometry.precision().xyTolerance(), 0.0);
    }

    @Test
    public void scriptKeepsConfiguredPrecision() throws Exception {
        String gdb = OUT_DIR + "OfgdbXyPrecisionTestScript.gdb";
        String script = OUT_DIR + "OfgdbXyPrecisionTestScript.sql";
        prepareInput(IMPORTED_X, IMPORTED_Y);
        OfgdbTestSetup setup = new OfgdbTestSetup(gdb);
        setup.resetDb();
        new File(script).delete();

        Config config = setup.initConfig(OUT_DIR + "SimpleCoord23.ili", gdb + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_SCRIPT);
        config.setDefaultSrsCode("2056");
        config.setValue(GeneratorOfgdb.XY_RESOLUTION, "0.005");
        config.setValue(GeneratorOfgdb.XY_TOLERANCE, "0.05");
        config.setCreatescript(script);
        Ili2db.run(config, null);

        String scriptText = new String(Files.readAllBytes(Paths.get(script)),
                Charset.forName("UTF-8"));
        assertTrue("script must keep the XY precision: " + scriptText,
                scriptText.contains("OFGDB_GEOMETRY(POINT,2056,2,0.005,0.05)"));

        setup.resetDb();
        Connection conn = setup.createDbSchema();
        try {
            conn.setAutoCommit(false);
            ch.ehi.sqlgen.DbUtility.executeSqlScript(conn, new java.io.FileReader(script));
            conn.commit();
        } finally {
            conn.close();
        }
        GeometryFieldDefinition geometry = geometryField(gdb, "classa1");
        assertEquals(200.0, geometry.precision().xyScale(), 0.0);
        assertEquals(0.05, geometry.precision().xyTolerance(), 0.0);
    }

    @Test
    public void derivesMissingValues() {
        CoordinatePrecision both = OfgdbFileGdb.xyPrecision("0.005", "0.05");
        assertEquals(200.0, both.xyScale(), 0.0);
        assertEquals(0.05, both.xyTolerance(), 0.0);

        CoordinatePrecision resolutionOnly = OfgdbFileGdb.xyPrecision("0.005", null);
        assertEquals(200.0, resolutionOnly.xyScale(), 0.0);
        assertEquals(0.05, resolutionOnly.xyTolerance(), 0.0);

        CoordinatePrecision toleranceOnly = OfgdbFileGdb.xyPrecision(null, "0.05");
        assertEquals(0.005, toleranceOnly.xyResolution(), 1e-12);
        assertEquals(0.05, toleranceOnly.xyTolerance(), 0.0);

        CoordinatePrecision defaultPrecision = OfgdbFileGdb.xyPrecision(null, null);
        assertEquals(1000000.0, defaultPrecision.xyScale(), 0.0);
        assertEquals(0.00001, defaultPrecision.xyTolerance(), 0.0);
    }

    @Test
    public void adaptsToleranceAndOrigin() {
        // tolerance is at least twice the resolution
        CoordinatePrecision raisedTolerance = OfgdbFileGdb.xyPrecision("0.005", "0.001");
        assertEquals(0.01, raisedTolerance.xyTolerance(), 0.0);

        // invalid values are ignored, the remaining value defines the precision
        CoordinatePrecision invalidResolution = OfgdbFileGdb.xyPrecision("not-a-number", "0.05");
        assertEquals(0.005, invalidResolution.xyResolution(), 1e-12);
        CoordinatePrecision invalidTolerance = OfgdbFileGdb.xyPrecision("0.005", "NaN");
        assertEquals(200.0, invalidTolerance.xyScale(), 0.0);
        assertEquals(0.05, invalidTolerance.xyTolerance(), 0.0);

        // a very fine resolution needs a shifted origin, otherwise the grid range of the file
        // geodatabase (9e15 grid units) could not cover the usual coordinates
        CoordinatePrecision fine = OfgdbFileGdb.xyPrecision("1e-9", null);
        assertEquals(1e-9, fine.xyResolution(), 1e-18);
        assertTrue("origin must be shifted, but was " + fine.xOrigin(), fine.xOrigin() > -1e9);
        assertEquals(fine.xOrigin(), fine.yOrigin(), 0.0);
    }

    @Test
    public void treatsEmptyValuesAsNotSet() {
        CoordinatePrecision precision = OfgdbFileGdb.xyPrecision("", " ");
        assertEquals(1000000.0, precision.xyScale(), 0.0);
    }

    private static String prepareInput(double x, double y) throws Exception {
        Files.createDirectories(Paths.get(OUT_DIR));
        Files.copy(Paths.get("test/data/Simple/SimpleCoord23.ili"),
                Paths.get(OUT_DIR, "SimpleCoord23.ili"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String xtf = OUT_DIR + "OfgdbXyPrecisionTest.xtf";
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<TRANSFER xmlns=\"http://www.interlis.ch/INTERLIS2.3\">\n"
                + "  <HEADERSECTION SENDER=\"ili2ofgdb\" VERSION=\"2.3\">\n"
                + "    <MODELS><MODEL NAME=\"SimpleCoord23\" VERSION=\"2016-12-21\""
                + " URI=\"mailto:ce@eisenhutinformatik.ch\"></MODEL></MODELS>\n"
                + "  </HEADERSECTION>\n"
                + "  <DATASECTION>\n"
                + "    <SimpleCoord23.TestA BID=\"b1\">\n"
                + "      <SimpleCoord23.TestA.ClassA1 TID=\"o1\">\n"
                + "        <attr1>grid</attr1>\n"
                + "        <attr2><COORD><C1>" + x + "</C1><C2>" + y + "</C2></COORD></attr2>\n"
                + "      </SimpleCoord23.TestA.ClassA1>\n"
                + "    </SimpleCoord23.TestA>\n"
                + "  </DATASECTION>\n"
                + "</TRANSFER>\n";
        Files.write(Paths.get(xtf), content.getBytes(Charset.forName("UTF-8")));
        return xtf;
    }

    private static double[] readCoordinate(OfgdbTestSetup setup) throws Exception {
        Connection conn = setup.createConnection();
        try {
            Statement stmt = conn.createStatement();
            try {
                ResultSet rs = stmt.executeQuery("SELECT attr2 FROM classa1");
                assertTrue(rs.next());
                IomObject coord = new Wkb2iox().read(rs.getBytes(1));
                assertFalse(rs.next());
                return new double[] { Double.parseDouble(coord.getattrvalue("C1")),
                        Double.parseDouble(coord.getattrvalue("C2")) };
            } finally {
                stmt.close();
            }
        } finally {
            conn.close();
        }
    }

    private static GeometryFieldDefinition geometryField(String gdb, String table) throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(gdb))) {
            return database.featureClass(table).geomField().geometry();
        } catch (Exception ex) {
            fail("failed to read geometry field definition: " + ex);
            return null;
        }
    }
}
