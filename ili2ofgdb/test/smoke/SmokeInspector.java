import ch.ehi.openfgdb4j.OpenFgdb;

public final class SmokeInspector {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("usage: SmokeInspector <dbfile>");
    }
    OpenFgdb api = new OpenFgdb();
    long db = api.open(args[0]);
    try {
      if (api.listDomains(db).isEmpty()) {
        throw new IllegalStateException("No domains found in smoke database.");
      }
      if (api.listRelationships(db).isEmpty()) {
        throw new IllegalStateException("No relationship classes found in smoke database.");
      }

      long table = api.openTable(db, "gis_geometrie");
      try {
        long cursor = api.search(table, "*", "");
        try {
          int rowCount = 0;
          int rowsWithGeometry = 0;
          while (true) {
            long row = api.fetchRow(cursor);
            if (row == 0L) {
              break;
            }
            rowCount++;
            try {
              byte[] geometry = api.rowGetGeometry(row);
              if (geometry != null && geometry.length > 0) {
                rowsWithGeometry++;
              }
            } finally {
              api.closeRow(row);
            }
          }
          if (rowCount == 0) {
            throw new IllegalStateException("gis_geometrie has no rows after import.");
          }
          if (rowsWithGeometry == 0) {
            throw new IllegalStateException("No non-null geometry found in gis_geometrie.");
          }
        } finally {
          api.closeCursor(cursor);
        }
      } finally {
        api.closeTable(db, table);
      }
    } finally {
      api.close(db);
    }
  }
}
