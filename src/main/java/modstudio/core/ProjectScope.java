package modstudio.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What kind of project this is (a mod, a data pack, a resource pack, a server plugin or a Bedrock add-on), judged
 * from its export targets, and which element types make sense for it. The workspace uses this so a data pack project
 * does not look like a mod project.
 */
public final class ProjectScope {
    private ProjectScope() {}

    public enum Kind { MOD, PLUGIN, DATAPACK, RESOURCEPACK, BEDROCK, MIXED }

    /** Element types whose files a resource pack carries (textures, models, sounds, fonts, shaders, translations). */
    private static final Set<ElementType> RESOURCE_PACK = EnumSet.of(ElementType.ITEM, ElementType.ARMOR_MATERIAL, ElementType.STATUS_EFFECT,
            ElementType.PAINTING, ElementType.SONG, ElementType.MODEL, ElementType.SHADER, ElementType.SOUND, ElementType.MENU, ElementType.THEME,
            ElementType.ICON_FONT);

    private static Map<ElementType, Set<Target>> support;

    /** Targets that can export an element of the type in some form (natively or through a fallback). */
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
                if (RESOURCE_PACK.contains(t)) s.add(Target.RESOURCEPACK);
                if (t == ElementType.CODE_FILE) {
                    s.clear();
                    for (Target x : Target.values()) if (x.usesGradle()) s.add(x);
                }
            }
            support = m;
        }
        return support.get(type);
    }

    /** Whether the type belongs in this project: one of its targets exports it, or the project already has one. */
    public static boolean available(Project p, ElementType type) {
        if (!p.ofType(type).isEmpty()) return true;
        Set<Target> s = targets(type);
        for (Target t : p.targets()) if (s.contains(t)) return true;
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

    /** Whether some target compiles Java code (mods and plugins), so a Java package is needed. */
    public static boolean hasCode(List<Target> targets) {
        for (Target t : targets) if (t.usesGradle()) return true;
        return false;
    }
}
