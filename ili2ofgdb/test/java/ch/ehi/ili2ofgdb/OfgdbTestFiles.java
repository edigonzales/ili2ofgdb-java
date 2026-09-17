package ch.ehi.ili2ofgdb;

import java.io.File;

public final class OfgdbTestFiles {
    private OfgdbTestFiles() {
    }

    public static void deleteFileGdb(File gdbDir) {
        if (gdbDir == null || !gdbDir.exists()) {
            return;
        }
        deleteRecursively(gdbDir);
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
