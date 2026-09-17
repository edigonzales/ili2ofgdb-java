package ch.ehi.ili2ofgdb;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the reference GDAL command line tools for an independent verification of the written file
 * geodatabases.
 *
 * <p>The executable is resolved from the system property {@code gdal.prefix} / environment variable
 * {@code GDAL_PREFIX}, from {@code PATH} and from the usual conda environments
 * ({@code ~/miniconda3/envs/gdal}, {@code ~/miniforge3/envs/gdal}, {@code ~/anaconda3/envs/gdal}).
 */
public final class OfgdbTestGdal {

    private OfgdbTestGdal() {
    }

    /** True if the interop checks are mandatory (CI), see {@code -PrequireGdal=true}. */
    public static boolean isRequired() {
        return Boolean.parseBoolean(System.getProperty("ofgdb.requireGdal", "false"));
    }

    public static Path findExecutable(String name) {
        String prefix = System.getProperty("gdal.prefix", System.getenv("GDAL_PREFIX"));
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = System.getenv("CONDA_PREFIX");
        }
        if (prefix != null && !prefix.trim().isEmpty()) {
            Path candidate = Paths.get(prefix).resolve("bin").resolve(name);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
            candidate = Paths.get(prefix).resolve(name);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        String pathEnvironment = System.getenv("PATH");
        if (pathEnvironment != null) {
            for (String directory : pathEnvironment.split(File.pathSeparator)) {
                if (directory.trim().isEmpty()) {
                    continue;
                }
                Path candidate = Paths.get(directory).resolve(name);
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        String home = System.getProperty("user.home");
        for (String environment : new String[] {"miniconda3", "miniforge3", "anaconda3"}) {
            Path candidate = Paths.get(home, environment, "envs", "gdal", "bin", name);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        for (String homebrew : new String[] {"/opt/homebrew/bin/", "/usr/local/bin/"}) {
            Path candidate = Paths.get(homebrew).resolve(name);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static String run(Path executable, String... arguments)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<String>();
        command.add(executable.toAbsolutePath().toString());
        for (String argument : arguments) {
            command.add(argument);
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(name(command) + " failed with exit code " + exitCode + ":\n" + output);
        }
        return output.toString();
    }

    /** Feature count per layer, read from {@code ogrinfo -al -so}. */
    public static Map<String, Long> featureCounts(String gdbPath) throws Exception {
        return parseFeatureCounts(runOgrInfo(gdbPath));
    }

    /** Geometry type per layer, read from {@code ogrinfo -al -so}. */
    public static Map<String, String> geometryTypes(String gdbPath) throws Exception {
        return parseGeometryTypes(runOgrInfo(gdbPath));
    }

    private static String runOgrInfo(String gdbPath) throws Exception {
        Path ogrinfo = findExecutable("ogrinfo");
        if (ogrinfo == null) {
            throw new IllegalStateException("ogrinfo not found");
        }
        return run(ogrinfo, "-al", "-so", gdbPath);
    }

    static Map<String, Long> parseFeatureCounts(String output) {
        Map<String, Long> counts = new LinkedHashMap<String, Long>();
        String layer = null;
        for (String line : output.split("\n")) {
            Matcher layerMatcher = Pattern.compile("^Layer name:\\s+(\\S+)\\s*$").matcher(line.trim());
            if (layerMatcher.matches()) {
                layer = layerMatcher.group(1);
                continue;
            }
            Matcher countMatcher = Pattern.compile("^Feature Count:\\s+(\\d+)\\s*$").matcher(line.trim());
            if (countMatcher.matches() && layer != null) {
                counts.put(layer, Long.valueOf(countMatcher.group(1)));
            }
        }
        return counts;
    }

    static Map<String, String> parseGeometryTypes(String output) {
        Map<String, String> types = new LinkedHashMap<String, String>();
        String layer = null;
        for (String line : output.split("\n")) {
            Matcher layerMatcher = Pattern.compile("^Layer name:\\s+(\\S+)\\s*$").matcher(line.trim());
            if (layerMatcher.matches()) {
                layer = layerMatcher.group(1);
                continue;
            }
            Matcher typeMatcher = Pattern.compile("^Geometry:\\s+(.+?)\\s*$").matcher(line.trim());
            if (typeMatcher.matches() && layer != null) {
                types.put(layer, typeMatcher.group(1));
            }
        }
        return types;
    }

    private static String name(List<String> command) {
        return command.isEmpty() ? "command" : Paths.get(command.get(0)).getFileName().toString();
    }
}
