package modstudio.gen;

import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Data side of status effects, enchantments, damage types, paintings and jukebox songs.
 * Registry data (enchantment, damage_type, painting_variant, jukebox_song) is plain JSON that every Java target loads;
 * status effects are real mob effects on mod loaders and are emulated with scores and tags elsewhere.
 */
public final class GameplayGen {
    /** Adds a value to a tag collected by DataGen (so user tags with the same id are merged, not overwritten). */
    public interface TagSink {
        void add(String registry, String namespace, String path, String value);
    }

    private final GenContext ctx;
    private final ProcCompiler pc;
    private final String root;
    private final String ns;
    private final boolean realItems;
    private final List<String> tick;
    private final TagSink tags;

    public GameplayGen(GenContext ctx, ProcCompiler pc, String root, boolean realItems, List<String> tick, TagSink tags) {
        this.ctx = ctx;
        this.pc = pc;
        this.root = root;
        this.ns = ctx.ns;
        this.realItems = realItems;
        this.tick = tick;
        this.tags = tags;
    }

    public void generate() {
        for (StatusEffect e : ctx.all(StatusEffect.class)) statusEffect(e);
        for (Potion po : ctx.all(Potion.class)) potion(po);
        for (Enchantment e : ctx.all(Enchantment.class)) enchantment(e);
        for (DamageType d : ctx.all(DamageType.class)) damageType(d);
        for (Painting p : ctx.all(Painting.class)) painting(p);
        for (Song s : ctx.all(Song.class)) song(s);
    }

    private String data(String dir, String path) {
        return root + "data/" + ns + "/" + dir + "/" + path;
    }

    private String fnId(String path) {
        return ns + ":" + path;
    }

    // ------------------------------------------------------------------ status effects

    /** Scoreboard objective holding the remaining ticks of an emulated effect. */
    public static String objective(String ns, String name) {
        return ns + ".se." + name;
    }

    public static String entityTag(String ns, String name) {
        return ns + ".se." + name;
    }

    private void statusEffect(StatusEffect e) {
        String dir = "status/" + e.name;
        if (realItems) {
            // mod loaders: ModMobEffect calls this function every tickInterval ticks
            if (!e.onTick.isEmpty()) pc.compile(dir + "/tick", e.onTick, List.of(), List.of());
            return;
        }
        String obj = objective(ns, e.name), tag = entityTag(ns, e.name);
        pc.objectives.add(obj);
        List<String> start = new ArrayList<>(List.of("tag @s add " + tag));
        List<String> end = new ArrayList<>(List.of("tag @s remove " + tag, "scoreboard players reset @s " + obj));
        int i = 0;
        for (EffectModifier m : e.modifiers) {
            if (m.attribute.isBlank()) continue;
            String a = m.attribute.contains(":") ? Ids.path(m.attribute) : m.attribute;
            if (!ctx.components() && !List.of("max_health", "movement_speed", "attack_damage", "attack_speed", "armor", "armor_toughness",
                    "knockback_resistance", "luck", "follow_range", "attack_knockback", "flying_speed").contains(a)) continue;
            String attr = "minecraft:" + (ctx.registryKeys() ? a : "generic." + a);
            String id = ctx.singular() ? ns + ":effect." + e.name + "." + i
                    : java.util.UUID.nameUUIDFromBytes((ns + ":" + e.name + ":" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            String op = switch (m.operation) {
                case "add_value" -> ctx.components() ? "add_value" : "add";
                case "add_multiplied_base" -> ctx.components() ? "add_multiplied_base" : "multiply_base";
                default -> ctx.components() ? "add_multiplied_total" : "multiply";
            };
            String remove = "attribute @s " + attr + " modifier remove " + id;
            start.add(remove);
            start.add("attribute @s " + attr + " modifier add " + id + (ctx.singular() ? "" : " " + ns + "." + e.name) + " " + m.amount + " " + op);
            end.add(remove);
            i++;
        }
        pc.functions.put(dir + "/start", start);
        pc.functions.put(dir + "/end", end);
        List<String> t = new ArrayList<>(List.of("scoreboard players remove @s " + obj + " 1"));
        if (!e.onTick.isEmpty()) {
            pc.compile(dir + "/on_tick", e.onTick, List.of(), List.of());
            String cnt = ns + ".sc." + e.name;
            pc.objectives.add(cnt);
            t.add("scoreboard players add @s " + cnt + " 1");
            t.add("execute if score @s " + cnt + " matches " + Math.max(1, e.tickInterval) + ".. run function " + fnId(dir + "/on_tick"));
            t.add("execute if score @s " + cnt + " matches " + Math.max(1, e.tickInterval) + ".. run scoreboard players set @s " + cnt + " 0");
        }
        t.add("execute unless score @s " + obj + " matches 1.. run function " + fnId(dir + "/end"));
        pc.functions.put(dir + "/tick", t);
        tick.add("execute as @e[tag=" + tag + "] at @s run function " + fnId(dir + "/tick"));
    }

    // ------------------------------------------------------------------ potions

    private static final List<String> BOTTLES = List.of("potion", "splash_potion", "lingering_potion", "tipped_arrow");

    /**
     * Functions for a potion: apply (its effects on @s) and give, give_splash, give_lingering, give_arrow. Mods give
     * the registered potion; data packs and plugins give a bottle with custom effects, and drinking it applies the
     * emulated project effects through an advancement.
     */
    private void potion(Potion p) {
        String dir = "potion/" + p.name;
        List<String> apply = new ArrayList<>();
        List<String> emulated = new ArrayList<>();
        for (PotionEffect pe : p.effects) {
            String f = Ids.full(pe.effect.trim());
            int sec = Math.max(1, pe.seconds), amp = Math.max(0, Math.min(255, pe.amplifier));
            if (Ids.ns(f).equals(ns) && ctx.p.byName(ElementType.STATUS_EFFECT, Ids.path(f)) instanceof StatusEffect se) {
                if (realItems) apply.add("effect give @s " + f + " " + sec + " " + amp + " " + !pe.particles);
                else {
                    pc.objectives.add(objective(ns, se.name));
                    emulated.addAll(applyEmulated(ctx, se, "@s", sec));
                }
            } else if (Ids.ns(f).equals("minecraft") && JavaCommon.vanillaEffectConst(ctx.v, Ids.path(f)) != null) {
                apply.add("effect give @s " + f + " " + sec + " " + amp + " " + !pe.particles);
            }
        }
        List<String> all = new ArrayList<>(apply);
        all.addAll(emulated);
        pc.functions.put(dir + "/apply", all);
        for (String bottle : BOTTLES) {
            String fn = switch (bottle) {
                case "splash_potion" -> "/give_splash";
                case "lingering_potion" -> "/give_lingering";
                case "tipped_arrow" -> "/give_arrow";
                default -> "/give";
            };
            pc.functions.put(dir + fn, List.of("give @s " + potionStack(p, bottle) + (bottle.equals("tipped_arrow") ? " 16" : "")));
        }
        if (!realItems && !emulated.isEmpty()) {
            // drinking the bottle applies the project effects that the game cannot carry in the item
            JsonObject pred = ctx.components()
                    ? J.obj("items", "minecraft:potion", "predicates", J.obj("minecraft:custom_data", J.obj("PublicBukkitValues", J.obj(ns + ":potion", p.name))))
                    : J.obj("items", J.arr("minecraft:potion"), "nbt", "{PublicBukkitValues:{\"" + ns + ":potion\":\"" + p.name + "\"}}");
            JsonObject adv = J.obj("criteria", J.obj("drink", J.obj("trigger", "minecraft:consume_item", "conditions", J.obj("item", pred))),
                    "rewards", J.obj("function", fnId(dir + "/drink")));
            ctx.files.json(data(ctx.advDir(), "potion/" + p.name + "_drink.json"), adv);
            List<String> drink = new ArrayList<>(List.of("advancement revoke @s only " + fnId("potion/" + p.name + "_drink")));
            drink.addAll(emulated);
            pc.functions.put(dir + "/drink", drink);
        }
    }

    private static int rgb(String hex) {
        try {
            return Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return 0x3F76E4;
        }
    }

    private String potionName(Potion p, String bottle) {
        String n = p.displayName.isBlank() ? Ids.titleCase(p.name) : p.displayName;
        return switch (bottle) {
            case "splash_potion" -> p.splashName.isBlank() ? "Splash " + n : p.splashName;
            case "lingering_potion" -> p.lingeringName.isBlank() ? "Lingering " + n : p.lingeringName;
            case "tipped_arrow" -> p.arrowName.isBlank() ? "Tipped Arrow (" + n + ")" : p.arrowName;
            default -> n;
        };
    }

    /** Item stack argument for /give of a potion bottle or tipped arrow, usable with Paper's createItemStack too. */
    public String potionStack(Potion p, String bottle) {
        String item = "minecraft:" + bottle;
        String bukkit = "PublicBukkitValues:{\"" + ns + ":potion\":\"" + p.name + "\"}";
        // vanilla effects the bottle can carry (project effects are emulated outside mods)
        List<String> fx = new ArrayList<>();
        if (!realItems) {
            for (PotionEffect pe : p.effects) {
                String f = Ids.full(pe.effect.trim());
                String path = Ids.path(f);
                if (!Ids.ns(f).equals("minecraft") || JavaCommon.vanillaEffectConst(ctx.v, path) == null) continue;
                int ticks = Math.max(1, pe.seconds) * 20, amp = Math.max(0, Math.min(255, pe.amplifier));
                if (ctx.v.atLeast(McVersion.V1_20_4)) {
                    fx.add("{id:\"" + f + "\",duration:" + ticks + ",amplifier:" + amp + "b,show_particles:" + (pe.particles ? "1b" : "0b")
                            + ",show_icon:" + (pe.icon ? "1b" : "0b") + "}");
                } else {
                    fx.add("{Id:" + (Vanilla.EFFECTS.indexOf(f) + 1) + "b,Duration:" + ticks + ",Amplifier:" + amp + "b,ShowParticles:"
                            + (pe.particles ? "1b" : "0b") + ",ShowIcon:" + (pe.icon ? "1b" : "0b") + "}");
                }
            }
        }
        if (ctx.components()) {
            StringBuilder sb = new StringBuilder(item).append("[minecraft:potion_contents={");
            if (realItems) sb.append("potion:\"").append(ctx.id(p.name)).append('"');
            else {
                sb.append("custom_color:").append(rgb(p.color));
                if (!fx.isEmpty()) sb.append(",custom_effects:[").append(String.join(",", fx)).append(']');
            }
            sb.append('}');
            if (!realItems) sb.append(",minecraft:item_name=").append(ItemSpec.textArg(ctx, J.compact(J.obj("text", potionName(p, bottle)))));
            sb.append(",minecraft:custom_data={").append(bukkit).append("}]");
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder(item).append('{');
        if (realItems) sb.append("Potion:\"").append(ctx.id(p.name)).append("\",");
        else {
            sb.append("CustomPotionColor:").append(rgb(p.color)).append(',');
            if (!fx.isEmpty()) sb.append(ctx.v.atLeast(McVersion.V1_20_4) ? "custom_potion_effects:[" : "CustomPotionEffects:[").append(String.join(",", fx)).append("],");
            String name = J.compact(J.obj("text", potionName(p, bottle), "italic", false));
            sb.append("display:{Name:'").append(name.replace("\\", "\\\\").replace("'", "\\'")).append("'},");
        }
        sb.append(bukkit).append('}');
        return sb.toString();
    }

    // ------------------------------------------------------------------ enchantments (1.21+)

    private static JsonObject linear(double base, double perLevel) {
        return J.obj("type", "minecraft:linear", "base", base, "per_level_above_first", perLevel);
    }

    private void enchantment(Enchantment e) {
        if (!ctx.singular()) {
            ctx.note("enchantment " + e.name + ": data driven enchantments need Minecraft 1.21 or later");
            return;
        }
        String dir = "enchantments/" + e.name;
        JsonObject o = new JsonObject();
        o.add("description", J.obj("translate", "enchantment." + ns + "." + e.name, "fallback", e.displayName.isBlank() ? Ids.titleCase(e.name) : e.displayName));
        if (!e.exclusiveWith.isBlank()) o.addProperty("exclusive_set", Ids.full(e.exclusiveWith));
        o.addProperty("supported_items", Ids.full(e.supportedItems.isBlank() ? "#minecraft:enchantable/weapon" : e.supportedItems));
        if (!e.primaryItems.isBlank()) o.addProperty("primary_items", Ids.full(e.primaryItems));
        o.addProperty("weight", Math.max(1, Math.min(1024, e.weight)));
        o.addProperty("max_level", Math.max(1, Math.min(255, e.maxLevel)));
        o.add("min_cost", J.obj("base", e.minCostBase, "per_level_above_first", e.minCostPerLevel));
        o.add("max_cost", J.obj("base", e.maxCostBase, "per_level_above_first", e.maxCostPerLevel));
        o.addProperty("anvil_cost", Math.max(0, e.anvilCost));
        o.add("slots", J.arr(e.slot.isBlank() ? "any" : e.slot));
        JsonObject effects = new JsonObject();
        if (e.damagePerLevel != 0) {
            effects.add("minecraft:damage", J.arr(J.obj("effect", J.obj("type", "minecraft:add", "value", linear(e.damagePerLevel, e.damagePerLevel)))));
        }
        if (e.protectionPerLevel != 0) {
            effects.add("minecraft:damage_protection", J.arr(J.obj(
                    "effect", J.obj("type", "minecraft:add", "value", linear(e.protectionPerLevel, e.protectionPerLevel)),
                    "requirements", J.obj("condition", "minecraft:damage_source_properties",
                            "predicate", J.obj("tags", J.arr(J.obj("id", "minecraft:bypasses_invulnerability", "expected", false)))))));
        }
        if (!e.attribute.isBlank() && e.attributePerLevel != 0) {
            String a = e.attribute.contains(":") ? Ids.path(e.attribute) : e.attribute;
            effects.add("minecraft:attributes", J.arr(J.obj("id", ns + ":enchantment." + e.name, "attribute",
                    "minecraft:" + (ctx.registryKeys() ? a : "generic." + a), "amount", linear(e.attributePerLevel, e.attributePerLevel),
                    "operation", e.attributeOperation.isBlank() ? "add_value" : e.attributeOperation)));
        }
        if (!e.onHit.isEmpty()) {
            pc.compile(dir + "/on_hit", e.onHit, List.of(), List.of());
            effects.add("minecraft:post_attack", J.arr(J.obj("enchanted", "attacker", "affected", "victim",
                    "effect", J.obj("type", "minecraft:run_function", "function", fnId(dir + "/on_hit")))));
        }
        if (!e.onTick.isEmpty()) {
            pc.compile(dir + "/tick", e.onTick, List.of(), List.of());
            effects.add("minecraft:tick", J.arr(J.obj("effect", J.obj("type", "minecraft:run_function", "function", fnId(dir + "/tick")))));
        }
        o.add("effects", effects);
        ctx.files.json(data("enchantment", e.name + ".json"), o);
        String id = ctx.id(e.name);
        if (e.curse) tags.add("enchantment", "minecraft", "curse", id);
        if (e.treasure) {
            tags.add("enchantment", "minecraft", "treasure", id);
        } else {
            tags.add("enchantment", "minecraft", "in_enchanting_table", id);
            tags.add("enchantment", "minecraft", "non_treasure", id);
        }
        tags.add("enchantment", "minecraft", "on_random_loot", id);
        tags.add("enchantment", "minecraft", "tradeable", id);
    }

    // ------------------------------------------------------------------ damage types

    private void damageType(DamageType d) {
        JsonObject o = J.obj("message_id", ns + "." + d.name, "exhaustion", d.exhaustion,
                "scaling", d.scaling.isBlank() ? "when_caused_by_living_non_player" : d.scaling);
        if (!d.effects.isBlank() && !d.effects.equals("hurt")) o.addProperty("effects", d.effects);
        ctx.files.json(data("damage_type", d.name + ".json"), o);
        String id = ctx.id(d.name);
        if (d.bypassesArmor) tags.add("damage_type", "minecraft", "bypasses_armor", id);
        if (d.bypassesShield) tags.add("damage_type", "minecraft", "bypasses_shield", id);
        if (d.fire) tags.add("damage_type", "minecraft", "is_fire", id);
        if (d.projectile) tags.add("damage_type", "minecraft", "is_projectile", id);
        if (d.explosion) tags.add("damage_type", "minecraft", "is_explosion", id);
        if (d.noKnockback) tags.add("damage_type", "minecraft", "no_knockback", id);
        if (d.magic) tags.add("damage_type", "minecraft", "witch_resistant_to", id);
    }

    // ------------------------------------------------------------------ paintings and songs

    private void painting(Painting p) {
        int w = Math.max(1, Math.min(16, p.width)), h = Math.max(1, Math.min(16, p.height));
        if (ctx.singular()) {
            JsonObject o = J.obj("asset_id", ctx.id(p.name), "width", w, "height", h);
            if (ctx.registryKeys()) {
                o.add("title", J.obj("translate", "painting." + ns + "." + p.name + ".title", "color", "yellow"));
                o.add("author", J.obj("translate", "painting." + ns + "." + p.name + ".author", "color", "gray"));
            }
            ctx.files.json(data("painting_variant", p.name + ".json"), o);
        } else if (!realItems) {
            ctx.note("painting " + p.name + ": data pack paintings need Minecraft 1.21 or later");
            return;
        }
        if (p.placeable) tags.add("painting_variant", "minecraft", "placeable", ctx.id(p.name));
    }

    private void song(Song s) {
        if (!ctx.singular()) {
            ctx.note("song " + s.name + ": jukebox songs need Minecraft 1.21 or later");
            return;
        }
        String sound = s.sound.isBlank() ? "minecraft:music_disc.cat" : ctx.p.byName(ElementType.SOUND, s.sound) != null ? ctx.id(s.sound) : Ids.full(s.sound);
        JsonObject o = J.obj("sound_event", sound,
                "description", J.obj("translate", "jukebox_song." + ns + "." + s.name),
                "length_in_seconds", Math.max(1, s.lengthSeconds),
                "comparator_output", Math.max(0, Math.min(15, s.comparatorOutput)));
        ctx.files.json(data("jukebox_song", s.name + ".json"), o);
    }

    /** Command lines that apply an emulated status effect (data packs and plugins). */
    public static List<String> applyEmulated(GenContext ctx, StatusEffect e, String selector, int seconds) {
        return List.of("scoreboard players set " + selector + " " + objective(ctx.ns, e.name) + " " + Math.max(1, seconds) * 20,
                "execute as " + selector + " run function " + ctx.ns + ":status/" + e.name + "/start");
    }
}
