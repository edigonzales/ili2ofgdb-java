package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import ch.ehi.ili2db.AbstractTestSetup;
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iom_j.xtf.XtfReader;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

public class MultipleGeomAttrsOfgdbTest extends ch.ehi.ili2db.MultipleGeomAttrsTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/MultipleGeomAttrsOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    @Test
    public void exportXtf() throws Exception {
        super.exportXtfOneGeom();
    }

    @Override
    @Test
    public void importIli() throws Exception {
        super.importIliOneGeom();
    }

    @Override
    @Test
    public void importIliOneGeom() throws Exception {
        super.importIliOneGeom();
    }

    @Override
    @Test
    public void importIliExtendedClassSmart1() throws Exception {
        super.importIliExtendedClassSmart1OneGeom();
    }

    @Override
    @Test
    public void importIliExtendedClassSmart1OneGeom() throws Exception {
        super.importIliExtendedClassSmart1OneGeom();
    }

    @Override
    @Test
    public void importIliExtendedClassSmart2() throws Exception {
        super.importIliExtendedClassSmart2OneGeom();
    }

    @Override
    @Test
    public void importIliExtendedClassSmart2OneGeom() throws Exception {
        super.importIliExtendedClassSmart2OneGeom();
    }

    @Override
    @Test
    public void importXtf() throws Exception {
        super.importXtf();
    }

    @Override
    @Test
    public void exportXtfOneGeom() throws Exception {
        super.exportXtfOneGeom();
    }

    @Override
    @Test
    public void exportXtfExtendedClassSmart1() throws Exception {
        importXtfExtendedClassSmart1();
        File data = new File(TEST_OUT, "MultipleGeomAttrsExtendedClass-out.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_EXPORT);
        config.setExportTid(true);
        config.setModels("MultipleGeomAttrsExtendedClass");
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);
        assertExtendedExport(data);
    }

    @Override
    @Test
    public void exportXtfExtendedClassSmart2() throws Exception {
        importXtfExtendedClassSmart2();
        File data = new File(TEST_OUT, "MultipleGeomAttrsExtendedClass-out.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_EXPORT);
        config.setExportTid(true);
        config.setModels("MultipleGeomAttrsExtendedClass");
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);
        assertExtendedExport(data);
    }

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

    private void assertExtendedExport(File data) throws Exception {
        HashMap<String, IomObject> objs = new HashMap<String, IomObject>();
        XtfReader reader = new XtfReader(data);
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
        Assert.assertEquals(6, objs.size());
        assertExtendedClassA(objs.get("ClassA.1"), "MultipleGeomAttrsExtendedClass.Topic.ClassA", "ClassA.1");
        assertExtendedClassA(objs.get("ClassAp.1"), "MultipleGeomAttrsExtendedClass.Topic.ClassAp", "ClassAp.1");
        Assert.assertEquals("MultipleGeomAttrsExtendedClass.Topic.ClassAx oid ClassAx.1 {coord COORD {C1 2460002.000, C2 1045002.000}}",
                objs.get("ClassAx.1").toString());
        Assert.assertEquals("MultipleGeomAttrsExtendedClass.TopicB.ClassB oid ClassB.1 {geom COORD {C1 2460001.000, C2 1045001.000}}",
                objs.get("ClassB.1").toString());
        Assert.assertEquals(
                "MultipleGeomAttrsExtendedClass.TopicB.ClassB1 oid ClassB1.1 {coord COORD {C1 2460002.100, C2 1045002.100}, geom COORD {C1 2460002.000, C2 1045002.000}}",
                objs.get("ClassB1.1").toString());
        Assert.assertEquals(
                "MultipleGeomAttrsExtendedClass.TopicB.ClassB2 oid ClassB2.1 {coord COORD {C1 2460003.100, C2 1045003.100}, geom COORD {C1 2460003.000, C2 1045003.000}}",
                objs.get("ClassB2.1").toString());
    }

    private void assertExtendedClassA(IomObject obj, String expectedTag, String expectedOid) throws Exception {
        Assert.assertNotNull(obj);
        Assert.assertEquals(expectedTag, obj.getobjecttag());
        Assert.assertEquals(expectedOid, obj.getobjectoid());
        assertObjectProperties(obj);
    }
}
