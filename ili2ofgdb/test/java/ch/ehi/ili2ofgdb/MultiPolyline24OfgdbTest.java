package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.xtf.Xtf24Reader;
import ch.interlis.iox.EndBasketEvent;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartBasketEvent;
import ch.interlis.iox.StartTransferEvent;
import ch.interlis.iox_j.jts.Iox2jts;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiLineString;
import org.junit.Test;

import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MultiPolyline24OfgdbTest extends ch.ehi.ili2db.MultiPolyline24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/MultiPolyline24OfgdbTest.gdb";
    private static final double EPS = 1e-3;

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    protected void assertMultiPolyline24_classa1_geomattr1(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT geomattr1 FROM " + setup.prefixName("classa1"));
        OfgdbWkb2iox wkb2iox = new OfgdbWkb2iox();

        while (rs.next()) {
            byte[] value = rs.getBytes(1);
            if (value == null) {
                fail("expected binary value in geometry column but got null");
            }
            IomObject iomGeom = wkb2iox.read(value);
            assertMultiPolylineGeometry(iomGeom);
        }
    }

    @Test
    @Override
    public void exportXtf() throws Exception {
        importXtf();

        File data = new File(TEST_OUT, "MultiPolyline24-out.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_EXPORT);
        config.setModels("MultiPolyline24");
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);

        Configuration ili2cConfig = new Configuration();
        FileEntry fileEntry = new FileEntry(TEST_OUT + "/MultiPolyline24.ili", FileEntryKind.ILIMODELFILE);
        ili2cConfig.addFileEntry(fileEntry);
        TransferDescription td = ch.interlis.ili2c.Ili2c.runCompiler(ili2cConfig);
        assertNotNull(td);

        Xtf24Reader reader = new Xtf24Reader(data);
        reader.setModel(td);

        assertTrue(reader.read() instanceof StartTransferEvent);
        assertTrue(reader.read() instanceof StartBasketEvent);

        IoxEvent event = reader.read();
        assertTrue(event instanceof ObjectEvent);
        IomObject iomObj = ((ObjectEvent) event).getIomObject();
        assertEquals("MultiPolyline24.TestA.ClassA1", iomObj.getobjecttag());
        IomObject multiPolylineAttr = iomObj.getattrobj("geomAttr1", 0);
        assertNotNull(multiPolylineAttr);
        assertMultiPolylineGeometry(multiPolylineAttr);

        assertTrue(reader.read() instanceof EndBasketEvent);
        assertTrue(reader.read() instanceof EndTransferEvent);
    }

    private static void assertMultiPolylineGeometry(IomObject iomGeom) throws Exception {
        MultiLineString geom = Iox2jts.multipolyline2JTS(iomGeom, 0.0);
        assertEquals(2, geom.getNumGeometries());
        Coordinate[] actualCoords = geom.getCoordinates();
        assertEquals(6, actualCoords.length);
        assertCoords(actualCoords, new double[][]{
                {480000.111, 70000.111, 4000.111},
                {480000.222, 70000.222, 4000.222},
                {480000.333, 70000.333, 4000.333},
                {480000.444, 70000.444, 4000.444},
                {480000.555, 70000.555, 4000.555},
                {480000.666, 70000.666, 4000.666},
        });
    }

    private static void assertCoords(Coordinate[] actual, double[][] expected) {
        boolean[] used = new boolean[actual.length];
        for (double[] expectedCoord : expected) {
            int matched = -1;
            for (int i = 0; i < actual.length; i++) {
                if (used[i]) {
                    continue;
                }
                Coordinate coord = actual[i];
                if (Math.abs(coord.x - expectedCoord[0]) <= EPS
                        && Math.abs(coord.y - expectedCoord[1]) <= EPS
                        && Math.abs(coord.z - expectedCoord[2]) <= EPS) {
                    matched = i;
                    break;
                }
            }
            if (matched < 0) {
                fail("missing expected coordinate " + expectedCoord[0] + "," + expectedCoord[1] + "," + expectedCoord[2]);
            }
            used[matched] = true;
        }
    }
}
