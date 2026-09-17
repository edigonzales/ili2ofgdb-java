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
}
