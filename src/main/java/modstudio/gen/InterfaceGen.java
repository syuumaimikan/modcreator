package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import modstudio.core.CommandSpec;
import modstudio.core.Elements;
import modstudio.core.Elements.Step;
import modstudio.core.Ids;
import modstudio.core.Logic;
import modstudio.core.McVersion;
import modstudio.core.Project;
import modstudio.core.Systems.ApiRequest;
import modstudio.core.Systems.Extract;
import modstudio.core.Systems.Hud;
import modstudio.core.Systems.Menu;
import modstudio.core.Systems.MenuSlot;
import modstudio.core.Target;

import java.util.*;
import java.util.function.Function;

/**
 * Functions for the interface systems: inventory menus (chat menus on plain data packs), HUD sidebars, boss bars and
 * action bars with timers, web request handlers and command branches.
 */
public final class InterfaceGen {
    private final GenContext ctx;
    private final ProcCompiler pc;
    private final List<String> load, tick, join;
    private final String ns;

    public InterfaceGen(GenContext ctx, ProcCompiler pc, List<String> load, List<String> tick, List<String> join) {
        this.ctx = ctx;
        this.pc = pc;
        this.load = load;
        this.tick = tick;
        this.join = join;
        this.ns = ctx.ns;
    }

    public static boolean needsWeb(Project p) {
        return !p.all(ApiRequest.class).isEmpty() || p.all(Elements.Procedure.class).stream().anyMatch(x -> x.trigger.equals("cron"));
    }

    /** True when the Java side has to answer function calls (menus, web requests, schedules). */
    public static boolean needsBridge(Project p) {
        return !p.all(Menu.class).isEmpty() || needsWeb(p);
    }

    /** Function path of each command branch, null where the branch path does not parse. */
    public static List<String> branchFunctions(Elements.Command c) {
        List<String> r = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < c.branches.size(); i++) {
            List<CommandSpec.Token> toks = CommandSpec.tryParse(c.branches.get(i).path);
            if (toks == null) {
                r.add(null);
                continue;
            }
            String slug = CommandSpec.slug(toks, i), s = slug;
            if (used.contains(s)) {
                StringBuilder args = new StringBuilder();
                for (CommandSpec.Token t : toks) if (!t.literal()) args.append('_').append(t.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
                if (!args.isEmpty()) s = slug = slug + args;
            }
            int k = 2;
            while (!used.add(s)) s = slug + "_" + k++;
            r.add("commands/" + c.name + "/branch/" + s);
        }
        return r;
    }

    public void generate() {
        menus();
        huds();
        requests();
        if (ctx.target.isMod() && needsWeb(ctx.p)) load.add(pc.bridgeCommand() + " init");
    }

    private boolean plainPack() {
        return !ctx.target.isMod() && ctx.target != Target.PAPER;
    }

    // ------------------------------------------------------------------ text

    private static final String CODES = "0123456789abcdef";
    private static final String[] COLOR_NAMES = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    /**
     * Converts text with &amp; color codes and {placeholders} to a text component array. The resolver returns the
     * component for a placeholder, or null to keep it as plain text.
     */
    public static JsonArray rich(String text, Function<String, JsonElement> resolver) {
        JsonArray out = new JsonArray();
        out.add("");
        String s = text == null ? "" : text;
        StringBuilder buf = new StringBuilder();
        Map<String, Object> style = new LinkedHashMap<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                flush(out, buf, style);
                char code = Character.toLowerCase(s.charAt(i + 1));
                int ci = CODES.indexOf(code);
                if (ci >= 0) {
                    style.clear();
                    style.put("color", COLOR_NAMES[ci]);
                } else switch (code) {
                    case 'l' -> style.put("bold", true);
                    case 'o' -> style.put("italic", true);
                    case 'n' -> style.put("underlined", true);
                    case 'm' -> style.put("strikethrough", true);
                    case 'k' -> style.put("obfuscated", true);
                    default -> style.clear();
                }
                i += 2;
                continue;
            }
            if (c == '{' && resolver != null) {
                int end = s.indexOf('}', i);
                if (end > i) {
                    JsonElement ph = resolver.apply(s.substring(i + 1, end).trim());
                    if (ph != null) {
                        flush(out, buf, style);
                        if (ph.isJsonArray()) for (JsonElement e : ph.getAsJsonArray()) out.add(styled(e, style));
                        else out.add(styled(ph, style));
                        i = end + 1;
                        continue;
                    }
                }
            }
            buf.append(c);
            i++;
        }
        flush(out, buf, style);
        return out;
    }

    private static void flush(JsonArray out, StringBuilder buf, Map<String, Object> style) {
        if (buf.isEmpty()) return;
        JsonObject o = J.obj("text", buf.toString());
        apply(o, style);
        out.add(o);
        buf.setLength(0);
    }

    private static JsonElement styled(JsonElement e, Map<String, Object> style) {
        if (!e.isJsonObject() || style.isEmpty()) return e;
        JsonObject o = e.getAsJsonObject().deepCopy();
        apply(o, style);
        return o;
    }

    private static void apply(JsonObject o, Map<String, Object> style) {
        for (var en : style.entrySet()) {
            if (en.getValue() instanceof Boolean b) o.addProperty(en.getKey(), b);
            else o.addProperty(en.getKey(), en.getValue().toString());
        }
    }

    private static JsonObject score(String holder, String objective) {
        return J.obj("score", J.obj("name", holder, "objective", objective));
    }

    private String json(JsonElement e) {
        return J.compact(e);
    }

    private void click(JsonObject o, String command) {
        if (ctx.v.snbtText()) o.add("click_event", J.obj("action", "run_command", "command", command));
        else o.add("clickEvent", J.obj("action", "run_command", "value", command));
    }

    private void hover(JsonObject o, JsonElement text) {
        if (ctx.v.snbtText()) o.add("hover_event", J.obj("action", "show_text", "value", text));
        else o.add("hoverEvent", J.obj("action", "show_text", "contents", text));
    }

    // ------------------------------------------------------------------ menus

    private static int menuId(int menuIndex, MenuSlot sl) {
        return menuIndex * 100 + sl.slot + 1;
    }

    /** True when a slot runs a function when clicked. */
    public static boolean slotActive(MenuSlot sl) {
        return !sl.action.equals("none") || (sl.cost > 0 && !sl.costObjective.isBlank());
    }

    /** True when the Java side has to check the slot permission (levels on mods, nodes on Paper). */
    public static boolean javaPermission(Target t, MenuSlot sl) {
        String perm = sl.permission.trim();
        if (perm.isEmpty()) return false;
        return t == Target.PAPER || perm.matches("\\d");
    }

    private void menus() {
        List<Menu> menus = ctx.all(Menu.class);
        if (menus.isEmpty()) return;
        boolean pack = plainPack();
        List<String> click = new ArrayList<>();
        int mi = 0;
        for (Menu m : menus) {
            mi++;
            for (MenuSlot sl : m.slots) {
                if (!slotActive(sl)) continue;
                String base = "menus/" + m.name + "/slot_" + sl.slot;
                compileSlot(m, sl, base, pack);
                if (pack) click.add("execute if score @s " + ns + ".menu matches " + menuId(mi, sl) + " run function " + ns + ":" + base);
            }
            if (!m.openSound.isBlank()) pc.functions.put("menus/" + m.name + "/open_fx", List.of(sound(m.openSound)));
            if (pack) chatMenu(m, mi);
        }
        if (pack) {
            load.add("scoreboard objectives add " + ns + ".menu trigger");
            tick.add("scoreboard players enable @a " + ns + ".menu");
            tick.add("execute as @a[scores={" + ns + ".menu=1..}] at @s run function " + ns + ":menus/_click");
            click.add("scoreboard players set @s " + ns + ".menu 0");
            pc.functions.put("menus/_click", click);
        }
    }

    private static String sound(String id) {
        return "playsound " + Ids.full(id.trim()) + " master @s ~ ~ ~ 1 1";
    }

    private void compileSlot(Menu m, MenuSlot sl, String base, boolean pack) {
        List<Step> steps = new ArrayList<>();
        switch (sl.action) {
            case "run" -> steps.addAll(sl.steps);
            case "command" -> {
                Step c = Logic.newStep("command");
                c.p.put("command", sl.target);
                steps.add(c);
            }
            case "menu" -> {
                Step o = Logic.newStep("open_menu");
                o.p.put("menu", sl.target);
                o.p.put("target", "@s");
                steps.add(o);
            }
            default -> {
            }
        }
        boolean close = sl.action.equals("close") || ((sl.action.equals("run") || sl.action.equals("command")) && sl.closeAfter);
        if (close && !pack) {
            Step c = Logic.newStep("close_menu");
            c.p.put("target", "@s");
            steps.add(c);
        }
        String perm = sl.permission.trim();
        boolean tagPerm = !perm.isEmpty() && !javaPermission(ctx.target, sl);
        boolean cost = sl.cost > 0 && !sl.costObjective.isBlank();
        List<String> fx = m.clickSound.isBlank() ? List.of() : List.of(sound(m.clickSound));
        if (!tagPerm && !cost) {
            pc.compile(base, steps, fx, List.of());
            return;
        }
        String obj = sl.costObjective.trim();
        String run = base + "_run";
        List<String> pre = new ArrayList<>();
        if (cost) {
            pc.objectives.add(obj);
            pre.add("scoreboard players remove @s " + obj + " " + sl.cost);
        }
        pc.compile(run, steps, pre, List.of());
        pc.objectives.add(pc.tmp());
        String ok = "#ok " + pc.tmp();
        List<String> gate = new ArrayList<>(fx);
        gate.add("scoreboard players set " + ok + " 1");
        if (tagPerm) gate.add("execute unless entity @s[tag=" + perm + "] run scoreboard players set " + ok + " 0");
        if (cost) gate.add("execute unless score @s " + obj + " matches " + sl.cost + ".. run scoreboard players set " + ok + " 0");
        gate.add("execute if score " + ok + " matches 0 run tellraw @s " + json(rich(m.denyMessage, null)));
        gate.add("execute if score " + ok + " matches 1 run function " + ns + ":" + run);
        pc.functions.put(base, gate);
    }

    private void chatMenu(Menu m, int mi) {
        List<String> out = new ArrayList<>();
        String title = m.title.isBlank() ? Ids.titleCase(m.name) : m.title;
        JsonArray head = rich("&6&l" + title, null);
        if (!m.openSound.isBlank()) out.add(sound(m.openSound));
        out.add("tellraw @s " + json(head));
        TreeMap<Integer, List<MenuSlot>> rows = new TreeMap<>();
        List<MenuSlot> sorted = new ArrayList<>(m.slots);
        sorted.sort(Comparator.comparingInt(x -> x.slot));
        for (MenuSlot sl : sorted) rows.computeIfAbsent(sl.slot / 9, k -> new ArrayList<>()).add(sl);
        for (List<MenuSlot> row : rows.values()) {
            JsonArray line = new JsonArray();
            line.add("");
            boolean first = true;
            for (MenuSlot sl : row) {
                if (!first) line.add("  ");
                first = false;
                String label = sl.name.isBlank() ? Ids.titleCase(Ids.path(Ids.full(sl.item))) : sl.name;
                JsonObject c = J.obj("text", "[", "color", slotActive(sl) ? "aqua" : "gray");
                JsonArray extra = rich(label, null);
                extra.add("]");
                c.add("extra", extra);
                StringBuilder tip = new StringBuilder(label);
                if (!sl.lore.isBlank()) tip.append("\n&7").append(sl.lore.replace("\n", "\n&7"));
                if (sl.cost > 0 && !sl.costObjective.isBlank()) tip.append("\n&e").append(sl.cost).append(' ').append(sl.costObjective);
                hover(c, rich(tip.toString(), null));
                if (slotActive(sl)) click(c, "/trigger " + ns + ".menu set " + menuId(mi, sl));
                line.add(c);
            }
            out.add("tellraw @s " + json(line));
        }
        pc.functions.put("menus/" + m.name + "/open", out);
    }

    // ------------------------------------------------------------------ HUD

    private void huds() {
        List<Hud> huds = ctx.all(Hud.class);
        if (huds.isEmpty()) return;
        String t = ns + ".timer";
        pc.objectives.add(t);
        pc.objectives.add(ns + ".hud");
        load.add("scoreboard players set #20 " + t + " 20");
        load.add("scoreboard players set #60 " + t + " 60");
        load.add("scoreboard players set #10 " + t + " 10");
        int index = 0;
        for (Hud h : huds) hud(h, ++index);
    }

    /** Text shown by boss bars and action bars: the lines joined, or the title when there are none. */
    private static String barText(Hud h) {
        List<String> parts = new ArrayList<>();
        for (String l : h.lines) if (!l.isBlank()) parts.add(l);
        if (parts.isEmpty()) return h.title.isBlank() ? Ids.titleCase(h.name) : h.title;
        return String.join("  ", parts);
    }

    private List<String> lines(Hud h) {
        List<String> r = new ArrayList<>();
        for (String l : h.lines) r.add(l);
        while (r.size() > 15) r.remove(r.size() - 1);
        return r;
    }

    /** Placeholder components resolved by the game (boss bars, action bars). */
    private Function<String, JsonElement> resolved(Hud h, boolean perPlayer) {
        String t = ns + ".timer", n = h.name;
        return key -> switch (key) {
            case "timer" -> score("#" + n + ".s", t);
            case "min" -> score("#" + n + ".m", t);
            case "sec" -> {
                JsonArray a = new JsonArray();
                a.add(score("#" + n + ".s10", t));
                a.add(score("#" + n + ".s1", t));
                yield a;
            }
            case "player" -> perPlayer ? J.obj("selector", "@s") : null;
            default -> {
                if (!key.startsWith("score:")) yield null;
                String[] parts = key.substring(6).split(":", 2);
                if (parts[0].isBlank()) yield null;
                yield score(parts.length > 1 ? parts[1] : perPlayer ? "@s" : "#global", parts[0]);
            }
        };
    }

    /** Score reads for the sidebar: values are copied to storage and inserted by a macro (1.20.2+). */
    private record MacroRead(String key, String holder, String objective) {}

    private Function<String, JsonElement> macro(Hud h, List<MacroRead> reads) {
        String t = ns + ".timer", n = h.name;
        return key -> {
            List<MacroRead> add = new ArrayList<>();
            switch (key) {
                case "timer" -> add.add(new MacroRead("v" + reads.size(), "#" + n + ".s", t));
                case "min" -> add.add(new MacroRead("v" + reads.size(), "#" + n + ".m", t));
                case "sec" -> {
                    add.add(new MacroRead("v" + reads.size(), "#" + n + ".s10", t));
                    add.add(new MacroRead("v" + (reads.size() + 1), "#" + n + ".s1", t));
                }
                default -> {
                    if (!key.startsWith("score:")) return null;
                    String[] parts = key.substring(6).split(":", 2);
                    if (parts[0].isBlank()) return null;
                    add.add(new MacroRead("v" + reads.size(), parts.length > 1 ? parts[1] : "#global", parts[0]));
                }
            }
            JsonArray a = new JsonArray();
            for (MacroRead r : add) {
                reads.add(r);
                a.add(J.obj("text", "$(" + r.key() + ")"));
            }
            return a;
        };
    }

    private void hud(Hud h, int index) {
        String n = h.name, f = "hud/" + n + "/", fn = ns + ":" + f;
        String t = ns + ".timer", v = ns + ".hud";
        String tag = ns + ".hud." + n, audience = "@a[tag=" + tag + "]";
        String bar = ns + ":" + n, obj = ns + ".hud." + n;
        String title = h.title.isBlank() ? Ids.titleCase(n) : h.title;
        int refresh = Math.max(1, h.refresh);
        List<String> refreshFn = new ArrayList<>();
        refreshFn.add("scoreboard players set #" + n + ".r " + v + " 0");
        List<String> show = new ArrayList<>(), hide = new ArrayList<>();
        switch (h.kind) {
            case "sidebar" -> {
                boolean modern = ctx.v.atLeast(McVersion.V1_20_4);
                List<String> lines = lines(h);
                load.add("scoreboard objectives add " + obj + " dummy");
                load.add("scoreboard objectives modify " + obj + " displayname " + json(rich(title, null)));
                if (modern) load.add("scoreboard objectives modify " + obj + " numberformat blank");
                if (modern) {
                    List<MacroRead> reads = new ArrayList<>();
                    List<String> macroLines = new ArrayList<>();
                    Function<String, JsonElement> m = macro(h, reads);
                    macroLines.add(withMacro("scoreboard objectives modify " + obj + " displayname " + json(rich(title, m))));
                    for (int i = 0; i < lines.size(); i++) {
                        load.add("scoreboard players set #l" + i + " " + obj + " " + (lines.size() - i));
                        macroLines.add(withMacro("scoreboard players display name #l" + i + " " + obj + " " + json(rich(lines.get(i).isEmpty() ? " " : lines.get(i), m))));
                    }
                    for (MacroRead r : reads) {
                        pc.objectives.add(r.objective());
                        refreshFn.add("execute store result storage " + ns + ":hud " + n + "." + r.key() + " int 1 run scoreboard players get " + r.holder() + " " + r.objective());
                    }
                    pc.functions.put(f + "lines", macroLines);
                    refreshFn.add(reads.isEmpty() ? "function " + fn + "lines" : "function " + fn + "lines with storage " + ns + ":hud " + n);
                    if (!reads.isEmpty()) {
                        StringBuilder init = new StringBuilder("{");
                        for (MacroRead r : reads) init.append(init.length() > 1 ? "," : "").append(r.key()).append(":0");
                        load.add("data modify storage " + ns + ":hud " + n + " set value " + init + "}");
                    }
                } else {
                    for (int i = 0; i < lines.size(); i++) {
                        String holder = "§" + CODES.charAt(i) + "§r";
                        String team = ns + ".h" + index + "_" + i;
                        load.add("scoreboard players set " + holder + " " + obj + " " + (lines.size() - i));
                        load.add("team add " + team);
                        load.add("team join " + team + " " + holder);
                        load.add("team modify " + team + " prefix " + json(rich(lines.get(i), resolvedStatic(h))));
                    }
                }
                show.add("scoreboard objectives setdisplay sidebar " + obj);
                show.add("scoreboard players set #" + n + ".v " + v + " 1");
                show.add("function " + fn + "refresh");
                hide.add("execute if score #" + n + ".v " + v + " matches 1 run scoreboard objectives setdisplay sidebar");
                hide.add("scoreboard players set #" + n + ".v " + v + " 0");
            }
            case "bossbar" -> {
                String color = List.of("blue", "green", "pink", "purple", "red", "white", "yellow").contains(h.color) ? h.color : "white";
                String style = List.of("progress", "notched_6", "notched_10", "notched_12", "notched_20").contains(h.style) ? h.style : "progress";
                load.add("bossbar add " + bar + " " + json(rich(title, null)));
                load.add("bossbar set " + bar + " color " + color);
                load.add("bossbar set " + bar + " style " + style);
                load.add("bossbar set " + bar + " max " + Math.max(1, h.seconds));
                refreshFn.add("bossbar set " + bar + " name " + json(rich(barText(h), resolved(h, false))));
                if (h.timer) refreshFn.add("execute store result bossbar " + bar + " value run scoreboard players get #" + n + ".s " + t);
                refreshFn.add("execute unless entity " + audience + " run bossbar set " + bar + " players");
                refreshFn.add("bossbar set " + bar + " players " + audience);
                show.add("scoreboard players set #" + n + ".v " + v + " 1");
                show.add("bossbar set " + bar + " visible true");
                show.add("function " + fn + "refresh");
                hide.add("function " + fn + "refresh");
            }
            default -> {
                refreshFn.add("execute as " + audience + " at @s run title @s actionbar " + json(rich(barText(h), resolved(h, true))));
                show.add("scoreboard players set #" + n + ".v " + v + " 1");
                show.add("function " + fn + "refresh");
            }
        }
        pc.functions.put(f + "refresh", refreshFn);
        pc.functions.put(f + "show", show);
        pc.functions.put(f + "hide", hide);

        // timer
        pc.functions.put(f + "calc", List.of(
                "scoreboard players operation #" + n + ".s " + t + " = #" + n + ".t " + t,
                "scoreboard players add #" + n + ".s " + t + " 19",
                "scoreboard players operation #" + n + ".s " + t + " /= #20 " + t,
                "execute if score #" + n + ".s " + t + " matches ..-1 run scoreboard players set #" + n + ".s " + t + " 0",
                "scoreboard players operation #" + n + ".m " + t + " = #" + n + ".s " + t,
                "scoreboard players operation #" + n + ".m " + t + " /= #60 " + t,
                "scoreboard players operation #" + n + ".ss " + t + " = #" + n + ".s " + t,
                "scoreboard players operation #" + n + ".ss " + t + " %= #60 " + t,
                "scoreboard players operation #" + n + ".s10 " + t + " = #" + n + ".ss " + t,
                "scoreboard players operation #" + n + ".s10 " + t + " /= #10 " + t,
                "scoreboard players operation #" + n + ".s1 " + t + " = #" + n + ".ss " + t,
                "scoreboard players operation #" + n + ".s1 " + t + " %= #10 " + t));
        List<String> restart = new ArrayList<>(List.of(
                "scoreboard players operation #" + n + ".t " + t + " = #" + n + ".max " + t,
                "scoreboard players operation #" + n + ".t " + t + " *= #20 " + t,
                "scoreboard players set #" + n + ".run " + t + " 1"));
        if (h.kind.equals("bossbar")) restart.add("execute store result bossbar " + bar + " max run scoreboard players get #" + n + ".max " + t);
        restart.add("function " + fn + "calc");
        restart.add("function " + fn + "refresh");
        pc.functions.put(f + "restart", restart);
        pc.functions.put(f + "start", List.of("scoreboard players set #" + n + ".max " + t + " " + Math.max(1, h.seconds), "function " + fn + "restart"));
        pc.functions.put(f + "stop", List.of("scoreboard players set #" + n + ".run " + t + " 0", "function " + fn + "refresh"));
        pc.functions.put(f + "timer", List.of(
                "scoreboard players remove #" + n + ".t " + t + " 1",
                "function " + fn + "calc",
                "execute if score #" + n + ".t " + t + " matches ..0 run function " + fn + "finish"));
        pc.compile(f + "finish", h.onFinish, List.of(
                "scoreboard players set #" + n + ".run " + t + " 0",
                "scoreboard players set #" + n + ".t " + t + " 0",
                "function " + fn + "calc",
                "function " + fn + "refresh"), List.of());
        load.add("execute unless score #" + n + ".max " + t + " matches 1.. run scoreboard players set #" + n + ".max " + t + " " + Math.max(1, h.seconds));
        load.add("function " + fn + "calc");

        List<String> tickFn = new ArrayList<>();
        tickFn.add("execute if score #" + n + ".run " + t + " matches 1 run function " + fn + "timer");
        tickFn.add("execute if score #" + n + ".v " + v + " matches 1 run scoreboard players add #" + n + ".r " + v + " 1");
        tickFn.add("execute if score #" + n + ".r " + v + " matches " + refresh + ".. run function " + fn + "refresh");
        pc.functions.put(f + "tick", tickFn);
        tick.add("function " + fn + "tick");

        if (h.autoStart) {
            String players = h.players.isBlank() ? "@a" : h.players.trim();
            if (!h.kind.equals("sidebar")) load.add("tag " + players + " add " + tag);
            load.add("function " + fn + "show");
            if (h.timer) load.add("execute unless score #" + n + ".run " + t + " matches 1 run function " + fn + "start");
            if (!h.kind.equals("sidebar")) {
                String self = players.startsWith("@") && players.length() >= 2 ? "@s" + players.substring(2) : "@s";
                join.add("execute if entity " + self + " run tag @s add " + tag);
            }
        }
    }

    /** Sidebars before 1.20.3 cannot show changing values; placeholders render as fixed text. */
    private Function<String, JsonElement> resolvedStatic(Hud h) {
        return key -> key.equals("timer") || key.equals("min") || key.equals("sec") || key.startsWith("score:") ? new JsonPrimitive("-") : null;
    }

    private static String withMacro(String line) {
        return line.contains("$(") ? "$" + line : line;
    }

    // ------------------------------------------------------------------ web requests

    private void requests() {
        List<ApiRequest> reqs = ctx.all(ApiRequest.class);
        if (reqs.isEmpty()) return;
        pc.objectives.add(ns + ".http");
        if (ctx.target == Target.PAPER) pc.objectives.add(ns + ".q");
        for (ApiRequest r : reqs) {
            for (Extract x : r.extract) if (x.toScore && x.key.trim().matches("[A-Za-z0-9_.+-]+")) pc.objectives.add(x.key.trim());
            String b = "api/" + r.name + "/";
            pc.compile(b + "success", r.onSuccess, List.of(), List.of());
            pc.compile(b + "client_error", r.onClientError.isEmpty() ? r.onFailure : r.onClientError, List.of(), List.of());
            pc.compile(b + "server_error", r.onServerError.isEmpty() ? r.onFailure : r.onServerError, List.of(), List.of());
            pc.compile(b + "failure", r.onFailure, List.of(), List.of());
        }
    }
}
