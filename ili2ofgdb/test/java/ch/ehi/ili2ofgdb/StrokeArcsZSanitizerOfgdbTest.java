package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox_j.wkb.WKBConstants;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StrokeArcsZSanitizerOfgdbTest {
    private static final String TEST_OUT = "test/data/Datatypes23/";
    private static final String TEST_DB_DIR = "build/test-ofgdb";

    @Test
    public void strokeArcsEnabledSetsNaNZToZeroOn3DLines() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/StrokeArcsZSanitizerLineStroked.gdb");
        importFile(setup, "Datatypes23Line.xtf", true);

        byte[] wkb = queryGeometry(setup,
                "SELECT straightsarcs3d FROM " + setup.prefixName("line3") + " WHERE t_ili_tid='Line3.1'");
        IomObject geom = new OfgdbWkb2iox().read(wkb);
        List<Double> zValues = collectC3Values(geom);

        assertFalse("expected Z values in 3D geometry", zValues.isEmpty());
        assertFalse("did not expect NaN Z values after sanitizing", containsNaN(zValues));
        assertTrue("expected at least one densified point with Z=0.0", containsValue(zValues, 0.0d));
        assertTrue("expected original Z values to remain present", containsValue(zValues, 300.0d));
        assertFalse("did not expect ARC segment when strokeArcs is enabled", geom.toString().contains("ARC {"));
    }

    @Test
    public void strokeArcsDisabledKeepsOriginal3DLineValues() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/StrokeArcsZSanitizerLineCurved.gdb");
        importFile(setup, "Datatypes23Line.xtf", false);

        byte[] wkb = queryGeometry(setup,
                "SELECT straightsarcs3d FROM " + setup.prefixName("line3") + " WHERE t_ili_tid='Line3.1'");
        IomObject geom = new OfgdbWkb2iox().read(wkb);
        List<Double> zValues = collectC3Values(geom);

        assertFalse("expected Z values in 3D geometry", zValues.isEmpty());
        assertFalse("did not expect NaN Z values with strokeArcs disabled", containsNaN(zValues));
        assertFalse("did not expect injected Z=0.0 when strokeArcs is disabled", containsValue(zValues, 0.0d));
        assertTrue("expected ARC segment when strokeArcs is disabled", geom.toString().contains("ARC {"));
    }

    @Test
    public void sanitizerHandlesCurvePolygonAndMultiSurfaceFamilies() throws Exception {
        byte[] curvePolygon = buildCurvePolygon3dWithNaNZ();
        byte[] sanitizedCurvePolygon = curvePolygon.clone();
        OfgdbStrokeZSanitizer.sanitizeNaNZToZero(sanitizedCurvePolygon);
        assertSanitizedC3Values(sanitizedCurvePolygon);

        byte[] multiSurface = buildMultiSurface3dWithNaNZ();
        byte[] sanitizedMultiSurface = multiSurface.clone();
        OfgdbStrokeZSanitizer.sanitizeNaNZToZero(sanitizedMultiSurface);
        assertSanitizedC3Values(sanitizedMultiSurface);
    }

    @Test
    public void sanitizerLeaves2dGeometriesUnchanged() throws Exception {
        byte[] line2d = buildLineString2d();
        byte[] sanitized = line2d.clone();
        OfgdbStrokeZSanitizer.sanitizeNaNZToZero(sanitized);
        assertArrayEquals(line2d, sanitized);
    }

    private static void assertSanitizedC3Values(byte[] wkb) throws Exception {
        IomObject geometry = new OfgdbWkb2iox().read(wkb);
        List<Double> zValues = collectC3Values(geometry);
        assertFalse("expected C3 values in sanitized 3D geometry", zValues.isEmpty());
        assertFalse("did not expect NaN C3 values after sanitizing", containsNaN(zValues));
        assertTrue("expected at least one sanitized C3 value 0.0", containsValue(zValues, 0.0d));
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

    private static byte[] queryGeometry(OfgdbTestSetup setup, String sql) throws Exception {
        try (Connection connection = setup.createConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            byte[] value = rs.getBytes(1);
            assertNotNull(value);
            return value;
        }
    }

    private static List<Double> collectC3Values(IomObject obj) {
        List<Double> zValues = new ArrayList<Double>();
        collectC3Values(obj, zValues);
        return zValues;
    }

    private static void collectC3Values(IomObject obj, List<Double> out) {
        if (obj == null) {
            return;
        }
        if (obj.getattrvaluecount(Iom_jObject.COORD_C3) > 0) {
            String c3 = obj.getattrvalue(Iom_jObject.COORD_C3);
            if (c3 != null) {
                out.add(Double.parseDouble(c3));
            }
        }
        int attrCount = obj.getattrcount();
        for (int attrIndex = 0; attrIndex < attrCount; attrIndex++) {
            String attrName = obj.getattrname(attrIndex);
            int valueCount = obj.getattrvaluecount(attrName);
            for (int valueIndex = 0; valueIndex < valueCount; valueIndex++) {
                IomObject child = obj.getattrobj(attrName, valueIndex);
                if (child != null) {
                    collectC3Values(child, out);
                }
            }
        }
    }

    private static boolean containsNaN(List<Double> values) {
        for (Double value : values) {
            if (value != null && Double.isNaN(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsValue(List<Double> values, double expected) {
        for (Double value : values) {
            if (value != null && Double.compare(value, expected) == 0) {
                return true;
            }
        }
        return false;
    }

    private static byte[] buildCurvePolygon3dWithNaNZ() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeByte(out, WKBConstants.wkbNDR);
        writeIntLe(out, WKBConstants.wkbCurvePolygon + WKBConstants.wkbIncludesZ);
        writeIntLe(out, 1);
        writeLineString3d(out, new double[][]{
                {0.0, 0.0, 5.0},
                {10.0, 0.0, Double.NaN},
                {10.0, 10.0, 5.0},
                {0.0, 0.0, 5.0}
        });
        return out.toByteArray();
    }

    private static byte[] buildMultiSurface3dWithNaNZ() {
        byte[] curvePolygon = buildCurvePolygon3dWithNaNZ();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeByte(out, WKBConstants.wkbNDR);
        writeIntLe(out, WKBConstants.wkbMultiSurface + WKBConstants.wkbIncludesZ);
        writeIntLe(out, 1);
        out.write(curvePolygon, 0, curvePolygon.length);
        return out.toByteArray();
    }

    private static byte[] buildLineString2d() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeByte(out, WKBConstants.wkbNDR);
        writeIntLe(out, WKBConstants.wkbLineString);
        writeIntLe(out, 2);
        writeDoubleLe(out, 1.0);
        writeDoubleLe(out, 1.0);
        writeDoubleLe(out, 2.0);
        writeDoubleLe(out, 2.0);
        return out.toByteArray();
    }

    private static void writeLineString3d(ByteArrayOutputStream out, double[][] coords) {
        writeByte(out, WKBConstants.wkbNDR);
        writeIntLe(out, WKBConstants.wkbLineString + WKBConstants.wkbIncludesZ);
        writeIntLe(out, coords.length);
        for (double[] coord : coords) {
            if (coord.length != 3) {
                throw new IllegalArgumentException("expected XYZ coordinate, got " + Arrays.toString(coord));
            }
            writeDoubleLe(out, coord[0]);
            writeDoubleLe(out, coord[1]);
            writeDoubleLe(out, coord[2]);
        }
    }

    private static void writeByte(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
    }

    private static void writeIntLe(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private static void writeDoubleLe(ByteArrayOutputStream out, double value) {
        long bits = Double.doubleToRawLongBits(value);
        out.write((int) (bits & 0xFFL));
        out.write((int) ((bits >> 8) & 0xFFL));
        out.write((int) ((bits >> 16) & 0xFFL));
        out.write((int) ((bits >> 24) & 0xFFL));
        out.write((int) ((bits >> 32) & 0xFFL));
        out.write((int) ((bits >> 40) & 0xFFL));
        out.write((int) ((bits >> 48) & 0xFFL));
        out.write((int) ((bits >> 56) & 0xFFL));
    }
}
