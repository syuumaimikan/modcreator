package modstudio.core;

import modstudio.core.Elements.AnimKey;
import modstudio.core.Elements.AnimTrack;
import modstudio.core.Elements.Animation;
import modstudio.core.Elements.Bone;
import modstudio.core.Elements.Cube;
import modstudio.core.Elements.Face;
import modstudio.core.Elements.Model;

import java.util.*;

/**
 * Bone animation math shared by the editor preview and the generators.
 *
 * <p>Matrices are 4x4, row-major, in model units (16 per block). A bone moves its cubes by
 * {@code world(parent) * T(pivot + position) * R(rotation) * S(scale) * T(-pivot)}, with
 * {@code R = Rz * Ry * Rx} in degrees. The model front is north (towards -Z), like block models.
 */
public final class Rig {
    private Rig() {}

    public static final List<String> CHANNELS = List.of("rotation", "position", "scale");
    public static final List<String> EASINGS = List.of("linear", "smooth", "step", "ease_in", "ease_out", "ease_in_out");
    public static final List<String> MODES = List.of("loop", "once", "hold");
    /** Name of the bone that holds cubes without a bone. */
    public static final String ROOT = "root";
    /** Bone item models store cubes shrunk by this factor so big models stay inside the -16..32 model limit. */
    public static final double K = 0.25;

    // ------------------------------------------------------------------ matrices

    public static double[] identity() {
        return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    public static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
                r[i * 4 + j] = s;
            }
        }
        return r;
    }

    public static double[] mul(double[]... ms) {
        double[] r = ms[0];
        for (int i = 1; i < ms.length; i++) r = mul(r, ms[i]);
        return r;
    }

    public static double[] translate(double x, double y, double z) {
        return new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
    }

    public static double[] scale(double x, double y, double z) {
        return new double[]{x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1};
    }

    public static double[] rotX(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
    }

    public static double[] rotY(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
    }

    public static double[] rotZ(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    public static double[] euler(double rx, double ry, double rz) {
        return mul(rotZ(rz), rotY(ry), rotX(rx));
    }

    public static double[] apply(double[] m, double x, double y, double z) {
        return new double[]{
                m[0] * x + m[1] * y + m[2] * z + m[3],
                m[4] * x + m[5] * y + m[6] * z + m[7],
                m[8] * x + m[9] * y + m[10] * z + m[11]};
    }

    public static boolean same(double[] a, double[] b) {
        if (a == null || b == null) return false;
        for (int i = 0; i < 16; i++) if (Math.abs(a[i] - b[i]) > 1e-4) return false;
        return true;
    }

    // ------------------------------------------------------------------ structure

    /** True when the model has bones and so can be animated. */
    public static boolean animated(Model m) {
        return m != null && !m.bones.isEmpty();
    }

    /** Bone a cube follows: its own bone when it exists, otherwise the root. */
    public static String boneOf(Model m, Cube c) {
        return c.bone != null && !c.bone.isBlank() && m.bone(c.bone) != null ? c.bone : ROOT;
    }

    /**
     * Bones with parents before children. A missing parent or a parent loop makes the bone a root.
     * A synthetic root bone comes first when some cubes have no bone.
     */
    public static List<Bone> ordered(Model m) {
        List<Bone> out = new ArrayList<>();
        Set<String> done = new HashSet<>();
        boolean loose = m.cubes.stream().anyMatch(c -> boneOf(m, c).equals(ROOT));
        if (loose && m.bone(ROOT) == null) {
            Bone r = new Bone();
            r.name = ROOT;
            out.add(r);
            done.add(ROOT);
        }
        List<Bone> left = new ArrayList<>(m.bones);
        boolean progress = true;
        while (!left.isEmpty() && progress) {
            progress = false;
            for (Iterator<Bone> it = left.iterator(); it.hasNext(); ) {
                Bone b = it.next();
                String p = parent(m, b);
                if (p.isEmpty() || done.contains(p)) {
                    out.add(b);
                    done.add(b.name);
                    it.remove();
                    progress = true;
                }
            }
        }
        out.addAll(left); // only reachable through loops, which parent() already breaks
        return out;
    }

    /** Parent bone name, or "" when the bone is a root (missing parent or a loop). */
    public static String parent(Model m, Bone b) {
        String p = b.parent == null ? "" : b.parent.trim();
        if (p.isEmpty() || p.equals(b.name) || m.bone(p) == null) return "";
        Set<String> seen = new HashSet<>(Set.of(b.name));
        String cur = p;
        while (!cur.isEmpty()) {
            if (!seen.add(cur)) return "";
            Bone pb = m.bone(cur);
            cur = pb == null || pb.parent == null ? "" : pb.parent.trim();
        }
        return p;
    }

    public static int ticks(Animation a) {
        return Math.max(2, (int) Math.round(Math.max(0.1, a.length) * 20));
    }

    // ------------------------------------------------------------------ sampling

    public static List<AnimKey> sorted(AnimTrack t) {
        List<AnimKey> ks = new ArrayList<>(t.keys);
        ks.sort(Comparator.comparingDouble(k -> k.time));
        return ks;
    }

    /** Value of a track at a time in seconds. */
    public static double[] sample(AnimTrack t, double time, double[] def) {
        List<AnimKey> ks = sorted(t);
        if (ks.isEmpty()) return def.clone();
        if (time <= ks.get(0).time) return v(ks.get(0));
        AnimKey last = ks.get(ks.size() - 1);
        if (time >= last.time) return v(last);
        int i = 0;
        while (i < ks.size() - 2 && ks.get(i + 1).time <= time) i++;
        AnimKey a = ks.get(i), b = ks.get(i + 1);
        double span = b.time - a.time;
        double u = span <= 1e-9 ? 1 : (time - a.time) / span;
        String e = a.easing == null ? "linear" : a.easing;
        if (e.equals("step")) return v(a);
        if (e.equals("smooth")) {
            AnimKey p0 = i > 0 ? ks.get(i - 1) : a, p3 = i + 2 < ks.size() ? ks.get(i + 2) : b;
            return new double[]{
                    CameraPath.catmull(p0.x, a.x, b.x, p3.x, u),
                    CameraPath.catmull(p0.y, a.y, b.y, p3.y, u),
                    CameraPath.catmull(p0.z, a.z, b.z, p3.z, u)};
        }
        double w = CameraPath.ease(e, u);
        return new double[]{a.x + (b.x - a.x) * w, a.y + (b.y - a.y) * w, a.z + (b.z - a.z) * w};
    }

    private static double[] v(AnimKey k) {
        return new double[]{k.x, k.y, k.z};
    }

    /** Model-space matrix of every bone (including the synthetic root) at a time; a null animation is the rest pose. */
    public static Map<String, double[]> pose(Model m, Animation a, double time) {
        Map<String, double[]> world = new LinkedHashMap<>();
        for (Bone b : ordered(m)) {
            double[] rot = {0, 0, 0}, pos = {0, 0, 0}, scl = {1, 1, 1};
            if (a != null) {
                AnimTrack tr = a.track(b.name, "rotation");
                if (tr != null) rot = sample(tr, time, rot);
                tr = a.track(b.name, "position");
                if (tr != null) pos = sample(tr, time, pos);
                tr = a.track(b.name, "scale");
                if (tr != null) scl = sample(tr, time, scl);
            }
            double[] p = b.pivot;
            double[] local = mul(translate(p[0] + pos[0], p[1] + pos[1], p[2] + pos[2]), euler(rot[0], rot[1], rot[2]),
                    scale(scl[0], scl[1], scl[2]), translate(-p[0], -p[1], -p[2]));
            String parent = parent(m, b);
            double[] pw = parent.isEmpty() ? null : world.get(parent);
            world.put(b.name, pw == null ? local : mul(pw, local));
        }
        return world;
    }

    /**
     * Transformation of the item display that shows one bone, as a row-major 4x4 matrix in blocks.
     * The bone item model holds the bone cubes around its pivot shrunk by {@link #K}; the display renderer turns
     * items 180 degrees around Y, which the trailing turn cancels, and the leading turn makes the model front (north)
     * face where the mob looks. {@code drop} moves the model down from the riding point to the feet of the mob.
     */
    public static double[] display(Bone b, double[] world, double modelScale, double drop) {
        double s = modelScale / 16.0, back = 16.0 / K;
        double[] p = b.pivot;
        return mul(translate(0, -drop, 0), rotY(180), scale(s, s, s), translate(-8, 0, -8), world,
                translate(p[0], p[1], p[2]), scale(back, back, back), rotY(180));
    }

    /** The cubes of one bone placed around its pivot and shrunk, ready to be written as an item model. */
    public static Model boneModel(Model m, Bone b) {
        Model r = new Model();
        r.name = m.name + "_" + b.name;
        r.kind = "item";
        r.textures.putAll(m.textures);
        r.ambientOcclusion = m.ambientOcclusion;
        r.textureWidth = m.textureWidth;
        r.textureHeight = m.textureHeight;
        double[] p = b.pivot;
        for (Cube c : m.cubes) {
            if (!boneOf(m, c).equals(b.name)) continue;
            Cube n = Models.copy(c);
            for (String dir : Cube.FACES) {
                Face f = n.face(dir);
                if (f.enabled) f.uv = Models.uv(c, dir).clone();
            }
            for (int i = 0; i < 3; i++) {
                n.from[i] = 8 + K * (c.from[i] - p[i]);
                n.to[i] = 8 + K * (c.to[i] - p[i]);
                n.origin[i] = 8 + K * (c.origin[i] - p[i]);
            }
            r.cubes.add(n);
        }
        return r;
    }

    /** Largest distance of a cube corner from its bone pivot, in model units (bone models must stay within 96). */
    public static double reach(Model m) {
        double max = 0;
        Map<String, Bone> byName = new HashMap<>();
        for (Bone b : ordered(m)) byName.put(b.name, b);
        for (Cube c : m.cubes) {
            Bone b = byName.get(boneOf(m, c));
            if (b == null) continue;
            for (int i = 0; i < 3; i++) {
                max = Math.max(max, Math.abs(c.from[i] - b.pivot[i]));
                max = Math.max(max, Math.abs(c.to[i] - b.pivot[i]));
            }
        }
        return max;
    }

    // ------------------------------------------------------------------ presets

    public static final List<String> PRESETS = List.of("swing", "swing_back", "nod", "sway", "turn", "spin", "bob", "pulse", "shake", "flinch", "strike");

    /** Replaces one track of a bone with a ready-made motion spread over the animation length. */
    public static void applyPreset(Animation a, String bone, String preset) {
        String ch;
        String ease = "smooth";
        double[][] k; // {fraction of length, x, y, z}
        switch (preset) {
            case "swing_back" -> {
                ch = "rotation";
                k = new double[][]{{0, 0, 0, 0}, {0.25, -30, 0, 0}, {0.5, 0, 0, 0}, {0.75, 30, 0, 0}, {1, 0, 0, 0}};
            }
            case "nod" -> {
                ch = "rotation";
                k = new double[][]{{0, 0, 0, 0}, {0.5, 12, 0, 0}, {1, 0, 0, 0}};
            }
            case "sway" -> {
                ch = "rotation";
                k = new double[][]{{0, 0, 0, 0}, {0.25, 0, 0, 6}, {0.75, 0, 0, -6}, {1, 0, 0, 0}};
            }
            case "turn" -> {
                ch = "rotation";
                k = new double[][]{{0, 0, 0, 0}, {0.25, 0, 25, 0}, {0.75, 0, -25, 0}, {1, 0, 0, 0}};
            }
            case "spin" -> {
                ch = "rotation";
                ease = "linear";
                k = new double[][]{{0, 0, 0, 0}, {1, 0, 360, 0}};
            }
            case "bob" -> {
                ch = "position";
                k = new double[][]{{0, 0, 0, 0}, {0.5, 0, 1, 0}, {1, 0, 0, 0}};
            }
            case "pulse" -> {
                ch = "scale";
                k = new double[][]{{0, 1, 1, 1}, {0.5, 1.15, 1.15, 1.15}, {1, 1, 1, 1}};
            }
            case "shake" -> {
                ch = "rotation";
                ease = "linear";
                k = new double[][]{{0, 0, 0, 0}, {0.2, 0, 0, 12}, {0.4, 0, 0, -12}, {0.6, 0, 0, 8}, {0.8, 0, 0, -4}, {1, 0, 0, 0}};
            }
            case "flinch" -> {
                ch = "rotation";
                ease = "ease_out";
                k = new double[][]{{0, 0, 0, 0}, {0.25, -20, 0, 0}, {1, 0, 0, 0}};
            }
            case "strike" -> {
                ch = "rotation";
                ease = "ease_out";
                k = new double[][]{{0, 0, 0, 0}, {0.35, 110, 0, 0}, {1, 0, 0, 0}};
            }
            default -> {
                ch = "rotation";
                k = new double[][]{{0, 0, 0, 0}, {0.25, 30, 0, 0}, {0.5, 0, 0, 0}, {0.75, -30, 0, 0}, {1, 0, 0, 0}};
            }
        }
        AnimTrack tr = a.track(bone, ch);
        if (tr == null) {
            tr = new AnimTrack();
            tr.bone = bone;
            tr.channel = ch;
            a.tracks.add(tr);
        }
        tr.keys.clear();
        for (double[] row : k) {
            AnimKey key = new AnimKey();
            key.time = Math.round(row[0] * a.length * 100) / 100.0;
            key.x = row[1];
            key.y = row[2];
            key.z = row[3];
            key.easing = ease;
            tr.keys.add(key);
        }
    }

    /** A standing figure two blocks tall with bones and idle, walk, hurt, attack and death animations. */
    public static void biped(Model m) {
        m.cubes.clear();
        m.bones.clear();
        m.animations.clear();
        m.kind = "item";
        String[][] bones = {{"base", "", "8", "0", "8"}, {"body", "base", "8", "12", "8"}, {"left_leg", "base", "6", "12", "8"},
                {"right_leg", "base", "10", "12", "8"}, {"head", "body", "8", "24", "8"}, {"left_arm", "body", "2", "23", "8"},
                {"right_arm", "body", "14", "23", "8"}};
        for (String[] b : bones) {
            Bone bone = new Bone();
            bone.name = b[0];
            bone.parent = b[1];
            bone.pivot = new double[]{Double.parseDouble(b[2]), Double.parseDouble(b[3]), Double.parseDouble(b[4])};
            m.bones.add(bone);
        }
        Object[][] cubes = {{"body", 4, 12, 6, 12, 24, 10}, {"head", 4, 24, 4, 12, 32, 12}, {"left_leg", 4, 0, 6, 8, 12, 10},
                {"right_leg", 8, 0, 6, 12, 12, 10}, {"left_arm", 0, 12, 6, 4, 24, 10}, {"right_arm", 12, 12, 6, 16, 24, 10}};
        for (Object[] c : cubes) {
            Cube cu = Models.cube((String) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], (int) c[5], (int) c[6]);
            cu.bone = (String) c[0];
            for (String dir : Cube.FACES) cu.face(dir).uv = new double[]{0, 0, 16, 16};
            m.cubes.add(cu);
        }
        Animation idle = anim(m, "idle", 2, "loop");
        key(idle, "head", "rotation", "smooth", new double[][]{{0, 0, 0, 0}, {1, -4, 0, 0}, {2, 0, 0, 0}});
        key(idle, "right_arm", "rotation", "smooth", new double[][]{{0, 0, 0, 0}, {1, 0, 0, 4}, {2, 0, 0, 0}});
        key(idle, "left_arm", "rotation", "smooth", new double[][]{{0, 0, 0, 0}, {1, 0, 0, -4}, {2, 0, 0, 0}});
        Animation walk = anim(m, "walk", 1, "loop");
        applyPreset(walk, "right_leg", "swing");
        applyPreset(walk, "left_leg", "swing_back");
        applyPreset(walk, "right_arm", "swing_back");
        applyPreset(walk, "left_arm", "swing");
        Animation hurt = anim(m, "hurt", 0.4, "once");
        applyPreset(hurt, "body", "flinch");
        Animation attack = anim(m, "attack", 0.5, "once");
        applyPreset(attack, "right_arm", "strike");
        Animation death = anim(m, "death", 0.9, "hold");
        key(death, "base", "rotation", "ease_in", new double[][]{{0, 0, 0, 0}, {0.8, 0, 0, 90}});
        if (m.textures.isEmpty()) m.textures.put("0", "");
    }

    private static Animation anim(Model m, String name, double length, String mode) {
        Animation a = new Animation();
        a.name = name;
        a.length = length;
        a.mode = mode;
        m.animations.add(a);
        return a;
    }

    /** Adds a track from rows of {time in seconds, x, y, z}. */
    public static void key(Animation a, String bone, String channel, String easing, double[][] rows) {
        AnimTrack tr = new AnimTrack();
        tr.bone = bone;
        tr.channel = channel;
        for (double[] row : rows) {
            AnimKey k = new AnimKey();
            k.time = row[0];
            k.x = row[1];
            k.y = row[2];
            k.z = row[3];
            k.easing = easing;
            tr.keys.add(k);
        }
        a.tracks.add(tr);
    }
}
