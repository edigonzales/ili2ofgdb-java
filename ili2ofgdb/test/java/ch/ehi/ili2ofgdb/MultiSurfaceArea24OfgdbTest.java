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
import ch.interlis.iox_j.wkb.Wkb2iox;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import org.junit.Test;

import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MultiSurfaceArea24OfgdbTest extends ch.ehi.ili2db.MultiSurfaceArea24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/MultiSurfaceArea24OfgdbTest.gdb";
    private static final double EPS = 1e-3;

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    protected void assertMultiSurfaceArea24_classa12_geomattr(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT geomattr1 FROM " + setup.prefixName("classa1"));
        Wkb2iox wkb2iox = new Wkb2iox();

        while (rs.next()) {
            byte[] value = rs.getBytes(1);
            if (value == null) {
                fail("expected binary value in geometry column but got null");
            }
            assertExpectedSurfaceGeometry(value, wkb2iox);
        }

        rs = stmt.executeQuery("SELECT geomattr1 FROM " + setup.prefixName("classa2"));
        while (rs.next()) {
            byte[] value = rs.getBytes(1);
            if (value == null) {
                fail("expected binary value in geometry column but got null");
            }
            assertExpectedSurfaceGeometry(value, wkb2iox);
        }
    }

    @Test
    @Override
    public void exportXtf() throws Exception {
        importXtf();

        File data = new File(TEST_OUT, "MultiSurfaceArea24-out.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_EXPORT);
        config.setModels("MultiSurfaceArea24");
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);

        Configuration ili2cConfig = new Configuration();
        FileEntry fileEntry = new FileEntry(TEST_OUT + "/MultiSurfaceArea24.ili", FileEntryKind.ILIMODELFILE);
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
        assertEquals("MultiSurfaceArea24.TestA.ClassA1", iomObj.getobjecttag());
        assertNotNull(iomObj.getattrobj("geomAttr1", 0));
        assertExpectedSurfaceGeometryFromIom(iomObj.getattrobj("geomAttr1", 0));

        event = reader.read();
        assertTrue(event instanceof ObjectEvent);
        iomObj = ((ObjectEvent) event).getIomObject();
        assertEquals("MultiSurfaceArea24.TestA.ClassA2", iomObj.getobjecttag());
        assertNotNull(iomObj.getattrobj("geomAttr1", 0));
        assertExpectedSurfaceGeometryFromIom(iomObj.getattrobj("geomAttr1", 0));

        assertTrue(reader.read() instanceof EndBasketEvent);
        assertTrue(reader.read() instanceof EndTransferEvent);
    }

    private static void assertExpectedSurfaceGeometry(byte[] value, Wkb2iox wkb2iox) throws Exception {
        IomObject iomGeom = wkb2iox.read(value);
        assertExpectedSurfaceGeometryFromIom(iomGeom);
    }

    private static void assertExpectedSurfaceGeometryFromIom(IomObject iomGeom) throws Exception {
        MultiPolygon geom = Iox2jts.multisurface2JTS(iomGeom, 0.0, 2056);
        assertEquals(2, geom.getNumGeometries());

        List<Envelope> outerEnvelopes = new ArrayList<Envelope>();
        List<Envelope> innerEnvelopes = new ArrayList<Envelope>();
        for (int i = 0; i < geom.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) geom.getGeometryN(i);
            assertEquals(1, polygon.getNumInteriorRing());
            outerEnvelopes.add(polygon.getExteriorRing().getEnvelopeInternal());
            innerEnvelopes.add(polygon.getInteriorRingN(0).getEnvelopeInternal());
        }

        assertEnvelopeSet(outerEnvelopes, new double[][]{
                {480000.111, 480000.999, 70000.111, 70000.999},
                {490000.111, 490000.999, 70000.111, 70000.999},
        });
        assertEnvelopeSet(innerEnvelopes, new double[][]{
                {480000.555, 480000.666, 70000.222, 70000.666},
                {490000.555, 490000.666, 70000.222, 70000.666},
        });
    }

    private static void assertEnvelopeSet(List<Envelope> actual, double[][] expected) {
        assertEquals(expected.length, actual.size());
        boolean[] used = new boolean[actual.size()];
        for (double[] expectedEnv : expected) {
            int matched = -1;
            for (int i = 0; i < actual.size(); i++) {
                if (used[i]) {
                    continue;
                }
                Envelope env = actual.get(i);
                if (Math.abs(env.getMinX() - expectedEnv[0]) <= EPS
                        && Math.abs(env.getMaxX() - expectedEnv[1]) <= EPS
                        && Math.abs(env.getMinY() - expectedEnv[2]) <= EPS
                        && Math.abs(env.getMaxY() - expectedEnv[3]) <= EPS) {
                    matched = i;
                    break;
                }
            }
            if (matched < 0) {
                fail("missing expected envelope [" + expectedEnv[0] + "," + expectedEnv[1] + "," + expectedEnv[2] + "," + expectedEnv[3] + "]");
            }
            used[matched] = true;
        }
    }
}
