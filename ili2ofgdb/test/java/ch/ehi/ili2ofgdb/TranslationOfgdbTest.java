package ch.ehi.ili2ofgdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Assert;

import ch.ehi.ili2db.AbstractTestSetup;
import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateList;

public class TranslationOfgdbTest extends ch.ehi.ili2db.TranslationTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/TranslationOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    protected void validateImportItf10lineTable_Geom(Statement stmt) throws SQLException {
        validateLineTableGeom(stmt, setup.prefixName("classa2_geoma"));
        validateLineTableGeom(stmt, setup.prefixName("classa3_geoma"));
    }

    private void validateLineTableGeom(Statement stmt, String tableName) throws SQLException {
        Assert.assertTrue(stmt.execute("SELECT _geom FROM " + tableName));
        ResultSet rs = stmt.getResultSet();
        try {
            Assert.assertTrue(rs.next());
            byte[] wkb = rs.getBytes(1);
            Assert.assertNotNull("missing geometry in " + tableName, wkb);
            IomObject geom = decodeWkb(wkb, tableName);
            assertClosedLineCoordinates(geom, tableName);
        } finally {
            if (rs != null) {
                rs.close();
            }
        }
    }

    private IomObject decodeWkb(byte[] wkb, String tableName) throws SQLException {
        try {
            return new OfgdbWkb2iox().read(wkb);
        } catch (Exception e) {
            throw new SQLException("failed to decode WKB from " + tableName, e);
        }
    }

    private void assertClosedLineCoordinates(IomObject geom, String tableName) throws SQLException {
        Coordinate[] coords = toCoordinates(geom, tableName);
        Assert.assertEquals("unexpected coordinate count in " + tableName, 5, coords.length);
        assertCoord(coords[0], 480000.0, 70000.0);
        assertCoord(coords[1], 480010.0, 70000.0);
        assertCoord(coords[2], 480010.0, 70010.0);
        assertCoord(coords[3], 480000.0, 70010.0);
        assertCoord(coords[4], 480000.0, 70000.0);
    }

    private Coordinate[] toCoordinates(IomObject geom, String tableName) throws SQLException {
        try {
            CoordinateList list = Iox2jts.polyline2JTS(geom, false, 0);
            return list.toCoordinateArray();
        } catch (Iox2jtsException e) {
            throw new SQLException("failed to build coordinate list for " + tableName, e);
        }
    }

    private void assertCoord(Coordinate coord, double x, double y) {
        Assert.assertEquals(x, coord.x, 0.0);
        Assert.assertEquals(y, coord.y, 0.0);
    }
}
