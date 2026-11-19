package modstudio.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What kind of project this is (a mod, a data pack, a resource pack, a server plugin or a Bedrock add-on), judged
 * from its export targets, and which element types make sense for it. The workspace uses this so a data pack,
 * resource pack or plugin project has its own focused workflow rather than looking like a mod project.
 */
public final class ProjectScope {
    private ProjectScope() {}

    public enum Kind { MOD, PLUGIN, DATAPACK, RESOURCEPACK, BEDROCK, MIXED }

    /** Native Resource Pack asset types shown for standalone Resource Pack projects. */
    private static final Set<ElementType> RESOURCE_PACK_NATIVE = EnumSet.of(
            ElementType.MODEL, ElementType.SHADER, ElementType.SOUND, ElementType.THEME, ElementType.ICON_FONT);

    /** Data Pack types that additionally need a companion Resource Pack (CMD items, custom models/assets). */
    private static final Set<ElementType> RESOURCE_PACK_PAIRED = EnumSet.of(
            ElementType.ITEM, ElementType.ARMOR_MATERIAL, ElementType.STATUS_EFFECT, ElementType.POTION,
            ElementType.PAINTING, ElementType.SONG, ElementType.MODEL, ElementType.SHADER, ElementType.SOUND,
            ElementType.MENU, ElementType.THEME, ElementType.ICON_FONT, ElementType.ARMOR_TRIM, ElementType.BANNER_PATTERN);

    /** Core Data Pack elements native to data-driven Java Edition datapacks (no resource pack/mod required). */
    private static final Set<ElementType> DATAPACK_NATIVE = EnumSet.of(
            ElementType.RECIPE, ElementType.LOOT_TABLE, ElementType.ADVANCEMENT, ElementType.TAG,
            ElementType.FUNCTION, ElementType.PREDICATE, ElementType.ITEM_MODIFIER,
            ElementType.ENCHANTMENT, ElementType.DAMAGE_TYPE, ElementType.PAINTING, ElementType.SONG,
            ElementType.ARMOR_TRIM, ElementType.BANNER_PATTERN, ElementType.WORLD_FEATURE,
            ElementType.BIOME, ElementType.DIMENSION, ElementType.STRUCTURE,
            ElementType.TRADER, ElementType.DIALOG, ElementType.CUTSCENE,
            ElementType.PROCEDURE, ElementType.COMMAND, ElementType.EFFECT,
            ElementType.HUD, ElementType.MENU);

    /** Heavy runtime/plugin/client shims that only appear for Mod or Plugin projects. */
    private static final Set<ElementType> PLUGIN_OR_MOD_SHIMS = EnumSet.of(
            ElementType.API_REQUEST, ElementType.RESOURCE, ElementType.MACHINE, ElementType.MULTIBLOCK,
            ElementType.MOB, ElementType.KEYBIND);

    private static Map<ElementType, Set<Target>> support;

    /** Targets that natively or cleanly support an element of the type. */
    public static synchronized Set<Target> targets(ElementType type) {
        if (support == null) {
            Map<ElementType, Set<Target>> m = new EnumMap<>(ElementType.class);
            Project p = new Project();
            p.meta.modId = "scope";
            p.meta.targets = new ArrayList<>();
            for (Target t : Target.values()) p.meta.targets.add(t.id);
            p.meta.mcVersions = new ArrayList<>(List.of(McVersion.latest().id));
            p.meta.mcVersion = McVersion.latest().id;
            for (ElementType t : ElementType.values()) {
                Element e = t.create();
                e.name = "scope_" + t.id;
                p.elements.add(e);
            }
            for (Compat.Row r : Compat.analyze(p)) {
                Set<Target> s = EnumSet.noneOf(Target.class);
                for (var c : r.cells().entrySet()) if (c.getValue().level() != Compat.Level.NONE) s.add(c.getKey());
                m.put(r.element().type(), s);
            }
            for (ElementType t : ElementType.values()) {
                Set<Target> s = m.computeIfAbsent(t, k -> EnumSet.noneOf(Target.class));
                if (RESOURCE_PACK_NATIVE.contains(t)) s.add(Target.RESOURCEPACK);
                else s.remove(Target.RESOURCEPACK);
                if (!DATAPACK_NATIVE.contains(t) || PLUGIN_OR_MOD_SHIMS.contains(t)) {
                    s.remove(Target.DATAPACK);
                }
                if (t == ElementType.CODE_FILE) {
                    s.clear();
                    for (Target x : Target.values()) if (x.usesGradle()) s.add(x);
                }
            }
            support = m;
        }
        return support.get(type);
    }

    /** Whether the type belongs in this project's sidebar/dashboard. Existing elements of any type remain editable. */
    public static boolean available(Project p, ElementType type) {
        if (!p.ofType(type).isEmpty()) return true;
        List<Target> ts = p.targets();
        if (ts.isEmpty()) return true;
        Kind k = kind(ts);
        if (k == Kind.DATAPACK && ts.contains(Target.DATAPACK) && ts.contains(Target.RESOURCEPACK)) {
            if (RESOURCE_PACK_PAIRED.contains(type) && !PLUGIN_OR_MOD_SHIMS.contains(type)) return true;
        }
        Set<Target> s = targets(type);
        for (Target t : ts) if (s.contains(t)) return true;
        return false;
    }

    public static Kind kind(List<Target> targets) {
        if (targets.isEmpty()) return Kind.MIXED;
        boolean mod = false, plugin = false, data = false, res = false, bedrock = false;
        for (Target t : targets) {
            switch (t.kind) {
                case MOD -> mod = true;
                case PLUGIN -> plugin = true;
                case BEDROCK -> bedrock = true;
                case PACK -> {
                    if (t == Target.DATAPACK) data = true;
                    else res = true;
                }
            }
        }
        if (mod) return Kind.MOD;
        if (plugin && !bedrock) return Kind.PLUGIN;
        if (bedrock && !plugin && !data && !res) return Kind.BEDROCK;
        if (data && !plugin && !bedrock) return Kind.DATAPACK;
        if (res && !plugin && !bedrock) return Kind.RESOURCEPACK;
        return Kind.MIXED;
    }

    public static Kind kind(Project p) {
        return kind(p.targets());
    }

    /** Whether some target compiles Java code (mods and plugins), so a Java package and Gradle build are needed. */
    public static boolean hasCode(List<Target> targets) {
        for (Target t : targets) if (t.usesGradle()) return true;
        return false;
    }
}
