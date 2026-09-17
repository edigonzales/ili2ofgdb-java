package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;

/**
 * Tests for the WKT resolution of {@link OfgdbCrs} and its effect on the written feature class
 * definitions.
 */
public class OfgdbCrsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void bundledWktIsUsedForKnownEpsgCode() {
        String wkt = OfgdbCrs.wktFor(2056);
        assertTrue("expected the bundled CH1903+ / LV95 WKT but got: " + wkt,
                wkt.contains("CH1903+ / LV95"));
        assertTrue(wkt.contains("ID[\"EPSG\",2056]"));
    }

    @Test
    public void unknownEpsgCodeFallsBackToEmptyWkt() {
        assertEquals("", OfgdbCrs.wktFor(999999));
        assertEquals("", OfgdbCrs.wktFor(0));
    }

    @Test
    public void userDirectoryOverridesTheBundledWkt() throws Exception {
        File directory = temporaryFolder.newFolder("wkt");
        Files.write(new File(directory, "2056.wkt").toPath(),
                "PROJCRS[\"custom\"]".getBytes(StandardCharsets.UTF_8));
        OfgdbCrs.setWktDirectory(directory.getAbsolutePath());
        try {
            assertEquals("PROJCRS[\"custom\"]", OfgdbCrs.wktFor(2056));
        } finally {
            OfgdbCrs.setWktDirectory(null);
        }
    }

    @Test
    public void writtenFeatureClassCarriesTheCrsWkt() throws Exception {
        String testDb = "build/test-ofgdb/OfgdbCrsTest.gdb";
        OfgdbTestSetup setup = new OfgdbTestSetup(testDb);
        setup.resetDb();
        File model = new File("test/data/Rounder/Rounding23.ili");
        Config config = setup.initConfig(model.getPath(), model.getPath() + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_SCHEMAIMPORT);
        config.setCreateFk(Config.CREATE_FK_YES);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        Ili2db.run(config, null);

        String definition = OfgdbTestCatalogue.definition(testDb, "classkoord2");
        assertTrue("expected the WKT in the definition but got: " + definition,
                definition.contains("CH1903+ / LV95"));
        assertTrue(definition.contains("<WKID>2056</WKID>"));
    }
}
