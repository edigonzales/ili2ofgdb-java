package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.openfgdb4j.OpenFgdb;

public class MandatoryChecksOfgdbTest {
    private static final String TEST_DATA_DIR = "test/data/MandatoryChecks";
    private static final String TEST_DB_DIR = "build/test-ofgdb";

    @Test
    public void mandatoryTextAndReferenceBecomeNotNullColumns() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB_DIR + "/MandatorySimple.gdb");
        setup.resetDb();

        File data = new File(TEST_DATA_DIR + "/MandatorySimple.ili");
        Config config = setup.initConfig(data.getPath(), data.getPath() + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_SCHEMAIMPORT);
        config.setCreateFk(Config.CREATE_FK_YES);
        config.setCreateMandatoryChecks(true);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        Ili2db.run(config, null);

        String definitionXml = readItemDefinition(config.getDbfile(), setup.prefixName("classa"));
        assertNotNull(definitionXml);
        assertFieldNullable(definitionXml, "aname", Boolean.FALSE);
        assertFieldNullable(definitionXml, "target", Boolean.FALSE);
        assertFieldNullable(definitionXml, "note", Boolean.TRUE);

        try (Connection jdbcConnection = setup.createConnection()) {
            assertColumn(jdbcConnection, setup.prefixName("classa"), "aname", Types.VARCHAR, "VARCHAR", 20, false);
            assertColumn(jdbcConnection, setup.prefixName("classa"), "target", Types.INTEGER, "INTEGER", 10, false);
            assertColumn(jdbcConnection, setup.prefixName("classa"), "note", Types.VARCHAR, "VARCHAR", 30, true);

            try (Statement stmt = jdbcConnection.createStatement()) {
                try {
                    stmt.executeUpdate("INSERT INTO " + setup.prefixName("classa") + " (T_Id) VALUES (1)");
                    fail("Expected NOT NULL violation for missing mandatory columns");
                } catch (SQLException expected) {
                    assertTrue(exceptionContains(expected, "not null") || exceptionContains(expected, "non-nullable"));
                }
            }
        }
    }

    private static String readItemDefinition(String dbFile, String itemName) throws Exception {
        OpenFgdb api = new OpenFgdb();
        long dbHandle = api.open(dbFile);
        try {
            long tableHandle = api.openTable(dbHandle, "GDB_Items");
            try {
                long cursor = api.search(tableHandle, "Name,Definition", "");
                try {
                    while (true) {
                        long row = api.fetchRow(cursor);
                        if (row == 0L) {
                            return null;
                        }
                        try {
                            String rowName = api.rowGetString(row, "Name");
                            if (rowName != null && rowName.equalsIgnoreCase(itemName)) {
                                return api.rowGetString(row, "Definition");
                            }
                        } finally {
                            api.closeRow(row);
                        }
                    }
                } finally {
                    api.closeCursor(cursor);
                }
            } finally {
                api.closeTable(dbHandle, tableHandle);
            }
        } finally {
            api.close(dbHandle);
        }
    }

    private static void assertFieldNullable(String definitionXml, String fieldName, Boolean expectedNullable) throws Exception {
        FieldNullableInspection inspection = inspectFieldNullable(definitionXml, fieldName);
        if (expectedNullable.equals(inspection.parsedNullable)) {
            return;
        }
        throw new AssertionError("Unexpected IsNullable value for field '" + fieldName + "': expected <"
                + expectedNullable + "> but was <" + inspection.parsedNullable + ">; "
                + inspection.diagnosticLine);
    }

    private static FieldNullableInspection inspectFieldNullable(String definitionXml, String fieldName) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(definitionXml)));
        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Node node = allNodes.item(i);
            if (!(node instanceof Element) || !nodeNameMatches(node, "GPFieldInfoEx")) {
                continue;
            }
            Element fieldNode = (Element) node;
            String currentFieldName = childTagText(fieldNode, "Name");
            if (currentFieldName == null || !currentFieldName.equalsIgnoreCase(fieldName)) {
                continue;
            }
            String nullableText = childTagText(fieldNode, "IsNullable");
            String diagnosticLine = buildFieldDiagnostic(fieldName, nullableText, fieldNode);
            if (nullableText == null || nullableText.isEmpty()) {
                return new FieldNullableInspection(null, diagnosticLine);
            }
            if ("1".equals(nullableText)) {
                return new FieldNullableInspection(Boolean.TRUE, diagnosticLine);
            }
            if ("0".equals(nullableText)) {
                return new FieldNullableInspection(Boolean.FALSE, diagnosticLine);
            }
            return new FieldNullableInspection(Boolean.valueOf(Boolean.parseBoolean(nullableText)), diagnosticLine);
        }
        return new FieldNullableInspection(null, "field=" + fieldName + "; GPFieldInfoEx node not found in definition");
    }

    private static String buildFieldDiagnostic(String fieldName, String nullableText, Element fieldNode) {
        return "field=" + fieldName
                + "; rawIsNullable=" + String.valueOf(nullableText)
                + "; childTags=" + listChildTagNames(fieldNode)
                + "; fieldXmlSnippet=\"" + summarizeXml(fieldNode.getTextContent()) + "\"";
    }

    private static String listChildTagNames(Element fieldNode) {
        Set<String> names = new LinkedHashSet<String>();
        NodeList children = fieldNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) {
                continue;
            }
            String name = child.getNodeName();
            if (name != null && name.trim().length() > 0) {
                names.add(name.trim());
            }
        }
        return names.toString();
    }

    private static String summarizeXml(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        normalized = normalized.replaceAll("\\s+", " ");
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300) + "...";
    }

    private static String childTagText(Element parent, String tagName) {
        NodeList descendants = parent.getElementsByTagName("*");
        for (int i = 0; i < descendants.getLength(); i++) {
            Node child = descendants.item(i);
            if (child instanceof Element && nodeNameMatches(child, tagName)) {
                String text = child.getTextContent();
                return text != null ? text.trim() : null;
            }
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && nodeNameMatches(child, tagName)) {
                String text = child.getTextContent();
                return text != null ? text.trim() : null;
            }
        }
        return null;
    }

    private static boolean nodeNameMatches(Node node, String localTagName) {
        if (node == null || localTagName == null) {
            return false;
        }
        String name = node.getNodeName();
        return name != null && name.endsWith(localTagName);
    }

    private static void assertColumn(Connection jdbcConnection, String tableName, String columnName, int dataType,
            String typeName, int columnSize, boolean nullable) throws SQLException {
        try (ResultSet cols = jdbcConnection.getMetaData().getColumns(null, null, tableName, columnName)) {
            assertTrue("column not found: " + tableName + "." + columnName, cols.next());
            assertEquals(dataType, cols.getInt("DATA_TYPE"));
            assertEquals(typeName, cols.getString("TYPE_NAME"));
            assertEquals(columnSize, cols.getInt("COLUMN_SIZE"));
            assertEquals(nullable ? "YES" : "NO", cols.getString("IS_NULLABLE"));
            assertEquals(nullable ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls, cols.getInt("NULLABLE"));
        }
    }

    private static boolean exceptionContains(Throwable throwable, String text) {
        String lowercaseText = text.toLowerCase();
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains(lowercaseText)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class FieldNullableInspection {
        private final Boolean parsedNullable;
        private final String diagnosticLine;

        private FieldNullableInspection(Boolean parsedNullable, String diagnosticLine) {
            this.parsedNullable = parsedNullable;
            this.diagnosticLine = diagnosticLine;
        }
    }
}
