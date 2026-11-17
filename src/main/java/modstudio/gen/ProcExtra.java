package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import modstudio.core.Elements;
import modstudio.core.Elements.Condition;
import modstudio.core.Elements.Step;
import modstudio.core.ElementType;
import modstudio.core.Ids;
import modstudio.core.McVersion;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Commands for the larger step set (core.LogicExtra): chat and text, item editing through generated item modifiers,
 * entity data, displays, world, score math and storage, teams and progress, and the bodies that run as, at or in
 * something. Version differences (components, SNBT text, renamed attributes) are handled here.
 */
final class ProcExtra {
    private final ProcCompiler pc;
    private final GenContext ctx;
    private final String ns;

    ProcExtra(ProcCompiler pc, GenContext ctx) {
        this.pc = pc;
        this.ctx = ctx;
        this.ns = ctx.ns;
    }

    // ------------------------------------------------------------------ helpers

    private static String sel(Step s) {
        return or(s.get("target"), "@s");
    }

    private static String or(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static String coord(String c) {
        return c == null || c.isBlank() ? "~" : c.trim();
    }

    private static int intOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static double dbl(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.4f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String pos(Step s) {
        return coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z"));
    }

    private static String oneLine(String v) {
        return v == null ? "" : v.replaceAll("\\s*\\R\\s*", " ").trim();
    }

    /** Text component as JSON (valid SNBT too). */
    private static JsonObject textObj(String text, String color) {
        JsonObject o = J.obj("text", GenContext.legacy(text == null ? "" : text));
        if (color != null && !color.isBlank() && !color.equals("white")) o.addProperty("color", color);
        return o;
    }

    private static String textJson(String text, String color) {
        return J.compact(textObj(text, color));
    }

    /** A text component inside entity or item NBT: an SNBT compound from 1.21.5, a quoted JSON string before. */
    private String nbtText(JsonObject text) {
        String json = J.compact(text);
        if (ctx.v.snbtText()) return json;
        return "'" + json.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String bool(String v) {
        return Boolean.parseBoolean(v.isBlank() ? "true" : v.trim()) ? "1b" : "0b";
    }

    private String obj(Step s, String key) {
        String o = s.get(key).trim();
        if (!o.isEmpty()) pc.objectives.add(o);
        return o;
    }

    private String blockArg(String id) {
        String full = Ids.full(or(id, "minecraft:stone"));
        if (pc.customItems() && ctx.p.itemLike(full) instanceof Elements.Block) return null;
        return full;
    }

    private String particle(String id) {
        String p = Ids.full(or(id, "minecraft:happy_villager"));
        if (p.equals("minecraft:dust")) return ctx.components() ? "minecraft:dust{color:[1f,0f,0f],scale:1f}" : "minecraft:dust 1 0 0 1";
        return p;
    }

    /** Attribute id: generic. prefix before 1.21.2; null when the attribute does not exist in this version. */
    private String attribute(String a) {
        String n = or(a, "movement_speed");
        boolean newer = List.of("scale", "jump_strength", "gravity", "step_height", "safe_fall_distance").contains(n);
        if (newer && !ctx.components()) return null;
        return "minecraft:" + (ctx.registryKeys() ? n : "generic." + n);
    }

    private String operation(String op) {
        return switch (or(op, "add_multiplied_total")) {
            case "add_value" -> ctx.components() ? "add_value" : "add";
            case "add_multiplied_base" -> ctx.components() ? "add_multiplied_base" : "multiply_base";
            default -> ctx.components() ? "add_multiplied_total" : "multiply";
        };
    }

    /** Attribute modifier id: a resource location from 1.21, a UUID (plus a name) before. */
    private String modifierId(String name, boolean withName) {
        String n = Ids.toId(or(name, "boost"));
        if (ctx.singular()) return ns + ":proc." + n;
        String uuid = UUID.nameUUIDFromBytes((ns + ":proc:" + n).getBytes(StandardCharsets.UTF_8)).toString();
        return withName ? uuid + " " + ns + "." + n : uuid;
    }

    /** Writes an item modifier file and returns its id. */
    private String modifier(JsonObject function) {
        String dir = ctx.singular() ? "item_modifier" : "item_modifiers";
        String name = "procedures/m_" + Integer.toHexString(J.compact(function).hashCode());
        pc.dataJson.put(dir + "/" + name, function);
        return ns + ":" + name;
    }

    private void modify(Step s, JsonObject function, List<String> r) {
        r.add("item modify entity " + sel(s) + " " + or(s.get("slot"), "weapon.mainhand") + " " + modifier(function));
    }

    private void tellraw(Step s, JsonObject text, List<String> r) {
        r.add("tellraw " + sel(s) + " " + J.compact(text));
    }

    /** Click event of a text component; the keys changed in 1.21.5. */
    private void click(JsonObject o, String action, String value) {
        if (ctx.v.snbtText()) {
            String key = switch (action) {
                case "open_url" -> "url";
                case "copy_to_clipboard" -> "value";
                default -> "command";
            };
            String v = action.equals("run_command") || action.equals("suggest_command") ? (value.startsWith("/") ? value.substring(1) : value) : value;
            if (action.equals("suggest_command")) v = value;
            o.add("click_event", J.obj("action", action, key, v));
        } else {
            String v = action.equals("run_command") && !value.startsWith("/") ? "/" + value : value;
            o.add("clickEvent", J.obj("action", action, "value", v));
        }
    }

    private void hover(JsonObject o, JsonObject shown) {
        if (ctx.v.snbtText()) o.add("hover_event", J.obj("action", "show_text", "value", shown));
        else o.add("hoverEvent", J.obj("action", "show_text", "contents", shown));
    }

    private static Step synthetic(String kind, String... kv) {
        Step st = new Step();
        st.kind = kind;
        for (int i = 0; i + 1 < kv.length; i += 2) st.p.put(kv[i], kv[i + 1]);
        return st;
    }

    // ------------------------------------------------------------------ steps

    /** Adds the commands of an extra step; false when the step is not one of them. */
    boolean commands(Step s, List<String> r) {
        switch (s.kind) {
            // ---------------------------------------------------------- chat and text
            case "say" -> r.add("say " + oneLine(or(s.get("text"), "Hello!")));
            case "tell" -> r.add("tell " + sel(s) + " " + oneLine(or(s.get("text"), "Hello!")));
            case "tellraw_raw" -> r.add("tellraw " + sel(s) + " " + oneLine(or(s.get("json"), "{\"text\":\"\"}")));
            case "title_times" -> r.add("title " + sel(s) + " times " + Math.max(0, intOr(s.get("fade_in"), 10)) + " "
                    + Math.max(0, intOr(s.get("stay"), 70)) + " " + Math.max(0, intOr(s.get("fade_out"), 20)));
            case "title_clear" -> r.add("title " + sel(s) + " clear");
            case "message_score", "actionbar_score" -> {
                String o = obj(s, "objective");
                if (o.isEmpty()) return true;
                JsonArray parts = new JsonArray();
                parts.add(textObj(s.get("text"), s.get("color")));
                JsonObject score = J.obj("score", J.obj("name", "*", "objective", o));
                if (!s.get("color").isBlank() && !s.get("color").equals("white")) score.addProperty("color", s.get("color"));
                parts.add(score);
                String json = "[" + J.compact(parts.get(0)) + "," + J.compact(parts.get(1)) + "]";
                r.add(s.kind.equals("message_score") ? "tellraw " + sel(s) + " " + json : "title " + sel(s) + " actionbar " + json);
            }
            case "message_link" -> {
                JsonObject t = textObj(s.get("text"), s.get("color"));
                t.addProperty("underlined", true);
                click(t, "open_url", or(s.get("url"), "https://example.com"));
                tellraw(s, t, r);
            }
            case "message_command" -> {
                JsonObject t = textObj(s.get("text"), s.get("color"));
                click(t, "run_command", or(s.get("command"), "trigger menu"));
                tellraw(s, t, r);
            }
            case "message_suggest" -> {
                JsonObject t = textObj(s.get("text"), s.get("color"));
                // not trimmed: a trailing space leaves the cursor ready for the value
                click(t, "suggest_command", s.get("command").isBlank() ? "/" : s.get("command"));
                tellraw(s, t, r);
            }
            case "message_copy" -> {
                JsonObject t = textObj(s.get("text"), s.get("color"));
                click(t, "copy_to_clipboard", s.get("copy"));
                tellraw(s, t, r);
            }
            case "message_hover" -> {
                JsonObject t = textObj(s.get("text"), s.get("color"));
                hover(t, textObj(s.get("hover"), "gray"));
                tellraw(s, t, r);
            }
            case "message_name" -> r.add("tellraw " + sel(s) + " [" + J.compact(J.obj("selector", "@s")) + "," + textJson(s.get("text"), s.get("color")) + "]");
            case "clear_chat" -> r.add("tellraw " + sel(s) + " " + J.compact(J.obj("text", "\n".repeat(100))));

            // ---------------------------------------------------------- items
            case "clear_all" -> r.add("clear " + sel(s));
            case "item_damage" -> modify(s, J.obj("function", "minecraft:set_damage", "damage", -Math.max(0, Math.min(100, dbl(s.get("percent"), 10))) / 100.0, "add", true), r);
            case "item_repair" -> modify(s, J.obj("function", "minecraft:set_damage", "damage", 1.0), r);
            case "item_rename" -> {
                JsonObject name = textObj(or(s.get("text"), "Item"), s.get("color"));
                name.addProperty("italic", false);
                JsonObject f = J.obj("function", "minecraft:set_name", "name", name);
                if (ctx.components()) f.addProperty("target", "custom_name");
                modify(s, f, r);
            }
            case "item_lore" -> {
                JsonObject line = textObj(s.get("text"), or(s.get("color"), "gray"));
                line.addProperty("italic", false);
                JsonArray lore = new JsonArray();
                lore.add(line);
                JsonObject f = J.obj("function", "minecraft:set_lore", "lore", lore);
                if (ctx.components()) f.addProperty("mode", "append");
                else f.addProperty("replace", false);
                modify(s, f, r);
            }
            case "item_count" -> modify(s, J.obj("function", "minecraft:set_count", "count", intOr(s.get("count"), 1),
                    "add", Boolean.parseBoolean(or(s.get("add"), "true"))), r);
            case "item_enchant_random" -> modify(s, J.obj("function", "minecraft:enchant_randomly"), r);
            case "item_unbreakable" -> {
                if (ctx.components()) modify(s, J.obj("function", "minecraft:set_components", "components", J.obj("minecraft:unbreakable", new JsonObject())), r);
                else modify(s, J.obj("function", "minecraft:set_nbt", "tag", "{Unbreakable:1b}"), r);
            }
            case "item_glint" -> {
                if (!ctx.components()) {
                    r.add("# the glint override needs Minecraft 1.20.5 or later");
                    return true;
                }
                modify(s, J.obj("function", "minecraft:set_components", "components",
                        J.obj("minecraft:enchantment_glint_override", Boolean.parseBoolean(or(s.get("on"), "true")))), r);
            }
            case "item_copy" -> r.add("item replace entity " + sel(s) + " " + or(s.get("to"), "weapon.offhand") + " from entity " + sel(s) + " "
                    + or(s.get("from"), "weapon.mainhand"));
            case "item_remove" -> r.add("item replace entity " + sel(s) + " " + or(s.get("slot"), "weapon.mainhand") + " with minecraft:air");
            case "give_named" -> {
                String item = pc.itemArg(or(s.get("item"), "minecraft:diamond_sword"));
                if (item == null || item.contains("[") || item.contains("{")) {
                    r.add("# give_named needs a vanilla item");
                    return true;
                }
                JsonObject name = textObj(or(s.get("name"), "Item"), s.get("color"));
                name.addProperty("italic", false);
                JsonObject lore = textObj(s.get("lore"), "gray");
                lore.addProperty("italic", false);
                int count = Math.max(1, intOr(s.get("count"), 1));
                if (ctx.components()) {
                    String n = ItemSpec.textArg(ctx, J.compact(name)), l = ItemSpec.textArg(ctx, J.compact(lore));
                    r.add("give " + sel(s) + " " + item + "[minecraft:custom_name=" + n + (s.get("lore").isBlank() ? "" : ",minecraft:lore=[" + l + "]") + "] " + count);
                } else {
                    String n = "'" + J.compact(name).replace("'", "\\'") + "'", l = "'" + J.compact(lore).replace("'", "\\'") + "'";
                    r.add("give " + sel(s) + " " + item + "{display:{Name:" + n + (s.get("lore").isBlank() ? "" : ",Lore:[" + l + "]") + "}} " + count);
                }
            }
            case "give_enchanted" -> {
                String item = pc.itemArg(or(s.get("item"), "minecraft:diamond_sword"));
                if (item == null || item.contains("[") || item.contains("{")) {
                    r.add("# give_enchanted needs a vanilla item");
                    return true;
                }
                String en = Ids.full(or(s.get("enchantment"), "minecraft:sharpness"));
                int lvl = Math.max(1, Math.min(255, intOr(s.get("level"), 1)));
                if (ctx.v.snbtText()) r.add("give " + sel(s) + " " + item + "[minecraft:enchantments={\"" + en + "\":" + lvl + "}]");
                else if (ctx.components()) r.add("give " + sel(s) + " " + item + "[minecraft:enchantments={levels:{\"" + en + "\":" + lvl + "}}]");
                else r.add("give " + sel(s) + " " + item + "{Enchantments:[{id:\"" + en + "\",lvl:" + lvl + "s}]}");
            }
            case "give_potion" -> {
                String id = Ids.full(or(s.get("potion"), "minecraft:swiftness"));
                String bottle = or(s.get("bottle"), "potion");
                if (Ids.ns(id).equals(ns) && ctx.p.byName(ElementType.POTION, Ids.path(id)) != null) {
                    String fn = switch (bottle) {
                        case "splash_potion" -> "/give_splash";
                        case "lingering_potion" -> "/give_lingering";
                        case "tipped_arrow" -> "/give_arrow";
                        default -> "/give";
                    };
                    r.add("execute as " + sel(s) + " run function " + ns + ":potion/" + Ids.path(id) + fn);
                } else if (ctx.components()) {
                    r.add("give " + sel(s) + " minecraft:" + bottle + "[minecraft:potion_contents={potion:\"" + id + "\"}]");
                } else {
                    r.add("give " + sel(s) + " minecraft:" + bottle + "{Potion:\"" + id + "\"}");
                }
            }
            case "apply_potion" -> {
                String p = s.get("potion").trim();
                if (p.isEmpty() || ctx.p.byName(ElementType.POTION, p) == null) r.add("# unknown potion " + p);
                else r.add("execute as " + sel(s) + " run function " + ns + ":potion/" + p + "/apply");
            }
            case "give_mob_loot" -> r.add("loot give " + sel(s) + " kill " + or(s.get("entity"), "@e[type=minecraft:zombie,limit=1,sort=nearest]"));
            case "give_block_loot" -> {
                String tool = pc.itemArg(or(s.get("tool"), "minecraft:diamond_pickaxe"));
                r.add("loot give " + sel(s) + " mine " + pos(s) + " " + (tool == null ? "minecraft:diamond_pickaxe" : tool));
            }
            case "container_set" -> {
                String item = pc.itemArg(or(s.get("item"), "minecraft:diamond"));
                if (item == null) {
                    r.add("# blocks from this project are not available on this platform");
                    return true;
                }
                r.add("item replace block " + pos(s) + " container." + Math.max(0, Math.min(53, intOr(s.get("slot_number"), 0))) + " with " + item + " "
                        + Math.max(1, intOr(s.get("count"), 1)));
            }
            case "container_clear" -> r.add("data remove block " + pos(s) + " Items");

            // ---------------------------------------------------------- players and mobs
            case "effect_clear_one" -> r.add("effect clear " + sel(s) + " " + Ids.full(or(s.get("effect"), "minecraft:poison")));
            case "push" -> r.add("execute as " + sel(s) + " run data merge entity @s[type=!minecraft:player] {Motion:[" + num(dbl(s.get("dx"), 0)) + "d,"
                    + num(dbl(s.get("dy"), 1)) + "d," + num(dbl(s.get("dz"), 0)) + "d]}");
            case "push_away" -> {
                double strength = Math.max(0.1, Math.min(10, dbl(s.get("strength"), 1.2)));
                pc.objectives.add(pc.tmp());
                String origin = ns + ".push", probe = ns + ".pushv";
                String key = "fx/push_" + Integer.toHexString(Double.hashCode(strength));
                pc.functions.put(key, List.of(
                        "summon minecraft:marker ^ ^ ^" + num(strength) + " {Tags:[\"" + probe + "\"]}",
                        "execute store result score #px " + pc.tmp() + " run data get entity @e[type=minecraft:marker,tag=" + probe + ",limit=1] Pos[0] 1000",
                        "execute store result score #pz " + pc.tmp() + " run data get entity @e[type=minecraft:marker,tag=" + probe + ",limit=1] Pos[2] 1000",
                        "execute store result score #sx " + pc.tmp() + " run data get entity @s Pos[0] 1000",
                        "execute store result score #sz " + pc.tmp() + " run data get entity @s Pos[2] 1000",
                        "scoreboard players operation #px " + pc.tmp() + " -= #sx " + pc.tmp(),
                        "scoreboard players operation #pz " + pc.tmp() + " -= #sz " + pc.tmp(),
                        "execute store result entity @s Motion[0] double 0.001 run scoreboard players get #px " + pc.tmp(),
                        "execute store result entity @s Motion[2] double 0.001 run scoreboard players get #pz " + pc.tmp(),
                        "data modify entity @s Motion[1] set value 0.4d",
                        "kill @e[type=minecraft:marker,tag=" + probe + "]"));
                r.add("summon minecraft:marker ~ ~ ~ {Tags:[\"" + origin + "\"]}");
                r.add("execute as " + sel(s) + " at @s facing entity @e[type=minecraft:marker,tag=" + origin + ",limit=1,sort=nearest] feet rotated ~180 0 "
                        + "if entity @s[type=!minecraft:player] run function " + ns + ":" + key);
                r.add("kill @e[type=minecraft:marker,tag=" + origin + "]");
            }
            case "set_health" -> r.add("execute as " + sel(s) + " run data merge entity @s[type=!minecraft:player] {Health:"
                    + num(Math.max(0, dbl(s.get("health"), 10))) + "f}");
            case "entity_name" -> {
                JsonObject t = textObj(or(s.get("name"), "Name"), s.get("color"));
                r.add("execute as " + sel(s) + " run data merge entity @s[type=!minecraft:player] {CustomName:" + nbtText(t) + ",CustomNameVisible:"
                        + bool(s.get("visible")) + "}");
            }
            case "no_ai" -> r.add("execute as " + sel(s) + " run data merge entity @s[type=!minecraft:player] {NoAI:" + bool(s.get("on")) + "}");
            case "invulnerable" -> r.add("execute as " + sel(s) + " run data merge entity @s[type=!minecraft:player] {Invulnerable:" + bool(s.get("on")) + "}");
            case "silent" -> r.add("execute as " + sel(s) + " run data merge entity @s[type=!minecraft:player] {Silent:" + bool(s.get("on")) + "}");
            case "no_gravity" -> r.add("execute as " + sel(s) + " run data merge entity @s[type=!minecraft:player] {NoGravity:" + bool(s.get("on")) + "}");
            case "glowing" -> {
                boolean on = Boolean.parseBoolean(or(s.get("on"), "true"));
                r.add("execute as " + sel(s) + " run data merge entity @s[type=!minecraft:player] {Glowing:" + (on ? "1b" : "0b") + "}");
                r.add(on ? "effect give " + withType(sel(s), "minecraft:player") + " minecraft:glowing infinite 0 true"
                        : "effect clear " + withType(sel(s), "minecraft:player") + " minecraft:glowing");
            }
            case "persistent" -> r.add("execute as " + sel(s) + " run data merge entity @s[type=!minecraft:player] {PersistenceRequired:1b}");
            case "damage_by" -> r.add("damage " + sel(s) + " " + num(Math.max(0, dbl(s.get("amount"), 4))) + " " + Ids.full(or(s.get("damageType"), "minecraft:mob_attack"))
                    + " by " + or(s.get("attacker"), "@s"));
            case "kill_type" -> r.add("kill @e[type=" + Ids.full(or(s.get("entity"), "minecraft:zombie")) + ",distance=.." + num(Math.max(0.5, dbl(s.get("radius"), 16))) + "]");
            case "face_position" -> r.add("execute as " + sel(s) + " at @s run tp @s ~ ~ ~ facing " + pos(s));
            case "face_entity" -> r.add("execute as " + sel(s) + " at @s run tp @s ~ ~ ~ facing entity " + or(s.get("other"), "@p") + " eyes");
            case "rotate" -> r.add("execute as " + sel(s) + " at @s run tp @s ~ ~ ~ " + coord(s.get("yaw")) + " " + coord(s.get("pitch")));
            case "teleport_to" -> r.add("tp " + sel(s) + " " + or(s.get("destination"), "@p"));
            case "teleport_forward" -> r.add("execute as " + sel(s) + " at @s run tp @s ^ ^ ^" + num(dbl(s.get("distance"), 5)));
            case "teleport_random" -> r.add("execute as " + sel(s) + " at @s run spreadplayers ~ ~ 0 " + Math.max(2, intOr(s.get("radius"), 50)) + " false @s");
            case "spread" -> {
                int d = Math.max(0, intOr(s.get("distance"), 5)), range = Math.max(d + 1, intOr(s.get("range"), 100));
                r.add("spreadplayers " + coord(s.get("x")) + " " + coord(s.get("z")) + " " + d + " " + range + " false " + sel(s));
            }
            case "scale" -> {
                String a = attribute("scale");
                if (a == null) r.add("# the scale attribute needs Minecraft 1.20.5 or later");
                else r.add("attribute " + sel(s) + " " + a + " base set " + num(Math.max(0.0625, Math.min(16, dbl(s.get("scale"), 1)))));
            }
            case "set_speed" -> r.add("attribute " + sel(s) + " " + attribute("movement_speed") + " base set " + num(Math.max(0, dbl(s.get("speed"), 0.1))));
            case "modifier_add" -> {
                String a = attribute(s.get("attribute"));
                if (a == null) {
                    r.add("# attribute " + s.get("attribute") + " needs Minecraft 1.20.5 or later");
                    return true;
                }
                // replacing keeps the step repeatable: an existing modifier with the same id is removed first
                r.add("attribute " + sel(s) + " " + a + " modifier remove " + modifierId(s.get("name"), false));
                r.add("attribute " + sel(s) + " " + a + " modifier add " + modifierId(s.get("name"), true) + " " + num(dbl(s.get("amount"), 0.2)) + " "
                        + operation(s.get("operation")));
            }
            case "modifier_remove" -> {
                String a = attribute(s.get("attribute"));
                if (a != null) r.add("attribute " + sel(s) + " " + a + " modifier remove " + modifierId(s.get("name"), false));
            }
            case "spectate" -> r.add("execute as " + sel(s) + " run spectate " + or(s.get("entity"), "@e[limit=1,sort=nearest,type=!minecraft:player]") + " @s");
            case "stop_spectate" -> r.add("execute as " + sel(s) + " run spectate");
            case "xp_clear" -> {
                r.add("xp set " + sel(s) + " 0 levels");
                r.add("xp set " + sel(s) + " 0 points");
            }
            case "full_heal" -> r.add("effect give " + sel(s) + " minecraft:instant_health 1 10 true");
            case "summon_named" -> {
                JsonObject t = textObj(or(s.get("name"), "Guide"), s.get("color"));
                r.add("summon " + Ids.full(or(s.get("entity"), "minecraft:villager")) + " " + pos(s) + " {CustomName:" + nbtText(t) + ",CustomNameVisible:1b}");
            }
            case "summon_nbt" -> r.add("summon " + Ids.full(or(s.get("entity"), "minecraft:zombie")) + " " + pos(s) + " " + oneLine(or(s.get("nbt"), "{}")));
            case "entity_data" -> r.add("execute as " + sel(s) + " run data merge entity @s " + oneLine(or(s.get("nbt"), "{}")));
            case "lightning_at" -> r.add("execute at " + sel(s) + " run summon minecraft:lightning_bolt ~ ~ ~");
            case "explode_at" -> {
                for (String c : pc.commands(synthetic("explosion", "power", or(s.get("power"), "2"), "x", "~", "y", "~", "z", "~")))
                    r.add("execute at " + sel(s) + " run " + c);
            }
            case "particle_at" -> r.add("execute at " + sel(s) + " run particle " + particle(s.get("particle")) + " ~ ~1 ~ 0.3 0.5 0.3 0.05 "
                    + Math.max(1, intOr(s.get("count"), 8)));
            case "sound_at" -> r.add("execute at " + sel(s) + " run playsound " + Ids.full(or(s.get("sound"), "minecraft:block.note_block.chime")) + " master @a ~ ~ ~ 1 "
                    + num(Math.max(0.5, Math.min(2, dbl(s.get("pitch"), 1)))));
            case "set_spawn_here" -> r.add("execute as " + sel(s) + " at @s run spawnpoint @s ~ ~ ~");

            // ---------------------------------------------------------- world
            case "setblock_mode" -> {
                String b = blockArg(s.get("block"));
                if (b == null) r.add("# blocks from this project are not available on this platform");
                else r.add("setblock " + pos(s) + " " + b + " " + or(s.get("mode"), "replace"));
            }
            case "destroy_block" -> r.add("setblock " + pos(s) + " minecraft:air destroy");
            case "fill_replace" -> {
                String b = blockArg(s.get("block")), f = blockArg(s.get("filter"));
                if (b == null || f == null) r.add("# blocks from this project are not available on this platform");
                else r.add("fill " + box(s) + " " + b + " replace " + f);
            }
            case "clone_area" -> r.add("clone " + box(s) + " " + coord(s.get("dx")) + " " + coord(s.get("dy")) + " " + coord(s.get("dz")) + " "
                    + or(s.get("mode"), "replace"));
            case "playsound_at" -> r.add("playsound " + Ids.full(or(s.get("sound"), "minecraft:entity.generic.explode")) + " master @a " + pos(s) + " "
                    + num(Math.max(0, dbl(s.get("volume"), 1))) + " " + num(Math.max(0.5, Math.min(2, dbl(s.get("pitch"), 1)))));
            case "play_music" -> {
                r.add("stopsound " + sel(s) + " music");
                r.add("stopsound " + sel(s) + " record");
                r.add("execute as " + sel(s) + " at @s run playsound " + Ids.full(or(s.get("sound"), "minecraft:music_disc.cat")) + " record @s ~ ~ ~ 1 1 1");
            }
            case "particle_circle" -> {
                int n = Math.max(3, Math.min(96, intOr(s.get("points"), 24)));
                double rad = Math.max(0.1, Math.min(64, dbl(s.get("radius"), 2))), dy = dbl(s.get("dy"), 0.2);
                String p = particle(s.get("particle"));
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n;
                    r.add("particle " + p + " ~" + num(Math.cos(a) * rad) + " ~" + num(dy) + " ~" + num(Math.sin(a) * rad) + " 0 0 0 0 1");
                }
            }
            case "particle_burst" -> r.add("particle " + particle(s.get("particle")) + " ~ ~1 ~ 0 0 0 " + num(Math.max(0, dbl(s.get("speed"), 0.5))) + " "
                    + Math.max(1, Math.min(2000, intOr(s.get("count"), 40))) + " force");
            case "particle_column" -> {
                String p = particle(s.get("particle"));
                int h = Math.max(1, Math.min(64, intOr(s.get("height"), 6)));
                for (int i = 0; i <= h * 2; i++) r.add("particle " + p + " ~ ~" + num(i * 0.5) + " ~ 0 0 0 0 1");
            }
            case "firework" -> {
                int rgb;
                try {
                    rgb = Integer.parseInt(or(s.get("rgb"), "#FF3355").replace("#", ""), 16) & 0xFFFFFF;
                } catch (NumberFormatException e) {
                    rgb = 0xFF3355;
                }
                String shape = or(s.get("shape"), "large_ball");
                int flight = Math.max(0, Math.min(3, intOr(s.get("flight"), 1)));
                if (ctx.components()) {
                    r.add("summon minecraft:firework_rocket " + pos(s) + " {LifeTime:" + (10 + flight * 10) + ",FireworksItem:{id:\"minecraft:firework_rocket\",count:1,"
                            + "components:{\"minecraft:fireworks\":{flight_duration:" + flight + "b,explosions:[{shape:\"" + shape + "\",colors:[I;" + rgb + "],has_twinkle:true}]}}}}");
                } else {
                    int type = List.of("small_ball", "large_ball", "star", "creeper", "burst").indexOf(shape);
                    r.add("summon minecraft:firework_rocket " + pos(s) + " {LifeTime:" + (10 + flight * 10) + ",FireworksItem:{id:\"minecraft:firework_rocket\",Count:1b,"
                            + "tag:{Fireworks:{Flight:" + flight + "b,Explosions:[{Type:" + Math.max(0, type) + "b,Colors:[I;" + rgb + "],Flicker:1b}]}}}}");
                }
            }
            case "time_add" -> r.add("time add " + Math.max(0, intOr(s.get("ticks"), 1000)));
            case "time_set" -> r.add("time set " + Math.max(0, intOr(s.get("ticks"), 6000)));
            case "weather_for" -> r.add("weather " + or(s.get("weather"), "rain") + " " + Math.max(1, intOr(s.get("seconds"), 300)) + "s");
            case "world_spawn" -> r.add("setworldspawn " + pos(s));
            case "forceload" -> r.add("forceload " + or(s.get("op"), "add") + " " + coord(s.get("x")) + " " + coord(s.get("z")));
            case "fill_biome" -> r.add("fillbiome " + box(s) + " " + Ids.full(or(s.get("biome"), "minecraft:plains")));
            case "worldborder_center" -> r.add("worldborder center " + coord(s.get("x")) + " " + coord(s.get("z")));
            case "worldborder_damage" -> r.add("worldborder damage amount " + num(Math.max(0, dbl(s.get("amount"), 0.5))));
            case "place_template" -> r.add("place template " + Ids.full(or(s.get("template"), "minecraft:igloo/top")) + " " + pos(s) + " " + or(s.get("rotation"), "none"));
            case "block_display" -> {
                String b = blockArg(s.get("block"));
                if (b == null) {
                    r.add("# blocks from this project are not available on this platform");
                    return true;
                }
                double size = Math.max(0.01, dbl(s.get("size"), 0.5)), off = -size / 2;
                r.add("summon minecraft:block_display " + pos(s) + " {block_state:{Name:\"" + b + "\"},Tags:[\"" + tag(s) + "\"],transformation:{left_rotation:[0f,0f,0f,1f],"
                        + "right_rotation:[0f,0f,0f,1f],translation:[" + num(off) + "f,0f," + num(off) + "f],scale:[" + num(size) + "f," + num(size) + "f," + num(size) + "f]}}");
            }
            case "item_display" -> {
                String item = Ids.full(or(s.get("item"), "minecraft:diamond"));
                double size = Math.max(0.01, dbl(s.get("size"), 1));
                String stack = ctx.components() ? "{id:\"" + item + "\",count:1}" : "{id:\"" + item + "\",Count:1b}";
                r.add("summon minecraft:item_display " + pos(s) + " {item:" + stack + ",billboard:\"vertical\",Tags:[\"" + tag(s) + "\"],transformation:{left_rotation:[0f,0f,0f,1f],"
                        + "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[" + num(size) + "f," + num(size) + "f," + num(size) + "f]}}");
            }
            case "text_display" -> r.add("summon minecraft:text_display " + pos(s) + " {text:" + nbtText(textObj(or(s.get("text"), "Hello"), s.get("color")))
                    + ",billboard:\"" + or(s.get("billboard"), "center") + "\",Tags:[\"" + tag(s) + "\"]}");
            case "remove_tagged" -> r.add("kill @e[tag=" + tag(s) + ",type=!minecraft:player]");

            // ---------------------------------------------------------- score math and storage
            case "score_operation" -> {
                String o = obj(s, "objective"), so = obj(s, "source_objective");
                if (o.isEmpty() || so.isEmpty()) return true;
                r.add("scoreboard players operation " + sel(s) + " " + o + " " + or(s.get("op"), "+=") + " " + or(s.get("source"), "@s") + " " + so);
            }
            case "score_multiply", "score_divide", "score_modulo" -> {
                String o = obj(s, "objective");
                if (o.isEmpty()) return true;
                int v = intOr(s.get("value"), 2);
                if (v == 0 && !s.kind.equals("score_multiply")) {
                    r.add("# division by zero skipped");
                    return true;
                }
                pc.objectives.add(pc.tmp());
                String c = "#c" + v;
                r.add("scoreboard players set " + c + " " + pc.tmp() + " " + v);
                String op = switch (s.kind) {
                    case "score_multiply" -> "*=";
                    case "score_divide" -> "/=";
                    default -> "%=";
                };
                r.add("scoreboard players operation " + sel(s) + " " + o + " " + op + " " + c + " " + pc.tmp());
            }
            case "score_clamp" -> {
                String o = obj(s, "objective");
                if (o.isEmpty()) return true;
                int min = intOr(s.get("min"), 0), max = Math.max(min, intOr(s.get("max"), 100));
                r.add("execute as " + sel(s) + " if score @s " + o + " matches .." + (min - 1) + " run scoreboard players set @s " + o + " " + min);
                r.add("execute as " + sel(s) + " if score @s " + o + " matches " + (max + 1) + ".. run scoreboard players set @s " + o + " " + max);
            }
            case "score_reset" -> {
                String o = s.get("objective").trim();
                r.add("scoreboard players reset " + sel(s) + (o.isEmpty() ? "" : " " + o));
            }
            case "objective_add" -> {
                String o = s.get("objective").trim();
                if (o.isEmpty()) return true;
                r.add("scoreboard objectives add " + o + " " + or(s.get("criteria"), "dummy") + " " + textJson(or(s.get("name"), o), null));
            }
            case "objective_remove" -> {
                if (!s.get("objective").isBlank()) r.add("scoreboard objectives remove " + s.get("objective").trim());
            }
            case "objective_rename" -> {
                String o = obj(s, "objective");
                if (!o.isEmpty()) r.add("scoreboard objectives modify " + o + " displayname " + textJson(or(s.get("name"), o), s.get("color")));
            }
            case "sidebar_clear" -> r.add("scoreboard objectives setdisplay " + or(s.get("slot"), "sidebar"));
            case "real_time" -> {
                String o = obj(s, "objective");
                if (o.isEmpty()) return true;
                if (ctx.target.isMod()) r.add(ScreenGen.command(ctx) + " time " + o);
                else r.add("# the computer's clock needs a mod loader target");
            }
            case "open_screen", "show_overlay", "hide_overlay" -> {
                String name = s.get("screen").trim();
                if (!(ctx.p.byName(ElementType.SCREEN, name) instanceof modstudio.core.Systems.CustomScreen sc)) {
                    r.add("# unknown screen " + name);
                } else if (!ctx.target.isMod()) {
                    r.add("# custom screens need a mod loader target");
                } else if (s.kind.equals("open_screen")) {
                    r.add(ScreenGen.message(ctx, sel(s), sc, sc.overlay() ? "show" : "open"));
                    if (sc.overlay()) r.add("tag " + sel(s) + " add " + ScreenGen.tag(ctx, sc));
                } else {
                    boolean show = s.kind.equals("show_overlay");
                    String t = or(s.get("target"), "@a");
                    r.add("tag " + t + (show ? " add " : " remove ") + ScreenGen.tag(ctx, sc));
                    r.add(ScreenGen.message(ctx, t, sc, show ? "show" : "hide"));
                }
            }
            case "close_screen" -> {
                if (ctx.target.isMod()) r.add(ScreenGen.closeMessage(ctx, sel(s)));
                else r.add("# custom screens need a mod loader target");
            }
            case "score_to_storage" -> {
                String o = obj(s, "objective");
                if (!o.isEmpty()) r.add("execute store result storage " + ns + ":data " + or(s.get("path"), "saved") + " int 1 run scoreboard players get " + single(sel(s)) + " " + o);
            }
            case "storage_to_score" -> {
                String o = obj(s, "objective");
                if (!o.isEmpty()) r.add("execute store result score " + sel(s) + " " + o + " run data get storage " + ns + ":data " + or(s.get("path"), "saved"));
            }
            case "storage_remove" -> r.add("data remove storage " + ns + ":data " + or(s.get("path"), "saved"));
            case "storage_append" -> r.add("data modify storage " + ns + ":data " + or(s.get("path"), "log") + " append value " + snbt(s.get("value")));
            case "data_to_score" -> {
                String o = obj(s, "objective");
                if (!o.isEmpty()) r.add("execute as " + sel(s) + " store result score @s " + o + " run data get entity @s " + or(s.get("path"), "Health") + " "
                        + num(dbl(s.get("factor"), 1)));
            }
            case "count_entities" -> {
                String o = obj(s, "objective");
                if (!o.isEmpty()) r.add("execute store result score " + or(s.get("holder"), "#count") + " " + o + " if entity " + or(s.get("selector"), "@e"));
            }
            case "score_from_xp" -> {
                String o = obj(s, "objective");
                if (!o.isEmpty()) r.add("execute as " + sel(s) + " store result score @s " + o + " run xp query @s levels");
            }
            case "score_from_time" -> {
                String o = obj(s, "objective");
                if (!o.isEmpty()) r.add("execute store result score " + or(s.get("holder"), "#time") + " " + o + " run time query daytime");
            }
            case "score_from_position" -> {
                String o = obj(s, "objective");
                int i = switch (or(s.get("axis"), "y")) {
                    case "x" -> 0;
                    case "z" -> 2;
                    default -> 1;
                };
                if (!o.isEmpty()) r.add("execute as " + sel(s) + " store result score @s " + o + " run data get entity @s Pos[" + i + "]");
            }
            case "score_random" -> {
                for (String c : pc.commands(synthetic("random_score", "target", sel(s), "objective", s.get("objective"), "min", s.get("min"), "max", s.get("max"))))
                    r.add(c);
            }

            // ---------------------------------------------------------- players and progress
            case "advancement_grant", "advancement_revoke" -> r.add("advancement " + (s.kind.endsWith("grant") ? "grant " : "revoke ") + sel(s) + " only "
                    + Ids.full(or(s.get("advancement"), "minecraft:story/root")));
            case "recipe_give", "recipe_take" -> {
                String rc = or(s.get("recipe"), "*");
                r.add("recipe " + (s.kind.endsWith("give") ? "give " : "take ") + sel(s) + " " + (rc.equals("*") ? "*" : Ids.full(rc)));
            }
            case "team_join" -> {
                String t = team(s);
                r.add("team add " + t);
                r.add("team join " + t + " " + sel(s));
            }
            case "team_leave" -> r.add("team leave " + sel(s));
            case "team_color" -> {
                r.add("team add " + team(s));
                r.add("team modify " + team(s) + " color " + or(s.get("color"), "red"));
            }
            case "team_friendly_fire" -> {
                r.add("team add " + team(s));
                r.add("team modify " + team(s) + " friendlyFire " + Boolean.parseBoolean(or(s.get("on"), "false")));
            }
            case "team_prefix" -> {
                r.add("team add " + team(s));
                r.add("team modify " + team(s) + " prefix " + textJson(s.get("prefix"), s.get("color")));
            }
            case "team_nametag" -> {
                r.add("team add " + team(s));
                r.add("team modify " + team(s) + " nametagVisibility " + or(s.get("visibility"), "always"));
            }
            case "trigger_enable" -> {
                String o = obj(s, "objective");
                if (!o.isEmpty()) r.add("scoreboard players enable " + sel(s) + " " + o);
            }

            // ---------------------------------------------------------- logic
            case "return_value" -> r.add(ctx.v.atLeast(McVersion.V1_20_4) ? "return " + intOr(s.get("value"), 1) : "# return needs Minecraft 1.20.2 or later");
            case "return_fail" -> r.add(ctx.v.atLeast(McVersion.V1_20_4) ? "return fail" : "# return fail needs Minecraft 1.20.3 or later");
            default -> {
                return false;
            }
        }
        return true;
    }

    private static String box(Step s) {
        return coord(s.get("x1")) + " " + coord(s.get("y1")) + " " + coord(s.get("z1")) + " " + coord(s.get("x2")) + " " + coord(s.get("y2")) + " " + coord(s.get("z2"));
    }

    private String tag(Step s) {
        return Ids.toId(or(s.get("tag"), "deco")).replace(':', '_');
    }

    private static String team(Step s) {
        return Ids.toId(or(s.get("team"), "red"));
    }

    /** A selector limited to one entity, for commands that read a single value. */
    private static String single(String sel) {
        if (!sel.startsWith("@") || sel.startsWith("@s") || sel.startsWith("@p") || sel.startsWith("@r")) return sel;
        if (sel.length() == 2) return sel + "[limit=1]";
        return sel.contains("limit=") ? sel : sel.substring(0, sel.length() - 1) + ",limit=1]";
    }

    private static String withType(String sel, String type) {
        if (!sel.startsWith("@")) return sel;
        if (sel.length() == 2) return sel + "[type=" + type + "]";
        if (sel.contains("type=")) return sel;
        return sel.substring(0, sel.length() - 1) + ",type=" + type + "]";
    }

    private static String snbt(String v) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) return "\"\"";
        if (t.matches("-?\\d+(\\.\\d+)?[bBsSlLfFdD]?") || t.equals("true") || t.equals("false")) return t;
        if (t.startsWith("{") || t.startsWith("[") || t.startsWith("\"") || t.startsWith("'")) return t;
        return "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ------------------------------------------------------------------ bodies

    /** The execute modifier of an extra body step, or null when the step is not one of them. */
    String bodyModifier(Step s) {
        return switch (s.kind) {
            case "as_entities" -> "as " + or(s.get("selector"), "@e[distance=..16]");
            case "at_entities" -> "at " + or(s.get("selector"), "@e[distance=..16]");
            case "in_dimension" -> "in " + Ids.full(or(s.get("dimension"), "minecraft:overworld"));
            case "facing_entity" -> "facing entity " + or(s.get("selector"), "@p") + " eyes";
            case "on_relation" -> "on " + or(s.get("relation"), "vehicle");
            case "summon_run" -> "summon " + Ids.full(or(s.get("entity"), "minecraft:marker"));
            case "positioned_over" -> "positioned over " + or(s.get("heightmap"), "world_surface");
            case "rotated" -> "rotated " + coord(s.get("yaw")) + " " + coord(s.get("pitch"));
            default -> null;
        };
    }

    // ------------------------------------------------------------------ conditions

    /** Whether the condition is one of the extra ones. */
    static boolean isCondition(String kind) {
        return switch (kind) {
            case "entity_count", "holding_offhand", "wearing", "hand_empty", "on_ground", "gliding", "health", "food", "light", "in_lava", "riding",
                 "has_passenger", "team", "player_name", "storage_exists", "entity_nbt", "score_compare", "time_between", "looking", "facing",
                 "has_effect", "has_advancement", "near_position" -> true;
            default -> false;
        };
    }

    /** Execute sub-command ("if ...") for an extra condition, adding set-up commands to pre; null when unsupported. */
    String condition(Condition c, List<String> pre) {
        String tmp = pc.tmp();
        switch (c.kind) {
            case "entity_count" -> {
                pc.objectives.add(tmp);
                pre.add("execute store result score #count " + tmp + " if entity " + or(c.get("selector"), "@e"));
                return "if score #count " + tmp + " matches " + range(c.get("min"), c.get("max"));
            }
            case "holding_offhand" -> {
                return slotHas("weapon.offhand", -106, c.get("item"));
            }
            case "wearing" -> {
                String slot = or(c.get("slot"), "armor.head");
                int nbtSlot = switch (slot) {
                    case "armor.chest" -> 102;
                    case "armor.legs" -> 101;
                    case "armor.feet" -> 100;
                    default -> 103;
                };
                return slotHas(slot, nbtSlot, c.get("item"));
            }
            case "hand_empty" -> {
                if (ctx.components()) return "unless items entity @s weapon.mainhand *";
                return "unless entity @s[nbt={SelectedItem:{}}]";
            }
            case "on_ground" -> {
                return "if entity @s[nbt={OnGround:1b}]";
            }
            case "gliding" -> {
                return "if entity @s[nbt={FallFlying:1b}]";
            }
            case "health", "food" -> {
                pc.objectives.add(tmp);
                pre.add("execute store result score #v " + tmp + " run data get entity @s " + (c.kind.equals("health") ? "Health" : "foodLevel"));
                return "if score #v " + tmp + " matches " + range(c.get("min"), c.get("max"));
            }
            case "light" -> {
                int min = Math.max(0, intOr(c.get("min"), 0)), max = Math.min(15, Math.max(min, intOr(c.get("max"), 7)));
                String name = "procedures/light_" + min + "_" + max;
                pc.predicates.put(name, J.obj("condition", "minecraft:location_check", "predicate", J.obj("light", J.obj("light", J.obj("min", min, "max", max)))));
                return "if predicate " + ns + ":" + name;
            }
            case "in_lava" -> {
                return "if block ~ ~ ~ minecraft:lava";
            }
            case "riding", "has_passenger" -> {
                pc.objectives.add(tmp);
                pre.add("scoreboard players set #rel " + tmp + " 0");
                pre.add("execute on " + (c.kind.equals("riding") ? "vehicle" : "passengers") + " run scoreboard players set #rel " + tmp + " 1");
                return "if score #rel " + tmp + " matches 1";
            }
            case "team" -> {
                return "if entity @s[team=" + Ids.toId(or(c.get("team"), "red")) + "]";
            }
            case "player_name" -> {
                return "if entity @s[name=" + or(c.get("name"), "Steve").replace(" ", "") + "]";
            }
            case "storage_exists" -> {
                return "if data storage " + ns + ":data " + or(c.get("path"), "saved");
            }
            case "entity_nbt" -> {
                return "if entity @s[nbt=" + oneLine(or(c.get("nbt"), "{}")) + "]";
            }
            case "score_compare" -> {
                String o = c.get("objective").trim(), o2 = c.get("other_objective").trim();
                if (o.isEmpty() || o2.isEmpty()) return null;
                pc.objectives.add(o);
                pc.objectives.add(o2);
                return "if score " + or(c.get("target"), "@s") + " " + o + " " + or(c.get("op"), ">") + " " + or(c.get("other"), "@s") + " " + o2;
            }
            case "time_between" -> {
                int min = Math.max(0, intOr(c.get("min"), 13000)), max = Math.max(min, intOr(c.get("max"), 23000));
                String name = "procedures/time_" + min + "_" + max;
                pc.predicates.put(name, J.obj("condition", "minecraft:time_check", "value", J.obj("min", min, "max", max), "period", 24000));
                return "if predicate " + ns + ":" + name;
            }
            case "looking" -> {
                return "if entity @s[x_rotation=" + intOr(c.get("min"), -90) + ".." + intOr(c.get("max"), -45) + "]";
            }
            case "facing" -> {
                return "if entity @s[y_rotation=" + intOr(c.get("min"), -45) + ".." + intOr(c.get("max"), 45) + "]";
            }
            case "has_effect" -> {
                String e = Ids.full(or(c.get("effect"), "minecraft:speed"));
                String name = "procedures/effect_" + Ids.toId(e.replace(':', '_'));
                pc.predicates.put(name, J.obj("condition", "minecraft:entity_properties", "entity", "this", "predicate", J.obj("effects", J.obj(e, new JsonObject()))));
                return "if predicate " + ns + ":" + name;
            }
            case "has_advancement" -> {
                return "if entity @s[advancements={" + Ids.full(or(c.get("advancement"), "minecraft:story/root")) + "=true}]";
            }
            case "near_position" -> {
                return "if entity @s[x=" + num(dbl(c.get("x"), 0)) + ",y=" + num(dbl(c.get("y"), 64)) + ",z=" + num(dbl(c.get("z"), 0)) + ",distance=.."
                        + num(Math.max(0, dbl(c.get("radius"), 10))) + "]";
            }
            default -> {
                return null;
            }
        }
    }

    private static String range(String min, String max) {
        String a = min == null ? "" : min.trim(), b = max == null ? "" : max.trim();
        if (a.isEmpty() && b.isEmpty()) return "1..";
        if (a.isEmpty()) return ".." + intOr(b, 0);
        if (b.isEmpty()) return intOr(a, 0) + "..";
        return intOr(a, 0) + ".." + Math.max(intOr(a, 0), intOr(b, 0));
    }

    /** Whether an equipment slot holds an item: execute if items from 1.20.5, the Inventory NBT before. */
    private String slotHas(String slot, int nbtSlot, String item) {
        String full = Ids.full(or(item, "minecraft:shield"));
        if (ctx.components()) {
            String arg = full;
            if (pc.customItems() && ctx.p.itemLike(full) instanceof Elements.Item it) arg = ItemSpec.match(ctx, it);
            return "if items entity @s " + slot + " " + arg;
        }
        return "if entity @s[nbt={Inventory:[{Slot:" + nbtSlot + "b,id:\"" + full + "\"}]}]";
    }
}
