package ch.ehi.ili2ofgdb.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class OfgdbPreparedStatement extends OfgdbStatement implements PreparedStatement {
    private final String sqlTemplate;
    private List<Object> params = new ArrayList<Object>();

    protected OfgdbPreparedStatement(OfgdbConnection conn, String sqlTemplate) {
        super(conn);
        this.sqlTemplate = sqlTemplate;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return super.executeQuery(materializeSql(sqlTemplate, params));
    }

    @Override
    public int executeUpdate() throws SQLException {
        return super.executeUpdate(materializeSql(sqlTemplate, params));
    }

    @Override
    public boolean execute() throws SQLException {
        return super.execute(materializeSql(sqlTemplate, params));
    }

    @Override
    public long executeLargeUpdate() throws SQLException {
        return executeUpdate();
    }

    @Override
    public void clearParameters() {
        params.clear();
    }

    @Override
    public void addBatch() throws SQLException {
        queueBatchSql(materializeSql(sqlTemplate, params));
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) {
        setParam(parameterIndex, null);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) {
        setParam(parameterIndex, Boolean.valueOf(x));
    }

    @Override
    public void setByte(int parameterIndex, byte x) {
        setParam(parameterIndex, Byte.valueOf(x));
    }

    @Override
    public void setShort(int parameterIndex, short x) {
        setParam(parameterIndex, Short.valueOf(x));
    }

    @Override
    public void setInt(int parameterIndex, int x) {
        setParam(parameterIndex, Integer.valueOf(x));
    }

    @Override
    public void setLong(int parameterIndex, long x) {
        setParam(parameterIndex, Long.valueOf(x));
    }

    @Override
    public void setFloat(int parameterIndex, float x) {
        setParam(parameterIndex, Float.valueOf(x));
    }

    @Override
    public void setDouble(int parameterIndex, double x) {
        setParam(parameterIndex, Double.valueOf(x));
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setString(int parameterIndex, String x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x, SQLType targetSqlType) {
        setParam(parameterIndex, x);
    }

    @Override
    public ResultSetMetaData getMetaData() {
        return null;
    }

    @Override
    public ParameterMetaData getParameterMetaData() {
        return null;
    }

    @Override
    public void setArray(int parameterIndex, Array x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) {
        setParam(parameterIndex, inputStream);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) {
        setParam(parameterIndex, inputStream);
    }

    @Override
    public void setClob(int parameterIndex, Clob x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) {
        setParam(parameterIndex, reader);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) {
        setParam(parameterIndex, reader);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) {
        setParam(parameterIndex, value);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) {
        setParam(parameterIndex, reader);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) {
        setParam(parameterIndex, reader);
    }

    @Override
    public void setRef(int parameterIndex, Ref x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) {
        setParam(parameterIndex, xmlObject);
    }

    @Override
    public void setURL(int parameterIndex, URL x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setNString(int parameterIndex, String value) {
        setParam(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) {
        setParam(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) {
        setParam(parameterIndex, value);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) {
        setParam(parameterIndex, reader);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) {
        setParam(parameterIndex, reader);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) {
        setParam(parameterIndex, reader);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) {
        setParam(parameterIndex, x);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) {
        setParam(parameterIndex, null);
    }

    private void setParam(int parameterIndex, Object value) {
        int idx = parameterIndex - 1;
        while (params.size() <= idx) {
            params.add(null);
        }
        params.set(idx, value);
    }
}
