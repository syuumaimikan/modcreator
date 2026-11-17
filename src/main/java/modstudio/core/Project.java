package modstudio.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A ModStudio project. Serialized to a single .msproj JSON file. */
public final class Project {
    public int format = 1;
    public String id = Ids.uid();
    public Meta meta = new Meta();
    /** Toolchain version overrides: target id -> key -> value. */
    public Map<String, Map<String, String>> versions = new LinkedHashMap<>();
    public List<Element> elements = new ArrayList<>();
    public List<TextureAsset> textures = new ArrayList<>();
    /** Extra translation keys: game language -> key -> text. */
    public Map<String, Map<String, String>> translations = new LinkedHashMap<>();

    public static final class Meta {
        public String name = "My Mod";
        public String modId = "mymod";
        public String version = "1.0.0";
        public String description = "";
        public String authors = "";
        public String license = "MIT";
        public String website = "";
        public String packageName = "com.example.mymod";
        /** Version edited in the UI (vanilla lists, previews). Always contained in mcVersions. */
        public String mcVersion = "1.21.1";
        /** All Minecraft versions the project is exported for. */
        public List<String> mcVersions = new ArrayList<>();
        public List<String> targets = new ArrayList<>(List.of("neoforge", "fabric"));
        public List<String> languages = new ArrayList<>();
        public String icon = "";
    }

    public McVersion mc() {
        return McVersion.of(meta.mcVersion);
    }

    /** Fixes up version settings after loading or editing: known ids only, sorted, primary included. */
    public void normalizeVersions() {
        List<String> ids = new ArrayList<>();
        for (McVersion v : McVersion.values()) {
            if (meta.mcVersions.contains(v.id) || v.id.equals(meta.mcVersion)) ids.add(v.id);
        }
        if (ids.isEmpty()) ids.add(McVersion.V1_21_1.id);
        if (McVersion.orNull(meta.mcVersion) == null || !ids.contains(meta.mcVersion)) meta.mcVersion = ids.get(ids.size() - 1);
        meta.mcVersions = ids;
    }

    /** Selected Minecraft versions, oldest first. */
    public List<McVersion> mcVersions() {
        List<McVersion> r = new ArrayList<>();
        for (McVersion v : McVersion.values()) {
            if (meta.mcVersions.contains(v.id) || v.id.equals(meta.mcVersion)) r.add(v);
        }
        if (r.isEmpty()) r.add(mc());
        return r;
    }

    /** Selected targets that can be built for at least one selected version. */
    public List<Target> targets() {
        List<Target> r = new ArrayList<>();
        for (String s : meta.targets) {
            Target t = Target.of(s);
            if (t == null) continue;
            for (McVersion v : mcVersions()) {
                if (t.supports(v)) {
                    r.add(t);
                    break;
                }
            }
        }
        return r;
    }

    /** Every output to generate: each selected target for each selected version it supports. */
    public List<Build> builds() {
        List<Build> r = new ArrayList<>();
        for (Target t : targets()) {
            if (!t.versioned()) {
                r.add(new Build(t, mc()));
                continue;
            }
            for (McVersion v : mcVersions()) if (t.supports(v)) r.add(new Build(t, v));
        }
        return r;
    }

    public boolean multiVersion() {
        return mcVersions().size() > 1;
    }

    public boolean has(Target t) {
        return targets().contains(t);
    }

    @SuppressWarnings("unchecked")
    public <T extends Element> List<T> all(Class<T> cls) {
        List<T> r = new ArrayList<>();
        for (Element e : elements) if (cls.isInstance(e)) r.add((T) e);
        return r;
    }

    public List<Element> ofType(ElementType t) {
        List<Element> r = new ArrayList<>();
        for (Element e : elements) if (e.type() == t) r.add(e);
        return r;
    }

    public Element byUid(String uid) {
        for (Element e : elements) if (e.uid.equals(uid)) return e;
        return null;
    }

    public Element byName(ElementType t, String name) {
        for (Element e : elements) if (e.type() == t && e.name.equals(name)) return e;
        return null;
    }

    /** Finds a project item or block by its full id (modid:name). */
    public Element itemLike(String id) {
        if (id == null || !id.startsWith(meta.modId + ":")) return null;
        String n = id.substring(meta.modId.length() + 1);
        Element e = byName(ElementType.ITEM, n);
        return e != null ? e : byName(ElementType.BLOCK, n);
    }

    /** Project model by element name, or null. */
    public Elements.Model model(String name) {
        if (name == null || name.isBlank()) return null;
        return all(Elements.Model.class).stream().filter(m -> m.name.equals(name)).findFirst().orElse(null);
    }

    public TextureAsset texture(String id) {
        if (id == null || id.isEmpty()) return null;
        for (TextureAsset t : textures) if (t.id.equals(id)) return t;
        return null;
    }

    public TextureAsset textureByName(String name) {
        for (TextureAsset t : textures) if (t.name.equals(name)) return t;
        return null;
    }

    /** Key of the toolchain override map for a target and version. */
    public static String versionKey(Target t, McVersion v) {
        return t.versioned() ? t.id + "@" + v.id : t.id;
    }

    public String version(Target t, McVersion mc, String key) {
        Map<String, String> m = versions.get(versionKey(t, mc));
        if (m != null) {
            String v = m.get(key);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return Versions.defaults(t, mc).getOrDefault(key, "");
    }

    /** A unique, valid element name derived from a base. */
    public String uniqueName(ElementType type, String base) {
        String b = Ids.toId(base);
        if (b.isEmpty()) b = type.id;
        String n = b;
        int i = 2;
        while (nameTaken(type, n)) n = b + "_" + i++;
        return n;
    }

    public boolean nameTaken(ElementType type, String n) {
        for (Element e : elements) {
            if (e.type().registryGroup().equals(type.registryGroup()) && e.name.equals(n)) return true;
        }
        return false;
    }

    public String uniqueTextureName(String base) {
        String b = Ids.toId(base);
        if (b.isEmpty()) b = "texture";
        String n = b;
        int i = 2;
        while (textureByName(n) != null) n = b + "_" + i++;
        return n;
    }
}
