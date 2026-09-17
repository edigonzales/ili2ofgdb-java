package ch.ehi.ili2ofgdb.jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OfgdbResultSet extends AbstractResultSet {
    private static final String BYTE_LITERAL_PREFIX = "__OFGDB_BYTES_B64__:";
    private final List<Map<String, Object>> rows;
    private final List<String> columns;
    private int rowIndex = -1;
    private boolean closed = false;
    private boolean lastGetWasNull = false;

    public OfgdbResultSet(List<Map<String, Object>> rows, List<String> columns) {
        this.rows = rows != null ? rows : Collections.<Map<String, Object>>emptyList();
        this.columns = columns != null ? columns : Collections.<String>emptyList();
    }

    @Override
    public void close() throws SQLException {
        closed = true;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public boolean next() throws SQLException {
        ensureOpen();
        int next = rowIndex + 1;
        if (next >= rows.size()) {
            return false;
        }
        rowIndex = next;
        return true;
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equalsIgnoreCase(columnLabel)) {
                return i + 1;
            }
        }
        throw new SQLException("unknown column " + columnLabel);
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        ensureOpen();
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new SQLException("result set cursor is not positioned on a row");
        }
        if (columnIndex <= 0 || columnIndex > columns.size()) {
            throw new SQLException("invalid column index " + columnIndex);
        }
        return getObject(columns.get(columnIndex - 1));
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        ensureOpen();
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new SQLException("result set cursor is not positioned on a row");
        }
        Map<String, Object> row = rows.get(rowIndex);
        Object value = row.get(columnLabel);
        if (value == null && !row.containsKey(columnLabel)) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(columnLabel)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        if (value == null) {
            lastGetWasNull = true;
            return null;
        }
        lastGetWasNull = false;
        return value;
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        Object value = getObject(columnIndex);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        Object value = getObject(columnIndex);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        Object value = getObject(columnIndex);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        String text = value.toString();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        Object value = getObject(columnIndex);
        return value != null ? value.toString() : null;
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        Object value = getObject(columnLabel);
        return value != null ? value.toString() : null;
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        Object value = getObject(columnIndex);
        return asBytes(value, "column " + columnIndex);
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        Object value = getObject(columnLabel);
        return asBytes(value, "column " + columnLabel);
    }

    @Override
    public boolean wasNull() throws SQLException {
        return lastGetWasNull;
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        javax.sql.rowset.RowSetMetaDataImpl ret = new javax.sql.rowset.RowSetMetaDataImpl();
        ret.setColumnCount(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            ret.setColumnName(i + 1, columns.get(i));
            ret.setColumnType(i + 1, java.sql.Types.VARCHAR);
        }
        return ret;
    }

    private void ensureOpen() throws SQLException {
        if (closed) {
            throw new SQLException("result set is closed");
        }
    }

    private static byte[] asBytes(Object value, String column) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof String) {
            String text = (String) value;
            if (text.startsWith(BYTE_LITERAL_PREFIX)) {
                String b64 = text.substring(BYTE_LITERAL_PREFIX.length());
                try {
                    return java.util.Base64.getDecoder().decode(b64);
                } catch (IllegalArgumentException e) {
                    throw new SQLException("invalid binary literal in " + column, e);
                }
            }
        }
        throw new SQLException("expected binary value in " + column + " but got " + value.getClass().getName());
    }
}
