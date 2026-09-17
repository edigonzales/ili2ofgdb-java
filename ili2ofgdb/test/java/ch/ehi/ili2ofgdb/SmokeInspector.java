package ch.ehi.ili2ofgdb;

import java.nio.file.Paths;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;

/**
 * Command line smoke check for the bindist: the imported geo database must contain domains,
 * relationship classes and geometries.
 */
public final class SmokeInspector {

    private SmokeInspector() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: SmokeInspector <dbfile>");
        }
        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(args[0]))) {
            if (database.domains().isEmpty()) {
                throw new IllegalStateException("No domains found in smoke database.");
            }
            if (database.relationships().isEmpty()) {
                throw new IllegalStateException("No relationship classes found in smoke database.");
            }
            FileGdbTable table = database.table("gis_geometrie");
            int rowCount = 0;
            int rowsWithGeometry = 0;
            for (FileGdbRow row : table) {
                rowCount++;
                if (row.geometry() != null) {
                    rowsWithGeometry++;
                }
            }
            if (rowCount == 0) {
                throw new IllegalStateException("gis_geometrie has no rows after import.");
            }
            if (rowsWithGeometry == 0) {
                throw new IllegalStateException("No non-null geometry found in gis_geometrie.");
            }
            System.out.println("smoke OK: rows=" + rowCount + " geometries=" + rowsWithGeometry
                    + " domains=" + database.domains().size()
                    + " relationships=" + database.relationships().size());
        }
    }
}
