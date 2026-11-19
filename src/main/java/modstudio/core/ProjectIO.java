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
            JsonElement rawType = o.get("type");
            if (rawType == null || !rawType.isJsonPrimitive()) throw new JsonParseException("Missing element type in JSON");
            ElementType type = ElementType.of(rawType.getAsString());
            if (type == null) throw new JsonParseException("Unknown element type " + rawType);
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
    public static void migrate(Project p) {
        if (p == null) return;
        if (p.meta == null) p.meta = new Project.Meta();
        if (p.elements == null) p.elements = new java.util.ArrayList<>();
        p.elements.removeIf(e -> e == null);
        if (p.textures == null) p.textures = new java.util.ArrayList<>();
        if (p.versions == null) p.versions = new java.util.LinkedHashMap<>();
        if (p.translations == null) p.translations = new java.util.LinkedHashMap<>();
        if (p.meta.mcVersions == null) p.meta.mcVersions = new java.util.ArrayList<>();
        if (p.meta.dependencies == null) p.meta.dependencies = new java.util.ArrayList<>();
        if (p.meta.mavenRepos == null) p.meta.mavenRepos = new java.util.ArrayList<>();
        if (p.meta.libraries == null) p.meta.libraries = new java.util.ArrayList<>();
        if (p.meta.customGradle == null) p.meta.customGradle = "";
        if (p.meta.jvmArgs == null) p.meta.jvmArgs = "";
        // Toolchain overrides used to be keyed by target only and applied to the single version.
        for (Target t : Target.values()) {
            if (!t.versioned()) continue;
            var old = p.versions.remove(t.id);
            if (old != null && !old.isEmpty()) p.versions.putIfAbsent(Project.versionKey(t, p.mc()), old);
        }
        for (Element e : p.elements) {
            if (e instanceof Elements.Advancement a && "sleeping_in_bed".equals(a.trigger)) a.trigger = "slept_in_bed";
            if (e instanceof Elements.CodeFile cf) {
                if (cf.hook == null || cf.hook.isBlank()) cf.hook = "none";
                if (cf.hookMethod == null || cf.hookMethod.isBlank()) cf.hookMethod = "init";
                if (cf.language == null || cf.language.isBlank()) cf.language = "auto";
            }
            if (e instanceof Elements.Item it) {
                if (it.attributes == null) it.attributes = new java.util.ArrayList<>();
                if (it.consumeAnimation == null || it.consumeAnimation.isBlank()) it.consumeAnimation = "eat";
                if (it.wornModel == null) it.wornModel = "";
                if (it.repairItem == null) it.repairItem = "";
                if (it.craftRemainder == null) it.craftRemainder = "";
                if (it.foodEffect == null) it.foodEffect = "";
                if (it.useReturnItem == null) it.useReturnItem = "";
            }
            if (e instanceof Elements.ArmorMaterial am) {
                if (am.helmetModel == null) am.helmetModel = "";
                if (am.chestplateModel == null) am.chestplateModel = "";
                if (am.leggingsModel == null) am.leggingsModel = "";
                if (am.bootsModel == null) am.bootsModel = "";
                if (am.armorTexture == null) am.armorTexture = "";
                if (am.setBonusEffect == null) am.setBonusEffect = "";
            }
            if (e instanceof Elements.Block bl) {
                if (bl.oreDistribution == null || bl.oreDistribution.isBlank()) bl.oreDistribution = "trapezoid";
                if (bl.oreReplaceTarget == null) bl.oreReplaceTarget = "";
                if (bl.oreBiomes == null) bl.oreBiomes = "";
                if (bl.instrument == null || bl.instrument.isBlank()) bl.instrument = "harp";
                if (bl.pushReaction == null || bl.pushReaction.isBlank()) bl.pushReaction = "normal";
            }
            if (e instanceof ModElements.CustomEntity ce) {
                if (ce.bossColor == null || ce.bossColor.isBlank()) ce.bossColor = "purple";
                if (ce.bossStyle == null || ce.bossStyle.isBlank()) ce.bossStyle = "progress";
                if (ce.mainhand == null) ce.mainhand = "";
                if (ce.head == null) ce.head = "";
                if (ce.idleAnim == null) ce.idleAnim = "idle";
                if (ce.walkAnim == null) ce.walkAnim = "walk";
                if (ce.attackAnim == null) ce.attackAnim = "attack";
                if (ce.tameItem == null) ce.tameItem = "minecraft:bone";
            }
            if (e instanceof Elements.ToolMaterial tm) {
                if (tm.displayName == null) tm.displayName = "";
                if (tm.repairItem == null) tm.repairItem = "";
            }
            if (e instanceof ModElements.WorldFeature wf) {
                if (wf.distribution == null || wf.distribution.isBlank()) wf.distribution = "trapezoid";
            }
            if (e instanceof ModElements.MixinSpec mx) {
                if (mx.rawCode == null) mx.rawCode = "";
                if (mx.code == null) mx.code = "";
            }
        }
        p.normalizeVersions();
        if (p.format < 1) p.format = 1;
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
