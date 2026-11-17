package modstudio.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import modstudio.core.Elements.Cube;
import modstudio.core.Elements.Face;

import java.util.*;
import java.util.function.Function;

/**
 * Turns block ids (with an optional [state]) into textured quads for previews: the project's own blocks from their
 * shape or model, vanilla blocks from the block states and models of a client jar on this computer (variants,
 * multipart, parent models, element and variant rotation), and anything else as a coloured cube.
 */
public final class BlockModels {
    /** A textured quad in block units (0..16): four corners (x y z) with their uv (0..1), in drawing order. */
    public record Quad(double[] pos, double[] uv, String texture, double shade, int tint, String cull) {}

    /**
     * A block's look. full: it fills its cell, so neighbours hide the faces against it; opaque: those faces are hidden.
     * icon: a texture for flat views. Textures are project texture ids, vanilla ids (minecraft:block/stone) or
     * "color:<block id>" for a plain colour.
     */
    public record Shape(List<Quad> quads, boolean full, boolean opaque, String icon) {
        public static final Shape EMPTY = new Shape(List.of(), false, false, null);
    }

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("facing", "north"), Map.entry("axis", "y"), Map.entry("half", "bottom"), Map.entry("type", "bottom"),
            Map.entry("lit", "false"), Map.entry("powered", "false"), Map.entry("open", "false"), Map.entry("waterlogged", "false"),
            Map.entry("face", "floor"), Map.entry("shape", "straight"), Map.entry("hinge", "left"), Map.entry("part", "foot"),
            Map.entry("attachment", "floor"), Map.entry("hanging", "false"), Map.entry("level", "0"), Map.entry("age", "7"));

    private static final Set<String> SEE_THROUGH = Set.of("glass", "leaves", "ice", "slime", "honey_block", "barrier", "spawner",
            "beacon", "water", "lava", "mangrove_roots", "scaffolding", "cobweb", "frosted_ice", "copper_grate");

    private final Project project;
    private final VanillaAssets va;
    private final Map<String, Shape> cache = new HashMap<>();

    public BlockModels(Project project, VanillaAssets va) {
        this.project = project;
        this.va = va;
    }

    public boolean hasVanilla() {
        return va != null;
    }

    public Shape shape(String blockId) {
        String id = blockId == null ? "" : blockId.trim();
        return cache.computeIfAbsent(id, this::build);
    }

    private Shape build(String id) {
        if (id.isEmpty()) return Shape.EMPTY;
        String full = Ids.full(id);
        Map<String, String> props = new HashMap<>();
        int b = full.indexOf('[');
        String block = full;
        if (b >= 0) {
            block = full.substring(0, b);
            String inner = full.substring(b + 1).replace("]", "");
            for (String kv : inner.split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
        String ns = Ids.ns(block), path = Ids.path(block);
        if (ns.equals("minecraft") && (path.equals("air") || path.equals("cave_air") || path.equals("void_air"))) return Shape.EMPTY;
        if (ns.equals(project.meta.modId)) {
            Shape s = projectBlock(path);
            if (s != null) return s;
        }
        if (va != null) {
            Shape s = vanilla(ns, path, props);
            if (s != null) return s;
        }
        return colored(block);
    }

    // ------------------------------------------------------------------ project blocks

    private Shape projectBlock(String name) {
        Elements.Block blk = null;
        Elements.Model model = null;
        for (Element e : project.elements) {
            if (e instanceof Elements.Block x && x.name.equals(name)) blk = x;
            if (e instanceof Elements.Model x && x.name.equals(name) && model == null) model = x;
        }
        if (blk != null) {
            switch (blk.shape) {
                case "cross", "crop" -> {
                    String tex = blk.isCrop() && !blk.stageTextures.isEmpty() ? blk.stageTextures.get(blk.stageTextures.size() - 1) : blk.face("side");
                    return new Shape(quads(cross(), k -> tex, 0, 0, false, -1), false, false, tex);
                }
                case "model" -> {
                    Elements.Model m = null;
                    for (Element e : project.elements) if (e instanceof Elements.Model x && x.name.equals(blk.model)) m = x;
                    if (m != null) return fromModel(m);
                }
                default -> {
                }
            }
            Cube c = Models.cube("block", 0, 0, 0, 16, 16, 16);
            for (String d : Cube.FACES) {
                c.face(d).texture = d.equals("up") ? blk.face("top") : d.equals("down") ? blk.face("bottom") : blk.face("side");
                c.face(d).cullface = d;
            }
            return new Shape(quads(List.of(c), k -> k, 0, 0, false, -1), true, !blk.transparent, blk.face("top"));
        }
        if (model != null) return fromModel(model);
        return null;
    }

    private Shape fromModel(Elements.Model m) {
        String first = m.textures.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
        Function<String, String> tex = k -> {
            String v = m.textures.get(k == null || k.isBlank() ? "0" : k);
            return v == null || v.isBlank() ? first : v;
        };
        List<Cube> cubes = m.cubes;
        boolean full = cubes.size() == 1 && isFullCube(cubes.get(0));
        String icon = cubes.isEmpty() ? first : tex.apply(cubes.get(0).face("up").texture);
        return new Shape(quads(cubes, tex, 0, 0, false, -1), full, full, icon);
    }

    /** Shape of a list of cuboids whose face textures are resolved by tex (model editor, thumbnails). */
    public static Shape of(List<Cube> cubes, Function<String, String> tex) {
        boolean full = cubes.size() == 1 && isFullCube(cubes.get(0));
        return new Shape(quads(cubes, tex, 0, 0, false, -1), full, full, null);
    }

    private static List<Cube> cross() {
        Cube a = Models.cube("a", 0.8, 0, 8, 15.2, 16, 8);
        Cube b = Models.cube("b", 8, 0, 0.8, 8, 16, 15.2);
        for (Cube c : List.of(a, b)) {
            c.rotation = 45;
            c.axis = "y";
            c.origin = new double[]{8, 8, 8};
            c.shade = false;
            for (String d : Cube.FACES) c.face(d).texture = "x";
        }
        for (String d : List.of("east", "west", "up", "down")) a.face(d).enabled = false;
        for (String d : List.of("north", "south", "up", "down")) b.face(d).enabled = false;
        return List.of(a, b);
    }

    private static boolean isFullCube(Cube c) {
        return c.rotation == 0 && c.from[0] == 0 && c.from[1] == 0 && c.from[2] == 0 && c.to[0] == 16 && c.to[1] == 16 && c.to[2] == 16;
    }

    private static Shape colored(String block) {
        Cube c = Models.cube("block", 0, 0, 0, 16, 16, 16);
        for (String d : Cube.FACES) {
            c.face(d).texture = "x";
            c.face(d).cullface = d;
        }
        String tex = "color:" + block;
        return new Shape(quads(List.of(c), k -> tex, 0, 0, false, -1), true, true, tex);
    }

    // ------------------------------------------------------------------ vanilla blocks

    private record Variant(String model, int x, int y, boolean uvlock) {}

    private Shape vanilla(String ns, String path, Map<String, String> props) {
        JsonObject bs = va.json("assets/" + ns + "/blockstates/" + path + ".json");
        if (bs == null) return null;
        List<Variant> variants = new ArrayList<>();
        if (bs.has("variants")) {
            JsonObject v = bs.getAsJsonObject("variants");
            JsonElement chosen = null;
            for (int pass = 0; pass < 2 && chosen == null; pass++) {
                for (var e : v.entrySet()) {
                    if (matches(e.getKey(), props, pass == 0)) {
                        chosen = e.getValue();
                        break;
                    }
                }
            }
            if (chosen == null && !v.entrySet().isEmpty()) chosen = v.entrySet().iterator().next().getValue();
            if (chosen != null) variants.add(variant(chosen));
        } else if (bs.has("multipart")) {
            for (JsonElement part : bs.getAsJsonArray("multipart")) {
                JsonObject po = part.getAsJsonObject();
                if (!po.has("when") || when(po.get("when"), props)) variants.add(variant(po.get("apply")));
            }
        }
        List<Quad> quads = new ArrayList<>();
        boolean full = false;
        String icon = null, particle = null;
        int tint = tintColor(path);
        for (Variant v : variants) {
            if (v == null) continue;
            Map<String, String> textures = new HashMap<>();
            List<Cube> cubes = elements(v.model(), textures);
            if (particle == null) particle = texture("#particle", textures);
            if (cubes.isEmpty()) continue;
            quads.addAll(quads(cubes, k -> texture(k, textures), v.x(), v.y(), v.uvlock(), tint));
            if (variants.size() == 1 && cubes.size() == 1 && isFullCube(cubes.get(0))) full = true;
            if (icon == null) {
                for (Cube c : cubes) {
                    Face up = c.faces.get("up");
                    if (up != null && up.enabled) {
                        icon = texture(up.texture, textures);
                        break;
                    }
                }
            }
        }
        if (quads.isEmpty()) {
            // block entities and fluids (chests, water) have no elements: a cube of the particle texture
            if (particle == null) return null;
            Cube c = Models.cube("block", 0, 0, 0, 16, path.equals("water") || path.equals("lava") ? 14 : 16, 16);
            for (String d : Cube.FACES) {
                c.face(d).texture = "x";
                c.face(d).cullface = d;
                c.face(d).tintIndex = path.equals("water") ? 0 : -1;
            }
            String tex = particle;
            return new Shape(quads(List.of(c), k -> tex, 0, 0, false, tint), false, false, tex);
        }
        boolean opaque = full && SEE_THROUGH.stream().noneMatch(path::contains);
        return new Shape(quads, full, opaque, icon != null ? icon : particle);
    }

    private static Variant variant(JsonElement e) {
        if (e == null) return null;
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            if (a.isEmpty()) return null;
            e = a.get(0);
        }
        if (!e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        if (!o.has("model")) return null;
        return new Variant(o.get("model").getAsString(), o.has("x") ? o.get("x").getAsInt() : 0, o.has("y") ? o.get("y").getAsInt() : 0,
                o.has("uvlock") && o.get("uvlock").getAsBoolean());
    }

    /** Whether a variant key such as "facing=north,lit=false" fits the given state (missing values use common defaults). */
    private static boolean matches(String key, Map<String, String> props, boolean useDefaults) {
        if (key.isBlank()) return true;
        for (String kv : key.split(",")) {
            int eq = kv.indexOf('=');
            if (eq < 0) continue;
            String k = kv.substring(0, eq), v = kv.substring(eq + 1);
            String have = props.get(k);
            if (have == null && useDefaults) have = DEFAULTS.get(k);
            if (have != null && !have.equals(v)) return false;
        }
        return true;
    }

    private static boolean when(JsonElement w, Map<String, String> props) {
        if (!w.isJsonObject()) return false;
        JsonObject o = w.getAsJsonObject();
        if (o.has("OR")) {
            for (JsonElement e : o.getAsJsonArray("OR")) if (when(e, props)) return true;
            return false;
        }
        if (o.has("AND")) {
            for (JsonElement e : o.getAsJsonArray("AND")) if (!when(e, props)) return false;
            return true;
        }
        for (var e : o.entrySet()) {
            String have = props.getOrDefault(e.getKey(), DEFAULTS.get(e.getKey()));
            if (have == null) return false;
            boolean any = false;
            for (String v : e.getValue().getAsString().split("\\|")) if (v.equals(have)) any = true;
            if (!any) return false;
        }
        return true;
    }

    /** Elements of a model after its parents, with the merged texture variables filled into the map. */
    private List<Cube> elements(String modelId, Map<String, String> textures) {
        List<JsonObject> chain = new ArrayList<>();
        String id = modelId;
        for (int depth = 0; depth < 16 && id != null; depth++) {
            if (id.startsWith("builtin/") || id.startsWith("minecraft:builtin/")) break;
            JsonObject m = va.model(id);
            if (m == null) break;
            chain.add(m);
            id = m.has("parent") ? m.get("parent").getAsString() : null;
        }
        JsonArray elements = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            JsonObject m = chain.get(i);
            if (m.has("textures")) for (var e : m.getAsJsonObject("textures").entrySet()) {
                if (e.getValue().isJsonPrimitive()) textures.put(e.getKey(), e.getValue().getAsString());
            }
        }
        for (JsonObject m : chain) {
            if (m.has("elements")) {
                elements = m.getAsJsonArray("elements");
                break;
            }
        }
        List<Cube> r = new ArrayList<>();
        if (elements == null) return r;
        for (JsonElement el : elements) {
            JsonObject o = el.getAsJsonObject();
            Cube c = new Cube();
            c.from = vec(o.getAsJsonArray("from"));
            c.to = vec(o.getAsJsonArray("to"));
            c.origin = new double[]{8, 8, 8};
            if (o.has("rotation")) {
                JsonObject rot = o.getAsJsonObject("rotation");
                if (rot.has("origin")) c.origin = vec(rot.getAsJsonArray("origin"));
                if (rot.has("axis")) c.axis = rot.get("axis").getAsString();
                if (rot.has("angle")) c.rotation = rot.get("angle").getAsDouble();
            }
            if (o.has("shade")) c.shade = o.get("shade").getAsBoolean();
            JsonObject faces = o.has("faces") ? o.getAsJsonObject("faces") : new JsonObject();
            for (String d : Cube.FACES) {
                Face f = c.face(d);
                JsonObject fo = faces.has(d) ? faces.getAsJsonObject(d) : null;
                f.enabled = fo != null;
                if (fo == null) continue;
                f.texture = fo.has("texture") ? fo.get("texture").getAsString() : "#missing";
                if (fo.has("uv")) f.uv = vec(fo.getAsJsonArray("uv"));
                if (fo.has("rotation")) f.rotation = fo.get("rotation").getAsInt();
                if (fo.has("cullface")) f.cullface = fo.get("cullface").getAsString();
                if (fo.has("tintindex")) f.tintIndex = fo.get("tintindex").getAsInt();
            }
            r.add(c);
        }
        return r;
    }

    private static double[] vec(JsonArray a) {
        double[] r = new double[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsDouble();
        return r;
    }

    /** Follows #variable references to a texture id (minecraft:block/stone), or null. */
    private static String texture(String ref, Map<String, String> textures) {
        String r = ref;
        for (int i = 0; i < 12 && r != null && r.startsWith("#"); i++) r = textures.get(r.substring(1));
        if (r == null || r.startsWith("#")) return null;
        return Ids.full(r);
    }

    /** Colour for faces with a tint index: grass, foliage and water take the biome colour in game. */
    static int tintColor(String path) {
        if (path.contains("birch_leaves")) return 0x80a755;
        if (path.contains("spruce_leaves")) return 0x619961;
        if (path.contains("leaves") || path.equals("vine")) return 0x77ab2f;
        if (path.contains("water") || path.equals("bubble_column")) return 0x3f76e4;
        if (path.equals("redstone_wire")) return 0xc81e1e;
        if (path.equals("lily_pad")) return 0x208030;
        if (path.contains("stem")) return 0xa2a828;
        return 0x91bd59;
    }

    // ------------------------------------------------------------------ geometry

    /**
     * Quads of cuboids: element rotation around its origin, then the variant rotation (x, then y, in steps of 90
     * degrees around the block centre). tint is the colour for faces with a tint index, or -1.
     */
    static List<Quad> quads(List<Cube> cubes, Function<String, String> tex, int rx, int ry, boolean uvlock, int tint) {
        List<Quad> r = new ArrayList<>();
        int qx = Math.floorMod(rx / 90, 4), qy = Math.floorMod(ry / 90, 4);
        for (Cube c : cubes) {
            double x1 = Math.min(c.from[0], c.to[0]), y1 = Math.min(c.from[1], c.to[1]), z1 = Math.min(c.from[2], c.to[2]);
            double x2 = Math.max(c.from[0], c.to[0]), y2 = Math.max(c.from[1], c.to[1]), z2 = Math.max(c.from[2], c.to[2]);
            for (String dir : Cube.FACES) {
                Face f = c.faces.get(dir);
                if (f == null || !f.enabled) continue;
                String texture = tex.apply(f.texture);
                if (texture == null || texture.isBlank()) continue;
                double[][] q = switch (dir) {
                    case "north" -> new double[][]{{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}};
                    case "south" -> new double[][]{{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}};
                    case "west" -> new double[][]{{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}};
                    case "east" -> new double[][]{{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}};
                    case "up" -> new double[][]{{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
                    default -> new double[][]{{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}};
                };
                double[] normal = normal(dir);
                if (c.rotation != 0) {
                    for (double[] p : q) rotate(p, c.axis, c.rotation, c.origin);
                    rotate(normal, c.axis, c.rotation, new double[]{0, 0, 0});
                }
                for (double[] p : q) turn(p, qx, qy, 8);
                turn(normal, qx, qy, 0);
                String finalDir = direction(normal);
                double[] uv = Models.uv(c, dir);
                int rot = Math.floorMod(f.rotation / 90, 4);
                if (uvlock && (qx != 0 || qy != 0)) {
                    uv = lockedUv(q, finalDir);
                    rot = 0;
                }
                double[][] uvc = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
                double[] pos = new double[12], tc = new double[8];
                for (int i = 0; i < 4; i++) {
                    pos[i * 3] = q[i][0];
                    pos[i * 3 + 1] = q[i][1];
                    pos[i * 3 + 2] = q[i][2];
                    double[] t = uvc[(i - rot + 4) % 4];
                    tc[i * 2] = t[0] / 16;
                    tc[i * 2 + 1] = t[1] / 16;
                }
                double shade = !c.shade ? 1.0 : switch (finalDir) {
                    case "up" -> 1.0;
                    case "down" -> 0.55;
                    case "north", "south" -> 0.82;
                    default -> 0.66;
                };
                String cull = f.cullface == null || f.cullface.isBlank() ? "" : turnDirection(f.cullface, qx, qy);
                r.add(new Quad(pos, tc, texture, shade, f.tintIndex >= 0 ? tint : -1, cull));
            }
        }
        return r;
    }

    private static double[] normal(String dir) {
        return switch (dir) {
            case "north" -> new double[]{0, 0, -1};
            case "south" -> new double[]{0, 0, 1};
            case "west" -> new double[]{-1, 0, 0};
            case "east" -> new double[]{1, 0, 0};
            case "up" -> new double[]{0, 1, 0};
            default -> new double[]{0, -1, 0};
        };
    }

    private static String direction(double[] n) {
        double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ay >= ax && ay >= az) return n[1] > 0 ? "up" : "down";
        if (ax >= az) return n[0] > 0 ? "east" : "west";
        return n[2] > 0 ? "south" : "north";
    }

    private static String turnDirection(String dir, int qx, int qy) {
        double[] n = normal(dir);
        turn(n, qx, qy, 0);
        return direction(n);
    }

    /** Right-handed rotation around an axis through the origin point, as element rotations are defined. */
    private static void rotate(double[] p, String axis, double degrees, double[] o) {
        double a = Math.toRadians(degrees), cos = Math.cos(a), sin = Math.sin(a);
        double x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
        switch (axis) {
            case "x" -> {
                p[1] = o[1] + y * cos - z * sin;
                p[2] = o[2] + y * sin + z * cos;
            }
            case "z" -> {
                p[0] = o[0] + x * cos - y * sin;
                p[1] = o[1] + x * sin + y * cos;
            }
            default -> {
                p[0] = o[0] + x * cos + z * sin;
                p[2] = o[2] - x * sin + z * cos;
            }
        }
    }

    /** Variant rotation: x (up turns to north per step), then y (north turns to east per step), around c. */
    private static void turn(double[] p, int qx, int qy, double c) {
        for (int i = 0; i < qx; i++) {
            double y = p[1] - c, z = p[2] - c;
            p[1] = c + z;
            p[2] = c - y;
        }
        for (int i = 0; i < qy; i++) {
            double x = p[0] - c, z = p[2] - c;
            p[0] = c - z;
            p[2] = c + x;
        }
    }

    /** uv taken from where the face is after turning, so the texture keeps its world orientation (uvlock). */
    private static double[] lockedUv(double[][] q, String dir) {
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : q) for (int i = 0; i < 3; i++) {
            lo[i] = Math.min(lo[i], p[i]);
            hi[i] = Math.max(hi[i], p[i]);
        }
        Cube box = Models.cube("lock", lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
        return Models.autoUv(box, dir);
    }
}
