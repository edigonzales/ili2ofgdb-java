package ch.ehi.ili2ofgdb.jdbc;

import java.sql.Connection;
import java.sql.BatchUpdateException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import ch.ehi.openfgdb4j.OpenFgdb;
import ch.ehi.openfgdb4j.OpenFgdbException;
import ch.ehi.ili2ofgdb.jdbc.parser.SqlLexer;
import ch.ehi.ili2ofgdb.jdbc.parser.SqlSyntax;
import ch.ehi.ili2ofgdb.jdbc.sql.AbstractSelectStmt;
import ch.ehi.ili2ofgdb.jdbc.sql.ColRef;
import ch.ehi.ili2ofgdb.jdbc.sql.ComplexSelectStmt;
import ch.ehi.ili2ofgdb.jdbc.sql.OfgdbSelectStmt;
import ch.ehi.ili2ofgdb.jdbc.sql.IntConst;
import ch.ehi.ili2ofgdb.jdbc.sql.IsNull;
import ch.ehi.ili2ofgdb.jdbc.sql.JoinStmt;
import ch.ehi.ili2ofgdb.jdbc.sql.SelectValue;
import ch.ehi.ili2ofgdb.jdbc.sql.SelectValueField;
import ch.ehi.ili2ofgdb.jdbc.sql.SelectValueNull;
import ch.ehi.ili2ofgdb.jdbc.sql.SelectValueString;
import ch.ehi.ili2ofgdb.jdbc.sql.SqlStmt;
import ch.ehi.ili2ofgdb.jdbc.sql.StringConst;
import ch.ehi.ili2ofgdb.jdbc.sql.Value;

public class OfgdbStatement implements Statement {
    private static final String BYTE_LITERAL_PREFIX = "__OFGDB_BYTES_B64__:";
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "(?is)^\\s*SELECT\\s+(.+?)\\s+FROM\\s+((?:\"[^\"]+\"|[A-Za-z0-9_.$]+))(?:\\s+WHERE\\s+(.+?))?(?:\\s+ORDER\\s+BY\\s+(.+?))?\\s*$");
    private static final Pattern COUNT_ONLY_PATTERN = Pattern.compile(
            "(?is)^COUNT\\s*\\(\\s*(\\*|1|\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s*$");
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\s+([A-Za-z0-9_.$\"]+)\\s*\\(.*$");
    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile("(?is)^\\s*DROP\\s+TABLE\\s+([A-Za-z0-9_.$\"]+)\\s*$");
    private static final Pattern SIMPLE_IDENTIFIER_PATTERN = Pattern.compile("(?i)^\"?[A-Za-z_][A-Za-z0-9_$]*\"?(?:\\.\"?[A-Za-z_][A-Za-z0-9_$]*\"?)*$");
    private static final Pattern QUALIFIED_IDENTIFIER_PATTERN = Pattern.compile(
            "(?:\"([^\"]+)\"|([A-Za-z_][A-Za-z0-9_$]*))\\.(?:\"([^\"]+)\"|([A-Za-z_][A-Za-z0-9_$]*))");
    private static final Pattern UNION_DERIVED_SELECT_PATTERN = Pattern.compile(
            "(?is)^\\s*SELECT\\s+(.+?)\\s+FROM\\s*\\((.+)\\)\\s+(?:\"?[A-Za-z_][A-Za-z0-9_$]*\"?)\\s*(?:WHERE\\s+(.+?))?\\s*$");

    private final OfgdbConnection conn;
    private boolean closed = false;
    private ResultSet currentResultSet = null;
    private int updateCount = -1;
    private final List<String> batchedSql = new ArrayList<String>();

    protected OfgdbStatement(OfgdbConnection conn) {
        this.conn = conn;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        ensureOpen();
        ResultSet rs = executeSelectSql(normalizeSelectSql(sql));
        currentResultSet = rs;
        updateCount = -1;
        return rs;
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        ensureOpen();
        String normalizedSql = normalizeSelectSql(sql);
        if (normalizedSql == null || normalizedSql.isEmpty()) {
            throw new SQLException("empty SQL statement");
        }
        try {
            conn.getApi().execSql(conn.getDbHandle(), normalizedSql);
            trackSchemaMutation(normalizedSql);
            if (currentResultSet != null) {
                currentResultSet.close();
                currentResultSet = null;
            }
            updateCount = 0;
            return updateCount;
        } catch (OpenFgdbException e) {
            throw new SQLException("failed to execute update <" + normalizedSql + ">", e);
        }
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        ensureOpen();
        String normalizedSql = normalizeSelectSql(sql);
        if (normalizedSql == null || normalizedSql.isEmpty()) {
            throw new SQLException("empty SQL statement");
        }
        String upper = normalizedSql != null ? normalizedSql.toUpperCase(Locale.ROOT) : "";
        if (upper.startsWith("SELECT ")) {
            executeQuery(normalizedSql);
            return true;
        }
        executeUpdate(normalizedSql);
        return false;
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
        batchedSql.clear();
        closed = true;
    }

    @Override
    public Connection getConnection() throws SQLException {
        ensureOpen();
        return conn;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        ensureOpen();
        return currentResultSet;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        ensureOpen();
        return updateCount;
    }

    @Override
    public int getResultSetType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void setFetchSize(int rows) {
    }

    @Override
    public int getFetchSize() {
        return 0;
    }

    @Override
    public void setFetchDirection(int direction) {
    }

    @Override
    public int getFetchDirection() {
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setMaxRows(int max) {
    }

    @Override
    public int getMaxRows() {
        return 0;
    }

    @Override
    public void setMaxFieldSize(int max) {
    }

    @Override
    public int getMaxFieldSize() {
        return 0;
    }

    @Override
    public void setEscapeProcessing(boolean enable) {
    }

    @Override
    public int getQueryTimeout() {
        return 0;
    }

    @Override
    public void setQueryTimeout(int seconds) {
    }

    @Override
    public void cancel() {
    }

    @Override
    public SQLWarning getWarnings() {
        return null;
    }

    @Override
    public void clearWarnings() {
    }

    @Override
    public void setCursorName(String name) {
    }

    @Override
    public boolean getMoreResults() {
        return false;
    }

    @Override
    public boolean getMoreResults(int current) {
        return false;
    }

    @Override
    public ResultSet getGeneratedKeys() {
        return new OfgdbResultSet(new ArrayList<Map<String, Object>>(), new ArrayList<String>());
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return execute(sql);
    }

    @Override
    public int[] executeBatch() throws SQLException {
        ensureOpen();
        if (batchedSql.isEmpty()) {
            return new int[0];
        }
        List<String> pending = new ArrayList<String>(batchedSql);
        batchedSql.clear();
        int[] updateCounts = new int[pending.size()];
        int successful = 0;
        for (int i = 0; i < pending.size(); i++) {
            try {
                executeUpdate(pending.get(i));
                updateCounts[i] = Statement.SUCCESS_NO_INFO;
                successful = i + 1;
            } catch (SQLException e) {
                int[] partial = new int[successful];
                System.arraycopy(updateCounts, 0, partial, 0, successful);
                throw new BatchUpdateException(
                        "batch execution failed at statement " + (i + 1) + ": " + e.getMessage(),
                        e.getSQLState(),
                        e.getErrorCode(),
                        partial,
                        e);
            }
        }
        updateCount = -1;
        return updateCounts;
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        queueBatchSql(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        ensureOpen();
        batchedSql.clear();
    }

    @Override
    public void setPoolable(boolean poolable) {
    }

    @Override
    public boolean isPoolable() {
        return false;
    }

    @Override
    public void closeOnCompletion() {
    }

    @Override
    public boolean isCloseOnCompletion() {
        return false;
    }

    @Override
    public void setLargeMaxRows(long max) {
    }

    @Override
    public long getLargeMaxRows() {
        return 0;
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        int[] updateCounts = executeBatch();
        long[] out = new long[updateCounts.length];
        for (int i = 0; i < updateCounts.length; i++) {
            out[i] = updateCounts[i];
        }
        return out;
    }

    @Override
    public long executeLargeUpdate(String sql) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }

    protected String materializeSql(String sqlTemplate, List<Object> parameters) {
        if (sqlTemplate == null || parameters == null || parameters.isEmpty()) {
            return sqlTemplate;
        }
        StringBuilder out = new StringBuilder();
        int idx = 0;
        for (int i = 0; i < sqlTemplate.length(); i++) {
            char c = sqlTemplate.charAt(i);
            if (c == '?' && idx < parameters.size()) {
                out.append(encodeLiteral(parameters.get(idx++)));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    protected void queueBatchSql(String sql) throws SQLException {
        ensureOpen();
        String normalizedSql = normalizeSelectSql(sql);
        if (normalizedSql == null || normalizedSql.isEmpty()) {
            throw new SQLException("empty SQL statement");
        }
        batchedSql.add(normalizedSql);
    }

    private ResultSet executeSelectSql(String sql) throws SQLException {
        String normalizedSql = normalizeSelectSql(sql);
        normalizedSql = rewritePlainJoinToLeftJoin(normalizedSql);
        if (looksLikeUnionDerivedSelect(normalizedSql)) {
            return executeUnionDerivedSelect(normalizedSql);
        }
        QueryPlan plan = null;
        try {
            plan = parseSelect(normalizedSql);
        } catch (SQLException parseFailed) {
            AbstractSelectStmt stmt = parseSelectStatement(normalizedSql);
            return executeSelectStmt(stmt);
        }
        return executeSimpleSelect(plan);
    }

    private ResultSet executeUnionDerivedSelect(String sql) throws SQLException {
        UnionDerivedSelectSpec spec = parseUnionDerivedSelect(sql);
        List<String> baseColumns = new ArrayList<String>();
        List<Map<String, Object>> mergedRows = new ArrayList<Map<String, Object>>();
        java.util.Set<String> dedup = new java.util.LinkedHashSet<String>();
        for (String unionPart : splitTopLevelUnionParts(spec.unionBody)) {
            ResultSet rs = null;
            try {
                rs = executeSelectSql(unionPart);
                ResultSetMetaData md = rs.getMetaData();
                if (baseColumns.isEmpty()) {
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        baseColumns.add(normalizeColumn(md.getColumnName(i)));
                    }
                }
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        row.put(normalizeColumn(md.getColumnName(i)), rs.getObject(i));
                    }
                    String key = buildRowKey(row, baseColumns);
                    if (dedup.add(key)) {
                        mergedRows.add(row);
                    }
                }
            } finally {
                if (rs != null) {
                    rs.close();
                }
            }
        }
        List<String> projectionColumns = spec.projectionColumns;
        if (projectionColumns.isEmpty() || (projectionColumns.size() == 1 && "*".equals(projectionColumns.get(0)))) {
            projectionColumns = new ArrayList<String>(baseColumns);
        }
        List<Map<String, Object>> outRows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : mergedRows) {
            if (!matchesSimpleWhere(row, spec.whereClause)) {
                continue;
            }
            Map<String, Object> out = new HashMap<String, Object>();
            for (String column : projectionColumns) {
                out.put(column, getIgnoreCase(row, column));
            }
            outRows.add(out);
        }
        return new OfgdbResultSet(outRows, projectionColumns);
    }

    private static UnionDerivedSelectSpec parseUnionDerivedSelect(String sql) throws SQLException {
        Matcher matcher = UNION_DERIVED_SELECT_PATTERN.matcher(sql);
        if (!matcher.matches()) {
            throw new SQLException("unsupported UNION SELECT statement");
        }
        UnionDerivedSelectSpec spec = new UnionDerivedSelectSpec();
        String fieldSpec = matcher.group(1) != null ? matcher.group(1).trim() : "*";
        if (fieldSpec.isEmpty()) {
            fieldSpec = "*";
        }
        if ("*".equals(fieldSpec)) {
            spec.projectionColumns.add("*");
        } else {
            for (String col : splitColumns(fieldSpec)) {
                spec.projectionColumns.add(normalizeColumn(col));
            }
        }
        spec.unionBody = matcher.group(2) != null ? matcher.group(2).trim() : "";
        spec.whereClause = matcher.group(3) != null ? normalizeWhereClause(matcher.group(3).trim()) : "";
        return spec;
    }

    private static List<String> splitTopLevelUnionParts(String unionBody) throws SQLException {
        List<String> parts = new ArrayList<String>();
        if (unionBody == null || unionBody.trim().isEmpty()) {
            throw new SQLException("unsupported UNION SELECT statement");
        }
        String upper = unionBody.toUpperCase(Locale.ROOT);
        int depth = 0;
        int partStart = 0;
        int i = 0;
        while (i < unionBody.length()) {
            char c = unionBody.charAt(i);
            if (c == '(') {
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                if (depth > 0) {
                    depth--;
                }
                i++;
                continue;
            }
            if (depth == 0 && upper.startsWith(" UNION ", i)) {
                String part = unionBody.substring(partStart, i).trim();
                if (!part.isEmpty()) {
                    parts.add(part);
                }
                i += " UNION ".length();
                partStart = i;
                continue;
            }
            i++;
        }
        String tail = unionBody.substring(partStart).trim();
        if (!tail.isEmpty()) {
            parts.add(tail);
        }
        if (parts.isEmpty()) {
            throw new SQLException("unsupported UNION SELECT statement");
        }
        return parts;
    }

    private static String buildRowKey(Map<String, Object> row, List<String> columns) {
        StringBuilder key = new StringBuilder();
        String sep = "";
        for (String col : columns) {
            Object value = getIgnoreCase(row, col);
            key.append(sep).append(col.toLowerCase(Locale.ROOT)).append('=').append(value != null ? value.toString() : "<null>");
            sep = "|";
        }
        return key.toString();
    }

    private static boolean matchesSimpleWhere(Map<String, Object> row, String whereClause) throws SQLException {
        if (whereClause == null || whereClause.trim().isEmpty()) {
            return true;
        }
        String[] conditions = whereClause.split("(?i)\\s+AND\\s+");
        for (String conditionRaw : conditions) {
            String condition = conditionRaw != null ? conditionRaw.trim() : "";
            if (condition.isEmpty()) {
                continue;
            }
            int eqPos = condition.indexOf('=');
            if (eqPos <= 0) {
                throw new SQLException("unsupported WHERE condition in UNION SELECT: " + condition);
            }
            String left = normalizeColumn(condition.substring(0, eqPos).trim());
            String right = condition.substring(eqPos + 1).trim();
            Object expected = parseSqlLiteral(right);
            Object actual = getIgnoreCase(row, left);
            if (actual == null && expected == null) {
                continue;
            }
            if (actual == null || expected == null) {
                return false;
            }
            if (!actual.toString().equals(expected.toString())) {
                return false;
            }
        }
        return true;
    }

    private static Object parseSqlLiteral(String literalText) {
        if (literalText == null) {
            return null;
        }
        String text = literalText.trim();
        if (text.equalsIgnoreCase("NULL")) {
            return null;
        }
        if (text.startsWith("'") && text.endsWith("'") && text.length() >= 2) {
            return text.substring(1, text.length() - 1).replace("''", "'");
        }
        return parseValue(text);
    }

    private AbstractSelectStmt parseSelectStatement(String sql) throws SQLException {
        SqlLexer lexer = new SqlLexer(new java.io.StringReader(sql));
        SqlSyntax parser = new SqlSyntax(lexer);
        final SqlStmt stmt;
        try {
            stmt = parser.statement();
        } catch (RecognitionException e) {
            throw new SQLException("failed to parse SELECT statement", e);
        } catch (TokenStreamException e) {
            throw new SQLException("failed to parse SELECT statement", e);
        }
        if (!(stmt instanceof AbstractSelectStmt)) {
            throw new SQLException("Only SELECT statements are supported");
        }
        return (AbstractSelectStmt) stmt;
    }

    private ResultSet executeSelectStmt(AbstractSelectStmt stmt) throws SQLException {
        if (stmt instanceof JoinStmt) {
            return executeJoinSelectStmt((JoinStmt) stmt);
        }
        if (stmt instanceof ComplexSelectStmt) {
            return executeComplexSelectStmt((ComplexSelectStmt) stmt);
        }
        if (stmt instanceof OfgdbSelectStmt) {
            return executeOfgdbSelectStmt((OfgdbSelectStmt) stmt);
        }
        throw new SQLException("Unsupported SELECT statement type " + stmt.getClass().getName());
    }

    private ResultSet executeComplexSelectStmt(ComplexSelectStmt stmt) throws SQLException {
        ResultSet subResult = executeSelectStmt(stmt.getSubSelect());
        return new MemResultSet(subResult, stmt.getConditions(), null);
    }

    private ResultSet executeJoinSelectStmt(JoinStmt stmt) throws SQLException {
        ResultSet left = null;
        List<ResultSet> right = new ArrayList<ResultSet>();
        try {
            left = executeSelectStmt(stmt.getLeftStmt());
            for (AbstractSelectStmt rightStmt : stmt.getRightStmt()) {
                right.add(executeSelectStmt(rightStmt));
            }
            return new JoinResultSet(left, right, stmt);
        } catch (SQLException e) {
            if (left != null) {
                try {
                    left.close();
                } catch (SQLException ignore) {
                }
            }
            for (ResultSet rs : right) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            throw e;
        }
    }

    private ResultSet executeOfgdbSelectStmt(OfgdbSelectStmt stmt) throws SQLException {
        List<String> projectedColumns = new ArrayList<String>();
        for (SelectValue field : stmt.getFields()) {
            projectedColumns.add(field.getColumnName());
        }
        return executeSearch(stmt.getTableName(), "*", stmt.getConditions(), projectedColumns, stmt.getFields());
    }

    private ResultSet executeSimpleSelect(QueryPlan plan) throws SQLException {
        if (isCountQuery(plan)) {
            return executeCountQuery(plan);
        }
        List<SelectValue> projection = new ArrayList<SelectValue>();
        if (!"*".equals(plan.fieldSpec)) {
            for (String column : plan.columns) {
                projection.add(new SelectValueField(new ch.ehi.ili2ofgdb.jdbc.sql.SqlQname(java.util.Arrays.asList(column))));
            }
        }
        return executeSearch(
                plan.tableName,
                plan.fieldSpec,
                plan.whereClause,
                plan.orderByClause,
                plan.columns,
                projection.isEmpty() ? null : projection);
    }

    private ResultSet executeSearch(
            String tableName,
            String fieldSpec,
            String whereClause,
            String orderByClause,
            List<String> requestedColumns,
            List<SelectValue> projection) throws SQLException {
        OpenFgdb api = conn.getApi();
        long tableHandle = 0L;
        long cursorHandle = 0L;
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        List<String> columns = new ArrayList<String>();
        try {
            String resolvedTableName = conn.resolveTableName(tableName);
            try {
                tableHandle = api.openTable(conn.getDbHandle(), resolvedTableName);
            } catch (OpenFgdbException e) {
                if (!isTableNotFound(e)) {
                    throw e;
                }
                conn.reopenSession();
                resolvedTableName = conn.resolveTableName(tableName);
                tableHandle = api.openTable(conn.getDbHandle(), resolvedTableName);
            }
            List<String> tableColumns = api.getFieldNames(tableHandle);
            String effectiveFieldSpec = fieldSpec;
            List<String> fetchColumns = new ArrayList<String>();
            if ("*".equals(fieldSpec)) {
                fetchColumns.addAll(tableColumns);
                effectiveFieldSpec = joinColumns(fetchColumns);
                if (requestedColumns != null && !requestedColumns.isEmpty()) {
                    columns.addAll(requestedColumns);
                    for (String fetchColumn : fetchColumns) {
                        if (!containsIgnoreCase(columns, fetchColumn)) {
                            columns.add(fetchColumn);
                        }
                    }
                } else {
                    columns.addAll(fetchColumns);
                }
            } else {
                fetchColumns.addAll(canonicalizeColumns(splitColumns(fieldSpec), tableColumns));
                effectiveFieldSpec = joinColumns(fetchColumns);
                if (requestedColumns != null && !requestedColumns.isEmpty()) {
                    columns.addAll(requestedColumns);
                } else {
                    columns.addAll(fetchColumns);
                }
            }

            String where = whereClause;
            if (projection != null && whereClause == null) {
                where = buildWhereClause(null);
            }
            cursorHandle = api.search(tableHandle, effectiveFieldSpec, where);
            while (true) {
                long rowHandle = api.fetchRow(cursorHandle);
                if (rowHandle == 0L) {
                    break;
                }
                try {
                    Map<String, Object> baseRow = new HashMap<String, Object>();
                    for (String column : fetchColumns) {
                        baseRow.put(column, readRowValue(api, rowHandle, column));
                    }
                    Map<String, Object> outRow = new HashMap<String, Object>();
                    if (projection == null || projection.isEmpty()) {
                        for (String column : columns) {
                            outRow.put(column, getIgnoreCase(baseRow, column));
                        }
                    } else {
                        for (SelectValue selectValue : projection) {
                            outRow.put(selectValue.getColumnName(), evaluateProjection(baseRow, selectValue));
                        }
                        for (String fetchColumn : fetchColumns) {
                            if (!containsIgnoreCaseKey(outRow, fetchColumn)) {
                                outRow.put(fetchColumn, getIgnoreCase(baseRow, fetchColumn));
                            }
                        }
                    }
                    rows.add(outRow);
                } finally {
                    api.closeRow(rowHandle);
                }
            }
            applyOrderBy(rows, columns, orderByClause);
            return new OfgdbResultSet(rows, columns);
        } catch (OpenFgdbException e) {
            throw new SQLException("failed to execute query", e);
        } finally {
            if (cursorHandle != 0L) {
                try {
                    api.closeCursor(cursorHandle);
                } catch (OpenFgdbException ignore) {
                }
            }
            if (tableHandle != 0L) {
                try {
                    api.closeTable(conn.getDbHandle(), tableHandle);
                } catch (OpenFgdbException ignore) {
                }
            }
        }
    }

    private ResultSet executeCountQuery(QueryPlan plan) throws SQLException {
        OpenFgdb api = conn.getApi();
        long tableHandle = 0L;
        long cursorHandle = 0L;
        long count = 0L;
        try {
            String resolvedTableName = conn.resolveTableName(plan.tableName);
            try {
                tableHandle = api.openTable(conn.getDbHandle(), resolvedTableName);
            } catch (OpenFgdbException e) {
                if (!isTableNotFound(e)) {
                    throw e;
                }
                conn.reopenSession();
                resolvedTableName = conn.resolveTableName(plan.tableName);
                tableHandle = api.openTable(conn.getDbHandle(), resolvedTableName);
            }
            String where = plan.whereClause != null ? plan.whereClause : "";
            cursorHandle = api.search(tableHandle, "*", where);
            while (true) {
                long rowHandle = api.fetchRow(cursorHandle);
                if (rowHandle == 0L) {
                    break;
                }
                count++;
                api.closeRow(rowHandle);
            }
        } catch (OpenFgdbException e) {
            throw new SQLException("failed to execute count query", e);
        } finally {
            if (cursorHandle != 0L) {
                try {
                    api.closeCursor(cursorHandle);
                } catch (OpenFgdbException ignore) {
                }
            }
            if (tableHandle != 0L) {
                try {
                    api.closeTable(conn.getDbHandle(), tableHandle);
                } catch (OpenFgdbException ignore) {
                }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>(1);
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("count", Long.valueOf(count));
        rows.add(row);
        return new OfgdbResultSet(rows, java.util.Arrays.asList("count"));
    }

    private ResultSet executeSearch(
            String tableName,
            String fieldSpec,
            List<java.util.Map.Entry<Value, Value>> conditions,
            List<String> requestedColumns,
            List<SelectValue> projection) throws SQLException {
        return executeSearch(tableName, fieldSpec, buildWhereClause(conditions), null, requestedColumns, projection);
    }

    private static List<String> splitColumns(String fieldSpec) {
        List<String> columns = new ArrayList<String>();
        if (fieldSpec == null || fieldSpec.trim().isEmpty()) {
            return columns;
        }
        String[] parts = fieldSpec.split(",");
        for (String part : parts) {
            String name = normalizeColumn(part);
            if (!name.isEmpty()) {
                columns.add(name);
            }
        }
        return columns;
    }

    private static List<String> canonicalizeColumns(List<String> requestedColumns, List<String> availableColumns) throws SQLException {
        List<String> resolved = new ArrayList<String>();
        for (String requested : requestedColumns) {
            String canonical = canonicalizeColumnName(requested, availableColumns);
            if (canonical == null || canonical.isEmpty()) {
                continue;
            }
            if (!containsIgnoreCase(resolved, canonical)) {
                resolved.add(canonical);
            }
        }
        return resolved;
    }

    private static String canonicalizeColumnName(String requested, List<String> availableColumns) throws SQLException {
        if (requested == null) {
            return null;
        }
        String normalized = requested.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        if (availableColumns == null || availableColumns.isEmpty()) {
            return normalized;
        }
        if (!isSimpleColumnReference(normalized)) {
            return normalized;
        }
        String probe = normalizeColumn(normalized);
        for (String available : availableColumns) {
            if (available.equals(probe)) {
                return available;
            }
        }
        for (String available : availableColumns) {
            if (available.equalsIgnoreCase(probe)) {
                return available;
            }
        }
        throw new SQLException("unknown column " + probe);
    }

    private static String buildWhereClause(List<java.util.Map.Entry<Value, Value>> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        String sep = "";
        for (java.util.Map.Entry<Value, Value> cond : conditions) {
            if (!(cond.getKey() instanceof ColRef)) {
                continue;
            }
            String colName = ((ColRef) cond.getKey()).getName();
            Value value = cond.getValue();
            out.append(sep).append(colName);
            if (value instanceof IsNull) {
                out.append(" IS NULL");
            } else if (value instanceof IntConst) {
                out.append("=").append(((IntConst) value).getValue());
            } else if (value instanceof StringConst) {
                out.append("='").append(((StringConst) value).getValue().replace("'", "''")).append("'");
            } else {
                return "";
            }
            sep = " AND ";
        }
        return out.toString();
    }

    private static Object evaluateProjection(Map<String, Object> row, SelectValue selectValue) {
        if (selectValue instanceof SelectValueNull) {
            return null;
        }
        if (selectValue instanceof SelectValueString) {
            return ((SelectValueString) selectValue).getLiteralValue();
        }
        if (selectValue instanceof SelectValueField) {
            return getIgnoreCase(row, selectValue.getColumnName());
        }
        return getIgnoreCase(row, selectValue.getColumnName());
    }

    private static Object getIgnoreCase(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null || row.containsKey(key)) {
            return value;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(List<String> values, String probe) {
        for (String value : values) {
            if (value.equalsIgnoreCase(probe)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCaseKey(Map<String, Object> values, String probe) {
        for (String key : values.keySet()) {
            if (key.equalsIgnoreCase(probe)) {
                return true;
            }
        }
        return false;
    }

    private static QueryPlan parseSelect(String sql) throws SQLException {
        Matcher matcher = SELECT_PATTERN.matcher(sql);
        if (!matcher.matches()) {
            throw new SQLException("Only simple SELECT statements are supported");
        }
        String rawFieldSpec = matcher.group(1).trim();
        String table = normalizeTableIdentifier(matcher.group(2).trim());
        String where = normalizeWhereClause(matcher.group(3) != null ? matcher.group(3).trim() : "");
        String orderBy = matcher.group(4) != null ? matcher.group(4).trim() : "";

        String fieldSpec = rawFieldSpec;
        List<String> columns = new ArrayList<String>();
        if (!"*".equals(rawFieldSpec)) {
            List<String> fetchColumns = new ArrayList<String>();
            String[] parts = rawFieldSpec.split(",");
            for (String part : parts) {
                String fetchColumn = normalizeSelectExpression(part);
                String col = normalizeColumn(part);
                fetchColumns.add(fetchColumn);
                columns.add(col);
            }
            fieldSpec = joinColumns(fetchColumns);
        }
        return new QueryPlan(table, fieldSpec, where, orderBy, columns);
    }

    private static String normalizeTableIdentifier(String raw) {
        if (raw == null) {
            return null;
        }
        String table = raw.trim();
        if (table.length() >= 2 && table.startsWith("\"") && table.endsWith("\"")) {
            table = table.substring(1, table.length() - 1);
        }
        return table;
    }

    private static String normalizeSelectSql(String sql) {
        if (sql == null) {
            return null;
        }
        String normalized = sql.trim();
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static String rewritePlainJoinToLeftJoin(String sql) {
        if (sql == null) {
            return null;
        }
        String upper = sql.toUpperCase(Locale.ROOT);
        if (!upper.contains(" JOIN ")) {
            return sql;
        }
        if (upper.contains(" LEFT JOIN ")
                || upper.contains(" RIGHT JOIN ")
                || upper.contains(" INNER JOIN ")
                || upper.contains(" FULL JOIN ")
                || upper.contains(" CROSS JOIN ")) {
            return sql;
        }
        return sql.replaceAll("(?i)\\bJOIN\\b", "LEFT JOIN");
    }

    private static boolean looksLikeUnionDerivedSelect(String sql) {
        if (sql == null) {
            return false;
        }
        return UNION_DERIVED_SELECT_PATTERN.matcher(sql).matches();
    }

    private static String normalizeColumn(String raw) {
        String col = stripAlias(raw);
        if (col.contains(".")) {
            col = col.substring(col.lastIndexOf('.') + 1);
        }
        if (col.length() >= 2 && col.startsWith("\"") && col.endsWith("\"")) {
            col = col.substring(1, col.length() - 1);
        }
        return col;
    }

    private static String normalizeSelectExpression(String raw) {
        String expression = stripAlias(raw);
        if (isSimpleColumnReference(expression)) {
            return normalizeColumn(expression);
        }
        return expression;
    }

    private static String stripAlias(String raw) {
        String col = raw != null ? raw.trim() : "";
        int asIdx = col.toUpperCase(Locale.ROOT).indexOf(" AS ");
        if (asIdx > 0) {
            return col.substring(0, asIdx).trim();
        }
        int spaceIdx = col.indexOf(' ');
        if (spaceIdx > 0) {
            String suffix = col.substring(spaceIdx + 1).trim();
            if (!suffix.isEmpty()) {
                char first = suffix.charAt(0);
                if (Character.isLetter(first) || first == '_' || first == '"') {
                    return col.substring(0, spaceIdx).trim();
                }
            }
        }
        return col;
    }

    private static boolean isSimpleColumnReference(String expression) {
        if (expression == null) {
            return false;
        }
        return SIMPLE_IDENTIFIER_PATTERN.matcher(expression.trim()).matches();
    }

    private static String normalizeWhereClause(String whereClause) {
        if (whereClause == null || whereClause.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        StringBuilder outsideLiteral = new StringBuilder();
        boolean inLiteral = false;
        for (int i = 0; i < whereClause.length(); i++) {
            char c = whereClause.charAt(i);
            if (c == '\'') {
                if (!inLiteral) {
                    out.append(normalizeQualifiedIdentifiers(outsideLiteral.toString()));
                    outsideLiteral.setLength(0);
                    out.append(c);
                    inLiteral = true;
                } else if (i + 1 < whereClause.length() && whereClause.charAt(i + 1) == '\'') {
                    out.append("''");
                    i++;
                } else {
                    out.append(c);
                    inLiteral = false;
                }
            } else if (inLiteral) {
                out.append(c);
            } else {
                outsideLiteral.append(c);
            }
        }
        if (outsideLiteral.length() > 0) {
            out.append(normalizeQualifiedIdentifiers(outsideLiteral.toString()));
        }
        return out.toString();
    }

    private static boolean isCountQuery(QueryPlan plan) {
        if (plan == null || plan.fieldSpec == null) {
            return false;
        }
        String fieldSpec = plan.fieldSpec.trim();
        if (fieldSpec.isEmpty()) {
            return false;
        }
        if (fieldSpec.indexOf(',') >= 0) {
            return false;
        }
        return COUNT_ONLY_PATTERN.matcher(fieldSpec).matches();
    }

    private static String normalizeQualifiedIdentifiers(String input) {
        String current = input;
        while (true) {
            Matcher matcher = QUALIFIED_IDENTIFIER_PATTERN.matcher(current);
            StringBuffer rewritten = new StringBuffer();
            boolean changed = false;
            while (matcher.find()) {
                String column = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
                String replacement = matcher.group(3) != null ? "\"" + column + "\"" : column;
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
                changed = true;
            }
            matcher.appendTail(rewritten);
            String next = rewritten.toString();
            if (!changed || next.equals(current)) {
                return next;
            }
            current = next;
        }
    }

    private static String joinColumns(List<String> columns) {
        StringBuilder out = new StringBuilder();
        String sep = "";
        for (String column : columns) {
            out.append(sep).append(column);
            sep = ",";
        }
        if (out.length() == 0) {
            return "*";
        }
        return out.toString();
    }

    private static void applyOrderBy(List<Map<String, Object>> rows, List<String> columns, String orderByClause) {
        if (rows == null || rows.size() <= 1 || orderByClause == null || orderByClause.trim().isEmpty()) {
            return;
        }
        String[] parts = orderByClause.split(",");
        final List<OrderBySpec> orderSpecs = new ArrayList<OrderBySpec>();
        for (String rawPart : parts) {
            String part = rawPart != null ? rawPart.trim() : "";
            if (part.isEmpty()) {
                continue;
            }
            String upper = part.toUpperCase(Locale.ROOT);
            boolean desc = upper.endsWith(" DESC");
            boolean asc = upper.endsWith(" ASC");
            String column = part;
            if (desc) {
                column = part.substring(0, part.length() - " DESC".length()).trim();
            } else if (asc) {
                column = part.substring(0, part.length() - " ASC".length()).trim();
            }
            column = normalizeColumn(column);
            if (!column.isEmpty()) {
                orderSpecs.add(new OrderBySpec(column, !desc));
            }
        }
        if (orderSpecs.isEmpty()) {
            return;
        }
        java.util.Collections.sort(rows, new java.util.Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                for (OrderBySpec spec : orderSpecs) {
                    Object l = getIgnoreCase(left, spec.column);
                    Object r = getIgnoreCase(right, spec.column);
                    int cmp = compareNullable(l, r);
                    if (cmp != 0) {
                        return spec.ascending ? cmp : -cmp;
                    }
                }
                return 0;
            }
        });
    }

    private static int compareNullable(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Number && right instanceof Number) {
            double lv = ((Number) left).doubleValue();
            double rv = ((Number) right).doubleValue();
            return Double.compare(lv, rv);
        }
        return left.toString().compareTo(right.toString());
    }

    static String encodeLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "1" : "0";
        }
        if (value instanceof java.sql.Time) {
            return "'1970-01-01 " + value.toString() + "'";
        }
        if (value instanceof java.sql.Timestamp) {
            return "'" + value.toString().replace('T', ' ') + "'";
        }
        if (value instanceof java.sql.Date) {
            return "'" + value.toString() + "'";
        }
        if (value instanceof byte[]) {
            return "'" + BYTE_LITERAL_PREFIX + java.util.Base64.getEncoder().encodeToString((byte[]) value) + "'";
        }
        String text = value.toString().replace("'", "''");
        return "'" + text + "'";
    }

    static Object parseValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.matches("-?\\d+")) {
            try {
                return Integer.valueOf(trimmed);
            } catch (NumberFormatException ignore) {
            }
            try {
                return Long.valueOf(trimmed);
            } catch (NumberFormatException ignore) {
            }
        }
        if (trimmed.matches("-?\\d+\\.\\d+")) {
            try {
                return new java.math.BigDecimal(trimmed);
            } catch (NumberFormatException ignore) {
            }
        }
        if (trimmed.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return trimmed;
        }
        if (trimmed.startsWith(BYTE_LITERAL_PREFIX)) {
            String b64 = trimmed.substring(BYTE_LITERAL_PREFIX.length());
            try {
                return java.util.Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException ignore) {
                return value;
            }
        }
        return value;
    }

    private static Object readRowValue(OpenFgdb api, long rowHandle, String column) throws OpenFgdbException {
        if (api.rowIsNull(rowHandle, column)) {
            return null;
        }
        try {
            byte[] blob = api.rowGetBlob(rowHandle, column);
            if (blob != null) {
                return blob;
            }
        } catch (OpenFgdbException e) {
            if (!isTypeMismatch(e)) {
                throw e;
            }
        }
        try {
            String textValue = api.rowGetString(rowHandle, column);
            if (textValue != null) {
                return parseValue(textValue);
            }
        } catch (OpenFgdbException e) {
            if (!isTypeMismatch(e)) {
                throw e;
            }
        }
        return null;
    }

    private static boolean isTypeMismatch(OpenFgdbException ex) {
        return ex != null && ex.getErrorCode() == OpenFgdb.OFGDB_ERR_INVALID_ARG;
    }

    private static boolean isTableNotFound(OpenFgdbException ex) {
        return ex != null && ex.getErrorCode() == OpenFgdb.OFGDB_ERR_NOT_FOUND;
    }

    private void ensureOpen() throws SQLException {
        if (closed) {
            throw new SQLException("statement is closed");
        }
    }

    private void trackSchemaMutation(String sql) {
        if (sql == null) {
            return;
        }
        Matcher createMatcher = CREATE_TABLE_PATTERN.matcher(sql);
        if (createMatcher.matches()) {
            conn.registerTableName(normalizeTableIdentifier(createMatcher.group(1)));
            return;
        }
        Matcher dropMatcher = DROP_TABLE_PATTERN.matcher(sql);
        if (dropMatcher.matches()) {
            conn.removeTableName(normalizeTableIdentifier(dropMatcher.group(1)));
        }
    }

    private static final class UnionDerivedSelectSpec {
        String unionBody;
        String whereClause;
        final List<String> projectionColumns = new ArrayList<String>();
    }

    private static final class QueryPlan {
        final String tableName;
        final String fieldSpec;
        final String whereClause;
        final String orderByClause;
        final List<String> columns;

        QueryPlan(String tableName, String fieldSpec, String whereClause, String orderByClause, List<String> columns) {
            this.tableName = tableName;
            this.fieldSpec = fieldSpec;
            this.whereClause = whereClause;
            this.orderByClause = orderByClause;
            this.columns = columns;
        }
    }

    private static final class OrderBySpec {
        final String column;
        final boolean ascending;

        OrderBySpec(String column, boolean ascending) {
            this.column = column;
            this.ascending = ascending;
        }
    }
}
