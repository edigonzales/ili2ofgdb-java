package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2db.AbstractTestSetup;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iom_j.xtf.XtfReader;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox_j.wkb.Iox2wkb;
import ch.interlis.iox_j.wkb.Iox2wkbException;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import net.iharder.Base64;

public class Datatypes23OfgdbTest extends ch.ehi.ili2db.Datatypes23Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/Datatypes23OfgdbTest.gdb";
    private static final String EXPECTED_XMLBOX = "<x xmlns=\"http://www.interlis.ch/INTERLIS2.3\">\n"
            + "                           <a></a>\n"
            + "                       </x>";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?(?:\\d+\\.\\d+|\\d+|\\.\\d+)(?:[eE][-+]?\\d+)?");

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Test
    public void schemaImportPreservesDeclaredTextLengths() throws Exception {
        Connection jdbcConnection = null;
        try {
            setup.resetDb();
            File data = new File(TEST_OUT + "Datatypes23.ili");
            Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
            Ili2db.setNoSmartMapping(config);
            config.setFunction(Config.FC_SCHEMAIMPORT);
            config.setCreateFk(Config.CREATE_FK_YES);
            config.setCreateTextChecks(true);
            config.setCreateNumChecks(true);
            config.setCreateDateTimeChecks(true);
            config.setTidHandling(Config.TID_HANDLING_PROPERTY);
            config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
            Ili2db.run(config, null);

            jdbcConnection = setup.createConnection();
            try (ResultSet cols = jdbcConnection.getMetaData().getColumns(null, null, setup.prefixName("classattr"), "%")) {
                boolean foundTextLimited = false;
                boolean foundMtextLimited = false;
                boolean foundTextUnlimited = false;
                Integer textLimitedSize = null;
                Integer mtextLimitedSize = null;
                Integer textUnlimitedSize = null;
                while (cols.next()) {
                    String col = cols.getString("COLUMN_NAME");
                    if ("textlimited".equalsIgnoreCase(col)) {
                        foundTextLimited = true;
                        textLimitedSize = Integer.valueOf(cols.getInt("COLUMN_SIZE"));
                    }
                    if ("mtextlimited".equalsIgnoreCase(col)) {
                        foundMtextLimited = true;
                        mtextLimitedSize = Integer.valueOf(cols.getInt("COLUMN_SIZE"));
                    }
                    if ("textunlimited".equalsIgnoreCase(col)) {
                        foundTextUnlimited = true;
                        textUnlimitedSize = Integer.valueOf(cols.getInt("COLUMN_SIZE"));
                    }
                }
                assertTrue(foundTextLimited);
                assertTrue(foundMtextLimited);
                assertTrue(foundTextUnlimited);
                assertEquals(Integer.valueOf(30), textLimitedSize);
                assertEquals(Integer.valueOf(30), mtextLimitedSize);
                assertEquals(Integer.valueOf(4096), textUnlimitedSize);
            }
        } finally {
            if (jdbcConnection != null) {
                jdbcConnection.close();
            }
        }
    }

    @Override
    @Test
    public void importXtfAttr() throws Exception {
        Connection jdbcConnection = null;
        Statement stmt = null;
        try {
            setup.resetDb();
            jdbcConnection = setup.createConnection();
            stmt = jdbcConnection.createStatement();
            File data = new File(TEST_OUT + "Datatypes23Attr.xtf");
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
            String stmtTxt = "SELECT * FROM " + setup.prefixName("classattr") + " ORDER BY t_id ASC";
            {
                Assert.assertTrue(stmt.execute(stmtTxt));
                ResultSet rs = stmt.getResultSet();
                {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals("22", rs.getString("aI32id"));
                    Assert.assertEquals(true, rs.getBoolean("aBoolean"));
                    Assert.assertEquals("15b6bcce-8772-4595-bf82-f727a665fbf3", rs.getString("aUuid"));
                    Assert.assertEquals("abc100\"\"''", rs.getString("textLimited"));
                    Assert.assertEquals("Left", rs.getString("horizAlignment"));
                    Assert.assertEquals("mailto:ceis@localhost", rs.getString("uritext"));
                    Assert.assertEquals("5", rs.getString("numericInt"));
                    String xmlbox = rs.getString("xmlbox");
                    Diff xmlboxDiff = DiffBuilder.compare(Input.fromString(EXPECTED_XMLBOX))
                            .withTest(Input.fromString(xmlbox))
                            .checkForSimilar()
                            .normalizeWhitespace()
                            .build();
                    Assert.assertFalse(xmlboxDiff.toString(), xmlboxDiff.hasDifferences());
                    Assert.assertEquals("mehr.vier", rs.getString("aufzaehlung"));
                    Assert.assertEquals(Time.valueOf("09:00:00"), rs.getTime("aTime"));
                    Assert.assertEquals("abc200\n" +
                            "end200", rs.getString("mtextLimited"));
                    Assert.assertEquals("chgAAAAAAAAA0azD", rs.getString("aStandardid"));
                    Assert.assertEquals("Grunddatensatz.Fixpunkte.LFP.Nummer", rs.getString("aAttribute"));
                    Assert.assertEquals(Date.valueOf("2002-09-24"), rs.getDate("aDate"));
                    Assert.assertEquals("Top", rs.getString("vertAlignment"));
                    Assert.assertEquals("ClassA", rs.getString("nametext"));
                    Assert.assertEquals("abc101", rs.getString("textUnlimited"));
                    assertDecimalEquals("6.0", rs.getString("numericDec"));
                    Assert.assertEquals("abc201\n" +
                            "end201", rs.getString("mtextUnlimited"));
                    Assert.assertEquals(Timestamp.valueOf("1900-01-01 12:30:05"), rs.getTimestamp("aDateTime"));
                }
                {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals(null, rs.getString("textLimited"));
                    Assert.assertTrue(rs.wasNull());
                    Assert.assertEquals("textNull", rs.getString("textUnlimited"));
                }
            }
            {
                String stmtT = "SELECT binbox FROM " + setup.prefixName("classattr") + " ORDER BY t_id ASC";
                Assert.assertTrue(stmt.execute(stmtT));
                ResultSet rs = stmt.getResultSet();
                Assert.assertTrue(rs.next());
                byte[] bytes = (byte[]) rs.getObject("binbox");
                Assert.assertFalse(rs.wasNull());
                String wkbText = Base64.encodeBytes(bytes);
                Assert.assertEquals("AAAA", wkbText);
            }
        } catch (SQLException e) {
            throw new IoxException(e);
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (jdbcConnection != null) {
                jdbcConnection.close();
            }
        }
    }

    @Override
    @Test
    public void importXtfLine() throws Exception {
        Connection jdbcConnection = null;
        Statement stmt = null;
        try {
            setup.resetDb();
            jdbcConnection = setup.createConnection();
            stmt = jdbcConnection.createStatement();
            File data = new File(TEST_OUT + "Datatypes23Line.xtf");
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

            Map<String, IomObject> expected = loadObjectsFromXtf(data);
            assertDbGeomEqualsExpected(expectedGeom(expected, "Line2.0", "straightsarcs2d"),
                    queryGeom(stmt, "line2", "straightsarcs2d", "Line2.0"));
            assertDbGeomEqualsExpected(expectedGeom(expected, "Line2.1", "straightsarcs2d"),
                    queryGeom(stmt, "line2", "straightsarcs2d", "Line2.1"));
            assertDbGeomEqualsExpected(expectedGeom(expected, "SimpleLine2.0", "straights2d"),
                    queryGeom(stmt, "simpleline2", "straights2d", "SimpleLine2.0"));
            assertDbGeomEqualsExpected(expectedGeom(expected, "SimpleLine2.1", "straights2d"),
                    queryGeom(stmt, "simpleline2", "straights2d", "SimpleLine2.1"));
            assertDbGeomEqualsExpected(expectedGeom(expected, "SimpleLine3.1", "straights3d"),
                    queryGeom(stmt, "simpleline3", "straights3d", "SimpleLine3.1"));
            assertDbGeomEqualsExpected(expectedGeom(expected, "Line3.1", "straightsarcs3d"),
                    queryGeom(stmt, "line3", "straightsarcs3d", "Line3.1"));
        } catch (SQLException e) {
            throw new IoxException(e);
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (jdbcConnection != null) {
                jdbcConnection.close();
            }
        }
    }

    @Override
    @Test
    public void importXtfSurface() throws Exception {
        Connection jdbcConnection = null;
        Statement stmt = null;
        try {
            setup.resetDb();
            jdbcConnection = setup.createConnection();
            stmt = jdbcConnection.createStatement();
            File data = new File(TEST_OUT + "Datatypes23Surface.xtf");
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

            Map<String, IomObject> expected = loadObjectsFromXtf(data);
            assertSurfaceTopoEquals(expectedGeom(expected, "Surface2.0", "surfacearcs2d"),
                    queryGeom(stmt, "surface2", "surfacearcs2d", "Surface2.0"),
                    "Surface2.0.surfacearcs2d");
            assertSurfaceTopoEquals(expectedGeom(expected, "Surface2.1", "surfacearcs2d"),
                    queryGeom(stmt, "surface2", "surfacearcs2d", "Surface2.1"),
                    "Surface2.1.surfacearcs2d");
            assertSurfaceTopoEquals(expectedGeom(expected, "SimpleSurface2.0", "surface2d"),
                    queryGeom(stmt, "simplesurface2", "surface2d", "SimpleSurface2.0"),
                    "SimpleSurface2.0.surface2d");
            assertSurfaceTopoEquals(expectedGeom(expected, "SimpleSurface2.1", "surface2d"),
                    queryGeom(stmt, "simplesurface2", "surface2d", "SimpleSurface2.1"),
                    "SimpleSurface2.1.surface2d");
            assertSurfaceTopoEquals(expectedGeom(expected, "SimpleSurface2.2", "surface2d"),
                    queryGeom(stmt, "simplesurface2", "surface2d", "SimpleSurface2.2"),
                    "SimpleSurface2.2.surface2d");
            assertSurfaceTopoEquals(expectedGeom(expected, "SimpleSurface2.3", "surface2d"),
                    queryGeom(stmt, "simplesurface2", "surface2d", "SimpleSurface2.3"),
                    "SimpleSurface2.3.surface2d");
        } catch (SQLException e) {
            throw new IoxException(e);
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (jdbcConnection != null) {
                jdbcConnection.close();
            }
        }
    }

    @Override
    @Test
    public void exportXtfSurface() throws Exception {
        importXtfSurface();

        File data = new File(TEST_OUT + "Datatypes23Surface-out.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_EXPORT);
        config.setExportTid(true);
        config.setModels("Datatypes23");
        config.setValidation(false);
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);

        Map<String, IomObject> expected = loadObjectsFromXtf(new File(TEST_OUT + "Datatypes23Surface.xtf"));
        Map<String, IomObject> actual = loadObjectsFromXtf(data);

        IomObject simpleSurface20 = actual.get("SimpleSurface2.0");
        Assert.assertNotNull(simpleSurface20);
        Assert.assertEquals("Datatypes23.Topic.SimpleSurface2", simpleSurface20.getobjecttag());
        Assert.assertEquals(0, simpleSurface20.getattrvaluecount("surface2d"));

        assertSurfaceTopoEquals(
                expectedGeom(expected, "SimpleSurface2.1", "surface2d"),
                expectedGeom(actual, "SimpleSurface2.1", "surface2d"),
                "SimpleSurface2.1.surface2d");
        assertSurfaceTopoEquals(
                expectedGeom(expected, "Surface2.1", "surfacearcs2d"),
                expectedGeom(actual, "Surface2.1", "surfacearcs2d"),
                "Surface2.1.surfacearcs2d");
    }

    @Override
    @Test
    public void importXtfSurface_asLines() throws Exception {
        Connection jdbcConnection = null;
        Statement stmt = null;
        try {
            setup.resetDb();
            jdbcConnection = setup.createConnection();
            stmt = jdbcConnection.createStatement();
            File data = new File(TEST_OUT + "Datatypes23Surface_asLines.xtf");
            Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
            Ili2db.setNoSmartMapping(config);
            config.setFunction(Config.FC_IMPORT);
            config.setDoImplicitSchemaImport(true);
            config.setValidation(false);
            config.setCreateFk(Config.CREATE_FK_YES);
            config.setTidHandling(Config.TID_HANDLING_PROPERTY);
            config.setImportTid(true);
            Ili2db.setSkipPolygonBuilding(config);
            Ili2db.run(config, null);

            IomObject actual = queryGeom(stmt, "simplesurface2", "surface2d", "SimpleSurface2.1");
            assertExpectedSurfaceAsLines(actual);
        } catch (SQLException e) {
            throw new IoxException(e);
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (jdbcConnection != null) {
                jdbcConnection.close();
            }
        }
    }

    private static void assertExpectedSurfaceAsLines(IomObject iomGeom) {
        assertNotNull(iomGeom);
        assertEquals(Iom_jObject.MULTIPOLYLINE, iomGeom.getobjecttag());
        assertEquals(1, iomGeom.getattrvaluecount(Iom_jObject.MULTIPOLYLINE_POLYLINE));
        IomObject polyline = iomGeom.getattrobj(Iom_jObject.MULTIPOLYLINE_POLYLINE, 0);
        assertNotNull(polyline);
        assertEquals(Iom_jObject.POLYLINE, polyline.getobjecttag());
        IomObject sequence = polyline.getattrobj(Iom_jObject.POLYLINE_SEQUENCE, 0);
        assertNotNull(sequence);
        assertEquals(5, sequence.getattrvaluecount(Iom_jObject.SEGMENTS_SEGMENT));
        assertCoord(sequence.getattrobj(Iom_jObject.SEGMENTS_SEGMENT, 0), 2460005.0, 1045005.0);
        assertCoord(sequence.getattrobj(Iom_jObject.SEGMENTS_SEGMENT, 1), 2460010.0, 1045005.0);
        assertCoord(sequence.getattrobj(Iom_jObject.SEGMENTS_SEGMENT, 2), 2460010.0, 1045010.0);
        assertCoord(sequence.getattrobj(Iom_jObject.SEGMENTS_SEGMENT, 3), 2460005.0, 1045010.0);
        assertCoord(sequence.getattrobj(Iom_jObject.SEGMENTS_SEGMENT, 4), 2460010.0, 1045010.0);
    }

    private static void assertCoord(IomObject coord, double x, double y) {
        assertNotNull(coord);
        assertEquals(Iom_jObject.COORD, coord.getobjecttag());
        assertEquals(x, Double.parseDouble(coord.getattrvalue(Iom_jObject.COORD_C1)), 0.0);
        assertEquals(y, Double.parseDouble(coord.getattrvalue(Iom_jObject.COORD_C2)), 0.0);
    }

    private IomObject queryGeom(Statement stmt, String tableName, String columnName, String tid) throws SQLException, ParseException {
        String sql = "SELECT " + columnName + " FROM " + setup.prefixName(tableName) + " WHERE t_ili_tid = '" + tid + "'";
        ResultSet rs = stmt.executeQuery(sql);
        assertTrue(rs.next());
        IomObject geom = readGeom(rs);
        assertFalse(rs.next());
        return geom;
    }

    private IomObject readGeom(ResultSet rs) throws SQLException, ParseException {
        byte[] value = rs.getBytes(1);
        if (value == null) {
            return null;
        }
        return decodeGeom(value);
    }

    private IomObject decodeGeom(byte[] wkb) throws ParseException {
        return new OfgdbWkb2iox().read(wkb);
    }

    private static Map<String, IomObject> loadObjectsFromXtf(File xtfFile) throws IoxException {
        Map<String, IomObject> objs = new HashMap<String, IomObject>();
        XtfReader reader = new XtfReader(xtfFile);
        IoxEvent event = null;
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

    private static IomObject expectedGeom(Map<String, IomObject> objs, String oid, String attrName) {
        IomObject obj = objs.get(oid);
        Assert.assertNotNull("missing object with oid " + oid, obj);
        if (obj.getattrvaluecount(attrName) == 0) {
            return null;
        }
        return obj.getattrobj(attrName, 0);
    }

    private static void assertDbGeomEqualsExpected(IomObject expected, IomObject actual) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertNotNull(actual);
        assertEquals(normalizeGeomText(expected.toString()), normalizeGeomText(actual.toString()));
    }

    private static void assertDecimalEquals(String expected, String actual) {
        Assert.assertNotNull("actual decimal value is null", actual);
        java.math.BigDecimal expectedValue = new java.math.BigDecimal(expected);
        java.math.BigDecimal actualValue = new java.math.BigDecimal(actual);
        Assert.assertEquals("decimal mismatch", 0, expectedValue.compareTo(actualValue));
    }

    private static void assertSurfaceTopoEquals(IomObject expected, IomObject actual, String context) {
        if (expected == null) {
            Assert.assertNull(context + " expected null surface", actual);
            return;
        }
        Assert.assertNotNull(context + " expected non-null surface", actual);
        Geometry expectedGeom = toLinearizedSurfaceJts(expected);
        Geometry actualGeom = toLinearizedSurfaceJts(actual);
        if (expectedGeom == null || actualGeom == null) {
            assertEquals(context + " geometry null mismatch",
                    normalizeGeomText(expected.toString()),
                    normalizeGeomText(actual.toString()));
            return;
        }
        if (!expectedGeom.equalsTopo(actualGeom)) {
            Assert.fail(context + " topological mismatch:\nexpected=" + normalizeGeomText(expected.toString())
                    + "\nactual=" + normalizeGeomText(actual.toString()));
        }
    }

    private static Geometry toLinearizedSurfaceJts(IomObject geom) {
        if (geom == null) {
            return null;
        }
        Iox2wkb converter = new Iox2wkb(2);
        byte[] wkb = null;
        try {
            wkb = converter.surface2wkb(geom, false, 0.001, false);
        } catch (Iox2wkbException e) {
            try {
                wkb = converter.multisurface2wkb(geom, false, 0.001, false);
            } catch (Iox2wkbException ex) {
                throw new IllegalStateException("failed to linearize surface geometry", ex);
            }
        }
        if (wkb == null) {
            return null;
        }
        try {
            return new WKBReader().read(wkb);
        } catch (ParseException e) {
            throw new IllegalStateException("failed to parse linearized surface WKB", e);
        }
    }

    private static String normalizeGeomText(String geomText) {
        if (geomText == null) {
            return null;
        }
        String normalized = geomText.replaceAll("(?i)nan", "NAN");
        Matcher matcher = NUMBER_PATTERN.matcher(normalized);
        StringBuffer out = new StringBuffer(normalized.length());
        while (matcher.find()) {
            String raw = matcher.group();
            String replacement = raw;
            try {
                java.math.BigDecimal value = new java.math.BigDecimal(raw).stripTrailingZeros();
                replacement = value.toPlainString();
                if ("-0".equals(replacement)) {
                    replacement = "0";
                }
            } catch (NumberFormatException ignored) {
                // keep token as is if it is not parseable as decimal number
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
