package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.sqlgen.DbUtility;
import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CodedValue;
import ch.so.agi.filegdb.catalog.CodedValueDomain;
import ch.so.agi.filegdb.catalog.Domain;
import ch.so.agi.filegdb.catalog.RangeDomain;
import ch.so.agi.filegdb.table.FileGdbFieldType;

/**
 * The offline DDL script has to be self-contained: domains cannot be recreated by the mapping (the
 * script is executed on an empty database without model knowledge), so the script contains {@code
 * CREATE DOMAIN} statements in front of the tables.
 */
public class OfgdbDomainScriptTest {
    private static final String OUT_DIR = "build/test-ofgdb/";

    @Test
    public void scriptRecreatesCodedDomain() throws Exception {
        String gdb = OUT_DIR + "OfgdbDomainScriptTestCoded.gdb";
        String scriptFile = OUT_DIR + "OfgdbDomainScriptTestCoded.sql";
        OfgdbTestSetup setup = new OfgdbTestSetup(gdb);
        setup.resetDb();
        new File(scriptFile).delete();

        File model = new File("test/data/Enum23/Enum23.ili");
        Config config = setup.initConfig(model.getPath(), gdb + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_SCRIPT);
        config.setCreateEnumDefs(Config.CREATE_ENUM_DEFS_MULTI);
        config.setCreatescript(scriptFile);
        Ili2db.run(config, null);

        String script = readText(scriptFile);
        assertTrue("script must create the coded domain",
                script.contains("CREATE DOMAIN Enum23_Enum1 AS STRING VALUES ("));

        executeScript(setup, scriptFile);

        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(gdb))) {
            Domain domain = database.domain("Enum23_Enum1").get();
            assertTrue("coded domain expected", domain instanceof CodedValueDomain);
            CodedValueDomain coded = (CodedValueDomain) domain;
            assertEquals(FileGdbFieldType.STRING, coded.fieldType());
            List<String> codes = new ArrayList<String>();
            for (CodedValue value : coded.values()) {
                codes.add(value.code());
            }
            assertTrue("Test1 in " + codes, codes.contains("Test1"));
            assertTrue("Test2_ele in " + codes, codes.contains("Test2_ele"));
        }
    }

    @Test
    public void scriptRecreatesRangeDomains() throws Exception {
        String gdb = OUT_DIR + "OfgdbDomainScriptTestRange.gdb";
        String scriptFile = OUT_DIR + "OfgdbDomainScriptTestRange.sql";
        OfgdbTestSetup setup = new OfgdbTestSetup(gdb);
        setup.resetDb();
        new File(scriptFile).delete();

        File model = new File("ili2ofgdb/test/data/mapping/OfgdbRangeDomainTest.ili");
        Config config = setup.initConfig(model.getPath(), gdb + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_SCRIPT);
        config.setCreateNumChecks(true);
        config.setCreatescript(scriptFile);
        Ili2db.run(config, null);

        String script = readText(scriptFile);
        assertTrue("script must create the integer range domain",
                script.contains(
                        "CREATE DOMAIN OfgdbRangeDomainTest_IntDomain AS INTEGER RANGE [0 .. 100]"));
        assertTrue("script must create the inline range domain",
                script.contains("CREATE DOMAIN OfgdbRangeDomainTest_T_C_inlineAttr AS DOUBLE RANGE [1.00 .. 999.99]"));

        executeScript(setup, scriptFile);

        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(gdb))) {
            RangeDomain intDomain =
                    (RangeDomain) database.domain("OfgdbRangeDomainTest_IntDomain").get();
            assertEquals(FileGdbFieldType.INT32, intDomain.fieldType());
            assertEquals("0", intDomain.minValue());
            assertEquals("100", intDomain.maxValue());

            RangeDomain inlineDomain =
                    (RangeDomain) database.domain("OfgdbRangeDomainTest_T_C_inlineAttr").get();
            assertEquals(FileGdbFieldType.FLOAT64, inlineDomain.fieldType());
            assertEquals("1.00", inlineDomain.minValue());
            assertEquals("999.99", inlineDomain.maxValue());

            RangeDomain bigIntDomain =
                    (RangeDomain) database.domain("OfgdbRangeDomainTest_BigIntDomain").get();
            assertEquals(FileGdbFieldType.INT64, bigIntDomain.fieldType());
            assertEquals("9223372036854775807", bigIntDomain.maxValue());
        }
    }

    private static void executeScript(OfgdbTestSetup setup, String scriptFile) throws Exception {
        setup.resetDb();
        Connection conn = setup.createDbSchema();
        try {
            conn.setAutoCommit(false);
            DbUtility.executeSqlScript(conn, new FileReader(scriptFile));
            conn.commit();
        } finally {
            conn.close();
        }
    }

    private static String readText(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), Charset.forName("UTF-8"));
    }
}
