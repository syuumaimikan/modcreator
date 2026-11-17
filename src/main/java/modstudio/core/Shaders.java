package modstudio.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Shader support: the core shader programs and include files of each Minecraft version, GLSL helper snippets,
 * basic source checks and reading the original shaders from a Minecraft client jar installed on this computer.
 */
public final class Shaders {
    private Shaders() {}

    public static final List<String> KINDS = List.of("core", "include");
    private static final Properties LISTS = new Properties();

    static {
        try (InputStream in = Shaders.class.getResourceAsStream("/modstudio/shaders/programs.properties")) {
            if (in != null) LISTS.load(in);
        } catch (IOException ignored) {
        }
    }

    private static List<String> list(String key) {
        String v = LISTS.getProperty(key, "").trim();
        return v.isEmpty() ? List.of() : List.of(v.split("\\s+"));
    }

    /** Core shader programs (file names without .vsh/.fsh) of a version. */
    public static List<String> programs(McVersion v) {
        return list("core." + v.id);
    }

    /** Include files (names without .glsl) of a version. */
    public static List<String> includes(McVersion v) {
        return list("include." + v.id);
    }

    /** Program or include names over all versions the project exports, for the editor's suggestions. */
    public static List<String> names(Project p, String kind) {
        TreeSet<String> r = new TreeSet<>();
        for (McVersion v : p.mcVersions()) r.addAll(kind.equals("include") ? includes(v) : programs(v));
        return new ArrayList<>(r);
    }

    /** The #moj_import line for an include: namespaced from 1.21.2 on. */
    public static String importLine(McVersion v, String include) {
        return v.atLeast(McVersion.V1_21_4) ? "#moj_import <minecraft:" + include + ".glsl>" : "#moj_import <" + include + ".glsl>";
    }

    /** Whether a shader is exported for a version (an empty bound means no limit). */
    public static boolean inRange(Systems.Shader s, McVersion v) {
        McVersion min = McVersion.orNull(s.minVersion), max = McVersion.orNull(s.maxVersion);
        return (min == null || v.atLeast(min)) && (max == null || !v.atLeast(max) || v == max);
    }

    // ------------------------------------------------------------------ checks

    /** Problems in a GLSL source as i18n keys: missing #version, missing main, unbalanced brackets. */
    public static List<String> check(String src, boolean needsMain) {
        List<String> r = new ArrayList<>();
        if (src == null || src.isBlank()) return r;
        String code = src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
        if (needsMain && !code.contains("#version")) r.add("shader.check_version");
        if (needsMain && !code.matches("(?s).*\\bvoid\\s+main\\s*\\(\\s*\\).*")) r.add("shader.check_main");
        int braces = 0, parens = 0;
        boolean bad = false;
        for (char ch : code.toCharArray()) {
            switch (ch) {
                case '{' -> braces++;
                case '}' -> braces--;
                case '(' -> parens++;
                case ')' -> parens--;
                default -> {
                }
            }
            if (braces < 0 || parens < 0) bad = true;
        }
        if (bad || braces != 0) r.add("shader.check_braces");
        if (bad || parens != 0) r.add("shader.check_parens");
        return r;
    }

    // ------------------------------------------------------------------ snippets

    /** Helper functions that can be pasted into any shader; they only use their parameters. */
    public static final Map<String, String> SNIPPETS = new LinkedHashMap<>();

    static {
        SNIPPETS.put("grayscale", """
                vec3 ms_grayscale(vec3 c) {
                    return vec3(dot(c, vec3(0.299, 0.587, 0.114)));
                }
                """);
        SNIPPETS.put("saturation", """
                vec3 ms_saturation(vec3 c, float amount) {
                    float l = dot(c, vec3(0.299, 0.587, 0.114));
                    return mix(vec3(l), c, amount);
                }
                """);
        SNIPPETS.put("hue_shift", """
                vec3 ms_hue_shift(vec3 c, float angle) {
                    const vec3 k = vec3(0.57735);
                    float ca = cos(angle);
                    return c * ca + cross(k, c) * sin(angle) + k * dot(k, c) * (1.0 - ca);
                }
                """);
        SNIPPETS.put("tint", """
                vec3 ms_tint(vec3 c, vec3 tint, float amount) {
                    return mix(c, c * tint, clamp(amount, 0.0, 1.0));
                }
                """);
        SNIPPETS.put("posterize", """
                vec3 ms_posterize(vec3 c, float steps) {
                    return floor(c * steps + 0.5) / steps;
                }
                """);
        SNIPPETS.put("pulse", """
                float ms_pulse(float time, float speed) {
                    return 0.5 + 0.5 * sin(time * speed * 6.2831853);
                }
                """);
        SNIPPETS.put("hash", """
                float ms_hash(vec2 p) {
                    p = fract(p * vec2(123.34, 456.21));
                    p += dot(p, p + 45.32);
                    return fract(p.x * p.y);
                }
                """);
        SNIPPETS.put("wave", """
                vec3 ms_wave(vec3 pos, float time, float amplitude, float frequency) {
                    pos.y += sin(pos.x * frequency + time) * amplitude;
                    pos.x += cos(pos.z * frequency + time) * amplitude * 0.5;
                    return pos;
                }
                """);
        SNIPPETS.put("rim", """
                float ms_rim(vec3 normal, vec3 viewDir, float power) {
                    return pow(1.0 - clamp(dot(normalize(normal), normalize(viewDir)), 0.0, 1.0), power);
                }
                """);
        SNIPPETS.put("emissive", """
                vec4 ms_emissive(vec4 color, vec3 glow, float strength) {
                    return vec4(color.rgb + glow * strength, color.a);
                }
                """);
    }

    /** Inserts a snippet before "void main" (or at the end when there is none), once per source. */
    public static String insertSnippet(String src, String snippet) {
        String code = SNIPPETS.getOrDefault(snippet, "");
        if (code.isEmpty()) return src;
        String fn = code.substring(0, code.indexOf('(')).trim();
        fn = fn.substring(fn.lastIndexOf(' ') + 1);
        if (src.contains(fn + "(")) return src;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*void\\s+main\\s*\\(").matcher(src);
        if (m.find()) return src.substring(0, m.start()) + code + "\n" + src.substring(m.start());
        return src + (src.isEmpty() || src.endsWith("\n") ? "" : "\n") + code;
    }

    // ------------------------------------------------------------------ installed game

    /** A Minecraft client jar on this computer. */
    public record GameJar(String version, Path jar) {
        @Override
        public String toString() {
            return version;
        }
    }

    /** Client jars in the launcher's versions folder, newest first. */
    public static List<GameJar> installed(Path gameDir) {
        List<GameJar> r = new ArrayList<>();
        Path versions = gameDir.resolve("versions");
        if (!Files.isDirectory(versions)) return r;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(versions)) {
            for (Path d : ds) {
                String v = d.getFileName().toString();
                Path jar = d.resolve(v + ".jar");
                if (Files.isRegularFile(jar)) r.add(new GameJar(v, jar));
            }
        } catch (IOException ignored) {
        }
        r.sort(Comparator.comparing((GameJar g) -> versionKey(g.version())).reversed());
        return r;
    }

    private static String versionKey(String v) {
        StringBuilder sb = new StringBuilder();
        for (String part : v.split("[.\\-]")) {
            if (part.matches("\\d+")) sb.append(String.format("%05d", Integer.parseInt(part)));
            else sb.append(part);
            sb.append('.');
        }
        return sb.toString();
    }

    /** Reads a text file of the client jar, or null when it is not there. */
    public static String readFromJar(Path jar, String path) throws IOException {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            ZipEntry e = z.getEntry(path);
            if (e == null) return null;
            try (InputStream in = z.getInputStream(e)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    /** Path of a shader file inside the game: core/name.vsh, core/name.fsh or include/name.glsl. */
    public static String gamePath(String kind, String name, String ext) {
        return "assets/minecraft/shaders/" + (kind.equals("include") ? "include/" + name + ".glsl" : "core/" + name + "." + ext);
    }
}
