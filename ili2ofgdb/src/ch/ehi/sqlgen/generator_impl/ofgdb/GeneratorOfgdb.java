package ch.ehi.sqlgen.generator_impl.ofgdb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.settings.Settings;
import ch.ehi.sqlgen.generator.Generator;
import ch.ehi.sqlgen.generator.SqlConfiguration;
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

public class GeneratorOfgdb implements Generator {
    public static final String OBJECTOID = "OBJECTID";
    public static final String XY_RESOLUTION = "ch.ehi.ilifgdb.xyResolution";
    public static final String XY_TOLERANCE = "ch.ehi.ilifgdb.xyTolerance";

    private Connection conn;
    private Statement ddlStmt;
    private DbTable currentTable;
    private List<String> columnDefs;
    private int geometryColumnCount;

    @Override
    public void visit1Begin() throws IOException {
    }

    @Override
    public void visit1End() throws IOException {
    }

    @Override
    public void visit1TableBegin(DbTable tab) throws IOException {
        currentTable = tab;
        columnDefs = new ArrayList<String>();
        geometryColumnCount = 0;
    }

    @Override
    public void visit1TableEnd(DbTable tab) throws IOException {
        if (tab == null || columnDefs == null) {
            return;
        }
        if (geometryColumnCount > 1) {
            throw new IOException("OFGDB supports only one geometry column per table; table "
                    + tab.getName().getName()
                    + " has " + geometryColumnCount
                    + " geometry columns (enable oneGeomPerTable for OFGDB)");
        }
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(tab.getName().getName()).append(" (");
        String sep = "";
        for (String colDef : columnDefs) {
            sql.append(sep).append(colDef);
            sep = ", ";
        }
        sql.append(")");
        execSql(sql.toString(), true);

        if (tab.isDeleteDataIfTableExists()) {
            execSql("DELETE FROM " + tab.getName().getName(), false);
        }
        currentTable = null;
        columnDefs = null;
        geometryColumnCount = 0;
    }

    @Override
    public void visit2Begin() throws IOException {
    }

    @Override
    public void visit2End() throws IOException {
    }

    @Override
    public void visit2TableBegin(DbTable arg0) throws IOException {
    }

    @Override
    public void visit2TableEnd(DbTable arg0) throws IOException {
    }

    @Override
    public void visitColumn(DbTable tab, DbColumn column) throws IOException {
        if (columnDefs == null || column == null) {
            return;
        }
        if (column instanceof DbColGeometry) {
            geometryColumnCount++;
        }

        StringBuilder def = new StringBuilder();
        def.append(column.getName()).append(" ").append(toSqlType(column));

        if (column instanceof DbColId && ((DbColId) column).isPrimaryKey()) {
            def.append(" PRIMARY KEY");
        }
        if (column.isNotNull()) {
            def.append(" NOT NULL");
        }
        columnDefs.add(def.toString());
    }

    @Override
    public void visitConstraint(DbConstraint arg0) throws IOException {
    }

    @Override
    public void visitEnumEle(DbEnumEle arg0) throws IOException {
    }

    @Override
    public void visitIndex(DbIndex arg0) throws IOException {
    }

    @Override
    public void visitSchemaBegin(Settings config, DbSchema arg1) throws IOException {
        conn = (Connection) config.getTransientObject(SqlConfiguration.JDBC_CONNECTION);
        if (conn == null) {
            throw new IllegalArgumentException("config.getConnection()==null");
        }
        try {
            ddlStmt = conn.createStatement();
        } catch (SQLException e) {
            throw new IOException("failed to initialize DDL statement", e);
        }
    }

    @Override
    public void visitSchemaEnd(DbSchema arg0) throws IOException {
        if (ddlStmt != null) {
            try {
                ddlStmt.close();
            } catch (SQLException e) {
                throw new IOException("failed to close DDL statement", e);
            } finally {
                ddlStmt = null;
            }
        }
    }

    @Override
    public void visitTableBeginColumn(DbTable arg0) throws IOException {
    }

    @Override
    public void visitTableBeginConstraint(DbTable arg0) throws IOException {
    }

    @Override
    public void visitTableBeginEnumEle(DbTable arg0) throws IOException {
    }

    @Override
    public void visitTableBeginIndex(DbTable arg0) throws IOException {
    }

    @Override
    public void visitTableEndColumn(DbTable arg0) throws IOException {
    }

    @Override
    public void visitTableEndConstraint(DbTable arg0) throws IOException {
    }

    @Override
    public void visitTableEndEnumEle(DbTable arg0) throws IOException {
    }

    @Override
    public void visitTableEndIndex(DbTable arg0) throws IOException {
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
        return "OFGDB_GEOMETRY(" + kind + "," + epsg + "," + dim + ")";
    }

    private void execSql(String sql, boolean ignoreIfExists) throws IOException {
        if (ddlStmt == null) {
            throw new IOException("DDL statement is not initialized");
        }
        EhiLogger.traceBackendCmd(sql);
        try {
            ddlStmt.executeUpdate(sql);
        } catch (SQLException e) {
            if (ignoreIfExists && isAlreadyExistsError(e)) {
                EhiLogger.logAdaption("ili2ofgdb: ignored DDL error for statement <" + sql + ">: " + e.getMessage());
                return;
            }
            throw new IOException("failed to execute DDL statement <" + sql + ">", e);
        }
    }

    private boolean isAlreadyExistsError(SQLException e) {
        String sqlState = e.getSQLState();
        if ("X0Y32".equals(sqlState)) {
            return true;
        }
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("already exists")
                || lower.contains("exists already")
                || lower.contains("already present");
    }
}
