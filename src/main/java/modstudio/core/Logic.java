package modstudio.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Definitions of procedure triggers, steps and conditions (shared by editors and compilers). */
public final class Logic {
    private Logic() {}

    /** Parameter value kinds; the editor picks a control per kind. */
    public enum PType { TEXT, LONGTEXT, INT, DECIMAL, BOOL, SELECTOR, ITEM, BLOCK, ENTITY, EFFECT, SOUND, PARTICLE, FUNCTION, CHOICE, COORD, DIMENSION, REF, ENCHANTMENT, DAMAGE_TYPE }

    public record Param(String key, PType type, String def, List<String> choices) {
        public static Param of(String key, PType type, String def) {
            return new Param(key, type, def, List.of());
        }

        public static Param choice(String key, String def, String... choices) {
            return new Param(key, PType.CHOICE, def, List.of(choices));
        }

        /** Name of a project element of the given type (ElementType id). */
        public static Param ref(String key, String elementType) {
            return new Param(key, PType.REF, "", List.of(elementType));
        }
    }

    public record Def(String id, String category, List<Param> params) {}

    public static final Map<String, Def> STEPS = new LinkedHashMap<>();
    public static final Map<String, Def> CONDITIONS = new LinkedHashMap<>();
    public static final Map<String, Def> TRIGGERS = new LinkedHashMap<>();

    private static void step(String id, String cat, Param... ps) {
        STEPS.put(id, new Def(id, cat, List.of(ps)));
    }

    private static void cond(String id, Param... ps) {
        CONDITIONS.put(id, new Def(id, "condition", List.of(ps)));
    }

    private static void trig(String id, Param... ps) {
        TRIGGERS.put(id, new Def(id, "trigger", List.of(ps)));
    }

    public static final String[] SLOTS = {"weapon.mainhand", "weapon.offhand", "armor.head", "armor.chest", "armor.legs", "armor.feet"};
    public static final String[] ATTRIBUTES = {"max_health", "movement_speed", "attack_damage", "attack_speed", "armor", "armor_toughness",
            "knockback_resistance", "luck", "scale", "jump_strength", "gravity", "step_height", "safe_fall_distance"};
    public static final String[] GAMEMODES = {"survival", "creative", "adventure", "spectator"};

    public static final String[] COLORS = {"white", "gray", "dark_gray", "black", "red", "dark_red", "gold", "yellow",
            "green", "dark_green", "aqua", "dark_aqua", "blue", "dark_blue", "light_purple", "dark_purple"};

    static {
        Param target = Param.of("target", PType.SELECTOR, "@s");
        step("message", "chat", target, Param.of("text", PType.TEXT, "Hello!"), Param.choice("color", "white", COLORS), Param.of("bold", PType.BOOL, "false"));
        step("title", "chat", target, Param.of("title", PType.TEXT, "Title"), Param.of("subtitle", PType.TEXT, ""), Param.choice("color", "gold", COLORS));
        step("actionbar", "chat", target, Param.of("text", PType.TEXT, "Text"), Param.choice("color", "white", COLORS));
        step("give", "items", target, Param.of("item", PType.ITEM, "minecraft:diamond"), Param.of("count", PType.INT, "1"));
        step("clear", "items", target, Param.of("item", PType.ITEM, "minecraft:dirt"), Param.of("count", PType.INT, "1"));
        step("effect", "entity", target, Param.of("effect", PType.EFFECT, "minecraft:speed"), Param.of("seconds", PType.INT, "10"), Param.of("amplifier", PType.INT, "0"), Param.of("hide", PType.BOOL, "false"));
        step("teleport", "entity", target, Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~10"), Param.of("z", PType.COORD, "~"));
        step("damage", "entity", target, Param.of("amount", PType.DECIMAL, "2"), Param.of("damageType", PType.DAMAGE_TYPE, "minecraft:generic"));
        step("xp", "entity", target, Param.of("amount", PType.INT, "5"), Param.choice("unit", "levels", "levels", "points"));
        step("kill", "entity", target);
        step("tag", "entity", target, Param.choice("op", "add", "add", "remove"), Param.of("tag", PType.TEXT, "my_tag"));
        step("sound", "world", Param.of("sound", PType.SOUND, "minecraft:entity.player.levelup"), Param.choice("source", "master", "master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice"), target, Param.of("volume", PType.DECIMAL, "1"), Param.of("pitch", PType.DECIMAL, "1"));
        step("particle", "world", Param.of("particle", PType.PARTICLE, "minecraft:heart"), Param.of("dx", PType.DECIMAL, "0.5"), Param.of("dy", PType.DECIMAL, "0.5"), Param.of("dz", PType.DECIMAL, "0.5"), Param.of("speed", PType.DECIMAL, "0.1"), Param.of("count", PType.INT, "10"));
        step("summon", "world", Param.of("entity", PType.ENTITY, "minecraft:lightning_bolt"), Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~"), Param.of("z", PType.COORD, "~"));
        step("setblock", "world", Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~-1"), Param.of("z", PType.COORD, "~"), Param.of("block", PType.BLOCK, "minecraft:gold_block"));
        step("time", "world", Param.choice("value", "day", "day", "noon", "night", "midnight"));
        step("weather", "world", Param.choice("weather", "clear", "clear", "rain", "thunder"));
        step("score", "data", Param.choice("op", "add", "set", "add", "remove"), target, Param.of("objective", PType.TEXT, "points"), Param.of("value", PType.INT, "1"));
        step("function", "logic", Param.of("function", PType.FUNCTION, ""));
        step("command", "logic", Param.of("command", PType.LONGTEXT, "say Hello"));
        step("if", "logic");
        step("repeat", "logic", Param.of("times", PType.INT, "3"));
        step("for_each", "logic", Param.of("selector", PType.SELECTOR, "@a[distance=..16]"));
        step("at_position", "logic", Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~5"), Param.of("z", PType.COORD, "~"));
        step("schedule", "logic", Param.of("ticks", PType.INT, "20"));
        step("stop", "logic");

        step("broadcast", "chat", Param.of("text", PType.TEXT, "Hello everyone!"), Param.choice("color", "yellow", COLORS), Param.of("bold", PType.BOOL, "false"));
        step("replace_item", "items", target, Param.choice("slot", "weapon.mainhand", SLOTS), Param.of("item", PType.ITEM, "minecraft:diamond_sword"), Param.of("count", PType.INT, "1"));
        step("drop_item", "items", Param.of("item", PType.ITEM, "minecraft:diamond"), Param.of("count", PType.INT, "1"), Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~1"), Param.of("z", PType.COORD, "~"));
        step("enchant", "items", target, Param.of("enchantment", PType.ENCHANTMENT, "minecraft:sharpness"), Param.of("level", PType.INT, "1"));
        step("heal", "entity", target, Param.of("amount", PType.INT, "1"));
        step("feed", "entity", target, Param.of("amount", PType.INT, "1"));
        step("clear_effects", "entity", target);
        step("gamemode", "entity", target, Param.choice("mode", "creative", GAMEMODES));
        step("spawnpoint", "entity", target, Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~"), Param.of("z", PType.COORD, "~"));
        step("attribute", "entity", target, Param.choice("attribute", "max_health", ATTRIBUTES), Param.of("value", PType.DECIMAL, "40"));
        step("ride", "entity", target, Param.of("vehicle", PType.SELECTOR, "@e[type=minecraft:horse,limit=1,sort=nearest]"));
        step("dismount", "entity", target);
        step("fill", "world", Param.of("x1", PType.COORD, "~-2"), Param.of("y1", PType.COORD, "~-1"), Param.of("z1", PType.COORD, "~-2"),
                Param.of("x2", PType.COORD, "~2"), Param.of("y2", PType.COORD, "~-1"), Param.of("z2", PType.COORD, "~2"),
                Param.of("block", PType.BLOCK, "minecraft:glass"), Param.choice("mode", "replace", "replace", "destroy", "hollow", "outline", "keep"));
        step("explosion", "world", Param.of("power", PType.INT, "3"), Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~"), Param.of("z", PType.COORD, "~"));
        step("lightning", "world", Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~"), Param.of("z", PType.COORD, "~"));
        step("place", "world", Param.choice("kind", "feature", "feature", "structure"), Param.of("id", PType.TEXT, "minecraft:oak"),
                Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~"), Param.of("z", PType.COORD, "~"));
        step("stop_sound", "world", target);
        step("gamerule", "world", Param.of("rule", PType.TEXT, "keepInventory"), Param.of("value", PType.TEXT, "true"));
        step("difficulty", "world", Param.choice("value", "normal", "peaceful", "easy", "normal", "hard"));
        step("worldborder", "world", Param.of("size", PType.DECIMAL, "1000"), Param.of("seconds", PType.INT, "0"));
        step("bossbar", "data", Param.of("id", PType.TEXT, "progress"), Param.of("text", PType.TEXT, "Progress"),
                Param.choice("color", "purple", "blue", "green", "pink", "purple", "red", "white", "yellow"),
                Param.of("value", PType.INT, "50"), Param.of("max", PType.INT, "100"), Param.of("players", PType.SELECTOR, "@a"));
        step("bossbar_remove", "data", Param.of("id", PType.TEXT, "progress"));
        step("scoreboard_show", "data", Param.of("objective", PType.TEXT, "points"), Param.choice("slot", "sidebar", "sidebar", "list", "below_name"), Param.of("title", PType.TEXT, "Points"));
        step("random_score", "data", target, Param.of("objective", PType.TEXT, "roll"), Param.of("min", PType.INT, "1"), Param.of("max", PType.INT, "6"));
        step("storage", "data", Param.of("path", PType.TEXT, "counter"), Param.of("value", PType.TEXT, "0"));

        Param x = Param.of("x", PType.COORD, "~"), y = Param.of("y", PType.COORD, "~"), z = Param.of("z", PType.COORD, "~");
        step("spawn_mob", "entity", Param.ref("mob", "mob"), x, y, z);
        step("play_effect", "world", Param.ref("effect", "effect"), x, Param.of("y", PType.COORD, "~1"), z);
        step("place_structure", "world", Param.ref("structure", "structure"), x, y, z,
                Param.choice("rotation", "none", "none", "clockwise_90", "180", "counterclockwise_90"));
        step("teleport_dimension", "entity", target, Param.of("dimension", PType.DIMENSION, "minecraft:the_nether"), x, y, z,
                Param.of("safe", PType.BOOL, "true"));
        step("move_blocks", "world", Param.of("x1", PType.COORD, "~-1"), Param.of("y1", PType.COORD, "~"), Param.of("z1", PType.COORD, "~-1"),
                Param.of("x2", PType.COORD, "~1"), Param.of("y2", PType.COORD, "~2"), Param.of("z2", PType.COORD, "~1"),
                Param.of("dx", PType.INT, "0"), Param.of("dy", PType.INT, "1"), Param.of("dz", PType.INT, "0"));
        step("shoot", "entity", Param.of("entity", PType.ENTITY, "minecraft:arrow"), Param.of("speed", PType.DECIMAL, "1.5"));
        step("beam", "world", Param.of("target", PType.SELECTOR, "@e[type=!minecraft:player,limit=1,sort=nearest,distance=..32]"),
                Param.of("particle", PType.PARTICLE, "minecraft:end_rod"), Param.of("length", PType.INT, "32"));

        step("open_menu", "interface", Param.ref("menu", "menu"), target);
        step("close_menu", "interface", target);
        step("hud_show", "interface", Param.ref("hud", "hud"), Param.of("target", PType.SELECTOR, "@a"));
        step("hud_hide", "interface", Param.ref("hud", "hud"), Param.of("target", PType.SELECTOR, "@a"));
        step("timer_start", "interface", Param.ref("hud", "hud"), Param.of("seconds", PType.INT, "0"));
        step("timer_stop", "interface", Param.ref("hud", "hud"));
        step("timer_add", "interface", Param.ref("hud", "hud"), Param.of("seconds", PType.INT, "10"));
        step("http_request", "web", Param.ref("request", "api_request"));
        step("machine_place", "machine", Param.ref("machine", "machine"), x, y, z);
        step("resource_add", "machine", Param.ref("resource", "resource"), Param.of("amount", PType.TEXT, "100"), Param.of("radius", PType.DECIMAL, "3"));
        step("multiblock_build", "machine", Param.ref("multiblock", "multiblock"), x, y, z);
        step("contraption_move", "machine", Param.ref("multiblock", "multiblock"), x, y, z,
                Param.of("dx", PType.INT, "0"), Param.of("dy", PType.INT, "3"), Param.of("dz", PType.INT, "0"), Param.of("ticks", PType.INT, "40"));

        cond("chance", Param.of("percent", PType.DECIMAL, "50"));
        cond("sneaking");
        cond("holding", Param.of("item", PType.ITEM, "minecraft:diamond"));
        cond("dimension", Param.of("dimension", PType.DIMENSION, "minecraft:overworld"));
        cond("has_tag", Param.of("tag", PType.TEXT, "my_tag"));
        cond("score", Param.of("target", PType.SELECTOR, "@s"), Param.of("objective", PType.TEXT, "points"), Param.of("min", PType.TEXT, "1"), Param.of("max", PType.TEXT, ""));
        cond("block", Param.of("x", PType.COORD, "~"), Param.of("y", PType.COORD, "~-1"), Param.of("z", PType.COORD, "~"), Param.of("block", PType.BLOCK, "minecraft:grass_block"));
        cond("daytime");
        cond("raw", Param.of("execute", PType.TEXT, "if entity @s[level=10..]"));
        cond("entity_nearby", Param.of("entity", PType.ENTITY, "minecraft:zombie"), Param.of("radius", PType.DECIMAL, "8"));
        cond("level", Param.of("min", PType.INT, "10"));
        cond("gamemode", Param.choice("mode", "survival", GAMEMODES));
        cond("state", Param.choice("state", "sprinting", "on_fire", "sprinting", "swimming", "baby"));
        cond("raining");
        cond("thundering");
        cond("in_water");
        cond("biome", Param.of("biome", PType.TEXT, "minecraft:plains"));
        cond("has_item", Param.of("item", PType.ITEM, "minecraft:diamond"));
        cond("y_level", Param.of("min", PType.INT, "-64"), Param.of("max", PType.INT, "0"));
        cond("entity_type", Param.of("entity", PType.ENTITY, "minecraft:player"));
        cond("timer_running", Param.ref("hud", "hud"));
        cond("http_status", Param.ref("request", "api_request"), Param.of("min", PType.INT, "200"), Param.of("max", PType.INT, "299"));
        cond("multiblock_formed", Param.ref("multiblock", "multiblock"));
        cond("resource_amount", Param.ref("resource", "resource"), Param.of("min", PType.TEXT, "100"), Param.of("radius", PType.DECIMAL, "3"));
        cond("machine_running", Param.ref("machine", "machine"), Param.of("radius", PType.DECIMAL, "3"));
        cond("celestial", Param.choice("state", "night", "sky", "day", "night", "moon_full"));
        cond("platform", Param.choice("platform", "mod", "mod", "forge", "neoforge", "fabric", "paper", "datapack", "java_code", "bedrock"));
        String[] versions = Arrays.stream(McVersion.values()).map(v -> v.id).toArray(String[]::new);
        String[] maxVersions = new String[versions.length + 1];
        maxVersions[0] = "any";
        System.arraycopy(versions, 0, maxVersions, 1, versions.length);
        cond("mc_version", Param.choice("min", versions[0], versions), Param.choice("max", "any", maxVersions));

        trig("load");
        trig("tick");
        trig("player_tick");
        trig("interval");
        trig("player_join");
        trig("player_death");
        trig("block_break", Param.of("value", PType.BLOCK, "minecraft:stone"));
        trig("block_place", Param.of("value", PType.BLOCK, "minecraft:stone"));
        trig("item_right_click", Param.of("value", PType.ITEM, "minecraft:stick"));
        trig("item_eat", Param.of("value", PType.ITEM, "minecraft:apple"));
        trig("item_obtained", Param.of("value", PType.ITEM, "minecraft:diamond"));
        trig("item_crafted", Param.of("value", PType.ITEM, "minecraft:crafting_table"));
        trig("use_item_on_block", Param.of("value", PType.ITEM, "minecraft:stick"));
        trig("kill_entity", Param.of("value", PType.ENTITY, "minecraft:zombie"));
        trig("hurt_entity", Param.of("value", PType.ENTITY, ""));
        trig("player_hurt");
        trig("dimension_change", Param.of("value", PType.DIMENSION, "minecraft:the_nether"));
        trig("player_jump");
        trig("player_sleep");
        trig("item_enchanted", Param.of("value", PType.ITEM, ""));
        trig("fish_caught");
        trig("animal_tamed", Param.of("value", PType.ENTITY, ""));
        trig("animals_bred");
        trig("entity_interact", Param.of("value", PType.ENTITY, "minecraft:villager"));
        trig("effect_changed");
        trig("potion_brewed");
        trig("villager_trade");
        trig("totem_used");
        trig("lightning_strike");
        trig("bucket_filled", Param.of("value", PType.ITEM, ""));
        trig("enter_block", Param.of("value", PType.BLOCK, "minecraft:water"));
        trig("cron", Param.of("value", PType.TEXT, "*/5 * * * *"));
        trig("manual");
    }

    /** Steps that contain a nested list of steps (stored in Step.then). */
    public static boolean hasBody(String kind) {
        return kind.equals("repeat") || kind.equals("for_each") || kind.equals("at_position") || kind.equals("schedule");
    }

    /** Triggers that run without a player context (the executor is the server). */
    public static boolean serverContext(String trigger) {
        return trigger.equals("load") || trigger.equals("tick") || trigger.equals("interval") || trigger.equals("manual") || trigger.equals("cron");
    }

    /** Which targets can run a trigger. */
    public static List<Target> triggerSupport(String trigger) {
        List<Target> r = new ArrayList<>(List.of(Target.FORGE, Target.NEOFORGE, Target.FABRIC, Target.PAPER, Target.DATAPACK));
        if (trigger.equals("cron")) r.remove(Target.DATAPACK);
        switch (trigger) {
            case "load", "tick", "player_tick", "interval", "player_join", "player_death", "block_break", "block_place",
                 "item_right_click", "item_eat", "kill_entity" -> r.add(Target.BEDROCK);
            default -> {
            }
        }
        return r;
    }

    public static Elements.Step newStep(String kind) {
        Elements.Step s = new Elements.Step();
        s.kind = kind;
        Def d = STEPS.get(kind);
        if (d != null) for (Param p : d.params()) s.p.put(p.key(), p.def());
        if (kind.equals("if")) s.cond = newCondition("chance");
        return s;
    }

    public static Elements.Condition newCondition(String kind) {
        Elements.Condition c = new Elements.Condition();
        c.kind = kind;
        Def d = CONDITIONS.get(kind);
        if (d != null) for (Param p : d.params()) c.p.put(p.key(), p.def());
        return c;
    }
}
