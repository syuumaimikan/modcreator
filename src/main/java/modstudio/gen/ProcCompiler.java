package modstudio.gen;

import com.google.gson.JsonObject;
import modstudio.core.ElementType;
import modstudio.core.Elements;
import modstudio.core.Elements.Condition;
import modstudio.core.Elements.Step;
import modstudio.core.Ids;
import modstudio.core.Element;
import modstudio.core.McVersion;
import modstudio.core.Systems;

import java.util.*;

/**
 * Compiles procedure steps into Java Edition mcfunction files. The same output runs on Forge, NeoForge, Fabric,
 * Paper (through the embedded data pack) and plain data packs, so behaviour is identical across platforms.
 */
public final class ProcCompiler {
    private final GenContext ctx;
    private final String ns;
    private final boolean customItems;
    public final Map<String, List<String>> functions = new LinkedHashMap<>();
    public final Map<String, JsonObject> predicates = new LinkedHashMap<>();
    /** Other data files of the namespace, by path without the extension (for example "dialog/welcome"). */
    public final Map<String, JsonObject> dataJson = new LinkedHashMap<>();
    public final Set<String> objectives = new LinkedHashSet<>();
    private int counter;
    /** Commands a condition needs to run first (multiblock checks, sky checks); emitted before the condition is used. */
    private final List<String> pre = new ArrayList<>();

    /** @param customItems true when project items are represented as custom-model-data items (data pack, Paper). */
    public ProcCompiler(GenContext ctx, boolean customItems) {
        this.ctx = ctx;
        this.ns = ctx.ns;
        this.customItems = customItems;
    }

    public String tmp() {
        return ns + ".tmp";
    }

    public boolean customItems() {
        return customItems;
    }

    public void compile(String fnPath, List<Step> steps, List<String> prefix, List<String> suffix) {
        List<String> out = new ArrayList<>(prefix);
        emitAll(fnPath, steps, out);
        out.addAll(suffix);
        functions.put(fnPath, out);
    }

    /** Hidden command that lets functions reach the mod's Java side (menus, web requests). */
    public String bridgeCommand() {
        return ns + "_bridge";
    }

    /** Storage holding the arguments of the running command branch. */
    public String argsStorage() {
        return "storage " + ns + ":args a";
    }

    /**
     * Compiles a command branch whose steps may use $(name) placeholders. Lines with placeholders become macro lines
     * (1.20.2+) and calls to generated functions that contain macro lines pass the argument storage along.
     */
    public void compileWithArgs(String fnPath, List<Step> steps, List<String> prefix) {
        Set<String> before = new HashSet<>(functions.keySet());
        compile(fnPath, steps, prefix, List.of());
        List<String> added = new ArrayList<>();
        for (String k : functions.keySet()) if (!before.contains(k)) added.add(k);
        Set<String> macro = new HashSet<>();
        for (String k : added) {
            List<String> lines = functions.get(k);
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                if (!l.contains("$(") || l.startsWith("#")) continue;
                if (ctx.v.macros()) {
                    if (!l.startsWith("$")) lines.set(i, "$" + l);
                    macro.add(k);
                } else {
                    lines.set(i, "# needs Minecraft 1.20.2 or later: " + l);
                }
            }
        }
        for (String k : added) {
            List<String> lines = functions.get(k);
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                if (l.startsWith("#") || l.contains(" with ")) continue;
                for (String m : macro) {
                    if (l.endsWith("function " + ns + ":" + m)) {
                        lines.set(i, l + " with " + argsStorage());
                        break;
                    }
                }
            }
        }
    }

    /** True when the named function contains macro lines and must be called with the argument storage. */
    public boolean isMacro(String fnPath) {
        List<String> lines = functions.get(fnPath);
        if (lines == null) return false;
        for (String l : lines) if (l.startsWith("$")) return true;
        return false;
    }

    /**
     * Conditions on the build itself (target platform, Minecraft version), or null for conditions checked in game.
     */
    public Boolean compileTime(Condition c) {
        if (c == null) return null;
        Boolean r = switch (c.kind) {
            case "platform" -> platformMatches(ctx.target, c.get("platform"));
            case "mc_version" -> {
                McVersion min = McVersion.orNull(c.get("min"));
                McVersion max = McVersion.orNull(c.get("max"));
                yield (min == null || ctx.v.atLeast(min)) && (max == null || !ctx.v.atLeast(max) || ctx.v == max);
            }
            default -> null;
        };
        return r == null ? null : r != c.negate;
    }

    public static boolean platformMatches(modstudio.core.Target t, String platform) {
        return switch (platform == null ? "" : platform) {
            case "mod" -> t.isMod();
            case "plugin", "paper" -> t == modstudio.core.Target.PAPER;
            case "datapack" -> t == modstudio.core.Target.DATAPACK;
            case "java_code" -> t.isMod() || t == modstudio.core.Target.PAPER;
            case "bedrock" -> t == modstudio.core.Target.BEDROCK;
            default -> t.id.equals(platform);
        };
    }

    private void emitAll(String base, List<Step> steps, List<String> out) {
        for (Step s : steps) emit(base, s, out);
    }

    private void emit(String base, Step s, List<String> out) {
        switch (s.kind) {
            case "if" -> {
                Boolean fixed = compileTime(s.cond);
                if (fixed != null) {
                    // platform and version checks are decided while generating: only the matching branch is emitted
                    out.add("# " + s.cond.kind + " " + (fixed ? "matches" : "does not match") + " " + ctx.target.id + " " + ctx.v.id);
                    emitAll(base, fixed ? s.then : s.orElse, out);
                    return;
                }
                pre.clear();
                String cond = condition(s.cond);
                if (cond == null) {
                    pre.clear();
                    out.add("# condition not supported on this platform");
                    return;
                }
                out.addAll(pre);
                pre.clear();
                if (s.orElse.isEmpty()) {
                    List<String> single = s.then.size() == 1 && !s.then.get(0).kind.equals("if") && !s.then.get(0).kind.equals("repeat")
                            ? commands(s.then.get(0)) : null;
                    if (single != null && single.size() == 1) {
                        out.add("execute " + cond + " run " + single.get(0));
                    } else if (!s.then.isEmpty()) {
                        String sub = base + "/b" + (++counter);
                        compile(sub, s.then, List.of(), List.of());
                        out.add("execute " + cond + " run function " + ns + ":" + sub);
                    }
                } else {
                    String flag = "#c" + (++counter);
                    objectives.add(tmp());
                    out.add("scoreboard players set " + flag + " " + tmp() + " 0");
                    out.add("execute " + cond + " run scoreboard players set " + flag + " " + tmp() + " 1");
                    if (!s.then.isEmpty()) {
                        String sub = base + "/b" + (++counter);
                        compile(sub, s.then, List.of(), List.of());
                        out.add("execute if score " + flag + " " + tmp() + " matches 1 run function " + ns + ":" + sub);
                    }
                    String sub2 = base + "/b" + (++counter);
                    compile(sub2, s.orElse, List.of(), List.of());
                    out.add("execute if score " + flag + " " + tmp() + " matches 0 run function " + ns + ":" + sub2);
                }
            }
            case "for_each" -> {
                if (s.then.isEmpty()) return;
                String sub = base + "/b" + (++counter);
                compile(sub, s.then, List.of(), List.of());
                String sel = s.get("selector").isBlank() ? "@a" : s.get("selector").trim();
                out.add("execute as " + sel + " at @s run function " + ns + ":" + sub);
            }
            case "at_position" -> {
                if (s.then.isEmpty()) return;
                String sub = base + "/b" + (++counter);
                compile(sub, s.then, List.of(), List.of());
                out.add("execute positioned " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")) + " run function " + ns + ":" + sub);
            }
            case "schedule" -> {
                if (s.then.isEmpty()) return;
                String sub = base + "/b" + (++counter);
                compile(sub, s.then, List.of(), List.of());
                out.add("schedule function " + ns + ":" + sub + " " + Math.max(1, intOr(s.get("ticks"), 20)) + "t append");
            }
            case "repeat" -> {
                int n = Math.max(1, Math.min(1000, intOr(s.get("times"), 1)));
                if (s.then.isEmpty()) return;
                String sub = base + "/b" + (++counter);
                compile(sub, s.then, List.of(), List.of());
                for (int i = 0; i < n; i++) out.add("function " + ns + ":" + sub);
            }
            default -> out.addAll(commands(s));
        }
    }

    /**
     * The score holder of a whole-number command argument when {@code raw} is exactly {@code $(name)}. Command
     * branches copy int arguments to {@code $name <ns>.args}, so such values also work without function macros.
     */
    private String argScore(String raw) {
        if (ctx.v.macros()) return null;
        java.util.regex.Matcher m = ARG_REF.matcher(raw.trim());
        return m.matches() ? "$" + m.group(1) + " " + ns + ".args" : null;
    }

    private static final java.util.regex.Pattern ARG_REF = java.util.regex.Pattern.compile("\\$\\(([A-Za-z0-9_]+)\\)");

    private static int intOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static double parseD(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String num(String s, String def) {
        if (s == null || s.isBlank()) return def;
        try {
            Double.parseDouble(s.trim());
            return s.trim();
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String sel(Step s) {
        String t = s.get("target").trim();
        return t.isEmpty() ? "@s" : t;
    }

    private static String text(String text, String color, boolean bold) {
        JsonObject o = J.obj("text", GenContext.legacy(text));
        if (color != null && !color.isBlank() && !color.equals("white")) o.addProperty("color", color);
        if (bold) o.addProperty("bold", true);
        return J.compact(o);
    }

    private Elements.Item projectItem(String id) {
        if (id == null || !id.startsWith(ns + ":")) return null;
        var e = ctx.p.itemLike(id);
        return e instanceof Elements.Item it ? it : null;
    }

    private boolean isProjectBlock(String id) {
        return ctx.p.itemLike(id) instanceof Elements.Block;
    }

    /** Item argument usable by give and item replace, or null when the item does not exist on this platform. */
    public String itemArg(String id) {
        return giveArg(id);
    }

    private boolean hasElement(ElementType t, String name) {
        return name != null && !name.isBlank() && ctx.p.byName(t, name.trim()) != null;
    }

    private static final java.util.regex.Pattern DISTANCE = java.util.regex.Pattern.compile(",?\\s*distance\\s*=\\s*[^,\\]]*");

    /** Selector with its distance limited to the given range (an existing distance option is replaced). */
    static String withDistance(String selector, String range) {
        String s = selector.trim();
        if (!s.startsWith("@")) return s;
        if (s.length() == 2) return s + "[distance=" + range + "]";
        String inner = DISTANCE.matcher(s.substring(3, s.length() - 1)).replaceAll("").replaceAll("^\\s*,", "");
        return s.substring(0, 2) + "[" + (inner.isBlank() ? "" : inner + ",") + "distance=" + range + "]";
    }

    /** Item argument for give: custom items become a vanilla base with components/NBT. */
    private String giveArg(String id) {
        String full = Ids.full(id);
        if (customItems) {
            Elements.Item it = projectItem(full);
            if (it != null) return ItemSpec.give(ctx, it);
            if (isProjectBlock(full)) return null;
        }
        return full;
    }

    private String matchArg(String id) {
        String full = Ids.full(id);
        if (customItems) {
            Elements.Item it = projectItem(full);
            if (it != null) return ItemSpec.match(ctx, it);
            if (isProjectBlock(full)) return null;
        }
        return full;
    }

    public List<String> commands(Step s) {
        List<String> r = new ArrayList<>();
        switch (s.kind) {
            case "command" -> {
                for (String line : s.get("command").split("\\R")) {
                    String c = line.trim();
                    if (c.startsWith("/")) c = c.substring(1);
                    if (!c.isEmpty()) r.add(c);
                }
            }
            case "open_menu" -> {
                String m = s.get("menu").trim();
                if (!hasElement(ElementType.MENU, m)) {
                    r.add("# unknown menu " + m);
                } else if (ctx.target.isMod()) {
                    r.add("execute as " + sel(s) + " run " + bridgeCommand() + " menu " + m);
                } else if (ctx.target == modstudio.core.Target.PAPER) {
                    r.add("tag " + sel(s) + " add " + ns + ".q.menu." + m);
                } else {
                    r.add("execute as " + sel(s) + " at @s run function " + ns + ":menus/" + m + "/open");
                }
            }
            case "cutscene_play" -> {
                String cs = s.get("cutscene").trim();
                if (!hasElement(ElementType.CUTSCENE, cs)) r.add("# unknown cutscene " + cs);
                else r.add("execute as " + sel(s) + " at @s run function " + CutsceneGen.playFunction(ns, cs));
            }
            case "play_animation" -> {
                String an = RigGen.safe(s.get("animation"));
                if (!RigGen.animationNames(ctx.p).contains(an)) r.add("# no animated mob has the animation " + an);
                else r.add("execute as " + sel(s) + " if entity @s[tag=" + ns + ".rigged] run function " + ns + ":rig/play/" + an);
            }
            case "stop_animation" -> {
                if (RigGen.animationNames(ctx.p).isEmpty()) r.add("# no animated mobs");
                else r.add("execute as " + sel(s) + " if entity @s[tag=" + ns + ".rigged] run scoreboard players set @s " + ns + ".af 0");
            }
            case "modify_item" -> {
                String m = s.get("modifier").trim();
                String slot = s.get("slot").isBlank() ? "weapon.mainhand" : s.get("slot").trim();
                if (!hasElement(ElementType.ITEM_MODIFIER, m)) r.add("# unknown item modifier " + m);
                else r.add("item modify entity " + sel(s) + " " + slot + " " + ns + ":" + m);
            }
            case "give_loot" -> {
                String t = s.get("table").trim();
                if (t.isEmpty()) r.add("# no loot table");
                else r.add("loot give " + sel(s) + " loot " + (t.contains(":") ? t : ns + ":" + t));
            }
            case "show_dialog" -> {
                String d = s.get("dialog").trim();
                if (!hasElement(ElementType.DIALOG, d)) r.add("# unknown dialog " + d);
                else r.add("execute as " + sel(s) + " at @s run function " + DialogGen.showFunction(ns, d));
            }
            case "spawn_trader" -> {
                String t = s.get("trader").trim();
                if (!hasElement(ElementType.TRADER, t)) r.add("# unknown trader " + t);
                else r.add("execute positioned " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")) + " run function " + ns + ":traders/" + t);
            }
            case "cutscene_stop" -> r.add("execute as " + sel(s) + " if entity @s[tag=" + ns + ".cs_on] run function " + ns + ":cutscene/_stop");
            case "close_menu" -> {
                if (ctx.target.isMod()) r.add("execute as " + sel(s) + " run " + bridgeCommand() + " close");
                else if (ctx.target == modstudio.core.Target.PAPER) r.add("tag " + sel(s) + " add " + ns + ".q.close");
                else r.add("# chat menus need no closing");
            }
            case "hud_show", "hud_hide" -> {
                String h = s.get("hud").trim();
                var hud = (modstudio.core.Systems.Hud) ctx.p.byName(ElementType.HUD, h);
                if (hud == null) {
                    r.add("# unknown hud " + h);
                    break;
                }
                boolean show = s.kind.equals("hud_show");
                String t = s.get("target").isBlank() ? "@a" : s.get("target").trim();
                if (!hud.kind.equals("sidebar")) r.add("tag " + t + " " + (show ? "add" : "remove") + " " + ns + ".hud." + h);
                r.add("function " + ns + ":hud/" + h + "/" + (show ? "show" : "hide"));
            }
            case "timer_start" -> {
                String h = s.get("hud").trim();
                var hud = (modstudio.core.Systems.Hud) ctx.p.byName(ElementType.HUD, h);
                if (hud == null) {
                    r.add("# unknown hud " + h);
                    break;
                }
                String raw = s.get("seconds").trim();
                if (raw.contains("$(")) {
                    objectives.add(ns + ".timer");
                    String arg = argScore(raw);
                    if (arg != null) r.add("scoreboard players operation #" + h + ".max " + ns + ".timer = " + arg);
                    else r.add("scoreboard players set #" + h + ".max " + ns + ".timer " + raw);
                    r.add("function " + ns + ":hud/" + h + "/restart");
                    break;
                }
                int sec = intOr(raw, 0);
                if (sec <= 0) {
                    r.add("function " + ns + ":hud/" + h + "/start");
                } else {
                    objectives.add(ns + ".timer");
                    r.add("scoreboard players set #" + h + ".max " + ns + ".timer " + sec);
                    r.add("function " + ns + ":hud/" + h + "/restart");
                }
            }
            case "timer_stop" -> {
                String h = s.get("hud").trim();
                if (!hasElement(ElementType.HUD, h)) r.add("# unknown hud " + h);
                else r.add("function " + ns + ":hud/" + h + "/stop");
            }
            case "timer_add" -> {
                String h = s.get("hud").trim();
                if (!hasElement(ElementType.HUD, h)) {
                    r.add("# unknown hud " + h);
                    break;
                }
                objectives.add(ns + ".timer");
                int ticks = intOr(s.get("seconds"), 10) * 20;
                r.add("scoreboard players " + (ticks >= 0 ? "add" : "remove") + " #" + h + ".t " + ns + ".timer " + Math.abs(ticks));
            }
            case "http_request" -> {
                String q = s.get("request").trim();
                if (!hasElement(ElementType.API_REQUEST, q)) {
                    r.add("# unknown request " + q);
                } else if (ctx.target.isMod()) {
                    r.add(bridgeCommand() + " http " + q);
                } else if (ctx.target == modstudio.core.Target.PAPER) {
                    objectives.add(ns + ".q");
                    r.add("tag @s add " + ns + ".q.http." + q);
                    r.add("execute unless entity @s run scoreboard players set #http." + q + " " + ns + ".q 1");
                } else {
                    objectives.add(ns + ".http");
                    r.add("# web requests need a mod or plugin host; the failure branch runs instead");
                    r.add("scoreboard players set #" + q + " " + ns + ".http -1");
                    r.add("function " + ns + ":api/" + q + "/failure");
                }
            }
            case "machine_place" -> {
                Element el = ctx.p.byName(ElementType.MACHINE, s.get("machine").trim());
                String why = el instanceof Systems.Machine m ? MachineGen.unavailable(ctx, !customItems, m) : "unknown machine";
                if (why != null) r.add("# machine " + s.get("machine").trim() + ": " + why);
                else r.add("execute positioned " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z"))
                        + " align xyz positioned ~.5 ~.5 ~.5 run function " + ns + ":machines/" + el.name + "/build");
            }
            case "resource_add" -> {
                String rn = s.get("resource").trim();
                if (!hasElement(ElementType.RESOURCE, rn)) {
                    r.add("# unknown resource " + rn);
                    break;
                }
                String amt = MachineGen.amountObj(ns, rn), cap = MachineGen.capObj(ns, rn);
                objectives.add(amt);
                objectives.add(cap);
                double rad = 3;
                try {
                    rad = Double.parseDouble(s.get("radius").trim());
                } catch (Exception ignored) {
                }
                String who = "execute as @e[type=minecraft:marker,tag=" + MachineGen.hasTag(ns, rn) + ",limit=1,sort=nearest,distance=.."
                        + SystemsGen.f(Math.max(0.5, rad)) + "] run ";
                String raw = s.get("amount").trim();
                String arg = argScore(raw);
                if (arg != null) r.add(who + "scoreboard players operation @s " + amt + " += " + arg);
                else if (raw.contains("$(")) r.add(who + "scoreboard players add @s " + amt + " " + raw);
                else {
                    int a = intOr(raw, 0);
                    r.add(who + "scoreboard players " + (a < 0 ? "remove" : "add") + " @s " + amt + " " + Math.abs(a));
                }
                r.add(who.replace(" run ", " if score @s " + amt + " > @s " + cap + " run ") + "scoreboard players operation @s " + amt + " = @s " + cap);
                r.add(who.replace(" run ", " if score @s " + amt + " matches ..-1 run ") + "scoreboard players set @s " + amt + " 0");
            }
            case "multiblock_build" -> {
                Element el = ctx.p.byName(ElementType.MULTIBLOCK, s.get("multiblock").trim());
                if (!(el instanceof Systems.Multiblock mb)) r.add("# unknown multiblock " + s.get("multiblock").trim());
                else {
                    MachineGen.ensureMultiblock(ctx, this, mb);
                    r.add("execute positioned " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")) + " run function " + ns + ":multiblock/" + mb.name + "/build");
                }
            }
            case "contraption_move" -> {
                Element el = ctx.p.byName(ElementType.MULTIBLOCK, s.get("multiblock").trim());
                if (!(el instanceof Systems.Multiblock mb)) r.add("# unknown multiblock " + s.get("multiblock").trim());
                else r.addAll(MachineGen.contraptionMove(ctx, this, mb, coord(s.get("x")), coord(s.get("y")), coord(s.get("z")),
                        intOr(s.get("dx"), 0), intOr(s.get("dy"), 0), intOr(s.get("dz"), 0), intOr(s.get("ticks"), 40)));
            }
            case "spawn_mob" -> {
                String m = s.get("mob").trim();
                if (!hasElement(ElementType.MOB, m)) r.add("# unknown mob " + m);
                else r.add("execute positioned " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")) + " run function " + ns + ":mobs/" + m + "/spawn");
            }
            case "play_effect" -> {
                String e = s.get("effect").trim();
                if (!hasElement(ElementType.EFFECT, e)) r.add("# unknown effect " + e);
                else r.add("execute positioned " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")) + " run function " + ns + ":effects/" + e);
            }
            case "place_structure" -> {
                String st = s.get("structure").trim();
                if (!hasElement(ElementType.STRUCTURE, st)) r.add("# unknown structure " + st);
                else {
                    String rot = or(s.get("rotation"), "none");
                    r.add("place template " + ns + ":" + st + " " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z"))
                            + (rot.equals("none") ? "" : " " + rot));
                }
            }
            case "teleport_dimension" -> {
                String dim = s.get("dimension").trim();
                if (!dim.contains(":")) dim = hasElement(ElementType.DIMENSION, dim) ? ns + ":" + dim : "minecraft:" + dim;
                String pos = coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z"));
                r.add("execute in " + dim + " run tp " + sel(s) + " " + pos);
                if (Boolean.parseBoolean(s.get("safe"))) {
                    r.add("execute in " + dim + " as " + sel(s) + " at @s run spreadplayers ~ ~ 0 1 false @s");
                }
            }
            case "move_blocks" -> {
                // clone moves the blocks; the source is cleared by "move"
                int dx = intOr(s.get("dx"), 0), dy = intOr(s.get("dy"), 0), dz = intOr(s.get("dz"), 0);
                r.add("clone " + coord(s.get("x1")) + " " + coord(s.get("y1")) + " " + coord(s.get("z1")) + " " + coord(s.get("x2")) + " "
                        + coord(s.get("y2")) + " " + coord(s.get("z2")) + " " + shift(s.get("x1"), s.get("x2"), dx) + " "
                        + shift(s.get("y1"), s.get("y2"), dy) + " " + shift(s.get("z1"), s.get("z2"), dz) + " replace move");
            }
            case "beam" -> {
                String target = or(s.get("target"), "@e[type=!minecraft:player,limit=1,sort=nearest,distance=..32]");
                String particle = Ids.full(or(s.get("particle"), "minecraft:end_rod"));
                if (particle.equals("minecraft:dust")) particle = ctx.components() ? "minecraft:dust{color:[1f,0f,0f],scale:1f}" : "minecraft:dust 1 0 0 1";
                String key = "fx/beam_" + Integer.toHexString((particle + "|" + target).hashCode());
                objectives.add(tmp());
                functions.put(key, List.of("particle " + particle + " ~ ~ ~ 0 0 0 0 1 force",
                        "scoreboard players remove #beam " + tmp() + " 1",
                        "execute if score #beam " + tmp() + " matches 1.. unless entity " + withDistance(target, "..0.8")
                                + " positioned ^ ^ ^0.5 run function " + ns + ":" + key));
                r.add("scoreboard players set #beam " + tmp() + " " + Math.max(2, Math.min(512, intOr(s.get("length"), 32) * 2)));
                r.add("execute anchored eyes positioned ^ ^ ^ facing entity " + target + " eyes run function " + ns + ":" + key);
            }
            case "message" -> r.add("tellraw " + sel(s) + " " + text(s.get("text"), s.get("color"), Boolean.parseBoolean(s.get("bold"))));
            case "title" -> {
                if (!s.get("subtitle").isBlank()) r.add("title " + sel(s) + " subtitle " + text(s.get("subtitle"), "gray", false));
                r.add("title " + sel(s) + " title " + text(s.get("title"), s.get("color"), false));
            }
            case "actionbar" -> r.add("title " + sel(s) + " actionbar " + text(s.get("text"), s.get("color"), false));
            case "give" -> {
                String arg = giveArg(s.get("item"));
                if (arg == null) r.add("# blocks from this project are not available on this platform");
                else r.add("give " + sel(s) + " " + arg + " " + Math.max(1, intOr(s.get("count"), 1)));
            }
            case "clear" -> {
                String arg = matchArg(s.get("item"));
                if (arg == null) r.add("# blocks from this project are not available on this platform");
                else r.add("clear " + sel(s) + " " + arg + " " + Math.max(0, intOr(s.get("count"), 1)));
            }
            case "effect" -> {
                String id = Ids.full(s.get("effect"));
                // data packs and plugins cannot register effects: project effects run on scores and tags
                if (customItems && id.startsWith(ns + ":") && ctx.p.byName(modstudio.core.ElementType.STATUS_EFFECT, Ids.path(id)) instanceof Elements.StatusEffect se) {
                    objectives.add(GameplayGen.objective(ns, se.name));
                    r.addAll(GameplayGen.applyEmulated(ctx, se, sel(s), Math.max(1, intOr(s.get("seconds"), 10))));
                } else {
                    r.add("effect give " + sel(s) + " " + id + " " + Math.max(1, intOr(s.get("seconds"), 10))
                            + " " + Math.max(0, intOr(s.get("amplifier"), 0)) + " " + Boolean.parseBoolean(s.get("hide")));
                }
            }
            case "shoot" -> {
                // direction from two markers in front of the eyes, scaled into the projectile's Motion
                String e = Ids.full(or(s.get("entity"), "minecraft:arrow"));
                double speed = Math.max(0.05, Math.min(10, parseD(s.get("speed"), 1.5)));
                String t0 = ns + ".dir0", t1 = ns + ".dir1", shot = ns + ".shot";
                objectives.add(tmp());
                r.add("execute anchored eyes positioned ^ ^ ^ run summon minecraft:marker ~ ~ ~ {Tags:[\"" + t0 + "\"]}");
                r.add("execute anchored eyes positioned ^ ^ ^1 run summon minecraft:marker ~ ~ ~ {Tags:[\"" + t1 + "\"]}");
                r.add("execute anchored eyes positioned ^ ^ ^1 run summon " + e + " ~ ~ ~ {Tags:[\"" + shot + "\"]}");
                String scale = String.valueOf(speed * 1000);
                for (int i = 0; i < 3; i++) {
                    r.add("execute store result score #a " + tmp() + " run data get entity @e[tag=" + t0 + ",limit=1,sort=nearest] Pos[" + i + "] " + scale);
                    r.add("execute store result score #b " + tmp() + " run data get entity @e[tag=" + t1 + ",limit=1,sort=nearest] Pos[" + i + "] " + scale);
                    r.add("scoreboard players operation #b " + tmp() + " -= #a " + tmp());
                    r.add("execute store result entity @e[tag=" + shot + ",limit=1,sort=nearest] Motion[" + i + "] double 0.001 run scoreboard players get #b " + tmp());
                }
                r.add("data modify entity @e[tag=" + shot + ",limit=1,sort=nearest] Owner set from entity @s UUID");
                r.add("kill @e[tag=" + t0 + "]");
                r.add("kill @e[tag=" + t1 + "]");
                r.add("tag @e[tag=" + shot + "] remove " + shot);
            }
            case "teleport" -> r.add("tp " + sel(s) + " " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")));
            case "damage" -> r.add("damage " + sel(s) + " " + num(s.get("amount"), "1") + " " + Ids.full(s.get("damageType").isBlank() ? "generic" : s.get("damageType")));
            case "xp" -> r.add("xp add " + sel(s) + " " + intOr(s.get("amount"), 1) + " " + (s.get("unit").equals("points") ? "points" : "levels"));
            case "kill" -> r.add("kill " + sel(s));
            case "tag" -> r.add("tag " + sel(s) + " " + (s.get("op").equals("remove") ? "remove" : "add") + " " + s.get("tag").trim());
            case "sound" -> r.add("playsound " + Ids.full(s.get("sound")) + " " + (s.get("source").isBlank() ? "master" : s.get("source"))
                    + " " + sel(s) + " ~ ~ ~ " + num(s.get("volume"), "1") + " " + num(s.get("pitch"), "1"));
            case "particle" -> r.add("particle " + Ids.full(s.get("particle")) + " ~ ~1 ~ " + num(s.get("dx"), "0") + " " + num(s.get("dy"), "0")
                    + " " + num(s.get("dz"), "0") + " " + num(s.get("speed"), "0") + " " + Math.max(1, intOr(s.get("count"), 1)));
            case "summon" -> r.add("summon " + Ids.full(s.get("entity")) + " " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")));
            case "setblock" -> {
                String b = Ids.full(s.get("block"));
                if (customItems && isProjectBlock(b)) r.add("# blocks from this project are not available on this platform");
                else r.add("setblock " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")) + " " + b);
            }
            case "time" -> r.add("time set " + (s.get("value").isBlank() ? "day" : s.get("value")));
            case "weather" -> r.add("weather " + (s.get("weather").isBlank() ? "clear" : s.get("weather")));
            case "score" -> {
                String obj = s.get("objective").trim();
                if (!obj.isEmpty()) objectives.add(obj);
                String op = switch (s.get("op")) {
                    case "set" -> "set";
                    case "remove" -> "remove";
                    default -> "add";
                };
                String value = s.get("value").trim();
                String arg = argScore(value);
                if (arg != null) r.add("scoreboard players operation " + sel(s) + " " + obj + " " + switch (op) {
                    case "set" -> "=";
                    case "remove" -> "-=";
                    default -> "+=";
                } + " " + arg);
                else if (value.contains("$(")) r.add("scoreboard players " + op + " " + sel(s) + " " + obj + " " + value);
                else r.add("scoreboard players " + op + " " + sel(s) + " " + obj + " " + intOr(value, 0));
            }
            case "function" -> {
                String f = s.get("function").trim();
                if (!f.isEmpty()) r.add("function " + (f.contains(":") ? f : ns + ":" + f));
            }
            case "broadcast" -> r.add("tellraw @a " + text(s.get("text"), s.get("color"), Boolean.parseBoolean(s.get("bold"))));
            case "stop_sound" -> r.add("stopsound " + sel(s));
            case "replace_item" -> {
                String item = giveArg(s.get("item"));
                if (item != null) r.add("item replace entity " + sel(s) + " " + slot(s.get("slot")) + " with " + item + " " + Math.max(1, intOr(s.get("count"), 1)));
            }
            case "drop_item" -> {
                String id = Ids.full(s.get("item"));
                if (customItems && (projectItem(id) != null || isProjectBlock(id))) {
                    r.add("# drop_item: project items are given with \"give\" in data packs and plugins");
                } else {
                    int n = Math.max(1, intOr(s.get("count"), 1));
                    String stack = ctx.components() ? "{id:\"" + id + "\",count:" + n + "}" : "{id:\"" + id + "\",Count:" + n + "b}";
                    r.add("summon minecraft:item " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")) + " {Item:" + stack + "}");
                }
            }
            case "enchant" -> r.add("enchant " + sel(s) + " " + Ids.full(s.get("enchantment")) + " " + Math.max(1, intOr(s.get("level"), 1)));
            case "heal" -> r.add("effect give " + sel(s) + " minecraft:instant_health 1 " + Math.max(0, intOr(s.get("amount"), 1) - 1) + " true");
            case "feed" -> r.add("effect give " + sel(s) + " minecraft:saturation 1 " + Math.max(0, intOr(s.get("amount"), 1) - 1) + " true");
            case "clear_effects" -> r.add("effect clear " + sel(s));
            case "gamemode" -> r.add("gamemode " + or(s.get("mode"), "survival") + " " + sel(s));
            case "spawnpoint" -> r.add("spawnpoint " + sel(s) + " " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")));
            case "attribute" -> {
                String a = or(s.get("attribute"), "max_health");
                String id = a.contains(":") ? a : ctx.registryKeys() ? "minecraft:" + a : "minecraft:generic." + a;
                r.add("attribute " + sel(s) + " " + id + " base set " + num(s.get("value"), "1"));
            }
            case "ride" -> r.add("ride " + sel(s) + " mount " + or(s.get("vehicle"), "@e[limit=1,sort=nearest,type=!minecraft:player]"));
            case "dismount" -> r.add("ride " + sel(s) + " dismount");
            case "fill" -> {
                String mode = s.get("mode").isBlank() || s.get("mode").equals("replace") ? "" : " " + s.get("mode");
                r.add("fill " + coord(s.get("x1")) + " " + coord(s.get("y1")) + " " + coord(s.get("z1")) + " " + coord(s.get("x2")) + " "
                        + coord(s.get("y2")) + " " + coord(s.get("z2")) + " " + Ids.full(s.get("block")) + mode);
            }
            case "explosion" -> r.add("summon minecraft:creeper " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z"))
                    + " {ExplosionRadius:" + Math.max(0, Math.min(127, intOr(s.get("power"), 3))) + "b,Fuse:0s,ignited:1b,Silent:1b,Invisible:1b}");
            case "lightning" -> r.add("summon minecraft:lightning_bolt " + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")));
            case "place" -> r.add("place " + (s.get("kind").equals("structure") ? "structure " : "feature ") + Ids.full(s.get("id")) + " "
                    + coord(s.get("x")) + " " + coord(s.get("y")) + " " + coord(s.get("z")));
            case "gamerule" -> {
                if (!s.get("rule").isBlank()) r.add("gamerule " + s.get("rule").trim() + " " + or(s.get("value"), "true"));
            }
            case "difficulty" -> r.add("difficulty " + or(s.get("value"), "normal"));
            case "worldborder" -> r.add("worldborder set " + num(s.get("size"), "1000") + (intOr(s.get("seconds"), 0) > 0 ? " " + intOr(s.get("seconds"), 0) : ""));
            case "bossbar" -> {
                String id = bossbarId(s.get("id"));
                String name = text(s.get("text"), "white", false);
                r.add("bossbar add " + id + " " + name);
                r.add("bossbar set " + id + " name " + name);
                r.add("bossbar set " + id + " color " + or(s.get("color"), "purple"));
                r.add("bossbar set " + id + " max " + Math.max(1, intOr(s.get("max"), 100)));
                r.add("bossbar set " + id + " value " + Math.max(0, intOr(s.get("value"), 0)));
                r.add("bossbar set " + id + " players " + or(s.get("players"), "@a"));
                r.add("bossbar set " + id + " visible true");
            }
            case "bossbar_remove" -> r.add("bossbar remove " + bossbarId(s.get("id")));
            case "scoreboard_show" -> {
                String obj = s.get("objective").trim();
                if (obj.isEmpty()) break;
                objectives.add(obj);
                if (!s.get("title").isBlank()) r.add("scoreboard objectives modify " + obj + " displayname " + text(s.get("title"), "white", false));
                String slot = or(s.get("slot"), "sidebar");
                if (slot.equals("below_name") && !ctx.v.atLeast(McVersion.V1_20_4)) slot = "belowName";
                r.add("scoreboard objectives setdisplay " + slot + " " + obj);
            }
            case "random_score" -> {
                String obj = s.get("objective").trim();
                if (obj.isEmpty()) break;
                objectives.add(obj);
                int min = intOr(s.get("min"), 1), max = Math.max(min, intOr(s.get("max"), 6));
                if (ctx.v.atLeast(McVersion.V1_20_4)) r.add("execute store result score " + sel(s) + " " + obj + " run random value " + min + ".." + max);
                else {
                    // shim for 1.20.1: a fresh entity UUID is random; reduce it into the range
                    objectives.add(tmp());
                    r.add("summon minecraft:marker ~ ~ ~ {Tags:[\"" + ns + ".rng\"]}");
                    r.add("execute store result score #rng " + tmp() + " run data get entity @e[type=minecraft:marker,tag=" + ns + ".rng,limit=1] UUID[0]");
                    r.add("kill @e[type=minecraft:marker,tag=" + ns + ".rng]");
                    r.add("scoreboard players set #range " + tmp() + " " + (max - min + 1));
                    r.add("scoreboard players operation #rng " + tmp() + " %= #range " + tmp());
                    r.add("scoreboard players operation " + sel(s) + " " + obj + " = #rng " + tmp());
                    if (min != 0) r.add("scoreboard players " + (min > 0 ? "add " : "remove ") + sel(s) + " " + obj + " " + Math.abs(min));
                }
            }
            case "storage" -> {
                String path = s.get("path").trim();
                if (!path.isEmpty()) r.add("data modify storage " + ns + ":data " + path + " set value " + snbtValue(s.get("value")));
            }
            case "stop" -> r.add(ctx.v.atLeast(McVersion.V1_20_4) ? "return 0" : "# stop needs Minecraft 1.20.2 or later");
            default -> {
            }
        }
        return r;
    }

    /** Destination corner for clone: the lower source coordinate moved by the offset. */
    private static String shift(String a, String b, int d) {
        String lo = lower(coord(a), coord(b));
        if (lo.startsWith("~") || lo.startsWith("^")) {
            String rest = lo.substring(1);
            double v = rest.isEmpty() ? 0 : Double.parseDouble(rest);
            long n = Math.round(v + d);
            return lo.charAt(0) + (n == 0 ? "" : String.valueOf(n));
        }
        try {
            return String.valueOf(Math.round(Double.parseDouble(lo) + d));
        } catch (NumberFormatException e) {
            return lo;
        }
    }

    private static String lower(String a, String b) {
        try {
            double x = a.startsWith("~") || a.startsWith("^") ? (a.length() == 1 ? 0 : Double.parseDouble(a.substring(1))) : Double.parseDouble(a);
            double y = b.startsWith("~") || b.startsWith("^") ? (b.length() == 1 ? 0 : Double.parseDouble(b.substring(1))) : Double.parseDouble(b);
            return x <= y ? a : b;
        } catch (NumberFormatException e) {
            return a;
        }
    }

    private static String or(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static String slot(String s) {
        return s == null || s.isBlank() ? "weapon.mainhand" : s.trim();
    }

    private String bossbarId(String id) {
        String i = Ids.toId(id == null || id.isBlank() ? "bar" : id.trim());
        return ns + ":" + i;
    }

    /** SNBT literal: numbers, booleans and already quoted or compound values are kept, other text is quoted. */
    private static String snbtValue(String v) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) return "\"\"";
        if (t.matches("-?\\d+(\\.\\d+)?[bBsSlLfFdD]?") || t.equals("true") || t.equals("false")) return t;
        if (t.startsWith("{") || t.startsWith("[") || t.startsWith("\"") || t.startsWith("'")) return t;
        return "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String coord(String c) {
        return c == null || c.isBlank() ? "~" : c.trim();
    }

    /** Returns an execute sub-command ("if ..." / "unless ..."), or null when unsupported. */
    public String condition(Condition c) {
        if (c == null) return null;
        String r;
        switch (c.kind) {
            case "chance" -> {
                double pct = 50;
                try {
                    pct = Double.parseDouble(c.get("percent"));
                } catch (Exception ignored) {
                }
                pct = Math.max(0, Math.min(100, pct));
                String name = "procedures/chance_" + J.num(pct).toString().replace('.', '_');
                predicates.put(name, J.obj("condition", "minecraft:random_chance", "chance", pct / 100.0));
                r = "if predicate " + ns + ":" + name;
            }
            case "sneaking" -> {
                predicates.put("procedures/sneaking", J.obj("condition", "minecraft:entity_properties", "entity", "this",
                        "predicate", J.obj("flags", J.obj("is_sneaking", true))));
                r = "if predicate " + ns + ":procedures/sneaking";
            }
            case "holding" -> {
                String id = Ids.full(c.get("item"));
                Elements.Item it = customItems ? projectItem(id) : null;
                if (customItems && it == null && isProjectBlock(id)) return null;
                if (ctx.components()) {
                    String m = it != null ? ItemSpec.match(ctx, it) : id;
                    r = "if items entity @s weapon.mainhand " + m;
                } else {
                    String name = "procedures/holding_" + Ids.toId(Ids.path(id)) + (Ids.isTag(id) ? "_tag" : "");
                    JsonObject itemPred = new JsonObject();
                    if (Ids.isTag(id)) itemPred.addProperty("tag", id.substring(1));
                    else if (it != null) {
                        itemPred.add("items", J.arr(ItemSpec.base(it)));
                        itemPred.addProperty("nbt", ItemSpec.nbtTag(ctx, it));
                    } else itemPred.add("items", J.arr(id));
                    predicates.put(name, J.obj("condition", "minecraft:entity_properties", "entity", "this",
                            "predicate", J.obj("equipment", J.obj("mainhand", itemPred))));
                    r = "if predicate " + ns + ":" + name;
                }
            }
            case "dimension" -> r = "if dimension " + Ids.full(c.get("dimension"));
            case "predicate" -> {
                String n = c.get("predicate").trim();
                r = "if predicate " + (n.contains(":") ? n : ns + ":" + n);
            }
            case "has_tag" -> r = "if entity @s[tag=" + c.get("tag").trim() + "]";
            case "score" -> {
                String min = c.get("min").trim(), max = c.get("max").trim();
                String range = min.isEmpty() && max.isEmpty() ? "1.." : min.equals(max) ? min : min + ".." + max;
                String target = c.get("target").isBlank() ? "@s" : c.get("target").trim();
                String obj = c.get("objective").trim();
                objectives.add(obj);
                String minArg = argScore(min), maxArg = argScore(max);
                if (minArg != null || maxArg != null) {
                    String head = "if score " + target + " " + obj + " ";
                    List<String> parts = new ArrayList<>();
                    if (minArg != null) parts.add(head + ">= " + minArg);
                    else if (!min.isEmpty()) parts.add(head + "matches " + min + "..");
                    if (maxArg != null) parts.add(head + "<= " + maxArg);
                    else if (!max.isEmpty()) parts.add(head + "matches .." + max);
                    r = String.join(" ", parts);
                } else r = "if score " + target + " " + obj + " matches " + range;
            }
            case "block" -> r = "if block " + coord(c.get("x")) + " " + coord(c.get("y")) + " " + coord(c.get("z")) + " " + Ids.full(c.get("block"));
            case "daytime" -> {
                predicates.put("procedures/daytime", J.obj("condition", "minecraft:time_check", "value", J.obj("min", 0, "max", 12000), "period", 24000));
                r = "if predicate " + ns + ":procedures/daytime";
            }
            case "entity_nearby" -> {
                String id = c.get("entity").trim();
                double rad = 8;
                try {
                    rad = Double.parseDouble(c.get("radius").trim());
                } catch (Exception ignored) {
                }
                r = "if entity @e[" + (id.isEmpty() ? "type=!minecraft:player," : "type=" + Ids.full(id) + ",") + "distance=.." + J.num(rad) + "]";
            }
            case "level" -> r = "if entity @s[level=" + Math.max(0, intOr(c.get("min"), 1)) + "..]";
            case "gamemode" -> r = "if entity @s[gamemode=" + (c.get("mode").isBlank() ? "survival" : c.get("mode").trim()) + "]";
            case "state" -> {
                String flag = switch (c.get("state")) {
                    case "on_fire" -> "is_on_fire";
                    case "swimming" -> "is_swimming";
                    case "baby" -> "is_baby";
                    default -> "is_sprinting";
                };
                String name = "procedures/state_" + flag;
                predicates.put(name, J.obj("condition", "minecraft:entity_properties", "entity", "this", "predicate", J.obj("flags", J.obj(flag, true))));
                r = "if predicate " + ns + ":" + name;
            }
            case "raining", "thundering" -> {
                String name = "procedures/" + c.kind;
                predicates.put(name, J.obj("condition", "minecraft:weather_check", c.kind, true));
                r = "if predicate " + ns + ":" + name;
            }
            case "in_water" -> r = "if block ~ ~ ~ minecraft:water";
            case "biome" -> r = "if biome ~ ~ ~ " + Ids.full(c.get("biome"));
            case "has_item" -> {
                String id = Ids.full(c.get("item"));
                Elements.Item it = customItems ? projectItem(id) : null;
                if (customItems && it == null && isProjectBlock(id)) return null;
                if (ctx.components()) {
                    r = "if items entity @s container.* " + (it != null ? ItemSpec.match(ctx, it) : id);
                } else {
                    if (Ids.isTag(id)) return null;
                    r = "if entity @s[nbt={Inventory:[{id:\"" + (it != null ? ItemSpec.base(it) : id) + "\"}]}]";
                }
            }
            case "y_level" -> {
                String name = "procedures/y_" + c.get("min").trim().replace('-', 'm') + "_" + c.get("max").trim().replace('-', 'm');
                JsonObject y = new JsonObject();
                if (!c.get("min").isBlank()) y.addProperty("min", intOr(c.get("min"), -64));
                if (!c.get("max").isBlank()) y.addProperty("max", intOr(c.get("max"), 320));
                predicates.put(name, J.obj("condition", "minecraft:location_check", "predicate", J.obj("position", J.obj("y", y))));
                r = "if predicate " + ns + ":" + name;
            }
            case "entity_type" -> r = "if entity @s[type=" + Ids.full(c.get("entity")) + "]";
            case "timer_running" -> {
                String h = c.get("hud").trim();
                if (!hasElement(ElementType.HUD, h)) return null;
                objectives.add(ns + ".timer");
                r = "if score #" + h + ".run " + ns + ".timer matches 1";
            }
            case "platform", "mc_version" -> {
                objectives.add(tmp());
                pre.add("scoreboard players set #const " + tmp() + " 1");
                // compileTime already applies negate; undo it here because the caller negates "if" itself
                r = "if score #const " + tmp() + " matches " + (Boolean.TRUE.equals(compileTime(c)) != c.negate ? "1" : "0");
            }
            case "multiblock_formed" -> {
                Element el = ctx.p.byName(ElementType.MULTIBLOCK, c.get("multiblock").trim());
                if (!(el instanceof Systems.Multiblock mb)) return null;
                objectives.add(tmp());
                pre.add("function " + MachineGen.ensureMultiblock(ctx, this, mb));
                r = "if score #mb " + tmp() + " matches 1";
            }
            case "resource_amount" -> {
                String rn = c.get("resource").trim();
                if (!hasElement(ElementType.RESOURCE, rn)) return null;
                objectives.add(MachineGen.amountObj(ns, rn));
                double rad = 3;
                try {
                    rad = Double.parseDouble(c.get("radius").trim());
                } catch (Exception ignored) {
                }
                String min = c.get("min").trim();
                r = "if entity @e[type=minecraft:marker,tag=" + MachineGen.hasTag(ns, rn) + ",distance=.." + SystemsGen.f(Math.max(0.5, rad))
                        + ",scores={" + MachineGen.amountObj(ns, rn) + "=" + (min.isEmpty() ? "1" : min) + "..}]";
            }
            case "machine_running" -> {
                String mn = c.get("machine").trim();
                if (!hasElement(ElementType.MACHINE, mn)) return null;
                objectives.add(ns + ".mst");
                double rad = 3;
                try {
                    rad = Double.parseDouble(c.get("radius").trim());
                } catch (Exception ignored) {
                }
                r = "if entity @e[type=minecraft:marker,tag=" + MachineGen.machineTag(ns, mn) + ",distance=.." + SystemsGen.f(Math.max(0.5, rad))
                        + ",scores={" + ns + ".mst=1}]";
            }
            case "celestial" -> {
                String st = c.get("state");
                JsonObject pred = MachineGen.celestialPredicate(st);
                if (pred != null) {
                    predicates.put("machines/" + st, pred);
                    r = "if predicate " + ns + ":machines/" + st;
                } else {
                    objectives.add(tmp());
                    pre.add("scoreboard players set #sky " + tmp() + " 0");
                    pre.add("execute positioned over motion_blocking if entity @s[distance=..1] run scoreboard players set #sky " + tmp() + " 1");
                    r = "if score #sky " + tmp() + " matches 1";
                }
            }
            case "http_status" -> {
                String q = c.get("request").trim();
                if (!hasElement(ElementType.API_REQUEST, q)) return null;
                objectives.add(ns + ".http");
                String min = c.get("min").trim(), max = c.get("max").trim();
                r = "if score #" + q + " " + ns + ".http matches " + (min.isEmpty() ? "" : intOr(min, 0)) + ".." + (max.isEmpty() ? "" : intOr(max, 999));
            }
            case "raw" -> {
                String s = c.get("execute").trim();
                if (s.startsWith("execute ")) s = s.substring(8);
                if (s.endsWith(" run")) s = s.substring(0, s.length() - 4);
                if (s.isEmpty()) return null;
                r = s;
            }
            default -> {
                return null;
            }
        }
        if (c.negate) {
            if (r.startsWith("if ")) r = "unless " + r.substring(3);
            else if (r.startsWith("unless ")) r = "if " + r.substring(7);
        }
        return r;
    }
}
