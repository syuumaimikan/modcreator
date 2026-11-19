package modstudio.core;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.core.Systems.*;
import modstudio.i18n.I18n;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Imports existing Minecraft artifacts (.jar mods/plugins, .zip/folder data & resource packs,
 * Bedrock .mcaddon/.mcpack add-ons) into an editable ModStudio {@link Project}.
 */
public final class ProjectImporter {
    private ProjectImporter() {}

    public record Report(Project project, Map<String, Integer> counts, List<String> notes) {}

    /** Hook context exposed to {@link modstudio.ext.ModStudioPlugin} custom archive analyzers. */
    public record ImportHookContext(Project project, Path source, Map<String, byte[]> files, List<String> notes) {}

    private static final List<Consumer<ImportHookContext>> CUSTOM_ANALYZERS = new CopyOnWriteArrayList<>();

    /** Registers an external archive enrichment hook invoked at the end of {@link #importArtifact(Path)}. */
    public static void registerAnalyzer(Consumer<ImportHookContext> analyzer) {
        if (analyzer != null) CUSTOM_ANALYZERS.add(analyzer);
    }

    public static Report importArtifact(Path source) throws IOException {
        if (source == null || !Files.exists(source)) throw new IOException(I18n.tOr("import.error.not_found", "File not found: " + source, String.valueOf(source)));
        EntryStore store = EntryStore.read(source);
        State st = new State(source, store);
        st.run();
        if (!CUSTOM_ANALYZERS.isEmpty()) {
            ImportHookContext hookCtx = new ImportHookContext(st.p, source, Collections.unmodifiableMap(store.files), st.notes);
            for (var analyzer : CUSTOM_ANALYZERS) analyzer.accept(hookCtx);
        }
        return new Report(st.p, st.counts(), st.notes);
    }

    // ------------------------------------------------------------------ archive & folder store

    private static final class EntryStore {
        private static final List<String> ROOT_MARKERS = List.of(
                "pack.mcmeta", "fabric.mod.json", "quilt.mod.json",
                "META-INF/mods.toml", "META-INF/neoforge.mods.toml",
                "mcmod.info", "litemod.json", "riftmod.json",
                "plugin.yml", "paper-plugin.yml", "manifest.json"
        );
        private static final List<String> GRADLE_MARKERS = List.of(
                "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties"
        );
        private static final List<String> GRADLE_SUBROOTS = List.of(
                "src/main/resources/", "src/generated/resources/", "src/main/java/"
        );

        private static final byte[] EMPTY_BYTES = new byte[0];
        final Map<String, byte[]> files = new LinkedHashMap<>();
        final Map<String, String> ciPaths = new LinkedHashMap<>();
        int classFileCount;

        private static boolean isOpaqueBinary(String path) {
            String low = path.toLowerCase(Locale.ROOT);
            return low.endsWith(".class") || low.endsWith(".dll") || low.endsWith(".so")
                    || low.endsWith(".dylib") || low.endsWith(".jnilib") || low.endsWith(".exe");
        }

        static EntryStore read(Path source) throws IOException {
            EntryStore raw = new EntryStore();
            if (Files.isDirectory(source)) {
                try (var walk = Files.walk(source)) {
                    for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                        String rel = norm(source.relativize(f).toString());
                        if (rel.startsWith(".git/") || rel.startsWith(".gradle/") || rel.startsWith(".idea/") || rel.startsWith("run/")) {
                            continue;
                        }
                        raw.put(rel, isOpaqueBinary(rel) ? EMPTY_BYTES : Files.readAllBytes(f));
                    }
                }
            } else if (!readViaZipFile(source, raw)) {
                try (InputStream fis = Files.newInputStream(source); ZipInputStream zis = new ZipInputStream(fis)) {
                    ZipEntry e;
                    while ((e = zis.getNextEntry()) != null) {
                        if (e.isDirectory()) continue;
                        String rel = norm(e.getName());
                        raw.put(rel, isOpaqueBinary(rel) ? EMPTY_BYTES : zis.readAllBytes());
                    }
                }
            }
            return flattenGradleRoots(unwrapRoot(raw));
        }

        private static boolean readViaZipFile(Path source, EntryStore dest) {
            for (Charset cs : List.of(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1)) {
                dest.clear();
                try (ZipFile zf = new ZipFile(source.toFile(), cs)) {
                    Enumeration<? extends ZipEntry> en = zf.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry e = en.nextElement();
                        if (e.isDirectory()) continue;
                        String rel = norm(e.getName());
                        if (isOpaqueBinary(rel)) {
                            dest.put(rel, EMPTY_BYTES);
                        } else {
                            try (InputStream is = zf.getInputStream(e)) {
                                dest.put(rel, is.readAllBytes());
                            }
                        }
                    }
                    if (!dest.files.isEmpty()) return true;
                } catch (Exception ignored) {
                }
            }
            dest.clear();
            return false;
        }

        private void clear() {
            files.clear();
            ciPaths.clear();
            classFileCount = 0;
        }

        private void put(String path, byte[] bytes) {
            if (!files.containsKey(path) && path.toLowerCase(Locale.ROOT).endsWith(".class")) classFileCount++;
            files.put(path, bytes);
            ciPaths.putIfAbsent(path.toLowerCase(Locale.ROOT), path);
        }

        private void putIfAbsent(String path, byte[] bytes) {
            if (!files.containsKey(path)) put(path, bytes);
        }

        private static EntryStore unwrapRoot(EntryStore raw) {
            EntryStore cur = raw;
            for (int depth = 0; depth < 2; depth++) {
                if (hasRootMarkerCI(cur) || hasGradleMarkerCI(cur)) break;
                String prefix = singleTopFolder(cur.files);
                if (prefix == null) break;
                EntryStore unwrapped = new EntryStore();
                for (var e : cur.files.entrySet()) {
                    if (isOsJunk(e.getKey())) continue;
                    if (e.getKey().startsWith(prefix)) {
                        String stripped = e.getKey().substring(prefix.length());
                        if (!stripped.isEmpty()) unwrapped.put(stripped, e.getValue());
                    }
                }
                if (hasRootMarkerCI(unwrapped) || hasGradleMarkerCI(unwrapped) || hasAnyGradleSubroot(unwrapped.files)) {
                    cur = unwrapped;
                } else {
                    break;
                }
            }
            return cur;
        }

        private static EntryStore flattenGradleRoots(EntryStore cur) {
            if (hasRootMarkerCI(cur) || !hasAnyGradleSubroot(cur.files)) return cur;
            EntryStore merged = new EntryStore();
            for (var e : cur.files.entrySet()) {
                String mapped = stripGradlePrefix(e.getKey());
                if (mapped != null && !mapped.isEmpty()) {
                    merged.putIfAbsent(mapped, e.getValue());
                }
            }
            for (var e : cur.files.entrySet()) {
                if (stripGradlePrefix(e.getKey()) == null && !e.getKey().startsWith("build/")) {
                    merged.putIfAbsent(e.getKey(), e.getValue());
                }
            }
            return merged.files.isEmpty() ? cur : merged;
        }

        private static boolean hasRootMarkerCI(EntryStore s) {
            for (String p : ROOT_MARKERS) {
                if (s.hasCI(p)) return true;
            }
            for (String k : s.files.keySet()) {
                String kl = k.toLowerCase(Locale.ROOT);
                if (kl.startsWith("data/") || kl.startsWith("assets/")) return true;
            }
            return false;
        }

        private static boolean hasGradleMarkerCI(EntryStore s) {
            for (String p : GRADLE_MARKERS) {
                if (s.hasCI(p)) return true;
            }
            return false;
        }

        private static boolean hasAnyGradleSubroot(Map<String, byte[]> map) {
            for (String k : map.keySet()) {
                if (stripGradlePrefix(k) != null) return true;
            }
            return false;
        }

        private static String stripGradlePrefix(String k) {
            for (String sub : GRADLE_SUBROOTS) {
                if (k.startsWith(sub)) return k.substring(sub.length());
                int slash = k.indexOf('/');
                if (slash > 0 && k.startsWith(sub, slash + 1)) {
                    String mod = k.substring(0, slash);
                    if (!mod.equals("build") && !mod.equals(".gradle")) {
                        return k.substring(slash + 1 + sub.length());
                    }
                }
            }
            return null;
        }

        private static boolean isOsJunk(String k) {
            return k.startsWith("__MACOSX/") || k.equals(".DS_Store") || k.endsWith("/.DS_Store")
                    || k.equals("Thumbs.db") || k.endsWith("/Thumbs.db");
        }

        private static String singleTopFolder(Map<String, byte[]> map) {
            String prefix = null;
            for (String k : map.keySet()) {
                if (isOsJunk(k)) continue;
                int slash = k.indexOf('/');
                if (slash <= 0) return null;
                String top = k.substring(0, slash + 1);
                if (prefix == null) prefix = top;
                else if (!prefix.equals(top)) return null;
            }
            return prefix;
        }

        boolean has(String path) { return files.containsKey(path); }
        byte[] get(String path) { return files.get(path); }

        String findCI(String path) { return ciPaths.get(path.toLowerCase(Locale.ROOT)); }
        boolean hasCI(String path) { return findCI(path) != null; }

        byte[] getCI(String path) {
            String k = findCI(path);
            return k != null ? files.get(k) : null;
        }

        String text(String path) {
            byte[] b = files.get(path);
            if (b == null) return null;
            String s = new String(b, StandardCharsets.UTF_8);
            return s.startsWith("\uFEFF") ? s.substring(1) : s;
        }

        String textCI(String path) {
            String k = findCI(path);
            return k != null ? text(k) : null;
        }

        JsonElement jsonElement(String path) {
            String t = text(path);
            if (t == null || t.isBlank()) return null;
            try {
                JsonReader jr = new JsonReader(new StringReader(cleanJsonTrailingCommas(t)));
                jr.setStrictness(Strictness.LENIENT);
                return JsonParser.parseReader(jr);
            } catch (Exception e) {
                return null;
            }
        }

        private static String cleanJsonTrailingCommas(String raw) {
            StringBuilder out = new StringBuilder(raw.length());
            int n = raw.length();
            boolean inStr = false;
            for (int i = 0; i < n; i++) {
                char c = raw.charAt(i);
                if (inStr) {
                    out.append(c);
                    if (c == '\\' && i + 1 < n) {
                        out.append(raw.charAt(++i));
                    } else if (c == '"') {
                        inStr = false;
                    }
                } else if (c == '"') {
                    inStr = true;
                    out.append(c);
                } else if (c == '/' && i + 1 < n && raw.charAt(i + 1) == '/') {
                    while (i < n && raw.charAt(i) != '\n') out.append(raw.charAt(i++));
                    if (i < n) out.append('\n');
                } else if (c == '/' && i + 1 < n && raw.charAt(i + 1) == '*') {
                    out.append("/*");
                    i += 2;
                    while (i + 1 < n && !(raw.charAt(i) == '*' && raw.charAt(i + 1) == '/')) {
                        out.append(raw.charAt(i++));
                    }
                    if (i + 1 < n) {
                        out.append("*/");
                        i++;
                    } else {
                        break;
                    }
                } else if (c == ',') {
                    int j = skipWsAndComments(raw, i + 1);
                    if (j < n && (raw.charAt(j) == '}' || raw.charAt(j) == ']')) continue;
                    out.append(c);
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }

        private static int skipWsAndComments(String raw, int idx) {
            int n = raw.length();
            while (idx < n) {
                char nc = raw.charAt(idx);
                if (Character.isWhitespace(nc)) {
                    idx++;
                } else if (nc == '/' && idx + 1 < n && raw.charAt(idx + 1) == '/') {
                    idx += 2;
                    while (idx < n && raw.charAt(idx) != '\n') idx++;
                } else if (nc == '/' && idx + 1 < n && raw.charAt(idx + 1) == '*') {
                    idx += 2;
                    while (idx + 1 < n && !(raw.charAt(idx) == '*' && raw.charAt(idx + 1) == '/')) idx++;
                    idx = Math.min(n, idx + 2);
                } else {
                    break;
                }
            }
            return idx;
        }

        JsonObject json(String path) {
            JsonElement je = jsonElement(path);
            return je != null && je.isJsonObject() ? je.getAsJsonObject() : null;
        }

        JsonElement jsonElementCI(String path) {
            String k = findCI(path);
            return k != null ? jsonElement(k) : null;
        }

        JsonObject jsonCI(String path) {
            String k = findCI(path);
            return k != null ? json(k) : null;
        }

        private static String norm(String p) {
            String s = p.replace('\\', '/');
            while (s.startsWith("./")) s = s.substring(2);
            while (s.startsWith("/")) s = s.substring(1);
            return s;
        }
    }

    // ------------------------------------------------------------------ importer state

    private static final class State {
        final Path source;
        final EntryStore store;
        final Project p = new Project();
        final List<String> notes = new ArrayList<>();
        final Map<String, String> texByKey = new LinkedHashMap<>();
        final Map<String, Map<String, String>> langs = new LinkedHashMap<>();
        final Set<String> consumedLangKeys = new HashSet<>();
        final Set<String> loadFns = new HashSet<>();
        final Set<String> tickFns = new HashSet<>();
        final Set<String> createdModels = new HashSet<>();
        final Map<String, Element> blockByName = new LinkedHashMap<>();
        final Map<String, Elements.Item> itemByName = new LinkedHashMap<>();
        final Map<String, String> advIdMap = new LinkedHashMap<>();
        final List<Elements.Advancement> importedAdvs = new ArrayList<>();
        final Set<String> preservedPaths = new LinkedHashSet<>();
        final Map<String, Set<String>> blockTags = new LinkedHashMap<>();
        final Map<String, Set<String>> itemTags = new LinkedHashMap<>();
        final Map<String, JsonObject> blockLootJsons = new LinkedHashMap<>();
        final Map<String, JsonObject> configuredFeatures = new LinkedHashMap<>();
        final Map<String, JsonObject> placedFeatures = new LinkedHashMap<>();
        final List<JsonObject> biomeModifierJsons = new ArrayList<>();
        final Map<String, String> javaSources = new LinkedHashMap<>();
        boolean hasPackageFromDescriptor;

        State(Path source, EntryStore store) {
            this.source = source;
            this.store = store;
            p.sourceArtifact = source.toAbsolutePath().toString();
        }

        void run() {
            detectMetadata();
            readTextures();
            readLanguages();
            readTagFunctionHooks();
            readShaders();
            readSounds();
            readBlocksAndItems();
            readModels();
            readData();
            readBedrockContent();
            readMixinsAndCode();
            enrichImportedFeatures();
            finalizeProject();
        }

        Map<String, Integer> counts() {
            Map<String, Integer> r = new LinkedHashMap<>();
            for (ElementType t : ElementType.values()) {
                int c = p.ofType(t).size();
                if (c > 0) r.put(t.id, c);
            }
            if (!p.textures.isEmpty()) r.put("textures", p.textures.size());
            return r;
        }

        // -------------------------------------------------------------- metadata

        private void detectMetadata() {
            List<String> targets = new ArrayList<>();
            p.meta.targets = targets;
            p.meta.name = Ids.titleCase(stripExt(source.getFileName().toString()));
            p.meta.modId = sanitizeModId(stripExt(source.getFileName().toString()));
            p.meta.packageName = "com.example." + p.meta.modId;
            p.meta.mcVersions = new ArrayList<>();

            boolean isJarArchive = source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
            String iconPath = null;
            boolean hasModDescriptor = false;

            if (store.hasCI("quilt.mod.json")) {
                targets.add(Target.QUILT.id);
                hasModDescriptor = true;
                iconPath = parseQuilt(store.jsonCI("quilt.mod.json"));
            }
            if (store.hasCI("fabric.mod.json")) {
                if (!targets.contains(Target.QUILT.id)) targets.add(Target.FABRIC.id);
                hasModDescriptor = true;
                String fi = parseFabric(store.jsonCI("fabric.mod.json"));
                if (iconPath == null) iconPath = fi;
            }
            boolean parsedModsToml = false;
            if (store.hasCI("META-INF/neoforge.mods.toml")) {
                targets.add(Target.NEOFORGE.id);
                hasModDescriptor = true;
                String ni = parseModsToml(store.textCI("META-INF/neoforge.mods.toml"));
                if (iconPath == null) iconPath = ni;
                parsedModsToml = true;
            }
            if (store.hasCI("META-INF/mods.toml")) {
                String toml = store.textCI("META-INF/mods.toml");
                String modTomlTarget = isNeoForgeToml(toml) ? Target.NEOFORGE.id : Target.FORGE.id;
                if (!targets.contains(modTomlTarget)) targets.add(modTomlTarget);
                hasModDescriptor = true;
                if (!parsedModsToml) {
                    String fi = parseModsToml(toml);
                    if (iconPath == null) iconPath = fi;
                }
            }
            if (store.hasCI("mcmod.info")) {
                if (!targets.contains(Target.FORGE.id) && !targets.contains(Target.NEOFORGE.id)) {
                    targets.add(Target.FORGE.id);
                }
                hasModDescriptor = true;
                String mi = parseMcmodInfo(store.jsonElementCI("mcmod.info"));
                if (iconPath == null) iconPath = mi;
            }
            if (store.hasCI("litemod.json")) {
                if (!targets.contains(Target.FABRIC.id)) targets.add(Target.FABRIC.id);
                hasModDescriptor = true;
                parseLitemodJson(store.jsonCI("litemod.json"));
            }
            if (store.hasCI("riftmod.json")) {
                if (!targets.contains(Target.FABRIC.id)) targets.add(Target.FABRIC.id);
                hasModDescriptor = true;
                parseRiftJson(store.jsonCI("riftmod.json"));
            }
            if (store.hasCI("paper-plugin.yml") || store.hasCI("plugin.yml")) {
                String ymlKey = store.hasCI("paper-plugin.yml") ? "paper-plugin.yml" : "plugin.yml";
                parsePluginYml(store.textCI(ymlKey), targets);
                hasModDescriptor = true;
            } else if (targets.isEmpty() && (store.hasCI("velocity-plugin.json") || store.hasCI("bungee.yml"))) {
                targets.add(Target.PAPER.id);
                hasModDescriptor = true;
            }
            parseBedrockManifests(targets);

            if (targets.isEmpty()) {
                inspectNestedJars(targets);
            }

            boolean isJavaArtifact = isJarArchive || store.classFileCount > 0 || hasJavaSources()
                    || hasMixinConfigs() || hasModServiceOrManifest();
            if (targets.isEmpty() && isJavaArtifact) {
                inferJavaLoader(targets);
            }

            boolean hasData = hasAnyPrefix("data/") || hasAnyPrefix("datapack/data/");
            boolean hasAssets = hasAnyPrefix("assets/");
            if (targets.isEmpty()) {
                if (hasData) targets.add(Target.DATAPACK.id);
                if (hasAssets) targets.add(Target.RESOURCEPACK.id);
                if (targets.isEmpty() && store.hasCI("pack.mcmeta")) targets.add(Target.DATAPACK.id);
            }
            if (targets.isEmpty()) targets.add(Target.DATAPACK.id);

            if (p.meta.mcVersions.isEmpty()) {
                matchMcVersion(source.getFileName().toString());
            }
            JsonObject mcmeta = store.jsonCI("pack.mcmeta");
            if (mcmeta != null) parsePackMeta(mcmeta, p.meta.mcVersions.isEmpty());

            if (!hasModDescriptor) {
                String ns = inferPrimaryNamespace();
                if (ns != null && !ns.equals("minecraft")) p.meta.modId = sanitizeModId(ns);
            }
            inferPackageFromClasses();
            if (p.meta.mcVersions.isEmpty()) {
                boolean hasLegacySource = !p.meta.sourceMcVersion.isBlank() && McVersion.orNull(p.meta.sourceMcVersion) == null;
                McVersion fallback = hasLegacySource ? McVersion.V1_20_1 : McVersion.V1_21_1;
                p.meta.mcVersion = fallback.id;
                p.meta.mcVersions.add(fallback.id);
            } else {
                p.meta.mcVersion = p.meta.mcVersions.get(p.meta.mcVersions.size() - 1);
            }
            p.normalizeVersions();
            ensureTargetVersionCompatibility();
            if (p.meta.sourceMcVersion.isBlank()) p.meta.sourceMcVersion = p.meta.mcVersion;
            if (!p.meta.sourceMcVersion.equals(p.meta.mcVersion) && McVersion.orNull(p.meta.sourceMcVersion) == null) {
                notes.add("Legacy Minecraft version detected (" + p.meta.sourceMcVersion + "); ready to edit and upgrade to modern versions.");
            }

            if (iconPath != null) loadIcon(iconPath);
            if (p.meta.icon.isBlank() && store.hasCI("pack.png")) loadIcon("pack.png");
            if (p.meta.icon.isBlank() && store.hasCI("pack_icon.png")) loadIcon("pack_icon.png");
            if (!targets.stream().anyMatch(s -> Target.of(s) != null && Target.of(s).usesGradle())) {
                // Non-code packs don't rely on package names
                p.meta.packageName = "com.example." + p.meta.modId;
            }
        }

        private String parseFabric(JsonObject o) {
            if (o == null) return null;
            if (hasStr(o, "id")) p.meta.modId = sanitizeModId(o.get("id").getAsString());
            if (hasStr(o, "name")) p.meta.name = o.get("name").getAsString();
            if (hasStr(o, "version")) p.meta.version = o.get("version").getAsString();
            if (hasStr(o, "description")) p.meta.description = o.get("description").getAsString();
            if (hasStr(o, "license")) p.meta.license = o.get("license").getAsString();
            if (o.has("authors") && o.get("authors").isJsonArray()) {
                List<String> a = new ArrayList<>();
                for (JsonElement je : o.getAsJsonArray("authors")) {
                    if (je.isJsonPrimitive()) a.add(je.getAsString());
                    else if (je.isJsonObject() && hasStr(je.getAsJsonObject(), "name")) a.add(je.getAsJsonObject().get("name").getAsString());
                }
                if (!a.isEmpty()) p.meta.authors = String.join(", ", a);
            }
            if (o.has("contact") && o.get("contact").isJsonObject() && hasStr(o.getAsJsonObject("contact"), "homepage")) {
                p.meta.website = o.getAsJsonObject("contact").get("homepage").getAsString();
            }
            if (o.has("entrypoints") && o.get("entrypoints").isJsonObject()) {
                JsonObject ep = o.getAsJsonObject("entrypoints");
                for (String key : List.of("main", "client")) {
                    if (!ep.has(key) || !ep.get(key).isJsonArray() || ep.getAsJsonArray(key).isEmpty()) continue;
                    String cls = ep.getAsJsonArray(key).get(0).isJsonPrimitive() ? ep.getAsJsonArray(key).get(0).getAsString()
                            : ep.getAsJsonArray(key).get(0).isJsonObject() && hasStr(ep.getAsJsonArray(key).get(0).getAsJsonObject(), "value")
                            ? ep.getAsJsonArray(key).get(0).getAsJsonObject().get("value").getAsString() : "";
                    int dot = cls.lastIndexOf('.');
                    if (dot > 0) {
                        String pkg = cls.substring(0, dot);
                        if (Ids.PACKAGE.matcher(pkg).matches()) {
                            p.meta.packageName = pkg;
                            hasPackageFromDescriptor = true;
                            break;
                        }
                    }
                }
            }
            if (o.has("depends") && o.get("depends").isJsonObject()) {
                for (var e : o.getAsJsonObject("depends").entrySet()) {
                    String depId = e.getKey();
                    String ver = "*";
                    if (e.getValue().isJsonPrimitive()) {
                        ver = e.getValue().getAsString();
                    } else if (e.getValue().isJsonArray()) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonElement je : e.getValue().getAsJsonArray()) {
                            if (je.isJsonPrimitive()) sb.append(je.getAsString()).append(' ');
                        }
                        if (!sb.isEmpty()) ver = sb.toString().trim();
                    }
                    if (depId.equals("minecraft")) {
                        matchMcVersion(ver);
                    } else if (!Set.of("fabricloader", "fabric", "fabric-api", "java").contains(depId)) {
                        ModDependency d = new ModDependency();
                        d.modId = depId; d.version = ver; d.required = true;
                        p.meta.dependencies.add(d);
                    }
                }
            }
            return hasStr(o, "icon") ? o.get("icon").getAsString() : null;
        }

        private String parseQuilt(JsonObject o) {
            if (o == null || !o.has("quilt_loader") || !o.get("quilt_loader").isJsonObject()) return null;
            JsonObject ql = o.getAsJsonObject("quilt_loader");
            if (hasStr(ql, "id")) p.meta.modId = sanitizeModId(ql.get("id").getAsString());
            if (hasStr(ql, "version")) p.meta.version = ql.get("version").getAsString();
            if (ql.has("metadata") && ql.get("metadata").isJsonObject()) {
                JsonObject md = ql.getAsJsonObject("metadata");
                if (hasStr(md, "name")) p.meta.name = md.get("name").getAsString();
                if (hasStr(md, "description")) p.meta.description = md.get("description").getAsString();
                if (md.has("license") && md.get("license").isJsonPrimitive()) p.meta.license = md.get("license").getAsString();
                if (hasStr(md, "icon")) return md.get("icon").getAsString();
            }
            if (ql.has("depends") && ql.get("depends").isJsonArray()) {
                for (JsonElement je : ql.getAsJsonArray("depends")) {
                    if (!je.isJsonObject()) continue;
                    JsonObject doj = je.getAsJsonObject();
                    if (!hasStr(doj, "id")) continue;
                    String did = doj.get("id").getAsString();
                    String ver = hasStr(doj, "versions") ? doj.get("versions").getAsString() : "*";
                    if (did.equals("minecraft")) matchMcVersion(ver);
                    else if (!Set.of("quilt_loader", "quilted_fabric_api").contains(did)) {
                        ModDependency d = new ModDependency();
                        d.modId = did; d.version = ver; d.required = !(doj.has("optional") && doj.get("optional").getAsBoolean());
                        p.meta.dependencies.add(d);
                    }
                }
            }
            return null;
        }

        private String parseModsToml(String toml) {
            if (toml == null) return null;
            String logo = null;
            String section = "";
            String curDepId = null, curDepVer = "*";
            boolean curDepReq = true;
            String[] lines = toml.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String cleanLine = stripTomlComment(line).trim();
                if (cleanLine.isEmpty()) continue;
                if (cleanLine.startsWith("[[")) {
                    flushTomlDep(curDepId, curDepVer, curDepReq);
                    curDepId = null; curDepVer = "*"; curDepReq = true;
                    section = cleanLine;
                    continue;
                }
                if (cleanLine.startsWith("[")) {
                    flushTomlDep(curDepId, curDepVer, curDepReq);
                    curDepId = null; curDepVer = "*"; curDepReq = true;
                    section = cleanLine;
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String rawVal = line.substring(eq + 1).trim();
                if (rawVal.startsWith("'''") || rawVal.startsWith("\"\"\"")) {
                    String delim = rawVal.substring(0, 3);
                    StringBuilder sb = new StringBuilder(rawVal.substring(3));
                    if (sb.toString().contains(delim)) {
                        int end = sb.indexOf(delim);
                        rawVal = delim + sb.substring(0, end) + delim;
                    } else {
                        while (++i < lines.length) {
                            String nl = lines[i];
                            int end = nl.indexOf(delim);
                            if (end >= 0) {
                                sb.append('\n').append(nl, 0, end);
                                break;
                            }
                            sb.append('\n').append(nl);
                        }
                        rawVal = delim + sb + delim;
                    }
                } else {
                    rawVal = stripTomlComment(rawVal).trim();
                }
                String v = unquoteToml(rawVal);
                if (section.isEmpty()) {
                    if (k.equals("license")) p.meta.license = v;
                } else if (section.equals("[[mods]]")) {
                    switch (k) {
                        case "modId" -> p.meta.modId = sanitizeModId(v);
                        case "displayName" -> p.meta.name = v;
                        case "version" -> { if (!v.contains("${")) p.meta.version = v; }
                        case "description" -> p.meta.description = v;
                        case "authors" -> p.meta.authors = v;
                        case "displayURL" -> p.meta.website = v;
                        case "logoFile" -> logo = v;
                    }
                } else if (section.startsWith("[[dependencies.")) {
                    switch (k) {
                        case "modId" -> curDepId = v;
                        case "versionRange" -> curDepVer = v;
                        case "mandatory" -> curDepReq = Boolean.parseBoolean(v);
                        case "type" -> curDepReq = "required".equalsIgnoreCase(v);
                    }
                }
            }
            flushTomlDep(curDepId, curDepVer, curDepReq);
            return logo;
        }

        private void flushTomlDep(String curDepId, String curDepVer, boolean curDepReq) {
            if (curDepId == null) return;
            if ("minecraft".equals(curDepId)) {
                matchMcVersion(curDepVer);
            } else if ("neoforge".equals(curDepId)) {
                matchNeoForgeRange(curDepVer);
            } else if (!"forge".equals(curDepId)) {
                ModDependency d = new ModDependency();
                d.modId = curDepId; d.version = curDepVer; d.required = curDepReq;
                p.meta.dependencies.add(d);
            }
        }

        private void matchNeoForgeRange(String expr) {
            if (expr == null) return;
            var m = Pattern.compile("(20|21|26)\\.(\\d+)").matcher(expr);
            while (m.find()) {
                String candidate = "1." + m.group(1) + "." + m.group(2);
                McVersion v = McVersion.orNull(candidate);
                if (v == null && m.group(1).equals("26")) v = McVersion.V26_1;
                if (v != null && !p.meta.mcVersions.contains(v.id)) {
                    p.meta.mcVersions.add(v.id);
                    if (p.meta.sourceMcVersion.isBlank()) p.meta.sourceMcVersion = v.id;
                }
            }
        }

        private void ensureTargetVersionCompatibility() {
            if (!p.targets().isEmpty() || p.meta.targets.isEmpty()) return;
            for (String tid : p.meta.targets) {
                Target t = Target.of(tid);
                if (t == null) continue;
                for (McVersion v : McVersion.values()) {
                    if (t.supports(v)) {
                        p.meta.mcVersion = v.id;
                        p.meta.mcVersions = new ArrayList<>(List.of(v.id));
                        p.normalizeVersions();
                        return;
                    }
                }
            }
        }

        private static boolean isNeoForgeToml(String toml) {
            if (toml == null) return false;
            String low = toml.toLowerCase(Locale.ROOT);
            return low.contains("net.neoforged")
                    || Pattern.compile("(?i)(modid\\s*=\\s*[\"']neoforge[\"']|\\[dependencies\\.neoforge\\])").matcher(toml).find();
        }

        private String parseMcmodInfo(JsonElement je) {
            JsonObject o = null;
            if (je != null && je.isJsonArray() && !je.getAsJsonArray().isEmpty()) {
                JsonElement first = je.getAsJsonArray().get(0);
                if (first.isJsonObject()) o = first.getAsJsonObject();
            } else if (je != null && je.isJsonObject()) {
                JsonObject root = je.getAsJsonObject();
                if (root.has("modList") && root.get("modList").isJsonArray() && !root.getAsJsonArray("modList").isEmpty()) {
                    JsonElement first = root.getAsJsonArray("modList").get(0);
                    if (first.isJsonObject()) o = first.getAsJsonObject();
                } else {
                    o = root;
                }
            }
            if (o == null) return null;
            if (hasStr(o, "modid")) p.meta.modId = sanitizeModId(o.get("modid").getAsString());
            if (hasStr(o, "name")) p.meta.name = o.get("name").getAsString();
            if (hasStr(o, "version")) p.meta.version = o.get("version").getAsString();
            if (hasStr(o, "description")) p.meta.description = o.get("description").getAsString();
            if (hasStr(o, "url")) p.meta.website = o.get("url").getAsString();
            if (hasStr(o, "mcversion")) matchMcVersion(o.get("mcversion").getAsString());
            if (o.has("authorList") && o.get("authorList").isJsonArray()) {
                List<String> a = new ArrayList<>();
                for (JsonElement ax : o.getAsJsonArray("authorList")) {
                    if (ax.isJsonPrimitive()) a.add(ax.getAsString());
                }
                if (!a.isEmpty()) p.meta.authors = String.join(", ", a);
            }
            return hasStr(o, "logoFile") ? o.get("logoFile").getAsString() : null;
        }

        private void parseLitemodJson(JsonObject o) {
            if (o == null) return;
            if (hasStr(o, "name")) {
                p.meta.name = o.get("name").getAsString();
                p.meta.modId = sanitizeModId(p.meta.name);
            }
            if (hasStr(o, "version")) p.meta.version = o.get("version").getAsString();
            if (hasStr(o, "author")) p.meta.authors = o.get("author").getAsString();
            if (hasStr(o, "description")) p.meta.description = o.get("description").getAsString();
            if (hasStr(o, "mcversion")) matchMcVersion(o.get("mcversion").getAsString());
        }

        private void parseRiftJson(JsonObject o) {
            if (o == null) return;
            if (hasStr(o, "id")) p.meta.modId = sanitizeModId(o.get("id").getAsString());
            if (hasStr(o, "name")) p.meta.name = o.get("name").getAsString();
            if (hasStr(o, "version")) p.meta.version = o.get("version").getAsString();
        }

        private void inspectNestedJars(List<String> targets) {
            for (var en : store.files.entrySet()) {
                String low = en.getKey().toLowerCase(Locale.ROOT);
                if (!(low.startsWith("meta-inf/jars/") || low.startsWith("meta-inf/jarjar/")) || !low.endsWith(".jar")) {
                    continue;
                }
                try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(en.getValue()))) {
                    ZipEntry ze;
                    while ((ze = zis.getNextEntry()) != null) {
                        if (ze.isDirectory()) continue;
                        String ne = EntryStore.norm(ze.getName()).toLowerCase(Locale.ROOT);
                        if (ne.equals("quilt.mod.json") && !targets.contains(Target.QUILT.id)) {
                            targets.add(Target.QUILT.id);
                        } else if (ne.equals("fabric.mod.json") && !targets.contains(Target.FABRIC.id) && !targets.contains(Target.QUILT.id)) {
                            targets.add(Target.FABRIC.id);
                        } else if (ne.equals("meta-inf/neoforge.mods.toml") && !targets.contains(Target.NEOFORGE.id)) {
                            targets.add(Target.NEOFORGE.id);
                        } else if (ne.equals("meta-inf/mods.toml")) {
                            String toml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                            String t = isNeoForgeToml(toml) ? Target.NEOFORGE.id : Target.FORGE.id;
                            if (!targets.contains(t)) targets.add(t);
                        } else if (ne.equals("mcmod.info") && !targets.contains(Target.FORGE.id)) {
                            targets.add(Target.FORGE.id);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        private boolean hasJavaSources() {
            for (String k : store.files.keySet()) {
                if (k.toLowerCase(Locale.ROOT).endsWith(".java")) return true;
            }
            return false;
        }

        private boolean hasMixinConfigs() {
            for (String k : store.files.keySet()) {
                String kl = k.toLowerCase(Locale.ROOT);
                if ((kl.endsWith(".mixins.json") || basename(kl).startsWith("mixins.")) && kl.endsWith(".json")) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasModServiceOrManifest() {
            for (String k : store.files.keySet()) {
                String kl = k.toLowerCase(Locale.ROOT);
                if (kl.startsWith("meta-inf/services/cpw.mods.")
                        || kl.startsWith("meta-inf/services/net.minecraftforge.")
                        || kl.startsWith("meta-inf/services/net.neoforged.")
                        || kl.startsWith("meta-inf/services/net.fabricmc.")
                        || kl.startsWith("meta-inf/services/org.quiltmc.")) {
                    return true;
                }
            }
            String mf = store.textCI("META-INF/MANIFEST.MF");
            if (mf == null) return false;
            String mfl = mf.toLowerCase(Locale.ROOT);
            return mfl.contains("fmlcoreplugin") || mfl.contains("tweakclass")
                    || mfl.contains("mixinconfigs") || mfl.contains("fabric-")
                    || mfl.contains("neoforge") || mfl.contains("forge-");
        }

        private void inferJavaLoader(List<String> targets) {
            String mf = store.textCI("META-INF/MANIFEST.MF");
            StringBuilder clues = new StringBuilder(mf != null ? mf.toLowerCase(Locale.ROOT) : "");
            for (String k : store.files.keySet()) {
                String kl = k.toLowerCase(Locale.ROOT);
                if (kl.startsWith("meta-inf/services/") || kl.endsWith(".mixins.json")) {
                    clues.append('\n').append(kl);
                    String txt = store.text(k);
                    if (txt != null) clues.append('\n').append(txt.toLowerCase(Locale.ROOT));
                }
            }
            String c = clues.toString();
            if (c.contains("io.papermc") || c.contains("org.bukkit") || c.contains("org.spigotmc")) {
                targets.add(Target.PAPER.id);
            } else if (c.contains("org.quiltmc") || c.contains("quilt_loader")) {
                targets.add(Target.QUILT.id);
            } else if (c.contains("net.neoforged") || c.contains("neoforge")) {
                targets.add(Target.NEOFORGE.id);
            } else if (c.contains("net.fabricmc") || c.contains("fabric-loom") || c.contains("mixinconfigs")) {
                targets.add(Target.FABRIC.id);
            } else if (c.contains("net.minecraftforge") || c.contains("cpw.mods.") || c.contains("fmlcoreplugin")) {
                targets.add(Target.FORGE.id);
            } else {
                targets.add(hasMixinConfigs() ? Target.FABRIC.id : Target.FORGE.id);
            }
            notes.add("No standard mod descriptor (fabric.mod.json / mods.toml) was found; inferred Java Mod target ("
                    + targets.get(0) + "). You can adjust the target loader in Overview.");
        }

        private void parsePluginYml(String yml, List<String> targets) {
            if (yml == null) return;
            boolean isFolia = false;
            for (String raw : yml.split("\\R")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int colon = line.indexOf(':');
                if (colon <= 0 || raw.startsWith(" ")) continue;
                String k = line.substring(0, colon).trim();
                String v = unquoteYaml(line.substring(colon + 1).trim());
                switch (k) {
                    case "name" -> {
                        p.meta.name = v;
                        p.meta.modId = sanitizeModId(v);
                    }
                    case "version" -> p.meta.version = v;
                    case "description" -> p.meta.description = v;
                    case "website" -> p.meta.website = v;
                    case "author" -> p.meta.authors = v;
                    case "authors" -> p.meta.authors = v.replaceAll("[\\[\\]'\"]", "").trim();
                    case "main" -> {
                        int dot = v.lastIndexOf('.');
                        if (dot > 0) {
                            String pkg = v.substring(0, dot);
                            if (Ids.PACKAGE.matcher(pkg).matches()) {
                                p.meta.packageName = pkg;
                                hasPackageFromDescriptor = true;
                            }
                        }
                    }
                    case "api-version" -> matchMcVersion(v);
                    case "folia-supported" -> isFolia = Boolean.parseBoolean(v);
                    case "depend" -> parseYamlDepList(v, true);
                    case "softdepend" -> parseYamlDepList(v, false);
                }
            }
            targets.add(isFolia ? Target.FOLIA.id : Target.PAPER.id);
        }

        private void parseYamlDepList(String v, boolean required) {
            String clean = v.replace("[", "").replace("]", "");
            for (String item : clean.split(",")) {
                String id = unquoteYaml(item.trim());
                if (id.isEmpty()) continue;
                ModDependency d = new ModDependency();
                d.modId = id; d.required = required;
                p.meta.dependencies.add(d);
            }
        }

        private void parseBedrockManifests(List<String> targets) {
            for (var en : store.files.entrySet()) {
                String k = en.getKey();
                if (!k.equalsIgnoreCase("manifest.json") && !k.toLowerCase(Locale.ROOT).endsWith("/manifest.json")) continue;
                String kl = k.toLowerCase(Locale.ROOT);
                if (kl.startsWith("assets/") || kl.startsWith("data/") || kl.startsWith("meta-inf/")) continue;
                JsonObject m = store.json(k);
                if (m == null) continue;
                if (!((m.has("header") && m.has("modules")) || m.has("format_version"))) continue;
                if (!targets.contains(Target.BEDROCK.id)) targets.add(Target.BEDROCK.id);
                if (m.has("header") && m.get("header").isJsonObject()) {
                    JsonObject h = m.getAsJsonObject("header");
                    if (hasStr(h, "name")) {
                        p.meta.name = h.get("name").getAsString().replaceAll("\\s+(BP|RP)$", "");
                        p.meta.modId = sanitizeModId(p.meta.name);
                    }
                    if (hasStr(h, "description")) p.meta.description = h.get("description").getAsString();
                    if (h.has("version") && h.get("version").isJsonArray()) {
                        JsonArray va = h.getAsJsonArray("version");
                        if (va.size() >= 3) p.meta.version = va.get(0).getAsInt() + "." + va.get(1).getAsInt() + "." + va.get(2).getAsInt();
                    }
                }
                if (m.has("metadata") && m.get("metadata").isJsonObject()) {
                    JsonObject md = m.getAsJsonObject("metadata");
                    if (md.has("authors") && md.get("authors").isJsonArray()) {
                        List<String> a = new ArrayList<>();
                        for (JsonElement x : md.getAsJsonArray("authors")) if (x.isJsonPrimitive()) a.add(x.getAsString());
                        if (!a.isEmpty()) p.meta.authors = String.join(", ", a);
                    }
                }
            }
        }

        private void parsePackMeta(JsonObject mcmeta, boolean detectVersion) {
            if (!mcmeta.has("pack") || !mcmeta.get("pack").isJsonObject()) return;
            JsonObject pack = mcmeta.getAsJsonObject("pack");
            if (p.meta.description.isBlank() && pack.has("description") && pack.get("description").isJsonPrimitive()) {
                p.meta.description = pack.get("description").getAsString();
            }
            if (!detectVersion) return;
            Integer fmt = null;
            if (pack.has("min_format")) {
                JsonElement mf = pack.get("min_format");
                if (mf.isJsonArray() && !mf.getAsJsonArray().isEmpty() && mf.getAsJsonArray().get(0).isJsonPrimitive()) {
                    fmt = optInt(mf.getAsJsonArray().get(0), 0);
                } else if (mf.isJsonPrimitive()) {
                    fmt = optInt(mf, 0);
                }
            } else if (pack.has("pack_format") && pack.get("pack_format").isJsonPrimitive()) {
                fmt = optInt(pack.get("pack_format"), 0);
            }
            if (fmt != null) {
                McVersion best = McVersion.V1_21_1;
                int bestDiff = Integer.MAX_VALUE;
                boolean res = p.meta.targets.equals(List.of(Target.RESOURCEPACK.id));
                for (McVersion v : McVersion.values()) {
                    int diff = Math.abs(v.format(res) - fmt);
                    if (diff < bestDiff) { bestDiff = diff; best = v; }
                }
                p.meta.mcVersions.add(best.id);
                if (p.meta.sourceMcVersion.isBlank()) {
                    p.meta.sourceMcVersion = legacyVersionForFormat(fmt, best.id);
                }
            }
        }

        private static String legacyVersionForFormat(int fmt, String fallback) {
            return switch (fmt) {
                case 1 -> "1.8.9";
                case 2 -> "1.10.2";
                case 3 -> "1.12.2";
                case 4 -> "1.14.4";
                case 5 -> "1.15.2";
                case 6 -> "1.16.5";
                case 7 -> "1.17.1";
                case 8, 9 -> "1.18.2";
                case 10, 11, 12, 13 -> "1.19.4";
                case 15 -> "1.20.1";
                case 18 -> "1.20.2";
                case 22, 26 -> "1.20.4";
                default -> fallback;
            };
        }

        private void matchMcVersion(String expr) {
            if (expr == null) return;
            for (McVersion v : McVersion.values()) {
                if (expr.contains(v.id) && !p.meta.mcVersions.contains(v.id)) {
                    p.meta.mcVersions.add(v.id);
                }
            }
            if (p.meta.sourceMcVersion.isBlank()) {
                var m = Pattern.compile("(1\\.\\d+(?:\\.\\d+)?|26\\.\\d+(?:\\.\\d+)?)").matcher(expr);
                if (m.find()) p.meta.sourceMcVersion = m.group(1);
            }
        }

        private String inferPrimaryNamespace() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String k : store.files.keySet()) {
                String rel = k.startsWith("datapack/") ? k.substring(9) : k;
                String ns = null;
                if (rel.startsWith("data/")) ns = segment(rel, 1);
                else if (rel.startsWith("assets/")) ns = segment(rel, 1);
                if (ns != null && !ns.equals("minecraft") && !ns.isEmpty()) {
                    counts.merge(ns, 1, Integer::sum);
                }
            }
            return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        }

        private static final Set<String> SUBPACKAGE_NOISE = Set.of(
                "api", "advancement", "advancements", "aspect", "aspects", "block", "blocks",
                "client", "common", "compat", "config", "container", "data", "datagen", "effect",
                "enchantment", "entity", "entities", "event", "events", "fluid", "gui", "handler",
                "init", "integration", "inventory", "item", "items", "loot", "mixin", "mixins",
                "model", "models", "network", "packet", "packets", "potion", "proxy", "recipe",
                "recipes", "registry", "render", "renderer", "server", "sound", "spell", "spells",
                "tile", "tileentity", "util", "utils", "world", "worldgen"
        );

        private void inferPackageFromClasses() {
            if (hasPackageFromDescriptor && Ids.PACKAGE.matcher(p.meta.packageName).matches()) return;
            // 1. Prefer the package of a @Mod annotated entry-point class (Forge 1.12-1.21 / NeoForge)
            String modAnnotatedPkg = null;
            for (var en : store.files.entrySet()) {
                String k = en.getKey();
                if (!k.endsWith(".class") || k.contains("$") || isLibraryClassPath(k)) continue;
                byte[] raw = en.getValue();
                if (raw == null || raw.length < 40) continue;
                if (containsAscii(raw, "fml/common/Mod;")) {
                    int slash = k.lastIndexOf('/');
                    if (slash > 0) {
                        String pkg = k.substring(0, slash).replace('/', '.');
                        if (Ids.PACKAGE.matcher(pkg).matches()) {
                            if (pkg.toLowerCase(Locale.ROOT).contains(p.meta.modId) || modAnnotatedPkg == null
                                    || pkg.length() < modAnnotatedPkg.length()) {
                                modAnnotatedPkg = pkg;
                            }
                        }
                    }
                }
            }
            if (modAnnotatedPkg != null) {
                p.meta.packageName = modAnnotatedPkg;
                return;
            }

            // 2. Otherwise pick the cleanest shortest root package across mod classes (skipping api/advancement/client subpackages)
            String bestModIdMatch = null;
            String bestFallback = null;
            for (String k : store.files.keySet()) {
                if (!k.endsWith(".class") && !k.endsWith(".java")) continue;
                if (isLibraryClassPath(k)) continue;
                int slash = k.lastIndexOf('/');
                if (slash <= 0) continue;
                String pkg = k.substring(0, slash).replace('/', '.');
                String[] parts = pkg.split("\\.");
                int keep = parts.length;
                while (keep > 1 && SUBPACKAGE_NOISE.contains(parts[keep - 1].toLowerCase(Locale.ROOT))) {
                    keep--;
                }
                if (keep > 3) keep = 3;
                String cand = String.join(".", Arrays.copyOf(parts, keep));
                if (!Ids.PACKAGE.matcher(cand).matches()) continue;
                if (cand.toLowerCase(Locale.ROOT).contains(p.meta.modId)) {
                    if (bestModIdMatch == null || cand.length() < bestModIdMatch.length()) bestModIdMatch = cand;
                } else if (keep >= 2) {
                    if (bestFallback == null || cand.length() < bestFallback.length()) bestFallback = cand;
                }
            }
            if (bestModIdMatch != null) {
                p.meta.packageName = bestModIdMatch;
            } else if (bestFallback != null) {
                p.meta.packageName = bestFallback;
            } else if (!Ids.PACKAGE.matcher(p.meta.packageName).matches()) {
                p.meta.packageName = "com.example." + p.meta.modId;
            }
        }

        private static boolean isLibraryClassPath(String k) {
            String low = k.toLowerCase(Locale.ROOT);
            return low.startsWith("net/minecraft/") || low.startsWith("com/mojang/") || low.startsWith("meta-inf/")
                    || low.contains("/libraries/") || low.contains("/shaded/") || low.contains("/repackaged/")
                    || low.startsWith("org/objectweb/") || low.startsWith("com/google/") || low.startsWith("org/apache/")
                    || low.startsWith("io/netty/") || low.startsWith("kotlin/") || low.startsWith("kotlinx/");
        }

        private static boolean containsAscii(byte[] data, String needle) {
            byte[] pat = needle.getBytes(StandardCharsets.ISO_8859_1);
            outer:
            for (int i = 0; i <= data.length - pat.length; i++) {
                for (int j = 0; j < pat.length; j++) {
                    if (data[i + j] != pat[j]) continue outer;
                }
                return true;
            }
            return false;
        }

        private void loadIcon(String path) {
            String clean = EntryStore.norm(path);
            byte[] b = store.getCI(clean);
            if (b == null && !clean.contains("/")) b = store.getCI("assets/" + p.meta.modId + "/" + clean);
            if (b == null) return;
            try {
                int[] wh = new int[2];
                int[] px = Images.read(b, wh);
                String name = p.uniqueTextureName("icon");
                TextureAsset ta = TextureAsset.of(name, px, wh[0], wh[1]);
                p.textures.add(ta);
                p.meta.icon = ta.id;
            } catch (Exception ignored) {
            }
        }

        // -------------------------------------------------------------- textures & langs

        private void readTextures() {
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                if (!path.endsWith(".png")) continue;
                String key = null;
                String ns = p.meta.modId;
                String subRel = null;
                if (path.startsWith("assets/")) {
                    String[] parts = path.split("/", 4);
                    if (parts.length == 4 && parts[2].equals("textures")) {
                        ns = parts[1];
                        subRel = parts[3].substring(0, parts[3].length() - 4);
                        key = ns + ":" + subRel;
                    }
                } else {
                    int idx = path.indexOf("textures/");
                    if (idx >= 0) {
                        subRel = path.substring(idx + "textures/".length(), path.length() - 4);
                        key = p.meta.modId + ":" + subRel;
                    }
                }
                if (key == null || subRel.startsWith("font/")) continue;
                try {
                    int[] wh = new int[2];
                    int[] px = Images.read(en.getValue(), wh);
                    String baseName = subRel.contains("/") ? subRel.substring(subRel.lastIndexOf('/') + 1) : subRel;
                    TextureAsset ta = TextureAsset.of(p.uniqueTextureName(baseName), px, wh[0], wh[1]);
                    JsonObject meta = store.json(path + ".mcmeta");
                    if (meta != null && meta.has("animation") && meta.get("animation").isJsonObject()) {
                        JsonObject a = meta.getAsJsonObject("animation");
                        if (a.has("frametime")) ta.frameTime = Math.max(1, a.get("frametime").getAsInt());
                        if (a.has("interpolate")) ta.interpolate = a.get("interpolate").getAsBoolean();
                    }
                    p.textures.add(ta);
                    texByKey.put(key, ta.id);
                    texByKey.putIfAbsent(subRel, ta.id);
                    texByKey.putIfAbsent(ns + ":" + baseName, ta.id);
                    if (subRel.startsWith("blocks/")) texByKey.putIfAbsent(ns + ":block/" + subRel.substring(7), ta.id);
                    else if (subRel.startsWith("items/")) texByKey.putIfAbsent(ns + ":item/" + subRel.substring(6), ta.id);
                } catch (Exception ignored) {
                }
            }
        }

        private String findTex(String ref, String defaultFolder) {
            if (ref == null || ref.isBlank()) return "";
            String clean = ref.replace("#", "").trim();
            if (clean.startsWith("minecraft:")) return "";
            if (texByKey.containsKey(clean)) return texByKey.get(clean);
            if (clean.contains(":blocks/")) {
                String alt = clean.replace(":blocks/", ":block/");
                if (texByKey.containsKey(alt)) return texByKey.get(alt);
            } else if (clean.contains(":items/")) {
                String alt = clean.replace(":items/", ":item/");
                if (texByKey.containsKey(alt)) return texByKey.get(alt);
            } else if (clean.contains(":block/")) {
                String alt = clean.replace(":block/", ":blocks/");
                if (texByKey.containsKey(alt)) return texByKey.get(alt);
            } else if (clean.contains(":item/")) {
                String alt = clean.replace(":item/", ":items/");
                if (texByKey.containsKey(alt)) return texByKey.get(alt);
            }
            if (!clean.contains(":")) {
                String q = p.meta.modId + ":" + defaultFolder + "/" + clean;
                if (texByKey.containsKey(q)) return texByKey.get(q);
                q = p.meta.modId + ":" + defaultFolder + "s/" + clean;
                if (texByKey.containsKey(q)) return texByKey.get(q);
                q = p.meta.modId + ":" + clean;
                if (texByKey.containsKey(q)) return texByKey.get(q);
            }
            return texByKey.getOrDefault(clean, "");
        }

        private String findTexFallback(String ns, String name, String folder) {
            String direct = findTex(ns + ":" + folder + "/" + name, folder);
            if (!direct.isBlank()) return direct;
            String other = "block".equals(folder) ? "item" : "block";
            String cross = findTex(ns + ":" + other + "/" + name, other);
            if (!cross.isBlank()) return cross;
            for (String suf : List.of("_0", "_1", "_normal", "_side", "_top", "_base", "_on", "_off", "_empty", "_default")) {
                String t = findTex(ns + ":" + folder + "/" + name + suf, folder);
                if (!t.isBlank()) return t;
            }
            // Variant suffix fallback (e.g. candle_black -> candle, nitor_red -> nitor, wizard_hat_fire -> wizard_hat)
            int under = name.lastIndexOf('_');
            if (under > 2) {
                String base = name.substring(0, under);
                String t = findTex(ns + ":" + folder + "/" + base, folder);
                if (!t.isBlank()) return t;
                int under2 = base.lastIndexOf('_');
                if (under2 > 2) {
                    String base2 = base.substring(0, under2);
                    t = findTex(ns + ":" + folder + "/" + base2, folder);
                    if (!t.isBlank()) return t;
                }
            }
            return findTex(ns + ":" + name, folder);
        }

        private void readLanguages() {
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                if (path.startsWith("assets/") && path.contains("/lang/") && path.endsWith(".json")) {
                    String file = basename(path);
                    String lang = file.substring(0, file.length() - 5).toLowerCase(Locale.ROOT);
                    JsonObject obj = store.json(path);
                    if (obj == null) continue;
                    Map<String, String> m = langs.computeIfAbsent(lang, k -> new LinkedHashMap<>());
                    for (var kv : obj.entrySet()) {
                        if (kv.getValue().isJsonPrimitive()) m.put(kv.getKey(), kv.getValue().getAsString());
                    }
                    if (!lang.equals("en_us") && !p.meta.languages.contains(lang)) p.meta.languages.add(lang);
                } else if (path.endsWith(".lang") && (path.contains("texts/") || path.contains("/lang/"))) {
                    String file = basename(path);
                    String lang = file.substring(0, file.length() - 5).toLowerCase(Locale.ROOT);
                    String nsHint = path.startsWith("assets/") ? segment(path, 1) : p.meta.modId;
                    String txt = store.text(path);
                    if (txt == null) continue;
                    Map<String, String> m = langs.computeIfAbsent(lang, k -> new LinkedHashMap<>());
                    for (String line : txt.split("\\R")) {
                        String s = line.trim();
                        if (s.isEmpty() || s.startsWith("#")) continue;
                        int eq = s.indexOf('=');
                        if (eq > 0) {
                            String rawKey = s.substring(0, eq).trim();
                            String val = s.substring(eq + 1).replace("\t#", "").trim();
                            m.put(rawKey, val);
                            mapLegacyLangKey(m, nsHint, rawKey, val);
                        }
                    }
                    if (!lang.equals("en_us") && !p.meta.languages.contains(lang)) p.meta.languages.add(lang);
                }
            }
        }

        private static void mapLegacyLangKey(Map<String, String> m, String ns, String key, String val) {
            if (!key.endsWith(".name")) return;
            String core = key.substring(0, key.length() - 5);
            if (core.startsWith("tile.")) {
                String rest = core.substring(5);
                String name = rest.startsWith(ns + ".") ? rest.substring(ns.length() + 1) : rest;
                m.putIfAbsent("block." + ns + "." + name, val);
            } else if (core.startsWith("item.")) {
                String rest = core.substring(5);
                String name = rest.startsWith(ns + ".") ? rest.substring(ns.length() + 1) : rest;
                m.putIfAbsent("item." + ns + "." + name, val);
            } else if (core.startsWith("entity.")) {
                String rest = core.substring(7);
                String name = rest.startsWith(ns + ".") ? rest.substring(ns.length() + 1) : rest;
                m.putIfAbsent("entity." + ns + "." + name, val);
            }
        }

        private String tr(String key) {
            String v = langs.getOrDefault("en_us", Map.of()).get(key);
            if (v != null) consumedLangKeys.add(key);
            return v != null ? v : "";
        }

        private void applyLang(Element e, String field, String key) {
            if (key == null || key.isEmpty()) return;
            consumedLangKeys.add(key);
            for (var le : langs.entrySet()) {
                if (le.getKey().equals("en_us")) continue;
                String v = le.getValue().get(key);
                if (v != null && !v.isBlank()) e.setLoc(field, le.getKey(), v);
            }
        }

        // -------------------------------------------------------------- shaders, sounds, models, blocks, items

        private void readShaders() {
            Map<String, Systems.Shader> map = new LinkedHashMap<>();
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                if (!path.startsWith("assets/minecraft/shaders/")) continue;
                String sub = path.substring("assets/minecraft/shaders/".length());
                if (sub.startsWith("core/") && (sub.endsWith(".fsh") || sub.endsWith(".vsh"))) {
                    String base = sub.substring(5, sub.length() - 4);
                    Systems.Shader s = map.computeIfAbsent("core:" + base, k -> makeShader(base, "core"));
                    if (sub.endsWith(".vsh")) s.vertex = store.text(path);
                    else s.fragment = store.text(path);
                } else if (sub.startsWith("program/") && (sub.endsWith(".fsh") || sub.endsWith(".vsh"))) {
                    String base = sub.substring(8, sub.length() - 4);
                    Systems.Shader s = map.computeIfAbsent("post:" + base, k -> makeShader(base, "post"));
                    if (sub.endsWith(".vsh")) s.vertex = store.text(path);
                    else s.fragment = store.text(path);
                } else if (sub.startsWith("include/") && sub.endsWith(".glsl")) {
                    String base = sub.substring(8, sub.length() - 5);
                    Systems.Shader s = map.computeIfAbsent("include:" + base, k -> makeShader(base, "include"));
                    s.fragment = store.text(path);
                }
            }
            p.elements.addAll(map.values());
        }

        private Systems.Shader makeShader(String prog, String kind) {
            Systems.Shader s = new Systems.Shader();
            s.name = p.uniqueName(ElementType.SHADER, prog);
            s.kind = kind;
            s.program = prog;
            s.mode = "code";
            return s;
        }

        private void readSounds() {
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                if (!path.startsWith("assets/") || !path.endsWith("/sounds.json")) continue;
                String ns = segment(path, 1);
                if ("minecraft".equals(ns)) continue;
                JsonObject obj = store.json(path);
                if (obj == null) continue;
                for (var kv : obj.entrySet()) {
                    if (!kv.getValue().isJsonObject()) continue;
                    JsonObject so = kv.getValue().getAsJsonObject();
                    Elements.Sound snd = new Elements.Sound();
                    snd.name = p.uniqueName(ElementType.SOUND, kv.getKey());
                    if (hasStr(so, "subtitle")) {
                        String sk = so.get("subtitle").getAsString();
                        snd.subtitle = tr(sk);
                        applyLang(snd, "subtitle", sk);
                    }
                    if (so.has("sounds") && so.get("sounds").isJsonArray()) {
                        for (JsonElement je : so.getAsJsonArray("sounds")) {
                            String name = je.isJsonPrimitive() ? je.getAsString()
                                    : je.isJsonObject() && hasStr(je.getAsJsonObject(), "name") ? je.getAsJsonObject().get("name").getAsString() : "";
                            if (je.isJsonObject() && je.getAsJsonObject().has("stream")) snd.stream = je.getAsJsonObject().get("stream").getAsBoolean();
                            String full = name.contains(":") ? name : ns + ":" + name;
                            String soundPath = "assets/" + Ids.ns(full) + "/sounds/" + Ids.path(full) + ".ogg";
                            byte[] ogg = store.get(soundPath);
                            if (ogg != null) {
                                SoundFile sf = new SoundFile();
                                sf.fileName = basename(soundPath);
                                sf.data = Base64.getEncoder().encodeToString(ogg);
                                snd.files.add(sf);
                            }
                        }
                    }
                    p.elements.add(snd);
                }
            }
        }

        private void readBlocksAndItems() {
            Set<String> seenBlocks = new LinkedHashSet<>();
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                if (!path.startsWith("assets/")) continue;
                String[] parts = path.split("/", 4);
                if (parts.length == 4 && parts[2].equals("blockstates") && parts[3].endsWith(".json") && !"minecraft".equals(parts[1])) {
                    String ns = parts[1];
                    String name = parts[3].substring(0, parts[3].length() - 5);
                    if (seenBlocks.add(Ids.toId(name))) {
                        JsonObject state = store.json(path);
                        readSingleBlock(ns, name, state);
                        if (state != null && state.has("multipart")) {
                            preserveRawResource(path);
                        }
                    }
                }
            }
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                if (!path.startsWith("data/")) continue;
                String[] parts = path.split("/", 5);
                if (parts.length == 5 && (parts[2].equals("loot_table") || parts[2].equals("loot_tables"))
                        && parts[3].equals("blocks") && parts[4].endsWith(".json") && !"minecraft".equals(parts[1])) {
                    String ns = parts[1];
                    String name = parts[4].substring(0, parts[4].length() - 5);
                    if (!name.contains("/") && seenBlocks.add(Ids.toId(name))) {
                        readSingleBlock(ns, name, null);
                    }
                }
            }
            // Items (1.21.4+ assets/<ns>/items/*.json and models/item/*.json)
            Set<String> seenItems = new LinkedHashSet<>();
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                if (!path.startsWith("assets/")) continue;
                String[] parts = path.split("/", 4);
                if (parts.length == 4 && parts[2].equals("models") && parts[3].startsWith("item/") && parts[3].endsWith(".json") && !"minecraft".equals(parts[1])) {
                    String ns = parts[1];
                    String name = parts[3].substring(5, parts[3].length() - 5);
                    handleDiscoveredItem(ns, name, store.json(path), seenBlocks, seenItems);
                } else if (parts.length == 4 && parts[2].equals("items") && parts[3].endsWith(".json") && !"minecraft".equals(parts[1])) {
                    String ns = parts[1];
                    String name = parts[3].substring(0, parts[3].length() - 5);
                    JsonObject itemDef = store.json(path);
                    JsonObject im = resolveItemDefModel(ns, name, itemDef);
                    handleDiscoveredItem(ns, name, im, seenBlocks, seenItems);
                }
            }
        }

        private JsonObject resolveItemDefModel(String ns, String name, JsonObject itemDef) {
            if (itemDef != null && itemDef.has("model")) {
                String ref = findFirstModelRef(itemDef.get("model"));
                if (ref != null && !ref.isBlank()) {
                    JsonObject resolved = store.json("assets/" + Ids.ns(ref) + "/models/" + Ids.path(ref) + ".json");
                    if (resolved != null) return resolved;
                }
            }
            return store.json("assets/" + ns + "/models/item/" + name + ".json");
        }

        private static String findFirstModelRef(JsonElement je) {
            if (je == null || !je.isJsonObject()) return null;
            JsonObject o = je.getAsJsonObject();
            if (hasStr(o, "model")) return o.get("model").getAsString();
            for (String k : List.of("on_true", "on_false", "fallback", "base")) {
                if (o.has(k)) {
                    String found = findFirstModelRef(o.get(k));
                    if (found != null) return found;
                }
            }
            if (o.has("cases") && o.get("cases").isJsonArray()) {
                for (JsonElement ce : o.getAsJsonArray("cases")) {
                    if (ce.isJsonObject() && ce.getAsJsonObject().has("model")) {
                        String found = findFirstModelRef(ce.getAsJsonObject().get("model"));
                        if (found != null) return found;
                    }
                }
            }
            if (o.has("models") && o.get("models").isJsonArray()) {
                for (JsonElement me : o.getAsJsonArray("models")) {
                    String found = findFirstModelRef(me);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private void handleDiscoveredItem(String ns, String name, JsonObject im, Set<String> seenBlocks, Set<String> seenItems) {
            if (seenBlocks.contains(name) || !seenItems.add(name)) return;
            if (name.endsWith("_spawn_egg") && name.length() > 10) {
                String mobName = name.substring(0, name.length() - 10);
                if (p.byName(ElementType.ENTITY, Ids.toId(mobName)) == null) {
                    ModElements.CustomEntity ce = new ModElements.CustomEntity();
                    ce.name = p.uniqueName(ElementType.ENTITY, mobName);
                    ce.spawnEgg = true;
                    String elk = "entity." + ns + "." + mobName;
                    String ilk = "item." + ns + "." + name;
                    String dn = tr(elk);
                    if (dn.isEmpty()) dn = tr(ilk).replaceAll("\\s+Spawn\\s+Egg$", "");
                    ce.displayName = dn.isEmpty() ? Ids.titleCase(mobName) : dn;
                    applyLang(ce, "name", elk);
                    ce.texture = findEntityTex(ns, mobName);
                    p.elements.add(ce);
                }
                return;
            }
            readSingleItem(ns, name, im);
        }

        private static boolean isLikelyCropBlock(String name, JsonObject bm) {
            String low = name.toLowerCase(Locale.ROOT);
            for (String nonCrop : List.of("ice", "frost", "obsidian", "crust", "permafrost", "fire", "flame",
                    "magma", "lava", "snow", "water", "portal", "gas", "flux", "taint", "slime", "cloud", "mist",
                    "decay", "rune", "stone", "crystal", "ore", "glass", "bricks", "wood", "log", "plank")) {
                if (low.contains(nonCrop)) return false;
            }
            if (bm != null && hasStr(bm, "parent")) {
                String par = bm.get("parent").getAsString().toLowerCase(Locale.ROOT);
                if (par.contains("cube")) return false;
            }
            return true;
        }

        private JsonObject loadModelByRef(String defaultNs, String ref, String defaultSubdir) {
            if (ref == null || ref.isBlank()) return null;
            String clean = ref.trim();
            String mNs = clean.contains(":") ? Ids.ns(clean) : defaultNs;
            String mPath = clean.contains(":") ? Ids.path(clean) : clean;
            if ("minecraft".equals(mNs)) return null;
            JsonObject o = store.json("assets/" + mNs + "/models/" + mPath + ".json");
            if (o != null) return o;
            if (!mPath.startsWith(defaultSubdir + "/")) {
                o = store.json("assets/" + mNs + "/models/" + defaultSubdir + "/" + mPath + ".json");
                if (o != null) return o;
            }
            return null;
        }

        private void collectModelTexturesAndParent(String defaultNs, JsonObject model, String subdir,
                                                   Map<String, String> outTex, List<String> parents, int depth) {
            if (model == null || depth > 4) return;
            if (hasStr(model, "parent")) {
                String par = model.get("parent").getAsString();
                parents.add(par);
                JsonObject pm = loadModelByRef(defaultNs, par, subdir);
                if (pm != null) collectModelTexturesAndParent(defaultNs, pm, subdir, outTex, parents, depth + 1);
            }
            if (model.has("textures") && model.get("textures").isJsonObject()) {
                for (var e : model.getAsJsonObject("textures").entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        String val = e.getValue().getAsString();
                        if (!val.startsWith("#")) outTex.put(e.getKey(), val);
                    }
                }
            }
        }

        private void extractForgeBlockstateHints(String ns, JsonObject state, Map<String, String> outTex,
                                                 List<String> modelRefs, String preferredVariant) {
            if (state == null) return;
            if (state.has("defaults") && state.get("defaults").isJsonObject()) {
                JsonObject def = state.getAsJsonObject("defaults");
                if (hasStr(def, "model")) modelRefs.add(def.get("model").getAsString());
                if (def.has("textures") && def.get("textures").isJsonObject()) {
                    for (var e : def.getAsJsonObject("textures").entrySet()) {
                        if (e.getValue().isJsonPrimitive() && !e.getValue().getAsString().startsWith("#")) {
                            outTex.putIfAbsent(e.getKey(), e.getValue().getAsString());
                        }
                    }
                }
            }
            if (state.has("variants") && state.get("variants").isJsonObject()) {
                JsonObject vars = state.getAsJsonObject("variants");
                List<String> keys = new ArrayList<>();
                if (preferredVariant != null && vars.has(preferredVariant)) keys.add(preferredVariant);
                for (String pref : List.of("normal", "", "inventory")) {
                    if (vars.has(pref) && !keys.contains(pref)) keys.add(pref);
                }
                for (String k : vars.keySet()) if (!keys.contains(k)) keys.add(k);
                int checked = 0;
                for (String k : keys) {
                    if (checked++ > 8 && !modelRefs.isEmpty() && !outTex.isEmpty()) break;
                    extractVariantEntryHints(vars.get(k), outTex, modelRefs, 0);
                }
            }
        }

        private static void extractVariantEntryHints(JsonElement el, Map<String, String> outTex,
                                                     List<String> modelRefs, int depth) {
            if (el == null || depth > 3) return;
            if (el.isJsonArray()) {
                for (JsonElement child : el.getAsJsonArray()) {
                    extractVariantEntryHints(child, outTex, modelRefs, depth + 1);
                    if (!modelRefs.isEmpty() && !outTex.isEmpty()) break;
                }
            } else if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (hasStr(o, "model")) modelRefs.add(o.get("model").getAsString());
                if (o.has("textures") && o.get("textures").isJsonObject()) {
                    for (var e : o.getAsJsonObject("textures").entrySet()) {
                        if (e.getValue().isJsonPrimitive() && !e.getValue().getAsString().startsWith("#")) {
                            outTex.putIfAbsent(e.getKey(), e.getValue().getAsString());
                        }
                    }
                }
                // Forge blockstate v1 nested sub-variants: "variants": { "type": { "a": { "model": ... } } }
                if (!o.has("model") && !o.has("textures")) {
                    for (var e : o.entrySet()) {
                        extractVariantEntryHints(e.getValue(), outTex, modelRefs, depth + 1);
                        if (!modelRefs.isEmpty() && !outTex.isEmpty()) break;
                    }
                }
            }
        }

        private void readSingleBlock(String ns, String name, JsonObject state) {
            Elements.Block b = new Elements.Block();
            b.name = p.uniqueName(ElementType.BLOCK, name);
            String lk = "block." + ns + "." + name;
            String dn = tr(lk);
            b.displayName = dn.isEmpty() ? Ids.titleCase(name) : dn;
            applyLang(b, "name", lk);

            JsonObject bm = store.json("assets/" + ns + "/models/block/" + name + ".json");
            Map<String, String> resolvedTex = new LinkedHashMap<>();
            List<String> parentChain = new ArrayList<>();
            List<String> stateModelRefs = new ArrayList<>();
            extractForgeBlockstateHints(ns, state, resolvedTex, stateModelRefs, "normal");
            if (bm == null) {
                for (String mref : stateModelRefs) {
                    JsonObject cand = loadModelByRef(ns, mref, "block");
                    if (cand != null) { bm = cand; break; }
                }
            }
            if (bm != null) {
                collectModelTexturesAndParent(ns, bm, "block", resolvedTex, parentChain, 0);
            }
            for (String mref : stateModelRefs) {
                if (!parentChain.contains(mref)) parentChain.add(mref);
            }

            if (state != null && state.has("variants") && state.get("variants").isJsonObject()) {
                String vKeys = state.getAsJsonObject("variants").keySet().toString();
                if (vKeys.contains("type=top") || vKeys.contains("type=bottom")) b.shape = "slab";
                else if (vKeys.contains("half=") && vKeys.contains("facing=")) b.shape = "stairs";
                else if (vKeys.contains("half=") && !vKeys.contains("facing=")) b.shape = "cross";
                else if (vKeys.contains("open=")) b.shape = name.contains("trap") ? "trapdoor" : "door";
                else if (vKeys.contains("age=") && isLikelyCropBlock(name, bm)) b.shape = "crop";
                else if (vKeys.contains("axis=")) b.shape = "pillar";
                else if (vKeys.contains("powered=") && vKeys.contains("face=")) b.shape = "button";
                else if (vKeys.contains("powered=")) b.shape = "pressure_plate";
            }
            for (String par : parentChain) {
                if (par.contains("slab")) { b.shape = "slab"; break; }
                if (par.contains("stairs")) { b.shape = "stairs"; break; }
                if (par.contains("fence")) { b.shape = "fence"; break; }
                if (par.contains("wall")) { b.shape = "wall"; break; }
                if (par.contains("trapdoor")) { b.shape = "trapdoor"; break; }
                if (par.contains("door")) { b.shape = "door"; break; }
                if (par.contains("button")) { b.shape = "button"; break; }
                if (par.contains("pressure_plate")) { b.shape = "pressure_plate"; break; }
                if (par.contains("carpet")) { b.shape = "carpet"; break; }
                if (par.contains("cross")) { b.shape = "cross"; break; }
                if (par.contains("cube_bottom_top")) { b.shape = "cube_bottom_top"; break; }
                if (par.contains("cube_column")) { b.shape = "pillar"; break; }
            }
            if (bm != null && bm.has("elements") && "cube".equals(b.shape)) {
                b.shape = "model";
                b.model = importModelJson("block/" + name, bm);
            }
            if (!resolvedTex.isEmpty()) {
                if (resolvedTex.containsKey("all")) b.texture = findTex(resolvedTex.get("all"), "block");
                if (resolvedTex.containsKey("cross")) b.texture = findTex(resolvedTex.get("cross"), "block");
                if (resolvedTex.containsKey("top")) b.textureTop = findTex(resolvedTex.get("top"), "block");
                if (resolvedTex.containsKey("end")) b.textureTop = findTex(resolvedTex.get("end"), "block");
                if (resolvedTex.containsKey("bottom")) b.textureBottom = findTex(resolvedTex.get("bottom"), "block");
                if (resolvedTex.containsKey("side")) b.textureSide = findTex(resolvedTex.get("side"), "block");
                if (b.texture.isBlank()) {
                    for (String v : resolvedTex.values()) {
                        b.texture = findTex(v, "block");
                        if (!b.texture.isBlank()) break;
                    }
                }
            }
            if (b.texture.isBlank() && !b.textureSide.isBlank()) b.texture = b.textureSide;
            if (b.texture.isBlank() && !b.textureTop.isBlank()) b.texture = b.textureTop;
            if (b.texture.isBlank() && !b.textureBottom.isBlank()) b.texture = b.textureBottom;
            if (b.texture.isBlank()) b.texture = findTexFallback(ns, name, "block");
            p.elements.add(b);
            blockByName.put(name, b);
        }

        private void readSingleItem(String ns, String name, JsonObject im) {
            Elements.Item it = new Elements.Item();
            it.name = p.uniqueName(ElementType.ITEM, name);
            String lk = "item." + ns + "." + name;
            String dn = tr(lk);
            it.displayName = dn.isEmpty() ? Ids.titleCase(name) : dn;
            applyLang(it, "name", lk);
            Map<String, String> resolvedTex = new LinkedHashMap<>();
            List<String> parentChain = new ArrayList<>();
            if (im != null) {
                collectModelTexturesAndParent(ns, im, "item", resolvedTex, parentChain, 0);
                for (String parent : parentChain) {
                    if (parent.contains("crossbow")) { it.kind = "crossbow"; break; }
                    if (parent.contains("bow")) { it.kind = name.contains("crossbow") ? "crossbow" : "bow"; break; }
                    if (parent.contains("shield")) { it.kind = "shield"; break; }
                    if (parent.contains("trident")) { it.kind = "trident"; break; }
                    if (parent.contains("handheld")) {
                        if (name.endsWith("_pickaxe") || name.endsWith("_pick")) it.kind = "pickaxe";
                        else if (name.endsWith("_axe")) it.kind = "axe";
                        else if (name.endsWith("_shovel") || name.endsWith("_spade")) it.kind = "shovel";
                        else if (name.endsWith("_hoe")) it.kind = "hoe";
                        else if (name.endsWith("_sword") || name.endsWith("_blade")) it.kind = "sword";
                        break;
                    }
                }
                if (im.has("overrides") && im.get("overrides").isJsonArray()) {
                    for (JsonElement ov : im.getAsJsonArray("overrides")) {
                        if (!ov.isJsonObject() || !ov.getAsJsonObject().has("predicate")) continue;
                        JsonObject pr = ov.getAsJsonObject().getAsJsonObject("predicate");
                        if (pr.has("pulling") || pr.has("pull")) it.kind = name.contains("crossbow") ? "crossbow" : "bow";
                        else if (pr.has("charged") || pr.has("firework")) it.kind = "crossbow";
                        else if (pr.has("blocking")) it.kind = "shield";
                    }
                }
                if (im.has("elements")) it.model = importModelJson("item/" + name, im);
            }
            // Also check Forge 1.12 blockstates/<name>.json inventory variant textures for items
            if (resolvedTex.isEmpty()) {
                JsonObject bs = store.json("assets/" + ns + "/blockstates/" + name + ".json");
                if (bs != null) {
                    List<String> refs = new ArrayList<>();
                    extractForgeBlockstateHints(ns, bs, resolvedTex, refs, "inventory");
                }
            }
            if (resolvedTex.containsKey("layer0")) it.texture = findTex(resolvedTex.get("layer0"), "item");
            if (it.texture.isBlank()) {
                for (String v : resolvedTex.values()) {
                    it.texture = findTex(v, "item");
                    if (!it.texture.isBlank()) break;
                }
            }
            if (it.texture.isBlank()) it.texture = findTexFallback(ns, name, "item");
            p.elements.add(it);
            itemByName.put(name, it);
        }

        private void readModels() {
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                if (path.endsWith(".geo.json")) {
                    try {
                        Model m = Models.read(store.text(path), basename(path)).model();
                        m.name = p.uniqueName(ElementType.MODEL, stripExt(stripExt(basename(path))));
                        p.elements.add(m);
                    } catch (Exception ignored) {
                    }
                } else if (path.startsWith("assets/") && path.contains("/models/") && path.endsWith(".json")) {
                    String ns = segment(path, 1);
                    if ("minecraft".equals(ns)) continue;
                    String rel = path.substring(("assets/" + ns + "/models/").length(), path.length() - 5);
                    if (createdModels.contains(rel)) continue;
                    JsonObject o = store.json(path);
                    if (o != null && o.has("elements")) importModelJson(rel, o);
                }
            }
        }

        private String importModelJson(String relKey, JsonObject json) {
            createdModels.add(relKey);
            try {
                Model m = Models.read(json.toString(), basename(relKey) + ".json").model();
                m.name = p.uniqueName(ElementType.MODEL, Ids.toId(relKey.replace('/', '_')));
                for (var e : m.textures.entrySet()) {
                    String t = findTex(e.getValue(), relKey.startsWith("item/") ? "item" : "block");
                    if (!t.isBlank()) e.setValue(t);
                }
                p.elements.add(m);
                return m.name;
            } catch (Exception ignored) {
                return "";
            }
        }

        // -------------------------------------------------------------- data & registry JSONs

        private void readTagFunctionHooks() {
            for (String fnTag : List.of("data/minecraft/tags/functions/load.json", "data/minecraft/tags/function/load.json")) {
                collectTagStrings(store.json(fnTag), loadFns);
            }
            for (String fnTag : List.of("data/minecraft/tags/functions/tick.json", "data/minecraft/tags/function/tick.json")) {
                collectTagStrings(store.json(fnTag), tickFns);
            }
        }

        private static void collectTagStrings(JsonObject o, Set<String> out) {
            if (o == null || !o.has("values") || !o.get("values").isJsonArray()) return;
            for (JsonElement je : o.getAsJsonArray("values")) {
                if (je.isJsonPrimitive()) out.add(je.getAsString());
                else if (je.isJsonObject() && hasStr(je.getAsJsonObject(), "id")) out.add(je.getAsJsonObject().get("id").getAsString());
            }
        }

        private void readData() {
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                if (path.startsWith("datapack/")) path = path.substring("datapack/".length());
                if (!path.startsWith("data/") && (path.startsWith("functions/") || path.contains("/functions/")) && path.endsWith(".mcfunction")) {
                    int idx = path.startsWith("functions/") ? "functions/".length() : path.indexOf("/functions/") + "/functions/".length();
                    String name = path.substring(idx, path.length() - 11);
                    Elements.Function f = new Elements.Function();
                    f.name = p.uniqueName(ElementType.FUNCTION, name);
                    f.code = store.text(en.getKey());
                    p.elements.add(f);
                    continue;
                }
                String cleanPath = path.startsWith("datapack/") ? path.substring("datapack/".length()) : path;
                boolean isLegacyAssetsData = false;
                if (cleanPath.startsWith("assets/")) {
                    String[] ap = cleanPath.split("/", 4);
                    if (ap.length == 4 && (ap[2].equals("recipes") || ap[2].equals("advancements") || ap[2].equals("loot_tables") || ap[2].equals("structures"))) {
                        isLegacyAssetsData = true;
                    }
                }
                if (!cleanPath.startsWith("data/") && !isLegacyAssetsData) continue;
                String[] parts = cleanPath.split("/", 4);
                if (parts.length < 4) continue;
                String ns = parts[1], folder = parts[2], rest = parts[3];

                if ((folder.equals("function") || folder.equals("functions")) && rest.endsWith(".mcfunction")) {
                    String fnName = rest.substring(0, rest.length() - 11);
                    Elements.Function f = new Elements.Function();
                    f.name = p.uniqueName(ElementType.FUNCTION, fnName);
                    f.code = store.text(en.getKey());
                    String fid = ns + ":" + fnName;
                    f.onLoad = loadFns.contains(fid);
                    f.onTick = tickFns.contains(fid);
                    p.elements.add(f);
                    continue;
                }
                if ((folder.equals("structure") || folder.equals("structures")) && rest.endsWith(".nbt")) {
                    readNbtStructure(rest.substring(0, rest.length() - 4), en.getValue());
                    continue;
                }
                if (!rest.endsWith(".json")) continue;
                if (folder.equals("predicate") || folder.equals("predicates")
                        || folder.equals("item_modifier") || folder.equals("item_modifiers")) {
                    JsonElement je = store.jsonElement(en.getKey());
                    if (je == null) continue;
                    String itemPath = rest.substring(0, rest.length() - 5);
                    if (folder.startsWith("predicate")) readPredicateElement(itemPath, je);
                    else readModifierElement(itemPath, je);
                    continue;
                }
                JsonObject obj = store.json(en.getKey());
                if (obj == null) continue;
                String itemPath = rest.substring(0, rest.length() - 5);

                switch (folder) {
                    case "recipe", "recipes" -> readRecipe(ns, itemPath, en.getKey(), obj);
                    case "loot_table", "loot_tables" -> readLootTable(ns, itemPath, obj);
                    case "advancement", "advancements" -> readAdvancement(ns, itemPath, obj);
                    case "tags" -> readTag(ns, itemPath, obj);
                    case "predicate", "predicates" -> readPredicateElement(itemPath, obj);
                    case "item_modifier", "item_modifiers" -> readModifierElement(itemPath, obj);
                    case "enchantment" -> readEnchantment(ns, itemPath, obj);
                    case "damage_type" -> readDamageType(ns, itemPath, obj);
                    case "painting_variant" -> readPainting(ns, itemPath, obj);
                    case "jukebox_song" -> readSong(ns, itemPath, obj);
                    case "trim_pattern" -> readTrimPattern(ns, itemPath, obj);
                    case "banner_pattern" -> readBannerPattern(ns, itemPath, obj);
                    case "dialog" -> readDialog(ns, itemPath, obj);
                    case "worldgen" -> readWorldgen(ns, itemPath, en.getKey(), obj);
                    case "neoforge", "forge" -> {
                        if (itemPath.startsWith("biome_modifier/")) biomeModifierJsons.add(obj);
                        else preserveRawResource(en.getKey());
                    }
                    default -> preserveRawResource(en.getKey());
                }
            }
            resolveAdvancementParents();
            reconcileImportedSelfRefs();
        }

        private void readRecipe(String ns, String name, String rawPath, JsonObject o) {
            String rawType = hasStr(o, "type") ? o.get("type").getAsString() : "minecraft:crafting_shaped";
            String type = Ids.path(rawType).toLowerCase(Locale.ROOT);
            if ("conditional".equals(type) && o.has("recipes") && o.get("recipes").isJsonArray()) {
                JsonArray arr = o.getAsJsonArray("recipes");
                boolean extracted = false;
                if (arr.size() == 1 && arr.get(0).isJsonObject()) {
                    JsonObject entry = arr.get(0).getAsJsonObject();
                    boolean noConds = !entry.has("conditions") || !entry.get("conditions").isJsonArray()
                            || entry.getAsJsonArray("conditions").isEmpty();
                    if (noConds && entry.has("recipe") && entry.get("recipe").isJsonObject()) {
                        readRecipe(ns, name, rawPath, entry.getAsJsonObject("recipe"));
                        extracted = true;
                    }
                }
                if (!extracted) preserveRawResource(rawPath);
                return;
            }
            if (type.startsWith("crafting_special_") || type.equals("smithing_trim") || type.equals("crafting_decorated_pot")) {
                preserveRawResource(rawPath);
                return;
            }
            String mappedType = switch (type) {
                case "crafting_shapeless", "ore_shapeless", "shapeless" -> "shapeless";
                case "smelting" -> "smelting";
                case "blasting" -> "blasting";
                case "smoking" -> "smoking";
                case "campfire_cooking", "campfire" -> "campfire";
                case "stonecutting" -> "stonecutting";
                case "smithing_transform", "smithing" -> "smithing";
                case "crafting_shaped", "ore_shaped", "shaped" -> "shaped";
                default -> "";
            };
            if (mappedType.isEmpty()) {
                preserveRawResource(rawPath);
                return;
            }

            Elements.Recipe r = new Elements.Recipe();
            r.name = p.uniqueName(ElementType.RECIPE, basename(name));
            r.recipeType = mappedType;
            if (hasStr(o, "group")) r.group = o.get("group").getAsString();
            if (o.has("experience") && o.get("experience").isJsonPrimitive()) r.experience = o.get("experience").getAsDouble();
            if (o.has("cookingtime") && o.get("cookingtime").isJsonPrimitive()) r.cookTime = o.get("cookingtime").getAsInt();
            extractRecipeResult(o, r);

            if (r.recipeType.equals("shaped") && o.has("pattern") && o.get("pattern").isJsonArray() && o.has("key") && o.get("key").isJsonObject()) {
                JsonArray pat = o.getAsJsonArray("pattern");
                JsonObject key = o.getAsJsonObject("key");
                for (int row = 0; row < Math.min(3, pat.size()); row++) {
                    if (!pat.get(row).isJsonPrimitive()) continue;
                    String line = pat.get(row).getAsString();
                    for (int col = 0; col < Math.min(3, line.length()); col++) {
                        char ch = line.charAt(col);
                        if (ch != ' ' && key.has(String.valueOf(ch))) r.grid.set(row * 3 + col, ingId(key.get(String.valueOf(ch))));
                    }
                }
            } else if (r.recipeType.equals("shapeless") && o.has("ingredients") && o.get("ingredients").isJsonArray()) {
                JsonArray ings = o.getAsJsonArray("ingredients");
                for (int i = 0; i < Math.min(9, ings.size()); i++) r.grid.set(i, ingId(ings.get(i)));
            } else if (r.recipeType.equals("smithing")) {
                r.template = ingId(o.get("template"));
                r.base = ingId(o.get("base"));
                r.addition = ingId(o.get("addition"));
                if (r.template.isBlank() && type.equals("smithing")
                        && (r.addition.contains("netherite") || r.result.contains("netherite"))) {
                    r.template = "minecraft:netherite_upgrade_smithing_template";
                }
            } else if (o.has("ingredient")) {
                r.input = ingId(o.get("ingredient"));
            }

            boolean missingInput = switch (r.recipeType) {
                case "shaped", "shapeless" -> r.grid.stream().allMatch(String::isBlank);
                case "smithing" -> r.template.isBlank() || r.base.isBlank() || r.addition.isBlank();
                default -> r.input.isBlank();
            };
            if (r.result.isBlank() || missingInput) {
                preserveRawResource(rawPath);
                return;
            }
            p.elements.add(r);
        }

        private static void extractRecipeResult(JsonObject o, Elements.Recipe r) {
            for (String key : List.of("result", "output", "results", "outputs")) {
                if (!o.has(key)) continue;
                JsonElement el = o.get(key);
                if (el.isJsonArray() && !el.getAsJsonArray().isEmpty()) {
                    el = el.getAsJsonArray().get(0);
                }
                if (el.isJsonPrimitive()) {
                    r.result = el.getAsString();
                } else if (el.isJsonObject()) {
                    JsonObject ro = el.getAsJsonObject();
                    if (ro.has("stack") && ro.get("stack").isJsonObject()) ro = ro.getAsJsonObject("stack");
                    r.result = firstStr(ro, "id", "item", "Item", "Name");
                    if (ro.has("count") && ro.get("count").isJsonPrimitive()) r.count = Math.max(1, ro.get("count").getAsInt());
                    else if (ro.has("Count") && ro.get("Count").isJsonPrimitive()) r.count = Math.max(1, ro.get("Count").getAsInt());
                    if (ro.has("components") && ro.get("components").isJsonObject()) r.resultComponents = ro.get("components").toString();
                }
                if (!r.result.isBlank()) break;
            }
            if (!r.result.isBlank() && o.has("count") && o.get("count").isJsonPrimitive() && r.count <= 1) {
                r.count = Math.max(1, o.get("count").getAsInt());
            }
        }

        private static String firstStr(JsonObject o, String... keys) {
            for (String k : keys) {
                if (hasStr(o, k)) return o.get(k).getAsString();
            }
            return "";
        }

        private static String ingId(JsonElement je) {
            if (je == null || je.isJsonNull()) return "";
            if (je.isJsonPrimitive()) return je.getAsString();
            if (je.isJsonArray() && !je.getAsJsonArray().isEmpty()) return ingId(je.getAsJsonArray().get(0));
            if (je.isJsonObject()) {
                JsonObject o = je.getAsJsonObject();
                if (hasStr(o, "item")) return o.get("item").getAsString();
                if (hasStr(o, "id")) return o.get("id").getAsString();
                if (hasStr(o, "tag")) return "#" + o.get("tag").getAsString();
                if (hasStr(o, "ore")) return mapOreDict(o.get("ore").getAsString());
                if (o.has("items") && o.get("items").isJsonArray() && !o.getAsJsonArray("items").isEmpty()) {
                    return ingId(o.getAsJsonArray("items").get(0));
                }
            }
            return "";
        }

        private static String mapOreDict(String ore) {
            return switch (ore) {
                case "ingotIron" -> "#c:ingots/iron";
                case "ingotGold" -> "#c:ingots/gold";
                case "gemDiamond" -> "#c:gems/diamond";
                case "gemEmerald" -> "#c:gems/emerald";
                case "plankWood" -> "#minecraft:planks";
                case "logWood" -> "#minecraft:logs";
                case "cobblestone" -> "#minecraft:stone_tool_materials";
                case "stickWood" -> "#c:rods/wooden";
                case "dustRedstone" -> "#c:dusts/redstone";
                case "chestWood" -> "#c:chests/wooden";
                case "paneGlass" -> "#c:glass_panes";
                case "dye" -> "#c:dyes";
                default -> "#c:" + ore.toLowerCase(Locale.ROOT);
            };
        }

        private void readLootTable(String ns, String name, JsonObject o) {
            if (name != null && name.startsWith("blocks/") && !"minecraft".equals(ns)) {
                blockLootJsons.put(name.substring("blocks/".length()), o);
            }
            LootTable lt = new LootTable();
            lt.name = p.uniqueName(ElementType.LOOT_TABLE, name);
            if ("minecraft".equals(ns)) lt.replaces = "minecraft:" + name;
            String t = hasStr(o, "type") ? Ids.path(o.get("type").getAsString()) : "";
            lt.lootType = List.of("block", "entity", "chest", "gift", "fishing").contains(t) ? t : "generic";
            if (o.has("pools") && o.get("pools").isJsonArray()) {
                for (JsonElement pe : o.getAsJsonArray("pools")) {
                    if (!pe.isJsonObject()) continue;
                    JsonObject po = pe.getAsJsonObject();
                    LootPool lp = new LootPool();
                    if (po.has("rolls")) {
                        JsonElement r = po.get("rolls");
                        if (r.isJsonPrimitive()) { lp.rollsMin = lp.rollsMax = r.getAsInt(); }
                        else if (r.isJsonObject()) {
                            if (r.getAsJsonObject().has("min")) lp.rollsMin = r.getAsJsonObject().get("min").getAsInt();
                            if (r.getAsJsonObject().has("max")) lp.rollsMax = r.getAsJsonObject().get("max").getAsInt();
                        }
                    }
                    if (po.has("entries") && po.get("entries").isJsonArray()) {
                        for (JsonElement ee : po.getAsJsonArray("entries")) {
                            if (!ee.isJsonObject()) continue;
                            JsonObject eo = ee.getAsJsonObject();
                            LootEntry le = new LootEntry();
                            String ek = hasStr(eo, "type") ? Ids.path(eo.get("type").getAsString()) : "item";
                            le.kind = List.of("item", "tag", "loot_table", "empty").contains(ek) ? ek : "item";
                            if (hasStr(eo, "name")) le.name = eo.get("name").getAsString();
                            if (eo.has("weight")) le.weight = eo.get("weight").getAsInt();
                            if (eo.has("functions") && eo.get("functions").isJsonArray()) {
                                for (JsonElement fe : eo.getAsJsonArray("functions")) {
                                    if (!fe.isJsonObject()) continue;
                                    JsonObject fo = fe.getAsJsonObject();
                                    String fn = hasStr(fo, "function") ? Ids.path(fo.get("function").getAsString()) : "";
                                    if (fn.equals("explosion_decay")) le.explosionDecay = true;
                                    else if (fn.equals("looting_enchant") || fn.equals("enchanted_count_increase")) le.lootingBonus = 1;
                                    else if (fn.equals("set_count") && fo.has("count")) {
                                        JsonElement cnt = fo.get("count");
                                        if (cnt.isJsonPrimitive()) le.countMin = le.countMax = cnt.getAsInt();
                                        else if (cnt.isJsonObject()) {
                                            if (cnt.getAsJsonObject().has("min")) le.countMin = cnt.getAsJsonObject().get("min").getAsInt();
                                            if (cnt.getAsJsonObject().has("max")) le.countMax = cnt.getAsJsonObject().get("max").getAsInt();
                                        }
                                    }
                                }
                            }
                            lp.entries.add(le);
                        }
                    }
                    lt.pools.add(lp);
                }
            }
            p.elements.add(lt);
        }

        private void readAdvancement(String ns, String name, JsonObject o) {
            Elements.Advancement a = new Elements.Advancement();
            a.name = p.uniqueName(ElementType.ADVANCEMENT, name);
            if (hasStr(o, "parent")) a.parent = o.get("parent").getAsString().trim();
            if (o.has("display") && o.get("display").isJsonObject()) {
                JsonObject d = o.getAsJsonObject("display");
                a.title = textField(d.get("title"), a, "title", basename(name));
                a.description = textField(d.get("description"), a, "description", "");
                if (d.has("icon") && d.get("icon").isJsonObject()) {
                    JsonObject ic = d.getAsJsonObject("icon");
                    if (hasStr(ic, "id")) a.icon = ic.get("id").getAsString();
                    else if (hasStr(ic, "item")) a.icon = ic.get("item").getAsString();
                } else if (hasStr(d, "icon")) {
                    a.icon = d.get("icon").getAsString();
                }
                if (hasStr(d, "frame")) a.frame = d.get("frame").getAsString();
                if (hasStr(d, "background")) a.background = d.get("background").getAsString();
                if (d.has("show_toast")) a.showToast = d.get("show_toast").getAsBoolean();
                if (d.has("announce_to_chat")) a.announce = d.get("announce_to_chat").getAsBoolean();
                if (d.has("hidden")) a.hidden = d.get("hidden").getAsBoolean();
            }
            if (o.has("criteria") && o.get("criteria").isJsonObject()) {
                for (var kv : o.getAsJsonObject("criteria").entrySet()) {
                    if (!kv.getValue().isJsonObject()) continue;
                    JsonObject co = kv.getValue().getAsJsonObject();
                    if (hasStr(co, "trigger")) {
                        String trig = Ids.path(co.get("trigger").getAsString());
                        a.trigger = "sleeping_in_bed".equals(trig) ? "slept_in_bed" : trig;
                    }
                    if (co.has("conditions") && co.get("conditions").isJsonObject()) {
                        a.triggerValue = extractAdvTriggerValue(a.trigger, co.getAsJsonObject("conditions"));
                    }
                    break;
                }
            }
            if (o.has("rewards") && o.get("rewards").isJsonObject()) {
                JsonObject rw = o.getAsJsonObject("rewards");
                if (rw.has("experience")) a.rewardXp = rw.get("experience").getAsInt();
                if (hasStr(rw, "function")) a.rewardFunction = rw.get("function").getAsString();
                if (rw.has("loot") && rw.get("loot").isJsonArray() && !rw.getAsJsonArray("loot").isEmpty()) {
                    a.rewardLoot = rw.getAsJsonArray("loot").get(0).getAsString();
                }
            }
            if (ns != null) {
                p.elements.add(a);
                importedAdvs.add(a);
                String targetId = p.meta.modId + ":" + a.name;
                advIdMap.putIfAbsent(ns + ":" + name, targetId);
                advIdMap.putIfAbsent(p.meta.modId + ":" + name, targetId);
                advIdMap.putIfAbsent(ns + ":" + Ids.toPath(name), targetId);
            }
        }

        private static String extractAdvTriggerValue(String trig, JsonObject cond) {
            switch (trig) {
                case "inventory_changed" -> {
                    if (cond.has("items") && cond.get("items").isJsonArray() && !cond.getAsJsonArray("items").isEmpty()) {
                        JsonElement first = cond.getAsJsonArray("items").get(0);
                        if (first.isJsonObject()) return extractPredicateItem(first.getAsJsonObject());
                    }
                }
                case "consume_item", "enchanted_item", "filled_bucket" -> {
                    if (cond.has("item") && cond.get("item").isJsonObject()) {
                        return extractPredicateItem(cond.getAsJsonObject("item"));
                    }
                }
                case "placed_block", "item_used_on_block" -> {
                    if (cond.has("location") && cond.get("location").isJsonArray() && !cond.getAsJsonArray("location").isEmpty()) {
                        JsonElement first = cond.getAsJsonArray("location").get(0);
                        if (first.isJsonObject()) {
                            JsonObject lo = first.getAsJsonObject();
                            if (hasStr(lo, "block")) return lo.get("block").getAsString();
                            if (lo.has("predicate") && lo.get("predicate").isJsonObject()) {
                                return extractPredicateItem(lo.getAsJsonObject("predicate"));
                            }
                        }
                    } else if (hasStr(cond, "block")) {
                        return cond.get("block").getAsString();
                    }
                }
                case "enter_block" -> {
                    if (hasStr(cond, "block")) return cond.get("block").getAsString();
                }
                case "changed_dimension" -> {
                    if (hasStr(cond, "to")) return cond.get("to").getAsString();
                }
                case "recipe_unlocked" -> {
                    if (hasStr(cond, "recipe")) return cond.get("recipe").getAsString();
                }
                case "player_killed_entity", "player_hurt_entity" -> {
                    if (cond.has("entity") && cond.get("entity").isJsonArray() && !cond.getAsJsonArray("entity").isEmpty()) {
                        JsonElement first = cond.getAsJsonArray("entity").get(0);
                        if (first.isJsonObject() && first.getAsJsonObject().has("predicate")
                                && first.getAsJsonObject().get("predicate").isJsonObject()) {
                            JsonObject po = first.getAsJsonObject().getAsJsonObject("predicate");
                            if (hasStr(po, "type")) return po.get("type").getAsString();
                        }
                    }
                }
                default -> {
                }
            }
            return "";
        }

        private static String extractPredicateItem(JsonObject io) {
            if (hasStr(io, "items")) return io.get("items").getAsString();
            if (hasStr(io, "item")) return io.get("item").getAsString();
            if (io.has("items") && io.get("items").isJsonArray() && !io.getAsJsonArray("items").isEmpty()) {
                JsonElement e = io.getAsJsonArray("items").get(0);
                if (e.isJsonPrimitive()) return e.getAsString();
            }
            if (hasStr(io, "tag")) return "#" + io.get("tag").getAsString();
            return "";
        }

        private void resolveAdvancementParents() {
            String nsPrefix = p.meta.modId + ":";
            for (Elements.Advancement a : importedAdvs) {
                if (a.parent.isBlank()) continue;
                String raw = a.parent.trim();
                String mapped = advIdMap.get(raw);
                if (mapped != null) {
                    a.parent = mapped;
                    continue;
                }
                if (!raw.contains(":")) {
                    String local = advIdMap.get(nsPrefix + raw);
                    if (local != null) {
                        a.parent = local;
                        continue;
                    }
                    if (p.byName(ElementType.ADVANCEMENT, Ids.toPath(raw)) != null) {
                        a.parent = nsPrefix + Ids.toPath(raw);
                    } else {
                        a.parent = "minecraft:" + raw;
                    }
                    continue;
                }
                if (raw.startsWith(nsPrefix)) {
                    String path = raw.substring(nsPrefix.length());
                    String cleanPath = Ids.toPath(path);
                    if (p.byName(ElementType.ADVANCEMENT, cleanPath) != null) {
                        a.parent = nsPrefix + cleanPath;
                    } else if (path.startsWith("recipes/")) {
                        a.parent = "minecraft:recipes/root";
                    } else {
                        a.parent = "";
                        notes.add("Advancement '" + a.name + "' referenced missing parent '" + raw + "'; converted to root advancement.");
                    }
                }
            }
        }

        private void reconcileImportedSelfRefs() {
            String nsPrefix = p.meta.modId + ":";
            Map<String, String> byNorm = new LinkedHashMap<>();
            Map<String, String> byCompact = new LinkedHashMap<>();
            for (Element el : p.elements) {
                if (el instanceof Elements.Item || el instanceof Elements.Block) {
                    byNorm.putIfAbsent(el.name, el.name);
                    byCompact.putIfAbsent(el.name.replace("_", "").toLowerCase(Locale.ROOT), el.name);
                } else if (el instanceof Elements.Fluid fl && fl.generateBucket) {
                    String b = fl.name + "_bucket";
                    byNorm.putIfAbsent(b, b);
                } else if (el instanceof ModElements.CustomEntity ce && ce.spawnEgg && ce.isLivingMob()) {
                    String s = ce.name + "_spawn_egg";
                    byNorm.putIfAbsent(s, s);
                }
            }

            java.util.function.Function<String, String> fixRef = raw -> {
                if (raw == null || raw.isBlank() || raw.startsWith("#")) return raw;
                String s = raw.trim();
                if (!s.startsWith(nsPrefix)) return raw;
                String path = s.substring(nsPrefix.length());
                if (path.isEmpty()) return raw;
                if (p.itemLike(s) != null || byNorm.containsKey(path)) {
                    return byNorm.containsKey(path) ? nsPrefix + byNorm.get(path) : s;
                }
                String clean = Ids.toId(path);
                if (byNorm.containsKey(clean)) return nsPrefix + byNorm.get(clean);
                String compact = path.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
                if (byCompact.containsKey(compact)) return nsPrefix + byCompact.get(compact);
                if (store.classFileCount > 0) {
                    Elements.Item synth = new Elements.Item();
                    synth.name = p.uniqueName(ElementType.ITEM, path);
                    String lk = "item." + p.meta.modId + "." + path;
                    String dn = tr(lk);
                    if (dn.isEmpty()) dn = tr("item." + p.meta.modId + "." + clean);
                    if (dn.isEmpty()) dn = tr("block." + p.meta.modId + "." + clean);
                    synth.displayName = dn.isEmpty() ? Ids.titleCase(clean.isEmpty() ? path : clean) : dn;
                    applyLang(synth, "name", lk);
                    String tex = findTex(nsPrefix + clean, "item");
                    if (tex.isBlank()) tex = findTex(nsPrefix + clean, "block");
                    synth.texture = tex;
                    p.elements.add(synth);
                    byNorm.put(synth.name, synth.name);
                    byCompact.put(compact, synth.name);
                    return nsPrefix + synth.name;
                }
                return raw;
            };

            for (Element el : new ArrayList<>(p.elements)) {
                switch (el) {
                    case Elements.Recipe rc -> {
                        for (int i = 0; i < rc.grid.size(); i++) rc.grid.set(i, fixRef.apply(rc.grid.get(i)));
                        rc.input = fixRef.apply(rc.input);
                        rc.result = fixRef.apply(rc.result);
                        rc.template = fixRef.apply(rc.template);
                        rc.base = fixRef.apply(rc.base);
                        rc.addition = fixRef.apply(rc.addition);
                    }
                    case Elements.LootTable lt -> {
                        for (LootPool pool : lt.pools) {
                            for (LootEntry en : pool.entries) {
                                if (en.kind.equals("item")) en.name = fixRef.apply(en.name);
                            }
                        }
                    }
                    case Elements.Advancement a -> {
                        a.icon = fixRef.apply(a.icon);
                        if (List.of("inventory_changed", "consume_item", "enchanted_item", "filled_bucket",
                                "item_used_on_block", "placed_block", "enter_block").contains(a.trigger)) {
                            a.triggerValue = fixRef.apply(a.triggerValue);
                        }
                    }
                    case Elements.Tag t -> {
                        if (t.registry.equals("item") || t.registry.equals("block")) {
                            for (int i = 0; i < t.values.size(); i++) t.values.set(i, fixRef.apply(t.values.get(i)));
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        private void readTag(String ns, String rel, JsonObject o) {
            int slash = rel.indexOf('/');
            if (slash <= 0) return;
            String rawReg = rel.substring(0, slash);
            String reg = switch (rawReg) {
                case "items" -> "item";
                case "blocks" -> "block";
                case "entity_types" -> "entity_type";
                case "functions" -> "function";
                case "fluids" -> "fluid";
                default -> rawReg;
            };
            String name = rel.substring(slash + 1);
            Elements.Tag t = new Elements.Tag();
            t.name = p.uniqueName(ElementType.TAG, name);
            t.registry = reg;
            t.namespace = ns;
            t.replace = o.has("replace") && o.get("replace").getAsBoolean();
            if (o.has("values") && o.get("values").isJsonArray()) {
                for (JsonElement je : o.getAsJsonArray("values")) {
                    if (je.isJsonPrimitive()) {
                        t.values.add(je.getAsString());
                    } else if (je.isJsonObject() && hasStr(je.getAsJsonObject(), "id")) {
                        JsonObject jo = je.getAsJsonObject();
                        String tid = jo.get("id").getAsString();
                        boolean req = !jo.has("required") || !jo.get("required").isJsonPrimitive() || jo.get("required").getAsBoolean();
                        if (!req && (reg.equals("item") || reg.equals("block"))
                                && tid.startsWith(p.meta.modId + ":") && p.itemLike(tid) == null) {
                            continue;
                        }
                        t.values.add(tid);
                    }
                }
            }
            if ("block".equals(reg)) {
                blockTags.computeIfAbsent(ns + ":" + name, k -> new LinkedHashSet<>()).addAll(t.values);
            } else if ("item".equals(reg)) {
                itemTags.computeIfAbsent(ns + ":" + name, k -> new LinkedHashSet<>()).addAll(t.values);
            }
            p.elements.add(t);
        }

        private void readPredicateElement(String name, JsonElement je) {
            Elements.Predicate pr = new Elements.Predicate();
            pr.name = p.uniqueName(ElementType.PREDICATE, name);
            collectPredicateTerms(je, pr.terms);
            if (pr.terms.isEmpty()) {
                PredicateTerm term = new PredicateTerm();
                term.kind = "random_chance";
                term.p.put("chance", "0.5");
                pr.terms.add(term);
            }
            p.elements.add(pr);
        }

        private void collectPredicateTerms(JsonElement je, List<PredicateTerm> out) {
            if (je == null || je.isJsonNull()) return;
            if (je.isJsonArray()) {
                for (JsonElement el : je.getAsJsonArray()) collectPredicateTerms(el, out);
                return;
            }
            if (!je.isJsonObject()) return;
            JsonObject o = je.getAsJsonObject();
            String cond = hasStr(o, "condition") ? Ids.path(o.get("condition").getAsString()) : "";
            if ((cond.equals("all_of") || cond.equals("any_of")) && o.has("terms") && o.get("terms").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("terms")) collectPredicateTerms(el, out);
                return;
            }
            PredicateTerm term = new PredicateTerm();
            term.kind = cond.isEmpty() ? "raw" : cond;
            if (o.has("chance")) term.p.put("chance", optStr(o.get("chance"), "0.5"));
            if (o.has("inverted") || cond.equals("inverted")) term.p.put("inverted", "true");
            for (var en : o.entrySet()) {
                if ("condition".equals(en.getKey())) continue;
                if (en.getValue().isJsonPrimitive()) term.p.put(en.getKey(), en.getValue().getAsString());
            }
            if (cond.isEmpty() || !Logic.PREDICATE_TERMS.containsKey(cond)) {
                term.kind = "raw";
                term.p.put("json", o.toString());
            }
            out.add(term);
        }

        private void readModifierElement(String name, JsonElement je) {
            Elements.ItemModifier im = new Elements.ItemModifier();
            im.name = p.uniqueName(ElementType.ITEM_MODIFIER, name);
            collectModifierFns(je, im.functions);
            if (im.functions.isEmpty()) {
                ModifierFunction mf = new ModifierFunction();
                mf.kind = "set_count";
                mf.p.put("count", "1");
                im.functions.add(mf);
            }
            p.elements.add(im);
        }

        private void collectModifierFns(JsonElement je, List<ModifierFunction> out) {
            if (je == null || je.isJsonNull()) return;
            if (je.isJsonArray()) {
                for (JsonElement el : je.getAsJsonArray()) collectModifierFns(el, out);
                return;
            }
            if (!je.isJsonObject()) return;
            JsonObject o = je.getAsJsonObject();
            String fn = hasStr(o, "function") ? Ids.path(o.get("function").getAsString()) : "";
            ModifierFunction mf = new ModifierFunction();
            mf.kind = fn.isEmpty() ? "raw" : fn;
            if (o.has("add") && o.get("add").isJsonPrimitive()) mf.p.put("add", o.get("add").getAsString());
            if (o.has("count") && o.get("count").isJsonPrimitive()) mf.p.put("count", o.get("count").getAsString());
            else if (o.has("count") && o.get("count").isJsonObject()) {
                JsonObject c = o.getAsJsonObject("count");
                if (c.has("min") && c.get("min").isJsonPrimitive()) mf.p.put("count", c.get("min").getAsString());
            }
            if (o.has("damage") && o.get("damage").isJsonPrimitive()) mf.p.put("percent", o.get("damage").getAsString());
            if (o.has("levels") && o.get("levels").isJsonPrimitive()) mf.p.put("levels", o.get("levels").getAsString());
            if (o.has("name")) mf.p.put("text", textField(o.get("name"), null, null, ""));
            if (fn.equals("set_attributes")) mf.kind = "raw";
            if (fn.isEmpty() || !Logic.MODIFIER_FUNCTIONS.containsKey(mf.kind)) {
                mf.kind = "raw";
                mf.p.put("json", o.toString());
            }
            out.add(mf);
        }

        private void readEnchantment(String ns, String name, JsonObject o) {
            Elements.Enchantment e = new Elements.Enchantment();
            e.name = p.uniqueName(ElementType.ENCHANTMENT, name);
            e.displayName = textField(o.get("description"), e, "name", Ids.titleCase(name));
            if (hasStr(o, "supported_items")) e.supportedItems = o.get("supported_items").getAsString();
            if (hasStr(o, "primary_items")) e.primaryItems = o.get("primary_items").getAsString();
            if (hasStr(o, "exclusive_set")) e.exclusiveWith = o.get("exclusive_set").getAsString();
            e.maxLevel = Math.max(1, optInt(o.get("max_level"), e.maxLevel));
            e.weight = Math.max(1, optInt(o.get("weight"), e.weight));
            e.anvilCost = Math.max(0, optInt(o.get("anvil_cost"), e.anvilCost));
            if (o.has("min_cost") && o.get("min_cost").isJsonObject()) {
                JsonObject mc = o.getAsJsonObject("min_cost");
                e.minCostBase = optInt(mc.get("base"), e.minCostBase);
                e.minCostPerLevel = optInt(mc.get("per_level_above_first"), e.minCostPerLevel);
            }
            if (o.has("max_cost") && o.get("max_cost").isJsonObject()) {
                JsonObject mc = o.getAsJsonObject("max_cost");
                e.maxCostBase = optInt(mc.get("base"), e.maxCostBase);
                e.maxCostPerLevel = optInt(mc.get("per_level_above_first"), e.maxCostPerLevel);
            }
            if (o.has("slots") && o.get("slots").isJsonArray() && !o.getAsJsonArray("slots").isEmpty()) {
                JsonElement s0 = o.getAsJsonArray("slots").get(0);
                if (s0.isJsonPrimitive()) e.slot = s0.getAsString();
            }
            p.elements.add(e);
        }

        private void readDamageType(String ns, String name, JsonObject o) {
            Elements.DamageType dt = new Elements.DamageType();
            dt.name = p.uniqueName(ElementType.DAMAGE_TYPE, name);
            if (o.has("exhaustion")) dt.exhaustion = o.get("exhaustion").getAsDouble();
            if (hasStr(o, "scaling")) dt.scaling = o.get("scaling").getAsString();
            if (hasStr(o, "effects")) dt.effects = o.get("effects").getAsString();
            dt.deathMessage = tr("death.attack." + ns + "." + name);
            applyLang(dt, "death", "death.attack." + ns + "." + name);
            p.elements.add(dt);
        }

        private void readPainting(String ns, String name, JsonObject o) {
            Elements.Painting pt = new Elements.Painting();
            pt.name = p.uniqueName(ElementType.PAINTING, name);
            if (o.has("width")) pt.width = o.get("width").getAsInt();
            if (o.has("height")) pt.height = o.get("height").getAsInt();
            pt.title = textField(o.get("title"), pt, "title", Ids.titleCase(name));
            pt.author = textField(o.get("author"), pt, "author", "");
            pt.texture = findTex(hasStr(o, "asset_id") ? o.get("asset_id").getAsString() : ns + ":painting/" + name, "painting");
            p.elements.add(pt);
        }

        private void readSong(String ns, String name, JsonObject o) {
            Elements.Song s = new Elements.Song();
            s.name = p.uniqueName(ElementType.SONG, name);
            s.description = textField(o.get("description"), s, "description", Ids.titleCase(name));
            if (hasStr(o, "sound_event")) s.sound = o.get("sound_event").getAsString();
            if (o.has("length_in_seconds")) s.lengthSeconds = o.get("length_in_seconds").getAsDouble();
            if (o.has("comparator_output")) s.comparatorOutput = o.get("comparator_output").getAsInt();
            p.elements.add(s);
        }

        private void readTrimPattern(String ns, String name, JsonObject o) {
            ModElements.ArmorTrim at = new ModElements.ArmorTrim();
            at.name = p.uniqueName(ElementType.ARMOR_TRIM, name);
            at.displayName = textField(o.get("description"), at, "name", Ids.titleCase(name));
            if (hasStr(o, "template_item")) {
                at.item = o.get("template_item").getAsString();
                at.generateTemplate = false;
            }
            if (o.has("decal")) at.decal = o.get("decal").getAsBoolean();
            at.texture = findTex(ns + ":trims/models/armor/" + name, "trims/models/armor");
            p.elements.add(at);
        }

        private void readBannerPattern(String ns, String name, JsonObject o) {
            ModElements.BannerPattern bp = new ModElements.BannerPattern();
            bp.name = p.uniqueName(ElementType.BANNER_PATTERN, name);
            String tk = hasStr(o, "translation_key") ? o.get("translation_key").getAsString() : "block.minecraft.banner." + ns + "." + name;
            bp.displayName = tr(tk);
            if (bp.displayName.isEmpty()) bp.displayName = Ids.titleCase(name);
            applyLang(bp, "name", tk);
            bp.texture = findTex(ns + ":entity/banner/" + name, "entity/banner");
            p.elements.add(bp);
        }

        private void readDialog(String ns, String name, JsonObject o) {
            Systems.Dialog d = new Systems.Dialog();
            d.name = p.uniqueName(ElementType.DIALOG, name);
            d.title = textField(o.get("title"), d, "title", Ids.titleCase(name));
            String dtype = hasStr(o, "type") ? Ids.path(o.get("type").getAsString()) : "notice";
            d.kind = List.of("notice", "confirmation", "multi_action").contains(dtype) ? dtype : "multi_action";
            if (o.has("columns")) d.columns = Math.max(1, Math.min(4, optInt(o.get("columns"), 2)));
            if (o.has("can_close_with_escape") && o.get("can_close_with_escape").isJsonPrimitive()) {
                d.canClose = o.get("can_close_with_escape").getAsBoolean();
            }
            if (o.has("pause") && o.get("pause").isJsonPrimitive()) d.pause = o.get("pause").getAsBoolean();
            if (o.has("body") && o.get("body").isJsonArray()) {
                List<String> lines = new ArrayList<>();
                for (JsonElement be : o.getAsJsonArray("body")) {
                    if (be.isJsonObject() && be.getAsJsonObject().has("contents")) {
                        lines.add(textField(be.getAsJsonObject().get("contents"), null, null, ""));
                    }
                }
                d.body = String.join("\n", lines);
            } else if (o.has("body")) {
                d.body = textField(o.get("body"), d, "body", "");
            }
            if (o.has("actions") && o.get("actions").isJsonArray()) {
                for (JsonElement ae : o.getAsJsonArray("actions")) {
                    if (ae.isJsonObject()) d.buttons.add(readDialogButton(ae.getAsJsonObject()));
                }
            }
            for (String key : List.of("yes", "no", "action", "exit_action")) {
                if (o.has(key) && o.get(key).isJsonObject()) d.buttons.add(readDialogButton(o.getAsJsonObject(key)));
            }
            if (d.buttons.isEmpty()) {
                DialogButton ok = new DialogButton();
                ok.label = "OK";
                d.buttons.add(ok);
            }
            p.elements.add(d);
        }

        private DialogButton readDialogButton(JsonObject bo) {
            DialogButton db = new DialogButton();
            db.label = textField(bo.get("label"), null, null, "OK");
            if (bo.has("tooltip")) db.tooltip = textField(bo.get("tooltip"), null, null, "");
            if (bo.has("width")) db.width = Math.max(40, Math.min(300, optInt(bo.get("width"), 150)));
            if (bo.has("action") && bo.get("action").isJsonObject()) {
                JsonObject ac = bo.getAsJsonObject("action");
                String at = hasStr(ac, "type") ? Ids.path(ac.get("type").getAsString()) : "";
                if (at.equals("open_url") && hasStr(ac, "url")) { db.action = "url"; db.target = ac.get("url").getAsString(); }
                else if (at.equals("copy_to_clipboard") && hasStr(ac, "value")) { db.action = "copy"; db.target = ac.get("value").getAsString(); }
                else if (at.equals("show_dialog") && hasStr(ac, "dialog")) { db.action = "dialog"; db.target = Ids.path(ac.get("dialog").getAsString()); }
                else if (at.equals("run_command") && hasStr(ac, "command")) { db.action = "run"; db.target = ac.get("command").getAsString(); }
            }
            return db;
        }

        private void readWorldgen(String ns, String rel, String rawPath, JsonObject o) {
            if (rel.startsWith("biome/")) {
                String name = rel.substring(6);
                Systems.Biome b = new Systems.Biome();
                b.name = p.uniqueName(ElementType.BIOME, name);
                b.temperature = optDouble(o.get("temperature"), b.temperature);
                b.downfall = optDouble(o.get("downfall"), b.downfall);
                if (o.has("has_precipitation") && o.get("has_precipitation").isJsonPrimitive()) b.precipitation = o.get("has_precipitation").getAsBoolean();
                if (o.has("effects") && o.get("effects").isJsonObject()) {
                    JsonObject ef = o.getAsJsonObject("effects");
                    if (ef.has("sky_color")) b.sky = hexColor(ef.get("sky_color"));
                    if (ef.has("fog_color")) b.fog = hexColor(ef.get("fog_color"));
                    if (ef.has("water_color")) b.water = hexColor(ef.get("water_color"));
                    if (ef.has("water_fog_color")) b.waterFog = hexColor(ef.get("water_fog_color"));
                    if (ef.has("grass_color")) b.grass = hexColor(ef.get("grass_color"));
                    if (ef.has("foliage_color")) b.foliage = hexColor(ef.get("foliage_color"));
                    if (ef.has("ambient_sound") && hasStr(ef, "ambient_sound")) b.ambientSound = ef.get("ambient_sound").getAsString();
                    if (ef.has("music") && ef.get("music").isJsonObject() && hasStr(ef.getAsJsonObject("music"), "sound")) {
                        b.music = ef.getAsJsonObject("music").get("sound").getAsString();
                    }
                }
                if (o.has("spawners") && o.get("spawners").isJsonObject()) {
                    for (var se : o.getAsJsonObject("spawners").entrySet()) {
                        if (!se.getValue().isJsonArray()) continue;
                        for (JsonElement item : se.getValue().getAsJsonArray()) {
                            if (!item.isJsonObject()) continue;
                            JsonObject sp = item.getAsJsonObject();
                            if (!hasStr(sp, "type")) continue;
                            Systems.MobSpawn ms = new Systems.MobSpawn();
                            ms.entity = sp.get("type").getAsString();
                            ms.category = se.getKey();
                            ms.weight = optInt(sp.get("weight"), 10);
                            ms.min = optInt(sp.get("minCount"), 1);
                            ms.max = optInt(sp.get("maxCount"), 4);
                            b.spawns.add(ms);
                        }
                    }
                }
                p.elements.add(b);
            } else if (rel.startsWith("dimension/")) {
                String name = rel.substring(10);
                Systems.Dimension d = new Systems.Dimension();
                d.name = p.uniqueName(ElementType.DIMENSION, name);
                if (hasStr(o, "type")) d.effects = o.get("type").getAsString();
                if (o.has("generator") && o.get("generator").isJsonObject()) {
                    JsonObject gen = o.getAsJsonObject("generator");
                    String gt = Ids.path(hasStr(gen, "type") ? gen.get("type").getAsString() : "noise");
                    d.generator = List.of("noise", "flat", "void").contains(gt) ? gt : "noise";
                    if (gen.has("settings") && hasStr(gen, "settings")) d.noise = gen.get("settings").getAsString();
                    if (gen.has("biome_source") && gen.get("biome_source").isJsonObject()) {
                        JsonObject bs = gen.getAsJsonObject("biome_source");
                        String bt = hasStr(bs, "type") ? Ids.path(bs.get("type").getAsString()) : "fixed";
                        d.biomeSource = bt.contains("checkerboard") ? "checkerboard" : bt.contains("multi") ? "multi_noise" : "single";
                        if (hasStr(bs, "biome")) d.biomes = new ArrayList<>(List.of(bs.get("biome").getAsString()));
                        else if (bs.has("biomes") && bs.get("biomes").isJsonArray()) {
                            List<String> bl = new ArrayList<>();
                            for (JsonElement be : bs.getAsJsonArray("biomes")) {
                                if (be.isJsonPrimitive()) bl.add(be.getAsString());
                                else if (be.isJsonObject() && hasStr(be.getAsJsonObject(), "biome")) bl.add(be.getAsJsonObject().get("biome").getAsString());
                            }
                            if (!bl.isEmpty()) d.biomes = bl;
                        }
                    }
                    if ("flat".equals(d.generator) && gen.has("settings") && gen.get("settings").isJsonObject()) {
                        JsonObject st = gen.getAsJsonObject("settings");
                        if (st.has("layers") && st.get("layers").isJsonArray()) {
                            List<String> flat = new ArrayList<>();
                            for (JsonElement le : st.getAsJsonArray("layers")) {
                                if (!le.isJsonObject()) continue;
                                JsonObject lo = le.getAsJsonObject();
                                String blk = hasStr(lo, "block") ? lo.get("block").getAsString() : "minecraft:stone";
                                int h = optInt(lo.get("height"), 1);
                                flat.add(blk + "*" + Math.max(1, h));
                            }
                            if (!flat.isEmpty()) d.flatLayers = String.join("\n", flat);
                        }
                    }
                }
                p.elements.add(d);
            } else if (rel.startsWith("configured_feature/")) {
                String name = rel.substring("configured_feature/".length());
                configuredFeatures.put(ns + ":" + name, o);
                configuredFeatures.putIfAbsent(name, o);
                ModElements.WorldFeature wf = new ModElements.WorldFeature();
                wf.name = p.uniqueName(ElementType.WORLD_FEATURE, name);
                String t = hasStr(o, "type") ? Ids.path(o.get("type").getAsString()) : "ore";
                wf.featureType = t.contains("tree") ? "tree" : (t.contains("patch") || t.contains("random_patch")) ? "patch" : t.contains("spike") ? "spike" : "ore";
                if (o.has("config") && o.get("config").isJsonObject()) {
                    JsonObject cfg = o.getAsJsonObject("config");
                    wf.size = Math.max(1, optInt(cfg.get("size"), wf.size));
                    wf.xzSpread = Math.max(1, optInt(cfg.get("xz_spread"), wf.xzSpread));
                    wf.ySpread = Math.max(0, optInt(cfg.get("y_spread"), wf.ySpread));
                    wf.discardAir = optDouble(cfg.get("discard_chance_on_air_exposure"), wf.discardAir);
                    if (cfg.has("targets") && cfg.get("targets").isJsonArray() && !cfg.getAsJsonArray("targets").isEmpty()) {
                        JsonObject firstT = cfg.getAsJsonArray("targets").get(0).getAsJsonObject();
                        if (firstT.has("state") && firstT.get("state").isJsonObject() && hasStr(firstT.getAsJsonObject("state"), "Name")) {
                            wf.block = firstT.getAsJsonObject("state").get("Name").getAsString();
                        }
                        if (firstT.has("target") && firstT.get("target").isJsonObject()) {
                            JsonObject tr = firstT.getAsJsonObject("target");
                            if (hasStr(tr, "tag")) wf.replaceTarget = "#" + tr.get("tag").getAsString();
                            else if (hasStr(tr, "block")) wf.replaceTarget = tr.get("block").getAsString();
                        }
                    }
                    if (cfg.has("trunk_provider") && cfg.get("trunk_provider").isJsonObject()) {
                        JsonObject tp = cfg.getAsJsonObject("trunk_provider");
                        if (tp.has("state") && tp.get("state").isJsonObject() && hasStr(tp.getAsJsonObject("state"), "Name")) {
                            wf.trunkBlock = tp.getAsJsonObject("state").get("Name").getAsString();
                        }
                    }
                    if (cfg.has("foliage_provider") && cfg.get("foliage_provider").isJsonObject()) {
                        JsonObject fp = cfg.getAsJsonObject("foliage_provider");
                        if (fp.has("state") && fp.get("state").isJsonObject() && hasStr(fp.getAsJsonObject("state"), "Name")) {
                            wf.leavesBlock = fp.getAsJsonObject("state").get("Name").getAsString();
                        }
                    }
                }
                p.elements.add(wf);
            } else if (rel.startsWith("placed_feature/")) {
                String name = rel.substring("placed_feature/".length());
                placedFeatures.put(ns + ":" + name, o);
                placedFeatures.putIfAbsent(name, o);
            } else {
                preserveRawResource(rawPath);
            }
        }

        // -------------------------------------------------------------- mixins & code

        private void readMixinsAndCode() {
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                String low = path.toLowerCase(Locale.ROOT);
                if ((low.endsWith(".mixins.json") || basename(low).startsWith("mixins.")) && low.endsWith(".json")) {
                    JsonObject mo = store.json(path);
                    if (mo == null) continue;
                    String pkg = hasStr(mo, "package") ? mo.get("package").getAsString() : p.meta.packageName + ".mixin";
                    readMixinList(mo.getAsJsonArray("mixins"), "common", pkg);
                    readMixinList(mo.getAsJsonArray("client"), "client", pkg);
                    readMixinList(mo.getAsJsonArray("server"), "server", pkg);
                } else if (low.endsWith(".java") || low.endsWith(".kt") || low.endsWith(".scala") || low.endsWith(".groovy")
                        || ((low.endsWith(".js") || low.endsWith(".ts")) && low.contains("scripts/"))) {
                    Elements.CodeFile cf = new Elements.CodeFile();
                    cf.name = p.uniqueName(ElementType.CODE_FILE, Ids.toId(stripExt(basename(path))));
                    cf.target = (low.endsWith(".js") || low.endsWith(".ts")) ? "all"
                            : (p.meta.targets.isEmpty() ? "fabric" : p.meta.targets.size() > 1 ? String.join(",", p.meta.targets) : p.meta.targets.get(0));
                    String prefix = low.endsWith(".java") ? "src/main/java/" : low.endsWith(".kt") ? "src/main/kotlin/" : low.endsWith(".scala") ? "src/main/scala/" : low.endsWith(".groovy") ? "src/main/groovy/" : "";
                    cf.path = !prefix.isEmpty() && !path.startsWith(prefix) && !path.startsWith("src/main/") ? prefix + path : path;
                    cf.language = low.endsWith(".kt") ? "kotlin" : low.endsWith(".scala") ? "scala" : low.endsWith(".groovy") ? "groovy" : low.endsWith(".ts") ? "typescript" : "auto";
                    cf.content = store.text(path);
                    if (cf.content != null && (low.endsWith(".java") || low.endsWith(".kt"))) javaSources.put(path, cf.content);
                    p.elements.add(cf);
                } else if (low.endsWith(".accesswidener")
                        || low.equals("meta-inf/accesstransformer.cfg")
                        || low.startsWith("config/") && (low.endsWith(".toml") || low.endsWith(".json") || low.endsWith(".cfg"))
                        || low.startsWith("assets/") && (low.contains("/font/") || low.contains("/particles/") || low.contains("/post_effect/")) && low.endsWith(".json")) {
                    preserveRawResource(path);
                }
            }
            if (store.classFileCount > 0) {
                notes.add(I18n.tOr("import.note.bytecode_classes",
                        "Compiled bytecode classes detected: " + store.classFileCount + " (.class files scanned for properties/logic; you can also patch the original JAR directly to preserve any custom JVM bytecode).",
                        store.classFileCount));
            }
        }

        // -------------------------------------------------------------- Bedrock content & full gameplay enrichment

        private static boolean isBedrockSection(String low, String dir) {
            return (low.startsWith(dir + "/") || low.contains("/" + dir + "/"))
                    && !low.startsWith("assets/") && !low.startsWith("data/");
        }

        private void readBedrockContent() {
            for (var en : store.files.entrySet()) {
                String path = en.getKey();
                String low = path.toLowerCase(Locale.ROOT);
                if (!low.endsWith(".json")) continue;
                if (isBedrockSection(low, "items")) {
                    JsonObject o = store.json(path);
                    if (o == null || !o.has("minecraft:item")) continue;
                    JsonObject mi = o.getAsJsonObject("minecraft:item");
                    JsonObject desc = mi.has("description") && mi.get("description").isJsonObject() ? mi.getAsJsonObject("description") : null;
                    String rawId = desc != null && hasStr(desc, "identifier") ? desc.get("identifier").getAsString() : stripExt(basename(path));
                    String itemName = Ids.toId(Ids.path(rawId));
                    if (itemName.isEmpty() || itemByName.containsKey(itemName)) continue;
                    Elements.Item it = new Elements.Item();
                    it.name = p.uniqueName(ElementType.ITEM, itemName);
                    String dn = tr("item." + rawId);
                    it.displayName = dn.isEmpty() ? Ids.titleCase(itemName) : dn;
                    it.texture = findTex(p.meta.modId + ":items/" + itemName, "items");
                    JsonObject comp = mi.has("components") && mi.get("components").isJsonObject() ? mi.getAsJsonObject("components") : new JsonObject();
                    if (comp.has("minecraft:icon") && comp.get("minecraft:icon").isJsonPrimitive()) {
                        String tx = findTex(p.meta.modId + ":items/" + comp.get("minecraft:icon").getAsString(), "items");
                        if (!tx.isBlank()) it.texture = tx;
                    }
                    if (comp.has("minecraft:max_stack_size")) it.maxStack = Math.max(1, Math.min(99, comp.get("minecraft:max_stack_size").getAsInt()));
                    if (comp.has("minecraft:durability") && comp.get("minecraft:durability").isJsonObject()) {
                        JsonObject dur = comp.getAsJsonObject("minecraft:durability");
                        if (dur.has("max_durability")) it.maxDamage = dur.get("max_durability").getAsInt();
                    }
                    if (comp.has("minecraft:food") && comp.get("minecraft:food").isJsonObject()) {
                        it.kind = "food";
                        JsonObject fd = comp.getAsJsonObject("minecraft:food");
                        if (fd.has("nutrition")) it.nutrition = fd.get("nutrition").getAsInt();
                        if (fd.has("saturation_modifier")) it.saturation = fd.get("saturation_modifier").getAsDouble();
                        if (fd.has("can_always_eat")) it.alwaysEdible = fd.get("can_always_eat").getAsBoolean();
                    }
                    if (comp.has("minecraft:cooldown") && comp.get("minecraft:cooldown").isJsonObject()) {
                        JsonObject cd = comp.getAsJsonObject("minecraft:cooldown");
                        if (cd.has("duration")) it.useCooldown = cd.get("duration").getAsDouble();
                    }
                    if (comp.has("minecraft:wearable") && comp.get("minecraft:wearable").isJsonObject()) {
                        String sl = hasStr(comp.getAsJsonObject("minecraft:wearable"), "slot") ? comp.getAsJsonObject("minecraft:wearable").get("slot").getAsString() : "";
                        if (sl.contains("head")) it.kind = "helmet";
                        else if (sl.contains("chest")) it.kind = "chestplate";
                        else if (sl.contains("legs")) it.kind = "leggings";
                        else if (sl.contains("feet")) it.kind = "boots";
                    }
                    if (comp.has("minecraft:damage")) {
                        it.kind = "sword";
                        it.attackDamage = comp.get("minecraft:damage").getAsDouble();
                    }
                    if (comp.has("minecraft:glint") && comp.get("minecraft:glint").isJsonPrimitive()) it.glint = comp.get("minecraft:glint").getAsBoolean();
                    p.elements.add(it);
                    itemByName.put(itemName, it);
                } else if (isBedrockSection(low, "blocks")) {
                    JsonObject o = store.json(path);
                    if (o == null || !o.has("minecraft:block")) continue;
                    JsonObject mb = o.getAsJsonObject("minecraft:block");
                    JsonObject desc = mb.has("description") && mb.get("description").isJsonObject() ? mb.getAsJsonObject("description") : null;
                    String rawId = desc != null && hasStr(desc, "identifier") ? desc.get("identifier").getAsString() : stripExt(basename(path));
                    String blkName = Ids.toId(Ids.path(rawId));
                    if (blkName.isEmpty() || blockByName.containsKey(blkName)) continue;
                    Elements.Block b = new Elements.Block();
                    b.name = p.uniqueName(ElementType.BLOCK, blkName);
                    b.displayName = Ids.titleCase(blkName);
                    b.texture = findTex(p.meta.modId + ":blocks/" + blkName, "blocks");
                    JsonObject comp = mb.has("components") && mb.get("components").isJsonObject() ? mb.getAsJsonObject("components") : new JsonObject();
                    if (comp.has("minecraft:destructible_by_mining") && comp.get("minecraft:destructible_by_mining").isJsonObject()) {
                        JsonObject dm = comp.getAsJsonObject("minecraft:destructible_by_mining");
                        if (dm.has("seconds_to_destroy")) b.hardness = dm.get("seconds_to_destroy").getAsDouble();
                    }
                    if (comp.has("minecraft:light_emission")) b.light = Math.max(0, Math.min(15, comp.get("minecraft:light_emission").getAsInt()));
                    if (comp.has("minecraft:friction")) b.friction = comp.get("minecraft:friction").getAsDouble();
                    p.elements.add(b);
                    blockByName.put(blkName, b);
                } else if (isBedrockSection(low, "entities")) {
                    JsonObject o = store.json(path);
                    if (o == null || !o.has("minecraft:entity")) continue;
                    JsonObject me = o.getAsJsonObject("minecraft:entity");
                    JsonObject desc = me.has("description") && me.get("description").isJsonObject() ? me.getAsJsonObject("description") : null;
                    String rawId = desc != null && hasStr(desc, "identifier") ? desc.get("identifier").getAsString() : stripExt(basename(path));
                    String entName = Ids.toId(Ids.path(rawId));
                    if (entName.isEmpty() || p.byName(ElementType.ENTITY, entName) != null) continue;
                    ModElements.CustomEntity ce = new ModElements.CustomEntity();
                    ce.name = p.uniqueName(ElementType.ENTITY, entName);
                    ce.displayName = Ids.titleCase(entName);
                    JsonObject comp = me.has("components") && me.get("components").isJsonObject() ? me.getAsJsonObject("components") : new JsonObject();
                    if (comp.has("minecraft:health") && comp.get("minecraft:health").isJsonObject() && comp.getAsJsonObject("minecraft:health").has("max")) {
                        ce.health = comp.getAsJsonObject("minecraft:health").get("max").getAsDouble();
                    }
                    if (comp.has("minecraft:movement") && comp.get("minecraft:movement").isJsonObject() && comp.getAsJsonObject("minecraft:movement").has("value")) {
                        ce.speed = comp.getAsJsonObject("minecraft:movement").get("value").getAsDouble();
                    }
                    if (comp.has("minecraft:attack") && comp.get("minecraft:attack").isJsonObject() && comp.getAsJsonObject("minecraft:attack").has("damage")) {
                        ce.attackDamage = comp.getAsJsonObject("minecraft:attack").get("damage").getAsDouble();
                    }
                    if (comp.has("minecraft:collision_box") && comp.get("minecraft:collision_box").isJsonObject()) {
                        JsonObject cb = comp.getAsJsonObject("minecraft:collision_box");
                        if (cb.has("width")) ce.width = cb.get("width").getAsDouble();
                        if (cb.has("height")) ce.height = cb.get("height").getAsDouble();
                    }
                    if (comp.has("minecraft:scale") && comp.get("minecraft:scale").isJsonObject() && comp.getAsJsonObject("minecraft:scale").has("value")) {
                        ce.scale = comp.getAsJsonObject("minecraft:scale").get("value").getAsDouble();
                    }
                    if (comp.has("minecraft:fire_immune")) ce.fireImmune = true;
                    p.elements.add(ce);
                } else if (isBedrockSection(low, "recipes")) {
                    JsonObject o = store.json(path);
                    if (o == null) continue;
                    for (String k : List.of("minecraft:recipe_shaped", "minecraft:recipe_shapeless", "minecraft:recipe_furnace")) {
                        if (o.has(k) && o.get(k).isJsonObject()) {
                            JsonObject rc = o.getAsJsonObject(k).deepCopy();
                            rc.addProperty("type", k.replace("minecraft:recipe_", "minecraft:crafting_").replace("crafting_furnace", "smelting"));
                            readRecipe(p.meta.modId, stripExt(basename(path)), path, rc);
                            break;
                        }
                    }
                }
            }
        }

        private void enrichImportedFeatures() {
            String ns = p.meta.modId;
            // 1. Apply mining tool / level tags onto Blocks
            applyBlockTagProperty("minecraft:mineable/pickaxe", b -> b.tool = "pickaxe");
            applyBlockTagProperty("minecraft:mineable/axe", b -> b.tool = "axe");
            applyBlockTagProperty("minecraft:mineable/shovel", b -> b.tool = "shovel");
            applyBlockTagProperty("minecraft:mineable/hoe", b -> b.tool = "hoe");
            applyBlockTagProperty("minecraft:needs_stone_tool", b -> { b.toolLevel = "stone"; b.requiresTool = true; });
            applyBlockTagProperty("minecraft:needs_iron_tool", b -> { b.toolLevel = "iron"; b.requiresTool = true; });
            applyBlockTagProperty("minecraft:needs_diamond_tool", b -> { b.toolLevel = "diamond"; b.requiresTool = true; });

            // 2. Apply block loot tables directly onto Block.drop / dropItem / fortune / silkTouch
            for (var en : blockLootJsons.entrySet()) {
                if (!(blockByName.get(en.getKey()) instanceof Elements.Block b)) continue;
                applyBlockLootToBlock(b, en.getValue());
            }

            // 3. Apply item tags onto Item.kind
            applyItemTagKind("swords", "sword");
            applyItemTagKind("pickaxes", "pickaxe");
            applyItemTagKind("axes", "axe");
            applyItemTagKind("shovels", "shovel");
            applyItemTagKind("hoes", "hoe");
            applyItemTagKind("head_armor", "helmet");
            applyItemTagKind("chest_armor", "chestplate");
            applyItemTagKind("leg_armor", "leggings");
            applyItemTagKind("foot_armor", "boots");
            applyItemTagKind("arrows", "basic");
            applyItemTagKind("music_discs", "basic");
            for (String ft : List.of("minecraft:meat", "c:foods", "forge:foods")) {
                Set<String> set = itemTags.get(ft);
                if (set != null) {
                    for (String id : set) {
                        if (findItemById(id) instanceof Elements.Item it && "basic".equals(it.kind)) it.kind = "food";
                    }
                }
            }

            // 4. Discover armor materials from armor/armour layer textures or equipment assets & link armor pieces
            Map<String, ArmorMaterial> detectedArmors = new LinkedHashMap<>();
            for (var en : texByKey.entrySet()) {
                String texKey = en.getKey();
                String texId = en.getValue();
                if (!texKey.contains(":")) continue;
                String rel = Ids.path(texKey);
                if (rel.startsWith("models/armor/") || rel.startsWith("entity/armor/") || rel.startsWith("entity/armour/")) {
                    String rest = rel.substring(rel.indexOf('/', rel.indexOf('/') + 1) + 1);
                    if (rest.endsWith("_overlay")) continue;
                    boolean isL1 = rest.endsWith("_layer_1") || rest.endsWith("_1");
                    boolean isL2 = rest.endsWith("_layer_2") || rest.endsWith("_2") || rest.endsWith("_legs");
                    String stripped = rest
                            .replaceAll("(_layer)?_[12]$", "")
                            .replaceAll("_legs$", "")
                            .replaceAll("_(armour|armor)$", "");
                    String matName = Ids.toId(stripped);
                    if (matName.isEmpty()) continue;
                    ArmorMaterial am = detectedArmors.computeIfAbsent(matName, this::getOrCreateArmorMat);
                    if (isL2) {
                        if (am.layer2.isBlank()) am.layer2 = texId;
                    } else if (isL1 || rel.startsWith("entity/armor/") || rel.startsWith("entity/armour/")) {
                        if (am.layer1.isBlank()) am.layer1 = texId;
                    }
                } else if (rel.startsWith("armour/") || rel.startsWith("armor/")) {
                    String rest = rel.substring(rel.indexOf('/') + 1);
                    if (rest.contains("/")) rest = rest.substring(rest.lastIndexOf('/') + 1);
                    boolean isLegs = rest.endsWith("_legs") || rest.endsWith("_layer_2") || rest.endsWith("_2");
                    String stripped = rest
                            .replaceAll("(_layer)?_[12]$", "")
                            .replaceAll("_legs$", "")
                            .replaceAll("_(armour|armor)$", "");
                    // Also handle element-suffixed armour textures like battlemage_armour_fire_legs -> battlemage
                    int armIdx = stripped.indexOf("_armour");
                    if (armIdx < 0) armIdx = stripped.indexOf("_armor");
                    if (armIdx > 1) stripped = stripped.substring(0, armIdx);
                    String matName = Ids.toId(stripped);
                    if (matName.isEmpty()) continue;
                    ArmorMaterial am = detectedArmors.computeIfAbsent(matName, this::getOrCreateArmorMat);
                    if (isLegs) {
                        if (am.layer2.isBlank()) am.layer2 = texId;
                    } else {
                        if (am.layer1.isBlank()) am.layer1 = texId;
                    }
                }
            }
            for (Elements.Item it : p.all(Elements.Item.class)) {
                if (it.kind.equals("basic")) {
                    if (it.name.endsWith("_helmet")) it.kind = "helmet";
                    else if (it.name.endsWith("_chestplate")) it.kind = "chestplate";
                    else if (it.name.endsWith("_leggings")) it.kind = "leggings";
                    else if (it.name.endsWith("_boots")) it.kind = "boots";
                }
                if (it.isArmor() && it.armorMaterial.isBlank()) {
                    String baseMat = it.name.replaceAll("_(helmet|chestplate|leggings|boots)$", "");
                    if (!baseMat.equals(it.name)) {
                        ArmorMaterial am = detectedArmors.computeIfAbsent(baseMat, this::getOrCreateArmorMat);
                        it.armorMaterial = am.name;
                        it.maxStack = 1;
                    }
                }
                if (it.kind.equals("basic") && it.song.isBlank() && it.name.startsWith("music_disc_")) {
                    String sName = Ids.toId(it.name.substring("music_disc_".length()));
                    if (p.byName(ElementType.SONG, sName) != null) it.song = sName;
                }
            }
            // Fill missing armor layer1/layer2 via prefix match in armour/armor textures or mirror single-layer armor
            for (ArmorMaterial am : p.all(ArmorMaterial.class)) {
                if (am.layer1.isBlank() || am.layer2.isBlank()) {
                    for (var en : texByKey.entrySet()) {
                        String tk = en.getKey();
                        if (!tk.contains(":")) continue;
                        String rel = Ids.path(tk);
                        if (!rel.startsWith("models/armor/") && !rel.startsWith("entity/armor/") && !rel.startsWith("entity/armour/")
                                && !rel.startsWith("armour/") && !rel.startsWith("armor/")) continue;
                        String base = rel.contains("/") ? rel.substring(rel.lastIndexOf('/') + 1) : rel;
                        if (base.endsWith("_overlay")) continue;
                        if (!base.startsWith(am.name)) continue;
                        boolean isL2 = base.endsWith("_legs") || base.endsWith("_layer_2") || base.endsWith("_2");
                        if (isL2 && am.layer2.isBlank()) am.layer2 = en.getValue();
                        else if (!isL2 && am.layer1.isBlank()) am.layer1 = en.getValue();
                    }
                }
                if (am.layer1.isBlank() && !am.layer2.isBlank()) am.layer1 = am.layer2;
                if (am.layer2.isBlank() && !am.layer1.isBlank()) am.layer2 = am.layer1;
            }

            // 5. Connect placed_feature & biome_modifier details into WorldFeature + ore Blocks
            for (ModElements.WorldFeature wf : p.all(ModElements.WorldFeature.class)) {
                JsonObject pf = placedFeatures.getOrDefault(ns + ":" + wf.name, placedFeatures.get(wf.name));
                if (pf != null && pf.has("placement") && pf.get("placement").isJsonArray()) {
                    for (JsonElement pe : pf.getAsJsonArray("placement")) {
                        if (!pe.isJsonObject()) continue;
                        JsonObject po = pe.getAsJsonObject();
                        String pt = hasStr(po, "type") ? Ids.path(po.get("type").getAsString()) : "";
                        if (pt.equals("count") && po.has("count") && po.get("count").isJsonPrimitive()) wf.count = po.get("count").getAsInt();
                        else if (pt.equals("rarity_filter") && po.has("chance")) { wf.rareFilter = true; wf.count = po.get("chance").getAsInt(); }
                        else if (pt.equals("height_range") && po.has("height") && po.get("height").isJsonObject()) {
                            JsonObject ho = po.getAsJsonObject("height");
                            if (hasStr(ho, "type") && Ids.path(ho.get("type").getAsString()).equals("uniform")) wf.distribution = "uniform";
                            if (ho.has("min_inclusive") && ho.get("min_inclusive").isJsonObject() && ho.getAsJsonObject("min_inclusive").has("absolute"))
                                wf.minY = ho.getAsJsonObject("min_inclusive").get("absolute").getAsInt();
                            if (ho.has("max_inclusive") && ho.get("max_inclusive").isJsonObject() && ho.getAsJsonObject("max_inclusive").has("absolute"))
                                wf.maxY = ho.getAsJsonObject("max_inclusive").get("absolute").getAsInt();
                        } else if (pt.equals("surface_relative_threshold_filter") && po.has("max_inclusive")) {
                            wf.preventSurface = true;
                            wf.surfaceDepth = Math.max(1, -po.get("max_inclusive").getAsInt());
                        }
                    }
                }
                for (JsonObject bm : biomeModifierJsons) {
                    String feats = bm.has("features") ? bm.get("features").toString() : "";
                    if (feats.contains(wf.name) && bm.has("biomes")) {
                        JsonElement be = bm.get("biomes");
                        if (be.isJsonPrimitive()) wf.biomes = be.getAsString();
                    }
                }
                if ("ore".equals(wf.featureType) && wf.block != null && !wf.block.isBlank()) {
                    if (blockByName.get(Ids.path(wf.block)) instanceof Elements.Block ob) {
                        ob.oreGen = true;
                        ob.oreSize = wf.size;
                        ob.oreCount = wf.count;
                        ob.oreMinY = wf.minY;
                        ob.oreMaxY = Math.min(320, wf.maxY);
                        ob.oreDistribution = wf.distribution;
                        ob.oreDiscardAir = wf.discardAir;
                        ob.orePreventSurface = wf.preventSurface;
                        ob.oreSurfaceDepth = wf.surfaceDepth;
                        if (wf.replaceTarget != null && !wf.replaceTarget.isBlank()) ob.oreReplaceTarget = wf.replaceTarget;
                        if (wf.biomes != null && !wf.biomes.isBlank()) ob.oreBiomes = wf.biomes;
                    }
                }
            }

            // 6. Auto-discover Entities / StatusEffects / CreativeTabs from language keys
            Map<String, String> enLang = langs.getOrDefault("en_us", langs.values().stream().findFirst().orElse(Map.of()));
            for (var kv : enLang.entrySet()) {
                String k = kv.getKey();
                if (k.startsWith("entity." + ns + ".")) {
                    String eName = Ids.toId(k.substring(("entity." + ns + ".").length()));
                    if (!eName.isEmpty() && !eName.contains(".") && p.byName(ElementType.ENTITY, eName) == null) {
                        ModElements.CustomEntity ce = new ModElements.CustomEntity();
                        ce.name = p.uniqueName(ElementType.ENTITY, eName);
                        ce.displayName = kv.getValue();
                        applyLang(ce, "name", k);
                        ce.texture = findEntityTex(ns, eName);
                        p.elements.add(ce);
                    }
                } else if (k.startsWith("effect." + ns + ".")) {
                    String efName = Ids.toId(k.substring(("effect." + ns + ".").length()));
                    if (!efName.isEmpty() && p.byName(ElementType.STATUS_EFFECT, efName) == null) {
                        Elements.StatusEffect se = new Elements.StatusEffect();
                        se.name = p.uniqueName(ElementType.STATUS_EFFECT, efName);
                        se.displayName = kv.getValue();
                        applyLang(se, "name", k);
                        p.elements.add(se);
                    }
                } else if (k.startsWith("itemGroup." + ns + ".")) {
                    String tName = Ids.toId(k.substring(("itemGroup." + ns + ".").length()));
                    if (!tName.isEmpty() && p.byName(ElementType.CREATIVE_TAB, tName) == null) {
                        Elements.CreativeTab tab = new Elements.CreativeTab();
                        tab.name = p.uniqueName(ElementType.CREATIVE_TAB, tName);
                        tab.displayName = kv.getValue();
                        applyLang(tab, "name", k);
                        p.elements.add(tab);
                    }
                }
            }

            // 7. Run JVM Bytecode (.class) + Java/Kotlin Source Extractor to restore properties & functional procedures
            List<String> classPaths = new ArrayList<>();
            for (String path : store.files.keySet()) {
                if (path.endsWith(".class")) classPaths.add(path);
            }
            ModLogicExtractor.Result logic = ModLogicExtractor.analyze(classPaths, this::readRawClass, javaSources, ns);
            mergeLogicResults(logic);
        }

        private void applyBlockTagProperty(String fullTag, java.util.function.Consumer<Elements.Block> fn) {
            Set<String> ids = blockTags.get(fullTag);
            if (ids == null) return;
            for (String id : ids) {
                if (blockByName.get(Ids.path(id)) instanceof Elements.Block b) fn.accept(b);
            }
        }

        private void applyItemTagKind(String tagSuffix, String kind) {
            for (String prefix : List.of("minecraft:", "c:", "forge:", "neoforge:")) {
                Set<String> ids = itemTags.get(prefix + tagSuffix);
                if (ids == null) continue;
                for (String id : ids) {
                    if (findItemById(id) instanceof Elements.Item it && "basic".equals(it.kind)) {
                        it.kind = kind;
                    }
                }
            }
        }

        private Elements.Item findItemById(String raw) {
            String path = Ids.path(raw);
            Elements.Item direct = itemByName.get(path);
            if (direct != null) return direct;
            return p.byName(ElementType.ITEM, Ids.toId(path)) instanceof Elements.Item it ? it : null;
        }

        private String findEntityTex(String ns, String name) {
            for (String candidate : List.of(
                    ns + ":entity/" + name,
                    ns + ":entity/" + name + "/" + name,
                    ns + ":mob/" + name,
                    ns + ":" + name)) {
                String t = findTex(candidate, "entity");
                if (!t.isBlank()) return t;
            }
            return "";
        }

        private ArmorMaterial getOrCreateArmorMat(String rawName) {
            String clean = Ids.toId(rawName);
            if (p.byName(ElementType.ARMOR_MATERIAL, clean) instanceof ArmorMaterial ex) return ex;
            ArmorMaterial am = new ArmorMaterial();
            am.name = p.uniqueName(ElementType.ARMOR_MATERIAL, clean);
            p.elements.add(am);
            return am;
        }

        private void applyBlockLootToBlock(Elements.Block b, JsonObject loot) {
            if (loot == null || !loot.has("pools") || !loot.get("pools").isJsonArray()) return;
            JsonArray pools = loot.getAsJsonArray("pools");
            if (pools.isEmpty()) { b.drop = "none"; return; }
            for (JsonElement pe : pools) {
                if (!pe.isJsonObject() || !pe.getAsJsonObject().has("entries")) continue;
                for (JsonElement ee : pe.getAsJsonObject().getAsJsonArray("entries")) {
                    if (!ee.isJsonObject()) continue;
                    JsonObject eo = ee.getAsJsonObject();
                    if (hasStr(eo, "name")) {
                        String dropId = eo.get("name").getAsString();
                        if (!Ids.path(dropId).equals(b.name)) {
                            b.drop = "item";
                            b.dropItem = dropId;
                        }
                    }
                    if (eo.has("functions") && eo.get("functions").isJsonArray()) {
                        for (JsonElement fe : eo.getAsJsonArray("functions")) {
                            if (!fe.isJsonObject()) continue;
                            JsonObject fo = fe.getAsJsonObject();
                            String fn = hasStr(fo, "function") ? Ids.path(fo.get("function").getAsString()) : "";
                            if (fn.equals("apply_bonus")) b.fortune = true;
                            else if (fn.equals("set_count") && fo.has("count")) {
                                JsonElement c = fo.get("count");
                                if (c.isJsonPrimitive()) { b.dropMin = b.dropMax = c.getAsInt(); }
                                else if (c.isJsonObject()) {
                                    if (c.getAsJsonObject().has("min")) b.dropMin = c.getAsJsonObject().get("min").getAsInt();
                                    if (c.getAsJsonObject().has("max")) b.dropMax = c.getAsJsonObject().get("max").getAsInt();
                                }
                            }
                        }
                    }
                    if (eo.has("children") && eo.get("children").isJsonArray()) {
                        for (JsonElement ce : eo.getAsJsonArray("children")) {
                            if (!ce.isJsonObject()) continue;
                            JsonObject co = ce.getAsJsonObject();
                            if (co.toString().contains("silk_touch")) b.silkTouch = true;
                            if (hasStr(co, "name") && !Ids.path(co.get("name").getAsString()).equals(b.name)) {
                                b.drop = "item";
                                b.dropItem = co.get("name").getAsString();
                            }
                        }
                    }
                }
            }
        }

        private void mergeLogicResults(ModLogicExtractor.Result logic) {
            int restoredProps = 0;
            for (var en : logic.items.entrySet()) {
                String id = en.getKey();
                ModLogicExtractor.ItemProps ip = en.getValue();
                if (blockByName.containsKey(id)) continue; // BlockItem skips Item creation
                Elements.Item it = findItemById(id);
                if (it == null && (!ip.onUse.isEmpty() || !ip.onHitEntity.isEmpty() || !ip.onTickInHand.isEmpty())) {
                    // Propagate shared family class logic (e.g. ItemWand -> *_wand, ItemScroll -> *_scroll)
                    List<Elements.Item> familyMatches = new ArrayList<>();
                    for (Elements.Item cand : p.all(Elements.Item.class)) {
                        if (cand.name.endsWith("_" + id) || cand.name.startsWith(id + "_")) familyMatches.add(cand);
                    }
                    if (!familyMatches.isEmpty()) {
                        for (Elements.Item fm : familyMatches) {
                            if (!ip.onUse.isEmpty() && fm.onUse.isEmpty()) fm.onUse.addAll(ip.onUse);
                            if (!ip.onHitEntity.isEmpty() && fm.onHitEntity.isEmpty()) fm.onHitEntity.addAll(ip.onHitEntity);
                            if (!ip.onTickInHand.isEmpty() && fm.onTickInHand.isEmpty()) fm.onTickInHand.addAll(ip.onTickInHand);
                            if (ip.maxStack != null && fm.maxStack == 64) fm.maxStack = ip.maxStack;
                            if (ip.useCooldown != null && fm.useCooldown <= 0) fm.useCooldown = ip.useCooldown;
                            restoredProps++;
                        }
                        continue;
                    }
                }
                if (it == null && (ip.kind != null || !ip.onUse.isEmpty() || !ip.onHitEntity.isEmpty() || ip.nutrition != null)) {
                    String lk = "item." + p.meta.modId + "." + id;
                    String dn = tr(lk);
                    String tex = findTexFallback(p.meta.modId, id, "item");
                    if (!itemByName.isEmpty() && tex.isBlank() && dn.isEmpty()) continue;
                    it = new Elements.Item();
                    it.name = p.uniqueName(ElementType.ITEM, id);
                    it.displayName = dn.isEmpty() ? Ids.titleCase(id) : dn;
                    applyLang(it, "name", lk);
                    it.texture = tex;
                    p.elements.add(it);
                    itemByName.put(id, it);
                }
                if (it == null) continue;
                boolean touched = false;
                if (ip.kind != null && "basic".equals(it.kind)) { it.kind = ip.kind; touched = true; }
                if (ip.tier != null) { it.tier = ip.tier; touched = true; }
                if (ip.maxStack != null) { it.maxStack = ip.maxStack; touched = true; }
                if (ip.maxDamage != null) { it.maxDamage = ip.maxDamage; it.durability = ip.maxDamage; touched = true; }
                if (ip.enchantability != null) { it.enchantability = ip.enchantability; touched = true; }
                if (Boolean.TRUE.equals(ip.fireResistant)) { it.fireResistant = true; touched = true; }
                if (Boolean.TRUE.equals(ip.glint)) { it.glint = true; touched = true; }
                if (ip.rarity != null) { it.rarity = ip.rarity; touched = true; }
                if (ip.attackDamage != null) { it.attackDamage = ip.attackDamage; touched = true; }
                if (ip.attackSpeed != null) { it.attackSpeed = ip.attackSpeed; touched = true; }
                if (ip.useCooldown != null) { it.useCooldown = ip.useCooldown; touched = true; }
                if (ip.craftRemainder != null) { it.craftRemainder = ip.craftRemainder; touched = true; }
                if (ip.nutrition != null) { it.nutrition = ip.nutrition; it.kind = "food"; touched = true; }
                if (ip.saturation != null) { it.saturation = ip.saturation; it.kind = "food"; touched = true; }
                if (Boolean.TRUE.equals(ip.alwaysEdible)) { it.alwaysEdible = true; it.kind = "food"; touched = true; }
                if (Boolean.TRUE.equals(ip.fastFood)) { it.fastFood = true; it.kind = "food"; touched = true; }
                if (ip.foodEffect != null) {
                    it.foodEffect = ip.foodEffect;
                    if (ip.foodEffectSeconds != null) it.foodEffectSeconds = ip.foodEffectSeconds;
                    if (ip.foodEffectAmplifier != null) it.foodEffectAmplifier = ip.foodEffectAmplifier;
                    touched = true;
                }
                if (!ip.onUse.isEmpty() && it.onUse.isEmpty()) { it.onUse.addAll(ip.onUse); touched = true; }
                if (!ip.onHitEntity.isEmpty() && it.onHitEntity.isEmpty()) { it.onHitEntity.addAll(ip.onHitEntity); touched = true; }
                if (!ip.onTickInHand.isEmpty() && it.onTickInHand.isEmpty()) { it.onTickInHand.addAll(ip.onTickInHand); touched = true; }
                if (touched) restoredProps++;
            }

            for (var en : logic.blocks.entrySet()) {
                String id = en.getKey();
                ModLogicExtractor.BlockProps bp = en.getValue();
                Elements.Block b = blockByName.get(id) instanceof Elements.Block ex ? ex : null;
                if (b == null && (bp.hardness != null || !bp.onUse.isEmpty() || !bp.onStep.isEmpty())) {
                    String lk = "block." + p.meta.modId + "." + id;
                    String dn = tr(lk);
                    String tex = findTexFallback(p.meta.modId, id, "block");
                    if (!blockByName.isEmpty() && tex.isBlank() && dn.isEmpty()) continue;
                    b = new Elements.Block();
                    b.name = p.uniqueName(ElementType.BLOCK, id);
                    b.displayName = dn.isEmpty() ? Ids.titleCase(id) : dn;
                    applyLang(b, "name", lk);
                    b.texture = tex;
                    p.elements.add(b);
                    blockByName.put(id, b);
                }
                if (b == null) continue;
                boolean touched = false;
                if (bp.shape != null && "cube".equals(b.shape)) { b.shape = bp.shape; touched = true; }
                if (bp.hardness != null) { b.hardness = bp.hardness; touched = true; }
                if (bp.resistance != null) { b.resistance = bp.resistance; touched = true; }
                if (bp.light != null) { b.light = bp.light; touched = true; }
                if (bp.requiresTool != null) { b.requiresTool = bp.requiresTool; touched = true; }
                if (bp.hasCollision != null) { b.hasCollision = bp.hasCollision; touched = true; }
                if (Boolean.TRUE.equals(bp.falling)) { b.falling = true; touched = true; }
                if (bp.friction != null) { b.friction = bp.friction; touched = true; }
                if (bp.speedFactor != null) { b.speedFactor = bp.speedFactor; touched = true; }
                if (bp.jumpFactor != null) { b.jumpFactor = bp.jumpFactor; touched = true; }
                if (bp.sound != null) { b.sound = bp.sound; touched = true; }
                if (bp.instrument != null) { b.instrument = bp.instrument; touched = true; }
                if (bp.pushReaction != null) { b.pushReaction = bp.pushReaction; touched = true; }
                if (bp.xpMin != null) { b.xpMin = bp.xpMin; touched = true; }
                if (bp.xpMax != null) { b.xpMax = bp.xpMax; touched = true; }
                if (!bp.onUse.isEmpty() && b.onUse.isEmpty()) { b.onUse.addAll(bp.onUse); touched = true; }
                if (!bp.onStep.isEmpty() && b.onStep.isEmpty()) { b.onStep.addAll(bp.onStep); touched = true; }
                if (!bp.onRandomTick.isEmpty() && b.onRandomTick.isEmpty()) { b.onRandomTick.addAll(bp.onRandomTick); touched = true; }
                if (touched) restoredProps++;
            }

            for (var en : logic.armors.entrySet()) {
                ArmorMaterial am = getOrCreateArmorMat(en.getKey());
                ModLogicExtractor.ArmorMatProps ap = en.getValue();
                if (ap.durability > 0) am.durability = ap.durability;
                if (ap.helmet >= 0) am.helmet = ap.helmet;
                if (ap.chestplate >= 0) am.chestplate = ap.chestplate;
                if (ap.leggings >= 0) am.leggings = ap.leggings;
                if (ap.boots >= 0) am.boots = ap.boots;
                if (ap.toughness >= 0) am.toughness = ap.toughness;
                if (ap.knockbackResistance >= 0) am.knockbackResistance = ap.knockbackResistance;
                if (ap.equipSound != null) am.equipSound = ap.equipSound;
                restoredProps++;
            }

            for (var en : logic.entities.entrySet()) {
                String id = en.getKey();
                ModLogicExtractor.EntityProps ep = en.getValue();
                ModElements.CustomEntity ce = p.byName(ElementType.ENTITY, id) instanceof ModElements.CustomEntity ex ? ex : null;
                if (ce == null) {
                    ce = new ModElements.CustomEntity();
                    ce.name = p.uniqueName(ElementType.ENTITY, id);
                    String lk = "entity." + p.meta.modId + "." + id;
                    String dn = tr(lk);
                    ce.displayName = dn.isEmpty() ? Ids.titleCase(id) : dn;
                    applyLang(ce, "name", lk);
                    ce.texture = findEntityTex(p.meta.modId, id);
                    p.elements.add(ce);
                }
                if (ce.texture.isBlank()) ce.texture = findEntityTex(p.meta.modId, ce.name);
                if (ep.kind != null) ce.kind = ep.kind;
                if (ep.category != null) ce.category = ep.category;
                if (ep.width != null) ce.width = ep.width;
                if (ep.height != null) ce.height = ep.height;
                if (ep.health != null) ce.health = ep.health;
                if (ep.speed != null) ce.speed = ep.speed;
                if (ep.attackDamage != null) ce.attackDamage = ep.attackDamage;
                if (ep.followRange != null) ce.followRange = ep.followRange;
                if (ep.armor != null) ce.armor = ep.armor;
                if (ep.knockbackResistance != null) ce.knockbackResistance = ep.knockbackResistance;
                if (ep.xpReward != null) ce.xpReward = ep.xpReward;
                if (Boolean.TRUE.equals(ep.fireImmune)) ce.fireImmune = true;
                if (Boolean.TRUE.equals(ep.boss)) ce.boss = true;
                if (ep.bossColor != null) ce.bossColor = ep.bossColor;
                if (!ep.aiGoals.isEmpty()) {
                    ce.aiGoals.clear();
                    ce.aiGoals.addAll(ep.aiGoals);
                }
                if (!ep.onHurt.isEmpty() && ce.onHurt.isEmpty()) ce.onHurt.addAll(ep.onHurt);
                if (!ep.onDeath.isEmpty() && ce.onDeath.isEmpty()) ce.onDeath.addAll(ep.onDeath);
                if (!ep.onInteract.isEmpty() && ce.onInteract.isEmpty()) ce.onInteract.addAll(ep.onInteract);
                restoredProps++;
            }

            if (restoredProps > 0 || logic.extractedHooks > 0) {
                notes.add("Restored properties/capabilities for " + restoredProps + " elements and " + logic.extractedHooks + " behavioral procedure hooks from " + logic.analyzedClasses + " class(es) & sources.");
            }
        }

        private void preserveRawResource(String archivePath) {
            if (archivePath == null || archivePath.isBlank()) return;
            String clean = archivePath.startsWith("datapack/") ? archivePath.substring("datapack/".length()) : archivePath;
            if (clean.startsWith("assets/")) {
                String[] ap = clean.split("/", 4);
                if (ap.length == 4 && (ap[2].equals("recipes") || ap[2].equals("advancements") || ap[2].equals("loot_tables") || ap[2].equals("structures"))) {
                    clean = "data/" + ap[1] + "/" + ap[2] + "/" + ap[3];
                }
            }
            String targetPath = clean.startsWith("src/main/resources/") ? clean : "src/main/resources/" + clean;
            if (!preservedPaths.add(targetPath)) return;
            String txt = store.text(archivePath);
            if (txt == null) return;
            Elements.CodeFile cf = new Elements.CodeFile();
            cf.name = p.uniqueName(ElementType.CODE_FILE, Ids.toId(stripExt(clean.replace('/', '_'))));
            cf.target = "all";
            cf.path = targetPath;
            cf.content = txt;
            p.elements.add(cf);
        }

        private void readMixinList(JsonArray arr, String side, String pkg) {
            if (arr == null) return;
            for (JsonElement je : arr) {
                if (!je.isJsonPrimitive()) continue;
                String cls = je.getAsString();
                String mixinFqcn = (pkg == null || pkg.isBlank()) ? cls : pkg + "." + cls;
                String relClass = mixinFqcn.replace('.', '/') + ".class";
                ClassAnnotationReader.MixinInfo info = ClassAnnotationReader.inspectMixin(readRawClass(relClass));
                String target = "";
                boolean targetsStr = false;
                if (info != null) {
                    if (!info.targets().isEmpty()) {
                        target = info.targets().get(0);
                        targetsStr = target.contains("$");
                    } else if (!info.targetStrings().isEmpty()) {
                        target = info.targetStrings().get(0);
                        targetsStr = true;
                    }
                }
                boolean mcTarget = target.startsWith("net.minecraft.") || target.startsWith("com.mojang.");
                boolean obf = target.isBlank()
                        || target.startsWith("net.minecraft.class_")
                        || target.matches(".*\\.C_\\d+.*")
                        || (!target.contains(".") && target.length() <= 4);

                MixinSpec ms = new MixinSpec();
                ms.name = p.uniqueName(ElementType.MIXIN, Ids.toId(basename(cls.replace('.', '/'))));
                ms.side = side;
                ms.rawMode = true;
                ms.useTargetsString = targetsStr;
                if (mcTarget && !obf) {
                    ms.enabled = true;
                    ms.targetClass = target;
                    String methods = info.injectMethods().isEmpty() ? "" : String.join(", ", info.injectMethods());
                    String imp = target.replace('$', '.');
                    int dollar = target.indexOf('$');
                    String impLine = targetsStr ? "" : (dollar > 0 ? "import " + target.substring(0, dollar) + ";\n" : (target.contains(".") ? "import " + imp + ";\n" : ""));
                    String annot = targetsStr
                            ? "@Mixin(targets = \"" + target + "\")"
                            : "@Mixin(" + ms.simpleTarget() + ".class)";
                    if (info.isInterface()) {
                        ms.rawCode = "package {package_name}.mixin;\n\n"
                                + impLine
                                + "import org.spongepowered.asm.mixin.Mixin;\n\n"
                                + annot + "\n"
                                + "public interface {mixin_class} {\n"
                                + "    // Imported accessor/invoker mixin from " + mixinFqcn + "\n"
                                + "}\n";
                    } else {
                        if (!methods.isEmpty()) ms.methodTarget = info.injectMethods().get(0);
                        ms.rawCode = "package {package_name}.mixin;\n\n"
                                + impLine
                                + "import org.spongepowered.asm.mixin.Mixin;\n"
                                + "import org.spongepowered.asm.mixin.Unique;\n\n"
                                + annot + "\n"
                                + "public abstract class {mixin_class} {\n"
                                + "    // Imported mixin from " + mixinFqcn
                                + (methods.isEmpty() ? "" : " (detected hooks: " + methods + ")") + "\n"
                                + "    @Unique\n"
                                + "    private void modstudio$stub() {}\n"
                                + "}\n";
                    }
                } else {
                    ms.enabled = false;
                    ms.targetClass = target.isBlank() ? "net.minecraft.world.entity.player.Player" : target;
                    ms.rawCode = "// Disabled imported mixin stub (" + mixinFqcn + ")\n"
                            + "// Original bytecode target: " + (target.isBlank() ? "<unknown>" : target) + "\n"
                            + "// Enable after setting a resolvable class target or external mod dependency.\n";
                }
                p.elements.add(ms);
            }
        }

        private byte[] readRawClass(String relClass) {
            try {
                if (Files.isDirectory(source)) {
                    for (String prefix : List.of("", "build/classes/java/main/", "bin/main/")) {
                        Path p = source.resolve(prefix + relClass);
                        if (Files.isRegularFile(p)) return Files.readAllBytes(p);
                    }
                    return null;
                }
                try (ZipFile zf = new ZipFile(source.toFile())) {
                    ZipEntry e = zf.getEntry(relClass);
                    if (e != null) {
                        try (InputStream in = zf.getInputStream(e)) {
                            return in.readAllBytes();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        private void finalizeProject() {
            for (var le : langs.entrySet()) {
                Map<String, String> extras = new LinkedHashMap<>();
                for (var kv : le.getValue().entrySet()) {
                    if (!consumedLangKeys.contains(kv.getKey()) && !kv.getValue().isBlank()) {
                        extras.put(kv.getKey(), kv.getValue());
                    }
                }
                if (!extras.isEmpty()) p.translations.put(le.getKey(), extras);
            }
            if (p.meta.icon.isBlank() && !p.textures.isEmpty()) {
                p.meta.icon = p.textures.get(0).id;
            }
        }

        // -------------------------------------------------------------- helpers

        private String textField(JsonElement je, Element target, String locField, String def) {
            if (je == null || je.isJsonNull()) return def;
            if (je.isJsonPrimitive()) return je.getAsString();
            if (je.isJsonObject()) {
                JsonObject o = je.getAsJsonObject();
                if (hasStr(o, "translate")) {
                    String k = o.get("translate").getAsString();
                    if (target != null && locField != null) applyLang(target, locField, k);
                    String en = tr(k);
                    return en.isEmpty() ? def : en;
                }
                if (hasStr(o, "text")) return o.get("text").getAsString();
            }
            return def;
        }

        private void readNbtStructure(String name, byte[] bytes) {
            if (bytes == null || bytes.length < 4) return;
            try (var dis = new java.io.DataInputStream(new java.io.BufferedInputStream(
                    (bytes[0] == (byte) 0x1F && bytes[1] == (byte) 0x8B)
                            ? new java.util.zip.GZIPInputStream(new ByteArrayInputStream(bytes))
                            : new ByteArrayInputStream(bytes)))) {
                int rootTag = dis.readUnsignedByte();
                if (rootTag != 10) return;
                int rootLen = dis.readUnsignedShort();
                dis.skipBytes(rootLen);
                Map<String, Object> root = readNbtCompound(dis);
                @SuppressWarnings("unchecked") List<Object> size = (List<Object>) root.get("size");
                @SuppressWarnings("unchecked") List<Object> pal = (List<Object>) root.get("palette");
                @SuppressWarnings("unchecked") List<Object> blocks = (List<Object>) root.get("blocks");
                if (size == null || size.size() < 3 || pal == null || blocks == null) return;
                int sx = Math.max(1, Math.min(48, ((Number) size.get(0)).intValue()));
                int sy = Math.max(1, Math.min(48, ((Number) size.get(1)).intValue()));
                int sz = Math.max(1, Math.min(48, ((Number) size.get(2)).intValue()));
                Systems.Structure st = new Systems.Structure();
                st.name = p.uniqueName(ElementType.STRUCTURE, name);
                st.sx = sx; st.sy = sy; st.sz = sz;
                st.palette = new ArrayList<>();
                for (Object pe : pal) {
                    if (pe instanceof Map<?, ?> m && m.get("Name") instanceof String s) st.palette.add(s);
                    else st.palette.add("minecraft:air");
                }
                st.ensure();
                for (Object be : blocks) {
                    if (!(be instanceof Map<?, ?> bm)) continue;
                    if (!(bm.get("pos") instanceof List<?> pl) || pl.size() < 3) continue;
                    if (!(bm.get("state") instanceof Number sn)) continue;
                    int x = ((Number) pl.get(0)).intValue();
                    int y = ((Number) pl.get(1)).intValue();
                    int z = ((Number) pl.get(2)).intValue();
                    st.set(x, y, z, sn.intValue());
                }
                p.elements.add(st);
            } catch (Exception ignored) {
                notes.add("Binary NBT structure skipped (custom compressed format): " + name);
            }
        }

        private static Map<String, Object> readNbtCompound(java.io.DataInputStream dis) throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            while (true) {
                int tag = dis.readUnsignedByte();
                if (tag == 0) break;
                int len = dis.readUnsignedShort();
                byte[] nb = dis.readNBytes(len);
                String k = new String(nb, StandardCharsets.UTF_8);
                map.put(k, readNbtPayload(tag, dis));
            }
            return map;
        }

        private static Object readNbtPayload(int tag, java.io.DataInputStream dis) throws IOException {
            return switch (tag) {
                case 1 -> dis.readByte();
                case 2 -> dis.readShort();
                case 3 -> dis.readInt();
                case 4 -> dis.readLong();
                case 5 -> dis.readFloat();
                case 6 -> dis.readDouble();
                case 7 -> dis.readNBytes(dis.readInt());
                case 8 -> new String(dis.readNBytes(dis.readUnsignedShort()), StandardCharsets.UTF_8);
                case 9 -> {
                    int elemTag = dis.readUnsignedByte();
                    int count = dis.readInt();
                    List<Object> list = new ArrayList<>(Math.min(count, 1024));
                    for (int i = 0; i < count; i++) list.add(readNbtPayload(elemTag, dis));
                    yield list;
                }
                case 10 -> readNbtCompound(dis);
                case 11 -> {
                    int count = dis.readInt();
                    int[] arr = new int[count];
                    for (int i = 0; i < count; i++) arr[i] = dis.readInt();
                    yield arr;
                }
                case 12 -> {
                    int count = dis.readInt();
                    long[] arr = new long[count];
                    for (int i = 0; i < count; i++) arr[i] = dis.readLong();
                    yield arr;
                }
                default -> null;
            };
        }

        private static int optInt(JsonElement je, int def) {
            if (je == null || !je.isJsonPrimitive()) return def;
            try { return je.getAsInt(); } catch (Exception e) { return def; }
        }

        private static double optDouble(JsonElement je, double def) {
            if (je == null || !je.isJsonPrimitive()) return def;
            try { return je.getAsDouble(); } catch (Exception e) { return def; }
        }

        private static String optStr(JsonElement je, String def) {
            return (je != null && je.isJsonPrimitive()) ? je.getAsString() : def;
        }

        private boolean hasAnyPrefix(String prefix) {
            for (String k : store.files.keySet()) if (k.startsWith(prefix)) return true;
            return false;
        }
    }

    private static boolean hasStr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive();
    }

    private static String segment(String path, int idx) {
        String[] parts = path.split("/");
        return idx < parts.length ? parts[idx] : "";
    }

    private static String basename(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String sanitizeModId(String raw) {
        String id = Ids.toId(raw).replaceAll("[^a-z0-9_]", "");
        if (id.isEmpty() || !Character.isLetter(id.charAt(0))) id = "m" + id;
        while (id.length() < 2) id = id + "mod";
        return id;
    }

    private static String stripTomlComment(String s) {
        boolean inD = false, inS = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && inD && i + 1 < s.length()) { i++; continue; }
            if (c == '"' && !inS) inD = !inD;
            else if (c == '\'' && !inD) inS = !inS;
            else if (c == '#' && !inD && !inS) return s.substring(0, i);
        }
        return s;
    }

    private static String unquoteToml(String v) {
        if (v.length() >= 6 && ((v.startsWith("\"\"\"") && v.endsWith("\"\"\"")) || (v.startsWith("'''") && v.endsWith("'''")))) {
            return v.substring(3, v.length() - 3).trim();
        }
        if (v.equals("'''") || v.equals("\"\"\"")) return "";
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String unquoteYaml(String v) {
        if ((v.startsWith("'") && v.endsWith("'")) || (v.startsWith("\"") && v.endsWith("\""))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String hexColor(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return "#78A7FF";
        if (e.getAsJsonPrimitive().isNumber()) {
            return String.format("#%06X", e.getAsInt() & 0xFFFFFF);
        }
        return e.getAsString();
    }
}
