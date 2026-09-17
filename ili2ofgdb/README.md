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
| DDL script | `--createScript` writes a self-contained script (domains included) for an empty geodatabase |
| XY precision | `--fgdbXyResolution`/`--fgdbXyTolerance` define the storage grid of the geometry columns |
| Relationship classes | 1:1, 1:n and n:m; n:m uses the association table of ili2db as mapping table and keeps the attributes (`IsAttributed`) |
| Geometry | one geometry column per table; WKB at the JDBC boundary, native curves (circular arcs) in the geodatabase |
| Transactions | snapshot based rollback, one shared writable session per `.gdb` |

Flavour specific switches:

* `--dbfile <folder.gdb>`
* `--fgdbCreateDomains[=true|false]` (default true)
* `--fgdbCreateRelationshipClasses[=true|false]` (default true)
* `--fgdbIncludeInactiveEnumValues[=true|false]` (default false)
* `--fgdbWktDir <dir>` – WKT files for CRS codes that are not bundled
* `--fgdbXyResolution <value>`, `--fgdbXyTolerance <value>` – storage grid of the geometry columns
  (default `0.000001`/`0.00001`). If only one value is given, the other is derived with a ratio of
  10; the tolerance is raised to at least twice the resolution. Very fine resolutions shift the XY
  origin, so that the grid range of the file geodatabase (9e15 grid units) is not exhausted.

## Known limitations

* Attribute indexes / unique constraints are not supported by the file geodatabase; the
  corresponding contract tests are skipped with a reason.
* Multiple geometry columns per table are not possible; the one-geometry contract tests
  (`*OneGeom`) are the ones the flavour implements.
* Relationship classes are created during the schema import (fresh geodatabase). When editing an
  existing geodatabase only existing relationships are reused.
* Non-EPSG spatial references are not resolved (no CRS is written).

## Tests

* `ili2ofgdb/test/java` – the FGDB contract tests (subclasses of the shared abstract tests) and the
  FGDB specific tests (mapping, JDBC, domain scripts, XY precision, geometry, interop); the smoke
  inspector additionally verifies the packaged bindist end to end (CLI import/export, GDAL cross
  check).
* `ili2ofgdb/test/data` – flavour specific test data (relationship/range mapping model, mandatory
  checks model, smoke data).

11 shared contract tests are skipped with a reason (9 multiple geometry columns, 2 attribute
indexes); all other contract tests run, including the offline DDL script tests.

```bash
./gradlew ili2ofgdbTest -PgdalPrefix=/opt/miniconda3/envs/gdal
./gradlew ili2ofgdbSmoke
```
