package modstudio.gen;

import modstudio.core.*;


import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
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

    /** File name of the pack that covers every chosen version. */
    public static String universalFileName(Project p, Target t) {
        return p.meta.modId + "-" + p.meta.version + (t == Target.RESOURCEPACK ? "-resourcepack" : "-datapack") + "-all.zip";
    }

    /**
     * Collects the data pack or resource pack builds of several versions into one pack: the oldest version's files
     * are the base and every newer version gets an overlay folder (Minecraft 1.20.2+) with the files that differ from
     * what the versions before it already provide. Later overlays win, so each version sees its own files.
     *
     * @return the pack, or null when fewer than two versions were built
     */
    public static FileMap universalPack(Map<McVersion, FileMap> builds, Target t) {
        if (builds.size() < 2) return null;
        List<McVersion> versions = new ArrayList<>(builds.keySet());
        versions.sort(Comparator.naturalOrder());
        boolean res = t == Target.RESOURCEPACK;
        FileMap out = new FileMap();
        Map<String, byte[]> seen = new HashMap<>();
        FileMap base = builds.get(versions.get(0));
        for (String path : base.paths()) {
            if (path.equals("pack.mcmeta")) continue;
            out.bytes(path, base.get(path));
            seen.put(path, base.get(path));
        }
        McVersion minV = versions.get(0), maxV = versions.get(versions.size() - 1);
        int max = format(maxV, res);
        com.google.gson.JsonArray entries = new com.google.gson.JsonArray();
        for (int k = 1; k < versions.size(); k++) {
            McVersion v = versions.get(k);
            FileMap files = builds.get(v);
            String dir = "mc" + v.id.replace('.', '_');
            int changed = 0;
            for (String path : files.paths()) {
                if (path.equals("pack.mcmeta") || path.equals("pack.png")) continue;
                byte[] data = files.get(path);
                if (Arrays.equals(seen.get(path), data)) continue;
                out.bytes(dir + "/" + path, data);
                seen.put(path, data);
                changed++;
            }
            if (changed > 0) {
                com.google.gson.JsonObject entry = J.obj("formats", J.arr(format(v, res), max), "directory", dir);
                if (maxV.minorPackFormat()) {
                    entry.add("min_format", J.arr(v.format(res), v.formatMinor(res)));
                    entry.add("max_format", J.arr(maxV.format(res), maxV.formatMinor(res)));
                }
                entries.add(entry);
            }
        }
        String desc = "";
        try {
            byte[] newest = builds.get(maxV).get("pack.mcmeta");
            com.google.gson.JsonObject n = com.google.gson.JsonParser.parseString(new String(newest, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            desc = n.getAsJsonObject("pack").get("description").getAsString();
        } catch (Exception ignored) {
        }
        com.google.gson.JsonObject meta = J.packMeta(minV, maxV, res, desc);
        if (!entries.isEmpty()) meta.add("overlays", J.obj("entries", entries));
        out.json("pack.mcmeta", meta);
        return out;
    }

    private static int format(McVersion v, boolean resource) {
        return resource ? v.resourceFormat : v.dataFormat;
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
