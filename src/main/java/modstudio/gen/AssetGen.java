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
        fluids();
        Map<String, TreeMap<Integer, String>> byBase = new TreeMap<>();
        rigAssets(byBase);
        overrides(byBase);
        menuArt();
        iconFont();
        screenTextures();
        armor(true);
        gameplay(true);
        tooltips();
        themes();
        sounds();
        lang();
        advancedAssets(true, forgeRenderType);
        shaders();
    }

    /** Shader files: core programs, post-processing programs, and includes (#moj_import). */
    public void shaders() {
        for (Systems.Shader s : ctx.all(Systems.Shader.class)) {
            if (!Shaders.inRange(s, ctx.v) || s.program.isBlank()) continue;
            String name = s.program.trim();
            String fragment = ShaderNodes.fragment(s);
            if (s.kind.equals("include")) {
                if (!fragment.isBlank()) ctx.files.text(asset("minecraft", "shaders/include/" + name + ".glsl"), fragment);
                continue;
            }
            if (s.kind.equals("post")) {
                if (s.graph() && ShaderNodes.order(s.nodes) == null) continue;
                if (!s.vertex.isBlank()) ctx.files.text(asset("minecraft", "shaders/program/" + name + ".vsh"), s.vertex);
                if (!fragment.isBlank()) ctx.files.text(asset("minecraft", "shaders/program/" + name + ".fsh"), fragment);
                continue;
            }
            if (s.graph() && (!ShaderNodes.patchable(s.fragment) || ShaderNodes.order(s.nodes) == null)) continue;
            if (!s.vertex.isBlank()) ctx.files.text(asset("minecraft", "shaders/core/" + name + ".vsh"), s.vertex);
            if (!fragment.isBlank()) ctx.files.text(asset("minecraft", "shaders/core/" + name + ".fsh"), fragment);
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

    /**
     * Menu backgrounds: one bitmap glyph per menu in the font ns:menu, plus space glyphs that move the title to the
     * screen corner and back. The menu title starts with these characters (see MenuArt.prefix).
     */
    private void menuArt() {
        JsonArray providers = new JsonArray();
        JsonObject advances = new JsonObject();
        advances.addProperty(String.valueOf(MenuArt.SHIFT_LEFT), -8);
        int n = 0;
        List<JsonObject> bitmaps = new ArrayList<>();
        for (Systems.Menu m : ctx.all(Systems.Menu.class)) {
            int[] px = MenuArt.image(ctx.p, m);
            if (px == null) continue;
            int w = MenuArt.WIDTH, h = MenuArt.height(m);
            ctx.files.bytes(asset(ctx.ns, "textures/gui/menu/" + m.name + ".png"), Images.png(px, w, h));
            JsonArray chars = new JsonArray();
            chars.add(String.valueOf(MenuArt.glyph(n)));
            bitmaps.add(J.obj("type", "bitmap", "file", ctx.ns + ":gui/menu/" + m.name + ".png", "ascent", 13, "height", h, "chars", chars));
            advances.addProperty(String.valueOf(MenuArt.shiftBack(n)), 8 - MenuArt.advance(px, w, h));
            n++;
        }
        if (n == 0) return;
        providers.add(J.obj("type", "space", "advances", advances));
        for (JsonObject b : bitmaps) providers.add(b);
        ctx.files.json(asset(ctx.ns, "font/menu.json"), J.obj("providers", providers));
    }

    /**
     * Icon fonts: each icon is a bitmap glyph in the default font (fonts of all packs are merged, so the vanilla
     * characters stay), plus the space characters behind {space:N}.
     */
    private void iconFont() {
        List<Systems.IconFont> fonts = ctx.all(Systems.IconFont.class);
        if (fonts.isEmpty()) return;
        JsonArray providers = new JsonArray();
        JsonObject advances = new JsonObject();
        for (int bit = 0; bit < Icons.SPACE_BITS; bit++) {
            advances.addProperty(String.valueOf((char) (Icons.SPACE + bit)), 1 << bit);
            advances.addProperty(String.valueOf((char) (Icons.SPACE + Icons.SPACE_BITS + bit)), -(1 << bit));
        }
        providers.add(J.obj("type", "space", "advances", advances));
        int i = 0;
        for (Systems.IconFont f : fonts) {
            for (Systems.Icon ic : f.icons) {
                if (ic.name == null || ic.name.isBlank()) continue;
                char ch = Icons.charOf(i++);
                TextureAsset t = ctx.p.texture(ic.texture);
                if (t == null) continue;
                String file = "font/icons/" + Ids.toId(ic.name) + ".png";
                ctx.files.bytes(asset(ctx.ns, "textures/" + file), t.animated() ? Images.png(t.firstFrame(), t.width, t.width) : t.bytes());
                int h = Math.max(1, Math.min(256, ic.height));
                JsonArray chars = new JsonArray();
                chars.add(String.valueOf(ch));
                providers.add(J.obj("type", "bitmap", "file", ctx.ns + ":" + file, "ascent", Math.min(h, ic.ascent), "height", h, "chars", chars));
            }
        }
        ctx.files.json(asset("minecraft", "font/default.json"), J.obj("providers", providers));
    }

    /** Images of custom screens, drawn by the mod's own code (animated ones frame by frame). */
    private void screenTextures() {
        for (Systems.CustomScreen s : ctx.all(Systems.CustomScreen.class)) {
            for (Systems.ScreenWidget w : s.widgets) {
                TextureAsset t = ctx.p.texture(w.texture);
                if (t == null) continue;
                String path = asset(ctx.ns, "textures/gui/screen/" + t.name + ".png");
                if (written.add(path)) ctx.files.bytes(path, t.bytes());
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
        String parent = switch (it.kind) {
            case "bow" -> "minecraft:item/bow";
            case "crossbow" -> "minecraft:item/crossbow";
            case "shield" -> "minecraft:item/generated";
            default -> it.isTool() || it.kind.equals("trident") ? "minecraft:item/handheld" : "minecraft:item/generated";
        };
        ctx.files.json(asset(ctx.ns, "models/item/" + modelName + ".json"),
                J.obj("parent", parent, "textures", J.obj("layer0", tex == null ? "minecraft:item/barrier" : tex)));
        itemDefinition(modelName);
    }

    private void fluids() {
        for (Fluid fl : ctx.all(Fluid.class)) {
            String still = or(texture(fl.stillTexture, "block"), "minecraft:block/water_still");
            or(texture(fl.flowingTexture, "block"), still);
            ctx.files.json(asset(ctx.ns, "models/block/" + fl.name + ".json"), J.obj("textures", J.obj("particle", still)));
            ctx.files.json(asset(ctx.ns, "blockstates/" + fl.name + ".json"), J.obj("variants", J.obj("", J.obj("model", ctx.ns + ":block/" + fl.name))));
            if (fl.generateBucket) {
                String bname = fl.name + "_bucket";
                ctx.files.json(asset(ctx.ns, "models/item/" + bname + ".json"),
                        J.obj("parent", "minecraft:item/generated", "textures", J.obj("layer0", "minecraft:item/water_bucket")));
                itemDefinition(bname);
            }
        }
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
        String tex = or(texture(b.texture, "block"), fallback);
        String self = ctx.ns + ":block/" + b.name;
        boolean cutout = b.cutout();
        if (List.of("slab", "stairs", "fence", "wall", "door", "trapdoor", "button", "pressure_plate", "carpet").contains(b.shape)) {
            shapedBlock(b, tex, self, cutout, forgeRenderType);
            return;
        }
        JsonObject model;
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
            case "cross" -> model = J.obj("parent", "minecraft:block/cross", "textures", J.obj("cross", tex));
            default -> model = J.obj("parent", "minecraft:block/cube_all", "textures", J.obj("all", tex));
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
            itemModel = J.obj("parent", "minecraft:item/generated", "textures", J.obj("layer0", tex));
        } else itemModel = J.obj("parent", self);
        ctx.files.json(asset(ctx.ns, "models/item/" + b.name + ".json"), itemModel);
        itemDefinition(b.name);
    }

    private void shapedBlock(Block b, String tex, String self, boolean cutout, boolean forgeRenderType) {
        String ns = ctx.ns;
        JsonObject blockStates;
        switch (b.shape) {
            case "slab" -> {
                blockModel(ns, b.name, "minecraft:block/slab", J.obj("bottom", tex, "top", tex, "side", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_top", "minecraft:block/slab_top", J.obj("bottom", tex, "top", tex, "side", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_double", "minecraft:block/cube_all", J.obj("all", tex), cutout, forgeRenderType);
                blockStates = J.obj("variants", J.obj(
                        "type=bottom", J.obj("model", self),
                        "type=top", J.obj("model", self + "_top"),
                        "type=double", J.obj("model", self + "_double")));
                ctx.files.json(asset(ns, "models/item/" + b.name + ".json"), J.obj("parent", self));
            }
            case "stairs" -> {
                JsonObject t = J.obj("bottom", tex, "top", tex, "side", tex);
                blockModel(ns, b.name, "minecraft:block/stairs", t, cutout, forgeRenderType);
                blockModel(ns, b.name + "_inner", "minecraft:block/inner_stairs", t.deepCopy(), cutout, forgeRenderType);
                blockModel(ns, b.name + "_outer", "minecraft:block/outer_stairs", t.deepCopy(), cutout, forgeRenderType);
                JsonObject variants = new JsonObject();
                String[] dirs = {"east", "west", "south", "north"};
                int[] ys = {0, 180, 90, 270};
                for (int i = 0; i < 4; i++) {
                    for (String half : List.of("bottom", "top")) {
                        int xRot = half.equals("top") ? 180 : 0;
                        for (String shape : List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right")) {
                            String m = self + (shape.startsWith("inner") ? "_inner" : shape.startsWith("outer") ? "_outer" : "");
                            int y = ys[i];
                            if (half.equals("bottom") && (shape.equals("inner_left") || shape.equals("outer_left"))) y = (y + 270) % 360;
                            if (half.equals("top") && (shape.equals("inner_right") || shape.equals("outer_right"))) y = (y + 90) % 360;
                            JsonObject entry = J.obj("model", m);
                            if (xRot != 0) entry.addProperty("x", xRot);
                            if (y != 0) entry.addProperty("y", y);
                            if (xRot != 0 || y != 0) entry.addProperty("uvlock", true);
                            variants.add("facing=" + dirs[i] + ",half=" + half + ",shape=" + shape, entry);
                        }
                    }
                }
                blockStates = J.obj("variants", variants);
                ctx.files.json(asset(ns, "models/item/" + b.name + ".json"), J.obj("parent", self));
            }
            case "fence" -> {
                blockModel(ns, b.name + "_post", "minecraft:block/fence_post", J.obj("texture", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_side", "minecraft:block/fence_side", J.obj("texture", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_inventory", "minecraft:block/fence_inventory", J.obj("texture", tex), cutout, forgeRenderType);
                blockStates = J.obj("multipart", J.arr(
                        J.obj("apply", J.obj("model", self + "_post")),
                        J.obj("when", J.obj("north", "true"), "apply", J.obj("model", self + "_side", "uvlock", true)),
                        J.obj("when", J.obj("east", "true"), "apply", J.obj("model", self + "_side", "y", 90, "uvlock", true)),
                        J.obj("when", J.obj("south", "true"), "apply", J.obj("model", self + "_side", "y", 180, "uvlock", true)),
                        J.obj("when", J.obj("west", "true"), "apply", J.obj("model", self + "_side", "y", 270, "uvlock", true))));
                ctx.files.json(asset(ns, "models/item/" + b.name + ".json"), J.obj("parent", self + "_inventory"));
            }
            case "wall" -> {
                blockModel(ns, b.name + "_post", "minecraft:block/template_wall_post", J.obj("wall", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_side", "minecraft:block/template_wall_side", J.obj("wall", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_side_tall", "minecraft:block/template_wall_side_tall", J.obj("wall", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_inventory", "minecraft:block/wall_inventory", J.obj("wall", tex), cutout, forgeRenderType);
                JsonArray mp = J.arr(J.obj("when", J.obj("up", "true"), "apply", J.obj("model", self + "_post")));
                String[] dirs = {"north", "east", "south", "west"};
                for (int i = 0; i < 4; i++) {
                    mp.add(J.obj("when", J.obj(dirs[i], "low"), "apply", i == 0 ? J.obj("model", self + "_side", "uvlock", true)
                            : J.obj("model", self + "_side", "y", i * 90, "uvlock", true)));
                    mp.add(J.obj("when", J.obj(dirs[i], "tall"), "apply", i == 0 ? J.obj("model", self + "_side_tall", "uvlock", true)
                            : J.obj("model", self + "_side_tall", "y", i * 90, "uvlock", true)));
                }
                blockStates = J.obj("multipart", mp);
                ctx.files.json(asset(ns, "models/item/" + b.name + ".json"), J.obj("parent", self + "_inventory"));
            }
            case "door" -> {
                String topTex = or(texture(b.textureTop, "block"), tex);
                JsonObject dt = J.obj("bottom", tex, "top", topTex);
                for (String part : List.of("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                        "top_left", "top_left_open", "top_right", "top_right_open")) {
                    blockModel(ns, b.name + "_" + part, "minecraft:block/door_" + part, dt.deepCopy(), true, forgeRenderType);
                }
                JsonObject variants = new JsonObject();
                String[] dirs = {"east", "south", "west", "north"};
                for (int i = 0; i < 4; i++) {
                    for (String half : List.of("lower", "upper")) {
                        String hp = half.equals("lower") ? "bottom" : "top";
                        for (String hinge : List.of("left", "right")) {
                            for (boolean open : new boolean[]{false, true}) {
                                String suf = hp + "_" + hinge + (open ? "_open" : "");
                                int y = ((open ? (hinge.equals("left") ? i + 1 : i + 3) : i) & 3) * 90;
                                JsonObject e = J.obj("model", self + "_" + suf);
                                if (y != 0) e.addProperty("y", y);
                                variants.add("facing=" + dirs[i] + ",half=" + half + ",hinge=" + hinge + ",open=" + open, e);
                            }
                        }
                    }
                }
                blockStates = J.obj("variants", variants);
                ctx.files.json(asset(ns, "models/item/" + b.name + ".json"),
                        J.obj("parent", "minecraft:item/generated", "textures", J.obj("layer0", tex)));
            }
            case "trapdoor" -> {
                blockModel(ns, b.name + "_bottom", "minecraft:block/template_trapdoor_bottom", J.obj("texture", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_top", "minecraft:block/template_trapdoor_top", J.obj("texture", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_open", "minecraft:block/template_trapdoor_open", J.obj("texture", tex), cutout, forgeRenderType);
                JsonObject variants = new JsonObject();
                String[] dirs = {"north", "east", "south", "west"};
                for (int i = 0; i < 4; i++) {
                    for (String half : List.of("bottom", "top")) {
                        for (boolean open : new boolean[]{false, true}) {
                            String suf = open ? "_open" : "_" + half;
                            int y = i * 90;
                            JsonObject e = J.obj("model", self + suf);
                            if (y != 0) e.addProperty("y", y);
                            variants.add("facing=" + dirs[i] + ",half=" + half + ",open=" + open, e);
                        }
                    }
                }
                blockStates = J.obj("variants", variants);
                ctx.files.json(asset(ns, "models/item/" + b.name + ".json"), J.obj("parent", self + "_bottom"));
            }
            case "button" -> {
                blockModel(ns, b.name, "minecraft:block/button", J.obj("texture", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_pressed", "minecraft:block/button_pressed", J.obj("texture", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_inventory", "minecraft:block/button_inventory", J.obj("texture", tex), cutout, forgeRenderType);
                JsonObject variants = new JsonObject();
                String[] dirs = {"north", "east", "south", "west"};
                for (int i = 0; i < 4; i++) {
                    for (String face : List.of("floor", "wall", "ceiling")) {
                        for (boolean pow : new boolean[]{false, true}) {
                            JsonObject e = J.obj("model", self + (pow ? "_pressed" : ""));
                            int x = face.equals("wall") ? 90 : face.equals("ceiling") ? 180 : 0;
                            if (x != 0) e.addProperty("x", x);
                            int y = face.equals("ceiling") ? ((i + 2) & 3) * 90 : i * 90;
                            if (y != 0) e.addProperty("y", y);
                            if (face.equals("wall")) e.addProperty("uvlock", true);
                            variants.add("face=" + face + ",facing=" + dirs[i] + ",powered=" + pow, e);
                        }
                    }
                }
                blockStates = J.obj("variants", variants);
                ctx.files.json(asset(ns, "models/item/" + b.name + ".json"), J.obj("parent", self + "_inventory"));
            }
            case "pressure_plate" -> {
                blockModel(ns, b.name, "minecraft:block/pressure_plate_up", J.obj("texture", tex), cutout, forgeRenderType);
                blockModel(ns, b.name + "_down", "minecraft:block/pressure_plate_down", J.obj("texture", tex), cutout, forgeRenderType);
                blockStates = J.obj("variants", J.obj("powered=false", J.obj("model", self), "powered=true", J.obj("model", self + "_down")));
                ctx.files.json(asset(ns, "models/item/" + b.name + ".json"), J.obj("parent", self));
            }
            default -> { // carpet
                blockModel(ns, b.name, "minecraft:block/carpet", J.obj("wool", tex), cutout, forgeRenderType);
                blockStates = J.obj("variants", J.obj("", J.obj("model", self)));
                ctx.files.json(asset(ns, "models/item/" + b.name + ".json"), J.obj("parent", self));
            }
        }
        ctx.files.json(asset(ns, "blockstates/" + b.name + ".json"), blockStates);
        itemDefinition(b.name);
    }

    private void blockModel(String ns, String name, String parent, JsonObject textures, boolean cutout, boolean forgeRenderType) {
        JsonObject o = J.obj("parent", parent, "textures", textures);
        if (cutout && forgeRenderType) o.addProperty("render_type", "minecraft:cutout");
        ctx.files.json(asset(ns, "models/block/" + name + ".json"), o);
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
            Map<String, String> values = new LinkedHashMap<>();
            e.getValue().forEach((k, v) -> values.put(k, Icons.apply(v, GenContext.glyphs())));
            ctx.files.json(asset(ctx.ns, "lang/" + e.getKey() + ".json"), J.el(values));
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
        menuArt();
        iconFont();
        armor(false);
        gameplay(false);
        tooltips();
        themes();
        sounds();
        lang();
        advancedAssets(false, false);
        shaders();
    }

    private void advancedAssets(boolean mods, boolean forgeRenderType) {
        if (mods) {
            for (ModElements.CustomEntity ce : ctx.all(ModElements.CustomEntity.class)) {
                TextureAsset tex = ctx.p.texture(ce.texture);
                if (tex == null) {
                    Model mdl = ModelGen.entityModelSpec(ctx.p, ce);
                    if (mdl != null) tex = ModelGen.firstModelTexture(ctx.p, mdl);
                }
                if (tex != null) ctx.files.bytes(asset(ctx.ns, "textures/entity/" + ce.name + ".png"), tex.bytes());
                if (ce.spawnEgg && ce.isLivingMob()) {
                    String egg = ce.name + "_spawn_egg";
                    ctx.files.json(asset(ctx.ns, "models/item/" + egg + ".json"), J.obj("parent", "minecraft:item/template_spawn_egg"));
                    itemDefinition(egg);
                }
            }
            for (ModElements.Container ct : ctx.all(ModElements.Container.class)) {
                String bn = ct.block.isBlank() ? ct.name : Ids.path(Ids.full(ct.block));
                if (ctx.all(Block.class).stream().anyMatch(b -> b.name.equals(bn))) continue;
                String self = ctx.ns + ":block/" + bn;
                blockModel(ctx.ns, bn, "minecraft:block/cube_all", J.obj("all", "minecraft:block/furnace_top"), false, forgeRenderType);
                ctx.files.json(asset(ctx.ns, "blockstates/" + bn + ".json"), J.obj("variants", J.obj("", J.obj("model", self))));
                ctx.files.json(asset(ctx.ns, "models/item/" + bn + ".json"), J.obj("parent", self));
                itemDefinition(bn);
            }
            for (ModElements.Portal pt : ctx.all(ModElements.Portal.class)) {
                String ptex = or(texture(pt.texture, "block"), "minecraft:block/nether_portal");
                JsonObject pm = J.obj("textures", J.obj("particle", ptex, "portal", ptex),
                        "elements", J.arr(
                                J.obj("from", J.arr(0, 0, 6), "to", J.arr(16, 16, 10), "faces", J.obj(
                                        "north", J.obj("uv", J.arr(0, 0, 16, 16), "texture", "#portal"),
                                        "south", J.obj("uv", J.arr(0, 0, 16, 16), "texture", "#portal")))));
                if (forgeRenderType) pm.addProperty("render_type", "minecraft:translucent");
                String bname = pt.name + "_portal";
                ctx.files.json(asset(ctx.ns, "models/block/" + bname + ".json"), pm);
                ctx.files.json(asset(ctx.ns, "blockstates/" + bname + ".json"), J.obj("variants", J.obj(
                        "axis=z", J.obj("model", ctx.ns + ":block/" + bname, "y", 90),
                        "axis=x", J.obj("model", ctx.ns + ":block/" + bname))));
                if (pt.generateIgniter) {
                    String iname = pt.name + "_igniter";
                    String itex = or(texture(pt.igniterTexture, "item"), "minecraft:item/flint_and_steel");
                    ctx.files.json(asset(ctx.ns, "models/item/" + iname + ".json"), J.obj("parent", "minecraft:item/generated", "textures", J.obj("layer0", itex)));
                    itemDefinition(iname);
                }
            }
        }
        for (ModElements.ArmorTrim at : ctx.all(ModElements.ArmorTrim.class)) {
            if (mods && at.generateTemplate) {
                String tname = at.name + "_armor_trim_smithing_template";
                String ttex = or(texture(at.texture, "item"), "minecraft:item/coast_armor_trim_smithing_template");
                ctx.files.json(asset(ctx.ns, "models/item/" + tname + ".json"), J.obj("parent", "minecraft:item/generated", "textures", J.obj("layer0", ttex)));
                itemDefinition(tname);
            }
            TextureAsset tex = ctx.p.texture(at.texture);
            if (tex != null) {
                ctx.files.bytes(asset(ctx.ns, "textures/trims/models/armor/" + at.name + ".png"), tex.bytes());
                ctx.files.bytes(asset(ctx.ns, "textures/trims/models/armor/" + at.name + "_leggings.png"), tex.bytes());
            }
        }
        for (ModElements.BannerPattern bp : ctx.all(ModElements.BannerPattern.class)) {
            TextureAsset tex = ctx.p.texture(bp.texture);
            if (tex != null) {
                ctx.files.bytes(asset(ctx.ns, "textures/entity/banner/" + bp.name + ".png"), tex.bytes());
                ctx.files.bytes(asset(ctx.ns, "textures/entity/shield/" + bp.name + ".png"), tex.bytes());
            }
        }
    }

    /** Tooltip box sprites for items with their own tooltip look (the tooltip_style component, 1.21.2+). */
    private void tooltips() {
        if (!ctx.v.atLeast(McVersion.V1_21_4)) return;
        for (Item it : ctx.all(Item.class)) {
            if (!it.customTooltip) continue;
            String base = "textures/gui/sprites/tooltip/" + it.name;
            TextureAsset bt = ctx.p.texture(it.tooltipBackgroundTexture), ft = ctx.p.texture(it.tooltipFrameTexture);
            int bg = TooltipArt.argb(it.tooltipBackground, it.tooltipBackgroundOpacity);
            int top = TooltipArt.argb(it.tooltipBorderTop, it.tooltipBorderOpacity), bottom = TooltipArt.argb(it.tooltipBorderBottom, it.tooltipBorderOpacity);
            ctx.files.bytes(asset(ctx.ns, base + "_background.png"), bt != null ? bt.bytes() : Images.png(TooltipArt.background(bg), TooltipArt.SIZE, TooltipArt.SIZE));
            ctx.files.json(asset(ctx.ns, base + "_background.png.mcmeta"), nineSlice(bt == null ? TooltipArt.SIZE : bt.width, bt == null ? TooltipArt.SIZE : bt.height, 9, false));
            ctx.files.bytes(asset(ctx.ns, base + "_frame.png"), ft != null ? ft.bytes() : Images.png(TooltipArt.frame(top, bottom), TooltipArt.SIZE, TooltipArt.SIZE));
            ctx.files.json(asset(ctx.ns, base + "_frame.png.mcmeta"), nineSlice(ft == null ? TooltipArt.SIZE : ft.width, ft == null ? TooltipArt.SIZE : ft.height, 10, true));
        }
    }

    /** UI themes: replaced vanilla textures, the enchantment glint, splash texts and the default tooltip box. */
    private void themes() {
        for (Systems.Theme th : ctx.all(Systems.Theme.class)) {
            for (Systems.TextureOverride o : th.textures) {
                TextureAsset t = ctx.p.texture(o.texture);
                String rel = o.path.trim().replace('\\', '/');
                if (t == null || rel.isEmpty() || rel.contains("..")) continue;
                String path = asset("minecraft", "textures/" + rel + ".png");
                ctx.files.bytes(path, t.bytes());
                JsonObject meta = ThemeArt.meta(o.meta, t, rel.startsWith("gui/sprites/"));
                if (meta != null) ctx.files.json(path + ".mcmeta", meta);
            }
            if (!th.glint.equals("keep")) {
                TextureAsset gt = th.glint.equals("texture") ? ctx.p.texture(th.glintTexture) : null;
                byte[] png = gt != null ? Images.png(gt.firstFrame(), gt.width, gt.frameHeight())
                        : Images.png(ThemeArt.glint(th.glintColor, th.glintPattern), ThemeArt.GLINT, ThemeArt.GLINT);
                ctx.files.bytes(asset("minecraft", "textures/misc/enchanted_glint_item.png"), png);
                if (th.glintArmor) ctx.files.bytes(asset("minecraft", "textures/misc/enchanted_glint_entity.png"), png);
            }
            if (!th.splashes.isBlank()) {
                StringBuilder sb = new StringBuilder();
                for (String line : th.splashes.split("\\R")) if (!line.isBlank()) sb.append(line.strip()).append('\n');
                ctx.files.text(asset("minecraft", "texts/splashes.txt"), sb.toString());
            }
            if (th.tooltip && ctx.v.atLeast(McVersion.V1_21_4)) {
                int bg = TooltipArt.argb(th.tooltipBackground, th.tooltipBackgroundOpacity);
                int top = TooltipArt.argb(th.tooltipBorderTop, th.tooltipBorderOpacity), bottom = TooltipArt.argb(th.tooltipBorderBottom, th.tooltipBorderOpacity);
                String base = "textures/gui/sprites/tooltip/";
                ctx.files.bytes(asset("minecraft", base + "background.png"), Images.png(TooltipArt.background(bg), TooltipArt.SIZE, TooltipArt.SIZE));
                ctx.files.json(asset("minecraft", base + "background.png.mcmeta"), nineSlice(TooltipArt.SIZE, TooltipArt.SIZE, 9, false));
                ctx.files.bytes(asset("minecraft", base + "frame.png"), Images.png(TooltipArt.frame(top, bottom), TooltipArt.SIZE, TooltipArt.SIZE));
                ctx.files.json(asset("minecraft", base + "frame.png.mcmeta"), nineSlice(TooltipArt.SIZE, TooltipArt.SIZE, 10, true));
            }
        }
    }

    private static JsonObject nineSlice(int w, int h, int border, boolean stretch) {
        return J.obj("gui", J.obj("scaling", J.obj("type", "nine_slice", "width", w, "height", h, "border", border, "stretch_inner", stretch)));
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
            if (l1 == null && !m.armorTexture.isBlank()) l1 = ctx.p.texture(m.armorTexture);
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
        if (mod) {
            Set<String> emittedArmorTex = new HashSet<>();
            for (Item it : ctx.all(Item.class)) {
                if (!it.isArmor()) continue;
                Model mdl = ModelGen.wornArmorModel(ctx.p, it);
                if (mdl == null) continue;
                ArmorMaterial am = ModelGen.armorMaterialOf(ctx.p, it);
                String key = ModelGen.armor3dTexKey(ctx.p, it, am, mdl);
                if (key == null || !emittedArmorTex.add(key)) continue;
                TextureAsset tex = null;
                if (key.startsWith("mat_") && am != null) tex = ctx.p.texture(am.armorTexture);
                else if (key.startsWith("mdl_")) tex = ModelGen.firstModelTexture(ctx.p, mdl);
                else if (key.startsWith("lay_") && am != null) tex = ctx.p.texture(am.layer1);
                if (tex != null) ctx.files.bytes(asset(ctx.ns, "textures/entity/armor_3d/" + key + ".png"), tex.bytes());
            }
        }
    }
}
