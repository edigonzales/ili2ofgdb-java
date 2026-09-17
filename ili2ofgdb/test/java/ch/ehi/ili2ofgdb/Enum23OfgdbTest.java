package ch.ehi.ili2ofgdb;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Assert;
import org.junit.Test;

import ch.ehi.ili2db.AbstractTestSetup;
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;

public class Enum23OfgdbTest extends ch.ehi.ili2db.Enum23Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/Enum23OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    private Config createBaseSchemaImportConfig(String modelPath, String enumDefsMode) throws Exception {
        Config config = setup.initConfig(modelPath, modelPath + ".log");
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_SCHEMAIMPORT);
        config.setCreateFk(Config.CREATE_FK_YES);
        config.setCreateNumChecks(true);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        config.setCreateMetaInfo(true);
        config.setCreateEnumDefs(enumDefsMode);
        config.setCreatescript(null);
        return config;
    }

    private void runImportWithoutSchema(Config config, String xtfPath) throws Exception {
        config.setXtffile(xtfPath);
        config.setFunction(Config.FC_IMPORT);
        config.setDoImplicitSchemaImport(false);
        config.setCreatescript(null);
        Ili2db.readSettingsFromDb(config);
        Ili2db.run(config, null);
    }

    private void runFkTableScenario() throws Exception {
        Connection jdbcConnection = null;
        try {
            setup.resetDb();
            File model = new File(TEST_OUT, "Enum23b.ili");
            Config config = createBaseSchemaImportConfig(model.getPath(), Config.CREATE_ENUM_DEFS_MULTI_WITH_ID);
            Ili2db.run(config, null);

            jdbcConnection = setup.createDbSchema();
            Statement stmt = null;
            try {
                String stmtTxt =
                        "select count(*) from " + setup.prefixName("enum1")
                                + " where iliCode='Test1' and thisClass='Enum23b.Enum1' and baseClass is NULL";
                stmt = jdbcConnection.createStatement();
                Assert.assertTrue(stmt.execute(stmtTxt));
                ResultSet rs = stmt.getResultSet();
                Assert.assertTrue(rs.next());
                Assert.assertEquals(1, rs.getInt(1));
            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                jdbcConnection.close();
                jdbcConnection = null;
            }

            runImportWithoutSchema(config, new File(TEST_OUT, "Enum23b.xtf").getPath());
        } finally {
            if (jdbcConnection != null) {
                jdbcConnection.close();
            }
        }
    }

    private void runSingleTableScenario() throws Exception {
        setup.resetDb();
        File model = new File(TEST_OUT, "Enum23b.ili");
        Config config = createBaseSchemaImportConfig(model.getPath(), Config.CREATE_ENUM_DEFS_SINGLE);
        Ili2db.run(config, null);

        runImportWithoutSchema(config, new File(TEST_OUT, "Enum23b.xtf").getPath());
    }

    private void runMultiTableScenario() throws Exception {
        Connection jdbcConnection = null;
        try {
            setup.resetDb();
            File model = new File(TEST_OUT, "Enum23b.ili");
            Config config = createBaseSchemaImportConfig(model.getPath(), Config.CREATE_ENUM_DEFS_MULTI);
            Ili2db.run(config, null);

            jdbcConnection = setup.createDbSchema();
            PreparedStatement stmt = null;
            try {
                String stmtTxt = "select seq from " + setup.prefixName("enum2ordered") + " where iliCode=?";
                stmt = jdbcConnection.prepareStatement(stmtTxt);
                stmt.setString(1, "Test1");
                ResultSet rs = stmt.executeQuery();
                Assert.assertTrue(rs.next());
                Assert.assertEquals(0, rs.getInt(1));

                stmt.setString(1, "Test3.Test3b");
                rs = stmt.executeQuery();
                Assert.assertTrue(rs.next());
                Assert.assertEquals(3, rs.getInt(1));
            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                jdbcConnection.close();
                jdbcConnection = null;
            }

            runImportWithoutSchema(config, new File(TEST_OUT, "Enum23b.xtf").getPath());
        } finally {
            if (jdbcConnection != null) {
                jdbcConnection.close();
            }
        }
    }

    @Override
    @Test
    public void createScriptFromIliFkTable() throws Exception {
        runFkTableScenario();
    }

    @Override
    @Test
    public void createScriptFromIliSingleTable() throws Exception {
        runSingleTableScenario();
    }

    @Override
    @Test
    public void createScriptFromIliMultiTable() throws Exception {
        runMultiTableScenario();
    }

    @Override
    @Test
    public void createScriptFromIliFkTableScriptOnly() throws Exception {
        // OFGDB has no offline script collector path; verify equivalent schema+import behavior instead.
        runFkTableScenario();
    }

    @Override
    @Test
    public void createScriptFromIliSingleTableScriptOnly() throws Exception {
        // OFGDB has no offline script collector path; verify equivalent schema+import behavior instead.
        runSingleTableScenario();
    }

    @Override
    @Test
    public void createScriptFromIliMultiTableScriptOnly() throws Exception {
        // OFGDB has no offline script collector path; verify equivalent schema+import behavior instead.
        runMultiTableScenario();
    }
}
