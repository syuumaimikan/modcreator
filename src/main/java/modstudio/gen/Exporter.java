package modstudio.gen;

import modstudio.core.*;


import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes generated files to disk and packages packs. */
public final class Exporter {
    private Exporter() {}

    /** Folders kept between exports (build caches, run directories, wrapper binaries). */
    private static final Set<String> KEEP = Set.of(".gradle", "build", "run", "runs", ".idea", "out", "bin", "gradlew", "gradlew.bat", "gradle-wrapper.jar");

    public static Path outputDir(Path projectDir, Build b) {
        return projectDir.resolve("output").resolve(b.key());
    }

    public static Path distDir(Path projectDir) {
        return projectDir.resolve("dist");
    }

    /** Replaces previously generated files in dir with the new set, keeping caches. */
    public static void writeFolder(FileMap files, Path dir) throws IOException {
        Files.createDirectories(dir);
        clean(dir, dir);
        for (var e : files.all().entrySet()) {
            Path p = dir.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
    }

    private static void clean(Path root, Path dir) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (KEEP.contains(name)) continue;
                if (Files.isDirectory(p)) {
                    clean(root, p);
                    try (DirectoryStream<Path> rest = Files.newDirectoryStream(p)) {
                        if (!rest.iterator().hasNext()) Files.delete(p);
                    }
                } else Files.delete(p);
            }
        }
    }

    public static void writeZip(FileMap files, Path zip) throws IOException {
        Files.createDirectories(zip.getParent());
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(os)) {
            for (String path : files.paths()) {
                zos.putNextEntry(new ZipEntry(path));
                zos.write(files.get(path));
                zos.closeEntry();
            }
        }
    }

    public static String packFileName(Project p, Build b) {
        String base = p.meta.modId + "-" + p.meta.version;
        return switch (b.target()) {
            case DATAPACK -> base + "-datapack-mc" + b.mc().id + ".zip";
            case RESOURCEPACK -> base + "-resourcepack-mc" + b.mc().id + ".zip";
            case BEDROCK -> base + ".mcaddon";
            default -> base + "-" + b.target().id + "-mc" + b.mc().id + "-source.zip";
        };
    }

    /** File name of a built mod or plugin jar in the dist folder. */
    public static String jarFileName(Project p, Build b) {
        return p.meta.modId + "-" + p.meta.version + "-" + b.target().id + "-mc" + b.mc().id + ".jar";
    }

    public static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
