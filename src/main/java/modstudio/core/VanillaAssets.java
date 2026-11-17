package modstudio.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import modstudio.build.GameDirs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Read-only access to the block states, block models and textures inside a Minecraft client jar installed on this
 * computer (the launcher's versions folder, or the copy Gradle keeps after a Fabric build). Previews use it to draw
 * vanilla blocks with their real models; nothing from the jar is copied into projects or generated output.
 */
public final class VanillaAssets {
    private static final Map<String, Optional<VanillaAssets>> OPEN = new ConcurrentHashMap<>();

    public final String version;
    private final ZipFile zip;
    private final Map<String, Optional<JsonObject>> json = new ConcurrentHashMap<>();
    private final Map<String, Optional<byte[]>> bytes = new ConcurrentHashMap<>();

    private VanillaAssets(String version, Path jar) throws IOException {
        this.version = version;
        this.zip = new ZipFile(jar.toFile());
    }

    /** A client jar close to the given version (the same, else the newest older release, else any), or null. */
    public static VanillaAssets get(McVersion preferred) {
        List<Shaders.GameJar> jars = candidates();
        if (jars.isEmpty()) return null;
        Shaders.GameJar pick = null;
        String want = preferred == null ? "" : preferred.id;
        for (Shaders.GameJar g : jars) if (g.version().equals(want)) pick = g;
        if (pick == null && !want.isEmpty()) {
            for (Shaders.GameJar g : jars) {
                if (release(g.version()) && compare(g.version(), want) <= 0) {
                    pick = g;
                    break;
                }
            }
        }
        if (pick == null) for (Shaders.GameJar g : jars) if (release(g.version())) {
            pick = g;
            break;
        }
        if (pick == null) pick = jars.get(0);
        Shaders.GameJar chosen = pick;
        return OPEN.computeIfAbsent(chosen.jar().toString(), k -> {
            try {
                return Optional.of(new VanillaAssets(chosen.version(), chosen.jar()));
            } catch (IOException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Installed client jars, newest first. */
    static List<Shaders.GameJar> candidates() {
        List<Shaders.GameJar> r = new ArrayList<>(Shaders.installed(GameDirs.javaDefault()));
        Path loom = Path.of(System.getProperty("user.home"), ".gradle", "caches", "fabric-loom");
        if (Files.isDirectory(loom)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(loom)) {
                for (Path d : ds) {
                    Path jar = d.resolve("minecraft-client.jar");
                    String v = d.getFileName().toString();
                    if (Files.isRegularFile(jar) && r.stream().noneMatch(g -> g.version().equals(v))) r.add(new Shaders.GameJar(v, jar));
                }
            } catch (IOException ignored) {
            }
        }
        r.sort((a, b) -> compare(b.version(), a.version()));
        return r;
    }

    private static boolean release(String v) {
        return v.matches("\\d+(\\.\\d+)+");
    }

    /** Compares version names by their numbers (1.21.10 is after 1.21.9). */
    static int compare(String a, String b) {
        String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "0", q = i < y.length ? y[i] : "0";
            boolean pn = p.matches("\\d+"), qn = q.matches("\\d+");
            int c = pn && qn ? Long.compare(Long.parseLong(p), Long.parseLong(q)) : pn ? 1 : qn ? -1 : p.compareTo(q);
            if (c != 0) return c;
        }
        return 0;
    }

    /** Files of the jar under a folder, sorted. */
    public java.util.List<String> list(String prefix) {
        java.util.List<String> r = new java.util.ArrayList<>();
        var en = zip.entries();
        while (en.hasMoreElements()) {
            String n = en.nextElement().getName();
            if (n.startsWith(prefix) && !n.endsWith("/")) r.add(n);
        }
        java.util.Collections.sort(r);
        return r;
    }

    /** Raw bytes of a file in the jar, or null. */
    public byte[] bytes(String path) {
        return bytes.computeIfAbsent(path, k -> {
            try {
                ZipEntry e = zip.getEntry(k);
                if (e == null) return Optional.empty();
                try (InputStream in = zip.getInputStream(e)) {
                    return Optional.of(in.readAllBytes());
                }
            } catch (IOException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** A JSON file of the jar, or null. */
    public JsonObject json(String path) {
        return json.computeIfAbsent(path, k -> {
            byte[] b = bytes(k);
            if (b == null) return Optional.empty();
            try {
                JsonElement el = JsonParser.parseString(new String(b, StandardCharsets.UTF_8));
                return el.isJsonObject() ? Optional.of(el.getAsJsonObject()) : Optional.empty();
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Block state file of a vanilla block (path without namespace). */
    public JsonObject blockstate(String block) {
        return json("assets/minecraft/blockstates/" + block + ".json");
    }

    /** Model file by resource id such as minecraft:block/stone or block/stone. */
    public JsonObject model(String id) {
        String path = Ids.path(Ids.full(id));
        return json("assets/" + Ids.ns(Ids.full(id)) + "/models/" + path + ".json");
    }

    /** PNG bytes of a texture by resource id such as minecraft:block/stone. */
    public byte[] texture(String id) {
        String full = Ids.full(id);
        return bytes("assets/" + Ids.ns(full) + "/textures/" + Ids.path(full) + ".png");
    }
}
