package ch.ehi.ili2ofgdb.jdbc;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import ch.ehi.openfgdb4j.OpenFgdb;
import ch.ehi.openfgdb4j.OpenFgdbException;

public class OfgdbDriver implements Driver {
    public static final String BASE_URL = "jdbc:ili2ofgdb:";

    static {
        try {
            DriverManager.registerDriver(new OfgdbDriver());
        } catch (SQLException e) {
            throw new IllegalStateException("failed to register OfgdbDriver", e);
        }
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(BASE_URL);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        String dbPath = url.substring(BASE_URL.length());
        OpenFgdb api = new OpenFgdb();
        try {
            File file = new File(dbPath);
            long dbHandle = file.exists() ? api.open(file.getAbsolutePath()) : api.create(file.getAbsolutePath());
            return new OfgdbConnection(api, dbHandle, url);
        } catch (OpenFgdbException e) {
            throw new SQLException("failed to open/create openfgdb database", e);
        }
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Not supported");
    }
}
