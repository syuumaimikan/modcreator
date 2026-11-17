package modstudio.gen;

import com.google.gson.JsonObject;
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
        return b.isBlank() ? "minecraft:paper" : b;
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
        c.addProperty("minecraft:item_name", J.compact(J.obj("text", name(it))));
        String[] lore = lore(it);
        if (lore.length > 0) {
            var arr = new com.google.gson.JsonArray();
            for (String l : lore) arr.add(textJson(l, "gray"));
            c.add("minecraft:lore", arr);
        }
        for (var e : modelComponent(ctx, it).entrySet()) c.add(e.getKey(), e.getValue());
        if (it.glint) c.addProperty("minecraft:enchantment_glint_override", true);
        if (!it.isTool() && it.maxStack != 64) c.addProperty("minecraft:max_stack_size", Math.max(1, Math.min(99, it.maxStack)));
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
            if (!it.isTool() && it.maxStack != 64) sb.append(",minecraft:max_stack_size=").append(Math.max(1, Math.min(99, it.maxStack)));
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
