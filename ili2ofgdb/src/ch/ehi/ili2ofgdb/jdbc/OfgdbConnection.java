package ch.ehi.ili2ofgdb.jdbc;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLClientInfoException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import ch.ehi.openfgdb4j.OpenFgdb;
import ch.ehi.openfgdb4j.OpenFgdbException;

public class OfgdbConnection implements Connection {
    private final OpenFgdb api;
    private final String url;
    private final LinkedHashSet<String> knownTables = new LinkedHashSet<String>();
    private long dbHandle;
    private boolean autoCommit = true;
    private Path txnSnapshotPath = null;
    private boolean closed = false;

    protected OfgdbConnection(OpenFgdb api, long dbHandle, String url) {
        this.api = api;
        this.dbHandle = dbHandle;
        this.url = url;
        refreshKnownTableNames();
    }

    OpenFgdb getApi() {
        return api;
    }

    long getDbHandle() {
        return dbHandle;
    }

    void reopenSession() throws SQLException {
        ensureOpen();
        String dbPath = getDbPath();
        try {
            if (dbHandle != 0L) {
                api.close(dbHandle);
            }
            dbHandle = api.open(dbPath);
            synchronized (this) {
                knownTables.clear();
            }
            refreshKnownTableNames();
        } catch (OpenFgdbException e) {
            throw new SQLException("failed to reopen openfgdb connection", e);
        }
    }

    public OpenFgdb getOpenFgdbApi() throws SQLException {
        ensureOpen();
        return api;
    }

    public long getOpenFgdbHandle() throws SQLException {
        ensureOpen();
        return dbHandle;
    }

    String getUrl() {
        return url;
    }

    synchronized void registerTableName(String tableName) {
        if (tableName != null && !tableName.isEmpty()) {
            knownTables.add(tableName);
        }
    }

    synchronized void removeTableName(String tableName) {
        if (tableName != null) {
            knownTables.remove(tableName);
        }
    }

    synchronized java.util.List<String> getKnownTableNames() {
        return new ArrayList<String>(knownTables);
    }

    String resolveTableName(String tableName) {
        String probe = normalizeIdentifier(tableName);
        if (probe == null || probe.isEmpty()) {
            return tableName;
        }
        List<String> probeCandidates = buildProbeCandidates(probe);
        String resolved = findKnownTableName(probeCandidates);
        if (resolved != null) {
            return resolved;
        }
        refreshKnownTableNames();
        resolved = findKnownTableName(probeCandidates);
        return resolved != null ? resolved : probe;
    }

    private synchronized String findKnownTableName(List<String> probes) {
        for (String probe : probes) {
            for (String known : knownTables) {
                if (known.equals(probe)) {
                    return known;
                }
            }
        }
        for (String probe : probes) {
            for (String known : knownTables) {
                if (known.equalsIgnoreCase(probe)) {
                    return known;
                }
            }
        }
        return null;
    }

    private static List<String> buildProbeCandidates(String probe) {
        LinkedHashSet<String> probes = new LinkedHashSet<String>();
        probes.add(probe);
        List<String> identifierParts = splitIdentifierParts(probe);
        if (identifierParts.size() > 1) {
            for (int i = 1; i < identifierParts.size(); i++) {
                String suffix = joinParts(identifierParts, i);
                if (!suffix.isEmpty()) {
                    probes.add(suffix);
                }
            }
        }
        return new ArrayList<String>(probes);
    }

    private static String joinParts(List<String> parts, int startIdx) {
        StringBuilder ret = new StringBuilder();
        String sep = "";
        for (int i = startIdx; i < parts.size(); i++) {
            String part = normalizeIdentifier(parts.get(i));
            if (part == null || part.isEmpty()) {
                continue;
            }
            ret.append(sep).append(part);
            sep = ".";
        }
        return ret.toString();
    }

    private static List<String> splitIdentifierParts(String identifier) {
        ArrayList<String> parts = new ArrayList<String>();
        if (identifier == null || identifier.isEmpty()) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == '.' && !inQuotes) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String normalized = identifier.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private String getDbPath() {
        if (url != null && url.startsWith(OfgdbDriver.BASE_URL)) {
            return url.substring(OfgdbDriver.BASE_URL.length());
        }
        return url;
    }

    private void refreshKnownTableNames() {
        try {
            for (String tableName : api.listTableNames(dbHandle)) {
                registerTableName(tableName);
            }
        } catch (OpenFgdbException ignore) {
            // Metadata bootstrap should not block connection creation.
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        ensureOpen();
        return new OfgdbStatement(this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        ensureOpen();
        return new OfgdbPreparedStatement(this, sql);
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        ensureOpen();
        return new OfgdbMetaData(this);
    }

    @Override
    public synchronized void close() throws SQLException {
        if (closed) {
            return;
        }
        SQLException failure = null;
        if (!autoCommit) {
            try {
                rollbackInternal(false);
            } catch (SQLException e) {
                failure = e;
            }
        }
        try {
            if (dbHandle != 0L) {
                api.close(dbHandle);
            }
        } catch (OpenFgdbException e) {
            SQLException closeFailure = new SQLException("failed to close openfgdb connection", e);
            if (failure != null) {
                closeFailure.addSuppressed(failure);
            }
            failure = closeFailure;
        } finally {
            dbHandle = 0L;
            knownTables.clear();
            autoCommit = true;
            cleanupSnapshotQuietly();
            closed = true;
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void commit() throws SQLException {
        ensureOpen();
        if (autoCommit) {
            return;
        }
        cleanupSnapshot();
        autoCommit = true;
    }

    @Override
    public synchronized void rollback() throws SQLException {
        rollbackInternal(true);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints are not supported");
    }

    @Override
    public synchronized void setAutoCommit(boolean autoCommit) throws SQLException {
        ensureOpen();
        if (this.autoCommit == autoCommit) {
            return;
        }
        if (!autoCommit) {
            beginSnapshotTransaction();
            this.autoCommit = false;
            return;
        }
        commit();
    }

    @Override
    public synchronized boolean getAutoCommit() throws SQLException {
        ensureOpen();
        return autoCommit;
    }

    @Override
    public String nativeSQL(String sql) {
        return sql;
    }

    @Override
    public void clearWarnings() {
    }

    @Override
    public SQLWarning getWarnings() {
        return null;
    }

    @Override
    public String getCatalog() {
        return null;
    }

    @Override
    public void setCatalog(String catalog) {
    }

    @Override
    public int getTransactionIsolation() {
        return Connection.TRANSACTION_NONE;
    }

    @Override
    public void setTransactionIsolation(int level) {
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return createStatement();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints are not supported");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints are not supported");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints are not supported");
    }

    @Override
    public int getHoldability() {
        return 0;
    }

    @Override
    public void setHoldability(int holdability) {
    }

    @Override
    public Map<String, Class<?>> getTypeMap() {
        return null;
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) {
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public boolean isValid(int timeout) {
        return !closed;
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
    }

    @Override
    public String getClientInfo(String name) {
        return null;
    }

    @Override
    public Properties getClientInfo() {
        return new Properties();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public String getSchema() {
        return null;
    }

    @Override
    public void setSchema(String schema) {
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        close();
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) {
    }

    @Override
    public int getNetworkTimeout() {
        return 0;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface == null) {
            throw new SQLException("iface is null");
        }
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        if (iface.isAssignableFrom(OpenFgdb.class)) {
            ensureOpen();
            return iface.cast(api);
        }
        throw new SQLException("not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        if (iface == null) {
            return false;
        }
        return iface.isAssignableFrom(getClass()) || iface.isAssignableFrom(OpenFgdb.class);
    }

    private void ensureOpen() throws SQLException {
        if (closed) {
            throw new SQLException("connection is closed");
        }
    }

    private Path getDbPathAsPath() {
        return Paths.get(getDbPath()).toAbsolutePath().normalize();
    }

    private void beginSnapshotTransaction() throws SQLException {
        Path dbPath = getDbPathAsPath();
        Path snapshotPath = OfgdbFileSnapshot.buildSnapshotPath(dbPath);
        try {
            OfgdbFileSnapshot.createSnapshot(dbPath, snapshotPath);
            txnSnapshotPath = snapshotPath;
        } catch (Exception e) {
            throw new SQLException("failed to create transaction snapshot for " + dbPath, e);
        }
    }

    private void rollbackInternal(boolean reopenAfterRestore) throws SQLException {
        ensureOpen();
        if (autoCommit) {
            return;
        }
        if (txnSnapshotPath == null) {
            throw new SQLException("transaction snapshot is missing");
        }
        Path dbPath = getDbPathAsPath();
        try {
            if (dbHandle != 0L) {
                api.close(dbHandle);
                dbHandle = 0L;
            }
            OfgdbFileSnapshot.restoreSnapshot(txnSnapshotPath, dbPath);
            if (reopenAfterRestore) {
                dbHandle = api.open(dbPath.toString());
                synchronized (this) {
                    knownTables.clear();
                }
                refreshKnownTableNames();
            }
            OfgdbFileSnapshot.deleteRecursively(txnSnapshotPath);
            txnSnapshotPath = null;
            autoCommit = true;
        } catch (Exception e) {
            throw new SQLException("failed to rollback openfgdb transaction snapshot", e);
        }
    }

    private void cleanupSnapshot() throws SQLException {
        if (txnSnapshotPath == null) {
            return;
        }
        try {
            OfgdbFileSnapshot.deleteRecursively(txnSnapshotPath);
            txnSnapshotPath = null;
        } catch (Exception e) {
            throw new SQLException("failed to cleanup transaction snapshot", e);
        }
    }

    private void cleanupSnapshotQuietly() {
        if (txnSnapshotPath == null) {
            return;
        }
        try {
            OfgdbFileSnapshot.deleteRecursively(txnSnapshotPath);
        } catch (Exception ignore) {
            // connection is closing; best effort cleanup only
        } finally {
            txnSnapshotPath = null;
        }
    }
}
