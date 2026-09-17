package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.Test;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;

/**
 * Tests for {@code --createGeomIdx}: the feature classes get the native spatial index of the file
 * geodatabase, which filegdb4j builds as {@code .spx} sidecar file.
 */
public class GeomIndexOfgdbTest {

    private static final String TEST_DB = "build/test-ofgdb/GeomIndexOfgdbTest.gdb";
    private static final String MODEL = "test/data/Rounder/Rounding23.ili";
    private static final String XTF = "test/data/Rounder/Rounding1a.xtf";

    @Test
    public void createGeomIndexBuildsNativeSpatialIndex() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB);
        setup.resetDb();
        schemaImport(setup, true);

        assertTrue("expected a native .spx index after --createGeomIdx", hasSpatialIndexFile(TEST_DB));
        String definition = OfgdbTestCatalogue.definition(TEST_DB, "classkoord2");
        assertTrue("expected HasSpatialIndex in the definition but got: " + definition,
                definition.contains("<HasSpatialIndex>true</HasSpatialIndex>"));

        // appending rows must keep the index (filegdb4j rebuilds it on close)
        File data = new File(XTF);
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(false);
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);

        assertTrue("expected a native .spx index after the data import", hasSpatialIndexFile(TEST_DB));
    }

    @Test
    public void withoutFlagNoSpatialIndexIsWritten() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB);
        setup.resetDb();
        schemaImport(setup, false);

        assertFalse("did not expect a native .spx index without --createGeomIdx",
                hasSpatialIndexFile(TEST_DB));
        String definition = OfgdbTestCatalogue.definition(TEST_DB, "classkoord2");
        assertFalse(definition.contains("<HasSpatialIndex>true</HasSpatialIndex>"));
    }

    private static void schemaImport(OfgdbTestSetup setup, boolean createGeomIndex) throws Exception {
        File model = new File(MODEL);
        Config config = setup.initConfig(model.getPath(), model.getPath() + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_SCHEMAIMPORT);
        config.setCreateFk(Config.CREATE_FK_YES);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        if (createGeomIndex) {
            config.setValue(Config.CREATE_GEOM_INDEX, Config.TRUE);
        }
        Ili2db.run(config, null);
    }

    private static boolean hasSpatialIndexFile(String gdbPath) throws Exception {
        Path directory = Paths.get(gdbPath);
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(file -> file.getFileName().toString().endsWith(".spx"));
        }
    }
}
