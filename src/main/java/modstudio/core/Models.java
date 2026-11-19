package modstudio.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import modstudio.core.Elements.Cube;
import modstudio.core.Elements.Display;
import modstudio.core.Elements.Face;
import modstudio.core.Elements.Model;

import java.util.*;
import java.util.function.Function;

/** Templates, UV rules, import and export for cuboid models. */
public final class Models {
    private Models() {}

    public static final List<Double> ANGLES = List.of(-45.0, -22.5, 0.0, 22.5, 45.0);
    public static final List<String> DISPLAY_CONTEXTS = List.of("gui", "ground", "fixed", "head", "firstperson_righthand", "thirdperson_righthand");
    public static final List<String> TEMPLATES = List.of("block", "slab", "carpet", "post", "pillar_small", "table", "lamp", "pedestal",
            "stairs", "fence_post", "chair", "crystal", "rod", "armor_helmet", "armor_chestplate", "armor_leggings", "armor_boots", "quadruped", "biped", "empty");

    // ------------------------------------------------------------------ geometry

    /** Default UV of a face: the projection of the cube onto that face, like Minecraft does. */
    public static double[] autoUv(Cube c, String face) {
        double x1 = c.from[0], y1 = c.from[1], z1 = c.from[2], x2 = c.to[0], y2 = c.to[1], z2 = c.to[2];
        double[] r = switch (face) {
            case "north" -> new double[]{16 - x2, 16 - y2, 16 - x1, 16 - y1};
            case "south" -> new double[]{x1, 16 - y2, x2, 16 - y1};
            case "west" -> new double[]{z1, 16 - y2, z2, 16 - y1};
            case "east" -> new double[]{16 - z2, 16 - y2, 16 - z1, 16 - y1};
            case "up" -> new double[]{x1, z1, x2, z2};
            default -> new double[]{x1, 16 - z2, x2, 16 - z1};
        };
        for (int i = 0; i < 4; i++) r[i] = clamp(r[i], 0, 16);
        return r;
    }

    public static double[] uv(Cube c, String face) {
        Face f = c.face(face);
        return f.uv != null && f.uv.length == 4 ? f.uv : autoUv(c, face);
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static Cube cube(String name, double x1, double y1, double z1, double x2, double y2, double z2) {
        Cube c = new Cube();
        c.name = name;
        c.from = new double[]{x1, y1, z1};
        c.to = new double[]{x2, y2, z2};
        c.origin = new double[]{(x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2};
        return c;
    }

    public static Cube boneCube(String name, String bone, double x1, double y1, double z1, double x2, double y2, double z2) {
        Cube c = cube(name, x1, y1, z1, x2, y2, z2);
        c.bone = bone;
        return c;
    }

    private static void addBone(Model m, String name, String parent, double px, double py, double pz) {
        Elements.Bone b = new Elements.Bone();
        b.name = name;
        b.parent = parent;
        b.pivot = new double[]{px, py, pz};
        m.bones.add(b);
    }

    /** Returns a mirrored clone of a cube across center 8 on axis (0=X, 1=Y, 2=Z). */
    public static Cube mirrorCube(Cube src, int axis) {
        Cube c = copy(src);
        double a = 16.0 - c.to[axis], b = 16.0 - c.from[axis];
        c.from[axis] = Math.min(a, b);
        c.to[axis] = Math.max(a, b);
        c.origin[axis] = 16.0 - c.origin[axis];
        String rotAxis = c.axis == null ? "y" : c.axis;
        boolean flipRot = (axis == 0 && !rotAxis.equals("x"))
                || (axis == 1 && !rotAxis.equals("y"))
                || (axis == 2 && !rotAxis.equals("z"));
        if (flipRot && c.rotation != 0) c.rotation = -c.rotation;
        String pairA = axis == 0 ? "west" : axis == 1 ? "down" : "north";
        String pairB = axis == 0 ? "east" : axis == 1 ? "up" : "south";
        Face fa = c.faces.get(pairA), fb = c.faces.get(pairB);
        if (fa != null && fb != null) {
            c.faces.put(pairA, fb);
            c.faces.put(pairB, fa);
        }
        if (c.name != null && !c.name.endsWith("_mirror")) c.name = c.name + "_mirror";
        if (c.bone != null) {
            if (c.bone.endsWith("_l")) c.bone = c.bone.substring(0, c.bone.length() - 2) + "_r";
            else if (c.bone.endsWith("_r")) c.bone = c.bone.substring(0, c.bone.length() - 2) + "_l";
            else if (c.bone.startsWith("left_")) c.bone = "right_" + c.bone.substring(5);
            else if (c.bone.startsWith("right_")) c.bone = "left_" + c.bone.substring(6);
        }
        return c;
    }

    public static Cube copy(Cube c) {
        Cube n = new Cube();
        n.name = c.name;
        n.bone = c.bone;
        n.from = c.from.clone();
        n.to = c.to.clone();
        n.rotation = c.rotation;
        n.axis = c.axis;
        n.origin = c.origin.clone();
        n.rescale = c.rescale;
        n.shade = c.shade;
        n.inflate = c.inflate;
        n.faces = new LinkedHashMap<>();
        for (var e : c.faces.entrySet()) {
            Face f = e.getValue(), g = new Face();
            g.enabled = f.enabled;
            g.texture = f.texture;
            g.uv = f.uv == null ? null : f.uv.clone();
            g.rotation = f.rotation;
            g.cullface = f.cullface;
            g.tintIndex = f.tintIndex;
            n.faces.put(e.getKey(), g);
        }
        return n;
    }

    /** Fills a model from a named template. Existing cubes are replaced. */
    public static void applyTemplate(Model m, String template) {
        m.cubes.clear();
        m.bones.clear();
        m.animations.clear();
        switch (template) {
            case "biped" -> Rig.biped(m);
            case "slab" -> m.cubes.add(cube("slab", 0, 0, 0, 16, 8, 16));
            case "carpet" -> m.cubes.add(cube("carpet", 0, 0, 0, 16, 1, 16));
            case "post" -> m.cubes.add(cube("post", 4, 0, 4, 12, 16, 12));
            case "pillar_small" -> {
                m.cubes.add(cube("base", 2, 0, 2, 14, 2, 14));
                m.cubes.add(cube("shaft", 4, 2, 4, 12, 14, 12));
                m.cubes.add(cube("top", 2, 14, 2, 14, 16, 14));
            }
            case "table" -> {
                m.cubes.add(cube("top", 0, 13, 0, 16, 16, 16));
                m.cubes.add(cube("leg1", 1, 0, 1, 3, 13, 3));
                m.cubes.add(cube("leg2", 13, 0, 1, 15, 13, 3));
                m.cubes.add(cube("leg3", 1, 0, 13, 3, 13, 15));
                m.cubes.add(cube("leg4", 13, 0, 13, 15, 13, 15));
            }
            case "lamp" -> {
                m.cubes.add(cube("base", 4, 0, 4, 12, 2, 12));
                m.cubes.add(cube("stem", 7, 2, 7, 9, 9, 9));
                m.cubes.add(cube("shade", 3, 9, 3, 13, 16, 13));
            }
            case "pedestal" -> {
                m.cubes.add(cube("base", 1, 0, 1, 15, 3, 15));
                m.cubes.add(cube("column", 4, 3, 4, 12, 12, 12));
                m.cubes.add(cube("plate", 2, 12, 2, 14, 15, 14));
            }
            case "stairs" -> {
                m.cubes.add(cube("bottom", 0, 0, 0, 16, 8, 16));
                m.cubes.add(cube("step", 0, 8, 8, 16, 16, 16));
            }
            case "fence_post" -> {
                m.cubes.add(cube("post", 6, 0, 6, 10, 16, 10));
                m.cubes.add(cube("rail_top", 7, 12, 0, 9, 15, 16));
                m.cubes.add(cube("rail_bottom", 7, 6, 0, 9, 9, 16));
            }
            case "chair" -> {
                m.cubes.add(cube("seat", 2, 7, 2, 14, 9, 14));
                m.cubes.add(cube("back", 2, 9, 12, 14, 20, 14));
                m.cubes.add(cube("leg1", 2, 0, 2, 4, 7, 4));
                m.cubes.add(cube("leg2", 12, 0, 2, 14, 7, 4));
                m.cubes.add(cube("leg3", 2, 0, 12, 4, 7, 14));
                m.cubes.add(cube("leg4", 12, 0, 12, 14, 7, 14));
            }
            case "crystal" -> {
                m.cubes.add(cube("base", 5, 0, 5, 11, 2, 11));
                Cube a = cube("crystal_a", 6, 1, 6, 10, 13, 10);
                a.rotation = 22.5;
                a.axis = "z";
                a.origin = new double[]{8, 1, 8};
                m.cubes.add(a);
                Cube b = cube("crystal_b", 6.5, 1, 6.5, 9.5, 9, 9.5);
                b.rotation = -22.5;
                b.axis = "x";
                b.origin = new double[]{8, 1, 8};
                m.cubes.add(b);
            }
            case "rod" -> {
                m.kind = "item";
                Cube h = cube("handle", 7.5, 0, 7.5, 8.5, 10, 8.5);
                m.cubes.add(h);
                m.cubes.add(cube("head", 6.5, 10, 6.5, 9.5, 13, 9.5));
            }
            case "armor_helmet" -> {
                Rig.biped(m);
                m.cubes.clear();
                m.animations.clear();
                m.cubes.add(boneCube("helm_shell", "head", 3.5, 23.5, 3.5, 12.5, 32.5, 12.5));
                m.cubes.add(boneCube("helm_crest", "head", 7.0, 32.5, 2.5, 9.0, 34.5, 13.0));
                Cube hornL = boneCube("horn_l", "head", 12.2, 29.0, 6.5, 15.2, 31.5, 9.5);
                hornL.rotation = 22.5;
                hornL.axis = "z";
                hornL.origin = new double[]{12.2, 29.0, 8.0};
                m.cubes.add(hornL);
                Cube hornR = boneCube("horn_r", "head", 0.8, 29.0, 6.5, 3.8, 31.5, 9.5);
                hornR.rotation = -22.5;
                hornR.axis = "z";
                hornR.origin = new double[]{3.8, 29.0, 8.0};
                m.cubes.add(hornR);
            }
            case "armor_chestplate" -> {
                Rig.biped(m);
                m.cubes.clear();
                m.animations.clear();
                m.cubes.add(boneCube("torso_plate", "body", 3.4, 11.6, 5.4, 12.6, 24.4, 10.6));
                m.cubes.add(boneCube("chest_core", "body", 5.5, 15.5, 4.8, 10.5, 21.5, 5.6));
                m.cubes.add(boneCube("pauldron_r", "right_arm", -0.6, 18.5, 5.4, 4.4, 24.5, 10.6));
                m.cubes.add(boneCube("pauldron_l", "left_arm", 11.6, 18.5, 5.4, 16.6, 24.5, 10.6));
            }
            case "armor_leggings" -> {
                Rig.biped(m);
                m.cubes.clear();
                m.animations.clear();
                m.cubes.add(boneCube("belt", "body", 3.5, 11.5, 5.5, 12.5, 14.5, 10.5));
                m.cubes.add(boneCube("greave_r", "right_leg", 3.6, 4.0, 5.6, 8.2, 12.2, 10.4));
                m.cubes.add(boneCube("greave_l", "left_leg", 7.8, 4.0, 5.6, 12.4, 12.2, 10.4));
            }
            case "armor_boots" -> {
                Rig.biped(m);
                m.cubes.clear();
                m.animations.clear();
                m.cubes.add(boneCube("boot_r", "right_leg", 3.3, -0.3, 5.3, 8.3, 5.5, 10.7));
                m.cubes.add(boneCube("boot_l", "left_leg", 7.7, -0.3, 5.3, 12.7, 5.5, 10.7));
            }
            case "quadruped" -> {
                m.kind = "entity";
                m.textureWidth = 64;
                m.textureHeight = 64;
                addBone(m, "body", "", 8, 10, 8);
                addBone(m, "head", "body", 8, 13, 2);
                addBone(m, "leg_fl", "body", 5, 6, 3);
                addBone(m, "leg_fr", "body", 11, 6, 3);
                addBone(m, "leg_bl", "body", 5, 6, 13);
                addBone(m, "leg_br", "body", 11, 6, 13);
                m.cubes.add(boneCube("torso", "body", 4, 6, 1, 12, 14, 15));
                m.cubes.add(boneCube("skull", "head", 5, 10, -5, 11, 16, 1));
                m.cubes.add(boneCube("snout", "head", 6.5, 10, -8, 9.5, 13, -5));
                m.cubes.add(boneCube("foot_fl", "leg_fl", 4, 0, 1, 7, 6, 4));
                m.cubes.add(boneCube("foot_fr", "leg_fr", 9, 0, 1, 12, 6, 4));
                m.cubes.add(boneCube("foot_bl", "leg_bl", 4, 0, 11, 7, 6, 14));
                m.cubes.add(boneCube("foot_br", "leg_br", 9, 0, 11, 12, 6, 14));
            }
            case "empty" -> {
            }
            default -> m.cubes.add(cube("block", 0, 0, 0, 16, 16, 16));
        }
        if (m.textures.isEmpty()) m.textures.put("0", "");
    }

    /** Axis aligned boxes (0..16 units) for collision; rotated cubes use their unrotated bounds. */
    public static List<double[]> boxes(Model m) {
        List<double[]> r = new ArrayList<>();
        for (Cube c : m.cubes) {
            double[] b = new double[6];
            for (int i = 0; i < 3; i++) {
                b[i] = clamp(Math.min(c.from[i], c.to[i]), -16, 32);
                b[i + 3] = clamp(Math.max(c.from[i], c.to[i]), -16, 32);
            }
            if (b[3] - b[0] > 0 && b[4] - b[1] > 0 && b[5] - b[2] > 0) r.add(b);
        }
        return r;
    }

    /** True when the model is exactly a full block, so it can stay a normal occluding cube. */
    public static boolean isFullCube(Model m) {
        if (m.cubes.size() != 1) return false;
        Cube c = m.cubes.get(0);
        return c.rotation == 0 && Arrays.equals(c.from, new double[]{0, 0, 0}) && Arrays.equals(c.to, new double[]{16, 16, 16});
    }

    /** Rotates the model 90 degrees around the vertical axis (clockwise seen from above). */
    public static double[] rotateY(double[] p, int quarterTurns) {
        double x = p[0], z = p[2];
        for (int i = 0; i < ((quarterTurns % 4) + 4) % 4; i++) {
            double nx = 16 - z, nz = x;
            x = nx;
            z = nz;
        }
        return new double[]{x, p[1], z};
    }

    // ------------------------------------------------------------------ Java model JSON

    /**
     * Java Edition model JSON.
     *
     * @param tex resolves a texture variable value (asset id or resource id) to a resource location
     */
    public static JsonObject toJava(Model m, Function<String, String> tex) {
        JsonObject o = new JsonObject();
        if (!m.ambientOcclusion) o.addProperty("ambientocclusion", false);
        JsonObject textures = new JsonObject();
        String first = null;
        for (var e : m.textures.entrySet()) {
            String r = tex.apply(e.getValue());
            if (r == null) r = "minecraft:block/stone";
            textures.addProperty(e.getKey(), r);
            if (first == null) first = r;
        }
        if (!textures.has("particle") && first != null) textures.addProperty("particle", first);
        if (m.textureWidth != 16 || m.textureHeight != 16) o.add("texture_size", arr(m.textureWidth, m.textureHeight));
        o.add("textures", textures);
        JsonArray elements = new JsonArray();
        for (Cube c : m.cubes) {
            JsonObject el = new JsonObject();
            el.addProperty("name", c.name);
            el.add("from", arr(c.from));
            el.add("to", arr(c.to));
            if (c.rotation != 0) {
                JsonObject rot = new JsonObject();
                rot.addProperty("angle", num(c.rotation));
                rot.addProperty("axis", c.axis);
                rot.add("origin", arr(c.origin));
                if (c.rescale) rot.addProperty("rescale", true);
                el.add("rotation", rot);
            }
            if (!c.shade) el.addProperty("shade", false);
            JsonObject faces = new JsonObject();
            for (String dir : Cube.FACES) {
                Face f = c.face(dir);
                if (!f.enabled) continue;
                JsonObject fo = new JsonObject();
                fo.add("uv", arr(uv(c, dir)));
                fo.addProperty("texture", "#" + (f.texture == null || f.texture.isBlank() ? "0" : f.texture));
                if (f.rotation != 0) fo.addProperty("rotation", f.rotation);
                if (f.cullface != null && !f.cullface.isBlank()) fo.addProperty("cullface", f.cullface);
                if (f.tintIndex >= 0) fo.addProperty("tintindex", f.tintIndex);
                faces.add(dir, fo);
            }
            el.add("faces", faces);
            elements.add(el);
        }
        o.add("elements", elements);
        JsonObject display = displayJson(m);
        if (display != null) o.add("display", display);
        return o;
    }

    private static JsonObject displayJson(Model m) {
        JsonObject d = new JsonObject();
        for (var e : m.display.entrySet()) {
            Display v = e.getValue();
            JsonObject o = new JsonObject();
            o.add("rotation", arr(v.rotation));
            o.add("translation", arr(v.translation));
            o.add("scale", arr(v.scale));
            d.add(e.getKey(), o);
        }
        if (d.isEmpty() && m.kind.equals("block")) {
            // Same transforms as minecraft:block/block so custom models look right in hands and inventories.
            d.add("gui", disp(30, 225, 0, 0, 0, 0, 0.625));
            d.add("ground", disp(0, 0, 0, 0, 3, 0, 0.25));
            d.add("fixed", disp(0, 0, 0, 0, 0, 0, 0.5));
            d.add("thirdperson_righthand", disp(75, 45, 0, 0, 2.5, 0, 0.375));
            d.add("firstperson_righthand", disp(0, 45, 0, 0, 0, 0, 0.4));
            d.add("firstperson_lefthand", disp(0, 225, 0, 0, 0, 0, 0.4));
        }
        return d.isEmpty() ? null : d;
    }

    private static JsonObject disp(double rx, double ry, double rz, double tx, double ty, double tz, double s) {
        JsonObject o = new JsonObject();
        o.add("rotation", arr(rx, ry, rz));
        o.add("translation", arr(tx, ty, tz));
        o.add("scale", arr(s, s, s));
        return o;
    }

    private static Number num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return (long) d;
        return Math.round(d * 10000.0) / 10000.0;
    }

    private static JsonArray arr(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) a.add(num(d));
        return a;
    }

    private static double[] dbl(JsonElement e, int n, double def) {
        double[] r = new double[n];
        Arrays.fill(r, def);
        if (e == null || !e.isJsonArray()) return r;
        JsonArray a = e.getAsJsonArray();
        for (int i = 0; i < Math.min(n, a.size()); i++) r[i] = a.get(i).getAsDouble();
        return r;
    }

    // ------------------------------------------------------------------ import

    /** An imported model plus images that should become project textures (texture key -> PNG bytes). */
    public record Imported(Model model, Map<String, byte[]> images, Map<String, String> imageNames) {}

    /** Reads a Java block/item model JSON, a Blockbench .bbmodel file, or a Bedrock .geo.json file. */
    public static Imported read(String json, String fileName) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (o.has("meta") && o.has("elements") && o.get("elements").isJsonArray()
                && (o.has("resolution") || o.getAsJsonObject("meta").has("format_version"))) {
            return readBlockbench(o);
        }
        if (o.has("minecraft:geometry") && o.get("minecraft:geometry").isJsonArray()) {
            return readBedrock(o);
        }
        for (String k : o.keySet()) {
            if (k.startsWith("geometry.")) return readLegacyBedrock(o.getAsJsonObject(k));
        }
        return readJava(o);
    }

    private static Imported readJava(JsonObject o) {
        Model m = new Model();
        if (o.has("ambientocclusion")) m.ambientOcclusion = o.get("ambientocclusion").getAsBoolean();
        if (o.has("texture_size")) {
            double[] ts = dbl(o.get("texture_size"), 2, 16);
            m.textureWidth = (int) ts[0];
            m.textureHeight = (int) ts[1];
        }
        if (o.has("textures")) {
            for (var e : o.getAsJsonObject("textures").entrySet()) {
                if (e.getKey().equals("particle") && o.getAsJsonObject("textures").size() > 1) continue;
                m.textures.put(e.getKey(), e.getValue().getAsString());
            }
        }
        if (o.has("elements")) {
            int i = 0;
            for (JsonElement je : o.getAsJsonArray("elements")) {
                JsonObject el = je.getAsJsonObject();
                Cube c = new Cube();
                c.name = el.has("name") ? el.get("name").getAsString() : "cube" + (++i);
                c.from = dbl(el.get("from"), 3, 0);
                c.to = dbl(el.get("to"), 3, 16);
                if (el.has("rotation")) {
                    JsonObject r = el.getAsJsonObject("rotation");
                    c.rotation = r.has("angle") ? snapAngle(r.get("angle").getAsDouble()) : 0;
                    c.axis = r.has("axis") ? r.get("axis").getAsString() : "y";
                    c.origin = dbl(r.get("origin"), 3, 8);
                    c.rescale = r.has("rescale") && r.get("rescale").getAsBoolean();
                }
                if (el.has("shade")) c.shade = el.get("shade").getAsBoolean();
                for (String dir : Cube.FACES) c.face(dir).enabled = false;
                if (el.has("faces")) {
                    for (var fe : el.getAsJsonObject("faces").entrySet()) {
                        Face f = c.face(fe.getKey());
                        JsonObject fo = fe.getValue().getAsJsonObject();
                        f.enabled = true;
                        if (fo.has("uv")) {
                            f.uv = dbl(fo.get("uv"), 4, 0);
                        }
                        if (fo.has("texture")) f.texture = fo.get("texture").getAsString().replace("#", "");
                        if (fo.has("rotation")) f.rotation = fo.get("rotation").getAsInt();
                        if (fo.has("cullface")) f.cullface = fo.get("cullface").getAsString();
                        if (fo.has("tintindex")) f.tintIndex = fo.get("tintindex").getAsInt();
                    }
                }
                m.cubes.add(c);
            }
        }
        if (o.has("display")) {
            for (var e : o.getAsJsonObject("display").entrySet()) {
                JsonObject d = e.getValue().getAsJsonObject();
                Display v = new Display();
                v.rotation = dbl(d.get("rotation"), 3, 0);
                v.translation = dbl(d.get("translation"), 3, 0);
                v.scale = dbl(d.get("scale"), 3, 1);
                m.display.put(e.getKey(), v);
            }
        }
        if (o.has("parent") && o.get("parent").getAsString().contains("item")) m.kind = "item";
        if (m.textures.isEmpty()) m.textures.put("0", "");
        return new Imported(m, Map.of(), Map.of());
    }

    private static Imported readBlockbench(JsonObject o) {
        Model m = new Model();
        double resW = 16, resH = 16;
        if (o.has("resolution")) {
            JsonObject r = o.getAsJsonObject("resolution");
            resW = r.has("width") ? r.get("width").getAsDouble() : 16;
            resH = r.has("height") ? r.get("height").getAsDouble() : 16;
        }
        m.textureWidth = (int) resW;
        m.textureHeight = (int) resH;
        Map<String, byte[]> images = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        if (o.has("textures")) {
            int i = 0;
            for (JsonElement je : o.getAsJsonArray("textures")) {
                JsonObject t = je.getAsJsonObject();
                String key = String.valueOf(i++);
                String src = t.has("source") ? t.get("source").getAsString() : "";
                int comma = src.indexOf(',');
                if (src.startsWith("data:image") && comma > 0) {
                    images.put(key, Base64.getDecoder().decode(src.substring(comma + 1)));
                    String n = t.has("name") ? t.get("name").getAsString() : "texture" + key;
                    names.put(key, n.replaceAll("\\.png$", ""));
                }
                m.textures.put(key, "");
            }
        }
        // Blockbench UVs are in texture pixels; Java models use 0..16.
        double fu = 16.0 / resW, fv = 16.0 / resH;
        int i = 0;
        Map<String, Cube> byUuid = new HashMap<>();
        for (JsonElement je : o.getAsJsonArray("elements")) {
            JsonObject el = je.getAsJsonObject();
            if (el.has("type") && !el.get("type").getAsString().equals("cube")) continue;
            Cube c = new Cube();
            if (el.has("uuid")) byUuid.put(el.get("uuid").getAsString(), c);
            c.name = el.has("name") ? el.get("name").getAsString() : "cube" + (++i);
            c.from = dbl(el.get("from"), 3, 0);
            c.to = dbl(el.get("to"), 3, 16);
            c.origin = dbl(el.get("origin"), 3, 8);
            double[] rot = dbl(el.get("rotation"), 3, 0);
            for (int a = 0; a < 3; a++) {
                if (rot[a] != 0) {
                    c.rotation = snapAngle(rot[a]);
                    c.axis = new String[]{"x", "y", "z"}[a];
                    break;
                }
            }
            if (el.has("shade")) c.shade = el.get("shade").getAsBoolean();
            for (String dir : Cube.FACES) c.face(dir).enabled = false;
            if (el.has("faces")) {
                for (var fe : el.getAsJsonObject("faces").entrySet()) {
                    JsonObject fo = fe.getValue().getAsJsonObject();
                    Face f = c.face(fe.getKey());
                    if (fo.has("texture") && fo.get("texture").isJsonNull()) continue;
                    f.enabled = true;
                    if (fo.has("uv")) {
                        double[] uv = dbl(fo.get("uv"), 4, 0);
                        f.uv = new double[]{uv[0] * fu, uv[1] * fv, uv[2] * fu, uv[3] * fv};
                    }
                    if (fo.has("texture") && fo.get("texture").isJsonPrimitive()) f.texture = fo.get("texture").getAsString();
                    if (fo.has("rotation")) f.rotation = fo.get("rotation").getAsInt();
                    if (fo.has("cullface")) f.cullface = fo.get("cullface").getAsString();
                    if (fo.has("tint")) f.tintIndex = fo.get("tint").getAsInt();
                }
            }
            m.cubes.add(c);
        }
        m.textureWidth = 16;
        m.textureHeight = 16;
        if (m.textures.isEmpty()) m.textures.put("0", "");
        Map<String, String> boneByUuid = new HashMap<>();
        if (o.has("outliner") && o.get("outliner").isJsonArray()) {
            for (JsonElement je : o.getAsJsonArray("outliner")) readGroup(m, je, "", byUuid, boneByUuid);
        }
        if (o.has("animations") && o.get("animations").isJsonArray() && !m.bones.isEmpty()) {
            for (JsonElement je : o.getAsJsonArray("animations")) readAnimation(m, je.getAsJsonObject(), boneByUuid);
        }
        return new Imported(m, images, names);
    }

    /** Blockbench outliner groups become bones; cubes inside a group follow it. */
    private static void readGroup(Model m, JsonElement je, String parent, Map<String, Cube> cubes, Map<String, String> boneByUuid) {
        if (je.isJsonPrimitive()) {
            Cube c = cubes.get(je.getAsString());
            if (c != null) c.bone = parent;
            return;
        }
        if (!je.isJsonObject()) return;
        JsonObject g = je.getAsJsonObject();
        String base = Ids.toId(g.has("name") ? g.get("name").getAsString() : "bone");
        if (base.isEmpty() || base.equals(Rig.ROOT)) base = "bone";
        String name = base;
        for (int n = 2; m.bone(name) != null; n++) name = base + "_" + n;
        Elements.Bone b = new Elements.Bone();
        b.name = name;
        b.parent = parent;
        b.pivot = dbl(g.get("origin"), 3, 8);
        m.bones.add(b);
        if (g.has("uuid")) boneByUuid.put(g.get("uuid").getAsString(), name);
        if (g.has("children") && g.get("children").isJsonArray()) {
            for (JsonElement ch : g.getAsJsonArray("children")) readGroup(m, ch, name, cubes, boneByUuid);
        }
    }

    /**
     * Blockbench animations. Blockbench shows bone rotations with X and Y reversed and positions with X reversed
     * compared with model space, so those are flipped; Molang expressions that are not plain numbers become 0.
     */
    private static void readAnimation(Model m, JsonObject a, Map<String, String> boneByUuid) {
        Elements.Animation an = new Elements.Animation();
        String base = Ids.toId(a.has("name") ? a.get("name").getAsString().replaceAll(".*[.]", "") : "animation");
        if (base.isEmpty()) base = "animation";
        String name = base;
        for (int n = 2; m.animation(name) != null; n++) name = base + "_" + n;
        an.name = name;
        an.length = a.has("length") ? Math.max(0.1, a.get("length").getAsDouble()) : 1;
        String loop = a.has("loop") ? a.get("loop").getAsString() : "loop";
        an.mode = loop.equals("once") || loop.equals("hold") ? loop : "loop";
        if (a.has("animators") && a.get("animators").isJsonObject()) {
            for (var en : a.getAsJsonObject("animators").entrySet()) {
                String bone = boneByUuid.get(en.getKey());
                JsonObject anim = en.getValue().getAsJsonObject();
                if (bone == null || !anim.has("keyframes")) continue;
                for (JsonElement ke : anim.getAsJsonArray("keyframes")) {
                    JsonObject kf = ke.getAsJsonObject();
                    String ch = kf.has("channel") ? kf.get("channel").getAsString() : "";
                    if (!Rig.CHANNELS.contains(ch) || !kf.has("data_points") || !kf.get("data_points").isJsonArray()) continue;
                    JsonArray dps = kf.getAsJsonArray("data_points");
                    if (dps.isEmpty() || !dps.get(0).isJsonObject()) continue;
                    JsonObject dp = dps.get(0).getAsJsonObject();
                    Elements.AnimTrack tr = an.track(bone, ch);
                    if (tr == null) {
                        tr = new Elements.AnimTrack();
                        tr.bone = bone;
                        tr.channel = ch;
                        an.tracks.add(tr);
                    }
                    Elements.AnimKey k = new Elements.AnimKey();
                    k.time = kf.has("time") ? kf.get("time").getAsDouble() : 0;
                    double def = ch.equals("scale") ? 1 : 0;
                    k.x = molang(dp.get("x"), def);
                    k.y = molang(dp.get("y"), def);
                    k.z = molang(dp.get("z"), def);
                    if (ch.equals("rotation")) {
                        k.x = -k.x;
                        k.y = -k.y;
                    } else if (ch.equals("position")) {
                        k.x = -k.x;
                    }
                    String in = kf.has("interpolation") ? kf.get("interpolation").getAsString() : "linear";
                    k.easing = switch (in) {
                        case "catmullrom", "bezier" -> "smooth";
                        case "step" -> "step";
                        default -> "linear";
                    };
                    tr.keys.add(k);
                }
            }
        }
        for (Elements.AnimTrack tr : an.tracks) tr.keys.sort(Comparator.comparingDouble(k -> k.time));
        m.animations.add(an);
    }

    private static double molang(JsonElement e, double def) {
        if (e == null || e.isJsonNull()) return def;
        try {
            return e.getAsDouble();
        } catch (Exception ex) {
            try {
                return Double.parseDouble(e.getAsString().trim());
            } catch (Exception ex2) {
                return def;
            }
        }
    }

    public static double snapAngle(double a) {
        double best = 0;
        for (double c : ANGLES) if (Math.abs(c - a) < Math.abs(best - a)) best = c;
        return best;
    }

    // ------------------------------------------------------------------ Bedrock geometry

    /** Bedrock Edition geometry (format 1.12.0) for a block model. */
    public static JsonObject toBedrock(Model m, String identifier, int texW, int texH) {
        JsonArray cubes = new JsonArray();
        for (Cube c : m.cubes) {
            JsonObject cb = new JsonObject();
            // Bedrock blocks are centred on x/z: origin is the minimum corner in pixels relative to the block centre.
            double ox = -(c.to[0] - 8), oy = c.from[1], oz = c.from[2] - 8;
            double sx = c.to[0] - c.from[0], sy = c.to[1] - c.from[1], sz = c.to[2] - c.from[2];
            cb.add("origin", arr(ox, oy, oz));
            cb.add("size", arr(sx, sy, sz));
            if (Math.abs(c.inflate) > 1e-6) cb.addProperty("inflate", c.inflate);
            if (c.rotation != 0) {
                double[] r = new double[3];
                int idx = c.axis.equals("x") ? 0 : c.axis.equals("y") ? 1 : 2;
                r[idx] = idx == 2 ? c.rotation : -c.rotation;
                cb.add("rotation", arr(r));
                cb.add("pivot", arr(-(c.origin[0] - 8), c.origin[1], c.origin[2] - 8));
            }
            JsonObject uv = new JsonObject();
            for (String dir : Cube.FACES) {
                Face f = c.face(dir);
                if (!f.enabled) continue;
                double[] u = uv(c, dir);
                double kx = texW / 16.0, ky = texH / 16.0;
                JsonObject fo = new JsonObject();
                fo.add("uv", arr(u[0] * kx, u[1] * ky));
                fo.add("uv_size", arr((u[2] - u[0]) * kx, (u[3] - u[1]) * ky));
                fo.addProperty("material_instance", f.texture == null || f.texture.isBlank() ? "0" : f.texture);
                uv.add(dir, fo);
            }
            cb.add("uv", uv);
            cubes.add(cb);
        }
        JsonObject bone = new JsonObject();
        bone.addProperty("name", "root");
        bone.add("pivot", arr(0, 0, 0));
        bone.add("cubes", cubes);
        JsonObject desc = new JsonObject();
        desc.addProperty("identifier", identifier);
        desc.addProperty("texture_width", texW);
        desc.addProperty("texture_height", texH);
        desc.addProperty("visible_bounds_width", 2);
        desc.addProperty("visible_bounds_height", 2.5);
        desc.add("visible_bounds_offset", arr(0, 0.75, 0));
        JsonObject geo = new JsonObject();
        geo.add("description", desc);
        JsonArray bones = new JsonArray();
        bones.add(bone);
        geo.add("bones", bones);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.16.0");
        JsonArray list = new JsonArray();
        list.add(geo);
        root.add("minecraft:geometry", list);
        return root;
    }

    private static Imported readBedrock(JsonObject root) {
        JsonArray arr = root.getAsJsonArray("minecraft:geometry");
        if (arr.isEmpty()) return new Imported(new Model(), Map.of(), Map.of());
        JsonObject geo = arr.get(0).getAsJsonObject();
        Model m = new Model();
        if (geo.has("description") && geo.get("description").isJsonObject()) {
            JsonObject desc = geo.getAsJsonObject("description");
            m.textureWidth = desc.has("texture_width") ? Math.max(1, desc.get("texture_width").getAsInt()) : 16;
            m.textureHeight = desc.has("texture_height") ? Math.max(1, desc.get("texture_height").getAsInt()) : 16;
        }
        if (geo.has("bones") && geo.get("bones").isJsonArray()) {
            parseBedrockBones(m, geo.getAsJsonArray("bones"));
        }
        if (m.textures.isEmpty()) m.textures.put("0", "");
        return new Imported(m, Map.of(), Map.of());
    }

    private static Imported readLegacyBedrock(JsonObject geo) {
        Model m = new Model();
        m.textureWidth = geo.has("texturewidth") ? Math.max(1, geo.get("texturewidth").getAsInt()) : 64;
        m.textureHeight = geo.has("textureheight") ? Math.max(1, geo.get("textureheight").getAsInt()) : 32;
        if (geo.has("bones") && geo.get("bones").isJsonArray()) {
            parseBedrockBones(m, geo.getAsJsonArray("bones"));
        }
        if (m.textures.isEmpty()) m.textures.put("0", "");
        return new Imported(m, Map.of(), Map.of());
    }

    private static void parseBedrockBones(Model m, JsonArray bones) {
        boolean singleRoot = bones.size() == 1 && bones.get(0).isJsonObject()
                && "root".equals(bones.get(0).getAsJsonObject().has("name")
                ? bones.get(0).getAsJsonObject().get("name").getAsString() : "");
        double kx = 16.0 / m.textureWidth, ky = 16.0 / m.textureHeight;
        int cubeIdx = 0;
        for (JsonElement be : bones) {
            if (!be.isJsonObject()) continue;
            JsonObject bo = be.getAsJsonObject();
            String rawName = bo.has("name") ? bo.get("name").getAsString() : "bone";
            String bName = Ids.toId(rawName);
            if (bName.isEmpty() || bName.equals(Rig.ROOT)) bName = "bone";
            double[] bp = bo.has("pivot") ? dbl(bo.get("pivot"), 3, 0) : new double[]{0, 0, 0};
            double[] modPivot = {8 - bp[0], bp[1], bp[2] + 8};
            if (!singleRoot) {
                Elements.Bone b = new Elements.Bone();
                String unique = bName;
                for (int n = 2; m.bone(unique) != null; n++) unique = bName + "_" + n;
                bName = unique;
                b.name = bName;
                b.parent = bo.has("parent") ? Ids.toId(bo.get("parent").getAsString()) : "";
                b.pivot = modPivot;
                m.bones.add(b);
            }
            if (!bo.has("cubes") || !bo.get("cubes").isJsonArray()) continue;
            for (JsonElement ce : bo.getAsJsonArray("cubes")) {
                if (!ce.isJsonObject()) continue;
                JsonObject cb = ce.getAsJsonObject();
                double[] orig = dbl(cb.get("origin"), 3, 0);
                double[] size = dbl(cb.get("size"), 3, 1);
                Cube c = new Cube();
                c.name = "cube" + (++cubeIdx);
                c.from = new double[]{8 - (orig[0] + size[0]), orig[1], orig[2] + 8};
                c.to = new double[]{8 - orig[0], orig[1] + size[1], orig[2] + size[2] + 8};
                if (cb.has("inflate") && cb.get("inflate").isJsonPrimitive()) c.inflate = cb.get("inflate").getAsDouble();
                if (!singleRoot) c.bone = bName;
                if (cb.has("rotation") && cb.get("rotation").isJsonArray()) {
                    double[] r = dbl(cb.get("rotation"), 3, 0);
                    int ax = Math.abs(r[0]) >= Math.abs(r[1]) && Math.abs(r[0]) >= Math.abs(r[2]) ? 0
                            : Math.abs(r[1]) >= Math.abs(r[2]) ? 1 : 2;
                    c.axis = ax == 0 ? "x" : ax == 1 ? "y" : "z";
                    c.rotation = snapAngle(ax == 2 ? r[ax] : -r[ax]);
                    double[] cp = cb.has("pivot") ? dbl(cb.get("pivot"), 3, 0) : bp;
                    c.origin = new double[]{8 - cp[0], cp[1], cp[2] + 8};
                } else {
                    c.origin = modPivot.clone();
                }
                if (cb.has("uv") && cb.get("uv").isJsonObject()) {
                    for (String dir : Cube.FACES) c.face(dir).enabled = false;
                    JsonObject uvObj = cb.getAsJsonObject("uv");
                    for (var fe : uvObj.entrySet()) {
                        Face f = c.face(fe.getKey());
                        if (f == null || !fe.getValue().isJsonObject()) continue;
                        JsonObject fo = fe.getValue().getAsJsonObject();
                        f.enabled = true;
                        double[] u0 = dbl(fo.get("uv"), 2, 0);
                        double[] du = dbl(fo.get("uv_size"), 2, 16);
                        f.uv = new double[]{u0[0] * kx, u0[1] * ky, (u0[0] + du[0]) * kx, (u0[1] + du[1]) * ky};
                        if (fo.has("material_instance")) {
                            String mat = fo.get("material_instance").getAsString();
                            f.texture = mat;
                            m.textures.putIfAbsent(mat, "");
                        }
                    }
                } else if (cb.has("uv") && cb.get("uv").isJsonArray()) {
                    double[] boxUv = dbl(cb.get("uv"), 2, 0);
                    double u = boxUv[0], v = boxUv[1];
                    double sx = Math.max(0, size[0]), sy = Math.max(0, size[1]), sz = Math.max(0, size[2]);
                    setBoxUv(c.face("north"), u + sz, v + sz, u + sz + sx, v + sz + sy, kx, ky);
                    setBoxUv(c.face("south"), u + sz + sx + sz, v + sz, u + sz + 2 * sx + sz, v + sz + sy, kx, ky);
                    setBoxUv(c.face("west"), u, v + sz, u + sz, v + sz + sy, kx, ky);
                    setBoxUv(c.face("east"), u + sz + sx, v + sz, u + sz + sx + sz, v + sz + sy, kx, ky);
                    setBoxUv(c.face("up"), u + sz, v, u + sz + sx, v + sz, kx, ky);
                    setBoxUv(c.face("down"), u + sz + sx, v, u + sz + 2 * sx, v + sz, kx, ky);
                }
                m.cubes.add(c);
            }
        }
    }

    private static void setBoxUv(Face f, double u1, double v1, double u2, double v2, double kx, double ky) {
        f.enabled = true;
        f.texture = "0";
        f.uv = new double[]{u1 * kx, v1 * ky, u2 * kx, v2 * ky};
    }
}
