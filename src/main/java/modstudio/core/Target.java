package modstudio.core;

/** Output platforms a project can be generated for. */
public enum Target {
    FORGE("forge", Kind.MOD),
    NEOFORGE("neoforge", Kind.MOD),
    FABRIC("fabric", Kind.MOD),
    PAPER("paper", Kind.PLUGIN),
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

    /** Whether the target has a build of the given Minecraft version. */
    public boolean supports(McVersion v) {
        return this != NEOFORGE || v.atLeast(McVersion.V1_20_4);
    }

    /** Bedrock add-ons do not depend on the Java Edition version and are generated once. */
    public boolean versioned() {
        return this != BEDROCK;
    }

    public static Target of(String id) {
        for (Target t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
