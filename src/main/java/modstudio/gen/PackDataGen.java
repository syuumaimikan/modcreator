package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import modstudio.core.Elements;
import modstudio.core.Elements.ItemModifier;
import modstudio.core.Elements.ModifierFunction;
import modstudio.core.Elements.Predicate;
import modstudio.core.Elements.PredicateTerm;
import modstudio.core.Ids;
import modstudio.core.Logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Predicate and item modifier files. The JSON follows the version: biome becomes biomes and item lists become
 * holder sets with 1.20.5, set_lore swaps replace for mode, enchant_with_levels swaps treasure for options with 1.21,
 * and custom items are matched by their custom data instead of an NBT string.
 */
public final class PackDataGen {
    private final GenContext ctx;
    /** true: project items really exist (mod loaders); false: they are custom-model-data items. */
    private final boolean realItems;

    public PackDataGen(GenContext ctx, boolean realItems) {
        this.ctx = ctx;
        this.realItems = realItems;
    }

    public String modifierDir() {
        return ctx.singular() ? "item_modifier" : "item_modifiers";
    }

    // ------------------------------------------------------------------ predicates

    public JsonElement predicate(Predicate pr) {
        List<JsonObject> terms = new ArrayList<>();
        for (PredicateTerm t : pr.terms) {
            JsonObject o = term(t);
            if (o != null) terms.add(o);
        }
        if (terms.isEmpty()) return J.obj("condition", "minecraft:random_chance", "chance", 1);
        if (terms.size() == 1) return terms.get(0);
        return J.obj("condition", pr.combine.equals("any") ? "minecraft:any_of" : "minecraft:all_of", "terms", J.list(terms));
    }

    private JsonObject term(PredicateTerm t) {
        Logic.defaults(Logic.PREDICATE_TERMS.get(t.kind), t.p);
        JsonObject o = switch (t.kind) {
            case "random_chance" -> J.obj("condition", "minecraft:random_chance", "chance", J.num(Math.max(0, Math.min(100, dbl(t.get("percent"), 50))) / 100.0));
            case "weather" -> switch (t.get("weather")) {
                case "thunder" -> J.obj("condition", "minecraft:weather_check", "thundering", true);
                case "clear" -> J.obj("condition", "minecraft:weather_check", "raining", false);
                default -> J.obj("condition", "minecraft:weather_check", "raining", true);
            };
            case "time" -> J.obj("condition", "minecraft:time_check", "value", range(t.get("min"), t.get("max")), "period", 24000);
            case "entity_flag" -> entity(J.obj("flags", J.obj(t.get("flag"), true)));
            case "entity_type" -> entity(J.obj("type", Ids.full(t.get("entity").trim())));
            case "effect" -> entity(J.obj("effects", J.obj(Ids.full(t.get("effect").trim()),
                    J.obj("amplifier", J.obj("min", (int) dbl(t.get("amplifier"), 0))))));
            case "equipment" -> entity(J.obj("equipment", J.obj(t.get("slot"), itemPredicate(t.get("item")))));
            case "score" -> J.obj("condition", "minecraft:entity_scores", "entity", "this",
                    "scores", J.obj(t.get("objective").trim(), range(t.get("min"), t.get("max"))));
            case "player_level" -> entity(J.obj("type_specific", J.obj("type", "player", "level", J.obj("min", (int) dbl(t.get("min"), 0)))));
            case "vehicle" -> entity(J.obj("vehicle", J.obj("type", Ids.full(t.get("entity").trim()))));
            case "biome" -> location(J.obj(ctx.components() ? "biomes" : "biome", Ids.full(t.get("biome").trim())));
            case "dimension" -> location(J.obj("dimension", Ids.full(t.get("dimension").trim())));
            case "block_below" -> J.obj("condition", "minecraft:location_check", "offsetY", -1, "predicate", J.obj("block", blockPredicate(t.get("block"))));
            case "light" -> location(J.obj("light", J.obj("light", range(t.get("min"), t.get("max")))));
            case "y_level" -> location(J.obj("position", J.obj("y", range(t.get("min"), t.get("max")))));
            case "predicate" -> {
                String n = t.get("predicate").trim();
                yield J.obj("condition", "minecraft:reference", "name", n.contains(":") ? n : ctx.ns + ":" + n);
            }
            case "raw" -> raw(t.get("json"));
            default -> null;
        };
        if (o == null) return null;
        return t.invert ? J.obj("condition", "minecraft:inverted", "term", o) : o;
    }

    private static JsonObject entity(JsonObject predicate) {
        return J.obj("condition", "minecraft:entity_properties", "entity", "this", "predicate", predicate);
    }

    private static JsonObject location(JsonObject predicate) {
        return J.obj("condition", "minecraft:location_check", "predicate", predicate);
    }

    /** Number range; blank bounds are left open. */
    static JsonObject range(String min, String max) {
        JsonObject o = new JsonObject();
        if (!min.isBlank()) o.add("min", J.el((int) dbl(min, 0)));
        if (!max.isBlank()) o.add("max", J.el((int) dbl(max, 0)));
        return o;
    }

    private JsonObject blockPredicate(String id) {
        String f = Ids.full(id.trim());
        if (ctx.components()) return J.obj("blocks", f);
        if (Ids.isTag(f)) return J.obj("tag", f.substring(1));
        return J.obj("blocks", J.arr(f));
    }

    /** Item predicate; project items on data packs and Paper match their base item and custom data. */
    JsonObject itemPredicate(String id) {
        String f = Ids.full(id.trim());
        Elements.Item it = !realItems && ctx.p.itemLike(f) instanceof Elements.Item x ? x : null;
        JsonObject o = new JsonObject();
        if (it != null) {
            String base = ItemSpec.base(it);
            if (ctx.components()) {
                o.addProperty("items", base);
                o.add("predicates", J.obj("minecraft:custom_data", ItemSpec.nbtTag(ctx, it)));
            } else {
                o.add("items", J.arr(base));
                o.addProperty("nbt", ItemSpec.nbtTag(ctx, it));
            }
            return o;
        }
        if (ctx.components()) o.addProperty("items", f);
        else if (Ids.isTag(f)) o.addProperty("tag", f.substring(1));
        else o.add("items", J.arr(f));
        return o;
    }

    private static JsonObject raw(String json) {
        try {
            JsonElement e = JsonParser.parseString(json);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    static double dbl(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    // ------------------------------------------------------------------ item modifiers

    public JsonElement modifier(ItemModifier m) {
        List<JsonObject> fns = new ArrayList<>();
        for (ModifierFunction f : m.functions) {
            JsonObject o = function(f);
            if (o != null) fns.add(o);
        }
        if (fns.size() == 1) return fns.get(0);
        // 1.20.5 replaced the plain list with the sequence function
        if (ctx.components()) return J.obj("function", "minecraft:sequence", "functions", J.list(fns));
        return J.list(fns);
    }

    /** Text component with &amp; colour codes, not italic (names and lore are italic by default). */
    static JsonObject text(String text, String color) {
        JsonObject o = J.obj("text", "", "italic", false);
        if (color != null && !color.isBlank() && !color.equals("white")) o.addProperty("color", color);
        JsonArray extra = InterfaceGen.rich(text, null);
        o.add("extra", extra);
        return o;
    }

    private JsonObject function(ModifierFunction f) {
        Logic.defaults(Logic.MODIFIER_FUNCTIONS.get(f.kind), f.p);
        boolean add = Boolean.parseBoolean(f.get("add"));
        return switch (f.kind) {
            case "set_count" -> J.obj("function", "minecraft:set_count", "count", (int) dbl(f.get("count"), 1), "add", add);
            case "set_damage" -> J.obj("function", "minecraft:set_damage", "damage", J.num(Math.max(-1, Math.min(1, dbl(f.get("percent"), 100) / 100.0))), "add", add);
            case "set_name" -> J.obj("function", "minecraft:set_name", "name", text(f.get("text"), f.get("color")));
            case "set_lore" -> {
                JsonArray lore = new JsonArray();
                for (String line : f.get("text").split("\\R")) lore.add(text(line, "gray"));
                boolean replace = f.get("mode").equals("replace");
                if (ctx.components()) yield J.obj("function", "minecraft:set_lore", "lore", lore, "mode", replace ? "replace_all" : "append");
                yield J.obj("function", "minecraft:set_lore", "lore", lore, "replace", replace);
            }
            case "enchant" -> J.obj("function", "minecraft:set_enchantments",
                    "enchantments", J.obj(Ids.full(f.get("enchantment").trim()), Math.max(1, (int) dbl(f.get("level"), 1))), "add", add);
            case "enchant_randomly" -> J.obj("function", "minecraft:enchant_randomly");
            case "enchant_with_levels" -> {
                JsonObject o = J.obj("function", "minecraft:enchant_with_levels", "levels", Math.max(1, (int) dbl(f.get("levels"), 30)));
                boolean treasure = Boolean.parseBoolean(f.get("treasure"));
                if (ctx.singular()) {
                    if (treasure) o.addProperty("options", "#minecraft:on_random_loot");
                } else o.addProperty("treasure", treasure);
                yield o;
            }
            case "unbreakable" -> ctx.components()
                    ? J.obj("function", "minecraft:set_components", "components", J.obj("minecraft:unbreakable", new JsonObject()))
                    : J.obj("function", "minecraft:set_nbt", "tag", "{Unbreakable:1b}");
            case "glint" -> ctx.components()
                    ? J.obj("function", "minecraft:set_components", "components", J.obj("minecraft:enchantment_glint_override", true))
                    : J.obj("function", "minecraft:set_nbt", "tag", "{Enchantments:[{}]}");
            case "potion" -> J.obj("function", "minecraft:set_potion", "id", Ids.full(f.get("potion").trim()));
            case "furnace_smelt" -> J.obj("function", "minecraft:furnace_smelt");
            case "raw" -> raw(f.get("json"));
            default -> null;
        };
    }
}
