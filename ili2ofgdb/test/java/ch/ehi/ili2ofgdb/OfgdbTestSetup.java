package ch.ehi.ili2ofgdb;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2ofgdb.jdbc.OfgdbDriver;
import ch.ehi.sqlgen.generator_impl.ofgdb.GeneratorOfgdb;

public class OfgdbTestSetup extends ch.ehi.ili2db.AbstractTestSetup {
    private String fgdbFilename;

    public OfgdbTestSetup() {
        super();
    }

    protected OfgdbTestSetup(String fgdbFilename) {
        super();
        this.fgdbFilename = fgdbFilename;
    }

    @Override
    public void setXYParams(Config config) {
        config.setValue(GeneratorOfgdb.XY_RESOLUTION, "0.005");
        config.setValue(GeneratorOfgdb.XY_TOLERANCE, "0.05");
    }
    
    @Override
    public Config initConfig(String xtfFilename,String logfile) {
        Config config=new Config();
        new ch.ehi.ili2ofgdb.OfgdbMain().initConfig(config);
        config.setDbfile(fgdbFilename);
        config.setDburl(OfgdbDriver.BASE_URL+fgdbFilename);
        if(logfile!=null){
            config.setLogfile(logfile);
        }
        config.setXtffile(xtfFilename);
        if(xtfFilename!=null && Ili2db.isItfFilename(xtfFilename)){
            config.setItfTransferfile(true);
        }
        return config;
    }

    @Override
    public void initConfig(Config config) {
        new ch.ehi.ili2ofgdb.OfgdbMain().initConfig(config);
    }

    @Override
    public void resetDb() throws SQLException {
        File fgdbFile=new File(fgdbFilename);
        File parentDir = fgdbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        OfgdbTestFiles.deleteFileGdb(fgdbFile);
    }

    @Override
    public Connection createConnection() throws SQLException {
        DriverManager.registerDriver(new OfgdbDriver());
        
        Connection conn = DriverManager.getConnection(
                OfgdbDriver.BASE_URL+fgdbFilename, null, null);
        return conn;
    }

}
