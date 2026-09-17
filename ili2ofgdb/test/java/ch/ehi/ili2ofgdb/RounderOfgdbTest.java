package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.xtf.XtfReader;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.ObjectEvent;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RounderOfgdbTest extends ch.ehi.ili2db.RounderTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/RounderOfgdbTest.gdb";
    private static final String TEST_OUT = "test/data/Rounder/";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    @Test
    public void importXtfWithoutRounding() throws Exception {
        setup.resetDb();
        File data = new File(TEST_OUT, "Rounding1a.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(true);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setImportTid(true);
        config.setCreateNumChecks(true);
        config.setDisableRounding(true);
        Ili2db.run(config, null);

        assertCoord("Coord2.1", 2460001.0001, 1045001.0001);
    }

    @Override
    @Test
    public void importXtfWithRounding() throws Exception {
        setup.resetDb();
        File data = new File(TEST_OUT, "Rounding1a.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(true);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setImportTid(true);
        config.setCreateNumChecks(true);
        Ili2db.run(config, null);

        assertCoord("Coord2.1", 2460001.000, 1045001.000);
    }

    @Override
    @Test
    public void exportXtfWithRounding() throws Exception {
        importXtfWithoutRounding();
        File data = new File(TEST_OUT, "Rounding1a-out.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_EXPORT);
        config.setModels("Rounding23");
        config.setExportTid(true);
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);
        Map<String, IomObject> objs = readObjects(data);
        Assert.assertEquals(4, objs.size());
        assertAttr(objs.get("Attr.1"), 1.0, 0.00005);
        assertCoordObject(objs.get("Coord2.1"), 2460001.000, 1045001.000, 0.00005);
        assertLineObject(objs.get("Line2.1"), 2460001.000, 1045001.000, 2460006.000, 1045006.000, 2460005.000, 1045004.000, 2460010.000, 1045010.000, 0.00005);
        assertSurfaceObject(objs.get("Surface2.1"), 0.00005);
    }

    @Override
    @Test
    public void exportXtfWithoutRounding() throws Exception {
        importXtfWithoutRounding();
        File data = new File(TEST_OUT, "Rounding1a-out.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_EXPORT);
        config.setModels("Rounding23");
        config.setExportTid(true);
        config.setDisableRounding(true);
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);
        Map<String, IomObject> objs = readObjects(data);
        Assert.assertEquals(4, objs.size());
        assertAttr(objs.get("Attr.1"), 1.01, 0.00005);
        assertCoordObject(objs.get("Coord2.1"), 2460001.0001, 1045001.0001, 0.0002);
        assertLineObject(objs.get("Line2.1"), 2460001.000, 1045001.000, 2460006.0001, 1045006.0001, 2460005.0001, 1045004.0001, 2460010.000, 1045010.000, 0.0002);
        assertSurfaceObject(objs.get("Surface2.1"), 0.0002);
    }

    private void assertCoord(String tid, double expectedX, double expectedY) throws Exception {
        Connection jdbcConnection = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            jdbcConnection = setup.createConnection();
            stmt = jdbcConnection.createStatement();
            rs = stmt.executeQuery("SELECT lcoord FROM " + setup.prefixName("ClassKoord2") + " WHERE t_ili_tid = '" + tid + "'");
            Assert.assertTrue(rs.next());
            byte[] wkb = rs.getBytes(1);
            Assert.assertNotNull("expected WKB geometry value", wkb);
            ch.interlis.iom.IomObject coord = new OfgdbWkb2iox().read(wkb);
            Assert.assertEquals(expectedX, Double.parseDouble(coord.getattrvalue("C1")), 0.00005);
            Assert.assertEquals(expectedY, Double.parseDouble(coord.getattrvalue("C2")), 0.00005);
            Assert.assertFalse(rs.next());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (jdbcConnection != null) {
                jdbcConnection.close();
            }
        }
    }

    private Map<String, IomObject> readObjects(File data) throws Exception {
        HashMap<String, IomObject> objs = new HashMap<String, IomObject>();
        XtfReader reader = new XtfReader(data);
        IoxEvent event;
        do {
            event = reader.read();
            if (event instanceof ObjectEvent) {
                IomObject iomObj = ((ObjectEvent) event).getIomObject();
                if (iomObj.getobjectoid() != null) {
                    objs.put(iomObj.getobjectoid(), iomObj);
                }
            }
        } while (!(event instanceof EndTransferEvent));
        return objs;
    }

    private static void assertAttr(IomObject obj, double expected, double tol) {
        Assert.assertNotNull(obj);
        Assert.assertEquals("Rounding23.Topic.ClassAttr", obj.getobjecttag());
        double value = Double.parseDouble(obj.getattrvalue("numericDec"));
        Assert.assertEquals(expected, value, tol);
    }

    private static void assertCoordObject(IomObject obj, double expectedX, double expectedY, double tol) {
        Assert.assertNotNull(obj);
        Assert.assertEquals("Rounding23.Topic.ClassKoord2", obj.getobjecttag());
        IomObject coord = obj.getattrobj("lcoord", 0);
        Assert.assertNotNull(coord);
        Assert.assertEquals(expectedX, Double.parseDouble(coord.getattrvalue("C1")), tol);
        Assert.assertEquals(expectedY, Double.parseDouble(coord.getattrvalue("C2")), tol);
    }

    private static void assertLineObject(IomObject obj, double c1x, double c1y, double arcCx, double arcCy, double arcAx, double arcAy, double c2x, double c2y, double tol) {
        Assert.assertNotNull(obj);
        Assert.assertEquals("Rounding23.Topic.Line2", obj.getobjecttag());
        IomObject line = obj.getattrobj("straightsarcs2d", 0);
        Assert.assertNotNull(line);
        IomObject seg0 = line.getattrobj("sequence", 0).getattrobj("segment", 0);
        IomObject seg1 = line.getattrobj("sequence", 0).getattrobj("segment", 1);
        IomObject seg2 = line.getattrobj("sequence", 0).getattrobj("segment", 2);
        Assert.assertEquals(c1x, Double.parseDouble(seg0.getattrvalue("C1")), tol);
        Assert.assertEquals(c1y, Double.parseDouble(seg0.getattrvalue("C2")), tol);
        Assert.assertEquals(arcCx, Double.parseDouble(seg1.getattrvalue("C1")), tol);
        Assert.assertEquals(arcCy, Double.parseDouble(seg1.getattrvalue("C2")), tol);
        Assert.assertEquals(arcAx, Double.parseDouble(seg1.getattrvalue("A1")), tol);
        Assert.assertEquals(arcAy, Double.parseDouble(seg1.getattrvalue("A2")), tol);
        Assert.assertEquals(c2x, Double.parseDouble(seg2.getattrvalue("C1")), tol);
        Assert.assertEquals(c2y, Double.parseDouble(seg2.getattrvalue("C2")), tol);
    }

    private static void assertSurfaceObject(IomObject obj, double tol) {
        Assert.assertNotNull(obj);
        Assert.assertEquals("Rounding23.Topic.Surface2", obj.getobjecttag());
        IomObject surface = obj.getattrobj("surfacearcs2d", 0);
        IomObject polyline = surface.getattrobj("surface", 0)
                .getattrobj("boundary", 0)
                .getattrobj("polyline", 0);
        IomObject sequence = polyline.getattrobj("sequence", 0);
        Set<String> boundaryPoints = new HashSet<String>();
        boolean sawArc = false;
        for (int i = 0; i < sequence.getattrvaluecount("segment"); i++) {
            IomObject segment = sequence.getattrobj("segment", i);
            String tag = segment.getobjecttag();
            if ("COORD".equalsIgnoreCase(tag)) {
                boundaryPoints.add(pointKey(segment.getattrvalue("C1"), segment.getattrvalue("C2")));
            } else if ("ARC".equalsIgnoreCase(tag)) {
                sawArc = true;
                Assert.assertEquals(2460010.0, Double.parseDouble(segment.getattrvalue("A1")), tol);
                Assert.assertEquals(1045018.0, Double.parseDouble(segment.getattrvalue("A2")), tol);
                boundaryPoints.add(pointKey(segment.getattrvalue("C1"), segment.getattrvalue("C2")));
            }
        }
        Assert.assertTrue("expected an ARC segment in surface boundary", sawArc);
        Assert.assertTrue(boundaryPoints.contains(pointKey("2460001.000", "1045001.000")));
        Assert.assertTrue(boundaryPoints.contains(pointKey("2460020.000", "1045015.000")) || boundaryPoints.contains(pointKey("2460020.0001", "1045015.0001")));
        Assert.assertTrue(boundaryPoints.contains(pointKey("2460001.000", "1045015.000")));
    }

    private static String pointKey(String xRaw, String yRaw) {
        double x = Double.parseDouble(xRaw);
        double y = Double.parseDouble(yRaw);
        return String.format(java.util.Locale.ROOT, "%.4f|%.4f", x, y);
    }
}
