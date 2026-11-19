package modstudio.gen;

import com.google.gson.JsonObject;
import modstudio.core.CameraPath;
import modstudio.core.McVersion;
import modstudio.core.Systems;
import modstudio.core.Systems.CameraEvent;
import modstudio.core.Systems.Cutscene;

import java.util.*;

/**
 * Cutscenes as data pack functions, shared by every Java target. The viewer switches to spectator mode and spectates
 * an item display that is teleported along the precomputed path every tick (1.20.2+ smooths the moves on the client
 * with teleport_duration, and mod clients replace the camera's position and angles with the exact path because entity
 * packets round angles to 1.4 degrees). A marker keeps the start position. Frames are found with a binary tree of functions, so a
 * long cutscene costs a few checks per tick.
 *
 * <p>Scores: {@code <ns>.cs} cutscene number on the camera, {@code <ns>.cst} tick, {@code <ns>.cid} pairs the viewer
 * with its camera and marker, {@code <ns>.gm} keeps the viewer's game mode.
 */
public final class CutsceneGen {
    private static final int LEAF = 8;
    /** Client-side smoothing of the camera moves, in ticks; 0 on the tick of a cut. */
    static final int SMOOTH = 3;

    private final GenContext ctx;
    private final ProcCompiler pc;
    private final List<String> tick, join;
    private final String ns, t, cs, cst, cid, gm, cam, anchor, on, self, here;

    public CutsceneGen(GenContext ctx, ProcCompiler pc, List<String> tick, List<String> join) {
        this.ctx = ctx;
        this.pc = pc;
        this.tick = tick;
        this.join = join;
        this.ns = ctx.ns;
        this.t = pc.tmp();
        this.cs = ns + ".cs";
        this.cst = ns + ".cst";
        this.cid = ns + ".cid";
        this.gm = ns + ".gm";
        this.cam = ns + ".cs_cam";
        this.anchor = ns + ".cs_anchor";
        this.on = ns + ".cs_on";
        this.self = ns + ".cs_self";
        this.here = ns + ".cs_here";
    }

    public static String playFunction(String ns, String name) {
        return ns + ":cutscene/" + name + "/play";
    }

    public void generate() {
        List<Cutscene> all = ctx.all(Cutscene.class);
        if (all.isEmpty()) return;
        pc.objectives.addAll(List.of(t, cs, cst, cid, gm));
        pc.predicates.put("cutscene/sneaking", J.obj("condition", "minecraft:entity_properties", "entity", "this",
                "predicate", J.obj("flags", J.obj("is_sneaking", true))));
        List<String> dispatch = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Cutscene c = all.get(i);
            scene(c, i + 1);
            dispatch.add("execute if score @s " + cs + " matches " + (i + 1) + " run function " + ns + ":cutscene/" + c.name + "/tick");
        }
        pc.functions.put("cutscene/_tick", dispatch);
        tick.add("execute as @e[type=minecraft:item_display,tag=" + cam + "] at @s run function " + ns + ":cutscene/_tick");
        // The viewer left: drop the camera and its marker.
        pc.functions.put("cutscene/_abort", List.of(
                "execute as @e[type=minecraft:marker,tag=" + anchor + "] if score @s " + cid + " = #cid " + t + " run kill @s",
                "kill @s"));
        // A viewer who left during a cutscene gets the game mode back on the next join.
        pc.functions.put("cutscene/_recover", restore());
        join.add("execute if entity @s[tag=" + on + "] run function " + ns + ":cutscene/_recover");
        pc.functions.put("cutscene/_stop", List.of(
                "scoreboard players operation #cid " + t + " = @s " + cid,
                "execute as @e[type=minecraft:item_display,tag=" + cam + "] if score @s " + cid + " = #cid " + t
                        + " run scoreboard players set @s " + cst + " 2000000000"));
    }

    private List<String> restore() {
        List<String> r = new ArrayList<>();
        r.add("spectate");
        String[] modes = {"survival", "creative", "adventure", "spectator"};
        for (int i = 0; i < 3; i++) r.add("execute if score @s " + gm + " matches " + i + " run gamemode " + modes[i] + " @s");
        r.add("tag @s remove " + on);
        return r;
    }

    private void scene(Cutscene c, int index) {
        String dir = "cutscene/" + c.name;
        String fn = ns + ":" + dir;
        int end = CameraPath.ticks(c);
        boolean relative = !"absolute".equals(c.space);
        CameraPath.Pose first = CameraPath.at(c, 0);

        pc.functions.put(dir + "/play", List.of("execute unless entity @s[tag=" + on + "] run function " + fn + "/start"));

        StringBuilder camNbt = new StringBuilder("{Tags:[\"" + cam + "\",\"" + ns + ".cs_new\"]");
        // the name tells mod clients which path to follow
        camNbt.append(",CustomName:").append(ItemSpec.textArg(ctx, J.compact(new com.google.gson.JsonPrimitive(cameraName(ns, c)))));
        if (smoothing()) camNbt.append(",teleport_duration:" + SMOOTH);
        camNbt.append('}');
        String firstPos = relative ? pos(first, true) : pos(first, false);
        pc.functions.put(dir + "/start", List.of(
                "scoreboard players add #next " + cid + " 1",
                "scoreboard players operation @s " + cid + " = #next " + cid,
                "execute store result score @s " + gm + " run data get entity @s playerGameType",
                "summon minecraft:marker ~ ~ ~ {Tags:[\"" + anchor + "\",\"" + ns + ".cs_new\"]}",
                "tp @e[type=minecraft:marker,tag=" + ns + ".cs_new,limit=1] @s",
                "summon minecraft:item_display " + firstPos + " " + camNbt,
                "scoreboard players operation @e[tag=" + ns + ".cs_new] " + cid + " = @s " + cid,
                "scoreboard players set @e[type=minecraft:item_display,tag=" + ns + ".cs_new] " + cs + " " + index,
                "scoreboard players set @e[type=minecraft:item_display,tag=" + ns + ".cs_new] " + cst + " -1",
                "tag @e[tag=" + ns + ".cs_new] remove " + ns + ".cs_new",
                "tag @s add " + on,
                "gamemode spectator @s"));

        // per tick, as the camera
        List<String> tk = new ArrayList<>();
        tk.add("scoreboard players add @s " + cst + " 1");
        tk.add("scoreboard players operation #cid " + t + " = @s " + cid);
        tk.add("scoreboard players operation #t " + t + " = @s " + cst);
        tk.add("scoreboard players set #v " + t + " 0");
        tk.add("tag @s add " + self);
        if (relative) {
            tk.add("execute as @e[type=minecraft:marker,tag=" + anchor + "] if score @s " + cid + " = #cid " + t
                    + " at @s as @e[type=minecraft:item_display,tag=" + self + ",limit=1] run function " + fn + "/frame");
        } else {
            tk.add("function " + fn + "/frame");
        }
        tk.add("execute as @a[tag=" + on + "] if score @s " + cid + " = #cid " + t + " run function " + fn + "/view");
        tk.add("tag @s remove " + self);
        tk.add("execute if score #v " + t + " matches 0 run function " + ns + ":cutscene/_abort");
        tk.add("execute if score #v " + t + " matches 1 if score @s " + cst + " matches " + end + ".. run function " + fn + "/end");
        pc.functions.put(dir + "/tick", tk);

        // frames: a binary tree over the ticks
        tree(dir, "frame", 0, end, c, relative);

        // as the viewer
        List<String> view = new ArrayList<>();
        view.add("scoreboard players set #v " + t + " 1");
        view.add("spectate @e[type=minecraft:item_display,tag=" + self + ",limit=1] @s");
        if (c.skippable) {
            view.add("execute if predicate " + ns + ":cutscene/sneaking run scoreboard players set @e[type=minecraft:item_display,tag="
                    + self + ",limit=1] " + cst + " " + end);
        }
        Map<Integer, List<String>> byTick = new TreeMap<>();
        for (CameraEvent e : c.events) {
            int at = Math.max(0, Math.min(end, (int) Math.round(e.time * 20)));
            byTick.computeIfAbsent(at, k -> new ArrayList<>()).addAll(event(e));
        }
        for (var e : byTick.entrySet()) {
            pc.functions.put(dir + "/e" + e.getKey(), e.getValue());
            view.add("execute if score #t " + t + " matches " + e.getKey() + " run function " + fn + "/e" + e.getKey());
        }
        pc.functions.put(dir + "/view", view);

        // end, as the camera
        pc.functions.put(dir + "/end", List.of(
                "execute as @e[type=minecraft:marker,tag=" + anchor + "] if score @s " + cid + " = #cid " + t + " run tag @s add " + here,
                "execute as @a[tag=" + on + "] if score @s " + cid + " = #cid " + t + " run function " + fn + "/finish",
                "kill @e[type=minecraft:marker,tag=" + here + "]",
                "kill @s"));
        List<String> finish = restore();
        if (c.returnToStart) finish.add(1, "tp @s @e[type=minecraft:marker,tag=" + here + ",limit=1]");
        finish.add("title @s reset");
        if (!c.onEnd.isEmpty()) {
            pc.compile(dir + "/on_end", c.onEnd, List.of(), List.of());
            finish.add("function " + fn + "/on_end");
        }
        pc.functions.put(dir + "/finish", finish);
    }

    private boolean smoothing() {
        return ctx.v.atLeast(McVersion.V1_20_4);
    }

    /** Custom name of the camera entity. */
    static String cameraName(String ns, Cutscene c) {
        return ns + ":" + c.name;
    }

    /** Ticks where a key with the cut easing jumps the camera. */
    static Set<Integer> cuts(Cutscene c) {
        Set<Integer> r = new HashSet<>();
        for (Systems.CameraKey k : c.keys) if ("cut".equals(k.easing) && k.time > 0) r.add((int) Math.ceil(k.time * 20 - 1e-6));
        return r;
    }

    /**
     * The path sampled once per tick for mod clients: x y z yaw pitch per tick, separated by spaces, in chunks that
     * fit a class file constant. Positions are offsets from the start position when the cutscene is relative.
     */
    static List<String> samples(Cutscene c) {
        List<String> chunks = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int end = CameraPath.ticks(c);
        for (int k = 0; k <= end; k++) {
            CameraPath.Pose p = CameraPath.at(c, k / 20.0);
            if (sb.length() > 0) sb.append(' ');
            sb.append(num(p.x())).append(' ').append(num(p.y())).append(' ').append(num(p.z())).append(' ')
                    .append(num(p.yaw())).append(' ').append(num(p.pitch()));
            if (sb.length() > 40000) {
                chunks.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) chunks.add(sb.toString());
        return chunks;
    }

    private void tree(String dir, String name, int lo, int hi, Cutscene c, boolean relative) {
        List<String> lines = new ArrayList<>();
        if (hi - lo + 1 <= LEAF) {
            Set<Integer> cuts = smoothing() ? cuts(c) : Set.of();
            for (int k = lo; k <= hi; k++) {
                CameraPath.Pose p = CameraPath.at(c, k / 20.0);
                // a cut jumps at once: no smoothing from the tick before it to the tick after
                if (cuts.contains(k + 1)) lines.add("execute if score @s " + cst + " matches " + k + " run data merge entity @s {teleport_duration:0}");
                if (cuts.contains(k - 1)) lines.add("execute if score @s " + cst + " matches " + k + " run data merge entity @s {teleport_duration:" + SMOOTH + "}");
                lines.add("execute if score @s " + cst + " matches " + k + " run tp @s " + pos(p, relative) + " " + num(p.yaw()) + " " + num(p.pitch()));
            }
        } else {
            int mid = (lo + hi) / 2;
            String a = "f" + lo + "_" + mid, b = "f" + (mid + 1) + "_" + hi;
            lines.add("execute if score @s " + cst + " matches " + lo + ".." + mid + " run function " + ns + ":" + dir + "/" + a);
            lines.add("execute if score @s " + cst + " matches " + (mid + 1) + ".." + hi + " run function " + ns + ":" + dir + "/" + b);
            tree(dir, a, lo, mid, c, relative);
            tree(dir, b, mid + 1, hi, c, relative);
        }
        pc.functions.put(dir + "/" + name, lines);
    }

    private static String pos(CameraPath.Pose p, boolean relative) {
        if (relative) return rel(p.x()) + " " + rel(p.y()) + " " + rel(p.z());
        return num(p.x()) + " " + num(p.y()) + " " + num(p.z());
    }

    private static String rel(double d) {
        return Math.abs(d) < 1e-4 ? "~" : "~" + num(d);
    }

    static String num(double d) {
        double r = Math.round(d * 1000) / 1000.0;
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.valueOf(r);
    }

    private List<String> event(CameraEvent e) {
        List<String> r = new ArrayList<>();
        int ticks = Math.max(1, (int) Math.round(e.duration * 20));
        switch (e.kind) {
            case "title", "subtitle" -> {
                r.add("title @s times 10 " + ticks + " 10");
                if (e.kind.equals("subtitle")) {
                    r.add("title @s subtitle " + text(e.text, e.color));
                    r.add("title @s title {\"text\":\"\"}");
                } else r.add("title @s title " + text(e.text, e.color));
            }
            case "actionbar" -> r.add("title @s actionbar " + text(e.text, e.color));
            case "sound" -> {
                String snd = e.text.isBlank() ? "minecraft:ui.button.click" : modstudio.core.Ids.full(e.text.trim());
                r.add("playsound " + snd + " master @s ~ ~ ~ 1 1");
            }
            case "fade" -> r.add("effect give @s minecraft:blindness " + Math.max(1, (int) Math.ceil(e.duration)) + " 0 true");
            case "command" -> {
                for (String line : e.text.split("\\R")) {
                    String c = line.trim();
                    if (c.startsWith("/")) c = c.substring(1);
                    if (!c.isEmpty()) r.add(c);
                }
            }
            default -> {
            }
        }
        return r;
    }

    private static String text(String text, String color) {
        JsonObject o = J.obj("text", GenContext.legacy(text));
        if (color != null && !color.isBlank() && !color.equals("white")) o.addProperty("color", color);
        return J.compact(o);
    }

    // ------------------------------------------------------------------ Bedrock

    /**
     * JavaScript object with one function per cutscene for Bedrock Edition, which has a real camera command: the
     * keys become camera eases scheduled with system.runTimeout.
     */
    public static String bedrockScript(GenContext ctx) {
        List<Cutscene> all = ctx.all(Cutscene.class);
        StringBuilder sb = new StringBuilder("const activeCutscenes = new Map();\n\n");
        sb.append("function stopCutscene(player, runEnd) {\n");
        sb.append("  if (!player) return;\n");
        sb.append("  const state = activeCutscenes.get(player.id);\n");
        sb.append("  if (!state) { cmd(player, \"camera @s clear\"); return; }\n");
        sb.append("  activeCutscenes.delete(player.id);\n");
        sb.append("  for (const tid of state.timers) system.clearRun(tid);\n");
        sb.append("  if (state.skipRun !== undefined) system.clearRun(state.skipRun);\n");
        sb.append("  cmd(player, \"camera @s clear\");\n");
        sb.append("  if (state.returnToStart && state.startLoc && state.startDim) {\n");
        sb.append("    try { player.teleport(state.startLoc, { dimension: state.startDim }); } catch (e) {}\n");
        sb.append("  }\n");
        sb.append("  if (runEnd && state.onEnd) state.onEnd(player);\n");
        sb.append("}\n\n");
        sb.append("const cutscenes = {\n");
        for (Cutscene c : all) {
            boolean relative = !"absolute".equals(c.space);
            List<modstudio.core.Systems.CameraKey> keys = CameraPath.sorted(c);
            sb.append("  ").append(jsStr(c.name)).append(": (self) => {\n");
            sb.append("    if (!self || self.typeId !== \"minecraft:player\") return;\n");
            sb.append("    stopCutscene(self, false);\n");
            sb.append("    const o = self.location;\n");
            sb.append("    const state = { timers: [], returnToStart: ").append(c.returnToStart)
                    .append(", startLoc: { x: o.x, y: o.y, z: o.z }, startDim: self.dimension, onEnd: ")
                    .append(c.onEnd.isEmpty() ? "null" : "(p) => cutsceneEnds[" + jsStr(c.name) + "](p)")
                    .append(" };\n");
            sb.append("    activeCutscenes.set(self.id, state);\n");
            if (c.skippable) {
                sb.append("    let wasSneaking = self.isSneaking === true;\n");
                sb.append("    state.skipRun = system.runInterval(() => {\n");
                sb.append("      if (!activeCutscenes.has(self.id)) return;\n");
                sb.append("      const s = self.isSneaking === true;\n");
                sb.append("      if (s && !wasSneaking) { stopCutscene(self, true); return; }\n");
                sb.append("      wasSneaking = s;\n");
                sb.append("    }, 1);\n");
            }
            double prev = 0;
            for (int i = 0; i < keys.size(); i++) {
                var k = keys.get(i);
                CameraPath.Pose p = CameraPath.at(c, k.time);
                String ease = switch (k.easing) {
                    case "ease_in" -> "in_cubic";
                    case "ease_out" -> "out_cubic";
                    case "ease_in_out", "smooth" -> "in_out_sine";
                    default -> "linear";
                };
                double dur = Math.max(0.05, k.time - prev);
                String posExpr = relative
                        ? "${(o.x + " + num(p.x()) + ").toFixed(2)} ${(o.y + " + num(p.y()) + ").toFixed(2)} ${(o.z + " + num(p.z()) + ").toFixed(2)}"
                        : num(p.x()) + " " + num(p.y()) + " " + num(p.z());
                String cmd = (i == 0 || "cut".equals(k.easing))
                        ? "camera @s set minecraft:free pos " + posExpr + " rot " + num(p.pitch()) + " " + num(p.yaw())
                        : "camera @s set minecraft:free ease " + num(dur) + " " + ease + " pos " + posExpr + " rot " + num(p.pitch()) + " " + num(p.yaw());
                int delay = Math.max(0, (int) Math.round((i == 0 ? k.time : prev) * 20));
                sb.append("    state.timers.push(system.runTimeout(() => cmd(self, `").append(cmd).append("`), ").append(delay).append("));\n");
                prev = k.time;
            }
            for (CameraEvent e : c.events) {
                int delay = Math.max(0, (int) Math.round(e.time * 20));
                String cmd = switch (e.kind) {
                    case "title" -> "title @s title " + e.text.replace("`", "'");
                    case "subtitle" -> "title @s subtitle " + e.text.replace("`", "'");
                    case "actionbar" -> "title @s actionbar " + e.text.replace("`", "'");
                    case "sound" -> "playsound " + (e.text.isBlank() ? "random.click" : e.text.trim().replace("minecraft:", "")) + " @s";
                    case "fade" -> "camera @s fade time 0.5 " + num(Math.max(0, e.duration - 1)) + " 0.5";
                    case "command" -> e.text.trim().replace("`", "'");
                    default -> "";
                };
                if (cmd.isBlank()) continue;
                sb.append("    state.timers.push(system.runTimeout(() => cmd(self, `").append(cmd.replace("${", "$\\{")).append("`), ").append(delay).append("));\n");
            }
            int end = CameraPath.ticks(c) + 1;
            sb.append("    state.timers.push(system.runTimeout(() => stopCutscene(self, true), ").append(end).append("));\n");
            sb.append("  },\n");
        }
        sb.append("};\n\n");
        return sb.toString();
    }

    private static String jsStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
