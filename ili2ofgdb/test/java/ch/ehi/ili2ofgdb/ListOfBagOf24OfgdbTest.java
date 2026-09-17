package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;
import ch.interlis.iox_j.wkb.Wkb2iox;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ListOfBagOf24OfgdbTest extends ch.ehi.ili2db.ListOfBagOf24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/ListOfBagOf24OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    protected void assertTableContainsColumns(Connection jdbcConnection, String tableName, String... expectedColumns) throws SQLException {
        ResultSet resultSet = null;
        try {
            resultSet = jdbcConnection.getMetaData().getColumns(null, null, tableName, null);
            List<String> actualValues = new ArrayList<String>();
            while (resultSet.next()) {
                actualValues.add(resultSet.getString("COLUMN_NAME"));
            }
            assertThat(actualValues, containsInAnyOrder(expectedColumns));
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    @Override
    protected void assertClassA1Attr8(Connection jdbcConnection) throws Exception {
        Statement statement = null;
        try {
            statement = jdbcConnection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT attr8 FROM " + setup.prefixName("classa1"));
            Wkb2iox wkb2iox = new Wkb2iox();
            while (resultSet.next()) {
                byte[] value = resultSet.getBytes(1);
                if (value == null) {
                    fail("expected binary value in geometry column but got null");
                }
                assertEquals("COORD {C1 480000.0, C2 70000.0}", wkb2iox.read(value).toString());
            }
        } finally {
            if (statement != null) {
                statement.close();
            }
        }
    }

    @Override
    protected void assertClassA1Attr9(Connection jdbcConnection) throws Exception {
        Statement statement = null;
        try {
            statement = jdbcConnection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT attr9 FROM " + setup.prefixName("classa1_attr9"));
            Wkb2iox wkb2iox = new Wkb2iox();
            while (resultSet.next()) {
                byte[] value = resultSet.getBytes(1);
                if (value == null) {
                    fail("expected binary value in geometry column but got null");
                }
                assertEquals("COORD {C1 500000.0, C2 72000.0}", wkb2iox.read(value).toString());
            }
        } finally {
            if (statement != null) {
                statement.close();
            }
        }
    }

    @Override
    protected void assertTableContainsValues(Connection jdbcConnection, String table, String[] columns, String[][] expectedValues) throws SQLException {
        if (!containsAttr7(columns)) {
            super.assertTableContainsValues(jdbcConnection, table, columns, expectedValues);
            return;
        }
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            statement = jdbcConnection.createStatement();
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");
            String sep = "";
            for (String column : columns) {
                sql.append(sep).append(column);
                sep = ",";
            }
            sql.append(" FROM ").append(setup.prefixName(table));
            resultSet = statement.executeQuery(sql.toString());
            List<String> actualRows = new ArrayList<String>();
            while (resultSet.next()) {
                actualRows.add(joinNormalizedRow(resultSet, columns.length));
            }
            List<String> expectedRows = new ArrayList<String>();
            for (String[] expectedRow : expectedValues) {
                expectedRows.add(joinNormalizedRow(expectedRow));
            }
            Collections.sort(actualRows);
            Collections.sort(expectedRows);
            assertEquals("unexpected row values in " + table, expectedRows, actualRows);
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
        }
    }

    private static boolean containsAttr7(String[] columns) {
        if (columns == null) {
            return false;
        }
        for (String column : columns) {
            if ("attr7".equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    private static String joinNormalizedRow(ResultSet resultSet, int cols) throws SQLException {
        StringBuilder row = new StringBuilder();
        String sep = "";
        for (int i = 1; i <= cols; i++) {
            Object value = resultSet.getObject(i);
            row.append(sep).append(normalizeDateValue(value != null ? value.toString() : null));
            sep = "|";
        }
        return row.toString();
    }

    private static String joinNormalizedRow(String[] values) {
        StringBuilder row = new StringBuilder();
        String sep = "";
        for (String value : values) {
            row.append(sep).append(normalizeDateValue(value));
            sep = "|";
        }
        return row.toString();
    }

    private static String normalizeDateValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d{4}/\\d{2}/\\d{2}.*")) {
            return trimmed.substring(0, 10).replace('/', '-');
        }
        return trimmed;
    }
}
