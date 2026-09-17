package ch.ehi.ili2ofgdb;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import ch.ehi.basics.logging.EhiLogger;

/**
 * Resolves the WKT of an EPSG code for the CRS definition of written feature classes.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>a user supplied directory ({@code --fgdbWktDir} or {@code -Dili2ofgdb.wktDir}) containing
 *       {@code <epsg>.wkt} files,
 *   <li>the curated WKT resource bundled with the flavor,
 *   <li>empty WKT (the geodatabase then carries the WKID only, which GDAL and ArcGIS resolve).
 * </ol>
 */
public final class OfgdbCrs {

    private static volatile String wktDirectory = null;

    private OfgdbCrs() {
    }

    /** Directory with {@code <epsg>.wkt} files, or null. */
    public static void setWktDirectory(String directory) {
        wktDirectory = directory;
    }

    public static String getWktDirectory() {
        String directory = wktDirectory;
        if (directory == null || directory.trim().isEmpty()) {
            directory = System.getProperty("ili2ofgdb.wktDir");
        }
        return directory;
    }

    /** WKT for the given EPSG code; empty when it cannot be resolved. */
    public static String wktFor(int epsg) {
        if (epsg <= 0) {
            return "";
        }
        String fromDirectory = readFromDirectory(epsg);
        if (fromDirectory != null) {
            return fromDirectory;
        }
        String fromResource = readFromResource(epsg);
        if (fromResource != null) {
            return fromResource;
        }
        EhiLogger.logAdaption("ili2ofgdb: no WKT available for EPSG:" + epsg
                + "; the geodatabase keeps the WKID only");
        return "";
    }

    private static String readFromDirectory(int epsg) {
        String directory = getWktDirectory();
        if (directory == null || directory.trim().isEmpty()) {
            return null;
        }
        Path file = Paths.get(directory).resolve(epsg + ".wkt");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        } catch (java.io.IOException ex) {
            EhiLogger.logAdaption("ili2ofgdb: failed to read WKT file " + file + ": " + ex.getMessage());
            return null;
        }
    }

    private static String readFromResource(int epsg) {
        String name = "/ch/ehi/ili2ofgdb/crs/" + epsg + ".wkt";
        InputStream in = OfgdbCrs.class.getResourceAsStream(name);
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (java.io.IOException ex) {
            EhiLogger.logAdaption("ili2ofgdb: failed to read WKT resource " + name + ": " + ex.getMessage());
            return null;
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignore) {
            }
        }
    }
}
