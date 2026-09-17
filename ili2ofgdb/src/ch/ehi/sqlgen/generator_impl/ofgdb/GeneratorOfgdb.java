package ch.ehi.sqlgen.generator_impl.ofgdb;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.settings.Settings;
import ch.ehi.ili2ofgdb.OfgdbMapping;
import ch.ehi.sqlgen.generator_impl.jdbc.GeneratorJdbc;
import ch.ehi.sqlgen.generator_impl.jdbc.GeneratorJdbc.Stmt;
import ch.ehi.sqlgen.repository.DbColBlob;
import ch.ehi.sqlgen.repository.DbColBoolean;
import ch.ehi.sqlgen.repository.DbColDate;
import ch.ehi.sqlgen.repository.DbColDateTime;
import ch.ehi.sqlgen.repository.DbColDecimal;
import ch.ehi.sqlgen.repository.DbColGeometry;
import ch.ehi.sqlgen.repository.DbColId;
import ch.ehi.sqlgen.repository.DbColNumber;
import ch.ehi.sqlgen.repository.DbColTime;
import ch.ehi.sqlgen.repository.DbColUuid;
import ch.ehi.sqlgen.repository.DbColVarchar;
import ch.ehi.sqlgen.repository.DbColXml;
import ch.ehi.sqlgen.repository.DbColumn;
import ch.ehi.sqlgen.repository.DbConstraint;
import ch.ehi.sqlgen.repository.DbEnumEle;
import ch.ehi.sqlgen.repository.DbIndex;
import ch.ehi.sqlgen.repository.DbSchema;
import ch.ehi.sqlgen.repository.DbTable;

/**
 * DDL generator of the ili2ofgdb flavour.
 *
 * <p>Extends {@link GeneratorJdbc} so that the offline script mode of ili2db works: the collected
 * statements ({@code iteratorCreateLines()}) are written to the script file by the core, while the
 * JDBC shim executes them in normal runs.
 */
public class GeneratorOfgdb extends GeneratorJdbc {
    public static final String OBJECTOID = "OBJECTID";
    public static final String XY_RESOLUTION = "ch.ehi.ilifgdb.xyResolution";
    public static final String XY_TOLERANCE = "ch.ehi.ilifgdb.xyTolerance";

    private int geometryColumnCount;

    @Override
    public void visitSchemaBegin(Settings config, DbSchema schema) throws IOException {
        super.visitSchemaBegin(config, schema);
        // domains are created by the mapping when a database is available; the offline DDL script
        // needs them as statements, so that it can be executed on an empty database
        Object stashed = config.getTransientObject(OfgdbMapping.DOMAIN_DDL_CUSTOM_KEY);
        if (!(stashed instanceof List)) {
            return;
        }
        for (Object element : (List<?>) stashed) {
            String stmt = String.valueOf(element);
            addCreateLine(new Stmt(stmt));
            if (conn != null) {
                executeSql(stmt);
            }
        }
    }

    @Override
    public void visit1TableBegin(DbTable tab) throws IOException {
        super.visit1TableBegin(tab);
        geometryColumnCount = 0;
    }

    @Override
    public void visit1TableEnd(DbTable tab) throws IOException {
        if (tab == null) {
            return;
        }
        if (geometryColumnCount > 1) {
            throw new IOException("OFGDB supports only one geometry column per table; table "
                    + tab.getName().getName()
                    + " has " + geometryColumnCount
                    + " geometry columns (enable oneGeomPerTable for OFGDB)");
        }
        // the base class records the DDL (script mode), executes it for new tables and deletes the
        // rows of existing tables when requested
        super.visit1TableEnd(tab);
        geometryColumnCount = 0;
    }

    @Override
    public void visitColumn(DbTable tab, DbColumn column) throws IOException {
        if (tab == null || column == null) {
            return;
        }
        if (column instanceof DbColGeometry) {
            geometryColumnCount++;
        }

        StringBuilder def = new StringBuilder();
        def.append(getIndent()).append(colSep).append(column.getName()).append(" ")
                .append(toSqlType(column));
        Object domain = column.getCustomValue(ch.ehi.ili2ofgdb.OfgdbMapping.DOMAIN_CUSTOM_KEY);
        if (domain != null) {
            def.append(" DOMAIN ").append(domain);
        }
        if (column instanceof DbColId && ((DbColId) column).isPrimaryKey()) {
            def.append(" PRIMARY KEY");
        } else if (column.isNotNull()) {
            def.append(" NOT NULL");
        }
        def.append(newline());
        out.write(def.toString());
        colSep = ",";
    }

    @Override
    protected String getTableEndOptions(DbTable tab) {
        return "";
    }

    @Override
    public void visitIndex(DbIndex index) throws IOException {
        // FileGDB has no attribute indexes
    }

    @Override
    public void visitConstraint(DbConstraint constraint) throws IOException {
        // FileGDB has no CHECK constraints
    }

    @Override
    public void visitEnumEle(DbEnumEle element) throws IOException {
    }

    @Override
    public void visitTableBeginColumn(DbTable tab) throws IOException {
    }

    @Override
    public void visitTableEndColumn(DbTable tab) throws IOException {
    }

    @Override
    public void visitTableBeginConstraint(DbTable tab) throws IOException {
    }

    @Override
    public void visitTableEndConstraint(DbTable tab) throws IOException {
    }

    @Override
    public void visitTableBeginIndex(DbTable tab) throws IOException {
    }

    @Override
    public void visitTableEndIndex(DbTable tab) throws IOException {
    }

    @Override
    public void visitTableBeginEnumEle(DbTable tab) throws IOException {
    }

    @Override
    public void visitTableEndEnumEle(DbTable tab) throws IOException {
    }

    private void executeSql(String sql) throws IOException {
        if (conn == null) {
            throw new IOException("no JDBC connection for <" + sql + ">");
        }
        EhiLogger.traceBackendCmd(sql);
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException ex) {
            throw new IOException("failed to execute DDL statement <" + sql + ">", ex);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    private String toSqlType(DbColumn column) {
        if (column.getArraySize() != DbColumn.NOT_AN_ARRAY) {
            // ARRAY_TRAFO_COALESCE values are serialized as JSON text.
            return "VARCHAR(4096)";
        }
        if (column instanceof DbColBoolean) {
            return "SMALLINT";
        }
        if (column instanceof DbColDate || column instanceof DbColTime || column instanceof DbColDateTime) {
            return "TIMESTAMP";
        }
        if (column instanceof DbColDecimal) {
            return "DOUBLE";
        }
        if (column instanceof DbColGeometry) {
            return toGeometrySqlType((DbColGeometry) column);
        }
        if (column instanceof DbColBlob) {
            return "BLOB";
        }
        if (column instanceof DbColId) {
            return "INTEGER";
        }
        if (column instanceof DbColNumber) {
            DbColNumber col = (DbColNumber) column;
            int size = col.getSize();
            if (size > 10) {
                return "BIGINT";
            }
            return "INTEGER";
        }
        if (column instanceof DbColUuid) {
            return "VARCHAR(36)";
        }
        if (column instanceof DbColVarchar) {
            int size = ((DbColVarchar) column).getSize();
            if (size == DbColVarchar.UNLIMITED || size <= 0) {
                size = 4096;
            }
            return "VARCHAR(" + size + ")";
        }
        if (column instanceof DbColXml) {
            return "VARCHAR(4096)";
        }
        return "VARCHAR(1024)";
    }

    private String toGeometrySqlType(DbColGeometry column) {
        String kind;
        int type = column.getType();
        if (type == DbColGeometry.POINT) {
            kind = "POINT";
        } else if (type == DbColGeometry.MULTIPOINT) {
            kind = "MULTIPOINT";
        } else if (type == DbColGeometry.LINESTRING) {
            kind = "LINE";
        } else if (type == DbColGeometry.CIRCULARSTRING) {
            kind = "CIRCULARSTRING";
        } else if (type == DbColGeometry.COMPOUNDCURVE) {
            kind = "COMPOUNDCURVE";
        } else if (type == DbColGeometry.MULTILINESTRING) {
            kind = "MULTILINE";
        } else if (type == DbColGeometry.MULTICURVE) {
            kind = "MULTICURVE";
        } else if (type == DbColGeometry.POLYGON
                || type == DbColGeometry.POLYHEDRALSURFACE || type == DbColGeometry.TIN
                || type == DbColGeometry.TRIANGLE) {
            kind = "POLYGON";
        } else if (type == DbColGeometry.CURVEPOLYGON) {
            kind = "CURVEPOLYGON";
        } else if (type == DbColGeometry.MULTIPOLYGON) {
            kind = "MULTIPOLYGON";
        } else if (type == DbColGeometry.MULTISURFACE) {
            kind = "MULTISURFACE";
        } else if (type == DbColGeometry.GEOMETRYCOLLECTION) {
            throw new IllegalArgumentException("Unsupported geometry type GEOMETRYCOLLECTION for OFGDB geometry column "
                    + column.getName());
        } else {
            throw new IllegalArgumentException("Unsupported geometry type " + type + " for OFGDB geometry column "
                    + column.getName());
        }

        int epsg = 0;
        Integer srsId = getSrsId(column.getSrsAuth(), column.getSrsId());
        if (srsId != null) {
            epsg = srsId.intValue();
        }

        int dim = column.getDimension();
        if (dim != 3) {
            dim = 2;
        }
        StringBuilder sqlType = new StringBuilder("OFGDB_GEOMETRY(");
        sqlType.append(kind).append(",").append(epsg).append(",").append(dim);
        Object geomIndex = column.getCustomValue(OfgdbMapping.GEOM_INDEX_CUSTOM_KEY);
        if (geomIndex != null && "true".equalsIgnoreCase(geomIndex.toString())) {
            sqlType.append(",INDEX");
        }
        String xyResolution = text(column.getCustomValue(XY_RESOLUTION));
        String xyTolerance = text(column.getCustomValue(XY_TOLERANCE));
        if (xyResolution != null || xyTolerance != null) {
            sqlType.append(",").append(xyResolution == null ? "" : xyResolution)
                    .append(",").append(xyTolerance == null ? "" : xyTolerance);
        }
        return sqlType.append(")").toString();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public static Integer getSrsId(String srsAuth, String srsId) {
        if (srsAuth == null || srsId == null) {
            return null;
        }
        if (!"EPSG".equalsIgnoreCase(srsAuth)) {
            return null;
        }
        try {
            return Integer.valueOf(srsId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
