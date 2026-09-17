package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import ch.ehi.ili2db.AbstractTestSetup;
import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

/**
 * FGDB flavour of the multi geometry contract.
 *
 * <p>OpenFileGDB supports only one geometry column per table. The multiple-geometry contract tests
 * are skipped explicitly; the corresponding one-geometry variants ({@code *OneGeom}) run unchanged
 * and are the ones the flavor actually implements.
 */
public class MultipleGeomAttrsOfgdbTest extends ch.ehi.ili2db.MultipleGeomAttrsTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/MultipleGeomAttrsOfgdbTest.gdb";
    private static final String MULTIPLE_GEOMETRY_REASON =
            "OpenFileGDB supports only one geometry column per table; covered by the *OneGeom contract test";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    @Test
    @Ignore(MULTIPLE_GEOMETRY_REASON)
    public void importIli() throws Exception {
    }

    @Override
    @Test
    @Ignore(MULTIPLE_GEOMETRY_REASON)
    public void importIliExtendedClassSmart1() throws Exception {
    }

    @Override
    @Test
    @Ignore(MULTIPLE_GEOMETRY_REASON)
    public void importIliExtendedClassSmart2() throws Exception {
    }

    @Override
    @Test
    @Ignore(MULTIPLE_GEOMETRY_REASON)
    public void importXtf() throws Exception {
    }

    @Override
    @Test
    @Ignore(MULTIPLE_GEOMETRY_REASON)
    public void importXtfExtendedClassSmart1() throws Exception {
    }

    @Override
    @Test
    @Ignore(MULTIPLE_GEOMETRY_REASON)
    public void importXtfExtendedClassSmart2() throws Exception {
    }

    @Override
    @Test
    @Ignore(MULTIPLE_GEOMETRY_REASON)
    public void exportXtf() throws Exception {
    }

    @Override
    @Test
    @Ignore(MULTIPLE_GEOMETRY_REASON)
    public void exportXtfExtendedClassSmart1() throws Exception {
    }

    @Override
    @Test
    @Ignore(MULTIPLE_GEOMETRY_REASON)
    public void exportXtfExtendedClassSmart2() throws Exception {
    }

    /**
     * The upstream assertion compares exact coordinates; the file geodatabase may return a
     * different start point/orientation of the ring, so the topology is compared instead.
     */
    @Override
    public void assertObjectProperties(IomObject iomObj) throws Iox2jtsException {
        IomObject coordObj = iomObj.getattrobj("coord", 0);
        assertEquals("2460001.000", coordObj.getattrvalue("C1"));
        assertEquals("1045001.000", coordObj.getattrvalue("C2"));

        IomObject polylineObj = iomObj.getattrobj("line", 0);
        IomObject sequence = polylineObj.getattrobj("sequence", 0);
        IomObject segment0 = sequence.getattrobj("segment", 0);
        assertEquals("2460002.000", segment0.getattrvalue("C1"));
        assertEquals("1045002.000", segment0.getattrvalue("C2"));
        IomObject segment1 = sequence.getattrobj("segment", 1);
        assertEquals("2460010.000", segment1.getattrvalue("C1"));
        assertEquals("1045010.000", segment1.getattrvalue("C2"));

        IomObject surfaceObj = iomObj.getattrobj("surface", 0);
        MultiPolygon actual = Iox2jts.multisurface2JTS(surfaceObj, 0, 2056);
        Assert.assertEquals(1, actual.getNumGeometries());
        GeometryFactory gf = new GeometryFactory();
        Polygon expected = gf.createPolygon(new Coordinate[] {
                new Coordinate(2460005.0, 1045005.0),
                new Coordinate(2460010.0, 1045010.0),
                new Coordinate(2460005.0, 1045010.0),
                new Coordinate(2460005.0, 1045005.0) });
        Assert.assertTrue(actual.getGeometryN(0).equalsTopo(expected));
    }
}
