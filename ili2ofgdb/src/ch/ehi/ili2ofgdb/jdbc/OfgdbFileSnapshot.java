package ch.ehi.ili2ofgdb.jdbc;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

final class OfgdbFileSnapshot {
    private OfgdbFileSnapshot() {
    }

    static Path buildSnapshotPath(Path dbPath) {
        Path fileName = dbPath.getFileName();
        String snapshotName = (fileName != null ? fileName.toString() : "db") + ".ofgdb-txn-snapshot";
        Path parent = dbPath.getParent();
        if (parent != null) {
            return parent.resolve(snapshotName);
        }
        return Path.of(snapshotName);
    }

    static void createSnapshot(Path sourcePath, Path snapshotPath) throws IOException {
        if (!Files.exists(sourcePath)) {
            throw new IOException("database path does not exist: " + sourcePath);
        }
        deleteRecursively(snapshotPath);
        copyRecursively(sourcePath, snapshotPath);
    }

    static void restoreSnapshot(Path snapshotPath, Path targetPath) throws IOException {
        if (!Files.exists(snapshotPath)) {
            throw new IOException("snapshot path does not exist: " + snapshotPath);
        }
        deleteRecursively(targetPath);
        copyRecursively(snapshotPath, targetPath);
    }

    static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new SnapshotIOException(e);
                }
            });
        } catch (SnapshotIOException e) {
            throw e.getCause();
        }
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path rel = source.relativize(dir);
                    Path dest = target.resolve(rel);
                    Files.createDirectories(dest);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path rel = source.relativize(file);
                    Path dest = target.resolve(rel);
                    Files.createDirectories(dest.getParent());
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });
            return;
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static final class SnapshotIOException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SnapshotIOException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
