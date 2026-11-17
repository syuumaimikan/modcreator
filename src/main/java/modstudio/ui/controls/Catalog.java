package modstudio.ui.controls;

import modstudio.core.*;
import modstudio.i18n.I18n;
import modstudio.ui.Workspace;

import java.util.ArrayList;
import java.util.List;

/** Picker entries for each kind of id (project content first, then vanilla). */
public final class Catalog {
    private Catalog() {}

    public static List<Picker.Entry> entries(Workspace ws, IdField.Kind kind) {
        List<Picker.Entry> r = new ArrayList<>();
        Project p = ws.project;
        String ns = p.meta.modId;
        String project = I18n.t("picker.group_project");
        String vanilla = I18n.t("picker.group_minecraft");
        String tags = I18n.t("picker.group_tags");
        switch (kind) {
            case ITEM, ITEM_OR_TAG -> {
                for (Elements.Item it : p.all(Elements.Item.class))
                    r.add(new Picker.Entry(ns + ":" + it.name, label(it.displayName, it.name), ws.texture(it.texture, 32), project));
                for (Elements.Block b : p.all(Elements.Block.class))
                    r.add(new Picker.Entry(ns + ":" + b.name, label(b.displayName, b.name), ws.itemImage(ns + ":" + b.name, 32), project));
                for (String s : Vanilla.allItems()) r.add(vanilla(s, vanilla));
                if (kind == IdField.Kind.ITEM_OR_TAG) {
                    for (Elements.Tag t : p.all(Elements.Tag.class)) {
                        if (t.registry.equals("item")) r.add(new Picker.Entry("#" + tagNs(t, ns) + ":" + t.name, "#" + t.name, null, project));
                    }
                    for (String s : Vanilla.ITEM_TAGS) r.add(new Picker.Entry(s, s, null, tags));
                }
            }
            case BLOCK, BLOCK_OR_TAG -> {
                for (Elements.Block b : p.all(Elements.Block.class))
                    r.add(new Picker.Entry(ns + ":" + b.name, label(b.displayName, b.name), ws.itemImage(ns + ":" + b.name, 32), project));
                for (String s : Vanilla.BLOCKS) r.add(vanilla(s, vanilla));
                if (kind == IdField.Kind.BLOCK_OR_TAG) {
                    for (Elements.Tag t : p.all(Elements.Tag.class)) {
                        if (t.registry.equals("block")) r.add(new Picker.Entry("#" + tagNs(t, ns) + ":" + t.name, "#" + t.name, null, project));
                    }
                    for (String s : Vanilla.BLOCK_TAGS) r.add(new Picker.Entry(s, s, null, tags));
                }
            }
            case ENTITY -> Vanilla.ENTITIES.forEach(s -> r.add(vanilla(s, vanilla)));
            case EFFECT -> {
                for (Elements.StatusEffect s : p.all(Elements.StatusEffect.class))
                    r.add(new Picker.Entry(ns + ":" + s.name, label(s.displayName, s.name), null, project));
                Vanilla.EFFECTS.forEach(s -> r.add(vanilla(s, vanilla)));
            }
            case PARTICLE -> Vanilla.PARTICLES.forEach(s -> r.add(vanilla(s, vanilla)));
            case ENCHANTMENT -> {
                for (Elements.Enchantment s : p.all(Elements.Enchantment.class))
                    r.add(new Picker.Entry(ns + ":" + s.name, label(s.displayName, s.name), null, project));
                Vanilla.ENCHANTMENTS.forEach(s -> r.add(vanilla(s, vanilla)));
            }
            case DAMAGE_TYPE -> {
                for (Elements.DamageType s : p.all(Elements.DamageType.class))
                    r.add(new Picker.Entry(ns + ":" + s.name, s.name, null, project));
                Vanilla.DAMAGE_TYPES.forEach(s -> r.add(vanilla(s, vanilla)));
            }
            case DIMENSION -> Vanilla.DIMENSIONS.forEach(s -> r.add(vanilla(s, vanilla)));
            case SOUND -> {
                for (Elements.Sound s : p.all(Elements.Sound.class))
                    r.add(new Picker.Entry(ns + ":" + s.name, s.name, null, project));
                Vanilla.SOUNDS.forEach(s -> r.add(new Picker.Entry(Ids.full(s), Ids.path(s), null, vanilla)));
            }
            case FUNCTION -> {
                for (Elements.Function f : p.all(Elements.Function.class))
                    r.add(new Picker.Entry(ns + ":" + f.name, f.name, null, project));
                for (Elements.Procedure f : p.all(Elements.Procedure.class))
                    r.add(new Picker.Entry(ns + ":procedures/" + f.name, f.name, null, I18n.t("types.procedure")));
            }
            case ADVANCEMENT -> {
                for (Elements.Advancement a : p.all(Elements.Advancement.class))
                    r.add(new Picker.Entry(ns + ":" + a.name, label(a.title, a.name), null, project));
                for (String s : List.of("minecraft:story/root", "minecraft:story/mine_stone", "minecraft:story/smelt_iron",
                        "minecraft:story/mine_diamond", "minecraft:nether/root", "minecraft:end/root",
                        "minecraft:adventure/root", "minecraft:husbandry/root")) r.add(new Picker.Entry(s, s, null, vanilla));
            }
            case LOOT_TABLE -> {
                for (Elements.LootTable l : p.all(Elements.LootTable.class))
                    r.add(new Picker.Entry(ns + ":" + l.name, l.name, null, project));
                for (String s : List.of("minecraft:chests/simple_dungeon", "minecraft:chests/village/village_weaponsmith",
                        "minecraft:chests/abandoned_mineshaft", "minecraft:chests/desert_pyramid", "minecraft:chests/jungle_temple",
                        "minecraft:chests/end_city_treasure", "minecraft:chests/buried_treasure", "minecraft:gameplay/fishing",
                        "minecraft:entities/zombie", "minecraft:entities/skeleton", "minecraft:entities/creeper",
                        "minecraft:blocks/stone", "minecraft:blocks/dirt")) r.add(new Picker.Entry(s, s, null, vanilla));
            }
            case RECIPE -> {
                for (Elements.Recipe rc : p.all(Elements.Recipe.class))
                    r.add(new Picker.Entry(ns + ":" + rc.name, rc.name, null, project));
            }
            case ANY -> {
            }
        }
        return r;
    }

    private static Picker.Entry vanilla(String id, String group) {
        return new Picker.Entry(Ids.full(id), Ids.titleCase(Ids.path(id)), null, group);
    }

    private static String label(String display, String name) {
        return display == null || display.isBlank() ? Ids.titleCase(name) : display;
    }

    public static String tagNs(Elements.Tag t, String ns) {
        return t.namespace == null || t.namespace.isBlank() ? ns : t.namespace.trim();
    }

    /** Picker kind for a tag registry's values. */
    public static IdField.Kind forRegistry(String registry) {
        return switch (registry) {
            case "item" -> IdField.Kind.ITEM_OR_TAG;
            case "block" -> IdField.Kind.BLOCK_OR_TAG;
            case "entity_type" -> IdField.Kind.ENTITY;
            case "function" -> IdField.Kind.FUNCTION;
            default -> IdField.Kind.ANY;
        };
    }

    /** Picker kind for a procedure parameter type. */
    public static IdField.Kind forParam(Logic.PType t) {
        return switch (t) {
            case ITEM -> IdField.Kind.ITEM;
            case BLOCK -> IdField.Kind.BLOCK;
            case ENTITY -> IdField.Kind.ENTITY;
            case EFFECT -> IdField.Kind.EFFECT;
            case SOUND -> IdField.Kind.SOUND;
            case PARTICLE -> IdField.Kind.PARTICLE;
            case FUNCTION -> IdField.Kind.FUNCTION;
            case DIMENSION -> IdField.Kind.DIMENSION;
            case ENCHANTMENT -> IdField.Kind.ENCHANTMENT;
            case DAMAGE_TYPE -> IdField.Kind.DAMAGE_TYPE;
            default -> IdField.Kind.ANY;
        };
    }
}
