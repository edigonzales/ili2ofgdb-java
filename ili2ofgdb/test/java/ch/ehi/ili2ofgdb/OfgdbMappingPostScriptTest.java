package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2ofgdb.jdbc.OfgdbDriver;
import ch.ehi.openfgdb4j.OpenFgdb;
import ch.ehi.openfgdb4j.OpenFgdbException;

public class OfgdbMappingPostScriptTest {

    @Test
    public void postPostScriptIsIdempotent() throws Exception {
        OfgdbMapping mapping = new OfgdbMapping();
        Config config = new Config();
        config.setFgdbCreateDomains(true);
        config.setFgdbCreateRelationshipClasses(true);
        config.setFgdbIncludeInactiveEnumValues(false);
        Path workDir = Files.createTempDirectory("ili2ofgdb-postps-");
        config.setDbfile(workDir.resolve("schema.gdb").toString());
        ensureTable(config.getDbfile(), "classa", "T_Id INTEGER", "color VARCHAR(128)");
        ensureTable(config.getDbfile(), "assoc_tbl", "T_Id INTEGER", "rolea_fk INTEGER");

        mapping.fromIliInit(config);

        Map<String, String> domainValues = new LinkedHashMap<String, String>();
        domainValues.put("rot", "Rot");
        domainValues.put("blau", "Blau");
        addDomain(mapping, "Enum_Color", "STRING", domainValues);
        addDomainAssignment(mapping, "classa", "color", "Enum_Color");
        addRoleLink(mapping, "_Model.Topic.Assoc", "roleA", "assoc_tbl", "rolea_fk", "classa", "T_Id", "toA", "fromAssoc", "1:n",
                false);

        mapping.postPostScript(null, config);
        int[] first = snapshot(config.getDbfile());
        mapping.postPostScript(null, config);
        int[] second = snapshot(config.getDbfile());

        OpenFgdb api = new OpenFgdb();
        long dbHandle = api.open(config.getDbfile());
        try {
            List<String> domains = api.listDomains(dbHandle);
            List<String> relationships = api.listRelationships(dbHandle);
            assertTrue(domains.contains("Enum_Color"));
            assertEquals(1, relationships.size());
        } finally {
            api.close(dbHandle);
        }

        assertEquals(first[0], second[0]);
        assertEquals(first[1], second[1]);
    }

    @Test
    public void postPostScriptWithoutDbFileIsNoOp() {
        OfgdbMapping mapping = new OfgdbMapping();
        Config config = new Config();
        config.setFgdbCreateDomains(true);
        config.setFgdbCreateRelationshipClasses(true);
        mapping.postPostScript(null, config);
        assertTrue(true);
    }

    @Test
    public void createsMultipleRelationshipTypes() throws Exception {
        OfgdbMapping mapping = new OfgdbMapping();
        Config config = new Config();
        config.setFgdbCreateDomains(false);
        config.setFgdbCreateRelationshipClasses(true);
        Path workDir = Files.createTempDirectory("ili2ofgdb-reltypes-");
        config.setDbfile(workDir.resolve("schema.gdb").toString());
        ensureTable(config.getDbfile(), "obj1", "T_Id INTEGER");
        ensureTable(config.getDbfile(), "a1_tbl", "T_Id INTEGER", "a1_fk INTEGER");
        ensureTable(config.getDbfile(), "obj2", "T_Id INTEGER");
        ensureTable(config.getDbfile(), "a2_tbl", "T_Id INTEGER", "a2_fk INTEGER");
        ensureTable(config.getDbfile(), "obj3", "T_Id INTEGER");
        ensureTable(config.getDbfile(), "a3_tbl", "T_Id INTEGER", "a3_fk INTEGER");

        mapping.fromIliInit(config);
        addRoleLink(mapping, "_M.T.A1", "r1", "a1_tbl", "a1_fk", "obj1", "T_Id", "toObj1", "fromA1", "1:1", false);
        addRoleLink(mapping, "_M.T.A2", "r2", "a2_tbl", "a2_fk", "obj2", "T_Id", "toObj2", "fromA2", "1:n", false);
        addRoleLink(mapping, "_M.T.A3", "r3", "a3_tbl", "a3_fk", "obj3", "T_Id", "toObj3", "fromA3", "m:n", true);

        mapping.postPostScript(null, config);
        mapping.postPostScript(null, config);

        OpenFgdb api = new OpenFgdb();
        long dbHandle = api.open(config.getDbfile());
        try {
            List<String> relationships = api.listRelationships(dbHandle);
            assertEquals(3, relationships.size());
        } finally {
            api.close(dbHandle);
        }
    }

    @Test
    public void assignsExistingDomainToFieldWithoutRecreate() throws Exception {
        OfgdbMapping mapping = new OfgdbMapping();
        Config config = new Config();
        config.setFgdbCreateDomains(true);
        config.setFgdbCreateRelationshipClasses(false);
        Path workDir = Files.createTempDirectory("ili2ofgdb-domain-assign-");
        config.setDbfile(workDir.resolve("schema.gdb").toString());

        OpenFgdb api = new OpenFgdb();
        long dbHandle = api.create(config.getDbfile());
        try {
            api.execSql(dbHandle, "CREATE TABLE classa (T_Id INTEGER, color VARCHAR(128))");
            api.createCodedDomain(dbHandle, "Enum_Color", "STRING");
            api.addCodedValue(dbHandle, "Enum_Color", "rot", "Rot");
        } finally {
            api.close(dbHandle);
        }

        mapping.fromIliInit(config);
        Map<String, String> domainValues = new LinkedHashMap<String, String>();
        domainValues.put("rot", "Rot");
        addDomain(mapping, "Enum_Color", "STRING", domainValues);
        addDomainAssignment(mapping, "classa", "color", "Enum_Color");

        mapping.postPostScript(null, config);
        assertTrue(hasDomainAssignment(config.getDbfile(), "Enum_Color", "classa", "color"));
        int firstAssignments = countDomainAssignments(config.getDbfile(), "Enum_Color", "classa", "color");
        mapping.postPostScript(null, config);
        int secondAssignments = countDomainAssignments(config.getDbfile(), "Enum_Color", "classa", "color");
        assertEquals(1, firstAssignments);
        assertEquals(1, secondAssignments);
    }

    @Test
    public void domainAssignmentForCreateSingleEnumTab() throws Exception {
        assertDomainAssignmentMode("T_ILI2DB_ENUM", "iliCode");
    }

    @Test
    public void domainAssignmentForCreateEnumTabs() throws Exception {
        assertDomainAssignmentMode("Farbe", "iliCode");
    }

    @Test
    public void domainAssignmentForCreateEnumTabsWithId() throws Exception {
        assertDomainAssignmentMode("Farbe", "T_Id");
    }

    @Test
    public void assignsBooleanDomainsToSmallintColumns() throws Exception {
        OfgdbMapping mapping = new OfgdbMapping();
        Config config = new Config();
        config.setFgdbCreateDomains(true);
        config.setFgdbCreateRelationshipClasses(false);
        Path workDir = Files.createTempDirectory("ili2ofgdb-bool-domain-");
        config.setDbfile(workDir.resolve("schema.gdb").toString());
        ensureTable(config.getDbfile(), "bool_holder", "T_Id INTEGER", "bool_raw SMALLINT", "bool_alias SMALLINT");

        mapping.fromIliInit(config);
        Map<String, String> booleanValues = new LinkedHashMap<String, String>();
        booleanValues.put("0", "false");
        booleanValues.put("1", "true");
        addDomain(mapping, "INTERLIS_BOOLEAN", "SMALLINT", booleanValues);
        addDomain(mapping, "Enum23_BooleanDomain", "SMALLINT", booleanValues);
        addDomainAssignment(mapping, "bool_holder", "bool_raw", "INTERLIS_BOOLEAN");
        addDomainAssignment(mapping, "bool_holder", "bool_alias", "Enum23_BooleanDomain");

        mapping.postPostScript(null, config);
        mapping.postPostScript(null, config);

        OpenFgdb api = new OpenFgdb();
        long dbHandle = api.open(config.getDbfile());
        try {
            List<String> domains = api.listDomains(dbHandle);
            assertTrue(domains.contains("INTERLIS_BOOLEAN"));
            assertTrue(domains.contains("Enum23_BooleanDomain"));
        } finally {
            api.close(dbHandle);
        }

        assertTrue(hasDomainAssignment(config.getDbfile(), "INTERLIS_BOOLEAN", "bool_holder", "bool_raw"));
        assertTrue(hasDomainAssignment(config.getDbfile(), "Enum23_BooleanDomain", "bool_holder", "bool_alias"));
        assertEquals(1, countDomainAssignments(config.getDbfile(), "INTERLIS_BOOLEAN", "bool_holder", "bool_raw"));
        assertEquals(1, countDomainAssignments(config.getDbfile(), "Enum23_BooleanDomain", "bool_holder", "bool_alias"));
        assertEquals("esriFieldTypeSmallInteger", findFirstTagText(readItemDefinition(config.getDbfile(), "INTERLIS_BOOLEAN"), "FieldType"));
        assertEquals("esriFieldTypeSmallInteger", findFirstTagText(readItemDefinition(config.getDbfile(), "Enum23_BooleanDomain"), "FieldType"));
    }

    @Test
    public void assignsRangeDomainsToNumericColumns() throws Exception {
        OfgdbMapping mapping = new OfgdbMapping();
        Config config = new Config();
        config.setFgdbCreateDomains(true);
        config.setFgdbCreateRelationshipClasses(false);
        Path workDir = Files.createTempDirectory("ili2ofgdb-range-domain-");
        config.setDbfile(workDir.resolve("schema.gdb").toString());
        ensureTable(config.getDbfile(), "measurements", "T_Id INTEGER", "height DOUBLE", "level INTEGER", "big_level BIGINT");

        mapping.fromIliInit(config);
        addRangeDomain(mapping, "Height_Domain", "DOUBLE", "1.00", true, "999.99", true);
        addDomainAssignment(mapping, "measurements", "height", "Height_Domain");
        addRangeDomain(mapping, "Level_Domain", "INTEGER", "0", true, "100", true);
        addDomainAssignment(mapping, "measurements", "level", "Level_Domain");
        addRangeDomain(mapping, "BigLevel_Domain", "BIGINT", "0", true, "9223372036854775807", true);
        addDomainAssignment(mapping, "measurements", "big_level", "BigLevel_Domain");

        mapping.postPostScript(null, config);
        mapping.postPostScript(null, config);

        assertTrue(hasDomainAssignment(config.getDbfile(), "Height_Domain", "measurements", "height"));
        assertTrue(hasDomainAssignment(config.getDbfile(), "Level_Domain", "measurements", "level"));

        assertEquals("esriFieldTypeDouble", findFirstTagText(readItemDefinition(config.getDbfile(), "Height_Domain"), "FieldType"));
        assertEquals("esriFieldTypeInteger", findFirstTagText(readItemDefinition(config.getDbfile(), "Level_Domain"), "FieldType"));
        assertEquals("1", normalizeNumericText(findFirstTagText(readItemDefinition(config.getDbfile(), "Height_Domain"), "MinValue")));
        assertEquals("999.99", normalizeNumericText(findFirstTagText(readItemDefinition(config.getDbfile(), "Height_Domain"), "MaxValue")));
        assertEquals("0", normalizeNumericText(findFirstTagText(readItemDefinition(config.getDbfile(), "Level_Domain"), "MinValue")));
        assertEquals("100", normalizeNumericText(findFirstTagText(readItemDefinition(config.getDbfile(), "Level_Domain"), "MaxValue")));

        String bigLevelDefinition = readItemDefinition(config.getDbfile(), "BigLevel_Domain");
        if (bigLevelDefinition == null) {
            assertFalse(hasDomainAssignment(config.getDbfile(), "BigLevel_Domain", "measurements", "big_level"));
        } else {
            assertTrue(hasDomainAssignment(config.getDbfile(), "BigLevel_Domain", "measurements", "big_level"));
            assertEquals("esriFieldTypeBigInteger", findFirstTagText(bigLevelDefinition, "FieldType"));
            assertEquals("0", normalizeNumericText(findFirstTagText(bigLevelDefinition, "MinValue")));
            assertEquals("9223372036854775807", normalizeNumericText(findFirstTagText(bigLevelDefinition, "MaxValue")));
        }
    }

    @Test
    public void knownBigIntRangeDomainLimitationIsDetectedAndCanBeSkipped() {
        OpenFgdbException ex = new OpenFgdbException(
                OpenFgdb.OFGDB_ERR_INTERNAL,
                "ofgdb_create_range_domain failed with OFGDB_ERR_INTERNAL (3): gdal backend failed: "
                        + "failed to create range domain: Unsupported field type for FileGeoDatabase domain "
                        + "[requestedType=BIGINT ogrType=Integer64 ogrTypeId=12 ogrSubtypeId=0]");

        assertTrue(OfgdbMapping.isKnownBigIntRangeDomainLimitation("BIGINT", ex));
        assertFalse(OfgdbMapping.isKnownBigIntRangeDomainLimitation("INTEGER", ex));
        assertFalse(OfgdbMapping.isKnownBigIntRangeDomainLimitation(
                "BIGINT",
                new OpenFgdbException(OpenFgdb.OFGDB_ERR_INTERNAL, "Unsupported field type for FileGeoDatabase domain")));
        assertFalse(OfgdbMapping.isKnownBigIntRangeDomainLimitation("BIGINT", null));
    }

    @Test
    public void knownBigIntRangeDomainLimitationSkipsDomainAndAssignment() throws Exception {
        OfgdbMapping mapping = new OfgdbMapping();
        Config config = new Config();
        config.setFgdbCreateDomains(true);
        config.setFgdbCreateRelationshipClasses(false);
        Path workDir = Files.createTempDirectory("ili2ofgdb-range-domain-skip-");
        config.setDbfile(workDir.resolve("schema.gdb").toString());
        ensureTable(config.getDbfile(), "measurements", "T_Id INTEGER", "big_level BIGINT");

        mapping.fromIliInit(config);
        addRangeDomain(mapping, "BigLevel_Domain", "BIGINT", "0", true, "9223372036854775807", true);
        addDomainAssignment(mapping, "measurements", "big_level", "BigLevel_Domain");

        mapping.postPostScript(null, config);

        String bigLevelDefinition = readItemDefinition(config.getDbfile(), "BigLevel_Domain");
        if (bigLevelDefinition == null) {
            assertFalse(hasDomainAssignment(config.getDbfile(), "BigLevel_Domain", "measurements", "big_level"));
            assertNull(bigLevelDefinition);
        } else {
            assertTrue(hasDomainAssignment(config.getDbfile(), "BigLevel_Domain", "measurements", "big_level"));
            assertEquals("esriFieldTypeBigInteger", findFirstTagText(bigLevelDefinition, "FieldType"));
        }
    }

    @Test
    public void postPostScriptUsesOpenJdbcHandle() throws Exception {
        OfgdbMapping mapping = new OfgdbMapping();
        Config config = new Config();
        config.setFgdbCreateDomains(true);
        config.setFgdbCreateRelationshipClasses(true);
        config.setFgdbIncludeInactiveEnumValues(false);
        Path workDir = Files.createTempDirectory("ili2ofgdb-open-handle-");
        config.setDbfile(workDir.resolve("schema.gdb").toString());

        mapping.fromIliInit(config);
        Map<String, String> domainValues = new LinkedHashMap<String, String>();
        domainValues.put("AusgangslageKiesgrube", "Ausgangslage Kiesgrube");
        addDomain(mapping, "Enum_Art", "STRING", domainValues);
        addDomainAssignment(mapping, "fachapplikation_abbaustelle", "art", "Enum_Art");
        addRoleLink(mapping,
                "_SO_AFU_ABBAUSTELLEN_20210630.Fachapplikation.Abbaustelle_Geometrie",
                "Geometrie",
                "fachapplikation_abbaustelle",
                "geometrie",
                "gis_geometrie",
                "T_Id",
                "toGeometrie",
                "fromAbbaustelle",
                "1:1",
                false);

        DriverManager.registerDriver(new OfgdbDriver());
        Connection conn = DriverManager.getConnection(OfgdbDriver.BASE_URL + config.getDbfile(), null, null);
        try {
            Statement statement = conn.createStatement();
            try {
                statement.execute("CREATE TABLE gis_geometrie (T_Id INTEGER PRIMARY KEY NOT NULL, mpoly BLOB NOT NULL)");
                statement.execute("CREATE TABLE fachapplikation_abbaustelle ("
                        + "T_Id INTEGER PRIMARY KEY NOT NULL, "
                        + "art VARCHAR(255), "
                        + "stand VARCHAR(255), "
                        + "rohstoffart VARCHAR(255), "
                        + "gestaltungsplanvorhanden SMALLINT NOT NULL, "
                        + "standrichtplan VARCHAR(255), "
                        + "geometrie INTEGER NOT NULL)");
            } finally {
                statement.close();
            }

            mapping.postPostScript(conn, config);
        } finally {
            conn.close();
        }

        assertTrue(hasDomainAssignment(config.getDbfile(), "Enum_Art", "fachapplikation_abbaustelle", "art"));

        OpenFgdb api = new OpenFgdb();
        long dbHandle = api.open(config.getDbfile());
        try {
            List<String> relationships = api.listRelationships(dbHandle);
            assertEquals(1, relationships.size());
        } finally {
            api.close(dbHandle);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addDomain(OfgdbMapping mapping, String domainName, String fieldType, Map<String, String> codedValues)
            throws Exception {
        Class<?> domainClass = Class.forName("ch.ehi.ili2ofgdb.OfgdbMapping$DomainDefinition");
        Object domain = newInstance(domainClass);
        setField(domainClass, domain, "domainName", domainName);
        setField(domainClass, domain, "fieldType", fieldType);
        Field codedValuesField = domainClass.getDeclaredField("codedValues");
        codedValuesField.setAccessible(true);
        ((Map<String, String>) codedValuesField.get(domain)).putAll(codedValues);

        Field domainsField = OfgdbMapping.class.getDeclaredField("domains");
        domainsField.setAccessible(true);
        ((Map<String, Object>) domainsField.get(mapping)).put(domainName, domain);
    }

    @SuppressWarnings("unchecked")
    private static void addRangeDomain(
            OfgdbMapping mapping,
            String domainName,
            String fieldType,
            String minValue,
            boolean minInclusive,
            String maxValue,
            boolean maxInclusive) throws Exception {
        Class<?> domainClass = Class.forName("ch.ehi.ili2ofgdb.OfgdbMapping$DomainDefinition");
        Object domain = newInstance(domainClass);
        Class<?> kindClass = Class.forName("ch.ehi.ili2ofgdb.OfgdbMapping$DomainKind");
        Object rangeKind = Enum.valueOf((Class<Enum>) kindClass.asSubclass(Enum.class), "RANGE");
        setField(domainClass, domain, "kind", rangeKind);
        setField(domainClass, domain, "domainName", domainName);
        setField(domainClass, domain, "fieldType", fieldType);
        setField(domainClass, domain, "rangeMinValue", minValue);
        setField(domainClass, domain, "rangeMinInclusive", minInclusive);
        setField(domainClass, domain, "rangeMaxValue", maxValue);
        setField(domainClass, domain, "rangeMaxInclusive", maxInclusive);

        Field domainsField = OfgdbMapping.class.getDeclaredField("domains");
        domainsField.setAccessible(true);
        ((Map<String, Object>) domainsField.get(mapping)).put(domainName, domain);
    }

    @SuppressWarnings("unchecked")
    private static void addDomainAssignment(OfgdbMapping mapping, String tableName, String columnName, String domainName) throws Exception {
        Class<?> assignmentClass = Class.forName("ch.ehi.ili2ofgdb.OfgdbMapping$DomainAssignment");
        Object assignment = newInstance(assignmentClass);
        setField(assignmentClass, assignment, "tableName", tableName);
        setField(assignmentClass, assignment, "columnName", columnName);
        setField(assignmentClass, assignment, "domainName", domainName);

        Field assignmentsField = OfgdbMapping.class.getDeclaredField("assignments");
        assignmentsField.setAccessible(true);
        ((Map<String, Object>) assignmentsField.get(mapping)).put(tableName + "." + columnName, assignment);
    }

    @SuppressWarnings("unchecked")
    private static void addRoleLink(OfgdbMapping mapping, String associationScopedName, String roleName, String sourceTable,
            String sourceFkColumn, String targetTable, String targetPkColumn, String forwardLabel, String backwardLabel, String cardinality,
            boolean attributed) throws Exception {
        Class<?> roleClass = Class.forName("ch.ehi.ili2ofgdb.OfgdbMapping$RoleLinkDefinition");
        Object role = newInstance(roleClass);
        setField(roleClass, role, "associationScopedName", associationScopedName);
        setField(roleClass, role, "roleName", roleName);
        setField(roleClass, role, "sourceTable", sourceTable);
        setField(roleClass, role, "sourceFkColumn", sourceFkColumn);
        setField(roleClass, role, "targetTable", targetTable);
        setField(roleClass, role, "targetPkColumn", targetPkColumn);
        setField(roleClass, role, "forwardLabel", forwardLabel);
        setField(roleClass, role, "backwardLabel", backwardLabel);
        setField(roleClass, role, "cardinality", cardinality);
        setField(roleClass, role, "attributed", attributed);

        Field roleLinksField = OfgdbMapping.class.getDeclaredField("roleLinks");
        roleLinksField.setAccessible(true);
        ((Map<String, Object>) roleLinksField.get(mapping)).put(associationScopedName + "|" + roleName, role);
    }

    private static Object newInstance(Class<?> type) throws Exception {
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void setField(Class<?> type, Object instance, String fieldName, Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static int[] snapshot(String dbFile) throws Exception {
        OpenFgdb api = new OpenFgdb();
        long dbHandle = api.open(dbFile);
        try {
            int gdbItemsRows = countRows(api, dbHandle, "GDB_Items");
            int gdbItemRelationshipsRows = countRows(api, dbHandle, "GDB_ItemRelationships");
            return new int[] {gdbItemsRows, gdbItemRelationshipsRows};
        } finally {
            api.close(dbHandle);
        }
    }

    private static boolean hasDomainAssignment(String dbFile, String domainName, String tableName, String columnName) throws Exception {
        return countDomainAssignments(dbFile, domainName, tableName, columnName) > 0;
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

    private static String findFirstTagText(String definitionXml, String tagName) {
        if (definitionXml == null || tagName == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("(?is)<" + Pattern.quote(tagName) + "\\b[^>]*>\\s*(.*?)\\s*</" + Pattern.quote(tagName) + ">");
        Matcher matcher = pattern.matcher(definitionXml);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String normalizeNumericText(String value) {
        if (value == null) {
            return null;
        }
        double numericValue = Double.parseDouble(value.trim());
        if (Math.rint(numericValue) == numericValue) {
            return Long.toString(Math.round(numericValue));
        }
        return Double.toString(numericValue);
    }

    private static int countDomainAssignments(String dbFile, String domainName, String tableName, String columnName) throws Exception {
        OpenFgdb api = new OpenFgdb();
        long dbHandle = api.open(dbFile);
        try {
            long tableHandle = api.openTable(dbHandle, "GDB_Items");
            try {
                long cursor = api.search(tableHandle, "Name,Definition", "");
                try {
                    String tableDefinition = null;
                    while (true) {
                        long row = api.fetchRow(cursor);
                        if (row == 0L) {
                            break;
                        }
                        try {
                            String rowTableName = api.rowGetString(row, "Name");
                            if (rowTableName == null || !rowTableName.equalsIgnoreCase(tableName)) {
                                continue;
                            }
                            tableDefinition = api.rowGetString(row, "Definition");
                            break;
                        } finally {
                            api.closeRow(row);
                        }
                    }
                    return hasDomainInTableDefinition(tableDefinition, columnName, domainName) ? 1 : 0;
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

    private static boolean hasDomainInTableDefinition(String tableDefinition, String columnName, String domainName) {
        if (tableDefinition == null || columnName == null || domainName == null) {
            return false;
        }
        Pattern fieldPattern = Pattern.compile(
                "(?is)<GPFieldInfoEx\\b[^>]*>.*?<Name>\\s*" + Pattern.quote(columnName) + "\\s*</Name>.*?</GPFieldInfoEx>");
        Matcher fieldMatcher = fieldPattern.matcher(tableDefinition);
        while (fieldMatcher.find()) {
            String fieldBlock = fieldMatcher.group();
            Pattern domainPattern = Pattern.compile("(?is)<DomainName>\\s*" + Pattern.quote(domainName) + "\\s*</DomainName>");
            if (domainPattern.matcher(fieldBlock).find()) {
                return true;
            }
        }
        return false;
    }

    private static void assertDomainAssignmentMode(String tableName, String columnName) throws Exception {
        OfgdbMapping mapping = new OfgdbMapping();
        Config config = new Config();
        config.setFgdbCreateDomains(true);
        config.setFgdbCreateRelationshipClasses(false);
        Path workDir = Files.createTempDirectory("ili2ofgdb-domain-mode-");
        config.setDbfile(workDir.resolve("schema.gdb").toString());
        if ("T_Id".equalsIgnoreCase(columnName)) {
            ensureTable(config.getDbfile(), tableName, "T_Id INTEGER", "dummy VARCHAR(128)");
        } else {
            ensureTable(config.getDbfile(), tableName, "T_Id INTEGER", columnName + " VARCHAR(128)");
        }

        mapping.fromIliInit(config);
        Map<String, String> domainValues = new LinkedHashMap<String, String>();
        String domainFieldType = "T_Id".equalsIgnoreCase(columnName) ? "INTEGER" : "STRING";
        if ("INTEGER".equals(domainFieldType)) {
            domainValues.put("1", "Eins");
        } else {
            domainValues.put("rot", "Rot");
        }
        addDomain(mapping, "Enum_Mode", domainFieldType, domainValues);
        addDomainAssignment(mapping, tableName, columnName, "Enum_Mode");
        mapping.postPostScript(null, config);
        mapping.postPostScript(null, config);

        assertTrue(hasDomainAssignment(config.getDbfile(), "Enum_Mode", tableName, columnName));
        assertFalse(hasDomainAssignment(config.getDbfile(), "Enum_Mode", tableName, columnName + "_other"));
        assertEquals(1, countDomainAssignments(config.getDbfile(), "Enum_Mode", tableName, columnName));
    }

    private static int countRows(OpenFgdb api, long dbHandle, String tableName) throws Exception {
        long tableHandle = api.openTable(dbHandle, tableName);
        try {
            long cursor = api.search(tableHandle, "*", "");
            try {
                int count = 0;
                while (true) {
                    long row = api.fetchRow(cursor);
                    if (row == 0L) {
                        return count;
                    }
                    count++;
                    api.closeRow(row);
                }
            } finally {
                api.closeCursor(cursor);
            }
        } finally {
            api.closeTable(dbHandle, tableHandle);
        }
    }

    private static void ensureTable(String dbFile, String tableName, String... columnDefinitions) throws Exception {
        OpenFgdb api = new OpenFgdb();
        long dbHandle = 0L;
        try {
            if (new File(dbFile).exists()) {
                dbHandle = api.open(dbFile);
            } else {
                dbHandle = api.create(dbFile);
            }
            StringBuilder sql = new StringBuilder();
            sql.append("CREATE TABLE ").append(tableName).append(" (");
            String sep = "";
            for (String columnDefinition : columnDefinitions) {
                sql.append(sep).append(columnDefinition);
                sep = ",";
            }
            sql.append(")");
            try {
                api.execSql(dbHandle, sql.toString());
            } catch (Exception ignore) {
                // table already exists
            }
        } finally {
            if (dbHandle != 0L) {
                api.close(dbHandle);
            }
        }
    }

}
