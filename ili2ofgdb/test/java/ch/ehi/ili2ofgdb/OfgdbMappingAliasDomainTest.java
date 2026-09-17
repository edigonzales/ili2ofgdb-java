package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.gui.Config;
import ch.ehi.sqlgen.repository.DbColBoolean;
import ch.ehi.sqlgen.repository.DbColDecimal;
import ch.ehi.sqlgen.repository.DbColNumber;
import ch.ehi.sqlgen.repository.DbColVarchar;
import ch.ehi.sqlgen.repository.DbTable;
import ch.ehi.sqlgen.repository.DbTableName;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OfgdbMappingAliasDomainTest {

    @Test
    public void fixupAttributeKeepsEnumAliasAndCreatesBooleanDomainsForScalarColumns() throws Exception {
        TransferDescription td = compileModel("test/data/Enum23/Enum23.ili");

        Config config = new Config();
        new OfgdbMain().initConfig(config);
        config.setMaxSqlNameLength("60");
        config.setFgdbCreateDomains(true);
        config.setTransientObject(Config.TRANSIENT_MODEL, td);

        OfgdbMapping mapping = new OfgdbMapping();
        mapping.fromIliInit(config);

        AttributeDef enumAliasAttr = (AttributeDef) td.getElement("Enum23.TestA.ClassA1.attr2");
        AttributeDef rawBoolAttr = (AttributeDef) td.getElement("Enum23.TestA.ClassA1.attr4");
        AttributeDef boolAliasAttr = (AttributeDef) td.getElement("Enum23.TestA.ClassA1.attr5");
        assertNotNull(enumAliasAttr);
        assertNotNull(rawBoolAttr);
        assertNotNull(boolAliasAttr);

        DbTable table = new DbTable();
        table.setName(new DbTableName(null, "c"));

        DbColVarchar enumColumn = new DbColVarchar();
        enumColumn.setName("enumattr");
        DbColBoolean boolAliasColumn = new DbColBoolean();
        boolAliasColumn.setName("boolalias");
        DbColBoolean boolRawColumn = new DbColBoolean();
        boolRawColumn.setName("boolraw");

        mapping.fixupAttribute(table, enumColumn, enumAliasAttr);
        mapping.fixupAttribute(table, boolAliasColumn, boolAliasAttr);
        mapping.fixupAttribute(table, boolRawColumn, rawBoolAttr);

        Map<String, ?> domains = readMapField(mapping, "domains");
        Map<String, ?> assignments = readMapField(mapping, "assignments");

        String enumDomainName = "Enum23_Enum1";
        String boolAliasDomainName = "Enum23_BooleanDomain";
        String boolRawDomainName = "INTERLIS_BOOLEAN";

        assertTrue(domains.containsKey(enumDomainName));
        assertTrue(domains.containsKey(boolAliasDomainName));
        assertTrue(domains.containsKey(boolRawDomainName));

        assertTrue(assignments.containsKey("c.enumattr"));
        assertTrue(assignments.containsKey("c.boolalias"));
        assertTrue(assignments.containsKey("c.boolraw"));

        Object enumDomainDefinition = domains.get(enumDomainName);
        Map<String, String> enumValues = readMapField(enumDomainDefinition, "codedValues");
        assertTrue(enumValues.containsKey("Test1"));
        assertTrue(enumValues.containsKey("Test2_ele"));

        Object boolAliasDomainDefinition = domains.get(boolAliasDomainName);
        assertEquals("SMALLINT", readStringField(boolAliasDomainDefinition, "fieldType"));
        Map<String, String> boolAliasValues = readMapField(boolAliasDomainDefinition, "codedValues");
        assertEquals("false", boolAliasValues.get("0"));
        assertEquals("true", boolAliasValues.get("1"));

        Object boolRawDomainDefinition = domains.get(boolRawDomainName);
        assertEquals("SMALLINT", readStringField(boolRawDomainDefinition, "fieldType"));
        Map<String, String> boolRawValues = readMapField(boolRawDomainDefinition, "codedValues");
        assertEquals("false", boolRawValues.get("0"));
        assertEquals("true", boolRawValues.get("1"));
    }

    @Test
    public void fixupAttributeSkipsBooleanDomainForNonBooleanSqlColumn() throws Exception {
        TransferDescription td = compileModel("test/data/Enum23/Enum23.ili");

        Config config = new Config();
        new OfgdbMain().initConfig(config);
        config.setMaxSqlNameLength("60");
        config.setFgdbCreateDomains(true);
        config.setTransientObject(Config.TRANSIENT_MODEL, td);

        OfgdbMapping mapping = new OfgdbMapping();
        mapping.fromIliInit(config);

        AttributeDef boolAliasAttr = (AttributeDef) td.getElement("Enum23.TestA.ClassA1.attr5");
        assertNotNull(boolAliasAttr);

        DbTable table = new DbTable();
        table.setName(new DbTableName(null, "c"));

        DbColVarchar boolColumn = new DbColVarchar();
        boolColumn.setName("boolattr");

        mapping.fixupAttribute(table, boolColumn, boolAliasAttr);

        Map<String, ?> domains = readMapField(mapping, "domains");
        Map<String, ?> assignments = readMapField(mapping, "assignments");

        assertFalse(domains.containsKey("Enum23_BooleanDomain"));
        assertFalse(assignments.containsKey("c.boolattr"));
    }

    @Test
    public void fixupAttributeSkipsAllDomainsWhenDisabled() throws Exception {
        TransferDescription td = compileModel("test/data/Enum23/Enum23.ili");

        Config config = new Config();
        new OfgdbMain().initConfig(config);
        config.setMaxSqlNameLength("60");
        config.setFgdbCreateDomains(false);
        config.setTransientObject(Config.TRANSIENT_MODEL, td);

        OfgdbMapping mapping = new OfgdbMapping();
        mapping.fromIliInit(config);

        AttributeDef enumAliasAttr = (AttributeDef) td.getElement("Enum23.TestA.ClassA1.attr2");
        AttributeDef rawBoolAttr = (AttributeDef) td.getElement("Enum23.TestA.ClassA1.attr4");
        assertNotNull(enumAliasAttr);
        assertNotNull(rawBoolAttr);

        DbTable table = new DbTable();
        table.setName(new DbTableName(null, "c"));

        DbColVarchar enumColumn = new DbColVarchar();
        enumColumn.setName("enumattr");
        DbColBoolean boolRawColumn = new DbColBoolean();
        boolRawColumn.setName("boolraw");

        mapping.fixupAttribute(table, enumColumn, enumAliasAttr);
        mapping.fixupAttribute(table, boolRawColumn, rawBoolAttr);

        Map<String, ?> domains = readMapField(mapping, "domains");
        Map<String, ?> assignments = readMapField(mapping, "assignments");
        assertTrue(domains.isEmpty());
        assertTrue(assignments.isEmpty());
    }

    @Test
    public void fixupAttributeCreatesRangeDomainsForNumericColumns() throws Exception {
        TransferDescription td = compileModel("ili2ofgdb/test/data/mapping/OfgdbRangeDomainTest.ili");

        Config config = new Config();
        new OfgdbMain().initConfig(config);
        config.setMaxSqlNameLength("60");
        config.setFgdbCreateDomains(true);
        config.setCreateNumChecks(true);
        config.setTransientObject(Config.TRANSIENT_MODEL, td);

        OfgdbMapping mapping = new OfgdbMapping();
        mapping.fromIliInit(config);

        AttributeDef aliasAttr = (AttributeDef) td.getElement("OfgdbRangeDomainTest.T.C.aliasAttr");
        AttributeDef bigIntAttr = (AttributeDef) td.getElement("OfgdbRangeDomainTest.T.C.bigIntAttr");
        AttributeDef inlineAttr = (AttributeDef) td.getElement("OfgdbRangeDomainTest.T.C.inlineAttr");
        assertNotNull(aliasAttr);
        assertNotNull(bigIntAttr);
        assertNotNull(inlineAttr);

        DbTable table = new DbTable();
        table.setName(new DbTableName(null, "c"));

        DbColNumber aliasColumn = new DbColNumber();
        aliasColumn.setName("aliasattr");
        aliasColumn.setSize(3);
        DbColDecimal inlineColumn = new DbColDecimal();
        inlineColumn.setName("inlineattr");
        inlineColumn.setSize(5);
        inlineColumn.setPrecision(2);
        DbColNumber bigIntColumn = new DbColNumber();
        bigIntColumn.setName("bigintattr");
        bigIntColumn.setSize(19);

        mapping.fixupAttribute(table, aliasColumn, aliasAttr);
        mapping.fixupAttribute(table, bigIntColumn, bigIntAttr);
        mapping.fixupAttribute(table, inlineColumn, inlineAttr);

        Map<String, ?> domains = readMapField(mapping, "domains");
        Map<String, ?> assignments = readMapField(mapping, "assignments");

        Object aliasDomain = domains.get("OfgdbRangeDomainTest_IntDomain");
        assertNotNull(aliasDomain);
        assertEquals("INTEGER", readStringField(aliasDomain, "fieldType"));
        assertEquals("0", readStringField(aliasDomain, "rangeMinValue"));
        assertEquals("100", readStringField(aliasDomain, "rangeMaxValue"));

        Object bigIntDomain = domains.get("OfgdbRangeDomainTest_BigIntDomain");
        assertNotNull(bigIntDomain);
        assertEquals("BIGINT", readStringField(bigIntDomain, "fieldType"));
        assertEquals("0", readStringField(bigIntDomain, "rangeMinValue"));
        assertEquals("9223372036854775807", readStringField(bigIntDomain, "rangeMaxValue"));

        Object inlineDomain = domains.get("OfgdbRangeDomainTest_T_C_inlineAttr");
        assertNotNull(inlineDomain);
        assertEquals("DOUBLE", readStringField(inlineDomain, "fieldType"));
        assertEquals("1.00", readStringField(inlineDomain, "rangeMinValue"));
        assertEquals("999.99", readStringField(inlineDomain, "rangeMaxValue"));

        assertTrue(assignments.containsKey("c.aliasattr"));
        assertTrue(assignments.containsKey("c.bigintattr"));
        assertTrue(assignments.containsKey("c.inlineattr"));
    }

    @Test
    public void fixupAttributeSkipsRangeDomainsWhenNumericChecksDisabled() throws Exception {
        TransferDescription td = compileModel("ili2ofgdb/test/data/mapping/OfgdbRangeDomainTest.ili");

        Config config = new Config();
        new OfgdbMain().initConfig(config);
        config.setMaxSqlNameLength("60");
        config.setFgdbCreateDomains(true);
        config.setCreateNumChecks(false);
        config.setTransientObject(Config.TRANSIENT_MODEL, td);

        OfgdbMapping mapping = new OfgdbMapping();
        mapping.fromIliInit(config);

        AttributeDef aliasAttr = (AttributeDef) td.getElement("OfgdbRangeDomainTest.T.C.aliasAttr");
        assertNotNull(aliasAttr);

        DbTable table = new DbTable();
        table.setName(new DbTableName(null, "c"));

        DbColNumber aliasColumn = new DbColNumber();
        aliasColumn.setName("aliasattr");
        aliasColumn.setSize(3);

        mapping.fixupAttribute(table, aliasColumn, aliasAttr);

        Map<String, ?> domains = readMapField(mapping, "domains");
        Map<String, ?> assignments = readMapField(mapping, "assignments");
        assertFalse(domains.containsKey("OfgdbRangeDomainTest_IntDomain"));
        assertFalse(assignments.containsKey("c.aliasattr"));
    }

    private static TransferDescription compileModel(String modelPath) throws Exception {
        Configuration ili2cConfig = new Configuration();
        ili2cConfig.addFileEntry(new FileEntry(modelPath, FileEntryKind.ILIMODELFILE));
        TransferDescription td = ch.interlis.ili2c.Ili2c.runCompiler(ili2cConfig);
        assertNotNull(td);
        return td;
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> readMapField(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<String, T>) field.get(instance);
    }

    private static String readStringField(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(instance);
    }
}
