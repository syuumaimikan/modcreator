package modstudio.gen;

import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.Step;
import modstudio.core.Systems.*;

import java.util.*;

/**
 * Data pack side of the game systems (mobs, bosses, particle effects, HUD, menus, web requests, machines, worldgen).
 * Everything here is plain functions, advancements, predicates and worldgen JSON, so it runs on every Java target;
 * mod loaders and Paper add native code for menus and web requests.
 */
public final class SystemsGen {
    private final GenContext ctx;
    private final ProcCompiler pc;
    private final String root;
    private final String ns;
    private final boolean realItems;
    private final List<String> load;
    private final List<String> tick;

    public SystemsGen(GenContext ctx, ProcCompiler pc, String root, boolean realItems, List<String> load, List<String> tick) {
        this.ctx = ctx;
        this.pc = pc;
        this.root = root;
        this.ns = ctx.ns;
        this.realItems = realItems;
        this.load = load;
        this.tick = tick;
    }

    public void generate() {
        for (Mob m : ctx.all(Mob.class)) mob(m);
        for (Effect e : ctx.all(Effect.class)) pc.functions.put("effects/" + e.name, effectLines(e));
        for (Structure s : ctx.all(Structure.class)) structure(s);
        for (Biome b : ctx.all(Biome.class)) biome(b);
        for (Dimension d : ctx.all(Dimension.class)) dimension(d);
    }

    // ------------------------------------------------------------------ structures

    static int dataVersion(McVersion v) {
        return switch (v) {
            case V1_20_1 -> 3465;
            case V1_20_4 -> 3700;
            case V1_20_6 -> 3839;
            case V1_21_1 -> 3955;
            case V1_21_4 -> 4189;
            case V1_21_8 -> 4440;
        };
    }

    /** Splits "minecraft:oak_stairs[facing=east]" into a palette entry with properties. */
    static Nbt.Compound blockState(String state) {
        String s = state.trim();
        String name = s, props = "";
        int b = s.indexOf('[');
        if (b >= 0 && s.endsWith("]")) {
            name = s.substring(0, b);
            props = s.substring(b + 1, s.length() - 1);
        }
        Nbt.Compound c = new Nbt.Compound().put("Name", Ids.full(name));
        if (!props.isBlank()) {
            Nbt.Compound p = new Nbt.Compound();
            for (String kv : props.split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) p.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
            c.put("Properties", p);
        }
        return c;
    }

    /** Structure template file (.nbt) for a volume. */
    public static byte[] structureNbt(Volume s, McVersion v, String chestLoot) {
        s.ensure();
        List<Nbt.Compound> palette = new ArrayList<>();
        Map<Integer, Integer> remap = new HashMap<>();
        List<Nbt.Compound> blocks = new ArrayList<>();
        for (int y = 0; y < s.sy; y++)
            for (int z = 0; z < s.sz; z++)
                for (int x = 0; x < s.sx; x++) {
                    int idx = s.get(x, y, z);
                    if (idx < 0 || idx >= s.palette.size()) continue;
                    Integer st = remap.get(idx);
                    if (st == null) {
                        st = palette.size();
                        remap.put(idx, st);
                        palette.add(blockState(s.palette.get(idx)));
                    }
                    Nbt.Compound bc = new Nbt.Compound().put("pos", Nbt.ints(x, y, z)).put("state", st);
                    String id = s.palette.get(idx);
                    if (chestLoot != null && !chestLoot.isBlank() && (id.contains("chest") || id.contains("barrel"))) {
                        bc.put("nbt", new Nbt.Compound().put("LootTable", Ids.full(chestLoot)));
                    }
                    blocks.add(bc);
                }
        Nbt.Compound root = new Nbt.Compound()
                .put("DataVersion", dataVersion(v))
                .put("size", Nbt.ints(s.sx, s.sy, s.sz))
                .put("palette", Nbt.compounds(palette))
                .put("blocks", Nbt.compounds(blocks))
                .put("entities", Nbt.compounds(List.of()));
        return Nbt.gzip(root);
    }

    /** A biome tag, a single biome or a comma separated list of biomes. */
    private com.google.gson.JsonElement biomeList(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return new com.google.gson.JsonPrimitive("#minecraft:is_overworld");
        if (!t.contains(",")) return new com.google.gson.JsonPrimitive(t.startsWith("#") ? t : biomeRef(t));
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (String b : t.split(",")) if (!b.isBlank() && !b.trim().startsWith("#")) a.add(biomeRef(b));
        return a;
    }

    private void structure(Structure s) {
        String dir = ctx.singular() ? "structure" : "structures";
        ctx.files.bytes(data(dir, s.name + ".nbt"), structureNbt(s, ctx.v, s.chestLoot));
        if (!s.generate) return;
        JsonObject st = J.obj("type", "minecraft:jigsaw",
                "biomes", biomeList(s.biomes),
                "step", s.step,
                "spawn_overrides", new JsonObject(),
                "terrain_adaptation", s.terrain,
                "start_pool", ns + ":" + s.name + "/start",
                "size", 1,
                "start_height", J.obj("absolute", s.yOffset),
                "project_start_to_heightmap", "WORLD_SURFACE_WG",
                "max_distance_from_center", 80,
                "use_expansion_hack", false);
        ctx.files.json(data("worldgen/structure", s.name + ".json"), st);
        JsonObject element = J.obj("element_type", "minecraft:single_pool_element", "location", ns + ":" + s.name,
                "projection", "rigid", "processors", "minecraft:empty");
        ctx.files.json(data("worldgen/template_pool", s.name + "/start.json"),
                J.obj("fallback", "minecraft:empty", "elements", J.arr(J.obj("weight", 1, "element", element))));
        int spacing = Math.max(2, s.spacing);
        int separation = Math.max(0, Math.min(spacing - 1, s.separation));
        ctx.files.json(data("worldgen/structure_set", s.name + ".json"), J.obj(
                "structures", J.arr(J.obj("structure", ns + ":" + s.name, "weight", 1)),
                "placement", J.obj("type", "minecraft:random_spread", "spacing", spacing, "separation", separation,
                        "salt", Math.abs((ns + ":" + s.name).hashCode() % 1_000_000_000))));
    }

    // ------------------------------------------------------------------ biomes

    private static com.google.gson.JsonObject featurePresets;

    static synchronized com.google.gson.JsonObject featurePresets() {
        if (featurePresets == null) {
            try (var in = SystemsGen.class.getResourceAsStream("/modstudio/worldgen/features.json")) {
                featurePresets = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                featurePresets = new JsonObject();
            }
        }
        return featurePresets;
    }

    public static List<String> featurePresetNames() {
        return new ArrayList<>(featurePresets().keySet());
    }

    static int color(String hex, int def) {
        try {
            return Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (Exception e) {
            return def;
        }
    }

    private void biome(Biome b) {
        JsonObject o = new JsonObject();
        o.addProperty("has_precipitation", b.precipitation);
        o.addProperty("temperature", (float) b.temperature);
        o.addProperty("downfall", (float) b.downfall);
        JsonObject fx = new JsonObject();
        fx.addProperty("sky_color", color(b.sky, 7907327));
        fx.addProperty("fog_color", color(b.fog, 12638463));
        fx.addProperty("water_color", color(b.water, 4159204));
        fx.addProperty("water_fog_color", color(b.waterFog, 329011));
        if (!b.grass.isBlank()) fx.addProperty("grass_color", color(b.grass, 9551193));
        if (!b.foliage.isBlank()) fx.addProperty("foliage_color", color(b.foliage, 7842607));
        fx.add("mood_sound", J.obj("sound", "minecraft:ambient.cave", "tick_delay", 6000, "block_search_extent", 8, "offset", 2.0));
        if (!b.ambientParticle.isBlank()) {
            fx.add("particle", J.obj("options", J.obj("type", Ids.full(b.ambientParticle)), "probability", (float) Math.max(0, Math.min(1, b.particleChance))));
        }
        if (!b.ambientSound.isBlank()) fx.addProperty("ambient_sound", Ids.full(b.ambientSound));
        if (!b.music.isBlank()) {
            JsonObject music = J.obj("sound", Ids.full(b.music), "min_delay", 12000, "max_delay", 24000, "replace_current_music", false);
            if (ctx.v.snbtText()) fx.add("music", J.arr(J.obj("data", music, "weight", 1)));
            else fx.add("music", music);
        }
        o.add("effects", fx);
        JsonObject spawners = new JsonObject();
        for (MobSpawn s : b.spawns) {
            String cat = s.category.isBlank() ? "creature" : s.category;
            if (!spawners.has(cat)) spawners.add(cat, new com.google.gson.JsonArray());
            spawners.getAsJsonArray(cat).add(J.obj("type", Ids.full(s.entity), "weight", Math.max(1, s.weight),
                    "minCount", Math.max(1, s.min), "maxCount", Math.max(Math.max(1, s.min), s.max)));
        }
        o.add("spawners", spawners);
        o.add("spawn_costs", new JsonObject());
        String preset = featurePresets().has(b.features) ? b.features : "plains";
        boolean nether = preset.equals("nether") || preset.equals("basalt");
        boolean noCaves = preset.equals("barren") || preset.equals("end");
        List<String> carvers = noCaves ? List.of() : nether ? List.of("minecraft:nether_cave")
                : List.of("minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon");
        com.google.gson.JsonArray carv = new com.google.gson.JsonArray();
        carvers.forEach(carv::add);
        if (ctx.registryKeys()) o.add("carvers", carv);
        else o.add("carvers", noCaves ? new JsonObject() : J.obj("air", carv));
        JsonObject pr = featurePresets().getAsJsonObject(preset);
        o.add("features", pr.get(ctx.v.snbtText() ? "new" : "old").deepCopy());
        ctx.files.json(data("worldgen/biome", b.name + ".json"), o);
    }

    // ------------------------------------------------------------------ dimensions

    private String biomeRef(String id) {
        String s = id.trim();
        if (s.isEmpty()) return "minecraft:plains";
        return s.contains(":") ? s : (ctx.p.byName(ElementType.BIOME, s) != null ? ns + ":" + s : "minecraft:" + s);
    }

    private void dimension(Dimension d) {
        boolean flat = !d.generator.equals("noise");
        String noise = Ids.full(d.noise.isBlank() ? "minecraft:overworld" : d.noise);
        boolean tall = !flat && (noise.equals("minecraft:overworld") || noise.equals("minecraft:amplified")
                || noise.equals("minecraft:large_biomes") || noise.equals("minecraft:caves"));
        int minY = flat ? Math.floorDiv(d.minY, 16) * 16 : tall ? -64 : 0;
        int height = flat ? Math.max(16, Math.min(4064, (d.height + 15) / 16 * 16)) : tall ? 384 : 256;
        String effects = Ids.full(d.effects.isBlank() ? "minecraft:overworld" : d.effects);
        String infiniburn = effects.equals("minecraft:the_nether") ? "#minecraft:infiniburn_nether"
                : effects.equals("minecraft:the_end") ? "#minecraft:infiniburn_end" : "#minecraft:infiniburn_overworld";
        JsonObject type = J.obj("ultrawarm", d.ultrawarm, "natural", d.natural, "coordinate_scale", Math.max(0.00001, d.coordinateScale),
                "has_skylight", d.hasSkylight, "has_ceiling", d.hasCeiling, "ambient_light", (float) Math.max(0, Math.min(1, d.ambientLight)),
                "monster_spawn_light_level", 0, "monster_spawn_block_light_limit", 0, "piglin_safe", d.piglinSafe, "bed_works", d.bedWorks,
                "respawn_anchor_works", d.respawnAnchorWorks, "has_raids", d.hasRaids, "logical_height", noise.equals("minecraft:the_nether") ? 128 : height,
                "min_y", minY, "height", height, "infiniburn", infiniburn, "effects", effects);
        if (d.fixedTime >= 0) type.addProperty("fixed_time", (long) d.fixedTime);
        ctx.files.json(data("dimension_type", d.name + ".json"), type);

        List<String> biomes = new ArrayList<>();
        for (String b : d.biomes) if (!b.isBlank()) biomes.add(biomeRef(b));
        if (biomes.isEmpty()) biomes.add("minecraft:plains");
        JsonObject generator;
        if (flat) {
            com.google.gson.JsonArray layers = new com.google.gson.JsonArray();
            if (d.generator.equals("flat")) {
                for (String line : d.flatLayers.split("\\R")) {
                    String l = line.trim();
                    if (l.isEmpty()) continue;
                    int star = l.lastIndexOf('*');
                    int h = 1;
                    String block = l;
                    if (star > 0) {
                        block = l.substring(0, star).trim();
                        try {
                            h = Integer.parseInt(l.substring(star + 1).trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    layers.add(J.obj("block", Ids.full(block), "height", Math.max(1, h)));
                }
            }
            JsonObject settings = J.obj("biome", d.generator.equals("void") ? "minecraft:the_void" : biomes.get(0), "layers", layers,
                    "lakes", false, "features", false, "structure_overrides", new com.google.gson.JsonArray());
            generator = J.obj("type", "minecraft:flat", "settings", settings);
        } else {
            JsonObject source = switch (d.biomeSource) {
                case "checkerboard" -> {
                    com.google.gson.JsonArray a = new com.google.gson.JsonArray();
                    biomes.forEach(a::add);
                    yield J.obj("type", "minecraft:checkerboard", "biomes", a, "scale", 3);
                }
                case "multi_noise" -> {
                    com.google.gson.JsonArray a = new com.google.gson.JsonArray();
                    int n = biomes.size();
                    for (int i = 0; i < n; i++) {
                        double lo = -1 + 2.0 * i / n, hi = -1 + 2.0 * (i + 1) / n;
                        JsonObject params = J.obj("temperature", J.arr((float) lo, (float) hi), "humidity", J.arr(-1f, 1f),
                                "continentalness", J.arr(-1.2f, 1.2f), "erosion", J.arr(-1f, 1f), "weirdness", J.arr(-1f, 1f),
                                "depth", 0f, "offset", 0f);
                        a.add(J.obj("biome", biomes.get(i), "parameters", params));
                    }
                    yield J.obj("type", "minecraft:multi_noise", "biomes", a);
                }
                case "vanilla_overworld" -> J.obj("type", "minecraft:multi_noise", "preset", "minecraft:overworld");
                case "vanilla_nether" -> J.obj("type", "minecraft:multi_noise", "preset", "minecraft:nether");
                default -> J.obj("type", "minecraft:fixed", "biome", biomes.get(0));
            };
            generator = J.obj("type", "minecraft:noise", "settings", noise, "biome_source", source);
        }
        ctx.files.json(data("dimension", d.name + ".json"), J.obj("type", ns + ":" + d.name, "generator", generator));
    }

    // ------------------------------------------------------------------ helpers

    private String data(String dir, String path) {
        return root + "data/" + ns + "/" + dir + "/" + path;
    }

    private void fn(String path, List<String> lines) {
        pc.functions.put(path, new ArrayList<>(lines));
    }

    private String obj(String suffix) {
        String o = ns + "." + suffix;
        pc.objectives.add(o);
        return o;
    }

    private String tmp() {
        pc.objectives.add(pc.tmp());
        return pc.tmp();
    }

    static String f(double d) {
        if (Math.abs(d) < 0.0005) return "0";
        String s = String.format(Locale.ROOT, "%.3f", d);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.equals("-0") ? "0" : s;
    }

    private String attr(String name) {
        return ctx.registryKeys() ? "minecraft:" + name : "minecraft:generic." + name;
    }

    private String text(String text, String color) {
        JsonObject o = J.obj("text", GenContext.legacy(text));
        if (color != null && !color.isBlank() && !color.equals("white")) o.addProperty("color", color);
        return J.compact(o);
    }

    /** Name of a mob: a translatable text with the English name as fallback when the mob has translations. */
    private String mobName(Systems.Mob m, String color) {
        String english = m.displayName.isBlank() ? Ids.titleCase(m.name) : m.displayName;
        boolean translated = ctx.p.meta.languages.stream().anyMatch(l -> !m.loc("name", l).isBlank());
        if (!translated) return text(english, color);
        JsonObject o = J.obj("translate", "entity." + ns + "." + m.name, "fallback", GenContext.legacy(english));
        if (color != null && !color.isBlank() && !color.equals("white")) o.addProperty("color", color);
        return J.compact(o);
    }

    /** Text component inside entity NBT: SNBT from 1.21.5, a JSON string before. */
    private String nbtText(String text, String color) {
        return ItemSpec.textArg(ctx, text(text, color));
    }

    /** Item stack SNBT for an item display (custom items resolve to their base item and model). */
    private String itemSnbt(String id) {
        String full = Ids.full(id);
        Element el = ctx.p.itemLike(full);
        if (!realItems && el instanceof Elements.Item it && full.startsWith(ns + ":")) {
            if (ctx.components()) {
                return "{id:\"" + ItemSpec.base(it) + "\",count:1,components:" + J.compact(ItemSpec.modelComponent(ctx, it)) + "}";
            }
            return "{id:\"" + ItemSpec.base(it) + "\",Count:1b,tag:{CustomModelData:" + ItemSpec.modelData(it) + "}}";
        }
        return ctx.components() ? "{id:\"" + full + "\",count:1}" : "{id:\"" + full + "\",Count:1b}";
    }

    /** Particle argument; dust and entity effect particles take their color in a version specific syntax. */
    String particle(String id, String hex, double size) {
        return particle(ctx, id, hex, size);
    }

    static String particle(GenContext ctx, String id, String hex, double size) {
        String p = Ids.full(id.isBlank() ? "minecraft:flame" : id);
        double[] c = rgb(hex);
        double sc = Math.max(0.01, Math.min(4, size));
        if (p.equals("minecraft:dust")) {
            return ctx.components() ? "minecraft:dust{color:[" + f(c[0]) + "f," + f(c[1]) + "f," + f(c[2]) + "f],scale:" + f(sc) + "f}"
                    : "minecraft:dust " + f(c[0]) + " " + f(c[1]) + " " + f(c[2]) + " " + f(sc);
        }
        if (p.equals("minecraft:entity_effect") && ctx.components()) {
            return "minecraft:entity_effect{color:[" + f(c[0]) + "f," + f(c[1]) + "f," + f(c[2]) + "f,1f]}";
        }
        return p;
    }

    static double[] rgb(String hex) {
        try {
            String h = hex.trim().replace("#", "");
            int v = Integer.parseInt(h, 16);
            return new double[]{((v >> 16) & 255) / 255.0, ((v >> 8) & 255) / 255.0, (v & 255) / 255.0};
        } catch (Exception e) {
            return new double[]{1, 0, 0};
        }
    }

    private List<String> compile(String path, List<Step> steps, List<String> prefix) {
        pc.compile(path, steps, prefix, List.of());
        return pc.functions.get(path);
    }

    // ------------------------------------------------------------------ mobs

    private static final Map<String, Double> HEIGHTS = Map.ofEntries(
            Map.entry("minecraft:zombie", 1.95), Map.entry("minecraft:husk", 1.95), Map.entry("minecraft:skeleton", 1.99),
            Map.entry("minecraft:stray", 1.99), Map.entry("minecraft:wither_skeleton", 2.4), Map.entry("minecraft:piglin", 1.95),
            Map.entry("minecraft:villager", 1.95), Map.entry("minecraft:pig", 0.9), Map.entry("minecraft:cow", 1.4),
            Map.entry("minecraft:sheep", 1.3), Map.entry("minecraft:spider", 0.9), Map.entry("minecraft:iron_golem", 2.7),
            Map.entry("minecraft:wolf", 0.85), Map.entry("minecraft:ravager", 2.2), Map.entry("minecraft:blaze", 1.8),
            Map.entry("minecraft:creeper", 1.7), Map.entry("minecraft:enderman", 2.9), Map.entry("minecraft:allay", 0.6),
            Map.entry("minecraft:vex", 0.8), Map.entry("minecraft:warden", 2.9), Map.entry("minecraft:chicken", 0.7));

    public static String mobTag(String ns, String name) {
        return ns + ".mob." + name;
    }

    private void mob(Mob m) {
        String dir = "mobs/" + m.name;
        String tag = mobTag(ns, m.name);
        String base = Ids.full(m.base.isBlank() ? "minecraft:zombie" : m.base);
        double health = Math.max(1, m.health);

        // spawn
        StringBuilder nbt = new StringBuilder("{Tags:[\"" + ns + ".mob\",\"" + tag + "\",\"" + ns + ".new\"]");
        if (m.persistent) nbt.append(",PersistenceRequired:1b");
        if (m.glowing) nbt.append(",Glowing:1b");
        if (m.silent) nbt.append(",Silent:1b");
        if (m.noAi) nbt.append(",NoAI:1b");
        if (m.invulnerable) nbt.append(",Invulnerable:1b");
        if (m.baby) nbt.append(",IsBaby:1b,Age:-2147483647");
        if (!m.displayName.isBlank()) {
            nbt.append(",CustomName:").append(ItemSpec.textArg(ctx, mobName(m, m.boss ? "red" : "white")));
            if (m.showName) nbt.append(",CustomNameVisible:1b");
        }
        if (!m.lootTable.isBlank()) nbt.append(",DeathLootTable:\"").append(Ids.full(m.lootTable)).append('"');
        nbt.append('}');
        fn(dir + "/spawn", List.of("summon " + base + " ~ ~ ~ " + nbt,
                "execute as @e[tag=" + ns + ".new] at @s run function " + ns + ":" + dir + "/init"));

        // init (runs as the new mob)
        List<String> init = new ArrayList<>();
        init.add("tag @s remove " + ns + ".new");
        init.add("attribute @s " + attr("max_health") + " base set " + f(health));
        init.add("data modify entity @s Health set value " + f(health) + "f");
        init.add("attribute @s " + attr("attack_damage") + " base set " + f(m.damage));
        init.add("attribute @s " + attr("movement_speed") + " base set " + f(m.speed));
        if (m.armor > 0) init.add("attribute @s " + attr("armor") + " base set " + f(m.armor));
        if (m.knockbackResistance > 0) init.add("attribute @s " + attr("knockback_resistance") + " base set " + f(Math.min(1, m.knockbackResistance)));
        init.add("attribute @s " + attr("follow_range") + " base set " + f(m.followRange));
        if (Math.abs(m.scale - 1) > 0.001 && ctx.components()) init.add("attribute @s " + attr("scale") + " base set " + f(m.scale));
        String[][] eq = {{"weapon.mainhand", m.mainhand}, {"weapon.offhand", m.offhand}, {"armor.head", m.head},
                {"armor.chest", m.chest}, {"armor.legs", m.legs}, {"armor.feet", m.feet}};
        for (String[] e : eq) {
            if (e[1] == null || e[1].isBlank()) continue;
            String arg = pc.itemArg(e[1]);
            if (arg != null) init.add("item replace entity @s " + e[0] + " with " + arg);
        }
        if (!m.dropEquipment) {
            init.add(ctx.v.snbtText()
                    ? "data merge entity @s {drop_chances:{mainhand:0f,offhand:0f,head:0f,chest:0f,legs:0f,feet:0f}}"
                    : "data merge entity @s {HandDropChances:[0f,0f],ArmorDropChances:[0f,0f,0f,0f]}");
        }
        if (m.fireImmune) init.add("effect give @s minecraft:fire_resistance infinite 0 true");
        if (!m.appearance.isBlank()) {
            double s = Math.max(0.05, m.appearanceScale);
            double h = HEIGHTS.getOrDefault(base, 1.8);
            init.add("effect give @s minecraft:invisibility infinite 0 true");
            init.add("summon minecraft:item_display ~ ~ ~ {Tags:[\"" + ns + ".mobdisplay\",\"" + tag + ".display\",\"" + ns + ".newd\"],item:"
                    + itemSnbt(m.appearance) + ",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,"
                    + f(-h + 0.5 * s) + "f,0f],scale:[" + f(s) + "f," + f(s) + "f," + f(s) + "f]}}");
            init.add("ride @e[tag=" + ns + ".newd,limit=1] mount @s");
            init.add("tag @e[tag=" + ns + ".newd] remove " + ns + ".newd");
            tick.add("execute as @e[type=minecraft:item_display,tag=" + tag + ".display] at @s unless entity @e[tag=" + tag + ",distance=..4] run kill @s");
        }
        String bar = ns + ":boss_" + m.name;
        if (m.boss) {
            init.add("scoreboard players set @s " + obj("phase") + " 0");
            String title = m.displayName.isBlank() ? Ids.titleCase(m.name) : m.displayName;
            load.add("bossbar add " + bar + " " + mobName(m, "red"));
            load.add("bossbar set " + bar + " name " + mobName(m, "red"));
            load.add("bossbar set " + bar + " color " + m.bossColor);
            load.add("bossbar set " + bar + " style " + m.bossStyle);
            load.add("bossbar set " + bar + " max " + (int) Math.ceil(health));
            tick.add("execute unless entity @e[tag=" + tag + "] run bossbar set " + bar + " players");
        }
        if (!m.onSpawn.isEmpty()) {
            compile(dir + "/on_spawn", m.onSpawn, List.of());
            init.add("function " + ns + ":" + dir + "/on_spawn");
        }
        fn(dir + "/init", init);

        // per tick
        List<String> t = new ArrayList<>();
        if (!m.onTick.isEmpty()) {
            String mt = obj("mt");
            compile(dir + "/on_tick", m.onTick, List.of("scoreboard players set @s " + mt + " 0"));
            t.add("scoreboard players add @s " + mt + " 1");
            t.add("execute if score @s " + mt + " matches " + Math.max(1, m.tickInterval) + ".. run function " + ns + ":" + dir + "/on_tick");
        }
        if (!m.appearance.isBlank()) {
            t.add("execute on passengers if entity @s[tag=" + tag + ".display] run data modify entity @s Rotation[0] set from entity @e[tag="
                    + tag + ",limit=1,sort=nearest] Rotation[0]");
        }
        if (m.boss) {
            String hp = obj("hp");
            t.add("execute store result score @s " + hp + " run data get entity @s Health");
            t.add("execute store result bossbar " + bar + " value run scoreboard players get @s " + hp);
            t.add("bossbar set " + bar + " players @a[distance=.." + Math.max(8, m.bossRadius) + "]");
            List<BossPhase> phases = new ArrayList<>(m.phases);
            phases.sort((a, b) -> Integer.compare(b.health, a.health));
            for (int k = 1; k <= phases.size(); k++) {
                BossPhase ph = phases.get(k - 1);
                int threshold = (int) Math.ceil(health * Math.max(0, Math.min(100, ph.health)) / 100.0);
                List<String> pre = new ArrayList<>();
                pre.add("scoreboard players set @s " + obj("phase") + " " + k);
                if (!ph.message.isBlank()) pre.add("tellraw @a[distance=.." + Math.max(8, m.bossRadius) + "] " + text(ph.message, "gold"));
                compile(dir + "/phase_" + k, ph.steps, pre);
                t.add("execute if score @s " + hp + " matches .." + threshold + " if score @s " + obj("phase") + " matches .." + (k - 1)
                        + " run function " + ns + ":" + dir + "/phase_" + k);
            }
        }
        if (!t.isEmpty()) {
            fn(dir + "/tick", t);
            tick.add("execute as @e[tag=" + tag + "] at @s run function " + ns + ":" + dir + "/tick");
        }

        // player interactions through advancements
        playerAdvancement(m, "hurt", "player_hurt_entity", m.onHurt,
                "execute as @e[tag=" + tag + ",limit=1,sort=nearest,distance=..16] at @s run function " + ns + ":" + dir + "/on_hurt");
        playerAdvancement(m, "death", "player_killed_entity", m.onDeath, "function " + ns + ":" + dir + "/on_death");
        playerAdvancement(m, "interact", "player_interacted_with_entity", m.onInteract, "function " + ns + ":" + dir + "/on_interact");

        // natural spawning near players
        if (m.naturalSpawn) {
            int every = Math.max(1, m.spawnInterval) * 20;
            String chance = "mobs/" + m.name + "_chance";
            pc.predicates.put(chance, J.obj("condition", "minecraft:random_chance", "chance", Math.max(0, Math.min(100, m.spawnChance)) / 100.0));
            StringBuilder cond = new StringBuilder("execute as @a[gamemode=!spectator] at @s");
            if (!m.spawnBiome.isBlank()) cond.append(" if biome ~ ~ ~ ").append(Ids.full(m.spawnBiome));
            if (m.spawnAtNight) {
                pc.predicates.put("mobs/night", J.obj("condition", "minecraft:time_check", "value", J.obj("min", 13000, "max", 23000), "period", 24000));
                cond.append(" if predicate ").append(ns).append(":mobs/night");
            }
            cond.append(" if predicate ").append(ns).append(':').append(chance).append(" run function ").append(ns).append(':').append(dir).append("/natural_try");
            fn(dir + "/natural", List.of("schedule function " + ns + ":" + dir + "/natural " + every + "t replace", cond.toString()));
            load.add("schedule function " + ns + ":" + dir + "/natural " + every + "t replace");
            String t2 = tmp();
            fn(dir + "/natural_try", List.of(
                    "execute store result score #n " + t2 + " if entity @e[tag=" + tag + ",distance=..64]",
                    "execute if score #n " + t2 + " matches .." + Math.max(0, m.spawnMax - 1) + " run function " + ns + ":" + dir + "/natural_place"));
            fn(dir + "/natural_place", List.of(
                    "summon minecraft:marker ~ ~ ~ {Tags:[\"" + ns + ".spawnpos\"]}",
                    "execute store success score #ok " + t2 + " run spreadplayers ~ ~ 12 32 false @e[type=minecraft:marker,tag=" + ns + ".spawnpos]",
                    "execute if score #ok " + t2 + " matches 1 as @e[type=minecraft:marker,tag=" + ns + ".spawnpos] at @s run function " + ns + ":" + dir + "/spawn",
                    "kill @e[type=minecraft:marker,tag=" + ns + ".spawnpos]"));
        }
    }

    private void playerAdvancement(Mob m, String kind, String trigger, List<Step> steps, String call) {
        if (steps.isEmpty()) return;
        String dir = "mobs/" + m.name;
        compile(dir + "/on_" + kind, steps, List.of());
        String advPath = dir + "/" + kind;
        JsonObject entity = J.obj("nbt", "{Tags:[\"" + mobTag(ns, m.name) + "\"]}");
        ctx.files.json(data(ctx.advDir(), advPath + ".json"), J.obj("criteria", J.obj("hit", J.obj("trigger", "minecraft:" + trigger,
                "conditions", J.obj("entity", entity))), "rewards", J.obj("function", ns + ":" + advPath + "_reward")));
        fn(advPath + "_reward", List.of("advancement revoke @s only " + ns + ":" + advPath, call));
    }

    // ------------------------------------------------------------------ particle effects

    /** Points of a shape in shape space: x/z is the main plane, y is height. */
    public static List<double[]> shapePoints(Effect e) {
        List<double[]> r = new ArrayList<>();
        int n = Math.max(1, Math.min(2000, e.points));
        double rad = e.radius, h = e.height, len = e.length;
        switch (e.shape) {
            case "point" -> r.add(new double[]{0, 0, 0});
            case "sphere" -> {
                double golden = Math.PI * (3 - Math.sqrt(5));
                for (int i = 0; i < n; i++) {
                    double yy = 1 - (i / (double) Math.max(1, n - 1)) * 2;
                    double rr = Math.sqrt(Math.max(0, 1 - yy * yy));
                    double th = golden * i;
                    r.add(new double[]{Math.cos(th) * rr * rad, yy * rad, Math.sin(th) * rr * rad});
                }
            }
            case "helix", "cone" -> {
                for (int i = 0; i < n; i++) {
                    double t = i / (double) n;
                    double a = 2 * Math.PI * e.turns * t;
                    double rr = e.shape.equals("cone") ? rad * (1 - t) : rad;
                    r.add(new double[]{Math.cos(a) * rr, h * t, Math.sin(a) * rr});
                }
            }
            case "line" -> {
                for (int i = 0; i < n; i++) r.add(new double[]{0, 0, len * i / (double) Math.max(1, n - 1)});
            }
            case "wave" -> {
                for (int i = 0; i < n; i++) {
                    double t = i / (double) Math.max(1, n - 1);
                    r.add(new double[]{Math.sin(2 * Math.PI * e.turns * t) * rad, 0, len * t});
                }
            }
            case "spiral" -> {
                for (int i = 0; i < n; i++) {
                    double t = i / (double) n;
                    double a = 2 * Math.PI * e.turns * t;
                    r.add(new double[]{Math.cos(a) * rad * t, 0, Math.sin(a) * rad * t});
                }
            }
            case "square" -> {
                int side = Math.max(1, n / 4);
                for (int s = 0; s < 4; s++) {
                    for (int i = 0; i < side; i++) {
                        double t = -rad + 2 * rad * i / side;
                        r.add(switch (s) {
                            case 0 -> new double[]{t, 0, -rad};
                            case 1 -> new double[]{rad, 0, t};
                            case 2 -> new double[]{-t, 0, rad};
                            default -> new double[]{-rad, 0, -t};
                        });
                    }
                }
            }
            case "star" -> {
                double[][] v = new double[10][];
                for (int k = 0; k < 10; k++) {
                    double a = -Math.PI / 2 + k * Math.PI / 5;
                    double rr = k % 2 == 0 ? rad : rad * 0.4;
                    v[k] = new double[]{Math.cos(a) * rr, Math.sin(a) * rr};
                }
                int per = Math.max(1, n / 10);
                for (int k = 0; k < 10; k++) {
                    double[] p0 = v[k], p1 = v[(k + 1) % 10];
                    for (int i = 0; i < per; i++) {
                        double t = i / (double) per;
                        r.add(new double[]{p0[0] + (p1[0] - p0[0]) * t, 0, p0[1] + (p1[1] - p0[1]) * t});
                    }
                }
            }
            default -> { // circle
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n;
                    r.add(new double[]{Math.cos(a) * rad, 0, Math.sin(a) * rad});
                }
            }
        }
        return r;
    }

    List<String> effectLines(Effect e) {
        List<String> out = new ArrayList<>();
        String p = particle(e.particle, e.color, e.size);
        String tail = " " + f(e.spread) + " " + f(e.spread) + " " + f(e.spread) + " " + f(e.speed) + " " + Math.max(1, e.count) + (e.force ? " force" : "");
        String plane = e.shape.equals("line") || e.shape.equals("wave") ? (e.plane.equals("horizontal") ? "flat_forward" : "facing") : e.plane;
        for (double[] pt : shapePoints(e)) {
            String pos = switch (plane) {
                case "vertical" -> "^" + f(pt[0]) + " ^" + f(pt[2] + e.yOffset) + " ^" + f(pt[1]);
                case "facing" -> "^" + f(pt[0]) + " ^" + f(pt[1] + e.yOffset) + " ^" + f(pt[2]);
                case "flat_forward" -> "^" + f(pt[0]) + " ^" + f(e.yOffset) + " ^" + f(pt[2]);
                default -> "~" + f(pt[0]) + " ~" + f(pt[1] + e.yOffset) + " ~" + f(pt[2]);
            };
            out.add("particle " + p + " " + pos.replace("~0 ", "~ ").replace("^0 ", "^ ").replaceAll("([~^])0$", "$1") + tail);
        }
        return out;
    }
}
