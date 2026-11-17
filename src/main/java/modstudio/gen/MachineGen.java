package modstudio.gen;

import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.Step;
import modstudio.core.Systems.*;

import java.util.*;

/**
 * Machines, resource networks, multiblocks and moving contraptions as data pack functions, so they run on every
 * Java target. A machine is a block with a marker entity at its centre: the marker keeps the machine's stored
 * resources as scores, runs its cycle, pushes resources to neighbours and notices when the block is gone.
 *
 * <p>Resource models: <b>buffer</b> amounts flow from producers to stores and consumers (energy, mana, aspects;
 * overflow can turn into pollution). <b>stress</b> is capacity against demand: consumers add up the generators and
 * consumers connected to them and stop or break when the network is overloaded (kinetic systems).
 */
public final class MachineGen {
    public static final String CONTRAPTION_TICK = "contraption/_tick";

    private final GenContext ctx;
    private final ProcCompiler pc;
    private final String ns;
    private final String root;
    private final boolean realBlocks;
    private final List<String> tick;
    private final String t;

    public MachineGen(GenContext ctx, ProcCompiler pc, String root, boolean realBlocks, List<String> tick) {
        this.ctx = ctx;
        this.pc = pc;
        this.root = root;
        this.ns = ctx.ns;
        this.realBlocks = realBlocks;
        this.tick = tick;
        this.t = pc.tmp();
    }

    public void generate() {
        for (Multiblock mb : ctx.all(Multiblock.class)) ensureMultiblock(ctx, pc, mb);
        for (Resource r : ctx.all(Resource.class)) resource(r);
        List<String> dispatch = new ArrayList<>();
        for (Machine m : ctx.all(Machine.class)) {
            String why = unavailable(ctx, realBlocks, m);
            if (why != null) {
                ctx.note("machine " + m.name + ": " + why);
                continue;
            }
            machine(m);
            dispatch.add("execute if entity @s[tag=" + machineTag(ns, m.name) + "] run function " + ns + ":machines/" + m.name + "/tick");
        }
        if (!dispatch.isEmpty()) {
            // one pass over all machine markers per tick
            pc.functions.put("machines/_tick", dispatch);
            tick.add("execute as @e[type=minecraft:marker,tag=" + ns + ".machine] at @s run function " + ns + ":machines/_tick");
        }
        new AssemblyGen(ctx, pc, root, realBlocks, tick).generate();
        if (pc.functions.containsKey(CONTRAPTION_TICK)) {
            tick.add("execute as @e[type=minecraft:marker,tag=" + ns + ".ctr] at @s run function " + ns + ":" + CONTRAPTION_TICK);
        }
    }

    // ------------------------------------------------------------------ names

    public static String amountObj(String ns, String res) {
        return ns + ".r." + res;
    }

    static String capObj(String ns, String res) {
        return ns + ".c." + res;
    }

    static String unitsObj(String ns, String res) {
        return ns + ".su." + res;
    }

    public static String machineTag(String ns, String machine) {
        return ns + ".m." + machine;
    }

    /** Tag on every machine that keeps an amount of a buffer resource. */
    public static String hasTag(String ns, String res) {
        return ns + ".has." + res;
    }

    static String blockId(String block) {
        String b = block.trim();
        int i = b.indexOf('[');
        return Ids.full(i >= 0 ? b.substring(0, i) : b);
    }

    /** Why a machine cannot be generated for this target, or null when it can. */
    public static String unavailable(GenContext ctx, boolean realBlocks, Machine m) {
        if (m.block == null || m.block.isBlank()) return "no block is set";
        String id = blockId(m.block);
        if (!realBlocks && id.startsWith(ctx.ns + ":")) return "block " + id + " is not available on this target; use a vanilla block here";
        return null;
    }

    private static String rel(int d) {
        return d == 0 ? "~" : "~" + d;
    }

    private static String num(double d) {
        return SystemsGen.f(d);
    }

    /** Relative x/z offset after turning a pattern by r quarter turns clockwise (seen from above). */
    static int[] rotate(int dx, int dz, int r) {
        return switch (r & 3) {
            case 1 -> new int[]{-dz, dx};
            case 2 -> new int[]{-dx, -dz};
            case 3 -> new int[]{dz, -dx};
            default -> new int[]{dx, dz};
        };
    }

    /** {Name:"minecraft:furnace",Properties:{lit:"true"}} for block display entities. */
    static String blockStateSnbt(String state) {
        String s = state.trim();
        String name = s, props = "";
        int b = s.indexOf('[');
        if (b >= 0 && s.endsWith("]")) {
            name = s.substring(0, b);
            props = s.substring(b + 1, s.length() - 1);
        }
        StringBuilder sb = new StringBuilder("{Name:\"").append(Ids.full(name)).append('"');
        if (!props.isBlank()) {
            sb.append(",Properties:{");
            boolean first = true;
            for (String kv : props.split(",")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append(kv.substring(0, eq).trim()).append(":\"").append(kv.substring(eq + 1).trim()).append('"');
            }
            sb.append('}');
        }
        return sb.append('}').toString();
    }

    static String fullState(String state) {
        String s = state.trim();
        int b = s.indexOf('[');
        return b >= 0 ? Ids.full(s.substring(0, b)) + s.substring(b) : Ids.full(s);
    }

    static boolean isAir(String state) {
        String id = blockId(state);
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    // ------------------------------------------------------------------ multiblocks

    /**
     * Writes multiblock/&lt;name&gt;/check, which sets {@code #mb <ns>.tmp} to 1 when the pattern stands around the
     * current block (the core) in any allowed rotation, and multiblock/&lt;name&gt;/build, which places it.
     */
    public static String ensureMultiblock(GenContext ctx, ProcCompiler pc, Multiblock mb) {
        String ns = ctx.ns, t = pc.tmp(), dir = "multiblock/" + mb.name;
        if (pc.functions.containsKey(dir + "/check")) return ns + ":" + dir + "/check";
        pc.objectives.add(t);
        mb.ensure();
        int rots = mb.rotations ? 4 : 1;
        List<String> check = new ArrayList<>();
        check.add("scoreboard players set #mb " + t + " 0");
        for (int r = 0; r < rots; r++) {
            List<String> lines = new ArrayList<>();
            lines.add("scoreboard players set #mb " + t + " 1");
            for (int y = 0; y < mb.sy; y++)
                for (int z = 0; z < mb.sz; z++)
                    for (int x = 0; x < mb.sx; x++) {
                        int idx = mb.get(x, y, z);
                        if (idx < 0 || idx >= mb.palette.size() || mb.palette.get(idx).isBlank()) continue;
                        int[] o = rotate(x - mb.coreX, z - mb.coreZ, r);
                        lines.add("execute unless block " + rel(o[0]) + " " + rel(y - mb.coreY) + " " + rel(o[1]) + " "
                                + fullState(mb.palette.get(idx)) + " run scoreboard players set #mb " + t + " 0");
                    }
            lines.add("execute if score #mb " + t + " matches 1 run scoreboard players set #mbr " + t + " " + r);
            pc.functions.put(dir + "/r" + r, lines);
            check.add((r == 0 ? "" : "execute if score #mb " + t + " matches 0 run ") + "function " + ns + ":" + dir + "/r" + r);
        }
        pc.functions.put(dir + "/check", check);
        List<String> build = new ArrayList<>();
        for (int y = 0; y < mb.sy; y++)
            for (int z = 0; z < mb.sz; z++)
                for (int x = 0; x < mb.sx; x++) {
                    int idx = mb.get(x, y, z);
                    if (idx < 0 || idx >= mb.palette.size() || mb.palette.get(idx).isBlank()) continue;
                    build.add("setblock " + rel(x - mb.coreX) + " " + rel(y - mb.coreY) + " " + rel(z - mb.coreZ) + " " + fullState(mb.palette.get(idx)));
                }
        pc.functions.put(dir + "/build", build);
        return ns + ":" + dir + "/check";
    }

    /**
     * Lines for one "move contraption" step: lifts the multiblock (rotation as built) into block displays, slides them
     * by dx/dy/dz over the given ticks and places the blocks at the destination. Runs at the core block.
     */
    public static List<String> contraptionMove(GenContext ctx, ProcCompiler pc, Multiblock mb, String x, String y, String z,
                                               int dx, int dy, int dz, int ticks) {
        String ns = ctx.ns, t = pc.tmp(), dir = "contraption/" + mb.name;
        ensureMultiblock(ctx, pc, mb);
        String ctr = ns + ".ctr", ctrT = ns + ".ctrt", ctrK = ns + ".ctrk";
        pc.objectives.add(t);
        pc.objectives.add(ctr);
        pc.objectives.add(ctrT);
        pc.objectives.add(ctrK);
        if (!pc.functions.containsKey(dir + "/lift")) {
            List<String> lift = new ArrayList<>();
            List<String> clear = new ArrayList<>();
            List<String> place = new ArrayList<>();
            lift.add("scoreboard players add #ctr " + ctr + " 1");
            lift.add("summon minecraft:marker ~ ~ ~ {Tags:[\"" + ctr + "\",\"" + ns + ".ctr_new\"]}");
            for (int yy = 0; yy < mb.sy; yy++)
                for (int zz = 0; zz < mb.sz; zz++)
                    for (int xx = 0; xx < mb.sx; xx++) {
                        int idx = mb.get(xx, yy, zz);
                        if (idx < 0 || idx >= mb.palette.size()) continue;
                        String state = mb.palette.get(idx);
                        if (state.isBlank() || isAir(state)) continue;
                        String pos = rel(xx - mb.coreX) + " " + rel(yy - mb.coreY) + " " + rel(zz - mb.coreZ);
                        lift.add("summon minecraft:block_display " + pos + " {block_state:" + blockStateSnbt(state)
                                + ",Tags:[\"" + ns + ".ctrb\",\"" + ns + ".ctr_new\"]}");
                        clear.add("setblock " + pos + " minecraft:air");
                        place.add("setblock " + pos + " " + fullState(state));
                    }
            lift.addAll(clear);
            lift.add("scoreboard players operation @e[tag=" + ns + ".ctr_new] " + ctr + " = #ctr " + ctr);
            pc.functions.put(dir + "/lift", lift);
            pc.functions.put(dir + "/place", place);
        }
        List<String> tk = pc.functions.computeIfAbsent(CONTRAPTION_TICK, k -> new ArrayList<>(List.of("scoreboard players remove @s " + ctrT + " 1")));
        int k = (tk.size() - 1) / 2 + 1;
        int dur = Math.max(1, Math.min(1200, ticks));
        String start = dir + "/start_" + k, go = dir + "/go_" + k, land = dir + "/land_" + k;
        pc.functions.put(start, List.of(
                "function " + ns + ":multiblock/" + mb.name + "/r0",
                "execute if score #mb " + t + " matches 1 run function " + ns + ":" + dir + "/lift",
                "scoreboard players set @e[type=minecraft:marker,tag=" + ns + ".ctr_new] " + ctrK + " " + k,
                "scoreboard players set @e[type=minecraft:marker,tag=" + ns + ".ctr_new] " + ctrT + " " + (dur + 2),
                "tag @e[tag=" + ns + ".ctr_new] remove " + ns + ".ctr_new"));
        pc.functions.put(go, List.of(
                "scoreboard players operation #cur " + ctr + " = @s " + ctr,
                "execute as @e[type=minecraft:block_display,tag=" + ns + ".ctrb] if score @s " + ctr + " = #cur " + ctr
                        + " run data merge entity @s {start_interpolation:0,interpolation_duration:" + dur
                        + ",transformation:{translation:[" + dx + "f," + dy + "f," + dz + "f]}}"));
        pc.functions.put(land, List.of(
                "scoreboard players operation #cur " + ctr + " = @s " + ctr,
                "execute positioned " + rel(dx) + " " + rel(dy) + " " + rel(dz) + " run function " + ns + ":" + dir + "/place",
                "execute as @e[type=minecraft:block_display,tag=" + ns + ".ctrb] if score @s " + ctr + " = #cur " + ctr + " run kill @s",
                "kill @s"));
        tk.add("execute if score @s " + ctrK + " matches " + k + " if score @s " + ctrT + " matches " + dur + " run function " + ns + ":" + go);
        tk.add("execute if score @s " + ctrK + " matches " + k + " if score @s " + ctrT + " matches ..0 run function " + ns + ":" + land);
        return List.of("execute positioned " + x + " " + y + " " + z + " align xyz run function " + ns + ":" + start);
    }

    // ------------------------------------------------------------------ celestial

    /** Predicate for day, night or full moon nights, or null for other states. */
    public static JsonObject celestialPredicate(String state) {
        return switch (state) {
            case "day" -> J.obj("condition", "minecraft:time_check", "value", J.obj("min", 0, "max", 12000), "period", 24000);
            case "night" -> J.obj("condition", "minecraft:time_check", "value", J.obj("min", 13000, "max", 23000), "period", 24000);
            case "moon_full" -> J.obj("condition", "minecraft:time_check", "value", J.obj("min", 13000, "max", 23000), "period", 192000);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ resources

    private void resource(Resource r) {
        if (!r.pollution) return;
        String pol = ns + ".pollution";
        pc.objectives.add(pol);
        pc.compile("resources/" + r.name + "/pollution", r.onPollution, List.of("scoreboard players set #" + r.name + " " + pol + " 0"), List.of());
    }

    private Resource res(String name) {
        Element e = ctx.p.byName(ElementType.RESOURCE, name == null ? "" : name.trim());
        return e instanceof Resource r ? r : null;
    }

    // ------------------------------------------------------------------ machines

    private void machine(Machine m) {
        String dir = "machines/" + m.name;
        String tag = machineTag(ns, m.name);
        String block = blockId(m.block);
        String mcd = ns + ".mcd", mst = ns + ".mst", rpm = ns + ".rpm";
        pc.objectives.add(t);
        pc.objectives.add(mcd);
        pc.objectives.add(mst);
        pc.objectives.add(rpm);

        // inputs and outputs that point at existing resources
        List<MachineIO> buffer = new ArrayList<>(), stress = new ArrayList<>();
        for (MachineIO io : m.io) {
            Resource r = res(io.resource);
            if (r == null) continue;
            if (r.model.equals("stress")) stress.add(io);
            else buffer.add(io);
        }

        // tags and initial scores of the marker
        LinkedHashSet<String> tags = new LinkedHashSet<>(List.of(ns + ".machine", tag, ns + ".m_new"));
        List<String> init = new ArrayList<>(List.of(
                "scoreboard players set @s " + mcd + " 0",
                "scoreboard players set @s " + mst + " 0",
                "scoreboard players set @s " + rpm + " 0"));
        boolean generator = false;
        for (MachineIO io : buffer) {
            String rn = io.resource.trim();
            pc.objectives.add(amountObj(ns, rn));
            pc.objectives.add(capObj(ns, rn));
            tags.add(hasTag(ns, rn));
            switch (io.mode) {
                case "consume" -> {
                    tags.add(ns + ".in." + rn);
                    tags.add(ns + ".use." + rn);
                }
                case "store" -> {
                    tags.add(ns + ".in." + rn);
                    tags.add(ns + ".out." + rn);
                }
                default -> tags.add(ns + ".out." + rn);
            }
            init.add("scoreboard players set @s " + amountObj(ns, rn) + " 0");
            init.add("scoreboard players set @s " + capObj(ns, rn) + " " + Math.max(1, io.capacity));
        }
        for (MachineIO io : stress) {
            String rn = io.resource.trim();
            pc.objectives.add(unitsObj(ns, rn));
            tags.add(ns + ".k." + rn);
            if (io.mode.equals("produce")) {
                tags.add(ns + ".gen." + rn);
                generator = true;
            } else if (io.mode.equals("consume")) {
                tags.add(ns + ".use." + rn);
            }
            init.add("scoreboard players set @s " + unitsObj(ns, rn) + " " + Math.max(0, io.amount));
        }
        if (generator) init.add("scoreboard players set @s " + rpm + " " + Math.max(0, m.rpm));
        init.add("tag @s remove " + ns + ".m_new");
        pc.functions.put(dir + "/init", init);

        StringBuilder tagList = new StringBuilder();
        for (String tg : tags) tagList.append(tagList.isEmpty() ? "" : ",").append('"').append(tg).append('"');
        // place: at the block centre; onPlace steps run as whoever placed it
        pc.compile(dir + "/place", m.onPlace, List.of(
                "summon minecraft:marker ~ ~ ~ {Tags:[" + tagList + "]}",
                "execute as @e[type=minecraft:marker,tag=" + ns + ".m_new] run function " + ns + ":" + dir + "/init"), List.of());
        pc.functions.put(dir + "/build", List.of("setblock ~ ~ ~ " + fullState(m.block), "function " + ns + ":" + dir + "/place"));

        // noticing placed blocks: advancement + ray from the player's eyes to the new block
        String adv = dir + "/placed";
        JsonObject conditions = J.obj("location", J.arr(J.obj("condition", "minecraft:block_state_property", "block", block)));
        ctx.files.json(dataPath(ctx.advDir(), adv + ".json"), J.obj("criteria", J.obj("placed", J.obj("trigger", "minecraft:placed_block",
                "conditions", conditions)), "rewards", J.obj("function", ns + ":" + adv + "_reward")));
        pc.functions.put(adv + "_reward", List.of(
                "advancement revoke @s only " + ns + ":" + adv,
                "scoreboard players set #ray " + t + " 0",
                "execute anchored eyes positioned ^ ^ ^ run function " + ns + ":" + dir + "/ray"));
        pc.functions.put(dir + "/ray", List.of(
                "execute if block ~ ~ ~ " + block + " align xyz positioned ~.5 ~.5 ~.5 unless entity @e[type=minecraft:marker,tag=" + ns
                        + ".machine,distance=..0.5] run function " + ns + ":" + dir + "/found",
                "scoreboard players add #ray " + t + " 1",
                "execute if score #ray " + t + " matches 1..70 positioned ^ ^ ^0.1 run function " + ns + ":" + dir + "/ray"));
        pc.functions.put(dir + "/found", List.of(
                "scoreboard players set #ray " + t + " -1000",
                "function " + ns + ":" + dir + "/place"));

        // every tick: still there? count down to the next cycle
        pc.functions.put(dir + "/tick", List.of(
                "execute unless block ~ ~ ~ " + block + " run function " + ns + ":" + dir + "/broken",
                "execute if block ~ ~ ~ " + block + " run function " + ns + ":" + dir + "/live"));
        pc.functions.put(dir + "/live", List.of(
                "scoreboard players add @s " + mcd + " 1",
                "execute if score @s " + mcd + " matches " + Math.max(1, m.interval) + ".. run function " + ns + ":" + dir + "/cycle"));
        pc.compile(dir + "/broken", m.onBreak, List.of(), List.of("kill @s"));

        // cycle: check requirements, then work
        List<String> cycle = new ArrayList<>();
        cycle.add("scoreboard players set @s " + mcd + " 0");
        cycle.add("scoreboard players set #mok " + t + " 1");
        cycle.add("scoreboard players set #mst " + t + " 0");
        Element mbEl = m.multiblock.isBlank() ? null : ctx.p.byName(ElementType.MULTIBLOCK, m.multiblock.trim());
        if (mbEl instanceof Multiblock mb) {
            cycle.add("function " + ensureMultiblock(ctx, pc, mb));
            cycle.add("execute if score #mb " + t + " matches 0 run scoreboard players set #mok " + t + " 0");
        }
        switch (m.celestial) {
            case "sky" -> cycle.add("execute positioned over motion_blocking unless entity @s[distance=..1] run scoreboard players set #mok " + t + " 0");
            case "day", "night", "moon_full" -> {
                pc.predicates.put("machines/" + m.celestial, celestialPredicate(m.celestial));
                cycle.add("execute unless predicate " + ns + ":machines/" + m.celestial + " run scoreboard players set #mok " + t + " 0");
            }
            default -> {
            }
        }
        for (MachineIO io : buffer) {
            if (!io.mode.equals("consume")) continue;
            cycle.add("execute unless score @s " + amountObj(ns, io.resource.trim()) + " matches " + Math.max(0, io.amount)
                    + ".. run scoreboard players set #mok " + t + " 0");
        }
        for (MachineIO io : stress) {
            if (!io.mode.equals("consume")) continue;
            String rn = io.resource.trim();
            cycle.add("execute if score #mok " + t + " matches 1 run function " + ns + ":" + dir + "/net_" + rn);
            network(m, dir, rn);
        }
        cycle.add("execute if score #mok " + t + " matches 1 run function " + ns + ":" + dir + "/work");
        cycle.add("execute if score #mok " + t + " matches 0 run scoreboard players operation @s " + mst + " = #mst " + t);
        pc.functions.put(dir + "/cycle", cycle);

        // work: use inputs, make outputs, effects, the user's steps, then share
        List<String> work = new ArrayList<>();
        work.add("scoreboard players set @s " + mst + " 1");
        for (MachineIO io : buffer) {
            String rn = io.resource.trim(), amt = amountObj(ns, rn);
            int a = Math.max(0, io.amount);
            if (io.mode.equals("consume") && a > 0) work.add("scoreboard players remove @s " + amt + " " + a);
            if (io.mode.equals("produce") && a > 0) {
                work.add("scoreboard players add @s " + amt + " " + a);
                work.add("execute if score @s " + amt + " > @s " + capObj(ns, rn) + " run function " + ns + ":" + dir + "/overflow_" + rn);
                overflow(dir, rn);
            }
        }
        if (!m.particle.isBlank()) {
            work.add("particle " + SystemsGen.particle(ctx, m.particle, "#FFFFFF", 1)
                    + " ~ ~0.7 ~ 0.25 0.25 0.25 0 4");
        }
        List<String> after = new ArrayList<>();
        if (!m.transfer.equals("none") && buffer.stream().anyMatch(io -> !io.mode.equals("consume"))) {
            after.add("function " + ns + ":" + dir + "/push");
            push(m, dir, buffer);
        }
        pc.compile(dir + "/work", m.onCycle, work, after);

        // info for players: function <ns>:machines/<name>/info shows the nearest machine of this kind
        List<Object> parts = new ArrayList<>();
        JsonObject title = J.obj("text", Ids.titleCase(m.name) + "  ", "color", "gold");
        parts.add(title);
        for (MachineIO io : buffer) {
            Resource r = res(io.resource);
            String label = r.displayName.isBlank() ? Ids.titleCase(r.name) : r.displayName;
            parts.add(J.obj("text", label + " ", "color", "gray"));
            parts.add(J.obj("score", J.obj("name", "@s", "objective", amountObj(ns, r.name)), "color", "white"));
            parts.add(J.obj("text", " / ", "color", "gray"));
            parts.add(J.obj("score", J.obj("name", "@s", "objective", capObj(ns, r.name)), "color", "white"));
            parts.add(J.obj("text", " " + r.unit + "  ", "color", "gray"));
        }
        if (!stress.isEmpty()) {
            parts.add(J.obj("score", J.obj("name", "@s", "objective", rpm), "color", "aqua"));
            parts.add(J.obj("text", " RPM  ", "color", "gray"));
        }
        parts.add(J.obj("text", "state ", "color", "gray"));
        parts.add(J.obj("score", J.obj("name", "@s", "objective", mst), "color", "yellow"));
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        arr.add("");
        for (Object o : parts) arr.add((JsonObject) o);
        pc.functions.put(dir + "/info", List.of(
                "execute as @e[type=minecraft:marker,tag=" + tag + ",limit=1,sort=nearest,distance=..8] run tellraw @p " + J.compact(arr)));
    }

    private void overflow(String dir, String rn) {
        Resource r = res(rn);
        List<String> lines = new ArrayList<>();
        String amt = amountObj(ns, rn), cap = capObj(ns, rn);
        if (r != null && r.pollution) {
            String pol = ns + ".pollution";
            pc.objectives.add(pol);
            lines.add("scoreboard players operation #over " + t + " = @s " + amt);
            lines.add("scoreboard players operation #over " + t + " -= @s " + cap);
            lines.add("scoreboard players operation #" + rn + " " + pol + " += #over " + t);
            lines.add("execute if score #" + rn + " " + pol + " matches " + Math.max(1, r.pollutionLimit) + ".. run function " + ns + ":resources/" + rn + "/pollution");
        }
        lines.add("scoreboard players operation @s " + amt + " = @s " + cap);
        pc.functions.put(dir + "/overflow_" + rn, lines);
    }

    private double reach(Machine m) {
        return m.transfer.equals("adjacent") || m.transfer.equals("none") ? 1.1 : Math.max(1, Math.min(64, m.range));
    }

    /** Producers push to consumers and stores; stores push to consumers only. */
    private void push(Machine m, String dir, List<MachineIO> buffer) {
        List<String> lines = new ArrayList<>();
        String d = num(reach(m));
        lines.add("tag @s add " + ns + ".src");
        for (MachineIO io : buffer) {
            if (io.mode.equals("consume")) continue;
            String rn = io.resource.trim();
            String targets = io.mode.equals("store") ? ns + ".use." + rn : ns + ".in." + rn;
            lines.add("scoreboard players set #rate " + t + " " + Math.max(1, m.transferRate));
            lines.add("execute as @e[type=minecraft:marker,tag=" + targets + ",distance=0.1.." + d + ",sort=nearest] run function " + ns + ":" + dir + "/give_" + rn);
            String amt = amountObj(ns, rn), src = "@e[type=minecraft:marker,tag=" + ns + ".src,limit=1]";
            pc.functions.put(dir + "/give_" + rn, List.of(
                    "scoreboard players operation #t " + t + " = @s " + capObj(ns, rn),
                    "scoreboard players operation #t " + t + " -= @s " + amt,
                    "scoreboard players operation #t " + t + " < #rate " + t,
                    "scoreboard players operation #t " + t + " < " + src + " " + amt,
                    "execute if score #t " + t + " matches 1.. run function " + ns + ":" + dir + "/move_" + rn));
            List<String> move = new ArrayList<>(List.of(
                    "scoreboard players operation @s " + amt + " += #t " + t,
                    "scoreboard players operation " + src + " " + amt + " -= #t " + t,
                    "scoreboard players operation #rate " + t + " -= #t " + t));
            if (m.transfer.equals("beam")) {
                move.add("scoreboard players set #bl " + t + " " + (int) Math.ceil(reach(m) * 2 + 2));
                move.add("execute at " + src + " facing entity @s feet run function " + ns + ":" + dir + "/beam");
            }
            pc.functions.put(dir + "/move_" + rn, move);
        }
        lines.add("tag @s remove " + ns + ".src");
        pc.functions.put(dir + "/push", lines);
        if (m.transfer.equals("beam")) {
            String p = SystemsGen.particle(ctx, m.beamParticle.isBlank() ? "minecraft:end_rod" : m.beamParticle, "#FFFFFF", 1);
            pc.functions.put(dir + "/beam", List.of(
                    "particle " + p + " ~ ~ ~ 0 0 0 0 1 force",
                    "scoreboard players remove #bl " + t + " 1",
                    "execute if score #bl " + t + " matches 1.. unless entity @s[distance=..0.6] positioned ^ ^ ^0.5 run function " + ns + ":" + dir + "/beam"));
        }
    }

    /** Stress network of a consumer: every machine of the resource connected through neighbours within reach. */
    private void network(Machine m, String dir, String rn) {
        String net = ns + ".net", mst = ns + ".mst", rpm = ns + ".rpm", su = unitsObj(ns, rn);
        String d = num(reach(m));
        pc.functions.put(dir + "/flood_" + rn, List.of(
                "execute as @e[type=minecraft:marker,tag=" + ns + ".k." + rn + ",tag=!" + net + ",distance=.." + d + "] at @s run function "
                        + ns + ":" + dir + "/flood_" + rn + "_add"));
        pc.functions.put(dir + "/flood_" + rn + "_add", List.of("tag @s add " + net, "function " + ns + ":" + dir + "/flood_" + rn));
        List<String> lines = new ArrayList<>(List.of(
                "tag @s add " + net,
                "function " + ns + ":" + dir + "/flood_" + rn,
                "scoreboard players set #cap " + t + " 0",
                "scoreboard players set #dem " + t + " 0",
                "scoreboard players set #rpm " + t + " 0",
                "execute as @e[type=minecraft:marker,tag=" + net + ",tag=" + ns + ".gen." + rn + ",scores={" + mst + "=1}] run scoreboard players operation #cap " + t + " += @s " + su,
                "execute as @e[type=minecraft:marker,tag=" + net + ",tag=" + ns + ".use." + rn + "] run scoreboard players operation #dem " + t + " += @s " + su,
                "execute as @e[type=minecraft:marker,tag=" + net + ",tag=" + ns + ".gen." + rn + ",scores={" + mst + "=1}] run scoreboard players operation #rpm " + t + " > @s " + rpm,
                "tag @e[type=minecraft:marker,tag=" + net + "] remove " + net,
                "scoreboard players operation @s " + rpm + " = #rpm " + t,
                "execute if score #rpm " + t + " matches ..0 run scoreboard players set #mok " + t + " 0"));
        if (m.rpm > 0 && m.io.stream().noneMatch(io -> io.mode.equals("produce") && rn.equals(io.resource.trim()))) {
            lines.add("execute if score #rpm " + t + " matches .." + (m.rpm - 1) + " run scoreboard players set #mok " + t + " 0");
        }
        lines.add("execute if score #dem " + t + " > #cap " + t + " run function " + ns + ":" + dir + "/overload");
        pc.functions.put(dir + "/net_" + rn, lines);
        if (!pc.functions.containsKey(dir + "/overload")) {
            pc.functions.put(dir + "/overload", List.of(
                    "scoreboard players set #mok " + t + " 0",
                    "scoreboard players set #mst " + t + " 2",
                    "execute unless score @s " + mst + " matches 2 run function " + ns + ":" + dir + "/on_overload"));
            List<String> action = new ArrayList<>();
            switch (m.overload) {
                case "break" -> action.add("setblock ~ ~ ~ minecraft:air destroy");
                case "explode" -> {
                    action.add("setblock ~ ~ ~ minecraft:air");
                    action.add("summon minecraft:tnt ~ ~ ~ {" + (ctx.v.atLeast(McVersion.V1_20_4) ? "fuse" : "Fuse") + ":0s}");
                }
                default -> {
                }
            }
            pc.compile(dir + "/on_overload", m.onOverload, List.of("scoreboard players set @s " + mst + " 2"), action);
        }
    }

    private String dataPath(String dir, String path) {
        return root + "data/" + ns + "/" + dir + "/" + path;
    }
}
