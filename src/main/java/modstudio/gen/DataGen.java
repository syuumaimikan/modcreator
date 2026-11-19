package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.*;

/** Java Edition data pack content shared by mod loaders, Paper and data packs. */
public final class DataGen {
    private final GenContext ctx;
    private final String root;
    /** true: project items/blocks really exist (mod loaders). false: items are custom-model-data items. */
    private final boolean realItems;
    /** true when item right-click and commands are hooked natively in code. */
    private final boolean nativeHooks;
    private final Map<String, TagData> tags = new LinkedHashMap<>();

    private static final class TagData {
        boolean replace;
        final LinkedHashSet<String> values = new LinkedHashSet<>();
    }

    public DataGen(GenContext ctx, String root, boolean realItems, boolean nativeHooks) {
        this.ctx = ctx;
        this.root = root;
        this.realItems = realItems;
        this.nativeHooks = nativeHooks;
    }

    private String data(String namespace, String dir, String path) {
        return root + "data/" + namespace + "/" + dir + "/" + path;
    }

    /** Generates everything; recipes can be skipped (Paper registers recipes through the Bukkit API). */
    public void generate(boolean recipes) {
        if (recipes) recipes();
        lootTables();
        predicatesAndModifiers();
        if (realItems) blockData();
        advancedData();
        advancements();
        userTags();
        armorTags();
        functions();
        procedures();
        if (!realItems) giveFunctions();
        writeTags();
    }

    // ---------------------------------------------------------------- items

    private Item projectItem(String id) {
        return ctx.p.itemLike(id) instanceof Item it ? it : null;
    }

    private boolean projectBlock(String id) {
        return ctx.p.itemLike(id) instanceof Block;
    }

    /** Recipe ingredient: an object before 1.21.2, a plain id or #tag string after. */
    public JsonElement ingredient(String id) {
        String f = Ids.full(id);
        if (!realItems && !Ids.isTag(f)) {
            Item it = projectItem(f);
            if (it != null) f = ItemSpec.base(it);
        }
        if (ctx.registryKeys()) return J.el(f);
        if (Ids.isTag(f)) return J.obj("tag", f.substring(1));
        return J.obj("item", f);
    }

    /** Result stack; null if the result cannot exist on this platform. */
    public JsonObject result(String id, int count) {
        String f = Ids.full(id);
        if (!realItems) {
            Item it = projectItem(f);
            if (it != null) {
                if (!ctx.components()) return null;
                return J.obj("id", ItemSpec.base(it), "count", count, "components", ItemSpec.components(ctx, it));
            }
            if (projectBlock(f)) return null;
        }
        return ctx.components() ? J.obj("id", f, "count", count) : J.obj("item", f, "count", count);
    }

    private boolean usesProjectBlock(Recipe r) {
        List<String> all = new ArrayList<>(r.grid);
        all.addAll(List.of(r.input, r.result, r.template, r.base, r.addition));
        for (String s : all) if (!s.isBlank() && projectBlock(Ids.full(s))) return true;
        return false;
    }

    // ---------------------------------------------------------------- recipes

    public void recipes() {
        for (Recipe r : ctx.all(Recipe.class)) {
            if (r.result.isBlank()) continue;
            if (!realItems && usesProjectBlock(r)) {
                ctx.note("recipe " + r.name + ": blocks from this project are not available on " + ctx.target.id);
                continue;
            }
            JsonObject json = recipe(r);
            if (json == null) {
                ctx.note("recipe " + r.name + ": custom item results require Minecraft 1.20.5+ on " + ctx.target.id);
                continue;
            }
            ctx.files.json(data(ctx.ns, ctx.recipeDir(), r.name + ".json"), json);
        }
    }

    public JsonObject recipe(Recipe r) {
        JsonObject o = new JsonObject();
        switch (r.recipeType) {
            case "shaped" -> {
                o.addProperty("type", "minecraft:crafting_shaped");
                o.addProperty("category", "misc");
                if (!r.group.isBlank()) o.addProperty("group", r.group);
                Shape sh = Shape.of(r.grid);
                if (sh.rows.isEmpty()) return null;
                o.add("pattern", J.list(sh.rows));
                JsonObject key = new JsonObject();
                for (var e : sh.keys.entrySet()) key.add(String.valueOf(e.getValue()), ingredient(e.getKey()));
                o.add("key", key);
                JsonObject res = result(r.result, r.count);
                if (res == null) return null;
                applyResultComponents(res, r);
                o.add("result", res);
            }
            case "shapeless" -> {
                o.addProperty("type", "minecraft:crafting_shapeless");
                o.addProperty("category", "misc");
                if (!r.group.isBlank()) o.addProperty("group", r.group);
                JsonArray ing = new JsonArray();
                for (String s : r.grid) if (!s.isBlank()) ing.add(ingredient(s));
                if (ing.isEmpty()) return null;
                o.add("ingredients", ing);
                JsonObject res = result(r.result, r.count);
                if (res == null) return null;
                applyResultComponents(res, r);
                o.add("result", res);
            }
            case "smelting", "blasting", "smoking", "campfire" -> {
                o.addProperty("type", "minecraft:" + (r.recipeType.equals("campfire") ? "campfire_cooking" : r.recipeType));
                o.addProperty("category", "misc");
                if (!r.group.isBlank()) o.addProperty("group", r.group);
                if (r.input.isBlank()) return null;
                o.add("ingredient", ingredient(r.input));
                JsonObject res = result(r.result, 1);
                if (res == null) return null;
                if (ctx.components()) {
                    res.remove("count");
                    applyResultComponents(res, r);
                    o.add("result", res);
                } else o.addProperty("result", Ids.full(r.result));
                o.addProperty("experience", r.experience);
                o.addProperty("cookingtime", r.cookTime);
            }
            case "stonecutting" -> {
                o.addProperty("type", "minecraft:stonecutting");
                if (r.input.isBlank()) return null;
                o.add("ingredient", ingredient(r.input));
                JsonObject res = result(r.result, r.count);
                if (res == null) return null;
                if (ctx.components()) {
                    applyResultComponents(res, r);
                    o.add("result", res);
                } else {
                    o.addProperty("result", Ids.full(r.result));
                    o.addProperty("count", r.count);
                }
            }
            case "smithing" -> {
                o.addProperty("type", "minecraft:smithing_transform");
                if (r.template.isBlank() || r.base.isBlank() || r.addition.isBlank()) return null;
                o.add("template", ingredient(r.template));
                o.add("base", ingredient(r.base));
                o.add("addition", ingredient(r.addition));
                JsonObject res = result(r.result, 1);
                if (res == null) return null;
                if (ctx.components()) applyResultComponents(res, r);
                else res.remove("count");
                o.add("result", res);
            }
            default -> {
                return null;
            }
        }
        return o;
    }

    private void applyResultComponents(JsonObject res, Recipe r) {
        if (!ctx.components() || r.resultComponents == null || r.resultComponents.isBlank()) return;
        try {
            JsonElement parsed = JsonParser.parseString(r.resultComponents.trim());
            if (!parsed.isJsonObject()) return;
            JsonObject comp = res.has("components") && res.get("components").isJsonObject()
                    ? res.getAsJsonObject("components") : new JsonObject();
            for (var e : parsed.getAsJsonObject().entrySet()) comp.add(e.getKey(), e.getValue());
            res.add("components", comp);
        } catch (Exception ignored) {
        }
    }

    /** Trimmed shaped pattern with key letters. */
    public static final class Shape {
        public final List<String> rows = new ArrayList<>();
        public final LinkedHashMap<String, Character> keys = new LinkedHashMap<>();

        public static Shape of(List<String> grid) {
            Shape s = new Shape();
            int minR = 3, maxR = -1, minC = 3, maxC = -1;
            for (int i = 0; i < 9; i++) {
                if (i < grid.size() && !grid.get(i).isBlank()) {
                    int r = i / 3, c = i % 3;
                    minR = Math.min(minR, r);
                    maxR = Math.max(maxR, r);
                    minC = Math.min(minC, c);
                    maxC = Math.max(maxC, c);
                }
            }
            if (maxR < 0) return s;
            String letters = "ABCDEFGHI";
            for (int r = minR; r <= maxR; r++) {
                StringBuilder sb = new StringBuilder();
                for (int c = minC; c <= maxC; c++) {
                    String id = grid.get(r * 3 + c);
                    if (id.isBlank()) sb.append(' ');
                    else {
                        String full = Ids.full(id);
                        Character k = s.keys.get(full);
                        if (k == null) {
                            k = letters.charAt(s.keys.size());
                            s.keys.put(full, k);
                        }
                        sb.append(k);
                    }
                }
                s.rows.add(sb.toString());
            }
            return s;
        }
    }

    // ---------------------------------------------------------------- loot tables

    private JsonObject countFn(int min, int max) {
        Object count = min == max ? (Object) min : J.obj("type", "minecraft:uniform", "min", min, "max", max);
        return J.obj("function", "minecraft:set_count", "count", count);
    }

    private JsonObject silkTouch() {
        if (ctx.components()) {
            return J.obj("condition", "minecraft:match_tool", "predicate", J.obj("predicates", J.obj("minecraft:enchantments",
                    J.arr(J.obj("enchantments", "minecraft:silk_touch", "levels", J.obj("min", 1))))));
        }
        return J.obj("condition", "minecraft:match_tool", "predicate", J.obj("enchantments",
                J.arr(J.obj("enchantment", "minecraft:silk_touch", "levels", J.obj("min", 1)))));
    }

    /** Loot entry for an item id, converting project items on custom-item platforms. */
    private JsonObject itemEntry(String id, int weight, List<JsonObject> functions) {
        String f = Ids.full(id);
        JsonObject e = J.obj("type", "minecraft:item", "name", f);
        List<JsonObject> fns = new ArrayList<>(functions);
        if (!realItems) {
            Item it = projectItem(f);
            if (it != null) {
                e.addProperty("name", ItemSpec.base(it));
                if (ctx.components()) fns.add(0, J.obj("function", "minecraft:set_components", "components", ItemSpec.components(ctx, it)));
                else {
                    String give = ItemSpec.give(ctx, it);
                    fns.add(0, J.obj("function", "minecraft:set_nbt", "tag", give.substring(ItemSpec.base(it).length())));
                }
            }
        }
        if (weight != 1) e.addProperty("weight", weight);
        if (!fns.isEmpty()) e.add("functions", J.list(fns));
        return e;
    }

    public JsonObject lootTable(LootTable lt) {
        JsonArray pools = new JsonArray();
        for (LootPool pool : lt.pools) {
            JsonObject po = new JsonObject();
            po.add("rolls", pool.rollsMin == pool.rollsMax ? J.el(pool.rollsMin)
                    : J.obj("type", "minecraft:uniform", "min", pool.rollsMin, "max", pool.rollsMax));
            po.addProperty("bonus_rolls", J.num(pool.bonusRolls));
            JsonArray entries = new JsonArray();
            for (LootEntry en : pool.entries) {
                switch (en.kind) {
                    case "empty" -> entries.add(J.obj("type", "minecraft:empty", "weight", en.weight));
                    case "tag" -> {
                        if (en.name.isBlank()) continue;
                        entries.add(J.obj("type", "minecraft:tag", "name", Ids.full(en.name.replace("#", "")), "expand", true, "weight", en.weight));
                    }
                    case "loot_table" -> {
                        if (en.name.isBlank()) continue;
                        String ref = en.name.contains(":") ? en.name : ctx.ns + ":" + en.name;
                        entries.add(J.obj("type", "minecraft:loot_table", ctx.components() ? "value" : "name", ref, "weight", en.weight));
                    }
                    default -> {
                        if (en.name.isBlank()) continue;
                        if (!realItems && projectBlock(Ids.full(en.name))) continue;
                        List<JsonObject> fns = new ArrayList<>();
                        if (en.countMin != 1 || en.countMax != 1) fns.add(countFn(en.countMin, Math.max(en.countMin, en.countMax)));
                        if (en.fortune) fns.add(J.obj("function", "minecraft:apply_bonus", "enchantment", "minecraft:fortune", "formula", "minecraft:ore_drops"));
                        if (en.enchant) fns.add(J.obj("function", "minecraft:enchant_randomly"));
                        if (en.enchantLevelsMax > 0) {
                            fns.add(J.obj("function", "minecraft:enchant_with_levels", "levels", J.obj("type", "minecraft:uniform",
                                    "min", Math.max(1, en.enchantLevelsMin), "max", Math.max(Math.max(1, en.enchantLevelsMin), en.enchantLevelsMax))));
                        }
                        if (en.lootingBonus > 0) {
                            Object bonus = J.obj("type", "minecraft:uniform", "min", 0, "max", en.lootingBonus);
                            fns.add(ctx.singular()
                                    ? J.obj("function", "minecraft:enchanted_count_increase", "enchantment", "minecraft:looting", "count", bonus)
                                    : J.obj("function", "minecraft:looting_enchant", "count", bonus));
                        }
                        if (en.explosionDecay) fns.add(J.obj("function", "minecraft:explosion_decay"));
                        if (!en.customName.isBlank()) {
                            fns.add(J.obj("function", "minecraft:set_name", "name", J.obj("text", en.customName, "italic", false)));
                        }
                        if (!en.lore.isBlank()) {
                            JsonArray lines = new JsonArray();
                            for (String line : en.lore.split("\\R")) lines.add(J.obj("text", line));
                            fns.add(J.obj("function", "minecraft:set_lore", "lore", lines, "mode", ctx.components() ? "replace_all" : null));
                            if (!ctx.components()) fns.get(fns.size() - 1).remove("mode");
                        }
                        entries.add(itemEntry(en.name, en.weight, fns));
                    }
                }
            }
            po.add("entries", entries);
            JsonArray conds = new JsonArray();
            if (pool.chance < 1.0) conds.add(J.obj("condition", "minecraft:random_chance", "chance", pool.chance));
            if (pool.killedByPlayer) conds.add(J.obj("condition", "minecraft:killed_by_player"));
            if (!pool.predicate.isBlank()) {
                String pr = pool.predicate.trim();
                conds.add(J.obj("condition", "minecraft:reference", "name", pr.contains(":") ? pr : ctx.ns + ":" + pr));
            }
            if (!pool.blockStateBlock.isBlank() && !pool.blockStateProp.isBlank()) {
                conds.add(J.obj("condition", "minecraft:block_state_property", "block", Ids.full(pool.blockStateBlock),
                        "properties", J.obj(pool.blockStateProp.trim(), pool.blockStateVal.trim())));
            }
            if (!conds.isEmpty()) po.add("conditions", conds);
            pools.add(po);
        }
        return J.obj("type", "minecraft:" + lt.lootType, "pools", pools);
    }

    public String lootPath(LootTable lt) {
        if (lt.inject && !lt.replaces.isBlank()) return data(ctx.ns, ctx.lootDir(), "inject/" + lt.name + ".json");
        if (!lt.replaces.isBlank()) {
            String r = Ids.full(lt.replaces);
            return data(Ids.ns(r), ctx.lootDir(), Ids.path(r) + ".json");
        }
        return data(ctx.ns, ctx.lootDir(), lt.name + ".json");
    }

    private void lootTables() {
        for (LootTable lt : ctx.all(LootTable.class)) ctx.files.json(lootPath(lt), lootTable(lt));
    }

    private void predicatesAndModifiers() {
        PackDataGen g = new PackDataGen(ctx, realItems);
        for (Predicate pr : ctx.all(Predicate.class)) ctx.files.json(data(ctx.ns, ctx.predicateDir(), pr.name + ".json"), g.predicate(pr));
        for (ItemModifier m : ctx.all(ItemModifier.class)) ctx.files.json(data(ctx.ns, g.modifierDir(), m.name + ".json"), g.modifier(m));
    }

    // ---------------------------------------------------------------- blocks

    private void blockData() {
        for (Block b : ctx.all(Block.class)) {
            String id = ctx.id(b.name);
            if (ctx.p.byName(ElementType.LOOT_TABLE, "blocks/" + b.name) == null) {
                ctx.files.json(data(ctx.ns, ctx.lootDir(), "blocks/" + b.name + ".json"), blockLoot(b, id));
            }
            if (b.isCrop()) {
                tag("block", "minecraft", "crops").values.add(id);
                tag("block", "minecraft", "bee_growables").values.add(id);
                tag("block", "minecraft", "maintains_farmland").values.add(id);
                tag("item", "minecraft", "villager_plantable_seeds").values.add(id);
            } else {
                String shapeTag = switch (b.shape) {
                    case "slab" -> "slabs";
                    case "stairs" -> "stairs";
                    case "fence" -> "fences";
                    case "wall" -> "walls";
                    case "door" -> "doors";
                    case "trapdoor" -> "trapdoors";
                    case "button" -> "buttons";
                    case "pressure_plate" -> "pressure_plates";
                    case "carpet" -> "wool_carpets";
                    default -> "";
                };
                if (!shapeTag.isEmpty()) {
                    tag("block", "minecraft", shapeTag).values.add(id);
                    tag("item", "minecraft", shapeTag).values.add(id);
                }
                if (!b.tool.equals("none")) {
                    tag("block", "minecraft", "mineable/" + b.tool).values.add(id);
                    switch (b.toolLevel) {
                        case "stone" -> tag("block", "minecraft", "needs_stone_tool").values.add(id);
                        case "iron" -> tag("block", "minecraft", "needs_iron_tool").values.add(id);
                        case "diamond" -> tag("block", "minecraft", "needs_diamond_tool").values.add(id);
                        default -> {
                        }
                    }
                }
            }
            if (b.oreGen) oreFeatures(b);
        }
        for (Fluid fl : ctx.all(Fluid.class)) {
            String tagName = fl.isLavaLike ? "lava" : "water";
            tag("fluid", "minecraft", tagName).values.add(ctx.id(fl.name));
            tag("fluid", "minecraft", tagName).values.add(ctx.id(fl.name + "_flowing"));
        }
        for (ModElements.Container ct : ctx.all(ModElements.Container.class)) {
            String bn = ct.block.isBlank() ? ct.name : Ids.path(Ids.full(ct.block));
            if (ctx.all(Block.class).stream().anyMatch(b -> b.name.equals(bn))) continue;
            String id = ctx.id(bn);
            if (ctx.p.byName(ElementType.LOOT_TABLE, "blocks/" + bn) == null) {
                ctx.files.json(data(ctx.ns, ctx.lootDir(), "blocks/" + bn + ".json"),
                        J.obj("type", "minecraft:block", "pools", J.arr(J.obj("rolls", 1, "bonus_rolls", 0,
                                "entries", J.arr(J.obj("type", "minecraft:item", "name", id)),
                                "conditions", J.arr(J.obj("condition", "minecraft:survives_explosion"))))));
            }
            tag("block", "minecraft", "mineable/pickaxe").values.add(id);
        }
    }

    private JsonObject blockLoot(Block b, String id) {
        if (b.isCrop()) return cropLoot(b, id);
        JsonObject survives = J.obj("condition", "minecraft:survives_explosion");
        switch (b.drop) {
            case "none" -> {
                return J.obj("type", "minecraft:block", "pools", new JsonArray());
            }
            case "item" -> {
                if (b.dropItem.isBlank()) break;
                List<JsonObject> fns = new ArrayList<>();
                if (b.dropMin != 1 || b.dropMax != 1) fns.add(countFn(b.dropMin, Math.max(b.dropMin, b.dropMax)));
                if (b.fortune) fns.add(J.obj("function", "minecraft:apply_bonus", "enchantment", "minecraft:fortune", "formula", "minecraft:ore_drops"));
                fns.add(J.obj("function", "minecraft:explosion_decay"));
                JsonObject drop = J.obj("type", "minecraft:item", "name", Ids.full(b.dropItem), "functions", J.list(fns));
                JsonObject entry;
                if (b.silkTouch) {
                    JsonObject self = J.obj("type", "minecraft:item", "name", id, "conditions", J.arr(silkTouch()));
                    entry = J.obj("type", "minecraft:alternatives", "children", J.arr(self, drop));
                } else entry = drop;
                return J.obj("type", "minecraft:block", "pools", J.arr(J.obj("rolls", 1, "bonus_rolls", 0, "entries", J.arr(entry))));
            }
            default -> {
            }
        }
        if (b.shape.equals("slab")) {
            JsonObject entry = J.obj("type", "minecraft:item", "name", id, "functions", J.arr(
                    J.obj("function", "minecraft:set_count", "count", 2, "conditions", J.arr(
                            J.obj("condition", "minecraft:block_state_property", "block", id, "properties", J.obj("type", "double")))),
                    J.obj("function", "minecraft:explosion_decay")));
            return J.obj("type", "minecraft:block", "pools", J.arr(J.obj("rolls", 1, "bonus_rolls", 0, "entries", J.arr(entry))));
        }
        if (b.shape.equals("door")) {
            JsonObject cond = J.obj("condition", "minecraft:block_state_property", "block", id, "properties", J.obj("half", "lower"));
            return J.obj("type", "minecraft:block", "pools", J.arr(J.obj("rolls", 1, "bonus_rolls", 0,
                    "entries", J.arr(J.obj("type", "minecraft:item", "name", id, "conditions", J.arr(cond))),
                    "conditions", J.arr(survives))));
        }
        return J.obj("type", "minecraft:block", "pools", J.arr(J.obj("rolls", 1, "bonus_rolls", 0,
                "entries", J.arr(J.obj("type", "minecraft:item", "name", id)), "conditions", J.arr(survives))));
    }

    /** Seeds always; when ripe the produce and, optionally, bonus seeds (like wheat). */
    private JsonObject cropLoot(Block b, String id) {
        JsonObject ripe = J.obj("condition", "minecraft:block_state_property", "block", id, "properties", J.obj("age", "7"));
        String produce = b.cropProduce.isBlank() ? id : Ids.full(b.cropProduce);
        int min = Math.max(0, b.cropProduceMin), max = Math.max(min, b.cropProduceMax);
        JsonObject produceEntry = J.obj("type", "minecraft:item", "name", produce, "conditions", J.arr(ripe));
        if (min != 1 || max != 1) produceEntry.add("functions", J.arr(countFn(min, max)));
        JsonObject seedEntry = J.obj("type", "minecraft:item", "name", id);
        JsonArray pools = J.arr(J.obj("rolls", 1, "bonus_rolls", 0,
                "entries", J.arr(J.obj("type", "minecraft:alternatives", "children", J.arr(produceEntry, seedEntry)))));
        if (b.cropSeedBonus) {
            pools.add(J.obj("rolls", 1, "bonus_rolls", 0, "conditions", J.arr(ripe.deepCopy()),
                    "entries", J.arr(J.obj("type", "minecraft:item", "name", id, "functions", J.arr(J.obj(
                            "function", "minecraft:apply_bonus", "enchantment", "minecraft:fortune",
                            "formula", "minecraft:binomial_with_bonus_count", "parameters", J.obj("extra", 3, "probability", 0.5714286)))))));
        }
        return J.obj("type", "minecraft:block", "pools", pools, "functions", J.arr(J.obj("function", "minecraft:explosion_decay")));
    }

    /** Name of the configured and placed ore feature; avoids names like ruby_ore_ore. */
    public static String oreFeature(Block b) {
        return b.name.endsWith("_ore") ? b.name : b.name + "_ore";
    }

    private void oreFeatures(Block b) {
        String id = ctx.id(b.name);
        String feature = oreFeature(b);
        JsonArray targets = new JsonArray();
        if (b.oreReplaceTarget != null && !b.oreReplaceTarget.isBlank()) {
            for (String raw : b.oreReplaceTarget.split(",")) {
                String t = Ids.full(raw.trim());
                if (t.isBlank()) continue;
                JsonObject rule = Ids.isTag(t)
                        ? J.obj("predicate_type", "minecraft:tag_match", "tag", t.substring(1))
                        : J.obj("predicate_type", "minecraft:block_match", "block", t);
                targets.add(J.obj("state", J.obj("Name", id), "target", rule));
            }
        }
        if (targets.isEmpty()) {
            switch (b.oreDimension) {
                case "nether" -> targets.add(J.obj("state", J.obj("Name", id), "target",
                        J.obj("predicate_type", "minecraft:tag_match", "tag", "minecraft:base_stone_nether")));
                case "end" -> targets.add(J.obj("state", J.obj("Name", id), "target",
                        J.obj("predicate_type", "minecraft:block_match", "block", "minecraft:end_stone")));
                default -> {
                    targets.add(J.obj("state", J.obj("Name", id), "target",
                            J.obj("predicate_type", "minecraft:tag_match", "tag", "minecraft:stone_ore_replaceables")));
                    targets.add(J.obj("state", J.obj("Name", id), "target",
                            J.obj("predicate_type", "minecraft:tag_match", "tag", "minecraft:deepslate_ore_replaceables")));
                }
            }
        }
        double discardAir = Math.max(0.0, Math.min(1.0, b.oreDiscardAir));
        ctx.files.json(data(ctx.ns, "worldgen/configured_feature", feature + ".json"), J.obj("type", "minecraft:ore",
                "config", J.obj("discard_chance_on_air_exposure", discardAir, "size", b.oreSize, "targets", targets)));
        String heightType = "uniform".equals(b.oreDistribution) ? "minecraft:uniform" : "minecraft:trapezoid";
        JsonArray placement = J.arr(
                J.obj("type", "minecraft:count", "count", b.oreCount),
                J.obj("type", "minecraft:in_square"),
                J.obj("type", "minecraft:height_range", "height", J.obj("type", heightType,
                        "min_inclusive", J.obj("absolute", Math.min(b.oreMinY, b.oreMaxY)),
                        "max_inclusive", J.obj("absolute", Math.max(b.oreMinY, b.oreMaxY)))));
        if (b.orePreventSurface && !"nether".equals(b.oreDimension) && !"end".equals(b.oreDimension)) {
            placement.add(J.obj("type", "minecraft:surface_relative_threshold_filter",
                    "heightmap", "OCEAN_FLOOR_WG",
                    "max_inclusive", -Math.max(1, b.oreSurfaceDepth)));
        }
        placement.add(J.obj("type", "minecraft:biome"));
        ctx.files.json(data(ctx.ns, "worldgen/placed_feature", feature + ".json"), J.obj(
                "feature", ctx.id(feature),
                "placement", placement));
    }

    public static String biomeTag(Block b) {
        if (b.oreBiomes != null && !b.oreBiomes.isBlank()) {
            for (String raw : b.oreBiomes.split(",")) {
                String t = Ids.full(raw.trim());
                if (!t.isBlank()) return t;
            }
        }
        return switch (b.oreDimension) {
            case "nether" -> "#minecraft:is_nether";
            case "end" -> "#minecraft:is_end";
            default -> "#minecraft:is_overworld";
        };
    }

    public static List<String> biomeList(Block b) {
        List<String> out = new ArrayList<>();
        if (b.oreBiomes != null && !b.oreBiomes.isBlank()) {
            for (String raw : b.oreBiomes.split(",")) {
                String t = Ids.full(raw.trim());
                if (!t.isBlank()) out.add(t);
            }
        }
        if (out.isEmpty()) out.add(biomeTag(b));
        return out;
    }

    // ---------------------------------------------------------------- advancements

    public JsonObject icon(String id) {
        String f = Ids.full(id.isBlank() ? "minecraft:stone" : id);
        if (!realItems) {
            Item it = projectItem(f);
            if (it != null) {
                if (ctx.components()) return J.obj("id", ItemSpec.base(it), "components", ItemSpec.modelComponent(ctx, it));
                return J.obj("item", ItemSpec.base(it), "nbt", "{CustomModelData:" + ItemSpec.modelData(it) + "}");
            }
            if (projectBlock(f)) f = "minecraft:stone";
        }
        return ctx.components() ? J.obj("id", f) : J.obj("item", f);
    }

    private JsonObject itemPredicate(String id) {
        String f = Ids.full(id);
        if (Ids.isTag(f)) return ctx.components() ? J.obj("items", f) : J.obj("tag", f.substring(1));
        if (!realItems) {
            Item it = projectItem(f);
            if (it != null) {
                if (ctx.components()) return J.obj("items", ItemSpec.base(it), "predicates",
                        J.obj("minecraft:custom_data", J.obj("PublicBukkitValues", J.obj(ctx.ns + ":item_id", it.name))));
                return J.obj("items", J.arr(ItemSpec.base(it)), "nbt", ItemSpec.nbtTag(ctx, it));
            }
        }
        return J.obj("items", J.arr(f));
    }

    private JsonArray entityConditions(String entity) {
        if (entity == null || entity.isBlank()) return new JsonArray();
        return J.arr(J.obj("condition", "minecraft:entity_properties", "entity", "this", "predicate", J.obj("type", Ids.full(entity))));
    }

    /** Criterion conditions for an advancement trigger. */
    public JsonObject triggerConditions(String trigger, String value) {
        boolean has = value != null && !value.isBlank();
        String v = has ? Ids.full(value) : "";
        switch (trigger) {
            case "inventory_changed" -> {
                return has ? J.obj("items", J.arr(itemPredicate(v))) : new JsonObject();
            }
            case "placed_block" -> {
                // 1.20 (23w18a) replaced block/state/item with a list of loot conditions in "location"
                if (!has) return new JsonObject();
                return J.obj("location", J.arr(J.obj("condition", "minecraft:block_state_property", "block", v)));
            }
            case "consume_item", "enchanted_item", "filled_bucket" -> {
                return has ? J.obj("item", itemPredicate(v)) : new JsonObject();
            }
            case "player_killed_entity", "player_hurt_entity", "tame_animal", "player_interacted_with_entity" -> {
                return has ? J.obj("entity", entityConditions(v)) : new JsonObject();
            }
            case "changed_dimension" -> {
                return has ? J.obj("to", v) : new JsonObject();
            }
            case "enter_block" -> {
                return has ? J.obj("block", v) : new JsonObject();
            }
            case "item_used_on_block" -> {
                if (!has) return new JsonObject();
                return J.obj("location", J.arr(J.obj("condition", "minecraft:match_tool", "predicate", itemPredicate(v))));
            }
            case "recipe_unlocked" -> {
                return has ? J.obj("recipe", v) : new JsonObject();
            }
            default -> {
                return new JsonObject();
            }
        }
    }

    public JsonObject advancement(Advancement a) {
        JsonObject o = new JsonObject();
        String parent = a.parent.trim();
        if (!parent.isEmpty()) o.addProperty("parent", parent.contains(":") ? parent : ctx.ns + ":" + parent);
        String key = "advancements." + ctx.ns + "." + a.name.replace('/', '.');
        JsonObject display = J.obj(
                "icon", icon(a.icon),
                "title", J.obj("translate", key + ".title", "fallback", a.title.isBlank() ? Ids.titleCase(a.name) : a.title),
                "description", J.obj("translate", key + ".description", "fallback", a.description),
                "frame", a.frame,
                "show_toast", a.showToast,
                "announce_to_chat", a.announce,
                "hidden", a.hidden);
        if (parent.isEmpty() && !a.background.isBlank()) display.addProperty("background", a.background);
        o.add("display", display);
        String trig = "sleeping_in_bed".equals(a.trigger) ? "slept_in_bed" : a.trigger;
        o.add("criteria", J.obj("main", J.obj("trigger", "minecraft:" + trig, "conditions", triggerConditions(trig, a.triggerValue))));
        JsonObject rewards = new JsonObject();
        if (a.rewardXp > 0) rewards.addProperty("experience", a.rewardXp);
        if (!a.rewardFunction.isBlank()) rewards.addProperty("function", a.rewardFunction.contains(":") ? a.rewardFunction : ctx.ns + ":" + a.rewardFunction);
        if (!a.rewardLoot.isBlank()) rewards.add("loot", J.arr(a.rewardLoot.contains(":") ? a.rewardLoot : ctx.ns + ":" + a.rewardLoot));
        if (!rewards.isEmpty()) o.add("rewards", rewards);
        return o;
    }

    private void advancements() {
        for (Advancement a : ctx.all(Advancement.class)) {
            ctx.files.json(data(ctx.ns, ctx.advDir(), a.name + ".json"), advancement(a));
        }
    }

    // ---------------------------------------------------------------- tags

    private TagData tag(String registry, String namespace, String path) {
        return tags.computeIfAbsent(registry + "|" + namespace + "|" + path, k -> new TagData());
    }

    private void userTags() {
        for (Tag t : ctx.all(Tag.class)) {
            String nsName = t.namespace.isBlank() ? ctx.ns : t.namespace.trim();
            TagData d = tag(t.registry, nsName, t.name);
            d.replace |= t.replace;
            for (String v : t.values) {
                if (v.isBlank()) continue;
                String f = Ids.full(v);
                if (!realItems && !t.registry.equals("function") && ctx.p.itemLike(f.replace("#", "")) != null) {
                    ctx.note("tag " + t.name + ": " + f + " is not a real item on " + ctx.target.id);
                    continue;
                }
                d.values.add(f);
            }
        }
    }

    /** Armor/tool material repair tags, custom item repair tags, and 1.20.6+ incorrect-for-tool block tags. */
    private void armorTags() {
        if (!realItems) return;
        if (ctx.components()) {
            for (ToolMaterial tm : ctx.all(ToolMaterial.class)) {
                String parentTag = switch (tm.miningLevel) {
                    case 0 -> "#minecraft:incorrect_for_wooden_tool";
                    case 1 -> "#minecraft:incorrect_for_stone_tool";
                    case 3 -> "#minecraft:incorrect_for_diamond_tool";
                    case 4 -> "#minecraft:incorrect_for_netherite_tool";
                    default -> "#minecraft:incorrect_for_iron_tool";
                };
                tag("block", ctx.ns, "incorrect_for_" + tm.name + "_tool").values.add(parentTag);
                if (ctx.registryKeys() && !tm.repairItem.isBlank()) {
                    tag("item", ctx.ns, "repairs_" + tm.name + "_tool").values.add(Ids.full(tm.repairItem));
                }
            }
        }
        if (!ctx.registryKeys()) return;
        for (ArmorMaterial m : ctx.all(ArmorMaterial.class)) {
            if (m.repairItem.isBlank()) continue;
            tag("item", ctx.ns, "repairs_" + m.name).values.add(Ids.full(m.repairItem));
        }
        for (Item it : ctx.all(Item.class)) {
            if (it.repairItem.isBlank()) continue;
            String rep = Ids.full(it.repairItem.trim());
            if (!Ids.isTag(rep) && !Ids.ns(rep).equals("minecraft")) {
                tag("item", ctx.ns, "repair_" + it.name).values.add(rep);
            }
        }
    }

    private void writeTags() {
        for (var e : tags.entrySet()) {
            String[] k = e.getKey().split("\\|", 3);
            TagData d = e.getValue();
            if (d.values.isEmpty() && !d.replace) continue;
            ctx.files.json(data(k[1], "tags/" + ctx.tagDir(k[0]), k[2] + ".json"), J.obj("replace", d.replace, "values", J.list(d.values)));
        }
    }

    // ---------------------------------------------------------------- functions & procedures

    private String fn(String path) {
        return data(ctx.ns, ctx.funcDir(), path + ".mcfunction");
    }

    private static String cleanFunction(String code) {
        StringBuilder sb = new StringBuilder();
        for (String line : code.split("\\R", -1)) {
            String l = line.stripLeading();
            if (l.startsWith("/")) l = l.substring(1);
            sb.append(l).append('\n');
        }
        return sb.toString().replaceAll("\n+$", "\n");
    }

    private void functions() {
        for (Function f : ctx.all(Function.class)) {
            ctx.files.text(fn(f.name), cleanFunction(f.code));
            if (f.onLoad) tag("function", "minecraft", "load").values.add(ctx.id(f.name));
            if (f.onTick) tag("function", "minecraft", "tick").values.add(ctx.id(f.name));
        }
    }

    private static final Map<String, String> ADV_TRIGGERS = Map.ofEntries(
            Map.entry("block_place", "placed_block"),
            Map.entry("item_eat", "consume_item"),
            Map.entry("kill_entity", "player_killed_entity"),
            Map.entry("hurt_entity", "player_hurt_entity"),
            Map.entry("player_hurt", "entity_hurt_player"),
            Map.entry("item_obtained", "inventory_changed"),
            Map.entry("dimension_change", "changed_dimension"),
            Map.entry("use_item_on_block", "item_used_on_block"),
            Map.entry("player_sleep", "slept_in_bed"),
            Map.entry("item_enchanted", "enchanted_item"),
            Map.entry("fish_caught", "fishing_rod_hooked"),
            Map.entry("animal_tamed", "tame_animal"),
            Map.entry("animals_bred", "bred_animals"),
            Map.entry("entity_interact", "player_interacted_with_entity"),
            Map.entry("effect_changed", "effects_changed"),
            Map.entry("potion_brewed", "brewed_potion"),
            Map.entry("villager_trade", "villager_trade"),
            Map.entry("totem_used", "used_totem"),
            Map.entry("lightning_strike", "lightning_strike"),
            Map.entry("bucket_filled", "filled_bucket"),
            Map.entry("enter_block", "enter_block"));

    private void procedures() {
        List<Procedure> procs = ctx.all(Procedure.class);
        List<Elements.Command> cmds = ctx.all(Elements.Command.class);
        ProcCompiler pc = new ProcCompiler(ctx, !realItems);
        List<String> load = new ArrayList<>();
        List<String> tick = new ArrayList<>();
        List<String> joinCalls = new ArrayList<>();
        List<String> deathCalls = new ArrayList<>();
        int stat = 0;
        String ns = ctx.ns;
        for (Procedure pr : procs) {
            String path = "procedures/" + pr.name;
            String self = ns + ":" + path;
            List<String> prefix = new ArrayList<>();
            List<String> suffix = new ArrayList<>();
            String value = Ids.full(pr.triggerValue);
            switch (pr.trigger) {
                case "load" -> tag("function", "minecraft", "load").values.add(self);
                case "tick" -> tag("function", "minecraft", "tick").values.add(self);
                case "player_tick" -> tick.add("execute as @a at @s run function " + self);
                case "interval" -> {
                    int n = Math.max(1, pr.interval);
                    load.add("schedule function " + self + " " + n + "t replace");
                    suffix.add("schedule function " + self + " " + n + "t replace");
                }
                case "player_join" -> joinCalls.add("function " + self);
                case "player_death" -> deathCalls.add("function " + self);
                case "player_jump" -> {
                    String obj = ns + ".s" + (stat++);
                    load.add("scoreboard objectives add " + obj + " minecraft.custom:minecraft.jump");
                    tick.add("execute as @a[scores={" + obj + "=1..}] at @s run function " + self);
                    prefix.add("scoreboard players reset @s " + obj);
                }
                case "block_break", "item_crafted" -> {
                    if (value.isBlank()) break;
                    String obj = ns + ".s" + (stat++);
                    String crit = pr.trigger.equals("block_break") ? "minecraft.mined:" : "minecraft.crafted:";
                    load.add("scoreboard objectives add " + obj + " " + crit + Ids.statId(value));
                    tick.add("execute as @a[scores={" + obj + "=1..}] at @s run function " + self);
                    prefix.add("scoreboard players reset @s " + obj);
                }
                case "item_right_click" -> {
                    if (nativeHooks || value.isBlank()) break;
                    String obj = ns + ".s" + (stat++);
                    String statItem = value;
                    Item it = projectItem(value);
                    if (it != null) statItem = ItemSpec.base(it);
                    load.add("scoreboard objectives add " + obj + " minecraft.used:" + Ids.statId(statItem));
                    tick.add("execute as @a[scores={" + obj + "=1..}] at @s run function " + self);
                    prefix.add("scoreboard players reset @s " + obj);
                }
                default -> {
                    String adv = ADV_TRIGGERS.get(pr.trigger);
                    if (adv != null) {
                        JsonObject json = J.obj("criteria", J.obj("trigger", J.obj("trigger", "minecraft:" + adv,
                                "conditions", triggerConditions(adv, pr.triggerValue))), "rewards", J.obj("function", self));
                        ctx.files.json(data(ns, ctx.advDir(), path + ".json"), json);
                        prefix.add("advancement revoke @s only " + self);
                    }
                }
            }
            pc.compile(path, pr.steps, prefix, suffix);
        }
        for (Elements.Command c : cmds) {
            String path = "commands/" + c.name;
            pc.compile(path, c.steps, List.of(), List.of());
            List<String> branchFns = InterfaceGen.branchFunctions(c);
            for (int i = 0; i < branchFns.size(); i++) {
                if (branchFns.get(i) != null) pc.compileWithArgs(branchFns.get(i), c.branches.get(i).steps, List.of());
            }
            if (!c.branches.isEmpty()) pc.objectives.add(ns + ".args");
            if (!nativeHooks) {
                // data packs: /trigger <name> runs the command, /trigger <name> set <2+i> runs branch i when it has no arguments
                load.add("scoreboard objectives add " + c.name + " trigger");
                tick.add("scoreboard players enable @a " + c.name);
                tick.add("execute as @a[scores={" + c.name + "=1..}] at @s run function " + ns + ":" + path + "/_trigger");
                List<String> trig = new ArrayList<>();
                trig.add("execute if score @s " + c.name + " matches 1 run function " + ns + ":" + path);
                for (int i = 0; i < branchFns.size(); i++) {
                    var toks = modstudio.core.CommandSpec.tryParse(c.branches.get(i).path);
                    if (branchFns.get(i) == null || toks == null || toks.stream().anyMatch(x -> !x.literal())) continue;
                    trig.add("execute if score @s " + c.name + " matches " + (i + 2) + " run function " + ns + ":" + branchFns.get(i));
                }
                trig.add("scoreboard players set @s " + c.name + " 0");
                pc.functions.put(path + "/_trigger", trig);
            }
        }
        new SystemsGen(ctx, pc, root, realItems, load, tick).generate();
        new GameplayGen(ctx, pc, root, realItems, tick, (reg, n, path, value) -> tag(reg, n, path).values.add(value)).generate();
        new InterfaceGen(ctx, pc, load, tick, joinCalls).generate();
        new MachineGen(ctx, pc, root, realItems, tick).generate();
        new CutsceneGen(ctx, pc, tick, joinCalls).generate();
        new ScreenGen(ctx).refreshFunction(pc.functions, load);
        for (Block b : ctx.all(Block.class)) {
            if (!b.onUse.isEmpty()) pc.compile("blocks/" + b.name + "/on_use", b.onUse, List.of(), List.of());
            if (!b.onStep.isEmpty()) pc.compile("blocks/" + b.name + "/on_step", b.onStep, List.of(), List.of());
            if (!b.onRandomTick.isEmpty()) pc.compile("blocks/" + b.name + "/on_random_tick", b.onRandomTick, List.of(), List.of());
            if (!b.onNeighborChanged.isEmpty()) pc.compile("blocks/" + b.name + "/on_neighbor_changed", b.onNeighborChanged, List.of(), List.of());
        }
        for (Item it : ctx.all(Item.class)) {
            if (!it.onUse.isEmpty()) pc.compile("items/" + it.name + "/on_use", it.onUse, List.of(), List.of());
            if (!it.onHitEntity.isEmpty()) pc.compile("items/" + it.name + "/on_hit", it.onHitEntity, List.of(), List.of());
            if (!it.onTickInHand.isEmpty()) pc.compile("items/" + it.name + "/on_tick", it.onTickInHand, List.of(), List.of());
        }
        for (Fluid fl : ctx.all(Fluid.class)) {
            if (!fl.onEntityInside.isEmpty()) pc.compile("fluids/" + fl.name + "/inside", fl.onEntityInside, List.of(), List.of());
        }
        for (ModElements.Container ct : ctx.all(ModElements.Container.class)) {
            if (!ct.onOpen.isEmpty()) pc.compile("containers/" + ct.name + "/on_open", ct.onOpen, List.of(), List.of());
            if (!ct.onClose.isEmpty()) pc.compile("containers/" + ct.name + "/on_close", ct.onClose, List.of(), List.of());
            if (!ct.onCraft.isEmpty()) pc.compile("containers/" + ct.name + "/on_craft", ct.onCraft, List.of(), List.of());
        }
        for (ModElements.CustomEntity ce : ctx.all(ModElements.CustomEntity.class)) {
            if (!ce.onSpawn.isEmpty()) pc.compile("entities/" + ce.name + "/on_spawn", ce.onSpawn, List.of(), List.of());
            if (!ce.onHurt.isEmpty()) pc.compile("entities/" + ce.name + "/on_hurt", ce.onHurt, List.of(), List.of());
            if (!ce.onDeath.isEmpty()) pc.compile("entities/" + ce.name + "/on_death", ce.onDeath, List.of(), List.of());
            if (!ce.onInteract.isEmpty()) pc.compile("entities/" + ce.name + "/on_interact", ce.onInteract, List.of(), List.of());
        }
        if (!joinCalls.isEmpty()) {
            load.add("scoreboard objectives add " + ns + ".left minecraft.custom:minecraft.leave_game");
            tick.add("execute as @a[tag=!" + ns + ".joined] at @s run function " + ns + ":_join");
            tick.add("execute as @a[scores={" + ns + ".left=1..}] at @s run function " + ns + ":_join");
            List<String> j = new ArrayList<>(List.of("tag @s add " + ns + ".joined", "scoreboard players reset @s " + ns + ".left"));
            j.addAll(joinCalls);
            ctx.files.text(fn("_join"), String.join("\n", j) + "\n");
        }
        if (!deathCalls.isEmpty()) {
            load.add("scoreboard objectives add " + ns + ".deaths deathCount");
            tick.add("execute as @a[scores={" + ns + ".deaths=1..}] at @s run function " + ns + ":_death");
            List<String> d = new ArrayList<>(List.of("scoreboard players reset @s " + ns + ".deaths"));
            d.addAll(deathCalls);
            ctx.files.text(fn("_death"), String.join("\n", d) + "\n");
        }
        List<String> loadAll = new ArrayList<>();
        for (String o : pc.objectives) if (!o.isBlank()) loadAll.add("scoreboard objectives add " + o + " dummy");
        loadAll.addAll(load);
        if (!loadAll.isEmpty()) {
            ctx.files.text(fn("_load"), String.join("\n", loadAll) + "\n");
            TagData lt = tag("function", "minecraft", "load");
            LinkedHashSet<String> ordered = new LinkedHashSet<>();
            ordered.add(ns + ":_load");
            ordered.addAll(lt.values);
            lt.values.clear();
            lt.values.addAll(ordered);
        }
        if (!tick.isEmpty()) {
            ctx.files.text(fn("_tick"), String.join("\n", tick) + "\n");
            tag("function", "minecraft", "tick").values.add(ns + ":_tick");
        }
        for (var e : pc.functions.entrySet()) {
            List<String> lines = e.getValue();
            ctx.files.text(fn(e.getKey()), lines.isEmpty() ? "# empty\n" : String.join("\n", lines) + "\n");
        }
        for (var e : pc.predicates.entrySet()) {
            ctx.files.json(data(ns, ctx.predicateDir(), e.getKey() + ".json"), e.getValue());
        }
        for (var e : pc.dataJson.entrySet()) {
            ctx.files.json(root + "data/" + ns + "/" + e.getKey() + ".json", e.getValue());
        }
    }

    private void giveFunctions() {
        for (Item it : ctx.all(Item.class)) {
            ctx.files.text(fn("give/" + it.name), "give @s " + ItemSpec.give(ctx, it) + " 1\n");
        }
    }

    private void advancedData() {
        for (ModElements.WorldFeature wf : ctx.all(ModElements.WorldFeature.class)) {
            JsonObject configured = configuredFeature(wf);
            ctx.files.json(data(ctx.ns, "worldgen/configured_feature", wf.name + ".json"), configured);
            ctx.files.json(data(ctx.ns, "worldgen/placed_feature", wf.name + ".json"), placedFeature(wf));
        }
        for (ModElements.ArmorTrim at : ctx.all(ModElements.ArmorTrim.class)) {
            String tpl = at.generateTemplate
                    ? (realItems ? ctx.id(at.name + "_armor_trim_smithing_template")
                    : (at.item.isBlank() ? "minecraft:coast_armor_trim_smithing_template" : Ids.full(at.item)))
                    : Ids.full(at.item);
            JsonObject o = J.obj("asset_id", ctx.id(at.name), "description", J.obj("translate", "trim_pattern." + ctx.ns + "." + at.name), "decal", at.decal);
            if (!ctx.v.atLeast(McVersion.V1_21_8) && !tpl.isBlank()) o.addProperty("template_item", tpl);
            ctx.files.json(data(ctx.ns, "trim_pattern", at.name + ".json"), o);
            if (at.generateTemplate && realItems) tag("item", "minecraft", "trim_templates").values.add(tpl);
        }
        if (realItems) {
            for (ModElements.CustomEntity ce : ctx.all(ModElements.CustomEntity.class)) {
                if (!ce.isLivingMob() || ce.lootTable.isBlank() || ctx.p.byName(ElementType.LOOT_TABLE, "entities/" + ce.name) != null) continue;
                String tableRef = Ids.full(ce.lootTable.trim());
                JsonObject entry = J.obj("type", "minecraft:loot_table", ctx.components() ? "value" : "name", tableRef);
                ctx.files.json(data(ctx.ns, ctx.lootDir(), "entities/" + ce.name + ".json"),
                        J.obj("type", "minecraft:entity", "pools", J.arr(J.obj("rolls", 1, "bonus_rolls", 0, "entries", J.arr(entry)))));
            }
        }
        if (ctx.components()) {
            for (ModElements.BannerPattern bp : ctx.all(ModElements.BannerPattern.class)) {
                ctx.files.json(data(ctx.ns, "banner_pattern", bp.name + ".json"), J.obj(
                        "asset_id", ctx.id(bp.name),
                        "translation_key", "block.minecraft.banner." + ctx.ns + "." + bp.name));
            }
        }
    }

    private JsonObject configuredFeature(ModElements.WorldFeature wf) {
        String blk = Ids.full(wf.block.isBlank() ? "minecraft:stone" : wf.block);
        return switch (wf.featureType) {
            case "tree" -> {
                String trunk = Ids.full(wf.trunkBlock.isBlank() ? "minecraft:oak_log" : wf.trunkBlock);
                String leaves = Ids.full(wf.leavesBlock.isBlank() ? "minecraft:oak_leaves" : wf.leavesBlock);
                yield J.obj("type", "minecraft:tree", "config", J.obj(
                        "ignore_vines", true,
                        "minimum_size", J.obj("type", "minecraft:two_layers_feature_size", "limit", 1, "lower_size", 0, "upper_size", 1),
                        "dirt_provider", stateProvider("minecraft:dirt"),
                        "trunk_provider", stateProvider(trunk),
                        "foliage_provider", stateProvider(leaves),
                        "trunk_placer", J.obj("type", "minecraft:straight_trunk_placer", "base_height", Math.max(2, wf.trunkHeight), "height_rand_a", 2, "height_rand_b", 0),
                        "foliage_placer", J.obj("type", "minecraft:blob_foliage_placer", "radius", Math.max(1, wf.foliageRadius), "offset", 0, "height", 3),
                        "decorators", new JsonArray()));
            }
            case "patch" -> J.obj("type", "minecraft:random_patch", "config", J.obj(
                    "tries", Math.max(1, wf.size * 4),
                    "xz_spread", Math.max(1, Math.min(16, wf.xzSpread)),
                    "y_spread", Math.max(1, Math.min(16, wf.ySpread)),
                    "feature", J.obj("feature", J.obj("type", "minecraft:simple_block", "config", J.obj("to_place", stateProvider(blk))), "placement", new JsonArray())));
            case "spike" -> J.obj("type", "minecraft:block_column", "config", J.obj(
                    "direction", "up",
                    "allowed_placement", J.obj("type", "minecraft:matching_blocks", "blocks", "minecraft:air"),
                    "prioritize_tip", false,
                    "layers", J.arr(J.obj("height", J.obj("type", "minecraft:uniform", "min_inclusive", 2, "max_inclusive", Math.max(3, wf.size)),
                            "provider", stateProvider(blk)))));
            default -> {
                String raw = wf.replaceTarget.isBlank() ? "#minecraft:stone_ore_replaceables" : wf.replaceTarget;
                JsonArray targets = new JsonArray();
                for (String part : raw.split(",")) {
                    String target = Ids.full(part.trim());
                    if (target.isBlank()) continue;
                    JsonObject rule = Ids.isTag(target)
                            ? J.obj("predicate_type", "minecraft:tag_match", "tag", target.substring(1))
                            : J.obj("predicate_type", "minecraft:block_match", "block", target);
                    targets.add(J.obj("target", rule, "state", J.obj("Name", blk)));
                }
                if (targets.isEmpty()) {
                    targets.add(J.obj("target", J.obj("predicate_type", "minecraft:tag_match", "tag", "minecraft:stone_ore_replaceables"),
                            "state", J.obj("Name", blk)));
                }
                double discard = Math.max(0.0, Math.min(1.0, wf.discardAir));
                yield J.obj("type", "minecraft:ore", "config", J.obj("discard_chance_on_air_exposure", discard, "size", Math.max(1, Math.min(64, wf.size)),
                        "targets", targets));
            }
        };
    }

    private JsonObject placedFeature(ModElements.WorldFeature wf) {
        JsonArray place = new JsonArray();
        if (wf.rareFilter) place.add(J.obj("type", "minecraft:rarity_filter", "chance", Math.max(1, wf.count)));
        else place.add(J.obj("type", "minecraft:count", "count", Math.max(1, wf.count)));
        place.add(J.obj("type", "minecraft:in_square"));
        if (wf.featureType.equals("ore")) {
            int min = Math.min(wf.minY, wf.maxY), max = Math.max(wf.minY, wf.maxY);
            String heightType = "uniform".equals(wf.distribution) ? "minecraft:uniform" : "minecraft:trapezoid";
            place.add(J.obj("type", "minecraft:height_range", "height", J.obj("type", heightType,
                    "min_inclusive", J.obj("absolute", min), "max_inclusive", J.obj("absolute", max))));
            boolean nonSurfaceDim = wf.biomes != null && (wf.biomes.contains("nether") || wf.biomes.contains("end"));
            if (wf.preventSurface && !nonSurfaceDim) {
                place.add(J.obj("type", "minecraft:surface_relative_threshold_filter",
                        "heightmap", "OCEAN_FLOOR_WG",
                        "max_inclusive", -Math.max(1, wf.surfaceDepth)));
            }
        } else {
            place.add(J.obj("type", "minecraft:heightmap", "heightmap", wf.featureType.equals("tree") ? "OCEAN_FLOOR" : "WORLD_SURFACE_WG"));
        }
        place.add(J.obj("type", "minecraft:biome"));
        return J.obj("feature", ctx.id(wf.name), "placement", place);
    }

    private static JsonObject stateProvider(String blockId) {
        return J.obj("type", "minecraft:simple_state_provider", "state", J.obj("Name", blockId));
    }
}
