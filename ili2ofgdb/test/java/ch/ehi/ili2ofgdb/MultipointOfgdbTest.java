package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.io.WKBReader;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MultipointOfgdbTest extends ch.ehi.ili2db.MultipointTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/MultipointOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    protected void importXtfSmartCustom_assert_classa1(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT geom FROM " + setup.prefixName("classa1"));
        WKBReader wkbReader = new WKBReader();
        while (rs.next()) {
            byte[] wkb = rs.getBytes(1);
            assertNotNull("expected geometry bytes in column geom", wkb);
            Geometry geom = wkbReader.read(wkb);
            assertEquals(MultiPoint.class, geom.getClass());
            Coordinate[] coords = geom.getCoordinates();
            assertEquals(2, coords.length);
            assertEquals(600030.0, coords[0].x, 0.0001);
            assertEquals(200020.0, coords[0].y, 0.0001);
            assertEquals(600015.0, coords[1].x, 0.0001);
            assertEquals(200005.0, coords[1].y, 0.0001);
        }
    }
}
