package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.*;

/** Java Edition resource pack content: textures, models, block states, sounds and language files. */
public final class AssetGen {
    private final GenContext ctx;
    private final String root;
    private final Set<String> written = new HashSet<>();

    public AssetGen(GenContext ctx, String root) {
        this.ctx = ctx;
        this.root = root;
    }

    private String asset(String namespace, String path) {
        return root + "assets/" + namespace + "/" + path;
    }

    /** Writes a texture PNG into textures/<folder>/ and returns its resource id, or null when missing. */
    public String texture(String assetId, String folder) {
        TextureAsset t = ctx.p.texture(assetId);
        if (t == null) return null;
        String path = asset(ctx.ns, "textures/" + folder + "/" + t.name + ".png");
        if (written.add(path)) {
            ctx.files.bytes(path, t.bytes());
            if (t.animated()) ctx.files.json(path + ".mcmeta", animation(t));
        }
        return ctx.ns + ":" + folder + "/" + t.name;
    }

    /** Animation metadata (texture.png.mcmeta) for a texture with frames stacked from top to bottom. */
    static JsonObject animation(TextureAsset t) {
        JsonObject a = J.obj("frametime", Math.max(1, t.frameTime));
        if (t.interpolate) a.addProperty("interpolate", true);
        return J.obj("animation", a);
    }

    /** Assets of a mod: models for real items and blocks. */
    public void modAssets(boolean forgeRenderType) {
        for (Item it : ctx.all(Item.class)) itemModel(it, it.name);
        for (Block b : ctx.all(Block.class)) block(b, forgeRenderType);
        Map<String, TreeMap<Integer, String>> byBase = new TreeMap<>();
        rigAssets(byBase);
        overrides(byBase);
        armor(true);
        gameplay(true);
        sounds();
        lang();
        shaders();
    }

    /** Shader files: core programs replace the vanilla ones, includes can be used with #moj_import. */
    public void shaders() {
        for (Systems.Shader s : ctx.all(Systems.Shader.class)) {
            if (!Shaders.inRange(s, ctx.v) || s.program.isBlank()) continue;
            String name = s.program.trim();
            if (s.kind.equals("include")) {
                if (!s.fragment.isBlank()) ctx.files.text(asset("minecraft", "shaders/include/" + name + ".glsl"), s.fragment);
                continue;
            }
            if (!s.vertex.isBlank()) ctx.files.text(asset("minecraft", "shaders/core/" + name + ".vsh"), s.vertex);
            if (!s.fragment.isBlank()) ctx.files.text(asset("minecraft", "shaders/core/" + name + ".fsh"), s.fragment);
        }
    }

    /**
     * Item models for the bones of animated mob models. Before 1.21.4 they are custom model data overrides of the
     * rig base item; from 1.21.4 an item model definition per bone is selected with the item_model component.
     */
    private void rigAssets(Map<String, TreeMap<Integer, String>> byBase) {
        for (var e : RigGen.boneModels(ctx.p).entrySet()) {
            Model md = e.getKey();
            for (modstudio.core.Elements.Bone b : e.getValue()) {
                String path = RigGen.modelPath(md, b);
                ctx.files.json(asset(ctx.ns, "models/item/" + path + ".json"), customModel(modstudio.core.Rig.boneModel(md, b), "item"));
                itemDefinition(path);
                byBase.computeIfAbsent(RigGen.BASE_ITEM, k -> new TreeMap<>()).put(RigGen.cmd(md, b), ctx.ns + ":item/" + path);
            }
        }
    }

    /** Custom model data overrides on vanilla base items (before 1.21.4, which selects models with item_model). */
    private void overrides(Map<String, TreeMap<Integer, String>> byBase) {
        if (ctx.v.itemDefinitions()) return;
        for (var e : byBase.entrySet()) {
            String base = e.getKey();
            String parent = Vanilla.CMD_BASES.getOrDefault(base, "minecraft:item/generated");
            JsonArray overrides = new JsonArray();
            for (var o : e.getValue().entrySet()) {
                overrides.add(J.obj("predicate", J.obj("custom_model_data", o.getKey()), "model", o.getValue()));
            }
            JsonObject model = J.obj("parent", parent, "textures", J.obj("layer0", "minecraft:item/" + Ids.path(base)), "overrides", overrides);
            ctx.files.json(asset(Ids.ns(base), "models/item/" + Ids.path(base) + ".json"), model);
        }
    }

    /** Resolves a model texture value: a project texture is written out, a resource id is kept. */
    private String modelTexture(String v, String folder) {
        if (v == null || v.isBlank()) return null;
        if (ctx.p.texture(v) != null) return texture(v, folder);
        return v.contains(":") ? v : "minecraft:" + v;
    }

    private JsonObject customModel(Model m, String folder) {
        return Models.toJava(m, v -> modelTexture(v, folder));
    }

    private void itemModel(Item it, String modelName) {
        Model custom = ctx.p.model(it.model);
        if (custom != null) {
            ctx.files.json(asset(ctx.ns, "models/item/" + modelName + ".json"), customModel(custom, "item"));
            itemDefinition(modelName);
            return;
        }
        String tex = texture(it.texture, "item");
        String parent = it.isTool() ? "minecraft:item/handheld" : "minecraft:item/generated";
        ctx.files.json(asset(ctx.ns, "models/item/" + modelName + ".json"),
                J.obj("parent", parent, "textures", J.obj("layer0", tex == null ? "minecraft:item/barrier" : tex)));
        itemDefinition(modelName);
    }

    /** 1.21.4+ item model definition pointing at models/item/<name>. */
    private void itemDefinition(String name) {
        if (!ctx.v.itemDefinitions()) return;
        ctx.files.json(asset(ctx.ns, "items/" + name + ".json"),
                J.obj("model", J.obj("type", "minecraft:model", "model", ctx.ns + ":item/" + name)));
    }

    private void block(Block b, boolean forgeRenderType) {
        if (b.isCrop()) {
            crop(b, forgeRenderType);
            return;
        }
        String fallback = "minecraft:block/stone";
        JsonObject model;
        String self = ctx.ns + ":block/" + b.name;
        boolean cutout = b.cutout();
        Model custom = b.shape.equals("model") ? ctx.p.model(b.model) : null;
        switch (b.shape) {
            case "cube_bottom_top" -> model = J.obj("parent", "minecraft:block/cube_bottom_top", "textures", J.obj(
                    "top", or(texture(b.textureTop, "block"), fallback),
                    "bottom", or(texture(b.textureBottom, "block"), fallback),
                    "side", or(texture(b.textureSide, "block"), fallback)));
            case "pillar" -> {
                String end = or(texture(b.textureTop, "block"), fallback);
                String side = or(texture(b.textureSide, "block"), fallback);
                model = J.obj("parent", "minecraft:block/cube_column", "textures", J.obj("end", end, "side", side));
                JsonObject hor = J.obj("parent", "minecraft:block/cube_column_horizontal", "textures", J.obj("end", end, "side", side));
                if (cutout && forgeRenderType) hor.addProperty("render_type", "minecraft:cutout");
                ctx.files.json(asset(ctx.ns, "models/block/" + b.name + "_horizontal.json"), hor);
            }
            case "model" -> model = custom != null ? customModel(custom, "block")
                    : J.obj("parent", "minecraft:block/cube_all", "textures", J.obj("all", fallback));
            case "cross" -> model = J.obj("parent", "minecraft:block/cross", "textures", J.obj("cross", or(texture(b.texture, "block"), fallback)));
            default -> model = J.obj("parent", "minecraft:block/cube_all", "textures", J.obj("all", or(texture(b.texture, "block"), fallback)));
        }
        if (cutout && forgeRenderType) model.addProperty("render_type", "minecraft:cutout");
        ctx.files.json(asset(ctx.ns, "models/block/" + b.name + ".json"), model);

        JsonObject states;
        if (b.shape.equals("pillar")) {
            states = J.obj("variants", J.obj(
                    "axis=x", J.obj("model", self + "_horizontal", "x", 90, "y", 90),
                    "axis=y", J.obj("model", self),
                    "axis=z", J.obj("model", self + "_horizontal", "x", 90)));
        } else if (b.shape.equals("model") && b.rotatable) {
            states = J.obj("variants", J.obj(
                    "facing=north", J.obj("model", self),
                    "facing=east", J.obj("model", self, "y", 90),
                    "facing=south", J.obj("model", self, "y", 180),
                    "facing=west", J.obj("model", self, "y", 270)));
        } else {
            states = J.obj("variants", J.obj("", J.obj("model", self)));
        }
        ctx.files.json(asset(ctx.ns, "blockstates/" + b.name + ".json"), states);

        JsonObject itemModel;
        if (b.shape.equals("cross")) {
            itemModel = J.obj("parent", "minecraft:item/generated", "textures", J.obj("layer0", or(texture(b.texture, "block"), fallback)));
        } else itemModel = J.obj("parent", self);
        ctx.files.json(asset(ctx.ns, "models/item/" + b.name + ".json"), itemModel);
        itemDefinition(b.name);
    }

    private static String or(String a, String b) {
        return a == null ? b : a;
    }

    /** Crop models (one per stage texture), the age blockstate and a flat seed item model. */
    private void crop(Block b, boolean forgeRenderType) {
        int n = Math.min(8, b.stageTextures.size());
        JsonObject variants = new JsonObject();
        for (int i = 0; i < Math.max(1, n); i++) {
            String tex = n == 0 ? null : texture(b.stageTextures.get(i), "block");
            JsonObject model = J.obj("parent", "minecraft:block/crop", "textures", J.obj("crop", or(tex, "minecraft:block/wheat_stage" + Math.min(7, i))));
            if (forgeRenderType) model.addProperty("render_type", "minecraft:cutout");
            ctx.files.json(asset(ctx.ns, "models/block/" + b.name + "_stage" + i + ".json"), model);
        }
        for (int age = 0; age <= 7; age++) {
            variants.add("age=" + age, J.obj("model", ctx.ns + ":block/" + b.name + "_stage" + Block.stageOf(age, Math.max(1, n))));
        }
        ctx.files.json(asset(ctx.ns, "blockstates/" + b.name + ".json"), J.obj("variants", variants));
        String seed = or(texture(b.texture, "item"), "minecraft:item/wheat_seeds");
        ctx.files.json(asset(ctx.ns, "models/item/" + b.name + ".json"), J.obj("parent", "minecraft:item/generated", "textures", J.obj("layer0", seed)));
        itemDefinition(b.name);
    }

    public void sounds() {
        JsonObject all = new JsonObject();
        for (Sound s : ctx.all(Sound.class)) {
            JsonArray list = new JsonArray();
            int i = 0;
            for (SoundFile f : s.files) {
                if (f.data == null || f.data.isEmpty()) continue;
                String name = s.files.size() == 1 ? s.name : s.name + "_" + (++i);
                ctx.files.bytes(asset(ctx.ns, "sounds/" + name + ".ogg"), Images.decodeBase64(f.data));
                JsonObject entry = J.obj("name", ctx.ns + ":" + name);
                if (s.stream) entry.addProperty("stream", true);
                list.add(entry);
            }
            if (list.isEmpty()) continue;
            JsonObject def = J.obj("sounds", list);
            if (!s.subtitle.isBlank()) def.addProperty("subtitle", "subtitles." + ctx.ns + "." + s.name);
            all.add(s.name, def);
        }
        if (!all.isEmpty()) ctx.files.json(asset(ctx.ns, "sounds.json"), all);
    }

    public void lang() {
        for (var e : Localization.files(ctx.p).entrySet()) {
            ctx.files.json(asset(ctx.ns, "lang/" + e.getKey() + ".json"), J.el(e.getValue()));
        }
    }

    /** Resource pack for custom-model-data items used by data packs and plugins. */
    public void customModelDataAssets() {
        // base item -> (custom model data -> model)
        Map<String, TreeMap<Integer, String>> byBase = new TreeMap<>();
        for (Item it : ctx.all(Item.class)) {
            itemModel(it, it.name);
            byBase.computeIfAbsent(ItemSpec.base(it), k -> new TreeMap<>()).put(ItemSpec.modelData(it), ctx.ns + ":item/" + it.name);
        }
        rigAssets(byBase);
        overrides(byBase);
        armor(false);
        gameplay(false);
        sounds();
        lang();
        shaders();
    }

    /** Status effect icons (mods) and painting textures. */
    public void gameplay(boolean mod) {
        if (mod) {
            for (StatusEffect e : ctx.all(StatusEffect.class)) {
                TextureAsset t = ctx.p.texture(e.icon);
                if (t != null) ctx.files.bytes(asset(ctx.ns, "textures/mob_effect/" + e.name + ".png"), t.bytes());
            }
        }
        if (!mod && !ctx.singular()) return;
        for (Painting p : ctx.all(Painting.class)) {
            TextureAsset t = ctx.p.texture(p.texture);
            if (t != null) ctx.files.bytes(asset(ctx.ns, "textures/painting/" + p.name + ".png"), t.bytes());
        }
    }

    /**
     * Worn armor textures. 1.21.2+: an equipment asset with humanoid layers (also used by data packs through the
     * equippable component). Before: models/armor layer files, in the minecraft namespace before 1.20.5.
     */
    public void armor(boolean mod) {
        for (ArmorMaterial m : ctx.all(ArmorMaterial.class)) {
            TextureAsset l1 = ctx.p.texture(m.layer1), l2 = ctx.p.texture(m.layer2);
            if (ctx.v.itemDefinitions()) {
                JsonObject layers = new JsonObject();
                String tex = ctx.ns + ":" + m.name;
                if (l1 != null) {
                    ctx.files.bytes(asset(ctx.ns, "textures/entity/equipment/humanoid/" + m.name + ".png"), l1.bytes());
                    layers.add("humanoid", J.arr(J.obj("texture", tex)));
                }
                if (l2 != null) {
                    ctx.files.bytes(asset(ctx.ns, "textures/entity/equipment/humanoid_leggings/" + m.name + ".png"), l2.bytes());
                    layers.add("humanoid_leggings", J.arr(J.obj("texture", tex)));
                }
                ctx.files.json(asset(ctx.ns, "equipment/" + m.name + ".json"), J.obj("layers", layers));
            } else if (mod) {
                boolean modern = ctx.components();
                String ns = modern ? ctx.ns : "minecraft";
                String base = modern ? m.name : ctx.ns + "_" + m.name;
                if (l1 != null) ctx.files.bytes(asset(ns, "textures/models/armor/" + base + "_layer_1.png"), l1.bytes());
                if (l2 != null) ctx.files.bytes(asset(ns, "textures/models/armor/" + base + "_layer_2.png"), l2.bytes());
            }
        }
    }
}
