package modstudio.core;

import modstudio.core.Elements.*;
import modstudio.i18n.I18n;

import java.util.*;

/**
 * Compatibility layer report: for every element and every export target, whether it works natively, through a
 * fallback (a shim or a reduced form that ModStudio generates instead) or not at all, with the reason.
 */
public final class Compat {
    private Compat() {}

    public enum Level { FULL, FALLBACK, NONE }

    public record Cell(Level level, List<String> notes) {
        static Cell full() {
            return new Cell(Level.FULL, List.of());
        }
    }

    public record Row(Element element, Map<Target, Cell> cells) {
        public Level worst() {
            Level w = Level.FULL;
            for (Cell c : cells.values()) if (c.level().ordinal() > w.ordinal()) w = c.level();
            return w;
        }
    }

    /** Targets shown as columns: everything the project exports except resource packs (assets only). */
    public static List<Target> columns(Project p) {
        List<Target> r = new ArrayList<>();
        for (Target t : p.targets()) if (t != Target.RESOURCEPACK) r.add(t);
        return r;
    }

    public static List<Row> analyze(Project p) {
        List<Row> rows = new ArrayList<>();
        List<Target> cols = columns(p);
        boolean old = p.mcVersions().contains(McVersion.V1_20_1);
        boolean preComponents = p.mcVersions().stream().anyMatch(v -> !v.atLeast(McVersion.V1_20_6));
        for (Element e : p.elements) {
            Map<Target, Cell> cells = new LinkedHashMap<>();
            for (Target t : cols) {
                Acc a = new Acc();
                rules(p, e, t, old, preComponents, a);
                cells.put(t, new Cell(a.level, List.copyOf(a.notes)));
            }
            rows.add(new Row(e, cells));
        }
        return rows;
    }

    private static final class Acc {
        Level level = Level.FULL;
        final LinkedHashSet<String> notes = new LinkedHashSet<>();

        void fallback(String key, Object... args) {
            if (level == Level.FULL) level = Level.FALLBACK;
            notes.add(I18n.t(key, args));
        }

        void none(String key, Object... args) {
            level = Level.NONE;
            notes.add(I18n.t(key, args));
        }
    }

    private static void rules(Project p, Element e, Target t, boolean old, boolean preComponents, Acc a) {
        boolean mod = t.isMod(), paper = t == Target.PAPER, pack = t == Target.DATAPACK, bedrock = t == Target.BEDROCK;
        switch (e) {
            case Item it -> {
                if (paper || pack) a.fallback("compat.item_cmd");
                if (it.customTooltip) {
                    if (bedrock) a.notes.add(I18n.t("compat.tooltip_bedrock"));
                    else if ((t == Target.FABRIC || paper || pack) && p.mcVersions().stream().anyMatch(v -> !v.atLeast(McVersion.V1_21_4)))
                        a.notes.add(I18n.t("compat.tooltip_old"));
                }
                if (it.isArmor() && (paper || pack) && p.mcVersions().stream().anyMatch(v -> !v.atLeast(McVersion.V1_21_4)))
                    a.fallback("compat.armor_leather");
            }
            case Elements.StatusEffect se -> {
                if (paper || pack) a.fallback("compat.effect_emulated");
                if (bedrock) a.none("compat.java_only");
            }
            case Elements.Potion po -> {
                if (bedrock) a.none("compat.java_only");
                else if (paper || pack) a.fallback("compat.potion_item");
            }
            case Elements.Enchantment en -> {
                if (bedrock) a.none("compat.java_only");
                else if (p.mcVersions().stream().anyMatch(v -> !v.atLeast(McVersion.V1_21_1))) a.fallback("compat.needs_121");
                if (paper) a.notes.add(I18n.t("compat.paper_registry"));
            }
            case Elements.DamageType dt -> {
                if (bedrock) a.none("compat.java_only");
                if (paper) a.notes.add(I18n.t("compat.paper_registry"));
            }
            case Elements.Painting pa -> {
                if (bedrock) a.none("compat.java_only");
                else if ((paper || pack) && p.mcVersions().stream().anyMatch(v -> !v.atLeast(McVersion.V1_21_1))) a.fallback("compat.needs_121");
                if (paper) a.notes.add(I18n.t("compat.paper_registry"));
            }
            case Elements.Song so -> {
                if (bedrock) a.none("compat.java_only");
                else if (p.mcVersions().stream().anyMatch(v -> !v.atLeast(McVersion.V1_21_1))) a.fallback("compat.needs_121");
            }
            case Elements.ArmorMaterial am -> {
                if (paper || pack) {
                    if (p.mcVersions().stream().anyMatch(v -> !v.atLeast(McVersion.V1_21_4))) a.fallback("compat.armor_leather");
                    else a.notes.add(I18n.t("compat.armor_equippable"));
                }
                if (bedrock) a.notes.add(I18n.t("compat.armor_bedrock"));
            }
            case Block b -> {
                if (paper || pack) a.none("compat.block_pack");
                if (bedrock && b.isCrop()) a.none("compat.crop_bedrock");
            }
            case Elements.Model m -> {
                if (paper || pack) a.fallback("compat.model_rp");
            }
            case Systems.Shader sh -> {
                if (bedrock) a.none("compat.shader_bedrock");
                else if (paper || pack) a.fallback("compat.shader_rp");
                if (!bedrock && p.mcVersions().stream().anyMatch(v -> !Shaders.inRange(sh, v))) a.notes.add(I18n.t("compat.shader_versions"));
            }
            case CreativeTab tab -> {
                if (!mod) a.none("compat.mod_only");
            }
            case Sound s -> {
                if (paper || pack) a.fallback("compat.sound_rp");
            }
            case Recipe r when r.recipeType.equals("brewing") -> {
                boolean custom = !Ids.full(r.input.trim()).startsWith("minecraft:") || !Ids.full(r.result.trim()).startsWith("minecraft:");
                if (pack) a.none("compat.brewing_pack");
                else if ((paper || bedrock) && custom) a.none("compat.brewing_custom_potion");
                else if (bedrock && (Ids.path(Ids.full(r.input.trim())).equals("luck") || Ids.path(Ids.full(r.result.trim())).equals("luck"))) a.none("compat.brewing_luck");
            }
            case Recipe r -> {
                if (bedrock) break;
                Element res = p.itemLike(Ids.full(r.result));
                if ((paper || pack) && res instanceof Block) a.none("compat.recipe_block");
                else if (pack && res instanceof Item && preComponents) a.fallback("compat.recipe_custom_old");
            }
            case LootTable l -> {
                if (l.inject && !l.replaces.isBlank()) {
                    String kind = Ids.path(Ids.full(l.replaces.trim()));
                    boolean chest = kind.startsWith("chests/"), entity = kind.startsWith("entities/"), block = kind.startsWith("blocks/");
                    if (pack) a.none("compat.loot_inject_pack");
                    else if (paper) {
                        if (block) a.fallback("compat.loot_inject_command");
                        else if (!chest && !entity) a.none("compat.loot_inject_paper");
                    } else if (bedrock) {
                        if (entity || block) a.fallback("compat.loot_inject_command");
                        else a.none("compat.loot_inject_bedrock");
                    }
                }
            }
            case Advancement adv -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Tag tag -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Function f -> {
                if (bedrock) a.fallback("compat.function_bedrock");
            }
            case Procedure pr -> {
                if (!Logic.triggerSupport(pr.trigger).contains(t)) a.none("compat.trigger", I18n.tOr("trigger." + pr.trigger, pr.trigger));
                steps(p, pr.steps, t, old, a);
            }
            case Elements.Command c -> {
                if (pack) {
                    if (c.branches.isEmpty()) a.fallback("compat.command_trigger");
                    else a.fallback("compat.command_branches_trigger");
                }
                if (bedrock && !c.branches.isEmpty()) a.fallback("compat.command_branches_bedrock");
                steps(p, c.steps, t, old, a);
                for (CommandBranch b : c.branches) steps(p, b.steps, t, old, a);
            }
            case CodeFile cf -> {
                if (!cf.target.equals(t.id)) a.none("compat.codefile", cf.target);
            }
            case Systems.Mob m -> {
                if (bedrock) a.none("compat.java_only");
                else if (t == Target.DATAPACK && !m.rig.isBlank()) a.fallback("compat.rig_pack");
                for (List<Step> l : List.of(m.onSpawn, m.onTick, m.onHurt, m.onDeath, m.onInteract)) steps(p, l, t, old, a);
            }
            case Systems.Effect fx -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Systems.Structure s -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Systems.Biome b -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Systems.Dimension d -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Systems.Menu m -> {
                if (bedrock) a.none("compat.java_only");
                else if (pack) a.fallback("compat.menu_chat");
                else if (t == Target.PAPER && MenuArt.active(p, m)) a.fallback("compat.menu_art_pack");
                for (Systems.MenuSlot sl : m.slots) steps(p, sl.steps, t, old, a);
            }
            case Systems.Hud h -> {
                if (bedrock) a.none("compat.java_only");
                boolean dynamic = h.lines.stream().anyMatch(l -> l.contains("{")) || h.title.contains("{");
                if (!bedrock && h.kind.equals("sidebar") && dynamic && old) a.fallback("compat.sidebar_old");
                steps(p, h.onFinish, t, old, a);
            }
            case Systems.ApiRequest r -> {
                if (bedrock) a.none("compat.java_only");
                else if (pack) a.fallback("compat.http_pack");
                for (List<Step> l : List.of(r.onSuccess, r.onClientError, r.onServerError, r.onFailure)) steps(p, l, t, old, a);
            }
            case Systems.Resource r -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Systems.Multiblock mb -> {
                if (bedrock) a.none("compat.java_only");
                else if (mb.assemble && !mb.model.isBlank() && (t == Target.PAPER || t == Target.DATAPACK)) a.fallback("compat.rig_pack");
                for (List<Step> l : List.of(mb.onAssemble, mb.onUse, mb.onFinish, mb.onDisassemble)) steps(p, l, t, old, a);
            }
            case Elements.Predicate pr -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Elements.ItemModifier im -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Systems.Trader tr -> {
                if (bedrock) a.none("compat.java_only");
            }
            case Systems.Dialog d -> {
                if (bedrock) a.none("compat.java_only");
                else {
                    if (p.mcVersions().stream().anyMatch(v -> !v.dialogs())) a.fallback("compat.dialog_chat");
                    if (paper && p.mcVersions().stream().anyMatch(McVersion::dialogs)) a.notes.add(I18n.t("compat.dialog_restart"));
                }
                for (Systems.DialogButton b : d.buttons) steps(p, b.steps, t, old, a);
            }
            case Systems.KeyBind k -> {
                if (!mod) {
                    if (k.fallback.equals("none")) a.none("compat.key_none");
                    else a.fallback("compat.key_fallback", I18n.t("fallback." + k.fallback));
                }
                steps(p, k.steps, t, old, a);
            }
            case Systems.Cutscene cs -> {
                if (bedrock) a.fallback("compat.cutscene_bedrock");
                else if (p.mcVersions().contains(McVersion.V1_20_1)) a.fallback("compat.cutscene_old");
                steps(p, cs.onEnd, t, old, a);
            }
            case Systems.Machine m -> {
                if (bedrock) a.none("compat.java_only");
                else if ((paper || pack) && Ids.full(m.block).startsWith(p.meta.modId + ":")) a.none("compat.machine_block");
                for (List<Step> l : List.of(m.onCycle, m.onPlace, m.onBreak, m.onOverload)) steps(p, l, t, old, a);
            }
            default -> {
            }
        }
    }

    /** Step level fallbacks: shims for old versions and reduced forms on data packs. */
    private static void steps(Project p, List<Step> steps, Target t, boolean old, Acc a) {
        boolean pack = t == Target.DATAPACK, bedrock = t == Target.BEDROCK;
        for (Step s : steps) {
            if (s.kind.equals("if") && s.cond != null && (s.cond.kind.equals("platform") || s.cond.kind.equals("mc_version"))) {
                a.notes.add(I18n.t("compat.branch"));
            }
            if (!bedrock) {
                switch (s.kind) {
                    case "http_request" -> {
                        if (pack) a.fallback("compat.http_pack");
                    }
                    case "open_menu" -> {
                        if (pack) a.fallback("compat.menu_chat");
                    }
                    case "show_dialog" -> {
                        if (p.mcVersions().stream().anyMatch(v -> !v.dialogs())) a.fallback("compat.dialog_chat");
                    }
                    case "effect" -> {
                        String id = Ids.full(s.get("effect"));
                        if ((pack || t == Target.PAPER) && id.startsWith(p.meta.modId + ":")) a.fallback("compat.effect_emulated");
                    }
                    case "random_score" -> {
                        if (old) a.fallback("compat.random_shim");
                    }
                    case "stop" -> {
                        if (old) a.fallback("compat.stop_old");
                    }
                    default -> {
                    }
                }
                if (old && (Validator.needsMacro(s.p) || (s.cond != null && Validator.needsMacro(s.cond.p)))) a.fallback("compat.macro_old");
            }
            if (bedrock && s.kind.equals("shoot")) a.fallback("compat.shoot_bedrock");
            if (bedrock && List.of("modify_item", "give_loot", "show_dialog", "spawn_trader").contains(s.kind)) a.fallback("compat.step_java_only");
            if (bedrock && (s.kind.equals("play_animation") || s.kind.equals("stop_animation"))) a.fallback("compat.animation_bedrock");
            steps(p, s.then, t, old, a);
            steps(p, s.orElse, t, old, a);
        }
    }
}
