package modstudio.gen;

import modstudio.core.*;
import modstudio.core.Elements.Animation;
import modstudio.core.Elements.Bone;
import modstudio.core.Elements.Model;
import modstudio.core.Systems.Mob;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Animated mobs built from display entities. Every bone of the model is an item display riding the (invisible) mob
 * and showing a per-bone item model. Animations are sampled every two ticks into frame functions that set the
 * display transformations with a two tick interpolation, so the client moves the bones smoothly in between.
 *
 * <p>Scores on the mob: {@code anim} (playing animation, 1-based), {@code at} (ticks into it) and {@code af}
 * (animation started by a step, hurt, attack or death, which wins over idle and walk until it ends).
 */
final class RigGen {
    /** Custom model data of bone models before 1.21.4: above item values, below 2^24 so floats stay exact. */
    static final int CMD_BASE = 11_000_000;
    static final String BASE_ITEM = "minecraft:paper";
    /** Ticks between frames; also the interpolation time of each frame. */
    static final int STEP = 2;

    private final GenContext ctx;
    private final ProcCompiler pc;
    private final String ns, anim, at, af, px, pz, tmp;

    RigGen(GenContext ctx, ProcCompiler pc) {
        this.ctx = ctx;
        this.pc = pc;
        this.ns = ctx.ns;
        anim = ns + ".anim";
        at = ns + ".at";
        af = ns + ".af";
        px = ns + ".px";
        pz = ns + ".pz";
        tmp = pc.tmp();
    }

    // ------------------------------------------------------------------ shared helpers

    /** The animated model of a mob, or null when it has none. */
    static Model model(Project p, Mob m) {
        if (m.rig == null || m.rig.isBlank()) return null;
        Model md = p.model(m.rig.trim());
        return Rig.animated(md) ? md : null;
    }

    /** Bones that hold cubes and so get a display entity, parents first. */
    static List<Bone> shown(Model md) {
        List<Bone> r = new ArrayList<>();
        for (Bone b : Rig.ordered(md)) {
            if (md.cubes.stream().anyMatch(c -> Rig.boneOf(md, c).equals(b.name))) r.add(b);
        }
        return r;
    }

    static String safe(String s) {
        String id = Ids.toId(s);
        return id.isEmpty() ? "x" : id;
    }

    /** Item model path (under models/item and items) of a bone. */
    static String modelPath(Model md, Bone b) {
        return "rig/" + safe(md.name) + "/" + safe(b.name);
    }

    static int cmd(Model md, Bone b) {
        return CMD_BASE + Math.floorMod((md.name + "/" + b.name).hashCode(), 5_000_000);
    }

    /** All bones of all animated mob models and assembled multiblock models, once per model. */
    static Map<Model, List<Bone>> boneModels(Project p) {
        Map<Model, List<Bone>> r = new LinkedHashMap<>();
        for (Mob m : p.all(Mob.class)) {
            Model md = model(p, m);
            if (md != null) r.putIfAbsent(md, shown(md));
        }
        for (Systems.Multiblock mb : p.all(Systems.Multiblock.class)) {
            Model md = AssemblyGen.model(p, mb);
            if (md != null) r.putIfAbsent(md, shown(md));
        }
        return r;
    }

    String itemSnbt(Model md, Bone b) {
        if (ctx.v.itemDefinitions()) {
            return "{id:\"" + BASE_ITEM + "\",count:1,components:{\"minecraft:item_model\":\"" + ns + ":" + modelPath(md, b) + "\"}}";
        }
        if (ctx.components()) return "{id:\"" + BASE_ITEM + "\",count:1,components:{\"minecraft:custom_model_data\":" + cmd(md, b) + "}}";
        return "{id:\"" + BASE_ITEM + "\",Count:1b,tag:{CustomModelData:" + cmd(md, b) + "}}";
    }

    private static final Map<String, Double> PASSENGER = Map.ofEntries(
            Map.entry("minecraft:zombie", 2.0125), Map.entry("minecraft:husk", 2.075), Map.entry("minecraft:piglin", 2.0125),
            Map.entry("minecraft:cow", 1.36875), Map.entry("minecraft:enderman", 2.80625), Map.entry("minecraft:pig", 0.86875),
            Map.entry("minecraft:spider", 0.765), Map.entry("minecraft:warden", 3.15), Map.entry("minecraft:sheep", 1.2375),
            Map.entry("minecraft:vex", 0.7375));
    private static final Set<String> ZOMBIES = Set.of("minecraft:zombie", "minecraft:husk", "minecraft:drowned",
            "minecraft:zombie_villager", "minecraft:zombified_piglin");

    /** Height above the feet of the point where passengers ride, which changed in 1.20.2 and 1.20.5. */
    static double ridePoint(GenContext ctx, String base, double height) {
        boolean spider = base.equals("minecraft:spider") || base.equals("minecraft:cave_spider");
        if (!ctx.v.atLeast(McVersion.V1_20_4)) return spider ? 0.5 * height : 0.75 * height;
        if (!ctx.components()) {
            if (ZOMBIES.contains(base)) return height + 0.0625;
            if (spider) return 0.85 * height;
            if (base.equals("minecraft:pig")) return height - 0.03125;
            return height;
        }
        return PASSENGER.getOrDefault(base, height);
    }

    static String f(double d) {
        double r = Math.round(d * 10000) / 10000.0;
        if (r == 0) return "0";
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.valueOf(r);
    }

    static String matrix(double[] m) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 16; i++) {
            if (i > 0) sb.append(',');
            sb.append(f(m[i])).append('f');
        }
        return sb.append(']').toString();
    }

    static String boneTag(String ns, Bone b) {
        return ns + ".rb." + safe(b.name);
    }

    /** 1-based number of an animation in the model, 0 when there is none with that name. */
    static int id(Model md, String name) {
        if (name == null || name.isBlank()) return 0;
        for (int i = 0; i < md.animations.size(); i++) if (md.animations.get(i).name.equals(name.trim())) return i + 1;
        return 0;
    }

    // ------------------------------------------------------------------ per mob

    /** Size of the mob relative to a normal adult: babies are half size, the scale attribute exists from 1.20.5. */
    private double size(Mob m) {
        double s = m.baby ? 0.5 : 1;
        if (ctx.components()) s *= Math.max(0.01, m.scale);
        return s;
    }

    private double drop(Mob m, String base) {
        return ridePoint(ctx, base, SystemsGen.HEIGHTS.getOrDefault(base, 1.8)) * size(m);
    }

    private double[] displayMatrix(Mob m, String base, Bone b, double[] world) {
        return Rig.display(b, world, Math.max(0.05, m.rigScale) * size(m), drop(m, base));
    }

    /** Lines run as a newly spawned mob: hide it and mount one display per bone in the rest pose. */
    List<String> spawn(Mob m, Model md, String base, String mobTag) {
        List<String> r = new ArrayList<>();
        Map<String, double[]> rest = Rig.pose(md, null, 0);
        r.add("effect give @s minecraft:invisibility infinite 0 true");
        r.add("tag @s add " + ns + ".rigged");
        r.add("tag @s add " + ns + ".rig_self");
        String smooth = ctx.v.atLeast(McVersion.V1_20_4) ? ",teleport_duration:" + STEP : "";
        for (Bone b : shown(md)) {
            r.add("summon minecraft:item_display ~ ~ ~ {Tags:[\"" + ns + ".rig\",\"" + mobTag + ".bone\",\"" + boneTag(ns, b) + "\",\"" + ns + ".newd\"],item:"
                    + itemSnbt(md, b) + ",interpolation_duration:" + STEP + smooth + ",transformation:"
                    + matrix(displayMatrix(m, base, b, rest.get(b.name))) + "}");
        }
        r.add("execute as @e[type=minecraft:item_display,tag=" + ns + ".newd] run ride @s mount @e[tag=" + ns + ".rig_self,limit=1]");
        r.add("tag @e[type=minecraft:item_display,tag=" + ns + ".newd] remove " + ns + ".newd");
        r.add("tag @s remove " + ns + ".rig_self");
        r.add("scoreboard players set @s " + anim + " 0");
        r.add("scoreboard players set @s " + af + " 0");
        r.add("execute store result score @s " + px + " run data get entity @s Pos[0] 20");
        r.add("execute store result score @s " + pz + " run data get entity @s Pos[2] 20");
        return r;
    }

    /** Lines run every tick as the mob: follow its yaw, choose an animation and play it. */
    List<String> tick(Mob m, Model md, String base, String dir) {
        for (String o : List.of(anim, at, af, px, pz, tmp)) pc.objectives.add(o);
        String rdir = dir + "/rig";
        int idle = id(md, m.idleAnim), walk = id(md, m.walkAnim), hurt = id(md, m.hurtAnim), death = id(md, m.deathAnim);
        List<String> t = new ArrayList<>();
        t.add("data modify storage " + ns + ":rig yaw set from entity @s Rotation[0]");
        t.add("execute on passengers if entity @s[tag=" + ns + ".rig] run data modify entity @s Rotation[0] set from storage " + ns + ":rig yaw");
        t.add("scoreboard players set #want " + tmp + " " + idle);
        if (walk > 0) {
            t.add("execute store result score #x " + tmp + " run data get entity @s Pos[0] 20");
            t.add("execute store result score #z " + tmp + " run data get entity @s Pos[2] 20");
            t.add("scoreboard players operation #dx " + tmp + " = #x " + tmp);
            t.add("scoreboard players operation #dx " + tmp + " -= @s " + px);
            t.add("scoreboard players operation #dz " + tmp + " = #z " + tmp);
            t.add("scoreboard players operation #dz " + tmp + " -= @s " + pz);
            t.add("scoreboard players operation @s " + px + " = #x " + tmp);
            t.add("scoreboard players operation @s " + pz + " = #z " + tmp);
            t.add("execute unless score #dx " + tmp + " matches -1..1 run scoreboard players set #want " + tmp + " " + walk);
            t.add("execute unless score #dz " + tmp + " matches -1..1 run scoreboard players set #want " + tmp + " " + walk);
        }
        if (hurt > 0 || death > 0) t.add("execute store result score #d " + tmp + " run data get entity @s DeathTime");
        if (hurt > 0) {
            t.add("execute store result score #h " + tmp + " run data get entity @s HurtTime");
            t.add("execute if score #d " + tmp + " matches 0 if score #h " + tmp + " matches 9..10 unless score @s " + af + " matches " + hurt
                    + " run function " + ns + ":" + rdir + "/play/" + safe(m.hurtAnim));
        }
        if (death > 0) {
            t.add("execute if score #d " + tmp + " matches 1 run function " + ns + ":" + rdir + "/play/" + safe(m.deathAnim));
            t.add("execute if score #d " + tmp + " matches 19.. on passengers if entity @s[tag=" + ns + ".rig] run kill @s");
        } else {
            t.add("execute if score #d " + tmp + " matches 1.. on passengers if entity @s[tag=" + ns + ".rig] run kill @s");
            if (hurt == 0) t.add(0, "execute store result score #d " + tmp + " run data get entity @s DeathTime");
        }
        t.add("execute if score @s " + af + " matches 1.. run scoreboard players operation #want " + tmp + " = @s " + af);
        t.add("execute unless score @s " + anim + " = #want " + tmp + " run scoreboard players set @s " + at + " 0");
        t.add("scoreboard players operation @s " + anim + " = #want " + tmp);
        t.add("function " + ns + ":" + rdir + "/anim");

        animations(md, rdir, tag -> "execute on passengers if entity @s[tag=" + tag + "] run ", (b, w) -> displayMatrix(m, base, b, w));
        return t;
    }

    /**
     * Frame functions of every animation of a model, rdir/anim (runs the playing animation of the entity that runs
     * it) and rdir/play/&lt;animation&gt;. boneCmd turns a bone tag into the command prefix that runs as that bone's
     * display; matrix gives the display transformation of a bone for a pose.
     */
    void animations(Model md, String rdir, Function<String, String> boneCmd, BiFunction<Bone, double[], double[]> matrix) {
        for (String o : List.of(anim, at, af, tmp)) pc.objectives.add(o);
        List<String> dispatch = new ArrayList<>();
        for (int i = 0; i < md.animations.size(); i++) {
            Animation a = md.animations.get(i);
            String adir = rdir + "/" + safe(a.name);
            dispatch.add("execute if score @s " + anim + " matches " + (i + 1) + " run function " + ns + ":" + adir + "/run");
            pc.functions.put(rdir + "/play/" + safe(a.name), List.of(
                    "scoreboard players set @s " + af + " " + (i + 1),
                    "scoreboard players set @s " + anim + " " + (i + 1),
                    "scoreboard players set @s " + at + " 0"));
            animation(md, a, i + 1, adir, boneCmd, matrix);
        }
        pc.functions.put(rdir + "/anim", dispatch);
        // the rest pose, for when an entity stops animating
        Map<String, double[]> rest = Rig.pose(md, null, 0);
        List<String> restLines = new ArrayList<>();
        for (Bone b : shown(md)) {
            restLines.add(boneCmd.apply(boneTag(ns, b)) + "data merge entity @s {start_interpolation:0,interpolation_duration:" + STEP
                    + ",transformation:" + matrix(matrix.apply(b, rest.get(b.name))) + "}");
        }
        pc.functions.put(rdir + "/rest", restLines);
    }

    private void animation(Model md, Animation a, int id, String adir, Function<String, String> boneCmd, BiFunction<Bone, double[], double[]> matrix) {
        int len = Rig.ticks(a);
        List<String> run = new ArrayList<>();
        switch (a.mode == null ? "loop" : a.mode) {
            case "once" -> {
                run.add("execute if score @s " + at + " matches " + len + ".. if score @s " + af + " matches " + id + " run scoreboard players set @s " + af + " 0");
                run.add("execute if score @s " + at + " matches " + len + ".. run scoreboard players set @s " + at + " " + len);
            }
            case "hold" -> run.add("execute if score @s " + at + " matches " + len + ".. run scoreboard players set @s " + at + " " + len);
            default -> run.add("execute if score @s " + at + " matches " + len + ".. run scoreboard players set @s " + at + " 0");
        }

        // frames: at tick k the bones head for the pose at k + STEP
        List<Bone> bones = shown(md);
        Map<Integer, List<String>> frames = new TreeMap<>();
        Map<String, double[]> last = new HashMap<>();
        for (int k = 0; k < len; k += STEP) {
            Map<String, double[]> pose = Rig.pose(md, a, Math.min(len, k + STEP) / 20.0);
            List<String> lines = new ArrayList<>();
            for (Bone b : bones) {
                double[] mat = matrix.apply(b, pose.get(b.name));
                if (k > 0 && Rig.same(mat, last.get(b.name))) continue;
                last.put(b.name, mat);
                lines.add(boneCmd.apply(boneTag(ns, b)) + "data merge entity @s {start_interpolation:0,interpolation_duration:"
                        + STEP + ",transformation:" + matrix(mat) + "}");
            }
            if (!lines.isEmpty()) frames.put(k, lines);
        }
        for (var e : frames.entrySet()) pc.functions.put(adir + "/k" + e.getKey(), e.getValue());
        List<Integer> keys = new ArrayList<>(frames.keySet());
        if (!keys.isEmpty()) {
            tree(adir, "frames", keys, 0, keys.size() - 1);
            run.add("execute if score @s " + at + " matches 0.." + (len - 1) + " run function " + ns + ":" + adir + "/frames");
        }
        run.add("scoreboard players add @s " + at + " 1");
        pc.functions.put(adir + "/run", run);
    }

    private static final int LEAF = 8;

    private void tree(String dir, String name, List<Integer> keys, int lo, int hi) {
        List<String> lines = new ArrayList<>();
        if (hi - lo + 1 <= LEAF) {
            for (int i = lo; i <= hi; i++) {
                int k = keys.get(i);
                lines.add("execute if score @s " + at + " matches " + k + " run function " + ns + ":" + dir + "/k" + k);
            }
        } else {
            int mid = (lo + hi) / 2;
            int a0 = keys.get(lo), a1 = keys.get(mid), b0 = keys.get(mid + 1), b1 = keys.get(hi);
            String a = "t" + a0 + "_" + a1, b = "t" + b0 + "_" + b1;
            lines.add("execute if score @s " + at + " matches " + a0 + ".." + a1 + " run function " + ns + ":" + dir + "/" + a);
            lines.add("execute if score @s " + at + " matches " + b0 + ".." + b1 + " run function " + ns + ":" + dir + "/" + b);
            tree(dir, a, keys, lo, mid);
            tree(dir, b, keys, mid + 1, hi);
        }
        pc.functions.put(dir + "/" + name, lines);
    }

    /** Attack animation: an advancement for players hurt by the mob plays it on the nearest such mob. */
    List<String> attackCall(Mob m, String dir, String mobTag) {
        return List.of("execute as @e[tag=" + mobTag + ",limit=1,sort=nearest,distance=..8] run function " + ns + ":" + dir + "/rig/play/" + safe(m.attackAnim));
    }

    // ------------------------------------------------------------------ project wide

    /**
     * Shared functions: removal of displays whose mob is gone, and rig/play/<animation> plus rig/stop for the
     * procedure steps, which find the right mob type by its tag.
     */
    void shared(List<String> tick) {
        Map<String, List<String>> plays = new TreeMap<>();
        boolean any = false;
        for (Mob m : ctx.all(Mob.class)) {
            Model md = model(ctx.p, m);
            if (md == null) continue;
            any = true;
            for (Animation a : md.animations) {
                plays.computeIfAbsent(safe(a.name), k -> new ArrayList<>()).add("execute if entity @s[tag=" + SystemsGen.mobTag(ns, m.name)
                        + "] run function " + ns + ":mobs/" + m.name + "/rig/play/" + safe(a.name));
            }
        }
        if (!any) return;
        for (var e : plays.entrySet()) pc.functions.put("rig/play/" + e.getKey(), e.getValue());
        pc.functions.put("rig/stop", List.of("scoreboard players set @s[tag=" + ns + ".rigged] " + af + " 0"));
        tick.add("tag @e[type=minecraft:item_display,tag=" + ns + ".rig] add " + ns + ".orphan");
        tick.add("execute as @e[tag=" + ns + ".rigged] on passengers run tag @s remove " + ns + ".orphan");
        tick.add("kill @e[type=minecraft:item_display,tag=" + ns + ".orphan]");
    }

    /** Animation names that some animated mob can play (for the procedure steps). */
    static Set<String> animationNames(Project p) {
        Set<String> r = new TreeSet<>();
        for (Mob m : p.all(Mob.class)) {
            Model md = model(p, m);
            if (md != null) for (Animation a : md.animations) r.add(safe(a.name));
        }
        return r;
    }
}
