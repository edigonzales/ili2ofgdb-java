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

## License

Like upstream ili2db, this repository is licensed under the GNU Lesser General Public License
(LGPL), see [LICENSE](LICENSE) and [docs/LICENSE.lgpl](docs/LICENSE.lgpl).
