package ch.ehi.ili2ofgdb;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.xtf.XtfReader;
import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CodedValueDomain;
import ch.so.agi.filegdb.catalog.Domain;
import ch.so.agi.filegdb.catalog.RelationshipClass;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;

/**
 * Command line smoke check for the bindist: the geodatabase created by the CLI through the packaged
 * jar must contain the expected domains, relationship class and geometries, and an exported
 * transfer file must contain the expected objects. If GDAL is available (or required), the
 * geodatabase is additionally verified with {@code ogrinfo}.
 */
public final class SmokeInspector {

    private static final String MODEL_PREFIX = "SO_AFU_ABBAUSTELLEN_20210630";
    private static final String GEOMETRY_TABLE = "gis_geometrie";
    private static final String ABBAUSTELLE_TABLE = "fachapplikation_abbaustelle";
    private static final String ABBAUSTELLE_CLASS = MODEL_PREFIX + ".Fachapplikation.Abbaustelle";
    private static final String GEOMETRY_CLASS = MODEL_PREFIX + ".GIS.Geometrie";
    private static final int EXPECTED_OBJECTS = 99;
    private static final Set<String> EXPECTED_DOMAINS = new HashSet<String>(Arrays.asList(
            MODEL_PREFIX + "_Fachapplikation_Art_Code",
            MODEL_PREFIX + "_Fachapplikation_Stand_Code",
            MODEL_PREFIX + "_Fachapplikation_Rohstoffart_Code",
            MODEL_PREFIX + "_Fachapplikation_StandRichtplan_Code",
            "INTERLIS_BOOLEAN"));
    private static final String RELATIONSHIP_NAME =
            "_" + MODEL_PREFIX + "_Fachapplikation_Abbaustelle_Geometrie_Geometrie";

    private SmokeInspector() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("usage: SmokeInspector <dbfile> [<exportedXtf>]");
        }
        Path gdb = Paths.get(args[0]);

        checkDomains(gdb);
        checkRelationship(gdb);
        int rows = checkGeometryTable(gdb);
        int exported = 0;
        if (args.length > 1) {
            exported = checkExportedXtf(Paths.get(args[1]));
        }
        checkWithGdal(gdb);

        System.out.println("smoke OK: rows=" + rows + " geometries=" + rows
                + " domains=" + EXPECTED_DOMAINS.size()
                + " relationships=1"
                + (args.length > 1 ? " exportedObjects=" + exported : ""));
    }

    private static void checkDomains(Path gdb) throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(gdb)) {
            Set<String> actual = new HashSet<String>();
            for (Domain domain : database.domains()) {
                actual.add(domain.name());
                if (domain instanceof CodedValueDomain) {
                    int values = ((CodedValueDomain) domain).values().size();
                    if (values == 0) {
                        throw new IllegalStateException("Domain " + domain.name() + " has no values.");
                    }
                }
            }
            if (!actual.equals(EXPECTED_DOMAINS)) {
                throw new IllegalStateException(
                        "Unexpected domains: expected " + EXPECTED_DOMAINS + " but got " + actual);
            }
        }
    }

    private static void checkRelationship(Path gdb) throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(gdb)) {
            List<RelationshipClass> relationships = database.relationships();
            if (relationships.size() != 1) {
                throw new IllegalStateException(
                        "Expected exactly one relationship class but got " + relationships.size());
            }
            RelationshipClass relationship = relationships.get(0);
            if (!RELATIONSHIP_NAME.equals(relationship.name())) {
                throw new IllegalStateException("Unexpected relationship class "
                        + relationship.name() + ", expected " + RELATIONSHIP_NAME);
            }
            if (!"ONE_TO_ONE".equals(relationship.cardinality().name())) {
                throw new IllegalStateException("Unexpected cardinality "
                        + relationship.cardinality() + " of " + relationship.name());
            }
            if (!ABBAUSTELLE_TABLE.equals(relationship.destinationClassName())
                    || !GEOMETRY_TABLE.equals(relationship.originClassName())) {
                throw new IllegalStateException("Unexpected participants of " + relationship.name()
                        + ": origin=" + relationship.originClassName()
                        + " destination=" + relationship.destinationClassName());
            }
        }
    }

    private static int checkGeometryTable(Path gdb) throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(gdb)) {
            String wkt = database.featureClass(GEOMETRY_TABLE).geomField().geometry().wkt();
            if (wkt == null || !wkt.contains("ID[\"EPSG\",2056]")) {
                throw new IllegalStateException("Geometry column of " + GEOMETRY_TABLE
                        + " has no EPSG:2056 CRS definition.");
            }
            int rows = 0;
            int rowsWithGeometry = 0;
            for (FileGdbRow row : database.featureClass(GEOMETRY_TABLE)) {
                rows++;
                if (row.geometry() != null) {
                    rowsWithGeometry++;
                }
            }
            if (rows != EXPECTED_OBJECTS || rowsWithGeometry != rows) {
                throw new IllegalStateException("Expected " + EXPECTED_OBJECTS
                        + " geometries but got " + rows + " rows with " + rowsWithGeometry
                        + " geometries.");
            }
            return rows;
        }
    }

    private static int checkExportedXtf(Path xtf) throws Exception {
        if (!xtf.toFile().exists()) {
            throw new IllegalStateException("Exported transfer file " + xtf + " does not exist.");
        }
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        XtfReader reader = new XtfReader(xtf.toFile());
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent) {
                    IomObject object = ((ObjectEvent) event).getIomObject();
                    Integer count = counts.get(object.getobjecttag());
                    counts.put(object.getobjecttag(),
                            Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
                if (event instanceof EndTransferEvent) {
                    break;
                }
            }
        } finally {
            reader.close();
        }
        if (countObjects(counts, ABBAUSTELLE_CLASS) != EXPECTED_OBJECTS
                || countObjects(counts, GEOMETRY_CLASS) != EXPECTED_OBJECTS) {
            throw new IllegalStateException("Exported transfer file has unexpected objects: "
                    + counts);
        }
        return EXPECTED_OBJECTS * 2;
    }

    private static int countObjects(Map<String, Integer> counts, String objectTag) {
        Integer count = counts.get(objectTag);
        return count == null ? 0 : count.intValue();
    }

    private static void checkWithGdal(Path gdb) throws Exception {
        Path ogrinfo = OfgdbTestGdal.findExecutable("ogrinfo");
        if (ogrinfo == null) {
            if (OfgdbTestGdal.isRequired()) {
                throw new IllegalStateException(
                        "ogrinfo is required (-PrequireGdal=true) but was not found");
            }
            System.out.println("smoke: ogrinfo not found, skipping the GDAL verification");
            return;
        }
        String output = OfgdbTestGdal.run(ogrinfo, "-al", "-so", gdb.toAbsolutePath().toString());
        Map<String, Long> counts = OfgdbTestGdal.featureCounts(gdb.toAbsolutePath().toString());
        Map<String, String> types = OfgdbTestGdal.geometryTypes(gdb.toAbsolutePath().toString());
        if (countFeatures(counts, GEOMETRY_TABLE) != EXPECTED_OBJECTS
                || countFeatures(counts, ABBAUSTELLE_TABLE) != EXPECTED_OBJECTS) {
            throw new IllegalStateException("GDAL reports unexpected feature counts: " + counts);
        }
        if (!"Multi Polygon".equals(types.get(GEOMETRY_TABLE))) {
            throw new IllegalStateException("GDAL reports geometry type "
                    + types.get(GEOMETRY_TABLE) + " for " + GEOMETRY_TABLE + ": " + output);
        }
        if (!output.contains("ID[\"EPSG\",2056]")) {
            throw new IllegalStateException("GDAL does not report the EPSG:2056 CRS:\n" + output);
        }
        System.out.println("smoke: ogrinfo OK (" + counts.get(GEOMETRY_TABLE) + " / "
                + counts.get(ABBAUSTELLE_TABLE) + " features, " + types.get(GEOMETRY_TABLE) + ")");
    }

    private static long countFeatures(Map<String, Long> counts, String layer) {
        Long count = counts.get(layer);
        return count == null ? -1 : count.longValue();
    }
}
