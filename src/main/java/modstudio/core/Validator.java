package modstudio.core;

import modstudio.core.Elements.*;
import modstudio.i18n.I18n;

import java.util.*;

/** Checks a project for errors and platform limitations. */
public final class Validator {
    private Validator() {}

    public enum Severity { ERROR, WARNING, INFO }

    public record Problem(Severity severity, String elementUid, String message) {}

    public static List<Problem> validate(Project p) {
        List<Problem> r = new ArrayList<>();
        String ns = p.meta.modId;
        if (!Ids.MODID.matcher(ns).matches()) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.modid_invalid", ns)));
        if (!Ids.PACKAGE.matcher(p.meta.packageName).matches()) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.package_invalid", p.meta.packageName)));
        List<Target> targets = p.targets();
        if (targets.isEmpty()) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.no_targets")));
        for (String t : p.meta.targets) {
            Target tt = Target.of(t);
            if (tt == null) continue;
            for (McVersion v : p.mcVersions()) {
                if (!tt.supports(v)) r.add(new Problem(Severity.WARNING, null, I18n.t("problem.version_unsupported", I18n.t("target." + t), v.id)));
            }
        }

        Map<String, Element> seen = new HashMap<>();
        for (Element e : p.elements) {
            String typeName = I18n.t("type." + e.type().id);
            if (e.name.isBlank()) {
                r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.name_empty", typeName)));
                continue;
            }
            boolean valid = e.type() == ElementType.CODE_FILE || (e.type().allowsPath() ? Ids.PATH : Ids.NAME).matcher(e.name).matches();
            if (!valid) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.name_invalid", e.name)));
            String key = e.type().registryGroup() + "|" + e.name;
            if (seen.containsKey(key)) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.name_duplicate", e.name)));
            else seen.put(key, e);
        }

        Set<String> texNames = new HashSet<>();
        for (TextureAsset t : p.textures) {
            if (!Ids.NAME.matcher(t.name).matches()) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.texture_name_invalid", t.name)));
            if (!texNames.add(t.name)) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.texture_name_duplicate", t.name)));
        }

        boolean modTarget = targets.stream().anyMatch(Target::isMod);
        for (Element e : p.elements) {
            switch (e) {
                case Item it -> {
                    if (!it.model.isBlank() && p.model(it.model) == null)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.model_missing", it.name, it.model)));
                    else if (it.model.isBlank() && p.texture(it.texture) == null)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.texture_missing", it.name)));
                    if (it.creativeTab.startsWith("tab:") && p.byName(ElementType.CREATIVE_TAB, it.creativeTab.substring(4)) == null)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_tab", it.creativeTab.substring(4))));
                    if ((p.has(Target.PAPER) || p.has(Target.DATAPACK)) && !Vanilla.CMD_BASES.containsKey(Ids.full(it.baseItem)))
                        r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.base_item_model", it.baseItem)));
                }
                case Block b -> {
                    boolean missing = switch (b.shape) {
                        case "cube_bottom_top" -> p.texture(b.textureTop) == null || p.texture(b.textureBottom) == null || p.texture(b.textureSide) == null;
                        case "pillar" -> p.texture(b.textureTop) == null || p.texture(b.textureSide) == null;
                        case "model" -> false;
                        default -> p.texture(b.texture) == null;
                    };
                    if (missing) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.texture_missing", b.name)));
                    if (b.shape.equals("model") && p.model(b.model) == null)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.model_missing", b.name, b.model)));
                    if (b.drop.equals("item")) checkRef(p, r, e, b.dropItem);
                    if (b.creativeTab.startsWith("tab:") && p.byName(ElementType.CREATIVE_TAB, b.creativeTab.substring(4)) == null)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_tab", b.creativeTab.substring(4))));
                    for (Target t : targets) {
                        if (t == Target.PAPER || t == Target.DATAPACK || t == Target.RESOURCEPACK)
                            r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.block_unsupported", b.name, I18n.t("target." + t.id))));
                    }
                }
                case Recipe rc -> {
                    if (rc.result.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.recipe_no_result", rc.name)));
                    boolean empty = switch (rc.recipeType) {
                        case "shaped", "shapeless" -> rc.grid.stream().allMatch(String::isBlank);
                        case "smithing" -> rc.template.isBlank() || rc.base.isBlank() || rc.addition.isBlank();
                        default -> rc.input.isBlank();
                    };
                    if (empty) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.recipe_empty", rc.name)));
                    for (String s : rc.grid) checkRef(p, r, e, s);
                    for (String s : List.of(rc.input, rc.result, rc.template, rc.base, rc.addition)) checkRef(p, r, e, s);
                }
                case LootTable lt -> {
                    if (lt.pools.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.loot_empty", lt.name)));
                    for (LootPool pool : lt.pools) for (LootEntry en : pool.entries) if (en.kind.equals("item")) checkRef(p, r, e, en.name);
                }
                case Advancement a -> {
                    checkRef(p, r, e, a.icon);
                    if (!a.parent.isBlank()) {
                        String par = a.parent.contains(":") ? a.parent : ns + ":" + a.parent;
                        if (par.startsWith(ns + ":") && p.byName(ElementType.ADVANCEMENT, par.substring(ns.length() + 1)) == null)
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.adv_parent", a.parent)));
                    }
                }
                case Tag t -> {
                    if (t.registry.equals("item") || t.registry.equals("block")) for (String v : t.values) checkRef(p, r, e, v);
                }
                case Procedure pr -> {
                    Logic.Def d = Logic.TRIGGERS.get(pr.trigger);
                    boolean needsValue = d != null && !d.params().isEmpty() && List.of("block_break", "item_right_click", "item_crafted").contains(pr.trigger);
                    if (needsValue && pr.triggerValue.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.trigger_value", pr.name)));
                    if (pr.trigger.equals("cron")) {
                        if (!WebTest.cronValid(pr.triggerValue)) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.cron_invalid", pr.name, pr.triggerValue)));
                    } else {
                        checkRef(p, r, e, pr.triggerValue);
                    }
                    checkSteps(p, r, e, pr.steps);
                    List<Target> sup = Logic.triggerSupport(pr.trigger);
                    for (Target t : targets) {
                        if (t == Target.RESOURCEPACK) continue;
                        if (!sup.contains(t)) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.trigger_unsupported", I18n.t("trigger." + pr.trigger), I18n.t("target." + t.id))));
                    }
                }
                case Elements.Command c -> {
                    if (!Ids.NAME.matcher(c.name).matches()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.command_name", c.name)));
                    checkSteps(p, r, e, c.steps);
                    for (CommandBranch b : c.branches) {
                        try {
                            CommandSpec.parse(b.path);
                        } catch (CommandSpec.SpecException ex) {
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.command_path", c.name, b.path, ex.getMessage())));
                        }
                        checkSteps(p, r, e, b.steps);
                        if (p.mcVersions().contains(McVersion.V1_20_1) && usesMacro(b.steps))
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.macro_old", "/" + c.name + " " + b.path)));
                    }
                }
                case Systems.Menu m -> {
                    for (Systems.MenuSlot s : m.slots) {
                        checkSteps(p, r, e, s.steps);
                        checkRef(p, r, e, s.item);
                        if (s.action.equals("menu") && p.byName(ElementType.MENU, s.target.trim()) == null)
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", m.name, I18n.t("type.menu"), s.target)));
                    }
                    if (p.has(Target.DATAPACK)) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.menu_chat", m.name)));
                }
                case Systems.Hud h -> {
                    checkSteps(p, r, e, h.onFinish);
                    boolean dynamic = h.lines.stream().anyMatch(l -> l.contains("{")) || h.title.contains("{");
                    if (h.kind.equals("sidebar") && dynamic && p.mcVersions().contains(McVersion.V1_20_1))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.sidebar_static", h.name)));
                }
                case Systems.ApiRequest a -> {
                    for (List<Step> l : List.of(a.onSuccess, a.onClientError, a.onServerError, a.onFailure)) checkSteps(p, r, e, l);
                    if (a.schedule.equals("cron") && !WebTest.cronValid(a.cron))
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.cron_invalid", a.name, a.cron)));
                    if (p.has(Target.DATAPACK)) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.http_datapack", a.name)));
                }
                case Systems.Resource res -> {
                    checkSteps(p, r, e, res.onPollution);
                    boolean used = p.all(Systems.Machine.class).stream().anyMatch(m -> m.io.stream().anyMatch(io -> io.resource.trim().equals(res.name)));
                    if (!used) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.resource_unused", res.name)));
                }
                case Systems.Machine m -> {
                    for (List<Step> l : List.of(m.onCycle, m.onPlace, m.onBreak, m.onOverload)) checkSteps(p, r, e, l);
                    if (m.block.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.machine_block", m.name)));
                    else {
                        checkRef(p, r, e, m.block.contains("[") ? m.block.substring(0, m.block.indexOf('[')) : m.block);
                        if (Ids.full(m.block).startsWith(p.meta.modId + ":") && (p.has(Target.DATAPACK) || p.has(Target.PAPER)))
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.machine_project_block", m.name)));
                    }
                    for (Systems.MachineIO io : m.io) {
                        if (p.byName(ElementType.RESOURCE, io.resource.trim()) == null)
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", m.name, I18n.t("type.resource"), io.resource.isBlank() ? "-" : io.resource)));
                    }
                    if (!m.multiblock.isBlank() && p.byName(ElementType.MULTIBLOCK, m.multiblock.trim()) == null)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", m.name, I18n.t("type.multiblock"), m.multiblock)));
                    if (m.io.isEmpty() && m.onCycle.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.machine_idle", m.name)));
                }
                case Systems.Multiblock mb -> {
                    mb.ensure();
                    if (mb.coreX >= mb.sx || mb.coreY >= mb.sy || mb.coreZ >= mb.sz)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.multiblock_core", mb.name)));
                    if (mb.count() == 0) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.multiblock_empty", mb.name)));
                    for (String b : mb.palette) if (!b.isBlank()) checkRef(p, r, e, b.contains("[") ? b.substring(0, b.indexOf('[')) : b);
                }
                case Systems.Shader sh -> {
                    String prog = sh.program.trim();
                    boolean include = sh.kind.equals("include");
                    if (prog.isEmpty() || !prog.matches("[a-z0-9_./-]+"))
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.shader_program", sh.name, prog)));
                    if (sh.fragment.isBlank() && (include || sh.vertex.isBlank()))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.shader_empty", sh.name)));
                    McVersion min = McVersion.orNull(sh.minVersion), max = McVersion.orNull(sh.maxVersion);
                    if (min != null && max != null && !max.atLeast(min))
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.shader_range", sh.name)));
                    if (p.mcVersions().stream().noneMatch(v -> Shaders.inRange(sh, v)))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.shader_no_version", sh.name)));
                    List<String> issues = new ArrayList<>(Shaders.check(sh.fragment, !include));
                    if (!include) issues.addAll(Shaders.check(sh.vertex, true));
                    for (String k : new LinkedHashSet<>(issues))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.shader_code", sh.name, I18n.t(k))));
                    if (!include) {
                        for (McVersion v : p.mcVersions()) {
                            List<String> known = Shaders.programs(v);
                            if (Shaders.inRange(sh, v) && !known.isEmpty() && !prog.isEmpty() && !known.contains(prog))
                                r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.shader_unknown", sh.name, prog, v.id)));
                        }
                    }
                }
                case CodeFile cf -> {
                    if (cf.path.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.codefile_path", cf.name)));
                }
                case CreativeTab tab -> {
                    checkRef(p, r, e, tab.icon);
                    if (!modTarget) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.tab_mods_only", tab.name)));
                }
                default -> {
                }
            }
        }
        if (p.elements.stream().anyMatch(e -> e instanceof Item) && (p.has(Target.PAPER) || p.has(Target.DATAPACK)) && !p.has(Target.RESOURCEPACK)) {
            r.add(new Problem(Severity.INFO, null, I18n.t("problem.need_resourcepack")));
        }
        r.sort(Comparator.comparing(Problem::severity));
        return r;
    }

    private static void checkSteps(Project p, List<Problem> r, Element e, List<Step> steps) {
        for (Step s : steps) {
            for (String k : List.of("item", "block")) checkRef(p, r, e, s.get(k));
            checkElementRefs(p, r, e, Logic.STEPS.get(s.kind), s.p);
            if (s.cond != null) {
                checkRef(p, r, e, s.cond.get("item"));
                checkRef(p, r, e, s.cond.get("block"));
                checkElementRefs(p, r, e, Logic.CONDITIONS.get(s.cond.kind), s.cond.p);
            }
            checkSteps(p, r, e, s.then);
            checkSteps(p, r, e, s.orElse);
        }
    }

    /** Steps and conditions that name project elements (mobs, menus, requests...) must point to existing ones. */
    private static void checkElementRefs(Project p, List<Problem> r, Element e, Logic.Def def, Map<String, String> values) {
        if (def == null) return;
        for (Logic.Param prm : def.params()) {
            if (prm.type() != Logic.PType.REF) continue;
            String v = values.getOrDefault(prm.key(), "").trim();
            ElementType t = ElementType.of(prm.choices().get(0));
            if (t == null || p.byName(t, v) != null) continue;
            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", e.name, I18n.t("type." + t.id), v.isEmpty() ? "-" : v)));
        }
    }

    /**
     * True when a step needs function macros. A whole argument in a number field ({@code $(amount)} as a score
     * value, condition bound or timer length) compiles to a score operation instead and works on every version.
     */
    private static boolean usesMacro(List<Step> steps) {
        for (Step s : steps) {
            if (needsMacro(s.p)) return true;
            if (s.cond != null && needsMacro(s.cond.p)) return true;
            if (usesMacro(s.then) || usesMacro(s.orElse)) return true;
        }
        return false;
    }

    static boolean needsMacro(Map<String, String> params) {
        for (Map.Entry<String, String> e : params.entrySet()) {
            String v = e.getValue();
            if (v == null || !v.contains("$(")) continue;
            boolean numeric = switch (e.getKey()) {
                case "seconds", "value", "min", "max" -> true;
                default -> false;
            };
            if (!numeric || !v.trim().matches("\\$\\([A-Za-z0-9_]+\\)")) return true;
        }
        return false;
    }

    private static void checkRef(Project p, List<Problem> r, Element e, String id) {
        if (id == null || id.isBlank()) return;
        String f = id.startsWith("#") ? id.substring(1) : id;
        if (id.startsWith("#")) return;
        if (f.startsWith(p.meta.modId + ":") && p.itemLike(f) == null) {
            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_ref", id)));
        }
    }

    public static long count(List<Problem> list, Severity s) {
        return list.stream().filter(x -> x.severity() == s).count();
    }
}
