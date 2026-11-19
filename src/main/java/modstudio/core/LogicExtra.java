package modstudio.core;

import modstudio.core.Logic.PType;
import modstudio.core.Logic.Param;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The larger set of procedure steps and conditions: text and chat, item editing, entity data, displays, world and
 * time, score math and storage, teams, advancements and recipes, and blocks that run their body as, at or in
 * something. Registered into {@link Logic} when it loads; compiled by gen.ProcExtra.
 */
public final class LogicExtra {
    private LogicExtra() {}

    /** Chat steps that Bedrock shows as plain text (links, hover and click actions do not exist there). */
    public static final Set<String> BEDROCK_TEXT = Set.of("message_link", "message_command", "message_suggest", "message_hover", "message_copy",
            "message_name", "message_score", "actionbar_score");

    /** Ids of the steps and conditions added here. */
    private static final Set<String> IDS = new HashSet<>();

    /** Steps added here that hold a nested body of steps. */
    public static final Set<String> BODY = Set.of("as_entities", "at_entities", "in_dimension", "facing_entity", "on_relation", "summon_run",
            "positioned_over", "rotated");

    /** Steps that need the mod's client or server code (custom screens and the computer's clock). */
    public static final Set<String> MOD_ONLY = Set.of("open_screen", "close_screen", "show_overlay", "hide_overlay", "real_time");

    static final String[] SLOTS = {"weapon.mainhand", "weapon.offhand", "armor.head", "armor.chest", "armor.legs", "armor.feet",
            "hotbar.0", "hotbar.1", "hotbar.2", "hotbar.3", "hotbar.4", "hotbar.5", "hotbar.6", "hotbar.7", "hotbar.8"};

    static void register() {
        Set<String> before = new HashSet<>(Logic.STEPS.keySet());
        before.addAll(Logic.CONDITIONS.keySet());
        add();
        IDS.addAll(Logic.STEPS.keySet());
        IDS.addAll(Logic.CONDITIONS.keySet());
        IDS.removeAll(before);
    }

    /** Whether the step or condition is one of the larger set. */
    public static boolean isExtra(String kind) {
        return IDS.contains(kind);
    }

    private static String or(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static String pos(Elements.Step s) {
        return or(s.get("x"), "~") + " " + or(s.get("y"), "~") + " " + or(s.get("z"), "~");
    }

    private static String box(Elements.Step s) {
        return or(s.get("x1"), "~") + " " + or(s.get("y1"), "~") + " " + or(s.get("z1"), "~") + " " + or(s.get("x2"), "~") + " " + or(s.get("y2"), "~") + " "
                + or(s.get("z2"), "~");
    }

    /** Custom plugin-registered Bedrock command emitters keyed by step kind. */
    private static final Map<String, Function<Elements.Step, String>> CUSTOM_BEDROCK_COMMANDS = new ConcurrentHashMap<>();

    /** Registers or overrides the Bedrock command template for a step kind. */
    public static void registerBedrockCommand(String kind, Function<Elements.Step, String> handler) {
        if (kind != null && !kind.isBlank() && handler != null) CUSTOM_BEDROCK_COMMANDS.put(kind, handler);
    }

    /**
     * The Bedrock command of a step from the larger set or custom extensions whose command runs in Bedrock
     * (an empty string when it does nothing there), or null when the step has no Bedrock form.
     */
    public static String bedrockCommand(Elements.Step s) {
        if (s == null || s.kind == null) return null;
        Function<Elements.Step, String> custom = CUSTOM_BEDROCK_COMMANDS.get(s.kind);
        if (custom != null) return custom.apply(s);
        if (!isExtra(s.kind)) return null;
        String t = or(s.get("target"), "@s");
        return switch (s.kind) {
            case "say" -> "say " + or(s.get("text"), "Hello!").replaceAll("\\s*\\R\\s*", " ");
            case "tell" -> "tell " + t + " " + or(s.get("text"), "Hello!").replaceAll("\\s*\\R\\s*", " ");
            case "tellraw_raw" -> "tellraw " + t + " {\"rawtext\":[{\"text\":" + jsonQuote(or(s.get("json"), "{}")) + "}]}";
            case "title_times" -> "title " + t + " times " + or(s.get("fade_in"), "10") + " " + or(s.get("stay"), "70") + " " + or(s.get("fade_out"), "20");
            case "title_clear" -> "title " + t + " clear";
            case "message_score" -> "tellraw " + t + " {\"rawtext\":[{\"text\":" + jsonQuote(or(s.get("text"), "Points: "))
                    + "},{\"score\":{\"name\":\"*\",\"objective\":" + jsonQuote(or(s.get("objective"), "points")) + "}}]}";
            case "actionbar_score" -> "titleraw " + t + " actionbar {\"rawtext\":[{\"text\":" + jsonQuote(or(s.get("text"), "Coins: "))
                    + "},{\"score\":{\"name\":\"*\",\"objective\":" + jsonQuote(or(s.get("objective"), "coins")) + "}}]}";
            case "clear_chat" -> "";
            case "clear_all" -> "clear " + t;
            case "time_add" -> "time add " + or(s.get("ticks"), "1000");
            case "time_set" -> "time set " + or(s.get("ticks"), "6000");
            case "weather_for" -> "weather " + or(s.get("weather"), "rain") + " " + or(s.get("seconds"), "300");
            case "teleport_to" -> "tp " + t + " " + or(s.get("destination"), "@p");
            case "teleport_forward" -> "execute as " + t + " at @s run tp @s ^ ^ ^" + or(s.get("distance"), "5");
            case "teleport_random" -> "spreadplayers ~ ~ 1 " + or(s.get("radius"), "50") + " " + t;
            case "face_position" -> "execute as " + t + " at @s run tp @s ~ ~ ~ facing " + pos(s);
            case "face_entity" -> "execute as " + t + " at @s run tp @s ~ ~ ~ facing " + or(s.get("other"), "@p");
            case "rotate" -> "execute as " + t + " at @s run tp @s ~ ~ ~ " + or(s.get("yaw"), "~") + " " + or(s.get("pitch"), "~");
            case "lightning_at" -> "execute at " + t + " run summon lightning_bolt ~ ~ ~";
            case "explode_at" -> "execute at " + t + " run summon creeper ~ ~ ~ minecraft:start_exploding_forced";
            case "particle_at" -> "execute at " + t + " run particle " + Ids.path(or(s.get("particle"), "minecraft:heart")) + " ~ ~1 ~";
            case "sound_at" -> "execute as " + t + " at @s run playsound " + Ids.path(or(s.get("sound"), "note.pling")) + " @s ~ ~ ~ 1 " + or(s.get("pitch"), "1");
            case "playsound_at" -> "playsound " + Ids.path(or(s.get("sound"), "random.explode")) + " @a " + pos(s) + " " + or(s.get("volume"), "1") + " " + or(s.get("pitch"), "1");
            case "play_music" -> "music play " + Ids.path(or(s.get("sound"), "record.cat"));
            case "full_heal" -> "effect " + t + " instant_health 1 10 true";
            case "effect_clear_one" -> "effect " + t + " clear";
            case "damage_by" -> "damage " + t + " " + or(s.get("amount"), "4") + " entity_attack entity " + or(s.get("attacker"), "@p");
            case "xp_clear" -> "xp -10000L " + t;
            case "set_spawn_here" -> "execute as " + t + " at @s run spawnpoint @s ~ ~ ~";
            case "kill_type" -> "kill @e[type=" + Ids.full(or(s.get("entity"), "minecraft:zombie")) + ",r=" + or(s.get("radius"), "16") + "]";
            case "spread" -> "spreadplayers " + or(s.get("x"), "~") + " " + or(s.get("z"), "~") + " " + or(s.get("distance"), "5") + " " + or(s.get("range"), "100") + " " + t;
            case "world_spawn" -> "setworldspawn " + pos(s);
            case "destroy_block" -> "setblock " + pos(s) + " air destroy";
            case "setblock_mode" -> "setblock " + pos(s) + " " + Ids.full(or(s.get("block"), "minecraft:stone")) + " " + or(s.get("mode"), "replace");
            case "fill_replace" -> "fill " + box(s) + " " + Ids.full(or(s.get("block"), "minecraft:stone")) + " replace " + Ids.full(or(s.get("filter"), "minecraft:dirt"));
            case "clone_area" -> "clone " + box(s) + " " + or(s.get("dx"), "~") + " " + or(s.get("dy"), "~") + " " + or(s.get("dz"), "~") + " "
                    + (or(s.get("mode"), "replace").equals("masked") ? "masked" : "replace");
            case "give_named", "give_enchanted" -> "give " + t + " " + Ids.path(or(s.get("item"), "diamond_sword")) + " " + or(s.get("count"), "1");
            case "summon_named" -> "summon " + Ids.path(or(s.get("entity"), "villager")) + " " + jsonQuote(or(s.get("name"), "Guide")) + " " + pos(s);
            case "score_operation" -> s.get("objective").isBlank() || s.get("source_objective").isBlank() ? ""
                    : "scoreboard players operation " + t + " " + s.get("objective").trim() + " " + or(s.get("op"), "+=") + " " + or(s.get("source"), "@s") + " "
                    + s.get("source_objective").trim();
            case "score_random" -> s.get("objective").isBlank() ? ""
                    : "scoreboard players random " + t + " " + s.get("objective").trim() + " " + or(s.get("min"), "1") + " " + or(s.get("max"), "100");
            case "score_reset" -> "scoreboard players reset " + t + (s.get("objective").isBlank() ? "" : " " + s.get("objective").trim());
            case "objective_add" -> s.get("objective").isBlank() ? ""
                    : "scoreboard objectives add " + s.get("objective").trim() + " dummy " + jsonQuote(or(s.get("name"), s.get("objective").trim()));
            case "objective_remove" -> s.get("objective").isBlank() ? "" : "scoreboard objectives remove " + s.get("objective").trim();
            case "objective_rename" -> "";
            case "sidebar_clear" -> "scoreboard objectives setdisplay " + (or(s.get("slot"), "sidebar").startsWith("sidebar") ? "sidebar" : or(s.get("slot"), "sidebar"));
            default -> null;
        };
    }

    private static String jsonQuote(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void add() {
        Param target = Param.of("target", PType.SELECTOR, "@s");
        Param x = Param.of("x", PType.COORD, "~"), y = Param.of("y", PType.COORD, "~"), z = Param.of("z", PType.COORD, "~");
        Param color = Param.choice("color", "white", Logic.COLORS);
        Param slot = Param.choice("slot", "weapon.mainhand", SLOTS);
        Param on = Param.of("on", PType.BOOL, "true");
        Param objective = Param.of("objective", PType.TEXT, "points");

        // ---------------------------------------------------------------- chat and text
        Logic.step("say", "chat", Param.of("text", PType.TEXT, "Hello!"));
        Logic.step("tell", "chat", target, Param.of("text", PType.TEXT, "Psst, over here"));
        Logic.step("tellraw_raw", "chat", target, Param.of("json", PType.LONGTEXT, "{\"text\":\"Hello\",\"color\":\"gold\"}"));
        Logic.step("title_times", "chat", target, Param.of("fade_in", PType.INT, "10"), Param.of("stay", PType.INT, "70"), Param.of("fade_out", PType.INT, "20"));
        Logic.step("title_clear", "chat", target);
        Logic.step("message_score", "chat", target, Param.of("text", PType.TEXT, "Points: "), objective, color);
        Logic.step("actionbar_score", "chat", target, Param.of("text", PType.TEXT, "Coins: "), Param.of("objective", PType.TEXT, "coins"), Param.choice("color", "gold", Logic.COLORS));
        Logic.step("message_link", "chat", target, Param.of("text", PType.TEXT, "Open the wiki"), Param.of("url", PType.TEXT, "https://example.com"), Param.choice("color", "aqua", Logic.COLORS));
        Logic.step("message_command", "chat", target, Param.of("text", PType.TEXT, "[Click to open the menu]"), Param.of("command", PType.TEXT, "trigger menu"), Param.choice("color", "green", Logic.COLORS));
        Logic.step("message_suggest", "chat", target, Param.of("text", PType.TEXT, "[Click to fill the chat]"), Param.of("command", PType.TEXT, "/trigger menu set "), Param.choice("color", "yellow", Logic.COLORS));
        Logic.step("message_hover", "chat", target, Param.of("text", PType.TEXT, "Hover me"), Param.of("hover", PType.TEXT, "Hidden details"), color);
        Logic.step("message_copy", "chat", target, Param.of("text", PType.TEXT, "[Copy the code]"), Param.of("copy", PType.TEXT, "RUBY-1234"), Param.choice("color", "light_purple", Logic.COLORS));
        Logic.step("message_name", "chat", target, Param.of("text", PType.TEXT, " found a ruby!"), Param.choice("color", "yellow", Logic.COLORS));
        Logic.step("clear_chat", "chat", target);

        // ---------------------------------------------------------------- items
        Logic.step("clear_all", "items", target);
        Logic.step("item_damage", "items", target, slot, Param.of("percent", PType.DECIMAL, "10"));
        Logic.step("item_repair", "items", target, slot);
        Logic.step("item_rename", "items", target, slot, Param.of("text", PType.TEXT, "Lucky Blade"), Param.choice("color", "gold", Logic.COLORS));
        Logic.step("item_lore", "items", target, slot, Param.of("text", PType.TEXT, "Blessed by the shrine"), Param.choice("color", "gray", Logic.COLORS));
        Logic.step("item_count", "items", target, slot, Param.of("count", PType.INT, "1"), Param.of("add", PType.BOOL, "true"));
        Logic.step("item_enchant_random", "items", target, slot);
        Logic.step("item_unbreakable", "items", target, slot);
        Logic.step("item_glint", "items", target, slot, on);
        Logic.step("item_copy", "items", target, Param.choice("from", "weapon.mainhand", SLOTS), Param.choice("to", "weapon.offhand", SLOTS));
        Logic.step("item_remove", "items", target, slot);
        Logic.step("give_named", "items", target, Param.of("item", PType.ITEM, "minecraft:diamond_sword"), Param.of("count", PType.INT, "1"),
                Param.of("name", PType.TEXT, "Hero's Blade"), Param.choice("color", "gold", Logic.COLORS), Param.of("lore", PType.TEXT, "A legendary sword"));
        Logic.step("give_enchanted", "items", target, Param.of("item", PType.ITEM, "minecraft:diamond_sword"), Param.of("enchantment", PType.ENCHANTMENT, "minecraft:sharpness"), Param.of("level", PType.INT, "5"));
        Logic.step("give_potion", "items", target, Param.of("potion", PType.TEXT, "minecraft:swiftness"), Param.choice("bottle", "potion", "potion", "splash_potion", "lingering_potion", "tipped_arrow"));
        Logic.step("apply_potion", "items", target, Param.ref("potion", "potion"));
        Logic.step("give_mob_loot", "items", target, Param.of("entity", PType.SELECTOR, "@e[type=minecraft:zombie,limit=1,sort=nearest]"));
        Logic.step("give_block_loot", "items", target, x, Param.of("y", PType.COORD, "~-1"), z, Param.of("tool", PType.ITEM, "minecraft:diamond_pickaxe"));
        Logic.step("container_set", "items", x, Param.of("y", PType.COORD, "~-1"), z, Param.of("slot_number", PType.INT, "0"), Param.of("item", PType.ITEM, "minecraft:diamond"), Param.of("count", PType.INT, "1"));
        Logic.step("container_clear", "items", x, Param.of("y", PType.COORD, "~-1"), z);

        // ---------------------------------------------------------------- players and mobs
        Logic.step("effect_clear_one", "entity", target, Param.of("effect", PType.EFFECT, "minecraft:poison"));
        Logic.step("push", "entity", Param.of("target", PType.SELECTOR, "@e[type=!minecraft:player,distance=..5]"), Param.of("dx", PType.DECIMAL, "0"), Param.of("dy", PType.DECIMAL, "1"), Param.of("dz", PType.DECIMAL, "0"));
        Logic.step("push_away", "entity", Param.of("target", PType.SELECTOR, "@e[type=!minecraft:player,distance=..6]"), Param.of("strength", PType.DECIMAL, "1.2"));
        Logic.step("set_health", "entity", Param.of("target", PType.SELECTOR, "@e[type=minecraft:zombie,limit=1,sort=nearest]"), Param.of("health", PType.DECIMAL, "10"));
        Logic.step("entity_name", "entity", Param.of("target", PType.SELECTOR, "@e[type=minecraft:villager,limit=1,sort=nearest]"), Param.of("name", PType.TEXT, "Bob"), Param.choice("color", "yellow", Logic.COLORS), Param.of("visible", PType.BOOL, "true"));
        Logic.step("no_ai", "entity", Param.of("target", PType.SELECTOR, "@e[type=minecraft:zombie,distance=..8]"), on);
        Logic.step("invulnerable", "entity", Param.of("target", PType.SELECTOR, "@e[type=minecraft:villager,distance=..8]"), on);
        Logic.step("silent", "entity", Param.of("target", PType.SELECTOR, "@e[distance=..8,type=!minecraft:player]"), on);
        Logic.step("no_gravity", "entity", Param.of("target", PType.SELECTOR, "@e[distance=..8,type=!minecraft:player]"), on);
        Logic.step("glowing", "entity", target, on);
        Logic.step("persistent", "entity", Param.of("target", PType.SELECTOR, "@e[type=minecraft:villager,distance=..8]"));
        Logic.step("damage_by", "entity", target, Param.of("amount", PType.DECIMAL, "4"), Param.of("damageType", PType.DAMAGE_TYPE, "minecraft:mob_attack"), Param.of("attacker", PType.SELECTOR, "@e[type=!minecraft:player,limit=1,sort=nearest]"));
        Logic.step("kill_type", "entity", Param.of("entity", PType.ENTITY, "minecraft:zombie"), Param.of("radius", PType.DECIMAL, "16"));
        Logic.step("face_position", "entity", target, x, y, z);
        Logic.step("face_entity", "entity", target, Param.of("other", PType.SELECTOR, "@e[type=!minecraft:player,limit=1,sort=nearest]"));
        Logic.step("rotate", "entity", target, Param.of("yaw", PType.COORD, "~90"), Param.of("pitch", PType.COORD, "~"));
        Logic.step("teleport_to", "entity", target, Param.of("destination", PType.SELECTOR, "@p"));
        Logic.step("teleport_forward", "entity", target, Param.of("distance", PType.DECIMAL, "5"));
        Logic.step("teleport_random", "entity", target, Param.of("radius", PType.INT, "50"));
        Logic.step("spread", "entity", Param.of("target", PType.SELECTOR, "@a"), Param.of("x", PType.COORD, "0"), Param.of("z", PType.COORD, "0"), Param.of("distance", PType.INT, "5"), Param.of("range", PType.INT, "100"));
        Logic.step("scale", "entity", target, Param.of("scale", PType.DECIMAL, "2"));
        Logic.step("set_speed", "entity", target, Param.of("speed", PType.DECIMAL, "0.15"));
        Logic.step("modifier_add", "entity", target, Param.choice("attribute", "movement_speed", Logic.ATTRIBUTES), Param.of("name", PType.TEXT, "boost"),
                Param.of("amount", PType.DECIMAL, "0.2"), Param.choice("operation", "add_multiplied_total", "add_value", "add_multiplied_base", "add_multiplied_total"));
        Logic.step("modifier_remove", "entity", target, Param.choice("attribute", "movement_speed", Logic.ATTRIBUTES), Param.of("name", PType.TEXT, "boost"));
        Logic.step("spectate", "entity", target, Param.of("entity", PType.SELECTOR, "@e[type=!minecraft:player,limit=1,sort=nearest]"));
        Logic.step("stop_spectate", "entity", target);
        Logic.step("xp_clear", "entity", target);
        Logic.step("full_heal", "entity", target);
        Logic.step("summon_named", "entity", Param.of("entity", PType.ENTITY, "minecraft:villager"), x, y, z, Param.of("name", PType.TEXT, "Guide"), Param.choice("color", "green", Logic.COLORS));
        Logic.step("summon_nbt", "entity", Param.of("entity", PType.ENTITY, "minecraft:zombie"), x, y, z, Param.of("nbt", PType.LONGTEXT, "{NoAI:1b,PersistenceRequired:1b}"));
        Logic.step("entity_data", "entity", Param.of("target", PType.SELECTOR, "@e[type=minecraft:zombie,distance=..8]"), Param.of("nbt", PType.LONGTEXT, "{CanPickUpLoot:1b}"));
        Logic.step("lightning_at", "entity", Param.of("target", PType.SELECTOR, "@e[type=minecraft:zombie,distance=..16]"));
        Logic.step("explode_at", "entity", Param.of("target", PType.SELECTOR, "@e[type=minecraft:creeper,distance=..16]"), Param.of("power", PType.INT, "2"));
        Logic.step("particle_at", "entity", Param.of("target", PType.SELECTOR, "@a"), Param.of("particle", PType.PARTICLE, "minecraft:happy_villager"), Param.of("count", PType.INT, "8"));
        Logic.step("sound_at", "entity", Param.of("target", PType.SELECTOR, "@a"), Param.of("sound", PType.SOUND, "minecraft:block.note_block.chime"), Param.of("pitch", PType.DECIMAL, "1"));
        Logic.step("set_spawn_here", "entity", target);

        // ---------------------------------------------------------------- world
        Logic.step("setblock_mode", "world", x, Param.of("y", PType.COORD, "~-1"), z, Param.of("block", PType.BLOCK, "minecraft:stone"), Param.choice("mode", "keep", "replace", "destroy", "keep"));
        Logic.step("destroy_block", "world", x, Param.of("y", PType.COORD, "~-1"), z);
        Logic.step("fill_replace", "world", Param.of("x1", PType.COORD, "~-3"), Param.of("y1", PType.COORD, "~-1"), Param.of("z1", PType.COORD, "~-3"),
                Param.of("x2", PType.COORD, "~3"), Param.of("y2", PType.COORD, "~-1"), Param.of("z2", PType.COORD, "~3"),
                Param.of("block", PType.BLOCK, "minecraft:gold_block"), Param.of("filter", PType.BLOCK, "minecraft:grass_block"));
        Logic.step("clone_area", "world", Param.of("x1", PType.COORD, "~-2"), Param.of("y1", PType.COORD, "~"), Param.of("z1", PType.COORD, "~-2"),
                Param.of("x2", PType.COORD, "~2"), Param.of("y2", PType.COORD, "~3"), Param.of("z2", PType.COORD, "~2"),
                Param.of("dx", PType.COORD, "~10"), Param.of("dy", PType.COORD, "~"), Param.of("dz", PType.COORD, "~"), Param.choice("mode", "replace", "replace", "masked"));
        Logic.step("playsound_at", "world", Param.of("sound", PType.SOUND, "minecraft:entity.generic.explode"), x, y, z, Param.of("volume", PType.DECIMAL, "1"), Param.of("pitch", PType.DECIMAL, "1"));
        Logic.step("play_music", "world", target, Param.of("sound", PType.SOUND, "minecraft:music_disc.cat"));
        Logic.step("particle_circle", "world", Param.of("particle", PType.PARTICLE, "minecraft:flame"), Param.of("radius", PType.DECIMAL, "2"), Param.of("points", PType.INT, "24"), Param.of("dy", PType.DECIMAL, "0.2"));
        Logic.step("particle_burst", "world", Param.of("particle", PType.PARTICLE, "minecraft:totem_of_undying"), Param.of("count", PType.INT, "40"), Param.of("speed", PType.DECIMAL, "0.5"));
        Logic.step("particle_column", "world", Param.of("particle", PType.PARTICLE, "minecraft:end_rod"), Param.of("height", PType.INT, "6"));
        Logic.step("firework", "world", x, Param.of("y", PType.COORD, "~1"), z, Param.of("rgb", PType.TEXT, "#FF3355"),
                Param.choice("shape", "large_ball", "small_ball", "large_ball", "star", "creeper", "burst"), Param.of("flight", PType.INT, "1"));
        Logic.step("time_add", "world", Param.of("ticks", PType.INT, "1000"));
        Logic.step("time_set", "world", Param.of("ticks", PType.INT, "6000"));
        Logic.step("weather_for", "world", Param.choice("weather", "rain", "clear", "rain", "thunder"), Param.of("seconds", PType.INT, "300"));
        Logic.step("world_spawn", "world", x, y, z);
        Logic.step("forceload", "world", Param.choice("op", "add", "add", "remove"), Param.of("x", PType.COORD, "~"), Param.of("z", PType.COORD, "~"));
        Logic.step("fill_biome", "world", Param.of("x1", PType.COORD, "~-8"), Param.of("y1", PType.COORD, "~-8"), Param.of("z1", PType.COORD, "~-8"),
                Param.of("x2", PType.COORD, "~8"), Param.of("y2", PType.COORD, "~8"), Param.of("z2", PType.COORD, "~8"), Param.of("biome", PType.TEXT, "minecraft:cherry_grove"));
        Logic.step("worldborder_center", "world", Param.of("x", PType.COORD, "0"), Param.of("z", PType.COORD, "0"));
        Logic.step("worldborder_damage", "world", Param.of("amount", PType.DECIMAL, "0.5"));
        Logic.step("place_template", "world", Param.of("template", PType.TEXT, "minecraft:igloo/top"), x, y, z,
                Param.choice("rotation", "none", "none", "clockwise_90", "180", "counterclockwise_90"));
        Logic.step("block_display", "world", Param.of("block", PType.BLOCK, "minecraft:diamond_block"), x, Param.of("y", PType.COORD, "~1"), z, Param.of("size", PType.DECIMAL, "0.5"), Param.of("tag", PType.TEXT, "deco"));
        Logic.step("item_display", "world", Param.of("item", PType.ITEM, "minecraft:diamond"), x, Param.of("y", PType.COORD, "~1.5"), z, Param.of("size", PType.DECIMAL, "1"), Param.of("tag", PType.TEXT, "deco"));
        Logic.step("text_display", "world", Param.of("text", PType.TEXT, "Welcome!"), x, Param.of("y", PType.COORD, "~2"), z, Param.choice("color", "gold", Logic.COLORS),
                Param.choice("billboard", "center", "center", "fixed", "vertical", "horizontal"), Param.of("tag", PType.TEXT, "deco"));
        Logic.step("remove_tagged", "world", Param.of("tag", PType.TEXT, "deco"));

        // ---------------------------------------------------------------- score math and storage
        Logic.step("score_operation", "math", target, objective, Param.choice("op", "+=", "+=", "-=", "*=", "/=", "%=", "=", "<", ">", "><"),
                Param.of("source", PType.SELECTOR, "@s"), Param.of("source_objective", PType.TEXT, "bonus"));
        Logic.step("score_multiply", "math", target, objective, Param.of("value", PType.INT, "2"));
        Logic.step("score_divide", "math", target, objective, Param.of("value", PType.INT, "2"));
        Logic.step("score_modulo", "math", target, objective, Param.of("value", PType.INT, "10"));
        Logic.step("score_clamp", "math", target, objective, Param.of("min", PType.INT, "0"), Param.of("max", PType.INT, "100"));
        Logic.step("score_reset", "math", target, objective);
        Logic.step("objective_add", "math", objective, Param.of("criteria", PType.TEXT, "dummy"), Param.of("name", PType.TEXT, "Points"));
        Logic.step("objective_remove", "math", objective);
        Logic.step("objective_rename", "math", objective, Param.of("name", PType.TEXT, "Points"), Param.choice("color", "yellow", Logic.COLORS));
        Logic.step("sidebar_clear", "math", Param.choice("slot", "sidebar", "sidebar", "list", "below_name"));
        Logic.step("score_to_storage", "math", target, objective, Param.of("path", PType.TEXT, "saved.points"));
        Logic.step("storage_to_score", "math", target, objective, Param.of("path", PType.TEXT, "saved.points"));
        Logic.step("storage_remove", "math", Param.of("path", PType.TEXT, "saved.points"));
        Logic.step("storage_append", "math", Param.of("path", PType.TEXT, "log"), Param.of("value", PType.TEXT, "\"opened\""));
        Logic.step("data_to_score", "math", target, Param.of("path", PType.TEXT, "Health"), objective, Param.of("factor", PType.DECIMAL, "1"));
        Logic.step("count_entities", "math", Param.of("selector", PType.SELECTOR, "@e[type=minecraft:zombie,distance=..32]"), Param.of("holder", PType.TEXT, "#zombies"), objective);
        Logic.step("score_from_xp", "math", target, objective);
        Logic.step("score_from_time", "math", Param.of("holder", PType.TEXT, "#time"), objective);
        Logic.step("score_from_position", "math", target, Param.choice("axis", "y", "x", "y", "z"), objective);
        Logic.step("score_random", "math", target, objective, Param.of("min", PType.INT, "1"), Param.of("max", PType.INT, "100"));
        Logic.step("real_time", "math", Param.of("objective", PType.TEXT, "clock"));

        // ---------------------------------------------------------------- custom screens (mod loaders)
        Logic.step("open_screen", "interface", Param.ref("screen", "screen"), target);
        Logic.step("close_screen", "interface", target);
        Logic.step("show_overlay", "interface", Param.ref("screen", "screen"), Param.of("target", PType.SELECTOR, "@a"));
        Logic.step("hide_overlay", "interface", Param.ref("screen", "screen"), Param.of("target", PType.SELECTOR, "@a"));

        // ---------------------------------------------------------------- players and progress
        Logic.step("advancement_grant", "player", target, Param.of("advancement", PType.TEXT, "minecraft:story/root"));
        Logic.step("advancement_revoke", "player", target, Param.of("advancement", PType.TEXT, "minecraft:story/root"));
        Logic.step("recipe_give", "player", target, Param.of("recipe", PType.TEXT, "*"));
        Logic.step("recipe_take", "player", target, Param.of("recipe", PType.TEXT, "*"));
        Logic.step("team_join", "player", target, Param.of("team", PType.TEXT, "red"));
        Logic.step("team_leave", "player", target);
        Logic.step("team_color", "player", Param.of("team", PType.TEXT, "red"), Param.choice("color", "red", Logic.COLORS));
        Logic.step("team_friendly_fire", "player", Param.of("team", PType.TEXT, "red"), Param.of("on", PType.BOOL, "false"));
        Logic.step("team_prefix", "player", Param.of("team", PType.TEXT, "red"), Param.of("prefix", PType.TEXT, "[Red] "), Param.choice("color", "red", Logic.COLORS));
        Logic.step("team_nametag", "player", Param.of("team", PType.TEXT, "red"), Param.choice("visibility", "hideForOtherTeams", "always", "never", "hideForOtherTeams", "hideForOwnTeam"));
        Logic.step("trigger_enable", "player", target, Param.of("objective", PType.TEXT, "menu"));

        // ---------------------------------------------------------------- logic: early exit and bodies
        Logic.step("return_value", "logic", Param.of("value", PType.INT, "1"));
        Logic.step("return_fail", "logic");
        Logic.step("as_entities", "logic", Param.of("selector", PType.SELECTOR, "@e[type=minecraft:zombie,distance=..16]"));
        Logic.step("at_entities", "logic", Param.of("selector", PType.SELECTOR, "@e[type=minecraft:armor_stand,tag=spot]"));
        Logic.step("in_dimension", "logic", Param.of("dimension", PType.DIMENSION, "minecraft:the_nether"));
        Logic.step("facing_entity", "logic", Param.of("selector", PType.SELECTOR, "@e[type=!minecraft:player,limit=1,sort=nearest]"));
        Logic.step("on_relation", "logic", Param.choice("relation", "vehicle", "attacker", "controller", "leasher", "origin", "owner", "passengers", "target", "vehicle"));
        Logic.step("summon_run", "logic", Param.of("entity", PType.ENTITY, "minecraft:marker"));
        Logic.step("positioned_over", "logic", Param.choice("heightmap", "world_surface", "world_surface", "motion_blocking", "motion_blocking_no_leaves", "ocean_floor"));
        Logic.step("rotated", "logic", Param.of("yaw", PType.COORD, "~"), Param.of("pitch", PType.COORD, "0"));

        // ---------------------------------------------------------------- conditions
        Logic.cond("entity_count", Param.of("selector", PType.SELECTOR, "@e[type=minecraft:zombie,distance=..16]"), Param.of("min", PType.INT, "3"), Param.of("max", PType.TEXT, ""));
        Logic.cond("holding_offhand", Param.of("item", PType.ITEM, "minecraft:shield"));
        Logic.cond("wearing", Param.choice("slot", "armor.head", "armor.head", "armor.chest", "armor.legs", "armor.feet"), Param.of("item", PType.ITEM, "minecraft:diamond_helmet"));
        Logic.cond("hand_empty");
        Logic.cond("on_ground");
        Logic.cond("gliding");
        Logic.cond("health", Param.of("min", PType.TEXT, ""), Param.of("max", PType.TEXT, "6"));
        Logic.cond("food", Param.of("min", PType.TEXT, ""), Param.of("max", PType.TEXT, "6"));
        Logic.cond("light", Param.of("min", PType.INT, "0"), Param.of("max", PType.INT, "7"));
        Logic.cond("in_lava");
        Logic.cond("riding");
        Logic.cond("has_passenger");
        Logic.cond("team", Param.of("team", PType.TEXT, "red"));
        Logic.cond("player_name", Param.of("name", PType.TEXT, "Steve"));
        Logic.cond("storage_exists", Param.of("path", PType.TEXT, "saved.points"));
        Logic.cond("entity_nbt", Param.of("nbt", PType.TEXT, "{OnGround:1b}"));
        Logic.cond("score_compare", Param.of("target", PType.SELECTOR, "@s"), objective, Param.choice("op", ">", "<", "<=", "=", ">=", ">"),
                Param.of("other", PType.SELECTOR, "#best"), Param.of("other_objective", PType.TEXT, "points"));
        Logic.cond("time_between", Param.of("min", PType.INT, "13000"), Param.of("max", PType.INT, "23000"));
        Logic.cond("looking", Param.of("min", PType.INT, "-90"), Param.of("max", PType.INT, "-45"));
        Logic.cond("facing", Param.of("min", PType.INT, "-45"), Param.of("max", PType.INT, "45"));
        Logic.cond("has_effect", Param.of("effect", PType.EFFECT, "minecraft:speed"));
        Logic.cond("has_advancement", Param.of("advancement", PType.TEXT, "minecraft:story/mine_diamond"));
        Logic.cond("near_position", Param.of("x", PType.COORD, "0"), Param.of("y", PType.COORD, "64"), Param.of("z", PType.COORD, "0"), Param.of("radius", PType.DECIMAL, "10"));
    }
}
