package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;

/**
 * Independent interoperability checks: the geodatabase written by ili2ofgdb is read back by GDAL
 * ({@code ogrinfo}). Runs automatically when GDAL is on {@code PATH} or {@code GDAL_PREFIX} points
 * to GDAL; {@code -PrequireGdal=true} turns a missing GDAL into a failure.
 */
public class OfgdbInteropTest {

    private static final String TEST_DB = "build/test-ofgdb/OfgdbInteropTest.gdb";

    @Test
    public void gdalReadsWrittenGeodatabase() throws Exception {
        Path ogrinfo = OfgdbTestGdal.findExecutable("ogrinfo");
        if (ogrinfo == null) {
            if (OfgdbTestGdal.isRequired()) {
                fail("ogrinfo is required (-PrequireGdal=true) but was not found");
            }
            Assume.assumeTrue("ogrinfo not available - skipping interop checks", false);
        }

        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB);
        setup.resetDb();
        File data = new File("test/data/Rounder/Rounding1a.xtf");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(true);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setImportTid(true);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        Ili2db.run(config, null);

        Map<String, Long> counts = OfgdbTestGdal.featureCounts(TEST_DB);
        assertEquals(Long.valueOf(1), counts.get("classmultikoord2"));
        assertEquals(Long.valueOf(1), counts.get("multiline2"));
        assertEquals(Long.valueOf(1), counts.get("multisurface2"));

        Map<String, String> types = OfgdbTestGdal.geometryTypes(TEST_DB);
        assertEquals("Multi Point", types.get("classmultikoord2"));
        assertEquals("Multi Line String", types.get("multiline2"));
        assertEquals("Multi Polygon", types.get("multisurface2"));

        String arcs = OfgdbTestGdal.run(ogrinfo, "-geom=SUMMARY", TEST_DB, "multiline2");
        assertTrue("expected a circular string arc in the GDAL output:\n" + arcs,
                arcs.contains("CIRCULARSTRING"));

        String info = OfgdbTestGdal.run(ogrinfo, "-al", "-so", TEST_DB);
        assertTrue("expected the EPSG:2056 CRS in the GDAL output:\n" + info,
                info.contains("ID[\"EPSG\",2056]"));
    }
}
