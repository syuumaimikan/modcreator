package modstudio.gen;

import modstudio.core.*;

import java.util.ArrayList;
import java.util.List;

/** State shared while generating one target. */
public final class GenContext {
    public final Project p;
    public final Target target;
    public final McVersion v;
    public final String ns;
    public final String pkg;
    public final String pkgPath;
    public final String mainClass;
    public final FileMap files = new FileMap();
    public final List<String> notes = new ArrayList<>();

    public GenContext(Project p, Target target, McVersion v) {
        this.p = p;
        this.target = target;
        this.v = v;
        this.ns = p.meta.modId;
        this.pkg = p.meta.packageName;
        this.pkgPath = pkg.replace('.', '/');
        this.mainClass = mainClass(p);
    }

    public static String mainClass(Project p) {
        String n = Ids.toPascal(p.meta.name.replaceAll("[^A-Za-z0-9 ]", " "));
        if (n.equals("Element") || n.equals("Mod") || n.length() < 2) n = Ids.toPascal(p.meta.modId) + "Mod";
        return n;
    }

    /** 1.20.5+ item components. */
    public boolean components() {
        return v.components();
    }

    /** 1.21+ singular folders and ResourceLocation factories. */
    public boolean singular() {
        return v.singular();
    }

    /** 1.21.2+ registry keys in properties, tool materials, string ingredients. */
    public boolean registryKeys() {
        return v.registryKeys();
    }

    public <T extends Element> List<T> all(Class<T> c) {
        return p.all(c);
    }

    public String id(String path) {
        return ns + ":" + path;
    }

    // Data folder names changed to singular in 1.21.
    public String recipeDir() { return singular() ? "recipe" : "recipes"; }
    public String lootDir() { return singular() ? "loot_table" : "loot_tables"; }
    public String advDir() { return singular() ? "advancement" : "advancements"; }
    public String funcDir() { return singular() ? "function" : "functions"; }
    public String predicateDir() { return singular() ? "predicate" : "predicates"; }

    public String tagDir(String registry) {
        if (singular()) return registry;
        return switch (registry) {
            case "entity_type" -> "entity_types";
            default -> registry + "s";
        };
    }

    public String javaDir() {
        return "src/main/java/" + pkgPath + "/";
    }

    public String version(String key) {
        return p.version(target, v, key);
    }

    /** Texture name for an asset id, or a missing-texture fallback. */
    public String texName(String assetId) {
        TextureAsset t = p.texture(assetId);
        return t == null ? null : t.name;
    }

    public void note(String s) {
        notes.add(s);
    }

    /** Converts "&a" style colour codes into section signs. */
    public static String legacy(String s) {
        if (s == null) return "";
        return s.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }

    public static String rlNew(boolean modern, String nsExpr, String pathExpr) {
        return modern ? "ResourceLocation.fromNamespaceAndPath(" + nsExpr + ", " + pathExpr + ")" : "new ResourceLocation(" + nsExpr + ", " + pathExpr + ")";
    }

    public static String rlParse(boolean modern, String expr) {
        return modern ? "ResourceLocation.parse(" + expr + ")" : "new ResourceLocation(" + expr + ")";
    }
}
