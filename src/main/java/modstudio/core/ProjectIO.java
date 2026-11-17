package modstudio.core;

import com.google.gson.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and writes .msproj files. */
public final class ProjectIO {
    private ProjectIO() {}

    private static final Gson PLAIN = new GsonBuilder().disableHtmlEscaping().create();

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeHierarchyAdapter(Element.class, new ElementAdapter())
            .create();

    private static final class ElementAdapter implements JsonSerializer<Element>, JsonDeserializer<Element> {
        @Override
        public JsonElement serialize(Element src, Type t, JsonSerializationContext ctx) {
            JsonObject body = PLAIN.toJsonTree(src).getAsJsonObject();
            JsonObject o = new JsonObject();
            o.addProperty("type", src.type().id);
            for (var e : body.entrySet()) o.add(e.getKey(), e.getValue());
            return o;
        }

        @Override
        public Element deserialize(JsonElement json, Type t, JsonDeserializationContext ctx) {
            JsonObject o = json.getAsJsonObject();
            ElementType type = ElementType.of(o.get("type").getAsString());
            if (type == null) throw new JsonParseException("Unknown element type " + o.get("type"));
            return PLAIN.fromJson(o, type.cls);
        }
    }

    public static Project load(Path file) throws IOException {
        String s = Files.readString(file, StandardCharsets.UTF_8);
        Project p = GSON.fromJson(s, Project.class);
        if (p == null) throw new IOException("Empty project file");
        p.elements.removeIf(e -> e == null);
        migrate(p);
        return p;
    }

    /** Upgrades projects written by older versions of the app. */
    static void migrate(Project p) {
        if (p.meta.mcVersions == null) p.meta.mcVersions = new java.util.ArrayList<>();
        // Toolchain overrides used to be keyed by target only and applied to the single version.
        for (Target t : Target.values()) {
            if (!t.versioned()) continue;
            var old = p.versions.remove(t.id);
            if (old != null && !old.isEmpty()) p.versions.putIfAbsent(Project.versionKey(t, p.mc()), old);
        }
        p.normalizeVersions();
    }

    public static void save(Project p, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(p), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static String toJson(Project p) {
        return GSON.toJson(p);
    }

    public static Project fromJson(String s) {
        Project p = GSON.fromJson(s, Project.class);
        if (p != null) migrate(p);
        return p;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Element> T copy(T e) {
        JsonElement j = GSON.toJsonTree(e, Element.class);
        T c = (T) GSON.fromJson(j, Element.class);
        c.uid = Ids.uid();
        return c;
    }
}
