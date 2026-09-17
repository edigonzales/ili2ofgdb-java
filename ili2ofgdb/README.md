# ili2ofgdb

Pure Java flavour of [ili2db](https://github.com/claeis/ili2db) for ESRI File Geodatabases, based
on [filegdb4j](https://github.com/edigonzales/filegdb4j). The JDBC interface of ili2db is kept: the
shim in `src/ch/ehi/ili2ofgdb/jdbc` translates the SQL issued by ili2db into filegdb4j operations.

See the [repository README](../README.md) for the architecture and the build/test commands.

## Features

| Area | Behaviour |
| --- | --- |
| CRS | WKID plus WKT (bundled resource, extensible with `--fgdbWktDir <dir>` containing `<epsg>.wkt`) |
| Spatial index | `--createGeomIdx` builds the native `.spx` index (kept on append) |
| Domains | coded domains (enums, booleans) and range domains (`--createNumChecks`); `--fgdbCreateDomains` |
| Relationship classes | 1:1, 1:n and n:m; n:m uses the association table of ili2db as mapping table and keeps the attributes (`IsAttributed`) |
| Geometry | one geometry column per table; WKB at the JDBC boundary, native curves (circular arcs) in the geodatabase |
| Transactions | snapshot based rollback, one shared writable session per `.gdb` |

Flavour specific switches:

* `--dbfile <folder.gdb>`
* `--fgdbCreateDomains[=true|false]` (default true)
* `--fgdbCreateRelationshipClasses[=true|false]` (default true)
* `--fgdbIncludeInactiveEnumValues[=true|false]` (default false)
* `--fgdbWktDir <dir>` – WKT files for CRS codes that are not bundled
* `--fgdbXyResolution <value>`, `--fgdbXyTolerance <value>` (informational; the fixed precision of
  the flavour is fine grained)

## Known limitations

* Attribute indexes / unique constraints are not supported by the file geodatabase; the
  corresponding contract tests are skipped with a reason.
* The flavour executes DDL directly; there is no offline DDL script collection.
* Multiple geometry columns per table are not possible; the one-geometry contract tests
  (`*OneGeom`) are the ones the flavour implements.
* Relationship classes are created during the schema import (fresh geodatabase). When editing an
  existing geodatabase only existing relationships are reused.
* Non-EPSG spatial references are not resolved (no CRS is written).

## Tests

* `ili2ofgdb/test/java` – the FGDB contract tests (subclasses of the shared abstract tests) and the
  FGDB specific tests (mapping, JDBC, geometry, interop).
* `ili2ofgdb/test/data` – flavour specific test data (relationship/range mapping model, mandatory
  checks model, smoke data).

```bash
./gradlew ili2ofgdbTest -PgdalPrefix=/opt/miniconda3/envs/gdal
./gradlew ili2ofgdbSmoke
```
