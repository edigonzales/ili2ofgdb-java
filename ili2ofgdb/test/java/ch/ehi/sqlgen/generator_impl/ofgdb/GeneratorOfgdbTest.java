package ch.ehi.sqlgen.generator_impl.ofgdb;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;

import org.junit.Test;

import ch.ehi.sqlgen.repository.DbColGeometry;

public class GeneratorOfgdbTest {

    @Test
    public void multipointMapsToMultipointGeometryKind() throws Exception {
        GeneratorOfgdb generator = new GeneratorOfgdb();
        DbColGeometry column = new DbColGeometry();
        column.setName("shape");
        column.setType(DbColGeometry.MULTIPOINT);
        column.setSrsAuth("EPSG");
        column.setSrsId("2056");
        column.setDimension(2);

        String sqlType = invokeGeometrySqlType(generator, column);

        assertEquals("OFGDB_GEOMETRY(MULTIPOINT,2056,2)", sqlType);
    }

    @Test
    public void pointStillMapsToPointGeometryKind() throws Exception {
        GeneratorOfgdb generator = new GeneratorOfgdb();
        DbColGeometry column = new DbColGeometry();
        column.setName("shape");
        column.setType(DbColGeometry.POINT);
        column.setSrsAuth("EPSG");
        column.setSrsId("2056");
        column.setDimension(2);

        String sqlType = invokeGeometrySqlType(generator, column);

        assertEquals("OFGDB_GEOMETRY(POINT,2056,2)", sqlType);
    }

    private static String invokeGeometrySqlType(GeneratorOfgdb generator, DbColGeometry column) throws Exception {
        Method method = GeneratorOfgdb.class.getDeclaredMethod("toGeometrySqlType", DbColGeometry.class);
        method.setAccessible(true);
        return (String) method.invoke(generator, column);
    }

    @Test
    public void geometryTypeCarriesXyPrecision() throws Exception {
        GeneratorOfgdb generator = new GeneratorOfgdb();
        DbColGeometry column = new DbColGeometry();
        column.setName("shape");
        column.setType(DbColGeometry.POINT);
        column.setSrsAuth("EPSG");
        column.setSrsId("2056");
        column.setDimension(2);
        column.setCustomValue(GeneratorOfgdb.XY_RESOLUTION, "0.005");
        column.setCustomValue(GeneratorOfgdb.XY_TOLERANCE, "0.05");

        String sqlType = invokeGeometrySqlType(generator, column);

        assertEquals("OFGDB_GEOMETRY(POINT,2056,2,0.005,0.05)", sqlType);
    }

    @Test
    public void geometryTypeCarriesSpatialIndexAndXyPrecision() throws Exception {
        GeneratorOfgdb generator = new GeneratorOfgdb();
        DbColGeometry column = new DbColGeometry();
        column.setName("shape");
        column.setType(DbColGeometry.POINT);
        column.setSrsAuth("EPSG");
        column.setSrsId("2056");
        column.setDimension(2);
        column.setCustomValue(ch.ehi.ili2ofgdb.OfgdbMapping.GEOM_INDEX_CUSTOM_KEY, "true");
        column.setCustomValue(GeneratorOfgdb.XY_RESOLUTION, "0.005");
        column.setCustomValue(GeneratorOfgdb.XY_TOLERANCE, "0.05");

        String sqlType = invokeGeometrySqlType(generator, column);

        assertEquals("OFGDB_GEOMETRY(POINT,2056,2,INDEX,0.005,0.05)", sqlType);
    }

    @Test
    public void ddlParsingKeepsSpatialIndexAndXyPrecision() {
        ch.ehi.ili2ofgdb.jdbc.OfgdbSql.CreateTableSpec spec =
                ch.ehi.ili2ofgdb.jdbc.OfgdbSql.parseCreateTable(
                        "CREATE TABLE t (shape OFGDB_GEOMETRY(POINT,2056,2,INDEX,0.005,0.05))");
        ch.ehi.ili2ofgdb.jdbc.OfgdbSql.ColumnSpec column = spec.columns.get(0);
        assertEquals("OFGDB_GEOMETRY", column.type);
        assertEquals("POINT", column.geometryKind);
        assertEquals(2056, column.srsId);
        org.junit.Assert.assertTrue(column.spatialIndex);
        assertEquals("0.005", column.xyResolution);
        assertEquals("0.05", column.xyTolerance);
    }

    @Test
    public void ddlParsingKeepsToleranceOnlyValue() {
        ch.ehi.ili2ofgdb.jdbc.OfgdbSql.CreateTableSpec spec =
                ch.ehi.ili2ofgdb.jdbc.OfgdbSql.parseCreateTable(
                        "CREATE TABLE t (shape OFGDB_GEOMETRY(POINT,2056,2,,0.05))");
        ch.ehi.ili2ofgdb.jdbc.OfgdbSql.ColumnSpec column = spec.columns.get(0);
        assertEquals(null, column.xyResolution);
        assertEquals("0.05", column.xyTolerance);
    }

    @Test
    public void ddlParsingKeepsResolutionOnlyValue() {
        ch.ehi.ili2ofgdb.jdbc.OfgdbSql.CreateTableSpec spec =
                ch.ehi.ili2ofgdb.jdbc.OfgdbSql.parseCreateTable(
                        "CREATE TABLE t (shape OFGDB_GEOMETRY(POINT,2056,2,0.005,))");
        ch.ehi.ili2ofgdb.jdbc.OfgdbSql.ColumnSpec column = spec.columns.get(0);
        assertEquals("0.005", column.xyResolution);
        assertEquals(null, column.xyTolerance);
    }
}
