package modstudio.gen;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import modstudio.core.Project;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Patches an existing compiled mod {@code .jar} in-place by retaining its original classes,
 * nested libraries, services, and loader hooks while overlaying edited assets, data packs,
 * translations, recipes, advancements, raw {@code CodeFile} resources, and metadata.
 */
public final class ModPatcher {
    private ModPatcher() {}

    public record Result(int keptEntries, int patchedEntries, int addedEntries) {}

    private static final Set<String> DESCRIPTOR_PATHS = Set.of(
            "fabric.mod.json",
            "quilt.mod.json",
            "meta-inf/mods.toml",
            "meta-inf/neoforge.mods.toml",
            "mcmod.info"
    );

    public static Result patchJar(Project p, GenContext ctx, Path sourceJar, Path destJar) throws IOException {
        Objects.requireNonNull(p, "project");
        Objects.requireNonNull(ctx, "ctx");
        if (sourceJar == null || !Files.isRegularFile(sourceJar)) {
            throw new IOException("Source JAR not found: " + sourceJar);
        }

        Map<String, byte[]> overlays = collectResourceOverlays(ctx);
        Map<String, byte[]> finalEntries = new LinkedHashMap<>();
        int kept = 0, patched = 0, added = 0;

        Map<String, byte[]> original = readZipEntries(sourceJar);
        for (var en : original.entrySet()) {
            String path = en.getKey();
            String low = path.toLowerCase(Locale.ROOT);
            // Remove JAR signatures so modified archives pass verification
            if (low.startsWith("meta-inf/") && (low.endsWith(".sf") || low.endsWith(".rsa") || low.endsWith(".dsa") || low.endsWith(".ec"))) {
                continue;
            }
            if (DESCRIPTOR_PATHS.contains(low)) {
                byte[] updatedDesc = patchDescriptor(p, low, en.getValue());
                finalEntries.put(path, updatedDesc);
                patched++;
                overlays.keySet().removeIf(k -> k.equalsIgnoreCase(path));
                continue;
            }
            byte[] replacement = overlays.remove(path);
            if (replacement != null) {
                finalEntries.put(path, replacement);
                patched++;
            } else {
                finalEntries.put(path, en.getValue());
                kept++;
            }
        }
        for (var en : overlays.entrySet()) {
            String low = en.getKey().toLowerCase(Locale.ROOT);
            if (DESCRIPTOR_PATHS.contains(low)) continue;
            finalEntries.put(en.getKey(), en.getValue());
            added++;
        }

        if (destJar.getParent() != null) Files.createDirectories(destJar.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destJar))) {
            for (var en : finalEntries.entrySet()) {
                zos.putNextEntry(new ZipEntry(en.getKey()));
                zos.write(en.getValue());
                zos.closeEntry();
            }
        }
        return new Result(kept, patched, added);
    }

    private static Map<String, byte[]> collectResourceOverlays(GenContext ctx) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        String resPrefix = "src/main/resources/";
        for (String p : ctx.files.paths()) {
            if (p.startsWith(resPrefix)) {
                String rel = p.substring(resPrefix.length());
                if (!rel.isEmpty()) map.put(rel, ctx.files.get(p));
            } else if (!p.endsWith(".java") && !p.startsWith("gradle") && !p.equals("build.gradle")
                    && !p.equals("build.gradle.kts") && !p.equals("settings.gradle")) {
                if (p.startsWith("assets/") || p.startsWith("data/") || p.equals("pack.mcmeta") || p.equals("pack.png")) {
                    map.put(p, ctx.files.get(p));
                }
            }
        }
        return map;
    }

    private static byte[] patchDescriptor(Project p, String lowPath, byte[] orig) {
        String text = new String(orig, StandardCharsets.UTF_8);
        try {
            if (lowPath.equals("fabric.mod.json")) {
                JsonReader jr = new JsonReader(new StringReader(text));
                jr.setStrictness(Strictness.LENIENT);
                JsonElement je = JsonParser.parseReader(jr);
                if (je != null && je.isJsonObject()) {
                    JsonObject o = je.getAsJsonObject();
                    if (!p.meta.name.isBlank()) o.addProperty("name", p.meta.name);
                    if (!p.meta.version.isBlank()) o.addProperty("version", p.meta.version);
                    if (!p.meta.description.isBlank()) o.addProperty("description", p.meta.description);
                    if (!p.meta.authors.isBlank()) {
                        JsonArray authors = new JsonArray();
                        for (String a : p.meta.authors.split(",")) if (!a.isBlank()) authors.add(a.trim());
                        if (!authors.isEmpty()) o.add("authors", authors);
                    }
                    return new GsonBuilder().setPrettyPrinting().create().toJson(o).getBytes(StandardCharsets.UTF_8);
                }
            } else if (lowPath.equals("meta-inf/mods.toml") || lowPath.equals("meta-inf/neoforge.mods.toml")) {
                String patched = text
                        .replaceFirst("(?m)^(\\s*displayName\\s*=\\s*).*$", "$1" + Matcher.quoteReplacement(quoteToml(p.meta.name)))
                        .replaceFirst("(?m)^(\\s*version\\s*=\\s*)(?!\"\\$\\{).*$", "$1" + Matcher.quoteReplacement(quoteToml(p.meta.version)));
                if (!p.meta.authors.isBlank()) {
                    patched = setOrAppendTomlKey(patched, "authors", quoteToml(p.meta.authors));
                }
                if (!p.meta.description.isBlank()) {
                    String next = patched.replaceFirst("(?m)^(\\s*description\\s*=\\s*)(?!'''|\"\"\").*$", "$1" + Matcher.quoteReplacement(quoteToml(p.meta.description)));
                    if (next.equals(patched) && !patched.matches("(?s).*^\\s*description\\s*=.*")) {
                        next = setOrAppendTomlKey(patched, "description", quoteToml(p.meta.description));
                    }
                    patched = next;
                }
                return patched.getBytes(StandardCharsets.UTF_8);
            } else if (lowPath.equals("mcmod.info")) {
                JsonReader jr = new JsonReader(new StringReader(text));
                jr.setStrictness(Strictness.LENIENT);
                JsonElement je = JsonParser.parseReader(jr);
                if (je != null && je.isJsonArray() && !je.getAsJsonArray().isEmpty() && je.getAsJsonArray().get(0).isJsonObject()) {
                    JsonObject o = je.getAsJsonArray().get(0).getAsJsonObject();
                    if (!p.meta.name.isBlank()) o.addProperty("name", p.meta.name);
                    if (!p.meta.version.isBlank()) o.addProperty("version", p.meta.version);
                    if (!p.meta.description.isBlank()) o.addProperty("description", p.meta.description);
                    return new GsonBuilder().setPrettyPrinting().create().toJson(je).getBytes(StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return orig;
    }

    private static String setOrAppendTomlKey(String toml, String key, String quotedVal) {
        String replaced = toml.replaceFirst("(?m)^(\\s*" + key + "\\s*=\\s*).*$", "$1" + Matcher.quoteReplacement(quotedVal));
        if (!replaced.equals(toml)) return replaced;
        int depIdx = toml.indexOf("[[dependencies");
        String line = key + "=" + quotedVal + "\n";
        if (depIdx >= 0) return toml.substring(0, depIdx) + line + toml.substring(depIdx);
        return toml + (toml.endsWith("\n") ? "" : "\n") + line;
    }

    private static String quoteToml(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Map<String, byte[]> readZipEntries(Path jar) throws IOException {
        for (Charset cs : List.of(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1)) {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            try (ZipFile zf = new ZipFile(jar.toFile(), cs)) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) continue;
                    String name = e.getName().replace('\\', '/');
                    while (name.startsWith("./")) name = name.substring(2);
                    while (name.startsWith("/")) name = name.substring(1);
                    try (InputStream is = zf.getInputStream(e)) {
                        entries.put(name, is.readAllBytes());
                    }
                }
                return entries;
            } catch (Exception ignored) {
            }
        }
        throw new IOException("Could not read JAR entries: " + jar);
    }
}
