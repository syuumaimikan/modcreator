package modstudio.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import modstudio.build.GameDirs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Vanilla sound events played from the launcher's asset store (assets/indexes and assets/objects), for previews in
 * the editor. Nothing is copied into projects.
 */
public final class VanillaSounds {
    private static VanillaSounds cached;
    private static boolean searched;

    private final Path objects;
    private final JsonObject index;
    private JsonObject sounds;

    private VanillaSounds(Path objects, JsonObject index) {
        this.objects = objects;
        this.index = index;
    }

    /** The newest asset index of the installed game, or null when there is none. */
    public static synchronized VanillaSounds get() {
        if (searched) return cached;
        searched = true;
        Path assets = GameDirs.javaDefault().resolve("assets");
        Path indexes = assets.resolve("indexes");
        if (!Files.isDirectory(indexes)) return null;
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(indexes, "*.json")) {
            for (Path p : ds) files.add(p);
        } catch (IOException e) {
            return null;
        }
        files.sort(Comparator.comparing((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }).reversed());
        for (Path f : files) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("objects") && o.getAsJsonObject("objects").has("minecraft/sounds.json")) {
                    cached = new VanillaSounds(assets.resolve("objects"), o.getAsJsonObject("objects"));
                    return cached;
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private byte[] object(String name) {
        JsonElement e = index.get(name);
        if (e == null) return null;
        String hash = e.getAsJsonObject().get("hash").getAsString();
        try {
            return Files.readAllBytes(objects.resolve(hash.substring(0, 2)).resolve(hash));
        } catch (IOException ex) {
            return null;
        }
    }

    private JsonObject sounds() {
        if (sounds == null) {
            byte[] b = object("minecraft/sounds.json");
            sounds = b == null ? new JsonObject() : JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        return sounds;
    }

    /** Ogg bytes of the first sound of an event such as minecraft:music_disc.cat, or null. */
    public byte[] event(String id) {
        return event(Ids.path(Ids.full(id)), 0);
    }

    private byte[] event(String name, int depth) {
        if (depth > 4) return null;
        JsonObject ev = sounds().has(name) ? sounds().getAsJsonObject(name) : null;
        if (ev == null || !ev.has("sounds")) return null;
        JsonArray list = ev.getAsJsonArray("sounds");
        for (JsonElement s : list) {
            String file;
            if (s.isJsonPrimitive()) file = s.getAsString();
            else {
                JsonObject o = s.getAsJsonObject();
                file = o.get("name").getAsString();
                if (o.has("type") && o.get("type").getAsString().equals("event")) {
                    byte[] r = event(Ids.path(Ids.full(file)), depth + 1);
                    if (r != null) return r;
                    continue;
                }
            }
            byte[] b = object("minecraft/sounds/" + Ids.path(Ids.full(file)) + ".ogg");
            if (b != null) return b;
        }
        return null;
    }
}
