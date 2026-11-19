package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bedrock Edition add-on: behavior pack + resource pack, procedures through the Script API. */
public final class BedrockGen {
    private final GenContext ctx;
    private final String bp;
    private final String rp;
    private final Set<String> textures = new HashSet<>();
    private final JsonObject itemTextures = new JsonObject();
    private final JsonObject terrainTextures = new JsonObject();
    private final JsonArray flipbooks = new JsonArray();

    public BedrockGen(GenContext ctx) {
        this.ctx = ctx;
        this.bp = ctx.ns + "_BP/";
        this.rp = ctx.ns + "_RP/";
    }

    private String uuid(String salt) {
        return UUID.nameUUIDFromBytes((ctx.p.id + ":" + salt).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private JsonArray version() {
        int[] v = {1, 0, 0};
        String[] parts = ctx.p.meta.version.split("[.\\-+]");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                v[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
            }
        }
        return J.arr(v[0], v[1], v[2]);
    }

    private JsonArray engine() {
        String[] parts = ctx.version("min_engine").split("\\.");
        int[] v = {1, 21, 0};
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                v[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
            }
        }
        return J.arr(v[0], v[1], v[2]);
    }

    private record ScriptHook(String modulePath, String hook, String method) {}

    private static boolean isScriptFile(CodeFile cf) {
        String lang = cf.resolvedLanguage();
        if ("typescript".equals(lang) || "javascript".equals(lang)) return true;
        String low = cf.path == null ? "" : cf.path.trim().toLowerCase(Locale.ROOT);
        return low.endsWith(".js") || low.endsWith(".ts") || low.endsWith(".tsx") || low.endsWith(".mjs");
    }

    public void generate() {
        boolean hasUi = !ctx.all(Systems.Dialog.class).isEmpty();
        boolean hasKeys = ctx.all(Systems.KeyBind.class).stream().anyMatch(k -> !k.fallback.equals("none"));
        boolean customScripts = ctx.all(CodeFile.class).stream()
                .anyMatch(cf -> Scaffold.matchesTarget(cf.target, ctx.target) && isScriptFile(cf));
        boolean scripts = customScripts || !ctx.all(Procedure.class).isEmpty() || !ctx.all(Elements.Command.class).isEmpty()
                || ctx.all(Function.class).stream().anyMatch(f -> f.onLoad)
                || !ctx.all(Systems.Cutscene.class).isEmpty() || hasUi || hasKeys;
        manifests(scripts, hasUi);
        items();
        blocks();
        entities();
        recipes();
        lootTables();
        functions();
        sounds();
        lang();
        if (!itemTextures.isEmpty()) ctx.files.json(rp + "textures/item_texture.json",
                J.obj("resource_pack_name", ctx.ns, "texture_name", "atlas.items", "texture_data", itemTextures));
        if (!flipbooks.isEmpty()) ctx.files.json(rp + "textures/flipbook_textures.json", flipbooks);
        if (!terrainTextures.isEmpty()) ctx.files.json(rp + "textures/terrain_texture.json",
                J.obj("resource_pack_name", ctx.ns, "texture_name", "atlas.terrain", "padding", 8, "num_mip_levels", 4, "texture_data", terrainTextures));
        TextureAsset icon = ctx.p.texture(ctx.p.meta.icon);
        if (icon != null) {
            ctx.files.bytes(bp + "pack_icon.png", icon.bytes());
            ctx.files.bytes(rp + "pack_icon.png", icon.bytes());
        }
        if (!ctx.all(Advancement.class).isEmpty()) ctx.note("advancements are not available in Bedrock Edition");
        if (!ctx.all(Tag.class).isEmpty()) ctx.note("tags are not exported to Bedrock Edition");
        List<ScriptHook> extraModules = new ArrayList<>();
        String userMainOverride = null;
        for (CodeFile cf : ctx.all(CodeFile.class)) {
            if (!Scaffold.matchesTarget(cf.target, ctx.target) || !isScriptFile(cf)) continue;
            String rawPath = cf.path == null ? "" : cf.path.trim();
            String norm = rawPath.replace('\\', '/').replace("{package}", ctx.pkgPath).replace("{modid}", ctx.ns);
            String low = norm.toLowerCase(Locale.ROOT);
            if (!low.endsWith(".js") && !low.endsWith(".ts") && !low.endsWith(".tsx") && !low.endsWith(".mjs")) {
                String base = Ids.toId(cf.name == null || cf.name.isBlank() ? "script" : cf.name);
                if (base.isEmpty()) base = "script";
                norm = "scripts/" + base + ("typescript".equals(cf.resolvedLanguage()) ? ".ts" : ".js");
            }
            String content = cf.content.replace("{package_name}", ctx.pkg).replace("{modid}", ctx.ns).replace("{main_class}", ctx.mainClass);
            if (norm.toLowerCase(Locale.ROOT).endsWith(".ts") || norm.toLowerCase(Locale.ROOT).endsWith(".tsx")) {
                norm = norm.replaceFirst("\\.(?i)tsx?$", ".js");
                content = transpileTsToJs(content);
            } else if ("typescript".equals(cf.resolvedLanguage())) {
                content = transpileTsToJs(content);
            }
            String rel = norm;
            if (rel.startsWith(bp)) rel = rel.substring(bp.length());
            while (rel.startsWith("/")) rel = rel.substring(1);
            if (!rel.startsWith("scripts/")) rel = "scripts/" + rel;
            String modRel = rel.substring("scripts/".length());
            if ("main.js".equals(modRel)) userMainOverride = content;
            else extraModules.add(new ScriptHook("./" + modRel, cf.hook, cf.hookMethod));
            ctx.files.text(bp + rel, content);
        }
        if (scripts) script(extraModules);
        if (userMainOverride != null) ctx.files.text(bp + "scripts/main.js", userMainOverride);
        for (CodeFile cf : ctx.all(CodeFile.class)) {
            if (!Scaffold.matchesTarget(cf.target, ctx.target) || cf.path.isBlank()) continue;
            if (cf.isJvmLanguage() || isScriptFile(cf)) continue;
            String path = cf.path.replace("{package}", ctx.pkgPath).replace("{modid}", ctx.ns);
            if (path.startsWith("src/main/resources/")) path = path.substring("src/main/resources/".length());
            ctx.files.text(path, cf.content.replace("{package_name}", ctx.pkg).replace("{modid}", ctx.ns).replace("{main_class}", ctx.mainClass));
        }
    }

    static String transpileTsToJs(String ts) {
        if (ts == null || ts.isBlank()) return "";
        String out = ts;
        out = out.replaceAll("(?m)^\\s*import\\s+type\\s+[^;]+;?\\r?\\n?", "");
        out = out.replaceAll("(?m)^\\s*export\\s+(?:interface|type)\\s+[^{;]+(?:\\{[^}]*\\}|=[^;]+;)?\\r?\\n?", "");
        out = out.replaceAll("(?m)^\\s*(?:interface|type)\\s+[A-Za-z_][\\w$]*(?:<[^>]+>)?\\s*(?:=[^;]+;|\\{[^}]*\\})\\r?\\n?", "");
        out = out.replaceAll("\\b((?:function\\s+[A-Za-z_][\\w$]*|(?:const|let|var)?\\s*[A-Za-z_][\\w$]*\\s*=\\s*)?\\([^)]*\\))\\s*:\\s*[A-Za-z_][\\w$.<>\\[\\]|&\\s]*(?=\\s*(?:=>|\\{))", "$1");
        out = out.replaceAll("(\\(|,)\\s*([A-Za-z_][\\w$]*)\\s*\\??:\\s*[A-Za-z_][\\w$.<>\\[\\]|&\\s]*(?=\\s*(?:[,)=]))", "$1 $2");
        out = out.replaceAll("\\b(const|let|var)\\s+([A-Za-z_][\\w$]*)\\s*:\\s*[A-Za-z_][\\w$.<>\\[\\]|&\\s]*(?=\\s*[=;])", "$1 $2");
        out = out.replaceAll("\\s+as\\s+(?:const|[A-Za-z_][\\w$.<>\\[\\]|&]*)", "");
        return out;
    }

    private void manifests(boolean scripts, boolean ui) {
        JsonArray bpModules = J.arr(J.obj("type", "data", "uuid", uuid("bp-data"), "version", version()));
        JsonArray bpDeps = J.arr(J.obj("uuid", uuid("rp-header"), "version", version()));
        if (scripts) {
            bpModules.add(J.obj("type", "script", "language", "javascript", "uuid", uuid("bp-script"), "entry", "scripts/main.js", "version", version()));
            bpDeps.add(J.obj("module_name", "@minecraft/server", "version", ctx.version("script_api")));
            if (ui) bpDeps.add(J.obj("module_name", "@minecraft/server-ui", "version", "1.2.0"));
        }
        ctx.files.json(bp + "manifest.json", J.obj("format_version", 2,
                "header", J.obj("name", ctx.p.meta.name + " BP", "description", ctx.p.meta.description, "uuid", uuid("bp-header"),
                        "version", version(), "min_engine_version", engine()),
                "modules", bpModules, "dependencies", bpDeps));
        ctx.files.json(rp + "manifest.json", J.obj("format_version", 2,
                "header", J.obj("name", ctx.p.meta.name + " RP", "description", ctx.p.meta.description, "uuid", uuid("rp-header"),
                        "version", version(), "min_engine_version", engine()),
                "modules", J.arr(J.obj("type", "resources", "uuid", uuid("rp-res"), "version", version()))));
    }

    /** Terrain texture key for a model texture: project textures are exported, vanilla ids use their short name. */
    private String bedrockModelTexture(String v) {
        if (v == null || v.isBlank()) return "stone";
        String k = texKey(v, true);
        if (k != null) return k;
        String path = v.contains(":") ? v.substring(v.indexOf(':') + 1) : v;
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static double[] bounds(Model m) {
        double[] r = {16, 16, 16, 0, 0, 0};
        for (double[] b : Models.boxes(m)) {
            for (int i = 0; i < 3; i++) {
                r[i] = Math.min(r[i], Models.clamp(b[i], 0, 16));
                r[i + 3] = Math.max(r[i + 3], Models.clamp(b[i + 3], 0, 16));
            }
        }
        if (r[3] <= r[0]) return new double[]{0, 0, 0, 16, 16, 16};
        return r;
    }

    private String texKey(String assetId, boolean block) {
        TextureAsset t = ctx.p.texture(assetId);
        if (t == null) return null;
        String folder = block ? "blocks" : "items";
        String key = ctx.ns + "_" + t.name;
        if (textures.add(folder + "/" + t.name)) {
            String path = "textures/" + folder + "/" + ctx.ns + "/" + t.name;
            // Bedrock animates block textures through flipbooks; item icons show the first frame.
            byte[] png = t.animated() && !block ? modstudio.core.Images.png(t.firstFrame(), t.width, t.width) : t.bytes();
            ctx.files.bytes(rp + path + ".png", png);
            (block ? terrainTextures : itemTextures).add(key, J.obj("textures", path));
            if (block && t.animated()) {
                JsonObject fb = J.obj("flipbook_texture", path, "atlas_tile", key, "ticks_per_frame", Math.max(1, t.frameTime));
                if (t.interpolate) fb.addProperty("blend_frames", true);
                flipbooks.add(fb);
            }
        }
        return key;
    }

    private String category(String tab, boolean block) {
        if (tab.contains("combat") || tab.contains("tools")) return "equipment";
        if (tab.contains("natural")) return "nature";
        if (block || tab.contains("building") || tab.contains("blocks")) return "construction";
        return "items";
    }

    private void items() {
        for (Item it : ctx.all(Item.class)) {
            JsonObject c = new JsonObject();
            String tex = texKey(it.texture, false);
            if (tex != null) c.addProperty("minecraft:icon", tex);
            c.add("minecraft:display_name", J.obj("value", it.displayName.isBlank() ? Ids.titleCase(it.name) : it.displayName));
            c.addProperty("minecraft:max_stack_size", it.damageable() ? 1 : Math.max(1, Math.min(64, it.maxStack)));
            armorPiece(it, c);
            if (it.glint) c.addProperty("minecraft:glint", true);
            if (it.burnTime > 0) c.add("minecraft:fuel", J.obj("duration", it.burnTime / 20.0));
            if (it.kind.equals("food")) {
                c.add("minecraft:food", J.obj("nutrition", it.nutrition, "saturation_modifier", it.saturation * 2, "can_always_eat", it.alwaysEdible));
                c.addProperty("minecraft:use_animation", "eat");
                c.add("minecraft:use_modifiers", J.obj("use_duration", it.fastFood ? 0.8 : 1.6, "movement_modifier", 0.35));
            }
            if (it.useCooldown > 0) c.add("minecraft:cooldown", J.obj("category", ctx.id(it.name), "duration", it.useCooldown));
            if (it.kind.equals("throwable")) {
                c.add("minecraft:throwable", J.obj("do_swing_animation", true));
                c.add("minecraft:projectile", J.obj("minimum_critical_power", 0.0, "projectile_entity", "minecraft:snowball"));
            }
            if (it.isTool()) {
                ItemSpec.ToolTierStats ts = ItemSpec.toolStats(ctx.p, it.tier);
                int dur = it.maxDamage > 0 ? it.maxDamage : ts.durability();
                int ench = it.enchantability >= 0 ? it.enchantability : ts.enchantability();
                c.addProperty("minecraft:hand_equipped", true);
                c.add("minecraft:durability", J.obj("max_durability", dur));
                int dmg = (int) Math.max(1, Math.round(it.attackDamage + ts.attackDamageBonus() + 1));
                c.addProperty("minecraft:damage", dmg);
                c.add("minecraft:enchantable", J.obj("value", Math.max(1, ench), "slot", it.kind));
                String rep = !it.repairItem.isBlank() ? it.repairItem : ts.repairItem();
                if (!rep.isBlank() && !Ids.isTag(Ids.full(rep))) {
                    c.add("minecraft:repairable", J.obj("repair_items", J.arr(J.obj("items", J.arr(Ids.full(rep)),
                            "repair_amount", Math.max(1, dur / 4)))));
                }
                String blocks = switch (it.kind) {
                    case "pickaxe" -> "q.any_tag('stone', 'metal', 'rail', 'minecraft:is_pickaxe_item_destructible')";
                    case "axe" -> "q.any_tag('wood', 'pumpkin', 'plant', 'minecraft:is_axe_item_destructible')";
                    case "shovel" -> "q.any_tag('dirt', 'sand', 'gravel', 'grass', 'snow', 'minecraft:is_shovel_item_destructible')";
                    case "hoe" -> "q.any_tag('minecraft:is_hoe_item_destructible')";
                    default -> null;
                };
                if (blocks != null) {
                    c.add("minecraft:digger", J.obj("use_efficiency", true, "destroy_speeds",
                            J.arr(J.obj("block", J.obj("tags", blocks), "speed", Math.round(ts.speed())))));
                }
            }
            ctx.files.json(bp + "items/" + it.name + ".json", J.obj("format_version", "1.21.0", "minecraft:item", J.obj(
                    "description", J.obj("identifier", ctx.id(it.name), "menu_category", J.obj("category", category(it.creativeTab, false))),
                    "components", c)));
        }
    }

    private static final java.util.Map<String, String[]> ARMOR_SLOTS = java.util.Map.of(
            "helmet", new String[]{"slot.armor.head", "armor_head", "helmet", "variable.helmet_layer_visible = 0.0;"},
            "chestplate", new String[]{"slot.armor.chest", "armor_torso", "chestplate", "variable.chest_layer_visible = 0.0;"},
            "leggings", new String[]{"slot.armor.legs", "armor_legs", "leggings", "variable.leg_layer_visible = 0.0;"},
            "boots", new String[]{"slot.armor.feet", "armor_feet", "boots", "variable.boot_layer_visible = 0.0;"});

    /** Wearable armor: protection, durability, repair and an attachable that draws the worn layer texture. */
    private void armorPiece(Item it, JsonObject c) {
        if (!it.isArmor() || !(ctx.p.byName(ElementType.ARMOR_MATERIAL, it.armorMaterial) instanceof ArmorMaterial m)) return;
        String[] slot = ARMOR_SLOTS.get(it.kind);
        c.add("minecraft:wearable", J.obj("slot", slot[0], "protection", m.defense(it.kind)));
        c.add("minecraft:durability", J.obj("max_durability", m.durabilityOf(it.kind)));
        c.add("minecraft:enchantable", J.obj("value", Math.max(1, m.enchantability), "slot", slot[1]));
        if (!m.repairItem.isBlank() && !Ids.isTag(Ids.full(m.repairItem))) {
            c.add("minecraft:repairable", J.obj("repair_items", J.arr(J.obj("items", J.arr(Ids.full(m.repairItem)),
                    "repair_amount", Math.max(1, m.durabilityOf(it.kind) / 4)))));
        }
        Model worn = ModelGen.wornArmorModel(ctx.p, it);
        String geoName = "geometry.humanoid.armor." + slot[2];
        String texPath;
        if (worn != null) {
            geoName = "geometry." + ctx.ns + "." + it.name;
            ctx.files.json(rp + "models/entity/" + it.name + ".geo.json", Models.toBedrock(worn, geoName, Math.max(16, worn.textureWidth), Math.max(16, worn.textureHeight)));
            String texKey = ModelGen.armor3dTexKey(ctx.p, it, m, worn);
            TextureAsset texAsset = texKey == null ? null
                    : texKey.startsWith("mat_") ? ctx.p.texture(m.armorTexture)
                    : texKey.startsWith("mdl_") ? ModelGen.firstModelTexture(ctx.p, worn)
                    : ctx.p.texture(m.layer1);
            if (texAsset != null) {
                texPath = "textures/models/armor/" + ctx.ns + "_" + it.name + "_3d";
                ctx.files.bytes(rp + texPath + ".png", texAsset.bytes());
            } else {
                texPath = "textures/models/armor/iron_1";
            }
        } else {
            TextureAsset layer = ctx.p.texture(it.kind.equals("leggings") ? m.layer2 : m.layer1);
            String ltex = "textures/models/armor/" + ctx.ns + "_" + m.name + (it.kind.equals("leggings") ? "_layer_2" : "_layer_1");
            if (layer != null) ctx.files.bytes(rp + ltex + ".png", layer.bytes());
            texPath = layer != null ? ltex : "textures/models/armor/iron_1";
        }
        JsonObject desc = J.obj("identifier", ctx.id(it.name),
                "materials", J.obj("default", "armor", "enchanted", "armor_enchanted"),
                "textures", J.obj("default", texPath, "enchanted", "textures/misc/enchanted_item_glint"),
                "geometry", J.obj("default", geoName),
                "scripts", J.obj("parent_setup", slot[3]),
                "render_controllers", J.arr("controller.render.armor"));
        ctx.files.json(rp + "attachables/" + it.name + ".json", J.obj("format_version", "1.10.0", "minecraft:attachable", J.obj("description", desc)));
    }

    private static String sound(String s) {
        return switch (s) {
            case "wool" -> "cloth";
            case "amethyst" -> "amethyst_block";
            case "bone" -> "bone_block";
            default -> s;
        };
    }

    private void blocks() {
        JsonObject blocksJson = J.obj("format_version", J.arr(1, 1, 0));
        for (Block b : ctx.all(Block.class)) {
            if (b.isCrop()) {
                ctx.note("crop " + b.name + " is not exported to Bedrock Edition (custom crops need scripts there)");
                continue;
            }
            JsonObject c = new JsonObject();
            c.add("minecraft:destructible_by_mining", J.obj("seconds_to_destroy", b.hardness < 0 ? 1e9 : b.hardness));
            c.add("minecraft:destructible_by_explosion", J.obj("explosion_resistance", b.resistance));
            if (b.light > 0) c.addProperty("minecraft:light_emission", Math.min(15, b.light));
            if (Math.abs(b.friction - 0.6) > 1e-6) c.addProperty("minecraft:friction", Math.max(0, Math.min(0.9, 1 - b.friction)));
            Model custom = b.shape.equals("model") ? ctx.p.model(b.model) : null;
            String render = b.cutout() ? "alpha_test" : "opaque";
            JsonObject mats = new JsonObject();
            JsonObject traits = null;
            JsonArray permutations = null;
            if (custom != null) {
                String geo = "geometry." + ctx.ns + "." + b.name;
                ctx.files.json(rp + "models/blocks/" + b.name + ".geo.json", Models.toBedrock(custom, geo, custom.textureWidth, custom.textureHeight));
                c.addProperty("minecraft:geometry", geo);
                String first = null;
                for (var t : custom.textures.entrySet()) {
                    String key = bedrockModelTexture(t.getValue());
                    if (first == null) first = key;
                    mats.add(t.getKey(), J.obj("texture", key, "render_method", render));
                }
                mats.add("*", J.obj("texture", first == null ? "stone" : first, "render_method", render));
                if (b.modelCollision && !Models.isFullCube(custom)) {
                    double[] bb = bounds(custom);
                    JsonObject box = J.obj("origin", J.arr(bb[0] - 8, bb[1], bb[2] - 8), "size", J.arr(bb[3] - bb[0], bb[4] - bb[1], bb[5] - bb[2]));
                    c.add("minecraft:collision_box", box);
                    c.add("minecraft:selection_box", box.deepCopy());
                }
                if (b.rotatable) {
                    traits = J.obj("minecraft:placement_direction", J.obj("enabled_states", J.arr("minecraft:cardinal_direction"), "y_rotation_offset", 180));
                    permutations = new JsonArray();
                    String[][] rots = {{"north", "0"}, {"west", "90"}, {"south", "180"}, {"east", "-90"}};
                    for (String[] r : rots) {
                        permutations.add(J.obj("condition", "q.block_state('minecraft:cardinal_direction') == '" + r[0] + "'",
                                "components", J.obj("minecraft:transformation", J.obj("rotation", J.arr(0, Integer.parseInt(r[1]), 0)))));
                    }
                }
            } else {
                c.addProperty("minecraft:geometry", "minecraft:geometry.full_block");
                String all = texKey(b.face("side"), true);
                mats.add("*", J.obj("texture", all == null ? "stone" : all, "render_method", render));
            }
            if (custom == null && !b.shape.equals("cube") && !b.shape.equals("cross")) {
                String top = texKey(b.face("top"), true), bottom = texKey(b.face("bottom"), true);
                if (top != null) mats.add("up", J.obj("texture", top, "render_method", render));
                if (bottom != null) mats.add("down", J.obj("texture", bottom, "render_method", render));
            }
            c.add("minecraft:material_instances", mats);
            if (b.transparent) c.addProperty("minecraft:light_dampening", 0);
            c.addProperty("minecraft:loot", "loot_tables/blocks/" + ctx.ns + "/" + b.name + ".json");
            if (!b.tool.equals("none")) c.add("tag:minecraft:is_" + b.tool + "_item_destructible", new JsonObject());
            JsonObject description = J.obj("identifier", ctx.id(b.name), "menu_category", J.obj("category", category(b.creativeTab, true)));
            if (traits != null) description.add("traits", traits);
            JsonObject blockDef = J.obj("description", description, "components", c);
            if (permutations != null) blockDef.add("permutations", permutations);
            ctx.files.json(bp + "blocks/" + b.name + ".json", J.obj("format_version", "1.21.0", "minecraft:block", blockDef));
            blocksJson.add(ctx.id(b.name), J.obj("sound", sound(b.sound)));
            if (b.shape.equals("cross") || b.shape.equals("pillar")) ctx.note("block " + b.name + ": shape is exported as a full block in Bedrock Edition");

            JsonObject entry;
            if (b.drop.equals("item") && !b.dropItem.isBlank()) {
                entry = J.obj("type", "item", "name", Ids.full(b.dropItem), "weight", 1);
                if (b.dropMin != 1 || b.dropMax != 1) entry.add("functions", J.arr(J.obj("function", "set_count", "count", J.obj("min", b.dropMin, "max", Math.max(b.dropMin, b.dropMax)))));
            } else entry = J.obj("type", "item", "name", ctx.id(b.name), "weight", 1);
            JsonObject table = b.drop.equals("none") ? J.obj("pools", new JsonArray()) : J.obj("pools", J.arr(J.obj("rolls", 1, "entries", J.arr(entry))));
            ctx.files.json(bp + "loot_tables/blocks/" + ctx.ns + "/" + b.name + ".json", table);
        }
        if (blocksJson.size() > 1) ctx.files.json(rp + "blocks.json", blocksJson);
    }

    private void entities() {
        for (ModElements.CustomEntity ce : ctx.all(ModElements.CustomEntity.class)) {
            if (!ce.isLivingMob()) continue;
            String eid = ctx.id(ce.name);
            JsonObject comp = new JsonObject();
            comp.add("minecraft:type_family", J.obj("family", J.arr(ce.kind, "mob")));
            comp.add("minecraft:health", J.obj("value", (int) Math.max(1, ce.health), "max", (int) Math.max(1, ce.health)));
            comp.add("minecraft:movement", J.obj("value", Math.max(0.05, ce.speed)));
            if (ce.attackDamage > 0) comp.add("minecraft:attack", J.obj("damage", ce.attackDamage));
            comp.add("minecraft:collision_box", J.obj("width", Math.max(0.2, ce.width), "height", Math.max(0.2, ce.height)));
            if (Math.abs(ce.scale - 1.0) > 0.01) comp.add("minecraft:scale", J.obj("value", Math.max(0.2, Math.min(4.0, ce.scale))));
            comp.add("minecraft:physics", new JsonObject());
            comp.add("minecraft:pushable", J.obj("is_pushable", ce.pushable, "is_pushable_by_piston", ce.pushable));
            if (ce.fireImmune) comp.add("minecraft:fire_immune", new JsonObject());
            if (ce.burnsInDaylight) comp.add("minecraft:burns_in_daylight", new JsonObject());
            if (ce.canFly) {
                comp.add("minecraft:can_fly", new JsonObject());
                comp.add("minecraft:navigation.fly", J.obj("can_path_over_water", true, "can_path_from_air", true));
                comp.add("minecraft:movement.fly", new JsonObject());
            }
            if (ce.tameable) {
                String tItem = ce.tameItem == null || ce.tameItem.isBlank() ? "minecraft:bone" : Ids.full(ce.tameItem);
                comp.add("minecraft:tameable", J.obj("probability", 0.33, "tame_items", J.arr(Ids.isTag(tItem) ? tItem.substring(1) : tItem)));
                comp.add("minecraft:behavior.follow_owner", J.obj("priority", 4, "speed_multiplier", 1.15, "start_distance", 10, "stop_distance", 2));
            }
            if (!ce.lootTable.isBlank()) comp.add("minecraft:loot", J.obj("table", "loot_tables/" + Ids.path(Ids.full(ce.lootTable)) + ".json"));
            if (ce.xpReward >= 0) comp.add("minecraft:experience_reward", J.obj("on_death", ce.xpReward));
            if (ce.aiGoals.contains("float") || ce.canSwim) comp.add("minecraft:behavior.float", J.obj("priority", 0));
            if (ce.aiGoals.contains("melee")) comp.add("minecraft:behavior.melee_attack", J.obj("priority", 2, "speed_multiplier", 1.2));
            if (ce.aiGoals.contains("panic")) comp.add("minecraft:behavior.panic", J.obj("priority", 1, "speed_multiplier", 1.4));
            if (ce.canFly) {
                comp.add("minecraft:behavior.random_fly", J.obj("priority", 5, "xz_dist", 10, "y_dist", 7, "speed_multiplier", 1.0));
            } else if (ce.aiGoals.contains("stroll")) {
                comp.add("minecraft:behavior.random_stroll", J.obj("priority", 5, "speed_multiplier", 1.0));
            }
            if (ce.aiGoals.contains("look_player")) comp.add("minecraft:behavior.look_at_player", J.obj("priority", 6, "look_distance", 8.0));
            if (ce.aiGoals.contains("attack_nearest_player")) {
                comp.add("minecraft:behavior.nearest_attackable_target", J.obj("priority", 1,
                        "entity_types", J.arr(J.obj("filters", J.obj("test", "is_family", "subject", "other", "value", "player"), "max_dist", ce.followRange))));
            }
            ctx.files.json(bp + "entities/" + ce.name + ".json", J.obj("format_version", "1.21.0", "minecraft:entity", J.obj(
                    "description", J.obj("identifier", eid, "is_spawnable", ce.spawnEgg, "is_summonable", true, "is_experimental", false),
                    "components", comp)));

            Model mdl = ModelGen.entityModelSpec(ctx.p, ce);
            TextureAsset tex = ctx.p.texture(ce.texture);
            if (tex == null && mdl != null) tex = ModelGen.firstModelTexture(ctx.p, mdl);
            String texPath = "textures/entity/zombie/zombie";
            if (tex != null) {
                texPath = "textures/entity/" + ctx.ns + "/" + ce.name;
                ctx.files.bytes(rp + texPath + ".png", tex.bytes());
            }
            String geo = "geometry.humanoid.custom";
            if (mdl != null) {
                geo = "geometry." + ctx.ns + "." + ce.name;
                ctx.files.json(rp + "models/entity/" + ce.name + ".geo.json",
                        Models.toBedrock(mdl, geo, Math.max(16, mdl.textureWidth), Math.max(16, mdl.textureHeight)));
            }
            JsonObject rDesc = J.obj("identifier", eid,
                    "materials", J.obj("default", "entity_alphatest"),
                    "textures", J.obj("default", texPath),
                    "geometry", J.obj("default", geo),
                    "render_controllers", J.arr("controller.render.default"));
            if (ce.spawnEgg) rDesc.add("spawn_egg", J.obj("base_color", ce.eggPrimary, "overlay_color", ce.eggSecondary));
            ctx.files.json(rp + "entity/" + ce.name + ".entity.json", J.obj("format_version", "1.10.0", "minecraft:client_entity", J.obj("description", rDesc)));

            if (ce.naturalSpawn) {
                ctx.files.json(bp + "spawn_rules/" + ce.name + ".json", J.obj("format_version", "1.8.0", "minecraft:spawn_rules", J.obj(
                        "description", J.obj("identifier", eid, "population_control", "monster".equals(ce.category) ? "monster" : "animal"),
                        "conditions", J.arr(J.obj("minecraft:spawns_on_surface", new JsonObject(),
                                "minecraft:weight", J.obj("default", Math.max(1, ce.spawnWeight)),
                                "minecraft:herd", J.obj("min_size", Math.max(1, ce.spawnMin), "max_size", Math.max(ce.spawnMin, ce.spawnMax)))))));
            }
        }
    }

    private JsonObject ingredient(String id) {
        String f = Ids.full(id);
        if (Ids.isTag(f)) return J.obj("tag", f.substring(1));
        return J.obj("item", f);
    }

    private void recipes() {
        for (Recipe r : ctx.all(Recipe.class)) {
            if (r.result.isBlank()) continue;
            JsonObject desc = J.obj("identifier", ctx.id(r.name));
            JsonObject body;
            String type;
            JsonArray unlock = J.arr(J.obj("context", "AlwaysUnlocked"));
            switch (r.recipeType) {
                case "shaped" -> {
                    DataGen.Shape sh = DataGen.Shape.of(r.grid);
                    if (sh.rows.isEmpty()) continue;
                    JsonObject key = new JsonObject();
                    for (var e : sh.keys.entrySet()) key.add(String.valueOf(e.getValue()), ingredient(e.getKey()));
                    type = "minecraft:recipe_shaped";
                    body = J.obj("description", desc, "tags", J.arr("crafting_table"), "pattern", J.list(sh.rows), "key", key,
                            "unlock", unlock, "result", J.obj("item", Ids.full(r.result), "count", r.count));
                }
                case "shapeless", "stonecutting" -> {
                    JsonArray ing = new JsonArray();
                    if (r.recipeType.equals("stonecutting")) {
                        if (r.input.isBlank()) continue;
                        ing.add(ingredient(r.input));
                    } else for (String g : r.grid) if (!g.isBlank()) ing.add(ingredient(g));
                    if (ing.isEmpty()) continue;
                    type = "minecraft:recipe_shapeless";
                    body = J.obj("description", desc, "tags", J.arr(r.recipeType.equals("stonecutting") ? "stonecutter" : "crafting_table"),
                            "ingredients", ing, "unlock", unlock, "result", J.obj("item", Ids.full(r.result), "count", r.count));
                }
                case "smelting", "blasting", "smoking", "campfire" -> {
                    if (r.input.isBlank()) continue;
                    JsonArray tags = switch (r.recipeType) {
                        case "blasting" -> J.arr("blast_furnace");
                        case "smoking" -> J.arr("smoker", "furnace");
                        case "campfire" -> J.arr("campfire", "soul_campfire");
                        default -> J.arr("furnace");
                    };
                    type = "minecraft:recipe_furnace";
                    body = J.obj("description", desc, "tags", tags, "input", Ids.full(r.input), "output", Ids.full(r.result));
                }
                case "smithing" -> {
                    if (r.template.isBlank() || r.base.isBlank() || r.addition.isBlank()) continue;
                    type = "minecraft:recipe_smithing_transform";
                    body = J.obj("description", desc, "tags", J.arr("smithing_table"), "template", Ids.full(r.template),
                            "base", Ids.full(r.base), "addition", Ids.full(r.addition), "result", Ids.full(r.result));
                }
                case "brewing" -> {
                    String in = bedrockPotion(r.input), out = bedrockPotion(r.result);
                    if (in == null || out == null || r.addition.isBlank() || Ids.isTag(r.addition.trim())) {
                        ctx.note("brewing recipe " + r.name + " skipped: Bedrock Edition only brews vanilla potions");
                        continue;
                    }
                    type = "minecraft:recipe_brewing_mix";
                    body = J.obj("description", desc, "tags", J.arr("brewing_stand"), "input", in, "reagent", Ids.full(r.addition.trim()), "output", out);
                }
                default -> {
                    continue;
                }
            }
            ctx.files.json(bp + "recipes/" + r.name + ".json", J.obj("format_version", "1.20.10", type, body));
        }
    }

    /** Bedrock potion type id for a vanilla Java potion, or null (project potions and luck do not exist there). */
    private static String bedrockPotion(String id) {
        String f = Ids.full(id.trim());
        if (!f.startsWith("minecraft:") || Ids.path(f).equals("luck")) return null;
        String path = Ids.path(f).replace("night_vision", "nightvision");
        return "minecraft:potion_type:" + path;
    }

    private void lootTables() {
        for (LootTable lt : ctx.all(LootTable.class)) {
            JsonArray pools = new JsonArray();
            for (LootPool pool : lt.pools) {
                JsonArray entries = new JsonArray();
                for (LootEntry en : pool.entries) {
                    switch (en.kind) {
                        case "empty" -> entries.add(J.obj("type", "empty", "weight", en.weight));
                        case "item" -> {
                            if (en.name.isBlank()) continue;
                            JsonObject e = J.obj("type", "item", "name", Ids.full(en.name), "weight", en.weight);
                            JsonArray fns = new JsonArray();
                            if (en.countMin != 1 || en.countMax != 1) fns.add(J.obj("function", "set_count", "count", J.obj("min", en.countMin, "max", Math.max(en.countMin, en.countMax))));
                            if (en.enchant) fns.add(J.obj("function", "enchant_randomly"));
                            if (!fns.isEmpty()) e.add("functions", fns);
                            entries.add(e);
                        }
                        default -> {
                        }
                    }
                }
                JsonObject po = J.obj("rolls", pool.rollsMin == pool.rollsMax ? J.el(pool.rollsMin) : J.obj("min", pool.rollsMin, "max", pool.rollsMax), "entries", entries);
                JsonArray conds = new JsonArray();
                if (pool.chance < 1) conds.add(J.obj("condition", "random_chance", "chance", pool.chance));
                if (pool.killedByPlayer) conds.add(J.obj("condition", "killed_by_player"));
                if (!conds.isEmpty()) po.add("conditions", conds);
                pools.add(po);
            }
            String path = lt.replaces.isBlank() ? ctx.ns + "/" + lt.name
                    : lt.inject ? ctx.ns + "/inject/" + lt.name : Ids.path(Ids.full(lt.replaces));
            ctx.files.json(bp + "loot_tables/" + path + ".json", J.obj("pools", pools));
        }
    }

    private void functions() {
        List<String> ticks = new ArrayList<>();
        for (Function f : ctx.all(Function.class)) {
            StringBuilder sb = new StringBuilder();
            for (String line : f.code.split("\\R")) {
                String l = line.strip();
                if (l.startsWith("/")) l = l.substring(1);
                sb.append(l.replace(ctx.ns + ":", ctx.ns + "/")).append('\n');
            }
            ctx.files.text(bp + "functions/" + ctx.ns + "/" + f.name + ".mcfunction", sb.toString());
            if (f.onTick) ticks.add(ctx.ns + "/" + f.name);
        }
        if (!ticks.isEmpty()) ctx.files.json(bp + "functions/tick.json", J.obj("values", J.list(ticks)));
        if (!ctx.all(Function.class).isEmpty()) ctx.note("function syntax differs between Java and Bedrock Edition; check commands in Bedrock");
    }

    private void sounds() {
        JsonObject defs = new JsonObject();
        for (Sound s : ctx.all(Sound.class)) {
            JsonArray list = new JsonArray();
            int i = 0;
            for (SoundFile f : s.files) {
                if (f.data == null || f.data.isEmpty()) continue;
                String name = s.files.size() == 1 ? s.name : s.name + "_" + (++i);
                ctx.files.bytes(rp + "sounds/" + ctx.ns + "/" + name + ".ogg", Images.decodeBase64(f.data));
                list.add("sounds/" + ctx.ns + "/" + name);
            }
            if (list.isEmpty()) continue;
            defs.add(ctx.ns + "." + s.name, J.obj("category", s.category, "sounds", list));
        }
        if (!defs.isEmpty()) ctx.files.json(rp + "sounds/sound_definitions.json", J.obj("format_version", "1.14.0", "sound_definitions", defs));
    }

    private static String bedrockLang(String java) {
        String[] p = java.split("_");
        return p.length == 2 ? p[0] + "_" + p[1].toUpperCase() : java;
    }

    private void lang() {
        List<String> langs = new ArrayList<>();
        Map<String, Map<String, String>> files = Localization.files(ctx.p);
        for (String lang : Localization.languages(ctx.p)) {
            StringBuilder sb = new StringBuilder();
            sb.append("pack.name=").append(ctx.p.meta.name).append('\n');
            sb.append("pack.description=").append(ctx.p.meta.description.replace("\n", " ")).append('\n');
            for (Item it : ctx.all(Item.class)) {
                String v = lang.equals("en_us") ? (it.displayName.isBlank() ? Ids.titleCase(it.name) : it.displayName) : it.loc("name", lang);
                if (!v.isBlank()) sb.append("item.").append(ctx.id(it.name)).append("=").append(v).append('\n');
            }
            for (Block b : ctx.all(Block.class)) {
                String v = lang.equals("en_us") ? (b.displayName.isBlank() ? Ids.titleCase(b.name) : b.displayName) : b.loc("name", lang);
                if (!v.isBlank()) sb.append("tile.").append(ctx.id(b.name)).append(".name=").append(v).append('\n');
            }
            Map<String, String> entries = files.getOrDefault(lang, Map.of());
            for (var en : entries.entrySet()) {
                String k = en.getKey(), v = en.getValue();
                if (k.startsWith("item.") || k.startsWith("block.") || v == null || v.isBlank()) continue;
                sb.append(k).append('=').append(v.replace("\n", "\\n")).append('\n');
            }
            String code = bedrockLang(lang);
            langs.add(code);
            ctx.files.text(rp + "texts/" + code + ".lang", sb.toString());
        }
        ctx.files.json(rp + "texts/languages.json", J.list(langs));
    }

    // ---------------------------------------------------------------- Script API

    private String js(String s) {
        return J.quote(s);
    }

    private String bedrockId(String id) {
        String f = Ids.full(id);
        return switch (f) {
            case "minecraft:the_nether" -> "minecraft:nether";
            default -> f;
        };
    }

    private static String code(String color) {
        return switch (color) {
            case "black" -> "§0";
            case "dark_blue" -> "§1";
            case "dark_green" -> "§2";
            case "dark_aqua" -> "§3";
            case "dark_red" -> "§4";
            case "dark_purple" -> "§5";
            case "gold" -> "§6";
            case "gray" -> "§7";
            case "dark_gray" -> "§8";
            case "blue" -> "§9";
            case "green" -> "§a";
            case "aqua" -> "§b";
            case "red" -> "§c";
            case "light_purple" -> "§d";
            case "yellow" -> "§e";
            default -> "";
        };
    }

    private static String sel(Step s) {
        String t = s.get("target").trim();
        return t.isEmpty() ? "@s" : t;
    }

    private String rawtext(String text, String color, boolean bold) {
        return J.compact(J.obj("rawtext", J.arr(J.obj("text", code(color) + (bold ? "§l" : "") + GenContext.legacy(text)))));
    }

    private List<String> commands(Step s) {
        List<String> r = new ArrayList<>();
        String bedrock = LogicExtra.bedrockCommand(s);
        if (bedrock != null) {
            if (!bedrock.isEmpty()) r.add(bedrock);
            return r;
        }
        switch (s.kind) {
            case "command" -> {
                for (String line : s.get("command").split("\\R")) {
                    String c = line.trim();
                    if (c.startsWith("/")) c = c.substring(1);
                    if (!c.isEmpty()) r.add(c);
                }
            }
            case "message" -> r.add("tellraw " + sel(s) + " " + rawtext(s.get("text"), s.get("color"), Boolean.parseBoolean(s.get("bold"))));
            case "title" -> {
                if (!s.get("subtitle").isBlank()) r.add("titleraw " + sel(s) + " subtitle " + rawtext(s.get("subtitle"), "gray", false));
                r.add("titleraw " + sel(s) + " title " + rawtext(s.get("title"), s.get("color"), false));
            }
            case "actionbar" -> r.add("titleraw " + sel(s) + " actionbar " + rawtext(s.get("text"), s.get("color"), false));
            case "message_link" -> r.add("tellraw " + sel(s) + " " + rawtext(s.get("text") + ": " + s.get("url"), s.get("color"), false));
            case "message_command", "message_suggest", "message_hover", "message_copy" -> {
                String extra = s.kind.equals("message_hover") ? s.get("hover") : s.kind.equals("message_copy") ? s.get("copy") : "";
                r.add("tellraw " + sel(s) + " " + rawtext(s.get("text") + (extra.isBlank() ? "" : " (" + extra + ")"), s.get("color"), false));
            }
            case "message_name" -> r.add("tellraw " + sel(s) + " " + J.compact(J.obj("rawtext", J.arr(J.obj("selector", "@s"),
                    J.obj("text", code(s.get("color")) + GenContext.legacy(s.get("text")))))));
            case "message_score", "actionbar_score" -> {
                String o = s.get("objective").trim();
                if (o.isEmpty()) break;
                String json = J.compact(J.obj("rawtext", J.arr(J.obj("text", code(s.get("color")) + GenContext.legacy(s.get("text"))),
                        J.obj("score", J.obj("name", "*", "objective", o)))));
                r.add(s.kind.equals("message_score") ? "tellraw " + sel(s) + " " + json : "titleraw " + sel(s) + " actionbar " + json);
            }
            case "give" -> r.add("give " + sel(s) + " " + Ids.full(s.get("item")) + " " + s.get("count"));
            case "clear" -> r.add("clear " + sel(s) + " " + Ids.full(s.get("item")) + " 0 " + s.get("count"));
            case "effect" -> r.add("effect " + sel(s) + " " + Ids.path(s.get("effect")) + " " + s.get("seconds") + " " + s.get("amplifier") + " " + s.get("hide"));
            case "teleport" -> r.add("tp " + sel(s) + " " + s.get("x") + " " + s.get("y") + " " + s.get("z"));
            case "damage" -> r.add("damage " + sel(s) + " " + (int) Math.ceil(parse(s.get("amount"), 1)));
            case "xp" -> r.add("xp " + s.get("amount") + (s.get("unit").equals("points") ? "" : "L") + " " + sel(s));
            case "kill" -> r.add("kill " + sel(s));
            case "tag" -> r.add("tag " + sel(s) + " " + (s.get("op").equals("remove") ? "remove" : "add") + " " + s.get("tag"));
            case "sound" -> r.add("playsound " + Ids.path(s.get("sound")) + " " + sel(s) + " ~ ~ ~ " + s.get("volume") + " " + s.get("pitch"));
            case "particle" -> r.add("particle " + Ids.full(s.get("particle")) + " ~ ~1 ~");
            case "summon" -> r.add("summon " + Ids.full(s.get("entity")) + " " + s.get("x") + " " + s.get("y") + " " + s.get("z"));
            case "setblock" -> r.add("setblock " + s.get("x") + " " + s.get("y") + " " + s.get("z") + " " + Ids.full(s.get("block")));
            case "time" -> r.add("time set " + s.get("value"));
            case "weather" -> r.add("weather " + s.get("weather"));
            case "score" -> r.add("scoreboard players " + s.get("op") + " " + sel(s) + " " + s.get("objective") + " " + s.get("value"));
            case "function" -> {
                String f = s.get("function").trim();
                if (!f.isEmpty()) r.add("function " + (f.contains(":") ? f.replace(':', '/') : ctx.ns + "/" + f));
            }
            case "broadcast" -> r.add("tellraw @a " + rawtext(s.get("text"), s.get("color"), Boolean.parseBoolean(s.get("bold"))));
            case "stop_sound" -> r.add("stopsound " + sel(s));
            case "replace_item" -> r.add("replaceitem entity " + sel(s) + " slot." + (s.get("slot").isBlank() ? "weapon.mainhand" : s.get("slot"))
                    + " 0 " + Ids.full(s.get("item")) + " " + s.get("count"));
            case "enchant" -> r.add("enchant " + sel(s) + " " + Ids.path(s.get("enchantment")) + " " + s.get("level"));
            case "heal" -> r.add("effect " + sel(s) + " instant_health 1 " + Math.max(0, (int) parse(s.get("amount"), 1) - 1) + " true");
            case "feed" -> r.add("effect " + sel(s) + " saturation 1 " + Math.max(0, (int) parse(s.get("amount"), 1) - 1) + " true");
            case "clear_effects" -> r.add("effect " + sel(s) + " clear");
            case "gamemode" -> r.add("gamemode " + s.get("mode") + " " + sel(s));
            case "spawnpoint" -> r.add("spawnpoint " + sel(s) + " " + s.get("x") + " " + s.get("y") + " " + s.get("z"));
            case "ride" -> r.add("ride " + sel(s) + " start_riding " + s.get("vehicle"));
            case "dismount" -> r.add("ride " + sel(s) + " stop_riding");
            case "fill" -> r.add("fill " + s.get("x1") + " " + s.get("y1") + " " + s.get("z1") + " " + s.get("x2") + " " + s.get("y2") + " " + s.get("z2")
                    + " " + Ids.full(s.get("block")) + (s.get("mode").isBlank() || s.get("mode").equals("replace") ? "" : " " + s.get("mode")));
            case "explosion" -> r.add("summon creeper " + s.get("x") + " " + s.get("y") + " " + s.get("z") + " minecraft:start_exploding_forced");
            case "lightning" -> r.add("summon lightning_bolt " + s.get("x") + " " + s.get("y") + " " + s.get("z"));
            case "place" -> {
                if (!s.get("kind").equals("structure")) r.add("placefeature " + Ids.full(s.get("id")) + " " + s.get("x") + " " + s.get("y") + " " + s.get("z"));
            }
            case "gamerule" -> r.add("gamerule " + s.get("rule").trim().toLowerCase(Locale.ROOT) + " " + s.get("value"));
            case "difficulty" -> r.add("difficulty " + s.get("value"));
            case "scoreboard_show" -> r.add("scoreboard objectives setdisplay " + (s.get("slot").equals("below_name") ? "belowname" : s.get("slot")) + " " + s.get("objective"));
            case "shoot" -> r.add("summon " + Ids.full(s.get("entity").isBlank() ? "minecraft:arrow" : s.get("entity")).replace("minecraft:", "") + " ^ ^1.5 ^1.5");
            case "random_score" -> r.add("scoreboard players random " + sel(s) + " " + s.get("objective") + " " + s.get("min") + " " + s.get("max"));
            default -> {
            }
        }
        return r;
    }

    private static double parse(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private String condition(Condition c) {
        String r = switch (c.kind) {
            case "chance" -> "chance(" + parse(c.get("percent"), 50) + ")";
            case "sneaking" -> "self.isSneaking === true";
            case "holding" -> "holding(self, " + js(Ids.full(c.get("item"))) + ")";
            case "dimension" -> "(self.dimension && self.dimension.id === " + js(bedrockId(c.get("dimension"))) + ")";
            case "has_tag" -> "(typeof self.hasTag === 'function' && self.hasTag(" + js(c.get("tag").trim()) + "))";
            case "score" -> "score(self, " + js(c.get("objective").trim()) + ", " + numOrNull(c.get("min")) + ", " + numOrNull(c.get("max")) + ")";
            case "block" -> "test(self, " + js("testforblock " + c.get("x") + " " + c.get("y") + " " + c.get("z") + " " + Ids.full(c.get("block"))) + ")";
            case "daytime" -> "(world.getTimeOfDay() % 24000) < 12000";
            case "raw" -> "test(self, " + js("execute " + c.get("execute").trim().replaceFirst("^execute ", "") + " run testfor @s") + ")";
            case "entity_nearby" -> "test(self, " + js("testfor @e[" + (c.get("entity").isBlank() ? "" : "type=" + Ids.full(c.get("entity")) + ",") + "r=" + c.get("radius") + "]") + ")";
            case "level" -> "test(self, " + js("testfor @s[lm=" + c.get("min") + "]") + ")";
            case "gamemode" -> "test(self, " + js("testfor @s[m=" + c.get("mode") + "]") + ")";
            case "state" -> switch (c.get("state")) {
                case "on_fire" -> "(typeof self.getComponent === 'function' && self.getComponent(\"minecraft:onfire\") !== undefined)";
                case "swimming" -> "self.isSwimming === true";
                case "baby" -> "false";
                default -> "self.isSprinting === true";
            };
            case "raining" -> "(typeof server().getWeather === 'function' && server().getWeather() !== \"Clear\")";
            case "thundering" -> "(typeof server().getWeather === 'function' && server().getWeather() === \"Thunder\")";
            case "in_water" -> "self.isInWater === true";
            case "has_item" -> "test(self, " + js("testfor @s[hasitem={item=" + Ids.full(c.get("item")) + "}]") + ")";
            case "y_level" -> "(self.location !== undefined && self.location.y >= " + parse(c.get("min"), -64) + " && self.location.y <= " + parse(c.get("max"), 320) + ")";
            case "entity_type" -> "self.typeId === " + js(Ids.full(c.get("entity")));
            case "platform" -> ProcCompiler.platformMatches(modstudio.core.Target.BEDROCK, c.get("platform")) ? "true" : "false";
            case "mc_version" -> "true";
            default -> "false";
        };
        return c.negate ? "!(" + r + ")" : r;
    }

    private static String numOrNull(String s) {
        try {
            return String.valueOf(Integer.parseInt(s.trim()));
        } catch (Exception e) {
            return "null";
        }
    }

    private void statements(List<Step> steps, StringBuilder out, String indent, Set<String> objectives) {
        for (Step s : steps) {
            switch (s.kind) {
                case "if" -> {
                    if (s.cond == null) continue;
                    if (s.cond.kind.equals("score")) objectives.add(s.cond.get("objective").trim());
                    out.append(indent).append("if (").append(condition(s.cond)).append(") {\n");
                    statements(s.then, out, indent + "  ", objectives);
                    if (!s.orElse.isEmpty()) {
                        out.append(indent).append("} else {\n");
                        statements(s.orElse, out, indent + "  ", objectives);
                    }
                    out.append(indent).append("}\n");
                }
                case "schedule" -> {
                    int t = (int) Math.max(1, parse(s.get("ticks"), 20));
                    out.append(indent).append("system.runTimeout(() => {\n");
                    statements(s.then, out, indent + "  ", objectives);
                    out.append(indent).append("}, ").append(t).append(");\n");
                }
                case "for_each" -> {
                    String sel = s.get("selector").isBlank() ? "@a" : s.get("selector").trim();
                    out.append(indent).append("cmd(self, ").append(js("tag " + sel + " add ms_each")).append(");\n");
                    out.append(indent).append("for (const each of eachTagged()) {\n");
                    out.append(indent).append("  ((self) => {\n");
                    statements(s.then, out, indent + "    ", objectives);
                    out.append(indent).append("  })(each);\n");
                    out.append(indent).append("}\n");
                }
                case "at_position" -> statements(s.then, out, indent, objectives);
                case "as_entities", "at_entities" -> {
                    String sel = s.get("selector").isBlank() ? "@e" : s.get("selector").trim();
                    out.append(indent).append("cmd(self, ").append(js("tag " + sel + " add ms_each")).append(");\n");
                    out.append(indent).append("for (const each of eachTagged()) {\n");
                    out.append(indent).append("  ((self) => {\n");
                    statements(s.then, out, indent + "    ", objectives);
                    out.append(indent).append("  })(each);\n");
                    out.append(indent).append("}\n");
                }
                case "in_dimension", "facing_entity", "on_relation", "summon_run", "positioned_over", "rotated" -> {
                    // execute contexts that the script API has no direct counterpart for: the body runs as is
                    statements(s.then, out, indent, objectives);
                }
                case "cutscene_play" -> {
                    String sel = s.get("target").isBlank() ? "@s" : s.get("target").trim();
                    String name = js(s.get("cutscene").trim());
                    if (sel.equals("@s")) {
                        out.append(indent).append("if (cutscenes[").append(name).append("]) cutscenes[").append(name).append("](self);\n");
                    } else {
                        out.append(indent).append("cmd(self, ").append(js("tag " + sel + " add ms_each")).append(");\n");
                        out.append(indent).append("for (const each of eachTagged()) if (cutscenes[").append(name).append("]) cutscenes[").append(name).append("](each);\n");
                    }
                }
                case "play_animation", "stop_animation" -> {
                    // animated mobs are made of Java display entities
                }
                case "cutscene_stop" -> {
                    String sel = s.get("target").isBlank() ? "@s" : s.get("target").trim();
                    if (sel.equals("@s")) out.append(indent).append("stopCutscene(self, false);\n");
                    else {
                        out.append(indent).append("cmd(self, ").append(js("tag " + sel + " add ms_each")).append(");\n");
                        out.append(indent).append("for (const each of eachTagged()) stopCutscene(each, false);\n");
                    }
                }
                case "show_dialog" -> {
                    String dlg = js(s.get("dialog").trim());
                    String sel = s.get("target").isBlank() ? "@s" : s.get("target").trim();
                    if (sel.equals("@s")) out.append(indent).append("if (dialogs[").append(dlg).append("]) dialogs[").append(dlg).append("](self);\n");
                    else {
                        out.append(indent).append("cmd(self, ").append(js("tag " + sel + " add ms_each")).append(");\n");
                        out.append(indent).append("for (const each of eachTagged()) if (dialogs[").append(dlg).append("]) dialogs[").append(dlg).append("](each);\n");
                    }
                }
                case "stop" -> out.append(indent).append("return;\n");
                case "repeat" -> {
                    int n = (int) Math.max(1, Math.min(1000, parse(s.get("times"), 1)));
                    out.append(indent).append("for (let i = 0; i < ").append(n).append("; i++) {\n");
                    statements(s.then, out, indent + "  ", objectives);
                    out.append(indent).append("}\n");
                }
                default -> {
                    if (s.kind.equals("score") && !s.get("objective").isBlank()) objectives.add(s.get("objective").trim());
                    for (String c : commands(s)) out.append(indent).append("cmd(self, ").append(js(c)).append(");\n");
                }
            }
        }
    }

    private void script(List<ScriptHook> extraModules) {
        boolean hasUi = !ctx.all(Systems.Dialog.class).isEmpty();
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated by ModStudio.\n");
        sb.append("import { world, system } from \"@minecraft/server\";\n");
        if (hasUi) sb.append("import { ActionFormData, MessageFormData } from \"@minecraft/server-ui\";\n");
        List<String> hookCalls = new ArrayList<>();
        for (int i = 0; i < extraModules.size(); i++) {
            ScriptHook m = extraModules.get(i);
            String hk = m.hook() == null ? "" : m.hook().trim();
            String fn = m.method() == null ? "" : m.method().trim();
            if (("common_init".equals(hk) || "server_tick".equals(hk)) && fn.matches("[A-Za-z_$][\\w$]*")) {
                String alias = "_modHook" + i;
                sb.append("import * as ").append(alias).append(" from ").append(js(m.modulePath())).append(";\n");
                if ("common_init".equals(hk)) {
                    hookCalls.add("if (typeof " + alias + "." + fn + " === \"function\") " + alias + "." + fn + "();");
                } else {
                    hookCalls.add("if (typeof " + alias + "." + fn + " === \"function\") system.runInterval(() => " + alias + "." + fn + "(), 20);");
                }
            } else {
                sb.append("import ").append(js(m.modulePath())).append(";\n");
            }
        }
        sb.append('\n');
        sb.append("""
                function cmd(source, command) {
                  try {
                    source.runCommand(command);
                  } catch (e) {
                  }
                }

                function test(source, command) {
                  try {
                    return source.runCommand(command).successCount > 0;
                  } catch (e) {
                    return false;
                  }
                }

                function chance(percent) {
                  return Math.random() * 100 < percent;
                }

                function holding(entity, id) {
                  try {
                    const equipment = entity.getComponent("minecraft:equippable");
                    const item = equipment ? equipment.getEquipment("Mainhand") : undefined;
                    return item !== undefined && item.typeId === id;
                  } catch (e) {
                    return false;
                  }
                }

                function score(entity, objective, min, max) {
                  try {
                    const o = world.scoreboard.getObjective(objective);
                    const v = o ? o.getScore(entity) : undefined;
                    if (v === undefined) return false;
                    return (min === null || v >= min) && (max === null || v <= max);
                  } catch (e) {
                    return false;
                  }
                }

                function server() {
                  return world.getDimension("overworld");
                }

                function eachTagged() {
                  const found = [];
                  for (const id of ["overworld", "nether", "the_end"]) {
                    try {
                      for (const e of world.getDimension(id).getEntities({ tags: ["ms_each"] })) {
                        e.removeTag("ms_each");
                        found.push(e);
                      }
                    } catch (e) {
                    }
                  }
                  return found;
                }

                """);
        Set<String> objectives = new LinkedHashSet<>();
        sb.append(CutsceneGen.bedrockScript(ctx));
        sb.append("const cutsceneEnds = {\n");
        for (Systems.Cutscene c : ctx.all(Systems.Cutscene.class)) {
            if (c.onEnd.isEmpty()) continue;
            sb.append("  ").append(js(c.name)).append(": (self) => {\n");
            statements(c.onEnd, sb, "    ", objectives);
            sb.append("  },\n");
        }
        sb.append("};\n\n");
        sb.append("const procedures = {\n");
        for (Procedure p : ctx.all(Procedure.class)) {
            sb.append("  ").append(js(p.name)).append(": (self) => {\n");
            statements(p.steps, sb, "    ", objectives);
            sb.append("  },\n");
        }
        sb.append("};\n\n");
        sb.append("const keybinds = {\n");
        for (Systems.KeyBind k : ctx.all(Systems.KeyBind.class)) {
            if (k.fallback.equals("none")) continue;
            sb.append("  ").append(js(k.name)).append(": (self) => {\n");
            statements(k.steps, sb, "    ", objectives);
            sb.append("  },\n");
        }
        sb.append("};\n\n");
        sb.append("const dialogs = {\n");
        for (Systems.Dialog d : ctx.all(Systems.Dialog.class)) {
            String title = d.title.isBlank() ? Ids.titleCase(d.name) : d.title;
            List<Systems.DialogButton> btns = d.effectiveButtons();
            sb.append("  ").append(js(d.name)).append(": (self) => {\n");
            sb.append("    if (!self || self.typeId !== \"minecraft:player\") return;\n");
            if (d.kind.equals("confirmation") && btns.size() >= 2) {
                sb.append("    const form = new MessageFormData().title(").append(js(title)).append(").body(").append(js(d.body)).append(")\n");
                sb.append("      .button1(").append(js(btns.get(0).label.isBlank() ? "Yes" : btns.get(0).label)).append(")\n");
                sb.append("      .button2(").append(js(btns.get(1).label.isBlank() ? "No" : btns.get(1).label)).append(");\n");
            } else {
                sb.append("    const form = new ActionFormData().title(").append(js(title)).append(").body(").append(js(d.body)).append(");\n");
                for (Systems.DialogButton b : btns) {
                    sb.append("    form.button(").append(js(b.label.isBlank() ? "OK" : b.label)).append(");\n");
                }
            }
            sb.append("    form.show(self).then((res) => {\n");
            sb.append("      if (res.canceled || res.selection === undefined) return;\n");
            for (int i = 0; i < btns.size(); i++) {
                Systems.DialogButton b = btns.get(i);
                sb.append("      ").append(i == 0 ? "if" : "else if").append(" (res.selection === ").append(i).append(") {\n");
                switch (b.action) {
                    case "run" -> statements(b.steps, sb, "        ", objectives);
                    case "dialog" -> sb.append("        if (dialogs[").append(js(b.target.trim())).append("]) dialogs[").append(js(b.target.trim())).append("](self);\n");
                    case "url", "suggest", "copy" -> sb.append("        cmd(self, ").append(js("tellraw @s " + rawtext(b.target, "aqua", false))).append(");\n");
                    default -> {
                    }
                }
                sb.append("      }\n");
            }
            sb.append("    });\n");
            sb.append("  },\n");
        }
        sb.append("};\n\n");
        sb.append("const commands = {\n");
        for (Elements.Command c : ctx.all(Elements.Command.class)) {
            sb.append("  ").append(js(c.name)).append(": (self) => {\n");
            statements(c.steps, sb, "    ", objectives);
            sb.append("  },\n");
        }
        sb.append("};\n\n");

        Map<String, List<Procedure>> by = new LinkedHashMap<>();
        for (Procedure p : ctx.all(Procedure.class)) by.computeIfAbsent(p.trigger, k -> new ArrayList<>()).add(p);

        sb.append("world.afterEvents.worldInitialize.subscribe(() => {\n");
        for (String o : objectives) if (!o.isBlank()) sb.append("  cmd(server(), ").append(js("scoreboard objectives add " + o + " dummy")).append(");\n");
        for (Function f : ctx.all(Function.class)) if (f.onLoad) sb.append("  cmd(server(), ").append(js("function " + ctx.ns + "/" + f.name)).append(");\n");
        for (Procedure p : by.getOrDefault("load", List.of())) sb.append("  procedures[").append(js(p.name)).append("](server());\n");
        sb.append("});\n\n");

        List<Procedure> tick = by.getOrDefault("tick", List.of()), ptick = by.getOrDefault("player_tick", List.of());
        List<Systems.KeyBind> sneakKeys = ctx.all(Systems.KeyBind.class).stream().filter(k -> k.fallback.equals("sneak")).toList();
        List<Systems.KeyBind> jumpKeys = ctx.all(Systems.KeyBind.class).stream().filter(k -> k.fallback.equals("jump")).toList();
        if (!tick.isEmpty() || !ptick.isEmpty() || !sneakKeys.isEmpty() || !jumpKeys.isEmpty()) {
            if (!sneakKeys.isEmpty()) sb.append("const prevSneak = new Map();\n");
            if (!jumpKeys.isEmpty()) sb.append("const prevJump = new Map();\n");
            sb.append("system.runInterval(() => {\n");
            for (Procedure p : tick) sb.append("  procedures[").append(js(p.name)).append("](server());\n");
            if (!ptick.isEmpty() || !sneakKeys.isEmpty() || !jumpKeys.isEmpty()) {
                sb.append("  for (const player of world.getAllPlayers()) {\n");
                for (Procedure p : ptick) sb.append("    procedures[").append(js(p.name)).append("](player);\n");
                if (!sneakKeys.isEmpty()) {
                    sb.append("    const sk = player.isSneaking === true;\n");
                    sb.append("    if (sk && !prevSneak.get(player.id)) {\n");
                    for (Systems.KeyBind k : sneakKeys) sb.append("      keybinds[").append(js(k.name)).append("](player);\n");
                    sb.append("    }\n");
                    sb.append("    prevSneak.set(player.id, sk);\n");
                }
                if (!jumpKeys.isEmpty()) {
                    sb.append("    const jp = player.isJumping === true;\n");
                    sb.append("    if (jp && !prevJump.get(player.id)) {\n");
                    for (Systems.KeyBind k : jumpKeys) sb.append("      keybinds[").append(js(k.name)).append("](player);\n");
                    sb.append("    }\n");
                    sb.append("    prevJump.set(player.id, jp);\n");
                }
                sb.append("  }\n");
            }
            sb.append("}, 1);\n\n");
        }
        for (Procedure p : by.getOrDefault("interval", List.of())) {
            sb.append("system.runInterval(() => procedures[").append(js(p.name)).append("](server()), ").append(Math.max(1, p.interval)).append(");\n");
        }
        events(sb, by, "player_join", "world.afterEvents.playerSpawn", "ev", "ev.initialSpawn", "ev.player");
        if (by.containsKey("player_death") || by.containsKey("kill_entity")) {
            sb.append("world.afterEvents.entityDie.subscribe((ev) => {\n");
            sb.append("  const dead = ev.deadEntity;\n");
            for (Procedure p : by.getOrDefault("player_death", List.of())) {
                sb.append("  if (dead.typeId === \"minecraft:player\") procedures[").append(js(p.name)).append("](dead);\n");
            }
            if (by.containsKey("kill_entity")) {
                sb.append("  const killer = ev.damageSource ? ev.damageSource.damagingEntity : undefined;\n");
                sb.append("  if (killer && killer.typeId === \"minecraft:player\") {\n");
                for (Procedure p : by.get("kill_entity")) {
                    String cond = p.triggerValue.isBlank() ? "true" : "dead.typeId === " + js(Ids.full(p.triggerValue));
                    sb.append("    if (").append(cond).append(") procedures[").append(js(p.name)).append("](killer);\n");
                }
                sb.append("  }\n");
            }
            sb.append("});\n\n");
        }
        typed(sb, by, "block_break", "world.afterEvents.playerBreakBlock", "ev.brokenBlockPermutation.type.id", "ev.player");
        typed(sb, by, "block_place", "world.afterEvents.playerPlaceBlock", "ev.block.typeId", "ev.player");
        typed(sb, by, "item_right_click", "world.afterEvents.itemUse", "ev.itemStack.typeId", "ev.source");
        typed(sb, by, "item_eat", "world.afterEvents.itemCompleteUse", "ev.itemStack.typeId", "ev.source");
        if (!ctx.all(Elements.Command.class).isEmpty()) {
            sb.append("system.afterEvents.scriptEventReceive.subscribe((ev) => {\n");
            sb.append("  const prefix = ").append(js(ctx.ns + ":")).append(";\n");
            sb.append("  if (!ev.id.startsWith(prefix)) return;\n");
            sb.append("  const handler = commands[ev.id.substring(prefix.length)];\n");
            sb.append("  if (handler) handler(ev.sourceEntity ? ev.sourceEntity : server());\n");
            sb.append("});\n");
            ctx.note("commands run with /scriptevent " + ctx.ns + ":<name> in Bedrock Edition");
        }
        for (Procedure p : ctx.all(Procedure.class)) {
            if (!Logic.triggerSupport(p.trigger).contains(Target.BEDROCK)) ctx.note("procedure " + p.name + ": trigger " + p.trigger + " is not available in Bedrock Edition");
        }
        for (String call : hookCalls) sb.append(call).append('\n');
        ctx.files.text(bp + "scripts/main.js", sb.toString());
    }

    private void events(StringBuilder sb, Map<String, List<Procedure>> by, String trigger, String event, String ev, String cond, String self) {
        List<Procedure> list = by.get(trigger);
        if (list == null) return;
        sb.append(event).append(".subscribe((").append(ev).append(") => {\n");
        sb.append("  if (!(").append(cond).append(")) return;\n");
        for (Procedure p : list) sb.append("  procedures[").append(js(p.name)).append("](").append(self).append(");\n");
        sb.append("});\n\n");
    }

    private void typed(StringBuilder sb, Map<String, List<Procedure>> by, String trigger, String event, String typeExpr, String self) {
        List<Procedure> list = by.get(trigger);
        if (list == null) return;
        sb.append(event).append(".subscribe((ev) => {\n");
        sb.append("  const type = ").append(typeExpr).append(";\n");
        for (Procedure p : list) {
            String cond = p.triggerValue.isBlank() ? "true" : "type === " + js(Ids.full(p.triggerValue));
            sb.append("  if (").append(cond).append(") procedures[").append(js(p.name)).append("](").append(self).append(");\n");
        }
        sb.append("});\n\n");
    }
}
