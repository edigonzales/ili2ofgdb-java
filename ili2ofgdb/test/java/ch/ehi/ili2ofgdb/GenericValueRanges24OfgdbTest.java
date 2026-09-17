package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;
import ch.ehi.ili2db.base.Ili2dbException;
import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.wkb.Wkb2iox;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import org.junit.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GenericValueRanges24OfgdbTest extends ch.ehi.ili2db.GenericValueRanges24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/GenericValueRanges24OfgdbTest.gdb";
    private static final double EPS = 1e-3;

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Test
    @Override
    public void importXtf() throws Exception {
        super.importXtf();
    }

    @Test
    @Override
    public void exportXtf() throws Exception {
        super.exportXtf();
    }

    @Test
    @Override
    public void importIli() throws SQLException, Ili2dbException {
        super.importIli();
        try (java.sql.Connection conn = setup.createConnection();
                ResultSet cols = conn.getMetaData().getColumns(null, null, setup.prefixName("classa_attrline"), null)) {
            List<String> names = new ArrayList<String>();
            while (cols.next()) {
                names.add(cols.getString("COLUMN_NAME"));
            }
            assertTrue("classa_attrline must contain attrline; actual columns=" + names,
                    containsIgnoreCase(names, "attrline"));
        }
    }

    @Override
    protected void assertAttrCoord(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT attrcoord FROM " + setup.prefixName("classa"));
        Wkb2iox wkb2iox = new Wkb2iox();

        assertTrue(rs.next());
        assertCoord(readGeom(wkb2iox, rs), 2530001.0, 1150002.0);
        assertFalse(rs.next());
    }

    @Override
    protected void assertAttrMultiCoord(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT attrmulticoord FROM " + setup.prefixName("classa_attrmulticoord"));
        Wkb2iox wkb2iox = new Wkb2iox();

        assertTrue(rs.next());
        MultiPoint geom = Iox2jts.multicoord2JTS(readGeom(wkb2iox, rs));
        assertCoords(geom.getCoordinates(), new double[][] {
                { 2530001.0, 1150002.0 },
                { 2740003.0, 1260004.0 },
        });
        assertFalse(rs.next());
    }

    @Override
    protected void assertAttrLine(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT attrline FROM " + setup.prefixName("classa_attrline"));
        Wkb2iox wkb2iox = new Wkb2iox();

        assertTrue(rs.next());
        IomObject iomGeom = readGeom(wkb2iox, rs);
        assertTrue(iomGeom != null);
        assertEquals("POLYLINE", iomGeom.getobjecttag());
        LineString geom = Iox2jts.polyline2JTSlineString(iomGeom, false, 0.0);
        Coordinate[] coords = geom.getCoordinates();
        assertCoords(coords, new double[][] {
                { 2480000.0, 1070000.0 },
                { 2490000.0, 1080000.0 },
        });
        assertFalse(rs.next());
    }

    @Override
    protected void assertAttrMultiLine(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT attrmultiline FROM " + setup.prefixName("classa_attrmultiline"));
        Wkb2iox wkb2iox = new Wkb2iox();

        assertTrue(rs.next());
        MultiLineString geom = Iox2jts.multipolyline2JTS(readGeom(wkb2iox, rs), 0.0);
        assertEquals(2, geom.getNumGeometries());
        assertCoords(geom.getCoordinates(), new double[][] {
                { 2480000.0, 1070000.0 },
                { 2490000.0, 1080000.0 },
                { 2480000.0, 1070000.0 },
                { 2490000.0, 1080000.0 },
        });
        assertFalse(rs.next());
    }

    @Override
    protected void assertAttrSurface(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT attrsurface FROM " + setup.prefixName("classa_attrsurface"));
        Wkb2iox wkb2iox = new Wkb2iox();

        assertTrue(rs.next());
        assertSurfaceWithHole(readGeom(wkb2iox, rs), 2480000.111, 2480000.999, 1070000.111, 1070000.999,
                2480000.555, 2480000.666, 1070000.222, 1070000.666);
        assertFalse(rs.next());
    }

    @Override
    protected void assertAttrMultiSurface(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT attrmultisurface FROM " + setup.prefixName("classa_attrmultisurface"));
        Wkb2iox wkb2iox = new Wkb2iox();

        assertTrue(rs.next());
        assertMultiSurfaceWithHole(readGeom(wkb2iox, rs), 2480000.111, 2480000.999, 1070000.111, 1070000.999,
                2480000.555, 2480000.666, 1070000.222, 1070000.666);
        assertFalse(rs.next());
    }

    private static IomObject readGeom(Wkb2iox wkb2iox, ResultSet rs) throws Exception {
        byte[] value = rs.getBytes(1);
        if (value == null) {
            return null;
        }
        return wkb2iox.read(value);
    }

    private static void assertCoord(IomObject geom, double x, double y) throws Exception {
        Coordinate coord = Iox2jts.coord2JTS(geom);
        assertEquals(x, coord.x, EPS);
        assertEquals(y, coord.y, EPS);
    }

    private static void assertSurfaceWithHole(
            IomObject geom,
            double outerMinX,
            double outerMaxX,
            double outerMinY,
            double outerMaxY,
            double innerMinX,
            double innerMaxX,
            double innerMinY,
            double innerMaxY) throws Exception {
        assertEquals("MULTISURFACE", geom.getobjecttag());
        Polygon polygon = Iox2jts.surface2JTS(geom, 0.0);
        assertEquals(1, polygon.getNumInteriorRing());
        assertEnvelope(
                polygon.getExteriorRing().getEnvelopeInternal(),
                outerMinX, outerMaxX, outerMinY, outerMaxY);
        assertEnvelope(
                polygon.getInteriorRingN(0).getEnvelopeInternal(),
                innerMinX, innerMaxX, innerMinY, innerMaxY);
    }

    private static void assertMultiSurfaceWithHole(
            IomObject geom,
            double outerMinX,
            double outerMaxX,
            double outerMinY,
            double outerMaxY,
            double innerMinX,
            double innerMaxX,
            double innerMinY,
            double innerMaxY) throws Exception {
        assertEquals("MULTISURFACE", geom.getobjecttag());
        MultiPolygon multiPolygon = Iox2jts.multisurface2JTS(geom, 0.0, 2056);
        assertEquals(1, multiPolygon.getNumGeometries());
        Polygon polygon = (Polygon) multiPolygon.getGeometryN(0);
        assertEquals(1, polygon.getNumInteriorRing());
        assertEnvelope(
                polygon.getExteriorRing().getEnvelopeInternal(),
                outerMinX, outerMaxX, outerMinY, outerMaxY);
        assertEnvelope(
                polygon.getInteriorRingN(0).getEnvelopeInternal(),
                innerMinX, innerMaxX, innerMinY, innerMaxY);
    }

    private static void assertEnvelope(
            Envelope envelope,
            double minX,
            double maxX,
            double minY,
            double maxY) {
        assertEquals(minX, envelope.getMinX(), EPS);
        assertEquals(maxX, envelope.getMaxX(), EPS);
        assertEquals(minY, envelope.getMinY(), EPS);
        assertEquals(maxY, envelope.getMaxY(), EPS);
    }

    private static void assertCoords(Coordinate[] actual, double[][] expected) {
        assertEquals(expected.length, actual.length);
        boolean[] used = new boolean[actual.length];
        for (double[] expectedCoord : expected) {
            int matched = -1;
            for (int i = 0; i < actual.length; i++) {
                if (used[i]) {
                    continue;
                }
                Coordinate coord = actual[i];
                if (Math.abs(coord.x - expectedCoord[0]) <= EPS && Math.abs(coord.y - expectedCoord[1]) <= EPS) {
                    matched = i;
                    break;
                }
            }
            if (matched < 0) {
                fail("missing expected coordinate " + expectedCoord[0] + "," + expectedCoord[1]);
            }
            used[matched] = true;
        }
    }

    private static boolean containsIgnoreCase(List<String> values, String probe) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(probe)) {
                return true;
            }
        }
        return false;
    }
}
