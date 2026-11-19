package modstudio.gen;

import com.google.gson.JsonObject;
import modstudio.core.ElementType;
import modstudio.core.Elements;
import modstudio.core.Ids;

/**
 * Representation of project items on platforms without real custom items (datapacks, plugins):
 * a vanilla base item carrying a name, lore, custom model data and an identifying tag.
 */
public final class ItemSpec {
    private ItemSpec() {}

    public static int modelData(Elements.Item it) {
        if (it.customModelData > 0) return it.customModelData;
        return 1_000_000 + Math.floorMod(it.name.hashCode(), 8_999_999);
    }

    public static String base(Elements.Item it) {
        String b = Ids.full(it.baseItem);
        // armor needs a wearable base: leather pieces unless another base was chosen
        if (it.isArmor() && (b.isBlank() || b.equals("minecraft:paper"))) return "minecraft:leather_" + it.kind;
        return b.isBlank() ? "minecraft:paper" : b;
    }

    /** Jukebox song id of a music disc item (1.21+), or null. */
    private static String song(GenContext ctx, Elements.Item it) {
        if (it.song.isBlank() || !ctx.singular() || ctx.p.byName(ElementType.SONG, it.song) == null) return null;
        return ctx.id(it.song);
    }

    // ------------------------------------------------------------------ armor

    private static Elements.ArmorMaterial armor(GenContext ctx, Elements.Item it) {
        return it.isArmor() && ctx.p.byName(ElementType.ARMOR_MATERIAL, it.armorMaterial) instanceof Elements.ArmorMaterial m ? m : null;
    }

    private static String slot(String kind) {
        return switch (kind) {
            case "helmet" -> "head";
            case "chestplate" -> "chest";
            case "leggings" -> "legs";
            default -> "feet";
        };
    }

    /** Attribute id: the generic. prefix was dropped in 1.21.2. */
    private static String attribute(GenContext ctx, String name) {
        return "minecraft:" + (ctx.registryKeys() ? name : "generic." + name);
    }

    /** Attribute name, amount pairs of an armor piece. */
    private static java.util.List<Object[]> armorAttributes(Elements.ArmorMaterial m, String kind) {
        java.util.List<Object[]> r = new java.util.ArrayList<>();
        r.add(new Object[]{"armor", (double) m.defense(kind)});
        if (m.toughness > 0) r.add(new Object[]{"armor_toughness", m.toughness});
        if (m.knockbackResistance > 0) r.add(new Object[]{"knockback_resistance", m.knockbackResistance});
        return r;
    }

    private static int[] uuid(GenContext ctx, Elements.Item it, String attr) {
        java.util.UUID u = java.util.UUID.nameUUIDFromBytes((ctx.ns + ":" + it.name + ":" + attr).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long a = u.getMostSignificantBits(), b = u.getLeastSignificantBits();
        return new int[]{(int) (a >> 32), (int) a, (int) (b >> 32), (int) b};
    }

    private static String sound(Elements.ArmorMaterial m) {
        String s = switch (m.equipSound) {
            case "leather", "chain", "gold", "diamond", "netherite", "turtle", "generic" -> m.equipSound;
            default -> "iron";
        };
        return "minecraft:item.armor.equip_" + s;
    }

    public record ToolTierStats(int durability, float speed, float attackDamageBonus, int enchantability, int miningLevel, String repairItem) {}

    public static ToolTierStats toolStats(modstudio.core.Project p, String tier) {
        Elements.ToolMaterial tm = p == null ? null : p.toolMaterial(tier);
        if (tm != null) {
            return new ToolTierStats(Math.max(1, tm.durability), (float) Math.max(0.5, tm.speed),
                    (float) tm.attackDamageBonus, Math.max(1, tm.enchantability),
                    Math.max(0, Math.min(4, tm.miningLevel)), tm.repairItem == null ? "" : tm.repairItem);
        }
        return switch (tier == null ? "" : tier) {
            case "wood" -> new ToolTierStats(59, 2.0f, 0.0f, 15, 0, "#minecraft:wooden_tool_materials");
            case "stone" -> new ToolTierStats(131, 4.0f, 1.0f, 5, 1, "#minecraft:stone_tool_materials");
            case "gold" -> new ToolTierStats(32, 12.0f, 0.0f, 22, 0, "#minecraft:gold_tool_materials");
            case "diamond" -> new ToolTierStats(1561, 8.0f, 3.0f, 10, 3, "#minecraft:diamond_tool_materials");
            case "netherite" -> new ToolTierStats(2031, 9.0f, 4.0f, 15, 4, "#minecraft:netherite_tool_materials");
            default -> new ToolTierStats(250, 6.0f, 2.0f, 14, 2, "#minecraft:iron_tool_materials");
        };
    }

    /** Vanilla/tag repair target for the item/material, or null (project items do not exist as real items here). */
    private static String repair(GenContext ctx, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String r = Ids.full(raw);
        if (!Ids.isTag(r) && ctx.p.itemLike(r) != null) return null;
        return r;
    }

    private static JsonObject attrJson(GenContext ctx, Elements.Item it, String key, String name, String slot, double amount) {
        JsonObject e = J.obj("type", attribute(ctx, name), "slot", slot, "amount", amount, "operation", "add_value");
        if (ctx.singular()) e.addProperty("id", ctx.ns + ":" + key + "." + it.name + "." + name);
        else {
            int[] u = uuid(ctx, it, name);
            e.add("uuid", J.arr(u[0], u[1], u[2], u[3]));
            e.addProperty("name", ctx.ns + "." + key);
        }
        return e;
    }

    private static String attrSnbt(GenContext ctx, Elements.Item it, String key, String name, String slot, double amount) {
        int[] u = uuid(ctx, it, name);
        String uuid = "[I;" + u[0] + "," + u[1] + "," + u[2] + "," + u[3] + "]";
        if (!ctx.components()) {
            return "{AttributeName:\"generic." + name + "\",Name:\"" + ctx.ns + "." + key + "\",Amount:" + amount
                    + "d,Operation:0,UUID:" + uuid + ",Slot:\"" + slot + "\"}";
        }
        if (!ctx.singular()) {
            return "{type:\"" + attribute(ctx, name) + "\",slot:\"" + slot + "\",uuid:" + uuid
                    + ",name:\"" + ctx.ns + "." + key + "\",amount:" + amount + "d,operation:\"add_value\"}";
        }
        return "{type:\"" + attribute(ctx, name) + "\",slot:\"" + slot + "\",id:\"" + ctx.ns + ":" + key + "." + it.name + "." + name
                + "\",amount:" + amount + "d,operation:\"add_value\"}";
    }

    private static void appendAttrModifiers(GenContext ctx, Elements.Item it, java.util.List<String> mods, StringBuilder sb) {
        String list = "[" + String.join(",", mods) + "]";
        if (!ctx.components()) {
            sb.append(",AttributeModifiers:").append(list);
            return;
        }
        sb.append(",minecraft:attribute_modifiers=").append(ctx.v.snbtText() ? list : "{modifiers:" + list + "}");
    }

    /** Armor data components as JSON (1.20.5+). */
    private static void armorComponents(GenContext ctx, Elements.Item it, JsonObject c) {
        Elements.ArmorMaterial m = armor(ctx, it);
        if (m == null) return;
        com.google.gson.JsonArray mods = new com.google.gson.JsonArray();
        for (Object[] a : armorAttributes(m, it.kind)) {
            mods.add(attrJson(ctx, it, "armor", (String) a[0], slot(it.kind), (double) a[1]));
        }
        if (ctx.v.snbtText()) c.add("minecraft:attribute_modifiers", mods);
        else c.add("minecraft:attribute_modifiers", J.obj("modifiers", mods));
        c.addProperty("minecraft:max_damage", m.durabilityOf(it.kind));
        if (ctx.registryKeys()) {
            c.add("minecraft:equippable", J.obj("slot", slot(it.kind), "asset_id", ctx.id(m.name), "equip_sound", sound(m)));
            c.add("minecraft:enchantable", J.obj("value", Math.max(1, m.enchantability)));
            String r = repair(ctx, m.repairItem);
            if (r != null) c.add("minecraft:repairable", J.obj("items", r));
        }
    }

    /** Tool data components as JSON (1.20.5+). */
    private static void toolComponents(GenContext ctx, Elements.Item it, JsonObject c) {
        if (!it.isTool()) return;
        ToolTierStats ts = toolStats(ctx.p, it.tier);
        com.google.gson.JsonArray mods = new com.google.gson.JsonArray();
        mods.add(attrJson(ctx, it, "tool", "attack_damage", "mainhand", Math.max(0.5, it.attackDamage + ts.attackDamageBonus())));
        mods.add(attrJson(ctx, it, "tool", "attack_speed", "mainhand", it.attackSpeed));
        if (ctx.v.snbtText()) c.add("minecraft:attribute_modifiers", mods);
        else c.add("minecraft:attribute_modifiers", J.obj("modifiers", mods));
        c.addProperty("minecraft:max_damage", it.maxDamage > 0 ? it.maxDamage : ts.durability());
        if (!it.kind.equals("sword")) {
            String blockTag = "#minecraft:mineable/" + it.kind;
            c.add("minecraft:tool", J.obj("rules", J.arr(J.obj("blocks", blockTag, "speed", ts.speed(), "correct_for_drops", true)), "damage_per_block", 1));
        }
        if (ctx.registryKeys()) {
            int ench = it.enchantability >= 0 ? it.enchantability : ts.enchantability();
            c.add("minecraft:enchantable", J.obj("value", Math.max(1, ench)));
            String r = repair(ctx, !it.repairItem.isBlank() ? it.repairItem : ts.repairItem());
            if (r != null) c.add("minecraft:repairable", J.obj("items", r));
        }
    }

    /** Armor components or NBT inside an item stack argument. */
    private static void armorArg(GenContext ctx, Elements.Item it, StringBuilder sb) {
        Elements.ArmorMaterial m = armor(ctx, it);
        if (m == null) return;
        java.util.List<String> mods = new java.util.ArrayList<>();
        for (Object[] a : armorAttributes(m, it.kind)) {
            mods.add(attrSnbt(ctx, it, "armor", (String) a[0], slot(it.kind), (double) a[1]));
        }
        appendAttrModifiers(ctx, it, mods, sb);
        if (!ctx.components()) return;
        sb.append(",minecraft:max_damage=").append(m.durabilityOf(it.kind));
        if (ctx.registryKeys()) {
            sb.append(",minecraft:equippable={slot:\"").append(slot(it.kind)).append("\",asset_id:\"").append(ctx.id(m.name))
                    .append("\",equip_sound:\"").append(sound(m)).append("\"}");
            sb.append(",minecraft:enchantable={value:").append(Math.max(1, m.enchantability)).append('}');
            String r = repair(ctx, m.repairItem);
            if (r != null) sb.append(",minecraft:repairable={items:\"").append(r).append("\"}");
        }
    }

    /** Tool components or NBT inside an item stack argument. */
    private static void toolArg(GenContext ctx, Elements.Item it, StringBuilder sb) {
        if (!it.isTool()) return;
        ToolTierStats ts = toolStats(ctx.p, it.tier);
        java.util.List<String> mods = java.util.List.of(
                attrSnbt(ctx, it, "tool", "attack_damage", "mainhand", Math.max(0.5, it.attackDamage + ts.attackDamageBonus())),
                attrSnbt(ctx, it, "tool", "attack_speed", "mainhand", it.attackSpeed));
        appendAttrModifiers(ctx, it, mods, sb);
        if (!ctx.components()) return;
        sb.append(",minecraft:max_damage=").append(it.maxDamage > 0 ? it.maxDamage : ts.durability());
        if (!it.kind.equals("sword")) {
            sb.append(",minecraft:tool={rules:[{blocks:\"#minecraft:mineable/").append(it.kind)
                    .append("\",speed:").append(Src.f(ts.speed())).append(",correct_for_drops:true}],damage_per_block:1}");
        }
        if (ctx.registryKeys()) {
            int ench = it.enchantability >= 0 ? it.enchantability : ts.enchantability();
            sb.append(",minecraft:enchantable={value:").append(Math.max(1, ench)).append('}');
            String r = repair(ctx, !it.repairItem.isBlank() ? it.repairItem : ts.repairItem());
            if (r != null) sb.append(",minecraft:repairable={items:\"").append(r).append("\"}");
        }
    }

    public static String rarityColor(String rarity) {
        return switch (rarity) {
            case "uncommon" -> "yellow";
            case "rare" -> "aqua";
            case "epic" -> "light_purple";
            default -> "white";
        };
    }

    private static String name(Elements.Item it) {
        return it.displayName.isBlank() ? Ids.titleCase(it.name) : it.displayName;
    }

    private static String[] lore(Elements.Item it) {
        String all = it.shiftTooltip.isBlank() ? it.tooltip : it.tooltip.isBlank() ? it.shiftTooltip : it.tooltip + "\n" + it.shiftTooltip;
        return all.isBlank() ? new String[0] : all.split("\\R");
    }

    private static String textJson(String text, String color) {
        return J.compact(J.obj("text", GenContext.legacy(text), "italic", false, "color", color));
    }

    /** Component selecting the item's model: item_model (1.21.4+) or custom_model_data. */
    public static JsonObject modelComponent(GenContext ctx, Elements.Item it) {
        if (ctx.v.itemDefinitions()) return J.obj("minecraft:item_model", ctx.id(it.name));
        return J.obj("minecraft:custom_model_data", modelData(it));
    }

    /** 1.20.5+ data components as JSON (for recipe results). */
    public static JsonObject components(GenContext ctx, Elements.Item it) {
        JsonObject c = new JsonObject();
        // text components are JSON strings until 1.21.4 and plain JSON objects from 1.21.5 (a string would be literal text)
        boolean objects = ctx.v.snbtText();
        if (objects) c.add("minecraft:item_name", J.obj("text", name(it)));
        else c.addProperty("minecraft:item_name", J.compact(J.obj("text", name(it))));
        String[] lore = lore(it);
        if (lore.length > 0) {
            var arr = new com.google.gson.JsonArray();
            for (String l : lore) {
                if (objects) arr.add(J.obj("text", GenContext.legacy(l), "italic", false, "color", "gray"));
                else arr.add(textJson(l, "gray"));
            }
            c.add("minecraft:lore", arr);
        }
        for (var e : modelComponent(ctx, it).entrySet()) c.add(e.getKey(), e.getValue());
        if (it.glint) c.addProperty("minecraft:enchantment_glint_override", true);
        if (it.customTooltip && ctx.v.atLeast(modstudio.core.McVersion.V1_21_4)) c.addProperty("minecraft:tooltip_style", ctx.id(it.name));
        if (song(ctx, it) != null) {
            if (ctx.v.snbtText()) c.addProperty("minecraft:jukebox_playable", song(ctx, it));
            else c.add("minecraft:jukebox_playable", J.obj("song", song(ctx, it)));
        }
        if (!it.damageable() && it.maxStack != 64) c.addProperty("minecraft:max_stack_size", Math.max(1, Math.min(99, it.maxStack)));
        armorComponents(ctx, it, c);
        toolComponents(ctx, it, c);
        if (!it.rarity.equals("common")) c.addProperty("minecraft:rarity", it.rarity);
        if (it.fireResistant) {
            if (ctx.registryKeys()) c.add("minecraft:damage_resistant", J.obj("types", "#minecraft:is_fire"));
            else c.add("minecraft:fire_resistant", new JsonObject());
        }
        if (it.kind.equals("food")) {
            float sat = (float) (it.nutrition * it.saturation * 2.0);
            if (ctx.registryKeys()) {
                c.add("minecraft:food", J.obj("nutrition", it.nutrition, "saturation", sat, "can_always_eat", it.alwaysEdible));
                c.add("minecraft:consumable", J.obj("consume_seconds", it.fastFood ? 0.8f : 1.6f));
            } else {
                c.add("minecraft:food", J.obj("nutrition", it.nutrition, "saturation", sat,
                        "can_always_eat", it.alwaysEdible, "eat_seconds", it.fastFood ? 0.8f : 1.6f));
            }
        }
        c.add("minecraft:custom_data", J.obj("PublicBukkitValues", J.obj(ctx.ns + ":item_id", it.name)));
        return c;
    }

    private static String snbtStr(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** Text component value inside an item stack argument: SNBT from 1.21.5, a JSON string before. */
    public static String textArg(GenContext ctx, String json) {
        return ctx.v.snbtText() ? json : snbtStr(json);
    }

    /** Item stack argument for /give. */
    public static String give(GenContext ctx, Elements.Item it) {
        String base = base(it);
        if (ctx.components()) {
            StringBuilder sb = new StringBuilder(base).append('[');
            sb.append("minecraft:item_name=").append(textArg(ctx, J.compact(J.obj("text", name(it)))));
            String[] lore = lore(it);
            if (lore.length > 0) {
                sb.append(",minecraft:lore=[");
                for (int i = 0; i < lore.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(textArg(ctx, textJson(lore[i], "gray")));
                }
                sb.append(']');
            }
            if (ctx.v.itemDefinitions()) sb.append(",minecraft:item_model=\"").append(ctx.id(it.name)).append('"');
            else sb.append(",minecraft:custom_model_data=").append(modelData(it));
            if (it.glint) sb.append(",minecraft:enchantment_glint_override=true");
            if (it.customTooltip && ctx.v.atLeast(modstudio.core.McVersion.V1_21_4)) sb.append(",minecraft:tooltip_style=\"").append(ctx.id(it.name)).append('"');
            if (song(ctx, it) != null) {
                sb.append(",minecraft:jukebox_playable=").append(ctx.v.snbtText() ? "\"" + song(ctx, it) + "\"" : "{song:\"" + song(ctx, it) + "\"}");
            }
            if (!it.damageable() && it.maxStack != 64) sb.append(",minecraft:max_stack_size=").append(Math.max(1, Math.min(99, it.maxStack)));
            armorArg(ctx, it, sb);
            toolArg(ctx, it, sb);
            if (!it.rarity.equals("common")) sb.append(",minecraft:rarity=\"").append(it.rarity).append('"');
            if (it.fireResistant) {
                sb.append(ctx.registryKeys() ? ",minecraft:damage_resistant={types:\"#minecraft:is_fire\"}" : ",minecraft:fire_resistant={}");
            }
            if (it.kind.equals("food")) {
                sb.append(",minecraft:food={nutrition:").append(it.nutrition)
                        .append(",saturation:").append((float) (it.nutrition * it.saturation * 2.0)).append("f")
                        .append(",can_always_eat:").append(it.alwaysEdible);
                if (ctx.registryKeys()) {
                    sb.append("},minecraft:consumable={consume_seconds:").append(it.fastFood ? "0.8f" : "1.6f").append('}');
                } else {
                    sb.append(",eat_seconds:").append(it.fastFood ? "0.8f" : "1.6f").append('}');
                }
            }
            sb.append(",minecraft:custom_data={PublicBukkitValues:{\"").append(ctx.ns).append(":item_id\":\"").append(it.name).append("\"}}");
            return sb.append(']').toString();
        }
        StringBuilder sb = new StringBuilder(base).append("{display:{Name:");
        sb.append(snbtStr(textJson(name(it), rarityColor(it.rarity))));
        String[] lore = lore(it);
        if (lore.length > 0) {
            sb.append(",Lore:[");
            for (int i = 0; i < lore.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(snbtStr(textJson(lore[i], "gray")));
            }
            sb.append(']');
        }
        sb.append("},CustomModelData:").append(modelData(it));
        if (it.glint) sb.append(",Enchantments:[{}]");
        armorArg(ctx, it, sb);
        toolArg(ctx, it, sb);
        sb.append(",PublicBukkitValues:{\"").append(ctx.ns).append(":item_id\":\"").append(it.name).append("\"}}");
        return sb.toString();
    }

    /** Item predicate matching the custom item (for /clear and execute if items). */
    public static String match(GenContext ctx, Elements.Item it) {
        String tag = "{PublicBukkitValues:{\"" + ctx.ns + ":item_id\":\"" + it.name + "\"}}";
        if (ctx.components()) return base(it) + "[minecraft:custom_data~" + tag + "]";
        return base(it) + tag;
    }

    public static String nbtTag(GenContext ctx, Elements.Item it) {
        return "{PublicBukkitValues:{\"" + ctx.ns + ":item_id\":\"" + it.name + "\"}}";
    }
}
