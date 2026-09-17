package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Test;

public class OfgdbBatchJdbcTest {
    private static final String TEST_DB_DIR = "build/test-ofgdb";

    @Test
    public void preparedStatementBatchInsertExecutesAllRows() throws Exception {
        OfgdbTestSetup setup = createSetup("OfgdbBatchJdbcPrepared");
        try (Connection conn = setup.createConnection();
                Statement ddl = conn.createStatement();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO t_batch(id, name) VALUES (?, ?)")) {
            ddl.executeUpdate("CREATE TABLE t_batch(id INTEGER, name VARCHAR)");
            ps.setInt(1, 1);
            ps.setString(2, "first");
            ps.addBatch();
            ps.setInt(1, 2);
            ps.setString(2, "second");
            ps.addBatch();

            int[] counts = ps.executeBatch();
            assertEquals(2, counts.length);
            assertEquals(Statement.SUCCESS_NO_INFO, counts[0]);
            assertEquals(Statement.SUCCESS_NO_INFO, counts[1]);
            assertEquals(2, readCount(conn, "t_batch"));
        }
    }

    @Test
    public void clearBatchDropsQueuedStatements() throws Exception {
        OfgdbTestSetup setup = createSetup("OfgdbBatchJdbcClear");
        try (Connection conn = setup.createConnection();
                Statement ddl = conn.createStatement();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO t_batch_clear(id, name) VALUES (?, ?)")) {
            ddl.executeUpdate("CREATE TABLE t_batch_clear(id INTEGER, name VARCHAR)");
            ps.setInt(1, 1);
            ps.setString(2, "first");
            ps.addBatch();
            ps.setInt(1, 2);
            ps.setString(2, "second");
            ps.addBatch();
            ps.clearBatch();

            int[] counts = ps.executeBatch();
            assertEquals(0, counts.length);
            assertEquals(0, readCount(conn, "t_batch_clear"));
        }
    }

    @Test
    public void executeBatchReturnsSuccessNoInfoPerEntry() throws Exception {
        OfgdbTestSetup setup = createSetup("OfgdbBatchJdbcCounts");
        try (Connection conn = setup.createConnection();
                Statement ddl = conn.createStatement();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO t_batch_counts(id) VALUES (?)")) {
            ddl.executeUpdate("CREATE TABLE t_batch_counts(id INTEGER)");
            ps.setInt(1, 1);
            ps.addBatch();
            ps.setInt(1, 2);
            ps.addBatch();
            ps.setInt(1, 3);
            ps.addBatch();

            int[] counts = ps.executeBatch();
            assertEquals(3, counts.length);
            assertEquals(Statement.SUCCESS_NO_INFO, counts[0]);
            assertEquals(Statement.SUCCESS_NO_INFO, counts[1]);
            assertEquals(Statement.SUCCESS_NO_INFO, counts[2]);
        }
    }

    @Test
    public void executeBatchFailureReturnsPartialCounts() throws Exception {
        OfgdbTestSetup setup = createSetup("OfgdbBatchJdbcFailure");
        try (Connection conn = setup.createConnection();
                Statement ddl = conn.createStatement();
                Statement stmt = conn.createStatement()) {
            ddl.executeUpdate("CREATE TABLE t_batch_fail(id INTEGER, name VARCHAR)");
            stmt.addBatch("INSERT INTO t_batch_fail(id, name) VALUES (1, 'ok')");
            stmt.addBatch("INSERT INTO does_not_exist(id, name) VALUES (2, 'boom')");
            try {
                stmt.executeBatch();
                fail("expected BatchUpdateException");
            } catch (BatchUpdateException e) {
                int[] partial = e.getUpdateCounts();
                assertEquals(1, partial.length);
                assertEquals(Statement.SUCCESS_NO_INFO, partial[0]);
            }
            assertEquals(1, readCount(conn, "t_batch_fail"));
        }
    }

    private OfgdbTestSetup createSetup(String dbName) throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/" + dbName + ".gdb");
        setup.resetDb();
        return setup;
    }

    private int readCount(Connection conn, String tableName) throws Exception {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
