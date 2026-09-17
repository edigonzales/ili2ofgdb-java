package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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

/**
 * Unit tests for the domain collection of {@link OfgdbMapping}: the mapping registers the domain
 * name on the column (custom value) and keeps the domain definition for the later DDL.
 */
public class OfgdbMappingAliasDomainTest {

    @Test
    public void fixupAttributeKeepsEnumAliasAndCreatesBooleanDomainsForScalarColumns() throws Exception {
        TransferDescription td = compileModel("test/data/Enum23/Enum23.ili");

        OfgdbMapping mapping = mapping(td, true, false);

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

        String enumDomainName = "Enum23_Enum1";
        String boolAliasDomainName = "Enum23_BooleanDomain";
        String boolRawDomainName = "INTERLIS_BOOLEAN";

        assertTrue(mapping.domains.containsKey(enumDomainName));
        assertTrue(mapping.domains.containsKey(boolAliasDomainName));
        assertTrue(mapping.domains.containsKey(boolRawDomainName));

        assertEquals(enumDomainName, enumColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));
        assertEquals(boolAliasDomainName, boolAliasColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));
        assertEquals(boolRawDomainName, boolRawColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));

        OfgdbMapping.DomainDefinition enumDomain = mapping.domains.get(enumDomainName);
        assertTrue(enumDomain.codedValues.containsKey("Test1"));
        assertTrue(enumDomain.codedValues.containsKey("Test2_ele"));

        OfgdbMapping.DomainDefinition boolAliasDomain = mapping.domains.get(boolAliasDomainName);
        assertEquals("SMALLINT", boolAliasDomain.fieldType);
        assertEquals("false", boolAliasDomain.codedValues.get("0"));
        assertEquals("true", boolAliasDomain.codedValues.get("1"));

        OfgdbMapping.DomainDefinition boolRawDomain = mapping.domains.get(boolRawDomainName);
        assertEquals("SMALLINT", boolRawDomain.fieldType);
        assertEquals("false", boolRawDomain.codedValues.get("0"));
        assertEquals("true", boolRawDomain.codedValues.get("1"));
    }

    @Test
    public void fixupAttributeSkipsBooleanDomainForNonBooleanSqlColumn() throws Exception {
        TransferDescription td = compileModel("test/data/Enum23/Enum23.ili");

        OfgdbMapping mapping = mapping(td, true, false);

        AttributeDef boolAliasAttr = (AttributeDef) td.getElement("Enum23.TestA.ClassA1.attr5");
        assertNotNull(boolAliasAttr);

        DbTable table = new DbTable();
        table.setName(new DbTableName(null, "c"));

        DbColVarchar boolColumn = new DbColVarchar();
        boolColumn.setName("boolattr");

        mapping.fixupAttribute(table, boolColumn, boolAliasAttr);

        assertFalse(mapping.domains.containsKey("Enum23_BooleanDomain"));
        assertNull(boolColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));
    }

    @Test
    public void fixupAttributeSkipsAllDomainsWhenDisabled() throws Exception {
        TransferDescription td = compileModel("test/data/Enum23/Enum23.ili");

        OfgdbMapping mapping = mapping(td, false, false);

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

        assertTrue(mapping.domains.isEmpty());
        assertNull(enumColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));
        assertNull(boolRawColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));
    }

    @Test
    public void fixupAttributeCreatesRangeDomainsForNumericColumns() throws Exception {
        TransferDescription td = compileModel("ili2ofgdb/test/data/mapping/OfgdbRangeDomainTest.ili");

        OfgdbMapping mapping = mapping(td, true, true);

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

        OfgdbMapping.DomainDefinition aliasDomain = mapping.domains.get("OfgdbRangeDomainTest_IntDomain");
        assertNotNull(aliasDomain);
        assertEquals("INTEGER", aliasDomain.fieldType);
        assertEquals("0", aliasDomain.rangeMinValue);
        assertEquals("100", aliasDomain.rangeMaxValue);

        OfgdbMapping.DomainDefinition bigIntDomain =
                mapping.domains.get("OfgdbRangeDomainTest_BigIntDomain");
        assertNotNull(bigIntDomain);
        assertEquals("BIGINT", bigIntDomain.fieldType);
        assertEquals("0", bigIntDomain.rangeMinValue);
        assertEquals("9223372036854775807", bigIntDomain.rangeMaxValue);

        OfgdbMapping.DomainDefinition inlineDomain =
                mapping.domains.get("OfgdbRangeDomainTest_T_C_inlineAttr");
        assertNotNull(inlineDomain);
        assertEquals("DOUBLE", inlineDomain.fieldType);
        assertEquals("1.00", inlineDomain.rangeMinValue);
        assertEquals("999.99", inlineDomain.rangeMaxValue);

        assertEquals("OfgdbRangeDomainTest_IntDomain",
                aliasColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));
        assertEquals("OfgdbRangeDomainTest_BigIntDomain",
                bigIntColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));
        assertEquals("OfgdbRangeDomainTest_T_C_inlineAttr",
                inlineColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));
    }

    @Test
    public void fixupAttributeSkipsRangeDomainsWhenNumericChecksDisabled() throws Exception {
        TransferDescription td = compileModel("ili2ofgdb/test/data/mapping/OfgdbRangeDomainTest.ili");

        OfgdbMapping mapping = mapping(td, true, false);

        AttributeDef aliasAttr = (AttributeDef) td.getElement("OfgdbRangeDomainTest.T.C.aliasAttr");
        assertNotNull(aliasAttr);

        DbTable table = new DbTable();
        table.setName(new DbTableName(null, "c"));

        DbColNumber aliasColumn = new DbColNumber();
        aliasColumn.setName("aliasattr");
        aliasColumn.setSize(3);

        mapping.fixupAttribute(table, aliasColumn, aliasAttr);

        assertFalse(mapping.domains.containsKey("OfgdbRangeDomainTest_IntDomain"));
        assertNull(aliasColumn.getCustomValue(OfgdbMapping.DOMAIN_CUSTOM_KEY));
    }

    private static OfgdbMapping mapping(TransferDescription td, boolean createDomains,
            boolean createNumChecks) {
        Config config = new Config();
        new OfgdbMain().initConfig(config);
        config.setMaxSqlNameLength("60");
        config.setValue(OfgdbMapping.FGDB_CREATE_DOMAINS, createDomains ? Config.TRUE : Config.FALSE);
        config.setCreateNumChecks(createNumChecks);
        config.setTransientObject(Config.TRANSIENT_MODEL, td);

        OfgdbMapping mapping = new OfgdbMapping();
        mapping.fromIliInit(config);
        return mapping;
    }

    private static TransferDescription compileModel(String modelPath) throws Exception {
        Configuration ili2cConfig = new Configuration();
        ili2cConfig.addFileEntry(new FileEntry(modelPath, FileEntryKind.ILIMODELFILE));
        TransferDescription td = ch.interlis.ili2c.Ili2c.runCompiler(ili2cConfig);
        assertNotNull(td);
        return td;
    }
}
