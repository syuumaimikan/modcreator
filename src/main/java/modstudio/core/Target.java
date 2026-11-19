package modstudio.core;

/** Output platforms a project can be generated for. */
public enum Target {
    FORGE("forge", Kind.MOD),
    NEOFORGE("neoforge", Kind.MOD),
    FABRIC("fabric", Kind.MOD),
    QUILT("quilt", Kind.MOD),
    PAPER("paper", Kind.PLUGIN),
    FOLIA("folia", Kind.PLUGIN),
    DATAPACK("datapack", Kind.PACK),
    RESOURCEPACK("resourcepack", Kind.PACK),
    BEDROCK("bedrock", Kind.BEDROCK);

    public enum Kind { MOD, PLUGIN, PACK, BEDROCK }

    public final String id;
    public final Kind kind;

    Target(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public boolean usesGradle() {
        return kind == Kind.MOD || kind == Kind.PLUGIN;
    }

    public boolean isMod() {
        return kind == Kind.MOD;
    }

    public boolean isPlugin() {
        return kind == Kind.PLUGIN;
    }

    /** Whether the target has a build of the given Minecraft version. */
    public boolean supports(McVersion v) {
        if (this == NEOFORGE && !v.atLeast(McVersion.V1_20_4)) return false;
        if (this == FORGE && v.unobfuscated()) return false;
        return true;
    }

    /** Bedrock add-ons do not depend on the Java Edition version and are generated once. */
    public boolean versioned() {
        return this != BEDROCK;
    }

    public static Target of(String id) {
        for (Target t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    /** Matches selectors like "all", "mods", "plugins", "packs", a single id, or comma-separated ids. */
    public static boolean matchesSelector(String selector, Target target) {
        if (target == null) return false;
        if (selector == null || selector.isBlank()) return true;
        for (String raw : selector.split(",")) {
            String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (s.isEmpty() || "all".equals(s)) return true;
            if ("mods".equals(s) && target.isMod()) return true;
            if ("plugins".equals(s) && target.isPlugin()) return true;
            if ("packs".equals(s) && target.kind == Kind.PACK) return true;
            if (s.equals(target.id)) return true;
        }
        return false;
    }
}
