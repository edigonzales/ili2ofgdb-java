# ili2ofgdb-java

Pure Java implementation of the `ili2ofgdb` flavor for [ili2db](https://github.com/claeis/ili2db),
based on the [filegdb4j](https://github.com/edigonzales/filegdb4j) library.

Unlike the previous `openfgdb4j`-based approach, this flavor needs no JNI, no FFM, no native
libraries and no GDAL at runtime. The File Geodatabase is read and written by filegdb4j directly,
behind the existing JDBC shim (`OfgdbDriver`, `OfgdbConnection`, `OfgdbStatement`, ...).

## Base

* The ili2db core, the shared tests and all other flavors are imported **unmodified** from
  `claeis/ili2db` master (5.5.3-SNAPSHOT, commit `ed9e7c2`).
* Everything specific to this project lives in `ili2ofgdb/` (flavor implementation, tests, data)
  and in additive build configuration.
* The provenance is verified with
  `git diff upstream/master -- src test/java/ch/ehi/ili2db <other flavor dirs>`.

## Layout

| Path | Content |
| --- | --- |
| `src/`, `test/`, other flavor directories | unmodified upstream ili2db |
| `ili2ofgdb/src` | ili2ofgdb flavor (JDBC shim on top of filegdb4j) |
| `ili2ofgdb/test/java` | FGDB contract tests and FGDB specific tests |
| `ili2ofgdb/test/data` | FGDB specific test data (mapping, smoke) |

## Architecture

The flavor keeps the ili2db mapping architecture and the JDBC interface, and replaces the native
`openfgdb4j` backend by the pure Java [filegdb4j](https://github.com/edigonzales/filegdb4j) library:

* `ch.ehi.ili2ofgdb.jdbc.OfgdbDriver` / `OfgdbConnection` / `OfgdbStatement` / `OfgdbMetaData` -
  the JDBC shim. One refcounted session per `.gdb` file is shared by all connections.
* `ch.ehi.ili2ofgdb.jdbc.OfgdbFileGdb` - the storage engine: tables, rows, DDL/DML, domains and
  relationship classes on top of filegdb4j.
* `ch.ehi.ili2ofgdb.jdbc.OfgdbSql` - the small SQL dialect of ili2db: `CREATE TABLE`, `CREATE
  DOMAIN`, `INSERT`, `UPDATE`, `DELETE` and WHERE evaluation.
* `ch.ehi.ili2ofgdb.jdbc.OfgdbGeometryBridge` / `OfgdbIomGeometry` - WKB (the contract of the JDBC
  layer) to the native filegdb4j geometry model, including circular arcs.
* `ch.ehi.ili2ofgdb.OfgdbMapping` - domains and relationship classes, implemented through the
  public custom mapping hooks only; the ili2db core stays untouched.

Genuine backend differences are expressed as explicit `@Ignore` overrides with a documented reason in
the flavor test classes, so the shared test suite runs with 11 documented skips instead of silently
replaced tests: the file geodatabase stores one geometry column per table (9 tests) and has no
attribute indexes/unique constraints (2 tests). Everything else, including the offline DDL scripts,
works.

## Features

* **CRS**: feature classes carry the WKID and, when available, the full WKT of the EPSG code
  (bundled resource for common codes, extensible with `--fgdbWktDir <dir>` containing `<epsg>.wkt`
  files).
* **Spatial index**: `--createGeomIdx` builds the native `.spx` index of the file geodatabase.
* **Domains**: INTERLIS enumerations, booleans and numeric ranges are written as coded/range
  domains (`--fgdbCreateDomains`).
* **Relationship classes**: 1:1, 1:n and n:m relationships are written as relationship classes;
  n:m relationships are bound to the association table created by ili2db and keep their attribute
  columns (`IsAttributed`).
* **XY precision**: `--fgdbXyResolution`/`--fgdbXyTolerance` define the storage grid and tolerance of
  the geometry columns (default 1 µm/10 µm).
* **Offline DDL scripts**: `--createScript` writes a self-contained script (including
  `CREATE DOMAIN`) that can be replayed on an empty geodatabase.

## Build

```bash
./gradlew ili2ofgdbTest
```

The interop tests additionally verify generated `.gdb` files with GDAL (`ogrinfo`/`ogr2ogr`).
They run automatically when GDAL is on `PATH` (or when `GDAL_PREFIX` points to a GDAL
installation); use `-PrequireGdal=true` to turn a missing GDAL into a failure. Locally, the
conda environment `gdal` is the recommended setup:

```bash
conda activate gdal
./gradlew ili2ofgdbTest
# or
./gradlew ili2ofgdbTest -PgdalPrefix=/opt/miniconda3/envs/gdal
```

The end to end smoke (CLI schema import, data import and catalog/geometry inspection of a real
model) runs with:

```bash
./gradlew ili2ofgdbSmoke
```

The imported ili2db core is protected against accidental changes:

```bash
./gradlew verifyUpstreamCoreUnmodified
```

Intentional divergences are listed in [tools/upstream-drift-allowlist.txt](tools/upstream-drift-allowlist.txt);
the list is currently empty, because the ili2db core, the shared tests and the shared test data are
imported unmodified.

## License

Like upstream ili2db, this repository is licensed under the GNU Lesser General Public License
(LGPL), see [LICENSE](LICENSE) and [docs/LICENSE.lgpl](docs/LICENSE.lgpl).
