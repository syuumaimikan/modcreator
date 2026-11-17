package modstudio.core;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Identifier helpers shared by the model, generators and UI. */
public final class Ids {
    private Ids() {}

    public static final Pattern NAME = Pattern.compile("[a-z0-9_]+");
    public static final Pattern PATH = Pattern.compile("[a-z0-9_]+(/[a-z0-9_.-]+)*");
    public static final Pattern MODID = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    public static final Pattern PACKAGE = Pattern.compile("[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*");
    public static final Pattern RESOURCE = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Converts free text to a registry-safe id ("Ruby Block" -> "ruby_block"). */
    public static String toId(String s) {
        if (s == null) return "";
        String r = s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        r = r.replaceAll("^_+|_+$", "");
        return r;
    }

    public static String toPascal(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String r = sb.toString();
        if (r.isEmpty()) r = "Element";
        if (Character.isDigit(r.charAt(0))) r = "E" + r;
        return r;
    }

    public static String toConst(String id) {
        String r = id.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        if (r.isEmpty() || Character.isDigit(r.charAt(0))) r = "_" + r;
        return r;
    }

    public static String titleCase(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("[_\\s/]+")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static String ns(String id) {
        String s = id.startsWith("#") ? id.substring(1) : id;
        int i = s.indexOf(':');
        return i < 0 ? "minecraft" : s.substring(0, i);
    }

    public static String path(String id) {
        String s = id.startsWith("#") ? id.substring(1) : id;
        int i = s.indexOf(':');
        return i < 0 ? s : s.substring(i + 1);
    }

    /** Adds the minecraft namespace when missing. */
    public static String full(String id) {
        if (id == null || id.isBlank()) return "";
        boolean tag = id.startsWith("#");
        String s = tag ? id.substring(1) : id;
        if (!s.contains(":")) s = "minecraft:" + s;
        return (tag ? "#" : "") + s;
    }

    /** "minecraft:stone" -> "minecraft.stone" (statistic criterion format). */
    public static String statId(String id) {
        String f = full(id);
        return f.replace(':', '.');
    }

    public static boolean isTag(String id) {
        return id != null && id.startsWith("#");
    }
}
