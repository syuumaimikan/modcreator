package modstudio.gen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/**
 * Hot reload for test runs: the data pack part of a fresh export (functions, advancements, loot tables, recipes,
 * tags, predicates) is written as a world data pack into the running test game, which then only needs /reload.
 * The pack is added after the mod or plugin data, so its files replace the ones built into the jar.
 * Java code, assets and world generation still need a restart; {@link #codeHash} tells when that is the case.
 */
public final class LiveData {
    private LiveData() {}

    /** Path of a data file inside the data pack ("data/ns/function/x.mcfunction"), or null for other files. */
    static String dataPath(String path) {
        String p = path.replace('\\', '/');
        if (p.startsWith("data/")) return p;
        for (String marker : List.of("resources/datapack/data/", "resources/data/")) {
            int i = p.indexOf(marker);
            if (i >= 0) return p.substring(i + marker.length() - "data/".length());
        }
        return null;
    }

    private static boolean worldgen(String dataPath) {
        return dataPath.contains("/worldgen/") || dataPath.contains("/dimension/") || dataPath.contains("/dimension_type/");
    }

    /** World folder of a dedicated test server (level-name in server.properties, "world" by default). */
    public static Path serverWorld(Path runDir) {
        String level = "world";
        Path props = runDir.resolve("server.properties");
        if (Files.isRegularFile(props)) {
            Properties pr = new Properties();
            try (InputStream in = Files.newInputStream(props)) {
                pr.load(in);
                level = pr.getProperty("level-name", "world").trim();
                if (level.isEmpty()) level = "world";
            } catch (IOException ignored) {
            }
        }
        return runDir.resolve(level);
    }

    /** The single player world of a client test run that was played last, or null when there is none yet. */
    public static Path newestSave(Path runDir) {
        Path saves = runDir.resolve("saves");
        if (!Files.isDirectory(saves)) return null;
        Path best = null;
        FileTime bestTime = null;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(saves)) {
            for (Path w : ds) {
                Path level = w.resolve("level.dat");
                if (!Files.isRegularFile(level)) continue;
                Path lock = w.resolve("session.lock");
                FileTime t = Files.getLastModifiedTime(Files.exists(lock) ? lock : level);
                if (bestTime == null || t.compareTo(bestTime) > 0) {
                    best = w;
                    bestTime = t;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return best;
    }

    public static Path packDir(Path world, String ns) {
        return world.resolve("datapacks").resolve(ns + "_live");
    }

    /** Removes the live packs a previous run left behind, so a fresh start uses only the new build. */
    public static void clear(Path runDir, String ns) throws IOException {
        Exporter.deleteTree(packDir(serverWorld(runDir), ns));
        Path saves = runDir.resolve("saves");
        if (!Files.isDirectory(saves)) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(saves)) {
            for (Path w : ds) Exporter.deleteTree(packDir(w, ns));
        }
    }

    /** Replaces the live data pack of a world with the data files of the export; returns how many were written. */
    public static int write(GenContext ctx, Path world) throws IOException {
        Path dir = packDir(world, ctx.ns);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path f : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
            }
        }
        Files.createDirectories(dir);
        int n = 0;
        for (Map.Entry<String, byte[]> e : ctx.files.all().entrySet()) {
            String rel = dataPath(e.getKey());
            if (rel == null || worldgen(rel)) continue;
            Path target = dir.resolve(rel).normalize();
            if (!target.startsWith(dir)) continue;
            Files.createDirectories(target.getParent());
            Files.write(target, e.getValue());
            n++;
        }
        String meta = "{\"pack\":{\"pack_format\":" + ctx.v.dataFormat + ",\"description\":\"" + ctx.ns + " live reload (ModStudio)\"}}";
        Files.writeString(dir.resolve("pack.mcmeta"), meta);
        return n;
    }

    /** Hash of everything that /reload cannot pick up: Java sources, assets, build files and world generation. */
    public static String codeHash(GenContext ctx) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String path : new TreeSet<>(ctx.files.all().keySet())) {
                String d = dataPath(path);
                if (d != null && !worldgen(d)) continue;
                if (path.endsWith("datapack/files.txt")) continue;
                md.update(path.getBytes(StandardCharsets.UTF_8));
                md.update(ctx.files.get(path));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }
}
