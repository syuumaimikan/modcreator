package modstudio.core;

/**
 * Supported Minecraft Java Edition versions, oldest first. Each entry carries the data and resource pack formats,
 * the Java version and the feature switches the generators need.
 */
public enum McVersion {
    V1_20_1("1.20.1", 17, 15, 0, 15, "1.20", "[1.20.1,1.20.2)"),
    V1_20_4("1.20.4", 17, 26, 0, 22, "1.20", "[1.20.4,1.20.5)"),
    V1_20_6("1.20.6", 21, 41, 0, 32, "1.20.6", "[1.20.6,1.21)"),
    V1_21_1("1.21.1", 21, 48, 0, 34, "1.21", "[1.21.1,1.21.2)"),
    V1_21_4("1.21.4", 21, 61, 0, 46, "1.21.4", "[1.21.4,1.21.5)"),
    V1_21_8("1.21.8", 21, 81, 0, 64, "1.21.8", "[1.21.8,1.21.9)"),
    V1_21_9("1.21.9", 21, 88, 0, 69, "1.21.9", "[1.21.9,1.21.10)"),
    V1_21_10("1.21.10", 21, 88, 0, 69, "1.21.10", "[1.21.10,1.21.11)"),
    V1_21_11("1.21.11", 21, 94, 1, 75, "1.21.11", "[1.21.11,1.22)"),
    V26_1("26.1", 25, 101, 1, 84, "26.1", "[26.1,26.2)");

    public final String id;
    public final int java;
    public final int dataFormat;
    public final int dataMinor;
    public final int resourceFormat;
    /** Paper / Spigot plugin.yml api-version. */
    public final String apiVersion;
    /** Version range for Forge / NeoForge metadata. */
    public final String range;

    McVersion(String id, int java, int dataFormat, int dataMinor, int resourceFormat, String apiVersion, String range) {
        this.id = id;
        this.java = java;
        this.dataFormat = dataFormat;
        this.dataMinor = dataMinor;
        this.resourceFormat = resourceFormat;
        this.apiVersion = apiVersion;
        this.range = range;
    }

    public int format(boolean resource) {
        return resource ? resourceFormat : dataFormat;
    }

    public int formatMinor(boolean resource) {
        return resource ? 0 : dataMinor;
    }

    /** 1.21.9+: pack.mcmeta uses [major, minor] min_format / max_format arrays. */
    public boolean minorPackFormat() {
        return atLeast(V1_21_9);
    }

    /** 26.1+: Mojang unobfuscated releases running on Java 25. */
    public boolean unobfuscated() {
        return atLeast(V26_1);
    }

    public boolean atLeast(McVersion other) {
        return ordinal() >= other.ordinal();
    }

    /** 1.20.5+: item data components instead of NBT, "id" in item stacks, /execute if items. */
    public boolean components() {
        return atLeast(V1_20_6);
    }

    /** 1.21+: singular data folder names and ResourceLocation factories. */
    public boolean singular() {
        return atLeast(V1_21_1);
    }

    /**
     * 1.21.2+: registry ids in item and block properties, tool materials, consumables, string recipe ingredients,
     * damage_resistant component.
     */
    public boolean registryKeys() {
        return atLeast(V1_21_4);
    }

    /** 1.21.4+: item model definitions in assets/<ns>/items and the item_model component. */
    public boolean itemDefinitions() {
        return atLeast(V1_21_4);
    }

    /** 1.21.5+: text components in commands are SNBT, tools and weapons are item properties. */
    /** Function macros ($(name) lines, function ... with storage), Minecraft 1.20.2 and later. */
    public boolean macros() {
        return atLeast(V1_20_4);
    }

    public boolean snbtText() {
        return atLeast(V1_21_8);
    }

    /** 1.21.6+: the dialog registry and the /dialog command. */
    public boolean dialogs() {
        return atLeast(V1_21_8);
    }

    public static McVersion of(String id) {
        for (McVersion v : values()) if (v.id.equals(id)) return v;
        return V1_21_1;
    }

    public static McVersion orNull(String id) {
        for (McVersion v : values()) if (v.id.equals(id)) return v;
        return null;
    }

    public static McVersion latest() {
        return values()[values().length - 1];
    }

    @Override
    public String toString() {
        return id;
    }
}
