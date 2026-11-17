package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import modstudio.core.Elements;
import modstudio.core.Ids;
import modstudio.core.Systems.Trade;
import modstudio.core.Systems.Trader;

import java.util.Map;

/**
 * The summon command of a trader: villager data, a fixed offer list and the name. Item stacks in the offers use
 * Count and tag before 1.20.5 and count and components after; custom items on data packs and Paper are matched by
 * their custom data when bought and carry all their components when sold.
 */
public final class TraderGen {
    private TraderGen() {}

    public static String summon(GenContext ctx, Trader t, boolean customItems) {
        StringBuilder sb = new StringBuilder();
        boolean villager = !t.entity.equals("wandering_trader");
        sb.append("summon minecraft:").append(villager ? "villager" : "wandering_trader").append(" ~ ~ ~ {");
        sb.append("PersistenceRequired:1b");
        if (t.noAi) sb.append(",NoAI:1b");
        if (t.invulnerable) sb.append(",Invulnerable:1b");
        if (t.silent) sb.append(",Silent:1b");
        if (villager) {
            String prof = Trader.PROFESSIONS.contains(t.profession) ? t.profession : "none";
            String type = Trader.VILLAGER_TYPES.contains(t.villagerType) ? t.villagerType : "plains";
            int level = Math.max(1, Math.min(5, t.level));
            sb.append(",VillagerData:{profession:\"minecraft:").append(prof).append("\",level:").append(level)
                    .append(",type:\"minecraft:").append(type).append("\"}");
            // a villager with experience keeps its profession without a workstation
            sb.append(",Xp:").append(Math.max(1, xpFor(level)));
        } else sb.append(",DespawnDelay:0");
        if (!t.displayName.isBlank()) {
            JsonObject name = PackDataGen.text(t.displayName, t.nameColor);
            sb.append(",CustomName:").append(ItemSpec.textArg(ctx, J.compact(name)));
        }
        sb.append(",Offers:{Recipes:[");
        boolean first = true;
        for (Trade tr : t.trades) {
            if (tr.buy.isBlank() || tr.sell.isBlank()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{buy:").append(stack(ctx, tr.buy, tr.buyCount, customItems, true));
            if (!tr.buyB.isBlank()) sb.append(",buyB:").append(stack(ctx, tr.buyB, tr.buyBCount, customItems, true));
            sb.append(",sell:").append(stack(ctx, tr.sell, tr.sellCount, customItems, false));
            sb.append(",maxUses:").append(Math.max(1, tr.maxUses));
            sb.append(",xp:").append(Math.max(0, tr.xp));
            sb.append(",priceMultiplier:").append((float) Math.max(0, tr.priceMultiplier)).append('f');
            sb.append(",rewardExp:").append(tr.rewardExp ? "1b" : "0b");
            sb.append('}');
        }
        sb.append("]}}");
        return sb.toString();
    }

    /** Lowest experience of a villager level, so the level shown matches the experience bar. */
    private static int xpFor(int level) {
        return switch (level) {
            case 2 -> 10;
            case 3 -> 70;
            case 4 -> 150;
            case 5 -> 250;
            default -> 0;
        };
    }

    private static String stack(GenContext ctx, String id, int count, boolean customItems, boolean cost) {
        String f = Ids.full(id.trim());
        int n = Math.max(1, Math.min(99, count));
        Elements.Item it = customItems && ctx.p.itemLike(f) instanceof Elements.Item x ? x : null;
        if (ctx.components()) {
            StringBuilder sb = new StringBuilder("{id:\"").append(it != null ? ItemSpec.base(it) : f).append("\",count:").append(n);
            if (it != null) {
                // a price matches the custom data only; the sold item gets every component
                JsonObject comps = cost
                        ? J.obj("minecraft:custom_data", J.obj("PublicBukkitValues", J.obj(ctx.ns + ":item_id", it.name)))
                        : ItemSpec.components(ctx, it);
                sb.append(",components:").append(snbt(comps));
            }
            return sb.append('}').toString();
        }
        StringBuilder sb = new StringBuilder("{id:\"").append(it != null ? ItemSpec.base(it) : f).append("\",Count:").append(n).append('b');
        if (it != null) {
            String tag = cost ? ItemSpec.nbtTag(ctx, it) : ItemSpec.give(ctx, it).substring(ItemSpec.base(it).length());
            sb.append(",tag:").append(tag);
        }
        return sb.append('}').toString();
    }

    /** JSON to SNBT; numbers keep their JSON form (codecs read any numeric tag), keys are quoted when needed. */
    public static String snbt(JsonElement e) {
        if (e == null || e.isJsonNull()) return "{}";
        if (e.isJsonObject()) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                if (!first) sb.append(',');
                first = false;
                String k = en.getKey();
                sb.append(k.matches("[A-Za-z0-9._+-]+") ? k : J.quote(k)).append(':').append(snbt(en.getValue()));
            }
            return sb.append('}').toString();
        }
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(snbt(a.get(i)));
            }
            return sb.append(']').toString();
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean() ? "true" : "false";
        if (p.isNumber()) return p.getAsNumber().toString();
        return J.quote(p.getAsString());
    }
}
