package modstudio.core;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import modstudio.core.Elements.*;
import modstudio.i18n.I18n;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Upgrades imported or existing projects (from older Minecraft versions such as 1.12.2–1.20.4)
 * to a newer {@link McVersion} and optional target loader list.
 */
public final class ProjectUpgrader {
    private ProjectUpgrader() {}

    public record Report(int migratedIds, int migratedFiles, List<String> changes, List<String> warnings) {}

    private static final Map<String, String> CUSTOM_IDS_1204 = new ConcurrentHashMap<>();
    private static final Map<String, String> CUSTOM_IDS_1206 = new ConcurrentHashMap<>();

    /** Registers a custom vanilla or modded ID migration rule applied from {@code minTarget} onward. */
    public static void registerIdMigration(McVersion minTarget, String oldId, String newId) {
        if (oldId == null || oldId.isBlank() || newId == null || newId.isBlank()) return;
        if (minTarget != null && minTarget.components()) CUSTOM_IDS_1206.put(oldId.trim(), newId.trim());
        else CUSTOM_IDS_1204.put(oldId.trim(), newId.trim());
    }

    private static final Map<String, String> LEGACY_IDS_1204 = Map.ofEntries(
            Map.entry("minecraft:grass", "minecraft:short_grass"),
            Map.entry("minecraft:tallgrass", "minecraft:short_grass"),
            Map.entry("minecraft:double_plant", "minecraft:sunflower"),
            Map.entry("minecraft:zombie_pigman", "minecraft:zombified_piglin"),
            Map.entry("minecraft:zombie_pigman_spawn_egg", "minecraft:zombified_piglin_spawn_egg"),
            Map.entry("minecraft:sign", "minecraft:oak_sign"),
            Map.entry("minecraft:reeds", "minecraft:sugar_cane"),
            Map.entry("minecraft:speckled_melon", "minecraft:glistering_melon_slice"),
            Map.entry("minecraft:melon", "minecraft:melon_slice"),
            Map.entry("minecraft:melon_block", "minecraft:melon"),
            Map.entry("minecraft:chorus_fruit_popped", "minecraft:popped_chorus_fruit"),
            Map.entry("minecraft:boat", "minecraft:oak_boat"),
            Map.entry("minecraft:dye", "minecraft:white_dye"),
            Map.entry("minecraft:stained_glass", "minecraft:white_stained_glass"),
            Map.entry("minecraft:wool", "minecraft:white_wool"),
            Map.entry("minecraft:log", "minecraft:oak_log"),
            Map.entry("minecraft:planks", "minecraft:oak_planks"),
            Map.entry("minecraft:sapling", "minecraft:oak_sapling"),
            Map.entry("minecraft:leaves", "minecraft:oak_leaves"),
            Map.entry("minecraft:stone_slab", "minecraft:smooth_stone_slab"),
            Map.entry("minecraft:wooden_slab", "minecraft:oak_slab"),
            Map.entry("minecraft:fish", "minecraft:cod"),
            Map.entry("minecraft:cooked_fish", "minecraft:cooked_cod"),
            Map.entry("minecraft:magma", "minecraft:magma_block"),
            Map.entry("minecraft:golden_rail", "minecraft:powered_rail"),
            Map.entry("minecraft:noteblock", "minecraft:note_block"),
            Map.entry("minecraft:mob_spawner", "minecraft:spawner"),
            Map.entry("minecraft:web", "minecraft:cobweb"),
            Map.entry("minecraft:deadbush", "minecraft:dead_bush"),
            Map.entry("minecraft:waterlily", "minecraft:lily_pad"),
            Map.entry("minecraft:record_13", "minecraft:music_disc_13"),
            Map.entry("minecraft:record_cat", "minecraft:music_disc_cat"),
            Map.entry("minecraft:record_blocks", "minecraft:music_disc_blocks"),
            Map.entry("minecraft:stonebrick", "minecraft:stone_bricks"),
            Map.entry("minecraft:brick_block", "minecraft:bricks"),
            Map.entry("minecraft:nether_brick", "minecraft:nether_bricks"),
            Map.entry("minecraft:red_nether_brick", "minecraft:red_nether_bricks"),
            Map.entry("minecraft:end_bricks", "minecraft:end_stone_bricks"),
            Map.entry("minecraft:quartz_ore", "minecraft:nether_quartz_ore"),
            Map.entry("minecraft:yellow_flower", "minecraft:dandelion"),
            Map.entry("minecraft:red_flower", "minecraft:poppy"),
            Map.entry("minecraft:snow_layer", "minecraft:snow"),
            Map.entry("minecraft:slime", "minecraft:slime_block"),
            Map.entry("minecraft:skull", "minecraft:skeleton_skull"),
            Map.entry("minecraft:bed", "minecraft:red_bed"),
            Map.entry("minecraft:banner", "minecraft:white_banner"),
            Map.entry("minecraft:stained_hardened_clay", "minecraft:white_terracotta"),
            Map.entry("minecraft:hardened_clay", "minecraft:terracotta"),
            Map.entry("minecraft:silver_glazed_terracotta", "minecraft:light_gray_glazed_terracotta"),
            Map.entry("minecraft:concrete", "minecraft:white_concrete"),
            Map.entry("minecraft:concrete_powder", "minecraft:white_concrete_powder"),
            Map.entry("minecraft:carpet", "minecraft:white_carpet"),
            Map.entry("minecraft:stained_glass_pane", "minecraft:white_stained_glass_pane"),
            Map.entry("minecraft:wooden_door", "minecraft:oak_door"),
            Map.entry("minecraft:fence", "minecraft:oak_fence"),
            Map.entry("minecraft:fence_gate", "minecraft:oak_fence_gate"),
            Map.entry("minecraft:trapdoor", "minecraft:oak_trapdoor"),
            Map.entry("minecraft:wooden_button", "minecraft:oak_button"),
            Map.entry("minecraft:wooden_pressure_plate", "minecraft:oak_pressure_plate"),
            Map.entry("minecraft:lit_pumpkin", "minecraft:jack_o_lantern"),
            Map.entry("minecraft:grass_path", "minecraft:dirt_path"),
            Map.entry("minecraft:fireworks", "minecraft:firework_rocket"),
            Map.entry("minecraft:firework_charge", "minecraft:firework_star"),
            Map.entry("minecraft:monster_egg", "minecraft:infested_stone"),
            Map.entry("minecraft:double_stone_slab", "minecraft:smooth_stone"),
            Map.entry("minecraft:spawn_egg", "minecraft:pig_spawn_egg")
    );
    private static final Map<String, String> LEGACY_IDS_1206 = Map.of(
            "minecraft:scute", "minecraft:turtle_scute"
    );

    public static Report upgrade(Project p, McVersion targetMc, List<Target> newTargets) {
        Objects.requireNonNull(p, "project");
        Objects.requireNonNull(targetMc, "targetMc");

        String fromVer = !p.meta.sourceMcVersion.isBlank() ? p.meta.sourceMcVersion : p.meta.mcVersion;
        List<String> changes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int[] idCount = {0};
        int fileCount = 0;

        p.meta.mcVersion = targetMc.id;
        LinkedHashSet<String> nextMc = new LinkedHashSet<>();
        nextMc.add(targetMc.id);
        for (String oldVer : p.meta.mcVersions) {
            McVersion parsed = McVersion.orNull(oldVer);
            if (parsed != null && parsed.atLeast(targetMc)) nextMc.add(parsed.id);
        }
        p.meta.mcVersions = new ArrayList<>(nextMc);

        if (newTargets != null && !newTargets.isEmpty()) {
            List<String> next = new ArrayList<>();
            for (Target t : newTargets) {
                if (!t.supports(targetMc)) {
                    warnings.add(I18n.tOr("upgrade.warn.unsupported_target", t.id + " does not support Minecraft " + targetMc.id + "; skipped.", t.id, targetMc.id));
                    continue;
                }
                if (!next.contains(t.id)) next.add(t.id);
            }
            if (!next.isEmpty()) {
                p.meta.targets = next;
                changes.add(I18n.tOr("upgrade.change.targets", "Targets updated to: " + String.join(", ", next), String.join(", ", next)));
            }
        } else {
            // Auto-migrate Forge -> NeoForge only if Forge doesn't support targetMc (26.1+)
            List<String> adjusted = new ArrayList<>();
            for (String tid : p.meta.targets) {
                Target t = Target.of(tid);
                if (t == Target.FORGE && !t.supports(targetMc) && Target.NEOFORGE.supports(targetMc)) {
                    if (!adjusted.contains(Target.NEOFORGE.id)) {
                        adjusted.add(Target.NEOFORGE.id);
                        changes.add(I18n.tOr("upgrade.change.forge_to_neoforge", "Switched loader forge -> neoforge (Forge does not support " + targetMc.id + ").", targetMc.id));
                    }
                } else {
                    adjusted.add(tid);
                }
            }
            if (!adjusted.isEmpty()) p.meta.targets = adjusted;
        }
        p.normalizeVersions();

        // Reset stale pinned toolchain overrides for older MC versions not in p.meta.mcVersions
        if (!p.versions.isEmpty()) {
            boolean removed = p.versions.keySet().removeIf(k -> !k.equals("bedrock")
                    && p.meta.mcVersions.stream().noneMatch(mv -> k.endsWith("@" + mv)));
            if (removed) changes.add(I18n.tOr("upgrade.change.toolchain", "Refreshed toolchain version settings for " + targetMc.id + ".", targetMc.id));
        }

        // Migrate element references and triggers across ALL element types
        for (Element el : p.elements) {
            switch (el) {
                case Item it -> {
                    it.baseItem = mapId(it.baseItem, targetMc, idCount);
                    it.repairItem = mapId(it.repairItem, targetMc, idCount);
                    it.craftRemainder = mapId(it.craftRemainder, targetMc, idCount);
                    it.useReturnItem = mapId(it.useReturnItem, targetMc, idCount);
                    it.foodEffect = mapId(it.foodEffect, targetMc, idCount);
                }
                case ArmorMaterial am -> am.repairItem = mapId(am.repairItem, targetMc, idCount);
                case ToolMaterial tm -> tm.repairItem = mapId(tm.repairItem, targetMc, idCount);
                case Block bl -> {
                    bl.dropItem = mapId(bl.dropItem, targetMc, idCount);
                    bl.cropProduce = mapId(bl.cropProduce, targetMc, idCount);
                    bl.oreReplaceTarget = mapId(bl.oreReplaceTarget, targetMc, idCount);
                }
                case Recipe rc -> {
                    for (int i = 0; i < rc.grid.size(); i++) rc.grid.set(i, mapId(rc.grid.get(i), targetMc, idCount));
                    rc.input = mapId(rc.input, targetMc, idCount);
                    rc.result = mapId(rc.result, targetMc, idCount);
                    rc.template = mapId(rc.template, targetMc, idCount);
                    rc.base = mapId(rc.base, targetMc, idCount);
                    rc.addition = mapId(rc.addition, targetMc, idCount);
                }
                case LootTable lt -> {
                    for (LootPool pool : lt.pools) {
                        for (LootEntry en : pool.entries) {
                            if (en.kind.equals("item") || en.kind.equals("tag")) en.name = mapId(en.name, targetMc, idCount);
                        }
                    }
                }
                case Advancement adv -> {
                    adv.icon = mapId(adv.icon, targetMc, idCount);
                    adv.triggerValue = mapId(adv.triggerValue, targetMc, idCount);
                    if ("sleeping_in_bed".equals(adv.trigger)) { adv.trigger = "slept_in_bed"; idCount[0]++; }
                    if (!adv.background.isBlank()) adv.background = adv.background.replace("textures/blocks/", "textures/block/");
                }
                case Tag tg -> {
                    for (int i = 0; i < tg.values.size(); i++) tg.values.set(i, mapId(tg.values.get(i), targetMc, idCount));
                }
                case Elements.Function fn -> {
                    String nextCode = upgradeFunctionCode(fn.code, targetMc);
                    if (!nextCode.equals(fn.code)) { fn.code = nextCode; fileCount++; }
                }
                case Procedure pr -> pr.triggerValue = mapId(pr.triggerValue, targetMc, idCount);
                case ModElements.CustomEntity ce -> {
                    ce.mainhand = mapId(ce.mainhand, targetMc, idCount);
                    ce.head = mapId(ce.head, targetMc, idCount);
                    ce.tameItem = mapId(ce.tameItem, targetMc, idCount);
                    ce.lootTable = mapId(ce.lootTable, targetMc, idCount);
                }
                case ModElements.Container ct -> {
                    ct.block = mapId(ct.block, targetMc, idCount);
                    for (ModElements.ContainerRecipe cr : ct.recipes) {
                        cr.input = mapId(cr.input, targetMc, idCount);
                        cr.output = mapId(cr.output, targetMc, idCount);
                    }
                }
                case ModElements.Portal pt -> {
                    pt.frameBlock = mapId(pt.frameBlock, targetMc, idCount);
                    pt.igniterItem = mapId(pt.igniterItem, targetMc, idCount);
                    pt.targetDimension = mapId(pt.targetDimension, targetMc, idCount);
                }
                case ModElements.Profession pf -> {
                    pf.workstationBlock = mapId(pf.workstationBlock, targetMc, idCount);
                    for (ModElements.ProfessionTrade tr : pf.trades) {
                        tr.costA = mapId(tr.costA, targetMc, idCount);
                        tr.costB = mapId(tr.costB, targetMc, idCount);
                        tr.result = mapId(tr.result, targetMc, idCount);
                    }
                }
                case ModElements.WorldFeature wf -> {
                    wf.block = mapId(wf.block, targetMc, idCount);
                    wf.replaceTarget = mapId(wf.replaceTarget, targetMc, idCount);
                    wf.trunkBlock = mapId(wf.trunkBlock, targetMc, idCount);
                    wf.leavesBlock = mapId(wf.leavesBlock, targetMc, idCount);
                }
                case Systems.Mob mb -> {
                    mb.base = mapId(mb.base, targetMc, idCount);
                    mb.mainhand = mapId(mb.mainhand, targetMc, idCount);
                    mb.offhand = mapId(mb.offhand, targetMc, idCount);
                    mb.head = mapId(mb.head, targetMc, idCount);
                    mb.chest = mapId(mb.chest, targetMc, idCount);
                    mb.legs = mapId(mb.legs, targetMc, idCount);
                    mb.feet = mapId(mb.feet, targetMc, idCount);
                }
                case Systems.Biome bi -> {
                    for (Systems.MobSpawn sp : bi.spawns) sp.entity = mapId(sp.entity, targetMc, idCount);
                }
                case Systems.Dimension dim -> {
                    for (int i = 0; i < dim.biomes.size(); i++) dim.biomes.set(i, mapId(dim.biomes.get(i), targetMc, idCount));
                    if (!dim.flatLayers.isBlank()) dim.flatLayers = upgradeFunctionCode(dim.flatLayers, targetMc);
                }
                case Systems.Menu mn -> {
                    for (Systems.MenuSlot sl : mn.slots) sl.item = mapId(sl.item, targetMc, idCount);
                }
                case Systems.Trader tr -> {
                    for (Systems.Trade trade : tr.trades) {
                        trade.buy = mapId(trade.buy, targetMc, idCount);
                        trade.buyB = mapId(trade.buyB, targetMc, idCount);
                        trade.sell = mapId(trade.sell, targetMc, idCount);
                    }
                }
                case CodeFile cf -> {
                    if (upgradeCodeFile(cf, targetMc)) fileCount++;
                }
                default -> {
                }
            }
        }
        StepWalker.walk(p, (owner, step) -> upgradeStepMap(step, targetMc, idCount));

        // Normalize legacy 1.12 .lang translation keys into modern block./item./entity. keys
        int langMigrated = upgradeTranslations(p);
        if (langMigrated > 0) changes.add(I18n.tOr("upgrade.change.translations", "Converted " + langMigrated + " legacy translation key(s) to modern format.", langMigrated));
        if (idCount[0] > 0) changes.add(I18n.tOr("upgrade.change.ids", "Updated " + idCount[0] + " legacy vanilla identifier/trigger reference(s).", idCount[0]));
        if (fileCount > 0) changes.add(I18n.tOr("upgrade.change.files", "Migrated " + fileCount + " function / resource JSON file(s) for " + targetMc.id + ".", fileCount, targetMc.id));

        if (targetMc.registryKeys() && !p.all(Systems.Shader.class).isEmpty()) {
            int removedLegacyShaders = 0;
            Iterator<Element> sit = p.elements.iterator();
            while (sit.hasNext()) {
                Element el = sit.next();
                if (el instanceof Systems.Shader sh && isIncompatibleLegacyShader(sh, targetMc)) {
                    sit.remove();
                    removedLegacyShaders++;
                }
            }
            if (removedLegacyShaders > 0) {
                changes.add(I18n.tOr("upgrade.change.legacy_shaders",
                        "Removed " + removedLegacyShaders + " incompatible pre-1.21.2 legacy GLSL shader override(s) for " + targetMc.id + ".",
                        removedLegacyShaders, targetMc.id));
            }
            if (!p.all(Systems.Shader.class).isEmpty()) {
                warnings.add(I18n.tOr("upgrade.warn.shaders", "Custom shaders targeting pre-1.21.2 format may require post_effect / uniform updates for " + targetMc.id + ".", targetMc.id));
            }
        }
        p.meta.sourceMcVersion = targetMc.id;
        if (!fromVer.equals(targetMc.id)) {
            changes.add(0, I18n.tOr("upgrade.change.version", "Upgraded project target from " + fromVer + " to Minecraft " + targetMc.id + ".", fromVer, targetMc.id));
        } else {
            changes.add(0, I18n.tOr("upgrade.change.normalized", "Normalized project for Minecraft " + targetMc.id + ".", targetMc.id));
        }
        return new Report(idCount[0], fileCount, changes, warnings);
    }

    private static void upgradeStepMap(Step s, McVersion v, int[] count) {
        for (var en : s.p.entrySet()) {
            String m = mapId(en.getValue(), v, count);
            if (!m.equals(en.getValue())) en.setValue(m);
        }
        if (s.cond != null) {
            for (var en : s.cond.p.entrySet()) {
                String m = mapId(en.getValue(), v, count);
                if (!m.equals(en.getValue())) en.setValue(m);
            }
        }
    }

    private static String mapId(String raw, McVersion to, int[] count) {
        if (raw == null || raw.isBlank()) return raw;
        boolean hash = raw.startsWith("#");
        String body = hash ? raw.substring(1) : raw;
        if (to.atLeast(McVersion.V1_20_4)) {
            String mapped = LEGACY_IDS_1204.getOrDefault(body, CUSTOM_IDS_1204.get(body));
            if (mapped != null) { count[0]++; return (hash ? "#" : "") + mapped; }
        }
        if (to.components()) {
            String mapped = LEGACY_IDS_1206.getOrDefault(body, CUSTOM_IDS_1206.get(body));
            if (mapped != null) { count[0]++; return (hash ? "#" : "") + mapped; }
        }
        return raw;
    }

    private static String upgradeFunctionCode(String code, McVersion to) {
        if (code == null || code.isEmpty()) return code;
        String out = code;
        if (to.atLeast(McVersion.V1_20_4)) {
            for (var en : LEGACY_IDS_1204.entrySet()) {
                out = replaceWholeId(out, en.getKey(), en.getValue());
            }
            for (var en : CUSTOM_IDS_1204.entrySet()) {
                out = replaceWholeId(out, en.getKey(), en.getValue());
            }
        }
        if (to.components()) {
            for (var en : LEGACY_IDS_1206.entrySet()) {
                out = replaceWholeId(out, en.getKey(), en.getValue());
            }
            for (var en : CUSTOM_IDS_1206.entrySet()) {
                out = replaceWholeId(out, en.getKey(), en.getValue());
            }
        }
        return out;
    }

    private static String replaceWholeId(String text, String oldId, String newId) {
        if (!text.contains(oldId)) return text;
        return text.replaceAll("(?<![A-Za-z0-9_./:-])" + Pattern.quote(oldId) + "(?![A-Za-z0-9_./-])", Matcher.quoteReplacement(newId));
    }

    private static boolean upgradeCodeFile(CodeFile cf, McVersion to) {
        boolean changed = false;
        String nextPath = upgradeResourcePath(cf.path, to);
        if (!nextPath.equals(cf.path)) {
            cf.path = nextPath;
            changed = true;
        }
        String content = cf.content;
        if (content != null && !content.isBlank() && cf.path.endsWith(".json")) {
            String upgraded = upgradeJsonContent(cf.path, content, to);
            if (!upgraded.equals(content)) {
                cf.content = upgraded;
                changed = true;
            }
        }
        return changed;
    }

    private static String upgradeResourcePath(String path, McVersion to) {
        if (path == null) return "";
        String prefix = path.startsWith("src/main/resources/data/") ? "src/main/resources/data/"
                : path.startsWith("data/") ? "data/" : null;
        if (prefix != null) {
            String tail = path.substring(prefix.length());
            int slash = tail.indexOf('/');
            if (slash > 0) {
                String ns = tail.substring(0, slash);
                String rest = tail.substring(slash + 1);
                boolean s = to.singular();
                String[][] pairs = {
                        {"recipe/", "recipes/"}, {"advancement/", "advancements/"},
                        {"loot_table/", "loot_tables/"}, {"function/", "functions/"},
                        {"predicate/", "predicates/"}, {"item_modifier/", "item_modifiers/"},
                        {"structure/", "structures/"}, {"tags/item/", "tags/items/"},
                        {"tags/block/", "tags/blocks/"}, {"tags/function/", "tags/functions/"},
                        {"tags/entity_type/", "tags/entity_types/"}, {"tags/fluid/", "tags/fluids/"}
                };
                for (String[] pair : pairs) {
                    if (s && rest.startsWith(pair[1])) {
                        return prefix + ns + "/" + pair[0] + rest.substring(pair[1].length());
                    }
                    if (!s && rest.startsWith(pair[0])) {
                        return prefix + ns + "/" + pair[1] + rest.substring(pair[0].length());
                    }
                }
            }
        }
        return path;
    }

    private static String upgradeJsonContent(String path, String jsonText, McVersion to) {
        String updated = jsonText
                .replace("textures/blocks/", "textures/block/")
                .replace("textures/items/", "textures/item/");
        if (to.atLeast(McVersion.V1_20_4)) {
            updated = updated.replace("\"minecraft:grass\"", "\"minecraft:short_grass\"");
        }
        if (!to.components()) return updated;
        try {
            JsonReader jr = new JsonReader(new StringReader(updated));
            jr.setStrictness(Strictness.LENIENT);
            JsonElement je = JsonParser.parseReader(jr);
            if (je == null || !je.isJsonObject()) return updated;
            JsonObject o = je.getAsJsonObject();
            boolean touched = !updated.equals(jsonText);
            if ((path.contains("/recipe/") || path.contains("/recipes/")) && o.has("result") && o.get("result").isJsonObject()) {
                JsonObject res = o.getAsJsonObject("result");
                if (res.has("item") && !res.has("id")) {
                    res.add("id", res.remove("item"));
                    touched = true;
                }
                if (res.has("data")) {
                    res.remove("data");
                    touched = true;
                }
                if (o.has("type") && o.get("type").isJsonPrimitive()) {
                    String rtype = o.get("type").getAsString();
                    if (rtype.equals("minecraft:smithing")) {
                        o.addProperty("type", "minecraft:smithing_transform");
                        touched = true;
                    } else if (rtype.equals("forge:ore_shaped")) {
                        o.addProperty("type", "minecraft:crafting_shaped");
                        touched = true;
                    } else if (rtype.equals("forge:ore_shapeless")) {
                        o.addProperty("type", "minecraft:crafting_shapeless");
                        touched = true;
                    }
                }
                if (to.registryKeys()) {
                    if (o.has("key") && o.get("key").isJsonObject()) {
                        JsonObject keyObj = o.getAsJsonObject("key");
                        for (String k : new ArrayList<>(keyObj.keySet())) {
                            JsonElement converted = toModernIngredient(keyObj.get(k));
                            if (converted != null) { keyObj.add(k, converted); touched = true; }
                        }
                    }
                    if (o.has("ingredients") && o.get("ingredients").isJsonArray()) {
                        JsonArray ings = o.getAsJsonArray("ingredients");
                        for (int i = 0; i < ings.size(); i++) {
                            JsonElement converted = toModernIngredient(ings.get(i));
                            if (converted != null) { ings.set(i, converted); touched = true; }
                        }
                    }
                }
            }
            if ((path.contains("/advancement/") || path.contains("/advancements/"))
                    && o.has("display") && o.get("display").isJsonObject()) {
                JsonObject disp = o.getAsJsonObject("display");
                if (disp.has("icon") && disp.get("icon").isJsonObject()) {
                    JsonObject ic = disp.getAsJsonObject("icon");
                    if (ic.has("item") && !ic.has("id")) {
                        ic.add("id", ic.remove("item"));
                        touched = true;
                    }
                }
            }
            return touched ? new GsonBuilder().setPrettyPrinting().create().toJson(o) : jsonText;
        } catch (Exception ignored) {
            return updated;
        }
    }

    private static boolean isIncompatibleLegacyShader(Systems.Shader sh, McVersion targetMc) {
        if (sh == null) return false;
        String combined = (sh.vertex == null ? "" : sh.vertex) + "\n" + (sh.fragment == null ? "" : sh.fragment);
        if (combined.contains("#version 120") || combined.contains("#version 110")
                || combined.contains("gl_FragColor") || combined.contains("gl_ModelViewProjectionMatrix")
                || combined.contains("gl_TexCoord")) {
            return true;
        }
        if ("post".equals(sh.kind) && !sh.program.isBlank() && !Shaders.postPrograms().contains(sh.program)) {
            return true;
        }
        if ("core".equals(sh.kind) && !sh.program.isBlank()) {
            List<String> known = Shaders.programs(targetMc);
            if (!known.isEmpty() && !known.contains(sh.program)) return true;
        }
        return false;
    }

    private static JsonElement toModernIngredient(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        if (o.has("item") && o.get("item").isJsonPrimitive()) {
            return new JsonPrimitive(o.get("item").getAsString());
        }
        if (o.has("tag") && o.get("tag").isJsonPrimitive()) {
            return new JsonPrimitive("#" + o.get("tag").getAsString());
        }
        return null;
    }

    private static int upgradeTranslations(Project p) {
        int count = 0;
        String ns = p.meta.modId;
        for (var langEntry : p.translations.entrySet()) {
            Map<String, String> map = langEntry.getValue();
            Map<String, String> additions = new LinkedHashMap<>();
            for (var kv : map.entrySet()) {
                String k = kv.getKey();
                if (!k.endsWith(".name")) continue;
                String core = k.substring(0, k.length() - 5);
                String nk = null;
                if (core.startsWith("tile.")) {
                    String r = core.substring(5);
                    nk = "block." + ns + "." + (r.startsWith(ns + ".") ? r.substring(ns.length() + 1) : r);
                } else if (core.startsWith("item.")) {
                    String r = core.substring(5);
                    nk = "item." + ns + "." + (r.startsWith(ns + ".") ? r.substring(ns.length() + 1) : r);
                } else if (core.startsWith("entity.")) {
                    String r = core.substring(7);
                    nk = "entity." + ns + "." + (r.startsWith(ns + ".") ? r.substring(ns.length() + 1) : r);
                }
                if (nk != null && !map.containsKey(nk)) {
                    additions.put(nk, kv.getValue());
                    count++;
                }
            }
            map.putAll(additions);
        }
        return count;
    }
}

