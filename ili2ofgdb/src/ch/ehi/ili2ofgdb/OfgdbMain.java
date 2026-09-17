package ch.ehi.ili2ofgdb;

import java.text.ParseException;

import ch.ehi.ili2db.base.DbUrlConverter;
import ch.ehi.ili2db.gui.AbstractDbPanelDescriptor;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2ofgdb.jdbc.OfgdbDriver;
import ch.ehi.sqlgen.generator_impl.ofgdb.GeneratorOfgdb;

public class OfgdbMain extends ch.ehi.ili2db.AbstractMain {

    @Override
    public void initConfig(Config config) {
        super.initConfig(config);
        config.setGeometryConverter(ch.ehi.ili2ofgdb.OfgdbColumnConverter.class.getName());
        config.setDdlGenerator(ch.ehi.sqlgen.generator_impl.ofgdb.GeneratorOfgdb.class.getName());
        config.setJdbcDriver(ch.ehi.ili2ofgdb.jdbc.OfgdbDriver.class.getName());
        config.setIdGenerator(ch.ehi.ili2db.base.TableBasedIdGen.class.getName());
        config.setIli2dbCustomStrategy(ch.ehi.ili2ofgdb.OfgdbMapping.class.getName());
        config.setOneGeomPerTable(true);
        config.setValue(OfgdbMapping.FGDB_CREATE_DOMAINS, Config.TRUE);
        config.setValue(OfgdbMapping.FGDB_CREATE_RELATIONSHIP_CLASSES, Config.TRUE);
        config.setValue(OfgdbMapping.FGDB_INCLUDE_INACTIVE_ENUM_VALUES, Config.FALSE);
    }

    @Override
    public DbUrlConverter getDbUrlConverter() {
        return new DbUrlConverter() {
            @Override
            public String makeUrl(Config config) {
                if (config.getDbfile() != null) {
                    return OfgdbDriver.BASE_URL + new java.io.File(config.getDbfile()).getAbsolutePath();
                }
                return null;
            }
        };
    }

    @Override
    public AbstractDbPanelDescriptor getDbPanelDescriptor() {
        return new FgdbDbPanelDescriptor();
    }

    public static void main(String args[]) {
        new OfgdbMain().domain(args);
    }

    @Override
    public String getAPP_NAME() {
        return "ili2ofgdb";
    }

    @Override
    public String getDB_PRODUCT_NAME() {
        return "OpenFileGDB";
    }

    @Override
    public String getJAR_NAME() {
        return "ili2ofgdb.jar";
    }

    @Override
    protected void printConnectOptions() {
        System.err.println("--dbfile fgdbfolder                     The folder name of the database.");
    }

    @Override
    protected void printSpecificOptions() {
        System.err.println("--fgdbXyResolution value                The precision with which coordinates are recorded.");
        System.err.println("--fgdbXyTolerance value                 The cluster tolerance used to cluster coincident geometry.");
        System.err.println("--fgdbCreateDomains                     Create/assign FGDB domains from INTERLIS enums and numeric ranges.");
        // TODO: Braucht es das? Kann m.E. bei uns nicht vorkommen resp. spiel einfach keine Rolle. Gugus.
        //System.err.println("--fgdbIncludeInactiveEnumValues         Include inactive enum values in created domains.");
        System.err.println("--fgdbCreateRelationshipClasses         Create FGDB relationship classes from model links.");
    }

    @Override
    protected int doArgs(String args[], int argi, Config config) throws ParseException {
        String arg = args[argi];
        if (arg.equals("--dbfile")) {
            argi++;
            config.setDbfile(args[argi]);
            argi++;
        } else if (arg.equals("--fgdbXyResolution")) {
            argi++;
            config.setValue(GeneratorOfgdb.XY_RESOLUTION, args[argi]);
            argi++;
        } else if (arg.equals("--fgdbXyTolerance")) {
            argi++;
            config.setValue(GeneratorOfgdb.XY_TOLERANCE, args[argi]);
            argi++;
        } else if (isOption(arg, "--fgdbCreateDomains")) {
            argi++;
            config.setValue(OfgdbMapping.FGDB_CREATE_DOMAINS, booleanArgument(parseBooleanArgument(arg)));
        } else if (isOption(arg, "--fgdbIncludeInactiveEnumValues")) {
            argi++;
            config.setValue(OfgdbMapping.FGDB_INCLUDE_INACTIVE_ENUM_VALUES, booleanArgument(parseBooleanArgument(arg)));
        } else if (isOption(arg, "--fgdbCreateRelationshipClasses")) {
            argi++;
            config.setValue(OfgdbMapping.FGDB_CREATE_RELATIONSHIP_CLASSES, booleanArgument(parseBooleanArgument(arg)));
        }
        return argi;
    }

    private static String booleanArgument(boolean value) {
        return value ? Config.TRUE : Config.FALSE;
    }
}
